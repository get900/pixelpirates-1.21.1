package net.get900.pixelpirates.homestead.town;

import net.get900.pixelpirates.homestead.HomesteadBlocks;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * THE FISHING CONTEST (2026-10-05, TownCalendar.FISHING_CONTEST): "Finn challenges the town - biggest fish by sundown".
 * From 1000 to 12000 on the day, every fish a player reels in within {@link #WATERS} blocks of the island is weighed
 * (by species; written on the fish) and the heaviest per angler goes on the board; Finn and his friends fish off the
 * pier all day and land a fish now and then (heavier for the better anglers). A new leader is called out; at sundown
 * the winner is named in the news - a player winner takes the GOLDEN MARLIN TROPHY and 20 coins.
 * The weighing hook: mixin/FishingContestMixin (FishingBobberEntity.use, the loot it hands the angler).
 */
public final class FishingContest {
    private FishingContest() {}

    static final int FROM = 1000, TO = 12000, WATERS = 320;

    /** Finn and the friends he brings: where on the quay each fishes (the water is to the south), and how good. */
    private static final Map<String, int[]> SPOTS = Map.of("finn", new int[]{12, 66, 132}, "hob", new int[]{8, 66, 132},
            "ned", new int[]{16, 66, 132}, "jack", new int[]{4, 66, 132});
    private static final Map<String, Double> SKILL = Map.of("finn", 0.9, "hob", 0.5, "ned", 0.6, "jack", 0.7);

    /** Angler key ("p:<uuid>" / "f:<id>") -> their best: weight, the fish, their name. */
    record Best(double lb, String fish, String name) {}

    private static final Map<String, Best> BOARD = new LinkedHashMap<>();
    private static long runningDay = -1;
    private static String leader = "";

    static boolean on(ServerWorld w) {
        if (TownCalendar.today(w) != TownCalendar.Big.FISHING_CONTEST && runningDay != w.getTimeOfDay() / 24000L) return false;
        int t = (int) (w.getTimeOfDay() % 24000L);
        return t >= FROM && t < TO;
    }

    /** A contest fisher's day: their spot on the quay, rod out. */
    static TownLife.Plan plan(ServerWorld w, String folk, Townsfolk.Phase phase) {
        int[] s = SPOTS.get(folk);
        if (s == null || !on(w) || phase == Townsfolk.Phase.SLEEP || phase == Townsfolk.Phase.SERVICE) return null;
        BlockPos at = new BlockPos(s[0], s[1], s[2]);
        TownLife.Plan p = new TownLife.Plan(phase, TownLife.standNear(w, at, 3));
        p.act = TownsfolkEntity.Act.FISH;
        p.look = Vec3d.ofCenter(at.south(6)).add(0, -1, 0);
        return p;
    }

    /** /pptown event fishing: today, now (testing). */
    static String force(ServerWorld w) {
        runningDay = w.getTimeOfDay() / 24000L;
        BOARD.clear();
        leader = "";
        int t = (int) (w.getTimeOfDay() % 24000L);
        if (t < FROM || t >= TO - 1200) w.setTimeOfDay(w.getTimeOfDay() - t + FROM + 10);
        started = true;
        finished = false;
        announceStart(w);
        return "The fishing contest is on until sundown.";
    }

    // ------------------------------------------------------------------ ticking (TownLife, every 40 ticks)
    private static boolean started, finished;

    static void tick(ServerWorld w) {
        boolean live = on(w);
        if (live && !started) { started = true; finished = false; if (BOARD.isEmpty()) announceStart(w); }
        if (!live) {
            if (started && !finished) finish(w);
            started = false;
            return;
        }
        // the townsfolk anglers: a bite now and then (about every 1-2 minutes each)
        for (String folk : SPOTS.keySet()) {
            TownsfolkEntity e = TownLife.live(w, folk);
            if (e == null || e.act() != TownsfolkEntity.Act.FISH || w.random.nextInt(60) != 0) continue;
            double skill = SKILL.getOrDefault(folk, 0.5);
            String[] fish = {"cod", "salmon", "cod", "salmon", "pufferfish", "red snapper", "sea bass", "mackerel"};
            String kind = fish[w.random.nextInt(fish.length)];
            double lb = weigh(kind, w.random.nextDouble() * (0.55 + 0.45 * skill));
            Vec3d look = e.plan != null && e.plan.look != null ? e.plan.look : e.getPos();
            w.spawnParticles(ParticleTypes.SPLASH, look.x, look.y + 1, look.z, 25, 0.4, 0.1, 0.4, 0.1);
            e.triggerAnim("action", "cheer");
            record(w, "f:" + folk, e.folk().name(), kind, lb);
        }
    }

    private static void announceStart(ServerWorld w) {
        broadcast(w, "Finn Gale challenges the town: the biggest fish by sundown wins the Golden Marlin! Cast anywhere round the island.");
    }

    private static void finish(ServerWorld w) {
        finished = true;
        runningDay = -1;
        var win = BOARD.entrySet().stream().max(Comparator.comparingDouble(e -> e.getValue().lb())).orElse(null);
        if (win == null) { broadcast(w, "Sundown - and not a fish weighed all day. Finn calls it off in disgust."); return; }
        Best b = win.getValue();
        broadcast(w, "Sundown! " + b.name() + " wins the fishing contest with " + article(b.fish()) + " of " + fmt(b.lb()) + ".");
        TownMemory.news(w, "catch", (win.getKey().startsWith("p:") ? "Captain " : "") + b.name() + " won Finn's fishing contest with a "
                + fmt(b.lb()) + " " + b.fish() + "!", "finn");
        if (win.getKey().startsWith("p:")) {
            ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(UUID.fromString(win.getKey().substring(2)));
            if (p != null) {
                p.getInventory().offerOrDrop(new ItemStack(HomesteadBlocks.GOLDEN_MARLIN_TROPHY));
                p.getInventory().offerOrDrop(new ItemStack(ModItems.COIN, 20));
                p.sendMessage(Text.literal("[Fishing Contest] Finn hands over the Golden Marlin and a purse of 20 coins. \"Beginner's luck.\"").formatted(Formatting.GOLD), false);
            }
        }
        BOARD.clear();
        leader = "";
    }

    // ------------------------------------------------------------------ players (FishingContestMixin)
    /** The catch, just before it flies to the angler: weigh each fish and enter the best. */
    public static void caught(ServerPlayerEntity p, Collection<ItemStack> loot) {
        ServerWorld w = p.getServerWorld();
        if (!w.getRegistryKey().equals(net.get900.pixelpirates.homestead.trade.PortTraders.DIM) || !on(w)) return;
        if (p.getPos().horizontalLength() > WATERS) return;
        for (ItemStack s : loot) {
            if (!s.isIn(ItemTags.FISHES)) continue;
            String kind = s.getName().getString().replace("Raw ", "").toLowerCase();
            double lb = weigh(kind, w.random.nextDouble());
            NbtCompound d = s.getOrCreateSubNbt("display");
            NbtList lore = new NbtList();
            lore.add(NbtString.of(Text.Serializer.toJson(Text.literal("Weight: " + fmt(lb) + " (Wavebreak fishing contest)").formatted(Formatting.GOLD))));
            d.put("Lore", lore);
            s.getOrCreateNbt().putDouble("ContestWeight", lb);
            record(w, "p:" + p.getUuid(), p.getName().getString(), kind, lb);
            int place = place("p:" + p.getUuid());
            p.sendMessage(Text.literal("[Fishing Contest] " + capital(article(kind)) + " of " + fmt(lb) + " - you're " + ordinal(place) + ".").formatted(Formatting.AQUA), true);
        }
    }

    /** A weight in lb for a fish of that kind; roll 0..1 (squared, so big ones are rare). */
    static double weigh(String kind, double roll) {
        double lo, hi;
        if (kind.contains("cod")) { lo = 1.5; hi = 14; }
        else if (kind.contains("salmon")) { lo = 3; hi = 22; }
        else if (kind.contains("puffer")) { lo = 0.5; hi = 4; }
        else if (kind.contains("tropical")) { lo = 0.2; hi = 2; }
        else if (kind.contains("bass") || kind.contains("snapper")) { lo = 2; hi = 16; }
        else if (kind.contains("mackerel")) { lo = 1; hi = 6; }
        else { lo = 1; hi = 12; }
        return Math.round((lo + (hi - lo) * roll * roll) * 10) / 10.0;
    }

    private static void record(ServerWorld w, String key, String name, String kind, double lb) {
        Best old = BOARD.get(key);
        if (old != null && old.lb() >= lb) return;
        BOARD.put(key, new Best(lb, kind, name));
        String top = BOARD.entrySet().stream().max(Comparator.comparingDouble(e -> e.getValue().lb())).map(Map.Entry::getKey).orElse("");
        if (!top.equals(leader) || top.equals(key)) {
            boolean newLeader = !top.equals(leader);
            leader = top;
            if (top.equals(key) && newLeader)
                broadcast(w, name + " takes the lead with " + article(kind) + " of " + fmt(lb) + "!");
            else if (top.equals(key))
                broadcast(w, name + " beats their own best: " + article(kind) + " of " + fmt(lb) + ".");
            w.playSound(null, new BlockPos(12, 66, 132), SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.NEUTRAL, 0.5f, 1.4f);
        }
    }

    private static int place(String key) {
        List<String> order = BOARD.entrySet().stream().sorted(Comparator.comparingDouble(e -> -e.getValue().lb())).map(Map.Entry::getKey).toList();
        return order.indexOf(key) + 1;
    }

    /** For Finn's dialogue: the top three. */
    static String standings() {
        if (BOARD.isEmpty()) return "Nothing weighed yet - get a line in the water!";
        return String.join(", ", BOARD.values().stream().sorted(Comparator.comparingDouble(b -> -b.lb())).limit(3)
                .map(b -> b.name() + " " + fmt(b.lb())).toList());
    }

    static void broadcast(ServerWorld w, String s) {
        for (ServerPlayerEntity p : w.getPlayers())
            p.sendMessage(Text.literal("[Fishing Contest] ").formatted(Formatting.AQUA).append(Text.literal(s).formatted(Formatting.WHITE)), false);
    }

    private static String fmt(double lb) { return String.format("%.1f lb", lb); }
    private static String article(String s) { return ("aeiou".indexOf(s.charAt(0)) >= 0 ? "an " : "a ") + s; }
    private static String capital(String s) { return Character.toUpperCase(s.charAt(0)) + s.substring(1); }
    private static String ordinal(int n) { return n + (n % 100 / 10 == 1 ? "th" : switch (n % 10) { case 1 -> "st"; case 2 -> "nd"; case 3 -> "rd"; default -> "th"; }); }
}
