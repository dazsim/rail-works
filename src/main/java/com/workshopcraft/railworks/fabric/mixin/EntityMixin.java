package com.workshopcraft.railworks.fabric.mixin;

import com.workshopcraft.railworks.trolley.CarriageBody;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Carriage bodies are solid: whatever moves is clipped against the parts fitted to them. */
@Mixin(Entity.class)
abstract class EntityMixin {
    @Inject(method = "collide", at = @At("RETURN"), cancellable = true)
    private void railworks$carriages(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
        Vec3 was = cir.getReturnValue();
        Vec3 now = CarriageBody.collide((Entity) (Object) this, was);
        if (now != was) cir.setReturnValue(now);
    }
}
