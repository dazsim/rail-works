package com.workshopcraft.railworks.neoforge;

import com.workshopcraft.railworks.RailWorks;
import com.workshopcraft.railworks.client.CokeOvenScreen;
import com.workshopcraft.railworks.client.RailWorksClient;
import com.workshopcraft.railworks.registry.ModContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = RailWorks.MOD_ID, dist = Dist.CLIENT)
public class RailWorksNeoForgeClient {
    public RailWorksNeoForgeClient(IEventBus modBus) {
        modBus.addListener((RegisterMenuScreensEvent e) -> e.register(ModContent.COKE_OVEN_MENU, CokeOvenScreen::new));
        modBus.addListener((RegisterFluidModelsEvent e) -> e.register(RailWorksClient.CREOSOTE_MODEL, ModContent.CREOSOTE, ModContent.FLOWING_CREOSOTE));
    }
}
