package net.get900.pixelpirates.homestead.chess;

import java.util.List;
import java.util.Random;

/**
 * The computer opponent: EASY looks one move ahead with a lot of noise (it blunders), NORMAL searches three plies
 * (alpha-beta, captures first) on material + a little piece placement. Runs on the server thread - a 3-ply search is
 * a few thousand positions.
 */
public final class ChessAI {
    private ChessAI() {}

    private static final int[] VALUE = {0, 100, 320, 330, 500, 900, 0};
    private static final int MATE = 100000;

    public static int choose(ChessRules pos, int level, Random r) {
        List<Integer> moves = pos.legal();
        if (moves.isEmpty()) return -1;
        int depth = level <= 1 ? 1 : 3, noise = level <= 1 ? 160 : 12;
        order(pos, moves);
        int best = moves.get(0), bestScore = Integer.MIN_VALUE;
        for (int m : moves) {
            ChessRules c = pos.copy();
            c.play(m);
            int s = -search(c, depth - 1, -MATE * 2, MATE * 2) + r.nextInt(noise + 1);
            if (s > bestScore) { bestScore = s; best = m; }
        }
        return best;
    }

    private static int search(ChessRules pos, int depth, int alpha, int beta) {
        if (depth <= 0) return eval(pos);
        List<Integer> moves = pos.legal();
        if (moves.isEmpty()) return pos.inCheck() ? -MATE + (10 - depth) : 0;
        order(pos, moves);
        for (int m : moves) {
            ChessRules c = pos.copy();
            c.play(m);
            int s = -search(c, depth - 1, -beta, -alpha);
            if (s >= beta) return beta;
            if (s > alpha) alpha = s;
        }
        return alpha;
    }

    /** Captures (biggest victim first) before quiet moves - makes the pruning bite. */
    private static void order(ChessRules pos, List<Integer> moves) {
        moves.sort((a, b) -> Integer.compare(VALUE[Math.abs(pos.sq[ChessRules.to(b)])], VALUE[Math.abs(pos.sq[ChessRules.to(a)])]));
    }

    /** From the side to move's point of view. */
    static int eval(ChessRules pos) {
        int s = 0;
        for (int i = 0; i < 64; i++) {
            int p = pos.sq[i];
            if (p == 0) continue;
            int a = Math.abs(p), f = i & 7, r = i >> 3;
            int v = VALUE[a];
            int centre = 6 - (Math.abs(2 * f - 7) + Math.abs(2 * r - 7)) / 2;          // 0 at the corners .. 6 in the middle
            int home = p > 0 ? 0 : 7, adv = p > 0 ? r : 7 - r;
            if (a == ChessRules.N || a == ChessRules.B) v += centre * 5 + (r != home ? 12 : 0);             // developed, central
            if (a == ChessRules.P) v += adv * 3 + ((f == 3 || f == 4) && adv >= 2 ? 14 : 0) - ((f == 0 || f == 7) && adv >= 2 ? 6 : 0);
            if (a == ChessRules.K) v += (p > 0 ? (r == 0 ? 10 : -10) : (r == 7 ? 10 : -10));
            s += p > 0 ? v : -v;
        }
        return pos.whiteToMove ? s : -s;
    }
}
