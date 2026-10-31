package com.workshopcraft.railworks.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Generated collision boxes for each part of the formed beehive oven, indexed by part, in pixels, facing north. */
final class CokeOvenShapes {
    private static final int[][] BOXES = {
            {4, 0, 8, 16, 16, 16, 8, 0, 0, 16, 16, 8},
            {0, 0, 4, 16, 16, 16},
            {0, 0, 0, 8, 16, 16, 8, 0, 8, 12, 16, 16},
            {4, 0, 8, 16, 8, 16, 4, 8, 12, 16, 16, 16, 8, 0, 0, 16, 12, 8, 8, 8, 8, 16, 16, 12, 12, 12, 0, 16, 16, 8},
            {0, 0, 0, 4, 16, 16, 4, 0, 4, 16, 16, 16, 4, 4, 0, 16, 16, 4, 12, 0, 0, 16, 4, 4},
            {0, 0, 0, 8, 12, 16, 0, 12, 0, 4, 16, 16, 4, 12, 8, 8, 16, 16, 8, 0, 8, 12, 8, 16, 8, 8, 12, 12, 16, 16},
            {},
            {0, 0, 8, 16, 8, 16, 0, 8, 12, 16, 12, 16, 4, 0, 4, 12, 4, 8},
            {},
            {0, 0, 0, 16, 16, 16},
            {0, 0, 0, 16, 16, 16},
            {0, 0, 0, 16, 16, 16},
            {0, 0, 0, 16, 8, 16, 0, 8, 4, 16, 12, 12, 4, 8, 0, 16, 16, 4, 4, 8, 12, 16, 16, 16, 4, 12, 4, 16, 16, 12},
            {0, 0, 0, 16, 16, 16},
            {0, 0, 0, 16, 8, 16, 0, 8, 0, 12, 16, 16, 12, 8, 4, 16, 12, 12},
            {4, 0, 4, 16, 4, 12, 8, 0, 0, 16, 8, 4, 8, 0, 12, 16, 8, 16, 8, 4, 4, 16, 8, 12, 12, 8, 0, 16, 12, 16},
            {0, 0, 0, 16, 16, 16},
            {0, 0, 0, 8, 8, 16, 0, 8, 0, 4, 12, 16, 8, 0, 4, 12, 4, 12},
            {4, 0, 0, 16, 16, 8, 8, 0, 8, 16, 16, 12},
            {0, 0, 0, 16, 16, 16},
            {0, 0, 0, 12, 16, 8, 0, 0, 8, 8, 16, 12},
            {4, 0, 0, 16, 8, 8, 4, 8, 0, 16, 16, 4, 8, 0, 8, 16, 8, 12, 8, 8, 4, 16, 16, 8, 12, 8, 8, 16, 16, 12},
            {0, 0, 0, 16, 8, 16, 0, 8, 0, 16, 16, 12, 4, 8, 12, 12, 12, 16},
            {0, 0, 0, 12, 8, 8, 0, 0, 8, 8, 8, 12, 0, 8, 0, 12, 16, 4, 0, 8, 4, 8, 16, 8, 0, 8, 8, 4, 16, 12},
            {},
            {0, 0, 0, 16, 8, 8, 0, 8, 0, 16, 12, 4, 4, 0, 8, 12, 4, 12},
            {}
    };
    private static final List<Map<Direction, VoxelShape>> SHAPES = new ArrayList<>();

    static {
        for (int[] b : BOXES) {
            VoxelShape s = Shapes.empty();
            for (int i = 0; i < b.length; i += 6) s = Shapes.or(s, Block.box(b[i], b[i + 1], b[i + 2], b[i + 3], b[i + 4], b[i + 5]));
            SHAPES.add(Shapes.rotateHorizontal(s.optimize()));
        }
    }

    private CokeOvenShapes() {}

    static VoxelShape get(int part, Direction facing) {
        return SHAPES.get(part).get(facing);
    }
}
