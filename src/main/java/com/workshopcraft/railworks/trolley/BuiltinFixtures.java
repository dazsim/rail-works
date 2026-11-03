package com.workshopcraft.railworks.trolley;

import com.workshopcraft.railworks.RailWorks;
import com.workshopcraft.railworks.api.CarriageFixtures;
import com.workshopcraft.railworks.api.Fixture;
import com.workshopcraft.railworks.api.FixtureContext;
import com.workshopcraft.railworks.api.FixtureModel;
import com.workshopcraft.railworks.api.FixturePower;
import com.workshopcraft.railworks.api.FixtureType;
import com.workshopcraft.railworks.api.PowerTier;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Rail Works' own working fixtures, registered through the same API other mods use. */
public final class BuiltinFixtures {
    private static boolean done;

    private BuiltinFixtures() {}

    public static synchronized void register() {
        if (done) return;
        done = true;
        layout("workbench", 0, c -> new Workbench());
        layout("stove", 3, c -> new Cooker(RecipeType.SMELTING, FurnaceMenu::new, SoundEvents.FURNACE_FIRE_CRACKLE));
        layout("locker", 27, c -> new Storage(SoundEvents.IRON_TRAPDOOR_OPEN, SoundEvents.IRON_TRAPDOOR_CLOSE));
        layout("trunk", 27, c -> new Storage(SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE));
        layout("barrel", 27, c -> new Storage(SoundEvents.BARREL_OPEN, SoundEvents.BARREL_CLOSE));
        if (Boolean.getBoolean("railworks.fixtureTest")) registerTest();
    }

    private static void layout(String part, int size, java.util.function.Function<FixtureContext, Fixture> f) {
        CarriageFixtures.register(FixtureType.builder(RailWorks.id(part), CarriageLayout.PARTS.get(part).item())
                .model(new FixtureModel.Layout(part)).inventorySize(size).factory(f).build());
    }

    /**
     * A small fixture built the way another mod would: a smoker block model, three slots, smoking recipes, and an
     * LV draw it declares but cannot get yet. Only with -Drailworks.fixtureTest=true.
     */
    private static void registerTest() {
        Identifier id = RailWorks.id("test_smoker");
        CarriageFixtures.register(FixtureType.builder(id, id)
                .model(new FixtureModel.Block(Blocks.SMOKER::defaultBlockState))
                .inventorySize(3)
                .power(FixturePower.consumer(PowerTier.LV, 8))
                .factory(c -> new Cooker(RecipeType.SMOKING, SmokerMenu::new, SoundEvents.SMOKER_SMOKE))
                .build());
    }

    /** The workbench: a vanilla crafting grid that stays open while the player stays near. */
    static final class Workbench implements Fixture {
        @Override
        public @Nullable AbstractContainerMenu menu(FixtureContext ctx, int id, Inventory inv, Player p) {
            return new CraftingMenu(id, inv, ContainerLevelAccess.create(ctx.level(), BlockPos.containing(ctx.position()))) {
                @Override
                public boolean stillValid(Player pl) {
                    return ctx.stillValid(pl);
                }
            };
        }

        @Override
        public boolean use(FixtureContext ctx, Player p) {
            return ctx.openMenu(p);
        }
    }

    /** Storage with chest rows, no hoppers. */
    static final class Storage implements Fixture {
        private final SoundEvent open, close;

        Storage(SoundEvent open, SoundEvent close) {
            this.open = open;
            this.close = close;
        }

        @Override
        public @Nullable AbstractContainerMenu menu(FixtureContext ctx, int id, Inventory inv, Player p) {
            return new ChestMenu(MenuType.GENERIC_9x3, id, inv, ctx.items(), 3) {
                @Override
                public void removed(Player pl) {
                    super.removed(pl);
                    if (!ctx.carriage().isRemoved()) sound(ctx, close, 0.5f);
                }
            };
        }

        @Override
        public boolean use(FixtureContext ctx, Player p) {
            if (!ctx.openMenu(p)) return false;
            sound(ctx, open, 0.5f);
            return true;
        }
    }

    static void sound(FixtureContext ctx, SoundEvent e, float vol) {
        Vec3 at = ctx.position();
        ctx.level().playSound(null, at.x, at.y, at.z, e, SoundSource.BLOCKS, vol, 0.9f + ctx.random().nextFloat() * 0.1f);
    }

    interface CookMenu {
        AbstractContainerMenu make(int id, Inventory inv, Container c, ContainerData d);
    }

