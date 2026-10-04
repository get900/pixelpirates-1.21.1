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
    /** Chain bosses beaten (rank IV/V and capstone gates in the skill screen). */
    public static int   bossesBeaten = 0;

    public static int level(String key) {
        int i = net.get900.pixelpirates.world.PirateLevelManager.getSkillIndex(key);
        return i >= 0 && i < skillLevels.length ? skillLevels[i] : 0;
    }

    public static int pointsInTree(PirateLevelingSystem.SkillTree tree) {
        int n = 0;
        for (int i = 0; i < PirateLevelingSystem.ALL_SKILLS.size() && i < skillLevels.length; i++)
            if (PirateLevelingSystem.ALL_SKILLS.get(i).tree() == tree) n += skillLevels[i];
        return n;
    }

    /**
     * DEEP DIVER: swimming is simulated on the client, so the boost lives here. Water drags 0.8 of the speed away each
     * tick; scaling velocity by k gives a terminal speed 0.2k / (1 - 0.8k) times normal - k is solved so that equals
     * 1 + 10% per rank.
     */
    public static void tickSwim(net.minecraft.client.MinecraftClient mc) {
        var p = mc.player;
        int r = level("deep_diver");
        if (p == null || r <= 0 || !p.isSwimming() && !(p.isSubmergedInWater() && p.input != null && p.input.hasForwardMovement())) return;
        double m = 1 + 0.10 * r, k = m / (0.2 + 0.8 * m);        // terminal 0.2k/(1-0.8k) = m
        var v = p.getVelocity();
        p.setVelocity(v.x * k, v.y * (p.isSwimming() ? k : 1), v.z * k);
    }
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
