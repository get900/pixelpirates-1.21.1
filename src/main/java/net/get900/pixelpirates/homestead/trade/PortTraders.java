package net.get900.pixelpirates.homestead.trade;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.homestead.HomesteadEntities;
import net.get900.pixelpirates.homestead.HomesteadState;
import net.minecraft.entity.SpawnReason;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

/**
 * Sets the four Port Traders up in Wavebreak Port's market square (stalls on the north row, PortCityLayout
 * marketSquare(): stall x1..x1+6, z1..z1+3, ground y67; each trader stands inside his stall at x1+5, z1+2) the first
 * time a player is near the market with its chunks loaded. Once per world (HomesteadState flag "port_traders").
 */
public final class PortTraders {
    private PortTraders() {}

    public static final RegistryKey<World> DIM = RegistryKey.of(RegistryKeys.WORLD, new Identifier(PixelPirates.MOD_ID, "pixel_pirates"));
    static final int[][] STALLS = {{-34, 18}, {-22, 18}, {12, 18}, {24, 18}};
    static final PortTraderEntity.Kind[] KINDS = {PortTraderEntity.Kind.QUARTERMASTER, PortTraderEntity.Kind.FISHMONGER,
            PortTraderEntity.Kind.BARKEEP, PortTraderEntity.Kind.CURIO_DEALER};

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(w -> {
            if (w.getTime() % 100 != 7 || !w.getRegistryKey().equals(DIM)) return;
            HomesteadState st = HomesteadState.get(w.getServer());
            if (st.flag("port_traders")) return;
            for (int[] s : STALLS) if (!w.isChunkLoaded(ChunkPos.toLong(new BlockPos(s[0] + 5, 68, s[1] + 2)))) return;
            if (w.getClosestPlayer(0, 68, 30, 96, false) == null) return;
            spawnAll(w);
            st.setFlag("port_traders");
        });
    }

    /** Spawn the market traders (also used by /pptest traders, which ignores the flag). */
    public static int spawnAll(ServerWorld w) {
        int n = 0;
        for (int i = 0; i < STALLS.length; i++) {
            BlockPos at = new BlockPos(STALLS[i][0] + 5, 68, STALLS[i][1] + 2);
            PortTraderEntity t = HomesteadEntities.PORT_TRADER.create(w);
            if (t == null) continue;
            t.setKind(KINDS[i]);
            t.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);    // facing south, over the counter
            t.setHome(at);
            t.initialize(w, w.getLocalDifficulty(at), SpawnReason.STRUCTURE, null, null);
            if (w.spawnEntity(t)) n++;
        }
        PixelPirates.LOGGER.info("[Homestead] {} port traders set up in the market square", n);
        return n;
    }
}
