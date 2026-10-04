package net.get900.pixelpirates.homestead.town;

import net.get900.pixelpirates.block.custom.FortCannonBlockEntity;
import net.get900.pixelpirates.world.faction.FactionManager;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FORT SECURITY (2026-10-05): the Governor's grounds are guarded. A player the island's faction counts an enemy
 * (reputation < 0 with {@link FortCannonBlockEntity#ISLAND_FACTION}, the same test as the fortress guns) who walks onto
 * the Residence grounds or into the Fortress is challenged by the nearest guard ON DUTY ("Halt!"), and if they are
 * still inside {@link #GRACE} ticks later they are marched out of the gate. No guard awake and in reach = no challenge,
 * so a wanted captain can still sneak in at night. A pardon from the Governor (Service.PARDON) ends it.
 * Ticked from TownLife every 40 ticks.
 */
public final class Garrison {
    private Garrison() {}

    /** x1, y1, z1, x2, y2, z2 of the guarded grounds, the gate to march them out of, and who guards it. */
    record Zone(String name, int[] box, BlockPos exit, List<String> guards) {
        boolean inside(ServerPlayerEntity p) {
            return p.getX() >= box[0] && p.getX() <= box[3] + 1 && p.getY() >= box[1] && p.getY() <= box[4] + 1
                    && p.getZ() >= box[2] && p.getZ() <= box[5] + 1;
        }
    }

    static final List<Zone> ZONES = List.of(
            new Zone("the Governor's Residence", new int[]{12, 66, -88, 53, 100, -50}, new BlockPos(30, 0, -45),
                    List.of("pell", "quayle", "crane", "dobbs")),
            new Zone("the Governor's Fortress", new int[]{-118, 80, -140, -80, 125, -100}, new BlockPos(-70, 0, -120),
                    List.of("ruddock", "ashby", "hale", "finch", "brask")));

    static final int GRACE = 100, REACH = 48;
    private static final Map<UUID, Long> WARNED = new HashMap<>();

    static void tick(ServerWorld w) {
        long now = w.getTime();
        for (ServerPlayerEntity p : w.getPlayers()) {
            if (p.isSpectator() || p.isCreative()) continue;
            Zone z = ZONES.stream().filter(zz -> zz.inside(p)).findFirst().orElse(null);
            if (z == null || FactionManager.getReputation(p, FortCannonBlockEntity.ISLAND_FACTION) >= 0) {
                WARNED.remove(p.getUuid());
                continue;
            }
            TownsfolkEntity guard = onDuty(w, z, p);
            if (guard == null) continue;                                     // nobody awake to see you
            Long at = WARNED.get(p.getUuid());
            if (at == null) {
                WARNED.put(p.getUuid(), now);
                say(p, guard, "Halt! You're wanted by the Iron Armada, captain - off " + z.name() + ", now!");
                w.playSound(null, guard.getBlockPos(), SoundEvents.BLOCK_BELL_USE, SoundCategory.NEUTRAL, 0.6f, 1.4f);
            } else if (now - at >= GRACE) {
                WARNED.remove(p.getUuid());
                BlockPos out = w.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, z.exit());
                p.teleport(w, out.getX() + 0.5, out.getY(), out.getZ() + 0.5, p.getYaw(), p.getPitch());
                say(p, guard, "Out you go. Come back with the Governor's pardon, or not at all.");
                p.sendMessage(Text.literal("[!] " + guard.folk().name() + " marches you out of the gate.").formatted(Formatting.RED), false);
            }
        }
    }

    /** The nearest of the zone's guards that is awake, at their post or on their rounds, and within reach. */
    private static TownsfolkEntity onDuty(ServerWorld w, Zone z, ServerPlayerEntity p) {
        TownLife.State st = TownLife.state(w.getServer());
        TownsfolkEntity best = null;
        for (String id : z.guards()) {
            UUID u = st.owner.get(id);
            Entity e = u == null ? null : w.getEntity(u);
            if (!(e instanceof TownsfolkEntity t) || t.act() == TownsfolkEntity.Act.SLEEP) continue;
            if (t.squaredDistanceTo(p) > REACH * REACH) continue;
            if (best == null || t.squaredDistanceTo(p) < best.squaredDistanceTo(p)) best = t;
        }
        return best;
    }

    private static void say(ServerPlayerEntity p, TownsfolkEntity guard, String line) {
        guard.getLookControl().lookAt(p, 30, 30);
        String[] n = guard.folk().name().split(" ");                     // "Private Pell" -> [Pell]
        p.sendMessage(Text.literal("[" + n[n.length - 1] + "] ")
                .formatted(Formatting.AQUA).append(Text.literal(line).formatted(Formatting.WHITE)), false);
    }
}
