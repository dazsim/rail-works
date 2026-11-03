package com.workshopcraft.railworks.fabric.mixin;

import com.workshopcraft.railworks.client.MoltenSteelFog;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void railworks$moltenSteel(Camera camera, int renderDistance, DeltaTracker delta, float darken, ClientLevel level,
                                       CallbackInfoReturnable<FogData> cir) {
        if (MoltenSteelFog.inSteel(camera)) MoltenSteelFog.apply(camera, cir.getReturnValue());
    }
}
