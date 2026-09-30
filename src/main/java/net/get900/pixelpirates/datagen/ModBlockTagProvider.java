package net.get900.pixelpirates.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.util.ModTags;
import net.minecraft.block.Block;
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
            "barrel", "waterline", "map_block", "diary", "hammock", "bedroll", "coconut", "cask", "palm_stairs", "palm_slab", "palm_fence", "palm_door", "palm_trapdoor", "rope_", "tiki", "woven_palm", "desk", "sea_chest", "cargo_crate", "rum_rack", "ships_wheel", "chair", "stool", "hanging_", "trophy", "lobster_pot", "fish_trap", "salvage_crate", "trading_post", "bounty_board", "jolly_roger", "mooring_post", "figurehead", "roulette_table");
    private static final List<String> SHOVEL = List.of("dirt", "sand", "silt", "shell_block");
    private static final List<String> HOE = List.of("leaves", "banana", "flesh");
    private static final List<String> PICKAXE = List.of("stone", "rock", "brick", "ore", "crystal", "emblem", "token",
            "anchor", "sword", "treasure", "slate", "vein", "barnacle", "pearl", "sulfur", "cannon", "water_light", "still", "pedestal", "hoard");

    @Override
    protected void configure(RegistryWrapper.WrapperLookup wrapperLookup) {
        // cannon balls vanish on these instead of blasting them (and fort shots never break blocks at all)
        getOrCreateTagBuilder(ModTags.Blocks.CANNON_IMMUNE).add(ModBlocks.BLAST_RUBBLE).add(ModBlocks.FORT_CANNON).add(ModBlocks.SERPENT_WARD).add(ModBlocks.QUENCH_VALVE).add(ModBlocks.TIDE_SLUICE).add(ModBlocks.MANACLE_ANCHOR)
                .add(ModBlocks.HEART_VALVE).add(ModBlocks.GALVANIC_PYLON);

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
                .add(ModBlocks.VOIDBLOOM_LOG);

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
