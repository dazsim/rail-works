package com.workshopcraft.railworks.block;

import com.workshopcraft.railworks.menu.BrickFurnaceMenu;
import com.workshopcraft.railworks.registry.ModContent;
import com.workshopcraft.railworks.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

/**
 * Steel is counted in hundredths of an ingot; a bucket or block is 9 ingots.
 * 8 iron + 4 coke melt into 8 ingots in 60 s. The tap pours 1 ingot/s into a mould and holds the steel when nothing is there to catch it.
 */
public class BrickFurnaceBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int IRON_IN = 0, FUEL_IN = 1;
    public static final int INGOT = 100;
    public static final int BUCKET = 9 * INGOT;
    public static final int CAPACITY = 4 * BUCKET;
    public static final int IRON_PER_BATCH = 8;
    public static final int FUEL_PER_BATCH = 4;
    public static final int STEEL_PER_BATCH = 8 * INGOT;
    public static final int BATCH_TICKS = 1200;
    public static final int TAP_RATE = 5;
    private static final int MAX_FALL = 6;
    public static String ingots(int amount) {
        return amount % INGOT == 0 ? Integer.toString(amount / INGOT) : String.format(java.util.Locale.ROOT, "%.1f", amount / (float) INGOT);
    }

    private static final int[] SLOTS = {IRON_IN, FUEL_IN};

    private NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
    private NonNullList<ItemStack> batch = NonNullList.withSize(2, ItemStack.EMPTY);
    private boolean burning;
    private int progress;
    private int tank;
    private boolean tapOpen;

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

    public BrickFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.BRICK_FURNACE_ENTITY, pos, state);
    }

    public int tank() {
        return tank;
    }

    public void drain(int amount) {
        tank = Math.max(0, tank - amount);
        setChanged();
    }

    public boolean toggleTap() {
        tapOpen = !tapOpen;
        setChanged();
        return tapOpen;
    }

    private Direction facing() {
        return getBlockState().getValue(BrickFurnaceBlock.FACING);
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(BrickFurnaceBlock.FACING);
        if ((level.getGameTime() + pos.asLong()) % 20 == 0) {
            if (!BrickFurnaceStructure.bricksPresent(level, pos, facing, true)) {
                BrickFurnaceStructure.unform(level, pos, facing);
                return;
            }
            pullDroppedItems(level, pos, facing);
        }
        boolean changed = false;
        if (burning) {
            if (++progress >= BATCH_TICKS) {
                tank = Math.min(CAPACITY, tank + STEEL_PER_BATCH);
                burning = false;
                batch = NonNullList.withSize(2, ItemStack.EMPTY);
                progress = 0;
                level.playSound(null, pos, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 1.0F, 0.7F);
            }
            changed = true;
        }
        if (!burning && canStart()) {
            batch.set(IRON_IN, items.get(IRON_IN).split(IRON_PER_BATCH));
            batch.set(FUEL_IN, items.get(FUEL_IN).split(FUEL_PER_BATCH));
            burning = true;
            progress = 0;
            changed = true;
        }
        changed |= tickTap(level, pos, facing);
        int fill = tank == 0 ? 0 : Math.max(1, (tank * 4 + CAPACITY / 2) / CAPACITY);
        if (state.getValue(BrickFurnaceBlock.LIT) != burning || state.getValue(BrickFurnaceBlock.LEVEL) != fill) {
            state = state.setValue(BrickFurnaceBlock.LIT, burning).setValue(BrickFurnaceBlock.LEVEL, fill);
            level.setBlock(pos, state, 3);
        }
        if (changed) setChanged(level, pos, state);
    }

    private boolean tickTap(ServerLevel level, BlockPos door, Direction facing) {
        BlockPos tap = BrickFurnaceStructure.tapPos(door, facing);
        boolean open = tapOpen || level.hasNeighborSignal(tap) || level.hasNeighborSignal(door);
        int shown = 0;
        boolean changed = false;
        if (open) {
            shown = 1;
            MouldBlockEntity mould = tank > 0 ? mouldUnderTap(level, door, facing) : null;
            int moved = mould == null ? 0 : mould.fill(Math.min(TAP_RATE, tank));
            if (moved > 0) {
                tank -= moved;
                shown = mould.getBlockPos().getY() == tap.getY() ? 2 : 3;
                changed = true;
            }
        }
        BlockState ts = level.getBlockState(tap);
        if (ts.is(ModContent.BRICK_FURNACE_PART) && ts.getValue(BrickFurnacePartBlock.TAP) != shown) {
            level.setBlock(tap, ts.setValue(BrickFurnacePartBlock.TAP, shown), 2);
        }
        return changed;
    }

    /** The first mould straight below the spout, through air or water. */
    public static @Nullable MouldBlockEntity mouldUnderTap(net.minecraft.world.level.Level level, BlockPos door, Direction facing) {
        BlockPos out = BrickFurnaceStructure.tapPos(door, facing).relative(BrickFurnaceStructure.tapSide(facing));
        for (int d = 0; d <= MAX_FALL; d++) {
            BlockPos p = out.below(d);
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof MouldBlock) {
                return level.getBlockEntity(MouldBlock.mainPos(p, s)) instanceof MouldBlockEntity mould ? mould : null;
            }
            boolean clear = s.canBeReplaced() && s.getFluidState().isEmpty() || s.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
            if (!clear) return null;
        }
        return null;
    }

    private void pullDroppedItems(ServerLevel level, BlockPos door, Direction facing) {
        BlockPos c = BrickFurnaceStructure.centre(door, facing);
        AABB shaft = new AABB(c.getX(), c.getY(), c.getZ(), c.getX() + 1, c.getY() + 5.5, c.getZ() + 1);
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, shaft)) {
            ItemStack s = e.getItem();
            int slot = s.is(ModTags.FURNACE_IRON) ? IRON_IN : s.is(ModTags.FURNACE_FUEL) ? FUEL_IN : -1;
            if (slot < 0) continue;
            ItemStack in = items.get(slot);
            if (in.isEmpty()) {
                items.set(slot, s.copy());
                e.discard();
            } else if (ItemStack.isSameItemSameComponents(in, s)) {
                int n = Math.min(s.getCount(), in.getMaxStackSize() - in.getCount());
                in.grow(n);
                s.shrink(n);
                if (s.isEmpty()) e.discard();
            }
            setChanged();
        }
    }

    private boolean canStart() {
        ItemStack iron = items.get(IRON_IN), fuel = items.get(FUEL_IN);
        return iron.getCount() >= IRON_PER_BATCH && iron.is(ModTags.FURNACE_IRON)
                && fuel.getCount() >= FUEL_PER_BATCH && fuel.is(ModTags.FURNACE_FUEL)
                && tank + STEEL_PER_BATCH <= CAPACITY;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return switch (slot) {
            case IRON_IN -> stack.is(ModTags.FURNACE_IRON);
            case FUEL_IN -> stack.is(ModTags.FURNACE_FUEL);
            default -> false;
        };
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return SLOTS;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return canPlaceItem(slot, stack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.railworks.brick_furnace");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new BrickFurnaceMenu(containerId, inventory, this, data);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(2, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        burning = input.getBooleanOr("burning", false);
        progress = input.getIntOr("progress", 0);
        batch = NonNullList.withSize(2, ItemStack.EMPTY);
        batch.set(IRON_IN, input.read("batch_iron", ItemStack.CODEC).orElse(ItemStack.EMPTY));
        batch.set(FUEL_IN, input.read("batch_fuel", ItemStack.CODEC).orElse(ItemStack.EMPTY));
        tapOpen = input.getBooleanOr("tap_open", false);
        tank = Math.min(CAPACITY, input.getIntOr("tank", 0) + input.getIntOr("spill", 0));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        output.putBoolean("burning", burning);
        output.putInt("progress", progress);
        if (!batch.get(IRON_IN).isEmpty()) output.store("batch_iron", ItemStack.CODEC, batch.get(IRON_IN));
        if (!batch.get(FUEL_IN).isEmpty()) output.store("batch_fuel", ItemStack.CODEC, batch.get(FUEL_IN));
        output.putInt("tank", tank);
        output.putBoolean("tap_open", tapOpen);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            BlockPos c = BrickFurnaceStructure.centre(pos, state.getValue(BrickFurnaceBlock.FACING));
            for (ItemStack s : batch) if (!s.isEmpty()) Containers.dropItemStack(level, c.getX(), c.getY(), c.getZ(), s);
        }
        batch = NonNullList.withSize(2, ItemStack.EMPTY);
        burning = false;
        progress = 0;
    }

    @Override
    public int getContainerSize() {
        return 2;
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
