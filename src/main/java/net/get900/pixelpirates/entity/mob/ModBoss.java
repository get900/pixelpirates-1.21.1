package net.get900.pixelpirates.entity.mob;

import net.get900.pixelpirates.world.PlayerProgressionManager;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Boss version of {@link ModMob}: boss bar, a home arena it leashes back to (regenerating only after
 * a full minute without taking damage), an enraged second phase at 50% health (roar, speed, extra abilities from
 * {@code MobSpec.phase2}), never despawns. Bosses form one chain ({@link BossProgression}): a boss
 * is SEALED to players who haven't reached it yet (it ignores them and shrugs off their attacks),
 * and on death every eligible player within 48 blocks is credited - advancing their chain,
 * unlocking {@code MobSpec.unlocksZone} and receiving the boss's relic.
 */
public class ModBoss extends ModMob {
    /** Synced so the client can swap to the NAME_enraged.png skin (GlowingMobRenderer). */
    private static final TrackedData<Boolean> ENRAGED = DataTracker.registerData(ModBoss.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final UUID ENRAGE_SPEED = UUID.fromString("7b0f6f3e-3d1c-4a51-9f53-2f0c3a9e6a11");
    private final ServerBossBar bar;
    private boolean enraged;
    private BlockPos home;
    /** Broken Shackle: while > 0 this boss can't heal (regen, lifesteal, mend). */
    private int shackledTicks;
    private int chainIndex = -2;

    public ModBoss(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.bar = new ServerBossBar(this.getDisplayName(), spec().barColor, BossBar.Style.NOTCHED_10);
        this.bar.setDarkenSky(true);
        this.setPersistent();
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(ENRAGED, false);
    }

    public boolean isEnraged() { return this.dataTracker.get(ENRAGED); }

    @Override
    public String skinVariant() { return isEnraged() ? "enraged" : null; }

    public ServerBossBar bossBar() { return bar; }

    /** The arena this boss leashes to (its spawn spot unless re-homed). */
    public BlockPos home() { return home != null ? home : this.getBlockPos(); }

    /** Move the arena this boss leashes to (the Ghost Captain re-homes when he boards a ship). */
    public void setHome(BlockPos h) { this.home = h.toImmutable(); }

    /** How far from home a boss will chase before giving up. */
    protected double leashRange() { return 40; }

    /** Players within this range share the kill (chain credit + relic). Bosses with huge lairs widen it. */
    protected double creditRange() { return 48; }

    /** Hook for bespoke bosses: runs once, right after the phase-2 switch. */
    protected void onEnrage() {}

    /** Position in BossProgression.CHAIN (-1 = not a chain boss). */
    public int chainIndex() {
        if (chainIndex == -2) chainIndex = BossProgression.indexOf(spec().id);
        return chainIndex;
    }

    @Override
    protected boolean mayTarget(net.minecraft.entity.LivingEntity player) {
        return !(player instanceof PlayerEntity p) || BossProgression.eligible(p, chainIndex());
    }

    @Override
    public void heal(float amount) {
        if (shackledTicks > 0) return;
        super.heal(amount);
    }

    @Override
    protected List<Abilities.Ability> activeAbilities() {
        if (!enraged) return spec().abilities;
        List<Abilities.Ability> all = new ArrayList<>(spec().abilities);
        all.addAll(spec().phase2);
        return all;
    }

    @Nullable
    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason,
                                 @Nullable EntityData data, @Nullable NbtCompound nbt) {
        this.home = this.getBlockPos();
        return super.initialize(world, difficulty, reason, data, nbt);
    }

    /** Regen only resumes after this long without taking damage (1 minute). */
    private static final int REGEN_DELAY_TICKS = 20 * 60;
    /** Ticks since the boss last took damage (NBT-persisted so a reload doesn't reset the fight). */
    private int ticksSinceHurt = REGEN_DELAY_TICKS;

