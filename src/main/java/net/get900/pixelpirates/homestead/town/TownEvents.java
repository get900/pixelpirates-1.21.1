package net.get900.pixelpirates.homestead.town;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.get900.pixelpirates.homestead.HomesteadBlocks;
import net.get900.pixelpirates.homestead.HomesteadItems;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import org.joml.Vector3f;

import java.util.List;
import java.util.Random;

/**
 * THE TOWN'S CALENDAR (2026-10-04, round 3 - the user's idea list):
 *  MARKET DAY   every 7th day (day % 7 == 3), 7 am - 5 pm: a pop-up stall in the plaza (display blocks - nothing in the
 *               world is touched) and Marco Venn the travelling merchant with rare stock that changes every visit.
 *  FESTIVAL     every 8th day (day % 8 == 5) - and the evening after a player kills a chain boss (THE PARTY) - 6:30 pm to
 *               10:30 pm: lanterns + bunting round the plaza, everyone out, Fiddler Dan playing, dancing, fireworks over
 *               the harbour (client-side - harmless).
 *  MUSIC NIGHT  odd days, 6:30 - 10 pm: Dan plays in the Grog Barrel and some of the drinkers dance.
 *  WEDDINGS     every 12th day (day % 12 == 6) at noon a townsfolk couple marry in the chapel (once each); two PLAYERS can
 *               ask Father Anselm to marry them any time. Organ, vows, the bells, confetti, fireworks.
 *  MEMORIAL     the morning after a player is lost at sea: a service with their name; it goes on the memorial roll (the
 *               chapel's MEMORIAL_PLAQUEs show it).
 *  RAIN         outdoor folk shelter in the inn and the tavern; strolls and games move indoors.
 */
public final class TownEvents {
    private TownEvents() {}

    public enum Mode { NONE, RAIN, FESTIVAL, MUSIC, WEDDING, MEMORIAL }

    public static final Identifier FX = new Identifier("pixelpirates", "town_fx");
    static final BlockPos PLAZA = new BlockPos(0, 66, 0), STAGE = new BlockPos(58, 68, 40), BELLS = new BlockPos(-25, 95, -55);
    static final Vec3d ALTAR_FRONT = new Vec3d(-25.5, 72, -74.5);
    private static final String FESTIVAL_TAG = "pp_festival", STALL_TAG = "pp_stall";

    static long day(ServerWorld w) { return w.getTimeOfDay() / 24000L; }

    static int tod(ServerWorld w) { return (int) Math.floorMod(w.getTimeOfDay(), 24000L); }

    // ------------------------------------------------------------------ the calendar
    public static boolean marketDay(ServerWorld w) { long d = day(w); int t = tod(w); return d % 7 == 3 && t >= 500 && t < 11500; }

    public static boolean festival(ServerWorld w) {
        long d = day(w);
        int t = tod(w);
        boolean today = d % 8 == 5 || TownMemory.get(w.getServer()).partyDay == d || forcedFestival(w);
        return today && (t >= 12500 && t < 16500 || forcedFestival(w));
    }

    private static long forcedFestivalAt = Long.MIN_VALUE, forcedWeddingAt = Long.MIN_VALUE, forcedMemorialAt = Long.MIN_VALUE;
    private static String[] forcedCouple = null;                    // {name a, name b, "players" | "folk", id a, id b}

    private static boolean forcedFestival(ServerWorld w) { return w.getTime() - forcedFestivalAt >= 0 && w.getTime() - forcedFestivalAt < 4000; }

    public static boolean musicNight(ServerWorld w) { int t = tod(w); return day(w) % 2 == 1 && t >= 12500 && t < 16000 && !festival(w); }

    /** NPC couples, married one per wedding day in this order. */
    static final String[][] COUPLES = {{"cobb", "wilma"}, {"jack", "morwenna"}, {"tobias", "ginny"}, {"brannoc", "nell"}};

