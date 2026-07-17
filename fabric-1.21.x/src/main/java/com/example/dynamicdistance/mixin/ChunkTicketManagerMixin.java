package com.example.dynamicdistance.mixin;

import com.example.dynamicdistance.DynamicDistance;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkLevelType;
import net.minecraft.server.world.ChunkLevels;
import net.minecraft.server.world.ChunkTicketManager;
import net.minecraft.util.math.ChunkSectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 1.21.x (yarn): per-player simulation distance.
 *
 * Vanilla adds each player's simulation contribution in handleChunkEnter/Leave at a
 * single GLOBAL level via getPlayerSimulationLevel() (= ENTITY_TICKING_level - globalSim).
 * We redirect that call so each player contributes at their OWN derived level. Because the
 * enter and leave call sites both go through this redirect and read the player's *cached*
 * applied distance, the add and remove levels stay matched for a player who is alone in a
 * chunk (the normal case).
 *
 * ponytail: known ceiling - if two players occupy the SAME chunk with different sim
 * distances, vanilla keeps one contribution per chunk and the higher distance wins (this
 * matches how ticking actually works: a chunk ticks if any nearby player needs it). A
 * distance change while co-located can also leave one stale contribution in that chunk
 * until it unloads. Rare, bounded, never affects view distance. Upgrade path: manage a
 * private per-player ticket keyed by (player,chunk) instead of reusing vanilla's.
 */
@Mixin(ChunkTicketManager.class)
public abstract class ChunkTicketManagerMixin {

    @Shadow protected abstract int getPlayerSimulationLevel();

    @Redirect(
            method = {"handleChunkEnter", "handleChunkLeave"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/server/world/ChunkTicketManager;getPlayerSimulationLevel()I"))
    private int dynamicdistance$perPlayerSimulationLevel(ChunkTicketManager self,
                                                         ChunkSectionPos section, ServerPlayerEntity player) {
        if (!DynamicDistance.config().enabled) return getPlayerSimulationLevel();
        try {
            Integer distance = DynamicDistance.service().appliedSimulationDistance(player);
            if (distance == null) return getPlayerSimulationLevel(); // not applied yet -> vanilla global
            int base = ChunkLevels.getLevelFromType(ChunkLevelType.ENTITY_TICKING);
            return Math.max(0, base - distance);
        } catch (Throwable t) {
            DynamicDistance.logError("simulation-level calc failed", t);
            return getPlayerSimulationLevel();
        }
    }
}
