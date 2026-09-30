package net.get900.pixelpirates.homestead;

import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.homestead.block.TropicalCropBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * HOMESTEAD (2026-09-30 overnight): base building, farming, the galley and the rum still, furniture, the hoard...
 * Every homestead block is listed in {@link #OWNED} so the main datagen providers leave them to HomesteadDatagen.
 */
public final class HomesteadBlocks {
    private HomesteadBlocks() {}

    public static final Set<Block> OWNED = new LinkedHashSet<>();
    /** Blocks that get a plain BlockItem, in creative-tab order. */
    public static final List<Block> WITH_ITEM = new ArrayList<>();

    static <T extends Block> T block(String name, T block, boolean item) {
        Registry.register(Registries.BLOCK, new Identifier(PixelPirates.MOD_ID, name), block);
        OWNED.add(block);
        if (item) {
            Registry.register(Registries.ITEM, new Identifier(PixelPirates.MOD_ID, name), new BlockItem(block, new Item.Settings()));
            WITH_ITEM.add(block);
        }
        return block;
    }

    static AbstractBlock.Settings crop() {
        return AbstractBlock.Settings.copy(Blocks.WHEAT).mapColor(MapColor.DARK_GREEN).noCollision().ticksRandomly().breakInstantly()
                .sounds(BlockSoundGroup.CROP).pistonBehavior(PistonBehavior.DESTROY);
    }

    // ================================================================== #11 TROPICAL CROPS
    public static final Block PINEAPPLE_CROP = block("pineapple_crop", new TropicalCropBlock(crop(), () -> HomesteadItems.PINEAPPLE_CROWN), false);
    public static final Block LIME_CROP = block("lime_crop", new TropicalCropBlock(crop(), () -> HomesteadItems.LIME_SEEDS), false);
    public static final Block CHILI_CROP = block("chili_crop", new TropicalCropBlock(crop(), () -> HomesteadItems.CHILI_SEEDS), false);

    // ================================================================== #12 RUM
    public static final Block RUM_STILL = block("rum_still", new net.get900.pixelpirates.homestead.rum.RumStillBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.ORANGE).strength(3.0f, 6.0f).requiresTool().nonOpaque().sounds(BlockSoundGroup.COPPER)
            .luminance(s -> s.get(net.get900.pixelpirates.homestead.rum.RumStillBlock.LIT) ? 9 : 0)), true);
    public static final Block AGING_CASK = block("aging_cask", new net.get900.pixelpirates.homestead.rum.AgingCaskBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.BROWN).strength(2.5f).nonOpaque().sounds(BlockSoundGroup.WOOD)), true);

    // ================================================================== #5 PALM & TROPICAL BUILDING
    static AbstractBlock.Settings palmWood() {
        return AbstractBlock.Settings.copy(net.get900.pixelpirates.block.ModBlocks.PALM_PLANKS).burnable();
    }

    public static final Block PALM_STAIRS = block("palm_stairs", new net.minecraft.block.StairsBlock(
            net.get900.pixelpirates.block.ModBlocks.PALM_PLANKS.getDefaultState(), palmWood()), true);
    public static final Block PALM_SLAB = block("palm_slab", new net.minecraft.block.SlabBlock(palmWood()), true);
    public static final Block PALM_FENCE = block("palm_fence", new net.minecraft.block.FenceBlock(palmWood()), true);
    public static final Block PALM_FENCE_GATE = block("palm_fence_gate", new net.minecraft.block.FenceGateBlock(palmWood(), net.minecraft.block.WoodType.OAK), true);
    public static final Block PALM_DOOR = block("palm_door", new net.minecraft.block.DoorBlock(palmWood().nonOpaque(), net.minecraft.block.BlockSetType.OAK), true);
    public static final Block PALM_TRAPDOOR = block("palm_trapdoor", new net.minecraft.block.TrapdoorBlock(palmWood().nonOpaque()
            .allowsSpawning((s, w, p, t) -> false), net.minecraft.block.BlockSetType.OAK), true);
    public static final Block THATCH = block("thatch", new Block(AbstractBlock.Settings.copy(Blocks.HAY_BLOCK)
            .mapColor(MapColor.YELLOW).burnable()), true);
    public static final Block THATCH_STAIRS = block("thatch_stairs", new net.minecraft.block.StairsBlock(THATCH.getDefaultState(),
            AbstractBlock.Settings.copy(Blocks.HAY_BLOCK).burnable()), true);
    public static final Block THATCH_SLAB = block("thatch_slab", new net.minecraft.block.SlabBlock(AbstractBlock.Settings.copy(Blocks.HAY_BLOCK).burnable()), true);
    public static final Block WOVEN_PALM_SCREEN = block("woven_palm_screen", new net.minecraft.block.PaneBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.PALE_YELLOW).strength(0.5f).sounds(BlockSoundGroup.GRASS).nonOpaque().burnable()), true);
    public static final Block WOVEN_MAT = block("woven_mat", new net.minecraft.block.CarpetBlock(AbstractBlock.Settings.copy(Blocks.WHITE_CARPET)
            .mapColor(MapColor.PALE_YELLOW).sounds(BlockSoundGroup.GRASS)), true);
    public static final Block DRIFTWOOD_FENCE = block("driftwood_fence", new net.minecraft.block.FenceBlock(AbstractBlock.Settings.copy(Blocks.OAK_FENCE)
            .mapColor(MapColor.LIGHT_GRAY)), true);
    public static final Block DRIFTWOOD_FENCE_GATE = block("driftwood_fence_gate", new net.minecraft.block.FenceGateBlock(AbstractBlock.Settings.copy(Blocks.OAK_FENCE_GATE)
            .mapColor(MapColor.LIGHT_GRAY), net.minecraft.block.WoodType.OAK), true);
    public static final Block ROPE_LADDER = block("rope_ladder", new net.get900.pixelpirates.homestead.building.RopeLadderBlock(AbstractBlock.Settings.copy(Blocks.LADDER)
            .sounds(BlockSoundGroup.WOOL)), true);
    public static final Block ROPE_BRIDGE = block("rope_bridge", new net.get900.pixelpirates.homestead.building.RopeBridgeBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.BROWN).strength(1.0f).sounds(BlockSoundGroup.WOOD).nonOpaque().burnable()), true);
    public static final Block TIKI_TORCH = block("tiki_torch", new net.get900.pixelpirates.homestead.building.TikiTorchBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.BROWN).strength(0.8f).sounds(BlockSoundGroup.WOOD).nonOpaque().noCollision().luminance(s -> 14)), true);

    // ================================================================== #2 PIRATE FURNITURE
    static AbstractBlock.Settings wood() { return AbstractBlock.Settings.create().mapColor(MapColor.BROWN).strength(2.0f).sounds(BlockSoundGroup.WOOD).nonOpaque().burnable(); }

    static double[] b(double... v) { return v; }

    public static final Block CAPTAINS_DESK = block("captains_desk", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(wood(), true, b(0, 0, 2, 16, 15, 16)), true);
    public static final Block SEA_CHEST = block("sea_chest", new net.get900.pixelpirates.homestead.furniture.StorageBlock(wood().strength(2.5f), 6, true, b(1, 0, 3, 15, 12, 13)), true);
    public static final Block CARGO_CRATE = block("cargo_crate", new net.get900.pixelpirates.homestead.furniture.StorageBlock(wood(), 3, false, b(1, 0, 1, 15, 14, 15)), true);
    public static final Block TREASURE_PEDESTAL = block("treasure_pedestal", new net.get900.pixelpirates.homestead.furniture.DisplayBlock(AbstractBlock.Settings.create().mapColor(MapColor.GOLD)
            .strength(1.5f, 6f).sounds(BlockSoundGroup.STONE).nonOpaque(), b(3, 0, 3, 13, 11, 13)), true);
    public static final Block RUM_RACK = block("rum_rack", new net.get900.pixelpirates.homestead.furniture.RumRackBlock(wood(), b(0, 0, 10, 16, 16, 16)), true);
    public static final Block HANGING_NET = block("hanging_net", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(AbstractBlock.Settings.create().mapColor(MapColor.PALE_YELLOW)
            .strength(0.3f).sounds(BlockSoundGroup.WOOL).nonOpaque().noCollision().burnable(), false, b(0, 0, 14, 16, 16, 16)), true);
    public static final Block ROPE_COIL = block("rope_coil", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(AbstractBlock.Settings.create().mapColor(MapColor.BROWN)
            .strength(0.5f).sounds(BlockSoundGroup.WOOL).nonOpaque().burnable(), true, b(2, 0, 2, 14, 5, 14)), true);
    public static final Block HANGING_ROPE = block("hanging_rope", new net.get900.pixelpirates.homestead.furniture.HangingRopeBlock(AbstractBlock.Settings.create().mapColor(MapColor.BROWN)
            .strength(0.4f).sounds(BlockSoundGroup.WOOL).nonOpaque().noCollision().burnable()), true);
    public static final Block SHIPS_WHEEL = block("ships_wheel", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(wood(), false, b(0, 0, 13, 16, 16, 16)), true);
    public static final Block DISPLAY_CANNON = block("display_cannon", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY)
            .strength(3f, 6f).sounds(BlockSoundGroup.METAL).nonOpaque().requiresTool(), true, b(2, 0, 1, 14, 10, 15)), true);
    public static final Block MAP_TABLE = block("map_table", new net.get900.pixelpirates.homestead.furniture.MapTableBlock(wood(), b(0, 0, 0, 16, 15, 16)), true);
    public static final Block CAPTAINS_CHAIR = block("captains_chair", new net.get900.pixelpirates.homestead.furniture.SeatBlock(wood(), 0.45, b(2, 0, 2, 14, 10, 14), b(2, 10, 12, 14, 22, 14)), true);
    public static final Block BARREL_STOOL = block("barrel_stool", new net.get900.pixelpirates.homestead.furniture.SeatBlock(wood(), 0.55, b(3, 0, 3, 13, 10, 13)), true);

    // ================================================================== #3 TREASURE HOARD
    public static final Block TREASURE_HOARD = block("treasure_hoard", new net.get900.pixelpirates.homestead.hoard.TreasureHoardBlock(
            AbstractBlock.Settings.create().mapColor(MapColor.GOLD).strength(1.5f, 6f).sounds(BlockSoundGroup.CHAIN).nonOpaque()
                    .luminance(s -> s.get(net.get900.pixelpirates.homestead.hoard.TreasureHoardBlock.LEVEL) >= 5 ? 4 : 0)), true);

    // ================================================================== #13 FISHING: traps + trophies
    public static final Block FISH_TRAP = block("fish_trap", new net.get900.pixelpirates.homestead.fishing.TrapBlock(AbstractBlock.Settings.create().mapColor(MapColor.PALE_YELLOW)
            .strength(0.6f).sounds(BlockSoundGroup.WOOL).nonOpaque().burnable(), false), true);
    public static final Block LOBSTER_POT = block("lobster_pot", new net.get900.pixelpirates.homestead.fishing.TrapBlock(AbstractBlock.Settings.create().mapColor(MapColor.BROWN)
            .strength(1.0f).sounds(BlockSoundGroup.WOOD).nonOpaque().burnable(), true), true);
    static Block trophy(String name) {
        return block(name, new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(AbstractBlock.Settings.create().mapColor(MapColor.BROWN).strength(1.0f).sounds(BlockSoundGroup.WOOD)
                .nonOpaque(), false, b(0, 2, 13, 16, 14, 16)), true);
    }
    public static final Block GOLDEN_MARLIN_TROPHY = trophy("golden_marlin_trophy");
    public static final Block GHOST_SWORDFISH_TROPHY = trophy("ghost_swordfish_trophy");
    public static final Block COELACANTH_TROPHY = trophy("coelacanth_trophy");

    // ================================================================== #9 SALVAGE
    public static final Block SALVAGE_CRATE = block("salvage_crate", new net.get900.pixelpirates.homestead.salvage.SalvageCrateBlock(wood().strength(2.0f)), true);

    // ================================================================== #15 TRADING POST
    public static final Block TRADING_POST = block("trading_post", new net.get900.pixelpirates.homestead.trade.TradingPostBlock(wood().strength(2.5f)), true);

    // ================================================================== #16 BOUNTY BOARD
    public static final Block BOUNTY_BOARD = block("bounty_board", new net.get900.pixelpirates.homestead.bounty.BountyBoardBlock(wood()), true);

    // ================================================================== #1 HIDEOUTS + #4 MOORING
    public static final Block JOLLY_ROGER = block("jolly_roger", new net.get900.pixelpirates.homestead.hideout.JollyRogerBlock(wood().strength(3.0f)), true);
    public static final Block MOORING_POST = block("mooring_post", new net.get900.pixelpirates.homestead.hideout.MooringPostBlock(
            AbstractBlock.Settings.create().mapColor(MapColor.BROWN).strength(3.0f).sounds(BlockSoundGroup.WOOD).nonOpaque()), true);

    // ================================================================== #25 SHIP UPGRADES: figureheads + sail canvas
    public static final Block MERMAID_FIGUREHEAD = block("mermaid_figurehead", new net.get900.pixelpirates.homestead.ship.FigureheadBlock(wood(), net.get900.pixelpirates.homestead.ship.FigureheadBlock.Kind.MERMAID), true);
    public static final Block KRAKEN_FIGUREHEAD = block("kraken_figurehead", new net.get900.pixelpirates.homestead.ship.FigureheadBlock(wood(), net.get900.pixelpirates.homestead.ship.FigureheadBlock.Kind.KRAKEN), true);
    public static final Block DREAD_SKULL_FIGUREHEAD = block("dread_skull_figurehead", new net.get900.pixelpirates.homestead.ship.FigureheadBlock(wood(), net.get900.pixelpirates.homestead.ship.FigureheadBlock.Kind.DREAD_SKULL), true);
    public static final Block NAVY_EAGLE_FIGUREHEAD = block("navy_eagle_figurehead", new net.get900.pixelpirates.homestead.ship.FigureheadBlock(wood(), net.get900.pixelpirates.homestead.ship.FigureheadBlock.Kind.NAVY_EAGLE), true);
    static AbstractBlock.Settings canvas() { return AbstractBlock.Settings.copy(Blocks.WHITE_WOOL); }
    public static final Block WHITE_SAIL_CANVAS = block("white_sail_canvas", new Block(canvas()), true);
    public static final Block BLACK_SAIL_CANVAS = block("black_sail_canvas", new Block(canvas()), true);
    public static final Block CRIMSON_SAIL_CANVAS = block("crimson_sail_canvas", new Block(canvas()), true);
    public static final Block STRIPED_SAIL_CANVAS = block("striped_sail_canvas", new Block(canvas()), true);
    public static final Block JOLLY_ROGER_SAIL_CANVAS = block("jolly_roger_sail_canvas", new Block(canvas()), true);

    // ================================================================== EXTRA: ROULETTE
    public static final Block ROULETTE_TABLE = block("roulette_table", new net.get900.pixelpirates.homestead.roulette.RouletteTableBlock(wood().strength(2.5f)), true);

    public static void init() {}
}
