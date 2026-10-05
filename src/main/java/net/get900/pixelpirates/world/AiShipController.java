package net.get900.pixelpirates.world;

import net.get900.pixelpirates.entity.ModEntities;
import net.get900.pixelpirates.entity.custom.CannonBallEntity;
import net.get900.pixelpirates.entity.custom.CaptainEntity;
import net.get900.pixelpirates.entity.custom.PirateCrewEntity;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.entity.EquipmentSlot;
import net.get900.pixelpirates.world.faction.Faction;
import net.get900.pixelpirates.world.faction.FactionManager;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4dc;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.core.internal.world.VsiServerShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Server-side AI controller for NPC faction ships.
 * Supports four factions: PIRATES (always hostile), MERCHANTS, NAVY, UNDEAD.
 * Non-pirate factions respect player reputation — they won't attack players above REP_HOSTILE.
 * Faction ships also target enemy faction ships when no player target is available.
 *
 * State machine: PATROL → APPROACH → BROADSIDE ↔ APPROACH
 *                                         ↘ RETREAT
 */
public class AiShipController {

    private static final Logger LOGGER = LoggerFactory.getLogger("PixelPirates-AI");

    // ── State ─────────────────────────────────────────────────────────────────

    public enum AiState { PATROL, APPROACH, BROADSIDE, RETREAT }

    /** Unified targeting abstraction — player or enemy faction ship. */
    private static final class TargetInfo {
        final Vec3d pos;
        final double dist;
        final ServerPlayerEntity player; // null when targeting an enemy faction ship
        TargetInfo(Vec3d pos, double dist, ServerPlayerEntity player) {
            this.pos = pos; this.dist = dist; this.player = player;
        }
    }

    public static class AiShipData {
        public final long        shipId;
        public final ServerWorld world;

        public AiState state      = AiState.PATROL;
        public int     stateTicks = 0;

        public int   patrolTurnTimer = 0;
        public float patrolTurnDir   = 1f;

        public boolean broadsideLeft = true;

        // Ship-space positions of cannon blocks (original world coords at assembly time).
        // Transformed to world space via getShipToWorld() at fire time.
        public final List<Vector3d> cannonShipPositions = new ArrayList<>();

        public Vector3d smoothedHeading = new Vector3d(0, 0, 1);
        public Vector3d prevShipPos     = null;

        public Vec3d prevTargetPos = null;

        // 0 = cannons ready, >0 = reloading
        public int reloadTimer    = 40;
        public int missingTicks   = 0;
        public int stuckCheckTick = 0;
        public Vec3d stuckRefPos  = null;
        public float stuckBoost   = 0f;
        // Ticks spent in PATROL with no target; ship is deleted at 9600 (8 min)
        public int idleTicks      = 0;

        // Crew spawning — delayed 40 ticks after registration to let VS2 finish assembly
        public boolean    crewSpawned    = false;
        public int        crewSpawnDelay = 40;
        public List<UUID> crewEntityIds  = new ArrayList<>();
        /** Each crew member's post in SHIP space - keepCrewAboard() follows the ship with it. */
        public final Map<UUID, Vector3d> crewPosts = new HashMap<>();
        // UUID of the captain entity (null for Merchant/Navy ships; present in crewEntityIds)
        public UUID captainEntityId = null;

        // Bell sound — ships ring periodically so players can navigate toward them.
        public int bellTimer = 20 + (int)(Math.random() * 580);

        /** A HUNTER (2026-10-05, half the pirate + drowned ships): seeks out players from {@link #HUNT_RANGE} and chases. */
        public boolean hunter = false;

        /** THE REGATTA (homestead/town/Regatta): a racer sails for raceTarget at full sail and never fights. */
        public boolean racing = false;
        public Vec3d raceTarget = null;
        public float raceSkill = 1f;

        public String       blueprintName = "";
        public AiShipConfig config        = AiShipConfig.DEFAULTS;
        public Faction      faction       = Faction.PIRATES;

        AiShipData(long shipId, ServerWorld world) {
            this.shipId = shipId;
            this.world  = world;
        }
    }

    public static final ConcurrentHashMap<Long, AiShipData> AI_SHIPS = new ConcurrentHashMap<>();

    // ── Tuning constants ──────────────────────────────────────────────────────

    private static final int  GRACE_TICKS          = 120;
    private static final int  PATROL_TURN_TICKS    = 260;
    private static final int  STUCK_CHECK_INTERVAL = 60;
    private static final double STUCK_THRESHOLD    = 1.5;
    private static final float  STUCK_BOOST        = 1.4f;
    private static final int  FIRE_JITTER          = 20;

    private static final double CANNON_SPEED       = 2.5;
    private static final double CANNON_GRAVITY     = 0.04;
    private static final double LEAD_FACTOR        = 0.75;
    /** How far a hunter sees and chases players (within VS2's ship load distance, ShipSimDistance). */
    public static final double HUNT_RANGE          = 300;

    private static double detection(AiShipData d) { return d.hunter ? Math.max(d.config.detectionRange, HUNT_RANGE) : d.config.detectionRange; }
    private static double chase(AiShipData d) { return d.hunter ? Math.max(d.config.chaseRange, HUNT_RANGE + 40) : d.config.chaseRange; }

    // ── Registration ─────────────────────────────────────────────────────────

    public static void registerAiShip(long shipId, ServerWorld world,
                                       List<Vector3d> cannonShipPositions,
                                       String blueprintName) {
        registerAiShip(shipId, world, cannonShipPositions, blueprintName, null);
    }

