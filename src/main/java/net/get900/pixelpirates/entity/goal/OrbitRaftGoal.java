package net.get900.pixelpirates.entity.goal;

import net.get900.pixelpirates.entity.custom.RaftEntity;
import net.get900.pixelpirates.entity.custom.SharkEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.EnumSet;

public class OrbitRaftGoal extends Goal {

    private final SharkEntity shark;
    private final double radius;
    private final float speed;
    private RaftEntity raft;

    public OrbitRaftGoal(SharkEntity shark, double radius, float speed) {
        this.shark = shark;
        this.radius = radius;
        this.speed = speed;
        setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        raft = nearestRaft();
        return raft != null && !raft.isRemoved() && shark.getTarget() == null;
    }

    @Override
    public boolean shouldContinue() {
        if (shark.getTarget() != null) return false;
        if (raft == null || raft.isRemoved()) raft = nearestRaft();
        return raft != null;
    }

    @Override
    public void tick() {
        if (raft == null) return;
        double time = shark.age + shark.getOrbitPhaseOffset();

        // Smooth circular orbit around the raft, bobbing slowly at sea level
        double angle = time * 0.038;
        double ox = Math.cos(angle) * radius;
        double oz = Math.sin(angle) * radius;
        double targetY = shark.getWorld().getSeaLevel() - 1.8 + Math.sin(time * 0.07) * 1.0;

        Vec3d orbitPt = new Vec3d(raft.getX() + ox, targetY, raft.getZ() + oz);

        // Steer toward orbit point, applying smooth yaw turn
        Vec3d toTarget = orbitPt.subtract(shark.getPos());
        double targetYaw = Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
        float smoothYaw = shark.getYaw()
                + MathHelper.wrapDegrees((float) targetYaw - shark.getYaw()) * 0.12f;
        shark.setYaw(smoothYaw);
        shark.setBodyYaw(smoothYaw);

        // Forward thrust + gentle vertical correction toward sea level
        Vec3d forward = shark.getRotationVector().normalize().multiply(speed);
        double dy = MathHelper.clamp((targetY - shark.getY()) * 0.07, -0.15, 0.15);
        // swim() clamps this against the shoreline — orbiting a beached raft must not
        // drag the shark onto the sand with it
        shark.swim(new Vec3d(forward.x, dy, forward.z));
    }

    private RaftEntity nearestRaft() {
        return shark.getWorld()
                .getEntitiesByClass(RaftEntity.class, shark.getBoundingBox().expand(radius), e -> true)
                .stream()
                .min(Comparator.comparingDouble(shark::distanceTo))
                .orElse(null);
    }
}
