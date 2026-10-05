package net.get900.pixelpirates.homestead.town;

import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.world.AiShipController;
import net.get900.pixelpirates.world.KeelBreaker;
import net.get900.pixelpirates.world.ShipHealthState;
import net.get900.pixelpirates.world.ShipRegistryState;
import net.get900.pixelpirates.world.ShipSteeringManager;
import net.get900.pixelpirates.world.faction.Faction;
import net.get900.pixelpirates.world.faction.FactionManager;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * A SHIP LIMPS IN (2026-10-05; the user's idea: "a damaged merchant ship drifts into the harbour; townsfolk rush to the
 * quay with bandages and ropes; the doctor patches the crew up"). Now and then on a quiet day (no market, festival,
 * wedding or big event; 1 day in {@link #ONE_IN}, at {@link #AT}) a battered merchant ship - smoking, at a third of her
 * hull - crawls in from the open sea (AiShipController's scripted mode, slow) to a berth between the grand pier and the
 * east pier. When she's in: Dr Marrow, Martha, Finn and Hob run out along the piers with bandages and ropes, the town
 * crowds the quay, and for {@link #TENDING} ticks the crew are patched up (hearts over the ship). A player can help by
 * giving Sea Bandages through Dr Marrow ("Help with the wounded") - the grateful captain pays 5 coins a bandage (up to
 * 8) and the Merchants remember it. Then she's patched, sails out again, and is gone. Test: /pptown event wreck.
 */
public final class LimpingShip {
    private LimpingShip() {}

    static final int AT = 3000, ONE_IN = 10, TENDING = 2400;
    static final Vec3d BERTH = new Vec3d(20, 62, 118), OUT = new Vec3d(20, 62, 420);
    static final BlockPos SPAWN = new BlockPos(20, 75, 285);
    static final String[] NAMES = {"the Silver Gull", "the Patience", "the Good Hope", "the Mary Rose", "the Bonny Kate"};
    /** The helpers and where they work: on the grand pier's east edge and the east pier's west edge, facing the berth. */
    static final Map<String, int[]> HELPERS = Map.of("elias", new int[]{3, 116}, "martha", new int[]{3, 121},
            "finn", new int[]{40, 114}, "hob", new int[]{40, 121});

    enum Phase { IDLE, COMING, TENDED, LEAVING }

    private static Phase phase = Phase.IDLE;
    private static long shipId = -1, phaseAt, lastDay = -1;
    private static String name = "";
    private static final Set<UUID> helped = new HashSet<>();

    static boolean active() { return phase != Phase.IDLE; }
    /** Players may hand bandages to Dr Marrow while she's coming in or being tended. */
    static boolean needsBandages() { return phase == Phase.COMING || phase == Phase.TENDED; }

    // ------------------------------------------------------------------ the town
    static TownLife.Plan plan(ServerWorld w, Townsfolk.Folk f, Townsfolk.Phase ph) {
        if (phase != Phase.TENDED || ph == Townsfolk.Phase.SLEEP || ph == Townsfolk.Phase.SERVICE) return null;
        int[] at = HELPERS.get(f.id());
        if (at != null) {
            TownLife.Plan p = new TownLife.Plan(ph, TownLife.groundAt(w, at[0], at[1]));
            p.look = BERTH.add(0, 3, 0);
            return p;
        }
        if (ph != Townsfolk.Phase.LEISURE && ph != Townsfolk.Phase.HOME) return null;
        int h = Math.floorMod(Objects.hash(f.id(), "limp"), 30);           // the crowd on the quay by the berth
        TownLife.Plan p = new TownLife.Plan(ph, TownLife.groundAt(w, 5 + h, 95));
        p.look = BERTH.add(0, 3, 0);
        return p;
    }

    // ------------------------------------------------------------------ the day (TownLife, every 40)
    static String force(ServerWorld w) {
        if (active()) return "A ship is already in trouble.";
        return begin(w) ? "A battered merchant ship is limping in from the south." : "She couldn't be spawned (something in the way?).";
    }

    static void tick(ServerWorld w) {
        long day = w.getTimeOfDay() / 24000L;
        int tod = (int) (w.getTimeOfDay() % 24000L);
        if (phase == Phase.IDLE) {
            if (lastDay != day && tod >= AT && tod < AT + 400) {
                lastDay = day;
                boolean quiet = TownCalendar.free(day) && TownCalendar.today(w) == null;
                if (quiet && Math.floorMod(Objects.hash(day, "limp"), ONE_IN) == 0) begin(w);
            }
            return;
        }
        Vector3d at = AiShipController.shipPos(w, shipId);
        if (at == null && w.getTime() - phaseAt > 200) { end(w); return; }
        if (at == null) return;
        Vec3d pos = new Vec3d(at.x, at.y, at.z);
        AiShipController.AiShipData d = AiShipController.AI_SHIPS.get(shipId);
        switch (phase) {
            case COMING -> {
                smoke(w, pos);
                if (Math.hypot(pos.x - BERTH.x, pos.z - BERTH.z) < 16 || w.getTime() - phaseAt > 7200) {
                    if (d != null) d.raceTarget = null;
                    ShipSteeringManager.ANCHORED_SHIPS.add(shipId);
                    phase = Phase.TENDED;
                    phaseAt = w.getTime();
                    TownEvents.broadcastTown(w, "[Harbour] " + cap(name) + " drops anchor off the grand pier - the town runs down to help! Dr Marrow is tending the wounded.");
                    w.playSound(null, BlockPos.ofFloored(BERTH), SoundEvents.BLOCK_BELL_USE, SoundCategory.NEUTRAL, 3f, 0.7f);
                    TownLife.replanAll(w);
                }
            }
            case TENDED -> {
                if (w.random.nextInt(2) == 0) smoke(w, pos);
                w.spawnParticles(ParticleTypes.HEART, pos.x, pos.y + 3, pos.z, 2, 3, 1, 3, 0);
                for (String id : HELPERS.keySet()) {
                    TownsfolkEntity e = TownLife.live(w, id);
                    if (e != null && w.random.nextInt(3) == 0) e.triggerAnim("action", "flourish");
                }
                if (w.getTime() - phaseAt > TENDING) {
                    ShipSteeringManager.ANCHORED_SHIPS.remove(shipId);
                    ShipHealthState.get(w).setHealth(shipId, ShipHealthState.get(w).maxHp(shipId) * 3 / 4);
                    if (d != null) d.raceTarget = OUT;
                    phase = Phase.LEAVING;
                    phaseAt = w.getTime();
                    TownEvents.broadcastTown(w, "[Harbour] The crew of " + name + " are patched up and her rigging spliced - she weighs anchor with a cheer for Wavebreak!");
                    TownMemory.news(w, "ship", cap(name) + " limped into harbour half-wrecked - Dr Marrow patched up her whole crew, and she sailed again.", "elias");
                    for (TownsfolkEntity e : w.getEntitiesByClass(TownsfolkEntity.class, new Box(-10, 60, 88, 50, 75, 130), x -> true)) e.triggerAnim("action", "wave");
                    TownLife.replanAll(w);
                }
            }
            case LEAVING -> {
                if (Math.hypot(pos.x - BERTH.x, pos.z - BERTH.z) > 200 || w.getTime() - phaseAt > 4800) end(w);
            }
            default -> { }
        }
    }

    private static boolean begin(ServerWorld w) {
        try {
            shipId = AiShipController.spawn(w, "merchant_lugger", SPAWN, 50);
        } catch (Exception e) {
            net.get900.pixelpirates.PixelPirates.LOGGER.warn("[Harbour] the limping ship could not be spawned: {}", e.getMessage());
            return false;
        }
        AiShipController.AiShipData d = AiShipController.AI_SHIPS.get(shipId);
        if (d != null) { d.racing = true; d.raceTarget = BERTH; d.raceSkill = 0.6f; }   // scripted, slow: she's barely afloat
        ShipRegistryState.get(w.getServer().getOverworld()).setUpgradeLevel(shipId, KeelBreaker.KEY, 2);
        ShipHealthState.get(w).setHealth(shipId, ShipHealthState.get(w).maxHp(shipId) / 3);
        name = NAMES[Math.floorMod((int) (w.getTimeOfDay() / 24000L), NAMES.length)];
        phase = Phase.COMING;
        phaseAt = w.getTime();
        helped.clear();
        TownEvents.broadcastTown(w, "[Harbour] Smoke on the southern horizon - a merchant ship, " + name + ", is limping towards the harbour! (Bring Sea Bandages to Dr Marrow.)");
        return true;
    }

    private static void end(ServerWorld w) {
        if (shipId >= 0) {
            ShipSteeringManager.ANCHORED_SHIPS.remove(shipId);
            AiShipController.despawn(shipId, w.getServer());
        }
        shipId = -1;
        phase = Phase.IDLE;
        TownLife.replanAll(w);
    }

    private static void smoke(ServerWorld w, Vec3d pos) {
        w.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.x + w.random.nextGaussian(), pos.y + 2, pos.z + w.random.nextGaussian(), 2, 0.3, 0.2, 0.3, 0.02);
    }

    /** Dr Marrow's card: hand over Sea Bandages (up to 8) - 5 coins each from the grateful captain, Merchants' goodwill. */
    static String help(ServerPlayerEntity p) {
        if (!needsBandages()) return "Nobody needs patching just now, thank heavens.";
        if (helped.contains(p.getUuid())) return "You've done your share, captain - I can manage the rest.";
        ItemStack held = p.getMainHandStack();
        if (!held.isOf(ModItems.SEA_BANDAGE)) return "Bandages, captain! Sea Bandages - as many as you can spare. Hold them out to me.";
        int n = Math.min(8, held.getCount());
        if (!p.isCreative()) held.decrement(n);
        helped.add(p.getUuid());
        p.getInventory().offerOrDrop(new ItemStack(ModItems.COIN, n * 5));
        FactionManager.modifyReputation(p, Faction.MERCHANTS, 5 * n);
        TownMemory.befriend(p, Townsfolk.get("elias"), 6);
        p.sendMessage(Text.literal("[Harbour] The captain of " + name + " presses " + (n * 5) + " coins into your hand. \"We'd have lost them without you.\" (+" + (5 * n) + " Merchants standing)").formatted(Formatting.GOLD), false);
        return "Bless you - " + n + " bandages! That's the bosun and both the cabin boys seen to.";
    }

    private static String cap(String s) { return Character.toUpperCase(s.charAt(0)) + s.substring(1); }
}
