package com.workshopcraft.railworks.neoforge;

import com.workshopcraft.railworks.RailWorks;
import com.workshopcraft.railworks.client.BrickFurnaceScreen;
import com.workshopcraft.railworks.client.CokeOvenScreen;
import com.workshopcraft.railworks.client.RailWorksClient;
import com.workshopcraft.railworks.registry.ModContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.FogEnvironment;
import com.workshopcraft.railworks.client.MoltenSteelFog;

@Mod(value = RailWorks.MOD_ID, dist = Dist.CLIENT)
public class RailWorksNeoForgeClient {
    public RailWorksNeoForgeClient(IEventBus modBus) {
        modBus.addListener((RegisterMenuScreensEvent e) -> {
            e.register(ModContent.COKE_OVEN_MENU, CokeOvenScreen::new);
            e.register(ModContent.BRICK_FURNACE_MENU, BrickFurnaceScreen::new);
        });
        modBus.addListener((RegisterFluidModelsEvent e) -> {
            e.register(RailWorksClient.CREOSOTE_MODEL, ModContent.CREOSOTE, ModContent.FLOWING_CREOSOTE);
            e.register(RailWorksClient.MOLTEN_STEEL_MODEL, ModContent.MOLTEN_STEEL, ModContent.FLOWING_MOLTEN_STEEL);
        });
        modBus.addListener((RegisterClientExtensionsEvent e) -> e.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public void modifyFogRender(Camera camera, FogEnvironment environment, float renderDistance, float partialTick, FogData fog) {
                MoltenSteelFog.apply(camera, fog);
            }
        }, NeoMoltenSteel.TYPE));
    }
}
