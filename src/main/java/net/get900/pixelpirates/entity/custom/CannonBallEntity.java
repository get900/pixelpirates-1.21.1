package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.entity.ModEntities;
import net.get900.pixelpirates.util.ModTags;
import net.get900.pixelpirates.world.ShipHealthState;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;

public class CannonBallEntity extends ThrownItemEntity {

    private static final float  DAMAGE          = 15.0f;
    private static final float  EXPLOSION_POWER = 1.5f;
    private static final double GRAVITY         = 0.04;
    private static final int    HULL_DAMAGE     = 75;
    private static final int    MAX_LIFETIME    = 100; // 5 seconds at 20 tps

    /**
     * Fort / boss shots (FortCannonBlockEntity, Captain Rackham's broadside): no block damage, no
     * ship-hull damage, and a gentler custom blast tuned for early-game players instead of the
     * ship-to-ship explosion. Short-lived, so not saved.
     */
    private boolean fortShot;
    /** CHAIN SHOT (homestead #25): two balls on a chain - little hull damage, but it fouls the rigging of the ship it
     *  hits (the ship is held as if anchored for 5 s). No terrain damage. Short-lived, not saved. */
    private boolean chainShot;

    public void setChainShot() { chainShot = true; }
    /** The spawn island's fortress guns: the blast only hurts players (never the town's villagers, traders or pets). */
    private boolean playersOnly;
    public void setPlayersOnly() { playersOnly = true; }
    private float fortDamage = 6f;
    private int maxLifetime = MAX_LIFETIME;

