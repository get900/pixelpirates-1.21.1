package net.get900.pixelpirates.world;

import net.get900.pixelpirates.entity.mob.BossProgression;
import net.get900.pixelpirates.entity.mob.ModBoss;
import net.get900.pixelpirates.entity.mob.ModMob;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.util.math.Box;

import java.util.Comparator;

/**
 * MOB DAMAGE PASS (Materials & Gear Ladder 7.1, 2026-10-01). The armor ladder came down 5-8 points per phase, so the
 * damage our mobs and bosses deal to players comes down with it: a player in that phase's crafted set takes about what
 * they used to. Applied once, after armor (BossArmorDamageMixin), so it covers melee, abilities, projectiles and the
 * bosses' scripted hits alike. Vanilla mobs are untouched.
 */
public final class MobDamageScale {
    private MobDamageScale() {}

    /** By roster phase (index 1-5). */
    private static final float[] PHASE = {1f, 0.75f, 0.68f, 0.70f, 0.72f, 0.65f};
    /** By chain boss index 0-9 (Rackham .. Leviathan). */
    private static final float[] BOSS = {0.75f, 0.68f, 0.70f, 0.72f, 0.68f, 0.68f, 0.68f, 0.68f, 0.68f, 0.68f};
    private static final float OTHER = 0.75f;          // our older hand-written mobs (sharks, crabs, sirens, brutes...)

    public static float scale(PlayerEntity victim, DamageSource src, float amount) {
        if (amount <= 0 || src.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return amount;
        Entity e = src.getAttacker() != null ? src.getAttacker() : src.getSource();
        if (e instanceof ProjectileEntity p && p.getOwner() != null) e = p.getOwner();
        if (e == null) {                                   // owner-less magic / blasts / burning: only near a boss
            ModBoss b = nearestBoss(victim, 64);
            return b == null ? amount : amount * bossFactor(b);
        }
        if (e == victim || e instanceof PlayerEntity) return amount;
        if (e instanceof ModBoss b) return amount * (b.isDefiantAgainst(victim) ? ModBoss.DEFIANT_DEALT : bossFactor(b));
        if (e instanceof ModMob m) {
            ModBoss b = nearestBoss(m, 48);                // arms, limbs, summons fight at their boss's level
            if (b != null && b.isDefiantAgainst(victim)) return amount * ModBoss.DEFIANT_DEALT;
            return amount * (b != null ? bossFactor(b) : PHASE[Math.max(1, Math.min(5, m.spec().phase))]);
        }
        if (e.getClass().getName().startsWith("net.get900.pixelpirates")) {
            ModBoss b = nearestBoss(e, 48);
            return amount * (b != null ? bossFactor(b) : OTHER);
        }
        return amount;
    }

    private static float bossFactor(ModBoss b) {
        int i = BossProgression.indexOf(b.spec().id);
        return i >= 0 && i < BOSS.length ? BOSS[i] : PHASE[Math.max(1, Math.min(5, b.spec().phase))];
    }

    private static ModBoss nearestBoss(Entity around, double r) {
        return around.getWorld().getEntitiesByClass(ModBoss.class, new Box(around.getBlockPos()).expand(r), Entity::isAlive)
                .stream().min(Comparator.comparingDouble(b -> b.squaredDistanceTo(around))).orElse(null);
    }
}
