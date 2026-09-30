package net.get900.pixelpirates.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class RadarState {

    public static boolean hasRadar     = false;
    public static boolean hasPlayerShip = false;
    public static double playerShipX    = 0;
    public static double playerShipZ    = 0;

    /** Each entry is [worldX, worldZ] for one AI ship. */
    public static final List<double[]> aiShips = new ArrayList<>();

    public static void update(boolean radar, boolean hasShip, double sx, double sz, List<double[]> ais) {
        hasRadar      = radar;
        hasPlayerShip = hasShip;
        playerShipX   = sx;
        playerShipZ   = sz;
        aiShips.clear();
        aiShips.addAll(ais);
    }
}