    /**
     * Registers an AI ship with an optional faction override.
     * When factionOverride is non-null it takes precedence over the faction field in the
     * blueprint's JSON config. This lets the natural spawn system assign e.g. MERCHANTS
     * to a sloop blueprint when no faction-specific merchant schematic exists yet.
     */
    public static void registerAiShip(long shipId, ServerWorld world,
                                       List<Vector3d> cannonShipPositions,
                                       String blueprintName,
                                       @org.jetbrains.annotations.Nullable Faction factionOverride) {
        AiShipData data = new AiShipData(shipId, world);
        data.cannonShipPositions.addAll(cannonShipPositions);
        data.blueprintName = blueprintName;
        data.config        = AiShipConfig.load(blueprintName);
        data.faction       = factionOverride != null ? factionOverride : Faction.fromId(data.config.faction);
        data.hunter = (data.faction == Faction.PIRATES || data.faction == Faction.UNDEAD) && Math.random() < 0.5;
        AI_SHIPS.put(shipId, data);
        int mastCount = ShipSteeringManager.MAST_COUNTS.getOrDefault(shipId, 1);
        ShipRegistryState registry = ShipRegistryState.get(world.getServer().getOverworld());
        registry.saveAiShip(shipId, mastCount, cannonShipPositions);
        registry.saveBlueprintName(shipId, blueprintName);
        LOGGER.info("[AI] Registered {} ship {} ('{}') with {} cannons in {}",
            data.faction.id, shipId, blueprintName, cannonShipPositions.size(),
            world.getRegistryKey().getValue());
    }

    /** Convenience accessor — returns the faction of a registered AI ship. */
    public static Faction getFactionForShip(long shipId) {
        AiShipData data = AI_SHIPS.get(shipId);
        return data != null ? data.faction : Faction.PIRATES;
    }

    /**
     * Forcibly despawns an AI ship: kills crew, deletes VS2 ship, cleans all maps.
     * Used by admin commands. If the shipId isn't an AI ship, this is a no-op.
     */
    public static boolean despawn(long shipId, MinecraftServer server) {
        AiShipData data = AI_SHIPS.remove(shipId);
        if (data == null) return false;
        GhostShipEncounter.forget(shipId);
        ShipSteeringManager.SHIP_INPUTS.remove(shipId);
        for (UUID uuid : data.crewEntityIds) {
            Entity crew = data.world.getEntity(uuid);
            if (crew != null) crew.discard();
        }
        VsiServerShipWorld sw = VSGameUtilsKt.getShipObjectWorld(data.world);
        if (sw != null) {
            org.valkyrienskies.core.api.ships.ServerShip s = sw.getLoadedShips().getById(shipId);
            if (s != null) sw.deleteShip(s);
        }
        ShipSteeringManager.MAST_COUNTS.remove(shipId);
        ShipRegistryState.get(server.getOverworld()).removeShip(shipId);
        return true;
    }

    // ── Tick entry point ──────────────────────────────────────────────────────

