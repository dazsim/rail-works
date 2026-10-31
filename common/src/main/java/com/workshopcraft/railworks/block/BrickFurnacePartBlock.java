package com.workshopcraft.railworks.block;

import com.workshopcraft.railworks.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
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

/** A brick of a formed furnace. Drops nothing itself; the furnace drops all its bricks when dismantled. */
public class BrickFurnacePartBlock extends Block implements WorldlyContainerHolder {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 44);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    /** Only used by the tap: 0 shut, 1 open, 2 pouring onto a box beside it, 3 pouring one block down. */
    public static final IntegerProperty TAP = IntegerProperty.create("tap", 0, 3);

    public BrickFurnacePartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, 0).setValue(FACING, Direction.NORTH).setValue(TAP, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, FACING, TAP);
    }

    static BlockPos door(BlockPos pos, BlockState state) {
        return BrickFurnaceStructure.doorPos(pos, state.getValue(FACING), state.getValue(PART));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BrickFurnaceShapes.get(state.getValue(PART), state.getValue(FACING));
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return Shapes.empty();
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(ModContent.FIRE_BRICKS);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        BlockPos door = door(pos, state);
        BlockState d = level.getBlockState(door);
        if (!(d.getBlock() instanceof BrickFurnaceBlock furnace)) return InteractionResult.PASS;
        if (state.getValue(PART) == BrickFurnaceStructure.TAP_PART && furnace.toggleTap(stack, level, door, player, hand)) return InteractionResult.SUCCESS;
        return furnace.useItemOn(stack, d, level, door, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos door = door(pos, state);
        BlockState d = level.getBlockState(door);
        if (d.getBlock() instanceof BrickFurnaceBlock furnace) return furnace.useWithoutItem(d, level, door, player, hit);
        return InteractionResult.PASS;
    }

    @Override
    public WorldlyContainer getContainer(BlockState state, LevelAccessor level, BlockPos pos) {
        if (level.getBlockEntity(door(pos, state)) instanceof BrickFurnaceBlockEntity be) return be;
        return new EmptyWorldly();
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BrickFurnaceStructure.playerBreaking(player.isCreative());
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        BlockPos door = door(pos, state);
        if (level.getBlockState(door).is(ModContent.BRICK_FURNACE)) BrickFurnaceStructure.dismantle(level, door, state.getValue(FACING), pos);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int tap = state.getValue(TAP);
        if (tap < 2) return;
        Direction side = BrickFurnaceStructure.tapSide(state.getValue(FACING));
        double x = pos.getX() + 0.5 + side.getStepX() * 1.03 + (random.nextDouble() - 0.5) * 0.1;
        double z = pos.getZ() + 0.5 + side.getStepZ() * 1.03 + (random.nextDouble() - 0.5) * 0.1;
        double y = pos.getY() + (tap == 2 ? 0.6 : -0.4);
        level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0, 0.0, 0.0);
        if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.LAVA, x, y, z, 0.0, 0.0, 0.0);
        if (random.nextInt(8) == 0) level.playLocalSound(x, y, z, SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 0.3F, 1.2F, false);
    }

    static final class EmptyWorldly extends SimpleContainer implements WorldlyContainer {
        EmptyWorldly() {
            super(0);
        }

        @Override
        public int[] getSlotsForFace(Direction side) {
            return new int[0];
        }

        @Override
        public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction side) {
            return false;
        }

        @Override
        public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
            return false;
        }
    }
}
