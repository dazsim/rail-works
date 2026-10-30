package com.workshopcraft.railworks.block;

import com.workshopcraft.railworks.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** The 3x3x3 oven: a shell of 26 bricks around a centre that may be hollow or brick, door in the middle of one side. */
public final class CokeOvenStructure {
    private CokeOvenStructure() {}

    public static BlockPos centre(BlockPos door, Direction facing) {
        return door.relative(facing.getOpposite());
    }

    public static List<BlockPos> shell(BlockPos centre) {
        List<BlockPos> out = new ArrayList<>(26);
        for (int x = -1; x <= 1; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -1; z <= 1; z++)
                    if (x != 0 || y != 0 || z != 0) out.add(centre.offset(x, y, z));
        return out;
    }

    /** True when every shell block except the door is a brick that is free (formed == false) or already ours (formed == true). */
    public static boolean bricksPresent(Level level, BlockPos door, Direction facing, boolean formed) {
        BlockPos c = centre(door, facing);
        for (BlockPos p : shell(c)) {
            if (p.equals(door)) continue;
            BlockState s = level.getBlockState(p);
            if (!s.is(ModContent.COKE_OVEN_BRICKS)) return false;
            if (!formed && s.getValue(CokeOvenBricksBlock.FORMED)) return false;
        }
        BlockState mid = level.getBlockState(c);
        return mid.isAir() || mid.is(ModContent.COKE_OVEN_BRICKS) && (formed || !mid.getValue(CokeOvenBricksBlock.FORMED));
    }

    public static void setFormed(Level level, BlockPos door, Direction facing, boolean formed) {
        BlockPos c = centre(door, facing);
        List<BlockPos> all = shell(c);
        all.add(c);
        for (BlockPos p : all) {
            if (p.equals(door)) continue;
            BlockState s = level.getBlockState(p);
            if (s.is(ModContent.COKE_OVEN_BRICKS) && s.getValue(CokeOvenBricksBlock.FORMED) != formed)
                level.setBlock(p, s.setValue(CokeOvenBricksBlock.FORMED, formed), 2);
        }
    }
}
