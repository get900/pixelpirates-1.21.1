package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.entity.mob.ModMob;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Thrown dynamite.
 *
 * PLAYER dynamite explodes on impact like TNT (breaks terrain; follows the TNT gamerule, not
 * mobGriefing) and is the ONLY thing that clears {@link ModBlocks#BLAST_RUBBLE} - rubble walls gate
 * structure vaults, so dynamite is an early-game key item.
 *
 * BOSS dynamite ({@link #lobbed}) lands, sits hissing on a visible fuse, then blows up without
 * touching terrain - a telegraphed area to run out of.
 */
public class DynamiteEntity extends ThrownItemEntity {
    private static final TrackedData<Boolean> LANDED = DataTracker.registerData(DynamiteEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    /** Radius (blocks) in which blast rubble is cleared. */
    public static final double RUBBLE_RADIUS = 3.5;

    private boolean noGrief;
    private int fuse = -1;            // -1 = explode on impact
    private float blastDamage = 8f;

    public DynamiteEntity(EntityType<? extends ThrownItemEntity> type, World world) {
        super(type, world);
    }

    public DynamiteEntity(EntityType<? extends ThrownItemEntity> type, World world, LivingEntity owner) {
        super(type, owner, world);
    }

    /** Terrain-safe fused stick for mob abilities. */
    public static DynamiteEntity lobbed(World world, LivingEntity owner, int fuseTicks, float damage) {
        DynamiteEntity d = new DynamiteEntity(net.get900.pixelpirates.entity.ModEntities.DYNAMITE, world, owner);
        d.noGrief = true;
        d.fuse = fuseTicks;
        d.blastDamage = damage;
        return d;
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(LANDED, false);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.DYNAMITE;
    }

    @Override
    public void tick() {
        if (this.dataTracker.get(LANDED)) {
            // sitting on the ground: hiss, spark, count down - no physics
            this.baseTick();
            if (this.getWorld().isClient) {
                this.getWorld().addParticle(ParticleTypes.FLAME, getX(), getY() + 0.35, getZ(), 0, 0.03, 0);
                this.getWorld().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.45, getZ(), 0, 0.04, 0);
                return;
            }
            if (--fuse <= 0) {
                explode();
                this.discard();
            }
            return;
        }
        super.tick();
        if (this.getWorld().isClient && this.age % 2 == 0)
            this.getWorld().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.1, getZ(), 0, 0.02, 0);
        if (!this.getWorld().isClient && this.age > 200) this.discard();
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        if (this.getWorld().isClient) return;
        if (fuse > 0) {
            // land and burn down the fuse where it fell
            this.setVelocity(Vec3d.ZERO);
            this.setPosition(hitResult.getPos().x, hitResult.getPos().y + 0.05, hitResult.getPos().z);
            this.dataTracker.set(LANDED, true);
            this.playSound(SoundEvents.ENTITY_TNT_PRIMED, 1.0f, 1.3f);
            return;
        }
        explode();
        this.discard();
    }

    private void explode() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        clearRubble(sw, this.getBlockPos());
        if (noGrief) {
            sw.spawnParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 3, 0.5, 0.3, 0.5, 0);
            sw.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.4, getZ(), 12, 0.7, 0.4, 0.7, 0.04);
            sw.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.6f, 1.1f);
            double r = 3.5;
            for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, this.getBoundingBox().expand(r),
                    e -> e.isAlive() && !(e instanceof ModMob) && !(e instanceof PirateCrewEntity))) {
                double d = e.getPos().distanceTo(this.getPos());
                if (d > r) continue;
                if (e.damage(this.getDamageSources().explosion(this, this.getOwner()), (float) (blastDamage * (1 - 0.6 * d / r)))) {
                    Vec3d away = e.getPos().subtract(this.getPos()).multiply(1, 0, 1);
                    away = away.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : away.normalize();
                    double kb = 1.0 - e.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
                    e.addVelocity(away.x * 0.6 * kb, 0.4 * kb, away.z * 0.6 * kb);
                    e.velocityModified = true;
                }
            }
            return;
        }
        sw.createExplosion(this, this.getX(), this.getY(), this.getZ(), 2.0f, World.ExplosionSourceType.TNT);
    }

    /** Break every blast-rubble block near the blast (they shrug off creepers, TNT and pickaxes). */
    public static void clearRubble(ServerWorld sw, BlockPos c) {
        int r = (int) Math.ceil(RUBBLE_RADIUS);
        boolean any = false;
        for (BlockPos p : BlockPos.iterate(c.add(-r, -r, -r), c.add(r, r, r))) {
            if (p.getSquaredDistance(c) > RUBBLE_RADIUS * RUBBLE_RADIUS) continue;
            if (sw.getBlockState(p).isOf(ModBlocks.BLAST_RUBBLE)) {
                sw.breakBlock(p, false);
                sw.spawnParticles(ParticleTypes.CLOUD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.02);
                any = true;
            }
        }
        if (any) sw.playSound(null, c, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 2.0f, 0.6f);
    }
}
