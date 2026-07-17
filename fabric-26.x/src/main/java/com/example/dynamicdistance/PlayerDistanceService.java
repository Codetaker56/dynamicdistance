package com.example.dynamicdistance;

import com.example.dynamicdistance.core.DistanceCalculator;
import com.example.dynamicdistance.core.DistanceConfig;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player distance state and the logic that applies it. All application work
 * runs on the server thread (join event, command, end-of-tick drain). The client
 * settings mixin only flips a thread-safe dirty flag; the actual chunk work is
 * deferred to the tick drain, which also enforces the debounce cooldown.
 */
public final class PlayerDistanceService {

    /** appliedSim sentinel: nothing applied yet, redirect should fall back to vanilla global. */
    static final int UNSET = Integer.MIN_VALUE;

    /** Per-player mutable state. Kept tiny; removed on disconnect (no UUID leak). */
    static final class State {
        volatile int viewOverride = DistanceCalculator.NO_OVERRIDE;
        volatile int simOverride = DistanceCalculator.NO_OVERRIDE;
        volatile int appliedSim = UNSET;      // simulation distance currently registered for this player
        volatile boolean dirty = false;       // needs (re)apply
        volatile long lastApplyTick = Long.MIN_VALUE;
    }

    private final ConcurrentHashMap<UUID, State> states = new ConcurrentHashMap<>();

    // ---- lifecycle ----

    public void onJoin(ServerPlayer player) {
        State s = states.computeIfAbsent(player.getUUID(), k -> new State());
        if (DynamicDistance.config().enabled && DynamicDistance.config().updateOnJoin) {
            s.dirty = true; // applied on the next tick (lastApplyTick == MIN => no cooldown wait)
        }
    }

    public void onClientSettingsChanged(ServerPlayer player) {
        DistanceConfig cfg = DynamicDistance.config();
        if (!cfg.enabled || !cfg.updateOnClientSettingsChange) return;
        states.computeIfAbsent(player.getUUID(), k -> new State()).dirty = true;
    }

    /** Remove all state for a player. Call on disconnect. */
    public void onDisconnect(UUID uuid) {
        states.remove(uuid);
    }

    // ---- called by mixins (hot path: keep cheap) ----

    /** Effective per-player view/chunk-send distance. */
    public int effectiveView(ServerPlayer player, int serverMax) {
        DistanceConfig cfg = DynamicDistance.config();
        int requested = player.requestedViewDistance();
        int override = cfg.allowPerPlayerOverrides ? viewOverride(player.getUUID()) : DistanceCalculator.NO_OVERRIDE;
        return DistanceCalculator.effectiveView(requested, serverMax, override, cfg);
    }

    /** Cached simulation distance for the redirect, or null to fall back to vanilla's global value. */
    public Integer appliedSimulationDistance(ServerPlayer player) {
        State s = states.get(player.getUUID());
        if (s == null || s.appliedSim == UNSET) return null;
        return s.appliedSim;
    }

    // ---- application (server thread only) ----

    /** Recompute and apply both distances for one player if they changed. */
    public void applyNow(ServerPlayer player, long tick) {
        try {
            DistanceController controller = controller(player);
            int serverMax = controller.dynamicdistance$serverViewDistance();
            DistanceConfig cfg = DynamicDistance.config();

            int view = effectiveView(player, serverMax);
            int simOverride = cfg.allowPerPlayerOverrides ? simOverride(player.getUUID()) : DistanceCalculator.NO_OVERRIDE;
            int sim = DistanceCalculator.effectiveSimulation(view, simOverride, cfg);

            // View distance: re-run vanilla tracking; the mixin's redirect now returns `view`.
            controller.dynamicdistance$refreshViewDistance(player);

            // Simulation: swap the player's ticket level (remove-at-old, add-at-new) so the
            // add/remove levels stay matched. See applySimulation for the co-located caveat.
            applySimulation(player, sim);

            State s = states.computeIfAbsent(player.getUUID(), k -> new State());
            s.lastApplyTick = tick;
            s.dirty = false;

            if (cfg.debugLogging) {
                DynamicDistance.LOGGER.info("[dynamicdistance] {} requested={} -> view={} sim={} (serverMax={})",
                        player.getName().getString(), player.requestedViewDistance(), view, sim, serverMax);
            }
        } catch (Throwable t) {
            DynamicDistance.logError("apply failed for " + player.getName().getString(), t);
        }
    }

