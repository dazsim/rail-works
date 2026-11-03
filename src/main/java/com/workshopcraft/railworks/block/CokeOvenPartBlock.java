package com.workshopcraft.railworks.block;

import com.workshopcraft.railworks.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A brick of a formed oven. Has no item and drops nothing; the oven drops all its bricks when dismantled. */
public class CokeOvenPartBlock extends Block {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 26);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    public CokeOvenPartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, 0).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, FACING);
    }

    private static BlockPos door(BlockPos pos, BlockState state) {
        return CokeOvenStructure.doorPos(pos, state.getValue(FACING), state.getValue(PART));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CokeOvenShapes.get(state.getValue(PART), state.getValue(FACING));
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return Shapes.empty();
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(ModContent.COKE_OVEN_BRICKS);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        BlockPos door = door(pos, state);
        BlockState d = level.getBlockState(door);
        if (d.getBlock() instanceof CokeOvenBlock oven) return oven.useItemOn(stack, d, level, door, player, hand, hit);
        return InteractionResult.PASS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos door = door(pos, state);
        BlockState d = level.getBlockState(door);
        if (d.getBlock() instanceof CokeOvenBlock oven) return oven.useWithoutItem(d, level, door, player, hit);
        return InteractionResult.PASS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        CokeOvenStructure.playerBreaking(player.isCreative());
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        BlockPos door = door(pos, state);
        if (level.getBlockState(door).is(ModContent.COKE_OVEN)) CokeOvenStructure.dismantle(level, door, state.getValue(FACING), pos);
    }
}