    @Override
    protected void mobTick() {
        super.mobTick();
        bar.setPercent(this.getHealth() / this.getMaxHealth());
        if (home == null) home = this.getBlockPos();
        if (!enraged && healthEnrage() && this.getHealth() < this.getMaxHealth() * 0.5f) enrage();
        if (ticksSinceHurt < REGEN_DELAY_TICKS) ticksSinceHurt++;
        if (shackledTicks > 0) shackledTicks--;
        // drop a target that can't fight us (e.g. god mode switched off mid-fight)
        if (this.getTarget() instanceof PlayerEntity tp && !mayTarget(tp)) this.setTarget(null);
        // warn players who wander into a lair they aren't ready for
        if (this.age % 40 == 0 && this.getWorld() instanceof ServerWorld sw) {
            for (ServerPlayerEntity p : sw.getPlayers(p -> p.squaredDistanceTo(this) < 20 * 20 && !p.isSpectator()))
                if (!BossProgression.eligible(p, chainIndex())) p.sendMessage(BossProgression.sealedMessage(p, chainIndex()), true);
        }
        // 1% max health per second, but only once nothing has hurt it for a full minute
        if (ticksSinceHurt >= REGEN_DELAY_TICKS && this.age % 20 == 0 && this.getHealth() < this.getMaxHealth())
            this.heal(this.getMaxHealth() * 0.01f);
        if (this.getTarget() == null) {
            if (!this.getBlockPos().isWithinDistance(home, 12) && this.getNavigation().isIdle())
                this.getNavigation().startMovingTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 1.0);
        } else if (!this.getBlockPos().isWithinDistance(home, leashRange())) {
            this.setTarget(null);   // leash: don't get dragged across the map
        }
    }

    /** False = this boss does not enrage at 50% health (it calls {@link #forceEnrage} on its own terms). */
    protected boolean healthEnrage() { return true; }

    /** Enrage now (once). */
    protected void forceEnrage() { if (!enraged) enrage(); }

    private void enrage() {
        enraged = true;
        this.dataTracker.set(ENRAGED, true);
        bar.setColor(BossBar.Color.RED);
        bar.setName(this.getDisplayName().copy().append(Text.literal(" - " + spec().enrageTitle).formatted(Formatting.DARK_RED)));
        var speed = this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(ENRAGE_SPEED) == null)
            speed.addPersistentModifier(new EntityAttributeModifier(ENRAGE_SPEED, "enrage", 0.3, EntityAttributeModifier.Operation.MULTIPLY_BASE));
        cooldowns.clear();
        triggerAnim(ACTION, spec().enrageAnim);
        if (this.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
            sw.spawnParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 1, getZ(), 40, 1.2, 1.2, 1.2, 0.05);
            sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 2.0f, 0.7f);
        }
        onEnrage();
    }

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        int zone = spec().unlocksZone;
        double credit = creditRange();
        for (ServerPlayerEntity p : sw.getPlayers(p -> p.squaredDistanceTo(this) < credit * credit)) {
            p.sendMessage(Text.literal("[X] ").formatted(Formatting.GOLD)
                    .append(this.getDisplayName().copy().formatted(Formatting.YELLOW))
                    .append(Text.literal(" has been defeated!").formatted(Formatting.GOLD)), false);
            if (chainIndex() < 0) {
                if (zone > 0) PlayerProgressionManager.unlockZone(p, zone);
            } else if (BossProgression.eligible(p, chainIndex())) {
                BossProgression.onBossKilled(p, chainIndex(), zone);
            }
        }
        sw.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, getX(), getY() + 1, getZ(), 80, 1.5, 1.5, 1.5, 0.3);
        sw.playSound(null, getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.HOSTILE, 1.5f, 1.0f);
    }

    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player) {
        super.onStartedTrackingBy(player);
        bar.addPlayer(player);
    }

    @Override
    public void onStoppedTrackingBy(ServerPlayerEntity player) {
        super.onStoppedTrackingBy(player);
        bar.removePlayer(player);
    }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) { return false; }

    @Override
    public boolean cannotDespawn() { return true; }

    @Override
    public boolean damage(DamageSource source, float amount) {
        // bosses can't be drowned/suffocated to death in their own lairs
        if (source.isOf(net.minecraft.entity.damage.DamageTypes.DROWN) && spec().kind == MobSpec.Kind.SWIM) return false;
        if (source.isOf(net.minecraft.entity.damage.DamageTypes.IN_WALL)) return false;
        if (source.getAttacker() instanceof ServerPlayerEntity p && !BossProgression.eligible(p, chainIndex())) {
            p.sendMessage(BossProgression.sealedMessage(p, chainIndex()), true);
            if (this.getWorld() instanceof ServerWorld sw)
                sw.spawnParticles(ParticleTypes.ENCHANT, getX(), getY() + getHeight() * 0.6, getZ(), 12, 0.6, 0.6, 0.6, 0.4);
            return false;
        }
        boolean hurt = super.damage(source, amount);
        if (hurt) ticksSinceHurt = 0;
        if (hurt && source.getAttacker() instanceof PlayerEntity p && BossProgression.relicActive(p, net.get900.pixelpirates.item.ModItems.BROKEN_SHACKLE)) {
            if (shackledTicks == 0 && this.getWorld() instanceof ServerWorld sw)
                sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.HOSTILE, 1.5f, 0.6f);
            shackledTicks = 160;
        }
        return hurt;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Enraged", enraged);
        nbt.putInt("TicksSinceHurt", ticksSinceHurt);
        nbt.putInt("Shackled", shackledTicks);
        if (home != null) nbt.put("Home", NbtHelper.fromBlockPos(home));
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        enraged = nbt.getBoolean("Enraged");
        this.dataTracker.set(ENRAGED, enraged);
        if (nbt.contains("TicksSinceHurt")) ticksSinceHurt = nbt.getInt("TicksSinceHurt");
        shackledTicks = nbt.getInt("Shackled");
        if (nbt.contains("Home")) home = NbtHelper.toBlockPos(nbt.getCompound("Home"));
        if (this.hasCustomName()) bar.setName(this.getDisplayName());
        if (enraged) {
            bar.setColor(BossBar.Color.RED);
            bar.setName(this.getDisplayName().copy().append(Text.literal(" - " + spec().enrageTitle).formatted(Formatting.DARK_RED)));
        }
    }
}
