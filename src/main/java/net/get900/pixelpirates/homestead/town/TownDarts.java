package net.get900.pixelpirates.homestead.town;

import net.get900.pixelpirates.homestead.darts.DartEntity;
import net.get900.pixelpirates.homestead.darts.DartboardBlockEntity;
import net.get900.pixelpirates.homestead.darts.Darts;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * DARTS and the townsfolk (2026-10-05): who is at which board, the plan that keeps them at the oche, regulars playing
 * each other in their leisure (GAMBLE hobby), a regular taking on a player who starts a game, and how each throws.
 * The game itself is {@link DartboardBlockEntity}.
 */
public final class TownDarts {
    private TownDarts() {}

    /** folk id -> the board they're playing (or waiting) at. */
    private static final Map<String, BlockPos> AT = new HashMap<>();

    /** How good each is (0..1); everyone else 0.25-0.65 by name. Brannoc and the old salts are sharp. */
    private static final Map<String, Double> SKILL = Map.of("brannoc", 0.9, "rufus", 0.8, "hale", 0.7, "jack", 0.75,
            "pettigrew", 0.65, "dobbs", 0.2, "pip", 0.1, "molly", 0.1, "lazlo", 0.85, "ned", 0.6);

    static double skill(String id) {
        Double s = SKILL.get(id);
        return s != null ? s : 0.25 + Math.floorMod(Objects.hash(id, "darts"), 41) / 100.0;
    }

    static boolean claimed(String id) { return AT.containsKey(id); }

    // ------------------------------------------------------------------ plans
    /** At a board: stand at the oche facing it. Bedtime or the service ends their game. */
    static TownLife.Plan plan(ServerWorld w, TownsfolkEntity e, Townsfolk.Phase phase) {
        BlockPos b = AT.get(e.folkId());
        if (b == null) return null;
        if (phase == Townsfolk.Phase.SLEEP || phase == Townsfolk.Phase.SERVICE || !(w.getBlockEntity(b) instanceof DartboardBlockEntity be)) {
            leaveGame(w, e.folkId());
            return null;
        }
        TownLife.Plan p = new TownLife.Plan(phase, TownLife.standNear(w, be.oche(), 1));
        p.act = TownsfolkEntity.Act.DARTS;
        p.look = Vec3d.ofCenter(b);
        p.dartboard = b;
        return p;
    }

    /** Leisure, GAMBLE hobby: join a regular waiting at a board, or (now and then) go and wait for someone. */
    static TownLife.Plan leisure(ServerWorld w, TownsfolkEntity e, Townsfolk.Phase phase) {
        String me = e.folkId();
        List<BlockPos> boards = TownLife.dartboards(w);
        for (BlockPos b : boards)
            if (w.getBlockEntity(b) instanceof DartboardBlockEntity be && be.phase == DartboardBlockEntity.Phase.LOBBY
                    && be.throwers.size() == 1 && be.throwers.get(0).isNpc() && !me.equals(be.throwers.get(0).folk)) {
                be.npcJoin(me, e.folk().name());
                AT.put(me, b);
                return plan(w, e, phase);
            }
        if (Math.floorMod(Objects.hash(me, w.getTimeOfDay() / 6000L), 3) != 0) return null;    // not every evening
        for (BlockPos b : boards)
            if (w.getBlockEntity(b) instanceof DartboardBlockEntity be && be.phase == DartboardBlockEntity.Phase.IDLE) {
                be.npcWaiting(w, me, e.folk().name());
                AT.put(me, b);
                return plan(w, e, phase);
            }
        return null;
    }

    /** A player started a game: the nearest regular (drinking or gambling in here, free) takes them on. Their name, or null. */
    public static String recruit(ServerWorld w, DartboardBlockEntity be) {
        BlockPos b = be.getPos();
        TownsfolkEntity best = null;
        for (TownsfolkEntity t : w.getEntitiesByClass(TownsfolkEntity.class, new Box(b).expand(24, 6, 24), t -> {
            Townsfolk.Folk f = t.folk();
            if (f == null || claimed(f.id()) || TownLife.challenged(t) || Townsfolk.scale(f.id()) < 1) return false;
            TownsfolkEntity.Act a = t.act();
            boolean social = f.hobbies().contains(Townsfolk.Hobby.GAMBLE) || f.hobbies().contains(Townsfolk.Hobby.DRINK);
            return social && (a == TownsfolkEntity.Act.DRINK || a == TownsfolkEntity.Act.GAMBLE || a == TownsfolkEntity.Act.SIT
                    || a == TownsfolkEntity.Act.IDLE || a == TownsfolkEntity.Act.DANCE);
        }))
            if (best == null || t.squaredDistanceTo(Vec3d.ofCenter(b)) < best.squaredDistanceTo(Vec3d.ofCenter(b))) best = t;
        if (best == null) return null;
        TownLife.release(w, best);
        best.leaveSpot();
        be.npcJoin(best.folkId(), best.folk().name());
        AT.put(best.folkId(), b);
        best.plan = null;                                                    // re-plan now: the darts plan
        return best.folk().name();
    }

