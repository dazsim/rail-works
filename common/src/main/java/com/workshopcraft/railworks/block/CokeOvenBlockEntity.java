package com.workshopcraft.railworks.block;

import com.workshopcraft.railworks.menu.CokeOvenMenu;
import com.workshopcraft.railworks.registry.ModContent;
import com.workshopcraft.railworks.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

public class CokeOvenBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int COAL_IN = 0, COKE_OUT = 1, BUCKET_IN = 2, BUCKET_OUT = 3;
    public static final int BUCKET = 1000;
    public static final int CAPACITY = 4 * BUCKET;
    public static final int COAL_PER_BATCH = 8;
    public static final int COKE_PER_BATCH = 4;
    public static final int BATCH_TICKS = 900;

    private static final int[] TOP = {COAL_IN, BUCKET_IN};
    private static final int[] SIDES = {COAL_IN, BUCKET_IN, COKE_OUT, BUCKET_OUT};
    private static final int[] BOTTOM = {COKE_OUT, BUCKET_OUT};

    private NonNullList<ItemStack> items = NonNullList.withSize(4, ItemStack.EMPTY);
    private boolean burning;
    private ItemStack batch = ItemStack.EMPTY;
    private int progress;
    private int tank;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int id) {
            return switch (id) {
                case 0 -> burning ? progress : 0;
                case 1 -> BATCH_TICKS;
                case 2 -> tank;
                default -> 0;
            };
        }

        @Override
        public void set(int id, int value) {}

        @Override
        public int getCount() {
            return 3;
        }
    };

    public CokeOvenBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.COKE_OVEN_ENTITY, pos, state);
    }

    public int tank() {
        return tank;
    }

    public void drain(int amount) {
        tank = Math.max(0, tank - amount);
        setChanged();
    }

    public void checkStructure() {
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        if (!state.is(ModContent.COKE_OVEN)) return;
        if (!CokeOvenStructure.bricksPresent(level, worldPosition, state.getValue(CokeOvenBlock.FACING), true)) {
            level.setBlock(worldPosition, ModContent.COKE_OVEN_BRICKS.defaultBlockState(), 3);
        }
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if ((level.getGameTime() + pos.asLong()) % 20 == 0) {
            checkStructure();
            if (!level.getBlockState(pos).is(ModContent.COKE_OVEN)) return;
        }
        boolean changed = false;
        if (burning) {
            if (++progress >= BATCH_TICKS) {
                ItemStack out = items.get(COKE_OUT);
                if (out.isEmpty()) items.set(COKE_OUT, new ItemStack(ModContent.COKE, COKE_PER_BATCH));
                else out.grow(COKE_PER_BATCH);
                tank = Math.min(CAPACITY, tank + BUCKET);
                burning = false;
                batch = ItemStack.EMPTY;
                progress = 0;
            }
            changed = true;
        }
        if (!burning && canStart()) {
            batch = items.get(COAL_IN).split(COAL_PER_BATCH);
            burning = true;
            progress = 0;
            changed = true;
        }
        if (tank >= BUCKET && items.get(BUCKET_IN).is(Items.BUCKET) && items.get(BUCKET_OUT).isEmpty()) {
            items.get(BUCKET_IN).shrink(1);
            items.set(BUCKET_OUT, new ItemStack(ModContent.CREOSOTE_BUCKET));
            tank -= BUCKET;
            changed = true;
        }
        if (state.getValue(CokeOvenBlock.LIT) != burning) {
            state = state.setValue(CokeOvenBlock.LIT, burning);
            level.setBlock(pos, state, 3);
        }
        if (changed) setChanged(level, pos, state);
    }

    private boolean canStart() {
        ItemStack coal = items.get(COAL_IN);
        if (coal.getCount() < COAL_PER_BATCH || !coal.is(ModTags.COKE_OVEN_INPUT)) return false;
        if (tank + BUCKET > CAPACITY) return false;
        ItemStack out = items.get(COKE_OUT);
        return out.isEmpty() || out.is(ModContent.COKE) && out.getCount() + COKE_PER_BATCH <= out.getMaxStackSize();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case COAL_IN -> stack.is(ModTags.COKE_OVEN_INPUT);
            case BUCKET_IN -> stack.is(Items.BUCKET);
            default -> false;
        };
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.UP ? TOP : side == Direction.DOWN ? BOTTOM : SIDES;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == COKE_OUT || slot == BUCKET_OUT;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.railworks.coke_oven");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new CokeOvenMenu(containerId, inventory, this, data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(4, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        burning = input.getBooleanOr("burning", false);
        progress = input.getIntOr("progress", 0);
        batch = input.read("batch", ItemStack.CODEC).orElse(burning ? new ItemStack(Items.COAL, COAL_PER_BATCH) : ItemStack.EMPTY);
        tank = input.getIntOr("tank", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putBoolean("burning", burning);
        output.putInt("progress", progress);
        if (!batch.isEmpty()) output.store("batch", ItemStack.CODEC, batch);
        output.putInt("tank", tank);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !batch.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), batch);
        batch = ItemStack.EMPTY;
        burning = false;
        progress = 0;
    }

    @Override
    public int getContainerSize() {
        return 4;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }
}
