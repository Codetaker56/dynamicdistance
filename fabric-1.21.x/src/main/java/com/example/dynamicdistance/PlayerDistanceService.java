package com.example.dynamicdistance;

import com.example.dynamicdistance.core.DistanceCalculator;
import com.example.dynamicdistance.core.DistanceConfig;
import com.example.dynamicdistance.mixin.ServerChunkManagerAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketManager;
import net.minecraft.util.math.ChunkSectionPos;

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

    public void onJoin(ServerPlayerEntity player) {
        State s = states.computeIfAbsent(player.getUuid(), k -> new State());
        if (DynamicDistance.config().enabled && DynamicDistance.config().updateOnJoin) {
            s.dirty = true; // applied on the next tick (lastApplyTick == MIN => no cooldown wait)
        }
    }

    public void onClientSettingsChanged(ServerPlayerEntity player) {
        DistanceConfig cfg = DynamicDistance.config();
        if (!cfg.enabled || !cfg.updateOnClientSettingsChange) return;
        states.computeIfAbsent(player.getUuid(), k -> new State()).dirty = true;
    }

    /** Remove all state for a player. Call on disconnect. */
    public void onDisconnect(UUID uuid) {
        states.remove(uuid);
    }

    // ---- called by mixins (hot path: keep cheap) ----

    /** Effective per-player view/chunk-send distance. */
    public int effectiveView(ServerPlayerEntity player, int serverMax) {
        DistanceConfig cfg = DynamicDistance.config();
        int requested = player.getViewDistance();
        int override = cfg.allowPerPlayerOverrides ? viewOverride(player.getUuid()) : DistanceCalculator.NO_OVERRIDE;
        return DistanceCalculator.effectiveView(requested, serverMax, override, cfg);
    }

    /** Cached simulation distance for the redirect, or null to fall back to vanilla's global value. */
    public Integer appliedSimulationDistance(ServerPlayerEntity player) {
        State s = states.get(player.getUuid());
        if (s == null || s.appliedSim == UNSET) return null;
        return s.appliedSim;
    }

    // ---- application (server thread only) ----

    /** Recompute and apply both distances for one player if they changed. */
    public void applyNow(ServerPlayerEntity player, long tick) {
        try {
            DistanceController controller = controller(player);
            int serverMax = controller.dynamicdistance$serverViewDistance();
            DistanceConfig cfg = DynamicDistance.config();

            int view = effectiveView(player, serverMax);
            int simOverride = cfg.allowPerPlayerOverrides ? simOverride(player.getUuid()) : DistanceCalculator.NO_OVERRIDE;
            int sim = DistanceCalculator.effectiveSimulation(view, simOverride, cfg);

            // View distance: re-run vanilla tracking; the mixin's redirect now returns `view`.
            controller.dynamicdistance$refreshViewDistance(player);

            // Simulation: swap the player's ticket level (remove-at-old, add-at-new) so the
            // add/remove levels stay matched. See applySimulation for the co-located caveat.
            applySimulation(player, sim);

            State s = states.computeIfAbsent(player.getUuid(), k -> new State());
            s.lastApplyTick = tick;
            s.dirty = false;

            if (cfg.debugLogging) {
                DynamicDistance.LOGGER.info("[dynamicdistance] {} requested={} -> view={} sim={} (serverMax={})",
                        player.getName().getString(), player.getViewDistance(), view, sim, serverMax);
            }
        } catch (Throwable t) {
            DynamicDistance.logError("apply failed for " + player.getName().getString(), t);
        }
    }

    private void applySimulation(ServerPlayerEntity player, int newSim) {
        State s = states.computeIfAbsent(player.getUuid(), k -> new State());
        if (s.appliedSim == newSim) return;
        ChunkTicketManager tickets = ticketManager(player);
        ChunkSectionPos section = ChunkSectionPos.from(player);
        // Remove the old contribution (redirect reads the still-old cached value)...
        tickets.handleChunkLeave(section, player);
        // ...flip the cache...
        s.appliedSim = newSim;
        // ...then add the new contribution (redirect now reads the new value).
        tickets.handleChunkEnter(section, player);
    }

    /** End-of-tick drain: apply dirty players whose cooldown has elapsed. */
    public void tick(MinecraftServer server) {
        DistanceConfig cfg = DynamicDistance.config();
        if (!cfg.enabled) return;
        long now = server.getTicks();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            State s = states.get(player.getUuid());
            if (s == null || !s.dirty) continue;
            if (s.lastApplyTick == Long.MIN_VALUE || now - s.lastApplyTick >= cfg.updateCooldownTicks) {
                applyNow(player, now);
            }
        }
    }

    /** Reapply every online player (used after /reload). */
    public void applyAll(MinecraftServer server) {
        long now = server.getTicks();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            applyNow(player, now);
        }
    }

    // ---- overrides (commands) ----

    public void setViewOverride(ServerPlayerEntity player, int distance) {
        states.computeIfAbsent(player.getUuid(), k -> new State()).viewOverride = distance;
        markDirty(player);
    }

    public void setSimOverride(ServerPlayerEntity player, int distance) {
        states.computeIfAbsent(player.getUuid(), k -> new State()).simOverride = distance;
        markDirty(player);
    }

    public void clearOverrides(ServerPlayerEntity player) {
        State s = states.get(player.getUuid());
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

    private void markDirty(ServerPlayerEntity player) {
        states.computeIfAbsent(player.getUuid(), k -> new State()).dirty = true;
    }

    // ---- helpers ----

    /** Current effective simulation distance for display (not necessarily applied yet). */
    public int computeSimulation(ServerPlayerEntity player) {
        DistanceConfig cfg = DynamicDistance.config();
        int view = effectiveView(player, controller(player).dynamicdistance$serverViewDistance());
        int simOverride = cfg.allowPerPlayerOverrides ? simOverride(player.getUuid()) : DistanceCalculator.NO_OVERRIDE;
        return DistanceCalculator.effectiveSimulation(view, simOverride, cfg);
    }

    public int serverViewDistance(ServerPlayerEntity player) {
        return controller(player).dynamicdistance$serverViewDistance();
    }

    static DistanceController controller(ServerPlayerEntity player) {
        return (DistanceController) player.getServerWorld().getChunkManager().chunkLoadingManager;
    }

    static ChunkTicketManager ticketManager(ServerPlayerEntity player) {
        return ((ServerChunkManagerAccessor) player.getServerWorld().getChunkManager()).dynamicdistance$ticketManager();
    }
}
