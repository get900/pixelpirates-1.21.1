package net.get900.pixelpirates.entity.goal;

import net.get900.pixelpirates.entity.custom.CursedMonkeyEntity;
import net.get900.pixelpirates.world.AiShipController;
import net.get900.pixelpirates.world.ShipSteeringManager;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.internal.world.VsiServerShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.EnumSet;
import java.util.List;

public class MonkeyThiefGoal extends Goal {

    private static final double SWIM_SPEED   = 0.65; // blocks/tick — roughly dolphin speed
    private static final double GRAVITY      = 0.08; // Minecraft gravity per tick
    private static final double LEAP_BUFFER  = 3.0;  // extra height clearance above target
    private static final double MAX_H_SPEED  = 1.1;  // cap horizontal leap velocity

    private final CursedMonkeyEntity monkey;
    private PlayerEntity target;
    private int scanCooldown;
    private int actionTimer;
    private Vec3d fleePos;

    public MonkeyThiefGoal(CursedMonkeyEntity monkey) {
        this.monkey = monkey;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (monkey.getMonkeyPhase() != CursedMonkeyEntity.Phase.IDLE) return false;
        if (scanCooldown > 0) { scanCooldown--; return false; }
        target = findTarget();
        return target != null;
    }

    @Override
    public boolean shouldContinue() {
        CursedMonkeyEntity.Phase phase = monkey.getMonkeyPhase();
        if (phase == CursedMonkeyEntity.Phase.IDLE) return false;
        if (phase == CursedMonkeyEntity.Phase.FLEEING) return actionTimer > 0;
        return target != null && target.isAlive();
    }

    @Override
    public void start() {
        monkey.setMonkeyPhase(CursedMonkeyEntity.Phase.STALKING);
        actionTimer = 0;
        fleePos = null;
    }

    @Override
    public void tick() {
        switch (monkey.getMonkeyPhase()) {
            case STALKING -> tickStalking();
            case LEAPING  -> tickLeaping();
            case FLEEING  -> tickFleeing();
            default -> {}
        }
    }

    @Override
    public void stop() {
        monkey.setMonkeyPhase(CursedMonkeyEntity.Phase.IDLE);
        monkey.getNavigation().stop();
        target = null;
        fleePos = null;
        scanCooldown = 480;
    }

    // ── phase ticks ───────────────────────────────────────────────────────────

    private void tickStalking() {
        if (target == null || !target.isAlive()) { stop(); return; }
        monkey.getLookControl().lookAt(target, 30f, 30f);

        double dx = target.getX() - monkey.getX();
        double dz = target.getZ() - monkey.getZ();
        double hDist = Math.sqrt(dx * dx + dz * dz);

        if (monkey.isTouchingWater()) {
            // Direct dolphin-speed velocity — bypasses navigation's speed cap entirely.
            // Also push upward toward the surface so the monkey stays ready to leap.
            double swimY = monkey.getVelocity().y;
            if (monkey.getY() < 62.5) swimY += 0.12; // surface quickly
            if (hDist > 0.1) {
                monkey.setVelocity((dx / hDist) * SWIM_SPEED, swimY, (dz / hDist) * SWIM_SPEED);
                monkey.velocityDirty = true;
            }
            // Leap when close enough horizontally — use hDist only so a ship hull
            // 10+ blocks tall doesn't prevent the trigger (player is far above in Y)
            if (hDist < 5.0) leap(dx, dz, hDist);
        } else {
            // On land: use navigation normally
            monkey.getNavigation().startMovingTo(target, 1.3);
            if (monkey.distanceTo(target) < 3.5) leap(dx, dz, hDist);
        }
    }

    private void leap(double dx, double dz, double hDist) {
        // Calculate vertical velocity needed to clear the target's deck height + buffer.
        double dy = Math.max(2.0, target.getY() - monkey.getY() + LEAP_BUFFER);
        double vy = Math.sqrt(2.0 * GRAVITY * dy); // v = sqrt(2gh)

        // Horizontal velocity to cover the gap while in the air.
        double timeToPeak = vy / GRAVITY;
        double vh = hDist > 0.1 ? Math.min(hDist / timeToPeak, MAX_H_SPEED) : MAX_H_SPEED * 0.5;

        Vec3d dir = hDist > 0.1 ? new Vec3d(dx / hDist, 0, dz / hDist) : Vec3d.ZERO;
        monkey.setVelocity(dir.x * vh, vy, dir.z * vh);
        monkey.velocityDirty = true;
        monkey.getNavigation().stop();
        monkey.setMonkeyPhase(CursedMonkeyEntity.Phase.LEAPING);
        // Give the monkey 5 seconds to land and reach the player before giving up
        actionTimer = 100;
    }

