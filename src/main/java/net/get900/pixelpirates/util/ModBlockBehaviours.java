package net.get900.pixelpirates.util;

import net.fabricmc.fabric.api.registry.CompostingChanceRegistry;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.fabricmc.fabric.api.registry.StrippableBlockRegistry;
import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.block.Block;

/**
 * Vanilla-parity behaviours for the mod's wood and plants: axe stripping, fire spread,
 * furnace fuel and composting. None of these existed before - logs couldn't be stripped,
 * planks couldn't fuel a furnace and leaves never caught fire.
 */
public final class ModBlockBehaviours {
    private ModBlockBehaviours() {}

    public static void register() {
        StrippableBlockRegistry.register(ModBlocks.SHOREWOOD_LOG, ModBlocks.STRIPPED_SHOREWOOD_LOG);
        StrippableBlockRegistry.register(ModBlocks.SHOREWOOD_WOOD, ModBlocks.STRIPPED_SHOREWOOD_WOOD);
        StrippableBlockRegistry.register(ModBlocks.ASHEN_LOG, ModBlocks.STRIPPED_ASHEN_LOG);
        StrippableBlockRegistry.register(ModBlocks.ASHEN_WOOD, ModBlocks.STRIPPED_ASHEN_WOOD);

        // Flammability uses vanilla's log/planks/leaves numbers. Ashen/charred/voidbloom wood
        // is deliberately fireproof - it grows in volcanic and void zones.
        FlammableBlockRegistry fire = FlammableBlockRegistry.getDefaultInstance();
        for (Block log : new Block[]{ModBlocks.SHOREWOOD_LOG, ModBlocks.SHOREWOOD_WOOD, ModBlocks.STRIPPED_SHOREWOOD_LOG,
                ModBlocks.STRIPPED_SHOREWOOD_WOOD, ModBlocks.DRIFTWOOD_BLOCK, ModBlocks.PALM_LOG,
                ModBlocks.TIDEWOOD_LOG, ModBlocks.WISPWOOD_LOG}) {
            fire.add(log, 5, 5);
        }
        for (Block planks : new Block[]{ModBlocks.PALM_PLANKS, ModBlocks.TIDEWOOD_PLANKS, ModBlocks.WISPWOOD_PLANKS,
                ModBlocks.PIRATE_PLANKS, ModBlocks.DARK_PIRATE_PLANKS, ModBlocks.MOSSY_PIRATE_PLANKS,
                ModBlocks.WEATHERED_WOOD, ModBlocks.GROG_BARREL, ModBlocks.HAMMOCK, ModBlocks.SHIP_BEDROLL}) {
            fire.add(planks, 5, 20);
        }
        for (Block leaves : new Block[]{ModBlocks.SHOREWOOD_LEAVES, ModBlocks.PALM_LEAVES, ModBlocks.TIDEWOOD_LEAVES,
                ModBlocks.WISP_LEAVES}) {
            fire.add(leaves, 30, 60);
        }

        FuelRegistry fuel = FuelRegistry.INSTANCE;
        fuel.add(ModItems.DRIFTWOOD, 800);
        for (Block wood : new Block[]{ModBlocks.SHOREWOOD_LOG, ModBlocks.SHOREWOOD_WOOD, ModBlocks.STRIPPED_SHOREWOOD_LOG,
                ModBlocks.STRIPPED_SHOREWOOD_WOOD, ModBlocks.PALM_LOG, ModBlocks.TIDEWOOD_LOG, ModBlocks.WISPWOOD_LOG,
                ModBlocks.PALM_PLANKS, ModBlocks.TIDEWOOD_PLANKS, ModBlocks.WISPWOOD_PLANKS, ModBlocks.PIRATE_PLANKS,
                ModBlocks.DARK_PIRATE_PLANKS, ModBlocks.MOSSY_PIRATE_PLANKS, ModBlocks.WEATHERED_WOOD}) {
            fuel.add(wood, 300);
        }
        fuel.add(ModBlocks.SHOREWOOD_SAPLING, 100);
        fuel.add(ModItems.VOLCANIC_EMBER, 1600);   // a hot coal, basically

        CompostingChanceRegistry compost = CompostingChanceRegistry.INSTANCE;
        for (Block leaves : new Block[]{ModBlocks.SHOREWOOD_LEAVES, ModBlocks.PALM_LEAVES, ModBlocks.TIDEWOOD_LEAVES,
                ModBlocks.EMBER_LEAVES, ModBlocks.WISP_LEAVES, ModBlocks.VOIDBLOOM_LEAVES, ModBlocks.SHOREWOOD_SAPLING}) {
            compost.add(leaves, 0.3f);
        }
        compost.add(ModItems.BANANA, 0.65f);
        compost.add(ModItems.COCONUT, 0.65f);
        compost.add(ModBlocks.BANANA_BLOCK, 0.85f);
        compost.add(ModItems.BANANA_BREAD, 0.85f);
        compost.add(ModItems.HARDTACK, 0.85f);
    }
}
