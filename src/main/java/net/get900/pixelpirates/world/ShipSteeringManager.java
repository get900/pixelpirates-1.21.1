package net.get900.pixelpirates.world;

import net.get900.pixelpirates.PixelPirates;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.core.internal.world.VsiServerShipWorld;
import org.valkyrienskies.mod.api.SeatedControllingPlayer;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.util.GameToPhysicsAdapter;

import java.util.concurrent.ConcurrentHashMap;

public class ShipSteeringManager {

    // Physics thread reads, server thread writes — ConcurrentHashMap for safe cross-thread access.
    // float[3]: [0]=forwardImpulse, [1]=leftImpulse (turn), [2]=sprintOn (1f=true)
    public static final ConcurrentHashMap<Long, float[]>  SHIP_INPUTS          = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<Long, Integer>  MAST_COUNTS          = new ConcurrentHashMap<>();
    // Freshness counter: set to 5 on each custom HELM_STEER packet; decremented per tick.
    // While > 0, SeatedControllingPlayer is bypassed and custom packet inputs are used instead.
    public static final ConcurrentHashMap<Long, Integer>  HELM_STEER_FRESHNESS = new ConcurrentHashMap<>();
    /** Wind Reader of whoever last steered the ship (thrust multiplier). */
    public static final ConcurrentHashMap<Long, Float>    HELM_BONUS = new ConcurrentHashMap<>();

    // Derelict AI ships: AI removed, waiting for player claim or 5-min deletion.
    // Value = server tick at which the ship will be deleted if unclaimed.
    public static final ConcurrentHashMap<Long, Long>        DERELICT_EXPIRY     = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<Long, ServerWorld> DERELICT_WORLDS     = new ConcurrentHashMap<>();
    // Blueprint name of the derelict ship — read at claim time to award zone unlock.
    public static final ConcurrentHashMap<Long, String>      DERELICT_BLUEPRINT  = new ConcurrentHashMap<>();

    // Per-ship buoyancy target Y. When a ShipWaterlineBlock is present, this is set so
    // that block lands exactly at WATER_Y when floating. Absent = use WATER_Y directly.
    public static final ConcurrentHashMap<Long, Double> WATERLINE_OFFSETS = new ConcurrentHashMap<>();

    // Anchor state: ships in ANCHORED_SHIPS receive heavy damping instead of thrust.
    // UNANCHOR_TIMERS maps shipId → server tick at which the anchor is automatically raised.
    public static final java.util.Set<Long>            ANCHORED_SHIPS   = ConcurrentHashMap.newKeySet();
    public static final ConcurrentHashMap<Long, Long>  UNANCHOR_TIMERS  = new ConcurrentHashMap<>();

    // ── Physics constants (must match what was in ShipHelmBlockEntity) ────────
    private static final double THRUST_PER_MAST = 30000.0;
    private static final double SPRINT_MULT     = 1.8;
    private static final double DRAG_FACTOR     = 0.5;
    private static final double TURN_FACTOR     = 10.0;
    private static final double YAW_DAMP        = 5.0;
    private static final double PITCH_ROLL_DAMP = 50.0;
    private static final double LEVEL_SPRING    = 45.0;
    private static final double BOW_LIFT        = 2.5;
    private static final double GRAVITY         = 9.8;
    private static final double VERT_DAMP       = 15.0;
    public  static final double WATER_Y         = 62.0;
    private static final double BUOY_SPRING     = 20.0;

