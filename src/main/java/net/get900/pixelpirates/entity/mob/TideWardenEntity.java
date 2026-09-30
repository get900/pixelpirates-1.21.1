package net.get900.pixelpirates.entity.mob;

import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.block.custom.TideSluiceBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.List;

/**
 * TIDE WARDEN - a drowned knight of the Sunken Court. Walks the flooded floor (MobSpec.seabed). Whenever a
 * Tide Sluice stands open it abandons the fight, marches to the nearest one and winds it shut over 3 s
 * (AbyssalKingEntity#closeSluice) - kill it first. Otherwise a plain trident fighter.
 */
public class TideWardenEntity extends ModMob {
    public static final int SEAL_TICKS = 60;

    public TideWardenEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initGoals() {
        super.initGoals();
        this.goalSelector.add(0, new SealGoal());
    }

    @Override
    protected List<String> extraAnims() { return List.of("seal"); }

    private AbyssalKingEntity king() {
        var type = ModMobs.TYPES.get("abyssal_king");
        if (type == null) return null;
        for (Entity e : this.getWorld().getEntitiesByType(type, this.getBoundingBox().expand(48), Entity::isAlive))
            if (e instanceof AbyssalKingEntity k) return k;
        return null;
    }

    class SealGoal extends Goal {
        private AbyssalKingEntity king;
        private BlockPos sluice;
        private Vec3d stand;
        private int ticks, repath;

        SealGoal() { this.setControls(EnumSet.of(Control.MOVE, Control.LOOK)); }

        @Override
        public boolean canStart() {
            // odd interval: vanilla only tries to start goals every other tick, so an even one can never line up
            if (age % 5 != 0 || getWorld().isClient) return false;
            king = king();
            if (king == null) return false;
            List<BlockPos> open = king.openSluices();
            if (open.isEmpty()) return false;
            BlockPos best = null; double bd = Double.MAX_VALUE;
            for (BlockPos p : open) { double d = p.getSquaredDistance(getPos()); if (d < bd) { bd = d; best = p; } }
            if (best == null || bd > 40 * 40) return false;
            sluice = best;
            BlockState st = getWorld().getBlockState(sluice);
            Direction f = st.contains(TideSluiceBlock.FACING) ? st.get(TideSluiceBlock.FACING) : Direction.NORTH;
            stand = Vec3d.ofBottomCenter(sluice.offset(f).down());
            return true;
        }

        @Override
        public boolean shouldContinue() {
            if (sluice == null || !isAlive()) return false;
            BlockState st = getWorld().getBlockState(sluice);
            return st.isOf(ModBlocks.TIDE_SLUICE) && st.get(TideSluiceBlock.OPEN);
        }

        @Override
        public void start() { ticks = 0; repath = 0; setTarget(null); }

        @Override
        public void stop() { sluice = null; getNavigation().stop(); }

        @Override
        public void tick() {
            getLookControl().lookAt(sluice.getX() + 0.5, sluice.getY() + 0.5, sluice.getZ() + 0.5);
            if (squaredDistanceTo(stand) > 2.2 * 2.2) {
                ticks = 0;
                if (--repath <= 0) { repath = 20; getNavigation().startMovingTo(stand.x, stand.y, stand.z, 1.2); }
                return;
            }
            getNavigation().stop();
            if (ticks % 20 == 0) triggerAnim(ACTION, "seal");
            if (getWorld() instanceof ServerWorld sw && ticks % 4 == 0)
                sw.spawnParticles(ParticleTypes.BUBBLE, sluice.getX() + 0.5, sluice.getY() + 0.5, sluice.getZ() + 0.5, 4, 0.4, 0.4, 0.4, 0.02);
            if (++ticks >= SEAL_TICKS && king != null && king.isAlive()) {
                king.closeSluice(sluice, Text.literal("A Tide Warden winds a sluice shut!").formatted(Formatting.DARK_AQUA));
                sluice = null;
            }
        }
    }
}
