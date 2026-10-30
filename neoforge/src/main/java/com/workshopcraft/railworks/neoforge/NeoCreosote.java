package com.workshopcraft.railworks.neoforge;

import com.workshopcraft.railworks.fluid.CreosoteFluid;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidType;

final class NeoCreosote {
    static final FluidType TYPE = new FluidType(FluidType.Properties.create()
            .descriptionId("block.railworks.creosote")
            .density(1100)
            .viscosity(3000)
            .canSwim(true)
            .canDrown(true)
            .canExtinguish(true)
            .canConvertToSource(false)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY));

    private NeoCreosote() {}

    static final class Source extends CreosoteFluid.Source {
        @Override
        public FluidType getFluidType() {
            return TYPE;
        }
    }

    static final class Flowing extends CreosoteFluid.Flowing {
        @Override
        public FluidType getFluidType() {
            return TYPE;
        }
    }
}
