package net.get900.pixelpirates.block;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.get900.pixelpirates.PixelPirates;

import net.get900.pixelpirates.block.custom.AmbientLeavesBlock;
import net.get900.pixelpirates.block.custom.AmbientParticleBlock;
import net.get900.pixelpirates.block.custom.AnchorBlock;
import net.get900.pixelpirates.block.custom.CannonBlock;
import net.get900.pixelpirates.block.custom.CannonBlockEntity;
import net.get900.pixelpirates.block.custom.GrogBarrelBlock;
import net.get900.pixelpirates.block.custom.HammockBlock;
import net.get900.pixelpirates.block.custom.HangingFruitBlock;
import net.get900.pixelpirates.block.custom.ShipBedrollBlock;
import net.get900.pixelpirates.block.custom.ShipHelmBlock;
import net.get900.pixelpirates.block.custom.ShipHelmBlockEntity;
import net.get900.pixelpirates.block.custom.ShipMastBlock;
import net.get900.pixelpirates.block.custom.ShipWaterlineBlock;
import net.get900.pixelpirates.block.custom.ShipwrightBlock;
import net.get900.pixelpirates.block.custom.WaterLightBlock;
import net.get900.pixelpirates.world.ModConfiguredFeatures;
import net.get900.pixelpirates.world.tree.ShorewoodSaplingGenerator;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public class ModBlocks {
    //General
    public static final Block HAMMOCK = registerBlock("hammock",
            new HammockBlock(AbstractBlock.Settings.create().strength(0.5f).nonOpaque()
                    .sounds(BlockSoundGroup.WOOL)));

    public static final Block SHIP_BEDROLL = registerBlock("ship_bedroll",
            new ShipBedrollBlock(AbstractBlock.Settings.create().strength(0.5f).nonOpaque()
                    .sounds(BlockSoundGroup.WOOL)));

    public static final Block SHIPWRIGHT_TABLE = registerBlock("shipwright_table",
            new ShipwrightBlock(AbstractBlock.Settings.create().strength(3.5f)
                    .requiresTool().sounds(BlockSoundGroup.WOOD)));

    public static final Block SHIP_CANNON = registerBlock("ship_cannon",
            new CannonBlock(AbstractBlock.Settings.create().strength(3.5f).nonOpaque()
                    .requiresTool().sounds(BlockSoundGroup.METAL)));

    /** Garrisoned wall cannon - fires terrain-safe shots at players while a fort captain lives (FortCannonBlockEntity). */
    public static final Block FORT_CANNON = registerBlock("fort_cannon",
            new net.get900.pixelpirates.block.custom.FortCannonBlock(AbstractBlock.Settings.create().strength(3.5f).nonOpaque()
                    .requiresTool().sounds(BlockSoundGroup.METAL)));

    /** Vault seal: unbreakable, blast-proof except to thrown dynamite (DynamiteEntity#clearRubble). */
    public static final Block BLAST_RUBBLE = registerBlock("blast_rubble",
            new net.get900.pixelpirates.block.custom.BlastRubbleBlock(AbstractBlock.Settings.create().strength(-1.0f, 3600000.0f)
                    .dropsNothing().sounds(BlockSoundGroup.STONE).mapColor(MapColor.STONE_GRAY)));

    /** Sea Serpent armour anchor - only a Tidebreaker cracks it (SerpentWardBlock, SeaSerpentEntity). */
    public static final Block SERPENT_WARD = registerBlock("serpent_ward",
            new net.get900.pixelpirates.block.custom.SerpentWardBlock(AbstractBlock.Settings.create().strength(50.0f, 3600000.0f)
                    .dropsNothing().luminance(state -> 12).sounds(BlockSoundGroup.AMETHYST_BLOCK).mapColor(MapColor.CYAN)));

    /** Crucible cistern valve - pours a quenching cascade (Molten Warlord fight, QuenchValveBlock). */
    public static final Block QUENCH_VALVE = registerBlock("quench_valve",
            new net.get900.pixelpirates.block.custom.QuenchValveBlock(AbstractBlock.Settings.create().strength(-1.0f, 3600000.0f)
                    .dropsNothing().sounds(BlockSoundGroup.METAL).mapColor(MapColor.CYAN)));

    /** Sunken Court floodgate wheel - drains the Abyssal King's hall (TideSluiceBlock, AbyssalKingEntity). */
    public static final Block TIDE_SLUICE = registerBlock("tide_sluice",
            new net.get900.pixelpirates.block.custom.TideSluiceBlock(AbstractBlock.Settings.create().strength(-1.0f, 3600000.0f)
                    .dropsNothing().luminance(state -> 7).sounds(BlockSoundGroup.METAL).mapColor(MapColor.CYAN)));

    /** Whaler's harpoon gun - hooks the Bloodfin (HarpoonWinchBlock, BloodfinEntity). */
    public static final Block HARPOON_WINCH = registerBlock("harpoon_winch",
            new net.get900.pixelpirates.block.custom.HarpoonWinchBlock(AbstractBlock.Settings.create().strength(3.0f, 6.0f)
                    .nonOpaque().sounds(BlockSoundGroup.WOOD).mapColor(MapColor.BROWN)));

    /** Gallows Pit wall anchor - where the Chained Revenant's torn limbs hang (RevenantPartEntity). */
    public static final Block MANACLE_ANCHOR = registerBlock("manacle_anchor",
            new net.get900.pixelpirates.block.custom.ManacleAnchorBlock(AbstractBlock.Settings.create().strength(-1.0f, 3600000.0f)
                    .dropsNothing().sounds(BlockSoundGroup.CHAIN).mapColor(MapColor.IRON_GRAY)));

    // ---- The Titan's Chest (Abyssal Heart, boss 9/10) - see world/dungeon/TitansChestLayout
    /** The walls of the heart's chambers and the arteries: meat that pulses (animated texture). */
    public static final Block LIVING_FLESH = registerBlock("living_flesh",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).sounds(BlockSoundGroup.WART_BLOCK).mapColor(MapColor.DARK_RED)));
    /** Glowing vessel: flares on every heartbeat (FleshVeinBlock.LIT, driven by AbyssalHeartEntity). */
    public static final Block FLESH_VEIN = registerBlock("flesh_vein",
            new net.get900.pixelpirates.block.custom.FleshVeinBlock(AbstractBlock.Settings.create().strength(1.5f, 6.0f)
                    .luminance(state -> state.get(net.get900.pixelpirates.block.custom.FleshVeinBlock.LIT) ? 15 : 5)
                    .sounds(BlockSoundGroup.WART_BLOCK).mapColor(MapColor.RED)));
    /** A shut heart valve - the Heart slams these into the chamber doorways on every beat. Unbreakable. */
    public static final Block HEART_VALVE = registerBlock("heart_valve",
            new Block(AbstractBlock.Settings.create().strength(-1.0f, 3600000.0f).dropsNothing()
                    .sounds(BlockSoundGroup.WART_BLOCK).mapColor(MapColor.DARK_RED)));
    /** The ancients' pacemaker coil (GalvanicPylonBlock): strike it on the beat. Unbreakable. */
    public static final Block GALVANIC_PYLON = registerBlock("galvanic_pylon",
            new net.get900.pixelpirates.block.custom.GalvanicPylonBlock(AbstractBlock.Settings.create().strength(-1.0f, 3600000.0f)
                    .dropsNothing().luminance(state -> switch (state.get(net.get900.pixelpirates.block.custom.GalvanicPylonBlock.STATE)) {
                        case 1 -> 12; case 2 -> 15; default -> 4; })
                    .sounds(BlockSoundGroup.COPPER).mapColor(MapColor.TEAL)));

    // ---- The Leviathan hunt (boss 10/10) - see world/leviathan (LeviathanBlock routes every use to LeviathanHunt)
    public static final Block RIFT_SEAL = registerBlock("rift_seal", leviathanBlock(net.get900.pixelpirates.block.custom.LeviathanBlock.Kind.SEAL, 10, MapColor.CYAN));
    public static final Block ANCHOR_WINCH = registerBlock("anchor_winch", leviathanBlock(net.get900.pixelpirates.block.custom.LeviathanBlock.Kind.WINCH, 3, MapColor.IRON_GRAY));
    public static final Block TIDE_BELL = registerBlock("tide_bell", leviathanBlock(net.get900.pixelpirates.block.custom.LeviathanBlock.Kind.BELL, 6, MapColor.GOLD));
    public static final Block BANE_BALLISTA = registerBlock("bane_ballista", leviathanBlock(net.get900.pixelpirates.block.custom.LeviathanBlock.Kind.BALLISTA, 4, MapColor.BROWN));
    public static final Block WATCHERS_HORN = registerBlock("watchers_horn", leviathanBlock(net.get900.pixelpirates.block.custom.LeviathanBlock.Kind.HORN, 4, MapColor.PALE_YELLOW));

    private static Block leviathanBlock(net.get900.pixelpirates.block.custom.LeviathanBlock.Kind kind, int light, MapColor color) {
        return new net.get900.pixelpirates.block.custom.LeviathanBlock(kind, AbstractBlock.Settings.create().strength(-1.0f, 3600000.0f)
                .dropsNothing().nonOpaque().luminance(state -> light).sounds(BlockSoundGroup.METAL).mapColor(color));
    }

    /** The hangman's greatsword driven into a boulder under the Chained Revenant's gallows: pull it to take the sword and
     *  wake him (GallowbrandStoneBlock). No block item - it only exists in the Gallows Pit. */
    public static final Block GALLOWBRAND_STONE = Registry.register(Registries.BLOCK, new Identifier(PixelPirates.MOD_ID, "gallowbrand_stone"),
            new net.get900.pixelpirates.block.custom.GallowbrandStoneBlock(AbstractBlock.Settings.create().strength(-1.0f, 3600000.0f)
                    .dropsNothing().nonOpaque().luminance(state -> 7).sounds(BlockSoundGroup.DEEPSLATE).mapColor(MapColor.BLACK)));

    // ---- The Flying Dutchman (Ghost Captain, boss 4/10) - see world/GhostShipDesign
    public static final Block GHOSTWOOD_LOG = registerBlock("ghostwood_log",
            new PillarBlock(AbstractBlock.Settings.create().strength(2.0f).nonOpaque().sounds(BlockSoundGroup.WOOD).mapColor(MapColor.CYAN)));
    public static final Block GHOSTWOOD_PLANKS = registerBlock("ghostwood_planks",
            new Block(AbstractBlock.Settings.create().strength(2.0f, 3.0f).nonOpaque().sounds(BlockSoundGroup.WOOD).mapColor(MapColor.CYAN)));
    /** Tattered glowing sailcloth (cutout texture with holes). */
    public static final Block SPECTRAL_SAIL = registerBlock("spectral_sail",
            new Block(AbstractBlock.Settings.create().strength(0.8f).nonOpaque().luminance(state -> 6)
                    .sounds(BlockSoundGroup.WOOL).mapColor(MapColor.CYAN)));
    /** The Dutchman's drive: each ghost mast pulls like two ship masts (GhostShipDesign). */
    public static final Block GHOST_MAST = registerBlock("ghost_mast",
            new Block(AbstractBlock.Settings.create().strength(2.0f).nonOpaque().luminance(state -> 9)
                    .sounds(BlockSoundGroup.WOOD).mapColor(MapColor.CYAN)));
    public static final Block GHOST_CANNON = registerBlock("ghost_cannon",
            new net.get900.pixelpirates.block.custom.GhostCannonBlock(AbstractBlock.Settings.create().strength(3.5f).nonOpaque()
                    .requiresTool().luminance(state -> 4).sounds(BlockSoundGroup.METAL)));
    /** Glowing float the Drowned Bell sits on: ringing that bell summons the Dutchman (GhostShipEncounter). */
    public static final Block PHANTOM_BUOY = registerBlock("phantom_buoy",
            new Block(AbstractBlock.Settings.create().strength(-1.0f, 3600000.0f).dropsNothing().luminance(state -> 12)
                    .sounds(BlockSoundGroup.WOOD).mapColor(MapColor.CYAN)));

    public static final Block SHIP_MAST = registerBlock("ship_mast",
            new ShipMastBlock(AbstractBlock.Settings.create().strength(2.0f)
                    .requiresTool().sounds(BlockSoundGroup.WOOD)));

    public static final Block SHIP_HELM = registerBlock("ship_helm",
            new ShipHelmBlock(AbstractBlock.Settings.create().strength(3.5f).nonOpaque()
                    .requiresTool().sounds(BlockSoundGroup.WOOD)));

    public static final Block SHIP_WATERLINE = registerBlock("ship_waterline",
            new ShipWaterlineBlock(AbstractBlock.Settings.create().strength(2.0f)
                    .sounds(BlockSoundGroup.WOOD)));

    public static BlockEntityType<ShipHelmBlockEntity> SHIP_HELM_BLOCK_ENTITY;
    public static BlockEntityType<CannonBlockEntity>   CANNON_BLOCK_ENTITY;
    public static BlockEntityType<net.get900.pixelpirates.block.custom.FortCannonBlockEntity> FORT_CANNON_BLOCK_ENTITY;
    public static BlockEntityType<net.get900.pixelpirates.block.custom.GallowbrandStoneBlockEntity> GALLOWBRAND_STONE_ENTITY;

    public static final Block GROG_BARREL = registerBlock("grog_barrel",
            new GrogBarrelBlock(AbstractBlock.Settings.create().strength(1.0f)
                    .sounds(BlockSoundGroup.WOOD)));

    public static final Block WATER_LIGHT_BLOCK = registerBlock("water_light_block",
            new WaterLightBlock(AbstractBlock.Settings.create().strength(1f).requiresTool().luminance(state -> state.get(WaterLightBlock.CLICKED)
            ? 15 : 0)));

    //Temperate Shallows
    public static final Block DRIFTWOOD_BLOCK = registerBlock("driftwood_block",
            new PillarBlock(AbstractBlock.Settings.create().strength(1.0f)
                    .requiresTool().sounds(BlockSoundGroup.WOOD)));
    public static final Block SHOREWOOD_LOG = registerBlock("shorewood_log",
            new PillarBlock(AbstractBlock.Settings.create().strength(2.0f).mapColor(MapColor.SPRUCE_BROWN)));
    public static final Block SHOREWOOD_WOOD = registerBlock("shorewood_wood",
            new PillarBlock(AbstractBlock.Settings.create().strength(2.0f).mapColor(MapColor.SPRUCE_BROWN)));
    public static final Block STRIPPED_SHOREWOOD_LOG = registerBlock("stripped_shorewood_log",
            new PillarBlock(AbstractBlock.Settings.create().strength(2.0f).mapColor(MapColor.PALE_YELLOW)));
    public static final Block STRIPPED_SHOREWOOD_WOOD = registerBlock("stripped_shorewood_wood",
            new PillarBlock(AbstractBlock.Settings.create().strength(2.0f).mapColor(MapColor.PALE_YELLOW)));
    public static final Block SHOREWOOD_LEAVES = registerBlock("shorewood_leaves",
            new LeavesBlock(AbstractBlock.Settings.create().strength(0.2f).nonOpaque().mapColor(MapColor.DARK_GREEN)));
    public static final Block SHOREWOOD_SAPLING = registerBlock("shorewood_sapling",
            new SaplingBlock(new ShorewoodSaplingGenerator(),
                    AbstractBlock.Settings.copy(Blocks.OAK_SAPLING).mapColor(MapColor.DARK_GREEN)));
    public static final Block POTTED_SHOREWOOD_SAPLING = Registry.register(Registries.BLOCK,
            new Identifier(PixelPirates.MOD_ID,"potted_shorewood_sapling"), new FlowerPotBlock(SHOREWOOD_SAPLING,
                    AbstractBlock.Settings.create().nonOpaque().breakInstantly()));
    public static final Block BANANA_BLOCK = registerBlock("banana_block",
            new HangingFruitBlock(Block.createCuboidShape(2, 3, 2, 14, 16, 14),
                    AbstractBlock.Settings.create().strength(0.3f).nonOpaque().noCollision()
                            .sounds(BlockSoundGroup.AZALEA_LEAVES).mapColor(MapColor.YELLOW)));
    public static final Block COCONUT_BLOCK = registerBlock("coconut_block",
            new HangingFruitBlock(Block.createCuboidShape(4, 4, 4, 12, 16, 12),
                    AbstractBlock.Settings.create().strength(0.5f).nonOpaque()
                            .sounds(BlockSoundGroup.WOOD).mapColor(MapColor.BROWN)));

    //Deep Ocean

    //Volcanic Waters
    public static final Block ASHEN_LOG = registerBlock("ashen_log",
            new PillarBlock(AbstractBlock.Settings.create().strength(2.0f).mapColor(MapColor.DEEPSLATE_GRAY)));
    public static final Block ASHEN_WOOD = registerBlock("ashen_wood",
            new PillarBlock(AbstractBlock.Settings.create().strength(2.0f).mapColor(MapColor.DEEPSLATE_GRAY)));
    public static final Block STRIPPED_ASHEN_LOG = registerBlock("stripped_ashen_log",
            new PillarBlock(AbstractBlock.Settings.create().strength(2.0f).mapColor(MapColor.GRAY)));
    public static final Block STRIPPED_ASHEN_WOOD = registerBlock("stripped_ashen_wood",
            new PillarBlock(AbstractBlock.Settings.create().strength(2.0f).mapColor(MapColor.GRAY)));
    // ── Ghost Zone (Zone 4 — Cursed Seas) ──────────────────────────────────────
    // Stone / brick types
    public static final Block SHROUD_STONE = registerBlock("shroud_stone",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block DARK_SHROUD_STONE = registerBlock("dark_shroud_stone",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block DARKER_SHROUD_STONE = registerBlock("darker_shroud_stone",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block DARKER_SHROUD_STONE_VEIN = registerBlock("darker_shroud_stone_vein",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block SHROUD_STONE_BRICKS = registerBlock("shroud_stone_bricks",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block CRACKED_SHROUD_STONE_BRICKS = registerBlock("cracked_shroud_stone_bricks",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block FANCY_SHROUD_STONE = registerBlock("fancy_shroud_stone",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    // Carved rune stones
    public static final Block SHROUD_STONE_K = registerBlock("shroud_stone_k",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block SHROUD_STONE_M = registerBlock("shroud_stone_m",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block SHROUD_STONE_N = registerBlock("shroud_stone_n",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    // Ores / special
    public static final Block SHROUDED_ORE = registerBlock("shrouded_ore",
            new Block(AbstractBlock.Settings.create().strength(3.0f, 3.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block SUNKEN_ORE = registerBlock("sunken_ore",
            new Block(AbstractBlock.Settings.create().strength(3.0f, 3.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block SHROUDED_EMBLEM = registerBlock("shrouded_emblem",
            new Block(AbstractBlock.Settings.create().strength(2.0f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block DROWNED_CRYSTAL = registerBlock("drowned_crystal",
            new Block(AbstractBlock.Settings.create().strength(1.5f).requiresTool().sounds(BlockSoundGroup.AMETHYST_BLOCK)));
    public static final Block SHROUDED_DIRT = registerBlock("shrouded_dirt",
            new Block(AbstractBlock.Settings.create().strength(0.5f).sounds(BlockSoundGroup.GRAVEL)));
    // Wood / planks types
    public static final Block BONE_PLANKS = registerBlock("bone_planks",
            new Block(AbstractBlock.Settings.create().strength(2.0f, 3.0f).sounds(BlockSoundGroup.BONE)));
    public static final Block SHROUDED_PLANKS = registerBlock("shrouded_planks",
            new Block(AbstractBlock.Settings.create().strength(2.0f, 3.0f).sounds(BlockSoundGroup.WOOD)));
    public static final Block LIGHT_SHROUDED_PLANKS = registerBlock("light_shrouded_planks",
            new Block(AbstractBlock.Settings.create().strength(2.0f, 3.0f).sounds(BlockSoundGroup.WOOD)));
    public static final Block SPOOKY_PLANKS = registerBlock("spooky_planks",
            new Block(AbstractBlock.Settings.create().strength(2.0f, 3.0f).sounds(BlockSoundGroup.WOOD)));
    public static final Block SHROUDED_LOG = registerBlock("shrouded_log",
            new Block(AbstractBlock.Settings.create().strength(2.0f, 3.0f).sounds(BlockSoundGroup.WOOD)));

    // ── Ship / Pirate Zone (Zone 3 — Pirate Seas) ──────────────────────────────
    // Stone / brick types
    public static final Block PIRATE_STONE_BRICKS = registerBlock("pirate_stone_bricks",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block BOLTED_BRICKS = registerBlock("bolted_bricks",
            new Block(AbstractBlock.Settings.create().strength(2.0f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block PIRATE_GOLD_ORE = registerBlock("pirate_gold_ore",
            new Block(AbstractBlock.Settings.create().strength(3.0f, 3.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block EMERALD_TOKEN = registerBlock("emerald_token",
            new Block(AbstractBlock.Settings.create().strength(3.0f, 3.0f).requiresTool().sounds(BlockSoundGroup.AMETHYST_BLOCK)));
    // Decorative blocks
    public static final Block ANCHOR_BLOCK = registerBlock("anchor_block",
            new AnchorBlock(AbstractBlock.Settings.create().strength(2.0f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block SWORD_BLOCK = registerBlock("sword_block",
            new Block(AbstractBlock.Settings.create().strength(2.0f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block TREASURE_BLOCK = registerBlock("treasure_block",
            new Block(AbstractBlock.Settings.create().strength(2.0f, 6.0f).requiresTool().sounds(BlockSoundGroup.WOOD)));
    public static final Block MAP_BLOCK = registerBlock("map_block",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 3.0f).sounds(BlockSoundGroup.WOOD)));
    public static final Block PIRATE_DIARY_BLOCK = registerBlock("pirate_diary_block",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 3.0f).sounds(BlockSoundGroup.WOOD)));
    // Wood / planks types
    public static final Block WEATHERED_WOOD = registerBlock("weathered_wood",
            new Block(AbstractBlock.Settings.create().strength(2.0f, 3.0f).sounds(BlockSoundGroup.WOOD)));
    public static final Block PIRATE_PLANKS = registerBlock("pirate_planks",
            new Block(AbstractBlock.Settings.create().strength(2.0f, 3.0f).sounds(BlockSoundGroup.WOOD)));
    public static final Block DARK_PIRATE_PLANKS = registerBlock("dark_pirate_planks",
            new Block(AbstractBlock.Settings.create().strength(2.0f, 3.0f).sounds(BlockSoundGroup.WOOD)));
    public static final Block MOSSY_PIRATE_PLANKS = registerBlock("mossy_pirate_planks",
            new Block(AbstractBlock.Settings.create().strength(2.0f, 3.0f).sounds(BlockSoundGroup.WOOD)));
    public static final Block DESTROYED_PLANKS = registerBlock("destroyed_planks",
            new Block(AbstractBlock.Settings.create().strength(1.0f, 2.0f).sounds(BlockSoundGroup.WOOD)));
    public static final Block TORN_PLANKS = registerBlock("torn_planks",
            new Block(AbstractBlock.Settings.create().strength(1.0f, 2.0f).sounds(BlockSoundGroup.WOOD)));
    public static final Block SUNKEN_PLANKS = registerBlock("sunken_planks",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 3.0f).sounds(BlockSoundGroup.WOOD)));
    public static final Block PIRATE_TWIGS = registerBlock("pirate_twigs",
            new Block(AbstractBlock.Settings.create().strength(0.5f, 1.0f).sounds(BlockSoundGroup.WOOD)));

    // ── Volcanic Islands ───────────────────────────────────────────────────────
    public static final Block VOLCANIC_ROCK = registerBlock("volcanic_rock",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block VOLCANIC_ROCK_2 = registerBlock("volcanic_rock_2",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block VOLCANIC_ROCK_K = registerBlock("volcanic_rock_k",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block VOLCANIC_ROCK_N = registerBlock("volcanic_rock_n",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block VOLCANIC_ROCK_FLAGS = registerBlock("volcanic_rock_flags",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block VOLCANIC_ROCK_EMBLEM = registerBlock("volcanic_rock_emblem",
            new Block(AbstractBlock.Settings.create().strength(2.0f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block VOLCANIC_ROCK_CRYSTAL = registerBlock("volcanic_rock_crystal",
            new Block(AbstractBlock.Settings.create().strength(1.5f).requiresTool().sounds(BlockSoundGroup.AMETHYST_BLOCK)));
    public static final Block VOLCANIC_PLANKS = registerBlock("volcanic_planks",
            new Block(AbstractBlock.Settings.create().strength(2.0f, 3.0f).sounds(BlockSoundGroup.WOOD)));
    public static final Block BURNING_LOG = registerBlock("burning_log",
            new Block(AbstractBlock.Settings.create().strength(2.0f, 3.0f).sounds(BlockSoundGroup.WOOD)));
    public static final Block VOLCANIC_ROCK_BRICKS = registerBlock("volcanic_rock_bricks",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block VOLCANIC_STONE_BRICKS_VEIN = registerBlock("volcanic_stone_bricks_vein",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block MOLTEN_STONE_BRICKS = registerBlock("molten_stone_bricks",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));

    // ── Ethereal (Zone 5 — The Abyss) ─────────────────────────────────────────
    public static final Block ETHEREAL_STONE = registerBlock("ethereal_stone",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block ETHEREAL_PULSING_STONE = registerBlock("ethereal_pulsing_stone",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block ETHEREAL_PINK_STONE = registerBlock("ethereal_pink_stone",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block ETHEREAL_CRYSTAL = registerBlock("ethereal_crystal",
            new Block(AbstractBlock.Settings.create().strength(1.5f).requiresTool().sounds(BlockSoundGroup.AMETHYST_BLOCK)));
    public static final Block ETHEREAL_STONE_BRICKS = registerBlock("ethereal_stone_bricks",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block ETHEREAL_STONE_BRICKS_2 = registerBlock("ethereal_stone_bricks_2",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block ETHEREAL_EMBLEM = registerBlock("ethereal_emblem",
            new Block(AbstractBlock.Settings.create().strength(2.0f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block ETHEREAL_STONE_FLAGS = registerBlock("ethereal_stone_flags",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block ETHEREAL_STONE_N = registerBlock("ethereal_stone_n",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block ETHEREAL_STONE_K = registerBlock("ethereal_stone_k",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE)));
    public static final Block ETHEREAL_PLANKS = registerBlock("ethereal_planks",
            new Block(AbstractBlock.Settings.create().strength(2.0f, 3.0f).sounds(BlockSoundGroup.WOOD)));
    public static final Block ETHEREAL_LOG = registerBlock("ethereal_log",
            new Block(AbstractBlock.Settings.create().strength(2.0f, 3.0f).sounds(BlockSoundGroup.WOOD)));
    public static final Block ETHEREAL_GLOWING_BRICKS = registerBlock("ethereal_glowing_bricks",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().luminance(state -> 10).sounds(BlockSoundGroup.STONE)));
    public static final Block ETHEREAL_DIM_BRICKS = registerBlock("ethereal_dim_bricks",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().luminance(state -> 4).sounds(BlockSoundGroup.STONE)));

    // ── Phase flora & ambience (custom trees + biome decoration) ────────────────
    // Phase 1 — palms & shoreline
    public static final Block PALM_LOG = registerBlock("palm_log",
            new PillarBlock(AbstractBlock.Settings.create().strength(2.0f).sounds(BlockSoundGroup.WOOD).mapColor(MapColor.DIRT_BROWN)));
    public static final Block PALM_LEAVES = registerBlock("palm_leaves",
            new LeavesBlock(AbstractBlock.Settings.create().strength(0.2f).nonOpaque().sounds(BlockSoundGroup.GRASS).mapColor(MapColor.GREEN)));
    public static final Block SHELL_BLOCK = registerBlock("shell_block",
            new Block(AbstractBlock.Settings.create().strength(0.6f).sounds(BlockSoundGroup.SAND).mapColor(MapColor.PALE_YELLOW)));
    public static final Block TIDE_POOL_ROCK = registerBlock("tide_pool_rock",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE).mapColor(MapColor.STONE_GRAY)));
    // Phase 2 — tidewood & reef
    public static final Block TIDEWOOD_LOG = registerBlock("tidewood_log",
            new PillarBlock(AbstractBlock.Settings.create().strength(2.0f).sounds(BlockSoundGroup.WOOD).mapColor(MapColor.CYAN)));
    public static final Block TIDEWOOD_LEAVES = registerBlock("tidewood_leaves",
            new AmbientLeavesBlock(ParticleTypes.DRIPPING_WATER, 40, AbstractBlock.Settings.create().strength(0.2f).nonOpaque().sounds(BlockSoundGroup.GRASS).mapColor(MapColor.TEAL)));
    public static final Block CORAL_ROCK = registerBlock("coral_rock",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE).mapColor(MapColor.TERRACOTTA_WHITE)));
    public static final Block PEARL_BLOCK = registerBlock("pearl_block",
            new AmbientParticleBlock(ParticleTypes.END_ROD, 24, false, AbstractBlock.Settings.create().strength(1.5f).luminance(state -> 10).sounds(BlockSoundGroup.AMETHYST_BLOCK).mapColor(MapColor.OFF_WHITE)));
    // Phase 3 — cinder trees & sulfur
    public static final Block CHARRED_LOG = registerBlock("charred_log",
            new PillarBlock(AbstractBlock.Settings.create().strength(2.0f).luminance(state -> 6).sounds(BlockSoundGroup.WOOD).mapColor(MapColor.BLACK)));
    public static final Block EMBER_LEAVES = registerBlock("ember_leaves",
            new AmbientLeavesBlock(ParticleTypes.FALLING_LAVA, 14, AbstractBlock.Settings.create().strength(0.2f).nonOpaque().luminance(state -> 10).sounds(BlockSoundGroup.GRASS).mapColor(MapColor.ORANGE)));
    public static final Block SCORCHED_SAND = registerBlock("scorched_sand",
            new Block(AbstractBlock.Settings.create().strength(0.5f).sounds(BlockSoundGroup.SAND).mapColor(MapColor.BLACK)));
    public static final Block SULFUR_BLOCK = registerBlock("sulfur_block",
            new AmbientParticleBlock(ParticleTypes.SMOKE, 10, true, AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.STONE).mapColor(MapColor.YELLOW)));
    // Phase 4 — wispwood & the drowned dead
    public static final Block WISPWOOD_LOG = registerBlock("wispwood_log",
            new PillarBlock(AbstractBlock.Settings.create().strength(2.0f).luminance(state -> 3).sounds(BlockSoundGroup.WOOD).mapColor(MapColor.LIGHT_GRAY)));
    public static final Block WISP_LEAVES = registerBlock("wisp_leaves",
            new AmbientLeavesBlock(ParticleTypes.SOUL, 30, AbstractBlock.Settings.create().strength(0.2f).nonOpaque().luminance(state -> 6).sounds(BlockSoundGroup.GRASS).mapColor(MapColor.LIGHT_GRAY)));
    public static final Block GRAVE_SILT = registerBlock("grave_silt",
            new Block(AbstractBlock.Settings.create().strength(0.5f).sounds(BlockSoundGroup.SOUL_SAND).mapColor(MapColor.GRAY)));
    public static final Block SOUL_BARNACLE = registerBlock("soul_barnacle",
            new AmbientParticleBlock(ParticleTypes.SOUL_FIRE_FLAME, 20, true, AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().luminance(state -> 7).sounds(BlockSoundGroup.STONE).mapColor(MapColor.CYAN)));
    // Phase 5 — voidbloom & the luminous deep
    public static final Block VOIDBLOOM_LOG = registerBlock("voidbloom_log",
            new PillarBlock(AbstractBlock.Settings.create().strength(2.0f).luminance(state -> 5).sounds(BlockSoundGroup.WOOD).mapColor(MapColor.PURPLE)));
    public static final Block VOIDBLOOM_LEAVES = registerBlock("voidbloom_leaves",
            new AmbientLeavesBlock(ParticleTypes.REVERSE_PORTAL, 10, AbstractBlock.Settings.create().strength(0.2f).nonOpaque().luminance(state -> 8).sounds(BlockSoundGroup.GRASS).mapColor(MapColor.PURPLE)));
    public static final Block ABYSSAL_SLATE = registerBlock("abyssal_slate",
            new Block(AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().sounds(BlockSoundGroup.DEEPSLATE).mapColor(MapColor.BLACK)));
    public static final Block LUMINOUS_VEIN = registerBlock("luminous_vein",
            new AmbientParticleBlock(ParticleTypes.GLOW, 5, false, AbstractBlock.Settings.create().strength(1.5f, 6.0f).requiresTool().luminance(state -> 13).sounds(BlockSoundGroup.AMETHYST_BLOCK).mapColor(MapColor.CYAN)));

    // Planks for the phase woods — buildable sets
    public static final Block PALM_PLANKS = registerBlock("palm_planks",
            new Block(AbstractBlock.Settings.create().strength(2.0f, 3.0f).sounds(BlockSoundGroup.WOOD).mapColor(MapColor.DIRT_BROWN)));
    public static final Block TIDEWOOD_PLANKS = registerBlock("tidewood_planks",
            new Block(AbstractBlock.Settings.create().strength(2.0f, 3.0f).sounds(BlockSoundGroup.WOOD).mapColor(MapColor.CYAN)));
    public static final Block CHARRED_PLANKS = registerBlock("charred_planks",
            new Block(AbstractBlock.Settings.create().strength(2.0f, 3.0f).luminance(state -> 3).sounds(BlockSoundGroup.WOOD).mapColor(MapColor.BLACK)));
    public static final Block WISPWOOD_PLANKS = registerBlock("wispwood_planks",
            new Block(AbstractBlock.Settings.create().strength(2.0f, 3.0f).sounds(BlockSoundGroup.WOOD).mapColor(MapColor.LIGHT_GRAY)));
    public static final Block VOIDBLOOM_PLANKS = registerBlock("voidbloom_planks",
            new Block(AbstractBlock.Settings.create().strength(2.0f, 3.0f).luminance(state -> 4).sounds(BlockSoundGroup.WOOD).mapColor(MapColor.PURPLE)));

    //Dead Waters

    //Frozen Sea

    private static Block registerBlock(String name, Block block) {
        registerBlockItem(name, block);
        return Registry.register(Registries.BLOCK, new Identifier(PixelPirates.MOD_ID,name), block);
    }

    private static void registerBlockItem(String name, Block block) {
        Registry.register(Registries.ITEM, new Identifier(PixelPirates.MOD_ID,name),
                new BlockItem(block, new Item.Settings()));
    }

    public static void registerModBlocks() {
        SHIP_HELM_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE,
            new Identifier(PixelPirates.MOD_ID, "ship_helm"),
            BlockEntityType.Builder.create(ShipHelmBlockEntity::new, SHIP_HELM).build(null));

        CANNON_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE,
            new Identifier(PixelPirates.MOD_ID, "ship_cannon"),
            BlockEntityType.Builder.create(CannonBlockEntity::new, SHIP_CANNON).build(null));

        GALLOWBRAND_STONE_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE,
            new Identifier(PixelPirates.MOD_ID, "gallowbrand_stone"),
            BlockEntityType.Builder.create(net.get900.pixelpirates.block.custom.GallowbrandStoneBlockEntity::new, GALLOWBRAND_STONE).build(null));

        FORT_CANNON_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE,
            new Identifier(PixelPirates.MOD_ID, "fort_cannon"),
            BlockEntityType.Builder.create(net.get900.pixelpirates.block.custom.FortCannonBlockEntity::new, FORT_CANNON).build(null));

        PixelPirates.LOGGER.info("Registered all mod blocks for " + PixelPirates.MOD_ID);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(fabricItemGroupEntries -> {
            fabricItemGroupEntries.add(ModBlocks.DRIFTWOOD_BLOCK);
        });
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(fabricItemGroupEntries -> {
            fabricItemGroupEntries.add(ModBlocks.GROG_BARREL);
            fabricItemGroupEntries.add(ModBlocks.SHIP_HELM);
            fabricItemGroupEntries.add(ModBlocks.SHIP_MAST);
            fabricItemGroupEntries.add(ModBlocks.SHIP_CANNON);
            fabricItemGroupEntries.add(ModBlocks.FORT_CANNON);
            fabricItemGroupEntries.add(ModBlocks.BLAST_RUBBLE);
            fabricItemGroupEntries.add(ModBlocks.SERPENT_WARD);
            fabricItemGroupEntries.add(ModBlocks.QUENCH_VALVE);
            fabricItemGroupEntries.add(ModBlocks.TIDE_SLUICE);
            fabricItemGroupEntries.add(ModBlocks.HARPOON_WINCH);
            fabricItemGroupEntries.add(ModBlocks.MANACLE_ANCHOR);
            fabricItemGroupEntries.add(ModBlocks.LIVING_FLESH);
            fabricItemGroupEntries.add(ModBlocks.FLESH_VEIN);
            fabricItemGroupEntries.add(ModBlocks.HEART_VALVE);
            fabricItemGroupEntries.add(ModBlocks.GALVANIC_PYLON);
            fabricItemGroupEntries.add(ModBlocks.RIFT_SEAL);
            fabricItemGroupEntries.add(ModBlocks.ANCHOR_WINCH);
            fabricItemGroupEntries.add(ModBlocks.TIDE_BELL);
            fabricItemGroupEntries.add(ModBlocks.BANE_BALLISTA);
            fabricItemGroupEntries.add(ModBlocks.WATCHERS_HORN);
            fabricItemGroupEntries.add(ModBlocks.GHOSTWOOD_LOG);
            fabricItemGroupEntries.add(ModBlocks.GHOSTWOOD_PLANKS);
            fabricItemGroupEntries.add(ModBlocks.SPECTRAL_SAIL);
            fabricItemGroupEntries.add(ModBlocks.GHOST_CANNON);
            fabricItemGroupEntries.add(ModBlocks.GHOST_MAST);
            fabricItemGroupEntries.add(ModBlocks.PHANTOM_BUOY);
            fabricItemGroupEntries.add(ModBlocks.HAMMOCK);
            fabricItemGroupEntries.add(ModBlocks.SHIP_BEDROLL);
            fabricItemGroupEntries.add(ModBlocks.SHIPWRIGHT_TABLE);
            fabricItemGroupEntries.add(ModBlocks.SHIP_WATERLINE);
        });

    }
}