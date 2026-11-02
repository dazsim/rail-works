package com.workshopcraft.railworks.neoforge.mixin;

import com.workshopcraft.railworks.trolley.Berths;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.TeleportTransition;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
    @Inject(method = "findRespawnPositionAndUseSpawnBlock", at = @At("HEAD"), cancellable = true)
    private void railworks$berth(boolean consume, TeleportTransition.PostTeleportTransition post, CallbackInfoReturnable<TeleportTransition> cir) {
        TeleportTransition t = Berths.respawn((ServerPlayer) (Object) this, post);
        if (t != null) cir.setReturnValue(t);
    }

    @Inject(method = "setRespawnPosition", at = @At("HEAD"))
    private void railworks$elsewhere(ServerPlayer.@Nullable RespawnConfig config, boolean showMessage, CallbackInfo ci) {
        if (config != null) Berths.forget((ServerPlayer) (Object) this);
    }
}
