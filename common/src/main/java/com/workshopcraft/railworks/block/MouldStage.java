package com.workshopcraft.railworks.block;

import net.minecraft.util.StringRepresentable;

public enum MouldStage implements StringRepresentable {
    EMPTY, PATTERN, READY, FILLING, HOT, CAST;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public boolean closed() {
        return this == READY || this == FILLING || this == HOT;
    }
}
