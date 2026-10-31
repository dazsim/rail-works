package com.workshopcraft.railworks.fabric;

import com.workshopcraft.railworks.fluid.CreosoteFluid;
import com.workshopcraft.railworks.fluid.MoltenSteelFluid;
import com.workshopcraft.railworks.registry.ModContent;
import com.workshopcraft.railworks.track.TrackSync;
import com.workshopcraft.railworks.track.TrackSyncPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.registry.FuelValueEvents;

public class RailWorksFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ModContent.registerAllDirect(new CreosoteFluid.Source(), new CreosoteFluid.Flowing(), new MoltenSteelFluid.Source(), new MoltenSteelFluid.Flowing());
        PayloadTypeRegistry.clientboundPlay().register(TrackSyncPayload.TYPE, TrackSyncPayload.CODEC);
        ServerTickEvents.END_LEVEL_TICK.register(TrackSync::tick);
        FuelValueEvents.BUILD.register((builder, context) -> builder.add(ModContent.COKE, 3200));
    }
}
