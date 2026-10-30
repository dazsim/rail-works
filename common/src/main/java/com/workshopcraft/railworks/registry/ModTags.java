package com.workshopcraft.railworks.registry;

import com.workshopcraft.railworks.RailWorks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ModTags {
    public static final TagKey<Item> HAMMERS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "tools/hammer"));
    public static final TagKey<Item> SOAKABLE_LOGS = TagKey.create(Registries.ITEM, RailWorks.id("soakable_logs"));
    public static final TagKey<Item> COKE_OVEN_INPUT = TagKey.create(Registries.ITEM, RailWorks.id("coke_oven_input"));

    private ModTags() {}
}
