package net.get900.pixelpirates.entity.goal;

import net.get900.pixelpirates.entity.custom.SharkEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.EnumSet;

public class SharkAttackGoal extends Goal {

    private final SharkEntity shark;
    private LivingEntity target;
    private int attackCooldown = 0;

    public SharkAttackGoal(SharkEntity shark) {
        this.shark = shark;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        LivingEntity potential = shark.getTarget();
        if (!(potential instanceof net.minecraft.entity.player.PlayerEntity player)) return false;
        if (!potential.isAlive() || !potential.isTouchingWater()) return false;
        if (!shark.isTouchingWater()) return false;

        // Shark Ward: per-level chance the shark decides to ignore this player
        int sharkWardLvl = net.get900.pixelpirates.world.PirateLevelManager.getSkillLevel(player, "shark_ward");
        if (sharkWardLvl > 0 && shark.getRandom().nextFloat() < sharkWardLvl * 0.10f) return false;

        target = potential;
        return true;
    }

    @Override
    public boolean shouldContinue() {
        if (target == null || !target.isAlive()) return false;
        // Never pursue out of the shark's own element, no matter how tempting the target
        if (!shark.isTouchingWater()) return false;
        // Keep chasing even if they briefly leave water (jump back onto raft etc.) for 1s
        return target.isTouchingWater() || attackCooldown > 0;
    }

    @Override
    public void start() {
        target = shark.getTarget();
        attackCooldown = 0;
    }

    @Override
    public void stop() {
        target = null;
        shark.setTarget(null);
    }

    @Override
    public void tick() {
        if (target == null || !target.isAlive()) return;
        if (attackCooldown > 0) { attackCooldown--; }

        Vec3d toTarget = target.getPos().subtract(shark.getPos());
        double distance = toTarget.length();
        Vec3d direction = distance > 0 ? toTarget.normalize() : Vec3d.ZERO;

        // Smooth yaw tracking — faster when close (closing in for the bite)
        float turnRate = distance < 6 ? 0.25f : 0.12f;
        float targetYaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        shark.setYaw(shark.getYaw() + MathHelper.wrapDegrees(targetYaw - shark.getYaw()) * turnRate);
        shark.setBodyYaw(shark.getYaw());

        // Pitch down toward target if it's below (player swimming deeper)
        float targetPitch = (float)(-Math.toDegrees(Math.atan2(direction.y, Math.sqrt(direction.x*direction.x + direction.z*direction.z))));
        shark.setPitch(shark.getPitch() + MathHelper.wrapDegrees(targetPitch - shark.getPitch()) * 0.15f);

        // Burst speed when close, cruise speed otherwise
        float spd = distance < 8 ? 0.28f : 0.18f;
        // swim() clamps this against the shoreline — a player standing in the shallows
        // cannot bait the shark up the beach
        shark.swim(direction.multiply(spd));

        // Bite when in range
        if (attackCooldown == 0 && shark.distanceTo(target) < 2.5f) {
            shark.triggerAttackAnimation();
            shark.tryAttack(target);
            // Vary cooldown so multiple sharks don't bite simultaneously
            attackCooldown = 35 + shark.getRandom().nextInt(45);
        }
    }
}
