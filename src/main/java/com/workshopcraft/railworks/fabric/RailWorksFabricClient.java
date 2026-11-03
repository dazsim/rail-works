package com.workshopcraft.railworks.fabric;

import com.workshopcraft.railworks.client.BrickFurnaceScreen;
import com.workshopcraft.railworks.client.ClientTrack;
import com.workshopcraft.railworks.track.TrackSyncPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import com.workshopcraft.railworks.client.CokeOvenScreen;
import com.workshopcraft.railworks.client.RailWorksClient;
import com.workshopcraft.railworks.client.MachineRenderer;
import com.workshopcraft.railworks.client.TrackRenderer;
import com.workshopcraft.railworks.registry.ModContent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import com.workshopcraft.railworks.client.TrolleyClient;
import com.workshopcraft.railworks.client.TrolleyRenderer;

public class RailWorksFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(ModContent.COKE_OVEN_MENU, CokeOvenScreen::new);
        MenuScreens.register(ModContent.BRICK_FURNACE_MENU, BrickFurnaceScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(TrackSyncPayload.TYPE, (p, ctx) -> ClientTrack.receive(p.tag()));
        ClientPlayNetworking.registerGlobalReceiver(com.workshopcraft.railworks.trolley.TrainSyncPayload.TYPE, (p, ctx) -> com.workshopcraft.railworks.client.ClientTrains.receive(p));
        ClientPlayNetworking.registerGlobalReceiver(com.workshopcraft.railworks.trolley.AboardSyncPayload.TYPE, (p, ctx) -> com.workshopcraft.railworks.client.ClientAboard.receive(p));
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> com.workshopcraft.railworks.client.ClientTrains.tick());
        LevelRenderEvents.COLLECT_SUBMITS.register(ctx -> ClientTrack.submit(ctx.submitNodeCollector(), ctx.levelState()));
        BlockEntityRenderers.register(ModContent.TRACK_NODE_ENTITY, TrackRenderer::new);
        BlockEntityRenderers.register(ModContent.MACHINE_ENTITY, MachineRenderer::new);
        EntityRenderers.register(ModContent.HAND_TROLLEY_ENTITY, TrolleyRenderer::new);
        EntityRenderers.register(ModContent.BOGIE_ENTITY, com.workshopcraft.railworks.client.BogieRenderer::new);
        EntityRenderers.register(ModContent.CARRIAGE_ENTITY, com.workshopcraft.railworks.client.CarriageRenderer::new);
        EntityRenderers.register(ModContent.ENGINE_ENTITY, com.workshopcraft.railworks.client.EngineRenderer::new);
        TrolleyClient.init();
        FluidRenderingRegistry.register(ModContent.CREOSOTE, ModContent.FLOWING_CREOSOTE, RailWorksClient.CREOSOTE_MODEL);
        FluidRenderingRegistry.register(ModContent.MOLTEN_STEEL, ModContent.FLOWING_MOLTEN_STEEL, RailWorksClient.MOLTEN_STEEL_MODEL);
    }
}
