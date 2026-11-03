package com.workshopcraft.railworks;

import net.minecraft.resources.Identifier;

public final class RailWorks {
    public static final String MOD_ID = "railworks";

    private RailWorks() {}

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
