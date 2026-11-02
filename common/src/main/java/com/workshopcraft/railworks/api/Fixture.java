package com.workshopcraft.railworks.api;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * One fitted fixture's behaviour, made by {@link FixtureType#factory()} for each carriage slot it sits in.
 * Everything runs on the server. Items live in {@link FixtureContext#items()}; Rail Works saves those, so
 * save and load are only for the fixture's own state, such as progress.
 */
public interface Fixture {
    /** Every server tick while the carriage is loaded, parked or moving. */
    default void tick(FixtureContext ctx) {}

    /** A player clicked it with an empty hand. Opens {@link #menu} when there is one. */
    default boolean use(FixtureContext ctx, Player player) {
        return ctx.openMenu(player);
    }

    /**
     * The menu to open, or null for none. Keep it valid only while {@link FixtureContext#stillValid} says so;
     * a menu built on {@link FixtureContext#items()} gets that for free.
     */
    default @Nullable AbstractContainerMenu menu(FixtureContext ctx, int containerId, Inventory inventory, Player player) {
        return null;
    }

    default Component title(FixtureContext ctx) {
        return ctx.partName();
    }

    default void save(ValueOutput out) {}

    default void load(ValueInput in) {}

    /** Just before the part comes off; its items drop straight after. */
    default void removed(FixtureContext ctx) {}
}
