package net.get900.pixelpirates.item;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.block.ModBlocks;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ModItemGroups {
    public static final ItemGroup PIXELPIRATES_GROUP = Registry.register(Registries.ITEM_GROUP,
            new Identifier(PixelPirates.MOD_ID,"pixelpirates"),
            FabricItemGroup.builder().icon(() -> new ItemStack(ModItems.PIRATE_COIN))
                    .displayName(Text.translatable("itemgroup.pixelpirates.pixelpirates"))
                    .entries((displayContext, entries) -> {

                        entries.add(ModItems.PIRATE_JOURNAL);
                        entries.add(ModItems.DIMENSION_KEY);
                        entries.add(ModItems.HEAT_AMULET);
                        entries.add(ModItems.SANITY_AMULET);
                        entries.add(ModItems.TIDEBREAKER);
                        entries.add(ModItems.DEPTH_CHARGE);
                        entries.add(ModItems.TIDESHACKLE);
                        entries.add(ModItems.GALLOWBRAND);
                        entries.add(ModItems.HARPOON);
                        entries.add(ModItems.CHUM);
                        entries.add(ModItems.BLOODFIN_FLESH);
                        entries.add(ModItems.RACKHAMS_DIVING_CHARM);
                        entries.add(ModItems.SERPENTS_TIDE_PEARL);
                        entries.add(ModItems.EVERBURNING_LANTERN);
                        entries.add(ModItems.SPECTRAL_ANCHOR);
                        entries.add(ModItems.ROYAL_TIDE_SIGIL);
                        entries.add(ModItems.BLOODFIN_RAZOR_TOOTH);
                        entries.add(ModItems.KRAKENS_INK_HEART);
                        entries.add(ModItems.BROKEN_SHACKLE);
                        entries.add(ModItems.ABYSSAL_HEARTSTONE);
                        entries.add(ModItems.CROWN_OF_THE_DROWNED);
                        entries.add(ModItems.POWDER_BARGE);
                        entries.add(ModItems.BANE_SHAFT);
                        entries.add(ModItems.LEVIATHAN_SCALE);
                        entries.add(ModItems.COIN);
                        entries.add(ModItems.PIRATE_COIN);
                        entries.add(ModItems.CUTLASS);
                        entries.add(ModItems.DAGGER);
                        entries.add(ModItems.DYNAMITE);
                        entries.add(ModItems.CANNON_BALL);
                  //      entries.add(ModItems.SHIP_ITEM);
                        entries.add(ModItems.RAFT_ITEM);
                        //entries.add(ModItems.PIRATE_HAT);
                        entries.add(ModItems.ROPE);
                        entries.add(ModItems.KRAKEN_INK);
                        entries.add(ModItems.CANNON);
                        entries.add(ModItems.SAIL);
                        entries.add(ModItems.MAST_WITH_SAILS);
                        entries.add(ModItems.MAST);
                        entries.add(ModItems.DRIFTWOOD);
                        entries.add(ModItems.TATTERED_CLOTH);
                        entries.add(ModItems.CURSED_BONE);
                        entries.add(ModItems.KRAKEN_SCALE);
                        entries.add(ModItems.VOLCANIC_EMBER);
                        entries.add(ModItems.DISC_HALYARD_SONG);
                        entries.add(ModItems.SEAFARERS_TOKEN);
                        entries.add(ModItems.TREASURE_MAP_COMMON);
                        entries.add(ModItems.TREASURE_MAP_RARE);
                        entries.add(ModItems.TREASURE_MAP_LEGENDARY);
                        entries.add(ModItems.BOUNTY_MAP);

                    }).build());

    public static final ItemGroup PIXELPIRATES_BLOCKS_GROUP = Registry.register(Registries.ITEM_GROUP,
            new Identifier(PixelPirates.MOD_ID,"pixelpirates_blocks"),
            FabricItemGroup.builder().icon(() -> new ItemStack(ModBlocks.DRIFTWOOD_BLOCK))
                    .displayName(Text.translatable("itemgroup.pixelpirates.pixelpirates_blocks"))
                    .entries((displayContext, entries) -> {

                        entries.add(ModBlocks.GROG_BARREL);
                        entries.add(ModBlocks.DRIFTWOOD_BLOCK);
                        entries.add(ModBlocks.WATER_LIGHT_BLOCK);
                        entries.add(ModBlocks.SHOREWOOD_LOG);
                        entries.add(ModBlocks.SHOREWOOD_WOOD);
                        entries.add(ModBlocks.STRIPPED_SHOREWOOD_LOG);
                        entries.add(ModBlocks.STRIPPED_SHOREWOOD_WOOD);
                        entries.add(ModBlocks.SHOREWOOD_LEAVES);
                        entries.add(ModBlocks.COCONUT_BLOCK);
                        entries.add(ModBlocks.BANANA_BLOCK);
                        entries.add(ModBlocks.ASHEN_LOG);
                        entries.add(ModBlocks.ASHEN_WOOD);
                        entries.add(ModBlocks.STRIPPED_ASHEN_LOG);
                        entries.add(ModBlocks.STRIPPED_ASHEN_WOOD);
                        // Ghost Zone blocks
                        entries.add(ModBlocks.SHROUD_STONE);
                        entries.add(ModBlocks.DARK_SHROUD_STONE);
                        entries.add(ModBlocks.DARKER_SHROUD_STONE);
                        entries.add(ModBlocks.DARKER_SHROUD_STONE_VEIN);
                        entries.add(ModBlocks.SHROUD_STONE_BRICKS);
                        entries.add(ModBlocks.CRACKED_SHROUD_STONE_BRICKS);
                        entries.add(ModBlocks.FANCY_SHROUD_STONE);
                        entries.add(ModBlocks.SHROUD_STONE_K);
                        entries.add(ModBlocks.SHROUD_STONE_M);
                        entries.add(ModBlocks.SHROUD_STONE_N);
                        entries.add(ModBlocks.SHROUDED_ORE);
                        entries.add(ModBlocks.SUNKEN_ORE);
                        entries.add(ModBlocks.SHROUDED_EMBLEM);
                        entries.add(ModBlocks.DROWNED_CRYSTAL);
                        entries.add(ModBlocks.SHROUDED_DIRT);
                        entries.add(ModBlocks.BONE_PLANKS);
                        entries.add(ModBlocks.SHROUDED_PLANKS);
                        entries.add(ModBlocks.LIGHT_SHROUDED_PLANKS);
                        entries.add(ModBlocks.SPOOKY_PLANKS);
                        entries.add(ModBlocks.SHROUDED_LOG);
                        // Ship / Pirate Zone blocks
                        entries.add(ModBlocks.PIRATE_STONE_BRICKS);
                        entries.add(ModBlocks.BOLTED_BRICKS);
                        entries.add(ModBlocks.PIRATE_GOLD_ORE);
                        entries.add(ModBlocks.EMERALD_TOKEN);
                        entries.add(ModBlocks.ANCHOR_BLOCK);
                        entries.add(ModBlocks.SWORD_BLOCK);
                        entries.add(ModBlocks.TREASURE_BLOCK);
                        entries.add(ModBlocks.MAP_BLOCK);
                        entries.add(ModBlocks.PIRATE_DIARY_BLOCK);
                        entries.add(ModBlocks.WEATHERED_WOOD);
                        entries.add(ModBlocks.PIRATE_PLANKS);
                        entries.add(ModBlocks.DARK_PIRATE_PLANKS);
                        entries.add(ModBlocks.MOSSY_PIRATE_PLANKS);
                        entries.add(ModBlocks.DESTROYED_PLANKS);
                        entries.add(ModBlocks.TORN_PLANKS);
                        entries.add(ModBlocks.SUNKEN_PLANKS);
                        entries.add(ModBlocks.PIRATE_TWIGS);
                        // Volcanic Island blocks
                        entries.add(ModBlocks.VOLCANIC_ROCK);
                        entries.add(ModBlocks.VOLCANIC_ROCK_2);
                        entries.add(ModBlocks.VOLCANIC_ROCK_K);
                        entries.add(ModBlocks.VOLCANIC_ROCK_N);
                        entries.add(ModBlocks.VOLCANIC_ROCK_FLAGS);
                        entries.add(ModBlocks.VOLCANIC_ROCK_EMBLEM);
                        entries.add(ModBlocks.VOLCANIC_ROCK_CRYSTAL);
                        entries.add(ModBlocks.VOLCANIC_PLANKS);
                        entries.add(ModBlocks.BURNING_LOG);
                        entries.add(ModBlocks.VOLCANIC_ROCK_BRICKS);
                        entries.add(ModBlocks.VOLCANIC_STONE_BRICKS_VEIN);
                        entries.add(ModBlocks.MOLTEN_STONE_BRICKS);
                        // Ethereal (Zone 5) blocks
                        entries.add(ModBlocks.ETHEREAL_STONE);
                        entries.add(ModBlocks.ETHEREAL_PULSING_STONE);
                        entries.add(ModBlocks.ETHEREAL_PINK_STONE);
                        entries.add(ModBlocks.ETHEREAL_CRYSTAL);
                        entries.add(ModBlocks.ETHEREAL_STONE_BRICKS);
                        entries.add(ModBlocks.ETHEREAL_STONE_BRICKS_2);
                        entries.add(ModBlocks.ETHEREAL_EMBLEM);
                        entries.add(ModBlocks.ETHEREAL_STONE_FLAGS);
                        entries.add(ModBlocks.ETHEREAL_STONE_N);
                        entries.add(ModBlocks.ETHEREAL_STONE_K);
                        entries.add(ModBlocks.ETHEREAL_PLANKS);
                        entries.add(ModBlocks.ETHEREAL_LOG);
                        entries.add(ModBlocks.ETHEREAL_GLOWING_BRICKS);
                        entries.add(ModBlocks.ETHEREAL_DIM_BRICKS);
                        // Phase flora & ambience
                        entries.add(ModBlocks.PALM_LOG);
                        entries.add(ModBlocks.PALM_LEAVES);
                        entries.add(ModBlocks.SHELL_BLOCK);
                        entries.add(ModBlocks.TIDE_POOL_ROCK);
                        entries.add(ModBlocks.TIDEWOOD_LOG);
                        entries.add(ModBlocks.TIDEWOOD_LEAVES);
                        entries.add(ModBlocks.CORAL_ROCK);
                        entries.add(ModBlocks.PEARL_BLOCK);
                        entries.add(ModBlocks.CHARRED_LOG);
                        entries.add(ModBlocks.EMBER_LEAVES);
                        entries.add(ModBlocks.SCORCHED_SAND);
                        entries.add(ModBlocks.SULFUR_BLOCK);
                        entries.add(ModBlocks.WISPWOOD_LOG);
                        entries.add(ModBlocks.WISP_LEAVES);
                        entries.add(ModBlocks.GRAVE_SILT);
                        entries.add(ModBlocks.SOUL_BARNACLE);
                        entries.add(ModBlocks.VOIDBLOOM_LOG);
                        entries.add(ModBlocks.VOIDBLOOM_LEAVES);
                        entries.add(ModBlocks.ABYSSAL_SLATE);
                        entries.add(ModBlocks.LUMINOUS_VEIN);
                        entries.add(ModBlocks.PALM_PLANKS);
                        entries.add(ModBlocks.TIDEWOOD_PLANKS);
                        entries.add(ModBlocks.CHARRED_PLANKS);
                        entries.add(ModBlocks.WISPWOOD_PLANKS);
                        entries.add(ModBlocks.VOIDBLOOM_PLANKS);

                    }).build());

    public static final ItemGroup PIXELPIRATES_FOOD_GROUP = Registry.register(Registries.ITEM_GROUP,
            new Identifier(PixelPirates.MOD_ID,"pixelpirates_food"),
            FabricItemGroup.builder().icon(() -> new ItemStack(ModItems.GROG))
                    .displayName(Text.translatable("itemgroup.pixelpirates.pixelpirates_food"))
                    .entries((displayContext, entries) -> {

                        entries.add(ModItems.GROG);
                        entries.add(ModItems.RAW_SHARK_MEAT);
                        entries.add(ModItems.COOKED_SHARK_MEAT);
                        entries.add(ModItems.RAW_SALTED_SWIMMER);
                        entries.add(ModItems.COOKED_SALTED_SWIMMER);
                        entries.add(ModItems.COCONUT);
                        entries.add(ModItems.BANANA);
                        entries.add(ModItems.BANANA_BREAD);
                        entries.add(ModItems.HARDTACK);
                        entries.add(ModItems.KRAKEN_CALAMARI);
                        entries.add(ModItems.COCONUT_GROG);
                        entries.add(ModItems.PIRATES_STEW);

                    }).build());

    public static final ItemGroup PIXELPIRATES_GEAR_GROUP = Registry.register(Registries.ITEM_GROUP,
            new Identifier(PixelPirates.MOD_ID,"pixelpirates_gear"),
            FabricItemGroup.builder().icon(() -> new ItemStack(ModItems.CUTLASS))
                    .displayName(Text.translatable("itemgroup.pixelpirates.pixelpirates_gear"))
                    .entries((displayContext, entries) -> {

                        entries.add(ModItems.CUTLASS);
                        entries.add(ModItems.DAGGER);
                        entries.add(ModItems.DYNAMITE);
                        entries.add(ModItems.RUSTED_CUTLASS);
                        entries.add(ModItems.BOARDING_AXE);
                        entries.add(ModItems.BROKEN_SHOVEL);
                        // Region weapons — Ring 1 → Ring 5
                        entries.add(ModItems.MARLINSPIKE);
                        entries.add(ModItems.BOARDING_SABRE);
                        entries.add(ModItems.NAVAL_RAPIER);
                        entries.add(ModItems.OFFICERS_SABRE);
                        entries.add(ModItems.THROWING_KNIFE);
                        entries.add(ModItems.CORSAIR_CUTLASS);
                        entries.add(ModItems.BOARDING_PIKE);
                        entries.add(ModItems.EMBERBRAND);
                        entries.add(ModItems.SOULRENDER);
                        entries.add(ModItems.WRAITHBLADE);
                        entries.add(ModItems.KRAKEN_FANG);
                        entries.add(ModItems.STORMCALLER);
                        entries.add(ModItems.ABYSSAL_HARPOON);
                        entries.add(ModItems.PIRATE_HELMET);
                        entries.add(ModItems.PIRATE_CHESTPLATE);
                        entries.add(ModItems.PIRATE_LEGGINGS);
                        entries.add(ModItems.PIRATE_BOOTS);
                        // Region armor — Ring 1 → Ring 5
                        entries.add(ModItems.CASTAWAY_HELMET);
                        entries.add(ModItems.CASTAWAY_CHESTPLATE);
                        entries.add(ModItems.CASTAWAY_LEGGINGS);
                        entries.add(ModItems.CASTAWAY_BOOTS);
                        entries.add(ModItems.NAVY_OFFICER_HELMET);
                        entries.add(ModItems.NAVY_OFFICER_CHESTPLATE);
                        entries.add(ModItems.NAVY_OFFICER_LEGGINGS);
                        entries.add(ModItems.NAVY_OFFICER_BOOTS);
                        entries.add(ModItems.CORSAIR_HELMET);
                        entries.add(ModItems.CORSAIR_CHESTPLATE);
                        entries.add(ModItems.CORSAIR_LEGGINGS);
                        entries.add(ModItems.CORSAIR_BOOTS);
                        entries.add(ModItems.ASHEN_HELMET);
                        entries.add(ModItems.ASHEN_CHESTPLATE);
                        entries.add(ModItems.ASHEN_LEGGINGS);
                        entries.add(ModItems.ASHEN_BOOTS);
                        entries.add(ModItems.CURSED_BONE_HELMET);
                        entries.add(ModItems.CURSED_BONE_CHESTPLATE);
                        entries.add(ModItems.CURSED_BONE_LEGGINGS);
                        entries.add(ModItems.CURSED_BONE_BOOTS);
                        entries.add(ModItems.KRAKEN_SCALE_HELMET);
                        entries.add(ModItems.KRAKEN_SCALE_CHESTPLATE);
                        entries.add(ModItems.KRAKEN_SCALE_LEGGINGS);
                        entries.add(ModItems.KRAKEN_SCALE_BOOTS);
                        entries.add(ModItems.FROST_HELM);
                        entries.add(ModItems.SHIP_REPAIR_KIT);
                        entries.add(ModItems.SHIP_BLUEPRINT);
                        entries.add(ModItems.DEBUG_WAND);
                        entries.add(ModItems.BOSS_SLAYER);
                        entries.add(ModItems.SHIP_SPAWNER_WAND);

                    }).build());

    public static void registerItemGroups() {
        PixelPirates.LOGGER.info("Registering Item Groups for " + PixelPirates.MOD_ID);
    }
}
