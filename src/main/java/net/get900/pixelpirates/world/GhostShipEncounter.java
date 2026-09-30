package net.get900.pixelpirates.world;

import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.entity.mob.GhostCaptainEntity;
import net.get900.pixelpirates.entity.mob.ModMobs;
import net.get900.pixelpirates.world.faction.Faction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.primitives.AABBd;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BOSS 4/10 - the Flying Dutchman encounter.
 * <ol>
 *   <li>Ring the Drowned Bell (a bell on a PHANTOM_BUOY, Dutchman's Rest lair): the Dutchman rises 80-100
 *       blocks away - a real VS2 ship ({@link GhostShipDesign}) sailed by the normal AI as an UNDEAD ship,
 *       firing broadsides from its ghost cannons. The Ghost Captain stands on the poop deck, BOUND to it:
 *       nothing can hurt him while his ship floats.</li>
 *   <li>Sink the ship (its hull HP to 0 with your own ship's cannons). Instead of turning derelict, the
 *       Dutchman goes down and the captain tears free and BOARDS the nearest player's ship - the duel.</li>
 * </ol>
 * AiShipController defers to this class for the Dutchman's crew ({@link #spawnCrew}) and its sinking
 * ({@link #onSunk}). The wreck is deleted 20 s after it goes under.
 */
public final class GhostShipEncounter {
    private GhostShipEncounter() {}

    /** Active Dutchmen: ship id -> the bell that summoned it. */
    private static final Map<Long, BlockPos> ACTIVE = new ConcurrentHashMap<>();
    /** Sinking wrecks: ship id -> ticks until deletion. */
    private static final Map<Long, Integer> SINKING = new ConcurrentHashMap<>();
    private static final Map<Long, ServerWorld> WORLDS = new ConcurrentHashMap<>();
    static boolean DEBUG_HEADING = false;   // logs Dutchman position + bow every 5 s when true

    public static java.util.Set<Long> activeIds() { return ACTIVE.keySet(); }

    public static boolean isDutchman(String blueprint) { return GhostShipDesign.BLUEPRINT.equals(blueprint); }

    /** Bell rung: raise the Dutchman somewhere 80-100 blocks out over open water. */
    public static boolean summon(ServerWorld world, BlockPos bell, @Nullable ServerPlayerEntity ringer) {
        for (var e : ACTIVE.entrySet())
            if (e.getValue().getSquaredDistance(bell) < 400 * 400) {
                if (ringer != null) ringer.sendMessage(Text.literal("The Dutchman already hunts these waters...").formatted(Formatting.DARK_AQUA), true);
                return false;
            }
        ShipSchematic schematic = GhostShipDesign.schematic();
        for (int attempt = 0; attempt < 16; attempt++) {
            double a = world.random.nextDouble() * Math.PI * 2, d = 80 + world.random.nextDouble() * 20;
            BlockPos origin = new BlockPos((int) (bell.getX() + Math.cos(a) * d), 90, (int) (bell.getZ() + Math.sin(a) * d));
            if (!world.getFluidState(new BlockPos(origin.getX(), world.getSeaLevel() - 1, origin.getZ())).isIn(FluidTags.WATER)) continue;
            AABBd clear = new AABBd(origin.getX() - 40, 0, origin.getZ() - 40, origin.getX() + 40, 256, origin.getZ() + 40);
            if (ValkyrienSkies.getShipsIntersecting(world, clear).iterator().hasNext()) continue;
            try {
                ServerShip ship = ShipSpawner.spawn(world, schematic, origin);
                Matrix4d worldToShip = new Matrix4d(ship.getTransform().getShipToWorld()).invert();
                List<Vector3d> cannons = new ArrayList<>();
                for (ShipSchematic.Entry e : schematic.getEntries()) {
                    if (!ShipSchematic.restoreState(e.stateNbt()).isOf(ModBlocks.GHOST_CANNON)) continue;
                    BlockPos cp = origin.add(e.relPos());
                    cannons.add(worldToShip.transformPosition(new Vector3d(cp.getX() + 0.5, cp.getY() + 0.5, cp.getZ() + 0.5), new Vector3d()));
                }
                AiShipController.registerAiShip(ship.getId(), world, cannons, GhostShipDesign.BLUEPRINT, Faction.UNDEAD);
                ACTIVE.put(ship.getId(), bell.toImmutable());
                WORLDS.put(ship.getId(), world);
                DECK_ORIGIN.put(ship.getId(), worldToShip.transformPosition(new Vector3d(origin.getX(), origin.getY(), origin.getZ()), new Vector3d()));
                announce(world, bell, origin);
                return true;
            } catch (Exception ex) {
                net.get900.pixelpirates.PixelPirates.LOGGER.warn("[Dutchman] spawn attempt {} failed: {}", attempt, ex.getMessage());
            }
        }
        if (ringer != null) ringer.sendMessage(Text.literal("The bell tolls, but the sea stays still. (No open water nearby)").formatted(Formatting.GRAY), true);
        return false;
    }

    /** Ship-space position of the schematic origin, so crew posts can be converted after the ship moves. */
    private static final Map<Long, Vector3d> DECK_ORIGIN = new ConcurrentHashMap<>();

    private static void announce(ServerWorld world, BlockPos bell, BlockPos at) {
        world.playSound(null, bell, SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 4.0f, 0.5f);
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
        if (bolt != null) {
            bolt.refreshPositionAfterTeleport(at.getX(), world.getSeaLevel(), at.getZ());
            bolt.setCosmetic(true);
            world.spawnEntity(bolt);
        }
        for (ServerPlayerEntity p : world.getPlayers(p -> p.squaredDistanceTo(bell.getX(), bell.getY(), bell.getZ()) < 300 * 300)) {
            p.sendMessage(Text.literal("[X] The Flying Dutchman rises from the deep! Sink her - her captain cannot be harmed while she floats.")
                    .formatted(Formatting.DARK_AQUA, Formatting.BOLD), false);
        }
    }

    /** Converts a schematic-relative block position into world space on the (possibly moved) ship. */
    private static Vector3d toWorld(LoadedServerShip ship, BlockPos rel) {
        Vector3d origin = DECK_ORIGIN.get(ship.getId());
        if (origin == null) origin = new Vector3d(ship.getTransform().getPositionInShip());
        Vector3d sp = new Vector3d(origin).add(rel.getX() + 0.5, rel.getY(), rel.getZ() + 0.5);
        return ship.getTransform().getShipToWorld().transformPosition(sp, new Vector3d());
    }

    /** AiShipController, 40 ticks after the ship assembles: the bound captain and a skeleton crew. */
    public static void spawnCrew(LoadedServerShip ship, AiShipController.AiShipData data) {
        ServerWorld world = data.world;
        net.get900.pixelpirates.PixelPirates.LOGGER.info("[Dutchman] spawning crew on ship {} at {}", ship.getId(), ship.getTransform().getPositionInWorld());
        var captainType = ModMobs.TYPES.get("ghost_captain");
        if (captainType != null) {
            Entity e = captainType.create(world);
            if (e instanceof GhostCaptainEntity cap) {
                Vector3d w = toWorld(ship, GhostShipDesign.CAPTAIN_SPOT);
                Vector3d shipSpace = new Vector3d(DECK_ORIGIN.getOrDefault(ship.getId(), new Vector3d()))
                        .add(GhostShipDesign.CAPTAIN_SPOT.getX() + 0.5, GhostShipDesign.CAPTAIN_SPOT.getY(), GhostShipDesign.CAPTAIN_SPOT.getZ() + 0.5);
                // spawn ABOVE the ship: VS2 moves an entity spawned inside a ship's bounds into the shipyard,
                // where it never ticks (2026-09-29). The bound captain's pin moves him onto the deck next tick.
                cap.refreshPositionAndAngles(w.x, w.y + 48, w.z, 0, 0);
                cap.initialize(world, world.getLocalDifficulty(cap.getBlockPos()), SpawnReason.EVENT, null, null);
                cap.bindToShip(ship.getId(), shipSpace);
                cap.setPersistent();
                boolean ok = world.spawnEntity(cap);
                net.get900.pixelpirates.PixelPirates.LOGGER.info("[Dutchman] captain spawned above ship {} (ok={}, in world={})", ship.getId(), ok, world.getEntity(cap.getUuid()) != null);
                data.captainEntityId = cap.getUuid();
                data.crewEntityIds.add(cap.getUuid());
            }
        }
        String[] crew = {"phantom_pirate", "phantom_pirate", "phantom_pirate", "phantom_pirate"};   // flyers: they drop in from above
        for (int i = 0; i < GhostShipDesign.CREW_SPOTS.length; i++) {
            var type = ModMobs.TYPES.get(crew[i]);
            if (type == null) continue;
            Entity e = type.create(world);
            if (e == null) continue;
            Vector3d w = toWorld(ship, GhostShipDesign.CREW_SPOTS[i]);
            e.refreshPositionAndAngles(w.x, w.y + 48, w.z, world.random.nextFloat() * 360, 0);
            if (e instanceof MobEntity m) {
                m.initialize(world, world.getLocalDifficulty(e.getBlockPos()), SpawnReason.EVENT, null, null);
                m.setPersistent();
            }
            world.spawnEntity(e);
            data.crewEntityIds.add(e.getUuid());
        }
    }

    /** AiShipController, hull HP reached 0: the ship goes down and the captain boards. */
    public static void onSunk(AiShipController.AiShipData data, MinecraftServer server) {
        ServerWorld world = data.world;
        ShipSteeringManager.SHIP_INPUTS.remove(data.shipId);
        Entity cap = data.captainEntityId == null ? null : world.getEntity(data.captainEntityId);
        for (var id : data.crewEntityIds) {
            Entity e = world.getEntity(id);
            if (e != null && e != cap) e.discard();
        }
        if (cap instanceof GhostCaptainEntity g) g.breakFree();
        SINKING.put(data.shipId, 400);
        WORLDS.put(data.shipId, world);
        for (ServerPlayerEntity p : world.getPlayers(p -> p.squaredDistanceTo(cap != null ? cap : p) < 200 * 200))
            p.sendMessage(Text.literal("[X] The Flying Dutchman goes down... and her captain rises from the wreck!").formatted(Formatting.DARK_AQUA), false);
    }

    /** Idle-despawned or admin-removed: forget it (the bell may be rung again). */
    public static void forget(long shipId) {
        ACTIVE.remove(shipId);
        DECK_ORIGIN.remove(shipId);
    }

    public static void tick(MinecraftServer server) {
        if (DEBUG_HEADING && server.getTicks() % 100 == 0)
            for (long id : ACTIVE.keySet()) {
                ServerWorld w = WORLDS.get(id);
                var sw = w == null ? null : VSGameUtilsKt.getShipObjectWorld(w);
                LoadedServerShip s = sw == null ? null : sw.getLoadedShips().getById(id);
                if (s == null) continue;
                Vector3d fwd = s.getTransform().getShipToWorldRotation().transform(new Vector3d(0, 0, 1));
                net.get900.pixelpirates.PixelPirates.LOGGER.info("[Dutchman] HEADING pos={} bow={}", s.getTransform().getPositionInWorld(), fwd);
            }
        for (Iterator<Map.Entry<Long, Integer>> it = SINKING.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            int left = e.getValue() - 1;
            if (left > 0) { e.setValue(left); continue; }
            it.remove();
            long id = e.getKey();
            ServerWorld world = WORLDS.remove(id);
            forget(id);
            if (world == null) continue;
            var sw = VSGameUtilsKt.getShipObjectWorld(world);
            if (sw != null) {
                ServerShip s = sw.getLoadedShips().getById(id);
                if (s != null) sw.deleteShip(s);
            }
            ShipSteeringManager.MAST_COUNTS.remove(id);
            ShipHealthState.SINKING_SHIPS.remove(id);
            ShipRegistryState.get(server.getOverworld()).removeShip(id);
        }
    }
}
