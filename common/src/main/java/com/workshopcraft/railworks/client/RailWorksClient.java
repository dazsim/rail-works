package com.workshopcraft.railworks.client;

import com.workshopcraft.railworks.RailWorks;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.resources.model.sprite.Material;

public final class RailWorksClient {
    public static final FluidModel.Unbaked CREOSOTE_MODEL = new FluidModel.Unbaked(
            new Material(RailWorks.id("block/creosote_still")), new Material(RailWorks.id("block/creosote_flow")), null, null);

    private RailWorksClient() {}
}
