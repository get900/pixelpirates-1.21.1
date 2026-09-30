package net.get900.pixelpirates.world.gen;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.get900.pixelpirates.entity.ModEntities;
import net.get900.pixelpirates.entity.custom.AquaticHostileEntity;
import net.get900.pixelpirates.entity.custom.CastawayEntity;
import net.get900.pixelpirates.entity.custom.CoralJellyEntity;
import net.get900.pixelpirates.entity.custom.ChestCrabEntity;
import net.get900.pixelpirates.entity.custom.SharkEntity;
import net.get900.pixelpirates.world.biome.ModBiomeKeys;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.Heightmap;

public class ModEntitySpawns {
    public static void addSpawns() {
        // Sharks spawn in Phase 1 open water — deep enough for canSpawn predicate (Y < seaLevel-2)
        BiomeModifications.addSpawn(
                BiomeSelectors.includeByKey(ModBiomeKeys.OPEN_OCEAN, ModBiomeKeys.TEMPERATE_SHALLOWS),
                SpawnGroup.WATER_CREATURE, ModEntities.SHARK,
                8,  // weight
                1,  // min group size
                3   // max group size
        );

        SpawnRestriction.register(
                ModEntities.SHARK,
                SpawnRestriction.Location.IN_WATER,
                Heightmap.Type.OCEAN_FLOOR,
                SharkEntity::canSpawn
        );

        // Cursed Monkeys — ISLAND_THICKETS only; group 1 so each spawn event places one mob,
        // preventing them from flooding the monster cap as the sole monster in the pool
        BiomeModifications.addSpawn(
                BiomeSelectors.includeByKey(ModBiomeKeys.ISLAND_THICKETS),
                SpawnGroup.MONSTER, ModEntities.CURSED_MONKEY,
                4,  // weight
                1,  // min group size
                1   // max group size — one at a time keeps population manageable
        );

        SpawnRestriction.register(
                ModEntities.CURSED_MONKEY,
                SpawnRestriction.Location.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                MobEntity::canMobSpawn
        );

        // Chest Crabs — SpawnGroup.MONSTER so they participate in the same spawn cycle as the
        // monkey; SpawnGroup.CREATURE uses a different cycle where MobEntity::canMobSpawn's
        // internal isSpawnDark() check always rejects daylit positions
        BiomeModifications.addSpawn(
                BiomeSelectors.includeByKey(
                        ModBiomeKeys.TEMPERATE_SHALLOWS,
                        ModBiomeKeys.ISLAND_THICKETS),
                SpawnGroup.MONSTER, ModEntities.CHEST_CRAB,
                5,  // weight
                1,  // min group size
                2   // max group size
        );

        SpawnRestriction.register(
                ModEntities.CHEST_CRAB,
                SpawnRestriction.Location.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                MobEntity::canMobSpawn
        );

        // Lava Crabs — Phase 3 volcanic ring. These biomes already carry skeletons/zombies/magma
        // cubes in the MONSTER pool, so a moderate weight here does not monopolise the mob cap
        // the way the monkey did as a sole entry.
        BiomeModifications.addSpawn(
                BiomeSelectors.includeByKey(
                        ModBiomeKeys.ASH_REEF,
                        ModBiomeKeys.BOILING_BASIN,
                        ModBiomeKeys.MAGMA_SEA),
                SpawnGroup.MONSTER, ModEntities.LAVA_CRAB,
                12, // weight — between the zombie (15-20) and magma cube entries of those biomes
                1,  // min group size
                2   // max group size
        );

        SpawnRestriction.register(
                ModEntities.LAVA_CRAB,
                SpawnRestriction.Location.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                MobEntity::canMobSpawn
        );

        // Castaways — marooned sailors on the phase 1 islands. Rare (weight 2, always solo) so
        // they read as a find rather than a population.
        BiomeModifications.addSpawn(
                BiomeSelectors.includeByKey(ModBiomeKeys.ISLAND_THICKETS),
                SpawnGroup.CREATURE, ModEntities.CASTAWAY,
                2,  // weight
                1,  // min group size
                1   // max group size — one marooned survivor at a time
        );

        // NB: CREATURE group, so the predicate is CastawayEntity::canSpawn, NOT
        // MobEntity::canMobSpawn — the latter's isSpawnDark() check can never pass here
        SpawnRestriction.register(
                ModEntities.CASTAWAY,
                SpawnRestriction.Location.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                CastawayEntity::canSpawn
        );

        // ---- Generated mobs (2026-09-28) ----
        // Siren - her namesake Siren Sea, rarely the reef edge. MONSTER + IN_WATER like the drowned;
        // the predicate checks water only (never sea level - see the shark rule in CLAUDE.md).
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(ModBiomeKeys.SIREN_SEA), SpawnGroup.MONSTER, ModEntities.SIREN, 6, 1, 1);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(ModBiomeKeys.REEF_EDGE), SpawnGroup.MONSTER, ModEntities.SIREN, 2, 1, 1);
        SpawnRestriction.register(ModEntities.SIREN, SpawnRestriction.Location.IN_WATER,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, AquaticHostileEntity::canSpawnInWater);

        // Coral Jelly - reef drifters in small blooms; WATER_AMBIENT like fish/squid
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(ModBiomeKeys.CORAL_BAY, ModBiomeKeys.REEF_EDGE, ModBiomeKeys.SIREN_SEA),
                SpawnGroup.WATER_AMBIENT, ModEntities.CORAL_JELLY, 10, 2, 4);
        SpawnRestriction.register(ModEntities.CORAL_JELLY, SpawnRestriction.Location.IN_WATER,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, CoralJellyEntity::canSpawn);

        // Magma Brute - volcanic ring, always alone (it's a mini-boss)
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(ModBiomeKeys.ASH_REEF, ModBiomeKeys.BOILING_BASIN, ModBiomeKeys.MAGMA_SEA),
                SpawnGroup.MONSTER, ModEntities.MAGMA_BRUTE, 4, 1, 1);
        SpawnRestriction.register(ModEntities.MAGMA_BRUTE, SpawnRestriction.Location.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MobEntity::canMobSpawn);

        // Mimic - cursed-seas islands, dormant until disturbed
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(ModBiomeKeys.PHANTOM_WAKE, ModBiomeKeys.SHIPGRAVE_DEPTHS, ModBiomeKeys.DROWNED_TRENCH),
                SpawnGroup.MONSTER, ModEntities.MIMIC, 3, 1, 1);
        SpawnRestriction.register(ModEntities.MIMIC, SpawnRestriction.Location.ON_GROUND,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MobEntity::canMobSpawn);

        // Abyssal Angler - the phase 5 deep
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(ModBiomeKeys.ABYSSAL_RINGS, ModBiomeKeys.PILLAR_SEA, ModBiomeKeys.MAW_DEPTHS),
                SpawnGroup.MONSTER, ModEntities.ABYSSAL_ANGLER, 5, 1, 1);
        SpawnRestriction.register(ModEntities.ABYSSAL_ANGLER, SpawnRestriction.Location.IN_WATER,
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, AquaticHostileEntity::canSpawnInWater);
    }
}
