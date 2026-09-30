package net.get900.pixelpirates.world.faction;

public enum Faction {
    PIRATES  ("pirates",   "§cCrimson Corsairs",    true,  false),
    MERCHANTS("merchants", "§2Emerald Trading Co.", false, true),
    NAVY     ("navy",      "§7Iron Armada",          false, true),
    UNDEAD   ("undead",    "§5Drowned Fleet",        false, false);

    public final String id;
    public final String displayName;
    /** Always attacks the player regardless of reputation. */
    public final boolean alwaysHostile;
    /** High-rep players can request convoy escort (future). */
    public final boolean offersConvoy;

    public static final int REP_MIN      = -1000;
    public static final int REP_MAX      =  1000;
    /** Below this threshold the faction's ships attack the player on sight. */
    public static final int REP_HOSTILE  =  -300;
    public static final int REP_NEUTRAL  =     0;
    public static final int REP_FRIENDLY =   300;
    public static final int REP_HONORED  =   600;

    Faction(String id, String displayName, boolean alwaysHostile, boolean offersConvoy) {
        this.id           = id;
        this.displayName  = displayName;
        this.alwaysHostile = alwaysHostile;
        this.offersConvoy  = offersConvoy;
    }

    public static Faction fromId(String id) {
        if (id == null) return PIRATES;
        for (Faction f : values()) if (f.id.equalsIgnoreCase(id)) return f;
        return PIRATES;
    }

    /** Returns true if this faction considers the other faction an enemy. */
    public boolean isEnemyFaction(Faction other) {
        if (other == this) return false;
        return switch (this) {
            case PIRATES   -> other == MERCHANTS || other == NAVY;
            case NAVY      -> other == PIRATES   || other == UNDEAD;
            case MERCHANTS -> other == PIRATES;
            case UNDEAD    -> other != UNDEAD;
        };
    }
}
