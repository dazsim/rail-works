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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public class CokeOvenBricksBlock extends Block {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    public CokeOvenBricksBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModTags.HAMMERS)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (state.getValue(FORMED)) return InteractionResult.PASS;
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
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!state.getValue(FORMED)) return;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, -2, -2), pos.offset(2, 2, 2))) {
            if (level.getBlockEntity(p) instanceof CokeOvenBlockEntity oven) oven.checkStructure();
        }
    }
}
