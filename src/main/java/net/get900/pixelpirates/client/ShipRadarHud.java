package net.get900.pixelpirates.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

@Environment(EnvType.CLIENT)
public class ShipRadarHud {

    private static final int SIZE   = 72;  // pixels square (down from 100)
    private static final int MARGIN = 6;   // pixels from screen edge
    private static final int RANGE  = 300; // world blocks from player to radar edge

    private static final int COL_BG        = 0xB0051015;
    private static final int COL_BORDER    = 0xFF886644;
    private static final int COL_CROSSHAIR = 0x40FFFFFF;
    private static final int COL_PLAYER    = 0xFFFFFFFF;
    private static final int COL_OWN_SHIP  = 0xFFFFCC00;
    private static final int COL_AI_SHIP   = 0xFFFF3333;
    private static final int COL_LABEL     = 0xFFAAFFAA;

    public static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        if (!RadarState.hasRadar) return;

        int sw  = client.getWindow().getScaledWidth();
        int rx  = sw - SIZE - MARGIN;
        int ry  = MARGIN;
        int cx  = rx + SIZE / 2;
        int cy  = ry + SIZE / 2;

        // Background
        ctx.fill(rx, ry, rx + SIZE, ry + SIZE, COL_BG);

        // Border (1-pixel lines on each edge)
        ctx.fill(rx,          ry,          rx + SIZE,     ry + 1,        COL_BORDER);
        ctx.fill(rx,          ry + SIZE-1, rx + SIZE,     ry + SIZE,     COL_BORDER);
        ctx.fill(rx,          ry,          rx + 1,        ry + SIZE,     COL_BORDER);
        ctx.fill(rx + SIZE-1, ry,          rx + SIZE,     ry + SIZE,     COL_BORDER);

        // Crosshair
        ctx.fill(cx, ry + 1,  cx + 1, ry + SIZE - 1, COL_CROSSHAIR);
        ctx.fill(rx + 1, cy,  rx + SIZE - 1, cy + 1, COL_CROSSHAIR);

        // Compass labels
        TextRenderer tr = client.textRenderer;
        ctx.drawTextWithShadow(tr, "N", cx - 2,         ry + 2,        COL_LABEL);
        ctx.drawTextWithShadow(tr, "S", cx - 2,         ry + SIZE - 9, COL_LABEL);
        ctx.drawTextWithShadow(tr, "W", rx + 2,         cy - 4,        COL_LABEL);
        ctx.drawTextWithShadow(tr, "E", rx + SIZE - 7,  cy - 4,        COL_LABEL);

        double px = client.player.getX();
        double pz = client.player.getZ();

        // Own ship — gold dot
        if (RadarState.hasPlayerShip) {
            int[] m = project(RadarState.playerShipX, RadarState.playerShipZ, px, pz, rx, ry);
            if (m != null) fillDot(ctx, m[0], m[1], 3, COL_OWN_SHIP);
        }

        // AI ships — red dots
        for (double[] ship : RadarState.aiShips) {
            int[] m = project(ship[0], ship[1], px, pz, rx, ry);
            if (m != null) fillDot(ctx, m[0], m[1], 2, COL_AI_SHIP);
        }

        // Player directional arrow (drawn last so it's always on top)
        drawArrow(ctx, cx, cy, client.player.getYaw(tickDelta));
    }

    /**
     * Draws a small directional arrow centred at (cx, cy) pointing in the
     * direction the player is facing on a north-up map.
     *
     * Minecraft yaw: 0 = south, 90 = west, -90 = east, ±180 = north.
     * Screen: +X = east (right), +Y = south (down).
     * Rotation angle so yaw 0 (south) points down on screen: angle = yaw + 180.
     */
    private static void drawArrow(DrawContext ctx, int cx, int cy, float yaw) {
        double a   = Math.toRadians(yaw + 180.0);
        double sin = Math.sin(a);
        double cos = Math.cos(a);

        // Arrow defined in local space: tip points "up" (screen -Y = north).
        // rotate(lx, ly) → (lx*cos - ly*sin, lx*sin + ly*cos)
        // Tip
        drawPixel(ctx, cx, cy, rot(0, -4, sin, cos), 0xFFFFFFFF);
        drawPixel(ctx, cx, cy, rot(0, -3, sin, cos), 0xFFFFFFFF);
        // Shaft
        drawPixel(ctx, cx, cy, rot(0, -2, sin, cos), 0xCCFFFFFF);
        drawPixel(ctx, cx, cy, rot(0, -1, sin, cos), 0xCCFFFFFF);
        // Arrowhead wings (at d=-2, ±2 sides)
        drawPixel(ctx, cx, cy, rot(-2, -1, sin, cos), 0xAAFFFFFF);
        drawPixel(ctx, cx, cy, rot( 2, -1, sin, cos), 0xAAFFFFFF);
        // Centre / tail
        drawPixel(ctx, cx, cy, rot(0, 0, sin, cos), 0x80FFFFFF);
    }

    /** Rotates local (lx, ly) by (sin, cos) and returns screen offset as int[2]. */
    private static int[] rot(int lx, int ly, double sin, double cos) {
        return new int[]{
            (int) Math.round(lx * cos - ly * sin),
            (int) Math.round(lx * sin + ly * cos)
        };
    }

    private static void drawPixel(DrawContext ctx, int cx, int cy, int[] offset, int colour) {
        int x = cx + offset[0];
        int y = cy + offset[1];
        ctx.fill(x, y, x + 1, y + 1, colour);
    }

    /** Projects a world (wx, wz) to a radar screen pixel. Returns null if outside the panel. */
    private static int[] project(double wx, double wz, double px, double pz, int rx, int ry) {
        double scale = (SIZE / 2.0) / RANGE;
        int sx = rx + SIZE / 2 + (int) Math.round((wx - px) * scale);
        int sy = ry + SIZE / 2 + (int) Math.round((wz - pz) * scale);
        if (sx < rx + 1 || sx >= rx + SIZE - 1 || sy < ry + 1 || sy >= ry + SIZE - 1) return null;
        return new int[]{sx, sy};
    }

    private static void fillDot(DrawContext ctx, int x, int y, int r, int colour) {
        ctx.fill(x - r, y - r, x + r, y + r, colour);
    }
}
