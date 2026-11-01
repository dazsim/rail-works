package com.workshopcraft.railworks.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class BogieRenderState extends EntityRenderState {
    public float heading, pitch, wheel;
    public int stage;
    public boolean brake;
    /** The tow bar ends, relative to the bogie, when it is towed. */
    public @Nullable Vec3 barFrom, barTo;
    /** The partner bogie, relative to this one, while a solebar is held over a pair. */
    public @Nullable Vec3 ghostTo;
}
