package net.get900.pixelpirates.homestead;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.fabricmc.fabric.api.registry.CompostingChanceRegistry;
import net.get900.pixelpirates.PixelPirates;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * HOMESTEAD - everything that makes the islands worth living on (2026-09-30 overnight build): tropical crops and the
 * galley, the rum still and aging casks, palm building blocks, pirate furniture, guns, region tools, the hoard, traders,
 * fishing, bounties, spyglass and compass, hideouts and moorings, salvage, grappling hook, ship upgrades, parrots, the
 * logbook, and the roulette table. Registered from {@link PixelPirates#onInitialize()}; client side in HomesteadClient.
 */
public final class Homestead {
    private Homestead() {}

    public static ItemGroup GROUP;

    public static void init() {
        HomesteadBlocks.init();
        HomesteadItems.init();
        HomesteadEffects.init();
        HomesteadBlockEntities.init();
        HomesteadEntities.init();
        GROUP = Registry.register(Registries.ITEM_GROUP, new Identifier(PixelPirates.MOD_ID, "homestead"),
                FabricItemGroup.builder().icon(() -> new ItemStack(HomesteadItems.PINEAPPLE))
                        .displayName(Text.translatable("itemgroup.pixelpirates.homestead"))
                        .entries((ctx, entries) -> {
                            for (Block b : HomesteadBlocks.WITH_ITEM) entries.add(b);
                            for (Item i : HomesteadItems.ALL) entries.add(i);
                        }).build());
        registerSeedDrops();
        net.get900.pixelpirates.homestead.trade.Prices.init();
        net.get900.pixelpirates.homestead.trade.PortTraders.register();
        net.get900.pixelpirates.homestead.bounty.Bounties.register();
        net.get900.pixelpirates.homestead.parrot.ParrotCompanion.register();
        net.get900.pixelpirates.homestead.fishing.Fishing.register();
        net.get900.pixelpirates.homestead.salvage.SalvageFeature.register();
        registerComposting();
        registerFlammable();
    }

    /** Tropical seeds turn up in island grass, like wheat seeds do (and from the Quartermaster). */
    private static void registerSeedDrops() {
        Identifier grass = new Identifier("minecraft", "blocks/grass");
        Identifier fern = new Identifier("minecraft", "blocks/fern");
        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, table, source) -> {
            if (!source.isBuiltin() || !(grass.equals(id) || fern.equals(id))) return;
            for (Item seed : new Item[]{HomesteadItems.PINEAPPLE_CROWN, HomesteadItems.LIME_SEEDS, HomesteadItems.CHILI_SEEDS})
                table.pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1))
                        .conditionally(RandomChanceLootCondition.builder(0.035f)).with(ItemEntry.builder(seed)));
        });
    }

    private static void registerFlammable() {
        var f = net.fabricmc.fabric.api.registry.FlammableBlockRegistry.getDefaultInstance();
        for (Block b : new Block[]{HomesteadBlocks.PALM_STAIRS, HomesteadBlocks.PALM_SLAB, HomesteadBlocks.PALM_FENCE, HomesteadBlocks.PALM_FENCE_GATE,
                HomesteadBlocks.DRIFTWOOD_FENCE, HomesteadBlocks.DRIFTWOOD_FENCE_GATE, HomesteadBlocks.ROPE_BRIDGE}) f.add(b, 5, 20);
        for (Block b : new Block[]{HomesteadBlocks.THATCH, HomesteadBlocks.THATCH_STAIRS, HomesteadBlocks.THATCH_SLAB}) f.add(b, 60, 20);
        for (Block b : new Block[]{HomesteadBlocks.WOVEN_PALM_SCREEN, HomesteadBlocks.WOVEN_MAT, HomesteadBlocks.ROPE_LADDER}) f.add(b, 30, 60);
    }

    private static void registerComposting() {
        CompostingChanceRegistry c = CompostingChanceRegistry.INSTANCE;
        c.add(HomesteadItems.PINEAPPLE_CROWN, 0.3f);
        c.add(HomesteadItems.LIME_SEEDS, 0.3f);
        c.add(HomesteadItems.CHILI_SEEDS, 0.3f);
        c.add(HomesteadItems.PINEAPPLE, 0.65f);
        c.add(HomesteadItems.LIME, 0.65f);
        c.add(HomesteadItems.CHILI_PEPPER, 0.5f);
    }
}
