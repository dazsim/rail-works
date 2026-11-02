package com.workshopcraft.railworks.neoforge.mixin;

import com.workshopcraft.railworks.trolley.Berths;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
abstract class CameraMixin {
    @Shadow
    private @Nullable Entity entity;

    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    // a bed faces the camera to the foot; a berth has no block to ask, so it is told by the carriage
    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void railworks$berth(float partialTicks, CallbackInfo ci) {
        if (!detached && entity != null && Berths.asleep(entity)) setRotation(Berths.headYaw(entity) - 180, 0);
    }
}
