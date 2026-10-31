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
 * The beehive oven: a 3x3x3 of bricks without the 4 top corners (23 blocks), door in the middle of one side.
 * Parts are indexed x + 3y + 9z in the oven frame, where the door is (1, 1, 0) and z runs away from the door.
 */
public final class CokeOvenStructure {
    public static final int DOOR_PART = 4;
    public static final int SIZE = 23;
    private static boolean dismantling;
    private static boolean creativeBreak;

    private CokeOvenStructure() {}

    public static boolean isPart(int part) {
        int x = part % 3, y = part / 3 % 3, z = part / 9;
        return !(y == 2 && x != 1 && z != 1);
    }

    private static Rotation rotation(Direction facing) {
        return switch (facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public static BlockPos partPos(BlockPos door, Direction facing, int part) {
        return door.offset(new BlockPos(part % 3 - 1, part / 3 % 3 - 1, part / 9).rotate(rotation(facing)));
    }

    public static BlockPos doorPos(BlockPos pos, Direction facing, int part) {
        return pos.subtract(new BlockPos(part % 3 - 1, part / 3 % 3 - 1, part / 9).rotate(rotation(facing)));
    }

    public static BlockPos centre(BlockPos door, Direction facing) {
        return door.relative(facing.getOpposite());
    }

    public static List<Integer> parts() {
        List<Integer> out = new ArrayList<>(SIZE);
        for (int i = 0; i < 27; i++) if (isPart(i)) out.add(i);
        return out;
    }

    /** True when every part except the door is a brick that is free (formed == false) or already formed (formed == true). */
    public static boolean bricksPresent(Level level, BlockPos door, Direction facing, boolean formed) {
        for (int part : parts()) {
            if (part == DOOR_PART) continue;
            BlockState s = level.getBlockState(partPos(door, facing, part));
            if (!s.is(ModContent.COKE_OVEN_BRICKS) || s.getValue(CokeOvenBricksBlock.FORMED) != formed) return false;
        }
        return true;
    }

    public static void setFormed(Level level, BlockPos door, Direction facing, boolean formed) {
        for (int part : parts()) {
            if (part == DOOR_PART) continue;
            BlockPos p = partPos(door, facing, part);
            BlockState s = level.getBlockState(p);
            if (!s.is(ModContent.COKE_OVEN_BRICKS)) continue;
            BlockState next = formed
                    ? s.setValue(CokeOvenBricksBlock.FORMED, true).setValue(CokeOvenBricksBlock.PART, part).setValue(CokeOvenBricksBlock.FACING, facing)
                    : ModContent.COKE_OVEN_BRICKS.defaultBlockState();
            if (next != s) level.setBlock(p, next, 3);
        }
    }

    /** Turns the controller back into a brick and unforms the rest, without dropping anything. */
    public static void unform(Level level, BlockPos door, Direction facing) {
        dismantling = true;
        try {
            setFormed(level, door, facing, false);
            level.setBlock(door, ModContent.COKE_OVEN_BRICKS.defaultBlockState(), 3);
        } finally {
            dismantling = false;
        }
    }

    public static void playerBreaking(boolean creative) {
        creativeBreak = creative;
    }

    /** Called when any part of a formed oven is removed: clears the other parts and drops all the bricks once. */
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
                boolean ours = part == DOOR_PART ? s.is(ModContent.COKE_OVEN)
                        : s.is(ModContent.COKE_OVEN_BRICKS) && s.getValue(CokeOvenBricksBlock.FORMED);
                if (ours) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
            if (drop) Block.popResource(level, centre(door, facing), new ItemStack(ModContent.COKE_OVEN_BRICKS, SIZE));
        } finally {
            dismantling = false;
        }
    }
}
