package com.workshopcraft.railworks.neoforge;

import com.workshopcraft.railworks.RailWorks;
import com.workshopcraft.railworks.client.BrickFurnaceScreen;
import com.workshopcraft.railworks.client.CokeOvenScreen;
import com.workshopcraft.railworks.client.RailWorksClient;
import com.workshopcraft.railworks.client.MachineRenderer;
import com.workshopcraft.railworks.client.TrackRenderer;
import com.workshopcraft.railworks.registry.ModContent;
import com.workshopcraft.railworks.client.ClientTrack;
import com.workshopcraft.railworks.track.TrackSyncPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import com.workshopcraft.railworks.client.WorkPieceRenderer;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
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
        modBus.addListener((RegisterClientPayloadHandlersEvent e) -> e.register(TrackSyncPayload.TYPE, (p, ctx) -> ClientTrack.receive(p.tag())));
        modBus.addListener((RegisterRenderPipelinesEvent e) -> {
            e.registerPipeline(ClientTrack.SOLID);
            e.registerPipeline(ClientTrack.GLASS);
        });
        NeoForge.EVENT_BUS.addListener((SubmitCustomGeometryEvent e) -> ClientTrack.submit(e.getSubmitNodeCollector(), e.getLevelRenderState()));
        modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
            e.registerBlockEntityRenderer(ModContent.TRACK_NODE_ENTITY, TrackRenderer::new);
            e.registerBlockEntityRenderer(ModContent.MACHINE_ENTITY, MachineRenderer::new);
            e.registerEntityRenderer(ModContent.HAND_TROLLEY_ENTITY, com.workshopcraft.railworks.client.TrolleyRenderer::new);
        });
        modBus.addListener((RegisterSpecialModelRendererEvent e) -> e.register(WorkPieceRenderer.ID, WorkPieceRenderer.Unbaked.MAP_CODEC));
        com.workshopcraft.railworks.client.TrolleyClient.init();
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
