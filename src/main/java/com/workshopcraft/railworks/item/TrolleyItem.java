package com.workshopcraft.railworks.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** A vehicle frame, or a whole vehicle taken off the track. Placed by right clicking near live track. */
public class TrolleyItem extends Item {
    public interface Placer {
        boolean place(ServerLevel level, Player player, Vec3 at, ItemStack stack);
    }

    private final Placer placer;

    public TrolleyItem(Properties properties, Placer placer) {
        super(properties);
        this.placer = placer;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return place(level, player, hand, player.pick(5, 1, false).getLocation());
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getPlayer() == null) return InteractionResult.PASS;
        return place(ctx.getLevel(), ctx.getPlayer(), ctx.getHand(), ctx.getClickLocation());
    }

    private InteractionResult place(Level level, Player player, InteractionHand hand, Vec3 at) {
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        ItemStack stack = player.getItemInHand(hand);
        if (!placer.place(sl, player, at, stack)) return InteractionResult.FAIL;
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.SUCCESS_SERVER;
    }
}
