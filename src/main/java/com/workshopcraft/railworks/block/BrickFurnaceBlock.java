package com.workshopcraft.railworks.block;

import com.mojang.serialization.MapCodec;
import com.workshopcraft.railworks.item.LadleItem;
import com.workshopcraft.railworks.registry.ModContent;
import com.workshopcraft.railworks.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/** Furnace door and controller. LEVEL shows how full the shaft is (0 empty to 4 full). */
public class BrickFurnaceBlock extends BaseEntityBlock {
    public static final MapCodec<BrickFurnaceBlock> CODEC = simpleCodec(BrickFurnaceBlock::new);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 4);

    public BrickFurnaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false).setValue(LEVEL, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BrickFurnaceBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel server) || type != ModContent.BRICK_FURNACE_ENTITY) return null;
        return (l, pos, s, be) -> ((BrickFurnaceBlockEntity) be).serverTick(server, pos, s);
    }

    /** Hammer on the tap opens or shuts it. */
    boolean toggleTap(ItemStack stack, Level level, BlockPos door, Player player, InteractionHand hand) {
        if (!stack.is(ModTags.HAMMERS) || !(level.getBlockEntity(door) instanceof BrickFurnaceBlockEntity be)) return false;
        if (!level.isClientSide()) {
            boolean open = be.toggleTap();
            level.playSound(null, BrickFurnaceStructure.tapPos(door, level.getBlockState(door).getValue(FACING)), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0F, open ? 0.8F : 0.6F);
            player.sendOverlayMessage(Component.translatable(open ? "message.railworks.brick_furnace.tap_open" : "message.railworks.brick_furnace.tap_shut"));
        }
        return true;
    }

    @Override
    public InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                       InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BrickFurnaceBlockEntity furnace)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (stack.is(Items.BUCKET) && furnace.tank() >= BrickFurnaceBlockEntity.BUCKET) {
            if (!level.isClientSide()) {
                furnace.drain(BrickFurnaceBlockEntity.BUCKET);
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(ModContent.MOLTEN_STEEL_BUCKET)));
                level.playSound(null, pos, SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.getItem() instanceof LadleItem && furnace.tank() > 0 && LadleItem.amount(stack) < LadleItem.CAPACITY) {
            if (!level.isClientSide()) {
                int moved = Math.min(furnace.tank(), LadleItem.CAPACITY - LadleItem.amount(stack));
                furnace.drain(moved);
                LadleItem.setAmount(stack, LadleItem.amount(stack) + moved);
                level.playSound(null, pos, SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS, 1.0F, 1.2F);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BrickFurnaceBlockEntity be) player.openMenu(be);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BrickFurnaceShapes.get(BrickFurnaceStructure.DOOR_PART, state.getValue(FACING));
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return Shapes.empty();
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BrickFurnaceStructure.playerBreaking(player.isCreative());
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        BrickFurnaceStructure.dismantle(level, pos, state.getValue(FACING), pos);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof BrickFurnaceBlockEntity be ? be.tank() * 15 / BrickFurnaceBlockEntity.CAPACITY : 0;
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
        builder.add(FACING, LIT, LEVEL);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;
        BlockPos c = BrickFurnaceStructure.centre(pos, state.getValue(FACING));
        double tx = c.getX() + 0.5 + random.nextDouble() * 0.5 - 0.25;
        double tz = c.getZ() + 0.5 + random.nextDouble() * 0.5 - 0.25;
        double ty = pos.getY() + 5.0;
        level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true, tx, ty, tz, 0.0, 0.06, 0.0);
        if (random.nextInt(2) == 0) level.addParticle(ParticleTypes.FLAME, tx, ty - 0.1, tz, 0.0, 0.02, 0.0);
        if (random.nextInt(4) == 0) level.addParticle(ParticleTypes.LAVA, tx, ty, tz, 0.0, 0.0, 0.0);
        if (random.nextDouble() < 0.1) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.0F, 0.8F, false);
        }
    }
}
