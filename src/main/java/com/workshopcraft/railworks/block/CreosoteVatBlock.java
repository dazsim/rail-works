package com.workshopcraft.railworks.block;

import com.mojang.serialization.MapCodec;
import com.workshopcraft.railworks.registry.ModContent;
import com.workshopcraft.railworks.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;

/** Half of a two block soaking vat. The block that was hammered is the main half and holds the tank and logs. */
public class CreosoteVatBlock extends BaseEntityBlock {
    public static final MapCodec<CreosoteVatBlock> CODEC = simpleCodec(CreosoteVatBlock::new);
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    public static final BooleanProperty MAIN = BooleanProperty.create("main");
    public static final EnumProperty<Direction> PARTNER = EnumProperty.create("partner", Direction.class, Direction.Plane.HORIZONTAL);
    public static final IntegerProperty FLUID = IntegerProperty.create("fluid", 0, 4);
    public static final IntegerProperty LOGS = IntegerProperty.create("logs", 0, 4);
    public static final BooleanProperty TREATED = BooleanProperty.create("treated");

    private static final VoxelShape FLOOR = Block.box(0, 0, 0, 16, 2, 16);
    private static final Map<Direction, VoxelShape> WALLS = Map.of(
            Direction.NORTH, Block.box(0, 0, 0, 16, 16, 2),
            Direction.SOUTH, Block.box(0, 0, 14, 16, 16, 16),
            Direction.WEST, Block.box(0, 0, 0, 2, 16, 16),
            Direction.EAST, Block.box(14, 0, 0, 16, 16, 16));
    private final Function<BlockState, VoxelShape> shapes;

    public CreosoteVatBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false).setValue(MAIN, false).setValue(PARTNER, Direction.NORTH)
                .setValue(FLUID, 0).setValue(LOGS, 0).setValue(TREATED, false));
        shapes = getShapeForEachState(state -> {
            VoxelShape s = FLOOR;
            for (Map.Entry<Direction, VoxelShape> e : WALLS.entrySet()) {
                if (!state.getValue(FORMED) || state.getValue(PARTNER) != e.getKey()) s = Shapes.or(s, e.getValue());
            }
            return s;
        }, MAIN, FLUID, LOGS, TREATED);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED, MAIN, PARTNER, FLUID, LOGS, TREATED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.apply(state);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CreosoteVatBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel server) || type != ModContent.VAT_ENTITY || !state.getValue(MAIN)) return null;
        return (l, pos, s, be) -> ((CreosoteVatBlockEntity) be).serverTick(server, pos, s);
    }

    public static @Nullable CreosoteVatBlockEntity main(Level level, BlockPos pos, BlockState state) {
        if (!state.is(ModContent.CREOSOTE_VAT) || !state.getValue(FORMED)) return null;
        BlockPos at = state.getValue(MAIN) ? pos : pos.relative(state.getValue(PARTNER));
        return level.getBlockEntity(at) instanceof CreosoteVatBlockEntity vat && vat.getBlockState().getValue(MAIN) ? vat : null;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!state.getValue(FORMED)) {
            if (!stack.is(ModTags.HAMMERS)) return InteractionResult.TRY_WITH_EMPTY_HAND;
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            Direction first = player.getDirection();
            Direction[] order = {first, first.getClockWise(), first.getCounterClockWise(), first.getOpposite()};
            for (Direction d : order) {
                BlockState other = level.getBlockState(pos.relative(d));
                if (other.is(this) && !other.getValue(FORMED)) {
                    level.setBlock(pos.relative(d), other.setValue(FORMED, true).setValue(MAIN, false).setValue(PARTNER, d.getOpposite()), 3);
                    level.setBlock(pos, state.setValue(FORMED, true).setValue(MAIN, true).setValue(PARTNER, d), 3);
                    level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.5F, 1.4F);
                    stack.hurtAndBreak(1, player, hand);
                    player.sendOverlayMessage(Component.translatable("message.railworks.vat.formed"));
                    return InteractionResult.SUCCESS;
                }
            }
            player.sendOverlayMessage(Component.translatable("message.railworks.vat.incomplete"));
            return InteractionResult.SUCCESS;
        }
        CreosoteVatBlockEntity vat = main(level, pos, state);
        if (vat == null) return InteractionResult.TRY_WITH_EMPTY_HAND;
        return vat.useItem(stack, player, hand);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        CreosoteVatBlockEntity vat = main(level, pos, state);
        if (vat == null) return InteractionResult.PASS;
        return vat.takeLog(player);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!state.getValue(FORMED)) return;
        BlockPos other = pos.relative(state.getValue(PARTNER));
        BlockState o = level.getBlockState(other);
        if (o.is(this) && o.getValue(FORMED) && o.getValue(PARTNER) == state.getValue(PARTNER).getOpposite()) {
            if (level.getBlockEntity(other) instanceof CreosoteVatBlockEntity vat) vat.dropLogs();
            level.setBlock(other, defaultBlockState(), 3);
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(PARTNER, rotation.rotate(state.getValue(PARTNER)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(PARTNER, mirror.mirror(state.getValue(PARTNER)));
    }
}