    /** The couple being married right now (ids, or player names for a player wedding) + ticks since it began; null = none. */
    static String[] wedding(ServerWorld w) {
        if (w.getTime() - forcedWeddingAt >= 0 && w.getTime() - forcedWeddingAt < 2400 && forcedCouple != null) return forcedCouple;
        long d = day(w);
        int t = tod(w);
        if (d % 12 != 6 || t < 5000 || t >= 7400) return null;
        TownMemory m = TownMemory.get(w.getServer());
        if (m.wed.contains("done:" + d)) return null;                                     // today's couple are already wed
        for (String[] c : COUPLES) if (!m.wed.contains(c[0] + "+" + c[1])) {
            Townsfolk.Folk a = Townsfolk.get(c[0]), b = Townsfolk.get(c[1]);
            return new String[]{a.name(), b.name(), "folk", c[0], c[1]};
        }
        return null;
    }

    static int weddingElapsed(ServerWorld w) {
        if (w.getTime() - forcedWeddingAt >= 0 && w.getTime() - forcedWeddingAt < 2400 && forcedCouple != null) return (int) (w.getTime() - forcedWeddingAt);
        return wedding(w) == null ? -1 : tod(w) - 5000;
    }

    static int memorialElapsed(ServerWorld w) {
        if (w.getTime() - forcedMemorialAt >= 0 && w.getTime() - forcedMemorialAt < 1800) return (int) (w.getTime() - forcedMemorialAt);
        TownMemory m = TownMemory.get(w.getServer());
        int t = tod(w);
        return m.memorialDay == day(w) && t >= 4000 && t < 5800 ? t - 4000 : -1;
    }

    /** What this townsperson's day turns into right now (NONE = the ordinary plan). */
    static Mode mode(ServerWorld w, Townsfolk.Folk f, Townsfolk.Phase phase) {
        if (phase == Townsfolk.Phase.SLEEP) return Mode.NONE;
        String[] wed = wedding(w);
        if (wed != null && (Townsfolk.attends(f) || phase == Townsfolk.Phase.LEISURE || f.id().equals(wed[3]) || f.id().equals(wed[4])
                || f.id().equals("anselm") || f.id().equals("harmonia"))) return Mode.WEDDING;
        if (memorialElapsed(w) >= 0 && (Townsfolk.attends(f) || f.id().equals("ned") || f.id().equals("rufus"))) return Mode.MEMORIAL;
        if (festival(w) && (phase == Townsfolk.Phase.LEISURE || phase == Townsfolk.Phase.HOME || f.id().equals("dan"))) return Mode.FESTIVAL;
        if (musicNight(w) && f.id().equals("dan")) return Mode.MUSIC;
        if (w.isRaining() && outdoors(f, phase)) return Mode.RAIN;
        return Mode.NONE;
    }

    private static boolean outdoors(Townsfolk.Folk f, Townsfolk.Phase phase) {
        if (phase == Townsfolk.Phase.LEISURE) return f.hobbies().contains(Townsfolk.Hobby.STROLL) || f.hobbies().contains(Townsfolk.Hobby.PLAY);
        if (phase != Townsfolk.Phase.WORK) return false;
        return switch (f.style()) {
            case WANDER, CRIER, PLAY, FISH -> true;
            case ROUNDS -> f.id().equals("brask");                 // the lamplighter + the night watch work in any weather
            default -> false;
        };
    }

    /** Is this character about today? Marco only comes on market days. */
    static boolean present(ServerWorld w, Townsfolk.Folk f) { return !f.id().equals("marco") || marketDay(w); }

    // ------------------------------------------------------------------ the world tick (TownLife, every 40 ticks)
    private static boolean festivalUp, stallUp;
    private static int lastWeddingStage = -1, lastMemorialStage = -1;

