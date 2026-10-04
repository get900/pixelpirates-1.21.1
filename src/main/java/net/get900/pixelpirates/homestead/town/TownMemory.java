package net.get900.pixelpirates.homestead.town;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.*;

/**
 * WHAT THE TOWN REMEMBERS (2026-10-04, round 3 - the user's idea list): the NEWS (everything worth gossiping about - chess
 * results, finished paintings, boss kills, weddings, memorials, a big catch, ships in the harbour, dice wins - the crier
 * cries it and the townsfolk mention it), FRIENDSHIP (per player per townsperson), the dice records against the regulars,
 * the memorial roll, who has married, the boss party, the gallery. Saved with the world ("pixelpirates_town_memory").
 */
public final class TownMemory extends PersistentState {
    public record News(long day, String kind, String text, String about) {}

    final List<News> news = new ArrayList<>();
    final Map<String, Integer> friend = new HashMap<>();           // player|folk -> score
    final Map<String, Long> lastTalk = new HashMap<>(), lastGift = new HashMap<>(), lastSale = new HashMap<>();
    final Map<String, int[]> dice = new HashMap<>();                // player|folk -> {player wins, their wins}
    final Map<String, Integer> tradesToday = new HashMap<>();
    final List<String> memorial = new ArrayList<>();                // "Name - lost at sea, day N"
    long memorialDay = -1;
    String memorialFor = "";
    final Set<String> wed = new HashSet<>();                        // couples married ("a+b")
    long partyDay = -1;
    String partyFor = "";
    final List<UUID> gallery = new ArrayList<>();
    final Set<UUID> trophies = new HashSet<>();

    public static TownMemory get(MinecraftServer s) {
        return s.getOverworld().getPersistentStateManager().getOrCreate(TownMemory::read, TownMemory::new, "pixelpirates_town_memory");
    }

    static long day(ServerWorld w) { return w.getTimeOfDay() / 24000L; }

    // ------------------------------------------------------------------ news
    /** Something happened. `about` = a townsperson id or a player name (so they don't gossip about themselves). */
    public static void news(ServerWorld w, String kind, String text, String about) {
        TownMemory m = get(w.getServer());
        long d = day(w);
        m.news.removeIf(n -> n.kind.equals(kind) && n.about.equals(about) && n.day == d);       // one of each per day
        m.news.add(new News(d, kind, text, about));
        while (m.news.size() > 40) m.news.remove(0);
        m.markDirty();
    }

    /** News from the last `days` days, newest first. */
    static List<News> recent(ServerWorld w, int days) {
        long d = day(w);
        List<News> out = new ArrayList<>();
        List<News> all = get(w.getServer()).news;
        for (int i = all.size() - 1; i >= 0; i--) if (d - all.get(i).day <= days) out.add(all.get(i));
        return out;
    }

    // ------------------------------------------------------------------ friendship
    public static final String[] LEVELS = {"Stranger", "Acquaintance", "Friend", "Good Friend", "Best Friend"};
    private static final int[] AT = {0, 10, 30, 60, 100};

    private static String key(ServerPlayerEntity p, String folk) { return p.getUuid() + "|" + folk; }

    static int score(ServerPlayerEntity p, String folk) { return get(p.getServer()).friend.getOrDefault(key(p, folk), 0); }

    static int level(int score) { int l = 0; for (int i = 0; i < AT.length; i++) if (score >= AT[i]) l = i; return l; }

    static int level(ServerPlayerEntity p, String folk) { return level(score(p, folk)); }

    /** Add friendship; tells the player when they reach a new level. */
    static void befriend(ServerPlayerEntity p, Townsfolk.Folk f, int n) {
        TownMemory m = get(p.getServer());
        String k = key(p, f.id());
        int before = m.friend.getOrDefault(k, 0), after = Math.max(-20, Math.min(150, before + n));
        m.friend.put(k, after);
        m.markDirty();
        if (level(after) > level(before))
            p.sendMessage(net.minecraft.text.Text.literal("[~] " + f.name() + " now counts you as a " + LEVELS[level(after)] + ".")
                    .formatted(net.minecraft.util.Formatting.LIGHT_PURPLE), false);
    }

    /** Once a day: true the first time they talk today (and remembers it). */
    static boolean firstTalkToday(ServerPlayerEntity p, String folk, long day) {
        TownMemory m = get(p.getServer());
        Long last = m.lastTalk.put(key(p, folk), day);
        m.markDirty();
        return last == null || last != day;
    }

    static boolean giftedToday(ServerPlayerEntity p, String folk, long day) {
        Long last = get(p.getServer()).lastGift.get(key(p, folk));
        return last != null && last == day;
    }

