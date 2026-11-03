package com.workshopcraft.railworks.block;

import com.mojang.serialization.MapCodec;
import com.workshopcraft.railworks.registry.ModContent;
import java.util.HashMap;
import java.util.Map;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * A hand cranked machine. The lathe is three blocks across the player's view (0 headstock on the left, 1 main, 2 tailstock)
 * with 3 to 5 above them, the pillar drill two blocks tall (0 main, 1 head). The block entity sits on the main part and draws
 * the whole machine. Shapes follow the model, and the crank wheel shape may reach into the next part.
 */
public class MachineBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 5);

    public enum Kind {
        // model space boxes in blocks from the main part's floor centre, x to the operator's right, z towards them
        LATHE(1, 6, new double[]{-1.56, 0.84, 0.44, -0.6, 1.8, 0.5},
                new double[]{-1.5, 0, -0.4375, 1.5, 1, 0.4375},
                new double[]{-1.43, 1, -0.28, -0.73, 1.69, 0.28},
                new double[]{-1.5, 1, -0.31, 1.5, 1.1, 0.31},
                new double[]{-0.73, 1, -0.3, 1.35, 1.72, 0.3},
                new double[]{-1.31, 1.09, -0.52, -0.85, 1.55, -0.28}),
        DRILL(0, 2, new double[]{-0.36, 1.215, -0.435, -0.295, 1.985, 0.335},
                new double[]{-0.375, 0, -0.375, 0.375, 1, 0.375},
                new double[]{-0.06, 1, -0.28, 0.06, 1.5, -0.16},
                new double[]{-0.14, 1.5, -0.3, 0.14, 2, 0.2},
                new double[]{-0.07, 1, 0.05, 0.07, 1.5, 0.19},
                new double[]{-0.37, 1.57, -0.08, 0.37, 1.63, -0.02},
                new double[]{0.26, 1.4, -0.25, 0.4, 1.8, 0.15}),
        ROLLS(1, 3, new double[]{0.65, 0.6, 0.42, 1.45, 1.4, 0.52},
                new double[]{-1.4, 0, -0.4, 1.4, 0.12, 0.4},
                new double[]{-1.35, 0.12, -0.32, -1.15, 1.3, 0.32},
                new double[]{1.15, 0.12, -0.32, 1.35, 1.3, 0.32},
                new double[]{-1.15, 0.65, -0.3, 1.15, 1.2, 0.3});

        public final int main, parts;
        final double[] crank;
        final double[][] body;

        Kind(int main, int parts, double[] crank, double[]... body) {
            this.main = main;
            this.parts = parts;
            this.crank = crank;
            this.body = body;
        }

        public String key() {
            return this == ROLLS ? "rolls" : this == DRILL ? "drill" : "lathe";
        }
    }

    public final Kind kind;
    private final MapCodec<MachineBlock> codec;
    private final Map<BlockState, VoxelShape> shapes = new HashMap<>();

    public MachineBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        this.codec = simpleCodec(p -> new MachineBlock(kind, p));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, kind.main));
        for (BlockState s : stateDefinition.getPossibleStates()) shapes.put(s, makeShape(s));
    }

    private static int cos(Direction f) {
        return f == Direction.NORTH ? 1 : f == Direction.SOUTH ? -1 : 0;
    }

    private static int sin(Direction f) {
        return f == Direction.WEST ? 1 : f == Direction.EAST ? -1 : 0;
    }

    private VoxelShape makeShape(BlockState state) {
        int part = state.getValue(PART);
        if (part >= kind.parts) return Shapes.block();
        Direction f = state.getValue(FACING);
        BlockPos d = partPos(BlockPos.ZERO, f, part);
        VoxelShape shape = Shapes.empty();
        for (double[] b : kind.body) shape = Shapes.or(shape, cell(b, f, d, true));
        double[] c = kind.crank;
        if (c[1] < d.getY() + 1 && c[4] > d.getY()) shape = Shapes.or(shape, cell(c, f, d, false));
        return shape.optimize();
    }

    /** One model box as a shape in the cell at offset d, cut to the cell, or for the crank only sideways. */
    private VoxelShape cell(double[] b, Direction f, BlockPos d, boolean clip) {
        int c = cos(f), s = sin(f);
        double x0 = b[0] * c + b[2] * s, x1 = b[3] * c + b[5] * s, z0 = -b[0] * s + b[2] * c, z1 = -b[3] * s + b[5] * c;
        double[] lo = {Math.min(x0, x1) + 0.5 - d.getX(), b[1] - d.getY(), Math.min(z0, z1) + 0.5 - d.getZ()};
        double[] hi = {Math.max(x0, x1) + 0.5 - d.getX(), b[4] - d.getY(), Math.max(z0, z1) + 0.5 - d.getZ()};
        for (int i = 0; i < 3; i++) {
            if (clip || i != 1) {
                lo[i] = Math.max(lo[i], 0);
                hi[i] = Math.min(hi[i], 1);
            }
        }
        if (hi[0] <= lo[0] || hi[1] <= lo[1] || hi[2] <= lo[2]) return Shapes.empty();
        return Shapes.box(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]);
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
        BlockPos p = main.above(part / 3);
        return part % 3 == 0 ? p.relative(facing.getCounterClockWise()) : part % 3 == 2 ? p.relative(facing.getClockWise()) : p;
    }

    public BlockPos mainPos(BlockPos pos, BlockState state) {
        int part = state.getValue(PART);
        if (kind == Kind.DRILL) return pos.below(part);
        Direction f = state.getValue(FACING);
        BlockPos p = pos.below(part / 3);
        return part % 3 == 0 ? p.relative(f.getClockWise()) : part % 3 == 2 ? p.relative(f.getCounterClockWise()) : p;
    }

    /** Whether a hit lands on the crank wheel, so any held item cranks instead of being used. */
    public boolean onCrank(BlockState state, BlockPos main, Vec3 hit) {
        Direction f = state.getValue(FACING);
        int c = cos(f), s = sin(f);
        double wx = hit.x - main.getX() - 0.5, wz = hit.z - main.getZ() - 0.5, y = hit.y - main.getY();
        double x = wx * c - wz * s, z = wx * s + wz * c, e = 1.0E-3;
        double[] b = kind.crank;
        return x > b[0] - e && x < b[3] + e && y > b[1] - e && y < b[4] + e && z > b[2] - e && z < b[5] + e;
    }

    /** Puts back any part cell that is empty, such as the lathe's upper cells on machines placed before they existed. */
    void fillParts(Level level, BlockPos main, BlockState state) {
        for (int i = 0; i < kind.parts; i++) {
            BlockPos p = partPos(main, state.getValue(FACING), i);
            if (i != kind.main && level.getBlockState(p).isAir() && p.getY() <= level.getMaxY()) level.setBlock(p, state.setValue(PART, i), 3);
        }
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
            if (i == part) continue;
            if (partPos(main, f, i).equals(neighbourPos) && (neighbourState.isAir() || neighbourState.is(this))
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
        BlockPos main = mainPos(pos, state);
        if (!(level.getBlockEntity(main) instanceof MachineBlockEntity be)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        boolean crank = onCrank(state, main, hit.getLocation());
        if (crank && be.job() != null) return be.crank(player, true);
        InteractionResult r = be.useItem(stack, player);
        return crank && r == InteractionResult.TRY_WITH_EMPTY_HAND ? be.crank(player, true) : r;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos main = mainPos(pos, state);
        if (level.getBlockEntity(main) instanceof MachineBlockEntity be) return be.crank(player, onCrank(state, main, hit.getLocation()));
        return InteractionResult.PASS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state);
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
