package net.get900.pixelpirates.world;

import net.get900.pixelpirates.PixelPirates;
import org.valkyrienskies.core.impl.config.VSCoreConfig;

/**
 * How far from a player VS2 keeps ships loaded and simulated (2026-10-05; the user: "if a boat is about 80 blocks away I
 * want it to still be simulated, otherwise you will never get the effect of it coming into port"). VS2's defaults
 * (vs-core-server.toml: load 128, unload 196 - measured in 3D, so a player up on the quay or in the rigging loses ships
 * sooner) made regatta racers, a ship coming into port and AI broadsides vanish mid-act. At server start, after VS2 has
 * read its config, this RAISES both to at least {@link #LOAD} / {@link #UNLOAD} - a server owner's larger values stay.
 */
public final class ShipSimDistance {
    private ShipSimDistance() {}

    public static final double LOAD = 320, UNLOAD = 384;

    public static void apply() {
        try {
            VSCoreConfig.Server s = VSCoreConfig.SERVER;
            double load = Math.max(LOAD, s.getShipLoadDistance()), unload = Math.max(UNLOAD, Math.max(load + 48, s.getShipUnloadDistance()));
            s.setShipLoadDistance(load);
            s.setShipUnloadDistance(unload);
            PixelPirates.LOGGER.info("[Ships] VS2 ship load/unload distance {} / {} blocks", (int) load, (int) unload);
        } catch (Throwable t) {                                             // a VS2 update that moved the config: keep running
            PixelPirates.LOGGER.warn("[Ships] could not raise the VS2 ship load distance: {}", t.toString());
        }
    }
}
