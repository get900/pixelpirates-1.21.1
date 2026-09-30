package net.get900.pixelpirates.world;

import java.util.*;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

/**
 * Pure data / formula layer for the pirate leveling system.
 * No world state — only constants, formulas, and skill definitions.
 */
public final class PirateLevelingSystem {

    private PirateLevelingSystem() {}

    // ── Level caps ────────────────────────────────────────────────────────────

    public static final int MAX_LEVEL      = 50;
    public static final int MAX_SKILL_LVL  = 5;

    // ── XP sources ────────────────────────────────────────────────────────────

    public static final int XP_KILL_MOB     = 5;
    public static final int XP_KILL_CREW    = 12;   // pillager crew on AI ship
    public static final int XP_KILL_CAPTAIN = 60;
    public static final int XP_KILL_SHARK   = 20;
    public static final int XP_BARREL_LOOT  = 10;   // floating barrel pickup
    public static final int XP_SINK_SHIP    = 250;  // cannon-sinking an AI ship
    public static final int XP_BOARD_SHIP   = 400;  // capturing via captain kill

    // ── XP formula ────────────────────────────────────────────────────────────

    /** XP required to advance FROM `level` to `level + 1`. */
    public static int xpToNextLevel(int level) {
        if (level >= MAX_LEVEL) return Integer.MAX_VALUE;
        return 100 + (level - 1) * 50;
    }

    /** Skill points awarded when the player reaches `level`. */
    public static int skillPointsAt(int level) {
        return 1 + (level % 5 == 0 ? 1 : 0); // +2 at every 5th level, +1 otherwise
    }

    // ── Rank system ───────────────────────────────────────────────────────────

    public static String rankName(int level) {
        if (level <  5) return "Castaway";
        if (level < 10) return "Deckhand";
        if (level < 15) return "Sailor";
        if (level < 20) return "Quartermaster";
        if (level < 25) return "First Mate";
        if (level < 30) return "Captain";
        if (level < 35) return "Commodore";
        if (level < 40) return "Admiral";
        if (level < 45) return "Pirate Lord";
        return "Pirate King";
    }

    /** Chat-format color code for the rank at this level. */
    public static String rankColor(int level) {
        if (level < 10) return "§7";   // gray
        if (level < 20) return "§f";   // white
        if (level < 30) return "§e";   // yellow
        if (level < 40) return "§6";   // gold
        if (level < 45) return "§c";   // red
        return "§5";                   // purple (Pirate King)
    }

    // ── Skill trees ───────────────────────────────────────────────────────────

    public enum SkillTree {
        BRAWLER("Brawler", "Sword & Melee", 0xCC3333),
        CANNONEER("Cannoneer", "Cannon & Gunpowder", 0x333333),
        NAVIGATOR("Navigator", "Movement & Exploration", 0x3355CC),
        MERCHANT("Merchant", "Trade & Economy", 0xCCAA00),
        SURVIVOR("Survivor", "Defense & Endurance", 0x33AA33);

        public final String name;
        public final String subtitle;
        public final int    color;   // packed RGB for UI tinting

        SkillTree(String name, String subtitle, int color) {
            this.name     = name;
            this.subtitle = subtitle;
            this.color    = color;
        }
    }

    // ── Skill definitions ─────────────────────────────────────────────────────

    public record SkillDef(
            String key,
            String displayName,
            SkillTree tree,
            int maxLevel,
            IntFunction<String> descAt   // descAt.apply(0) = unlearned description
    ) {}

    public static final List<SkillDef>        ALL_SKILLS;
    public static final Map<String, SkillDef> BY_KEY;

