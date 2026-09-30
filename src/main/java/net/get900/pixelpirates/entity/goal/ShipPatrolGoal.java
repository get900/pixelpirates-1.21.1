package net.get900.pixelpirates.entity.goal;

import net.minecraft.block.BlockState;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.util.math.BlockPos;

import java.util.EnumSet;

/**
 * Wanders within a fixed radius of a stored home position (the ship deck).
 * Pirates call setHome() once after spawn; until then the goal is dormant.
 * Replaces WanderAroundFarGoal so mobs don't path off the ship edge.
 */
public class ShipPatrolGoal extends Goal {

    private final PathAwareEntity mob;
    private final double speed;
    private final float radius;

    private double homeX, homeY, homeZ;
    private boolean homeSet = false;

    public ShipPatrolGoal(PathAwareEntity mob, double speed, float radius) {
        this.mob = mob;
        this.speed = speed;
        this.radius = radius;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    public void setHome(double x, double y, double z) {
        this.homeX = x;
        this.homeY = y;
        this.homeZ = z;
        this.homeSet = true;
    }

    public boolean isHomeSet() { return homeSet; }
    public double getHomeX()   { return homeX; }
    public double getHomeY()   { return homeY; }
    public double getHomeZ()   { return homeZ; }

    @Override
    public boolean canStart() {
        return homeSet && mob.getNavigation().isIdle() && mob.getRandom().nextInt(120) == 0;
    }

    @Override
    public boolean shouldContinue() {
        return !mob.getNavigation().isIdle();
    }

    @Override
    public void start() {
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = mob.getRandom().nextDouble() * Math.PI * 2;
            double dist  = mob.getRandom().nextDouble() * radius;
            double tx    = homeX + Math.cos(angle) * dist;
            double tz    = homeZ + Math.sin(angle) * dist;

            // Scan downward from homeY+3 to find a solid surface within 5 blocks
            for (int dy = 3; dy >= -5; dy--) {
                BlockPos check = BlockPos.ofFloored(tx, homeY + dy, tz);
                BlockState air   = mob.getWorld().getBlockState(check);
                BlockState below = mob.getWorld().getBlockState(check.down());
                if (air.isAir() && !below.isAir()) {
                    mob.getNavigation().startMovingTo(tx, check.getY(), tz, speed);
                    return;
                }
            }
        }
    }
}
