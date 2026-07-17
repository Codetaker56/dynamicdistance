package io.github.codetaker56.dynamicdistance.mixin;

import io.github.codetaker56.dynamicdistance.DistanceController;
import io.github.codetaker56.dynamicdistance.DynamicDistance;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 26.x (Mojang mappings, unobfuscated -> binds to real names): ChunkMap.
 *
 * Vanilla already does per-player chunk sending:
 *   getPlayerViewDistance(player) = Mth.clamp(player.requestedViewDistance(), 2, serverViewDistance)
 * and updateChunkTracking() diffs the old/new tracked area (ChunkTrackingView.difference)
 * and sends only the delta. We substitute our configurable per-player value into that one
 * method; all tracking / sending / loading / generation downstream follows.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin implements DistanceController {

    @Shadow private int serverViewDistance;

    @Shadow protected abstract void updateChunkTracking(ServerPlayer player);

    @ModifyReturnValue(
            method = "getPlayerViewDistance(Lnet/minecraft/server/level/ServerPlayer;)I",
            at = @At("RETURN"))
    private int dynamicdistance$perPlayerViewDistance(int original, ServerPlayer player) {
        if (!DynamicDistance.config().enabled) return original;
        try {
            return DynamicDistance.service().effectiveView(player, serverViewDistance);
        } catch (Throwable t) {
            DynamicDistance.logError("view-distance calc failed for " + player.getName().getString(), t);
            return original; // fail safe: fall back to vanilla behaviour
        }
    }

    @Override
    public int dynamicdistance$serverViewDistance() {
        return serverViewDistance;
    }

    @Override
    public void dynamicdistance$refreshViewDistance(ServerPlayer player) {
        updateChunkTracking(player);
    }
}
