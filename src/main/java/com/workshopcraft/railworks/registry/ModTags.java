package com.workshopcraft.railworks.registry;

import com.workshopcraft.railworks.RailWorks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

public final class ModTags {
    public static final TagKey<Item> HAMMERS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "tools/hammer"));
    public static final TagKey<Item> SOAKABLE_LOGS = TagKey.create(Registries.ITEM, RailWorks.id("soakable_logs"));
    public static final TagKey<Item> COKE_OVEN_INPUT = TagKey.create(Registries.ITEM, RailWorks.id("coke_oven_input"));
    public static final TagKey<Item> FURNACE_IRON = TagKey.create(Registries.ITEM, RailWorks.id("brick_furnace_iron"));
    public static final TagKey<Item> FURNACE_FUEL = TagKey.create(Registries.ITEM, RailWorks.id("brick_furnace_fuel"));
    public static final TagKey<Item> MOULD_SAND = TagKey.create(Registries.ITEM, Identifier.withDefaultNamespace("sand"));
    public static final TagKey<Fluid> MOLTEN_STEEL = TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("c", "molten_steel"));

    private ModTags() {}
}
