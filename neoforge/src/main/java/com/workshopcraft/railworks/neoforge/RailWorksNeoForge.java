package com.workshopcraft.railworks.neoforge;

import com.workshopcraft.railworks.RailWorks;
import com.workshopcraft.railworks.registry.ModContent;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

@Mod(RailWorks.MOD_ID)
public class RailWorksNeoForge {
    public RailWorksNeoForge(IEventBus modBus, ModContainer container) {
        modBus.addListener(RailWorksNeoForge::onRegister);
        modBus.addListener(RailWorksNeoForge::onCapabilities);
    }

    private static void onRegister(RegisterEvent e) {
        e.register(NeoForgeRegistries.Keys.FLUID_TYPES, h -> h.register(RailWorks.id("creosote"), NeoCreosote.TYPE));
        e.register(Registries.FLUID, h -> ModContent.registerFluids(h::register, new NeoCreosote.Source(), new NeoCreosote.Flowing()));
        e.register(Registries.BLOCK, h -> ModContent.registerBlocks(h::register));
        e.register(Registries.ITEM, h -> ModContent.registerItems(h::register));
        e.register(Registries.BLOCK_ENTITY_TYPE, h -> ModContent.registerBlockEntities(h::register));
        e.register(Registries.MENU, h -> ModContent.registerMenus(h::register));
        e.register(Registries.CREATIVE_MODE_TAB, h -> ModContent.registerCreativeTabs(h::register));
    }

    private static void onCapabilities(RegisterCapabilitiesEvent e) {
        e.registerBlockEntity(Capabilities.Item.BLOCK, ModContent.COKE_OVEN_ENTITY, WorldlyContainerWrapper::new);
    }
}
