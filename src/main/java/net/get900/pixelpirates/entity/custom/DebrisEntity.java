package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.entity.ModEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * WRECK RAIN (Leviathan phase 3): wreckage of the ports it destroyed, flung in a high arc. Drawn as a big tumbling block
 * (DebrisRenderer); where it lands it bursts - damage and knockback round the impact - and is gone (it never places blocks).
 */
public class DebrisEntity extends Entity {
    private static final TrackedData<Integer> BLOCK = DataTracker.registerData(DebrisEntity.class, TrackedDataHandlerRegistry.INTEGER);
    @Nullable private Entity owner;
    private float damage = 10f;
    private double targetY = Double.NEGATIVE_INFINITY;              // it bursts on reaching this height (it may fall through water)

    public DebrisEntity(EntityType<? extends DebrisEntity> type, World world) {
        super(type, world);
    }

    public static DebrisEntity fling(World world, @Nullable Entity owner, Vec3d from, Vec3d to, BlockState look, float damage) {
        DebrisEntity d = new DebrisEntity(ModEntities.DEBRIS, world);
        d.setPosition(from);
        d.owner = owner;
        d.damage = damage;
        d.targetY = to.y;
        d.dataTracker.set(BLOCK, Block.getRawIdFromState(look));
        // a lob: 40-tick flight under 0.06 gravity
        int t = 40;
        Vec3d dv = to.subtract(from);
        d.setVelocity(dv.x / t, dv.y / t + 0.03 * t, dv.z / t);
        return d;
    }

    public BlockState look() { return Block.getStateFromRawId(this.dataTracker.get(BLOCK)); }

    @Override
    protected void initDataTracker() { this.dataTracker.startTracking(BLOCK, Block.getRawIdFromState(Blocks.SPRUCE_PLANKS.getDefaultState())); }

    @Override
    public void tick() {
        super.tick();
        setVelocity(getVelocity().add(0, -0.06, 0));
        try { move(MovementType.SELF, getVelocity()); } catch (NullPointerException ignored) { }
        if (getWorld() instanceof ServerWorld sw && (isOnGround() || horizontalCollision || getVelocity().y < 0 && getY() <= targetY + 0.5 || age > 200)) land(sw);
    }

    private void land(ServerWorld sw) {
        sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, look()), getX(), getY() + 0.5, getZ(), 60, 1.2, 0.6, 1.2, 0.2);
        sw.spawnParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.5, getZ(), 2, 0.6, 0.3, 0.6, 0);
        sw.spawnParticles(ParticleTypes.SPLASH, getX(), getY() + 1, getZ(), 40, 1.5, 0.5, 1.5, 0.3);
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.0f, 0.8f);
        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(3.5), e -> e.isAlive() && e != owner
                && !(e instanceof net.get900.pixelpirates.entity.mob.ModMob))) {
            e.damage(getDamageSources().fallingBlock(this), damage);
            Vec3d away = e.getPos().subtract(getPos()).normalize();
            e.addVelocity(away.x * 0.8, 0.4, away.z * 0.8);
            e.velocityModified = true;
        }
        discard();
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) { }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) { }

    @Override
    public boolean shouldSave() { return false; }
}
