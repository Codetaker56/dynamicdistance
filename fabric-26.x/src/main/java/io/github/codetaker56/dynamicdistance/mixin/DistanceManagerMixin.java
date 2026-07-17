package io.github.codetaker56.dynamicdistance.mixin;

import io.github.codetaker56.dynamicdistance.DynamicDistance;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 26.x (Mojang mappings): per-player simulation distance.
 * Verified identical in 26.1, 26.2 and 26.3-snapshot-4.
 *
 * addPlayer/removePlayer register each player's PLAYER_SIMULATION ticket at a single
 * GLOBAL level via getPlayerTicketLevel() (= ENTITY_TICKING_level - globalSim). We redirect
 * that call so each player contributes at their OWN derived level. Enter and leave both go
 * through this redirect and read the player's *cached* applied distance, so add and remove
 * levels stay matched for a player alone in a chunk (the normal case).
 *
 * ponytail: known ceiling - the simulation ticket is per-chunk, so two players sharing one
 * chunk with different sim distances collapse to the higher one (matches how ticking works:
 * a chunk ticks if any nearby player needs it). A distance change while co-located can also
 * leave one stale ticket in that chunk until it unloads. Rare, bounded, never affects view
 * distance. Upgrade path: manage a private per-(player,chunk) ticket instead of reusing vanilla's.
 */
@Mixin(DistanceManager.class)
public abstract class DistanceManagerMixin {

    @Shadow protected abstract int getPlayerTicketLevel();

    @Redirect(
            method = {"addPlayer", "removePlayer"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/level/DistanceManager;getPlayerTicketLevel()I"))
    private int dynamicdistance$perPlayerSimulationLevel(DistanceManager self,
                                                         SectionPos section, ServerPlayer player) {
        // Driven purely by cached per-player state, NOT the enabled flag: when the mod is
        // disabled the service restores each player (appliedSim -> UNSET), and null below
        // falls back to vanilla. Gating on `enabled` here would make the restore's remove/add
        // levels mismatch and leak a ticket.
        try {
            Integer distance = DynamicDistance.service().appliedSimulationDistance(player);
            if (distance == null) return getPlayerTicketLevel(); // untracked / restored -> vanilla global
            int base = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);
            return Math.max(0, base - distance);
        } catch (Throwable t) {
            DynamicDistance.logError("simulation-level calc failed", t);
            return getPlayerTicketLevel();
        }
    }
}
