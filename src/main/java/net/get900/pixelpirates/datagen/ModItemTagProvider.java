package net.get900.pixelpirates.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.util.ModTags;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;

import java.util.concurrent.CompletableFuture;

public class ModItemTagProvider extends FabricTagProvider.ItemTagProvider {
    public ModItemTagProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> completableFuture) {
        super(output, completableFuture);
    }

    @Override
    protected void configure(RegistryWrapper.WrapperLookup wrapperLookup) {
        getOrCreateTagBuilder(ModTags.Items.SHOOTABLE_ITEMS)
                .add(ModItems.CANNON_BALL)
                .add(Items.COBBLESTONE);

        getOrCreateTagBuilder(ItemTags.SWORDS)
                .add(ModItems.CUTLASS)
                .add(ModItems.DAGGER)
                .add(ModItems.RUSTED_CUTLASS);

        getOrCreateTagBuilder(ItemTags.AXES)
                .add(ModItems.BOARDING_AXE);

        getOrCreateTagBuilder(ItemTags.SHOVELS)
                .add(ModItems.BROKEN_SHOVEL);

        getOrCreateTagBuilder(ItemTags.PLANKS)
                .add(ModBlocks.PALM_PLANKS.asItem())
                .add(ModBlocks.TIDEWOOD_PLANKS.asItem())
                .add(ModBlocks.CHARRED_PLANKS.asItem())
                .add(ModBlocks.WISPWOOD_PLANKS.asItem())
                .add(ModBlocks.VOIDBLOOM_PLANKS.asItem());

        net.get900.pixelpirates.homestead.datagen.HomesteadTags.items(this::getOrCreateTagBuilder);

        getOrCreateTagBuilder(ItemTags.TRIMMABLE_ARMOR)
                .add(ModItems.PIRATE_HELMET)
                .add(ModItems.PIRATE_CHESTPLATE)
                .add(ModItems.PIRATE_LEGGINGS)
                .add(ModItems.PIRATE_BOOTS);
    }
}
