package com.workshopcraft.railworks.item;

import com.workshopcraft.railworks.registry.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Consumer;

/** Hand ladle: carries up to 1000 mB of molten steel from the furnace to a mould. */
public class LadleItem extends Item {
    public static final int CAPACITY = 1000;

    public LadleItem(Properties properties) {
        super(properties);
    }

    public static int amount(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getIntOr("steel", 0);
    }

    public static void setAmount(ItemStack stack, int amount) {
        if (amount <= 0) {
            stack.remove(DataComponents.CUSTOM_DATA);
            stack.remove(DataComponents.CUSTOM_MODEL_DATA);
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("steel", Math.min(CAPACITY, amount));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(true), List.of(), List.of()));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return amount(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * amount(stack) / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xFF7A1E;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("item.railworks.hand_ladle.amount", amount(stack), CAPACITY).withStyle(ChatFormatting.GRAY));
    }

    /** A full ladle tipped out on the ground makes one block of molten steel. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (amount(stack) < CAPACITY) return InteractionResult.PASS;
        Level level = context.getLevel();
        BlockPos pos = new BlockPlaceContext(context).getClickedPos();
        if (!level.getBlockState(pos).canBeReplaced()) return InteractionResult.FAIL;
        if (!level.isClientSide()) {
            level.setBlockAndUpdate(pos, ModContent.MOLTEN_STEEL_BLOCK.defaultBlockState());
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 1.0F, 1.0F);
            setAmount(stack, 0);
        }
        return InteractionResult.SUCCESS;
    }
}
