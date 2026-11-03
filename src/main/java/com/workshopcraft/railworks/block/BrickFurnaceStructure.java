package com.workshopcraft.railworks.block;

import com.workshopcraft.railworks.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The brick furnace: a 3x3x5 hollow shaft of 40 fire bricks. Parts are indexed x + 3y + 15z in the furnace frame,
 * where the door is (1, 0, 0), z runs away from the door and the tap sits at (0, 1, 1) on the door's right.
 */
public final class BrickFurnaceStructure {
    public static final int DOOR_PART = 1;
    public static final int TAP_PART = 18;
    public static final int SIZE = 40;
    private static boolean dismantling;
    private static boolean creativeBreak;

    private BrickFurnaceStructure() {}

    public static boolean isPart(int part) {
        return !(part % 3 == 1 && part / 15 == 1);
    }

    private static Rotation rotation(Direction facing) {
        return switch (facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static BlockPos offset(int part) {
        return new BlockPos(part % 3 - 1, part / 3 % 5, part / 15);
    }

    public static BlockPos partPos(BlockPos door, Direction facing, int part) {
        return door.offset(offset(part).rotate(rotation(facing)));
    }

    public static BlockPos doorPos(BlockPos pos, Direction facing, int part) {
        return pos.subtract(offset(part).rotate(rotation(facing)));
    }

    public static BlockPos centre(BlockPos door, Direction facing) {
        return door.relative(facing.getOpposite());
    }

    public static Direction tapSide(Direction facing) {
        return facing.getCounterClockWise();
    }

    public static BlockPos tapPos(BlockPos door, Direction facing) {
        return partPos(door, facing, TAP_PART);
    }

    public static List<Integer> parts() {
        List<Integer> out = new ArrayList<>(SIZE);
        for (int i = 0; i < 45; i++) if (isPart(i)) out.add(i);
        return out;
    }

    /** Loose bricks everywhere (formed == false) with an open shaft, or formed parts everywhere but the door. */
    public static boolean bricksPresent(Level level, BlockPos door, Direction facing, boolean formed) {
        for (int part : parts()) {
            if (part == DOOR_PART) continue;
            BlockState s = level.getBlockState(partPos(door, facing, part));
            if (!s.is(formed ? ModContent.BRICK_FURNACE_PART : ModContent.FIRE_BRICKS)) return false;
        }
        if (!formed) {
            for (int y = 0; y < 5; y++) {
                if (level.getBlockState(centre(door, facing).above(y)).is(ModContent.FIRE_BRICKS)) return false;
            }
        }
        return true;
    }

    public static void setFormed(Level level, BlockPos door, Direction facing, boolean formed) {
        boolean was = dismantling;
        dismantling = true;
        try {
            for (int part : parts()) {
                if (part == DOOR_PART) continue;
                BlockPos p = partPos(door, facing, part);
                BlockState s = level.getBlockState(p);
                if (!s.is(formed ? ModContent.FIRE_BRICKS : ModContent.BRICK_FURNACE_PART)) continue;
                BlockState next = formed
                        ? ModContent.BRICK_FURNACE_PART.defaultBlockState().setValue(BrickFurnacePartBlock.PART, part).setValue(BrickFurnacePartBlock.FACING, facing)
                        : ModContent.FIRE_BRICKS.defaultBlockState();
                level.setBlock(p, next, 3);
            }
        } finally {
            dismantling = was;
        }
    }

    public static void unform(Level level, BlockPos door, Direction facing) {
        dismantling = true;
        try {
            setFormed(level, door, facing, false);
            level.setBlock(door, ModContent.FIRE_BRICKS.defaultBlockState(), 3);
        } finally {
            dismantling = false;
        }
    }

    public static void playerBreaking(boolean creative) {
        creativeBreak = creative;
    }

    /** Called when any part of a formed furnace is removed: clears the rest and drops all 40 bricks once. */
    public static void dismantle(ServerLevel level, BlockPos door, Direction facing, BlockPos removed) {
        if (dismantling) return;
        boolean drop = !creativeBreak;
        creativeBreak = false;
        dismantling = true;
        try {
            for (int part : parts()) {
                BlockPos p = partPos(door, facing, part);
                if (p.equals(removed)) continue;
                BlockState s = level.getBlockState(p);
                boolean ours = part == DOOR_PART ? s.is(ModContent.BRICK_FURNACE) : s.is(ModContent.BRICK_FURNACE_PART);
                if (ours) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
            if (drop) Block.popResource(level, centre(door, facing), new ItemStack(ModContent.FIRE_BRICKS, SIZE));
        } finally {
            dismantling = false;
        }
    }
}
