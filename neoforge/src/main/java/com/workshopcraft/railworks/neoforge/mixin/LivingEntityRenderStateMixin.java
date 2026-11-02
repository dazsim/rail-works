package com.workshopcraft.railworks.neoforge.mixin;

import com.workshopcraft.railworks.client.BerthState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
abstract class LivingEntityRenderStateMixin implements BerthState {
    @Unique
    private float railworks$headYaw = Float.NaN;

    @Override
    public float railworks$headYaw() {
        return railworks$headYaw;
    }

    @Override
    public void railworks$setHeadYaw(float yaw) {
        railworks$headYaw = yaw;
    }
}