    public static void tick(MinecraftServer server) {
        if (AI_SHIPS.isEmpty()) return;

        // Collect VS2 ships across all dimensions
        Map<Long, LoadedServerShip> allShips = new HashMap<>();
        for (ServerWorld world : server.getWorlds()) {
            VsiServerShipWorld sw = VSGameUtilsKt.getShipObjectWorld(world);
            if (sw != null) allShips.putAll(sw.getLoadedShips().getIdToShipData());
        }

        List<ServerPlayerEntity> allPlayers = server.getPlayerManager().getPlayerList();

        Iterator<Map.Entry<Long, AiShipData>> it = AI_SHIPS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, AiShipData> entry = it.next();
            AiShipData data = entry.getValue();

            LoadedServerShip ship = allShips.get(data.shipId);
            if (ship == null) ship = loaded(data.world, data.shipId);              // a just-assembled ship can be missing from the id map a moment
            if (ship == null) {
                if (data.racing && data.missingTicks % 100 == 0) LOGGER.warn("[Regatta] racer {} not loaded ({} ticks)", data.shipId, data.missingTicks);
                if (++data.missingTicks > GRACE_TICKS && !data.racing) {
                    LOGGER.warn("[AI] Ship {} missing — deregistering", data.shipId);
                    ShipSteeringManager.SHIP_INPUTS.remove(data.shipId);
                    it.remove();
                }
                continue;
            }
            data.missingTicks = 0;

            ShipTransform tf      = ship.getTransform();
            Vector3d      shipPos = new Vector3d(tf.getPositionInWorld());
            Vector3d      shipFwd = resolveHeading(tf, data, shipPos);

            // Players in this ship's world
            List<ServerPlayerEntity> players = allPlayers.stream()
                    .filter(p -> p.getServerWorld() == data.world)
                    .collect(Collectors.toList());

            // ── Target selection ──────────────────────────────────────────────
            TargetInfo targetInfo = selectTarget(data, shipPos, players, allShips);

            int hp    = ShipHealthState.get(data.world).getHealth(data.shipId);
            boolean lowHp = hp < ShipHealthState.MAX_HP * data.config.retreatHpFrac;

            if (hp <= 0 && GhostShipEncounter.isDutchman(data.blueprintName)) {
                GhostShipEncounter.onSunk(data, server);
                it.remove();
                continue;
            }
            if (hp <= 0) {
                makeDerelict(data.shipId, data.world, server, data.blueprintName,
                             data.captainEntityId, null, data.faction);
                it.remove();
                continue;
            }

            updateStuckDetection(data, shipPos);
            data.stateTicks++;
            if (data.reloadTimer > 0) data.reloadTimer--;

            // ── Crew spawn (40-tick delay lets VS2 finish ship assembly) ─────
            if (!data.crewSpawned && --data.crewSpawnDelay <= 0) {
                spawnCrew(ship, data);
                data.crewSpawned = true;
            }
            if (data.crewSpawned) keepCrewAboard(ship, data);

            // ── A regatta racer: no targets, no guns, no idle despawn - just the next buoy ──
            if (data.racing) {
                if (data.stateTicks == 1) LOGGER.info("[Regatta] racer {} ({}) under AI race control", data.shipId, data.blueprintName);
                ShipSteeringManager.SHIP_INPUTS.put(data.shipId, raceInputs(data, shipFwd, shipPos));
                data.prevShipPos = new Vector3d(shipPos);
                continue;
            }

            // ── Idle despawn (8 min = 9600 ticks with no target) ─────────────
            if (data.state == AiState.PATROL && targetInfo == null) {
                if (++data.idleTicks >= 9600) {
                    deleteIdleShip(data);
                    ShipSteeringManager.SHIP_INPUTS.remove(data.shipId);
                    it.remove();
                    continue;
                }
            } else {
                data.idleTicks = 0;
            }

            // ── State machine ─────────────────────────────────────────────────
            double dist = targetInfo != null ? targetInfo.dist : Double.MAX_VALUE;

            switch (data.state) {
                case PATROL -> {
                    if (lowHp) {
                        transition(data, AiState.RETREAT);
                    } else if (targetInfo != null && dist < detection(data)) {
                        LOGGER.info("[AI] {} ({}) spotted target at {} blocks — APPROACH",
                            data.shipId, data.faction.id, (int)dist);
                        transition(data, AiState.APPROACH);
                    }
                }

                case APPROACH -> {
                    if (lowHp) {
                        transition(data, AiState.RETREAT);
                    } else if (targetInfo == null || dist > chase(data)) {
                        transition(data, AiState.PATROL);
                    } else if (dist <= data.config.engageRange) {
                        data.broadsideLeft = chooseBroadsideSide(shipFwd, shipPos, targetInfo.pos);
                        LOGGER.info("[AI] {} in range — BROADSIDE (left={})", data.shipId, data.broadsideLeft);
                        transition(data, AiState.BROADSIDE);
                    }
                }

                case BROADSIDE -> {
                    if (lowHp) {
                        transition(data, AiState.RETREAT);
                    } else if (targetInfo == null || dist > data.config.engageRange * 2.5) {
                        transition(data, AiState.APPROACH);
                    } else if (data.reloadTimer == 0
                            && (isBroadsideAligned(tf, shipPos, targetInfo.pos, data.config.alignThreshold)
                                || data.stateTicks > 300)) {
                        fireCannonVolley(data, tf, shipPos, shipFwd, targetInfo);
                        data.reloadTimer = data.config.reloadTicks
                            + (int)(Math.random() * FIRE_JITTER) - FIRE_JITTER / 2;
                        data.stateTicks = 0;
                    }
                }

                case RETREAT -> {
                    if (targetInfo != null && data.reloadTimer == 0) {
                        fireCannonVolley(data, tf, shipPos, shipFwd, targetInfo);
                        data.reloadTimer = data.config.reloadTicks;
                    }
                    if (!lowHp && (targetInfo == null || dist > detection(data))) {
                        transition(data, AiState.PATROL);
                    }
                }
            }

            // ── Ship bell (PATROL / APPROACH only) ────────────────────────────
            if (data.state == AiState.PATROL || data.state == AiState.APPROACH) {
                if (--data.bellTimer <= 0) {
                    data.world.playSound(null,
                        shipPos.x, shipPos.y, shipPos.z,
                        SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS,
                        8.0f, 0.65f + (float)(Math.random() * 0.15));
                    data.bellTimer = 500 + (int)(Math.random() * 200);
                }
            }

            float[] inputs = computeInputs(data, shipFwd, shipPos, targetInfo);
            ShipSteeringManager.SHIP_INPUTS.put(data.shipId, inputs);

            data.prevShipPos   = new Vector3d(shipPos);
            data.prevTargetPos = targetInfo != null ? targetInfo.pos : null;
        }
    }

    // ── Target selection ──────────────────────────────────────────────────────

    /**
     * Finds the best target for this ship:
     * 1. Nearest player if the faction is hostile to them (rep check for non-Pirates).
     * 2. Nearest enemy-faction AI ship if no hostile player is found or is closer.
     * Returns null if nothing is in detection range.
     */
    private static TargetInfo selectTarget(AiShipData data, Vector3d shipPos,
                                            List<ServerPlayerEntity> players,
                                            Map<Long, LoadedServerShip> allShips) {
        TargetInfo best = null;

        // ── Player targets ────────────────────────────────────────────────────
        for (ServerPlayerEntity p : players) {
            Vec3d pp = p.getPos();
            double d = distXZ(shipPos, pp);
            if (d > detection(data)) continue;
            // the Flying Dutchman hunts whoever rang its bell, regardless of Undead reputation
            if (!GhostShipEncounter.isDutchman(data.blueprintName) && !FactionManager.isHostileTo(data.faction, p)) continue;
            if (best == null || d < best.dist) {
                best = new TargetInfo(pp, d, p);
            }
        }

        // ── Enemy faction ship targets ─────────────────────────────────────────
        for (Map.Entry<Long, AiShipData> other : AI_SHIPS.entrySet()) {
            long otherId = other.getKey();
            if (otherId == data.shipId) continue;
            AiShipData otherData = other.getValue();
            if (otherData.world != data.world || otherData.racing) continue;           // nobody fires on a regatta racer
            if (!data.faction.isEnemyFaction(otherData.faction)) continue;
            LoadedServerShip otherShip = allShips.get(otherId);
            if (otherShip == null) continue;
            Vector3d otherPos = new Vector3d(otherShip.getTransform().getPositionInWorld());
            double d = distXZ(shipPos, otherPos);
            if (d > data.config.detectionRange) continue;
            if (best == null || d < best.dist) {
                best = new TargetInfo(new Vec3d(otherPos.x, otherPos.y, otherPos.z), d, null);
            }
        }

        return best;
    }

    // ── Steering ─────────────────────────────────────────────────────────────

    private static float[] computeInputs(AiShipData data, Vector3d shipFwd,
                                          Vector3d shipPos, TargetInfo target) {
        float fwd = 0f, turn = 0f, sprint = 0f;
        float stuckMult = 1f + data.stuckBoost;

        switch (data.state) {
            case PATROL -> {
                fwd = data.config.patrolSpeed;
                data.patrolTurnTimer++;
                if (data.patrolTurnTimer >= PATROL_TURN_TICKS) {
                    data.patrolTurnTimer = 0;
                    data.patrolTurnDir = (Math.random() > 0.45) ? -data.patrolTurnDir : data.patrolTurnDir;
                }
                if (data.patrolTurnTimer < 70) turn = data.patrolTurnDir * 0.45f * stuckMult;
            }

            case APPROACH -> {
                if (target != null) {
                    Vector3d toTarget = dir2d(target.pos.x - shipPos.x, target.pos.z - shipPos.z);
                    double cross = cross2d(shipFwd, toTarget);
                    double dot   = dot2d(shipFwd, toTarget);
                    float rawTurn = (float)(cross * 1.8);
                    turn = Math.signum(rawTurn) * Math.min(Math.abs(rawTurn) * stuckMult, 1.0f);
                    boolean headingAligned = dot > 0.7;
                    fwd = headingAligned ? (target.dist > data.config.engageRange * 1.5 ? 1.0f : 0.65f) : 0.6f;
                    if (headingAligned && target.dist > data.config.engageRange * 2.0) sprint = 1.0f;
                }
            }

            case BROADSIDE -> {
                if (target != null) {
                    Vector3d toTarget = dir2d(target.pos.x - shipPos.x, target.pos.z - shipPos.z);
                    Vector3d orbitFwd = data.broadsideLeft
                            ? new Vector3d(-toTarget.z, 0, toTarget.x)
                            : new Vector3d(toTarget.z, 0, -toTarget.x);

                    double cross = cross2d(shipFwd, orbitFwd);
                    turn = (float) Math.max(-1.0, Math.min(1.0, cross * 1.2));

                    if (target.dist < data.config.broadsideMin) {
                        fwd = 0f;
                    } else if (target.dist < data.config.orbitRange) {
                        fwd = 0.3f;
                    } else if (target.dist <= data.config.broadsideRange) {
                        fwd = 0.5f;
                    } else {
                        fwd = 0.75f;
                    }
                }
            }

            case RETREAT -> {
                if (target != null) {
                    Vector3d away = dir2d(shipPos.x - target.pos.x, shipPos.z - target.pos.z);
                    double cross = cross2d(shipFwd, away);
                    double dot   = dot2d(shipFwd, away);
                    float rawTurn = (float)(cross * 2.0);
                    turn   = Math.signum(rawTurn) * Math.min(Math.abs(rawTurn) * stuckMult, 1.0f);
                    fwd    = dot > 0.5 ? 1.0f : 0.5f;
                    sprint = dot > 0.5 ? 1.0f : 0f;
                } else {
                    fwd = 0.6f;
                }
            }
        }
        // MEASURED (the Dutchman 2026-09-29, then every regatta cutter 2026-10-05): a positive turn input swings the bow
        // clockwise-from-above, i.e. AWAY from the side the cross product says to turn toward - so EVERY ship flips it
        // (before this only the Dutchman did, and the rest of the fleet steered away from its targets).
        turn = -turn;
        return new float[]{fwd * data.config.speedMult, turn, sprint};
    }

    /** Full sail for the next buoy; ease off in a hard turn so she comes round instead of sliding past. */
    private static float[] raceInputs(AiShipData data, Vector3d shipFwd, Vector3d shipPos) {
        if (data.raceTarget == null) return new float[]{0f, 0f, 0f};
        Vector3d to = dir2d(data.raceTarget.x - shipPos.x, data.raceTarget.z - shipPos.z);
        double cross = cross2d(shipFwd, to), dot = dot2d(shipFwd, to);
        float raw = (float) (cross * 1.8);
        float turn = Math.signum(raw) * Math.min(Math.abs(raw) * (1f + data.stuckBoost), 1.0f);
        if (dot < 0) turn = cross >= 0 ? 1f : -1f;                                       // it's behind us: hard over
        // MEASURED 2026-10-05 (regatta logs, all three cutters): a positive turn input swings the bow AWAY from the side
        // the cross product points to - the same thing GhostShipEncounter found for the Dutchman - so flip it.
        turn = -turn;
        float fwd = (dot > 0.6 ? 1.0f : dot > 0 ? 0.6f : 0.35f) * data.raceSkill;         // ease off to come about tighter
        float sprint = dot > 0.85 ? 1f : 0f;
        return new float[]{fwd * data.config.speedMult, turn, sprint};
    }

    /** A loaded ship by id, or null. */
    public static LoadedServerShip loaded(ServerWorld world, long shipId) {
        VsiServerShipWorld sw = VSGameUtilsKt.getShipObjectWorld(world);
        return sw == null ? null : sw.getLoadedShips().getById(shipId);
    }

    /** World -> ship space for a loaded ship (null if it isn't loaded). */
    public static Vector3d toShip(ServerWorld world, long shipId, Vector3d worldPos) {
        LoadedServerShip s = loaded(world, shipId);
        return s == null ? null : s.getTransform().getWorldToShip().transformPosition(worldPos, new Vector3d());
    }

    /** Ship -> world space for a loaded ship (null if it isn't loaded). */
    public static Vector3d toWorld(ServerWorld world, long shipId, Vector3d shipPos) {
        LoadedServerShip s = loaded(world, shipId);
        return s == null ? null : s.getTransform().getShipToWorld().transformPosition(shipPos, new Vector3d());
    }

    /** The world position of a loaded ship, or null. */
    public static Vector3d shipPos(ServerWorld world, long shipId) {
        VsiServerShipWorld sw = VSGameUtilsKt.getShipObjectWorld(world);
        if (sw == null) return null;
        LoadedServerShip s = sw.getLoadedShips().getById(shipId);
        return s == null ? null : new Vector3d(s.getTransform().getPositionInWorld());
    }

    /** Spawn an AI ship from a blueprint at {@code origin} (bow +Z) and register it; its ship id. Throws if the spot is taken. */
    public static long spawn(ServerWorld world, String blueprint, net.minecraft.util.math.BlockPos origin, int margin) throws Exception {
        ShipSchematic schematic = ShipSchematic.load(blueprint);
        org.valkyrienskies.core.api.ships.ServerShip ship = ShipSpawner.spawn(world, schematic, origin, margin);
        org.joml.Matrix4d worldToShip = new org.joml.Matrix4d(ship.getTransform().getShipToWorld()).invert();
        List<Vector3d> cannons = new ArrayList<>();
        for (ShipSchematic.Entry e : schematic.getEntries()) {
            net.minecraft.block.BlockState bs = ShipSchematic.restoreState(e.stateNbt());
            if (bs != null && bs.isOf(net.get900.pixelpirates.block.ModBlocks.SHIP_CANNON)) {
                net.minecraft.util.math.BlockPos wp = origin.add(e.relPos());
                cannons.add(worldToShip.transformPosition(new Vector3d(wp.getX() + 0.5, wp.getY() + 0.5, wp.getZ() + 0.5), new Vector3d()));
            }
        }
        registerAiShip(ship.getId(), world, cannons, blueprint);
        return ship.getId();
    }

    // ── Cannon firing ─────────────────────────────────────────────────────────

    private static boolean isBroadsideAligned(ShipTransform tf, Vector3d shipPos,
                                               Vec3d targetPos, double alignThreshold) {
        Vector3d toTarget = dir2d(targetPos.x - shipPos.x, targetPos.z - shipPos.z);
        try {
            Quaterniondc rot = tf.getShipToWorldRotation();
            Vector3d shipRight = rot.transform(1, 0, 0, new Vector3d());
            shipRight.y = 0;
            if (shipRight.length() > 1e-4) {
                shipRight.normalize();
                return Math.abs(dot2d(shipRight, toTarget)) >= alignThreshold;
            }
        } catch (Exception ignored) {}
        return true;
    }

    private static void fireCannonVolley(AiShipData data, ShipTransform tf,
                                          Vector3d shipPos, Vector3d shipFwd,
                                          TargetInfo target) {
        Vec3d leadAim = computeLeadAim(target.pos, data.prevTargetPos, shipPos);
        Matrix4dc shipToWorld = tf.getShipToWorld();
        Vector3d toTarget = dir2d(target.pos.x - shipPos.x, target.pos.z - shipPos.z);

        List<Vector3d> fireOrigins = new ArrayList<>();

        if (!data.cannonShipPositions.isEmpty()) {
            for (Vector3d shipSpacePos : data.cannonShipPositions) {
                Vector3d worldPos = shipToWorld.transformPosition(new Vector3d(shipSpacePos), new Vector3d());
                Vector3d offset = new Vector3d(worldPos.x - shipPos.x, 0, worldPos.z - shipPos.z);
                if (offset.length() > 0.3 && dot2d(offset, toTarget) > 0) {
                    fireOrigins.add(worldPos);
                }
            }
            if (fireOrigins.isEmpty()) {
                for (Vector3d sp : data.cannonShipPositions) {
                    fireOrigins.add(shipToWorld.transformPosition(new Vector3d(sp), new Vector3d()));
                }
            }
        } else {
            Vector3d shipRight;
            try {
                Quaterniondc rot = tf.getShipToWorldRotation();
                shipRight = rot.transform(1, 0, 0, new Vector3d());
                shipRight.y = 0;
                if (shipRight.length() < 1e-4) throw new Exception();
                shipRight.normalize();
            } catch (Exception e) {
                shipRight = new Vector3d(-shipFwd.z, 0, shipFwd.x);
            }
            boolean onRight = dot2d(shipRight, toTarget) > 0;
            Vector3d fireDir = onRight ? new Vector3d(shipRight) : new Vector3d(shipRight).negate();
            for (int s : new int[]{-1, 1}) {
                fireOrigins.add(new Vector3d(
                    shipPos.x + fireDir.x * 7 + shipFwd.x * (s * 5),
                    shipPos.y + 1.5,
                    shipPos.z + fireDir.z * 7 + shipFwd.z * (s * 5)));
            }
        }

        for (Vector3d origin : fireOrigins) {
            double dx = leadAim.x - origin.x;
            double dz = leadAim.z - origin.z;
            double dy = leadAim.y - origin.y;
            double hDist = Math.sqrt(dx * dx + dz * dz);

            double sinTwoAlpha = Math.min(1.0, CANNON_GRAVITY * hDist / (CANNON_SPEED * CANNON_SPEED));
            double pitch = 0.5 * Math.asin(sinTwoAlpha);
            if (Math.abs(dy) > 1.0 && hDist > 0.1) pitch += Math.atan2(dy, hDist) * 0.3;
            pitch = Math.max(0, Math.min(Math.toRadians(45), pitch));

            double dirX = hDist > 0.1 ? dx / hDist : toTarget.x;
            double dirZ = hDist > 0.1 ? dz / hDist : toTarget.z;

            double spawnX = origin.x + dirX * 2.5;
            double spawnY = origin.y + Math.sin(pitch) * 2.5;
            double spawnZ = origin.z + dirZ * 2.5;

            CannonBallEntity ball = new CannonBallEntity(data.world, spawnX, spawnY, spawnZ);
            double spread = data.config.accuracySpread;
            ball.setVelocity(
                (dirX + (Math.random() - 0.5) * spread) * Math.cos(pitch) * CANNON_SPEED,
                Math.sin(pitch) * CANNON_SPEED,
                (dirZ + (Math.random() - 0.5) * spread) * Math.cos(pitch) * CANNON_SPEED
            );
            data.world.spawnEntity(ball);
        }

        LOGGER.info("[AI] Ship {} ({}) fired {} cannon(s)", data.shipId, data.faction.id, fireOrigins.size());
        data.world.playSound(null, shipPos.x, shipPos.y, shipPos.z,
            SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 3.0f, 0.5f);
    }

    private static Vec3d computeLeadAim(Vec3d targetNow, Vec3d targetPrev, Vector3d shipPos) {
        if (targetPrev == null) return targetNow;
        double vx = targetNow.x - targetPrev.x;
        double vz = targetNow.z - targetPrev.z;
        double dist = Math.sqrt(Math.pow(targetNow.x - shipPos.x, 2) + Math.pow(targetNow.z - shipPos.z, 2));
        double flightTicks = dist / CANNON_SPEED;
        return new Vec3d(
            targetNow.x + vx * flightTicks * LEAD_FACTOR,
            targetNow.y,
            targetNow.z + vz * flightTicks * LEAD_FACTOR
        );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Vector3d resolveHeading(ShipTransform tf, AiShipData data, Vector3d shipPos) {
        try {
            Quaterniondc rot = tf.getShipToWorldRotation();
            Vector3d candidate = rot.transform(0, 0, 1, new Vector3d());
            candidate.y = 0;
            if (candidate.length() > 1e-4) {
                candidate.normalize();
                data.smoothedHeading.lerp(candidate, 0.35);
                if (data.smoothedHeading.length() > 1e-4) data.smoothedHeading.normalize();
                return new Vector3d(data.smoothedHeading);
            }
        } catch (Exception e) {
            LOGGER.debug("[AI] Quaternion unavailable for {}: {}", data.shipId, e.getMessage());
        }
        if (data.prevShipPos != null) {
            Vector3d delta = new Vector3d(shipPos).sub(data.prevShipPos);
            delta.y = 0;
            if (delta.length() > 0.002) {
                delta.normalize();
                data.smoothedHeading.lerp(delta, 0.15);
                if (data.smoothedHeading.length() > 1e-4) data.smoothedHeading.normalize();
            }
        }
        return new Vector3d(data.smoothedHeading);
    }

    private static void updateStuckDetection(AiShipData data, Vector3d shipPos) {
        if (++data.stuckCheckTick < STUCK_CHECK_INTERVAL) return;
        data.stuckCheckTick = 0;
        Vec3d cur = new Vec3d(shipPos.x, 0, shipPos.z);
        if (data.stuckRefPos != null) {
            double moved = cur.distanceTo(new Vec3d(data.stuckRefPos.x, 0, data.stuckRefPos.z));
            data.stuckBoost = (moved < STUCK_THRESHOLD && data.state != AiState.BROADSIDE)
                ? STUCK_BOOST - 1f : 0f;
        }
        data.stuckRefPos = cur;
    }

    private static void transition(AiShipData data, AiState next) {
        data.state = next;
        data.stateTicks = 0;
    }

    private static boolean chooseBroadsideSide(Vector3d shipFwd, Vector3d shipPos, Vec3d targetPos) {
        Vector3d toTarget  = dir2d(targetPos.x - shipPos.x, targetPos.z - shipPos.z);
        Vector3d leftPerp  = new Vector3d(-toTarget.z, 0, toTarget.x);
        Vector3d rightPerp = new Vector3d(toTarget.z, 0, -toTarget.x);
        return dot2d(shipFwd, leftPerp) >= dot2d(shipFwd, rightPerp);
    }

    private static double distXZ(Vector3d a, Vec3d b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double distXZ(Vector3d a, Vector3d b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static Vector3d dir2d(double dx, double dz) {
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4) return new Vector3d(0, 0, 1);
        return new Vector3d(dx / len, 0, dz / len);
    }

    private static double dot2d(Vector3d a, Vector3d b) { return a.x * b.x + a.z * b.z; }

    private static double cross2d(Vector3d a, Vector3d b) { return a.x * b.z - a.z * b.x; }

    // ── Crew spawning ─────────────────────────────────────────────────────────

    /**
     * Spawns faction-appropriate crew on the ship deck.
     * PIRATES / UNDEAD get a CaptainEntity in the first slot (boarding conquest target).
     * MERCHANTS get Villager crew (non-hostile, good for trading roleplay).
     * NAVY get Armada marines (entity/custom/MarineEntity - they replaced the vindicators 2026-10-05).
     * Remaining slots use faction mob types.
     */
    private static void spawnCrew(LoadedServerShip ship, AiShipData data) {
        if (GhostShipEncounter.isDutchman(data.blueprintName)) { GhostShipEncounter.spawnCrew(ship, data); return; }
        int wanted  = 3;
        int spawned = 0;

        boolean hasCaptain = data.faction == Faction.PIRATES || data.faction == Faction.UNDEAD;

        // Posts are real standing spots on the deck, found in the ship's own blocks (ship space). The old version
        // scanned straight down from above the centre of mass and took the first block it hit - on a rigged ship
        // that was the top of a sail, so the crew fell to the deck and keepCrewAboard teleported them back up there
        // every tick (the crew "bouncing" on every AI ship).
        List<Vector3d> spots = deckSpots(ship, data.world, wanted);
        if (spots.isEmpty()) {
            LOGGER.warn("[AI] No deck spots found on ship {} ('{}') - crew not spawned", data.shipId, data.blueprintName);
            return;
        }
        Matrix4dc shipToWorld = ship.getTransform().getShipToWorld();

        for (Vector3d spot : spots) {
            if (spawned >= wanted) break;
            Vector3d wp = shipToWorld.transformPosition(new Vector3d(spot), new Vector3d());
            double cx = wp.x, cz = wp.z, spawnY = wp.y + 0.05;

            if (hasCaptain && spawned == 0) {
                CaptainEntity captain = ModEntities.SHIP_CAPTAIN.create(data.world);
                if (captain == null) continue;
                captain.refreshPositionAndAngles(cx, spawnY, cz, (float)(Math.random() * 360), 0);
                captain.setShipId(data.shipId);
                var healthAttr = captain.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
                var damageAttr = captain.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
                var armorAttr  = captain.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
                if (healthAttr != null) healthAttr.setBaseValue(data.config.captainMaxHealth);
                if (damageAttr != null) damageAttr.setBaseValue(data.config.captainAttackDamage);
                if (armorAttr  != null) armorAttr .setBaseValue(data.config.captainArmor);
                captain.setHealth(captain.getMaxHealth());
                captain.setCustomName(Text.literal(data.faction.displayName + " §f[~] Captain"));
                captain.setCustomNameVisible(true);
                captain.setShipHome(cx, spawnY, cz);
                captain.equipStack(EquipmentSlot.MAINHAND, new ItemStack(ModItems.CUTLASS));
                captain.setPersistent();
                data.world.spawnEntity(captain);
                data.captainEntityId = captain.getUuid();
                data.crewEntityIds.add(captain.getUuid());
                data.crewPosts.put(captain.getUuid(), new Vector3d(spot));
            } else {
                // Faction-specific crew type
                Entity crew = spawnCrewMob(data);
                if (crew == null) continue;
                crew.refreshPositionAndAngles(cx, spawnY, cz, (float)(Math.random() * 360), 0);
                if (crew instanceof PirateCrewEntity pirate) {
                    pirate.setShipHome(cx, spawnY, cz);
                    pirate.equipStack(EquipmentSlot.MAINHAND, pirate.weapon());           // a cutlass, or a marine's sword
                    pirate.setCanPickUpLoot(false);
                    pirate.setPersistent();
                } else if (crew instanceof net.minecraft.entity.mob.MobEntity mob) {
                    mob.setPersistent();
                }
                data.crewEntityIds.add(crew.getUuid());
                data.crewPosts.put(crew.getUuid(), new Vector3d(spot));
                data.world.spawnEntity(crew);
            }
            spawned++;
        }

        LOGGER.info("[AI] Spawned {}/{} {} crew on ship {} ('{}')",
            spawned, wanted, data.faction.id, data.shipId, data.blueprintName);
    }

    /**
     * Crew used to patrol a fixed WORLD point set at spawn, so the moment the ship sailed they walked after an empty
     * patch of sea and fell off. Posts are kept in ship space: every tick each crew member's tether (patrol home, or the
     * vanilla walk-target leash for villager/vindicator/drowned crews) follows the ship. Teleporting is only the last
     * resort - someone in the water, fallen well below the deck, or clearly off the ship is put back on their post.
     * Normal walking about the deck is left alone (snapping at 5.5 blocks made the crew visibly jump around).
     */
    private static void keepCrewAboard(LoadedServerShip ship, AiShipData data) {
        if (data.crewPosts.isEmpty()) return;
        Matrix4dc toWorld = ship.getTransform().getShipToWorld();
        for (Map.Entry<UUID, Vector3d> post : data.crewPosts.entrySet()) {
            Entity e = data.world.getEntity(post.getKey());
            if (e == null || !e.isAlive()) continue;
            Vector3d w = toWorld.transformPosition(new Vector3d(post.getValue()), new Vector3d());
            if (e instanceof PirateCrewEntity pc) pc.setShipHome(w.x, w.y, w.z);
            else if (e instanceof CaptainEntity c) c.setShipHome(w.x, w.y, w.z);
            else if (e instanceof net.minecraft.entity.mob.PathAwareEntity pa) pa.setPositionTarget(BlockPos.ofFloored(w.x, w.y, w.z), 4);
            boolean fighting = e instanceof net.minecraft.entity.mob.MobEntity m && m.getTarget() != null
                    && m.getTarget().squaredDistanceTo(w.x, w.y, w.z) < 12 * 12;
            double dx = e.getX() - w.x, dz = e.getZ() - w.z;
            double drift = Math.sqrt(dx * dx + dz * dz);
            if (e.isTouchingWater() || e.getY() < w.y - 3.5 || drift > (fighting ? 14.0 : 11.0)) {
                LOGGER.info("[AI] crew {} put back on post (water={} dy={} drift={}) ship {}", e.getType().getUntranslatedName(),
                        e.isTouchingWater(), String.format("%.1f", e.getY() - w.y), String.format("%.1f", drift), data.shipId);
                e.refreshPositionAndAngles(w.x, w.y, w.z, e.getYaw(), e.getPitch());
                e.setVelocity(Vec3d.ZERO);
                e.fallDistance = 0;
            }
        }
    }

    /**
     * Up to {@code n} standing spots on the ship's working deck, in SHIP space (feet position, block centre): a block
     * with a full solid top and two free blocks above it. The deck is the helm's level (the helm stands on it) - spots
     * within 4 below to 1 above it count, the most crowded level (the main deck) is preferred, and spots are spread at
     * least 3 blocks apart, nearest the helm first. Falls back to any standing spot if the ship has no helm.
     */
    static List<Vector3d> deckSpots(LoadedServerShip ship, ServerWorld world, int n) {
        var box = ship.getShipAABB();
        List<Vector3d> out = new ArrayList<>();
        if (box == null) return out;
        BlockPos helm = null;
        List<BlockPos> stand = new ArrayList<>();
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int x = box.minX(); x <= box.maxX(); x++)
            for (int z = box.minZ(); z <= box.maxZ(); z++)
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    p.set(x, y, z);
                    BlockState st = world.getBlockState(p);
                    if (st.isOf(net.get900.pixelpirates.block.ModBlocks.SHIP_HELM)) helm = p.toImmutable();
                    if (!st.isSideSolidFullSquare(world, p, net.minecraft.util.math.Direction.UP)) continue;
                    BlockPos a = p.up(), b = p.up(2);
                    if (!world.getBlockState(a).getCollisionShape(world, a).isEmpty() || !world.getFluidState(a).isEmpty()) continue;
                    if (!world.getBlockState(b).getCollisionShape(world, b).isEmpty()) continue;
                    stand.add(a.toImmutable());
                }
        if (stand.isEmpty()) return out;
        final BlockPos h = helm != null ? helm : stand.get(stand.size() / 2);
        List<BlockPos> deck = new ArrayList<>(stand.stream().filter(s -> s.getY() >= h.getY() - 4 && s.getY() <= h.getY() + 1).toList());
        if (deck.isEmpty()) deck = stand;
        Map<Integer, Integer> perLevel = new HashMap<>();
        for (BlockPos s : deck) perLevel.merge(s.getY(), 1, Integer::sum);
        int main = perLevel.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();
        deck.sort(java.util.Comparator.comparingInt((BlockPos s) -> s.getY() == main ? 0 : 1)
                .thenComparingDouble(s -> s.getSquaredDistance(h)));
        for (BlockPos s : deck) {
            if (out.size() >= n) break;
            boolean far = out.stream().allMatch(o -> o.distanceSquared(s.getX() + 0.5, s.getY(), s.getZ() + 0.5) >= 9);
            if (far && !s.equals(h)) out.add(new Vector3d(s.getX() + 0.5, s.getY(), s.getZ() + 0.5));
        }
        return out;
    }

    private static Entity spawnCrewMob(AiShipData data) {
        // 2026-10-05 (the user): no more illagers - the Armada sails with MARINES; and everyone aboard holds a weapon
        // (pirates + marines arm themselves in initialize; a villager or drowned is handed one here).
        return switch (data.faction) {
            case MERCHANTS -> {
                var v = EntityType.VILLAGER.create(data.world);
                if (v != null) {
                    v.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Math.random() < 0.5 ? net.minecraft.item.Items.IRON_SWORD : ModItems.CUTLASS));
                    v.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f);
                }
                yield v;
            }
            case NAVY      -> ModEntities.ARMADA_MARINE.create(data.world);
            case UNDEAD    -> {
                // drowned burn in daylight; a skull (not damageable, so it never wears through) keeps the sun off
                var d = EntityType.DROWNED.create(data.world);
                if (d != null) {
                    d.equipStack(EquipmentSlot.HEAD, new ItemStack(net.minecraft.item.Items.SKELETON_SKULL));
                    d.setEquipmentDropChance(EquipmentSlot.HEAD, 0f);
                    d.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Math.random() < 0.4 ? net.minecraft.item.Items.TRIDENT : net.minecraft.item.Items.IRON_SWORD));
                    d.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0f);
                }
                yield d;
            }
            default        -> ModEntities.PIRATE_CREW.create(data.world);
        };
    }

    // ── Idle despawn ──────────────────────────────────────────────────────────

    private static void deleteIdleShip(AiShipData data) {
        GhostShipEncounter.forget(data.shipId);
        for (UUID uuid : data.crewEntityIds) {
            Entity crew = data.world.getEntity(uuid);
            if (crew != null) crew.discard();
        }
        VsiServerShipWorld sw = VSGameUtilsKt.getShipObjectWorld(data.world);
        if (sw != null) {
            org.valkyrienskies.core.api.ships.ServerShip s = sw.getLoadedShips().getById(data.shipId);
            if (s != null) sw.deleteShip(s);
        }
        ShipSteeringManager.MAST_COUNTS.remove(data.shipId);
        ShipRegistryState.get(data.world.getServer().getOverworld()).removeShip(data.shipId);
        LOGGER.info("[AI] {} ship {} despawned after 8 minutes idle.", data.faction.id, data.shipId);
    }

    // ── Derelict transition ───────────────────────────────────────────────────

    private static void makeDerelict(long shipId, ServerWorld world, MinecraftServer server,
                                      String blueprintName, UUID captainEntityId,
                                      ServerPlayerEntity boardingKiller, Faction faction) {
        ShipSteeringManager.SHIP_INPUTS.remove(shipId);
        ShipRegistryState.get(server.getOverworld()).convertToPlayerShip(shipId);

        if (captainEntityId != null) {
            Entity captainEnt = world.getEntity(captainEntityId);
            if (captainEnt != null) captainEnt.discard();
        }

        // Apply reputation change for boarding kill
        if (boardingKiller != null) {
            FactionManager.onShipDestroyed(boardingKiller, faction);
        }

        long expiry = server.getTicks() + 6000L;
        ShipSteeringManager.DERELICT_EXPIRY.put(shipId, expiry);
        ShipSteeringManager.DERELICT_WORLDS.put(shipId, world);
        ShipSteeringManager.DERELICT_BLUEPRINT.put(shipId, blueprintName);

        VsiServerShipWorld shipWorld = VSGameUtilsKt.getShipObjectWorld(world);
        if (shipWorld != null) {
            LoadedServerShip ls = shipWorld.getLoadedShips().getById(shipId);
            if (ls != null) {
                double sx = ls.getTransform().getPositionInWorld().x();
                double sy = ls.getTransform().getPositionInWorld().y();
                double sz = ls.getTransform().getPositionInWorld().z();

                int pirateCoins = boardingKiller != null
                        ? 10 + (int)(Math.random() * 16)
                        : 5  + (int)(Math.random() * 11);
                int kits = 1 + (int)(Math.random() * 3);
                world.spawnEntity(new ItemEntity(world, sx, sy + 1, sz,
                    new ItemStack(ModItems.PIRATE_COIN, pirateCoins)));
                world.spawnEntity(new ItemEntity(world, sx, sy + 1, sz,
                    new ItemStack(ModItems.SHIP_REPAIR_KIT, kits)));
                if (Math.random() < 0.6) {
                    world.spawnEntity(new ItemEntity(world, sx, sy + 1, sz,
                        new ItemStack(ModItems.COIN, 3 + (int)(Math.random() * 10))));
                }

                String notification = boardingKiller != null
                        ? "§6[~] " + boardingKiller.getName().getString() +
                          " §fhas boarded a " + faction.displayName + "§f ship! §75 min to claim."
                        : "§c⚠ A " + faction.displayName + "§c ship is sinking! 5 min to claim.";

                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    if (p.getServerWorld() != world) continue;
                    double dx = p.getX() - sx, dz = p.getZ() - sz;
                    if (dx * dx + dz * dz < 300.0 * 300.0) {
                        p.sendMessage(Text.literal(notification), false);
                    }
                }
            }
        }

        LOGGER.info("[AI] {} ship {} ('{}') became derelict (boarder={}).",
            faction.id, shipId, blueprintName,
            boardingKiller != null ? boardingKiller.getName().getString() : "none");
    }

    // ── Boarding conquest ─────────────────────────────────────────────────────

    /**
     * Called by CaptainEntity.onDeath when a player kills the captain.
     * Applies reputation change, kills remaining crew, starts 5-minute claim window.
     */
    public static void onCaptainKilled(long shipId, ServerPlayerEntity killer) {
        AiShipData data = AI_SHIPS.remove(shipId);
        if (data == null) return;
        ShipSteeringManager.SHIP_INPUTS.remove(shipId);

        MinecraftServer server = killer.getServer();
        if (server == null) return;

        // Apply captain-kill reputation change (ship-destroyed rep applied in makeDerelict)
        FactionManager.onCaptainKilled(killer, data.faction);

        for (UUID uuid : data.crewEntityIds) {
            Entity e = data.world.getEntity(uuid);
            if (e != null) e.discard();
        }

        makeDerelict(data.shipId, data.world, server, data.blueprintName, null, killer, data.faction);
        LOGGER.info("[AI] {} ship {} captured via boarding by {}",
            data.faction.id, data.shipId, killer.getName().getString());
    }
}
