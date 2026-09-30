package net.get900.pixelpirates;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.get900.pixelpirates.datagen.*;
import net.get900.pixelpirates.datagen.biome.PixelPiratesBiomes;
import net.get900.pixelpirates.world.ModConfiguredFeatures;
import net.get900.pixelpirates.world.ModPlacedFeatures;
import net.get900.pixelpirates.world.biome.ModBiomes;
import net.minecraft.registry.RegistryBuilder;
import net.minecraft.registry.RegistryKeys;

public class PixelPiratesDataGenerator implements DataGeneratorEntrypoint {
	@Override
	public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
		FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();

		pack.addProvider(ModBlockTagProvider::new);
		pack.addProvider(ModItemTagProvider::new);
		pack.addProvider(ModLootTableProvider::new);
		pack.addProvider(ModModelProvider::new);
		pack.addProvider(ModRecipeProvider::new);
		pack.addProvider(net.get900.pixelpirates.homestead.datagen.HomesteadModelProvider::new);
		pack.addProvider(net.get900.pixelpirates.homestead.datagen.HomesteadLootProvider::new);
		pack.addProvider(net.get900.pixelpirates.homestead.datagen.HomesteadRecipeProvider::new);
		pack.addProvider(ModRegistryDataGenerator::new);

		pack.addProvider(ModBiomeProvider::new);
	}

	@Override
	public void buildRegistry(RegistryBuilder registryBuilder) {
		registryBuilder.addRegistry(RegistryKeys.CONFIGURED_FEATURE, ModConfiguredFeatures::bootstrap);
		registryBuilder.addRegistry(RegistryKeys.PLACED_FEATURE, ModPlacedFeatures::bootstrap);
		registryBuilder.addRegistry(RegistryKeys.BIOME, ModBiomes::bootstrap);
	}
}
