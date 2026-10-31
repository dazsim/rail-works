package com.workshopcraft.railworks.block;

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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CokeOvenBricksBlock extends Block {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 26);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    public CokeOvenBricksBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false).setValue(PART, 0).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED, PART, FACING);
    }

    private static BlockPos door(BlockPos pos, BlockState state) {
        return CokeOvenStructure.doorPos(pos, state.getValue(FACING), state.getValue(PART));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FORMED) ? CokeOvenShapes.get(state.getValue(PART), state.getValue(FACING)) : Shapes.block();
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return state.getValue(FORMED) ? Shapes.empty() : Shapes.block();
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(FORMED)) {
            BlockPos door = door(pos, state);
            BlockState d = level.getBlockState(door);
            if (d.getBlock() instanceof CokeOvenBlock oven) return oven.useItemOn(stack, d, level, door, player, hand, hit);
            return InteractionResult.PASS;
        }
        if (!stack.is(ModTags.HAMMERS)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        Direction face = hit.getDirection();
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (face.getAxis().isVertical() || !CokeOvenStructure.bricksPresent(level, pos, face, false)) {
            player.sendOverlayMessage(Component.translatable("message.railworks.coke_oven.incomplete"));
            return InteractionResult.SUCCESS;
        }
        CokeOvenStructure.setFormed(level, pos, face, true);
        level.setBlock(pos, ModContent.COKE_OVEN.defaultBlockState().setValue(CokeOvenBlock.FACING, face), 3);
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6F, 1.2F);
        stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        player.sendOverlayMessage(Component.translatable("message.railworks.coke_oven.formed"));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!state.getValue(FORMED)) return InteractionResult.PASS;
        BlockPos door = door(pos, state);
        BlockState d = level.getBlockState(door);
        if (d.getBlock() instanceof CokeOvenBlock oven) return oven.useWithoutItem(d, level, door, player, hit);
        return InteractionResult.PASS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (state.getValue(FORMED)) CokeOvenStructure.playerBreaking(player.isCreative());
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!state.getValue(FORMED)) return;
        BlockPos door = door(pos, state);
        if (level.getBlockState(door).is(ModContent.COKE_OVEN)) CokeOvenStructure.dismantle(level, door, state.getValue(FACING), pos);
    }
}
