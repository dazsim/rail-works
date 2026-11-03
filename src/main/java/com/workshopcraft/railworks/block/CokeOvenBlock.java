package com.workshopcraft.railworks.block;

import com.mojang.serialization.MapCodec;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** The oven door and controller. Only exists while the oven is formed; breaking any part dismantles the whole oven. */
public class CokeOvenBlock extends BaseEntityBlock {
    public static final MapCodec<CokeOvenBlock> CODEC = simpleCodec(CokeOvenBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public CokeOvenBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CokeOvenBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel server) || type != ModContent.COKE_OVEN_ENTITY) return null;
        return (l, pos, s, be) -> ((CokeOvenBlockEntity) be).serverTick(server, pos, s);
    }

    @Override
    public InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (stack.is(Items.BUCKET) && level.getBlockEntity(pos) instanceof CokeOvenBlockEntity oven && oven.tank() >= CokeOvenBlockEntity.BUCKET) {
            if (!level.isClientSide()) {
                oven.drain(CokeOvenBlockEntity.BUCKET);
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(ModContent.CREOSOTE_BUCKET)));
                level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CokeOvenBlockEntity be) player.openMenu(be);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CokeOvenShapes.get(CokeOvenStructure.DOOR_PART, state.getValue(FACING));
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return Shapes.empty();
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        CokeOvenStructure.playerBreaking(player.isCreative());
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        CokeOvenStructure.dismantle(level, pos, state.getValue(FACING), pos);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;
        Direction facing = state.getValue(FACING);
        BlockPos c = CokeOvenStructure.centre(pos, facing);
        double tx = c.getX() + 0.5 + random.nextDouble() * 0.36 - 0.18;
        double tz = c.getZ() + 0.5 + random.nextDouble() * 0.36 - 0.18;
        double ty = pos.getY() + 2.75;
        level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, tx, ty, tz, 0.0, 0.05, 0.0);
        if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.LARGE_SMOKE, tx, ty + 0.1, tz, 0.0, 0.03, 0.0);
        if (random.nextInt(3) == 0) {
            double side = random.nextDouble() * 0.5 - 0.25;
            double out = 0.5 - 4.5 / 16.0;
            double dx = facing.getAxis() == Direction.Axis.X ? facing.getStepX() * out : side;
            double dz = facing.getAxis() == Direction.Axis.Z ? facing.getStepZ() * out : side;
            double y = pos.getY() - 1 + (3.5 + random.nextDouble() * 3.5) / 16.0;
            level.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + 0.5 + dx, y, pos.getZ() + 0.5 + dz, 0.0, 0.0, 0.0);
        }
        if (random.nextDouble() < 0.1) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() - 0.7, pos.getZ() + 0.5, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.0F, 0.8F, false);
        }
    }
}