    private void applySimulation(ServerPlayer player, int newSim) {
        State s = states.computeIfAbsent(player.getUUID(), k -> new State());
        if (s.appliedSim == newSim) return;
        DistanceManager dm = distanceManager(player);
        SectionPos section = SectionPos.of(player);
        // Remove the old contribution (redirect reads the still-old cached value)...
        dm.removePlayer(section, player);
        // ...flip the cache...
        s.appliedSim = newSim;
        // ...then add the new contribution (redirect now reads the new value).
        dm.addPlayer(section, player);
    }

    /** End-of-tick drain: apply dirty players whose cooldown has elapsed. */
    public void tick(MinecraftServer server) {
        DistanceConfig cfg = DynamicDistance.config();
        if (!cfg.enabled) return;
        long now = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            State s = states.get(player.getUUID());
            if (s == null || !s.dirty) continue;
            if (s.lastApplyTick == Long.MIN_VALUE || now - s.lastApplyTick >= cfg.updateCooldownTicks) {
                applyNow(player, now);
            }
        }
    }

    /** Reapply every online player (used after /reload). */
    public void applyAll(MinecraftServer server) {
        long now = server.getTickCount();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            applyNow(player, now);
        }
    }

    // ---- overrides (commands) ----

    public void setViewOverride(ServerPlayer player, int distance) {
        states.computeIfAbsent(player.getUUID(), k -> new State()).viewOverride = distance;
        markDirty(player);
    }

    public void setSimOverride(ServerPlayer player, int distance) {
        states.computeIfAbsent(player.getUUID(), k -> new State()).simOverride = distance;
        markDirty(player);
    }

    public void clearOverrides(ServerPlayer player) {
        State s = states.get(player.getUUID());
        if (s != null) {
            s.viewOverride = DistanceCalculator.NO_OVERRIDE;
            s.simOverride = DistanceCalculator.NO_OVERRIDE;
        }
        markDirty(player);
    }

    public int viewOverride(UUID uuid) {
        State s = states.get(uuid);
        return s == null ? DistanceCalculator.NO_OVERRIDE : s.viewOverride;
    }

    public int simOverride(UUID uuid) {
        State s = states.get(uuid);
        return s == null ? DistanceCalculator.NO_OVERRIDE : s.simOverride;
    }

    private void markDirty(ServerPlayer player) {
        states.computeIfAbsent(player.getUUID(), k -> new State()).dirty = true;
    }

    // ---- helpers ----

    /** Current effective simulation distance for display (not necessarily applied yet). */
    public int computeSimulation(ServerPlayer player) {
        DistanceConfig cfg = DynamicDistance.config();
        int view = effectiveView(player, controller(player).dynamicdistance$serverViewDistance());
        int simOverride = cfg.allowPerPlayerOverrides ? simOverride(player.getUUID()) : DistanceCalculator.NO_OVERRIDE;
        return DistanceCalculator.effectiveSimulation(view, simOverride, cfg);
    }

    public int serverViewDistance(ServerPlayer player) {
        return controller(player).dynamicdistance$serverViewDistance();
    }

    static DistanceController controller(ServerPlayer player) {
        return (DistanceController) player.level().getChunkSource().chunkMap;
    }

    static DistanceManager distanceManager(ServerPlayer player) {
        return player.level().getChunkSource().chunkMap.getDistanceManager();
    }
}
