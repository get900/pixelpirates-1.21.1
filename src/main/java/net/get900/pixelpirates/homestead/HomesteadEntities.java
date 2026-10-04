package net.get900.pixelpirates.homestead;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.homestead.furniture.SeatEntity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class HomesteadEntities {
    private HomesteadEntities() {}

    static <T extends net.minecraft.entity.Entity> EntityType<T> register(String name, EntityType<T> type) {
        return Registry.register(Registries.ENTITY_TYPE, new Identifier(PixelPirates.MOD_ID, name), type);
    }

    public static final EntityType<SeatEntity> SEAT = register("seat", FabricEntityTypeBuilder.<SeatEntity>create(SpawnGroup.MISC, SeatEntity::new)
            .dimensions(EntityDimensions.fixed(0.01f, 0.01f)).trackRangeBlocks(16).trackedUpdateRate(20).disableSummon().build());

    public static final EntityType<net.get900.pixelpirates.homestead.gun.MusketBallEntity> MUSKET_BALL = register("musket_ball",
            FabricEntityTypeBuilder.<net.get900.pixelpirates.homestead.gun.MusketBallEntity>create(SpawnGroup.MISC, net.get900.pixelpirates.homestead.gun.MusketBallEntity::new)
                    .dimensions(EntityDimensions.fixed(0.15f, 0.15f)).trackRangeBlocks(64).trackedUpdateRate(10).build());

    public static final EntityType<net.get900.pixelpirates.homestead.trade.PortTraderEntity> PORT_TRADER = register("port_trader",
            FabricEntityTypeBuilder.<net.get900.pixelpirates.homestead.trade.PortTraderEntity>create(SpawnGroup.MISC, net.get900.pixelpirates.homestead.trade.PortTraderEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f)).trackRangeBlocks(10).build());

    public static final EntityType<net.get900.pixelpirates.homestead.grapple.GrappleHookEntity> GRAPPLE_HOOK = register("grapple_hook",
            FabricEntityTypeBuilder.<net.get900.pixelpirates.homestead.grapple.GrappleHookEntity>create(SpawnGroup.MISC, net.get900.pixelpirates.homestead.grapple.GrappleHookEntity::new)
                    .dimensions(EntityDimensions.fixed(0.3f, 0.3f)).trackRangeBlocks(64).trackedUpdateRate(2).build());

    public static final EntityType<net.get900.pixelpirates.homestead.art.CustomPaintingEntity> CUSTOM_PAINTING = register("player_painting",
            FabricEntityTypeBuilder.<net.get900.pixelpirates.homestead.art.CustomPaintingEntity>create(SpawnGroup.MISC, net.get900.pixelpirates.homestead.art.CustomPaintingEntity::new)
                    .dimensions(EntityDimensions.fixed(0.5f, 0.5f)).trackRangeChunks(10).trackedUpdateRate(Integer.MAX_VALUE).build());
    public static final EntityType<net.get900.pixelpirates.homestead.swing.SwingSeatEntity> SWING_SEAT = register("swing_seat",
            FabricEntityTypeBuilder.<net.get900.pixelpirates.homestead.swing.SwingSeatEntity>create(SpawnGroup.MISC, net.get900.pixelpirates.homestead.swing.SwingSeatEntity::new)
                    .dimensions(EntityDimensions.fixed(0.01f, 0.01f)).trackRangeBlocks(48).trackedUpdateRate(1).disableSummon().build());

    /** The named townsfolk of Wavebreak (homestead/town). */
    public static final EntityType<net.get900.pixelpirates.homestead.town.TownsfolkEntity> TOWNSFOLK = register("townsfolk",
            FabricEntityTypeBuilder.<net.get900.pixelpirates.homestead.town.TownsfolkEntity>create(SpawnGroup.MISC, net.get900.pixelpirates.homestead.town.TownsfolkEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f)).trackRangeBlocks(64).build());

    /** The harbour gulls (homestead/town/SeagullEntity). */
    public static final EntityType<net.get900.pixelpirates.homestead.town.SeagullEntity> SEAGULL = register("seagull",
            FabricEntityTypeBuilder.<net.get900.pixelpirates.homestead.town.SeagullEntity>create(SpawnGroup.MISC, net.get900.pixelpirates.homestead.town.SeagullEntity::new)
                    .dimensions(EntityDimensions.fixed(0.5f, 0.5f)).trackRangeBlocks(80).build());

    /** A thrown dart (homestead/darts/DartEntity). */
    public static final EntityType<net.get900.pixelpirates.homestead.darts.DartEntity> DART = register("dart",
            FabricEntityTypeBuilder.<net.get900.pixelpirates.homestead.darts.DartEntity>create(SpawnGroup.MISC, net.get900.pixelpirates.homestead.darts.DartEntity::new)
                    .dimensions(EntityDimensions.fixed(0.15f, 0.15f)).trackRangeBlocks(32).trackedUpdateRate(1).forceTrackedVelocityUpdates(true).build());

    public static void init() {
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(SEAGULL, net.get900.pixelpirates.homestead.town.SeagullEntity.attributes());
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(TOWNSFOLK, net.get900.pixelpirates.homestead.town.TownsfolkEntity.attributes());
        net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry.register(PORT_TRADER, net.get900.pixelpirates.homestead.trade.PortTraderEntity.attributes());
    }
}
