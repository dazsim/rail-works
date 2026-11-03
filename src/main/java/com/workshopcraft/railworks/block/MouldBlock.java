package com.workshopcraft.railworks.block;

import com.mojang.serialization.MapCodec;
import com.workshopcraft.railworks.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

/** A moulding box. The rail bar box is two blocks long, the second half (TAIL) lying in the FACING direction. */
public class MouldBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<MouldStage> STAGE = EnumProperty.create("stage", MouldStage.class);
    public static final BooleanProperty TAIL = BooleanProperty.create("tail");

    public enum Kind {
        RAIL(BrickFurnaceBlockEntity.INGOT, true,
                cast(() -> ModContent.RAIL_PATTERN, () -> new ItemStack(ModContent.RAIL_BAR)),
                cast(() -> ModContent.AXLE_PATTERN, () -> new ItemStack(ModContent.AXLE)),
                cast(() -> ModContent.CARRIAGE_AXLE_PATTERN, () -> new ItemStack(ModContent.ROUGH_CARRIAGE_AXLE)),
                cast(() -> ModContent.PLATE_PATTERN, () -> new ItemStack(ModContent.STEEL_PLATE)),
                cast(() -> ModContent.SPRING_PATTERN, () -> new ItemStack(ModContent.BOGIE_SPRING)),
                cast(() -> ModContent.RIVET_PATTERN, () -> new ItemStack(ModContent.RIVETS, 16)),
                cast(() -> ModContent.BEAM_PATTERN, () -> new ItemStack(ModContent.STEEL_BEAM), 2)),
        INGOT(4 * BrickFurnaceBlockEntity.INGOT, false,
                cast(() -> ModContent.INGOT_PATTERN, () -> new ItemStack(ModContent.STEEL_INGOT, 4)),
                cast(() -> ModContent.WHEEL_PATTERN, () -> new ItemStack(ModContent.WHEEL, 4)),
                cast(() -> ModContent.CARRIAGE_WHEEL_PATTERN, () -> new ItemStack(ModContent.ROUGH_CARRIAGE_WHEEL, 2)),
                cast(() -> ModContent.AXLE_BOX_PATTERN, () -> new ItemStack(ModContent.ROUGH_AXLE_BOX, 4)),
                cast(() -> ModContent.BUFFER_PATTERN, () -> new ItemStack(ModContent.BUFFER_STOCK, 4)),
                cast(() -> ModContent.engine("engine_wheel_pattern"), () -> new ItemStack(ModContent.engine("rough_engine_wheel"))),
                cast(() -> ModContent.engine("cylinder_pattern"), () -> new ItemStack(ModContent.engine("rough_cylinder"))),
                cast(() -> ModContent.engine("chimney_pattern"), () -> new ItemStack(ModContent.engine("engine_chimney"))),
                cast(() -> ModContent.engine("smokebox_door_pattern"), () -> new ItemStack(ModContent.engine("smokebox_door"))));

        public final int need;
        public final boolean longBox;
        private final Cast[] casts;

        Kind(int need, boolean longBox, Cast... casts) {
            this.need = need;
            this.longBox = longBox;
            this.casts = casts;
        }

        /** Index 0 is the box's own pattern; the rest were added later and keep their order for old saves. */
        public Item pattern(int i) {
            return casts[Math.floorMod(i, casts.length)].pattern.get();
        }

        /** Molten steel a cast takes; most take what the box holds, a beam twice a bar's. */
        public int need(int i) {
            return need * casts[Math.floorMod(i, casts.length)].times;
        }

        public ItemStack result(int i) {
            return casts[Math.floorMod(i, casts.length)].result.get();
        }

        public int indexOf(ItemStack stack) {
            for (int i = 0; i < casts.length; i++) if (stack.is(casts[i].pattern.get())) return i;
            return -1;
        }
    }

    private record Cast(Supplier<Item> pattern, Supplier<ItemStack> result, int times) {}

    private static Cast cast(Supplier<Item> pattern, Supplier<ItemStack> result) {
        return new Cast(pattern, result, 1);
    }

    private static Cast cast(Supplier<Item> pattern, Supplier<ItemStack> result, int times) {
        return new Cast(pattern, result, times);
    }

    private static final Map<Direction, VoxelShape[]> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            VoxelShape[] s = new VoxelShape[6];
            for (int i = 0; i < 6; i++) {
                boolean closed = i % 2 == 1;
                int kind = i / 2;
                double h = closed ? 11 : 5;
                double z0 = kind == 1 ? 0 : 2, z1 = kind == 2 ? 16 : 14;
                s[i] = rotated(d, 2, z0, 14, z1, h);
            }
            SHAPES.put(d, s);
        }
    }

    private static VoxelShape rotated(Direction d, double x0, double z0, double x1, double z1, double h) {
        return switch (d) {
            case SOUTH -> Block.box(16 - x1, 0, 16 - z1, 16 - x0, h, 16 - z0);
            case EAST -> Block.box(16 - z1, 0, x0, 16 - z0, h, x1);
            case WEST -> Block.box(z0, 0, 16 - x1, z1, h, 16 - x0);
            default -> Block.box(x0, 0, z0, x1, h, z1);
        };
    }

    public final Kind kind;
    private final MapCodec<MouldBlock> codec;

    public MouldBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        this.codec = simpleCodec(p -> new MouldBlock(kind, p));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STAGE, MouldStage.EMPTY).setValue(TAIL, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STAGE, TAIL);
    }

    public static BlockPos mainPos(BlockPos pos, BlockState state) {
        return state.getValue(TAIL) ? pos.relative(state.getValue(FACING).getOpposite()) : pos;
    }

    public static BlockPos tailPos(BlockPos main, BlockState state) {
        return main.relative(state.getValue(FACING));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(TAIL) ? null : new MouldBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel server) || type != ModContent.MOULD_ENTITY) return null;
        return (l, pos, s, be) -> ((MouldBlockEntity) be).serverTick(server, pos, s);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        if (kind.longBox) {
            BlockPos tail = context.getClickedPos().relative(facing);
            Level level = context.getLevel();
            if (!level.getBlockState(tail).canBeReplaced(context) || !level.getWorldBorder().isWithinBounds(tail)) return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, by, itemStack);
        if (kind.longBox && !level.isClientSide()) {
            level.setBlock(tailPos(pos, state), state.setValue(TAIL, true), 3);
            level.updateNeighborsAt(pos, Blocks.AIR);
            state.updateNeighbourShapes(level, pos, 3);
        }
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir,
                                     BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (kind.longBox && !MouldBlockEntity.moving) {
            Direction toOther = state.getValue(TAIL) ? state.getValue(FACING).getOpposite() : state.getValue(FACING);
            if (dir == toOther) {
                return neighbourState.is(this) && neighbourState.getValue(TAIL) != state.getValue(TAIL)
                        ? state.setValue(STAGE, neighbourState.getValue(STAGE))
                        : Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, level, ticks, pos, dir, neighbourPos, neighbourState, random);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (kind.longBox && !level.isClientSide() && player.preventsBlockDrops() && state.getValue(TAIL)) {
            BlockPos main = mainPos(pos, state);
            BlockState m = level.getBlockState(main);
            if (m.is(this) && !m.getValue(TAIL)) {
                level.setBlock(main, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, main, Block.getId(m));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(mainPos(pos, state)) instanceof MouldBlockEntity be) return be.useItem(stack, player, hand);
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(mainPos(pos, state)) instanceof MouldBlockEntity be) return be.push(player);
        return InteractionResult.PASS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int kindIndex = !kind.longBox ? 0 : state.getValue(TAIL) ? 2 : 1;
        return SHAPES.get(state.getValue(FACING))[kindIndex * 2 + 1];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int kindIndex = !kind.longBox ? 0 : state.getValue(TAIL) ? 2 : 1;
        return SHAPES.get(state.getValue(FACING))[kindIndex * 2 + (state.getValue(STAGE).closed() ? 1 : 0)];
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        MouldStage stage = state.getValue(STAGE);
        if (state.getValue(TAIL) || (stage != MouldStage.HOT && stage != MouldStage.FILLING)) return;
        double x = pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.5;
        double z = pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.5;
        level.addParticle(stage == MouldStage.HOT ? ParticleTypes.CLOUD : ParticleTypes.SMOKE, x, pos.getY() + 0.75, z, 0.0, 0.04, 0.0);
        if (stage == MouldStage.HOT && random.nextInt(3) == 0) level.addParticle(ParticleTypes.WHITE_SMOKE, x, pos.getY() + 0.8, z, 0.0, 0.05, 0.0);
    }
}
