package com.workshopcraft.railworks.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jspecify.annotations.Nullable;

public class EngineRenderState extends EntityRenderState {
    public float heading, pitch, spin;
    public String key = "";
    public int wheels, paint, reg, rev, coal;
    public boolean rods, controls, bunker, brake, lit;
    public CarriageModel.@Nullable Built built, ghost;
}
