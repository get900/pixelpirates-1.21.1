package net.get900.pixelpirates.homestead.town;

import net.get900.pixelpirates.homestead.art.Art;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.random.Random;

/**
 * The townsfolk's own paintings (TownLife's painters): seascapes, sunsets, storms, a lighthouse by night, an island with a
 * palm, flowers in a jug. Each is drawn from scratch with its own random sky, sea, sun and ship, so no two are the same;
 * the painter's name goes on it as the author. The easel shows it filling in row by row while they paint.
 */
public final class TownArt {
    private TownArt() {}

    private static final String[][] TITLES = {
            {"Harbour at Dawn", "Calm Waters", "Fair Wind", "Off the Point", "The Morning Tide", "Homeward Bound"},
            {"Sunset over Wavebreak", "Red Sky at Night", "The Last Light", "Evening Sail"},
            {"The Squall", "Storm off the Reef", "Weathering It", "The Black Gale"},
            {"The Wavebreak Light", "Night Watch", "Keeper's Lamp"},
            {"The Island", "Castaway's Rest", "A Palm and a Prayer"},
            {"Flowers in a Jug", "Still Life with Jug", "From the Garden"}};

    /** {Size, Title, Author, Pixels} - the painting item's compound (Art). */
    public static NbtCompound paint(Random r, String author) {
        int kind = r.nextInt(6);
        Art.Size size = kind == 5 ? Art.Size.SQUARE : r.nextInt(3) == 0 ? Art.Size.WIDE : Art.Size.SQUARE;
        int w = size.pw, h = size.ph;
        int[] px = new int[w * h];
        switch (kind) {
            case 0 -> sea(px, w, h, r, sky(r, 0), false, false);
            case 1 -> sea(px, w, h, r, sky(r, 1), true, false);
            case 2 -> sea(px, w, h, r, sky(r, 2), false, true);
            case 3 -> lighthouse(px, w, h, r);
            case 4 -> island(px, w, h, r);
            default -> flowers(px, w, h, r);
        }
        for (int i = 0; i < px.length; i++) px[i] |= 0xFF000000;
        NbtCompound n = new NbtCompound();
        n.putByte("Size", (byte) size.ordinal());
        String[] t = TITLES[kind];
        n.putString("Title", t[r.nextInt(t.length)]);
        n.putString("Author", author);
        n.putIntArray("Pixels", px);
        return n;
    }

    private static int rgb(int r, int g, int b) {
        return Math.max(0, Math.min(255, r)) << 16 | Math.max(0, Math.min(255, g)) << 8 | Math.max(0, Math.min(255, b));
    }

    private static int lerp(int a, int b, float f) {
        return rgb((int) (((a >> 16) & 255) * (1 - f) + ((b >> 16) & 255) * f), (int) (((a >> 8) & 255) * (1 - f) + ((b >> 8) & 255) * f),
                (int) ((a & 255) * (1 - f) + (b & 255) * f));
    }

    /** Sky top / horizon colours: 0 day, 1 sunset, 2 storm. */
    private static int[] sky(Random r, int mood) {
        int j = r.nextInt(30) - 15;
        return switch (mood) {
            case 1 -> new int[]{rgb(70 + j, 50, 120), rgb(250, 140 + j, 60)};
            case 2 -> new int[]{rgb(50, 56 + j, 66), rgb(110, 118 + j, 124)};
            default -> new int[]{rgb(100 + j, 160 + j, 230), rgb(220, 226, 200 + j)};
        };
    }

