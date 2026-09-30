package net.get900.pixelpirates.entity.mob;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

/**
 * A node of the Abyssal Heart's conduction system, one per chamber (phase 2 - INTO THE HEART). It only yields ON THE
 * BEAT (AbyssalHeartEntity#inBeatWindow): between beats it clenches and shrugs every blow off. All four burst -> the
 * heart ruptures. Spawned by the heart, rooted, never attacks.
 */
public class HeartNodeEntity extends ModMob {
    private UUID heart;
    private int hintCooldown;

    public HeartNodeEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setPersistent();
    }

    public void setHeart(UUID h) { heart = h; }

    AbyssalHeartEntity heart() {
        return heart != null && this.getWorld() instanceof ServerWorld sw && sw.getEntity(heart) instanceof AbyssalHeartEntity h ? h : null;
    }

    @Override
    protected List<String> extraAnims() { return List.of("pulse", "clench"); }

    @Override
    protected List<Abilities.Ability> activeAbilities() { return List.of(); }

    @Override
    protected boolean scriptedMotion() { return true; }

    @Override
    protected void mobTick() {
        super.mobTick();
        this.setVelocity(Vec3d.ZERO);
        if (hintCooldown > 0) hintCooldown--;
        AbyssalHeartEntity h = heart();
        if (h != null && h.inBeatWindow() && this.age % 2 == 0 && this.getWorld() instanceof ServerWorld sw)
            sw.spawnParticles(new DustParticleEffect(AbyssalHeartEntity.GREEN, 1.3f), getX(), getY() + 1.2, getZ(), 4, 0.6, 0.6, 0.6, 0);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.damage(source, amount);
        AbyssalHeartEntity h = heart();
        if (h != null && !h.inBeatWindow()) {
            triggerAnim(ACTION, "clench");
            if (source.getAttacker() instanceof ServerPlayerEntity p && hintCooldown == 0) {
                hintCooldown = 30;
                p.sendMessage(Text.literal("The node clenches shut - strike it ON THE BEAT, when it glows green!").formatted(Formatting.GRAY), true);
                p.playSound(SoundEvents.ENTITY_SLIME_SQUISH_SMALL, SoundCategory.HOSTILE, 1.0f, 0.5f);
            }
            return false;
        }
        return super.damage(source, amount);
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (this.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(new DustParticleEffect(AbyssalHeartEntity.BLOOD, 3f), getX(), getY() + 1, getZ(), 120, 1.2, 1.2, 1.2, 0);
            sw.spawnParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1, getZ(), 2, 0.5, 0.5, 0.5, 0);
            sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_SLIME_DEATH, SoundCategory.HOSTILE, 3.0f, 0.4f);
            AbyssalHeartEntity h = heart();
            if (h != null) h.onNodeDestroyed(this);
        }
    }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean canImmediatelyDespawn(double d) { return false; }

    @Override
    protected SoundEvent sound(String kind) {
        return switch (kind) {
            case "hurt" -> SoundEvents.ENTITY_SLIME_HURT;
            case "death" -> SoundEvents.ENTITY_SLIME_DEATH;
            case "ambient" -> SoundEvents.ENTITY_WARDEN_HEARTBEAT;
            default -> null;
        };
    }

    @Override
    public float getSoundPitch() { return 0.6f + this.random.nextFloat() * 0.1f; }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (heart != null) nbt.putUuid("Heart", heart);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.containsUuid("Heart")) heart = nbt.getUuid("Heart");
    }
}
