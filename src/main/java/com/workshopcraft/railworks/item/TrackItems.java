package com.workshopcraft.railworks.item;

import com.workshopcraft.railworks.registry.ModContent;
import com.workshopcraft.railworks.track.TrackActions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The track-laying items. Right click (and hold) works on whatever track is near, so no block needs to be aimed at. */
public final class TrackItems {
    private TrackItems() {}

    public static class Hammer extends Item {
        public Hammer(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!(level instanceof ServerLevel sl)) return InteractionResult.PASS;
            return TrackActions.hammerGround(sl, player, player.pick(5, 1, false).getLocation());
        }

        @Override
        public InteractionResult useOn(UseOnContext ctx) {
            BlockState state = ctx.getLevel().getBlockState(ctx.getClickedPos());
            if (!state.is(ModContent.TRACK_NODE) || ctx.getPlayer() == null) return InteractionResult.PASS;
            if (!(ctx.getLevel() instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
            return TrackActions.hammerNode(sl, ctx.getPlayer(), ctx.getClickedPos(), state,
                    new BlockHitResult(ctx.getClickLocation(), ctx.getClickedFace(), ctx.getClickedPos(), ctx.isInside()));
        }
    }

    public static class Layable extends Item {
        private final boolean rails;

        public Layable(Properties properties, boolean rails) {
            super(properties);
            this.rails = rails;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!(level instanceof ServerLevel sl)) return InteractionResult.PASS;
            return TrackActions.lay(sl, player, hand, rails);
        }
    }

    public static class BufferStop extends Item {
        public BufferStop(Properties properties) {
            super(properties);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!(level instanceof ServerLevel sl)) return InteractionResult.PASS;
            return TrackActions.fitBuffer(sl, player, player.getItemInHand(hand));
        }
    }

    public static class Theodolite extends Item {
        public Theodolite(Properties properties) {
            super(properties);
        }

        @Override
        public int getUseDuration(ItemStack stack, LivingEntity user) {
            return 72000;
        }

        @Override
        public ItemUseAnimation getUseAnimation(ItemStack stack) {
            return ItemUseAnimation.SPYGLASS;
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            player.playSound(SoundEvents.SPYGLASS_USE, 1.0F, 1.0F);
            return ItemUtils.startUsingInstantly(level, player, hand);
        }

        @Override
        public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
            if (level instanceof ServerLevel sl && entity instanceof Player p && remaining % 5 == 0) TrackActions.sight(sl, p);
        }

        @Override
        public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
            entity.playSound(SoundEvents.SPYGLASS_STOP_USING, 1.0F, 1.0F);
            return true;
        }
    }
}
