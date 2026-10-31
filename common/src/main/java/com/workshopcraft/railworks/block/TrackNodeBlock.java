package com.workshopcraft.railworks.block;

import com.mojang.serialization.MapCodec;
import com.workshopcraft.railworks.registry.ModContent;
import com.workshopcraft.railworks.registry.ModTags;
import com.workshopcraft.railworks.track.TrackActions;
import com.workshopcraft.railworks.track.TrackGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** A survey stake, or once its track is live, the hidden node the track hangs from. Drawn by its block entity. */
public class TrackNodeBlock extends BaseEntityBlock {
    public static final MapCodec<TrackNodeBlock> CODEC = simpleCodec(TrackNodeBlock::new);
    public static final BooleanProperty STAKE = BooleanProperty.create("stake");
    private static final VoxelShape POST = Block.box(6.5, 0, 6.5, 9.5, 14, 9.5);
    private static final VoxelShape NUB = Block.box(4, 0, 4, 12, 4, 12);

    public TrackNodeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STAKE, true));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAKE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(STAKE)) return POST;
        return context.isHoldingItem(ModContent.HAMMER) || context.isHoldingItem(ModContent.SURVEY_STAKE) ? NUB : Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrackNodeBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        if (!(level instanceof ServerLevel sl)) return;
        if (by instanceof Player p && TrackActions.joinNearEnd(sl, p, pos)) return;
        TrackGraph g = TrackGraph.get(sl);
        TrackGraph.Node n = g.addNode(pos, by == null ? 0 : TrackActions.headingFromYaw(by.getYRot()));
        n.stake = true;
        g.changed(sl, n);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(ModTags.HAMMERS)) {
            if (level instanceof ServerLevel sl) return TrackActions.hammerNode(sl, player, pos, state, hit);
            return InteractionResult.SUCCESS;
        }
        if (stack.is(ModContent.SURVEY_STAKE) && !state.getValue(STAKE)) {
            if (level instanceof ServerLevel sl) {
                TrackActions.restake(sl, player, pos, state);
                stack.consume(1, player);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }
}
