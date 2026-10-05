package net.get900.pixelpirates.homestead.town;

import net.get900.pixelpirates.homestead.furniture.SeatBlock;
import net.get900.pixelpirates.homestead.furniture.SeatEntity;
import net.get900.pixelpirates.world.AiShipController;
import net.get900.pixelpirates.world.KeelBreaker;
import net.get900.pixelpirates.world.ShipRegistryState;
import net.get900.pixelpirates.world.ShipSteeringManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.VillagerData;
import net.minecraft.village.VillagerProfession;
import net.minecraft.village.VillagerType;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * CREWS ASHORE (2026-10-05; the user's idea: "when AI ships are in the harbour their sailors come ashore and drink in
 * the tavern"). On a quiet evening (no big event, market, festival or wedding; 1 day in {@link #ONE_IN}) a visiting
 * ship comes in from the sea at {@link #ARRIVE} and anchors off the harbour mouth ({@link #ANCHORAGE}). Once she's in,
 * {@link #SAILORS} of her crew "row ashore" and take stools in the Grog Barrel (villagers with her name on them - they
 * sit and drink; the regulars leave them their seats), until {@link #BACK_ABOARD}; she weighs anchor at
 * {@link #SAIL} and is gone. The town news has it. Test: /pptown event crew.
 */
public final class CrewsAshore {
    private CrewsAshore() {}

    static final int ARRIVE = 10000, BACK_ABOARD = 18000, SAIL = 22500, ONE_IN = 3, SAILORS = 4;
    static final Vec3d ANCHORAGE = new Vec3d(-40, 62, 175), OUT = new Vec3d(-40, 62, 420);
    static final BlockPos SPAWN = new BlockPos(-40, 75, 300);
    static final String TAG = "pp_sailor";
    /** Her name, blueprint, and the sailors' kind. */
    static final String[][] VISITORS = {{"the Silver Herring", "merchant_lugger", "fisherman"}, {"HMS Kestrel", "navy_cutter", "armorer"},
            {"the Coral Queen", "merchant_lugger", "cartographer"}, {"the Morning Star", "navy_cutter", "weaponsmith"}};

    enum Phase { IDLE, COMING, ANCHORED, LEAVING }

    private static Phase phase = Phase.IDLE;
    private static long shipId = -1, phaseAt, lastDay = -1;
    private static String[] visitor = VISITORS[0];
    private static final List<UUID> ashore = new ArrayList<>();
    private static boolean wentAshore, cameBack;

    static boolean active() { return phase != Phase.IDLE; }

    static String force(ServerWorld w) {
        if (active()) return "A ship is already in.";
        return begin(w, true) ? "A visiting ship is coming in - her crew will be in the Grog Barrel tonight." : "She couldn't be spawned (something in the way?).";
    }

    /** TownLife, every 40. */
    static void tick(ServerWorld w) {
        long day = w.getTimeOfDay() / 24000L;
        int tod = (int) (w.getTimeOfDay() % 24000L);
        if (phase == Phase.IDLE) {
            if (lastDay != day && tod >= ARRIVE && tod < ARRIVE + 400) {
                lastDay = day;
                boolean quiet = TownCalendar.free(day) && TownCalendar.today(w) == null && !LimpingShip.active() && !Regatta.active();
                if (quiet && Math.floorMod(Objects.hash(day, "crew"), ONE_IN) == 0) begin(w, false);
            }
            return;
        }
        Vector3d at = AiShipController.shipPos(w, shipId);
        if (at == null && w.getTime() - phaseAt > 200) { end(w); return; }
        if (at == null) return;
        AiShipController.AiShipData d = AiShipController.AI_SHIPS.get(shipId);
        switch (phase) {
            case COMING -> {
                if (Math.hypot(at.x - ANCHORAGE.x, at.z - ANCHORAGE.z) < 16 || w.getTime() - phaseAt > 7200) {
                    if (d != null) d.raceTarget = null;
                    ShipSteeringManager.ANCHORED_SHIPS.add(shipId);
                    phase = Phase.ANCHORED;
                    phaseAt = w.getTime();
                    TownEvents.broadcastTown(w, "[Harbour] " + cap(visitor[0]) + " drops anchor off the harbour mouth - her boats are pulling for the quay.");
                }
            }
            case ANCHORED -> {
                boolean forced = forcedAt >= 0 && w.getTime() - forcedAt < 6000;
                boolean evening = forced || tod >= ARRIVE && tod < BACK_ABOARD;
                if (!wentAshore && evening && w.getTime() - phaseAt > 200) { wentAshore = true; comeAshore(w); }
                if (wentAshore && !cameBack && (!evening || forced && w.getTime() - forcedAt > 4800)) { cameBack = true; backAboard(w); }
                boolean sail = forced ? w.getTime() - forcedAt > 5400 : tod >= SAIL || tod < ARRIVE && cameBack;
                if (cameBack && sail) {
                    ShipSteeringManager.ANCHORED_SHIPS.remove(shipId);
                    if (d != null) d.raceTarget = OUT;
                    phase = Phase.LEAVING;
                    phaseAt = w.getTime();
                    TownEvents.broadcastTown(w, "[Harbour] " + cap(visitor[0]) + " weighs anchor and stands out to sea.");
                }
            }
            case LEAVING -> {
                if (Math.hypot(at.x - ANCHORAGE.x, at.z - ANCHORAGE.z) > 200 || w.getTime() - phaseAt > 4800) end(w);
            }
            default -> { }
        }
    }

    private static long forcedAt = -1;

    private static boolean begin(ServerWorld w, boolean forced) {
        visitor = VISITORS[Math.floorMod((int) (w.getTimeOfDay() / 24000L), VISITORS.length)];
        try {
            shipId = AiShipController.spawn(w, visitor[1], SPAWN, 50);
        } catch (Exception e) {
            net.get900.pixelpirates.PixelPirates.LOGGER.warn("[Harbour] the visiting ship could not be spawned: {}", e.getMessage());
            return false;
        }
        AiShipController.AiShipData d = AiShipController.AI_SHIPS.get(shipId);
        if (d != null) { d.racing = true; d.raceTarget = ANCHORAGE; d.raceSkill = 0.7f; }   // scripted: in, anchor, out
        ShipRegistryState.get(w.getServer().getOverworld()).setUpgradeLevel(shipId, KeelBreaker.KEY, 2);
        phase = Phase.COMING;
        phaseAt = w.getTime();
        forcedAt = forced ? w.getTime() : -1;
        wentAshore = cameBack = false;
        TownEvents.broadcastTown(w, "[Harbour] A sail to the south - " + visitor[0] + " is putting into Wavebreak for the night.");
        return true;
    }

    /** Her sailors take free stools in the Grog Barrel and drink. */
    private static void comeAshore(ServerWorld w) {
        int n = 0;
        for (int i = 0; i < SAILORS; i++) {
            BlockPos seat = TownLife.freeSeat(w, TownLife.tavernBox(), "sailor" + i);
            if (seat == null || !(w.getBlockState(seat).getBlock() instanceof SeatBlock sb)) break;
            VillagerEntity v = EntityType.VILLAGER.create(w);
            if (v == null) break;
            v.refreshPositionAndAngles(seat.getX() + 0.5, seat.getY(), seat.getZ() + 0.5, 0, 0);
            v.initialize(w, w.getLocalDifficulty(seat), SpawnReason.EVENT, null, null);
            VillagerProfession prof = net.minecraft.registry.Registries.VILLAGER_PROFESSION.get(new net.minecraft.util.Identifier(visitor[2]));
            v.setVillagerData(new VillagerData(VillagerType.PLAINS, prof, 2));
            v.setCustomName(Text.literal("Sailor off " + visitor[0]));
            v.setAiDisabled(true);
            v.setInvulnerable(true);
            v.addCommandTag(TAG);
            w.spawnEntity(v);
            SeatEntity.sit(w, seat, sb.seatHeight(), v);
            ashore.add(v.getUuid());
            n++;
        }
        if (n > 0) {
            TownEvents.broadcastTown(w, "[Grog Barrel] " + n + " sailors off " + visitor[0] + " come stamping in, calling for ale!");
            TownMemory.news(w, "ship", "The crew of " + visitor[0] + " drank the Grog Barrel half dry last night.", "rufus");
        }
    }

    private static void backAboard(ServerWorld w) {
        for (UUID u : ashore) {
            Entity e = w.getEntity(u);
            if (e != null) { e.stopRiding(); e.discard(); }
        }
        ashore.clear();
        for (int i = 0; i < SAILORS; i++) TownLife.releaseSeat("sailor" + i);
        TownEvents.broadcastTown(w, "[Grog Barrel] The sailors off " + visitor[0] + " stagger back to their boats, singing.");
    }

    private static void end(ServerWorld w) {
        if (!cameBack) backAboard(w);
        if (shipId >= 0) {
            ShipSteeringManager.ANCHORED_SHIPS.remove(shipId);
            AiShipController.despawn(shipId, w.getServer());
        }
        shipId = -1;
        phase = Phase.IDLE;
    }

    private static String cap(String s) { return Character.toUpperCase(s.charAt(0)) + s.substring(1); }
}
