package net.get900.pixelpirates.entity.mob;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * DROWNED MEMORIES - the attacks of the bosses you have beaten, echoing back: the Abyssal Heart's flatline (boss 9) and,
 * one last time, the Leviathan's final phase (boss 10). Each is played from the host's centre at its fighters.
 */
public final class DrownedMemories {
    private DrownedMemories() {}

    public static final String[] NAMES = {"Captain Rackham's BROADSIDE", "the Sea Serpent's PRESSURE JET", "the Kraken's HARPOONS",
            "the Revenant's QUARTERING", "the Abyssal King's CRUSHING DEPTHS", "the Ghost Captain's SOUL BEAM"};
    static final Vector3f WARN = new Vector3f(1.0f, 0.18f, 0.1f), SOUL = new Vector3f(0.3f, 0.95f, 0.85f);

    /** A running effect: return false when done. t counts up from 0. */
    @FunctionalInterface
    public interface Task { boolean tick(ServerWorld sw, int t); }

    /** Whoever is remembering. */
    public interface Host {
        LivingEntity self();
        Vec3d centre();
        List<ServerPlayerEntity> fighters(ServerWorld sw);
        void schedule(Task task);
        /** Reach of the spatial memories (the Heart's room vs the Leviathan's lagoon). */
        default double reach() { return 32; }
    }

    public static void play(Host h, ServerWorld sw, int which, List<ServerPlayerEntity> fs, ServerPlayerEntity target) {
        switch (which) {
            case 0 -> broadside(h, fs);
            case 1 -> jet(h, target);
            case 2 -> harpoons(h, sw, target);
            case 3 -> quartering(h);
            case 4 -> depths(h, fs);
            default -> soulBeam(h, target);
        }
    }

    static Vec3d closest(Vec3d a, Vec3d b, Vec3d p) {
        Vec3d ab = b.subtract(a);
        double t = MathHelper.clamp(p.subtract(a).dotProduct(ab) / ab.lengthSquared(), 0, 1);
        return a.add(ab.multiply(t));
    }

    /** Rackham: red rings under everyone, then the shells come down on them. */
    static void broadside(Host h, List<ServerPlayerEntity> fs) {
        List<Vec3d> marks = new ArrayList<>();
        for (ServerPlayerEntity p : fs) marks.add(p.getPos());
        LivingEntity self = h.self();
        h.schedule((w, t) -> {
            for (Vec3d q : marks) {
                if (t < 26 && t % 2 == 0)
                    for (int i = 0; i < 14; i++) {
                        double a = i * Math.PI / 7;
                        w.spawnParticles(new DustParticleEffect(WARN, 1.6f), q.x + Math.cos(a) * 2.6, q.y + 0.2, q.z + Math.sin(a) * 2.6, 1, 0, 0, 0, 0);
                    }
                if (t == 26) {
                    w.spawnParticles(ParticleTypes.EXPLOSION, q.x, q.y + 0.5, q.z, 3, 1, 0.5, 1, 0);
                    w.playSound(null, BlockPos.ofFloored(q), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.0f, 0.8f);
                    for (ServerPlayerEntity p : h.fighters(w)) if (p.getPos().distanceTo(q) < 2.9) p.damage(self.getDamageSources().mobAttack(self), 10f);
                }
            }
            return t < 27;
        });
    }

