package com.workshopcraft.railworks.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import com.workshopcraft.railworks.trolley.CarriageLayout.Slot;
import org.jspecify.annotations.Nullable;

public class CarriageRenderState extends EntityRenderState {
    public float heading, pitch;
    public String slots = "", look = "";
    /** Fixtures drawn as blocks, with where each sits. */
    public final java.util.List<net.minecraft.client.renderer.block.BlockModelRenderState> blocks = new java.util.ArrayList<>();
    public final java.util.List<Slot> blockSlots = new java.util.ArrayList<>();
    public final float[] door = new float[6];
    public int coupled;
    /** The slots the held part would go in, empty when nothing is held. */
    public CarriageModel.@Nullable Built ghost;
}
