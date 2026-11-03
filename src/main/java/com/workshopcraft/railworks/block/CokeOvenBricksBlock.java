package com.workshopcraft.railworks.block;

import com.workshopcraft.railworks.registry.ModContent;
import com.workshopcraft.railworks.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.phys.BlockHitResult;

/** Loose oven bricks; hitting the middle of one side of a complete beehive with a hammer forms the oven. */
public class CokeOvenBricksBlock extends Block {
    public CokeOvenBricksBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModTags.HAMMERS)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        Direction face = hit.getDirection();
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (face.getAxis().isVertical() || !CokeOvenStructure.bricksPresent(level, pos, face, false)) {
            for (int i = 0; i < 4; i++) player.sendSystemMessage(Component.translatable("message.railworks.coke_oven.incomplete." + i));
            return InteractionResult.SUCCESS;
        }
        CokeOvenStructure.setFormed(level, pos, face, true);
        level.setBlock(pos, ModContent.COKE_OVEN.defaultBlockState().setValue(CokeOvenBlock.FACING, face), 3);
        level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6F, 1.2F);
        stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        player.sendOverlayMessage(Component.translatable("message.railworks.coke_oven.formed"));
        return InteractionResult.SUCCESS;
    }
}
