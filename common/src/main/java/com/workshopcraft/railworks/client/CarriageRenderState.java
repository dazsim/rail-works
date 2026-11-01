package com.workshopcraft.railworks.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jspecify.annotations.Nullable;

public class CarriageRenderState extends EntityRenderState {
    public float heading, pitch;
    public String slots = "";
    public final float[] door = new float[6];
    public int coupled;
    /** The slots the held part would go in, empty when nothing is held. */
    public CarriageModel.@Nullable Built ghost;
}
