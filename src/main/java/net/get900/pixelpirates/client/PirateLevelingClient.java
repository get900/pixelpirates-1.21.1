package net.get900.pixelpirates.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.get900.pixelpirates.world.PirateLevelingSystem;

/**
 * Client-side mirror of the player's pirate level data.
 * Populated by the S2C_LEVEL_SYNC packet. Read-only on the client.
 */
@Environment(EnvType.CLIENT)
public final class PirateLevelingClient {

    private PirateLevelingClient() {}

    public static int   level       = 1;
    public static int   xp          = 0;
    public static int   skillPoints = 0;
    public static int[] skillLevels = new int[PirateLevelingSystem.ALL_SKILLS.size()];

    public static void update(int newLevel, int newXp, int newPoints, int[] newSkills) {
        level       = newLevel;
        xp          = newXp;
        skillPoints = newPoints;
        skillLevels = newSkills;
    }

    public static int xpToNextLevel() {
        return PirateLevelingSystem.xpToNextLevel(level);
    }

    public static float xpFraction() {
        int needed = xpToNextLevel();
        if (needed <= 0 || level >= PirateLevelingSystem.MAX_LEVEL) return 1f;
        return Math.min(1f, (float) xp / needed);
    }
}
