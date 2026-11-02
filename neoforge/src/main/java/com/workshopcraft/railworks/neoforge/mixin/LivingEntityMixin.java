package com.workshopcraft.railworks.neoforge.mixin;

import com.workshopcraft.railworks.trolley.Berths;
import com.workshopcraft.railworks.trolley.Carriage;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    @Inject(method = "checkBedExists", at = @At("HEAD"), cancellable = true)
    private void railworks$berth(CallbackInfoReturnable<Boolean> cir) {
        if (Berths.asleep((Entity) (Object) this)) cir.setReturnValue(true);
    }

    @Inject(method = "setPosToBed", at = @At("HEAD"), cancellable = true)
    private void railworks$stayAboard(BlockPos pos, CallbackInfo ci) {
        if (((Entity) (Object) this).getVehicle() instanceof Carriage) ci.cancel();
    }
}
