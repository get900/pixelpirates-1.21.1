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

    // MOB TROPHIES (2026-10-01): the creature's own model mounted on a plaque - rare kill drops (MobTrophyBlock)
    static Block mobTrophy(String name, net.get900.pixelpirates.homestead.trophy.MobTrophyBlock.Mount m) {
        return block(name, new net.get900.pixelpirates.homestead.trophy.MobTrophyBlock(AbstractBlock.Settings.create().mapColor(MapColor.BROWN)
                .strength(1.0f).sounds(BlockSoundGroup.WOOD).nonOpaque(), m), true);
    }
    private static net.get900.pixelpirates.homestead.trophy.MobTrophyBlock.Mount mount(String mob, float scale, float cy, float cz, float depth, boolean profile, String idle) {
        return new net.get900.pixelpirates.homestead.trophy.MobTrophyBlock.Mount(mob, scale, 0, cy, cz, depth, profile, idle);
    }
    public static final Block SHARK_TROPHY = mobTrophy("shark_trophy", mount("shark", 0.37f, 4.2f, 6.5f, 0.18f, true, "swim"));
    public static final Block REEFBACK_TROPHY = mobTrophy("reefback_trophy", mount("reefback_fish", 0.45f, 10f, -0.5f, 0.13f, true, "idle"));
    public static final Block LAVA_CRAB_TROPHY = mobTrophy("lava_crab_trophy", mount("lava_crab", 0.36f, 7.85f, -1f, 0.23f, false, "idle"));
    public static final Block GHOST_SHARK_TROPHY = mobTrophy("ghost_shark_trophy", mount("ghost_shark", 0.33f, 10.5f, 1.5f, 0.23f, true, "idle"));
    public static final Block ANGLER_TROPHY = mobTrophy("angler_trophy", mount("abyssal_angler", 0.44f, 11.25f, 4.25f, 0.14f, true, "idle"));
    public static final Block ABYSS_EEL_TROPHY = mobTrophy("abyss_eel_trophy", mount("abyss_eel", 0.19f, 5.5f, 24.5f, 0.39f, true, "idle"));

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

    // ================================================================== THE GROG BARREL (2026-10-01): tavern games + decor
    static AbstractBlock.Settings glassware() { return AbstractBlock.Settings.create().mapColor(MapColor.BROWN).strength(0.3f).sounds(BlockSoundGroup.GLASS).nonOpaque(); }
    public static final Block TAVERN_SIGN = block("tavern_sign", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(wood().strength(1.0f), false, b(6, 2, 0, 10, 16, 16)), true);
    public static final Block CHANDLERY_SIGN = block("chandlery_sign", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(wood().strength(1.0f), false, b(6, 2, 0, 10, 16, 16)), true);
    public static final Block BAKERY_SIGN = block("bakery_sign", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(wood().strength(1.0f), false, b(6, 2, 0, 10, 16, 16)), true);
    public static final Block HARBOUR_SIGN = block("harbour_sign", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(wood().strength(1.0f), false, b(6, 2, 0, 10, 16, 16)), true);
    public static final Block WAREHOUSE_SIGN = block("warehouse_sign", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(wood().strength(1.0f), false, b(6, 2, 0, 10, 16, 16)), true);
    public static final Block DISTILLERY_SIGN = block("distillery_sign", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(wood().strength(1.0f), false, b(6, 2, 0, 10, 16, 16)), true);
    public static final Block FISH_SIGN = block("fish_sign", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(wood().strength(1.0f), false, b(6, 2, 0, 10, 16, 16)), true);
    public static final Block INN_SIGN = block("inn_sign", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(wood().strength(1.0f), false, b(6, 2, 0, 10, 16, 16)), true);
    // the aviary perch (2026-10-03): a jungle branch out from a wall, wall at the back (+z); tagged LOGS so wild parrots fly onto it
    public static final Block PERCH_BRANCH = block("perch_branch", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(wood().strength(1.5f).nonOpaque(), true, b(6.5, 6, 0, 9.5, 9, 16), b(5, 5, 15, 11, 10, 16)), true);
    // the PARROT ROOST (parrot types phase 4): opens your parrot collection (homestead/parrot/ParrotCollection)
    public static final Block PARROT_ROOST = block("parrot_roost", new net.get900.pixelpirates.homestead.parrot.ParrotRoostBlock(wood().strength(1.5f).nonOpaque()), true);
    // the harbour (homestead/harbour/HarbourDues): dues ledger, berth bollards, the dues board
    public static final Block DUES_LEDGER = block("dues_ledger", new net.get900.pixelpirates.homestead.harbour.HarbourBlocks.DuesLedger(wood().strength(2.0f)), true);
    public static final Block BERTH_BOLLARD = block("berth_bollard", new net.get900.pixelpirates.homestead.harbour.HarbourBlocks.BerthBollard(
            AbstractBlock.Settings.create().mapColor(MapColor.BLACK).strength(3.0f, 6.0f).sounds(BlockSoundGroup.METAL).nonOpaque()), true);
    public static final Block DUES_BOARD = block("dues_board", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(wood().strength(1.0f), false, b(0, 0, 14, 16, 16, 16)), true);
    public static final Block DOCK_SIGN = block("dock_sign", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(wood().strength(1.0f), false, b(6, 2, 0, 10, 16, 16)), true);
    // the chapel (homestead/chapel): pews, the bell rope, the organ, and its furnishings
    static AbstractBlock.Settings stoneDecor() { return AbstractBlock.Settings.create().mapColor(MapColor.STONE_GRAY).strength(2.0f, 6.0f).sounds(BlockSoundGroup.STONE).nonOpaque(); }
    public static final Block CHAPEL_PEW = block("chapel_pew", new net.get900.pixelpirates.homestead.chapel.ChapelBlocks.Pew(wood().strength(2.0f)), true);
    public static final Block BELL_ROPE = block("bell_rope", new net.get900.pixelpirates.homestead.chapel.ChapelBlocks.BellRope(
            AbstractBlock.Settings.create().mapColor(MapColor.BROWN).strength(0.5f).sounds(BlockSoundGroup.WOOL).nonOpaque().noCollision()), true);
    public static final Block ORGAN_CONSOLE = block("organ_console", new net.get900.pixelpirates.homestead.chapel.ChapelBlocks.OrganConsole(wood().strength(2.5f)), true);
    public static final Block ORGAN_PIPES = block("organ_pipes", new net.get900.pixelpirates.homestead.chapel.ChapelBlocks.OrganPipes(
            AbstractBlock.Settings.create().mapColor(MapColor.GOLD).strength(2.5f).sounds(BlockSoundGroup.METAL).nonOpaque()), true);
    public static final Block CHAPEL_ALTAR = block("chapel_altar", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(stoneDecor(), true, b(0, 0, 2, 16, 15, 14)), true);
    public static final Block ALTAR_CROSS = block("altar_cross", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(
            AbstractBlock.Settings.create().mapColor(MapColor.GOLD).strength(1.0f).sounds(BlockSoundGroup.METAL).nonOpaque(), false, b(5, 0, 6, 11, 16, 10)), true);
    public static final Block WALL_CROSS = block("wall_cross", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(wood().strength(1.0f), false, b(3, 1, 14, 13, 15, 16)), true);
    public static final Block CANDELABRA = block("candelabra", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(
            AbstractBlock.Settings.create().mapColor(MapColor.GOLD).strength(1.0f).sounds(BlockSoundGroup.METAL).nonOpaque().luminance(s -> 13), false, b(4, 0, 4, 12, 16, 12)), true);
    public static final Block VOTIVE_RACK = block("votive_rack", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(
            AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY).strength(1.5f).sounds(BlockSoundGroup.METAL).nonOpaque().luminance(s -> 11), true, b(0, 0, 4, 16, 12, 12)), true);
    public static final Block BAPTISMAL_FONT = block("baptismal_font", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(stoneDecor(), true, b(1, 0, 1, 15, 14, 15)), true);
    public static final Block HYMN_BOARD = block("hymn_board", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(wood().strength(1.0f), false, b(2, 0, 14, 14, 16, 16)), true);
    public static final Block MEMORIAL_PLAQUE = block("memorial_plaque", new net.get900.pixelpirates.homestead.chapel.MemorialPlaqueBlock(stoneDecor(), false, b(1, 3, 15, 15, 13, 16)), true);
    public static final Block VOTIVE_SHIP = block("votive_ship", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(wood().strength(0.5f), false, b(2, 2, 4, 14, 16, 12)), true);
    // the governor's residence (tools/gen_manor_assets.py): marble statues, urns, chandeliers, the portrait
    static AbstractBlock.Settings marble() { return AbstractBlock.Settings.create().mapColor(MapColor.WHITE).strength(3.0f, 6.0f).sounds(BlockSoundGroup.CALCITE).nonOpaque().requiresTool(); }
    public static final Block GOVERNOR_STATUE = block("governor_statue", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(marble(), true, b(2, 0, 3, 14, 16, 13)), true);
    public static final Block LION_STATUE = block("lion_statue", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(marble(), true, b(1, 0, 1, 15, 16, 15)), true);
    public static final Block SEA_GOD_STATUE = block("sea_god_statue", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(marble(), true, b(1, 0, 2, 15, 16, 14)), true);
    public static final Block MARBLE_BUST = block("marble_bust", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(marble(), true, b(4, 0, 4, 12, 16, 12)), true);
    public static final Block GARDEN_URN = block("garden_urn", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(marble(), true, b(3, 0, 3, 13, 14, 13)), true);
    public static final Block CRYSTAL_CHANDELIER = block("crystal_chandelier", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(
            AbstractBlock.Settings.create().mapColor(MapColor.GOLD).strength(0.8f).sounds(BlockSoundGroup.AMETHYST_BLOCK).nonOpaque().noCollision().luminance(s -> 15), false, b(1, 0, 1, 15, 16, 15)), true);
    public static final Block GOVERNOR_PORTRAIT = block("governor_portrait", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(wood().strength(0.8f), false, b(0, 0, 15, 16, 16, 16)), true);
    // the watch house (tools/gen_watch_assets.py): its hanging sign, wanted posters, weapon racks
    public static final Block WATCH_SIGN = block("watch_sign", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(wood().strength(1.0f), false, b(6, 2, 0, 10, 16, 16)), true);
    public static final Block WANTED_POSTER = block("wanted_poster", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(wood().strength(0.5f), false, b(0, 0, 15, 16, 16, 16)), true);
    public static final Block WEAPON_RACK = block("weapon_rack", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(wood().strength(1.5f), false, b(0, 0, 12, 16, 16, 16)), true);
    // the forge (homestead/forge, tools/gen_forge_assets.py): hearth, anvil, bellows, the pattern board, the smithy sign
    public static final Block FORGE_HEARTH = block("forge_hearth", new net.get900.pixelpirates.homestead.forge.ForgeHearthBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.DARK_RED).strength(3.5f, 6.0f).sounds(BlockSoundGroup.STONE).requiresTool().luminance(s -> s.get(net.get900.pixelpirates.homestead.forge.ForgeHearthBlock.FUEL) > 0 ? 13 : 0)), true);
    public static final Block FORGE_ANVIL = block("forge_anvil", new net.get900.pixelpirates.homestead.forge.ForgeAnvilBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.IRON_GRAY).strength(5.0f, 1200.0f).sounds(BlockSoundGroup.ANVIL).requiresTool().nonOpaque(), b(0, 0, 3, 16, 16, 13)), true);
    public static final Block BELLOWS = block("bellows", new net.get900.pixelpirates.homestead.forge.BellowsBlock(wood().strength(1.5f), b(1, 0, 2, 15, 10, 14)), true);
    public static final Block PATTERN_BOARD = block("pattern_board", new net.get900.pixelpirates.homestead.forge.PatternBoardBlock(wood().strength(1.0f), b(0, 0, 14, 16, 16, 16)), true);
    // props (tools/gen_props_assets.py): the telescope on its tripod, a sea chart framed for the wall
    public static final Block TELESCOPE = block("telescope", new net.get900.pixelpirates.homestead.furniture.TelescopeBlock(AbstractBlock.Settings.create()
            .mapColor(MapColor.ORANGE).strength(1.5f).sounds(BlockSoundGroup.COPPER).nonOpaque(), b(2, 0, 2, 14, 16, 14)), true);
    // ships in bottles (gen_props_assets.py bottles, 2026-10-04): the glassblower's wares - sloop / brig / galleon, the ghost ship from loot only
    static Block shipInBottle(String kind, int light) {
        return block("ship_in_bottle_" + kind, new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(glassware().luminance(s -> light), true, b(1, 0, 4, 16, 8, 12)), true);
    }
    public static final Block SHIP_IN_BOTTLE_SLOOP = shipInBottle("sloop", 0);
    public static final Block SHIP_IN_BOTTLE_BRIG = shipInBottle("brig", 0);
    public static final Block SHIP_IN_BOTTLE_GALLEON = shipInBottle("galleon", 0);
    public static final Block SHIP_IN_BOTTLE_GHOST = shipInBottle("ghost", 6);
    // the tattooist's chair (townhouse #33, homestead/tattoo): use it to get inked
    // the ship's-cat keeper's counter (townhouse #36, homestead/cat/Cattery): buy one of today's cats
    // the easel (paintings), the swings, the barber's chair (2026-10-04)
    public static final Block EASEL = block("easel", new net.get900.pixelpirates.homestead.art.EaselBlock(wood().strength(1.5f)), true);
    public static final Block SWING = block("swing", new net.get900.pixelpirates.homestead.swing.SwingBlock(wood().strength(1.5f), false), true);
    public static final Block HANGING_SWING = block("hanging_swing", new net.get900.pixelpirates.homestead.swing.SwingBlock(wood().strength(1.0f).noCollision(), true), true);
    // chess (2026-10-04): the table in the toymaker's shop, the giant floor set in the park
    /** The street lamp (homestead/town/StreetLampBlock): dark by day, lit at dusk by the lamplighter. */
    public static final Block STREET_LAMP = block("street_lamp", new net.get900.pixelpirates.homestead.town.StreetLampBlock(
            AbstractBlock.Settings.create().mapColor(MapColor.IRON_GRAY).requiresTool().strength(3.5f).sounds(BlockSoundGroup.LANTERN).nonOpaque()), true);
    /** The Commodore's chess trophy: beat Commodore Pettigrew at chess (homestead/town). */
    // DARTS (2026-10-05): a wall board with a game of 301 in it (homestead/darts)
    public static final Block DARTBOARD = block("dartboard", new net.get900.pixelpirates.homestead.darts.DartboardBlock(wood().strength(1.0f)), true);
    public static final Block CHESS_TROPHY = block("chess_trophy", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(stoneDecor().sounds(BlockSoundGroup.METAL), false, b(4, 0, 4, 12, 16, 12)), true);
    public static final Block CHESS_TABLE = block("chess_table", new net.get900.pixelpirates.homestead.chess.ChessTableBlock(wood().strength(1.5f)), true);
    public static final Block GIANT_CHESS = block("giant_chess", new net.get900.pixelpirates.homestead.chess.GiantChessBlock(
            AbstractBlock.Settings.create().mapColor(MapColor.STONE_GRAY).strength(2.0f, 6.0f).sounds(BlockSoundGroup.STONE).nonOpaque().requiresTool()), true);
    public static final Block BARBER_CHAIR = block("barber_chair", new net.get900.pixelpirates.homestead.beard.BarberChairBlock(wood().strength(2.0f)), true);
    public static final Block CATTERY_COUNTER = block("cattery_counter", new net.get900.pixelpirates.homestead.cat.CatteryCounterBlock(wood().strength(2.0f)), true);
    /** Captain Wren's music box - the Beach Wreck's easter egg (homestead/wreck, not craftable). */
    public static final Block WREN_MUSIC_BOX = block("wren_music_box", new net.get900.pixelpirates.homestead.wreck.MusicBoxBlock(wood().strength(1.0f).nonOpaque()), true);
    public static final Block TATTOO_CHAIR = block("tattoo_chair", new net.get900.pixelpirates.homestead.tattoo.TattooChairBlock(wood().strength(2.0f)), true);
    public static final Block SEA_CHART = block("sea_chart", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(wood().strength(0.8f), false, b(0, 0, 15, 16, 16, 16)), true);
    public static final Block PARK_SIGN = block("park_sign", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(wood().strength(1.0f), false, b(6, 2, 0, 10, 16, 16)), true);
    public static final Block SMITHY_SIGN = block("smithy_sign", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(wood().strength(1.0f), false, b(6, 2, 0, 10, 16, 16)), true);
    public static final Block DRINKS_MENU = block("drinks_menu", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(wood().strength(1.0f), false, b(0, 0, 14, 16, 16, 16)), true);
    public static final Block TANKARD = block("tankard", new net.get900.pixelpirates.homestead.tavern.TavernDecor.Tankards(wood().strength(0.5f)), true);
    public static final Block SPIRIT_BOTTLES = block("spirit_bottles", new net.get900.pixelpirates.homestead.tavern.TavernDecor.Bottles(glassware()), true);
    public static final Block TAVERN_KEG = block("tavern_keg", new net.get900.pixelpirates.homestead.tavern.TavernDecor.Keg(wood().strength(2.0f)), true);
    public static final Block DICE_CUP = block("dice_cup", new net.get900.pixelpirates.homestead.furniture.FurnitureBlock(wood().strength(0.5f), true, b(4, 0, 4, 12, 6, 12)), true);
    public static final Block LIARS_DICE_TABLE = block("liars_dice_table", new net.get900.pixelpirates.homestead.tavern.GameTableBlock(wood().strength(2.5f),
            net.get900.pixelpirates.homestead.tavern.LiarsDiceBlockEntity::new, () -> HomesteadBlockEntities.LIARS_DICE, b(0, 0, 0, 16, 15, 16)), true);
    public static final Block CROWN_ANCHOR_TABLE = block("crown_anchor_table", new net.get900.pixelpirates.homestead.tavern.GameTableBlock(wood().strength(2.5f),
            net.get900.pixelpirates.homestead.tavern.CrownAnchorBlockEntity::new, () -> HomesteadBlockEntities.CROWN_ANCHOR, b(0, 0, 0, 16, 15, 16)), true);

    public static void init() {}
}
