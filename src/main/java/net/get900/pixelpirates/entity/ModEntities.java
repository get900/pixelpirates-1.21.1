package net.get900.pixelpirates.entity;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.entity.custom.AbyssalAnglerEntity;
import net.get900.pixelpirates.entity.custom.CannonBallEntity;
import net.get900.pixelpirates.entity.custom.CoralJellyEntity;
import net.get900.pixelpirates.entity.custom.MagmaBruteEntity;
import net.get900.pixelpirates.entity.custom.MimicEntity;
import net.get900.pixelpirates.entity.custom.SirenEntity;
import net.get900.pixelpirates.entity.custom.CaptainEntity;
import net.get900.pixelpirates.entity.custom.CastawayEntity;
import net.get900.pixelpirates.entity.custom.ChestCrabEntity;
import net.get900.pixelpirates.entity.custom.CursedMonkeyEntity;
import net.get900.pixelpirates.entity.custom.DynamiteEntity;
import net.get900.pixelpirates.entity.custom.FloatingBarrelEntity;
import net.get900.pixelpirates.entity.custom.LavaCrabEntity;
import net.get900.pixelpirates.entity.custom.MapMerchantEntity;
import net.get900.pixelpirates.entity.custom.PirateCrewEntity;
import net.get900.pixelpirates.entity.custom.RaftEntity;
import net.get900.pixelpirates.entity.custom.SharkEntity;
import net.get900.pixelpirates.entity.custom.SloopEntity;
import net.get900.pixelpirates.entity.custom.ThrownKnifeEntity;
import net.minecraft.entity.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.world.Heightmap;