    /** Serpent: a bubble line marks the lane, then a cone of pressure blasts down it. */
    static void jet(Host h, ServerPlayerEntity target) {
        Vec3d c = h.centre();
        Vec3d dir = target.getPos().add(0, 1, 0).subtract(c).normalize();
        LivingEntity self = h.self();
        double reach = Math.max(28, h.reach() - 4);
        h.schedule((w, t) -> {
            if (t < 18 && t % 2 == 0)
                for (double s = 2; s < reach; s += 1.2) { Vec3d q = c.add(dir.multiply(s)); w.spawnParticles(ParticleTypes.BUBBLE, q.x, q.y, q.z, 2, 0.2, 0.2, 0.2, 0); }
            if (t == 18) {
                w.playSound(null, self.getBlockPos(), SoundEvents.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, SoundCategory.HOSTILE, 3.0f, 0.5f);
                for (double s = 2; s < reach; s += 0.8) { Vec3d q = c.add(dir.multiply(s)); w.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, q.x, q.y, q.z, 6, s * 0.08, s * 0.08, s * 0.08, 0.3); }
                for (ServerPlayerEntity p : h.fighters(w)) {
                    Vec3d d = p.getPos().add(0, 1, 0).subtract(c);
                    if (d.length() > reach || d.normalize().dotProduct(dir) < Math.cos(Math.toRadians(25))) continue;
                    p.damage(self.getDamageSources().mobAttack(self), 7f);
                    p.setVelocity(dir.x * 2.2, dir.y * 2.2 + 0.3, dir.z * 2.2);
                    p.velocityModified = true;
                }
            }
            return t < 19;
        });
    }

    /** Kraken: three barbed harpoons in a fan. */
    static void harpoons(Host h, ServerWorld sw, ServerPlayerEntity target) {
        Vec3d from = h.centre();
        Vec3d dir = target.getPos().add(0, target.getHeight() * 0.5, 0).subtract(from).normalize();
        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians((i - 1) * 11);
            Vec3d d = new Vec3d(dir.x * Math.cos(a) - dir.z * Math.sin(a), dir.y, dir.x * Math.sin(a) + dir.z * Math.cos(a));
            sw.spawnEntity(net.get900.pixelpirates.entity.custom.KrakenHarpoonEntity.spit(sw, h.self(), from.add(d.multiply(3.5)), d, 9f));
        }
        sw.playSound(null, h.self().getBlockPos(), SoundEvents.ITEM_CROSSBOW_SHOOT, SoundCategory.HOSTILE, 3.0f, 0.4f);
    }

    /** Revenant: four vertical blades of chain wheel out - get between them. Two waves. */
    static void quartering(Host h) {
        LivingEntity self = h.self();
        double rot0 = self.getRandom().nextDouble() * Math.PI, reach = h.reach();
        h.schedule((w, t) -> {
            int wave = t < 34 ? 0 : 1, tt = wave == 0 ? t : t - 34;
            double rot = rot0 + wave * Math.PI / 4;
            Vec3d c = h.centre();
            if (tt < 26 && tt % 3 == 0)
                for (int s = 0; s < 4; s++) {
                    double a = rot + s * Math.PI / 2;
                    for (double r = 3; r < reach; r += 1.5) for (int dy = -9; dy <= 9; dy += 6)
                        w.spawnParticles(new DustParticleEffect(WARN, tt > 16 ? 2.4f : 1.7f), c.x + Math.cos(a) * r, c.y + dy, c.z + Math.sin(a) * r, 1, 0.2, 0.4, 0.2, 0);
                }
            if (tt == 26) {
                w.playSound(null, self.getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.HOSTILE, 3.0f, 0.4f);
                for (int s = 0; s < 4; s++) {
                    double a = rot + s * Math.PI / 2;
                    for (double r = 3; r < reach; r += 1.0) w.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 3, 0.2, 4, 0.2, 0.01);
                }
                for (ServerPlayerEntity p : h.fighters(w)) {
                    double px = p.getX() - c.x, pz = p.getZ() - c.z;
                    if (Math.abs(p.getY() - c.y) > 12 || px * px + pz * pz > reach * reach) continue;
                    for (int s = 0; s < 4; s++) {
                        double a = rot + s * Math.PI / 2, ux = Math.cos(a), uz = Math.sin(a);
                        double along = px * ux + pz * uz, across = -px * uz + pz * ux;
                        if (along > 0.5 && Math.abs(across) < 1.8) {
                            p.damage(self.getDamageSources().mobAttack(self), 12f);
                            double side = across >= 0 ? 1 : -1;
                            p.setVelocity(-uz * side * 1.1, 0.4, ux * side * 1.1);
                            p.velocityModified = true;
                            break;
                        }
                    }
                }
            }
            return t < 61;
        });
    }

    /** Abyssal King: a ring of bubbles closes on each of you - be out of it when it implodes. */
    static void depths(Host h, List<ServerPlayerEntity> fs) {
        List<Vec3d> marks = new ArrayList<>();
        for (ServerPlayerEntity p : fs) marks.add(p.getPos().add(0, 1, 0));
        LivingEntity self = h.self();
        h.schedule((w, t) -> {
            double r = 5.5 - t * 0.1;
            for (Vec3d q : marks) {
                if (t < 40 && t % 2 == 0)
                    for (int i = 0; i < 16; i++) {
                        double a = i * Math.PI / 8;
                        w.spawnParticles(ParticleTypes.BUBBLE, q.x + Math.cos(a) * r, q.y, q.z + Math.sin(a) * r, 1, 0, 0.3, 0, 0);
                    }
                if (t == 40) {
                    w.spawnParticles(ParticleTypes.BUBBLE_POP, q.x, q.y, q.z, 80, 1, 1, 1, 0.3);
                    w.playSound(null, BlockPos.ofFloored(q), SoundEvents.ENTITY_GUARDIAN_ATTACK, SoundCategory.HOSTILE, 2.0f, 0.4f);
                    for (ServerPlayerEntity p : h.fighters(w))
                        if (p.getPos().add(0, 1, 0).distanceTo(q) < 2.4) {
                            p.damage(self.getDamageSources().mobAttack(self), 10f);
                            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1), self);
                        }
                }
            }
            return t < 41;
        });
    }

    /** Ghost Captain: a thin soul-line tracks you, locks, then the beam fires down it. */
    static void soulBeam(Host h, ServerPlayerEntity target) {
        Vec3d[] aim = {target.getPos().add(0, 1, 0)};
        LivingEntity self = h.self();
        double reach = Math.max(40, h.reach() + 8);
        h.schedule((w, t) -> {
            Vec3d c = h.centre();
            if (t < 20 && target.isAlive()) aim[0] = target.getPos().add(0, 1, 0);
            Vec3d dir = aim[0].subtract(c).normalize();
            if (t < 26 && t % 2 == 0)
                for (double s = 2; s < reach; s += 1.0) { Vec3d q = c.add(dir.multiply(s)); w.spawnParticles(new DustParticleEffect(SOUL, t >= 20 ? 1.6f : 0.8f), q.x, q.y, q.z, 1, 0, 0, 0, 0); }
            if (t == 26) {
                w.playSound(null, self.getBlockPos(), SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.HOSTILE, 3.0f, 0.6f);
                for (double s = 2; s < reach; s += 0.6) { Vec3d q = c.add(dir.multiply(s)); w.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, q.x, q.y, q.z, 2, 0.2, 0.2, 0.2, 0.02); }
                for (ServerPlayerEntity p : h.fighters(w)) {
                    Vec3d q = closest(c, c.add(dir.multiply(reach)), p.getPos().add(0, 0.9, 0));
                    if (q.distanceTo(p.getPos().add(0, 0.9, 0)) > 1.6) continue;
                    p.damage(self.getDamageSources().magic(), 10f);
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 80, 1), self);
                }
            }
            return t < 27;
        });
    }
}