    /** /pptown darts a b: the two play at the board nearest {@code at} (testing). */
    static String match(ServerWorld w, Vec3d at, String a, String b) {
        BlockPos best = null;
        for (BlockPos p : TownLife.dartboards(w)) if (best == null || p.getSquaredDistance(at) < best.getSquaredDistance(at)) best = p;
        if (best == null || !(w.getBlockEntity(best) instanceof DartboardBlockEntity be)) return "No dartboard loaded nearby.";
        TownsfolkEntity ea = entity(w, a), eb = entity(w, b);
        if (ea == null || eb == null) return "Both must be alive and loaded (/pptown summon <id>).";
        be.reset(w);
        for (TownsfolkEntity e : List.of(ea, eb)) { TownLife.release(w, e); e.leaveSpot(); }
        be.npcWaiting(w, a, ea.folk().name());
        be.npcJoin(b, eb.folk().name());
        AT.put(a, best); AT.put(b, best);
        ea.plan = null; eb.plan = null;
        return ea.folk().name() + " v " + eb.folk().name() + " at the board at " + best.toShortString();
    }

    // ------------------------------------------------------------------ the board asks
    /** A recruited/joined townsperson still walking over. */
    public static boolean npcComing(DartboardBlockEntity be) {
        if (!(be.getWorld() instanceof ServerWorld w)) return false;
        for (DartboardBlockEntity.Thrower t : be.throwers) if (t.isNpc() && !atOche(w, be, t.folk)) return true;
        return false;
    }

    public static boolean allAtOche(ServerWorld w, DartboardBlockEntity be) { return !npcComing(be); }

    public static boolean atOche(ServerWorld w, DartboardBlockEntity be, String folk) {
        TownsfolkEntity e = entity(w, folk);
        if (e == null || !be.getPos().equals(AT.get(folk))) return false;
        Vec3d o = Vec3d.ofBottomCenter(be.oche());
        return e.squaredDistanceTo(o.x, e.getY(), o.z) < 2.6 * 2.6 && e.act() == TownsfolkEntity.Act.DARTS;
    }

    /** One dart: aim for a finish (or treble twenty), scatter by skill, and fly it from the hand to the face. */
    public static void throwDart(ServerWorld w, DartboardBlockEntity be, String folk, int left) {
        TownsfolkEntity e = entity(w, folk);
        if (e == null) return;
        Direction f = be.facing();
        double[] at = Darts.scatter(Darts.aim(left), skill(folk), w.random);
        Vec3d to = Darts.point(be.getPos(), f, at[0], at[1]);
        e.getLookControl().lookAt(to.x, to.y, to.z, 30, 30);
        e.triggerAnim("action", "throw");
        Vec3d fwd = to.subtract(e.getEyePos()).normalize();
        Vec3d hand = e.getEyePos().add(fwd.multiply(0.4)).add(fwd.crossProduct(new Vec3d(0, 1, 0)).normalize().multiply(0.25)).add(0, 0.1, 0);
        w.spawnEntity(DartEntity.aimed(w, folk, be.getPos(), hand, to));
    }

    /** The game is won: the news, friendship for the players who played the regulars. */
    public static void result(ServerWorld w, DartboardBlockEntity be, DartboardBlockEntity.Thrower winner) {
        List<DartboardBlockEntity.Thrower> others = new ArrayList<>(be.throwers);
        others.remove(winner);
        if (others.isEmpty()) return;                                        // practice
        String losers = String.join(" and ", others.stream().map(t -> t.player != null ? "Captain " + t.name : t.name).toList());
        String who = winner.player != null ? "Captain " + winner.name : winner.name;
        String about = winner.isNpc() ? winner.folk : others.stream().filter(DartboardBlockEntity.Thrower::isNpc).map(t -> t.folk).findFirst().orElse("");
        if (!about.isEmpty()) TownMemory.news(w, "darts", who + " beat " + losers + " at darts in the Grog Barrel!", about);
        for (DartboardBlockEntity.Thrower t : be.throwers) {
            if (t.player == null) continue;
            ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(t.player);
            if (p == null) continue;
            for (DartboardBlockEntity.Thrower o : be.throwers)
                if (o.isNpc()) TownMemory.befriend(p, Townsfolk.get(o.folk), t == winner ? 8 : 4);
        }
    }

    // ------------------------------------------------------------------ leaving
    /** The board let them go (game over, they left): back to their day. */
    public static void release(ServerWorld w, String folk) {
        if (AT.remove(folk) == null) return;
        TownsfolkEntity e = entity(w, folk);
        if (e != null) e.plan = null;
    }

    public static void releaseAll(ServerWorld w, DartboardBlockEntity be) {
        for (DartboardBlockEntity.Thrower t : new ArrayList<>(be.throwers)) if (t.isNpc()) release(w, t.folk);
        AT.values().removeIf(be.getPos()::equals);
    }

    /** They walked away from the board (bedtime, the service, a new plan): out of the game. */
    static void left(ServerWorld w, TownsfolkEntity e) { leaveGame(w, e.folkId()); }

    private static void leaveGame(ServerWorld w, String folk) {
        BlockPos b = AT.remove(folk);
        if (b != null && w.getBlockEntity(b) instanceof DartboardBlockEntity be) be.npcLeft(w, folk);
    }

    private static TownsfolkEntity entity(ServerWorld w, String folk) {
        UUID u = TownLife.state(w.getServer()).owner.get(folk);
        Entity e = u == null ? null : w.getEntity(u);
        return e instanceof TownsfolkEntity t ? t : null;
    }
}