    static void gave(ServerPlayerEntity p, String folk, long day) { TownMemory m = get(p.getServer()); m.lastGift.put(key(p, folk), day); m.markDirty(); }

    /** The shop discount for a level: 0 / 0 / 5% / 10% / 15%. */
    static double discount(int level) { return level >= 4 ? 0.15 : level == 3 ? 0.10 : level == 2 ? 0.05 : 0; }

    // ------------------------------------------------------------------ dice against the regulars
    static void dice(MinecraftServer s, UUID player, String folk, boolean playerWon) {
        TownMemory m = get(s);
        int[] r = m.dice.computeIfAbsent(player + "|" + folk, k -> new int[2]);
        r[playerWon ? 0 : 1]++;
        m.markDirty();
    }

    static int[] diceRecord(ServerPlayerEntity p, String folk) { return get(p.getServer()).dice.get(key(p, folk)); }

    // ------------------------------------------------------------------ saving
    static TownMemory read(NbtCompound n) {
        TownMemory m = new TownMemory();
        for (NbtElement e : n.getList("News", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) e;
            m.news.add(new News(c.getLong("Day"), c.getString("Kind"), c.getString("Text"), c.getString("About")));
        }
        readInts(n.getCompound("Friend"), m.friend);
        readLongs(n.getCompound("Talk"), m.lastTalk);
        readLongs(n.getCompound("Gift"), m.lastGift);
        readLongs(n.getCompound("Sale"), m.lastSale);
        NbtCompound d = n.getCompound("Dice");
        for (String k : d.getKeys()) m.dice.put(k, d.getIntArray(k));
        for (NbtElement e : n.getList("Memorial", NbtElement.STRING_TYPE)) m.memorial.add(e.asString());
        m.memorialDay = n.contains("MemorialDay") ? n.getLong("MemorialDay") : -1;
        m.memorialFor = n.getString("MemorialFor");
        for (NbtElement e : n.getList("Wed", NbtElement.STRING_TYPE)) m.wed.add(e.asString());
        m.partyDay = n.contains("PartyDay") ? n.getLong("PartyDay") : -1;
        m.partyFor = n.getString("PartyFor");
        for (NbtElement e : n.getList("Gallery", NbtElement.INT_ARRAY_TYPE)) m.gallery.add(net.minecraft.nbt.NbtHelper.toUuid(e));
        for (NbtElement e : n.getList("Trophies", NbtElement.INT_ARRAY_TYPE)) m.trophies.add(net.minecraft.nbt.NbtHelper.toUuid(e));
        return m;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound n) {
        NbtList l = new NbtList();
        for (News x : news) {
            NbtCompound c = new NbtCompound();
            c.putLong("Day", x.day); c.putString("Kind", x.kind); c.putString("Text", x.text); c.putString("About", x.about);
            l.add(c);
        }
        n.put("News", l);
        n.put("Friend", writeInts(friend));
        n.put("Talk", writeLongs(lastTalk));
        n.put("Gift", writeLongs(lastGift));
        n.put("Sale", writeLongs(lastSale));
        NbtCompound d = new NbtCompound();
        dice.forEach(d::putIntArray);
        n.put("Dice", d);
        NbtList mem = new NbtList();
        for (String s : memorial) mem.add(NbtString.of(s));
        n.put("Memorial", mem);
        n.putLong("MemorialDay", memorialDay);
        n.putString("MemorialFor", memorialFor);
        NbtList w = new NbtList();
        for (String s : wed) w.add(NbtString.of(s));
        n.put("Wed", w);
        n.putLong("PartyDay", partyDay);
        n.putString("PartyFor", partyFor);
        NbtList g = new NbtList();
        for (UUID u : gallery) g.add(net.minecraft.nbt.NbtHelper.fromUuid(u));
        n.put("Gallery", g);
        NbtList t = new NbtList();
        for (UUID u : trophies) t.add(net.minecraft.nbt.NbtHelper.fromUuid(u));
        n.put("Trophies", t);
        return n;
    }

    private static void readInts(NbtCompound c, Map<String, Integer> to) { for (String k : c.getKeys()) to.put(k, c.getInt(k)); }

    private static void readLongs(NbtCompound c, Map<String, Long> to) { for (String k : c.getKeys()) to.put(k, c.getLong(k)); }

    private static NbtCompound writeInts(Map<String, Integer> m) { NbtCompound c = new NbtCompound(); m.forEach(c::putInt); return c; }

    private static NbtCompound writeLongs(Map<String, Long> m) { NbtCompound c = new NbtCompound(); m.forEach(c::putLong); return c; }
}
