package com.workshopcraft.railworks.block;

import com.workshopcraft.railworks.item.LadleItem;
import com.workshopcraft.railworks.registry.ModContent;
import com.workshopcraft.railworks.registry.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Pattern in, sand in (pattern back), pour, 20 s to cool, then knock the casting out with a hammer. */
public class MouldBlockEntity extends BlockEntity {
    public static final int COOL_TICKS = 400;
    static boolean moving;

    private int amount;
    private int timer;
    private int pattern;

    public MouldBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.MOULD_ENTITY, pos, state);
    }

    private MouldBlock block() {
        return (MouldBlock) getBlockState().getBlock();
    }

    public MouldStage stage() {
        return getBlockState().getValue(MouldBlock.STAGE);
    }

    private void setStage(MouldStage stage) {
        if (level == null) return;
        BlockState s = getBlockState().setValue(MouldBlock.STAGE, stage);
        level.setBlock(worldPosition, s, 3);
        if (block().kind.longBox) {
            BlockPos tail = MouldBlock.tailPos(worldPosition, s);
            BlockState t = level.getBlockState(tail);
            if (t.is(s.getBlock())) level.setBlock(tail, t.setValue(MouldBlock.STAGE, stage), 3);
        }
        setChanged();
    }

    /** Takes up to max units of steel, returning how much it took. */
    public int fill(int max) {
        MouldStage stage = stage();
        if (stage != MouldStage.READY && stage != MouldStage.FILLING) return 0;
        int take = Math.min(max, block().kind.need(pattern) - amount);
        if (take <= 0) return 0;
        amount += take;
        if (amount >= block().kind.need(pattern)) {
            timer = COOL_TICKS;
            setStage(MouldStage.HOT);
            level.playSound(null, worldPosition, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.2F);
        } else if (stage == MouldStage.READY) {
            setStage(MouldStage.FILLING);
        }
        setChanged();
        return take;
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getValue(MouldBlock.STAGE) != MouldStage.HOT) return;
        if (--timer <= 0) {
            timer = 0;
            setStage(MouldStage.CAST);
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.4F);
        } else {
            setChanged();
        }
    }

    InteractionResult useItem(ItemStack stack, Player player, InteractionHand hand) {
        if (level == null) return InteractionResult.PASS;
        MouldBlock.Kind kind = block().kind;
        MouldStage stage = stage();
        boolean client = level.isClientSide();
        if (stage == MouldStage.EMPTY && kind.indexOf(stack) >= 0) {
            if (!client) {
                pattern = kind.indexOf(stack);
                stack.consume(1, player);
                setStage(MouldStage.PATTERN);
                level.playSound(null, worldPosition, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }
        if (stage == MouldStage.PATTERN && stack.is(ModTags.MOULD_SAND)) {
            if (!client) {
                stack.consume(1, player);
                ItemStack back = new ItemStack(kind.pattern(pattern));
                if (!player.getInventory().add(back)) player.drop(back, false);
                amount = 0;
                setStage(MouldStage.READY);
                level.playSound(null, worldPosition, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
            }
            return InteractionResult.SUCCESS;
        }
        if ((stage == MouldStage.READY || stage == MouldStage.FILLING) && stack.getItem() instanceof LadleItem && LadleItem.amount(stack) > 0) {
            if (!client) {
                int moved = fill(LadleItem.amount(stack));
                LadleItem.setAmount(stack, LadleItem.amount(stack) - moved);
                level.playSound(null, worldPosition, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.BLOCKS, 0.8F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(ModTags.HAMMERS)) {
            if (stage == MouldStage.CAST) {
                if (!client) {
                    BlockPos at = worldPosition.above();
                    Block.popResource(level, at, kind.result(pattern));
                    amount = 0;
                    setStage(MouldStage.EMPTY);
                    level.playSound(null, worldPosition, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.4F, 1.6F);
                    stack.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                }
                return InteractionResult.SUCCESS;
            }
            if (stage == MouldStage.HOT) {
                if (!client) player.sendOverlayMessage(Component.translatable("message.railworks.mould.cooling", (timer + 19) / 20));
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /** Empty hand on a box on a roller table rolls it one block the way the player is looking. */
    InteractionResult push(Player player) {
        if (level == null) return InteractionResult.PASS;
        BlockState state = getBlockState();
        boolean longBox = block().kind.longBox;
        BlockPos main = worldPosition;
        BlockPos tail = longBox ? MouldBlock.tailPos(main, state) : null;
        Direction dir = player.getDirection();
        BlockPos newMain = main.relative(dir);
        BlockPos newTail = longBox ? tail.relative(dir) : null;
        if (!level.getBlockState(main.below()).is(ModContent.ROLLER_TABLE)) return InteractionResult.PASS;
        if (!free(newMain, main, tail) || longBox && !free(newTail, main, tail)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        BlockState tailState = longBox ? level.getBlockState(tail) : null;
        int a = amount, t = timer;
        int pat = pattern;
        moving = true;
        try {
            int quiet = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
            if (longBox) level.setBlock(tail, Blocks.AIR.defaultBlockState(), quiet);
            level.setBlock(main, Blocks.AIR.defaultBlockState(), quiet);
            level.setBlock(newMain, state, Block.UPDATE_ALL);
            if (longBox) level.setBlock(newTail, tailState, Block.UPDATE_ALL);
            level.updateNeighborsAt(main, Blocks.AIR);
            if (longBox) level.updateNeighborsAt(tail, Blocks.AIR);
        } finally {
            moving = false;
        }
        if (level.getBlockEntity(newMain) instanceof MouldBlockEntity be) {
            be.amount = a;
            be.timer = t;
            be.pattern = pat;
            be.setChanged();
        }
        level.playSound(null, newMain, SoundEvents.MINECART_INSIDE, SoundSource.BLOCKS, 0.15F, 1.6F);
        return InteractionResult.SUCCESS;
    }

    private boolean free(BlockPos p, BlockPos main, BlockPos tail) {
        if (p.equals(main) || p.equals(tail)) return true;
        BlockState below = level.getBlockState(p.below());
        return level.getBlockState(p).canBeReplaced() && below.is(ModContent.ROLLER_TABLE) && below.getValue(RollerTableBlock.PRESS) == 0;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (moving || level == null) return;
        MouldBlock.Kind kind = ((MouldBlock) state.getBlock()).kind;
        MouldStage stage = state.getValue(MouldBlock.STAGE);
        if (stage == MouldStage.PATTERN) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), new ItemStack(kind.pattern(pattern)));
        if (stage == MouldStage.CAST) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), kind.result(pattern));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        amount = input.getIntOr("amount", 0);
        timer = input.getIntOr("timer", 0);
        pattern = input.getIntOr("pattern", input.getBooleanOr("alt", false) ? 1 : 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("amount", amount);
        output.putInt("timer", timer);
        output.putInt("pattern", pattern);
    }
}
