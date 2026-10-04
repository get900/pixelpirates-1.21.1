package net.get900.pixelpirates.homestead.town;

import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.world.AiShipController;
import net.get900.pixelpirates.world.ShipRegistryState;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * THE REGATTA (2026-10-05, TownCalendar.REGATTA). At 5 pm on the day the town's three boats line up ABREAST on the
 * start line off the grand pier (z 215, 24 apart - spawned with a small clear margin, ShipSpawner), each skippered by a
 * townsperson at her helm (no faction crew) and fitted with Keelbreaker II; any player whose own ship lies within
 * {@link #ENTRY_R} of the start is entered too. The course: out to the TURNING BUOY (0,295) and back across the line
 * between the finish posts - ~160 blocks, in the deep water round the island (SpawnIslandTerrain DEEP_*), all in view
 * of the quay and the grand pier, where the town gathers to watch and cheer. AI racers use AiShipController's racing
 * mode (full sail for the next mark, never fight, never despawn). Prizes for players 40/15/5 coins; the winner makes
 * the news. Test: /pptown event regatta (/pptown regatta stop).
 */
public final class Regatta {
    private Regatta() {}

    /** Side by side (the user: "race side by side"): out to the turning buoy and back to the line they started on. */
    static final Vec3d[] MARKS = {new Vec3d(0, 62, 295), new Vec3d(0, 62, 212)};
    static final String[] MARK_NAMES = {"the turning buoy", "the finish"};
    static final double ROUND_R = 24, FINISH_HALF = 42, ENTRY_R = 150;
    static final Vec3d START = new Vec3d(0, 62, 215);
    /** The town's boats (the user: town NPCs, not faction ships): blueprint, her name, the townsperson who skippers her
     *  (standing at her helm - no faction crew aboard), her livery ("" = her own colours), how hard she's sailed. */
    static final String[][] FLEET = {{"navy_cutter", "HMS Swift", "pettigrew", "", "1.0"},
            {"merchant_lugger", "Herring Lass", "finn", "coral_reef", "0.97"},
            {"pirate_cutter", "Midnight Eel", "lazlo", "midnight_raider", "0.96"}};
    /** Abreast on the line, bows (+Z) at the turning buoy; spawned with a small clear margin (ShipSpawner), so 24 apart. */
    static final int[][] STARTS = {{-24, 215}, {0, 215}, {24, 215}};
    static final int SPAWN_MARGIN = 6;
    static final String TAG = "pp_regatta";
    static final int GATHER = 10600, GO = 11400, MAX_RACE = 7200;

    enum Phase { IDLE, GATHER, RACE, DONE }

    static final class Racer {
        final String name;
        final Long ship;                          // AI ship id (AI racers)
        String skipper;                           // the townsperson at her helm
        Vector3d helm;                            // where the skipper stands, in SHIP space
        final UUID player;                        // player racers
        int mark;
        int place;                                // 1.. once finished
        boolean retired;
        Vec3d lastPos;
        long lastMove;

        Racer(String name, Long ship, UUID player) { this.name = name; this.ship = ship; this.player = player; }

        boolean done() { return place > 0 || retired; }
    }

    private static Phase phase = Phase.IDLE;
    private static long phaseAt, goAt, lastDay = -1, announced = -1;
    private static final List<Racer> racers = new ArrayList<>();
    private static int finished;
    private static final int[] rounded = new int[MARKS.length];

    static boolean active() { return phase != Phase.IDLE; }

    // ------------------------------------------------------------------ the crowd
    /** A skipper at her helm; everyone else at leisure along the quay edge or out on the grand pier, watching. */
    static TownLife.Plan plan(ServerWorld w, Townsfolk.Folk f, Townsfolk.Phase ph) {
        if (!active()) return null;
        Vec3d helm = helmOf(w, f.id());
        if (helm != null) {
            TownLife.Plan p = new TownLife.Plan(ph, BlockPos.ofFloored(helm));
            p.look = MARKS[0];
            return p;
        }
        if (ph != Townsfolk.Phase.LEISURE && ph != Townsfolk.Phase.HOME) return null;
        int h = Math.floorMod(Objects.hash(f.id(), "regatta"), 100);
        BlockPos at = h < 70 ? TownLife.groundAt(w, -45 + h * 90 / 70, 95) : TownLife.groundAt(w, -2 + h % 5, 104 + (h - 70));
        TownLife.Plan p = new TownLife.Plan(ph, at);
        p.look = new Vec3d(0, 63, 240);
        return p;
    }

    static boolean skipper(String folk) { return active() && racers.stream().anyMatch(r -> folk.equals(r.skipper)); }

    /** Where this townsperson's helm is now (world), if they skipper a racer. */
    private static Vec3d helmOf(ServerWorld w, String folk) {
        for (Racer r : racers) {
            if (!folk.equals(r.skipper) || r.helm == null) continue;
            Vector3d at = AiShipController.toWorld(w, r.ship, r.helm);
            return at == null ? null : new Vec3d(at.x, at.y, at.z);
        }
        return null;
    }

    /** Keep the skippers at their helms (the ship moves under them; a fall or a bad lurch puts them back). */
    private static void skippers(ServerWorld w) {
        for (Racer r : racers) {
            if (r.skipper == null) continue;
            TownsfolkEntity e = TownLife.live(w, r.skipper);
            Vec3d at = helmOf(w, r.skipper);
            if (e == null || at == null) continue;
            if (e.squaredDistanceTo(at) > 2.5 * 2.5 || e.isTouchingWater()) {
                if (e.hasVehicle()) e.stopRiding();
                e.getNavigation().stop();
                e.requestTeleport(at.x, at.y + 0.1, at.z);
            }
        }
    }

    // ------------------------------------------------------------------ the day
    static String force(ServerWorld w) {
        if (active()) return "The regatta is already on.";
        begin(w, true);
        return "Regatta: the racers are coming out - the gun goes in 20 seconds.";
    }

    /** Every 5 ticks (TownLife). */
    static void tick(ServerWorld w) {
        long now = w.getTime();
        int tod = (int) (w.getTimeOfDay() % 24000L);
        long day = w.getTimeOfDay() / 24000L;
        switch (phase) {
            case IDLE -> {
                if (TownCalendar.today(w) == TownCalendar.Big.REGATTA && tod >= GATHER && tod < GO && lastDay != day) begin(w, false);
                if (announced != day && tod >= 1000 && tod < 1200 && TownCalendar.today(w) == TownCalendar.Big.REGATTA) {
                    announced = day;
                    broadcast(w, "Regatta day! The race starts off the harbour at 5 o'clock - bring your ship to the start line to enter.");
                }
            }
            case GATHER -> {
                if (now >= goAt - 60 && now < goAt - 55) broadcast(w, "Three...");
                if (now >= goAt - 40 && now < goAt - 35) broadcast(w, "Two...");
                if (now >= goAt - 20 && now < goAt - 15) broadcast(w, "One...");
                if (now >= goAt) go(w);
            }
            case RACE -> race(w, now);
            case DONE -> { if (now - phaseAt > 600) cleanup(w); }
        }
        if (phase != Phase.IDLE) skippers(w);
        if (phase != Phase.IDLE && now % 20 == 0) {
            buoyGlow(w);
            if (phase == Phase.RACE && w.random.nextInt(2) == 0) cheer(w);
        }
    }

    private static void begin(ServerWorld w, boolean forced) {
        lastDay = w.getTimeOfDay() / 24000L;
        racers.clear();
        finished = 0;
        java.util.Arrays.fill(rounded, 0);
        for (int i = 0; i < FLEET.length; i++) {
            String[] f = FLEET[i];
            Long id = null;
            BlockPos origin = null;
            for (int tries = 0; tries < 3 && id == null; tries++) {
                origin = new BlockPos(STARTS[i][0], 75, STARTS[i][1] + tries * 12);
                try { id = AiShipController.spawn(w, f[0], origin, SPAWN_MARGIN); }
                catch (Exception e) { net.get900.pixelpirates.PixelPirates.LOGGER.warn("[Regatta] {} could not start at try {}: {}", f[1], tries, e.getMessage()); }
            }
            if (id == null) continue;
            net.get900.pixelpirates.PixelPirates.LOGGER.info("[Regatta] {} spawned at {} (ship {})", f[1], origin.toShortString(), id);
            AiShipController.AiShipData d = AiShipController.AI_SHIPS.get(id);
            if (d != null) {
                d.racing = true; d.raceTarget = null; d.raceSkill = Float.parseFloat(f[4]);
                d.crewSpawned = true;                                            // no faction crew: the skipper sails her
            }
            ShipRegistryState reg = ShipRegistryState.get(w.getServer().getOverworld());
            reg.setUpgradeLevel(id, net.get900.pixelpirates.world.KeelBreaker.KEY, 2);   // she cuts through the shallows
            if (!f[3].isEmpty()) {
                var ship = AiShipController.loaded(w, id);
                var livery = net.get900.pixelpirates.world.livery.Livery.byId(f[3]);
                if (ship != null && livery != null)
                    net.get900.pixelpirates.world.livery.Liveries.repaint(w, ship, net.get900.pixelpirates.world.livery.LiveryState.get(w.getServer()), livery);
            }
            Townsfolk.Folk sk = Townsfolk.get(f[2]);
            Racer r = new Racer(sk != null ? sk.name() + "'s " + f[1] : f[1], id, null);
            r.skipper = f[2];
            r.helm = AiShipController.toShip(w, id, new Vector3d(origin.getX() + 0.5, origin.getY(), origin.getZ() - 0.5));   // behind the helm (bow +Z)
            racers.add(r);
        }
        buoys(w);
        phase = Phase.GATHER;
        TownLife.replanAll(w);                                                // the town to the quay
        for (Racer r : racers) if (r.skipper != null) { TownsfolkEntity e = TownLife.live(w, r.skipper); if (e != null) e.plan = null; }   // skippers to their helms
        phaseAt = w.getTime();
        goAt = w.getTime() + (forced ? 400 : Math.max(200, GO - (int) (w.getTimeOfDay() % 24000L)));
        broadcast(w, "The regatta! " + String.join(", ", racers.stream().map(r -> r.name).toList())
                + " are coming out to the start. Bring your own ship within " + (int) ENTRY_R + " blocks of the start line to race.");
    }

    private static void go(ServerWorld w) {
        ShipRegistryState reg = ShipRegistryState.get(w.getServer().getOverworld());
        for (ServerPlayerEntity p : w.getPlayers()) {
            Long ship = reg.getOwnedShip(p.getUuid());
            Vector3d at = ship == null ? null : AiShipController.shipPos(w, ship);
            if (at == null || Math.hypot(at.x - START.x, at.z - START.z) > ENTRY_R) continue;
            racers.add(new Racer("Captain " + p.getName().getString(), ship, p.getUuid()));
            p.sendMessage(Text.literal("[Regatta] You're racing! Round the far buoy, then the east buoy, then home through the finish gate.").formatted(Formatting.AQUA), false);
        }
        for (Racer r : racers) {
            if (r.player == null) {
                AiShipController.AiShipData d = AiShipController.AI_SHIPS.get(r.ship);
                if (d != null) d.raceTarget = MARKS[0];
            }
            r.lastMove = w.getTime();
        }
        phase = Phase.RACE;
        phaseAt = w.getTime();
        w.playSound(null, BlockPos.ofFloored(START), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.NEUTRAL, 3f, 1.4f);
        broadcast(w, "BANG! And they're off - " + racers.size() + " ships racing!");
    }

    private static void race(ServerWorld w, long now) {
        for (Racer r : racers) {
            if (r.done()) continue;
            Vector3d at = AiShipController.shipPos(w, r.ship);
            if (at == null) { r.retired = true; broadcast(w, r.name + " is out of the race."); continue; }
            Vec3d pos = new Vec3d(at.x, at.y, at.z);
            if (r.lastPos == null || r.lastPos.distanceTo(pos) > 8) { r.lastPos = pos; r.lastMove = now; }
            else if (now - r.lastMove > 1200) { r.retired = true; broadcast(w, r.name + " is stuck fast and retires!"); continue; }
            Vec3d m = MARKS[r.mark];
            boolean last = r.mark == MARKS.length - 1;
            boolean reached = last ? Math.abs(pos.x - m.x) < FINISH_HALF && pos.z <= m.z + 4          // back across the line
                    : Math.hypot(pos.x - m.x, pos.z - m.z) < ROUND_R;
            if (reached) {
                r.mark++;
                if (r.mark >= MARKS.length) {
                    r.place = ++finished;
                    broadcast(w, r.name + " crosses the line " + ordinal(r.place) + "!" + (r.place == 1 ? " What a race!" : ""));
                    w.playSound(null, BlockPos.ofFloored(MARKS[2]), SoundEvents.BLOCK_BELL_USE, SoundCategory.NEUTRAL, 4f, r.place == 1 ? 1f : 0.8f);
                    if (r.player == null) { AiShipController.AiShipData d = AiShipController.AI_SHIPS.get(r.ship); if (d != null) d.raceTarget = null; }
                } else {
                    int nth = ++rounded[r.mark - 1];
                    broadcast(w, r.name + " rounds " + MARK_NAMES[r.mark - 1] + (nth == 1 ? " in the lead!" : " " + ordinal(nth) + "."));
                    if (r.player == null) { AiShipController.AiShipData d = AiShipController.AI_SHIPS.get(r.ship); if (d != null) d.raceTarget = MARKS[r.mark]; }
                }
            }
            if (r.player != null && now % 40 == 0) {
                ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(r.player);
                if (p != null && !r.done()) {
                    Vec3d nm = MARKS[Math.min(r.mark, MARKS.length - 1)];
                    p.sendMessage(Text.literal("[Regatta] " + ordinal(standing(r)) + " - next: " + MARK_NAMES[r.mark] + " (" + (int) Math.hypot(pos.x - nm.x, pos.z - nm.z) + " blocks)").formatted(Formatting.AQUA), true);
                }
            }
        }
        if (racers.stream().allMatch(Racer::done) || now - phaseAt > MAX_RACE) results(w);
    }

    /** Who's ahead: finished first (by place), then the most buoys, then the nearest to their next. */
    private static List<Racer> order(ServerWorld w) {
        List<Racer> l = new ArrayList<>(racers);
        l.sort(Comparator.comparingInt((Racer r) -> r.place > 0 ? r.place - 1000 : 0).thenComparingInt(r -> -r.mark).thenComparingDouble(r -> {
            if (r.mark >= MARKS.length || r.lastPos == null) return 0.0;
            return Math.hypot(r.lastPos.x - MARKS[r.mark].x, r.lastPos.z - MARKS[r.mark].z);
        }));
        return l;
    }

    private static int standing(Racer r) { return order(null).indexOf(r) + 1; }

    private static void results(ServerWorld w) {
        List<Racer> l = order(w);
        StringBuilder sb = new StringBuilder("RESULTS: ");
        int n = 0;
        for (Racer r : l) {
            if (r.place <= 0) continue;
            if (n++ > 0) sb.append(", ");
            sb.append(ordinal(r.place)).append(" ").append(r.name);
        }
        if (n == 0) sb.append("nobody finished!");
        broadcast(w, sb.toString());
        Racer win = l.stream().filter(r -> r.place == 1).findFirst().orElse(null);
        if (win != null) TownMemory.news(w, "regatta", win.name + " won the regatta round the harbour buoys!", "finn");
        int[] purse = {40, 15, 5};
        for (Racer r : l) {
            if (r.player == null || r.place <= 0 || r.place > 3) continue;
            ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(r.player);
            if (p == null) continue;
            p.getInventory().offerOrDrop(new ItemStack(ModItems.COIN, purse[r.place - 1]));
            p.sendMessage(Text.literal("[Regatta] The harbourmaster hands you the " + ordinal(r.place) + " place purse: " + purse[r.place - 1] + " coins.").formatted(Formatting.GOLD), false);
        }
        for (Racer r : racers) if (r.player == null) { AiShipController.AiShipData d = AiShipController.AI_SHIPS.get(r.ship); if (d != null) d.raceTarget = null; }
        phase = Phase.DONE;
        phaseAt = w.getTime();
    }

    private static void cleanup(ServerWorld w) {
        for (int i = 0; i < racers.size(); i++) {                               // the skippers ashore before the boats go
            Racer r = racers.get(i);
            if (r.skipper == null) continue;
            TownsfolkEntity e = TownLife.live(w, r.skipper);
            if (e == null) continue;
            BlockPos q = TownLife.groundAt(w, -6 + i * 6, 95);
            e.requestTeleport(q.getX() + 0.5, q.getY(), q.getZ() + 0.5);
            e.plan = null;
        }
        for (Racer r : racers) if (r.player == null && r.ship != null) AiShipController.despawn(r.ship, w.getServer());
        racers.clear();
        for (Vec3d m : MARKS) TownEvents.clear(w, TAG, BlockPos.ofFloored(m), 40);
        phase = Phase.IDLE;
        TownLife.replanAll(w);
    }

    /** /pptown regatta stop. */
    static String stop(ServerWorld w) {
        if (!active()) return "No regatta on.";
        cleanup(w);
        return "Regatta called off.";
    }

    // ------------------------------------------------------------------ the buoys + the show
    private static void buoys(ServerWorld w) {
        for (int i = 0; i < MARKS.length; i++) {
            Vec3d m = MARKS[i];
            if (i == MARKS.length - 1) {                                       // the finish gate: two chequered posts
                for (int s : new int[]{-42, 42}) buoy(w, m.x + s, m.z, Blocks.WHITE_WOOL.getDefaultState(), Blocks.BLACK_WOOL.getDefaultState());
            } else buoy(w, m.x, m.z, Blocks.RED_WOOL.getDefaultState(), Blocks.WHITE_WOOL.getDefaultState());
        }
    }

    private static void buoy(ServerWorld w, double x, double z, BlockState a, BlockState b) {
        double y = 62.4;
        TownEvents.display(w, a, x, y, z, 1.6f, TAG);
        TownEvents.display(w, b, x, y + 1.6, z, 1.1f, TAG);
        TownEvents.display(w, a, x, y + 2.7, z, 0.7f, TAG);
        TownEvents.display(w, Blocks.LANTERN.getDefaultState(), x, y + 3.4, z, 1f, TAG);
    }

    private static void buoyGlow(ServerWorld w) {
        DustParticleEffect red = new DustParticleEffect(new org.joml.Vector3f(1f, 0.25f, 0.2f), 2f);
        for (int i = 0; i < MARKS.length - 1; i++) w.spawnParticles(red, MARKS[i].x, 67, MARKS[i].z, 6, 0.3, 1.5, 0.3, 0);
    }

    private static void cheer(ServerWorld w) {
        List<TownsfolkEntity> crowd = w.getEntitiesByClass(TownsfolkEntity.class, new net.minecraft.util.math.Box(-60, 60, 88, 60, 75, 145), e -> true);
        if (crowd.isEmpty()) return;
        TownsfolkEntity e = crowd.get(w.random.nextInt(crowd.size()));
        e.triggerAnim("action", w.random.nextBoolean() ? "cheer" : "wave");
    }

    static void broadcast(ServerWorld w, String s) {
        for (ServerPlayerEntity p : w.getPlayers())
            p.sendMessage(Text.literal("[Regatta] ").formatted(Formatting.AQUA).append(Text.literal(s).formatted(Formatting.WHITE)), false);
    }

    private static String ordinal(int n) { return n + (n % 100 / 10 == 1 ? "th" : switch (n % 10) { case 1 -> "st"; case 2 -> "nd"; case 3 -> "rd"; default -> "th"; }); }
}
