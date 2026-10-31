package com.workshopcraft.railworks.fabric;

import com.workshopcraft.railworks.client.BrickFurnaceScreen;
import com.workshopcraft.railworks.client.ClientTrack;
import com.workshopcraft.railworks.track.TrackSyncPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import com.workshopcraft.railworks.client.CokeOvenScreen;
import com.workshopcraft.railworks.client.RailWorksClient;
import com.workshopcraft.railworks.client.TrackRenderer;
import com.workshopcraft.railworks.registry.ModContent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public class RailWorksFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(ModContent.COKE_OVEN_MENU, CokeOvenScreen::new);
        MenuScreens.register(ModContent.BRICK_FURNACE_MENU, BrickFurnaceScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(TrackSyncPayload.TYPE, (p, ctx) -> ClientTrack.receive(p.tag()));
        LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> ClientTrack.submit(ctx.submitNodeCollector(), ctx.levelState()));
        BlockEntityRenderers.register(ModContent.TRACK_NODE_ENTITY, TrackRenderer::new);
        FluidRenderingRegistry.register(ModContent.CREOSOTE, ModContent.FLOWING_CREOSOTE, RailWorksClient.CREOSOTE_MODEL);
        FluidRenderingRegistry.register(ModContent.MOLTEN_STEEL, ModContent.FLOWING_MOLTEN_STEEL, RailWorksClient.MOLTEN_STEEL_MODEL);
    }
}
