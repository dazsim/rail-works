/**
 * Rail Works carriage fixtures: working parts such as machines, stoves and storage fitted into a carriage
 * the way a bench or a locker is.
 *
 * <p>Another mod adds a fixture by building a {@link com.workshopcraft.railworks.api.FixtureType} and passing it
 * to {@link com.workshopcraft.railworks.api.CarriageFixtures#register} during mod setup, before any world loads.
 * Touch this package only when Rail Works is present (for example behind a mod loaded check and a class that is
 * not loaded otherwise), so the fixture stays optional.
 *
 * <p>Rail Works handles the rest: the part item fits into a free half bay with a click, the hammer fixes it,
 * a sneak hammer takes it off again and drops what it holds, the items save and load with the carriage, menus
 * close when the part goes or the player walks more than 4 blocks away inside the carriage, and the fixture
 * ticks every server tick while its carriage is loaded, moving or not.
 *
 * <p>Space: each half bay gives a fixture 16 pixels along the carriage, 16 up from the floor and 10 deep from
 * the aisle face (z 0) back to the wall (z 10). A block model is shrunk to 10/16 so a whole block fits, with
 * its north face to the aisle.
 *
 * <p>Power: {@link com.workshopcraft.railworks.api.FixturePower} declares what a fixture uses or makes and
 * {@link com.workshopcraft.railworks.api.CarriagePowerNetwork} is the shared network it will draw from. For now
 * every carriage hands out {@link com.workshopcraft.railworks.api.CarriagePowerNetwork#NONE}; the LV rail
 * along the outer walls comes later, first per carriage and then per train through couplings.
 */
@NullMarked
package com.workshopcraft.railworks.api;

import org.jspecify.annotations.NullMarked;