public class ModEntities {
    public static final EntityType<SharkEntity> SHARK = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(PixelPirates.MOD_ID,"shark"),
            FabricEntityTypeBuilder.create(SpawnGroup.WATER_CREATURE, SharkEntity::new)
                    // Matches the renderer's 2x scale (SharkEntityRenderer); fins overhang the box
                    .dimensions(EntityDimensions.fixed(1.8f, 1.2f))
                    .trackRangeChunks(8)
                    .build()
    );

    public static final EntityType<RaftEntity> RAFT = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(PixelPirates.MOD_ID,"raft"),
            FabricEntityTypeBuilder.<RaftEntity>create(SpawnGroup.MISC, RaftEntity::new)
                    .dimensions(EntityDimensions.fixed(7.0f, 0.25f)) // Adjust to fit your raft model
                    .trackRangeBlocks(12)
                    .trackedUpdateRate(1)
                    .build()
    );

    public static final EntityType<SloopEntity> SLOOP = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(PixelPirates.MOD_ID,"sloop"),
            FabricEntityTypeBuilder.<SloopEntity>create(SpawnGroup.MISC, SloopEntity::new)
                    .dimensions(EntityDimensions.fixed(14.0f, 7.0f)) // Adjust to fit your sloop model
                    .trackRangeBlocks(80)
                    .trackedUpdateRate(1)
                    .build()
    );

    public static final EntityType<CannonBallEntity> CANNON_BALL = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(PixelPirates.MOD_ID, "cannon_ball"),
            FabricEntityTypeBuilder.<CannonBallEntity>create(SpawnGroup.MISC,
                    (type, world) -> new CannonBallEntity(type, world))
                    .dimensions(EntityDimensions.fixed(0.4f, 0.4f))
                    .trackRangeBlocks(80)
                    .trackedUpdateRate(1)
                    .build()
    );

    public static final EntityType<MapMerchantEntity> MAP_MERCHANT = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(PixelPirates.MOD_ID, "map_merchant"),
            FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, MapMerchantEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .trackRangeChunks(8)
                    .build()
    );

    public static final EntityType<DynamiteEntity> DYNAMITE = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(PixelPirates.MOD_ID,"dynamite"),
            FabricEntityTypeBuilder.<DynamiteEntity>create(SpawnGroup.MISC, DynamiteEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(10)
                    .build()
    );

    /** Captain Rackham's rolling powder keg (renderer: PowderKegEntityRenderer, 0.8-block barrel). */
    public static final EntityType<net.get900.pixelpirates.entity.custom.PowderKegEntity> POWDER_KEG = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(PixelPirates.MOD_ID, "powder_keg"),
            FabricEntityTypeBuilder.<net.get900.pixelpirates.entity.custom.PowderKegEntity>create(SpawnGroup.MISC,
                            net.get900.pixelpirates.entity.custom.PowderKegEntity::new)
                    .dimensions(EntityDimensions.fixed(0.8f, 0.8f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(2)
                    .build()
    );

    public static final EntityType<net.get900.pixelpirates.entity.custom.DepthChargeEntity> DEPTH_CHARGE = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(PixelPirates.MOD_ID, "depth_charge"),
            FabricEntityTypeBuilder.<net.get900.pixelpirates.entity.custom.DepthChargeEntity>create(SpawnGroup.MISC, net.get900.pixelpirates.entity.custom.DepthChargeEntity::new)
                    .dimensions(EntityDimensions.fixed(0.3f, 0.3f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(10)
                    .build()
    );

    public static final EntityType<net.get900.pixelpirates.entity.custom.HarpoonEntity> HARPOON = Registry.register(
            Registries.ENTITY_TYPE, new Identifier(PixelPirates.MOD_ID, "harpoon"),
            FabricEntityTypeBuilder.<net.get900.pixelpirates.entity.custom.HarpoonEntity>create(SpawnGroup.MISC, net.get900.pixelpirates.entity.custom.HarpoonEntity::new)
                    .dimensions(EntityDimensions.fixed(0.4f, 0.4f)).trackRangeBlocks(96).trackedUpdateRate(1).build());
    /** A torn-off arm / leg / head of the Chained Revenant. */
    public static final EntityType<net.get900.pixelpirates.entity.mob.RevenantPartEntity> REVENANT_PART = Registry.register(
            Registries.ENTITY_TYPE, new Identifier(PixelPirates.MOD_ID, "revenant_part"),
            FabricEntityTypeBuilder.<net.get900.pixelpirates.entity.mob.RevenantPartEntity>create(SpawnGroup.MISC, net.get900.pixelpirates.entity.mob.RevenantPartEntity::new)
                    .dimensions(EntityDimensions.fixed(1.4f, 2.2f)).trackRangeBlocks(96).trackedUpdateRate(1).build());
    // ---- The Leviathan (boss 10/10): its body segments, its wake between lairs, and the hunt's props
    /** A piece of the Leviathan's body (the head is the "leviathan" spec). Hitbox scaled per segment (SIZE). */
    public static final EntityType<net.get900.pixelpirates.entity.mob.LeviathanSegmentEntity> LEVIATHAN_SEGMENT = Registry.register(
            Registries.ENTITY_TYPE, new Identifier(PixelPirates.MOD_ID, "leviathan_segment"),
            FabricEntityTypeBuilder.<net.get900.pixelpirates.entity.mob.LeviathanSegmentEntity>create(SpawnGroup.MISC, net.get900.pixelpirates.entity.mob.LeviathanSegmentEntity::new)
                    .dimensions(EntityDimensions.changing(4.6f, 3.8f)).trackRangeBlocks(256).trackedUpdateRate(1).fireImmune().build());
    public static final EntityType<net.get900.pixelpirates.entity.mob.LeviathanWakeEntity> LEVIATHAN_WAKE = Registry.register(
            Registries.ENTITY_TYPE, new Identifier(PixelPirates.MOD_ID, "leviathan_wake"),
            FabricEntityTypeBuilder.<net.get900.pixelpirates.entity.mob.LeviathanWakeEntity>create(SpawnGroup.MISC, net.get900.pixelpirates.entity.mob.LeviathanWakeEntity::new)
                    .dimensions(EntityDimensions.fixed(4f, 3f)).trackRangeBlocks(256).trackedUpdateRate(2).build());
    public static final EntityType<net.get900.pixelpirates.entity.custom.PowderBargeEntity> POWDER_BARGE = Registry.register(
            Registries.ENTITY_TYPE, new Identifier(PixelPirates.MOD_ID, "powder_barge"),
            FabricEntityTypeBuilder.<net.get900.pixelpirates.entity.custom.PowderBargeEntity>create(SpawnGroup.MISC, net.get900.pixelpirates.entity.custom.PowderBargeEntity::new)
                    .dimensions(EntityDimensions.fixed(2.6f, 1.4f)).trackRangeBlocks(96).trackedUpdateRate(2).build());
    public static final EntityType<net.get900.pixelpirates.entity.custom.BaneBoltEntity> BANE_BOLT = Registry.register(
            Registries.ENTITY_TYPE, new Identifier(PixelPirates.MOD_ID, "bane_bolt"),
            FabricEntityTypeBuilder.<net.get900.pixelpirates.entity.custom.BaneBoltEntity>create(SpawnGroup.MISC, net.get900.pixelpirates.entity.custom.BaneBoltEntity::new)
                    .dimensions(EntityDimensions.fixed(0.8f, 0.8f)).trackRangeBlocks(160).trackedUpdateRate(1).build());
    public static final EntityType<net.get900.pixelpirates.entity.custom.DebrisEntity> DEBRIS = Registry.register(
            Registries.ENTITY_TYPE, new Identifier(PixelPirates.MOD_ID, "debris"),
            FabricEntityTypeBuilder.<net.get900.pixelpirates.entity.custom.DebrisEntity>create(SpawnGroup.MISC, net.get900.pixelpirates.entity.custom.DebrisEntity::new)
                    .dimensions(EntityDimensions.fixed(1.6f, 1.6f)).trackRangeBlocks(128).trackedUpdateRate(1).build());

    public static final EntityType<net.get900.pixelpirates.entity.custom.KrakenHarpoonEntity> KRAKEN_HARPOON = Registry.register(
            Registries.ENTITY_TYPE, new Identifier(PixelPirates.MOD_ID, "kraken_harpoon"),
            FabricEntityTypeBuilder.<net.get900.pixelpirates.entity.custom.KrakenHarpoonEntity>create(SpawnGroup.MISC, net.get900.pixelpirates.entity.custom.KrakenHarpoonEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 0.6f)).trackRangeBlocks(96).trackedUpdateRate(1).build());
    public static final EntityType<net.get900.pixelpirates.entity.custom.ThrownGallowbrandEntity> THROWN_GALLOWBRAND = Registry.register(
            Registries.ENTITY_TYPE, new Identifier(PixelPirates.MOD_ID, "thrown_gallowbrand"),
            FabricEntityTypeBuilder.<net.get900.pixelpirates.entity.custom.ThrownGallowbrandEntity>create(SpawnGroup.MISC, net.get900.pixelpirates.entity.custom.ThrownGallowbrandEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 0.6f)).trackRangeBlocks(96).trackedUpdateRate(20).build());
    public static final EntityType<net.get900.pixelpirates.entity.custom.ChumEntity> CHUM = Registry.register(
            Registries.ENTITY_TYPE, new Identifier(PixelPirates.MOD_ID, "chum"),
            FabricEntityTypeBuilder.<net.get900.pixelpirates.entity.custom.ChumEntity>create(SpawnGroup.MISC, net.get900.pixelpirates.entity.custom.ChumEntity::new)
                    .dimensions(EntityDimensions.fixed(0.35f, 0.35f)).trackRangeBlocks(64).trackedUpdateRate(10).build());

    public static final EntityType<ThrownKnifeEntity> THROWN_KNIFE = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(PixelPirates.MOD_ID, "thrown_knife"),
            FabricEntityTypeBuilder.<ThrownKnifeEntity>create(SpawnGroup.MISC, ThrownKnifeEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25f, 0.25f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(10)
                    .build()
    );

    public static final EntityType<CursedMonkeyEntity> CURSED_MONKEY = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(PixelPirates.MOD_ID, "cursed_monkey"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, CursedMonkeyEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.0f))
                    .trackRangeChunks(8)
                    .build()
    );

    public static final EntityType<FloatingBarrelEntity> FLOATING_BARREL = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(PixelPirates.MOD_ID, "floating_barrel"),
            FabricEntityTypeBuilder.<FloatingBarrelEntity>create(SpawnGroup.MISC, FloatingBarrelEntity::new)
                    .dimensions(EntityDimensions.fixed(1.0f, 1.0f))
                    .trackRangeChunks(6)
                    .trackedUpdateRate(5)
                    .build()
    );

    public static final EntityType<CaptainEntity> SHIP_CAPTAIN = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(PixelPirates.MOD_ID, "ship_captain"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, CaptainEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .trackRangeChunks(8)
                    .build()
    );

    public static final EntityType<PirateCrewEntity> PIRATE_CREW = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(PixelPirates.MOD_ID, "pirate_crew"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, PirateCrewEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .trackRangeChunks(8)
                    .build()
    );

    public static final EntityType<ChestCrabEntity> CHEST_CRAB = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(PixelPirates.MOD_ID, "chest_crab"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, ChestCrabEntity::new)
                    .dimensions(EntityDimensions.fixed(1.0f, 0.8f))
                    .trackRangeChunks(8)
                    .build()
    );

    public static final EntityType<LavaCrabEntity> LAVA_CRAB = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(PixelPirates.MOD_ID, "lava_crab"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, LavaCrabEntity::new)
                    // Matches the renderer's 0.5x scale (LavaCrabEntityRenderer): shell ~0.55
                    // blocks across, legs span 1.12 and overhang the box
                    .dimensions(EntityDimensions.fixed(0.6f, 0.4f))
                    .fireImmune()
                    .trackRangeChunks(8)
                    .build()
    );

    public static final EntityType<CastawayEntity> CASTAWAY = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(PixelPirates.MOD_ID, "castaway"),
            FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, CastawayEntity::new)
                    // Model is authored at vanilla humanoid size; matches the other NPCs
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
                    .trackRangeChunks(8)
                    .build()
    );

    // --- Generated mobs (tools/gen_mob_assets.py). Each hitbox matches the renderer scale in
    // PixelPiratesClient (GlowingMobRenderer.of(name, SCALE, shadow)) - scale never moves the hitbox.
    public static final EntityType<SirenEntity> SIREN = Registry.register(Registries.ENTITY_TYPE,
            new Identifier(PixelPirates.MOD_ID, "siren"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, SirenEntity::new)
                    .dimensions(EntityDimensions.fixed(0.7f, 1.7f))      // 37px model at 0.75x
                    .trackRangeChunks(8).build());

    public static final EntityType<CoralJellyEntity> CORAL_JELLY = Registry.register(Registries.ENTITY_TYPE,
            new Identifier(PixelPirates.MOD_ID, "coral_jelly"),
            FabricEntityTypeBuilder.create(SpawnGroup.WATER_AMBIENT, CoralJellyEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.2f))      // 25px model at 0.8x
                    .trackRangeChunks(6).build());

    public static final EntityType<MagmaBruteEntity> MAGMA_BRUTE = Registry.register(Registries.ENTITY_TYPE,
            new Identifier(PixelPirates.MOD_ID, "magma_brute"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, MagmaBruteEntity::new)
                    .dimensions(EntityDimensions.fixed(1.6f, 2.3f))      // 30px model at 1.25x
                    .fireImmune()
                    .trackRangeChunks(8).build());

    public static final EntityType<MimicEntity> MIMIC = Registry.register(Registries.ENTITY_TYPE,
            new Identifier(PixelPirates.MOD_ID, "mimic"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, MimicEntity::new)
                    .dimensions(EntityDimensions.fixed(0.9f, 0.9f))      // chest-sized, 1.0x
                    .trackRangeChunks(8).build());

    public static final EntityType<AbyssalAnglerEntity> ABYSSAL_ANGLER = Registry.register(Registries.ENTITY_TYPE,
            new Identifier(PixelPirates.MOD_ID, "abyssal_angler"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, AbyssalAnglerEntity::new)
                    .dimensions(EntityDimensions.fixed(1.3f, 1.1f))      // 30px-long model at 1.0x
                    .trackRangeChunks(8).build());

    /** Shared projectile for every data-driven mob's ranged ability (entity/mob/MobProjectileEntity). */
    public static final EntityType<net.get900.pixelpirates.entity.mob.MobProjectileEntity> MOB_PROJECTILE = Registry.register(
            Registries.ENTITY_TYPE, new Identifier(PixelPirates.MOD_ID, "mob_projectile"),
            FabricEntityTypeBuilder.<net.get900.pixelpirates.entity.mob.MobProjectileEntity>create(SpawnGroup.MISC,
                            net.get900.pixelpirates.entity.mob.MobProjectileEntity::new)
                    .dimensions(EntityDimensions.fixed(0.3f, 0.3f)).trackRangeBlocks(64).trackedUpdateRate(10).build());

    public static void registerModEntities() {
        PixelPirates.LOGGER.info("Registering ModEntities for " + PixelPirates.MOD_ID);
    }
}
