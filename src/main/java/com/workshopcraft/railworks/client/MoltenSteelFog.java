package com.workshopcraft.railworks.client;

import com.workshopcraft.railworks.registry.ModTags;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;

/** White-hot fog when the camera is inside molten steel. */
public final class MoltenSteelFog {
    private MoltenSteelFog() {}

    public static boolean inSteel(Camera camera) {
        if (camera.entity() == null) return false;
        Level level = camera.entity().level();
        BlockPos pos = camera.blockPosition();
        FluidState fluid = level.getFluidState(pos);
        return fluid.is(ModTags.MOLTEN_STEEL) && camera.position().y < pos.getY() + fluid.getHeight(level, pos);
    }

    public static void apply(Camera camera, FogData fog) {
        fog.color.set(1.0F, 0.86F, 0.52F, 1.0F);
        boolean resist = camera.entity() instanceof LivingEntity e && e.hasEffect(MobEffects.FIRE_RESISTANCE);
        fog.environmentalStart = resist ? 0.0F : 0.1F;
        fog.environmentalEnd = resist ? 3.0F : 0.6F;
        fog.skyEnd = fog.environmentalEnd;
        fog.cloudEnd = fog.environmentalEnd;
    }
}
