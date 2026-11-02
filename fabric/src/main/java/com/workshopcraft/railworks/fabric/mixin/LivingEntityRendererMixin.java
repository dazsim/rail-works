package com.workshopcraft.railworks.fabric.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.workshopcraft.railworks.client.BerthState;
import com.workshopcraft.railworks.trolley.Berths;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    private void railworks$berth(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
        float yaw = Berths.asleep(entity) ? Berths.headYaw(entity) : Float.NaN;
        ((BerthState) state).railworks$setHeadYaw(yaw);
        if (!Float.isNaN(yaw)) state.bodyRot = 90 - yaw;
    }

    // the sleeper stands at the pillow, so the body is laid back from there towards the feet, as a bed does
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V", ordinal = 0))
    private void railworks$pillow(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        float yaw = ((BerthState) state).railworks$headYaw();
        if (Float.isNaN(yaw)) return;
        double r = Math.toRadians(yaw), off = 1.52 * state.scale;
        pose.translate(Math.sin(r) * off, 0, -Math.cos(r) * off);
    }
}