    public static void tick(MinecraftServer server) {
        // Decrement and expire freshness counters each game tick
        HELM_STEER_FRESHNESS.replaceAll((k, v) -> v - 1);
        HELM_STEER_FRESHNESS.values().removeIf(v -> v <= 0);

        // Process anchor raise timers — unanchor when timer expires
        if (!UNANCHOR_TIMERS.isEmpty()) {
            long tick = server.getTicks();
            UNANCHOR_TIMERS.entrySet().removeIf(entry -> {
                if (entry.getValue() > tick) return false;
                long shipId = entry.getKey();
                ANCHORED_SHIPS.remove(shipId);
                return true;
            });
        }

        // Expire derelict ships: delete unclaimed AI ships after their 5-minute timer
        if (!DERELICT_EXPIRY.isEmpty()) {
            long currentTick = server.getTicks();
            DERELICT_EXPIRY.entrySet().removeIf(entry -> {
                if (entry.getValue() > currentTick) return false;
                long shipId = entry.getKey();
                ServerWorld dw = DERELICT_WORLDS.remove(shipId);
                MAST_COUNTS.remove(shipId);
                WATERLINE_OFFSETS.remove(shipId);
                ANCHORED_SHIPS.remove(shipId);
                UNANCHOR_TIMERS.remove(shipId);
                ShipHealthState.SINKING_SHIPS.remove(shipId);
                if (dw != null) {
                    VsiServerShipWorld sw = VSGameUtilsKt.getShipObjectWorld(dw);
                    if (sw != null) {
                        ServerShip ship = sw.getLoadedShips().getById(shipId);
                        if (ship != null) sw.deleteShip(ship);
                    }
                    ShipRegistryState.get(dw.getServer().getOverworld()).removeShip(shipId);
                }
                PixelPirates.LOGGER.info("[Derelict] Deleted unclaimed ship {}", shipId);
                return true;
            });
        }

        for (ServerWorld world : server.getWorlds()) {
            VsiServerShipWorld shipWorld = VSGameUtilsKt.getShipObjectWorld(world);
            if (shipWorld == null) continue;

            String dimensionId = VSGameUtilsKt.getDimensionId(world);
            GameToPhysicsAdapter gtpa = ValkyrienSkiesMod.INSTANCE.getOrCreateGTPA(dimensionId);

            for (LoadedServerShip ship : shipWorld.getLoadedShips().getIdToShipData().values()) {
                long    shipId      = ship.getId();
                boolean isPlayerShip = MAST_COUNTS.containsKey(shipId);
                boolean isAiShip    = AiShipController.AI_SHIPS.containsKey(shipId);
                if (!isPlayerShip && !isAiShip) continue;

                // For player ships: use SeatedControllingPlayer as fallback
                // when no fresh custom HELM_STEER packet has arrived this tick.
                // AI ships manage their own SHIP_INPUTS via AiShipController — never clear theirs.
                if (isPlayerShip && !isAiShip && !HELM_STEER_FRESHNESS.containsKey(shipId)) {
                    SeatedControllingPlayer ctrl = ship.getAttachment(SeatedControllingPlayer.class);
                    if (ctrl != null) {
                        SHIP_INPUTS.put(shipId, new float[]{
                            ctrl.getForwardImpulse(),
                            ctrl.getLeftImpulse(),
                            ctrl.getSprintOn() ? 1f : 0f
                        });
                    } else {
                        SHIP_INPUTS.remove(shipId);
                    }
                }

                // Apply all ship physics forces via GameToPhysicsAdapter every game tick.
                // This bypasses the BlockEntityPhysicsListener.physTick path which is
                // unreliable due to VS2 registering block entities before ship assembly
                // (leaving a null shipId, so physTick always receives null PhysShip).
                applyPhysics(gtpa, ship, shipId);
            }
        }
    }

