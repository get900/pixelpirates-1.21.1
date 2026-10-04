package net.get900.pixelpirates.homestead.darts;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * DARTS (2026-10-05): the board's geometry and scoring, and how a townsperson aims.
 *
 * The board (DartboardBlock, authored facing NORTH = its face looks north, its back on the wall to the south) is a disc
 * of radius {@link #R_DOUBLE} px centred on the block, its face {@link #FACE_DEPTH} px out from the wall. Rings in px
 * from the centre (game-sized, not to scale - a real bull would be a quarter of a pixel): bull, outer bull, treble,
 * double. Segments run clockwise from the top in the standard order {@link #ORDER}, as seen by the thrower.
 */
public final class Darts {
    private Darts() {}

    public static final int[] ORDER = {20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5};
    public static final double R_BULL = 0.6, R_OUTER = 1.3, R_TREBLE_IN = 3.6, R_TREBLE_OUT = 4.3, R_DOUBLE_IN = 6.2, R_DOUBLE = 7.0;
    /** The face plane: px out from the wall (the back plate is 1 px, the board 1.5 px). */
    public static final double FACE_DEPTH = 2.5;
    public static final int START = 301;

    /** One dart's score: value (0 = a miss) and how it reads ("T20", "D16", "Bull", "25", "7", "Miss"). */
    public record Hit(int value, String label) {}

    /** The board's centre on its face, for a board block at {@code pos} facing {@code f}. */
    public static Vec3d faceCentre(BlockPos pos, Direction f) {
        double k = FACE_DEPTH / 16 - 0.5;                                   // back edge (-0.5 along f) + the depth
        return Vec3d.ofCenter(pos).add(f.getOffsetX() * k, 0, f.getOffsetZ() * k);
    }

    /** The thrower's right on the board face (they look at it, i.e. towards -facing). */
    public static Vec3d right(Direction f) {
        Direction r = f.rotateYCounterclockwise();
        return new Vec3d(r.getOffsetX(), 0, r.getOffsetZ());
    }

    /** Score a point on (or near) the face. u = px to the thrower's right of the centre, v = px up. */
    public static Hit score(double u, double v) {
        double r = Math.sqrt(u * u + v * v);
        if (r <= R_BULL) return new Hit(50, "Bull");
        if (r <= R_OUTER) return new Hit(25, "25");
        if (r > R_DOUBLE) return new Hit(0, "Miss");
        double deg = Math.toDegrees(Math.atan2(u, v));                   // 0 = up, clockwise
        int seg = ORDER[Math.floorMod((int) Math.floor((deg + 9) / 18), 20)];
        if (r >= R_DOUBLE_IN) return new Hit(seg * 2, "D" + seg);
        if (r >= R_TREBLE_IN && r <= R_TREBLE_OUT) return new Hit(seg * 3, "T" + seg);
        return new Hit(seg, String.valueOf(seg));
    }

    /** A world point on the face -> (u, v) px. */
    public static double[] uv(BlockPos pos, Direction f, Vec3d hit) {
        Vec3d d = hit.subtract(faceCentre(pos, f));
        return new double[]{d.dotProduct(right(f)) * 16, d.y * 16};
    }

    /** (u, v) px -> the world point on the face. */
    public static Vec3d point(BlockPos pos, Direction f, double u, double v) {
        return faceCentre(pos, f).add(right(f).multiply(u / 16)).add(0, v / 16, 0);
    }

    // ------------------------------------------------------------------ aiming (townsfolk)
    /** The spot to aim for with {@code left} to go: (u, v) px. 301 here ends on exactly 0 (any bed - no double-out). */
    public static double[] aim(int left) {
        if (left == 50 || left == 25) return new double[]{0, 0};
        if (left <= 20) return centreOf(left, 1);                              // a single to finish
        if (left <= 40 && left % 2 == 0) return centreOf(left / 2, 2);         // a double to finish
        if (left <= 60 && left % 3 == 0) return centreOf(left / 3, 3);         // a treble to finish
        if (left < 40) return centreOf(left - 20, 1);                          // odd: leave 20 (D10)
        if (left <= 60) return centreOf(20, 1);                                // leave 21..40
        if (left < 100) return centreOf(Math.max(1, Math.min(20, (left - 32) / 3)), 3);   // leave about D16
        return centreOf(20, 3);                                                // treble twenty
    }

    /** The middle of segment {@code seg}'s single (1), double (2) or treble (3) bed. */
    static double[] centreOf(int seg, int bed) {
        int i = 0;
        while (ORDER[i] != seg) i++;
        double ang = Math.toRadians(i * 18.0);
        double r = switch (bed) { case 2 -> (R_DOUBLE_IN + R_DOUBLE) / 2; case 3 -> (R_TREBLE_IN + R_TREBLE_OUT) / 2; default -> 5.2; };
        return new double[]{Math.sin(ang) * r, Math.cos(ang) * r};
    }

    /** Where a dart aimed at (u, v) lands for a thrower of {@code skill} 0..1: a scatter of 0.8 (skilled) to 3 px. */
    public static double[] scatter(double[] at, double skill, Random r) {
        double s = 3.0 - 2.2 * Math.max(0, Math.min(1, skill));
        return new double[]{at[0] + r.nextGaussian() * s, at[1] + r.nextGaussian() * s};
    }
}
