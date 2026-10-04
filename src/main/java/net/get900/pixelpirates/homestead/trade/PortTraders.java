package net.get900.pixelpirates.homestead.trade;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.entity.ModEntities;
import net.get900.pixelpirates.entity.custom.MapMerchantEntity;
import net.get900.pixelpirates.homestead.HomesteadEntities;
import net.get900.pixelpirates.homestead.HomesteadState;
import net.get900.pixelpirates.world.gen.PortCityLayout;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * Seats the market's keepers - the seven Port Traders and the Map Merchant - behind their booths in the Wavebreak
 * Bazaar (PortCityLayout.MARKET_BOOTHS / boothStand: index = PortTraderEntity.Kind ordinal, 7 = the Map Merchant)
 * the first time a player is near with the market loaded. Existing keepers (older worlds: four traders in the old
 * stalls, a Map Merchant by the spawn plaza) are MOVED to their booths rather than duplicated; missing ones are
 * spawned. Once per world (HomesteadState flag "market_v2"); `/ppmarket seat` re-runs it.
 */
public final class PortTraders {
    private PortTraders() {}

    public static final RegistryKey<World> DIM = RegistryKey.of(RegistryKeys.WORLD, new Identifier(PixelPirates.MOD_ID, "pixel_pirates"));
    /** Where an old keeper may be: the whole market, the spawn plaza and the streets round them. */
    private static final Box SEARCH = new Box(-70, 55, -40, 70, 90, 60);

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(w -> {
            if (w.getTime() % 100 != 7 || !w.getRegistryKey().equals(DIM)) return;
            HomesteadState st = HomesteadState.get(w.getServer());
            if (st.flag("market_v2")) return;
            for (int i = 0; i < PortCityLayout.MARKET_BOOTHS.length; i++) {
                int[] s = PortCityLayout.boothStand(i);
                if (!w.isChunkLoaded(ChunkPos.toLong(new BlockPos(s[0], s[1], s[2])))) return;
            }
            if (w.getClosestPlayer(0, 68, 30, 96, false) == null) return;
            seatAll(w);
            st.setFlag("market_v2");
        });
    }

    /** Move every keeper to his booth, spawning the missing ones. Returns how many were spawned. */
    public static int seatAll(ServerWorld w) {
        List<PortTraderEntity> traders = w.getEntitiesByClass(PortTraderEntity.class, SEARCH, Entity::isAlive);
        List<MapMerchantEntity> merchants = w.getEntitiesByClass(MapMerchantEntity.class, SEARCH, Entity::isAlive);
        int spawned = 0;
        for (PortTraderEntity.Kind k : PortTraderEntity.Kind.values()) {
            PortTraderEntity t = traders.stream().filter(e -> e.kind() == k).findFirst().orElse(null);
            if (t == null) {
                t = HomesteadEntities.PORT_TRADER.create(w);
                if (t == null) continue;
                t.setKind(k);
                seat(t, k.ordinal());
                t.initialize(w, w.getLocalDifficulty(t.getBlockPos()), SpawnReason.STRUCTURE, null, null);
                if (w.spawnEntity(t)) spawned++;
            } else {
                traders.remove(t);
                seat(t, k.ordinal());
                t.setHome(t.getBlockPos());
            }
        }
        // a duplicate kind (two quartermasters from an old /summon) is left where it is
        int mapIdx = PortCityLayout.MARKET_BOOTHS.length - 1;
        MapMerchantEntity m = merchants.isEmpty() ? null : merchants.get(0);
        if (m == null) {
            m = ModEntities.MAP_MERCHANT.create(w);
            if (m != null) {
                seat(m, mapIdx);
                if (w.spawnEntity(m)) spawned++;
            }
        } else seat(m, mapIdx);
        if (m != null) m.setHome(m.getBlockPos());
        PixelPirates.LOGGER.info("[Homestead] market keepers seated in their booths ({} spawned, {} moved)",
                spawned, PortCityLayout.MARKET_BOOTHS.length - spawned);
        return spawned;
    }

    private static void seat(MobEntity e, int booth) {
        int[] s = PortCityLayout.boothStand(booth);
        e.getNavigation().stop();
        e.refreshPositionAndAngles(s[0] + 0.5, s[1], s[2] + 0.5, s[3], 0);
        e.setHeadYaw(s[3]);
        e.setBodyYaw(s[3]);
        if (e instanceof PortTraderEntity t) t.setHome(new BlockPos(s[0], s[1], s[2]));
        e.setPersistent();
    }
}
