package net.get900.pixelpirates.entity.mob;

import net.get900.pixelpirates.item.ModItems;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Ability library for {@link ModMob}. An {@link Ability} bundles an effect with its animation,
 * cooldown, range window and windup (ticks from the animation trigger to the effect, so the hit
 * lands on the impact frame). Effects are server-side; all particles are sent from the server.
 */
public final class Abilities {
    private Abilities() {}

    /** What happens when an ability fires. {@code target} may be null for self abilities. */
    @FunctionalInterface
    public interface Effect {
        void fire(ModMob mob, LivingEntity target);
    }

    /**
     * @param anim      triggerable animation name in the mob's .animation.json ("attack", "special", "special2")
     * @param windup    ticks between trigger and effect
     * @param channel   total ticks the mob stands still performing it (>= windup)
     * @param selfCast  may fire without a target in range (heals, summons when hurt)
     */
    public record Ability(String name, String anim, int cooldown, double minRange, double maxRange,
                          int windup, int channel, boolean selfCast, Effect effect) {
        public static Ability of(String name, String anim, int cooldown, double min, double max, int windup, Effect effect) {
            return new Ability(name, anim, cooldown, min, max, windup, Math.max(windup + 6, 12), false, effect);
        }

        public Ability self() {
            return new Ability(name, anim, cooldown, minRange, maxRange, windup, channel, true, effect);
        }
    }

    /** Projectile look + payload. */
    public record Shot(Item look, float damage, StatusEffect effect, int effectTicks, int effectAmp,
                       int fireSeconds, ParticleEffect trail, float gravity) {
        public static Shot of(Item look, float damage, ParticleEffect trail) {
            return new Shot(look, damage, null, 0, 0, 0, trail, 0.01f);
        }

        public Shot withEffect(StatusEffect e, int ticks, int amp) { return new Shot(look, damage, e, ticks, amp, fireSeconds, trail, gravity); }

        public Shot withFire(int secs) { return new Shot(look, damage, effect, effectTicks, effectAmp, secs, trail, gravity); }

        public Shot withGravity(float g) { return new Shot(look, damage, effect, effectTicks, effectAmp, fireSeconds, trail, g); }
    }

    // ------------------------------------------------------------------ helpers
    static ServerWorld sw(ModMob mob) { return (ServerWorld) mob.getWorld(); }

