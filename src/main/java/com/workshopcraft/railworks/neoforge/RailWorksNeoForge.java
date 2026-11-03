package com.workshopcraft.railworks.neoforge;

import com.workshopcraft.railworks.RailWorks;
import com.workshopcraft.railworks.block.BrickFurnaceBlockEntity;
import com.workshopcraft.railworks.block.BrickFurnacePartBlock;
import com.workshopcraft.railworks.block.BrickFurnaceStructure;
import com.workshopcraft.railworks.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import com.workshopcraft.railworks.track.TrackSync;
import com.workshopcraft.railworks.track.TrackSyncPayload;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
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
        modBus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar("1").playToClient(TrackSyncPayload.TYPE, TrackSyncPayload.CODEC).playToClient(com.workshopcraft.railworks.trolley.TrainSyncPayload.TYPE, com.workshopcraft.railworks.trolley.TrainSyncPayload.CODEC)
                .playToClient(com.workshopcraft.railworks.trolley.AboardSyncPayload.TYPE, com.workshopcraft.railworks.trolley.AboardSyncPayload.CODEC)
                .playToServer(com.workshopcraft.railworks.trolley.AboardPayload.TYPE, com.workshopcraft.railworks.trolley.AboardPayload.CODEC, (p, ctx) -> {
                    if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sp) com.workshopcraft.railworks.trolley.Aboard.receive(sp, p);
                }));
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) com.workshopcraft.railworks.trolley.Aboard.login(sp);
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) com.workshopcraft.railworks.trolley.Aboard.logout(sp);
        });
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel l) TrackSync.tick(l);
        });
    }

    private static void onRegister(RegisterEvent e) {
        e.register(NeoForgeRegistries.Keys.FLUID_TYPES, h -> {
            h.register(RailWorks.id("creosote"), NeoCreosote.TYPE);
            h.register(RailWorks.id("molten_steel"), NeoMoltenSteel.TYPE);
        });
        e.register(Registries.FLUID, h -> ModContent.registerFluids(h::register, new NeoCreosote.Source(), new NeoCreosote.Flowing(),
                new NeoMoltenSteel.Source(), new NeoMoltenSteel.Flowing()));
        e.register(Registries.BLOCK, h -> ModContent.registerBlocks(h::register));
        e.register(Registries.ITEM, h -> ModContent.registerItems(h::register));
        e.register(Registries.BLOCK_ENTITY_TYPE, h -> ModContent.registerBlockEntities(h::register));
        e.register(Registries.MENU, h -> ModContent.registerMenus(h::register));
        e.register(Registries.ENTITY_TYPE, h -> ModContent.registerEntities(h::register));
        e.register(Registries.TICKET_TYPE, h -> ModContent.registerTicketTypes(h::register));
        e.register(Registries.CREATIVE_MODE_TAB, h -> ModContent.registerCreativeTabs(h::register));
    }

    private static void onCapabilities(RegisterCapabilitiesEvent e) {
        e.registerBlockEntity(Capabilities.Item.BLOCK, ModContent.COKE_OVEN_ENTITY, WorldlyContainerWrapper::new);
        e.registerBlockEntity(Capabilities.Item.BLOCK, ModContent.BRICK_FURNACE_ENTITY, WorldlyContainerWrapper::new);
        e.registerBlock(Capabilities.Item.BLOCK, (level, pos, state, be, side) -> {
            BlockPos door = BrickFurnaceStructure.doorPos(pos, state.getValue(BrickFurnacePartBlock.FACING), state.getValue(BrickFurnacePartBlock.PART));
            return level.getBlockEntity(door) instanceof BrickFurnaceBlockEntity furnace ? new WorldlyContainerWrapper(furnace, side) : null;
        }, ModContent.BRICK_FURNACE_PART);
    }
}
