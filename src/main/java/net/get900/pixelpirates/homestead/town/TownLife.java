package net.get900.pixelpirates.homestead.town;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.homestead.HomesteadEntities;
import net.get900.pixelpirates.homestead.art.Art;
import net.get900.pixelpirates.homestead.art.CustomPaintingEntity;
import net.get900.pixelpirates.homestead.art.EaselBlock;
import net.get900.pixelpirates.homestead.art.EaselBlockEntity;
import net.get900.pixelpirates.homestead.chess.Chess;
import net.get900.pixelpirates.homestead.furniture.SeatBlock;
import net.get900.pixelpirates.homestead.furniture.SeatEntity;
import net.get900.pixelpirates.homestead.trade.PortTraders;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;

import java.util.*;

/**
 * THE LIFE OF THE TOWN (homestead/town). Keeps exactly one of every Townsfolk character in the world (spawned near a
 * player the first time, respawned if lost, duplicates retire themselves) and gives each their PLAN for the part of the
 * day they're in:
 *   SLEEP  - their own bed;            HOME - pottering about the house (finished paintings get hung on the walls)
 *   WORK   - their spot, by WorkStyle (stand, wander round it, drink at the tavern, paint at an easel, play chess)
 *   LEISURE - one of their HOBBIES, picked fresh each day: a stool in the Grog Barrel with a tankard (DRINK), a place at
 *            the dice / crown-and-anchor / roulette tables in the den (GAMBLE), a game of chess with another townsperson
 *            at the toymaker's table or the giant set on the Chess Green - a real game, played by the chess engine under
 *            their names (CHESS), an easel - in the Gardens or the painter's studio - where a painting of their own fills
 *            in row by row and goes home with them (PAINT), a pew in the chapel (PRAY), or a stroll (STROLL).
 * Seats, table places, easels and boards are reserved so two never share one; players come first everywhere.
 * /pptown list | spawn | summon <id> | tp <id> | reset
 */
public final class TownLife {
    private TownLife() {}

    /** Where someone is going and what they'll do there. */
    public static final class Plan {
        Townsfolk.Phase phase;
        TownsfolkEntity.Act act = TownsfolkEntity.Act.IDLE;
        BlockPos stand;
        BlockPos seat, bed, board, easel, dartboard;
        Vec3d look;
        int side, wander;
        BlockPos wanderCentre;
        int[] strollArea;
        int[][] route;
        boolean challenge;

        Plan(Townsfolk.Phase phase, BlockPos stand) { this.phase = phase; this.stand = stand; }
    }

    // ------------------------------------------------------------------ places
    private static final int[] TAVERN = {42, 66, 15, 83, 80, 46};
    private static final int[] CHAPEL = {-40, 68, -88, -12, 80, -47};
    private static final int[][] EASEL_BOXES = {{95, 68, -84, 109, 80, -49}, {-91, 68, -43, -55, 76, -12}};
    private static final Vec3d ALTAR = new Vec3d(-25, 72, -78);
    static final int[] INN_BOX = {-77, 66, 13, -42, 82, 44};
    private static final int[] GUARDHOUSE = {-55, 66, -41, -5, 85, -13};
    private static final BlockPos INN_HALL = new BlockPos(-60, 68, 28);
    private static final int[] PARK = {-91, 68, -43, -55, 76, -12};
    private static final int[] PARK_LAWNS = {-88, -40, -58, -15};
    private static final BlockPos ORGAN = new BlockPos(-19, 71, -78), LECTERN_STAND = new BlockPos(-28, 73, -76);
    private static final Vec3d PEWS = new Vec3d(-30, 72, -65);
    /** Stroll grounds: x1, z1, x2, z2. */
    private static final int[][] STROLLS = {{-8, -8, 8, 8}, {-88, -40, -58, -15}, {-60, 82, 60, 90}, {-36, -124, -14, -106},
            {25, -138, 66, -122}, {-30, -145, -5, -125}};

    /** The chess boards: pos, then the white and black places (a stool to sit on at the table, a spot to stand at the giant set). */
    private record Board(BlockPos pos, BlockPos white, BlockPos black, boolean sit, Vec3d centre) {}

    private static final List<Board> BOARDS = List.of(
            new Board(new BlockPos(101, 71, -30), new BlockPos(100, 71, -30), new BlockPos(102, 71, -30), true, new Vec3d(101.5, 71.5, -29.5)),
            new Board(new BlockPos(-30, 79, -118), new BlockPos(-31, 79, -114), new BlockPos(-20, 79, -114), false, new Vec3d(-25.5, 79, -114.5)));

