package net.get900.pixelpirates.homestead.town;

import net.get900.pixelpirates.block.custom.FortCannonBlockEntity;
import net.get900.pixelpirates.world.faction.FactionManager;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * THE GOVERNOR'S BALL (2026-10-05, TownCalendar.GOVERNORS_BALL; the user's idea: "a formal dance at the fort - everyone
 * in their best, a string quartet, the Governor's speech; invitations go to players with good Armada standing").
 * Held in the BALLROOM of the Governor's Residence (#17, east wing: parquet floor x37..40 z-76..-64, chandeliers, the
 * musicians' corner + organ console at the north end, sofas along x41).
 * MORNING: players with Iron Armada reputation >= {@link #INVITE_REP} get an INVITATION (a card - keep it in your pack).
 * EVENING 12500-16000: the guests arrive; from {@link #MUSIC_AT} the quartet (Dan, Harmonia and two friends) plays the
 * waltz (TownMusic.WALTZ, after Strauss's Blue Danube - public domain) and the couples dance on the parquet; at
 * {@link #SPEECH_AT} the Governor speaks and raises a glass - every invited player in the room gains Armada standing.
 * During the ball the Residence guards turn away anyone without an invitation (Garrison.ballGuest), politely.
 * Test: /pptown event ball.
 */
public final class GovernorsBall {
    private GovernorsBall() {}

    static final int FROM = 12500, MUSIC_AT = 12800, SPEECH_AT = 13600, TO = 16000, INVITE_REP = 100, TOAST_REP = 25;
    static final int G = 71;                                            // the ballroom floor (PortCityLayout manor: MG + 1)
    static final Box BALLROOM = new Box(35, G - 1, -79, 43, G + 6, -61);
    static final String CARD = "GovernorsBallInvitation";

    /** Couples on the parquet (each pair faces across a column), the quartet, the host and the guests along the walls. */
    static final String[][] COUPLES = {{"cobb", "wilma"}, {"jack", "morwenna"}, {"tobias", "ginny"}, {"brannoc", "nell"},
            {"isadora", "ptolemy"}, {"seraphine", "elias"}, {"rosalind", "gideon"}};
    static final Map<String, int[]> PLACES = Map.ofEntries(
            Map.entry("aldous", new int[]{38, -75}), Map.entry("dan", new int[]{40, -77}), Map.entry("harmonia", new int[]{39, -77}),
            Map.entry("quill", new int[]{37, -77}), Map.entry("silas", new int[]{41, -76}),            // the quartet
            Map.entry("pettigrew", new int[]{36, -70}), Map.entry("ashby", new int[]{36, -67}), Map.entry("anselm", new int[]{36, -64}),
            Map.entry("agatha", new int[]{41, -73}), Map.entry("martha", new int[]{41, -70}), Map.entry("rufus", new int[]{41, -67}));
    static final Set<String> QUARTET = Set.of("dan", "harmonia", "quill", "silas");

    private static long forcedAt = Long.MIN_VALUE, invitedDay = -1, heldDay = -1;
    private static boolean on, speechGiven;
    private static final Set<UUID> toasted = new HashSet<>();

    // ------------------------------------------------------------------ when
    static boolean today(ServerWorld w) {
        return TownCalendar.today(w) == TownCalendar.Big.GOVERNORS_BALL || w.getTime() - forcedAt >= 0 && w.getTime() - forcedAt < TO - FROM;
    }

    private static int clock(ServerWorld w) {
        if (w.getTime() - forcedAt >= 0 && w.getTime() - forcedAt < TO - FROM) return FROM + (int) (w.getTime() - forcedAt);
        return (int) (w.getTimeOfDay() % 24000L);
    }

    static boolean ball(ServerWorld w) { int c = clock(w); return today(w) && c >= FROM && c < TO; }

    static String force(ServerWorld w) {
        forcedAt = w.getTime();
        invite(w);
        return "The Governor's Ball begins - invitations are out.";
    }

    // ------------------------------------------------------------------ invitations
    static boolean invited(ServerPlayerEntity p) {
        if (FactionManager.getReputation(p, FortCannonBlockEntity.ISLAND_FACTION) >= INVITE_REP) return true;
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(Items.PAPER) && s.hasNbt() && s.getNbt().getBoolean(CARD)) return true;
        }
        return false;
    }

    private static void invite(ServerWorld w) {
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (FactionManager.getReputation(p, FortCannonBlockEntity.ISLAND_FACTION) < INVITE_REP) continue;
            ItemStack card = new ItemStack(Items.PAPER);
            card.setCustomName(Text.literal("Invitation to the Governor's Ball").formatted(Formatting.GOLD));
            NbtList lore = new NbtList();
            lore.add(NbtString.of(Text.Serializer.toJson(Text.literal("The Governor requests the pleasure of your company").formatted(Formatting.GRAY))));
            lore.add(NbtString.of(Text.Serializer.toJson(Text.literal("in the Ballroom of the Residence, at dusk.").formatted(Formatting.GRAY))));
            card.getOrCreateSubNbt("display").put("Lore", lore);
            card.getOrCreateNbt().putBoolean(CARD, true);
            p.getInventory().offerOrDrop(card);
            p.sendMessage(Text.literal("[Residence] A footman brings you a card sealed with the Armada's anchor: you are invited to the Governor's Ball tonight!").formatted(Formatting.GOLD), false);
        }
    }

    // ------------------------------------------------------------------ plans
    static TownLife.Plan plan(ServerWorld w, Townsfolk.Folk f, Townsfolk.Phase ph) {
        if (!ball(w) || ph == Townsfolk.Phase.SLEEP) return null;
        String id = f.id();
        int[] at = PLACES.get(id);
        if (at != null) {
            TownLife.Plan p = new TownLife.Plan(ph, new BlockPos(at[0], G, at[1]));
            p.look = QUARTET.contains(id) || id.equals("aldous") ? new Vec3d(38.5, G + 1.5, -69) : new Vec3d(38.5, G + 1.5, -70);
            return p;
        }
        for (int c = 0; c < COUPLES.length; c++) {
            int side = COUPLES[c][0].equals(id) ? 0 : COUPLES[c][1].equals(id) ? 1 : -1;
            if (side < 0) continue;
            int z = -73 + (c % 4) * 3, x = c < 4 ? (side == 0 ? 37 : 38) : (side == 0 ? 39 : 40);  // two columns of couples
            if (c >= 4) z = -72 + (c - 4) * 3;
            TownLife.Plan p = new TownLife.Plan(ph, new BlockPos(x, G, z));
            p.look = new Vec3d((side == 0 ? x + 1 : x - 1) + 0.5, G + 1.5, z + 0.5);       // facing their partner
            if (clock(w) >= MUSIC_AT) p.act = TownsfolkEntity.Act.DANCE;
            return p;
        }
        return null;
    }

    // ------------------------------------------------------------------ ticking (TownLife, every 40)
    static void tick(ServerWorld w) {
        long day = w.getTimeOfDay() / 24000L;
        int c = clock(w);
        if (TownCalendar.today(w) == TownCalendar.Big.GOVERNORS_BALL && invitedDay != day && c >= 1000 && c < 1400) {
            invitedDay = day;
            invite(w);
        }
        boolean now = ball(w);
        if (now && !on) {
            on = true; speechGiven = false; toasted.clear();
            heldDay = day;
            TownEvents.broadcastTown(w, "[Residence] The lamps are lit at the Governor's Residence - the Ball begins!");
            TownLife.replanAll(w);
        } else if (!now && on) {
            on = false;
            TownEvents.broadcastTown(w, "[Residence] The last waltz is played, and the carriages are called. Goodnight!");
            TownLife.replanAll(w);
        }
        if (!now) return;
        if (c >= MUSIC_AT) {
            if (c < MUSIC_AT + 40) TownLife.replanAll(w);                  // the couples take the floor
            TownMusic.play(w, "ball", new Vec3d(39, G + 1, -76), TownMusic.WALTZ, SoundEvents.BLOCK_NOTE_BLOCK_HARP.value(), 1.4f);
            for (String q : QUARTET) {
                TownsfolkEntity e = TownLife.live(w, q);
                if (e != null && w.random.nextInt(3) == 0) e.triggerAnim("action", "flourish");
            }
        }
        if (!speechGiven && c >= SPEECH_AT) { speechGiven = true; speech(w); }
    }

    private static void speech(ServerWorld w) {
        String[] lines = {"Friends! Captains! Citizens of Wavebreak!",
                "When I came to this island it was a sandbar and a rumour. Tonight it is the finest port on the Starter Seas.",
                "The Iron Armada keeps these waters safe - and it is the good people of this town who make them worth keeping.",
                "So raise your glasses: to Wavebreak, to the Armada, and to every captain who sails home to us!"};
        for (String l : lines) say(w, "[Governor Thorne] " + l);
        w.playSound(null, new BlockPos(38, G + 1, -72), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.NEUTRAL, 0.8f, 0.8f);
        for (TownsfolkEntity e : w.getEntitiesByClass(TownsfolkEntity.class, BALLROOM, x -> true)) e.triggerAnim("action", "cheer");
        for (ServerPlayerEntity p : w.getPlayers(p -> BALLROOM.contains(p.getPos()))) {
            if (!invited(p) || !toasted.add(p.getUuid())) continue;
            FactionManager.modifyReputation(p, FortCannonBlockEntity.ISLAND_FACTION, TOAST_REP);
            p.sendMessage(Text.literal("[Residence] The Governor raises his glass to you by name. (+" + TOAST_REP + " Iron Armada standing)").formatted(Formatting.GOLD), false);
            for (String id : PLACES.keySet()) { Townsfolk.Folk f = Townsfolk.get(id); if (f != null) TownMemory.befriend(p, f, 3); }
        }
        TownMemory.news(w, "ball", "The Governor's Ball was the talk of the island - " + (toasted.isEmpty() ? "though no captain came." : toasted.size() + " captain" + (toasted.size() == 1 ? "" : "s") + " danced the night away."), "aldous");
    }

    private static void say(ServerWorld w, String s) {
        for (ServerPlayerEntity p : w.getPlayers(p -> p.squaredDistanceTo(38, G, -70) < 48 * 48))
            p.sendMessage(Text.literal(s).formatted(Formatting.YELLOW), false);
    }
}
