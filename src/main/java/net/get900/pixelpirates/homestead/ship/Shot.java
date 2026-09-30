package net.get900.pixelpirates.homestead.ship;

import net.get900.pixelpirates.homestead.HomesteadItems;
import net.get900.pixelpirates.homestead.gun.MusketBallEntity;
import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.world.ShipHealthState;
import net.get900.pixelpirates.world.ShipSteeringManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;

/**
 * SPECIAL SHOT for the ship cannon (#25):
 *   ROUND - the cannon ball;
 *   CHAIN - chain shot: 40% hull damage, fouls the target ship's rigging (held still for 5 s), no terrain damage;
 *   GRAPE - grape shot: a cone of 12 iron pellets (6 damage each, short range) for clearing a deck - no ship damage.
 */
public final class Shot {
    private Shot() {}

    public static final int ROUND = 0, CHAIN = 1, GRAPE = 2;
    static final int TANGLE_TICKS = 100;

    /** Ammo type of a held stack, or -1 when it is not cannon ammunition. */
    public static int of(ItemStack s) {
        if (s.isOf(ModItems.CANNON_BALL)) return ROUND;
        if (s.isOf(HomesteadItems.CHAIN_SHOT)) return CHAIN;
        if (s.isOf(HomesteadItems.GRAPE_SHOT)) return GRAPE;
        return -1;
    }

    public static String label(int ammo) { return ammo == CHAIN ? " with chain shot" : ammo == GRAPE ? " with grape shot" : ""; }

    public static void grape(ServerWorld w, PlayerEntity owner, Vec3d origin, Vec3d velocity) {
        Vec3d dir = velocity.normalize();
        double speed = Math.max(1.6, velocity.length() * 0.9);
        for (int i = 0; i < 12; i++) {
            MusketBallEntity b = new MusketBallEntity(w, owner, 6f, 22, true);
            b.setPosition(origin);
            Vec3d v = dir.add(w.random.nextGaussian() * 0.09, w.random.nextGaussian() * 0.06, w.random.nextGaussian() * 0.09).normalize().multiply(speed);
            b.setVelocity(v);
            w.spawnEntity(b);
        }
        w.spawnParticles(ParticleTypes.CRIT, origin.x, origin.y, origin.z, 20, 0.4, 0.3, 0.4, 0.4);
    }

    public static void chainImpact(ServerWorld w, Entity ball, @Nullable BlockPos hitPos, float hullDamage) {
        w.playSound(null, ball.getX(), ball.getY(), ball.getZ(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.BLOCKS, 2f, 0.6f);
        w.playSound(null, ball.getX(), ball.getY(), ball.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 1f, 1.4f);
        w.spawnParticles(ParticleTypes.SMOKE, ball.getX(), ball.getY(), ball.getZ(), 20, 0.6, 0.6, 0.6, 0.05);
        if (hitPos == null) return;
        Ship ship = ValkyrienSkies.getShipManagingBlock(w, hitPos.getX(), hitPos.getY(), hitPos.getZ());
        if (ship == null) return;
        long id = ship.getId();
        ShipHealthState.get(w).damage(w, id, hitPos, Math.max(1, Math.round(hullDamage * 0.4f)));
        if (!ShipSteeringManager.ANCHORED_SHIPS.contains(id) || ShipSteeringManager.UNANCHOR_TIMERS.containsKey(id)) {
            ShipSteeringManager.ANCHORED_SHIPS.add(id);                               // fouled rigging: she wallows to a stop
            ShipSteeringManager.UNANCHOR_TIMERS.put(id, (long) w.getServer().getTicks() + TANGLE_TICKS);
        }
        w.spawnParticles(ParticleTypes.CLOUD, ball.getX(), ball.getY() + 2, ball.getZ(), 30, 1.5, 1, 1.5, 0.02);
    }
}