    static void tick(ServerWorld w) {
        boolean fest = festival(w);
        if (fest != festivalUp) { if (fest) decorate(w); else clear(w, FESTIVAL_TAG, PLAZA, 24); festivalUp = fest; if (fest) festivalNews(w); }
        boolean market = marketDay(w);
        if (market != stallUp) { if (market) stall(w); else clear(w, STALL_TAG, stallPos(w), 8); stallUp = market; }
        if (fest && tod(w) >= 13000 || fest && forcedFestival(w)) fireworks(w, PLAZA.getX(), PLAZA.getZ() + 70, 3);
        int el = weddingElapsed(w);
        if (el >= 0) weddingStage(w, el);
        else lastWeddingStage = -1;
        int me = memorialElapsed(w);
        if (me >= 0 && lastMemorialStage < 0) {
            lastMemorialStage = 0;
            String who = TownMemory.get(w.getServer()).memorialFor;
            chapelSay(w, Text.literal("[Chapel] The bell tolls for " + who + ", lost at sea.").formatted(Formatting.GRAY));
            w.playSound(null, BELLS, SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 3f, 0.6f);
        } else if (me < 0) lastMemorialStage = -1;
    }

    private static void festivalNews(ServerWorld w) {
        TownMemory m = TownMemory.get(w.getServer());
        String why = m.partyDay == day(w) && !m.partyFor.isEmpty() ? "a party for Captain " + m.partyFor + "!" : "the harbour festival!";
        TownMemory.news(w, "festival", "Tonight in the plaza: " + why + " Lanterns, music, dancing and fireworks!", "town");
        for (ServerPlayerEntity p : w.getPlayers())
            if (p.squaredDistanceTo(Vec3d.ofCenter(PLAZA)) < 200 * 200)
                p.sendMessage(Text.literal("[~] The lanterns go up in the plaza - " + why).formatted(Formatting.GOLD), false);
    }

