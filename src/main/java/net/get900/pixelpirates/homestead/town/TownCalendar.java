package net.get900.pixelpirates.homestead.town;

import net.minecraft.server.world.ServerWorld;

/**
 * The BIG EVENTS calendar (2026-10-05, the user: "spread out - each every 10-20 days, never two on the same day").
 * The day's existing fixtures stay where they are (TownEvents: market d%7==3, festival d%8==5, weddings d%12==6).
 * Every even day that has none of those is a big-event day, and the big events take those days in turn - so each comes
 * round every 12-16 days (avg 14) and two never fall together. /pptown event <name> still forces one for testing.
 */
public final class TownCalendar {
    private TownCalendar() {}

    public enum Big { CHESS_TOURNAMENT, FISHING_CONTEST, HARVEST_FESTIVAL, GOVERNORS_BALL, REGATTA }

    private static long cachedDay = Long.MIN_VALUE;
    private static Big cached;

    /** A day with no market, festival or wedding. */
    static boolean free(long d) { return d % 7 != 3 && d % 8 != 5 && d % 12 != 6; }

    /** Today's big event, or null. */
    public static Big today(ServerWorld w) { return on(w.getTimeOfDay() / 24000L); }

    public static Big on(long d) {
        if (d == cachedDay) return cached;
        Big b = null;
        if (d > 0 && free(d) && d % 2 == 0) {
            long slot = 0;                                                      // big-event days before this one
            for (long i = 1; i < d; i++) if (free(i) && i % 2 == 0) slot++;
            b = Big.values()[(int) (slot % Big.values().length)];
        }
        cachedDay = d;
        cached = b;
        return b;
    }

    /** Days until the next {@code b} (0 = today), for the crier and the notice boards. */
    public static long daysUntil(ServerWorld w, Big b) {
        long d = w.getTimeOfDay() / 24000L;
        for (long i = 0; i < 200; i++) if (on(d + i) == b) { on(d); return i; }
        on(d);
        return -1;
    }
}
