package net.get900.pixelpirates.entity.mob;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * PHANTASM - a drowned pirate that is not really there (the Abyssal Heart's TRIPLE BEAT). Only the player it haunts can
 * see it (everyone else: invisible, no shadow, and GlowingMobRenderer skips it); it swims at them and swings, but its
 * blows are only a sound in their ears. Any blow - or 10 s - and it is gone.
 */
public class HeartPhantasmEntity extends ModMob {
    private static final TrackedData<Optional<UUID>> VICTIM = DataTracker.registerData(HeartPhantasmEntity.class, TrackedDataHandlerRegistry.OPTIONAL_UUID);
    private int life = 200;

    public HeartPhantasmEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setInvisible(true);
        this.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.WATER, 0.0f);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(VICTIM, Optional.empty());
    }

    public void haunt(PlayerEntity p) { this.dataTracker.set(VICTIM, Optional.of(p.getUuid())); }

    public Optional<UUID> victim() { return this.dataTracker.get(VICTIM); }

    /** Only its victim can see it. */
    @Override
    public boolean isInvisibleTo(PlayerEntity player) {
        return victim().map(u -> !u.equals(player.getUuid())).orElse(true);
    }

    @Override
    protected boolean mayTarget(LivingEntity player) { return victim().map(u -> u.equals(player.getUuid())).orElse(false); }

    @Override
    protected List<Abilities.Ability> activeAbilities() { return List.of(); }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        PlayerEntity v = victim().map(sw::getPlayerByUuid).orElse(null);
        if (v == null || !v.isAlive() || v.squaredDistanceTo(this) > 40 * 40 || --life <= 0) { vanish(sw); return; }
        this.setTarget(v);
    }

    /** Its "blow": a swing and a sound only the victim hears. */
    @Override
    public boolean tryAttack(Entity target) {
        triggerAnim(ACTION, "attack");
        if (target instanceof ServerPlayerEntity p) {
            p.playSound(SoundEvents.ENTITY_PLAYER_HURT, SoundCategory.HOSTILE, 1.0f, 0.8f);
            p.playSound(SoundEvents.ENTITY_DROWNED_AMBIENT_WATER, SoundCategory.HOSTILE, 1.0f, 0.6f);
        }
        return false;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (this.getWorld() instanceof ServerWorld sw && source.getAttacker() != null) vanish(sw);
        return false;
    }

    private void vanish(ServerWorld sw) {
        sw.spawnParticles(ParticleTypes.SOUL, getX(), getY() + 1, getZ(), 12, 0.3, 0.6, 0.3, 0.02);
        victim().map(sw::getPlayerByUuid).ifPresent(p -> {
            if (p instanceof ServerPlayerEntity sp) sp.playSound(SoundEvents.ENTITY_VEX_DEATH, SoundCategory.HOSTILE, 0.8f, 0.5f);
        });
        this.discard();
    }

    @Override
    public boolean isPushable() { return false; }

    @Override
    protected void pushAway(Entity entity) { }

    @Override
    protected SoundEvent sound(String kind) { return null; }

    @Override
    public boolean canImmediatelyDespawn(double d) { return true; }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        victim().ifPresent(u -> nbt.putUuid("Victim", u));
        nbt.putInt("Life", life);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("Victim")) this.dataTracker.set(VICTIM, Optional.of(nbt.getUuid("Victim")));
        life = nbt.getInt("Life");
        this.setInvisible(true);
    }
}
