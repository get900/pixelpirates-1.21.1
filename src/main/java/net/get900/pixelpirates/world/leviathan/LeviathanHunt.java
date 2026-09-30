package net.get900.pixelpirates.world.leviathan;

import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.block.custom.LeviathanBlock;
import net.get900.pixelpirates.entity.ModEntities;
import net.get900.pixelpirates.entity.custom.BaneBoltEntity;
import net.get900.pixelpirates.entity.mob.BossProgression;
import net.get900.pixelpirates.entity.mob.LeviathanEntity;
import net.get900.pixelpirates.entity.mob.LeviathanWakeEntity;
import net.get900.pixelpirates.entity.mob.ModMobs;
import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.world.dimension.ModDimensions;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * THE HUNT (boss 10/10) - the world's one Leviathan, from the moment it is woken to the moment it dies. Runs every
 * server tick in the Pixel Pirates dimension:
 * <ul>
 *   <li>WAKING: the Rift Seal asks an eligible player to confirm (it never returns once slain); /ppleviathan awaken.</li>
 *   <li>IN A LAIR: keeps exactly one LeviathanEntity there while someone is near (spawned with the carried health).</li>
 *   <li>FLIGHT: after a retreat it swims the route in real time (a virtual position; the Leviathan itself, whole, swims it in view of
 *       anyone near), destroying the port it passes (LeviathanPorts), and waits at the next lair in its next form.</li>
 *   <li>THE SPIRE (phase 3): seals the channel, rings the Tide Bells, spins the whirlpool, raises THE LAST TIDE layer by
 *       layer; if the tide reaches the top it FLEES (heals to 33%) and only the Watchers' Horn calls it back (5 min).</li>
 *   <li>Blocks: seal, anchor winches, tide bells, the Bane ballista, the horn ({@link #onBlockUsed}).</li>
 * </ul>
 */
public final class LeviathanHunt {
    private LeviathanHunt() {}

    public static final int CHAIN_INDEX = 9;                          // BossProgression.CHAIN position of the Leviathan
    static final double FLIGHT_SPEED = 0.6;
    static final int HORN_COOLDOWN = 6000, BELL_SILENCE = 1800, BELL_HITS = 6, TIDE_INTERVAL = 240;

    // ---- runtime only (rebuilt after a restart)
    @Nullable private static UUID wake;
    private static boolean watchAll;                                  // test: treat the route as watched (no player needed)
    private static int nearTicks, maelstrom, tideTimer, sealed = -1, fillCursor, drainTimer;
    private static final int[] bellSilence = new int[4], bellHits = new int[4];
    private static final Map<UUID, Long> winchCooldown = new HashMap<>();
    @Nullable private static List<BlockPos> fillCells;

    @Nullable
    public static ServerWorld world(MinecraftServer server) { return server.getWorld(ModDimensions.PIXEL_PIRATES_WORLD); }

    static LeviathanRoute.Route route(ServerWorld w) { return LeviathanRoute.of(w.getSeed()); }

    static BlockPos at(ServerWorld w, String site, int[] rel) {
        LeviathanRoute.Site s = route(w).byId(site);
        return new BlockPos(s.x() + rel[0], rel[1], s.z() + rel[2]);
    }

    static String lairOf(int stage) {
        return switch (stage) { case LeviathanState.PHASE2, LeviathanState.FLIGHT1 -> LeviathanRoute.GULLET;
            case LeviathanState.PHASE3, LeviathanState.FLIGHT2, LeviathanState.FLED -> LeviathanRoute.SPIRE; default -> LeviathanRoute.RIFT; };
    }

    static void tellAll(ServerWorld w, Text t) {
        net.get900.pixelpirates.PixelPirates.LOGGER.info("[Leviathan] {}", t.getString());          // the hunt's story, in the log too
        for (ServerPlayerEntity p : w.getPlayers()) p.sendMessage(t, false);
    }

    /** Is this the world's one Leviathan? (any other copy discards itself) */
    public static boolean owns(ServerWorld w, LeviathanEntity e) {
        LeviathanState st = LeviathanState.get(w);
        if (st.stage != LeviathanState.PHASE1 && st.stage != LeviathanState.PHASE2 && st.stage != LeviathanState.PHASE3) return false;
        if (st.leviathan == null) { st.leviathan = e.getUuid(); st.markDirty(); }
        return st.leviathan.equals(e.getUuid());
    }

    /** Is this the one swimming the route right now? (between lairs; any other copy discards itself) */
    public static boolean ownsTraveler(ServerWorld w, LeviathanEntity e) {
        LeviathanState st = LeviathanState.get(w);
        if (st.stage != LeviathanState.FLIGHT1 && st.stage != LeviathanState.FLIGHT2) return false;
        if (wake == null) wake = e.getUuid();
        return wake.equals(e.getUuid());
    }

    @Nullable
    static LeviathanEntity living(ServerWorld w) {
        LeviathanState st = LeviathanState.get(w);
        return st.leviathan != null && w.getEntity(st.leviathan) instanceof LeviathanEntity l && l.isAlive() ? l : null;
    }

    // =====================================================================================
    // the tick
    // =====================================================================================
    public static void tick(MinecraftServer server) {
        LeviathanPorts.tick(server);
        ServerWorld w = world(server);
        if (w == null) return;
        LeviathanState st = LeviathanState.get(w);
        switch (st.stage) {
            case LeviathanState.PHASE1, LeviathanState.PHASE2, LeviathanState.PHASE3 -> ensure(w, st);
            case LeviathanState.FLIGHT1, LeviathanState.FLIGHT2 -> flight(w, st);
            default -> { }
        }
        LeviathanEntity lev = living(w);
        if (st.stage == LeviathanState.PHASE3 && lev != null) spire(w, st, lev);
        else if (st.tideLayers > 0 && ++drainTimer % 8 == 0 && spireLoaded(w)) drainLayer(w, st);
        if (st.stage != LeviathanState.PHASE3 && st.channelSealed && spireLoaded(w)) unseal(w, st);
        if (maelstrom > 0) maelstrom--;
    }

    // ---------------------------------------------------------------- keep one Leviathan in the lair while someone is there
    private static void ensure(ServerWorld w, LeviathanState st) {
        if (living(w) != null) { nearTicks = 0; return; }
        String site = lairOf(st.stage);
        LeviathanRoute.Site s = route(w).byId(site);
        boolean near = !w.getPlayers(p -> !p.isSpectator() && p.squaredDistanceTo(s.x(), p.getY(), s.z()) < 150 * 150).isEmpty();
        if (!near) { nearTicks = 0; return; }
        BlockPos spawn = spawnPos(w, site);
        if (!w.isChunkLoaded(spawn)) return;
        // entities load a moment after their chunk: wait before deciding the old one is really gone
        if (++nearTicks < 80) return;
        nearTicks = 0;
        spawn(w, st, site, spawn);
    }

    static BlockPos spawnPos(ServerWorld w, String site) {
        SiteLayout L = LeviathanSites.built(w.getSeed(), site, false).layout();
        int[] rel = L instanceof RiftLayout r ? r.spawn : L instanceof GulletLayout g ? g.spawn : ((SpireLayout) L).spawn;
        return at(w, site, rel);
    }

    static LeviathanEntity spawn(ServerWorld w, LeviathanState st, String site, BlockPos pos) {
        var type = ModMobs.TYPES.get("leviathan");
        if (type == null || !(type.create(w) instanceof LeviathanEntity lev)) return null;
        int form = site.equals(LeviathanRoute.RIFT) ? 1 : site.equals(LeviathanRoute.GULLET) ? 2 : 3;
        lev.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        lev.initialize(w, w.getLocalDifficulty(pos), SpawnReason.EVENT, null, null);
        lev.setup(site, form, st.health);
        lev.setPersistent();
        w.spawnEntity(lev);
        st.leviathan = lev.getUuid();
        st.markDirty();
        entered(form);
        return lev;
    }

    /** A Leviathan has just come into its lair (spawned there, or swum in from the route). */
    private static void entered(int form) {
        if (form == 3) { sealed = 0; for (int i = 0; i < 4; i++) { bellSilence[i] = 0; bellHits[i] = 0; } }
    }

    // ---------------------------------------------------------------- THE FLIGHT between lairs
    public static void retreated(ServerWorld w, LeviathanEntity lev) {
        LeviathanState st = LeviathanState.get(w);
        st.stage = st.stage == LeviathanState.PHASE1 ? LeviathanState.FLIGHT1 : LeviathanState.FLIGHT2;
        // the route starts at the lair's centre; it has already swum part of the first leg on its way out
        List<Vec3d> pts = flightPath(w, st.stage);
        Vec3d leg = pts.get(1).subtract(pts.get(0));
        st.flight = Math.max(0, lev.getPos().subtract(pts.get(0)).dotProduct(leg.normalize()));
        st.leviathan = null;
        st.markDirty();
        // and it stays in sight: the same creature swims on (LeviathanEntity travel mode)
        lev.beginTravel();
        wake = lev.getUuid();
    }

    static List<Vec3d> flightPath(ServerWorld w, int stage) {
        List<Vec3d> pts = new ArrayList<>();
        for (LeviathanRoute.Site s : route(w).flight(stage == LeviathanState.FLIGHT1 ? 1 : 2)) pts.add(new Vec3d(s.x() + 0.5, 58, s.z() + 0.5));
        return pts;
    }

    /** Where the fleeing Leviathan is `dist` blocks along its flight (and the heading there). */
    static Vec3d[] along(List<Vec3d> pts, double dist) {
        for (int i = 0; i + 1 < pts.size(); i++) {
            double len = pts.get(i).distanceTo(pts.get(i + 1));
            if (dist <= len) {
                Vec3d dir = pts.get(i + 1).subtract(pts.get(i)).normalize();
                return new Vec3d[]{pts.get(i).add(dir.multiply(dist)), dir};
            }
            dist -= len;
        }
        Vec3d last = pts.get(pts.size() - 1);
        return new Vec3d[]{last, pts.get(pts.size() - 1).subtract(pts.get(pts.size() - 2)).normalize(), null};
    }

    private static void flight(ServerWorld w, LeviathanState st) {
        List<Vec3d> pts = flightPath(w, st.stage);
        st.flight += FLIGHT_SPEED;
        Vec3d[] a = along(pts, st.flight);
        Vec3d pos = a[0], dir = a[1];
        // the port on its road
        int port = st.stage == LeviathanState.FLIGHT1 ? 0 : 1;
        LeviathanRoute.Site ps = route(w).byId(LeviathanPorts.IDS[port]);
        if (!st.portRuined[port] && ps.dist(pos.x, pos.z) < ps.radius() * 0.6) {
            LeviathanPorts.ruin(w, port, pos.x, pos.z);
            tellAll(w, Text.literal("Somewhere out on the sea, bells are ringing - and then they stop. " + LeviathanPorts.NAMES[port] + " is gone.")
                    .formatted(Formatting.DARK_RED, Formatting.ITALIC));
        }
        // the Leviathan itself, whole, swimming the route - whenever anyone is close enough to see it
        boolean watched = watchAll || !w.getPlayers(p -> p.squaredDistanceTo(pos.x, p.getY(), pos.z) < 256 * 256).isEmpty();
        LeviathanEntity tr = wake != null && w.getEntity(wake) instanceof LeviathanEntity e && e.isAlive() && e.traveling() ? e : null;
        if (watched && w.isChunkLoaded(BlockPos.ofFloored(pos))) {
            if (tr == null) tr = spawnTraveler(w, st, pos, dir);
            if (tr != null) tr.travelTo(pos, dir);
        } else if (tr != null) { tr.discardBody(w); tr.discard(); tr = null; wake = null; }
        if (a.length > 2) {                                                 // it has reached the next lair
            wake = null;
            st.stage = st.stage == LeviathanState.FLIGHT1 ? LeviathanState.PHASE2 : LeviathanState.PHASE3;
            st.flight = 0;
            st.markDirty();
            if (tr != null) {                                               // seen arriving: it simply becomes the lair's Leviathan
                int form = st.stage == LeviathanState.PHASE2 ? 2 : 3;
                tr.arrive(lairOf(st.stage), form, st.health);
                st.leviathan = tr.getUuid();
                entered(form);
            }
            tellAll(w, Text.literal(st.stage == LeviathanState.PHASE2 ? "It has gone to ground in THE GULLET. The Heartstone points the way."
                    : "It has reached THE DROWNING SPIRE. This is where it ends.").formatted(Formatting.DARK_AQUA, Formatting.BOLD));
        }
        if (st.flight % 20 < FLIGHT_SPEED) st.markDirty();
    }

    @Nullable
    private static LeviathanEntity spawnTraveler(ServerWorld w, LeviathanState st, Vec3d pos, Vec3d dir) {
        var type = ModMobs.TYPES.get("leviathan");
        if (type == null || !(type.create(w) instanceof LeviathanEntity lev)) return null;
        String from = st.stage == LeviathanState.FLIGHT1 ? LeviathanRoute.RIFT : LeviathanRoute.GULLET;
        lev.refreshPositionAndAngles(pos.x, pos.y, pos.z, (float) Math.toDegrees(Math.atan2(-dir.x, dir.z)), 0);
        lev.initialize(w, w.getLocalDifficulty(BlockPos.ofFloored(pos)), SpawnReason.EVENT, null, null);
        lev.setup(from, st.stage == LeviathanState.FLIGHT1 ? 1 : 2, st.health);
        lev.beginTravel();
        wake = lev.getUuid();
        w.spawnEntity(lev);
        return lev;
    }

    /** For the Heartstone: where the Leviathan is right now (null once it is dead). */
    @Nullable
    public static Vec3d whereIsIt(ServerWorld w) {
        LeviathanState st = LeviathanState.get(w);
        if (st.stage == LeviathanState.SLAIN) return null;
        LeviathanEntity l = living(w);
        if (l != null) return l.getPos();
        if (st.stage == LeviathanState.FLIGHT1 || st.stage == LeviathanState.FLIGHT2) return along(flightPath(w, st.stage), st.flight)[0];
        LeviathanRoute.Site s = route(w).byId(lairOf(st.stage));
        return new Vec3d(s.x() + 0.5, 60, s.z() + 0.5);
    }

    // ---------------------------------------------------------------- THE SPIRE: seal, bells, whirlpool, THE LAST TIDE
    public static void maelstromBoost(ServerWorld w, int ticks) { maelstrom = Math.max(maelstrom, ticks); }

    /** The whole lagoon loaded? (draining/unsealing waits for it rather than force-loading chunks from the tick) */
    private static boolean spireLoaded(ServerWorld w) {
        LeviathanRoute.Site s = route(w).byId(LeviathanRoute.SPIRE);
        int r = SpireLayout.WALL + 2;
        for (int cx = (s.x() - r) >> 4; cx <= (s.x() + r) >> 4; cx++)
            for (int cz = (s.z() - r) >> 4; cz <= (s.z() + r) >> 4; cz++) if (!w.isChunkLoaded(cx, cz)) return false;
        return true;
    }

    private static SpireLayout spireLayout(ServerWorld w) { return (SpireLayout) LeviathanSites.built(w.getSeed(), LeviathanRoute.SPIRE, false).layout(); }

    private static void spire(ServerWorld w, LeviathanState st, LeviathanEntity lev) {
        SpireLayout L = spireLayout(w);
        LeviathanRoute.Site s = route(w).byId(LeviathanRoute.SPIRE);
        // the arch comes down: the channel fills with rubble (everyone in the lagoon is shut in with it)
        if (sealed >= 0 && sealed < L.sealCells.size()) {
            if (sealed == 0) {
                tellAll(w, Text.literal("The sea arch over the Spire's channel COLLAPSES - there is no way out but over the rim.").formatted(Formatting.DARK_RED));
                st.channelSealed = true;
                st.markDirty();
            }
            int end = Math.min(L.sealCells.size(), sealed + 600);
            for (int k = sealed; k < end; k++) {
                int[] c = L.sealCells.get(k);
                BlockPos p = new BlockPos(s.x() + c[0], c[1], s.z() + c[2]);
                BlockState b = w.getBlockState(p);
                if (b.isAir() || b.isOf(Blocks.WATER)) w.setBlockState(p, rubble(k), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
            }
            if (end % 1200 < 600) w.playSound(null, new BlockPos(s.x() + L.sealCells.get(end - 1)[0], 80, s.z() + L.sealCells.get(end - 1)[2]), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 4.0f, 0.5f);
            sealed = end;
        }
        long now = w.getServer().getTicks();
        // the bells: ringing unless silenced
        int silenced = 0;
        for (int i = 0; i < L.bells.size(); i++) {
            BlockPos b = at(w, LeviathanRoute.SPIRE, L.bells.get(i));
            boolean ringing = bellSilence[i] <= 0;
            if (bellSilence[i] > 0 && --bellSilence[i] == 0) { bellHits[i] = 0; w.playSound(null, b, SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.BLOCKS, 4.0f, 0.5f); }
            if (!ringing) silenced++;
            BlockState bs = w.getBlockState(b);
            if (bs.isOf(ModBlocks.TIDE_BELL) && bs.get(LeviathanBlock.RINGING) != ringing) w.setBlockState(b, bs.with(LeviathanBlock.RINGING, ringing), Block.NOTIFY_LISTENERS);
            if (ringing && (now + i * 15) % 60 == 0) {
                w.playSound(null, b, SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 5.0f, 0.5f);
                w.spawnParticles(ParticleTypes.NOTE, b.getX() + 0.5, b.getY() + 1.5, b.getZ() + 0.5, 3, 0.4, 0.3, 0.4, 0.5);
            }
        }
        if (now % 800 == 0 && silenced > 0) {                                // TIDAL CALL: it rings them again
            for (int i = 0; i < 4; i++) { bellSilence[i] = 0; bellHits[i] = 0; }
            tellAll(w, Text.literal("It calls to the tide - every bell on the Spire rings out again!").formatted(Formatting.DARK_AQUA));
        }
        // THE LAST TIDE
        if (fillCells == null) {
            int interval = (int) (TIDE_INTERVAL * (1 + 0.6 * silenced));
            if (++tideTimer >= interval) { tideTimer = 0; beginLayer(w, st, L, s); }
        } else fillStep(w);
        // the whirlpool
        whirlpool(w, s, st, lev);
        // the Bane shaft: lost for good? it wears another from the wreckage
        if (now % 200 == 0 && !st.baneShaft) {
            Box box = new Box(s.x() - 72, 0, s.z() - 72, s.x() + 72, 130, s.z() + 72);
            boolean loose = !w.getEntitiesByClass(ItemEntity.class, box, e -> e.getStack().isOf(ModItems.BANE_SHAFT)).isEmpty()
                    || w.getPlayers(p -> p.squaredDistanceTo(s.x(), 80, s.z()) < 120 * 120 && BossProgression.has(p, ModItems.BANE_SHAFT)).size() > 0;
            if (!loose) lev.rearmCrown();
        }
    }

    static BlockState rubble(int k) {
        return switch (Math.floorMod(k * 7, 5)) { case 0 -> Blocks.MOSSY_COBBLESTONE.getDefaultState(); case 1 -> Blocks.STONE.getDefaultState();
            case 2 -> Blocks.COBBLESTONE.getDefaultState(); case 3 -> Blocks.ANDESITE.getDefaultState(); default -> Blocks.MOSSY_STONE_BRICKS.getDefaultState(); };
    }

    private static boolean inLagoon(LeviathanRoute.Site s, int x, int z) {
        double dx = x - s.x() - 0.5, dz = z - s.z() - 0.5;
        return dx * dx + dz * dz < (SpireLayout.LAGOON + 0.5) * (SpireLayout.LAGOON + 0.5);
    }

    private static void beginLayer(ServerWorld w, LeviathanState st, SpireLayout L, LeviathanRoute.Site s) {
        int y = SiteLayout.SEA + st.tideLayers + 1;
        if (y > SpireLayout.TIDE_MAX) { fail(w, st); return; }
        List<BlockPos> cells = new ArrayList<>();
        for (int x = s.x() - SpireLayout.LAGOON - 1; x <= s.x() + SpireLayout.LAGOON + 1; x++)
            for (int z = s.z() - SpireLayout.LAGOON - 1; z <= s.z() + SpireLayout.LAGOON + 1; z++)
                if (inLagoon(s, x, z)) cells.add(new BlockPos(x, y, z));
        fillCells = cells;
        fillCursor = 0;
        st.tideLayers++;
        st.markDirty();
        int left = SpireLayout.TIDE_MAX - y;
        if (left % 6 == 0 || left <= 3)
            tellAll(w, Text.literal("THE LAST TIDE rises up the Spire - " + left + " blocks below its crown. Silence the bells!").formatted(left <= 6 ? Formatting.RED : Formatting.DARK_AQUA));
    }

    private static void fillStep(ServerWorld w) {
        int end = Math.min(fillCells.size(), fillCursor + 2500);
        for (int k = fillCursor; k < end; k++) {
            BlockPos p = fillCells.get(k);
            if (w.getBlockState(p).isAir()) w.setBlockState(p, Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        }
        fillCursor = end;
        if (fillCursor >= fillCells.size()) fillCells = null;
    }

    private static void drainLayer(ServerWorld w, LeviathanState st) {
        LeviathanRoute.Site s = route(w).byId(LeviathanRoute.SPIRE);
        int y = SiteLayout.SEA + st.tideLayers;
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int x = s.x() - SpireLayout.LAGOON - 1; x <= s.x() + SpireLayout.LAGOON + 1; x++)
            for (int z = s.z() - SpireLayout.LAGOON - 1; z <= s.z() + SpireLayout.LAGOON + 1; z++) {
                if (!inLagoon(s, x, z)) continue;
                p.set(x, y, z);
                if (w.getBlockState(p).isOf(Blocks.WATER)) w.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
            }
        st.tideLayers--;
        fillCells = null;
        st.markDirty();
    }

    private static void unseal(ServerWorld w, LeviathanState st) {
        SpireLayout L = spireLayout(w);
        LeviathanRoute.Site s = route(w).byId(LeviathanRoute.SPIRE);
        for (int k = 0; k < L.sealCells.size(); k++) {
            int[] c = L.sealCells.get(k);
            BlockPos p = new BlockPos(s.x() + c[0], c[1], s.z() + c[2]);
            if (w.getBlockState(p).equals(rubble(k)))
                w.setBlockState(p, c[1] <= SiteLayout.SEA ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        }
        sealed = -1;
        st.channelSealed = false;
        st.markDirty();
    }

    private static void whirlpool(ServerWorld w, LeviathanRoute.Site s, LeviathanState st, LeviathanEntity lev) {
        double cx = s.x() + 0.5, cz = s.z() + 0.5;
        double k = maelstrom > 0 ? 2 : 1;
        Box box = new Box(cx - 52, SpireLayout.FLOOR, cz - 52, cx + 52, SiteLayout.SEA + st.tideLayers + 2, cz + 52);
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e.isTouchingWater() && !(e instanceof LeviathanEntity)
                && !(e instanceof net.get900.pixelpirates.entity.mob.LeviathanSegmentEntity))) {
            double dx = e.getX() - cx, dz = e.getZ() - cz, r = Math.sqrt(dx * dx + dz * dz);
            if (r < 9 || r > 50) continue;
            e.addVelocity(-dz / r * 0.03 * k - dx / r * 0.008 * k, 0, dx / r * 0.03 * k - dz / r * 0.008 * k);
            if (e instanceof ServerPlayerEntity) e.velocityModified = true;
        }
        if (w.getServer().getTicks() % 3 == 0) {
            double y = SiteLayout.SEA + st.tideLayers + 0.9, a0 = w.getServer().getTicks() * 0.05;
            for (int arm = 0; arm < 4; arm++) for (double r = 12; r < 48; r += 3) {
                double a = a0 + arm * Math.PI / 2 + r * 0.07;
                w.spawnParticles(ParticleTypes.BUBBLE_POP, cx + Math.cos(a) * r, y, cz + Math.sin(a) * r, 1, 0.3, 0, 0.3, 0);
            }
        }
    }

    /** THE LAST TIDE reached the Spire's crown: it dives into the maelstrom and is gone - until the horn calls it. */
    private static void fail(ServerWorld w, LeviathanState st) {
        LeviathanEntity lev = living(w);
        if (lev != null) {
            w.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, lev.getX(), lev.getY(), lev.getZ(), 400, 6, 6, 6, 0.4);
            lev.discard();
        }
        st.health = (lev != null ? lev.getMaxHealth() : 1024) * 0.33f;                // healed back to the phase's top
        st.stage = LeviathanState.FLED;
        st.leviathan = null;
        st.fails++;
        st.hornReadyAt = w.getServer().getTicks() + HORN_COOLDOWN;
        st.markDirty();
        fillCells = null;
        for (int i = 0; i < 4; i++) bellSilence[i] = BELL_SILENCE;
        tellAll(w, Text.literal("THE LAST TIDE reaches the Spire's crown. The Leviathan dives into the maelstrom and is gone - healed, waiting."
                + " The Watchers' Horn on the Spire will call it back in five minutes.").formatted(Formatting.DARK_RED, Formatting.BOLD));
    }

    /** It is dead. */
    public static void slain(ServerWorld w, LeviathanEntity lev) {
        LeviathanState st = LeviathanState.get(w);
        st.stage = LeviathanState.SLAIN;
        st.health = 0;
        st.leviathan = null;
        st.markDirty();
        fillCells = null;
        for (int i = 0; i < 4; i++) bellSilence[i] = Integer.MAX_VALUE / 2;
        // every hunter is credited, wherever they are
        for (UUID u : st.hunters) {
            ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(u);
            if (p == null || p.squaredDistanceTo(lev) < 200 * 200 || !BossProgression.eligible(p, CHAIN_INDEX)) continue;
            BossProgression.onBossKilled(p, CHAIN_INDEX, 0);
        }
        tellAll(w, Text.literal("The Leviathan is dead. The eclipse breaks, the tide falls back, and the sun comes up over the Maelstrom.")
                .formatted(Formatting.GOLD, Formatting.BOLD));
    }

    // =====================================================================================
    // THE BLOCKS
    // =====================================================================================
    public static void onBlockUsed(ServerWorld w, BlockPos pos, LeviathanBlock.Kind kind, PlayerEntity player, boolean attack) {
        LeviathanState st = LeviathanState.get(w);
        switch (kind) {
            case SEAL -> seal(w, st, player);
            case WINCH -> winch(w, st, pos, player);
            case BELL -> bell(w, st, pos, player);
            case BALLISTA -> { if (!attack) ballista(w, st, pos, player); }
            case HORN -> { if (!attack) horn(w, st, player); }
        }
    }

    private static void seal(ServerWorld w, LeviathanState st, PlayerEntity p) {
        if (st.stage == LeviathanState.SLAIN) { p.sendMessage(Text.literal("The seal is cold. What slept here is dead.").formatted(Formatting.GRAY), true); return; }
        if (st.stage != LeviathanState.SLEEPING) { p.sendMessage(Text.literal("The seal is broken. It is awake - follow the Heartstone.").formatted(Formatting.GRAY), true); return; }
        if (!BossProgression.eligible(p, CHAIN_INDEX)) { p.sendMessage(BossProgression.sealedMessage(p, CHAIN_INDEX), true); return; }
        p.sendMessage(Text.literal("\n[X] THE RIFT SEAL").formatted(Formatting.DARK_RED, Formatting.BOLD), false);
        p.sendMessage(Text.literal("Beneath this seal the Leviathan has slept for ten thousand years. Break it and it wakes - and it will not stop"
                + " until it is dead or the seas are. It will flee, feed, and destroy Saltmarrow and Brightwater on its way."
                + " There is only ONE, and it never returns once slain.").formatted(Formatting.GRAY), false);
        Text yes = Text.literal("[ BREAK THE SEAL ]").setStyle(Style.EMPTY.withColor(Formatting.RED).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ppleviathan awaken"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Wake the Leviathan. There is no going back."))));
        Text no = Text.literal("   [ leave it sleeping ]").formatted(Formatting.DARK_GRAY);
        p.sendMessage(Text.empty().append(yes).append(no), false);
    }

    /** /ppleviathan awaken - the confirmation from the seal's prompt. */
    public static int awaken(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        LeviathanState st = LeviathanState.get(w);
        if (!w.getRegistryKey().equals(ModDimensions.PIXEL_PIRATES_WORLD) || st.stage != LeviathanState.SLEEPING) return 0;
        BlockPos seal = at(w, LeviathanRoute.RIFT, ((RiftLayout) LeviathanSites.built(w.getSeed(), LeviathanRoute.RIFT, false).layout()).seal);
        if (p.squaredDistanceTo(Vec3d.ofCenter(seal)) > 16 * 16) { p.sendMessage(Text.literal("You must stand at the Rift Seal.").formatted(Formatting.GRAY), true); return 0; }
        if (!BossProgression.eligible(p, CHAIN_INDEX)) { p.sendMessage(BossProgression.sealedMessage(p, CHAIN_INDEX), true); return 0; }
        begin(w, st);
        w.playSound(null, seal, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 6.0f, 0.5f);
        w.spawnParticles(ParticleTypes.SCULK_SOUL, seal.getX() + 0.5, seal.getY() + 1, seal.getZ() + 0.5, 80, 1, 1, 1, 0.1);
        tellAll(w, Text.literal("Far out in the Pillar Sea, a seal breaks - and something vast opens its eyes.").formatted(Formatting.DARK_RED, Formatting.BOLD));
        return 1;
    }

    static void begin(ServerWorld w, LeviathanState st) {
        st.stage = LeviathanState.PHASE1;
        st.health = -1;
        java.util.Arrays.fill(st.chains, 100);
        st.crustBroken = 0;
        st.leviathan = null;
        st.markDirty();
        spawn(w, st, LeviathanRoute.RIFT, spawnPos(w, LeviathanRoute.RIFT));
    }

    private static void winch(ServerWorld w, LeviathanState st, BlockPos pos, PlayerEntity p) {
        LeviathanEntity lev = living(w);
        if (st.stage != LeviathanState.PHASE1 || lev == null) { p.sendMessage(Text.literal("The winch turns freely. Nothing pulls against it.").formatted(Formatting.GRAY), true); return; }
        long now = w.getServer().getTicks();
        Long last = winchCooldown.get(p.getUuid());
        if (last != null && now - last < 8) return;
        winchCooldown.put(p.getUuid(), now);
        RiftLayout L = (RiftLayout) LeviathanSites.built(w.getSeed(), LeviathanRoute.RIFT, false).layout();
        for (int i = 0; i < L.winches.size(); i++) if (at(w, LeviathanRoute.RIFT, L.winches.get(i)).equals(pos)) {
            lev.reinforce(w, i, p);
            w.playSound(null, pos, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 2.0f, 0.6f);
            return;
        }
    }

    private static void bell(ServerWorld w, LeviathanState st, BlockPos pos, PlayerEntity p) {
        if (st.stage != LeviathanState.PHASE3 || living(w) == null) return;
        SpireLayout L = spireLayout(w);
        for (int i = 0; i < L.bells.size(); i++) if (at(w, LeviathanRoute.SPIRE, L.bells.get(i)).equals(pos)) {
            if (bellSilence[i] > 0) { p.sendMessage(Text.literal("This bell is silent - for now.").formatted(Formatting.GRAY), true); return; }
            w.playSound(null, pos, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 2.0f, 0.9f);
            if (++bellHits[i] >= BELL_HITS) {
                bellSilence[i] = BELL_SILENCE;
                w.playSound(null, pos, SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.BLOCKS, 4.0f, 0.4f);
                w.spawnParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30, 0.5, 0.5, 0.5, 0.05);
                tellAll(w, Text.literal("A Tide Bell falls silent - the tide slows.").formatted(Formatting.AQUA));
            } else p.sendMessage(Text.literal("You hammer the bell to silence it (" + bellHits[i] + "/" + BELL_HITS + ")").formatted(Formatting.AQUA), true);
            return;
        }
    }

    private static void ballista(ServerWorld w, LeviathanState st, BlockPos pos, PlayerEntity p) {
        ItemStack held = p.getMainHandStack();
        boolean changed = false;
        if (held.isOf(ModItems.ABYSSAL_HEARTSTONE) && !st.baneHeartstone) {
            st.baneHeartstone = true; changed = true;
            if (!p.isCreative()) held.decrement(1);
            p.sendMessage(Text.literal("You set the Heartstone in the Bane's cradle. It glows, and the bow creaks tight.").formatted(Formatting.GOLD), false);
        } else if (held.isOf(ModItems.BANE_SHAFT) && !st.baneShaft) {
            st.baneShaft = true; changed = true;
            if (!p.isCreative()) held.decrement(1);
            p.sendMessage(Text.literal("You lay the Bane Shaft in its track.").formatted(Formatting.GOLD), false);
        } else if (held.isOf(Items.GUNPOWDER) && st.banePowder < LeviathanState.BANE_POWDER) {
            int take = Math.min(held.getCount(), LeviathanState.BANE_POWDER - st.banePowder);
            st.banePowder += take; changed = true;
            if (!p.isCreative()) held.decrement(take);
            p.sendMessage(Text.literal("You pack powder into the Bane's charge (" + st.banePowder + "/" + LeviathanState.BANE_POWDER + ")").formatted(Formatting.GOLD), true);
        }
        if (changed) {
            st.markDirty();
            BlockState bs = w.getBlockState(pos);
            if (bs.isOf(ModBlocks.BANE_BALLISTA)) w.setBlockState(pos, bs.with(LeviathanBlock.LOADED, st.baneLoaded()), Block.NOTIFY_LISTENERS);
            w.playSound(null, pos, SoundEvents.ITEM_CROSSBOW_LOADING_END, SoundCategory.BLOCKS, 2.0f, 0.6f);
            return;
        }
        if (!st.baneLoaded()) {
            p.sendMessage(Text.literal("THE BANE needs: " + (st.baneHeartstone ? "" : "the Heartstone, ") + (st.baneShaft ? "" : "the Bane Shaft (it wears it in its crown), ")
                    + (st.banePowder >= LeviathanState.BANE_POWDER ? "" : (LeviathanState.BANE_POWDER - st.banePowder) + " gunpowder")).formatted(Formatting.GRAY), false);
            return;
        }
        LeviathanEntity lev = living(w);
        if (lev == null || !lev.reared()) { p.sendMessage(Text.literal("Its core is not exposed - wait for it to rear against the Spire.").formatted(Formatting.GRAY), true); return; }
        w.spawnEntity(BaneBoltEntity.fire(w, Vec3d.ofCenter(pos).add(0, 1.2, 0), lev));
        w.playSound(null, pos, SoundEvents.ITEM_CROSSBOW_SHOOT, SoundCategory.BLOCKS, 6.0f, 0.3f);
        w.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 3.0f, 0.7f);
        w.spawnParticles(ParticleTypes.EXPLOSION, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 3, 0.5, 0.5, 0.5, 0);
    }

    private static void horn(ServerWorld w, LeviathanState st, PlayerEntity p) {
        if (st.stage != LeviathanState.FLED) { p.sendMessage(Text.literal("The Watchers' Horn. Its note would carry across the whole sea.").formatted(Formatting.GRAY), true); return; }
        long wait = st.hornReadyAt - w.getServer().getTicks();
        if (wait > 0) { p.sendMessage(Text.literal("The sea is still churning - the horn will carry in " + (wait / 20) + " s.").formatted(Formatting.GRAY), true); return; }
        st.stage = LeviathanState.PHASE3;
        st.markDirty();
        tideTimer = 0;
        w.playSound(null, p.getBlockPos(), SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.BLOCKS, 16.0f, 0.5f);
        tellAll(w, Text.literal("The Watchers' Horn sounds across the sea - and out of the depths, the Leviathan answers.").formatted(Formatting.DARK_RED, Formatting.BOLD));
    }

    // =====================================================================================
    // /ppleviathan (testing)
    // =====================================================================================
    public static String status(ServerWorld w) {
        LeviathanState st = LeviathanState.get(w);
        LeviathanEntity l = living(w);
        LeviathanRoute.Route r = route(w);
        StringBuilder s = new StringBuilder("Hunt: " + LeviathanState.STAGE_NAMES[st.stage] + ", health " + (st.health < 0 ? "full" : (int) st.health)
                + ", chains " + java.util.Arrays.toString(st.chains) + ", crust " + Long.bitCount(st.crustBroken) + " broken, ports "
                + (st.portRuined[0] ? "SALTMARROW RUINED" : "saltmarrow ok") + "/" + (st.portRuined[1] ? "BRIGHTWATER RUINED" : "brightwater ok")
                + ", bane " + (st.baneHeartstone ? "H" : "-") + (st.baneShaft ? "S" : "-") + st.banePowder + ", tide " + st.tideLayers + ", hunters " + st.hunters.size());
        if (st.stage == LeviathanState.FLIGHT1 || st.stage == LeviathanState.FLIGHT2) {
            s.append(", flight ").append((int) st.flight).append(" at ").append(BlockPos.ofFloored(whereIsIt(w)).toShortString());
            if (wake != null && w.getEntity(wake) instanceof LeviathanEntity tr) s.append("\nIN SIGHT ").append(tr.getUuid().toString(), 0, 8).append(": ").append(tr.debugStatus());
            else s.append("\n(unseen)");
        }
        if (l != null) s.append("\n").append(l.getUuid().toString(), 0, 8).append(": ").append(l.debugStatus());
        s.append("\nSites: ");
        for (LeviathanRoute.Site x : r.all()) s.append(x.id()).append(" ").append(x.x()).append(",").append(x.z()).append("  ");
        return s.toString();
    }

    /** Test ops: wake | summon | horn | bell I | stage N | flee | arrive | ruin P | restore P | tide N | drain | bane | kill | reset | act NAME. */
    public static String debug(ServerWorld w, String op, String arg) {
        LeviathanState st = LeviathanState.get(w);
        LeviathanEntity l = living(w);
        switch (op) {
            case "wake" -> { if (st.stage == LeviathanState.SLEEPING) begin(w, st); }
            case "stage" -> {
                int n = Integer.parseInt(arg);
                if (l != null) { l.discard(); st.leviathan = null; }
                st.stage = n; st.flight = 0; st.markDirty();
                if (n == LeviathanState.PHASE2 || n == LeviathanState.PHASE3) st.health = 1024 * (n == LeviathanState.PHASE2 ? 0.66f : 0.33f);
            }
            case "watch" -> watchAll = !watchAll;
            case "summon" -> {                                              // spawn it in its current lair without a player near
                if (l == null && (st.stage == LeviathanState.PHASE1 || st.stage == LeviathanState.PHASE2 || st.stage == LeviathanState.PHASE3))
                    spawn(w, st, lairOf(st.stage), spawnPos(w, lairOf(st.stage)));
            }
            case "horn" -> { st.hornReadyAt = 0; if (st.stage == LeviathanState.FLED) { st.stage = LeviathanState.PHASE3; tideTimer = 0; st.markDirty(); } }
            case "bell" -> { int i = Integer.parseInt(arg); bellSilence[i] = BELL_SILENCE; }
            case "flee" -> { if (l != null) l.retreat(w); }
            case "arrive" -> {                                              // skip the swim (the port on the way still falls)
                if (st.stage == LeviathanState.FLIGHT1 || st.stage == LeviathanState.FLIGHT2) {
                    int port = st.stage == LeviathanState.FLIGHT1 ? 0 : 1;
                    LeviathanRoute.Site ps = route(w).byId(LeviathanPorts.IDS[port]);
                    if (!st.portRuined[port]) LeviathanPorts.ruin(w, port, ps.x(), ps.z());
                    st.flight = 1e9;
                }
            }
            case "ruin" -> LeviathanPorts.ruin(w, Integer.parseInt(arg) - 1, route(w).byId(LeviathanPorts.IDS[Integer.parseInt(arg) - 1]).x(), route(w).byId(LeviathanPorts.IDS[Integer.parseInt(arg) - 1]).z());
            case "restore" -> LeviathanPorts.restore(w, Integer.parseInt(arg) - 1);
            case "tide" -> { st.tideLayers = Math.max(0, Integer.parseInt(arg)); st.markDirty(); tideTimer = TIDE_INTERVAL * 10; }
            case "drain" -> { while (st.tideLayers > 0) drainLayer(w, st); }
            case "bane" -> { st.baneHeartstone = true; st.baneShaft = true; st.banePowder = LeviathanState.BANE_POWDER; st.markDirty(); }
            case "kill" -> { if (l != null) l.damage(w.getDamageSources().genericKill(), 1e6f); }
            case "reset" -> {
                if (l != null) l.discard();
                for (int i = 0; i < 2; i++) if (st.portRuined[i]) LeviathanPorts.restore(w, i);
                while (st.tideLayers > 0) drainLayer(w, st);
                st.stage = LeviathanState.SLEEPING; st.health = -1; st.flight = 0; st.leviathan = null; st.crustBroken = 0;
                java.util.Arrays.fill(st.chains, 100); st.baneHeartstone = st.baneShaft = false; st.banePowder = 0; st.hunters.clear(); st.markDirty();
            }
            case "check" -> { String[] a = arg.split("\\."); return LeviathanSites.check(w, a[0], a.length > 1); }
            case "act" -> { return l == null ? "no Leviathan loaded" : l.debug(arg); }
            default -> { return "unknown op " + op; }
        }
        return status(w);
    }
}
