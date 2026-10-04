package net.get900.pixelpirates.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * SHIP RADAR, drawn as a BRASS COMPASS (2026-10-01 revamp; same function as before): a sea-chart dial with a compass
 * rose, your own ship as a gold sail pip, AI ships as red skull pips within 300 blocks (north up), and a needle turning
 * with your facing. Art: tools/gen_gui_textures.py radar() -> textures/gui/radar.png.
 */
@Environment(EnvType.CLIENT)
public class ShipRadarHud {
    private static final Identifier TEX = new Identifier("pixelpirates", "textures/gui/radar.png");
    private static final int TW = 256, TH = 128, ART = 96;   // texture atlas + compass size in texels
    private static final int SIZE = 80;                       // on-screen size (GUI pixels)
    private static final int MARGIN = 6;
    private static final int RANGE = 300;                     // world blocks from you to the dial's edge
    private static final float DIAL = 40f / 48f;              // the chart dial's radius as a share of the compass radius

    public static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        if (!RadarState.hasRadar) return;

        int rx = client.getWindow().getScaledWidth() - SIZE - MARGIN, ry = MARGIN;
        float cx = rx + SIZE / 2f, cy = ry + SIZE / 2f;
        ctx.drawTexture(TEX, rx, ry, SIZE, SIZE, 0, 0, ART, ART, TW, TH);

        double px = client.player.getX(), pz = client.player.getZ();
        if (RadarState.hasPlayerShip) pip(ctx, RadarState.playerShipX, RadarState.playerShipZ, px, pz, cx, cy, 0);
        for (double[] ship : RadarState.aiShips) pip(ctx, ship[0], ship[1], px, pz, cx, cy, 10);

        // the needle points where you look (yaw 0 = south; screen up = north)
        float s = SIZE / (float) ART;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, cy, 0);
        ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(client.player.getYaw(tickDelta) + 180f));
        ctx.getMatrices().scale(s, s, 1);
        ctx.drawTexture(TEX, -5, -22, 100, 0, 10, 44, TW, TH);
        ctx.getMatrices().pop();

        ctx.drawTexture(TEX, rx, ry, SIZE, SIZE, 128, 0, ART, ART, TW, TH);     // glass glint over everything
    }

    /** A 9x9 pip (v = 0 own ship, 10 enemy) at a world position, if it falls on the dial. */
    private static void pip(DrawContext ctx, double wx, double wz, double px, double pz, float cx, float cy, int v) {
        float radius = SIZE / 2f * DIAL;
        double dx = (wx - px) / RANGE * radius, dz = (wz - pz) / RANGE * radius;
        if (dx * dx + dz * dz > (radius - 3) * (radius - 3)) return;
        float s = SIZE / (float) ART;
        int size = Math.max(5, Math.round(9 * s));
        ctx.drawTexture(TEX, Math.round(cx + (float) dx - size / 2f), Math.round(cy + (float) dz - size / 2f), size, size, 112, v, 9, 9, TW, TH);
    }
}
