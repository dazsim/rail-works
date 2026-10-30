package com.workshopcraft.railworks.block;

import com.workshopcraft.railworks.registry.ModContent;
import com.workshopcraft.railworks.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

public class CreosoteVatBlockEntity extends BlockEntity {
    public static final int BUCKET = 1000;
    public static final int CAPACITY = 4 * BUCKET;
    public static final int MAX_LOGS = 8;
    public static final int PER_LOG = 125;
    public static final int SOAK_TICKS = 1200;

    private NonNullList<ItemStack> logs = NonNullList.withSize(MAX_LOGS, ItemStack.EMPTY);
    private int tank;
    private int progress;

    public CreosoteVatBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.VAT_ENTITY, pos, state);
    }

    private int count() {
        int n = 0;
        for (ItemStack s : logs) if (!s.isEmpty()) n++;
        return n;
    }

    private int raw() {
        int n = 0;
        for (ItemStack s : logs) if (!s.isEmpty() && !s.is(ModContent.TREATED_LOG.asItem())) n++;
        return n;
    }

    private boolean addLog(ItemStack one) {
        for (int i = 0; i < MAX_LOGS; i++) {
            if (logs.get(i).isEmpty()) {
                logs.set(i, one.copyWithCount(1));
                progress = 0;
                return true;
            }
        }
        return false;
    }

    public InteractionResult useItem(ItemStack stack, Player player, InteractionHand hand) {
        if (level == null) return InteractionResult.PASS;
        if (stack.is(ModContent.TREATED_LOG.asItem())) return takeLog(player);
        if (level.isClientSide()) {
            boolean ours = stack.is(ModContent.CREOSOTE_BUCKET) || stack.is(Items.BUCKET) || stack.is(ModTags.SOAKABLE_LOGS);
            return ours ? InteractionResult.SUCCESS : InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (stack.is(ModContent.CREOSOTE_BUCKET)) {
            if (tank + BUCKET > CAPACITY) return InteractionResult.FAIL;
            if (!level.isClientSide()) {
                tank += BUCKET;
                if (!player.getAbilities().instabuild) player.setItemInHand(hand, new ItemStack(Items.BUCKET));
                level.playSound(null, worldPosition, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 0.8F);
                sync();
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.BUCKET)) {
            if (tank < BUCKET) return InteractionResult.FAIL;
            if (!level.isClientSide()) {
                tank -= BUCKET;
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(ModContent.CREOSOTE_BUCKET)));
                level.playSound(null, worldPosition, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0F, 0.8F);
                sync();
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(ModTags.SOAKABLE_LOGS)) {
            if (count() >= MAX_LOGS) return InteractionResult.FAIL;
            if (!level.isClientSide()) {
                while (!stack.isEmpty() && addLog(stack)) {
                    if (player.getAbilities().instabuild) break;
                    stack.shrink(1);
                }
                level.playSound(null, worldPosition, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                sync();
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    public InteractionResult takeLog(Player player) {
        if (level == null) return InteractionResult.PASS;
        if (level.isClientSide()) return getBlockState().getValue(CreosoteVatBlock.LOGS) > 0 ? InteractionResult.SUCCESS : InteractionResult.PASS;
        if (count() == 0) return InteractionResult.PASS;
        {
            int pick = -1;
            for (int i = MAX_LOGS - 1; i >= 0; i--) {
                ItemStack s = logs.get(i);
                if (s.isEmpty()) continue;
                if (pick < 0 || s.is(ModContent.TREATED_LOG.asItem())) pick = i;
                if (s.is(ModContent.TREATED_LOG.asItem())) break;
            }
            ItemStack out = logs.set(pick, ItemStack.EMPTY);
            if (!player.getInventory().add(out)) player.drop(out, false);
            level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.0F);
            sync();
        }
        return InteractionResult.SUCCESS;
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.getValue(CreosoteVatBlock.FORMED)) return;
        if (level.getGameTime() % 10 == 0 && count() < MAX_LOGS) {
            BlockPos other = pos.relative(state.getValue(CreosoteVatBlock.PARTNER));
            AABB box = new AABB(pos).minmax(new AABB(other)).deflate(0.1, 0, 0.1);
            boolean took = false;
            for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box)) {
                ItemStack s = e.getItem();
                if (!s.is(ModTags.SOAKABLE_LOGS)) continue;
                while (!s.isEmpty() && addLog(s)) {
                    s.shrink(1);
                    took = true;
                }
                if (s.isEmpty()) e.discard();
                else e.setItem(s);
            }
            if (took) {
                level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.4F, 1.2F);
                sync();
            }
        }
        int raw = raw();
        if (raw > 0 && tank >= raw * PER_LOG) {
            progress++;
            if (level.getGameTime() % 8 == 0) {
                BlockPos other = pos.relative(state.getValue(CreosoteVatBlock.PARTNER));
                double f = level.getRandom().nextDouble();
                double x = pos.getX() + 0.5 + (other.getX() - pos.getX()) * f + (level.getRandom().nextDouble() - 0.5) * 0.6;
                double z = pos.getZ() + 0.5 + (other.getZ() - pos.getZ()) * f + (level.getRandom().nextDouble() - 0.5) * 0.6;
                level.sendParticles(ParticleTypes.BUBBLE_POP, x, pos.getY() + surface(), z, 1, 0, 0, 0, 0);
            }
            if (progress >= SOAK_TICKS) {
                for (int i = 0; i < MAX_LOGS; i++) {
                    if (!logs.get(i).isEmpty() && !logs.get(i).is(ModContent.TREATED_LOG.asItem())) logs.set(i, new ItemStack(ModContent.TREATED_LOG));
                }
                tank -= raw * PER_LOG;
                progress = 0;
                level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.6F, 0.7F);
                sync();
            } else {
                setChanged();
            }
        } else if (progress != 0) {
            progress = 0;
            setChanged();
        }
    }

    private double surface() {
        int f = fluidLevel();
        return f == 0 ? 0.2 : (2 + 3 * f) / 16.0;
    }

    private int fluidLevel() {
        return tank <= 0 ? 0 : Math.min(4, (tank + BUCKET - 1) / BUCKET);
    }

    private void sync() {
        setChanged();
        if (level == null) return;
        BlockState state = getBlockState();
        if (!state.getValue(CreosoteVatBlock.FORMED)) return;
        int n = count();
        int lanes = (n + 1) / 2;
        boolean treated = n > 0 && raw() == 0;
        apply(worldPosition, lanes, treated);
        apply(worldPosition.relative(state.getValue(CreosoteVatBlock.PARTNER)), lanes, treated);
    }

    private void apply(BlockPos pos, int lanes, boolean treated) {
        BlockState s = level.getBlockState(pos);
        if (!s.is(ModContent.CREOSOTE_VAT)) return;
        BlockState n = s.setValue(CreosoteVatBlock.FLUID, fluidLevel()).setValue(CreosoteVatBlock.LOGS, lanes).setValue(CreosoteVatBlock.TREATED, treated);
        if (n != s) level.setBlock(pos, n, 3);
    }

    public void dropLogs() {
        if (level == null) return;
        Containers.dropContents(level, worldPosition, logs);
        logs = NonNullList.withSize(MAX_LOGS, ItemStack.EMPTY);
        tank = 0;
        progress = 0;
        setChanged();
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) Containers.dropContents(level, pos, logs);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        logs = NonNullList.withSize(MAX_LOGS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, logs);
        tank = input.getIntOr("tank", 0);
        progress = input.getIntOr("progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, logs);
        output.putInt("tank", tank);
        output.putInt("progress", progress);
    }
}
