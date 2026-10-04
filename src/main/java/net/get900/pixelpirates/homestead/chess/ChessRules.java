package net.get900.pixelpirates.homestead.chess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The rules of chess (2026-10-04): a position (64 squares, a1 = 0 .. h8 = 63; pieces +1..+6 white P N B R Q K,
 * negative = black), legal move generation incl. castling, en passant and promotion, check / checkmate / stalemate,
 * the fifty-move rule and bare kings. Moves are ints: from | to << 6 | promotion piece << 12.
 */
public final class ChessRules {
    public static final int P = 1, N = 2, B = 3, R = 4, Q = 5, K = 6;

    public final int[] sq = new int[64];
    public boolean whiteToMove = true;
    /** Castling rights: 1 white king side, 2 white queen side, 4 black king side, 8 black queen side. */
    public int castling = 15;
    /** The en passant target square (-1 = none). */
    public int ep = -1;
    public int halfmoves;

    public static ChessRules start() {
        ChessRules c = new ChessRules();
        int[] back = {R, N, B, Q, K, B, N, R};
        for (int f = 0; f < 8; f++) {
            c.sq[f] = back[f]; c.sq[8 + f] = P;
            c.sq[48 + f] = -P; c.sq[56 + f] = -back[f];
        }
        return c;
    }

    public ChessRules copy() {
        ChessRules c = new ChessRules();
        System.arraycopy(sq, 0, c.sq, 0, 64);
        c.whiteToMove = whiteToMove; c.castling = castling; c.ep = ep; c.halfmoves = halfmoves;
        return c;
    }

    public static int from(int m) { return m & 63; }
    public static int to(int m) { return (m >> 6) & 63; }
    public static int promo(int m) { return (m >> 12) & 7; }
    public static int move(int from, int to, int promo) { return from | to << 6 | promo << 12; }
    static int file(int s) { return s & 7; }
    static int rank(int s) { return s >> 3; }

