package com.workshopcraft.railworks.registry;

import com.workshopcraft.railworks.RailWorks;
import com.workshopcraft.railworks.block.BrickFurnaceBlock;
import com.workshopcraft.railworks.block.BrickFurnaceBlockEntity;
import com.workshopcraft.railworks.block.BrickFurnacePartBlock;
import com.workshopcraft.railworks.block.CokeOvenBlock;
import com.workshopcraft.railworks.block.CokeOvenBlockEntity;
import com.workshopcraft.railworks.block.CokeOvenBricksBlock;
import com.workshopcraft.railworks.block.CokeOvenPartBlock;
import com.workshopcraft.railworks.block.CreosoteBlock;
import com.workshopcraft.railworks.block.CreosoteVatBlock;
import com.workshopcraft.railworks.block.CreosoteVatBlockEntity;
import com.workshopcraft.railworks.block.FireBricksBlock;
import com.workshopcraft.railworks.block.MoltenSteelBlock;
import com.workshopcraft.railworks.block.MachineBlock;
import com.workshopcraft.railworks.block.MachineBlockEntity;
import com.workshopcraft.railworks.block.MouldBlock;
import com.workshopcraft.railworks.block.MouldBlockEntity;
import com.workshopcraft.railworks.block.RollerTableBlock;
import com.workshopcraft.railworks.block.GroundFrameBlock;
import com.workshopcraft.railworks.block.TrackNodeBlock;
import com.workshopcraft.railworks.block.TrackNodeBlockEntity;
import com.workshopcraft.railworks.item.LadleItem;
import com.workshopcraft.railworks.item.TrackItems;
import com.workshopcraft.railworks.item.TrolleyItem;
import com.workshopcraft.railworks.trolley.Bogie;
import com.workshopcraft.railworks.trolley.Carriage;
import com.workshopcraft.railworks.trolley.HandTrolley;
import com.workshopcraft.railworks.menu.BrickFurnaceMenu;
import com.workshopcraft.railworks.menu.CokeOvenMenu;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
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
    public static FlowingFluid MOLTEN_STEEL;
    public static FlowingFluid FLOWING_MOLTEN_STEEL;

    public static Block COKE_OVEN_BRICKS;
    public static Block COKE_OVEN;
    public static Block COKE_OVEN_PART;
    public static Block CREOSOTE_BLOCK;
    public static Block CREOSOTE_VAT;
    public static Block TREATED_LOG;
    public static Block TREATED_PLANKS;
    public static Block TREATED_BEAM;
    public static Block FIRE_BRICKS;
    public static Block BRICK_FURNACE;
    public static Block BRICK_FURNACE_PART;
    public static Block MOLTEN_STEEL_BLOCK;
    public static Block SET_STEEL;
    public static Block STEEL_BLOCK;
    public static Block RAIL_BAR_MOULD;
    public static Block INGOT_MOULD;
    public static Block ROLLER_TABLE;
    public static Block TRACK_NODE;
    public static Block GROUND_FRAME;
    public static Block LATHE;
    public static Block PILLAR_DRILL;

    public static Item HAMMER;
    public static Item COKE;
    public static Item CREOSOTE_BUCKET;
    public static Item TIE;
    public static Item FIRE_CLAY;
    public static Item FIRE_BRICK;
    public static Item STEEL_INGOT;
    public static Item RAIL_BAR;
    public static Item RAIL_PATTERN;
    public static Item INGOT_PATTERN;
    public static Item HAND_LADLE;
    public static Item MOLTEN_STEEL_BUCKET;
    public static Item SURVEY_STAKE;
    public static Item THEODOLITE;
    public static Item BUFFER_STOP;
    public static Item WHEEL_PATTERN;
    public static Item AXLE_PATTERN;
    public static Item WHEEL;
    public static Item AXLE;
    public static Item WHEELSET;
    public static Item TROLLEY_FRAME;
    public static Item TROLLEY_PUMP;
    public static Item TROLLEY_BRAKE;
    public static Item HAND_TROLLEY;
    public static Item CARRIAGE_WHEEL_PATTERN;
    public static Item CARRIAGE_AXLE_PATTERN;
    public static Item PLATE_PATTERN;
    public static Item AXLE_BOX_PATTERN;
    public static Item SPRING_PATTERN;
    public static Item ROUGH_CARRIAGE_WHEEL;
    public static Item CARRIAGE_WHEEL;
    public static Item ROUGH_CARRIAGE_AXLE;
    public static Item CARRIAGE_AXLE;
    public static Item STEEL_PLATE;
    public static Item DRILLED_PLATE;
    public static Item ROUGH_AXLE_BOX;
    public static Item AXLE_BOX;
    public static Item BOGIE_SPRING;
    public static Item CARRIAGE_WHEELSET;
    public static Item BOGIE_FRAME;
    public static Item BOLSTER;
    public static Item TOW_BAR;
    public static Item BOGIE;
    public static Item RIVETS;
    public static Item RIVET_PATTERN;
    public static Item STEEL_BEAM;
    public static Item BEAM_PATTERN;
    public static Item ROLLED_SOLEBAR;
    public static Item SOLEBAR;
    public static Item HEADSTOCK;
    public static Item CROSSMEMBER;
    public static Item BODY_BOLSTER;
    public static Item TRUSS_RODS;
    public static Item BUFFER_PATTERN;
    public static Item BUFFER_STOCK;
    public static Item CARRIAGE_BUFFER;
    public static Item SCREW_COUPLING;
    public static Item FLOOR_SECTION;
    public static Item SIDE_FRAME;
    public static Item END_FRAME;
    public static Item STEEL_SIDE_PANEL;
    public static Item WOOD_SIDE_PANEL;
    public static Item CARRIAGE_DOOR;
    public static Item END_PANEL;
    public static Item ROOF_SECTION;
    public static Item BENCH_SEAT;
    public static Item LUGGAGE_RACK;
    public static Item CEILING_LAMP;
    public static Item CARRIAGE_SCREEN;
    public static Item STEEL_END_DOOR;
    public static Item WOOD_END_DOOR;
    public static Item GANGWAY_FRAME;
    public static Item GANGWAY_BELLOWS;
    public static Item GANGWAY_FOOTPLATE;
    public static Item COMPARTMENT_PARTITION;
    public static Item CORRIDOR_WALL;
    public static Item LV_RAIL_CLEATS;
    public static Item LV_RAIL;
    public static Item JUMPER_CABLE;
    public static Item AXLE_DYNAMO;
    public static Item BATTERY_BOX;
    public static Item ELECTRIC_LAMP;
    public static Item CHAIR_PAIR;
    public static Item CARRIAGE_TABLE;
    public static Item BERTH;
    public static Item CUPBOARD_SEAT;
    public static Item LOCKER;
    public static Item TRUNK;
    public static Item BARREL_FIXTURE;
    public static Item CARRIAGE_WORKBENCH;
    public static Item CARRIAGE_STOVE;
    public static @org.jspecify.annotations.Nullable Item TEST_SMOKER;
    public static Item STEEL_BLANK_PANEL;
    public static Item WOOD_BLANK_PANEL;

    public static EntityType<HandTrolley> HAND_TROLLEY_ENTITY;
    public static EntityType<Bogie> BOGIE_ENTITY;
    public static EntityType<Carriage> CARRIAGE_ENTITY;
    public static final TicketType TROLLEY_TICKET = new TicketType(40L, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);
    public static final TicketType TRAIN_TICKET = new TicketType(600L, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION | TicketType.FLAG_KEEP_DIMENSION_ACTIVE);

    public static BlockEntityType<CokeOvenBlockEntity> COKE_OVEN_ENTITY;
    public static BlockEntityType<CreosoteVatBlockEntity> VAT_ENTITY;
    public static BlockEntityType<BrickFurnaceBlockEntity> BRICK_FURNACE_ENTITY;
    public static BlockEntityType<MouldBlockEntity> MOULD_ENTITY;
    public static BlockEntityType<TrackNodeBlockEntity> TRACK_NODE_ENTITY;
    public static BlockEntityType<MachineBlockEntity> MACHINE_ENTITY;
    public static final MenuType<CokeOvenMenu> COKE_OVEN_MENU = new MenuType<>(CokeOvenMenu::client, FeatureFlags.VANILLA_SET);
    public static final MenuType<BrickFurnaceMenu> BRICK_FURNACE_MENU = new MenuType<>(BrickFurnaceMenu::client, FeatureFlags.VANILLA_SET);
    public static CreativeModeTab TAB;

    private ModContent() {}

    public static void registerFluids(BiConsumer<Identifier, net.minecraft.world.level.material.Fluid> r, FlowingFluid source, FlowingFluid flowing,
                                      FlowingFluid steel, FlowingFluid flowingSteel) {
        CREOSOTE = source;
        FLOWING_CREOSOTE = flowing;
        MOLTEN_STEEL = steel;
        FLOWING_MOLTEN_STEEL = flowingSteel;
        r.accept(RailWorks.id("creosote"), source);
        r.accept(RailWorks.id("flowing_creosote"), flowing);
        r.accept(RailWorks.id("molten_steel"), steel);
        r.accept(RailWorks.id("flowing_molten_steel"), flowingSteel);
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

        FIRE_BRICKS = block(r, "fire_bricks", FireBricksBlock::new, block("fire_bricks").mapColor(MapColor.SAND).instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops().strength(2.0F, 6.0F).sound(SoundType.STONE));
        BRICK_FURNACE_PART = block(r, "brick_furnace_part", BrickFurnacePartBlock::new, block("brick_furnace_part").mapColor(MapColor.SAND)
                .instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F).sound(SoundType.STONE)
                .noOcclusion().noLootTable().pushReaction(PushReaction.BLOCK));
        BRICK_FURNACE = block(r, "brick_furnace", BrickFurnaceBlock::new, block("brick_furnace").mapColor(MapColor.SAND)
                .instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 6.0F).sound(SoundType.STONE)
                .noOcclusion().pushReaction(PushReaction.BLOCK).lightLevel(s -> s.getValue(BrickFurnaceBlock.LIT) ? 13 : 0));
        MOLTEN_STEEL_BLOCK = block(r, "molten_steel", p -> new MoltenSteelBlock(MOLTEN_STEEL, p), block("molten_steel").mapColor(MapColor.FIRE)
                .replaceable().noCollision().strength(100.0F).lightLevel(s -> 15).pushReaction(PushReaction.DESTROY)
                .noLootTable().liquid().sound(SoundType.EMPTY));
        SET_STEEL = block(r, "set_steel", Block::new, block("set_steel").mapColor(MapColor.METAL).instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops().strength(4.0F, 6.0F).sound(SoundType.METAL));
        STEEL_BLOCK = block(r, "steel_block", Block::new, block("steel_block").mapColor(MapColor.METAL).instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                .requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.METAL));
        RAIL_BAR_MOULD = block(r, "rail_bar_mould", p -> new MouldBlock(MouldBlock.Kind.RAIL, p), block("rail_bar_mould").mapColor(MapColor.METAL)
                .strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK));
        INGOT_MOULD = block(r, "ingot_mould", p -> new MouldBlock(MouldBlock.Kind.INGOT, p), block("ingot_mould").mapColor(MapColor.METAL)
                .strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK));
        ROLLER_TABLE = block(r, "roller_table", RollerTableBlock::new, block("roller_table").mapColor(MapColor.METAL)
                .strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion());
        TRACK_NODE = block(r, "track_node", TrackNodeBlock::new, block("track_node").mapColor(MapColor.WOOD).strength(0.5F)
                .sound(SoundType.WOOD).noOcclusion().noLootTable().dynamicShape().pushReaction(PushReaction.BLOCK));
        GROUND_FRAME = block(r, "ground_frame", GroundFrameBlock::new, block("ground_frame").mapColor(MapColor.WOOD)
                .instrument(NoteBlockInstrument.BASS).strength(1.5F).sound(SoundType.WOOD).noOcclusion());
        LATHE = block(r, "lathe", p -> new MachineBlock(MachineBlock.Kind.LATHE, p), block("lathe").mapColor(MapColor.METAL)
                .strength(3.5F, 6.0F).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK));
        PILLAR_DRILL = block(r, "pillar_drill", p -> new MachineBlock(MachineBlock.Kind.DRILL, p), block("pillar_drill").mapColor(MapColor.METAL)
                .strength(3.5F, 6.0F).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK));
    }

    private static Item blockItem(BiConsumer<Identifier, Item> r, Block b, String name) {
        Item i = new BlockItem(b, item(name).useBlockDescriptionPrefix());
        r.accept(RailWorks.id(name), i);
        return i;
    }

    public static void registerItems(BiConsumer<Identifier, Item> r) {
        HAMMER = new TrackItems.Hammer(item("hammer").pickaxe(ToolMaterial.IRON, 4.0F, -3.0F));
        r.accept(RailWorks.id("hammer"), HAMMER);
        COKE = new Item(item("coke"));
        r.accept(RailWorks.id("coke"), COKE);
        CREOSOTE_BUCKET = new BucketItem(CREOSOTE, item("creosote_bucket").craftRemainder(Items.BUCKET).stacksTo(1));
        r.accept(RailWorks.id("creosote_bucket"), CREOSOTE_BUCKET);
        TIE = new TrackItems.Layable(item("tie"), false);
        r.accept(RailWorks.id("tie"), TIE);
        blockItem(r, COKE_OVEN_BRICKS, "coke_oven_bricks");
        blockItem(r, CREOSOTE_VAT, "creosote_vat");
        blockItem(r, TREATED_LOG, "treated_log");
        blockItem(r, TREATED_PLANKS, "treated_planks");
        blockItem(r, TREATED_BEAM, "treated_beam");

        FIRE_CLAY = simple(r, "fire_clay");
        FIRE_BRICK = simple(r, "fire_brick");
        STEEL_INGOT = simple(r, "steel_ingot");
        RAIL_BAR = new TrackItems.Layable(item("rail_bar"), true);
        r.accept(RailWorks.id("rail_bar"), RAIL_BAR);
        RAIL_PATTERN = new Item(item("rail_bar_pattern").stacksTo(16));
        r.accept(RailWorks.id("rail_bar_pattern"), RAIL_PATTERN);
        INGOT_PATTERN = new Item(item("ingot_pattern").stacksTo(16));
        r.accept(RailWorks.id("ingot_pattern"), INGOT_PATTERN);
        HAND_LADLE = new LadleItem(item("hand_ladle").stacksTo(1));
        r.accept(RailWorks.id("hand_ladle"), HAND_LADLE);
        MOLTEN_STEEL_BUCKET = new BucketItem(MOLTEN_STEEL, item("molten_steel_bucket").craftRemainder(Items.BUCKET).stacksTo(1));
        r.accept(RailWorks.id("molten_steel_bucket"), MOLTEN_STEEL_BUCKET);
        blockItem(r, FIRE_BRICKS, "fire_bricks");
        blockItem(r, SET_STEEL, "set_steel");
        blockItem(r, STEEL_BLOCK, "steel_block");
        blockItem(r, RAIL_BAR_MOULD, "rail_bar_mould");
        blockItem(r, INGOT_MOULD, "ingot_mould");
        blockItem(r, ROLLER_TABLE, "roller_table");
        SURVEY_STAKE = new BlockItem(TRACK_NODE, item("survey_stake"));
        r.accept(RailWorks.id("survey_stake"), SURVEY_STAKE);
        THEODOLITE = new TrackItems.Theodolite(item("theodolite").stacksTo(1));
        r.accept(RailWorks.id("theodolite"), THEODOLITE);
        BUFFER_STOP = new TrackItems.BufferStop(item("buffer_stop").stacksTo(16));
        r.accept(RailWorks.id("buffer_stop"), BUFFER_STOP);
        blockItem(r, GROUND_FRAME, "ground_frame");
        WHEEL_PATTERN = new Item(item("wheel_pattern").stacksTo(16));
        r.accept(RailWorks.id("wheel_pattern"), WHEEL_PATTERN);
        AXLE_PATTERN = new Item(item("axle_pattern").stacksTo(16));
        r.accept(RailWorks.id("axle_pattern"), AXLE_PATTERN);
        WHEEL = simple(r, "wheel");
        AXLE = simple(r, "axle");
        WHEELSET = new Item(item("wheelset").stacksTo(16));
        r.accept(RailWorks.id("wheelset"), WHEELSET);
        TROLLEY_FRAME = new TrolleyItem(item("trolley_frame").stacksTo(1), (l, pl, at, s) -> HandTrolley.place(l, pl, at, s, false));
        r.accept(RailWorks.id("trolley_frame"), TROLLEY_FRAME);
        TROLLEY_PUMP = new Item(item("trolley_pump").stacksTo(1));
        r.accept(RailWorks.id("trolley_pump"), TROLLEY_PUMP);
        TROLLEY_BRAKE = new Item(item("trolley_brake").stacksTo(1));
        r.accept(RailWorks.id("trolley_brake"), TROLLEY_BRAKE);
        HAND_TROLLEY = new TrolleyItem(item("hand_trolley").stacksTo(1), (l, pl, at, s) -> HandTrolley.place(l, pl, at, s, true));
        r.accept(RailWorks.id("hand_trolley"), HAND_TROLLEY);
        CARRIAGE_WHEEL_PATTERN = pattern(r, "carriage_wheel_pattern");
        CARRIAGE_AXLE_PATTERN = pattern(r, "carriage_axle_pattern");
        PLATE_PATTERN = pattern(r, "plate_pattern");
        AXLE_BOX_PATTERN = pattern(r, "axle_box_pattern");
        SPRING_PATTERN = pattern(r, "spring_pattern");
        ROUGH_CARRIAGE_WHEEL = simple(r, "rough_carriage_wheel");
        CARRIAGE_WHEEL = simple(r, "carriage_wheel");
        ROUGH_CARRIAGE_AXLE = simple(r, "rough_carriage_axle");
        CARRIAGE_AXLE = simple(r, "carriage_axle");
        STEEL_PLATE = simple(r, "steel_plate");
        DRILLED_PLATE = simple(r, "drilled_plate");
        ROUGH_AXLE_BOX = simple(r, "rough_axle_box");
        AXLE_BOX = simple(r, "axle_box");
        BOGIE_SPRING = simple(r, "bogie_spring");
        CARRIAGE_WHEELSET = new Item(item("carriage_wheelset").stacksTo(4));
        r.accept(RailWorks.id("carriage_wheelset"), CARRIAGE_WHEELSET);
        BOGIE_FRAME = new TrolleyItem(item("bogie_frame").stacksTo(1), (l, pl, at, s) -> Bogie.place(l, pl, at, false));
        r.accept(RailWorks.id("bogie_frame"), BOGIE_FRAME);
        BOLSTER = new Item(item("bolster").stacksTo(4));
        r.accept(RailWorks.id("bolster"), BOLSTER);
        TOW_BAR = new Item(item("tow_bar").stacksTo(16));
        r.accept(RailWorks.id("tow_bar"), TOW_BAR);
        BOGIE = new TrolleyItem(item("bogie").stacksTo(1), (l, pl, at, s) -> Bogie.place(l, pl, at, true));
        r.accept(RailWorks.id("bogie"), BOGIE);
        blockItem(r, LATHE, "lathe");
        blockItem(r, PILLAR_DRILL, "pillar_drill");
        RIVETS = simple(r, "rivets");
        RIVET_PATTERN = pattern(r, "rivet_pattern");
        STEEL_BEAM = simple(r, "steel_beam");
        BEAM_PATTERN = pattern(r, "beam_pattern");
        ROLLED_SOLEBAR = part(r, "rolled_solebar");
        SOLEBAR = part(r, "solebar");
        HEADSTOCK = part(r, "headstock");
        CROSSMEMBER = part(r, "crossmember");
        BODY_BOLSTER = part(r, "body_bolster");
        TRUSS_RODS = part(r, "truss_rods");
        BUFFER_PATTERN = pattern(r, "buffer_pattern");
        BUFFER_STOCK = simple(r, "buffer_stock");
        CARRIAGE_BUFFER = part(r, "carriage_buffer");
        SCREW_COUPLING = part(r, "screw_coupling");
        FLOOR_SECTION = part(r, "floor_section");
        SIDE_FRAME = part(r, "side_frame");
        END_FRAME = part(r, "end_frame");
        STEEL_SIDE_PANEL = part(r, "steel_side_panel");
        WOOD_SIDE_PANEL = part(r, "wood_side_panel");
        CARRIAGE_DOOR = part(r, "carriage_door");
        END_PANEL = part(r, "end_panel");
        ROOF_SECTION = part(r, "roof_section");
        BENCH_SEAT = part(r, "bench_seat");
        LUGGAGE_RACK = part(r, "luggage_rack");
        CEILING_LAMP = part(r, "ceiling_lamp");
        CARRIAGE_SCREEN = part(r, "carriage_screen");
        CHAIR_PAIR = part(r, "chair_pair");
        CARRIAGE_TABLE = part(r, "carriage_table");
        BERTH = part(r, "berth");
        CUPBOARD_SEAT = part(r, "cupboard_seat");
        LOCKER = part(r, "locker");
        TRUNK = part(r, "trunk");
        BARREL_FIXTURE = part(r, "barrel_fixture");
        CARRIAGE_WORKBENCH = part(r, "carriage_workbench");
        CARRIAGE_STOVE = part(r, "carriage_stove");
        STEEL_BLANK_PANEL = part(r, "steel_blank_panel");
        WOOD_BLANK_PANEL = part(r, "wood_blank_panel");
        STEEL_END_DOOR = part(r, "steel_end_door");
        WOOD_END_DOOR = part(r, "wood_end_door");
        GANGWAY_FRAME = part(r, "gangway_frame");
        GANGWAY_BELLOWS = part(r, "gangway_bellows");
        GANGWAY_FOOTPLATE = part(r, "gangway_footplate");
        COMPARTMENT_PARTITION = part(r, "compartment_partition");
        CORRIDOR_WALL = part(r, "corridor_wall");
        LV_RAIL_CLEATS = part(r, "lv_rail_cleats");
        LV_RAIL = part(r, "lv_rail");
        JUMPER_CABLE = part(r, "jumper_cable");
        AXLE_DYNAMO = part(r, "axle_dynamo");
        BATTERY_BOX = part(r, "battery_box");
        ELECTRIC_LAMP = part(r, "electric_lamp");
        if (Boolean.getBoolean("railworks.fixtureTest")) TEST_SMOKER = part(r, "test_smoker");
        com.workshopcraft.railworks.trolley.BuiltinFixtures.register();
    }

    public static void registerEntities(BiConsumer<Identifier, EntityType<?>> r) {
        ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, RailWorks.id("hand_trolley"));
        HAND_TROLLEY_ENTITY = EntityType.Builder.<HandTrolley>of(HandTrolley::new, MobCategory.MISC).noLootTable()
                .sized(2.0F, 1.0F).clientTrackingRange(10).updateInterval(3).build(key);
        r.accept(key.identifier(), HAND_TROLLEY_ENTITY);
        ResourceKey<EntityType<?>> bogie = ResourceKey.create(Registries.ENTITY_TYPE, RailWorks.id("bogie"));
        BOGIE_ENTITY = EntityType.Builder.<Bogie>of(Bogie::new, MobCategory.MISC).noLootTable()
                .sized(2.4F, 1.0F).clientTrackingRange(10).updateInterval(3).build(bogie);
        r.accept(bogie.identifier(), BOGIE_ENTITY);
        ResourceKey<EntityType<?>> carriage = ResourceKey.create(Registries.ENTITY_TYPE, RailWorks.id("carriage"));
        CARRIAGE_ENTITY = EntityType.Builder.<Carriage>of(Carriage::new, MobCategory.MISC).noLootTable()
                .sized(4.8F, 5.4F).clientTrackingRange(10).updateInterval(3).build(carriage);
        r.accept(carriage.identifier(), CARRIAGE_ENTITY);
    }

    public static void registerTicketTypes(BiConsumer<Identifier, TicketType> r) {
        r.accept(RailWorks.id("hand_trolley"), TROLLEY_TICKET);
        r.accept(RailWorks.id("train"), TRAIN_TICKET);
    }

    private static Item pattern(BiConsumer<Identifier, Item> r, String name) {
        Item i = new Item(item(name).stacksTo(16));
        r.accept(RailWorks.id(name), i);
        return i;
    }

    private static Item part(BiConsumer<Identifier, Item> r, String name) {
        Item i = new Item(item(name).stacksTo(16));
        r.accept(RailWorks.id(name), i);
        return i;
    }

    private static Item simple(BiConsumer<Identifier, Item> r, String name) {
        Item i = new Item(item(name));
        r.accept(RailWorks.id(name), i);
        return i;
    }

    public static void registerBlockEntities(BiConsumer<Identifier, BlockEntityType<?>> r) {
        COKE_OVEN_ENTITY = new BlockEntityType<>(CokeOvenBlockEntity::new, Set.of(COKE_OVEN));
        r.accept(RailWorks.id("coke_oven"), COKE_OVEN_ENTITY);
        VAT_ENTITY = new BlockEntityType<>(CreosoteVatBlockEntity::new, Set.of(CREOSOTE_VAT));
        r.accept(RailWorks.id("creosote_vat"), VAT_ENTITY);
        BRICK_FURNACE_ENTITY = new BlockEntityType<>(BrickFurnaceBlockEntity::new, Set.of(BRICK_FURNACE));
        r.accept(RailWorks.id("brick_furnace"), BRICK_FURNACE_ENTITY);
        MOULD_ENTITY = new BlockEntityType<>(MouldBlockEntity::new, Set.of(RAIL_BAR_MOULD, INGOT_MOULD));
        r.accept(RailWorks.id("mould"), MOULD_ENTITY);
        TRACK_NODE_ENTITY = new BlockEntityType<>(TrackNodeBlockEntity::new, Set.of(TRACK_NODE));
        r.accept(RailWorks.id("track_node"), TRACK_NODE_ENTITY);
        MACHINE_ENTITY = new BlockEntityType<>(MachineBlockEntity::new, Set.of(LATHE, PILLAR_DRILL));
        r.accept(RailWorks.id("machine"), MACHINE_ENTITY);
    }

    public static void registerMenus(BiConsumer<Identifier, MenuType<?>> r) {
        r.accept(RailWorks.id("coke_oven"), COKE_OVEN_MENU);
        r.accept(RailWorks.id("brick_furnace"), BRICK_FURNACE_MENU);
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
                    out.accept(FIRE_CLAY);
                    out.accept(FIRE_BRICK);
                    out.accept(FIRE_BRICKS);
                    out.accept(HAND_LADLE);
                    out.accept(MOLTEN_STEEL_BUCKET);
                    out.accept(SET_STEEL);
                    out.accept(STEEL_INGOT);
                    out.accept(STEEL_BLOCK);
                    out.accept(RAIL_PATTERN);
                    out.accept(INGOT_PATTERN);
                    out.accept(RAIL_BAR_MOULD);
                    out.accept(INGOT_MOULD);
                    out.accept(ROLLER_TABLE);
                    out.accept(RAIL_BAR);
                    out.accept(SURVEY_STAKE);
                    out.accept(THEODOLITE);
                    out.accept(GROUND_FRAME);
                    out.accept(BUFFER_STOP);
                    out.accept(WHEEL_PATTERN);
                    out.accept(AXLE_PATTERN);
                    out.accept(WHEEL);
                    out.accept(AXLE);
                    out.accept(WHEELSET);
                    out.accept(TROLLEY_FRAME);
                    out.accept(TROLLEY_PUMP);
                    out.accept(TROLLEY_BRAKE);
                    out.accept(HAND_TROLLEY);
                    out.accept(LATHE);
                    out.accept(PILLAR_DRILL);
                    out.accept(CARRIAGE_WHEEL_PATTERN);
                    out.accept(CARRIAGE_AXLE_PATTERN);
                    out.accept(PLATE_PATTERN);
                    out.accept(AXLE_BOX_PATTERN);
                    out.accept(SPRING_PATTERN);
                    out.accept(ROUGH_CARRIAGE_WHEEL);
                    out.accept(CARRIAGE_WHEEL);
                    out.accept(ROUGH_CARRIAGE_AXLE);
                    out.accept(CARRIAGE_AXLE);
                    out.accept(STEEL_PLATE);
                    out.accept(DRILLED_PLATE);
                    out.accept(ROUGH_AXLE_BOX);
                    out.accept(AXLE_BOX);
                    out.accept(BOGIE_SPRING);
                    out.accept(CARRIAGE_WHEELSET);
                    out.accept(BOGIE_FRAME);
                    out.accept(BOLSTER);
                    out.accept(TOW_BAR);
                    out.accept(BOGIE);
                    out.accept(RIVETS);
                    out.accept(RIVET_PATTERN);
                    out.accept(STEEL_BEAM);
                    out.accept(BEAM_PATTERN);
                    out.accept(ROLLED_SOLEBAR);
                    out.accept(SOLEBAR);
                    out.accept(HEADSTOCK);
                    out.accept(CROSSMEMBER);
                    out.accept(BODY_BOLSTER);
                    out.accept(TRUSS_RODS);
                    out.accept(BUFFER_PATTERN);
                    out.accept(BUFFER_STOCK);
                    out.accept(CARRIAGE_BUFFER);
                    out.accept(SCREW_COUPLING);
                    out.accept(FLOOR_SECTION);
                    out.accept(SIDE_FRAME);
                    out.accept(END_FRAME);
                    out.accept(STEEL_SIDE_PANEL);
                    out.accept(WOOD_SIDE_PANEL);
                    out.accept(CARRIAGE_DOOR);
                    out.accept(END_PANEL);
                    out.accept(ROOF_SECTION);
                    out.accept(BENCH_SEAT);
                    out.accept(LUGGAGE_RACK);
                    out.accept(CEILING_LAMP);
                    out.accept(CARRIAGE_SCREEN);
                    out.accept(CHAIR_PAIR);
                    out.accept(CARRIAGE_TABLE);
                    out.accept(BERTH);
                    out.accept(CUPBOARD_SEAT);
                    out.accept(LOCKER);
                    out.accept(TRUNK);
                    out.accept(BARREL_FIXTURE);
                    out.accept(CARRIAGE_WORKBENCH);
                    out.accept(CARRIAGE_STOVE);
                    if (TEST_SMOKER != null) out.accept(TEST_SMOKER);
                    out.accept(STEEL_BLANK_PANEL);
                    out.accept(WOOD_BLANK_PANEL);
                    out.accept(STEEL_END_DOOR);
                    out.accept(WOOD_END_DOOR);
                    out.accept(GANGWAY_FRAME);
                    out.accept(GANGWAY_BELLOWS);
                    out.accept(GANGWAY_FOOTPLATE);
                    out.accept(COMPARTMENT_PARTITION);
                    out.accept(CORRIDOR_WALL);
                    out.accept(LV_RAIL_CLEATS);
                    out.accept(LV_RAIL);
                    out.accept(JUMPER_CABLE);
                    out.accept(AXLE_DYNAMO);
                    out.accept(BATTERY_BOX);
                    out.accept(ELECTRIC_LAMP);
                })
                .build();
        r.accept(RailWorks.id("main"), TAB);
    }

    public static <T> BiConsumer<Identifier, T> direct(Registry<? super T> registry) {
        return (id, value) -> Registry.register(registry, id, value);
    }

    public static void registerAllDirect(FlowingFluid source, FlowingFluid flowing, FlowingFluid steel, FlowingFluid flowingSteel) {
        registerFluids(direct(BuiltInRegistries.FLUID), source, flowing, steel, flowingSteel);
        registerBlocks(direct(BuiltInRegistries.BLOCK));
        registerItems(direct(BuiltInRegistries.ITEM));
        registerBlockEntities(direct(BuiltInRegistries.BLOCK_ENTITY_TYPE));
        registerEntities(direct(BuiltInRegistries.ENTITY_TYPE));
        registerTicketTypes(direct(BuiltInRegistries.TICKET_TYPE));
        registerMenus(direct(BuiltInRegistries.MENU));
        registerCreativeTabs(direct(BuiltInRegistries.CREATIVE_MODE_TAB));
    }
}
