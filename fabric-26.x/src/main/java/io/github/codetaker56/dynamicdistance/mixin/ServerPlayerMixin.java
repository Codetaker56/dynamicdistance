package io.github.codetaker56.dynamicdistance.mixin;

import io.github.codetaker56.dynamicdistance.DynamicDistance;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Detects a live render-distance change. updateOptions() runs whenever the client re-sends
 * its ClientInformation (which it does when the player changes render distance in Video
 * Settings). By TAIL the new requestedViewDistance is already stored, so we just flag the
 * player dirty; the actual chunk work happens on the next server tick (debounced).
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "updateOptions", at = @At("TAIL"))
    private void dynamicdistance$onClientOptions(ClientInformation info, CallbackInfo ci) {
        DynamicDistance.service().onClientSettingsChanged((ServerPlayer) (Object) this);
    }
}
