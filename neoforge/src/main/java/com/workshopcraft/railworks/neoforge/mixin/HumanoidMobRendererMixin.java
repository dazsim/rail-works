package com.workshopcraft.railworks.neoforge.mixin;

import com.workshopcraft.railworks.client.TrolleyClient;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidMobRenderer.class)
abstract class HumanoidMobRendererMixin {
    @Inject(method = "extractHumanoidRenderState", at = @At("TAIL"))
    private static void railworks$standing(LivingEntity entity, HumanoidRenderState state, float partialTicks, ItemModelResolver resolver, CallbackInfo ci) {
        if (TrolleyClient.standing(entity)) state.isPassenger = false;
    }
}
