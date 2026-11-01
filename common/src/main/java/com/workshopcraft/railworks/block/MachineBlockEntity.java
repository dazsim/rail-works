package com.workshopcraft.railworks.block;

import com.workshopcraft.railworks.registry.ModContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/** One part on the machine at a time. Holding use on the crank turns it; letting go stops it and keeps the progress. */
public class MachineBlockEntity extends BlockEntity {
    /** Placeholder: about what sprinting costs per tick. */
    public static final float EXHAUSTION = 0.028F;
    private static final int HOLD = 6, BAR = 20;

    public enum Job {
        CARRIAGE_WHEEL(MachineBlock.Kind.LATHE, 240, () -> ModContent.ROUGH_CARRIAGE_WHEEL, () -> ModContent.CARRIAGE_WHEEL),
        CARRIAGE_AXLE(MachineBlock.Kind.LATHE, 160, () -> ModContent.ROUGH_CARRIAGE_AXLE, () -> ModContent.CARRIAGE_AXLE),
        PLATE(MachineBlock.Kind.DRILL, 80, () -> ModContent.STEEL_PLATE, () -> ModContent.DRILLED_PLATE),
        AXLE_BOX(MachineBlock.Kind.DRILL, 60, () -> ModContent.ROUGH_AXLE_BOX, () -> ModContent.AXLE_BOX);

        public final MachineBlock.Kind kind;
        public final int ticks;
        private final Supplier<Item> input, result;

        Job(MachineBlock.Kind kind, int ticks, Supplier<Item> input, Supplier<Item> result) {
            this.kind = kind;
            this.ticks = ticks;
            this.input = input;
            this.result = result;
        }

        public Item input() {
            return input.get();
        }

        public Item result() {
            return result.get();
        }

        static @Nullable Job of(MachineBlock.Kind kind, ItemStack stack) {
            for (Job j : values()) if (j.kind == kind && stack.is(j.input())) return j;
            return null;
        }
    }

    private @Nullable Job job;
    private int progress;
    private boolean turning;
    private long crankUntil;
    private @Nullable Player cranker;
    public float crank, crankO;

    public MachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.MACHINE_ENTITY, pos, state);
    }

    public MachineBlock.Kind kind() {
        return ((MachineBlock) getBlockState().getBlock()).kind;
    }

    public @Nullable Job job() {
        return job;
    }

    public float progress() {
        return job == null ? 0 : Math.min(1.0F, progress / (float) job.ticks);
    }

    public boolean done() {
        return job != null && progress >= job.ticks;
    }

    public boolean turning() {
        return turning;
    }

    InteractionResult useItem(ItemStack stack, Player player) {
        if (level == null || stack.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        Job j = Job.of(kind(), stack);
        if (j == null) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (job != null) {
            if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("message.railworks.machine.busy"));
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) {
            job = j;
            progress = 0;
            stack.consume(1, player);
            level.playSound(null, worldPosition, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8F, 0.9F);
            sync();
        }
        return InteractionResult.SUCCESS;
    }

    InteractionResult crank(Player player, boolean onCrank) {
        if (level == null) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        long now = level.getGameTime();
        if (job == null) {
            player.sendOverlayMessage(Component.translatable("message.railworks." + (kind() == MachineBlock.Kind.LATHE ? "lathe" : "drill") + ".empty"));
            return InteractionResult.SUCCESS;
        }
        if (done() || player.isShiftKeyDown()) {
            // still holding from the last turn: wait for the player to let go first
            if (now < crankUntil && cranker == player) {
                crankUntil = now + HOLD;
                return InteractionResult.SUCCESS;
            }
            ItemStack out = new ItemStack(done() ? job.result() : job.input());
            if (!player.getInventory().add(out)) player.drop(out, false);
            job = null;
            progress = 0;
            cranker = null;
            level.playSound(null, worldPosition, SoundEvents.CHAIN_STEP, SoundSource.BLOCKS, 0.8F, 1.2F);
            sync();
            return InteractionResult.SUCCESS;
        }
        if (!onCrank) {
            player.sendOverlayMessage(Component.translatable("message.railworks.machine.crank"));
            return InteractionResult.SUCCESS;
        }
        if (cranker != null && cranker != player && now < crankUntil) {
            player.sendOverlayMessage(Component.translatable("message.railworks.machine.taken"));
            return InteractionResult.SUCCESS;
        }
        if (!player.getFoodData().hasEnoughFood() && !player.getAbilities().mayfly) {
            player.sendOverlayMessage(Component.translatable("message.railworks.machine.hungry"));
            return InteractionResult.SUCCESS;
        }
        cranker = player;
        crankUntil = now + HOLD;
        return InteractionResult.SUCCESS;
    }

    void serverTick(ServerLevel level) {
        if (level.getGameTime() % 100 == 0 && getBlockState().getBlock() instanceof MachineBlock m) m.fillParts(level, worldPosition, getBlockState());
        Player p = cranker;
        boolean on = job != null && !done() && p != null && level.getGameTime() < crankUntil && p.isAlive()
                && p.distanceToSqr(Vec3.atCenterOf(worldPosition)) < 64;
        if (on) {
            progress++;
            p.causeFoodExhaustion(EXHAUSTION);
            boolean lathe = kind() == MachineBlock.Kind.LATHE;
            if (progress % 12 == 0) {
                level.playSound(null, worldPosition, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.35F, lathe ? 0.7F : 1.8F);
            }
            if (lathe && progress % 8 == 4) level.playSound(null, worldPosition, SoundEvents.CHAIN_STEP, SoundSource.BLOCKS, 0.25F, 0.6F);
            if (done()) {
                level.playSound(null, worldPosition, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.7F, 1.0F);
                p.sendOverlayMessage(Component.translatable("message.railworks.machine.done", new ItemStack(job.result()).getHoverName()));
                on = false;
            } else if (progress % 4 == 0) {
                p.sendOverlayMessage(bar(lathe));
            }
            setChanged();
        }
        if (on != turning) {
            turning = on;
            sync();
        }
    }

    private Component bar(boolean lathe) {
        int filled = Math.round(progress() * BAR);
        return Component.translatable("message.railworks." + (lathe ? "lathe" : "drill") + ".progress", new ItemStack(job.input()).getHoverName())
                .append(" ")
                .append(Component.literal("|".repeat(filled)).withStyle(ChatFormatting.GREEN))
                .append(Component.literal("|".repeat(BAR - filled)).withStyle(ChatFormatting.DARK_GRAY))
                .append(" " + Math.round(progress() * 100) + "%");
    }

    void clientTick() {
        crankO = crank;
        if (turning) {
            crank += 0.3F;
            if (job != null && progress < job.ticks) progress++;
        }
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null || job == null) return;
        Containers.dropItemStack(level, pos.getX(), pos.getY() + 0.5, pos.getZ(), new ItemStack(done() ? job.result() : job.input()));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        String name = input.getStringOr("job", "");
        job = null;
        for (Job j : Job.values()) if (j.name().toLowerCase().equals(name)) job = j;
        progress = input.getIntOr("progress", 0);
        turning = input.getBooleanOr("turning", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (job != null) output.putString("job", job.name().toLowerCase());
        output.putInt("progress", progress);
        output.putBoolean("turning", turning);
    }
}