    private static final int[][] KNIGHT = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
    private static final int[][] KING = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
    private static final int[][] ROOK = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] BISHOP = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    /** Is square s attacked by the side {@code byWhite}? */
    public boolean attacked(int s, boolean byWhite) {
        int f = file(s), r = rank(s), sign = byWhite ? 1 : -1;
        int pr = r - sign;                                                          // pawns attack from one rank behind
        for (int df : new int[]{-1, 1}) {
            int pf = f + df;
            if (pf >= 0 && pf < 8 && pr >= 0 && pr < 8 && sq[pr * 8 + pf] == sign * P) return true;
        }
        for (int[] d : KNIGHT) { int x = f + d[0], y = r + d[1]; if (x >= 0 && x < 8 && y >= 0 && y < 8 && sq[y * 8 + x] == sign * N) return true; }
        for (int[] d : KING) { int x = f + d[0], y = r + d[1]; if (x >= 0 && x < 8 && y >= 0 && y < 8 && sq[y * 8 + x] == sign * K) return true; }
        for (int[] d : ROOK) if (slideHits(f, r, d, sign * R, sign * Q)) return true;
        for (int[] d : BISHOP) if (slideHits(f, r, d, sign * B, sign * Q)) return true;
        return false;
    }

    private boolean slideHits(int f, int r, int[] d, int a, int b) {
        int x = f + d[0], y = r + d[1];
        while (x >= 0 && x < 8 && y >= 0 && y < 8) {
            int p = sq[y * 8 + x];
            if (p != 0) return p == a || p == b;
            x += d[0]; y += d[1];
        }
        return false;
    }

    public int king(boolean white) {
        for (int i = 0; i < 64; i++) if (sq[i] == (white ? K : -K)) return i;
        return -1;
    }

    public boolean inCheck() { int k = king(whiteToMove); return k >= 0 && attacked(k, !whiteToMove); }

    /** Every legal move for the side to move. */
    public List<Integer> legal() {
        List<Integer> out = new ArrayList<>();
        for (int m : pseudo()) {
            ChessRules c = copy();
            c.play(m);
            int k = c.king(whiteToMove);
            if (k >= 0 && !c.attacked(k, !whiteToMove)) out.add(m);
        }
        return out;
    }

    private List<Integer> pseudo() {
        List<Integer> out = new ArrayList<>();
        int sign = whiteToMove ? 1 : -1;
        for (int s = 0; s < 64; s++) {
            int p = sq[s] * sign;
            if (p <= 0) continue;
            int f = file(s), r = rank(s);
            switch (p) {
                case P -> {
                    int dir = sign, start = whiteToMove ? 1 : 6, last = whiteToMove ? 7 : 0;
                    int one = s + 8 * dir;
                    if (one >= 0 && one < 64 && sq[one] == 0) {
                        addPawn(out, s, one, last);
                        int two = s + 16 * dir;
                        if (r == start && sq[two] == 0) out.add(move(s, two, 0));
                    }
                    for (int df : new int[]{-1, 1}) {
                        int x = f + df, y = r + dir;
                        if (x < 0 || x > 7 || y < 0 || y > 7) continue;
                        int t = y * 8 + x;
                        if (sq[t] * sign < 0 || t == ep) addPawn(out, s, t, last);
                    }
                }
                case N -> jumps(out, s, KNIGHT, sign);
                case K -> {
                    jumps(out, s, KING, sign);
                    castles(out, s);
                }
                case B -> slides(out, s, BISHOP, sign);
                case R -> slides(out, s, ROOK, sign);
                case Q -> { slides(out, s, BISHOP, sign); slides(out, s, ROOK, sign); }
            }
        }
        return out;
    }

    private void addPawn(List<Integer> out, int s, int t, int last) {
        if (rank(t) == last) for (int pr : new int[]{Q, R, B, N}) out.add(move(s, t, pr));
        else out.add(move(s, t, 0));
    }

    private void jumps(List<Integer> out, int s, int[][] ds, int sign) {
        int f = file(s), r = rank(s);
        for (int[] d : ds) {
            int x = f + d[0], y = r + d[1];
            if (x < 0 || x > 7 || y < 0 || y > 7) continue;
            int t = y * 8 + x;
            if (sq[t] * sign <= 0) out.add(move(s, t, 0));
        }
    }

    private void slides(List<Integer> out, int s, int[][] ds, int sign) {
        int f = file(s), r = rank(s);
        for (int[] d : ds) {
            int x = f + d[0], y = r + d[1];
            while (x >= 0 && x < 8 && y >= 0 && y < 8) {
                int t = y * 8 + x;
                if (sq[t] * sign > 0) break;
                out.add(move(s, t, 0));
                if (sq[t] != 0) break;
                x += d[0]; y += d[1];
            }
        }
    }

    private void castles(List<Integer> out, int s) {
        boolean w = whiteToMove;
        int home = w ? 4 : 60;
        if (s != home || attacked(home, !w)) return;
        if ((castling & (w ? 1 : 4)) != 0 && sq[home + 1] == 0 && sq[home + 2] == 0 && sq[home + 3] == (w ? R : -R)
                && !attacked(home + 1, !w) && !attacked(home + 2, !w)) out.add(move(home, home + 2, 0));
        if ((castling & (w ? 2 : 8)) != 0 && sq[home - 1] == 0 && sq[home - 2] == 0 && sq[home - 3] == 0 && sq[home - 4] == (w ? R : -R)
                && !attacked(home - 1, !w) && !attacked(home - 2, !w)) out.add(move(home, home - 2, 0));
    }

    /** Play a move (assumed legal). Returns the captured piece (0 = none). */
    public int play(int m) {
        int f = from(m), t = to(m), pr = promo(m), p = sq[f], cap = sq[t];
        int sign = p > 0 ? 1 : -1;
        if (Math.abs(p) == P && t == ep && cap == 0) { cap = sq[t - 8 * sign]; sq[t - 8 * sign] = 0; }    // en passant
        if (Math.abs(p) == K && Math.abs(t - f) == 2) {                                                  // castling: the rook too
            int rf = t > f ? f + 3 : f - 4, rt = t > f ? f + 1 : f - 1;
            sq[rt] = sq[rf]; sq[rf] = 0;
        }
        sq[t] = pr != 0 ? pr * sign : p;
        sq[f] = 0;
        ep = Math.abs(p) == P && Math.abs(t - f) == 16 ? (f + t) / 2 : -1;
        if (Math.abs(p) == K) castling &= p > 0 ? ~3 : ~12;
        for (int[] c : new int[][]{{0, 2}, {7, 1}, {56, 8}, {63, 4}}) if (f == c[0] || t == c[0]) castling &= ~c[1];
        halfmoves = Math.abs(p) == P || cap != 0 ? 0 : halfmoves + 1;
        whiteToMove = !whiteToMove;
        return cap;
    }

    /** "" = play on, else how it ended: "checkmate", "stalemate", "fifty moves", "bare kings". */
    public String result() {
        if (legal().isEmpty()) return inCheck() ? "checkmate" : "stalemate";
        if (halfmoves >= 100) return "fifty moves";
        int minor = 0;
        for (int p : sq) {
            int a = Math.abs(p);
            if (a == P || a == R || a == Q) return "";
            if (a == N || a == B) minor++;
        }
        return minor <= 1 ? "bare kings" : "";
    }

    public static String squareName(int s) { return "" + (char) ('a' + file(s)) + (char) ('1' + rank(s)); }

    public static String moveName(int m) { return squareName(from(m)) + "-" + squareName(to(m)) + (promo(m) != 0 ? "=" + "  NBRQ".charAt(promo(m)) : ""); }

    public int[] toArray() {
        int[] a = Arrays.copyOf(sq, 68);
        a[64] = whiteToMove ? 1 : 0; a[65] = castling; a[66] = ep; a[67] = halfmoves;
        return a;
    }

    public static ChessRules fromArray(int[] a) {
        if (a == null || a.length < 68) return start();
        ChessRules c = new ChessRules();
        System.arraycopy(a, 0, c.sq, 0, 64);
        c.whiteToMove = a[64] == 1; c.castling = a[65]; c.ep = a[66]; c.halfmoves = a[67];
        return c;
    }
}
