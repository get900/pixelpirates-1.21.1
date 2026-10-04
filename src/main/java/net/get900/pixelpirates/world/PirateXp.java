package net.get900.pixelpirates.world;

import net.get900.pixelpirates.entity.custom.CaptainEntity;
import net.get900.pixelpirates.entity.custom.SharkEntity;
import net.get900.pixelpirates.entity.mob.BossProgression;
import net.get900.pixelpirates.entity.mob.ModMob;
import net.get900.pixelpirates.entity.mob.MobSpec;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementFrame;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * WHERE PIRATE XP COMES FROM (2026-09-30: "tie the skill tree into bosses / items" - before this only vanilla hostiles,
 * sharks, pillager crew and ship captains paid, so the 39 roster mobs and all 10 bosses gave nothing).
 * Level 50 needs ~63,700 XP (PirateLevelingSystem.xpToNextLevel). Rough budget for a full run:
 *   bosses ~27,500 (500 + 500 per chain step, first kill; re-kills 20%) | advancements ~6,500 | hoards/chests ~5-8k |
 *   first taste of each of the 40 galley dishes 1,000 | the rest from fighting across the seas (roster mobs by phase),
 *   ships sunk/boarded and floating barrels.
 * Discovery XP (advancements, chests, dishes, barrels) gets the Navigator tree's Explorer bonus.
 */
public final class PirateXp {
    private PirateXp() {}

    /** Roster mob by the sea it lives in (index = phase). */
    private static final int[] MOB_BY_PHASE = {0, 10, 18, 28, 40, 55};
    public static final int NON_CHAIN_BOSS = 400;          // obsidian/crystal golem and other non-chain bosses
    public static final int DISH_FIRST_TASTE = 25;

    // ------------------------------------------------------------------ kills (PixelPirates AFTER_KILLED_OTHER_ENTITY)
    /** XP for the player who landed the kill. Chain bosses pay through {@link #boss} instead (every credited player). */
    public static int forKill(Entity killed) {
        if (killed instanceof ModMob m) {
            MobSpec s = m.spec();
            if (s.isBoss()) return BossProgression.indexOf(s.id) >= 0 ? 0 : NON_CHAIN_BOSS;
            if (s.isPassive()) return 0;                                   // NPCs (the Lamplighter...) pay nothing
            int p = Math.max(1, Math.min(5, s.phase));
            return s.hostile() ? MOB_BY_PHASE[p] : MOB_BY_PHASE[p] / 2;
        }
        if (killed instanceof CaptainEntity) return PirateLevelingSystem.XP_KILL_CAPTAIN;
        if (killed instanceof SharkEntity) return PirateLevelingSystem.XP_KILL_SHARK;
        if (killed instanceof net.minecraft.entity.mob.PillagerEntity) return PirateLevelingSystem.XP_KILL_CREW;
        if (killed instanceof net.get900.pixelpirates.entity.custom.PirateCrewEntity) return PirateLevelingSystem.XP_KILL_CREW;
        if (killed instanceof HostileEntity) return PirateLevelingSystem.XP_KILL_MOB;
        return 0;
    }

    // ------------------------------------------------------------------ bosses (BossProgression.onBossKilled)
    public static void boss(ServerPlayerEntity p, int index, boolean firstKill) {
        int xp = 500 + 500 * index;
        if (!firstKill) xp /= 5;
        p.sendMessage(Text.literal("+" + xp + " pirate XP").formatted(Formatting.AQUA), true);
        PirateLevelManager.awardXp(p, xp, false);
    }

    // ------------------------------------------------------------------ advancements (PlayerAdvancementXpMixin)
    public static void advancement(ServerPlayerEntity p, Advancement adv) {
        Identifier id = adv.getId();
        if (!id.getNamespace().equals("pixelpirates") || adv.getDisplay() == null || id.getPath().equals("root")) return;
        if (id.getPath().startsWith("boss_")) return;                    // the boss kill already paid
        AdvancementFrame f = adv.getDisplay().getFrame();
        int xp = f == AdvancementFrame.CHALLENGE ? 300 : f == AdvancementFrame.GOAL ? 120 : 40;
        PirateLevelManager.awardXp(p, xp, true);
    }

    // ------------------------------------------------------------------ loot chests (LootChestXpMixin)
    public static void lootChest(ServerPlayerEntity p, Identifier table) {
        String path = table.getPath();
        int xp;
        if (table.getNamespace().equals("pixelpirates")) xp = path.contains("hoard") ? 250 : path.contains("treasure") ? 80 : 40;
        else xp = path.startsWith("chests/") ? 15 : 0;
        if (xp > 0) PirateLevelManager.awardXp(p, xp, true);
    }

    public static void discovery(ServerPlayerEntity p, int xp) { PirateLevelManager.awardXp(p, xp, true); }
}
