package com.example.dynamicdistance.mixin;

import com.example.dynamicdistance.DistanceController;
import com.example.dynamicdistance.DynamicDistance;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkLoadingManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 1.21.x (yarn): {@code ServerChunkLoadingManager} is vanilla's ChunkMap.
 *
 * Vanilla per-player chunk sending already exists here:
 *   getViewDistance(player) = clamp(player.getViewDistance(), 2, watchDistance)
 * and updateWatchedSection() diffs the old/new tracked area and sends only the
 * delta. We only need to substitute our configurable per-player value into that
 * one method; everything downstream (tracking, sending, loading, generation)
 * follows automatically.
 */
@Mixin(ServerChunkLoadingManager.class)
public abstract class ServerChunkLoadingManagerMixin implements DistanceController {

    @Shadow private int watchDistance;

    @Shadow protected abstract void updateWatchedSection(ServerPlayerEntity player);

    @ModifyReturnValue(
            method = "getViewDistance(Lnet/minecraft/server/network/ServerPlayerEntity;)I",
            at = @At("RETURN"))
    private int dynamicdistance$perPlayerViewDistance(int original, ServerPlayerEntity player) {
        if (!DynamicDistance.config().enabled) return original;
        try {
            return DynamicDistance.service().effectiveView(player, watchDistance);
        } catch (Throwable t) {
            DynamicDistance.logError("view-distance calc failed for " + player.getName().getString(), t);
            return original; // fail safe: fall back to vanilla behaviour
        }
    }

    @Override
    public int dynamicdistance$serverViewDistance() {
        return watchDistance;
    }

    @Override
    public void dynamicdistance$refreshViewDistance(ServerPlayerEntity player) {
        updateWatchedSection(player);
    }
}