    // ------------------------------------------------------------------ fireworks + confetti
    /** `n` bursts somewhere over (cx, cz): sent to the players within 220 blocks (client TownFx draws them - no rockets). */
    static void fireworks(ServerWorld w, int cx, int cz, int n) {
        for (int i = 0; i < n; i++) {
            if (w.random.nextInt(2) == 0) continue;
            double x = cx + w.random.nextInt(60) - 30, y = 96 + w.random.nextInt(20), z = cz + w.random.nextInt(40) - 20;
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z); buf.writeVarInt(w.random.nextInt(1 << 20));
            for (ServerPlayerEntity p : w.getPlayers()) if (p.squaredDistanceTo(x, y, z) < 220 * 220) ServerPlayNetworking.send(p, FX, PacketByteBufs.copy(buf));
        }
    }

    static void confetti(ServerWorld w, Vec3d at) {
        int[] cols = {0xFF4040, 0xFFD040, 0x40C0FF, 0xFF80E0, 0x60E060, 0xFFFFFF};
        for (int c : cols)
            w.spawnParticles(new DustParticleEffect(new Vector3f((c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f), 1.2f),
                    at.x, at.y + 2.5, at.z, 25, 2.5, 1.2, 2.5, 0.02);
    }

    // ------------------------------------------------------------------ decorations (display entities: nothing in the world changes)
    static void display(ServerWorld w, BlockState s, double x, double y, double z, float scale, String tag) {
        NbtCompound n = new NbtCompound();
        n.putString("id", "minecraft:block_display");
        n.put("block_state", NbtHelper.fromBlockState(s));
        NbtList pos = new NbtList();
        pos.add(net.minecraft.nbt.NbtDouble.of(x)); pos.add(net.minecraft.nbt.NbtDouble.of(y)); pos.add(net.minecraft.nbt.NbtDouble.of(z));
        n.put("Pos", pos);
        NbtCompound tr = new NbtCompound();
        tr.put("left_rotation", floats(0, 0, 0, 1));
        tr.put("right_rotation", floats(0, 0, 0, 1));
        tr.put("translation", floats(-scale / 2, 0, -scale / 2));
        tr.put("scale", floats(scale, scale, scale));
        n.put("transformation", tr);
        NbtList tags = new NbtList();
        tags.add(NbtString.of(tag));
        n.put("Tags", tags);
        Entity e = EntityType.loadEntityWithPassengers(n, w, en -> en);
        if (e != null) w.spawnEntity(e);
    }

    private static NbtList floats(float... v) { NbtList l = new NbtList(); for (float f : v) l.add(NbtFloat.of(f)); return l; }

    static void clear(ServerWorld w, String tag, BlockPos c, int r) {
        for (Entity e : w.getEntitiesByClass(net.minecraft.entity.decoration.DisplayEntity.BlockDisplayEntity.class, new Box(c).expand(r, 16, r),
                d -> d.getCommandTags().contains(tag))) e.discard();
    }

    /** Lanterns on a ring of strings round the plaza, with coloured bunting between them. */
    private static void decorate(ServerWorld w) {
        clear(w, FESTIVAL_TAG, PLAZA, 24);
        int g = TownLife.groundAt(w, PLAZA.getX(), PLAZA.getZ()).getY();
        BlockState lantern = Blocks.LANTERN.getDefaultState().with(net.minecraft.block.LanternBlock.HANGING, true);
        BlockState[] wool = {Blocks.RED_WOOL.getDefaultState(), Blocks.YELLOW_WOOL.getDefaultState(), Blocks.LIGHT_BLUE_WOOL.getDefaultState(),
                Blocks.LIME_WOOL.getDefaultState(), Blocks.MAGENTA_WOOL.getDefaultState(), Blocks.WHITE_WOOL.getDefaultState()};
        double r = 10.5;
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI * 2 / 64, x = PLAZA.getX() + 0.5 + Math.cos(a) * r, z = PLAZA.getZ() + 0.5 + Math.sin(a) * r;
            double sag = 0.6 * Math.abs(Math.sin(a * 4));                                     // the string droops between its posts
            double y = g + 5.2 - sag;
            if (i % 8 == 0) display(w, lantern, x, y - 0.8, z, 1f, FESTIVAL_TAG);
            else display(w, wool[i % wool.length], x, y - 0.3, z, 0.28f, FESTIVAL_TAG);
            display(w, Blocks.CHAIN.getDefaultState(), x, y, z, 0.25f, FESTIVAL_TAG);
        }
    }

    // ------------------------------------------------------------------ market day: the pop-up stall + Marco's stock
    static BlockPos stallPos(ServerWorld w) { return TownLife.groundAt(w, 6, -6); }

    /** A tiny striped stall: counter, posts, canopy, barrels, a crate - all display blocks. Marco stands behind it. */
    private static void stall(ServerWorld w) {
        BlockPos s = stallPos(w);
        clear(w, STALL_TAG, s, 8);
        stockSeed = day(w);
        double x = s.getX() + 0.5, y = s.getY(), z = s.getZ() + 0.5;
        for (int dx = -1; dx <= 1; dx++) display(w, Blocks.SPRUCE_PLANKS.getDefaultState(), x + dx, y, z, 1f, STALL_TAG);
        for (int dx = -1; dx <= 1; dx++) display(w, Blocks.SPRUCE_SLAB.getDefaultState(), x + dx, y + 1, z, 1f, STALL_TAG);
        for (int dx : new int[]{-2, 2})
            for (int dy = 0; dy < 3; dy++) display(w, Blocks.SPRUCE_FENCE.getDefaultState(), x + dx, y + dy, z, 1f, STALL_TAG);
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -1; dz <= 1; dz++)
                display(w, ((dx + 2) & 1) == 0 ? Blocks.RED_WOOL.getDefaultState() : Blocks.WHITE_WOOL.getDefaultState(), x + dx, y + 3, z + dz - 0.5, 1f, STALL_TAG);
        display(w, Blocks.BARREL.getDefaultState(), x - 2, y, z - 2, 1f, STALL_TAG);
        display(w, HomesteadBlocks.CARGO_CRATE.getDefaultState(), x + 2, y, z - 2, 1f, STALL_TAG);
        display(w, Blocks.LANTERN.getDefaultState().with(net.minecraft.block.LanternBlock.HANGING, true), x, y + 2.2, z, 1f, STALL_TAG);
        TownMemory.news(w, "market", "Marco Venn the travelling merchant has set up shop in the plaza - today only!", "marco");
    }

    /** Marco's goods: a different handful every visit (seeded by the day). */
    static void marcoStock(TradeOfferList o) {
        Random r = new Random(stockSeed * 31 + 7);
        Object[][] pool = {
                {HomesteadItems.PARROT_CRATE, 1, 40}, {HomesteadItems.COMPASS_OF_DESIRE, 1, 75}, {HomesteadItems.VINTAGE_RUM, 1, 12},
                {HomesteadItems.KRAKENS_KISS, 1, 8}, {ModItems.TREASURE_MAP_COMMON, 1, 10}, {Items.ENDER_PEARL, 2, 14},
                {Items.AMETHYST_SHARD, 6, 6}, {Items.NAUTILUS_SHELL, 1, 18}, {Items.HEART_OF_THE_SEA, 1, 120}, {Items.TRIDENT, 1, 150},
                {Items.ENCHANTED_BOOK, 1, 30}, {HomesteadBlocks.SHIP_IN_BOTTLE_GALLEON, 1, 40}, {HomesteadBlocks.SHIP_IN_BOTTLE_GHOST, 1, 60},
                {Items.GOLDEN_APPLE, 1, 30}, {Items.SPYGLASS, 1, 10}, {HomesteadItems.CAPTAINS_SPYGLASS, 1, 26}, {Items.MUSIC_DISC_13, 1, 24},
                {Items.SADDLE, 1, 20}, {Items.NAME_TAG, 1, 16}, {HomesteadBlocks.KRAKEN_FIGUREHEAD, 1, 55}};
        java.util.List<Object[]> l = new java.util.ArrayList<>(java.util.Arrays.asList(pool));
        java.util.Collections.shuffle(l, r);
        for (int i = 0; i < 6; i++) {
            Object[] e = l.get(i);
            o.add(new TradeOffer(new net.minecraft.item.ItemStack(ModItems.COIN, (int) e[2]),
                    new net.minecraft.item.ItemStack((net.minecraft.item.ItemConvertible) e[0], (int) e[1]), 2, 3, 0.05f));
        }
    }

    static long stockSeed;

    // ------------------------------------------------------------------ weddings
    static final String[] VOWS = {
            "Dearly beloved, we are gathered here in sight of the sea, which has brought these two together...",
            "Marriage is a voyage. There will be storms. There will be calms. There will be a great deal of bailing.",
            "Do you take this sailor to be your shipmate, through fair winds and foul, until the tide takes you both?",
            "They do! They both do!",
            "Then by the power vested in me by the Chapel of Wavebreak and the Governor's paperwork..."};

    private static void weddingStage(ServerWorld w, int el) {
        String[] c = wedding(w);
        if (c == null) return;
        int stage = el < 200 ? 0 : el < 1900 ? 1 + (el - 200) / 340 : el < 2100 ? 6 : 7;
        if (stage == lastWeddingStage) return;
        lastWeddingStage = stage;
        if (stage == 0) {
            chapelSay(w, Text.literal("[Chapel] The bells ring - " + c[0] + " and " + c[1] + " are to be married!").formatted(Formatting.GOLD));
            w.playSound(null, BELLS, SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 3f, 1f);
        } else if (stage >= 1 && stage <= 5) {
            chapelSay(w, Text.literal("[Father Anselm] ").formatted(Formatting.GOLD).append(Text.literal(VOWS[stage - 1]).formatted(Formatting.WHITE)));
        } else if (stage == 6) {
            chapelSay(w, Text.literal("[Father Anselm] ").formatted(Formatting.GOLD).append(Text.literal("...I now pronounce you married! You may kiss... well. Shake hands at least.").formatted(Formatting.WHITE)));
            for (int i = 0; i < 4; i++) w.playSound(null, BELLS, SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 3f, 0.8f + i * 0.1f);
            confetti(w, ALTAR_FRONT);
            fireworks(w, -26, -66, 6);
            TownMemory m = TownMemory.get(w.getServer());
            m.wed.add(c[2].equals("folk") ? c[3] + "+" + c[4] : "players:" + c[0] + "+" + c[1]);
            if (c[2].equals("folk") && forcedCouple == null) m.wed.add("done:" + day(w));
            m.markDirty();
            TownMemory.news(w, "wedding", c[0] + " and " + c[1] + " were married at the chapel!", c[2].equals("folk") ? c[3] : c[0]);
        }
    }

    /** Two players at the altar asked Father Anselm. */
    static void playerWedding(ServerWorld w, String a, String b) {
        forcedCouple = new String[]{a, b, "players", "", ""};
        forcedWeddingAt = w.getTime();
    }

    // ------------------------------------------------------------------ memorials + the party for a boss kill
    /** A player died at sea: their name goes on the roll and the chapel holds a memorial service the next morning. */
    public static void lostAtSea(ServerPlayerEntity p, String how) {
        ServerWorld w = p.getServerWorld();
        TownMemory m = TownMemory.get(w.getServer());
        long d = day(w);
        m.memorial.add(p.getName().getString() + " - " + how + " (day " + d + ")");
        while (m.memorial.size() > 60) m.memorial.remove(0);
        m.memorialDay = d + 1;
        m.memorialFor = p.getName().getString();
        m.markDirty();
        TownMemory.news(w, "memorial", "Captain " + p.getName().getString() + " was lost at sea. A memorial service tomorrow morning at the chapel.", p.getName().getString());
    }

    /** A chain boss fell: the news, and a party in the plaza that evening (or the next, if it's already late). */
    public static void bossKilled(ServerPlayerEntity p, String boss) {
        ServerWorld w = p.getServerWorld();
        TownMemory m = TownMemory.get(w.getServer());
        long d = day(w);
        m.partyDay = tod(w) < 12000 ? d : d + 1;
        m.partyFor = p.getName().getString();
        m.markDirty();
        TownMemory.news(w, "boss", "Captain " + p.getName().getString() + " has slain " + boss + "! The town throws a party in the plaza!", p.getName().getString());
        p.sendMessage(Text.literal("[~] Word of your victory reaches Wavebreak - the town is throwing you a party in the plaza " + (m.partyDay == d ? "tonight" : "tomorrow night") + "!").formatted(Formatting.GOLD), false);
    }

    static void chapelSay(ServerWorld w, Text t) {
        Box chapel = new Box(-48, 64, -96, -4, 100, -38);
        for (ServerPlayerEntity p : w.getPlayers()) if (chapel.contains(p.getPos())) p.sendMessage(t, false);
    }

    // ------------------------------------------------------------------ testing
    static void force(ServerWorld w, String what) {
        switch (what) {
            case "festival" -> forcedFestivalAt = w.getTime();
            case "wedding" -> { forcedCouple = new String[]{"Cobb Appleby", "Wilma Grist", "folk", "cobb", "wilma"}; forcedWeddingAt = w.getTime(); }
            case "memorial" -> {
                TownMemory m = TownMemory.get(w.getServer());
                if (m.memorialFor.isEmpty()) m.memorialFor = "Captain Nobody";
                forcedMemorialAt = w.getTime();
            }
            default -> { }
        }
    }

    public static List<String> roll(ServerWorld w) { return TownMemory.get(w.getServer()).memorial; }
}
