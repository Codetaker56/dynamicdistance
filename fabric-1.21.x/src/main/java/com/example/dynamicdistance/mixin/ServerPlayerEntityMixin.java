package com.example.dynamicdistance.mixin;

import com.example.dynamicdistance.DynamicDistance;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Detects a live render-distance change. setClientOptions() runs whenever the client
 * re-sends its options (which it does when the player changes render distance in Video
 * Settings). By TAIL the new viewDistance field is already stored, so we just flag the
 * player dirty; the actual chunk work happens on the next server tick (debounced).
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {

    @Inject(method = "setClientOptions", at = @At("TAIL"))
    private void dynamicdistance$onClientOptions(SyncedClientOptions options, CallbackInfo ci) {
        DynamicDistance.service().onClientSettingsChanged((ServerPlayerEntity) (Object) this);
    }
}
