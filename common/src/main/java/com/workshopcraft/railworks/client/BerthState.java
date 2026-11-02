package com.workshopcraft.railworks.client;

/** Carried on a living render state: the head yaw of a berth sleeper, NaN for anyone else. */
public interface BerthState {
    float railworks$headYaw();

    void railworks$setHeadYaw(float yaw);
}
