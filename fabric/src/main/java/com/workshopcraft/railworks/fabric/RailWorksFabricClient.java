package com.workshopcraft.railworks.fabric;

import com.workshopcraft.railworks.client.BrickFurnaceScreen;
import com.workshopcraft.railworks.client.CokeOvenScreen;
import com.workshopcraft.railworks.client.RailWorksClient;
import com.workshopcraft.railworks.registry.ModContent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.minecraft.client.gui.screens.MenuScreens;

public class RailWorksFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(ModContent.COKE_OVEN_MENU, CokeOvenScreen::new);
        MenuScreens.register(ModContent.BRICK_FURNACE_MENU, BrickFurnaceScreen::new);
        FluidRenderingRegistry.register(ModContent.CREOSOTE, ModContent.FLOWING_CREOSOTE, RailWorksClient.CREOSOTE_MODEL);
        FluidRenderingRegistry.register(ModContent.MOLTEN_STEEL, ModContent.FLOWING_MOLTEN_STEEL, RailWorksClient.MOLTEN_STEEL_MODEL);
    }
}