    // ------------------------------------------------------------------ reservations (live only - rebuilt from the plans)
    private static final Map<BlockPos, String> TAKEN = new HashMap<>();
    private static final Map<BlockPos, String> WAITING = new HashMap<>();
    private static final Map<BlockPos, Long> WAITING_SINCE = new HashMap<>();
    private static final Map<BlockPos, String[]> GAMES = new HashMap<>();
    private static final Map<String, List<BlockPos>> VENUES = new HashMap<>();
    private static final Map<String, Integer> MISSING = new HashMap<>();
    /** /pptown force <id> <hobby>: their leisure goes to this (testing; forgotten on restart). */
    private static final Map<String, Townsfolk.Hobby> FORCED = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(w -> {
            if (!w.getRegistryKey().equals(PortTraders.DIM)) return;
            if (w.getTime() % 5 == 0) Regatta.tick(w);                       // the ships move fast: buoys are checked often
            if (w.getTime() % 40 != 21) return;
            keepAlive(w, false);
            chessTimeouts(w);
            TownEvents.tick(w);
            gulls(w);
            watchChallenges(w);
            Garrison.tick(w);
            ChessLeague.tick(w);
            FishingContest.tick(w);
        });
        // a captain lost at sea: their name on the memorial roll + a memorial service in the chapel next morning
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof ServerPlayerEntity p) || !p.getWorld().getRegistryKey().equals(PortTraders.DIM)) return;
            boolean drowned = source.isOf(net.minecraft.entity.damage.DamageTypes.DROWN);
            boolean sea = drowned || p.isTouchingWater();
            if (!sea) {
                BlockPos.Mutable m = p.getBlockPos().mutableCopy();
                for (int i = 0; i < 8 && p.getWorld().getBlockState(m).isAir(); i++) m.move(Direction.DOWN);
                sea = p.getWorld().getFluidState(m).isIn(net.minecraft.registry.tag.FluidTags.WATER);
            }
            if (sea) TownEvents.lostAtSea(p, drowned ? "drowned at sea" : "lost at sea");
        });
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(CommandManager.literal("pptown").requires(s -> s.hasPermissionLevel(2))
                .then(CommandManager.literal("event").then(CommandManager.argument("what", StringArgumentType.word())
                        .suggests((c, b) -> { for (String s : new String[]{"festival", "wedding", "memorial", "party", "fishing", "regatta"}) b.suggest(s); return b.buildFuture(); })
                        .executes(c -> {
                            ServerWorld w = c.getSource().getServer().getWorld(PortTraders.DIM);
                            String what = StringArgumentType.getString(c, "what");
                            if (w == null) return 0;
                            if (what.equals("regatta")) { String r = Regatta.force(w); c.getSource().sendFeedback(() -> Text.literal(r), false); return 1; }
                            if (what.equals("fishing")) { String r = FishingContest.force(w); c.getSource().sendFeedback(() -> Text.literal(r), false); return 1; }
                            if (what.equals("party") && c.getSource().getPlayer() != null) TownEvents.bossKilled(c.getSource().getPlayer(), "a test monster");
                            else TownEvents.force(w, what);
                            c.getSource().sendFeedback(() -> Text.literal("Started: " + what), false);
                            return 1;
                        })))
                .then(CommandManager.literal("friend").then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests((c, b) -> { Townsfolk.ALL.keySet().forEach(b::suggest); return b.buildFuture(); })
                        .executes(c -> {
                            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                            Townsfolk.Folk f = Townsfolk.get(StringArgumentType.getString(c, "id"));
                            if (f == null) return 0;
                            TownMemory.befriend(p, f, 30);
                            c.getSource().sendFeedback(() -> Text.literal(f.name() + ": " + TownMemory.score(p, f.id())), false);
                            return 1;
                        })))
                .then(CommandManager.literal("list").executes(c -> list(c.getSource())))
                .then(CommandManager.literal("spawn").executes(c -> {
                    ServerWorld w = c.getSource().getServer().getWorld(PortTraders.DIM);
                    int n = w == null ? 0 : keepAlive(w, true);
                    c.getSource().sendFeedback(() -> Text.literal("Spawned " + n + " townsfolk (only where the chunks are loaded)."), false);
                    return n;
                }))
                .then(CommandManager.literal("summon").then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests((c, b) -> { Townsfolk.ALL.keySet().forEach(b::suggest); return b.buildFuture(); })
                        .executes(c -> summon(c.getSource(), StringArgumentType.getString(c, "id")))))
                .then(CommandManager.literal("tp").then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests((c, b) -> { Townsfolk.ALL.keySet().forEach(b::suggest); return b.buildFuture(); })
                        .executes(c -> tp(c.getSource(), StringArgumentType.getString(c, "id")))))
                .then(CommandManager.literal("force").then(CommandManager.argument("id", StringArgumentType.word())
                        .suggests((c, b) -> { Townsfolk.ALL.keySet().forEach(b::suggest); b.suggest("all"); return b.buildFuture(); })
                        .then(CommandManager.argument("hobby", StringArgumentType.word())
                                .suggests((c, b) -> { for (Townsfolk.Hobby h : Townsfolk.Hobby.values()) b.suggest(h.name().toLowerCase()); return b.buildFuture(); })
                                .executes(c -> force(c.getSource(), StringArgumentType.getString(c, "id"), StringArgumentType.getString(c, "hobby"))))))
                .then(CommandManager.literal("darts").then(CommandManager.argument("a", StringArgumentType.word())
                        .suggests((c, b) -> { Townsfolk.ALL.keySet().forEach(b::suggest); return b.buildFuture(); })
                        .then(CommandManager.argument("b", StringArgumentType.word())
                                .suggests((c, b) -> { Townsfolk.ALL.keySet().forEach(b::suggest); return b.buildFuture(); })
                                .executes(c -> {
                                    String r = TownDarts.match(c.getSource().getWorld(), c.getSource().getPosition(),
                                            StringArgumentType.getString(c, "a"), StringArgumentType.getString(c, "b"));
                                    c.getSource().sendFeedback(() -> Text.literal(r), false);
                                    return 1;
                                }))))
                .then(CommandManager.literal("regatta").then(CommandManager.literal("stop").executes(c -> {
                    String r = Regatta.stop(c.getSource().getWorld()); c.getSource().sendFeedback(() -> Text.literal(r), false); return 1; })))
                .then(CommandManager.literal("league")
                        .then(CommandManager.literal("start").executes(c -> { String r = ChessLeague.start(c.getSource().getWorld()); c.getSource().sendFeedback(() -> Text.literal(r), false); return 1; }))
                        .then(CommandManager.literal("stop").executes(c -> { String r = ChessLeague.stop(c.getSource().getWorld()); c.getSource().sendFeedback(() -> Text.literal(r), false); return 1; })))
                .then(CommandManager.literal("service").executes(c -> {
                    ServerWorld w = c.getSource().getServer().getWorld(PortTraders.DIM);
                    if (w == null) return 0;
                    forcedService = w.getTime();
                    c.getSource().sendFeedback(() -> Text.literal("The chapel service begins (two minutes)."), false);
                    return 1;
                }))
                .then(CommandManager.literal("reset").executes(c -> reset(c.getSource())))));
    }

    // ------------------------------------------------------------------ keeping everyone alive
    static State state(MinecraftServer s) {
        return s.getOverworld().getPersistentStateManager().getOrCreate(State::read, State::new, "pixelpirates_town");
    }

    /** The live entity of a townsperson, if loaded. */
    static TownsfolkEntity live(ServerWorld w, String id) {
        UUID u = state(w.getServer()).owner.get(id);
        Entity e = u == null ? null : w.getEntity(u);
        return e instanceof TownsfolkEntity t ? t : null;
    }

    static boolean owns(ServerWorld w, TownsfolkEntity e) {
        UUID u = state(w.getServer()).owner.get(e.folkId());
        return e.getUuid().equals(u);
    }

    /** One of each: find, track, and (re)spawn the missing near a player. force = /pptown spawn (no player needed). */
    private static int keepAlive(ServerWorld w, boolean force) {
        State st = state(w.getServer());
        int spawned = 0;
        for (Townsfolk.Folk f : Townsfolk.ALL.values()) {
            UUID u = st.owner.get(f.id());
            Entity e = u == null ? null : w.getEntity(u);
            if (e instanceof TownsfolkEntity t && t.isAlive()) {
                st.last.put(f.id(), t.getBlockPos().asLong());
                MISSING.remove(f.id());
                continue;
            }
            if (!TownEvents.present(w, f)) continue;                                                              // Marco: market days only
            Long last = st.last.get(f.id());
            if (u != null && last != null && !w.isChunkLoaded(ChunkPos.toLong(BlockPos.fromLong(last)))) continue;   // asleep in an unloaded chunk
            if (u != null && !force && MISSING.merge(f.id(), 1, Integer::sum) < 4) continue;                       // entities load after chunks
            Townsfolk.Phase ph = Townsfolk.phase(f, w.getTimeOfDay());
            BlockPos p = ph == Townsfolk.Phase.WORK ? new BlockPos(f.work()[0], f.work()[1], f.work()[2]) : home(w, f);
            if (!w.isChunkLoaded(ChunkPos.toLong(p))) continue;
            if (!force && !watched(w, Vec3d.ofCenter(p), 96)) continue;
            TownsfolkEntity t = HomesteadEntities.TOWNSFOLK.create(w);
            if (t == null) continue;
            BlockPos s = standNear(w, p, 5);
            t.setFolk(f);
            t.refreshPositionAndAngles(s.getX() + 0.5, s.getY(), s.getZ() + 0.5, w.random.nextFloat() * 360f, 0);
            t.initialize(w, w.getLocalDifficulty(s), SpawnReason.STRUCTURE, null, null);
            if (!w.spawnEntity(t)) continue;
            st.owner.put(f.id(), t.getUuid());
            st.last.put(f.id(), s.asLong());
            st.markDirty();
            MISSING.remove(f.id());
            spawned++;
        }
        if (spawned > 0) PixelPirates.LOGGER.info("[Town] {} townsfolk spawned", spawned);
        return spawned;
    }

    static boolean watched(ServerWorld w, Vec3d pos, double r) {
        for (ServerPlayerEntity p : w.getPlayers()) if (!p.isSpectator() && p.squaredDistanceTo(pos) < r * r) return true;
        return false;
    }

    // ------------------------------------------------------------------ plans
    static Plan plan(ServerWorld w, TownsfolkEntity e, Townsfolk.Folk f, Townsfolk.Phase phase, TownEvents.Mode mode) {
        BlockPos home = home(w, f), work = new BlockPos(f.work()[0], f.work()[1], f.work()[2]);
        Plan league = ChessLeague.plan(w, f.id(), phase);                       // a tournament game (even past bedtime)
        if (league != null) return league;
        Plan contest = FishingContest.plan(w, f.id(), phase);                   // Finn's fishing contest: on the quay all day
        if (contest != null) return contest;
        Plan regatta = Regatta.plan(w, f, phase);                              // regatta afternoon: on the quay to watch
        if (regatta != null) return regatta;
        Challenge ch = CHALLENGES.get(f.id());
        if (ch != null) return challengePlan(w, phase, ch);
        Plan darts = TownDarts.plan(w, e, phase);                              // at a dartboard (homestead/darts)
        if (darts != null) return darts;
        if (mode != null && mode != TownEvents.Mode.NONE) {
            Plan p = eventPlan(w, e, f, phase, mode);
            if (p != null) return p;
        }
        switch (phase) {
            case SERVICE -> {
                Plan p = servicePlan(w, e, f, phase);
                if (p != null) return p;
                p = new Plan(phase, standNear(w, new BlockPos(-26, 71, -62), 4));
                p.look = ALTAR;
                return p;
            }
            case SLEEP -> {
                BlockPos bed = findBed(w, home);
                Plan p = new Plan(phase, standNear(w, bed != null ? bed : home, 3));
                if (bed != null) { p.bed = bed; p.act = TownsfolkEntity.Act.SLEEP; }
                return p;
            }
            case HOME -> {
                Plan p = new Plan(phase, standNear(w, home, 4));
                p.wander = 3;
                return p;
            }
            case WORK -> {
                Plan p = switch (f.style()) {
                    case DRINK -> seatPlan(w, e, phase, TAVERN, TownsfolkEntity.Act.DRINK, null);
                    case PAINT -> easelPlan(w, e, phase, new int[]{work.getX() - 8, work.getY() - 4, work.getZ() - 8, work.getX() + 8, work.getY() + 6, work.getZ() + 8});
                    case CHESS -> chessPlan(w, e, phase, work);
                    case ORGAN -> organPlan(w, phase);
                    case INN_SIT -> seatPlan(w, e, phase, INN_BOX, TownsfolkEntity.Act.SIT, null);
                    case PLAY -> playPlan(w, e, phase);
                    case ROUNDS, CRIER -> routePlan(w, f, phase);
                    case FISH -> fishPlan(w, phase, work);
                    case MARKET -> stallPlan(w, phase);
                    default -> null;
                };
                if (p != null) return p;
                p = new Plan(phase, standNear(w, work, 4));
                p.act = TownsfolkEntity.Act.WORK;
                if (f.style() != Townsfolk.WorkStyle.STAND) { p.wander = 6; p.wanderCentre = work; }
                return p;
            }
            default -> {
                List<Townsfolk.Hobby> hs = new ArrayList<>(f.hobbies());
                if (FORCED.containsKey(f.id())) { hs.remove(FORCED.get(f.id())); hs.add(0, FORCED.get(f.id())); }
                long day = w.getTimeOfDay() / 24000L;
                if (hs.isEmpty()) return strollPlan(w, f, phase, day);                   // no hobbies (Lazlo): a wander
                int start = FORCED.containsKey(f.id()) ? 0 : Math.floorMod(Objects.hash(f.id(), day), hs.size());
                for (int i = 0; i < hs.size(); i++) {
                    Plan p = switch (hs.get((start + i) % hs.size())) {
                        case DRINK -> TownEvents.musicNight(w) && e.getRandom().nextInt(3) == 0 ? dancePlan(w, phase, TownEvents.STAGE, 2, 4)
                                : seatPlan(w, e, phase, TAVERN, TownsfolkEntity.Act.DRINK, null);
                        case GAMBLE -> { Plan q = TownDarts.leisure(w, e, phase); yield q != null ? q : gamblePlan(w, e, phase); }
                        case CHESS -> chessPlan(w, e, phase, null);
                        case PAINT -> { Plan q = null; for (int[] b : EASEL_BOXES) if (q == null) q = easelPlan(w, e, phase, b); yield q; }
                        case PRAY -> seatPlan(w, e, phase, CHAPEL, TownsfolkEntity.Act.PRAY, ALTAR);
                        case STROLL -> strollPlan(w, f, phase, day);
                        case PLAY -> playPlan(w, e, phase);
                    };
                    if (p != null) return p;
                }
                return strollPlan(w, f, phase, day);
            }
        }
    }

    // ------------------------------------------------------------------ round 3: events, fishing, music, the choir, children, gulls, lamps
    private static Plan eventPlan(ServerWorld w, TownsfolkEntity e, Townsfolk.Folk f, Townsfolk.Phase phase, TownEvents.Mode mode) {
        switch (mode) {
            case WEDDING -> {
                String[] c = TownEvents.wedding(w);
                if (c != null && (f.id().equals(c[3]) || f.id().equals(c[4]))) {
                    Plan p = new Plan(phase, standNear(w, BlockPos.ofFloored(TownEvents.ALTAR_FRONT.add(f.id().equals(c[3]) ? -1 : 1, 0, 0)), 1));
                    p.look = ALTAR;
                    return p;
                }
                Plan p = servicePlan(w, e, f, phase);
                if (p != null) return p;
                p = new Plan(phase, standNear(w, new BlockPos(-26, 71, -62), 4));
                p.look = ALTAR;
                return p;
            }
            case MEMORIAL -> { return servicePlan(w, e, f, phase); }
            case FESTIVAL -> {
                BlockPos c = groundAt(w, TownEvents.PLAZA.getX(), TownEvents.PLAZA.getZ());
                if (f.id().equals("dan")) {
                    Plan p = new Plan(phase, standNear(w, c, 2));
                    p.act = TownsfolkEntity.Act.FIDDLE; p.look = Vec3d.ofCenter(c.south(4));
                    return p;
                }
                return dancePlan(w, phase, c, 3, 8);
            }
            case MUSIC -> {
                Plan p = new Plan(phase, standNear(w, TownEvents.STAGE, 2));
                p.act = TownsfolkEntity.Act.FIDDLE; p.look = new Vec3d(60, 68, 30);
                return p;
            }
            case RAIN -> {
                Plan p = e.getRandom().nextBoolean() ? seatPlan(w, e, phase, TAVERN, TownsfolkEntity.Act.DRINK, null)
                        : seatPlan(w, e, phase, INN_BOX, TownsfolkEntity.Act.SIT, null);
                if (p != null) return p;
                p = new Plan(phase, standNear(w, INN_HALL, 5));
                p.wander = 2;
                return p;
            }
            default -> { return null; }
        }
    }

    /** A spot in a ring round `c` to dance (or stand and clap along). */
    private static Plan dancePlan(ServerWorld w, Townsfolk.Phase phase, BlockPos c, int r1, int r2) {
        double a = w.random.nextDouble() * Math.PI * 2, r = r1 + w.random.nextDouble() * (r2 - r1);
        BlockPos s = standNear(w, c.add((int) Math.round(Math.cos(a) * r), 0, (int) Math.round(Math.sin(a) * r)), 2);
        Plan p = new Plan(phase, s);
        p.act = w.random.nextInt(5) < 3 ? TownsfolkEntity.Act.DANCE : TownsfolkEntity.Act.IDLE;
        p.look = Vec3d.ofCenter(c);
        return p;
    }

    private static Plan fishPlan(ServerWorld w, Townsfolk.Phase phase, BlockPos work) {
        Plan p = new Plan(phase, standNear(w, work, 4));
        p.act = TownsfolkEntity.Act.FISH;
        p.look = Vec3d.ofCenter(work.south(6)).add(0, -1, 0);
        return p;
    }

    private static Plan stallPlan(ServerWorld w, Townsfolk.Phase phase) {
        BlockPos s = TownEvents.stallPos(w);
        Plan p = new Plan(phase, standNear(w, s.north(), 1));
        p.act = TownsfolkEntity.Act.WORK;
        p.look = Vec3d.ofCenter(s.south(3));
        return p;
    }

    /** Fiddler Dan plays: the shanty and our own jig, turn about. */
    static void fiddle(ServerWorld w, TownsfolkEntity e) {
        boolean jig = (e.age / 2400) % 2 == 1;
        TownMusic.play(w, "fiddle", e.getPos(), jig ? TownMusic.SALT_AND_THUNDER : TownMusic.DRUNKEN_SAILOR,
                net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BANJO.value(), 1.6f);
    }

    /** Finn on the pier: the float bobs, now and then a fish comes up (and the town hears about the best one). */
    static void fishStep(ServerWorld w, TownsfolkEntity e) {
        Plan p = e.plan;
        if (p == null || p.look == null) return;
        if (e.age % 80 < 10) w.spawnParticles(net.minecraft.particle.ParticleTypes.FISHING, p.look.x, p.look.y + 1, p.look.z, 6, 0.2, 0, 0.2, 0.02);
        if (e.age % 1200 < 10 && w.random.nextInt(2) == 0) {
            w.spawnParticles(net.minecraft.particle.ParticleTypes.SPLASH, p.look.x, p.look.y + 1, p.look.z, 30, 0.4, 0.1, 0.4, 0.1);
            w.playSound(null, BlockPos.ofFloored(p.look), net.minecraft.sound.SoundEvents.ENTITY_FISHING_BOBBER_SPLASH, net.minecraft.sound.SoundCategory.NEUTRAL, 1f, 1f);
            e.triggerAnim("action", "cheer");
            String[] fish = {"a fat cod", "a silver salmon", "a moonfish that glowed like a lantern", "a red snapper", "a lobster the size of a dog", "an old boot - and then a mahi-mahi"};
            TownMemory.news(w, "catch", "Finn Gale landed " + fish[w.random.nextInt(fish.length)] + " off the pier!", "finn");
        }
    }

    /** The choir: while the organ plays a hymn, a few in the pews sing along. */
    static void choir(ServerWorld w, TownsfolkEntity e) {
        boolean sing = TownMusic.playing("organ") && Math.floorMod(e.folkId().hashCode(), 3) == 0;
        e.setSinging(sing);
        if (sing && e.age % 20 < 10) w.spawnParticles(net.minecraft.particle.ParticleTypes.NOTE, e.getX(), e.getY() + 1.6, e.getZ(), 0, w.random.nextDouble(), 0, 0, 1);
    }

    /** Friends get a wave as they pass. */
    static void greetFriends(ServerWorld w, TownsfolkEntity e) {
        if (--e.waveCool > 0) return;
        e.waveCool = 6;
        ServerPlayerEntity p = (ServerPlayerEntity) w.getClosestPlayer(e, 6);
        if (p == null || p.isSpectator() || TownMemory.level(p, e.folkId()) < 2) return;
        e.getLookControl().lookAt(p, 30f, 30f);
        e.triggerAnim("action", "wave");
        e.waveCool = 60;
    }

    // ---- the children: tag round the Gardens, and running up to ask a captain about their ship
    private static String tagIt = "";
    private static final String[] ASK = {"Is that your ship in the harbour? Is it the big one? Can I see the cannons?",
            "Have you fought a sea monster? A real one? How big was it?", "Are you a real pirate? Say 'arr'! Go on!",
            "Can I be in your crew when I'm bigger? I can climb really well.", "Did you find any treasure today? Show me!"};

    static void kidStep(ServerWorld w, TownsfolkEntity e) {
        if (--e.kidCool <= 0) {
            ServerPlayerEntity p = (ServerPlayerEntity) w.getClosestPlayer(e, 10);
            if (p != null && !p.isSpectator() && w.random.nextInt(3) == 0) {
                e.kidCool = 120;
                e.getNavigation().startMovingTo(p, 0.7);
                p.sendMessage(Text.literal("[" + e.name().split(" ")[0] + "] ").formatted(Formatting.AQUA).append(Text.literal(ASK[w.random.nextInt(ASK.length)])), false);
                e.triggerAnim("action", "wave");
                return;
            }
            e.kidCool = 12;
        }
        Plan pl = e.plan;
        if (pl == null || pl.strollArea != PARK_LAWNS) return;
        List<TownsfolkEntity> kids = w.getEntitiesByClass(TownsfolkEntity.class, e.getBoundingBox().expand(14),
                k -> k != e && Townsfolk.scale(k.folkId()) < 1 && !k.hasVehicle() && !k.isSleeping());
        if (kids.isEmpty()) return;
        if (tagIt.isEmpty() || kids.stream().noneMatch(k -> k.folkId().equals(tagIt)) && !tagIt.equals(e.folkId())) tagIt = e.folkId();
        kids.removeIf(k -> !inLawns(k.getPos()));
        if (kids.isEmpty() || !inLawns(e.getPos())) return;
        if (tagIt.equals(e.folkId())) {                                                 // "it": chase the nearest
            TownsfolkEntity t = kids.get(0);
            for (TownsfolkEntity k : kids) if (k.squaredDistanceTo(e) < t.squaredDistanceTo(e)) t = k;
            if (t.squaredDistanceTo(e) < 2.25) { tagIt = t.folkId(); e.triggerAnim("action", "cheer"); t.triggerAnim("action", "laugh"); }
            else e.getNavigation().startMovingTo(t, 0.75);
        } else {
            for (TownsfolkEntity k : kids)
                if (k.folkId().equals(tagIt) && k.squaredDistanceTo(e) < 36) {               // run!
                    Vec3d away = e.getPos().add(e.getPos().subtract(k.getPos()).normalize().multiply(6));
                    int ax = (int) Math.max(PARK_LAWNS[0], Math.min(PARK_LAWNS[2], away.x)), az = (int) Math.max(PARK_LAWNS[1], Math.min(PARK_LAWNS[3], away.z));
                    BlockPos to = groundAt(w, ax, az);                                     // never out of the Gardens
                    e.getNavigation().startMovingTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 0.75);
                }
        }
    }

    private static boolean inLawns(Vec3d p) { return p.x >= PARK_LAWNS[0] - 2 && p.x <= PARK_LAWNS[2] + 2 && p.z >= PARK_LAWNS[1] - 2 && p.z <= PARK_LAWNS[3] + 2; }

    // ---- the gulls (homestead/town/SeagullEntity): a handful over the harbour and the plaza; Agnes's crumbs bring them down
    private static final Box GULL_BOX = new Box(-110, 40, -60, 110, 150, 170);

    private static void gulls(ServerWorld w) {
        if (!watched(w, new Vec3d(0, 70, 50), 110)) return;
        int n = w.getEntitiesByClass(SeagullEntity.class, GULL_BOX, g -> true).size();
        if (n >= 8) return;
        double x = -60 + w.random.nextInt(120), z = 10 + w.random.nextInt(110);
        if (!w.isChunkLoaded(BlockPos.ofFloored(x, 70, z))) return;
        SeagullEntity g = HomesteadEntities.SEAGULL.create(w);
        if (g == null) return;
        g.refreshPositionAndAngles(x, 92, z, w.random.nextFloat() * 360, 0);
        g.setHome(new Vec3d(w.random.nextBoolean() ? 0 : -10 + w.random.nextInt(20), 74, 40 + w.random.nextInt(50)));
        w.spawnEntity(g);
    }

    static void feedGulls(ServerWorld w, Vec3d at) {
        for (SeagullEntity g : w.getEntitiesByClass(SeagullEntity.class, new Box(BlockPos.ofFloored(at)).expand(40, 30, 40), g -> true)) g.feed(at);
        w.spawnParticles(new net.minecraft.particle.ItemStackParticleEffect(net.minecraft.particle.ParticleTypes.ITEM, new net.minecraft.item.ItemStack(net.minecraft.item.Items.BREAD)),
                at.x, at.y + 1, at.z, 12, 1.2, 0.2, 1.2, 0.05);
    }

    // ---- THE STREET LAMPS: Ginny lights every lamp near her dusk round (and turns old lamppost lanterns into street lamps)
    static void lightLamps(ServerWorld w, BlockPos at) {
        int t = (int) Math.floorMod(w.getTimeOfDay(), 24000L);
        if (t < 10500 || t > 16000) return;
        BlockPos.Mutable m = new BlockPos.Mutable();
        BlockState lit = net.get900.pixelpirates.homestead.HomesteadBlocks.STREET_LAMP.getDefaultState().with(StreetLampBlock.LIT, true);
        for (int dx = -16; dx <= 16; dx++)
            for (int dz = -16; dz <= 16; dz++)
                for (int dy = -4; dy <= 8; dy++) {
                    BlockState s = w.getBlockState(m.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz));
                    boolean lamp = s.getBlock() instanceof StreetLampBlock && !s.get(StreetLampBlock.LIT);
                    boolean old = s.isOf(net.minecraft.block.Blocks.LANTERN) && !s.get(net.minecraft.block.LanternBlock.HANGING)
                            && w.getBlockState(m.down()).getBlock() instanceof net.minecraft.block.FenceBlock
                            && w.getBlockState(m.down(2)).getBlock() instanceof net.minecraft.block.FenceBlock;
                    if (!lamp && !old) continue;
                    w.setBlockState(m, lamp ? s.with(StreetLampBlock.LIT, true) : lit, 3);
                    w.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME, m.getX() + 0.5, m.getY() + 0.5, m.getZ() + 0.5, 4, 0.1, 0.1, 0.1, 0.01);
                }
    }

    // ---- ships in the harbour (HarbourDues tells us where the moored ones are)
    static final List<Vec3d> SHIPS = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void harbourShips(ServerWorld w, List<Vec3d> at, List<String> owners) {
        SHIPS.clear();
        SHIPS.addAll(at);
        for (String o : owners) TownMemory.news(w, "ship", "Captain " + o + "'s ship is moored in the harbour - half the town has been down to the quay to look at her!", o);
    }

    // ---- CHESS CHALLENGES: a player asks a townsperson for a game
    record Challenge(String folk, java.util.UUID player, Board board, long started) {}

    private static final Map<String, Challenge> CHALLENGES = new HashMap<>();

    static boolean challenged(TownsfolkEntity e) {
        return CHALLENGES.containsKey(e.folkId()) || TownDarts.claimed(e.folkId()) || ChessLeague.playing(e.folkId()) || Regatta.skipper(e.folkId());
    }

    static boolean canChallenge(Townsfolk.Folk f) { return f.hobbies().contains(Townsfolk.Hobby.CHESS) || f.style() == Townsfolk.WorkStyle.CHESS; }

    /** Returns what the townsperson says. */
    static String challenge(ServerPlayerEntity p, TownsfolkEntity e) {
        ServerWorld w = p.getServerWorld();
        Townsfolk.Folk f = e.folk();
        if (f == null) return "";
        for (Challenge c : CHALLENGES.values()) if (c.player.equals(p.getUuid())) return "One game at a time, captain! Go and finish your other one.";
        Board pick = null;
        for (Board b : BOARDS)
            if (w.isChunkLoaded(b.pos) && !WAITING.containsKey(b.pos) && !GAMES.containsKey(b.pos) && Chess.idle(w, b.pos)
                    && CHALLENGES.values().stream().noneMatch(c -> c.board == b)
                    && (pick == null || b.pos.getSquaredDistance(p.getBlockPos()) < pick.pos.getSquaredDistance(p.getBlockPos()))) pick = b;
        if (pick == null) return "Both boards are busy just now. Ask me again later.";
        release(w, e);
        CHALLENGES.put(f.id(), new Challenge(f.id(), p.getUuid(), pick, w.getTime()));
        Chess.npcStartVs(w, pick.pos, f.name(), level(f.id()));
        e.plan = null;
        return "A game? Gladly! Meet me at " + (pick.sit ? "the toymaker's chess table" : "the giant board on the Chess Green")
                + " - you take white. " + (f.id().equals("pettigrew") ? "Beat me and there's a trophy in it for you. You won't." : "Don't keep me waiting!");
    }

    private static Plan challengePlan(ServerWorld w, Townsfolk.Phase phase, Challenge c) {
        Board b = c.board;
        Plan p = new Plan(phase, standNear(w, b.black, 2));
        p.board = b.pos; p.side = 1; p.look = b.centre; p.challenge = true;
        p.act = b.sit ? TownsfolkEntity.Act.CHESS_SIT : TownsfolkEntity.Act.CHESS_STAND;
        if (b.sit) p.seat = b.black;
        return p;
    }

    private static void watchChallenges(ServerWorld w) {
        for (java.util.Iterator<Challenge> it = CHALLENGES.values().iterator(); it.hasNext(); ) {
            Challenge c = it.next();
            Townsfolk.Folk f = Townsfolk.get(c.folk);
            ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(c.player);
            String r = Chess.result(w, c.board.pos);
            boolean seated = c.player.equals(Chess.seated(w, c.board.pos, 0));
            boolean timeout = !seated && w.getTime() - c.started > 3600;
            if (r.isEmpty() && !timeout && Chess.npcPlaying(w, c.board.pos, f.name())) continue;
            it.remove();
            Chess.npcStop(w, c.board.pos, f.name());
            UUID u = state(w.getServer()).owner.get(c.folk);
            if (u != null && w.getEntity(u) instanceof TownsfolkEntity t) t.plan = null;
            if (p == null) continue;
            if (timeout) { p.sendMessage(Text.literal("[" + f.name() + "] You never came to the board! Another time, then.").formatted(Formatting.GRAY), false); continue; }
            if (r.isEmpty()) continue;
            int win = Chess.winner(w, c.board.pos);
            if (win == 0) {
                TownMemory.befriend(p, f, 10);
                TownMemory.news(w, "chess", "Captain " + p.getName().getString() + " beat " + f.name() + " at chess!", c.folk);
                p.sendMessage(Text.literal("[" + f.name() + "] Well played, captain. Well played indeed.").formatted(Formatting.GOLD), false);
                TownMemory m = TownMemory.get(w.getServer());
                if (c.folk.equals("pettigrew") && m.trophies.add(p.getUuid())) {
                    m.markDirty();
                    p.getInventory().offerOrDrop(new net.minecraft.item.ItemStack(net.get900.pixelpirates.homestead.HomesteadBlocks.CHESS_TROPHY));
                    p.sendMessage(Text.literal("[*] The Commodore presents you with his Chess Trophy. \"I shall want a rematch.\"").formatted(Formatting.GOLD), false);
                }
            } else if (win == 1) {
                TownMemory.befriend(p, f, 4);
                TownMemory.news(w, "chess", f.name() + " beat Captain " + p.getName().getString() + " at chess!", c.folk);
                p.sendMessage(Text.literal("[" + f.name() + "] Checkmate! Don't feel bad - I've had practice.").formatted(Formatting.GOLD), false);
            } else {
                TownMemory.befriend(p, f, 6);
                p.sendMessage(Text.literal("[" + f.name() + "] A draw! A fair result between equals.").formatted(Formatting.GOLD), false);
            }
        }
    }

    // ---- LIAR'S DICE with the regulars: the townsfolk gambling at the table take the empty seats, and remember the games
    public static List<String> regularsNear(ServerWorld w, BlockPos table) {
        List<String> out = new ArrayList<>();
        for (TownsfolkEntity e : w.getEntitiesByClass(TownsfolkEntity.class, new Box(table).expand(4, 2, 4), e -> e.act() == TownsfolkEntity.Act.GAMBLE))
            out.add(e.name());
        return out;
    }

    public static void diceOver(ServerWorld w, List<UUID> humans, List<String> bots, String winner, boolean winnerBot) {
        for (String b : bots) {
            Townsfolk.Folk f = Townsfolk.ALL.values().stream().filter(x -> x.name().equals(b)).findFirst().orElse(null);
            if (f == null) continue;
            for (UUID h : humans) {
                ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(h);
                String pn = p == null ? "" : p.getName().getString();
                if (!winnerBot && winner.equals(pn)) TownMemory.dice(w.getServer(), h, f.id(), true);
                else if (winnerBot && winner.equals(b)) {
                    TownMemory.dice(w.getServer(), h, f.id(), false);
                    TownMemory.news(w, "dice", f.name() + " took Captain " + pn + "'s coins at Liar's Dice!", f.id());
                }
                if (p != null) TownMemory.befriend(p, f, 1);
            }
        }
    }

    // ------------------------------------------------------------------ round 2: lodgers, the children, the rounds, the chapel
    /** Where they sleep: their own bed, or a bed at the inn. */
    static BlockPos home(ServerWorld w, Townsfolk.Folk f) {
        if (f.bed() != null) return new BlockPos(f.bed()[0], f.bed()[1], f.bed()[2]);
        BlockPos b = innBed(w, f.id());
        return b != null ? b : INN_HALL;
    }

    private static final Map<String, BlockPos> INN_BEDS = new HashMap<>();

    /** A bed at the inn for a lodger (townsfolk without a house, the market keepers): the same one every night. */
    public static BlockPos innBed(ServerWorld w, String id) {
        BlockPos have = INN_BEDS.get(id);
        if (have != null) return have;
        String pool = Townsfolk.BED_POOL.getOrDefault(id, "inn");
        if (pool.startsWith("share:")) return innBed(w, pool.substring(6));
        int[] box = pool.equals("guardhouse") ? GUARDHOUSE : INN_BOX;
        List<BlockPos> beds = new ArrayList<>(venue(w, pool + "beds", box, s -> s.getBlock() instanceof BedBlock && s.get(BedBlock.PART) == BedPart.HEAD));
        beds.removeIf(b -> Townsfolk.ALL.values().stream().anyMatch(f -> f.bed() != null && b.getManhattanDistance(new BlockPos(f.bed()[0], f.bed()[1], f.bed()[2])) <= 2));
        beds.sort(Comparator.comparingLong(BlockPos::asLong));
        for (BlockPos b : beds)
            if (!INN_BEDS.containsValue(b)) { INN_BEDS.put(id, b); return b; }
        return null;
    }

    private static Plan organPlan(ServerWorld w, Townsfolk.Phase phase) {
        Plan p = new Plan(phase, standNear(w, ORGAN.west(), 1));
        p.act = TownsfolkEntity.Act.ORGAN;
        p.look = Vec3d.ofCenter(ORGAN);
        return p;
    }

    /** The children: a free swing in the Gardens, otherwise running about the lawns. */
    private static Plan playPlan(ServerWorld w, TownsfolkEntity e, Townsfolk.Phase phase) {
        for (BlockPos s : venue(w, "swings", PARK, st -> st.getBlock() instanceof net.get900.pixelpirates.homestead.swing.SwingBlock
                && st.get(net.get900.pixelpirates.homestead.TallFurniture.HALF) == DoubleBlockHalf.LOWER)) {
            BlockState st = w.getBlockState(s);
            if (TAKEN.containsKey(s) || !(st.getBlock() instanceof net.get900.pixelpirates.homestead.swing.SwingBlock)
                    || st.get(net.get900.pixelpirates.homestead.swing.SwingBlock.OCCUPIED) || e.getRandom().nextInt(3) == 0) continue;
            Plan p = new Plan(phase, standNear(w, s, 2));
            p.seat = s; p.act = TownsfolkEntity.Act.SWING;
            take(s, e);
            return p;
        }
        Plan p = new Plan(phase, strollPoint(w, PARK_LAWNS, w.random));
        p.wander = 1; p.strollArea = PARK_LAWNS;
        return p;
    }

    private static Plan routePlan(ServerWorld w, Townsfolk.Folk f, Townsfolk.Phase phase) {
        int[][] r = Townsfolk.ROUTES.get(f.id());
        if (r == null) return null;
        Plan p = new Plan(phase, groundAt(w, r[0][0], r[0][1]));
        p.route = r;
        p.act = TownsfolkEntity.Act.WORK;
        return p;
    }

    static BlockPos groundAt(ServerWorld w, int x, int z) {
        if (!w.isChunkLoaded(ChunkPos.toLong(new BlockPos(x, 70, z)))) return new BlockPos(x, 70, z);
        return standNear(w, new BlockPos(x, w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z), 3);
    }

    // ---- THE CHAPEL SERVICE: every third morning after the bell (1000-3400), or /pptown service. The priest preaches
    // from the lectern, the organist plays the hymns (Amazing Grace on the diapason, at the start and the end), everyone
    // who prays of an evening fills the pews.
    private static final int SERVICE_LEN = 2400;
    private static long forcedService = Long.MIN_VALUE;

    /** Ticks since this service began, or -1 when there's none. */
    public static int serviceElapsed(ServerWorld w) {
        long since = w.getTime() - forcedService;
        if (since >= 0 && since < SERVICE_LEN) return (int) since;
        long day = w.getTimeOfDay() / 24000L, t = Math.floorMod(w.getTimeOfDay(), 24000L);
        return day % 3 == 0 && t >= 1000 && t < 1000 + SERVICE_LEN ? (int) (t - 1000) : -1;
    }

    /** Days until the next service (0 = today, not yet over). */
    static int daysToService(ServerWorld w) {
        long day = w.getTimeOfDay() / 24000L, t = Math.floorMod(w.getTimeOfDay(), 24000L);
        if (day % 3 == 0 && t < 1000 + SERVICE_LEN) return 0;
        return (int) (3 - day % 3);
    }

    private static Plan servicePlan(ServerWorld w, TownsfolkEntity e, Townsfolk.Folk f, Townsfolk.Phase phase) {
        if (f.id().equals("anselm")) {
            Plan p = new Plan(phase, standNear(w, LECTERN_STAND, 2));
            p.act = TownsfolkEntity.Act.PREACH; p.look = PEWS;
            return p;
        }
        if (f.id().equals("harmonia")) return organPlan(w, phase);
        return seatPlan(w, e, phase, CHAPEL, TownsfolkEntity.Act.PRAY, ALTAR);
    }

    private static final String[] SERMON = {
            "Brothers and sisters of the sea - welcome. Whatever ship brought you, you are home now.",
            "The sea is wide and we are small. But a small boat with a good crew comes home.",
            "Pray for those still out there tonight: the fishermen, the merchants, and yes - even the pirates.",
            "Forgive your shipmates their snoring, as the Lord forgives you yours.",
            "The deep things are stirring. Do not go looking for them without a friend, a prayer and a very large cannon.",
            "Give what you can to the widow and the orphan. Mistress Fairweather, Pip, Molly - we see you.",
            "And now let us sing, and let Mistress Bellweather's organ carry it to every ship in the harbour."};

    /** Father Anselm at the lectern: a line of the sermon every ten seconds, between the hymns. */
    static void preach(ServerWorld w, TownsfolkEntity e) {
        if (e.planMode == TownEvents.Mode.WEDDING) { if (e.age % 120 < 10) e.triggerAnim("action", "talk"); return; }   // TownEvents speaks the vows
        if (e.planMode == TownEvents.Mode.MEMORIAL) {
            int me = TownEvents.memorialElapsed(w);
            if (me < 300 || me > 1500 || e.age % 240 >= 10) return;
            String who = TownMemory.get(w.getServer()).memorialFor;
            String[] lines = {"We remember " + who + ", who went down to the sea in ships and did not come home.",
                    "The sea keeps what it takes. But it cannot take what we remember.",
                    "Fair winds, " + who + ". May you find a calm harbour on the far shore.",
                    "Let us light a candle, and sing them home."};
            e.triggerAnim("action", "talk");
            TownEvents.chapelSay(w, Text.literal("[Father Anselm] ").formatted(Formatting.GOLD).append(Text.literal(lines[Math.min(3, (me - 300) / 300)]).formatted(Formatting.WHITE)));
            return;
        }
        int el = serviceElapsed(w);
        if (el < 0 || e.age % 200 >= 10) return;
        if (el < 700 || el > 1900) return;
        int i = Math.min(SERMON.length - 1, (el - 700) / 180);
        e.triggerAnim("action", "talk");
        Box chapel = new Box(CHAPEL[0] - 6, CHAPEL[1] - 2, CHAPEL[2] - 6, CHAPEL[3] + 6, CHAPEL[4] + 4, CHAPEL[5] + 6);
        for (ServerPlayerEntity p : w.getPlayers())
            if (chapel.contains(p.getPos())) p.sendMessage(Text.literal("[Father Anselm] ").formatted(Formatting.GOLD).append(Text.literal(SERMON[i]).formatted(Formatting.WHITE)), false);
    }

    /** The organist: the hymns at the service; on other mornings, practice now and then on the softer flute stop. */
    static void organ(ServerWorld w, TownsfolkEntity e, Townsfolk.Phase phase) {
        if (!w.getBlockState(ORGAN).isOf(net.get900.pixelpirates.homestead.HomesteadBlocks.ORGAN_CONSOLE)) return;
        Vec3d at = Vec3d.ofCenter(ORGAN);
        net.minecraft.sound.SoundEvent pipe = net.get900.pixelpirates.homestead.chapel.OrganNotes.DIAPASON, flute = net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value();
        if (e.planMode == TownEvents.Mode.WEDDING) {
            int el = TownEvents.weddingElapsed(w);
            if (el >= 20 && el < 600) TownMusic.play(w, "organ", at, TownMusic.BRIDAL, pipe, 2.5f);
            else if (el >= 1950) TownMusic.play(w, "organ", at, TownMusic.JOYFUL, pipe, 2.5f);
            return;
        }
        if (e.planMode == TownEvents.Mode.MEMORIAL) {
            int el = TownEvents.memorialElapsed(w);
            if ((el >= 40 && el < 300) || el >= 1500) TownMusic.play(w, "organ", at, TownMusic.AMAZING_GRACE, flute, 2f);
            return;
        }
        int el = serviceElapsed(w);
        if (phase == Townsfolk.Phase.SERVICE && el >= 0) {
            int[][] hymn = TownMemory.day(w) % 2 == 0 ? TownMusic.AMAZING_GRACE : TownMusic.JOYFUL;
            if ((el >= 40 && el < 640) || (el >= 1920 && el < 2350)) TownMusic.play(w, "organ", at, hymn, pipe, 2.5f);
        } else if (e.age % 2400 < 10) TownMusic.play(w, "organ", at, TownMusic.AMAZING_GRACE, flute, 1.5f);
    }

    // ---- THE TOWN CRIER's news: real things that happened in the town
    private static String lastChess = "", lastPainting = "";

    static void chessResult(ServerWorld w, Plan p, int win) {
        String[] g = GAMES.get(p.board);
        if (g == null || win < 0) return;
        String a = nameOf(g[win]), b = win == 0 ? (g[1].isEmpty() ? "the computer" : nameOf(g[1])) : nameOf(g[0]);
        if (win == 1 && g[1].isEmpty()) return;
        TownMemory.news(w, "chess", a + " beat " + b + " at chess" + (p.board.equals(BOARDS.get(1).pos) ? " on the Chess Green!" : " at the toymaker's table!"), g[win]);
    }

    static void painted(TownsfolkEntity e, String title) {
        if (e.getWorld() instanceof ServerWorld w)
            TownMemory.news(w, "painting", e.name() + " has finished a painting - \"" + title + "\" - and hung it at home.", e.folkId());
    }

    static void cry(ServerWorld w, TownsfolkEntity e) {
        List<ServerPlayerEntity> near = new ArrayList<>();
        for (ServerPlayerEntity p : w.getPlayers()) if (p.squaredDistanceTo(e) < 24 * 24) near.add(p);
        if (near.isEmpty()) return;
        List<String> news = new ArrayList<>();
        List<String> birds = new ArrayList<>();
        for (var b : w.getEntitiesByClass(net.minecraft.entity.passive.ParrotEntity.class, net.get900.pixelpirates.homestead.parrot.Aviary.cage(), b -> !b.isTamed()))
            birds.add(net.get900.pixelpirates.homestead.parrot.ParrotTypes.of(b).name());
        if (!birds.isEmpty()) news.add("New birds at Polly's aviary today: " + String.join(", ", birds) + "!");
        int d = daysToService(w);
        news.add(d == 0 ? "Service at the chapel this morning, after the bell! All welcome!" : "Next service at the chapel in " + d + " day" + (d == 1 ? "" : "s") + ", after the morning bell!");
        for (TownMemory.News n : TownMemory.recent(w, 2)) news.add(n.text());
        if (TownEvents.marketDay(w)) news.add("Market day! Marco Venn the travelling merchant is in the plaza - today only!");
        if (TownEvents.musicNight(w) || TownMemory.day(w) % 2 == 1) news.add("Music tonight at the Grog Barrel - Fiddler Dan plays from dusk!");
        if (w.isRaining()) news.add("Rain over Wavebreak! The Grog Barrel has a fire lit and a dry seat for every sailor!");
        news.add("By order of the Governor: berth dues are paid at the Harbourmaster's, and the fort's guns are loaded!");
        news.add("Fiddler Dan plays the plaza by day and the Grog Barrel by night!");
        String n = news.get(Math.floorMod(e.line++, news.size()));
        e.triggerAnim("action", "flourish");
        w.playSound(null, e.getBlockPos(), net.minecraft.sound.SoundEvents.BLOCK_BELL_USE, net.minecraft.sound.SoundCategory.NEUTRAL, 0.6f, 1.6f);
        for (ServerPlayerEntity p : near)
            p.sendMessage(Text.literal("[Town Crier] ").formatted(Formatting.GOLD).append(Text.literal("Hear ye, hear ye! " + n).formatted(Formatting.YELLOW)), false);
    }

    private static Plan strollPlan(ServerWorld w, Townsfolk.Folk f, Townsfolk.Phase phase, long day) {
        if (!SHIPS.isEmpty() && w.random.nextInt(3) == 0) {                             // a fine ship in: down to the quay to look at her
            Vec3d s = SHIPS.get(w.random.nextInt(SHIPS.size()));
            Plan p = new Plan(phase, groundAt(w, (int) Math.max(-60, Math.min(60, s.x + w.random.nextInt(9) - 4)), 86));
            p.look = s;
            return p;
        }
        if (TownEvents.marketDay(w) && w.random.nextInt(3) == 0) {                       // browse the pop-up stall
            BlockPos st = TownEvents.stallPos(w);
            Plan p = new Plan(phase, standNear(w, st.add(w.random.nextInt(5) - 2, 0, 2), 2));
            p.look = Vec3d.ofCenter(st);
            return p;
        }
        int[] a = STROLLS[Math.floorMod(Objects.hash(f.id(), day, 7), STROLLS.length)];
        Plan p = new Plan(phase, strollPoint(w, a, w.random));
        p.wander = 1;
        p.strollArea = a;
        return p;
    }

    static BlockPos strollPoint(ServerWorld w, int[] a, Random r) {
        int x = a[0] + r.nextInt(a[2] - a[0] + 1), z = a[1] + r.nextInt(a[3] - a[1] + 1);
        if (!w.isChunkLoaded(ChunkPos.toLong(new BlockPos(x, 70, z)))) return new BlockPos(x, 70, z);
        return standNear(w, new BlockPos(x, w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z), 3);
    }

    /** A free seat in a venue (no player and no other townsperson on it). */
    private static Plan seatPlan(ServerWorld w, TownsfolkEntity e, Townsfolk.Phase phase, int[] box, TownsfolkEntity.Act act, Vec3d look) {
        List<BlockPos> seats = new ArrayList<>(venue(w, "seats" + Arrays.toString(box), box, s -> s.getBlock() instanceof SeatBlock));
        Collections.shuffle(seats, new java.util.Random(e.getUuid().getLeastSignificantBits() ^ w.getTimeOfDay() / 24000L));
        for (BlockPos s : seats) {
            if (TAKEN.containsKey(s) || !w.getEntitiesByClass(SeatEntity.class, new Box(s), x -> true).isEmpty()) continue;
            Plan p = new Plan(phase, standNear(w, s, 2));
            p.seat = s; p.act = act; p.look = look;
            take(s, e);
            return p;
        }
        return null;
    }

    /** A place at a gaming table in the den. */
    private static Plan gamblePlan(ServerWorld w, TownsfolkEntity e, Townsfolk.Phase phase) {
        List<BlockPos> tables = new ArrayList<>(venue(w, "tables", TAVERN, s -> {
            String id = Registries.BLOCK.getId(s.getBlock()).getPath();
            return id.contains("dice_table") || id.contains("anchor_table") || id.contains("roulette");
        }));
        Collections.shuffle(tables, new java.util.Random(e.getUuid().getMostSignificantBits() ^ w.getTimeOfDay() / 24000L));
        for (BlockPos t : tables)
            for (int r = 1; r <= 2; r++)
                for (int dx = -r; dx <= r; dx++)
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r || (r == 1 && dx != 0 && dz != 0)) continue;
                        BlockPos s = t.add(dx, 0, dz);
                        if (TAKEN.containsKey(s)) continue;
                        Plan p;
                        if (r == 1 && w.getBlockState(s).getBlock() instanceof SeatBlock) {          // a stool at the table: sit and play
                            if (!w.getEntitiesByClass(SeatEntity.class, new Box(s), x -> true).isEmpty()) continue;
                            p = new Plan(phase, standNear(w, s, 2));
                            p.seat = s;
                        } else if (standable(w, s)) p = new Plan(phase, s);
                        else continue;
                        p.act = TownsfolkEntity.Act.GAMBLE; p.look = Vec3d.ofCenter(t);
                        take(s, e);
                        return p;
                    }
        return null;
    }

    /** The dartboards in the tavern and the inn (homestead/darts). */
    static List<BlockPos> dartboards(ServerWorld w) {
        List<BlockPos> out = new ArrayList<>(venue(w, "dartboards", TAVERN, s -> s.getBlock() instanceof net.get900.pixelpirates.homestead.darts.DartboardBlock));
        out.addAll(venue(w, "dartboards_inn", INN_BOX, s -> s.getBlock() instanceof net.get900.pixelpirates.homestead.darts.DartboardBlock));
        return out;
    }

    /** A free easel: blank, or one a townsperson left (a player's work in progress is never touched). */
    private static Plan easelPlan(ServerWorld w, TownsfolkEntity e, Townsfolk.Phase phase, int[] box) {
        for (BlockPos p : venue(w, "easels" + Arrays.toString(box), box, s -> s.getBlock() instanceof EaselBlock && s.get(EaselBlock.HALF) == DoubleBlockHalf.LOWER)) {
            EaselBlockEntity be = TownsfolkEntity.easel(w, p);
            if (be == null || TAKEN.containsKey(p) || !(be.pixels == null || !be.owner.isEmpty())) continue;
            Direction front = w.getBlockState(p).get(EaselBlock.FACING);
            BlockPos s = standNear(w, p.offset(front), 1);
            Plan plan = new Plan(phase, s);
            plan.easel = p; plan.act = TownsfolkEntity.Act.PAINT; plan.look = Vec3d.ofCenter(p);
            take(p, e);
            return plan;
        }
        return null;
    }

    /** Sit down opposite a townsperson who is waiting at a board - or take an empty board and wait for someone. */
    private static Plan chessPlan(ServerWorld w, TownsfolkEntity e, Townsfolk.Phase phase, BlockPos near) {
        String me = e.folkId();
        Board pick = null;
        int side = 0;
        List<Board> boards = new ArrayList<>(BOARDS);
        if (near != null) boards.sort(Comparator.comparingDouble(b -> b.pos.getSquaredDistance(near)));
        for (Board b : boards) {
            String other = WAITING.get(b.pos);
            if (other != null && !other.equals(me) && w.isChunkLoaded(b.pos)) { pick = b; side = 1; break; }
        }
        if (pick == null)
            for (Board b : boards)
                if (w.isChunkLoaded(b.pos) && !WAITING.containsKey(b.pos) && !GAMES.containsKey(b.pos) && Chess.idle(w, b.pos)) { pick = b; break; }
        if (pick == null) return null;
        BlockPos place = side == 0 ? pick.white : pick.black;
        Plan p = new Plan(phase, pick.sit ? standNear(w, place, 2) : standNear(w, place, 2));
        p.board = pick.pos; p.side = side; p.look = pick.centre;
        p.act = pick.sit ? TownsfolkEntity.Act.CHESS_SIT : TownsfolkEntity.Act.CHESS_STAND;
        if (pick.sit) p.seat = place;
        if (side == 1) {
            String white = WAITING.remove(pick.pos);
            WAITING_SINCE.remove(pick.pos);
            GAMES.put(pick.pos, new String[]{white, me});
            Chess.npcStart(w, pick.pos, nameOf(white), nameOf(me), level(white), level(me));
        } else {
            WAITING.put(pick.pos, me);
            WAITING_SINCE.put(pick.pos, w.getTime());
        }
        return p;
    }

    static boolean waitingAt(BlockPos board, String id) { return id.equals(WAITING.get(board)); }

    // ---- for the Chess League (ChessLeague): the two boards, and sitting someone at one for a tournament game
    static final int TABLE = 0, GREEN = 1;

    static BlockPos boardPos(int idx) { return BOARDS.get(idx).pos; }

    static int chessLevel(String id) { return level(id); }

    static Plan seatAt(ServerWorld w, Townsfolk.Phase phase, int idx, int side) {
        Board b = BOARDS.get(idx);
        BlockPos place = side == 0 ? b.white : b.black;
        Plan p = new Plan(phase, standNear(w, place, 2));
        p.board = b.pos; p.side = side; p.look = b.centre; p.challenge = true;
        p.act = b.sit ? TownsfolkEntity.Act.CHESS_SIT : TownsfolkEntity.Act.CHESS_STAND;
        if (b.sit) p.seat = place;
        return p;
    }

    /** Nobody came: after a minute the one waiting plays the computer instead. */
    private static void chessTimeouts(ServerWorld w) {
        for (Iterator<Map.Entry<BlockPos, Long>> it = WAITING_SINCE.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            if (w.getTime() - en.getValue() < 1200) continue;
            BlockPos b = en.getKey();
            String who = WAITING.remove(b);
            it.remove();
            if (who == null || !Chess.idle(w, b)) continue;
            GAMES.put(b, new String[]{who, ""});
            Chess.npcStart(w, b, nameOf(who), "Computer (normal)", level(who), 2);
        }
    }

    static void rematch(ServerWorld w, Plan plan) {
        String[] g = GAMES.get(plan.board);
        if (g == null) return;
        Chess.npcStart(w, plan.board, nameOf(g[0]), g[1].isEmpty() ? "Computer (normal)" : nameOf(g[1]), level(g[0]), g[1].isEmpty() ? 2 : level(g[1]));
    }

    private static String nameOf(String id) { Townsfolk.Folk f = Townsfolk.get(id); return f == null ? "Computer (normal)" : f.name(); }

    /** The strong players: the Commodore, the astronomer, the Governor and the toymaker play the normal engine. */
    private static int level(String id) {
        return switch (id) { case "pettigrew", "ptolemy", "aldous", "gideon" -> 2; default -> 1; };
    }

    private static void take(BlockPos p, TownsfolkEntity e) { TAKEN.put(p.toImmutable(), e.folkId()); }

    /** A free seat in a box for someone who isn't a townsperson (the market keepers' evenings - Lodging). */
    static BlockPos freeSeat(ServerWorld w, int[] box, String id) {
        List<BlockPos> seats = new ArrayList<>(venue(w, "seats" + Arrays.toString(box), box, s -> s.getBlock() instanceof SeatBlock));
        Collections.shuffle(seats, new java.util.Random(id.hashCode() ^ w.getTimeOfDay() / 24000L));
        for (BlockPos s : seats)
            if (!TAKEN.containsKey(s) && w.getEntitiesByClass(SeatEntity.class, new Box(s), x -> true).isEmpty()) { TAKEN.put(s, id); return s; }
        return null;
    }

    static void releaseSeat(String id) { TAKEN.values().removeIf(id::equals); }

    static boolean stillValid(ServerWorld w, TownsfolkEntity e, Plan p) {
        if (p.seat != null && !(w.getBlockState(p.seat).getBlock() instanceof SeatBlock)) return false;
        if (p.easel != null && TownsfolkEntity.easel(w, p.easel) == null) return false;
        if (p.dartboard != null && !TownDarts.claimed(e.folkId())) return false;      // the game is over
        return true;
    }

    /** Give up everything they hold: seats, places, easels, a waiting board or a game in progress. */
    static void release(ServerWorld w, TownsfolkEntity e) {
        String me = e.folkId();
        if (e.plan != null && e.plan.dartboard != null) TownDarts.left(w, e);
        TAKEN.values().removeIf(me::equals);
        for (Iterator<Map.Entry<BlockPos, String>> it = WAITING.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            if (me.equals(en.getValue())) { WAITING_SINCE.remove(en.getKey()); it.remove(); }
        }
        for (Iterator<Map.Entry<BlockPos, String[]>> it = GAMES.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            String[] g = en.getValue();
            if (!me.equals(g[0]) && !me.equals(g[1])) continue;
            Chess.npcStop(w, en.getKey(), nameOf(g[0]));
            Chess.npcStop(w, en.getKey(), g[1].isEmpty() ? "Computer (normal)" : nameOf(g[1]));
            it.remove();
        }
    }

    // ------------------------------------------------------------------ painting
    /** One step at the easel: the next row of their painting goes on; done, it goes home with them. */
    static void paintStep(ServerWorld w, TownsfolkEntity e) {
        Plan p = e.plan;
        if (p == null || p.easel == null || e.age % 60 >= 10) return;
        EaselBlockEntity be = TownsfolkEntity.easel(w, p.easel);
        if (be == null) { e.plan = null; return; }
        if (be.pixels != null && be.owner.isEmpty() && e.wipRow > 0) { e.plan = null; return; }      // a player took over the canvas
        if (e.wip == null) { e.wip = TownArt.paint(w.random, e.name()); e.wipRow = 0; }
        Art.Size size = TownsfolkEntity.sizeOf(e.wip);
        e.wipRow++;
        be.owner = e.folkId();
        be.set(size, e.wip.getString("Title"), TownArt.partial(e.wip.getIntArray("Pixels"), size, e.wipRow));
        if (e.wipRow < size.ph) return;
        e.pending.add(e.wip);
        painted(e, e.wip.getString("Title"));
        while (e.pending.size() > 3) e.pending.remove(0);
        e.wip = null;
        e.wipRow = 0;
        be.owner = "";
        be.set(Art.Size.SQUARE, "", null);
        e.triggerAnim("action", "cheer");
        if (p.phase != Townsfolk.Phase.WORK) {                                  // one a night for the amateurs
            TAKEN.remove(p.easel);
            p.easel = null;
            p.act = TownsfolkEntity.Act.IDLE;
            p.wander = 4;
            e.setAct(TownsfolkEntity.Act.IDLE);
        }
    }

    /** Isadora's gallery: a player's painting on a wall of her studio (the oldest of eight comes down). */
    static boolean hangInGallery(ServerWorld w, NbtCompound art) {
        BlockPos at = new BlockPos(102, 72, -73);
        if (!w.isChunkLoaded(at)) return false;
        CustomPaintingEntity c = wallSpot(w, at, art, 5);
        if (c == null) return false;
        TownMemory m = TownMemory.get(w.getServer());
        while (m.gallery.size() >= 8) { Entity old = w.getEntity(m.gallery.remove(0)); if (old instanceof CustomPaintingEntity) old.discard(); }
        c.onPlace();
        w.spawnEntity(c);
        m.gallery.add(c.getUuid());
        m.markDirty();
        return true;
    }

    /** The nearest free indoor wall to `at` that this painting fits on (null = none). */
    static CustomPaintingEntity wallSpot(ServerWorld w, BlockPos at, NbtCompound art, int r) {
        List<CustomPaintingEntity> spots = new ArrayList<>();
        for (int dy = 0; dy <= 2; dy++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = at.add(dx, dy, dz);
                    if (!w.getBlockState(p).isAir() || w.isSkyVisible(p)) continue;
                    for (Direction d : Direction.Type.HORIZONTAL) {
                        BlockPos wall = p.offset(d);
                        if (!w.getBlockState(wall).isOpaqueFullCube(w, wall)) continue;
                        CustomPaintingEntity c = new CustomPaintingEntity(w, p, d.getOpposite(), art);
                        if (c.canStayAttached()) spots.add(c);
                    }
                }
        spots.sort(Comparator.comparingDouble(c -> c.squaredDistanceTo(Vec3d.ofCenter(at))));
        return spots.isEmpty() ? null : spots.get(0);
    }

    /** Hang a finished painting on a wall of their house, near the bed (at most four of theirs up at once). */
    static boolean hang(ServerWorld w, TownsfolkEntity e, NbtCompound art) {
        Townsfolk.Folk f = e.folk();
        if (f == null || f.bed() == null) return true;
        BlockPos bed = new BlockPos(f.bed()[0], f.bed()[1], f.bed()[2]);
        if (!w.isChunkLoaded(bed)) return false;
        List<CustomPaintingEntity> spots = new ArrayList<>();
        for (int dy = 1; dy <= 2; dy++)
            for (int dx = -7; dx <= 7; dx++)
                for (int dz = -7; dz <= 7; dz++) {
                    BlockPos p = bed.add(dx, dy, dz);
                    if (!w.getBlockState(p).isAir() || w.isSkyVisible(p)) continue;
                    for (Direction d : Direction.Type.HORIZONTAL) {
                        BlockPos wall = p.offset(d);
                        if (!w.getBlockState(wall).isOpaqueFullCube(w, wall)) continue;
                        CustomPaintingEntity c = new CustomPaintingEntity(w, p, d.getOpposite(), art);
                        if (c.canStayAttached()) spots.add(c);
                    }
                }
        if (spots.isEmpty()) return false;
        spots.sort(Comparator.comparingDouble(c -> c.squaredDistanceTo(Vec3d.ofCenter(bed)) + w.random.nextInt(6)));
        while (e.hung.size() >= 4) {
            Entity old = w.getEntity(e.hung.remove(0));
            if (old instanceof CustomPaintingEntity) old.discard();
        }
        CustomPaintingEntity c = spots.get(0);
        c.onPlace();
        w.spawnEntity(c);
        e.hung.add(c.getUuid());
        return true;
    }

    // ------------------------------------------------------------------ the world
    /** The venue's blocks of one kind, scanned once its chunks are loaded. */
    private static List<BlockPos> venue(ServerWorld w, String key, int[] b, java.util.function.Predicate<BlockState> what) {
        List<BlockPos> got = VENUES.get(key);
        if (got != null) return got;
        for (int cx = b[0] >> 4; cx <= b[3] >> 4; cx++)
            for (int cz = b[2] >> 4; cz <= b[5] >> 4; cz++)
                if (!w.isChunkLoaded(cx, cz)) return List.of();
        got = new ArrayList<>();
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int x = b[0]; x <= b[3]; x++)
            for (int z = b[2]; z <= b[5]; z++)
                for (int y = b[1]; y <= b[4]; y++)
                    if (what.test(w.getBlockState(m.set(x, y, z)))) got.add(m.toImmutable());
        VENUES.put(key, got);
        return got;
    }

    /** The bed's HEAD near where they're meant to sleep (hand edits may have moved it a little). */
    static BlockPos findBed(ServerWorld w, BlockPos near) {
        if (!w.isChunkLoaded(near)) return null;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int r = 0; r <= 4; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++)
                    for (int dy = -2; dy <= 2; dy++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        BlockState s = w.getBlockState(m.set(near.getX() + dx, near.getY() + dy, near.getZ() + dz));
                        if (s.getBlock() instanceof BedBlock && s.get(BedBlock.PART) == BedPart.HEAD) return m.toImmutable();
                    }
        return null;
    }

    /** The nearest cell to `target` a person can stand in (solid under foot, two cells of room, no water). */
    static BlockPos standNear(ServerWorld w, BlockPos target, int radius) {
        if (!w.isChunkLoaded(target)) return target;
        int[] dys = {0, 1, -1, 2, -2, 3, -3};
        for (int r = 0; r <= radius; r++)
            for (int dy : dys)
                for (int dx = -r; dx <= r; dx++)
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        BlockPos p = target.add(dx, dy, dz);
                        if (standable(w, p)) return p;
                    }
        return target;
    }

    static boolean standable(ServerWorld w, BlockPos p) {
        BlockState below = w.getBlockState(p.down());
        if (below.getCollisionShape(w, p.down()).isEmpty() || below.getCollisionShape(w, p.down()).getMax(Direction.Axis.Y) < 0.5) return false;
        if (!w.getBlockState(p).getCollisionShape(w, p).isEmpty() || !w.getBlockState(p.up()).getCollisionShape(w, p.up()).isEmpty()) return false;
        return w.getFluidState(p).isEmpty();
    }

    // ------------------------------------------------------------------ commands
    private static int list(ServerCommandSource src) {
        ServerWorld w = src.getServer().getWorld(PortTraders.DIM);
        if (w == null) return 0;
        State st = state(src.getServer());
        for (Townsfolk.Folk f : Townsfolk.ALL.values()) {
            UUID u = st.owner.get(f.id());
            Entity e = u == null ? null : w.getEntity(u);
            String where = e instanceof TownsfolkEntity t
                    ? t.getBlockPos().toShortString() + " " + Townsfolk.phase(f, w.getTimeOfDay()) + "/" + t.act() + (t.arrived ? "" : " (on the way to " + (t.plan == null ? "?" : t.plan.stand.toShortString()) + ")")
                      + (t.hung.isEmpty() && t.pending.isEmpty() ? "" : " paintings hung " + t.hung.size() + ", to hang " + t.pending.size())
                    : u == null ? "never spawned" : "not loaded (last " + BlockPos.fromLong(st.last.getOrDefault(f.id(), 0L)).toShortString() + ")";
            src.sendFeedback(() -> Text.literal(f.id() + " - " + f.name() + ": " + where), false);
        }
        return 1;
    }

    private static int force(ServerCommandSource src, String id, String hobby) {
        Townsfolk.Hobby h;
        try { h = Townsfolk.Hobby.valueOf(hobby.toUpperCase()); } catch (IllegalArgumentException ex) { src.sendError(Text.literal("Hobbies: drink, gamble, chess, paint, pray, stroll")); return 0; }
        ServerWorld w = src.getServer().getWorld(PortTraders.DIM);
        int n = 0;
        for (Townsfolk.Folk f : Townsfolk.ALL.values()) {
            if (!id.equals("all") && !id.equals(f.id())) continue;
            FORCED.put(f.id(), h);
            n++;
            UUID u = state(src.getServer()).owner.get(f.id());
            if (w != null && u != null && w.getEntity(u) instanceof TownsfolkEntity t) t.plan = null;          // re-plan now
        }
        int m = n;
        src.sendFeedback(() -> Text.literal(m + " townsfolk will " + hobby + " in their leisure time (until a restart)."), false);
        return n;
    }

    private static int summon(ServerCommandSource src, String id) {
        Townsfolk.Folk f = Townsfolk.get(id);
        ServerWorld w = src.getServer().getWorld(PortTraders.DIM);
        if (f == null || w == null || src.getWorld() != w) { src.sendError(Text.literal("Unknown townsperson, or not in the pirate world.")); return 0; }
        State st = state(src.getServer());
        Entity e = st.owner.get(id) == null ? null : w.getEntity(st.owner.get(id));
        if (e instanceof TownsfolkEntity old) {
            old.leaveSpot();
            old.refreshPositionAndAngles(src.getPosition().x, src.getPosition().y, src.getPosition().z, 0, 0);
            old.plan = null;
        } else {
            TownsfolkEntity t = HomesteadEntities.TOWNSFOLK.create(w);
            if (t == null) return 0;
            t.setFolk(f);
            t.refreshPositionAndAngles(src.getPosition().x, src.getPosition().y, src.getPosition().z, 0, 0);
            t.initialize(w, w.getLocalDifficulty(t.getBlockPos()), SpawnReason.COMMAND, null, null);
            w.spawnEntity(t);
            if (st.owner.get(id) != null) {                                    // the old one is in an unloaded chunk: it retires on load
                PixelPirates.LOGGER.info("[Town] {} summoned afresh", id);
            }
            st.owner.put(id, t.getUuid());
            st.markDirty();
        }
        src.sendFeedback(() -> Text.literal("Summoned " + f.name() + " (they'll head off to their plan when nobody's talking to them)."), false);
        return 1;
    }

    private static int tp(ServerCommandSource src, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity p = src.getPlayerOrThrow();
        ServerWorld w = src.getServer().getWorld(PortTraders.DIM);
        State st = state(src.getServer());
        UUID u = st.owner.get(id);
        if (w == null || u == null) { src.sendError(Text.literal("Not spawned yet.")); return 0; }
        Entity e = w.getEntity(u);
        BlockPos at = e != null ? e.getBlockPos() : BlockPos.fromLong(st.last.getOrDefault(id, 0L));
        p.teleport(w, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, p.getYaw(), p.getPitch());
        return 1;
    }

    private static int reset(ServerCommandSource src) {
        ServerWorld w = src.getServer().getWorld(PortTraders.DIM);
        if (w == null) return 0;
        int n = 0;
        for (Entity e : w.iterateEntities()) if (e instanceof TownsfolkEntity) { ((TownsfolkEntity) e).leaveSpot(); e.discard(); n++; }
        State st = state(src.getServer());
        st.owner.clear(); st.last.clear(); st.markDirty();
        TAKEN.clear(); WAITING.clear(); WAITING_SINCE.clear(); GAMES.clear(); VENUES.clear(); MISSING.clear();
        int m = n;
        src.sendFeedback(() -> Text.literal("Removed " + m + " loaded townsfolk; they come back as you walk round the town (or /pptown spawn)."), false);
        return n;
    }

    // ------------------------------------------------------------------ saved: who is the real one of each
    public static final class State extends PersistentState {
        final Map<String, UUID> owner = new HashMap<>();
        final Map<String, Long> last = new HashMap<>();

        static State read(NbtCompound n) {
            State s = new State();
            NbtCompound o = n.getCompound("Owner"), l = n.getCompound("Last");
            for (String k : o.getKeys()) if (o.get(k) != null && o.get(k).getType() == NbtElement.INT_ARRAY_TYPE) s.owner.put(k, o.getUuid(k));
            for (String k : l.getKeys()) s.last.put(k, l.getLong(k));
            return s;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound n) {
            NbtCompound o = new NbtCompound(), l = new NbtCompound();
            owner.forEach(o::putUuid);
            last.forEach(l::putLong);
            n.put("Owner", o);
            n.put("Last", l);
            return n;
        }
    }
}