    static List<LivingEntity> victims(ModMob mob, double radius) {
        return mob.getWorld().getEntitiesByClass(LivingEntity.class, mob.getBoundingBox().expand(radius, radius * 0.5, radius),
                e -> e != mob && e.isAlive() && !(e instanceof ModMob) && !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator()))
                        && e.squaredDistanceTo(mob) <= radius * radius);
    }

    static void ring(ServerWorld sw, Vec3d c, double r, ParticleEffect p, int n) {
        for (int i = 0; i < n; i++) {
            double a = 2 * Math.PI * i / n;
            sw.spawnParticles(p, c.x + Math.cos(a) * r, c.y + 0.15, c.z + Math.sin(a) * r, 1, 0, 0.02, 0, 0.01);
        }
    }

    // ------------------------------------------------------------------ effects
    /** Ground slam: damage falling off with distance, optional fire, launches victims. */
    public static Effect slam(double radius, float damage, int fireSecs, ParticleEffect ringParticle) {
        return (mob, t) -> {
            ServerWorld sw = sw(mob);
            Vec3d c = mob.getPos();
            sw.spawnParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.3, c.z, 2, 0.4, 0.1, 0.4, 0);
            for (int k = 1; k <= 3; k++) ring(sw, c, radius * k / 3.0, ringParticle, 8 * k);
            for (LivingEntity e : victims(mob, radius)) {
                double d = e.distanceTo(mob);
                if (e.damage(mob.getDamageSources().mobAttack(mob), (float) (damage * (1.0 - 0.5 * d / radius)))) {
                    if (fireSecs > 0) e.setOnFireFor(fireSecs);
                    Vec3d away = e.getPos().subtract(c).multiply(1, 0, 1);
                    away = away.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : away.normalize();
                    double kb = 1.0 - e.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
                    e.addVelocity(away.x * kb, 0.5 * kb, away.z * kb);
                    e.velocityModified = true;
                }
            }
        };
    }

    /** Fire {@code count} projectiles at the target in a spread fan. */
    public static Effect projectile(Shot shot, int count, float spreadDeg, float speed) {
        return (mob, t) -> {
            if (t == null) return;
            Vec3d from = mob.getPos().add(0, mob.getHeight() * 0.7, 0);
            Vec3d to = t.getPos().add(0, t.getHeight() * 0.5, 0);
            Vec3d dir = to.subtract(from);
            double yawBase = Math.atan2(dir.z, dir.x);
            double horiz = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
            for (int i = 0; i < count; i++) {
                double off = count == 1 ? 0 : Math.toRadians(spreadDeg) * (i / (double) (count - 1) - 0.5);
                double yaw = yawBase + off;
                MobProjectileEntity p = new MobProjectileEntity(mob.getWorld(), mob, shot);
                p.setPosition(from.x, from.y, from.z);
                p.setVelocity(Math.cos(yaw) * horiz, dir.y + horiz * 0.08 * shot.gravity() * 20, Math.sin(yaw) * horiz, speed, 2.0f);
                mob.getWorld().spawnEntity(p);
            }
        };
    }

    /** Drag nearby players toward the mob (siren song, whirlpools, reeling tentacles). */
    public static Effect pull(double radius, double strength) {
        return (mob, t) -> {
            for (LivingEntity e : victims(mob, radius)) {
                if (e instanceof PlayerEntity p && BossProgression.relicActive(p, ModItems.ROYAL_TIDE_SIGIL)) continue;
                Vec3d v = mob.getPos().subtract(e.getPos());
                if (v.lengthSquared() < 4) continue;
                e.addVelocity(v.normalize().multiply(strength).add(0, 0.1, 0));
                e.velocityModified = true;
            }
            ring(sw(mob), mob.getPos(), radius * 0.5, ParticleTypes.BUBBLE_POP, 20);
        };
    }

    /** Lunge at the target; the AbilityGoal applies contact damage during the channel. */
    public static Effect dash(double speed) {
        return (mob, t) -> {
            if (t == null) return;
            Vec3d dir = t.getPos().add(0, t.getHeight() * 0.3, 0).subtract(mob.getPos()).normalize();
            mob.setVelocity(dir.multiply(speed).add(0, mob.isOnGround() ? 0.25 : 0, 0));
            mob.velocityDirty = true;
            mob.dashing = 10;
            sw(mob).spawnParticles(ParticleTypes.CLOUD, mob.getX(), mob.getY() + 0.5, mob.getZ(), 8, 0.3, 0.2, 0.3, 0.05);
        };
    }

    /** Status effect on everything in range (roars, miasma, psychic pulses). */
    public static Effect aura(StatusEffect effect, int ticks, int amp, double radius, ParticleEffect p) {
        return (mob, t) -> {
            for (LivingEntity e : victims(mob, radius)) e.addStatusEffect(new StatusEffectInstance(effect, ticks, amp), mob);
            ring(sw(mob), mob.getPos().add(0, mob.getHeight() * 0.5, 0), radius, p, 32);
        };
    }

    /** Buff the caster (enrage, harden, haste). */
    public static Effect selfBuff(StatusEffect effect, int ticks, int amp, ParticleEffect p) {
        return (mob, t) -> {
            mob.addStatusEffect(new StatusEffectInstance(effect, ticks, amp));
            sw(mob).spawnParticles(p, mob.getX(), mob.getY() + mob.getHeight() * 0.5, mob.getZ(), 20, 0.5, 0.6, 0.5, 0.05);
        };
    }

    /** Summon minions by entity id ("pixelpirates:skeleton_pirate") around the caster. */
    public static Effect summon(String entityId, int count, double radius) {
        return (mob, t) -> {
            EntityType<?> type = Registries.ENTITY_TYPE.get(new Identifier(entityId));
            ServerWorld sw = sw(mob);
            for (int i = 0; i < count; i++) {
                Entity e = type.create(sw);
                if (e == null) continue;
                double a = mob.getRandom().nextDouble() * Math.PI * 2;
                e.refreshPositionAndAngles(mob.getX() + Math.cos(a) * radius, mob.getY(), mob.getZ() + Math.sin(a) * radius, mob.getYaw(), 0);
                if (e instanceof MobEntity m) {
                    m.initialize(sw, sw.getLocalDifficulty(mob.getBlockPos()), SpawnReason.MOB_SUMMONED, null, null);
                    if (t != null) m.setTarget(t);
                }
                sw.spawnEntity(e);
                sw.spawnParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
            }
        };
    }

    /** Teleport behind (or beside) the target; ambushers, ghosts, burrowers. */
    public static Effect blink(ParticleEffect p) {
        return (mob, t) -> {
            if (t == null) return;
            ServerWorld sw = sw(mob);
            sw.spawnParticles(p, mob.getX(), mob.getY() + 0.8, mob.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
            float yaw = t.getYaw() * MathHelper.RADIANS_PER_DEGREE;
            for (int i = 0; i < 6; i++) {
                double dist = 2.0 + i * 0.4;
                double x = t.getX() + MathHelper.sin(yaw) * dist, z = t.getZ() - MathHelper.cos(yaw) * dist;
                BlockPos bp = BlockPos.ofFloored(x, t.getY(), z);
                if (sw.isSpaceEmpty(mob, mob.getDimensions(mob.getPose()).getBoxAt(x, bp.getY(), z))) {
                    mob.requestTeleport(x, bp.getY(), z);
                    sw.spawnParticles(p, x, bp.getY() + 0.8, z, 20, 0.4, 0.6, 0.4, 0.05);
                    sw.playSound(null, x, bp.getY(), z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 0.7f, 0.6f);
                    return;
                }
            }
        };
    }

    public static Effect heal(float fraction) {
        return (mob, t) -> {
            mob.heal(mob.getMaxHealth() * fraction);
            sw(mob).spawnParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + mob.getHeight() * 0.6, mob.getZ(), 16, 0.6, 0.6, 0.6, 0);
        };
    }

    /** Seize the target: heavy slow + damage + reel in (tentacles, drowned hands, chains). */
    public static Effect grab(float damage, int holdTicks) {
        return (mob, t) -> {
            if (t == null) return;
            boolean cutFree = t instanceof PlayerEntity p && BossProgression.relicActive(p, ModItems.BLOODFIN_RAZOR_TOOTH);
            if (t.damage(mob.getDamageSources().mobAttack(mob), damage) && !cutFree) {
                t.addStatusEffect(new StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.SLOWNESS, holdTicks, 4), mob);
                Vec3d v = mob.getPos().subtract(t.getPos());
                if (v.lengthSquared() > 2.25) { t.addVelocity(v.normalize().multiply(0.6)); t.velocityModified = true; }
            }
            sw(mob).spawnParticles(ParticleTypes.SQUID_INK, t.getX(), t.getY() + 0.5, t.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
        };
    }

    /** Lingering cloud (ink, spores, brimstone) around the target. */
    public static Effect cloud(StatusEffect effect, int ticks, float radius, ParticleEffect p) {
        return (mob, t) -> {
            Vec3d at = t != null ? t.getPos() : mob.getPos();
            AreaEffectCloudEntity cloud = new AreaEffectCloudEntity(mob.getWorld(), at.x, at.y, at.z);
            cloud.setOwner(mob);
            cloud.setRadius(radius);
            cloud.setDuration(100);
            cloud.setRadiusGrowth(-radius / 100f);
            cloud.setParticleType(p);
            cloud.addEffect(new StatusEffectInstance(effect, ticks, 0));
            mob.getWorld().spawnEntity(cloud);
        };
    }

    /** Instant beam to the target: damage + a particle line (psychic rays, eye beams). */
    public static Effect beam(float damage, StatusEffect effect, int ticks, ParticleEffect p) {
        return (mob, t) -> {
            if (t == null || !mob.canSee(t)) return;
            ServerWorld sw = sw(mob);
            Vec3d a = mob.getEyePos(), b = t.getPos().add(0, t.getHeight() * 0.5, 0);
            int n = (int) (a.distanceTo(b) * 3);
            for (int i = 0; i <= n; i++) {
                Vec3d q = a.lerp(b, i / (double) n);
                sw.spawnParticles(p, q.x, q.y, q.z, 1, 0, 0, 0, 0);
            }
            if (t.damage(mob.getDamageSources().indirectMagic(mob, mob), damage) && effect != null) {
                t.addStatusEffect(new StatusEffectInstance(effect, ticks, 0), mob);
            }
        };
    }

    /** Call down lightning on the target (cosmetic bolt + real damage/fire). */
    public static Effect lightning() { return lightning(6f); }

    public static Effect lightning(float damage) {
        return (mob, t) -> {
            if (t == null) return;
            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(mob.getWorld());
            if (bolt == null) return;
            bolt.refreshPositionAfterTeleport(t.getX(), t.getY(), t.getZ());
            bolt.setCosmetic(true);
            mob.getWorld().spawnEntity(bolt);
            t.damage(mob.getDamageSources().lightningBolt(), damage);
        };
    }

    /** Several effects in one ability (e.g. roar = aura + pull). */
    public static Effect both(Effect a, Effect b) {
        return (mob, t) -> { a.fire(mob, t); b.fire(mob, t); };
    }
}
