package net.get900.pixelpirates.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.util.ModTags;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.BlockTags;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ModBlockTagProvider extends FabricTagProvider.BlockTagProvider {
    public ModBlockTagProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
        super(output, registriesFuture);
    }

    // Name fragments -> mining tool. First match wins, so the more specific words come first.
    private static final List<String> AXE = List.of("planks", "_log", "_wood", "driftwood", "twigs", "table", "mast", "helm",
            "barrel", "waterline", "map_block", "diary", "hammock", "bedroll", "music_box", "coconut", "cask", "palm_stairs", "palm_slab", "palm_fence", "palm_door", "palm_trapdoor", "rope_", "tiki", "woven_palm", "desk", "sea_chest", "cargo_crate", "rum_rack", "ships_wheel", "chair", "stool", "hanging_", "trophy", "lobster_pot", "fish_trap", "salvage_crate", "trading_post", "bounty_board", "jolly_roger", "mooring_post", "figurehead", "roulette_table", "bellows", "weapon_rack", "pattern_board", "perch_branch", "parrot_roost");
    private static final List<String> SHOVEL = List.of("dirt", "sand", "silt", "shell_block");
    private static final List<String> HOE = List.of("leaves", "banana", "flesh");
    private static final List<String> PICKAXE = List.of("stone", "rock", "brick", "ore", "crystal", "emblem", "token",
            "anchor", "sword", "treasure", "slate", "vein", "barnacle", "pearl", "sulfur", "cannon", "water_light", "still", "pedestal", "hoard", "forge_hearth", "forge_anvil", "statue", "marble_bust", "garden_urn", "signal_lantern", "puzzle_node");

    @Override
    protected void configure(RegistryWrapper.WrapperLookup wrapperLookup) {
        // cannon balls vanish on these instead of blasting them (and fort shots never break blocks at all)
        getOrCreateTagBuilder(ModTags.Blocks.CANNON_IMMUNE).add(ModBlocks.BLAST_RUBBLE).add(ModBlocks.FORT_CANNON).add(ModBlocks.SERPENT_WARD).add(ModBlocks.QUENCH_VALVE).add(ModBlocks.TIDE_SLUICE).add(ModBlocks.MANACLE_ANCHOR)
                .add(ModBlocks.HEART_VALVE).add(ModBlocks.GALVANIC_PYLON);

        // Natural seabed a Keelbreaker hull grinds through (world/KeelBreaker). Only blocks with a collision box matter
        // (kelp/seagrass never stop a ship). Whitelist on purpose: player builds and dungeon blocks are never in here.
        getOrCreateTagBuilder(ModTags.Blocks.KEEL_SOFT)
                .forceAddTag(BlockTags.SAND).forceAddTag(BlockTags.DIRT).forceAddTag(BlockTags.CORAL_BLOCKS)
                .add(Blocks.GRAVEL, Blocks.CLAY, Blocks.MUD, Blocks.SEA_PICKLE, Blocks.SOUL_SAND,
                        Blocks.DEAD_TUBE_CORAL_BLOCK, Blocks.DEAD_BRAIN_CORAL_BLOCK, Blocks.DEAD_BUBBLE_CORAL_BLOCK,
                        Blocks.DEAD_FIRE_CORAL_BLOCK, Blocks.DEAD_HORN_CORAL_BLOCK)
                .add(ModBlocks.SHELL_BLOCK, ModBlocks.CORAL_ROCK, ModBlocks.SCORCHED_SAND, ModBlocks.GRAVE_SILT);
        getOrCreateTagBuilder(ModTags.Blocks.KEEL_ROCK)
                .forceAddTag(BlockTags.BASE_STONE_OVERWORLD)
                .forceAddTag(net.fabricmc.fabric.api.tag.convention.v1.ConventionalBlockTags.ORES)
                .add(Blocks.SANDSTONE, Blocks.RED_SANDSTONE, Blocks.CALCITE, Blocks.BASALT, Blocks.SMOOTH_BASALT,
                        Blocks.BLACKSTONE, Blocks.MAGMA_BLOCK, Blocks.DRIPSTONE_BLOCK, Blocks.POINTED_DRIPSTONE,
                        Blocks.PACKED_ICE, Blocks.ICE, Blocks.SNOW_BLOCK, Blocks.MOSS_BLOCK)
                .add(ModBlocks.TIDE_POOL_ROCK, ModBlocks.PEARL_BLOCK, ModBlocks.SULFUR_BLOCK, ModBlocks.SOUL_BARNACLE,
                        ModBlocks.ABYSSAL_SLATE, ModBlocks.LUMINOUS_VEIN);

        // Before this, every requiresTool() block outside the phase-flora set was in no mineable
        // tag - so it mined at bare-hand speed and dropped nothing with any tool.
        var axe = getOrCreateTagBuilder(BlockTags.AXE_MINEABLE);
        var shovel = getOrCreateTagBuilder(BlockTags.SHOVEL_MINEABLE);
        var hoe = getOrCreateTagBuilder(BlockTags.HOE_MINEABLE);
        var pickaxe = getOrCreateTagBuilder(BlockTags.PICKAXE_MINEABLE);
        for (Block block : Registries.BLOCK) {
            var id = Registries.BLOCK.getId(block);
            if (!id.getNamespace().equals(PixelPirates.MOD_ID)) continue;
            String n = id.getPath();
            if (matches(n, AXE)) axe.add(block);
            else if (matches(n, SHOVEL)) shovel.add(block);
            else if (matches(n, HOE)) hoe.add(block);
            else if (matches(n, PICKAXE)) pickaxe.add(block);
        }

        getOrCreateTagBuilder(BlockTags.PLANKS)
                .add(ModBlocks.PALM_PLANKS)
                .add(ModBlocks.TIDEWOOD_PLANKS)
                .add(ModBlocks.CHARRED_PLANKS)
                .add(ModBlocks.WISPWOOD_PLANKS)
                .add(ModBlocks.VOIDBLOOM_PLANKS);

        // LOGS matters for gameplay: hammocks must be strung between two LOGS-tagged blocks,
        // and leaves only stay alive near LOGS. Shorewood/ashen were missing.
        getOrCreateTagBuilder(BlockTags.LOGS_THAT_BURN)
                .add(ModBlocks.DRIFTWOOD_BLOCK)
                .add(ModBlocks.SHOREWOOD_LOG).add(ModBlocks.SHOREWOOD_WOOD)
                .add(ModBlocks.STRIPPED_SHOREWOOD_LOG).add(ModBlocks.STRIPPED_SHOREWOOD_WOOD)
                .add(ModBlocks.PALM_LOG)
                .add(ModBlocks.TIDEWOOD_LOG)
                .add(ModBlocks.WISPWOOD_LOG);
        getOrCreateTagBuilder(BlockTags.LOGS)
                .addTag(BlockTags.LOGS_THAT_BURN)
                .add(ModBlocks.ASHEN_LOG).add(ModBlocks.ASHEN_WOOD)
                .add(ModBlocks.STRIPPED_ASHEN_LOG).add(ModBlocks.STRIPPED_ASHEN_WOOD)
                .add(ModBlocks.CHARRED_LOG)
                .add(ModBlocks.VOIDBLOOM_LOG)
                .add(net.get900.pixelpirates.homestead.HomesteadBlocks.PERCH_BRANCH);    // wild parrots' "fly onto tree" goal looks for LOGS

        getOrCreateTagBuilder(BlockTags.LEAVES)
                .add(ModBlocks.SHOREWOOD_LEAVES)
                .add(ModBlocks.PALM_LEAVES)
                .add(ModBlocks.TIDEWOOD_LEAVES)
                .add(ModBlocks.EMBER_LEAVES)
                .add(ModBlocks.WISP_LEAVES)
                .add(ModBlocks.VOIDBLOOM_LEAVES);

        getOrCreateTagBuilder(BlockTags.SAPLINGS).add(ModBlocks.SHOREWOOD_SAPLING);
        getOrCreateTagBuilder(BlockTags.FLOWER_POTS).add(ModBlocks.POTTED_SHOREWOOD_SAPLING);

        net.get900.pixelpirates.homestead.datagen.HomesteadTags.blocks(this::getOrCreateTagBuilder);

        getOrCreateTagBuilder(ModTags.Blocks.NEEDS_PIRATE_TOOL)
                .add(ModBlocks.DRIFTWOOD_BLOCK);
    }

    private static boolean matches(String name, List<String> fragments) {
        for (String f : fragments) if (name.contains(f)) return true;
        return false;
    }
}
