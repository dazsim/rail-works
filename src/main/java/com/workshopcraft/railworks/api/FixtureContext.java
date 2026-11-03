package com.workshopcraft.railworks.api;

import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Where a fixture sits and what Rail Works gives it there. Server side only. */
public interface FixtureContext {
    FixtureType type();

    /** The carriage entity. Its id stays the same across saves. */
    Entity carriage();

    default UUID carriageId() {
        return carriage().getUUID();
    }

    /** The slot index within the carriage layout, stable for a given Rail Works version. */
    int slot();

    ServerLevel level();

    /** The middle of the fixture's space, in the world, where it is this tick. */
    Vec3 position();

    /** A point in half bay pixels (x along, y up, z aisle 0 to wall 10) in the world. */
    Vec3 toWorld(double x, double y, double z);

    /** Carriage speed along the track in blocks a second, signed. */
    double speed();

    /** The fixture's items, {@link FixtureType#inventorySize()} slots, saved with the carriage. */
    Container items();

    /** Fitted, the carriage alive, and the player within 4 blocks of it in the carriage's own frame. */
    boolean stillValid(Player player);

    /** Shows the lit boxes full bright and tells the client; cheap to call every tick. */
    void setLit(boolean lit);

    boolean lit();

    /** Marks the carriage to save. Item changes do this already. */
    void setChanged();

    /** The power network this fixture is on; {@link CarriagePowerNetwork#NONE} until the power rail exists. */
    CarriagePowerNetwork power();

    RandomSource random();

    /** The part item's name. */
    Component partName();

    /** Opens {@link Fixture#menu} for the player; false when there is none. */
    boolean openMenu(Player player);
}