    /** A furnace's slots and timing, as AbstractFurnaceBlockEntity runs them, minus experience. */
    static final class Cooker implements Fixture {
        private final RecipeManager.CachedCheck<SingleRecipeInput, ? extends AbstractCookingRecipe> check;
        private final CookMenu menu;
        private final SoundEvent crackle;
        int litTime, litTotal, cook, cookTotal;
        private @Nullable Item last;
        private final ContainerData data = new ContainerData() {
            @Override
            public int get(int i) {
                return switch (i) {
                    case 0 -> litTime;
                    case 1 -> litTotal;
                    case 2 -> cook;
                    case 3 -> cookTotal;
                    default -> 0;
                };
            }

            @Override
            public void set(int i, int v) {
                switch (i) {
                    case 0 -> litTime = v;
                    case 1 -> litTotal = v;
                    case 2 -> cook = v;
                    case 3 -> cookTotal = v;
                    default -> {}
                }
            }

            @Override
            public int getCount() {
                return 4;
            }
        };

        Cooker(RecipeType<? extends AbstractCookingRecipe> type, CookMenu menu, SoundEvent crackle) {
            this.check = RecipeManager.createCheck(type);
            this.menu = menu;
            this.crackle = crackle;
        }

        @Override
        public @Nullable AbstractContainerMenu menu(FixtureContext ctx, int id, Inventory inv, Player p) {
            return menu.make(id, inv, ctx.items(), data);
        }

        private int total(ServerLevel level, ItemStack in) {
            return check.getRecipeFor(new SingleRecipeInput(in), level).map(r -> r.value().cookingTime()).orElse(200);
        }

        @Override
        public void tick(FixtureContext ctx) {
            Container items = ctx.items();
            ServerLevel level = ctx.level();
            ItemStack fuel = items.getItem(1), in = items.getItem(0);
            // a new ingredient starts over, the way a furnace's setItem does
            if (last == null) last = in.getItem();
            else if (last != in.getItem()) {
                last = in.getItem();
                cookTotal = total(level, in);
                cook = 0;
            }
            boolean wasLit = litTime > 0;
            if (litTime > 0) litTime--;
            boolean lit = litTime > 0;
            if (lit || !fuel.isEmpty() && !in.isEmpty()) {
                if (!in.isEmpty()) {
                    SingleRecipeInput input = new SingleRecipeInput(in);
                    RecipeHolder<? extends AbstractCookingRecipe> r = check.getRecipeFor(input, level).orElse(null);
                    if (r != null) {
                        ItemStack out = r.value().assemble(input);
                        if (!out.isEmpty() && canBurn(items, out)) {
                            if (!lit) {
                                int t = level.fuelValues().burnDuration(fuel);
                                litTime = litTotal = t;
                                if (t > 0) {
                                    Item f = fuel.getItem();
                                    fuel.shrink(1);
                                    if (fuel.isEmpty()) {
                                        ItemStackTemplate rem = f.getCraftingRemainder();
                                        items.setItem(1, rem != null ? rem.create() : ItemStack.EMPTY);
                                    }
                                    lit = true;
                                }
                            }
                            if (lit) {
                                if (cookTotal <= 0) cookTotal = r.value().cookingTime();
                                if (++cook >= cookTotal) {
                                    cook = 0;
                                    cookTotal = r.value().cookingTime();
                                    ItemStack res = items.getItem(2);
                                    if (res.isEmpty()) items.setItem(2, out.copy());
                                    else res.grow(out.getCount());
                                    if (in.is(net.minecraft.world.item.Items.WET_SPONGE) && items.getItem(1).is(net.minecraft.world.item.Items.BUCKET))
                                        items.setItem(1, new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET));
                                    in.shrink(1);
                                    items.setChanged();
                                }
                            } else cook = 0;
                        } else cook = 0;
                    }
                } else cook = 0;
            } else if (cook > 0) {
                cook = Math.max(0, Math.min(cook - 2, cookTotal));
            }
            if (wasLit != lit) items.setChanged();
            ctx.setLit(lit);
            if (lit && ctx.random().nextInt(100) == 0) sound(ctx, crackle, 1.0f);
        }

        private static boolean canBurn(Container items, ItemStack out) {
            ItemStack res = items.getItem(2);
            if (res.isEmpty()) return true;
            if (!ItemStack.isSameItemSameComponents(res, out)) return false;
            return res.getCount() + out.getCount() <= Math.min(items.getMaxStackSize(), out.getMaxStackSize());
        }

        @Override
        public void save(ValueOutput out) {
            out.putInt("lit", litTime);
            out.putInt("lit_total", litTotal);
            out.putInt("cook", cook);
            out.putInt("cook_total", cookTotal);
        }

        @Override
        public void load(ValueInput in) {
            litTime = in.getIntOr("lit", 0);
            litTotal = in.getIntOr("lit_total", 0);
            cook = in.getIntOr("cook", 0);
            cookTotal = in.getIntOr("cook_total", 0);
        }
    }
}
