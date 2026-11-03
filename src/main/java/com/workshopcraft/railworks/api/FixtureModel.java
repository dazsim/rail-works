package com.workshopcraft.railworks.api;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How a fixture looks: boxes painted from the carriage sheet, a block state drawn at 10/16 scale, or one of
 * Rail Works' own part models.
 */
public sealed interface FixtureModel {
    /**
     * Colours a box can take, from the carriage texture sheet. FIRE is the stove's flame and is best used for
     * lit boxes.
     */
    List<String> ZONES = List.of("CHOC", "CREAM", "BLACK", "RED", "STEEL", "BRIGHT", "PLATE", "CAST", "BRASS",
            "LINING", "TREATED", "TEAK", "FLOOR", "CANVAS", "CEILING", "MOQUETTE", "LAMP", "FIRE");

    /** A box in half bay pixels: x along, y up, z from the aisle face (0) to the wall (10). */
    record Box(String zone, float x0, float y0, float z0, float x1, float y1, float z1) {
        public Box {
            if (!ZONES.contains(zone)) throw new IllegalArgumentException("unknown zone " + zone);
            if (x0 < 0 || y0 < 0 || z0 < 0 || x1 > 16 || y1 > 16 || z1 > 10 || x0 >= x1 || y0 >= y1 || z0 >= z1)
                throw new IllegalArgumentException("box must sit inside 16 x 16 x 10 pixels");
        }
    }

    /** Boxes, and lit boxes drawn full bright only while the fixture says it is lit. */
    record Boxes(List<Box> boxes, List<Box> lit) implements FixtureModel {
        public Boxes {
            boxes = List.copyOf(boxes);
            lit = List.copyOf(lit);
        }
    }

    /** A block state's own model, the north face to the aisle. The supplier is read on first use. */
    record Block(Supplier<BlockState> state) implements FixtureModel {}

    /** One of the models Rail Works lays out itself, by part id. Not for other mods. */
    record Layout(String part) implements FixtureModel {}

    static BoxBuilder boxes() {
        return new BoxBuilder();
    }

    static FixtureModel block(Supplier<BlockState> state) {
        return new Block(state);
    }

    final class BoxBuilder {
        private final List<Box> boxes = new ArrayList<>(), lit = new ArrayList<>();

        public BoxBuilder box(String zone, float x0, float y0, float z0, float x1, float y1, float z1) {
            boxes.add(new Box(zone, x0, y0, z0, x1, y1, z1));
            return this;
        }

        /** Keep a lit box a little proud of the boxes behind it, 0.1 pixels is enough. */
        public BoxBuilder lit(String zone, float x0, float y0, float z0, float x1, float y1, float z1) {
            lit.add(new Box(zone, x0, y0, z0, x1, y1, z1));
            return this;
        }

        public Boxes build() {
            return new Boxes(boxes, lit);
        }
    }
}
