package com.workshopcraft.railworks.neoforge;

import com.workshopcraft.railworks.fluid.MoltenSteelFluid;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidType;

final class NeoMoltenSteel {
    static final FluidType TYPE = new FluidType(FluidType.Properties.create()
            .descriptionId("block.railworks.molten_steel")
            .lightLevel(12)
            .density(3000)
            .viscosity(6000)
            .temperature(1700)
            .canSwim(false)
            .canDrown(false)
            .canExtinguish(false)
            .canHydrate(false)
            .canConvertToSource(false)
            .pathType(PathType.LAVA)
            .adjacentPathType(null)
            .motionScale(0.0023333333333333335D)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA));

    private NeoMoltenSteel() {}

    static final class Source extends MoltenSteelFluid.Source {
        @Override
        public FluidType getFluidType() {
            return TYPE;
        }
    }

    static final class Flowing extends MoltenSteelFluid.Flowing {
        @Override
        public FluidType getFluidType() {
            return TYPE;
        }
    }
}
