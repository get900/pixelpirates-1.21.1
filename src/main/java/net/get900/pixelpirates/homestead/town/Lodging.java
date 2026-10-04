package net.get900.pixelpirates.homestead.town;

import net.get900.pixelpirates.homestead.furniture.SeatBlock;
import net.get900.pixelpirates.homestead.furniture.SeatEntity;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * THE MARKET KEEPERS' EVENINGS (2026-10-04, the user: "make it so the market people go and sleep somewhere too - we have a
 * lot of available beds in the inn"). The seven Port Traders and the Map Merchant mind their booths by day; at dusk they
 * shut up shop and go to the inn for a sit-down in the common room, and at night they sleep in an inn bed of their own
 * (TownLife.innBed - the same bed every night). Each keeper carries a Mover; its tick returns true while they're away
 * from the booth, so their own "back to the booth" logic waits until morning.
 */
public final class Lodging {
    private Lodging() {}

    /** Day ticks: the market shuts at SHUT (about 7 pm), bed at BED, up again at RISE. */
    public static final int SHUT = 13000, BED = 16500, RISE = 23500;

    public static final class Mover {
        private int phase = -1;                          // 0 booth, 1 the inn's common room, 2 bed
        private boolean arrived;
        @Nullable private BlockPos to, seat;
        private double best = Double.MAX_VALUE;
        private int noProgress;

        /** True while the keeper is off duty (the caller then skips its booth logic). */
        public boolean tick(PathAwareEntity e, String id) {
            if (!(e.getWorld() instanceof ServerWorld w) || (e.age + e.getId()) % 10 != 0) return phase > 0;
            int t = (int) Math.floorMod(w.getTimeOfDay(), 24000L);
            int now = t >= BED && t < RISE ? 2 : t >= SHUT && t < BED ? 1 : 0;
            if (now != phase) {
                leave(e, id);
                phase = now;
                arrived = false;
                best = Double.MAX_VALUE;
                noProgress = 0;
                to = null;
                if (now == 1) {
                    seat = TownLife.freeSeat(w, TownLife.INN_BOX, id);
                    to = seat != null ? TownLife.standNear(w, seat, 2) : TownLife.standNear(w, new BlockPos(-60, 68, 28), 4);
                } else if (now == 2) {
                    BlockPos bed = TownLife.innBed(w, id);
                    to = bed != null ? TownLife.standNear(w, bed, 2) : null;
                    seat = bed;
                }
            }
            if (phase == 0 || to == null) return phase > 0;
            if (!arrived) travel(w, e);
            else if (phase == 1 && seat != null && !e.hasVehicle()) phase = -1;           // knocked off the stool: think again
            else if (phase == 2 && seat != null && !e.isSleeping()) phase = -1;
            return true;
        }

        private void travel(ServerWorld w, PathAwareEntity e) {
            Vec3d at = Vec3d.ofBottomCenter(to);
            double d = e.getPos().distanceTo(at);
            if (d < 1.4) { arrive(w, e); return; }
            if (!TownLife.watched(w, e.getPos(), 40) && !TownLife.watched(w, at, 40) && w.isChunkLoaded(to)) {
                e.getNavigation().stop();
                e.refreshPositionAndAngles(at.x, at.y, at.z, e.getYaw(), 0);
                arrive(w, e);
                return;
            }
            if (e.getNavigation().isIdle()) {
                Vec3d goal = at;
                if (d > 40) goal = Vec3d.ofBottomCenter(TownLife.standNear(w, BlockPos.ofFloored(e.getPos().add(at.subtract(e.getPos()).normalize().multiply(32))), 5));
                e.getNavigation().startMovingTo(goal.x, goal.y, goal.z, 0.55);
            }
            if (d < best - 0.3) { best = d; noProgress = 0; }
            else if (++noProgress > 20) {
                noProgress = 0;
                if (!TownLife.watched(w, e.getPos(), 12) && w.isChunkLoaded(to)) { e.refreshPositionAndAngles(at.x, at.y, at.z, e.getYaw(), 0); arrive(w, e); }
            }
        }

        private void arrive(ServerWorld w, PathAwareEntity e) {
            arrived = true;
            e.getNavigation().stop();
            if (seat == null) return;
            BlockState s = w.getBlockState(seat);
            if (phase == 1 && s.getBlock() instanceof SeatBlock sb) SeatEntity.sit(w, seat, sb.seatHeight(), e);
            else if (phase == 2 && s.getBlock() instanceof BedBlock && !s.get(BedBlock.OCCUPIED)) e.sleep(seat);
        }

        private void leave(PathAwareEntity e, String id) {
            if (e.hasVehicle()) e.stopRiding();
            if (e.isSleeping()) e.wakeUp();
            TownLife.releaseSeat(id);
        }

        public boolean asleep(PathAwareEntity e) { return e.isSleeping(); }
    }
}
