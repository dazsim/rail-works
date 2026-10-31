package com.workshopcraft.railworks.registry;

import com.workshopcraft.railworks.RailWorks;
import com.workshopcraft.railworks.block.CokeOvenBlock;
import com.workshopcraft.railworks.block.CokeOvenBlockEntity;
import com.workshopcraft.railworks.block.CokeOvenBricksBlock;
import com.workshopcraft.railworks.block.CokeOvenPartBlock;
import com.workshopcraft.railworks.block.CreosoteBlock;
import com.workshopcraft.railworks.block.CreosoteVatBlock;
import com.workshopcraft.railworks.block.CreosoteVatBlockEntity;
import com.workshopcraft.railworks.menu.CokeOvenMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class ModContent {
    public static FlowingFluid CREOSOTE;
    public static FlowingFluid FLOWING_CREOSOTE;

    public static Block COKE_OVEN_BRICKS;
    public static Block COKE_OVEN;
    public static Block COKE_OVEN_PART;
    public static Block CREOSOTE_BLOCK;
    public static Block CREOSOTE_VAT;
    public static Block TREATED_LOG;
    public static Block TREATED_PLANKS;
    public static Block TREATED_BEAM;

    public static Item HAMMER;
    public static Item COKE;
    public static Item CREOSOTE_BUCKET;
    public static Item TIE;

    public static BlockEntityType<CokeOvenBlockEntity> COKE_OVEN_ENTITY;
    public static BlockEntityType<CreosoteVatBlockEntity> VAT_ENTITY;
    public static final MenuType<CokeOvenMenu> COKE_OVEN_MENU = new MenuType<>(CokeOvenMenu::client, FeatureFlags.VANILLA_SET);
    public static CreativeModeTab TAB;

    private ModContent() {}

    public static void registerFluids(BiConsumer<Identifier, net.minecraft.world.level.material.Fluid> r, FlowingFluid source, FlowingFluid flowing) {
        CREOSOTE = source;
        FLOWING_CREOSOTE = flowing;
        r.accept(RailWorks.id("creosote"), source);
        r.accept(RailWorks.id("flowing_creosote"), flowing);
    }

    private static BlockBehaviour.Properties block(String name) {
        return BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, RailWorks.id(name)));
    }

    private static Item.Properties item(String name) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, RailWorks.id(name)));
    }

    private static <B extends Block> B block(BiConsumer<Identifier, Block> r, String name, Function<BlockBehaviour.Properties, B> make, BlockBehaviour.Properties props) {
        B b = make.apply(props);
        r.accept(RailWorks.id(name), b);
        return b;
    }

    public static void registerBlocks(BiConsumer<Identifier, Block> r) {
        BlockBehaviour.Properties bricks = block("coke_oven_bricks").mapColor(MapColor.COLOR_BROWN).instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops().strength(2.0F, 6.0F).sound(SoundType.STONE);
        COKE_OVEN_BRICKS = block(r, "coke_oven_bricks", CokeOvenBricksBlock::new, bricks);
        COKE_OVEN_PART = block(r, "coke_oven_part", CokeOvenPartBlock::new, block("coke_oven_part").mapColor(MapColor.COLOR_BROWN)
                .instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F).sound(SoundType.STONE)
                .noOcclusion().noLootTable().pushReaction(PushReaction.BLOCK));
        COKE_OVEN = block(r, "coke_oven", CokeOvenBlock::new, block("coke_oven").mapColor(MapColor.COLOR_BROWN)
                .instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F).sound(SoundType.STONE)
                .pushReaction(PushReaction.BLOCK).lightLevel(s -> s.getValue(CokeOvenBlock.LIT) ? 13 : 0));
        CREOSOTE_BLOCK = block(r, "creosote", p -> new CreosoteBlock(CREOSOTE, p), block("creosote").mapColor(MapColor.TERRACOTTA_BLACK)
                .replaceable().noCollision().strength(100.0F).pushReaction(PushReaction.DESTROY).noLootTable().liquid().sound(SoundType.EMPTY));
        CREOSOTE_VAT = block(r, "creosote_vat", CreosoteVatBlock::new, block("creosote_vat").mapColor(MapColor.COLOR_BROWN)
                .instrument(NoteBlockInstrument.BASS).strength(2.5F).sound(SoundType.WOOD).noOcclusion().ignitedByLava());
        TREATED_LOG = block(r, "treated_log", RotatedPillarBlock::new, block("treated_log").mapColor(MapColor.COLOR_BROWN)
                .instrument(NoteBlockInstrument.BASS).strength(2.5F).sound(SoundType.WOOD));
        TREATED_PLANKS = block(r, "treated_planks", Block::new, block("treated_planks").mapColor(MapColor.COLOR_BROWN)
                .instrument(NoteBlockInstrument.BASS).strength(2.5F, 3.5F).sound(SoundType.WOOD));
        TREATED_BEAM = block(r, "treated_beam", RotatedPillarBlock::new, block("treated_beam").mapColor(MapColor.COLOR_BROWN)
                .instrument(NoteBlockInstrument.BASS).strength(2.5F).sound(SoundType.WOOD));
    }

    private static Item blockItem(BiConsumer<Identifier, Item> r, Block b, String name) {
        Item i = new BlockItem(b, item(name).useBlockDescriptionPrefix());
        r.accept(RailWorks.id(name), i);
        return i;
    }

    public static void registerItems(BiConsumer<Identifier, Item> r) {
        HAMMER = new Item(item("hammer").pickaxe(ToolMaterial.IRON, 4.0F, -3.0F));
        r.accept(RailWorks.id("hammer"), HAMMER);
        COKE = new Item(item("coke"));
        r.accept(RailWorks.id("coke"), COKE);
        CREOSOTE_BUCKET = new BucketItem(CREOSOTE, item("creosote_bucket").craftRemainder(Items.BUCKET).stacksTo(1));
        r.accept(RailWorks.id("creosote_bucket"), CREOSOTE_BUCKET);
        TIE = new Item(item("tie"));
        r.accept(RailWorks.id("tie"), TIE);
        blockItem(r, COKE_OVEN_BRICKS, "coke_oven_bricks");
        blockItem(r, CREOSOTE_VAT, "creosote_vat");
        blockItem(r, TREATED_LOG, "treated_log");
        blockItem(r, TREATED_PLANKS, "treated_planks");
        blockItem(r, TREATED_BEAM, "treated_beam");
    }

    public static void registerBlockEntities(BiConsumer<Identifier, BlockEntityType<?>> r) {
        COKE_OVEN_ENTITY = new BlockEntityType<>(CokeOvenBlockEntity::new, Set.of(COKE_OVEN));
        r.accept(RailWorks.id("coke_oven"), COKE_OVEN_ENTITY);
        VAT_ENTITY = new BlockEntityType<>(CreosoteVatBlockEntity::new, Set.of(CREOSOTE_VAT));
        r.accept(RailWorks.id("creosote_vat"), VAT_ENTITY);
    }

    public static void registerMenus(BiConsumer<Identifier, MenuType<?>> r) {
        r.accept(RailWorks.id("coke_oven"), COKE_OVEN_MENU);
    }

    public static void registerCreativeTabs(BiConsumer<Identifier, CreativeModeTab> r) {
        TAB = CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                .title(Component.translatable("itemGroup." + RailWorks.MOD_ID))
                .icon(() -> new ItemStack(HAMMER))
                .displayItems((params, out) -> {
                    out.accept(HAMMER);
                    out.accept(COKE_OVEN_BRICKS);
                    out.accept(COKE);
                    out.accept(CREOSOTE_BUCKET);
                    out.accept(CREOSOTE_VAT);
                    out.accept(TREATED_LOG);
                    out.accept(TREATED_PLANKS);
                    out.accept(TREATED_BEAM);
                    out.accept(TIE);
                })
                .build();
        r.accept(RailWorks.id("main"), TAB);
    }

    public static <T> BiConsumer<Identifier, T> direct(Registry<? super T> registry) {
        return (id, value) -> Registry.register(registry, id, value);
    }

    public static void registerAllDirect(FlowingFluid source, FlowingFluid flowing) {
        registerFluids(direct(BuiltInRegistries.FLUID), source, flowing);
        registerBlocks(direct(BuiltInRegistries.BLOCK));
        registerItems(direct(BuiltInRegistries.ITEM));
        registerBlockEntities(direct(BuiltInRegistries.BLOCK_ENTITY_TYPE));
        registerMenus(direct(BuiltInRegistries.MENU));
        registerCreativeTabs(direct(BuiltInRegistries.CREATIVE_MODE_TAB));
    }
}
