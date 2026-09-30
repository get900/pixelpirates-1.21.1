package net.get900.pixelpirates.entity.mob;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.nbt.NbtCompound;
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
import software.bernie.geckolib.core.animation.RawAnimation;

import java.util.List;

/**
 * THE EYE OF THALASSAR - the Tide Father's eye, sealed in a socket of flesh behind the northern ribs of the Titan's
 * Chest (Abyssal Heart, boss 9/10). It cannot be hurt and never attacks, but it is always REACTING: it sleeps, stirs when
 * the fight begins, flinches at every bolt and every heavy blow on its heart, glares when the heart stops, and in the
 * FLATLINE it opens wide and shocks its heart back to life (AbyssalHeartEntity#shock - the eye just charges). When the
 * heart dies it screams and goes dark for good. The iris follows YOU (each client tracks its own player -
 * GlowingMobRenderer.rivalEye).
 */
public class RivalEyeEntity extends ModMob {
    public static final int SLEEP = 0, STIR = 1, OPEN = 2, DEAD = 3;
    private static final TrackedData<Integer> STATE = DataTracker.registerData(RivalEyeEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final RawAnimation[] LOOPS = {RawAnimation.begin().thenLoop("sleep"), RawAnimation.begin().thenLoop("stir"),
            RawAnimation.begin().thenLoop("watch"), RawAnimation.begin().thenLoop("dead")};
    private int blinkIn = 100, screamTicks, hintCooldown;

    public RivalEyeEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.setPersistent();
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(STATE, SLEEP);
    }

    public int state() { return this.dataTracker.get(STATE); }

    public String stateName() { return switch (state()) { case STIR -> "STIRRING"; case OPEN -> "OPEN"; case DEAD -> "DEAD"; default -> "ASLEEP"; }; }

    private void setState(int s) { this.dataTracker.set(STATE, s); }

    @Override
    public String skinVariant() { return state() == DEAD ? "dead" : null; }

    @Override
    protected RawAnimation movementOverride() { return LOOPS[Math.max(0, Math.min(3, state()))]; }

    @Override
    protected List<String> extraAnims() { return List.of("open", "blink", "flinch", "glare", "charge", "scream"); }

    // ------------------------------------------------------------------ reactions (called by the Heart)
    public void stir() {
        if (state() != SLEEP) return;
        setState(STIR);
        sound(SoundEvents.ENTITY_WARDEN_LISTENING, 0.5f);
    }

    public void open() {
        if (state() == DEAD) return;
        setState(OPEN);
        triggerAnim(ACTION, "open");
        sound(SoundEvents.ENTITY_WARDEN_EMERGE, 0.5f);
        if (this.getWorld() instanceof ServerWorld sw)
            sw.spawnParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + getHeight() * 0.5, getZ() + 3, 40, 3, 3, 1, 0.05);
    }

    public void flinch() {
        if (state() == SLEEP || state() == DEAD) return;
        triggerAnim(ACTION, "flinch");
        sound(SoundEvents.ENTITY_WARDEN_HURT, 0.6f);
    }

    public void glare() {
        if (state() == SLEEP || state() == DEAD) return;
        triggerAnim(ACTION, "glare");
        sound(SoundEvents.ENTITY_WARDEN_ANGRY, 0.5f);
    }

    /** Just before each FLATLINE shock: the veins round it light up. */
    public void charge() {
        if (state() != OPEN) return;
        triggerAnim(ACTION, "charge");
        sound(SoundEvents.BLOCK_BEACON_POWER_SELECT, 1.4f);
    }

    public void scream() {
        if (state() == DEAD) return;
        if (state() == SLEEP) setState(OPEN);
        triggerAnim(ACTION, "scream");
        screamTicks = 70;
        sound(SoundEvents.ENTITY_WARDEN_ROAR, 0.4f);
        sound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 0.5f);
    }

    private void sound(SoundEvent s, float pitch) {
        if (this.getWorld() instanceof ServerWorld sw) sw.playSound(null, getBlockPos(), s, SoundCategory.HOSTILE, 5.0f, pitch);
    }

    // ------------------------------------------------------------------ ticking: it never moves, it only watches
    @Override
    protected boolean scriptedMotion() { return true; }

    @Override
    protected List<Abilities.Ability> activeAbilities() { return List.of(); }

    @Override
    protected void mobTick() {
        super.mobTick();
        this.setVelocity(Vec3d.ZERO);
        this.setYaw(this.bodyYaw);
        this.setHeadYaw(this.bodyYaw);
        if (hintCooldown > 0) hintCooldown--;
        if (screamTicks > 0) {
            if (screamTicks % 4 == 0 && this.getWorld() instanceof ServerWorld sw)
                sw.spawnParticles(ParticleTypes.SONIC_BOOM, getX() + (random.nextDouble() - 0.5) * 6, getY() + 2 + random.nextDouble() * 8, getZ() + 4, 1, 0, 0, 0, 0);
            if (--screamTicks == 0) { setState(DEAD); sound(SoundEvents.BLOCK_BEACON_DEACTIVATE, 0.3f); }
            return;
        }
        if (state() == OPEN && --blinkIn <= 0) {
            blinkIn = 80 + this.random.nextInt(120);
            triggerAnim(ACTION, "blink");
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.damage(source, amount);
        if (source.getAttacker() instanceof ServerPlayerEntity p && hintCooldown == 0) {
            hintCooldown = 60;
            flinch();
            p.sendMessage(Text.literal(state() == DEAD ? "The eye is dark and cold."
                    : "Your blow sinks into the socket. The Eye of Thalassar cannot be hurt - it is only watching.").formatted(Formatting.GRAY), true);
        }
        return false;
    }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean isPushedByFluids() { return false; }

    @Override
    public boolean canImmediatelyDespawn(double d) { return false; }

    @Override
    protected SoundEvent sound(String kind) { return null; }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("EyeState", state());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        setState(nbt.getInt("EyeState"));
    }
}