    private void tickLeaping() {
        if (target != null) monkey.getLookControl().lookAt(target, 30f, 30f);

        // Once landed on the ship deck, navigate to the player and steal on contact
        if (monkey.isOnGround() && !monkey.isTouchingWater() && target != null) {
            monkey.getNavigation().startMovingTo(target, 1.3);
            if (monkey.distanceTo(target) < 2.5) {
                doSteal();
                fleePos = buildFleePos();
                monkey.setMonkeyPhase(CursedMonkeyEntity.Phase.FLEEING);
                actionTimer = 220;
                return;
            }
        }

        // Timeout — missed the ship, just flee
        if (--actionTimer <= 0) {
            fleePos = buildFleePos();
            monkey.setMonkeyPhase(CursedMonkeyEntity.Phase.FLEEING);
            actionTimer = 220;
        }
    }

    private void tickFleeing() {
        actionTimer--;
        if (monkey.isTouchingWater() && fleePos != null) {
            // Dolphin-speed fleeing in water
            double dx = fleePos.x - monkey.getX();
            double dz = fleePos.z - monkey.getZ();
            double hDist = Math.sqrt(dx * dx + dz * dz);
            if (hDist > 1.0) {
                monkey.setVelocity((dx / hDist) * SWIM_SPEED, monkey.getVelocity().y, (dz / hDist) * SWIM_SPEED);
                monkey.velocityDirty = true;
            }
        } else if (actionTimer % 30 == 0 && fleePos != null) {
            monkey.getNavigation().startMovingTo(fleePos.x, fleePos.y, fleePos.z, 1.6);
        }
        if (actionTimer <= 0) stop();
    }

    // ── steal ─────────────────────────────────────────────────────────────────

    private void doSteal() {
        if (target == null) return;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = target.getInventory().getStack(slot);
            if (stack.isEmpty()) continue;
            ItemStack stolen = stack.copy();
            stack.decrement(1);
            target.getInventory().setStack(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
            monkey.setStolenItem(stolen);
            monkey.playSound(SoundEvents.ENTITY_ITEM_PICKUP, 1.2f, 0.6f);
            target.sendMessage(Text.literal(
                "§cA Cursed Monkey stole your §e" + stolen.getName().getString() + "§c!"), true);
            break;
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Vec3d buildFleePos() {
        if (target == null) return monkey.getPos().add(20, 0, 0);
        Vec3d away = monkey.getPos().subtract(target.getPos()).normalize().multiply(50);
        return monkey.getPos().add(away.x, 0, away.z);
    }

    private PlayerEntity findTarget() {
        List<PlayerEntity> nearby = monkey.getWorld().getEntitiesByClass(
                PlayerEntity.class,
                monkey.getBoundingBox().expand(60),
                p -> !p.isSpectator() && !p.isCreative() && hasHotbarItem(p) && isOnShip(p)
        );
        if (nearby.isEmpty()) return null;
        PlayerEntity closest = null;
        double best = Double.MAX_VALUE;
        for (PlayerEntity p : nearby) {
            double d = monkey.squaredDistanceTo(p);
            if (d < best) { best = d; closest = p; }
        }
        return closest;
    }

    private static boolean hasHotbarItem(PlayerEntity player) {
        for (int i = 0; i < 9; i++) {
            if (!player.getInventory().getStack(i).isEmpty()) return true;
        }
        return false;
    }

    // Mirror the music-system proximity check — getShipManagingBlock() uses ship-space
    // coords so world-space block-pos checks always return null.
    private boolean isOnShip(PlayerEntity player) {
        if (!(monkey.getWorld() instanceof ServerWorld serverWorld)) return false;
        VsiServerShipWorld sw = VSGameUtilsKt.getShipObjectWorld(serverWorld);
        if (sw == null) return false;
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        for (long shipId : ShipSteeringManager.MAST_COUNTS.keySet()) {
            if (AiShipController.AI_SHIPS.containsKey(shipId)) continue;
            ServerShip ship = sw.getLoadedShips().getById(shipId);
            if (ship == null) continue;
            Vector3dc sp = ship.getTransform().getPositionInWorld();
            double dx = px - sp.x(), dz = pz - sp.z(), dy = py - sp.y();
            if (dx * dx + dz * dz < 625 && Math.abs(dy) < 10) return true;
        }
        return false;
    }
}