    private static void sea(int[] px, int w, int h, Random r, int[] sky, boolean sunset, boolean storm) {
        int horizon = h * (9 + r.nextInt(4)) / 16;
        int sunX = 4 + r.nextInt(w - 8), sunY = horizon - 2 - r.nextInt(Math.max(1, horizon / 2));
        int seaTop = storm ? rgb(40, 60, 70) : sunset ? rgb(90, 60, 90) : rgb(40, 110, 170), seaBot = storm ? rgb(20, 30, 36) : rgb(16, 50, 90);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int c;
                if (y < horizon) {
                    c = lerp(sky[0], sky[1], y / (float) horizon);
                    int d = (x - sunX) * (x - sunX) + (y - sunY) * (y - sunY);
                    if (!storm && d <= 5) c = sunset ? rgb(255, 210, 120) : rgb(255, 246, 200);
                    else if (!storm && d <= 12) c = lerp(c, sunset ? rgb(255, 170, 90) : rgb(255, 240, 200), 0.5f);
                } else {
                    c = lerp(seaTop, seaBot, (y - horizon) / (float) Math.max(1, h - horizon));
                    if (!storm && Math.abs(x - sunX) <= 1 + (y - horizon) / 3 && (x + y) % 3 == 0) c = sunset ? rgb(250, 170, 90) : rgb(246, 220, 150);
                    if ((x * 7 + y * 13 + r.nextInt(3)) % (storm ? 7 : 19) == 0) c = storm ? rgb(200, 210, 214) : rgb(200, 226, 240);
                }
                px[y * w + x] = c;
            }
        clouds(px, w, horizon, r, storm ? rgb(70, 76, 84) : sunset ? rgb(240, 150, 140) : rgb(244, 240, 232), storm ? 5 : 2);
        if (storm) for (int y = 1, x = 4 + r.nextInt(w - 8); y < horizon && x > 0 && x < w; y++) { px[y * w + x] = rgb(250, 250, 220); if (r.nextInt(2) == 0) x += r.nextBoolean() ? 1 : -1; }
        if (r.nextInt(4) != 0) ship(px, w, horizon, 3 + r.nextInt(Math.max(1, w - 14)), r, storm);
    }

    private static void clouds(int[] px, int w, int horizon, Random r, int col, int n) {
        for (int i = 0; i < n; i++) {
            int cx = r.nextInt(w), cy = 1 + r.nextInt(Math.max(1, horizon / 2)), len = 4 + r.nextInt(6);
            for (int x = cx; x < Math.min(w, cx + len); x++) {
                px[cy * w + x] = col;
                if (x > cx && x < cx + len - 1 && cy > 0) px[(cy - 1) * w + x] = col;
            }
        }
    }

    private static void ship(int[] px, int w, int horizon, int x0, Random r, boolean storm) {
        int hull = storm ? rgb(30, 22, 18) : rgb(90, 58, 34), sail = r.nextInt(5) == 0 ? rgb(30, 30, 30) : rgb(242, 234, 216);
        int y0 = horizon + 1, len = 8;
        for (int x = x0; x < Math.min(w, x0 + len); x++) {
            set(px, w, x, y0, hull);
            if (x > x0 && x < x0 + len - 1) set(px, w, x, y0 - 1, hull);
        }
        for (int m = 0; m < 2; m++) {
            int mx = x0 + 2 + m * 3;
            for (int y = y0 - 7; y < y0 - 1; y++) set(px, w, mx, y, hull);
            for (int y = y0 - 6; y < y0 - 2; y++) for (int dx = 1; dx <= 2; dx++) set(px, w, mx + dx - (m == 0 ? 0 : 0), y, sail);
        }
        if (r.nextBoolean()) set(px, w, x0 + 2, y0 - 8, rgb(200, 40, 40));                    // a pennant
    }

    private static void set(int[] px, int w, int x, int y, int c) {
        if (x >= 0 && x < w && y >= 0 && y < px.length / w) px[y * w + x] = c;
    }

    private static void lighthouse(int[] px, int w, int h, Random r) {
        sea(px, w, h, r, new int[]{rgb(10, 14, 40), rgb(40, 50, 90)}, false, false);
        for (int i = 0; i < 18; i++) set(px, w, r.nextInt(w), r.nextInt(h / 2), rgb(240, 240, 210));
        int lx = w - 8 - r.nextInt(4), base = h * 2 / 3;
        for (int y = base; y < h; y++) for (int x = lx - 4; x <= lx + 4; x++) if (Math.abs(x - lx) <= 2 + (y - base) / 2) set(px, w, x, y, rgb(50, 46, 44));
        for (int y = base - 12; y < base; y++) for (int x = lx - 1; x <= lx + 1; x++) set(px, w, x, y, ((y / 2) & 1) == 0 ? rgb(230, 226, 214) : rgb(190, 40, 40));
        set(px, w, lx, base - 13, rgb(255, 240, 150)); set(px, w, lx - 1, base - 13, rgb(255, 230, 120)); set(px, w, lx + 1, base - 13, rgb(255, 230, 120));
        for (int i = 2; i < 12; i++) set(px, w, lx - i, base - 13 + i / 4, lerp(rgb(255, 240, 150), rgb(40, 50, 90), i / 12f));
    }

    private static void island(int[] px, int w, int h, Random r) {
        sea(px, w, h, r, sky(r, 0), false, false);
        int cx = w / 2 + r.nextInt(7) - 3, top = h * 9 / 16;
        for (int y = top; y < top + 4; y++) for (int x = cx - 8 + (y - top); x <= cx + 8 - (y - top) / 2; x++) set(px, w, x, y, rgb(230, 206, 140));
        for (int y = top - 9; y < top; y++) set(px, w, cx + (top - y) / 4, y, rgb(110, 76, 40));
        int tx = cx + 2, ty = top - 9;
        for (int i = -5; i <= 5; i++) { set(px, w, tx + i, ty + Math.abs(i) / 2, rgb(40, 130, 50)); set(px, w, tx + i, ty + 1 + Math.abs(i) / 2, rgb(30, 100, 40)); }
        set(px, w, tx, ty + 2, rgb(120, 80, 40)); set(px, w, tx + 1, ty + 2, rgb(120, 80, 40));
    }

    private static void flowers(int[] px, int w, int h, Random r) {
        int wall = rgb(140 + r.nextInt(40), 110, 80), table = rgb(100, 64, 38);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[y * w + x] = y < h * 3 / 4 ? lerp(wall, rgb(60, 40, 30), y / (float) h * 0.5f) : table;
        int cx = w / 2, jt = h * 3 / 4 - 9;
        int jug = r.nextBoolean() ? rgb(60, 90, 160) : rgb(200, 190, 170);
        for (int y = jt; y < h * 3 / 4; y++) for (int x = cx - 4; x <= cx + 4; x++) if (Math.abs(x - cx) <= 3 + ((y - jt) > 2 && (y - jt) < 7 ? 1 : 0)) set(px, w, x, y, jug);
        int[] cols = {rgb(220, 50, 50), rgb(240, 200, 40), rgb(250, 250, 240), rgb(180, 80, 200), rgb(250, 140, 40)};
        for (int i = 0; i < 7; i++) {
            int fx = cx - 7 + r.nextInt(15), fy = 3 + r.nextInt(jt - 6);
            for (int y = fy; y < jt; y++) set(px, w, cx + (fx - cx) * (jt - y) / Math.max(1, jt - fy), y, rgb(50, 120, 50));
            int c = cols[r.nextInt(cols.length)];
            set(px, w, fx, fy, c); set(px, w, fx - 1, fy, c); set(px, w, fx + 1, fy, c); set(px, w, fx, fy - 1, c); set(px, w, fx, fy + 1, rgb(240, 220, 80));
        }
    }

    /** The painting so far: the first `rows` rows painted, the rest still blank canvas. */
    public static int[] partial(int[] full, Art.Size size, int rows) {
        int[] p = Art.blank(size);
        System.arraycopy(full, 0, p, 0, Math.min(full.length, Math.max(0, rows) * size.pw));
        return p;
    }
}
