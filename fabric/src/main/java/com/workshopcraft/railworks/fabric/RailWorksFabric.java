package com.workshopcraft.railworks.fabric;

import com.workshopcraft.railworks.fluid.CreosoteFluid;
import com.workshopcraft.railworks.fluid.MoltenSteelFluid;
import com.workshopcraft.railworks.registry.ModContent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.registry.FuelValueEvents;

public class RailWorksFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ModContent.registerAllDirect(new CreosoteFluid.Source(), new CreosoteFluid.Flowing(), new MoltenSteelFluid.Source(), new MoltenSteelFluid.Flowing());
        FuelValueEvents.BUILD.register((builder, context) -> builder.add(ModContent.COKE, 3200));
    }
}
