package com.workshopcraft.railworks.menu;

import com.workshopcraft.railworks.block.CokeOvenBlockEntity;
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
import net.minecraft.world.item.Items;

public class CokeOvenMenu extends AbstractContainerMenu {
    private static final int INV_START = 4;
    private static final int INV_END = INV_START + 36;

    private final Container container;
    private final ContainerData data;

    public static CokeOvenMenu client(int id, Inventory inventory) {
        return new CokeOvenMenu(id, inventory, new SimpleContainer(4), new SimpleContainerData(3));
    }

    public CokeOvenMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(ModContent.COKE_OVEN_MENU, id);
        checkContainerSize(container, 4);
        checkContainerDataCount(data, 3);
        this.container = container;
        this.data = data;
        addSlot(new Slot(container, CokeOvenBlockEntity.COAL_IN, 44, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModTags.COKE_OVEN_INPUT);
            }
        });
        addSlot(new OutputSlot(container, CokeOvenBlockEntity.COKE_OUT, 98, 35));
        addSlot(new Slot(container, CokeOvenBlockEntity.BUCKET_IN, 152, 17) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.BUCKET);
            }
        });
        addSlot(new OutputSlot(container, CokeOvenBlockEntity.BUCKET_OUT, 152, 53));
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
        } else if (stack.is(ModTags.COKE_OVEN_INPUT)) {
            if (!moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        } else if (stack.is(Items.BUCKET)) {
            if (!moveItemStackTo(stack, 2, 3, false)) return ItemStack.EMPTY;
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

    static class OutputSlot extends Slot {
        OutputSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