    static {
        List<SkillDef> s = new ArrayList<>();

        // ── BRAWLER ──────────────────────────────────────────────────────────
        s.add(new SkillDef("blade_mastery", "Blade Mastery", SkillTree.BRAWLER, 5,
                l -> l == 0 ? "Increases melee attack damage."
                            : "+" + (l * 4) + "% melee attack damage."));

        s.add(new SkillDef("parry", "Parry", SkillTree.BRAWLER, 5,
                l -> l == 0 ? "Chance to block incoming melee hits."
                            : l * 8 + "% chance to fully negate a melee hit."));

        s.add(new SkillDef("swift_strikes", "Swift Strikes", SkillTree.BRAWLER, 5,
                l -> l == 0 ? "Increases attack speed."
                            : "+" + (l * 5) + "% attack speed."));

        s.add(new SkillDef("bloodlust", "Bloodlust", SkillTree.BRAWLER, 5,
                l -> l == 0 ? "Regenerate health on melee hits."
                            : "Heal " + String.format("%.1f", l * 0.5f) + " HP per melee hit."));

        s.add(new SkillDef("executioner", "Executioner", SkillTree.BRAWLER, 5,
                l -> l == 0 ? "Bonus damage on low-health targets."
                            : "+" + (l * 3) + "% damage per 10% of target's missing HP."));

        // ── CANNONEER ────────────────────────────────────────────────────────
        s.add(new SkillDef("powder_monkey", "Powder Monkey", SkillTree.CANNONEER, 5,
                l -> l == 0 ? "Reduces cannon charge time."
                            : "-" + (l * 8) + "% cannon charge & auto-fire time."));

        s.add(new SkillDef("dead_eye", "Dead Eye", SkillTree.CANNONEER, 5,
                l -> l == 0 ? "Adds a natural aim spread to cannons but allows mastery to remove it."
                            : "Adds base spread but removes " + (l * 20) + "% of it (net: -" + Math.max(0, (l-1)*20) + "% spread vs vanilla)."));

        s.add(new SkillDef("heavy_shot", "Heavy Shot", SkillTree.CANNONEER, 5,
                l -> l == 0 ? "Increases cannonball hull damage."
                            : "+" + (l * 10) + "% hull damage per cannonball."));

        s.add(new SkillDef("chain_shot", "Chain Shot", SkillTree.CANNONEER, 5,
                l -> l == 0 ? "Cannonballs have a chance to slow targets."
                            : l * 6 + "% chance to apply Slowness II (3s) on hit."));

        s.add(new SkillDef("powder_keg", "Powder Keg", SkillTree.CANNONEER, 5,
                l -> l == 0 ? "Increases cannonball explosion radius."
                            : "+" + (l * 10) + "% cannonball explosion radius."));

        // ── NAVIGATOR ────────────────────────────────────────────────────────
        s.add(new SkillDef("sea_legs", "Sea Legs", SkillTree.NAVIGATOR, 5,
                l -> l == 0 ? "Increases movement speed on land and sea."
                            : "+" + (l * 3) + "% movement speed."));

        s.add(new SkillDef("wind_reader", "Wind Reader", SkillTree.NAVIGATOR, 5,
                l -> l == 0 ? "Increases your ship's thrust."
                            : "+" + (l * 6) + "% ship thrust (applies to your owned ship)."));

        s.add(new SkillDef("wave_dancer", "Wave Dancer", SkillTree.NAVIGATOR, 5,
                l -> l == 0 ? "Reduces fall and explosion knockback damage."
                            : "-" + (l * 10) + "% fall damage."));

        s.add(new SkillDef("shark_ward", "Shark Ward", SkillTree.NAVIGATOR, 5,
                l -> l == 0 ? "Sharks are less likely to target you."
                            : l * 10 + "% reduced chance for sharks to target you in water."));

        s.add(new SkillDef("explorer", "Explorer", SkillTree.NAVIGATOR, 5,
                l -> l == 0 ? "Earn bonus XP from pickups and discoveries."
                            : "+" + (l * 5) + "% XP from barrel loots and zone unlocks."));

        // ── MERCHANT ─────────────────────────────────────────────────────────
        s.add(new SkillDef("haggler", "Haggler", SkillTree.MERCHANT, 5,
                l -> l == 0 ? "Reduces the pirate coin cost of shop purchases."
                            : "-" + (l * 3) + "% purchase cost (rounds down)."));

        s.add(new SkillDef("silver_tongue", "Silver Tongue", SkillTree.MERCHANT, 5,
                l -> l == 0 ? "Extra coins drop from defeated enemies."
                            : l * 5 + "% chance for a bonus Pirate Coin on enemy kill."));

        s.add(new SkillDef("fence", "Fence", SkillTree.MERCHANT, 5,
                l -> l == 0 ? "Reduces coins lost on death."
                            : "-" + (l * 15) + "% doubloon penalty on death."));

        s.add(new SkillDef("appraiser", "Appraiser", SkillTree.MERCHANT, 5,
                l -> l == 0 ? "Reveals loot value information."
                            : "Reveals value of " + l + " extra loot type(s) on inspect."));

        s.add(new SkillDef("tycoon", "Tycoon", SkillTree.MERCHANT, 5,
                l -> l == 0 ? "Chance for double loot from ships and barrels."
                            : l * 4 + "% chance for double loot from ships and barrels."));

        // ── SURVIVOR ─────────────────────────────────────────────────────────
        s.add(new SkillDef("iron_skin", "Iron Skin", SkillTree.SURVIVOR, 5,
                l -> l == 0 ? "Permanently increases your maximum health."
                            : "+" + (l * 2) + " max HP (+" + l + " hearts)."));

        s.add(new SkillDef("sea_toughness", "Sea Toughness", SkillTree.SURVIVOR, 5,
                l -> l == 0 ? "Increases armor toughness."
                            : "+" + String.format("%.1f", l * 0.5) + " armor toughness."));

        s.add(new SkillDef("second_wind", "Second Wind", SkillTree.SURVIVOR, 5,
                l -> l == 0 ? "Automatically heal when critically low on health."
                            : "Heal 4 HP when below 25% health. Cooldown: " + (120 - l * 20) + "s."));

        s.add(new SkillDef("davys_luck", "Davy's Luck", SkillTree.SURVIVOR, 5,
                l -> l == 0 ? "Chance to survive a killing blow."
                            : l * 2 + "% chance to survive a fatal hit with 1 HP. (60s CD)"));

        s.add(new SkillDef("salt_skin", "Salt Skin", SkillTree.SURVIVOR, 5,
                l -> l == 0 ? "Reduces damage from poison and fire."
                            : "-" + (l * 10) + "% damage from poison and burning."));

        ALL_SKILLS = Collections.unmodifiableList(s);

        Map<String, SkillDef> byKey = new LinkedHashMap<>();
        for (SkillDef def : s) byKey.put(def.key(), def);
        BY_KEY = Collections.unmodifiableMap(byKey);
    }

    /** Returns all skills belonging to the given tree, in definition order. */
    public static List<SkillDef> skillsForTree(SkillTree tree) {
        return ALL_SKILLS.stream().filter(d -> d.tree() == tree).collect(Collectors.toList());
    }
}
