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

    // ── Skill trees (redesigned 2026-10-01 - every skill does something, ranks IV-V follow the boss chain) ────────

    public enum SkillTree {
        BRAWLER("Brawler", "Blades & boarding", 0xC0392B, "pixelpirates:boarding_sabre"),
        CANNONEER("Cannoneer", "Cannons, guns & powder", 0xD35400, "pixelpirates:cannon_ball"),
        NAVIGATOR("Navigator", "Sailing, swimming & exploring", 0x2E86C1, "minecraft:compass"),
        MERCHANT("Merchant", "Coin, trade & treasure", 0xD4AC0D, "pixelpirates:pirate_coin"),
        SURVIVOR("Survivor", "Toughness & staying alive", 0x229954, "pixelpirates:sea_bandage"),
        MONSTER_HUNTER("Monster Hunter", "Beasts of the five seas", 0x7D3C98, "pixelpirates:harpoon");

        public final String name;
        public final String subtitle;
        public final int    color;   // packed RGB for UI tinting
        public final String icon;    // item id drawn on the tab

        SkillTree(String name, String subtitle, int color, String icon) {
            this.name     = name;
            this.subtitle = subtitle;
            this.color    = color;
            this.icon     = icon;
        }
    }

    // ── Tiers, rank gates ─────────────────────────────────────────────────────

    /** Points that must already be spent in the SAME tree to open a tier (index = tier 1..4; 4 = capstone). */
    public static final int[] TIER_POINTS = {0, 0, 5, 10, 15};
    /** Bosses (chain steps) a player must have beaten to buy rank 4 / rank 5 of any skill. */
    public static final int RANK4_BOSSES = 2;   // Captain Rackham + the Sea Serpent
    public static final int RANK5_BOSSES = 5;   // ... up to the Abyssal King

    // ── Skill definitions ─────────────────────────────────────────────────────

    /**
     * tier 1..3 = the tree's rows, 4 = capstone (one rank). bossGate = chain bosses needed to learn it at all.
     * descAt(r) = what rank r does (r = 0: what the skill is about).
     */
    public record SkillDef(
            String key,
            String displayName,
            SkillTree tree,
            int maxLevel,
            int tier,
            int bossGate,
            String icon,
            IntFunction<String> descAt
    ) {}

    public static final List<SkillDef>        ALL_SKILLS;
    public static final Map<String, SkillDef> BY_KEY;

    private static void d(List<SkillDef> s, String key, String name, SkillTree tree, int tier, String icon, IntFunction<String> desc) {
        s.add(new SkillDef(key, name, tree, MAX_SKILL_LVL, tier, 0, icon, desc));
    }

    private static void cap(List<SkillDef> s, String key, String name, SkillTree tree, int bossGate, String icon, IntFunction<String> desc) {
        s.add(new SkillDef(key, name, tree, 1, 4, bossGate, icon, desc));
    }

    static {
        List<SkillDef> s = new ArrayList<>();

        // ── BRAWLER: melee, boarding, boss brawls ────────────────────────────
        d(s, "blade_mastery", "Blade Mastery", SkillTree.BRAWLER, 1, "pixelpirates:boarding_sabre",
                l -> l == 0 ? "Your blows land harder." : "+" + (l * 4) + "% melee damage.");
        d(s, "swift_strikes", "Swift Strikes", SkillTree.BRAWLER, 1, "pixelpirates:dagger",
                l -> l == 0 ? "You recover faster between swings." : "+" + (l * 5) + "% attack speed.");
        d(s, "parry", "Parry", SkillTree.BRAWLER, 2, "minecraft:shield",
                l -> l == 0 ? "Turn aside melee blows." : (l * 6) + "% chance to parry a melee hit (a boss's parried hit still deals half).");
        d(s, "bloodlust", "Bloodlust", SkillTree.BRAWLER, 2, "pixelpirates:bloodfin_flesh",
                l -> l == 0 ? "Kills restore you." : "Heal " + l + " HP on every melee kill.");
        d(s, "executioner", "Executioner", SkillTree.BRAWLER, 3, "pixelpirates:boarding_axe",
                l -> l == 0 ? "Finish off the wounded." : "+" + (l * 6) + "% damage to anything below half health.");
        cap(s, "berserker", "Berserker", SkillTree.BRAWLER, 3, "pixelpirates:emberbrand",
                l -> "Below 40% health: +25% melee damage and +15% speed.");

        // ── CANNONEER: ship cannons, flintlocks, relic guns, dynamite ────────
        d(s, "quick_hands", "Quick Hands", SkillTree.CANNONEER, 1, "minecraft:gunpowder",
                l -> l == 0 ? "Load and charge faster." : "-" + (l * 8) + "% cannon charge time and gun reload time.");
        d(s, "heavy_shot", "Heavy Shot", SkillTree.CANNONEER, 1, "pixelpirates:cannon_ball",
                l -> l == 0 ? "Heavier shot, deeper holes." : "+" + (l * 10) + "% hull damage, +" + (l * 6) + "% damage from cannonballs and gunshots.");
        d(s, "dead_eye", "Dead Eye", SkillTree.CANNONEER, 2, "pixelpirates:flintlock_pistol",
                l -> l == 0 ? "Tighter groups." : "-" + (l * 15) + "% spread on guns and scattershot.");
        d(s, "demolitions", "Demolitions", SkillTree.CANNONEER, 2, "pixelpirates:dynamite",
                l -> l == 0 ? "Bigger bangs." : "+" + (l * 10) + "% damage from your dynamite, depth charges, kegs and cannonballs.");
        d(s, "chain_shot", "Crippling Shot", SkillTree.CANNONEER, 3, "pixelpirates:chain_shot",
                l -> l == 0 ? "Shots that hobble." : (l * 6) + "% chance your cannonballs and gunshots slow the target (Slowness II, 3 s).");
        cap(s, "powder_soul", "Powder Soul", SkillTree.CANNONEER, 1, "minecraft:tnt",
                l -> "Your own explosions never hurt you.");

        // ── NAVIGATOR: sailing, the underwater bosses, exploration ───────────
        d(s, "sea_legs", "Sea Legs", SkillTree.NAVIGATOR, 1, "minecraft:leather_boots",
                l -> l == 0 ? "Quick on your feet." : "+" + (l * 3) + "% movement speed.");
        d(s, "wind_reader", "Wind Reader", SkillTree.NAVIGATOR, 1, "pixelpirates:sail",
                l -> l == 0 ? "Get more out of the wind." : "+" + (l * 6) + "% thrust for any ship you steer.");
        d(s, "deep_diver", "Deep Diver", SkillTree.NAVIGATOR, 2, "minecraft:nautilus_shell",
                l -> l == 0 ? "At home under the waves." : "+" + (l * 10) + "% swim speed; sharks are " + (l * 10) + "% less likely to hunt you.");
        d(s, "wave_dancer", "Wave Dancer", SkillTree.NAVIGATOR, 2, "minecraft:feather",
                l -> l == 0 ? "Land like a cat." : "-" + (l * 12) + "% fall damage.");
        d(s, "explorer", "Explorer", SkillTree.NAVIGATOR, 3, "minecraft:compass",
                l -> l == 0 ? "Learn from every discovery." : "+" + (l * 6) + "% XP from advancements, chests, new dishes and barrels.");
        cap(s, "sea_wolf", "Sea Wolf", SkillTree.NAVIGATOR, 2, "pixelpirates:ship_helm",
                l -> "Aboard any ship: Regeneration I and no hunger drain.");

        // ── MERCHANT: coin, trade, bounties, hoards ──────────────────────────
        d(s, "silver_tongue", "Silver Tongue", SkillTree.MERCHANT, 1, "pixelpirates:pirate_coin",
                l -> l == 0 ? "Pockets always jingle." : (l * 5) + "% chance for a bonus doubloon on every kill.");
        d(s, "haggler", "Haggler", SkillTree.MERCHANT, 1, "minecraft:emerald",
                l -> l == 0 ? "Drive a hard bargain." : "-" + (l * 4) + "% prices at port traders, the Map Merchant and the Shipwright.");
        d(s, "bounty_hunter", "Bounty Hunter", SkillTree.MERCHANT, 2, "pixelpirates:bounty_map",
                l -> l == 0 ? "The board pays you better." : "+" + (l * 10) + "% bounty rewards.");
        d(s, "fence", "Fence", SkillTree.MERCHANT, 2, "minecraft:gold_ingot",
                l -> l == 0 ? "Know who buys." : "+" + (l * 5) + "% coins when selling to port traders.");
        d(s, "treasure_hunter", "Treasure Hunter", SkillTree.MERCHANT, 3, "pixelpirates:treasure_map_legendary",
                l -> l == 0 ? "A nose for loot." : (l * 5) + "% chance a chest or barrel you open holds double loot.");
        cap(s, "plunderer", "Plunderer", SkillTree.MERCHANT, 5, "minecraft:chest",
                l -> "Every boss hoard you open holds double loot.");

        // ── SURVIVOR: the boss grind ──────────────────────────────────────────
        d(s, "iron_skin", "Iron Skin", SkillTree.SURVIVOR, 1, "pixelpirates:pirate_chestplate",
                l -> l == 0 ? "Harder to kill." : "+" + (l * 2) + " max health (+" + l + " hearts).");
        d(s, "sea_toughness", "Sea Toughness", SkillTree.SURVIVOR, 1, "minecraft:turtle_helmet",
                l -> l == 0 ? "Shrug off heavy blows." : "+" + String.format("%.1f", l * 0.5) + " armor toughness.");
        d(s, "salt_skin", "Salt Skin", SkillTree.SURVIVOR, 2, "minecraft:sea_pickle",
                l -> l == 0 ? "Cured against the elements." : "-" + (l * 6) + "% damage from fire, poison and wither.");
        d(s, "second_wind", "Second Wind", SkillTree.SURVIVOR, 2, "pixelpirates:coconut_water",
                l -> l == 0 ? "One more breath." : "Below 25% health: heal 4 HP. Cooldown " + (120 - l * 20) + " s.");
        d(s, "davys_luck", "Davy's Luck", SkillTree.SURVIVOR, 3, "minecraft:skeleton_skull",
                l -> l == 0 ? "Davy Jones isn't ready for you." : (l * 4) + "% chance to survive a killing blow on 1 HP (60 s cooldown).");
        cap(s, "unsinkable", "Unsinkable", SkillTree.SURVIVOR, 4, "minecraft:totem_of_undying",
                l -> "Once every 5 minutes a killing blow leaves you on 4 hearts with Resistance II for 4 s.");

        // ── MONSTER HUNTER: the creature roster and the boss chain ────────────
        d(s, "beast_slayer", "Beast Slayer", SkillTree.MONSTER_HUNTER, 1, "pixelpirates:harpoon",
                l -> l == 0 ? "Know where to strike a beast." : "+" + (l * 5) + "% damage to monsters (not bosses).");
        d(s, "trophy_hunter", "Trophy Hunter", SkillTree.MONSTER_HUNTER, 1, "minecraft:bone",
                l -> l == 0 ? "Take a trophy from every kill." : (l * 6) + "% chance a monster you kill drops its loot twice.");
        d(s, "tracker", "Tracker", SkillTree.MONSTER_HUNTER, 2, "minecraft:spyglass",
                l -> l == 0 ? "Nothing escapes you." : "Monsters you hit glow for " + (l * 2) + " s.");
        d(s, "monster_lore", "Monster Lore", SkillTree.MONSTER_HUNTER, 2, "minecraft:writable_book",
                l -> l == 0 ? "You know their tricks." : "-" + (l * 5) + "% damage taken from monsters (not bosses).");
        d(s, "boss_hunter", "Boss Hunter", SkillTree.MONSTER_HUNTER, 3, "pixelpirates:rackhams_diving_charm",
                l -> l == 0 ? "Every legend has a weak spot." : "+" + (l * 4) + "% damage to bosses.");
        cap(s, "apex_predator", "Apex Predator", SkillTree.MONSTER_HUNTER, 6, "pixelpirates:bloodfin_razor_tooth",
                l -> "Every monster you kill grants Strength I and Speed I for 8 s.");

        ALL_SKILLS = Collections.unmodifiableList(s);

        Map<String, SkillDef> byKey = new LinkedHashMap<>();
        for (SkillDef def : s) byKey.put(def.key(), def);
        BY_KEY = Collections.unmodifiableMap(byKey);
    }

    /** Returns all skills belonging to the given tree, in definition order. */
    public static List<SkillDef> skillsForTree(SkillTree tree) {
        return ALL_SKILLS.stream().filter(d -> d.tree() == tree).collect(Collectors.toList());
    }

    /** Why this skill can't take its next rank right now (null = it can). Shared by the server check and the screen. */
    public static String blockedReason(SkillDef def, int curRank, int pointsInTree, int bossesBeaten, int freePoints) {
        if (curRank >= def.maxLevel()) return "Mastered";
        if (pointsInTree < TIER_POINTS[def.tier()])
            return "Needs " + TIER_POINTS[def.tier()] + " points in " + def.tree().name;
        if (def.bossGate() > bossesBeaten) return "Defeat " + bossName(def.bossGate()) + " first";
        int next = curRank + 1;
        if (next == 4 && bossesBeaten < RANK4_BOSSES) return "Rank IV: defeat " + bossName(RANK4_BOSSES) + " first";
        if (next == 5 && bossesBeaten < RANK5_BOSSES) return "Rank V: defeat " + bossName(RANK5_BOSSES) + " first";
        if (freePoints <= 0) return "No skill points";
        return null;
    }

    /** The chain boss you must beat to have beaten `count` bosses. */
    public static String bossName(int count) {
        String[] names = {"", "Captain Rackham", "the Sea Serpent", "the Molten Warlord", "the Ghost Captain", "the Abyssal King",
                "the Bloodfin", "the Kraken", "the Chained Revenant", "the Abyssal Heart", "the Leviathan"};
        return names[Math.max(1, Math.min(10, count))];
    }

    public static String roman(int n) {
        return switch (n) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; default -> "" + n; };
    }
}
