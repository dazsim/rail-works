package com.workshopcraft.railworks.block;

import com.workshopcraft.railworks.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.redstone.Orientation;
import org.jspecify.annotations.Nullable;

/** A source sets into Set Steel after 30 s, or at once when water touches it; the flowing edges just vanish. */
public class MoltenSteelBlock extends LiquidBlock {
    public static final int SET_TICKS = 600;

    public MoltenSteelBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }

    private static boolean touchesWater(Level level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            if (d != Direction.DOWN && level.getFluidState(pos.relative(d)).is(FluidTags.WATER)) return true;
        }
        return false;
    }

    private static void solidify(Level level, BlockPos pos, BlockState state) {
        level.setBlockAndUpdate(pos, state.getFluidState().isSource() ? ModContent.SET_STEEL.defaultBlockState() : Blocks.AIR.defaultBlockState());
        level.levelEvent(1501, pos, 0);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && touchesWater(level, pos)) {
            solidify(level, pos, state);
            return;
        }
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && state.getFluidState().isSource() && !oldState.is(this)) level.scheduleTick(pos, this, SET_TICKS);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!level.isClientSide() && touchesWater(level, pos)) {
            solidify(level, pos, state);
            return;
        }
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        if (state.getFluidState().isSource()) solidify(level, pos, state);
    }
}
