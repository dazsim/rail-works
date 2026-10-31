package com.workshopcraft.railworks.menu;

import com.workshopcraft.railworks.block.BrickFurnaceBlockEntity;
import com.workshopcraft.railworks.registry.ModContent;
import com.workshopcraft.railworks.registry.ModTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public class BrickFurnaceMenu extends AbstractContainerMenu {
    private static final int INV_START = 2;
    private static final int INV_END = INV_START + 36;

    private final Container container;
    private final ContainerData data;

    public static BrickFurnaceMenu client(int id, Inventory inventory) {
        return new BrickFurnaceMenu(id, inventory, new SimpleContainer(2), new SimpleContainerData(3));
    }

    public BrickFurnaceMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(ModContent.BRICK_FURNACE_MENU, id);
        checkContainerSize(container, 2);
        checkContainerDataCount(data, 3);
        this.container = container;
        this.data = data;
        addSlot(new TagSlot(container, BrickFurnaceBlockEntity.IRON_IN, 44, 17, ModTags.FURNACE_IRON));
        addSlot(new TagSlot(container, BrickFurnaceBlockEntity.FUEL_IN, 44, 53, ModTags.FURNACE_FUEL));
        addStandardInventorySlots(inventory, 8, 84);
        addDataSlots(data);
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index < INV_START) {
            if (!moveItemStackTo(stack, INV_START, INV_END, true)) return ItemStack.EMPTY;
        } else if (stack.is(ModTags.FURNACE_IRON)) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (stack.is(ModTags.FURNACE_FUEL)) {
            if (!moveItemStackTo(stack, 1, 2, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == before.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return before;
    }

    public boolean isBurning() {
        return data.get(0) > 0;
    }

    public float progress() {
        int total = data.get(1);
        return total <= 0 ? 0 : Mth.clamp((float) data.get(0) / total, 0, 1);
    }

    public int tank() {
        return data.get(2);
    }

    static class TagSlot extends Slot {
        private final TagKey<Item> tag;

        TagSlot(Container container, int slot, int x, int y, TagKey<Item> tag) {
            super(container, slot, x, y);
            this.tag = tag;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(tag);
        }
    }
}