    private static void applyPhysics(GameToPhysicsAdapter gtpa, LoadedServerShip ship, long shipId) {
        ShipTransform tf    = ship.getTransform();
        double        mass  = ship.getInertiaData().getMass();
        Vector3dc     vel   = ship.getVelocity();
        Vector3dc     omega = ship.getAngularVelocity();
        Vector3dc     wPos  = tf.getPositionInWorld();

        // Sinking ships: strong downward force + roll torque, skip normal buoyancy/thrust
        if (ShipHealthState.SINKING_SHIPS.getOrDefault(shipId, false)) {
            gtpa.applyWorldForce(shipId, new Vector3d(0, -mass * GRAVITY * 3.0, 0), null);
            gtpa.applyBodyTorque(shipId, new Vector3d(0, 0, mass * 3.0));

            if (omega.length() > 0.001) {
                Vector3d ob = new Vector3d(omega);
                tf.getWorldToShip().transformDirection(ob);
                gtpa.applyBodyTorque(shipId, new Vector3d(ob).mul(-mass * YAW_DAMP * 0.4));
            }

            double hs = Math.sqrt(vel.x() * vel.x() + vel.z() * vel.z());
            if (hs > 0.001) {
                Vector3d hb = new Vector3d(vel.x(), 0, vel.z());
                tf.getWorldToShip().transformDirection(hb);
                // hb has magnitude hs; mul by -DRAG_FACTOR*mass gives velocity-proportional drag
                gtpa.applyBodyForce(shipId, new Vector3d(hb).mul(-mass * DRAG_FACTOR), new Vector3d());
            }
            return;
        }

        // Anchored ships: full buoyancy + heavy damping, no thrust
        if (ANCHORED_SHIPS.contains(shipId)) {
            gtpa.applyWorldForce(shipId, new Vector3d(0, mass * GRAVITY, 0), null);
            double buoyTargetA = WATERLINE_OFFSETS.getOrDefault(shipId, WATER_Y);
            gtpa.applyWorldForce(shipId, new Vector3d(0, mass * (BUOY_SPRING * (buoyTargetA - wPos.y()) - VERT_DAMP * vel.y()), 0), null);
            // Very strong drag to stop motion quickly
            if (vel.length() > 0.001) {
                gtpa.applyWorldForce(shipId, new Vector3d(vel.x(), 0, vel.z()).mul(-mass * DRAG_FACTOR * 15.0), null);
            }
            // Strong angular damping
            if (omega.length() > 0.001) {
                Vector3d ob = new Vector3d(omega);
                tf.getWorldToShip().transformDirection(ob);
                gtpa.applyBodyTorque(shipId, new Vector3d(-ob.x() * mass * PITCH_ROLL_DAMP * 5, -ob.y() * mass * YAW_DAMP * 5, -ob.z() * mass * PITCH_ROLL_DAMP * 5));
            }
            // Righting moment still applies
            Vector3d wuib = new Vector3d(0, 1, 0);
            tf.getWorldToShip().transformDirection(wuib);
            Vector3d lt = new Vector3d(0, 1, 0).cross(wuib);
            if (lt.length() > 0.001) gtpa.applyBodyTorque(shipId, lt.mul(mass * LEVEL_SPRING));
            return;
        }

        // Buoyancy: cancel VS2's gravity so the ship floats
        gtpa.applyWorldForce(shipId, new Vector3d(0, mass * GRAVITY, 0), null);

        // Spring toward water surface. Per-ship target if a waterline block was placed.
        double buoyTarget = WATERLINE_OFFSETS.getOrDefault(shipId, WATER_Y);
        double dy = buoyTarget - wPos.y();
        double vy = vel.y();
        gtpa.applyWorldForce(shipId, new Vector3d(0, mass * (BUOY_SPRING * dy - VERT_DAMP * vy), 0), null);

        // Horizontal drag — limits top speed
        double hs = Math.sqrt(vel.x() * vel.x() + vel.z() * vel.z());
        if (hs > 0.001) {
            Vector3d hb = new Vector3d(vel.x(), 0, vel.z());
            tf.getWorldToShip().transformDirection(hb);
            gtpa.applyBodyForce(shipId, new Vector3d(hb).mul(-mass * DRAG_FACTOR), new Vector3d());
        }

        // Per-axis angular damping: aggressive on pitch/roll, light on yaw
        if (omega.length() > 0.001) {
            Vector3d ob = new Vector3d(omega);
            tf.getWorldToShip().transformDirection(ob);
            gtpa.applyBodyTorque(shipId, new Vector3d(
                -ob.x() * mass * PITCH_ROLL_DAMP,
                -ob.y() * mass * YAW_DAMP,
                -ob.z() * mass * PITCH_ROLL_DAMP
            ));
        }

        // Righting moment: cross product of body-up with world-up-in-body gives correction axis
        Vector3d worldUpInBody = new Vector3d(0, 1, 0);
        tf.getWorldToShip().transformDirection(worldUpInBody);
        Vector3d levelTorque = new Vector3d(0, 1, 0).cross(worldUpInBody);
        if (levelTorque.length() > 0.001) {
            gtpa.applyBodyTorque(shipId, levelTorque.mul(mass * LEVEL_SPRING));
        }

        // Forward/backward thrust and yaw torque from SHIP_INPUTS
        float[] inputs = SHIP_INPUTS.get(shipId);
        float   fwd    = inputs != null ? inputs[0] : 0f;
        float   turn   = inputs != null ? inputs[1] : 0f;
        boolean sprint = inputs != null && inputs[2] > 0.5f;

        if (Math.abs(fwd) > 0.01f) {
            int    masts  = MAST_COUNTS.getOrDefault(shipId, 1);
            double thrust = fwd * masts * THRUST_PER_MAST * (sprint ? SPRINT_MULT : 1.0) * HELM_BONUS.getOrDefault(shipId, 1f);
            gtpa.applyBodyForce(shipId, new Vector3d(0, 0, thrust), new Vector3d());
        }

        if (Math.abs(turn) > 0.01f) {
            gtpa.applyBodyTorque(shipId, new Vector3d(0, turn * mass * TURN_FACTOR, 0));
        }

        // Anti-dive: bow-lift torque proportional to forward body-space speed
        Vector3d velInBody = new Vector3d(vel.x(), vel.y(), vel.z());
        tf.getWorldToShip().transformDirection(velInBody);
        double fwdSpeed = velInBody.z();
        if (Math.abs(fwdSpeed) > 0.1) {
            // Negative X torque = bow-up (right-hand rule: +X tilts bow DOWN)
            gtpa.applyBodyTorque(shipId, new Vector3d(-fwdSpeed * mass * BOW_LIFT, 0, 0));
        }
    }
}
