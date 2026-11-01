package com.workshopcraft.railworks.block;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import com.workshopcraft.railworks.registry.ModContent;
import com.workshopcraft.railworks.registry.ModTags;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Unpowered rollers. A moulding box on top can be rolled along by hand, and a wheelset is pressed here: axle, two wheels, hammer. Carriage wheelsets press the same way, and 4 steel beams roll into a solebar. */
public class RollerTableBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final IntegerProperty PRESS = IntegerProperty.create("press", 0, 10);
    /** Press values from BEAMS on hold 1 to 4 steel beams, SOLEBAR the last. */
    static final int BEAMS = 7, SOLEBAR = 10;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 12, 0, 16, 16, 16), Block.box(0, 0, 0, 16, 12, 2), Block.box(0, 0, 14, 16, 12, 16));
    private static final VoxelShape SHAPE_X = Shapes.or(Block.box(0, 12, 0, 16, 16, 16), Block.box(0, 0, 0, 2, 12, 16), Block.box(14, 0, 0, 16, 12, 16));

    public RollerTableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Z).setValue(PRESS, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, PRESS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.Z ? SHAPE_X : SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        BlockState above = level.getBlockState(pos.above());
        if (above.getBlock() instanceof MouldBlock && level.getBlockEntity(MouldBlock.mainPos(pos.above(), above)) instanceof MouldBlockEntity be) {
            return be.useItem(stack, player, hand);
        }
        if (!above.canBeReplaced()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        int press = state.getValue(PRESS);
        boolean carriage = press == 0 && stack.is(ModContent.CARRIAGE_AXLE);
        boolean beam = (press == 0 || press >= BEAMS && press < SOLEBAR) && stack.is(ModContent.STEEL_BEAM);
        boolean next = beam || (press == 0 ? stack.is(ModContent.AXLE) || carriage
                : press < 3 ? stack.is(ModContent.WHEEL) : press >= 4 && press < 6 && stack.is(ModContent.CARRIAGE_WHEEL));
        if (next) {
            if (!level.isClientSide()) {
                stack.consume(1, player);
                level.setBlock(pos, state.setValue(PRESS, carriage ? 4 : beam && press == 0 ? BEAMS : press + 1), 3);
                level.playSound(null, pos, press == 0 ? SoundEvents.CHAIN_PLACE : SoundEvents.METAL_STEP, SoundSource.BLOCKS, 0.8F, 0.9F);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(ModTags.HAMMERS) && press == 0) {
            if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("message.railworks.press.axle"));
            return InteractionResult.SUCCESS;
        }
        if (stack.is(ModTags.HAMMERS) && press > 0) {
            if (!level.isClientSide()) {
                int full = press >= BEAMS ? SOLEBAR : press >= 4 ? 6 : 3;
                if (press < full) {
                    player.sendOverlayMessage(Component.translatable(full == SOLEBAR ? "message.railworks.press.beams" : full == 6 ? "message.railworks.press.carriage_wheels" : "message.railworks.press.wheels", full - press));
                } else {
                    level.setBlock(pos, state.setValue(PRESS, 0), 3);
                    Block.popResource(level, pos.above(), new ItemStack(full == SOLEBAR ? ModContent.ROLLED_SOLEBAR : full == 6 ? ModContent.CARRIAGE_WHEELSET : ModContent.WHEELSET));
                    level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.5F, 1.3F);
                    stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    private static ItemStack[] parts(int press) {
        if (press >= BEAMS) return new ItemStack[]{new ItemStack(ModContent.STEEL_BEAM, press - BEAMS + 1)};
        ItemStack[] out = new ItemStack[press == 0 ? 0 : 2];
        if (press >= 4) {
            out[0] = new ItemStack(ModContent.CARRIAGE_AXLE);
            out[1] = new ItemStack(ModContent.CARRIAGE_WHEEL, press - 4);
        } else if (press > 0) {
            out[0] = new ItemStack(ModContent.AXLE);
            out[1] = new ItemStack(ModContent.WHEEL, press - 1);
        }
        return out;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        for (ItemStack s : parts(state.getValue(PRESS)))
            if (!s.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY() + 1, pos.getZ(), s);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockState above = level.getBlockState(pos.above());
        if (above.getBlock() instanceof MouldBlock && level.getBlockEntity(MouldBlock.mainPos(pos.above(), above)) instanceof MouldBlockEntity be) {
            return be.push(player);
        }
        int press = state.getValue(PRESS);
        if (press > 0 && player.getMainHandItem().isEmpty()) {
            if (!level.isClientSide()) {
                ItemStack back = new ItemStack(press >= BEAMS ? ModContent.STEEL_BEAM : press == 1 ? ModContent.AXLE : press == 4 ? ModContent.CARRIAGE_AXLE
                        : press > 4 ? ModContent.CARRIAGE_WHEEL : ModContent.WHEEL);
                level.setBlock(pos, state.setValue(PRESS, press == 4 || press == BEAMS ? 0 : press - 1), 3);
                if (!player.getInventory().add(back)) player.drop(back, false);
                level.playSound(null, pos, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.6F, 1.1F);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) {
            return state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        }
        return state;
    }
}
