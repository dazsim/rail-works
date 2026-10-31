package com.workshopcraft.railworks.block;

import com.mojang.serialization.MapCodec;
import com.workshopcraft.railworks.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A hand cranked machine. The lathe is three blocks across the player's view (0 headstock on the left, 1 main, 2 tailstock),
 * the pillar drill two blocks tall (0 main, 1 head). The block entity sits on the main part and draws the whole machine.
 */
public class MachineBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 2);

    public enum Kind {
        LATHE(1, 0, 3), DRILL(0, 1, 2);

        public final int main, crank, parts;

        Kind(int main, int crank, int parts) {
            this.main = main;
            this.crank = crank;
            this.parts = parts;
        }
    }

    private static final VoxelShape LATHE_X = Block.box(0, 0, 1, 16, 16, 15);
    private static final VoxelShape LATHE_Z = Block.box(1, 0, 0, 15, 16, 16);
    private static final VoxelShape DRILL_BASE = Block.box(2, 0, 2, 14, 16, 14);
    private static final VoxelShape DRILL_HEAD = Block.box(3, 0, 3, 13, 12, 13);

    public final Kind kind;
    private final MapCodec<MachineBlock> codec;

    public MachineBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        this.codec = simpleCodec(p -> new MachineBlock(kind, p));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, kind.main));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    public BlockPos partPos(BlockPos main, Direction facing, int part) {
        if (kind == Kind.DRILL) return main.above(part);
        return part == 0 ? main.relative(facing.getCounterClockWise()) : part == 2 ? main.relative(facing.getClockWise()) : main;
    }

    public BlockPos mainPos(BlockPos pos, BlockState state) {
        int part = state.getValue(PART);
        if (kind == Kind.DRILL) return pos.below(part);
        Direction f = state.getValue(FACING);
        return part == 0 ? pos.relative(f.getClockWise()) : part == 2 ? pos.relative(f.getCounterClockWise()) : pos;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == kind.main ? new MachineBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModContent.MACHINE_ENTITY) return null;
        if (level instanceof ServerLevel server) return (l, pos, s, be) -> ((MachineBlockEntity) be).serverTick(server);
        return (l, pos, s, be) -> ((MachineBlockEntity) be).clientTick();
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        Level level = context.getLevel();
        BlockPos main = context.getClickedPos();
        for (int i = 0; i < kind.parts; i++) {
            if (i == kind.main) continue;
            BlockPos p = partPos(main, facing, i);
            if (!level.getBlockState(p).canBeReplaced(context) || !level.getWorldBorder().isWithinBounds(p) || p.getY() > level.getMaxY()) return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, by, itemStack);
        if (level.isClientSide()) return;
        for (int i = 0; i < kind.parts; i++) {
            if (i != kind.main) level.setBlock(partPos(pos, state.getValue(FACING), i), state.setValue(PART, i), 3);
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                     BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        int part = state.getValue(PART);
        Direction f = state.getValue(FACING);
        BlockPos main = mainPos(pos, state);
        for (int i = 0; i < kind.parts; i++) {
            if (i == part || part != kind.main && i != kind.main) continue;
            if (partPos(main, f, i).equals(neighbourPos)
                    && !(neighbourState.is(this) && neighbourState.getValue(PART) == i && neighbourState.getValue(FACING) == f)) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, level, ticks, pos, dir, neighbourPos, neighbourState, random);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.preventsBlockDrops() && state.getValue(PART) != kind.main) {
            BlockPos main = mainPos(pos, state);
            BlockState m = level.getBlockState(main);
            if (m.is(this) && m.getValue(PART) == kind.main) {
                level.setBlock(main, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, main, Block.getId(m));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(mainPos(pos, state)) instanceof MachineBlockEntity be) return be.useItem(stack, player);
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(mainPos(pos, state)) instanceof MachineBlockEntity be) return be.crank(player, state.getValue(PART) == kind.crank);
        return InteractionResult.PASS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (kind == Kind.LATHE) return state.getValue(FACING).getAxis() == Direction.Axis.Z ? LATHE_X : LATHE_Z;
        return state.getValue(PART) == 0 ? DRILL_BASE : DRILL_HEAD;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
