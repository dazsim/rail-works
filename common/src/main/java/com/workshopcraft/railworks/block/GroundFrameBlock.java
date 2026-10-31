package com.workshopcraft.railworks.block;

import com.mojang.serialization.MapCodec;
import com.workshopcraft.railworks.registry.ModTags;
import com.workshopcraft.railworks.track.TrackActions;
import com.workshopcraft.railworks.track.TrackGraph;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** A one-lever ground frame working the nearest set of points. Redstone sets it reversed while powered. */
public class GroundFrameBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<GroundFrameBlock> CODEC = simpleCodec(GroundFrameBlock::new);
    public static final int REACH = 12;
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final BooleanProperty REVERSED = BooleanProperty.create("reversed");
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 6, 15);

    public GroundFrameBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false).setValue(REVERSED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, REVERSED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        if (!(level instanceof ServerLevel sl)) return;
        TrackGraph.Node toe = TrackActions.nearestPoints(sl, pos);
        if (level.hasNeighborSignal(pos)) {
            // placed against a live signal: take the reverse route now, as if the signal had just come on
            level.setBlock(pos, state.setValue(POWERED, true).setValue(REVERSED, true), 3);
            if (toe != null) {
                TrackGraph.get(sl).setRoute(sl, toe, 1);
                TrackActions.updateFrames(sl, toe);
            }
            return;
        }
        if (toe != null && (toe.route == 1) != state.getValue(REVERSED)) level.setBlock(pos, state.setValue(REVERSED, toe.route == 1), 3);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel sl) pull(sl, pos, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModTags.HAMMERS)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level instanceof ServerLevel sl) pull(sl, pos, player);
        return InteractionResult.SUCCESS;
    }

    private static void pull(ServerLevel level, BlockPos pos, Player player) {
        if (level.getBlockState(pos).getValue(POWERED)) {
            player.sendOverlayMessage(Component.translatable("railworks.track.frame_held").withStyle(ChatFormatting.GOLD));
            return;
        }
        TrackGraph.Node toe = TrackActions.nearestPoints(level, pos);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, 0.8F);
        if (toe == null) {
            player.sendOverlayMessage(Component.translatable("railworks.track.no_points").withStyle(ChatFormatting.RED));
            return;
        }
        TrackActions.throwPoints(level, TrackGraph.get(level), toe);
        player.sendOverlayMessage(Component.translatable("railworks.track.points", toe.route == 0 ? "A" : "B"));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        if (!(level instanceof ServerLevel sl)) return;
        boolean signal = level.hasNeighborSignal(pos);
        if (signal == state.getValue(POWERED)) return;
        level.setBlock(pos, state.setValue(POWERED, signal), 2);
        TrackGraph.Node toe = TrackActions.nearestPoints(sl, pos);
        if (toe == null) return;
        TrackGraph.get(sl).setRoute(sl, toe, signal ? 1 : 0);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, 0.8F);
        level.playSound(null, toe.pos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.4F, 1.4F);
        TrackActions.updateFrames(sl, toe);
    }
}