    public CannonBallEntity(EntityType<? extends CannonBallEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    public CannonBallEntity(World world, LivingEntity owner, double x, double y, double z) {
        super(ModEntities.CANNON_BALL, world);
        this.setOwner(owner);
        this.setPosition(x, y, z);
        this.setNoGravity(true);
    }

    // Used by AiShipController — no owning entity
    public CannonBallEntity(World world, double x, double y, double z) {
        super(ModEntities.CANNON_BALL, world);
        this.setPosition(x, y, z);
        this.setNoGravity(true);
    }

    /** A terrain-safe shot from a fort cannon or a boss ability. */
    public static CannonBallEntity fort(World world, @Nullable Entity owner, double x, double y, double z, float damage) {
        CannonBallEntity b = new CannonBallEntity(world, x, y, z);
        if (owner != null) b.setOwner(owner);
        b.fortShot = true;
        b.fortDamage = damage;
        b.maxLifetime = 200;
        return b;
    }

    @Override
    protected Item getDefaultItem() {
        return Items.COAL;
    }

    @Override
    public void tick() {
        if (this.age > maxLifetime) {
            this.discard();
            return;
        }
        this.setVelocity(this.getVelocity().add(0, -GRAVITY, 0));
        try {
            super.tick();
        } catch (ArrayIndexOutOfBoundsException e) {
            // VS2 entity-section race condition (physics thread vs server thread on Long2ObjectOpenHashMap).
            // Silently discard rather than crash the server.
            this.discard();
        }
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        if (hitResult.getType() == HitResult.Type.ENTITY) {
            onEntityHit((EntityHitResult) hitResult);
        } else {
            onBlockHit((BlockHitResult) hitResult);
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        Entity target = hit.getEntity();
        Entity owner  = this.getOwner();
        if (target.equals(owner)) return;
        if (fortShot && (target instanceof net.get900.pixelpirates.entity.mob.ModMob || target instanceof PirateCrewEntity
                || target instanceof CannonBallEntity)) return;          // the fort's own garrison
        if (playersOnly && !(target instanceof net.minecraft.entity.player.PlayerEntity)) return;
        if (!this.getWorld().isClient && fortShot) {
            fortBlast();
            return;
        }
        if (!this.getWorld().isClient) {
            target.damage(this.getDamageSources().thrown(this, owner), DAMAGE);
            explodeAndDiscard(null);
        }
    }

    @Override
    protected void onBlockHit(BlockHitResult hit) {
        if (this.getWorld().isClient) return;
        if (fortShot) { fortBlast(); return; }
        BlockState hitState = this.getWorld().getBlockState(hit.getBlockPos());
        if (hitState.isIn(ModTags.Blocks.CANNON_IMMUNE)) {
            // Immune block — vanish silently, no explosion
            this.discard();
            return;
        }
        explodeAndDiscard(hit.getBlockPos());
    }

    /** Cosmetic explosion + a falloff blast that only hurts players and non-garrison mobs. */
    private void fortBlast() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        double x = this.getX(), y = this.getY(), z = this.getZ();
        sw.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION, x, y + 0.3, z, 3, 0.6, 0.3, 0.6, 0);
        sw.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE, x, y + 0.5, z, 14, 0.8, 0.5, 0.8, 0.04);
        sw.spawnParticles(net.minecraft.particle.ParticleTypes.LAVA, x, y + 0.3, z, 6, 0.5, 0.2, 0.5, 0);
        sw.playSound(null, x, y, z, net.minecraft.sound.SoundEvents.ENTITY_GENERIC_EXPLODE, net.minecraft.sound.SoundCategory.HOSTILE,
                2.0f, 0.9f + this.random.nextFloat() * 0.2f);
        double r = 3.0;
        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, this.getBoundingBox().expand(r),
                e -> e.isAlive() && !(e instanceof net.get900.pixelpirates.entity.mob.ModMob) && !(e instanceof PirateCrewEntity)
                        && (!playersOnly || e instanceof net.minecraft.entity.player.PlayerEntity))) {
            double d = e.getPos().add(0, e.getHeight() * 0.5, 0).distanceTo(this.getPos());
            if (d > r) continue;
            float dmg = (float) (fortDamage * (1.0 - 0.6 * d / r));
            if (e.damage(this.getDamageSources().explosion(this, this.getOwner()), dmg)) {
                net.minecraft.util.math.Vec3d away = e.getPos().subtract(this.getPos()).multiply(1, 0, 1);
                away = away.lengthSquared() < 1e-4 ? new net.minecraft.util.math.Vec3d(0, 0, 1) : away.normalize();
                double kb = 1.0 - e.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
                e.addVelocity(away.x * 0.7 * kb, 0.45 * kb, away.z * 0.7 * kb);
                e.velocityModified = true;
            }
        }
        this.discard();
    }

    private void explodeAndDiscard(@Nullable BlockPos hitPos) {
        World world = this.getWorld();
        if (chainShot) {
            if (!world.isClient) net.get900.pixelpirates.homestead.ship.Shot.chainImpact((ServerWorld) world, this, hitPos, HULL_DAMAGE);
            this.discard();
            return;
        }
        double x = this.getX(), y = this.getY(), z = this.getZ();

        // NONE = keep sound/particles/entity knockback, but no vanilla block destruction.
        // We handle block destruction ourselves so we can respect the cannon_immune tag.
        world.createExplosion(this, x, y, z, EXPLOSION_POWER, false, World.ExplosionSourceType.NONE);

        if (!world.isClient) {
            ServerWorld sw = (ServerWorld) world;
            destroyBlocksInRadius(sw, x, y, z);

            if (hitPos != null) {
                Ship ship = ValkyrienSkies.getShipManagingBlock(sw, hitPos.getX(), hitPos.getY(), hitPos.getZ());
                if (ship != null) {
                    ShipHealthState.get(sw).damage(sw, ship.getId(), hitPos,
                            (int) Math.round(HULL_DAMAGE * net.get900.pixelpirates.world.SkillEffects.hullMult(net.get900.pixelpirates.world.SkillEffects.playerOf(getOwner()))));
                }
            }
        }

        this.discard();
    }

    private static void destroyBlocksInRadius(ServerWorld world, double cx, double cy, double cz) {
        int radius = (int) Math.ceil(EXPLOSION_POWER * 2.0);
        BlockPos center = BlockPos.ofFloored(cx, cy, cz);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
                    BlockPos pos = center.add(dx, dy, dz);
                    // Skip ship blocks — they are handled exclusively by ShipHealthState.damage().
                    // Breaking them here too causes double-removal and prevents repair tracking.
                    if (ValkyrienSkies.getShipManagingBlock(world, pos.getX(), pos.getY(), pos.getZ()) != null) continue;
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir() || state.isIn(ModTags.Blocks.CANNON_IMMUNE)) continue;
                    world.breakBlock(pos, true);
                }
            }
        }
    }
}
