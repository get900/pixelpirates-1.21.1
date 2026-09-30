package net.get900.pixelpirates.entity.mob;

import net.get900.pixelpirates.block.ModBlocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * BOSS 2/10 - THE SEA SERPENT, coiled in the Serpent's Hollow.
 *
 * TIDEPLATE: four pearl scale plates, each anchored by a Tideward Stone placed in the hollow at
 * worldgen (positions stored here). Every second the serpent recounts the stones still standing;
 * with all four it takes only 40% damage, each stone smashed (Tidebreaker only) strips a plate
 * (the model's plateN bone disappears) for +15%, down to full damage with none. A shed plate stuns
 * it for 2 s. Spawned without stones (egg / summon) it simply has no plates.
 *
 * Phase 1: Bite (lunge), Tail Lash (ring slam), Constrict (reels you in + holds), Pressure Jet (a cone
 * of water that blasts you into the walls), Silt Ambush (vanishes, bubbles mark the spot, erupts
 * under you). Phase 2 "Stormscale" at 50% (cracked skin, glowing cyan veins): Maelstrom (pull +
 * mining fatigue), Storm Surge (1 s charge, then an electric discharge around it) and Brood
 * (void squid, max 4 alive).
 */
public class SeaSerpentEntity extends ModBoss {
    public static final int PLATES = 4;
    private static final TrackedData<Integer> SCALES = DataTracker.registerData(SeaSerpentEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final List<String> PLATE_BONES = List.of("plate0", "plate1", "plate2", "plate3");

    private final List<BlockPos> wards = new ArrayList<>();
    private final Set<UUID> greeted = new HashSet<>();
    private int stun, hintCooldown;
    // silt ambush
    @Nullable private Vec3d ambushAt;
    @Nullable private LivingEntity ambushTarget;
    private int ambushTicks;
    // storm surge charge
    private int surgeCharge;

    public SeaSerpentEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(SCALES, 0);
    }

    public int scales() { return this.dataTracker.get(SCALES); }

    /** Worldgen: the Tideward Stones that anchor the plates (RackhamFort-style builder calls this). */
    public void setWards(List<BlockPos> positions) {
        wards.clear();
        wards.addAll(positions);
        this.dataTracker.set(SCALES, Math.min(PLATES, wards.size()));
    }

    /** Damage multiplier from the plates still on: 4 -> 0.40 ... 0 -> 1.0 */
    public float plateFactor() { return 1f - 0.15f * scales(); }

    @Override
    public List<String> toggleBones() { return PLATE_BONES; }

    @Override
    public boolean isBoneHidden(String bone) {
        int i = PLATE_BONES.indexOf(bone);
        return i >= 0 && i >= scales();
    }

    @Override
    protected List<String> extraAnims() { return List.of("shed"); }

    // ------------------------------------------------------------------ ticking
    @Override
    protected void mobTick() {
        if (stun > 0) {
            stun--;
            this.getNavigation().stop();
            this.setVelocity(this.getVelocity().multiply(0.5));
        }
        super.mobTick();
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        if (hintCooldown > 0) hintCooldown--;

        if (this.age % 20 == 0) {
            recountWards(sw);
            for (ServerPlayerEntity p : sw.getPlayers(p -> p.isAlive() && !p.isSpectator() && p.squaredDistanceTo(this) < 30 * 30)) {
                if (greeted.add(p.getUuid()) && scales() > 0)
                    p.sendMessage(Text.literal("The serpent's pearl scales drink the light of " + scales()
                            + " Tideward Stones. Smash them with a Tidebreaker to strip its armour.").formatted(Formatting.AQUA), false);
            }
            // light threads from each standing stone to the serpent - shows the player where they are
            if (scales() > 0 && !sw.getPlayers(p -> p.squaredDistanceTo(this) < 48 * 48).isEmpty())
                for (BlockPos w : wards) if (sw.isChunkLoaded(w) && sw.getBlockState(w).isOf(ModBlocks.SERPENT_WARD)) thread(sw, w);
        }
        tickAmbush(sw);
        tickSurge(sw);
    }

    private void recountWards(ServerWorld sw) {
        if (wards.isEmpty()) return;
        int standing = 0;
        for (BlockPos w : wards) {
            if (!sw.isChunkLoaded(w)) { standing++; continue; }         // unloaded: assume it still stands
            if (sw.getBlockState(w).isOf(ModBlocks.SERPENT_WARD)) standing++;
        }
        standing = Math.min(PLATES, standing);
        int had = scales();
        if (standing < had) shed(sw, had - standing, standing);
        this.dataTracker.set(SCALES, standing);
    }

    private void shed(ServerWorld sw, int lost, int left) {
        stun = 40;
        triggerAnim(ACTION, "shed");
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_ELDER_GUARDIAN_HURT, SoundCategory.HOSTILE, 2.5f, 0.6f);
        sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 2.0f, 0.5f);
        sw.spawnParticles(ParticleTypes.END_ROD, getX(), getY() + 1, getZ(), 40 * lost, 1.5, 0.8, 1.5, 0.12);
        sw.spawnParticles(ParticleTypes.BUBBLE, getX(), getY() + 1, getZ(), 60, 1.5, 1.0, 1.5, 0.3);
        String msg = left == 0 ? "The last pearl plate cracks away - the serpent is laid bare!"
                : "A pearl plate shatters off the serpent! (" + left + " left)";
        for (ServerPlayerEntity p : sw.getPlayers(p -> p.squaredDistanceTo(this) < 64 * 64))
            p.sendMessage(Text.literal(msg).formatted(Formatting.GOLD), false);
    }

    private void thread(ServerWorld sw, BlockPos w) {
        Vec3d a = Vec3d.ofCenter(w), b = this.getPos().add(0, this.getHeight() * 0.5, 0);
        int n = (int) Math.min(60, a.distanceTo(b) * 1.2);
        for (int i = 1; i < n; i += 2) {
            Vec3d q = a.lerp(b, i / (double) n);
            sw.spawnParticles(ParticleTypes.GLOW, q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0);
        }
    }

    // ------------------------------------------------------------------ damage
    @Override
    public boolean damage(DamageSource source, float amount) {
        if (scales() > 0 && source.getAttacker() instanceof ServerPlayerEntity p && hintCooldown == 0) {
            hintCooldown = 200;
            p.sendMessage(Text.literal("Your blow glances off the pearl scales. Smash the Tideward Stones!").formatted(Formatting.AQUA), true);
            this.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, 1.5f, 0.7f);
        }
        return super.damage(source, amount * plateFactor());
    }

    // ------------------------------------------------------------------ silt ambush
    void startAmbush(LivingEntity t) {
        if (t == null) return;
        ServerWorld sw = (ServerWorld) this.getWorld();
        double a = this.random.nextDouble() * Math.PI * 2;
        ambushAt = t.getPos().add(Math.cos(a) * 2.5, -1.5, Math.sin(a) * 2.5);
        ambushTarget = t;
        ambushTicks = 28;
        sw.spawnParticles(ParticleTypes.CLOUD, getX(), getY() + 0.5, getZ(), 30, 1.2, 0.6, 1.2, 0.02);
        this.setInvisible(true);
        this.playSound(SoundEvents.ENTITY_DOLPHIN_SPLASH, 1.5f, 0.5f);
    }

    private void tickAmbush(ServerWorld sw) {
        if (ambushTicks <= 0 || ambushAt == null) return;
        ambushTicks--;
        // the tell: a churning bubble column where it will burst out
        sw.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, ambushAt.x, ambushAt.y + 0.5, ambushAt.z, 6, 0.5, 0.2, 0.5, 0.1);
        if (ambushTicks % 6 == 0) sw.playSound(null, BlockPos.ofFloored(ambushAt), SoundEvents.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundCategory.HOSTILE, 1.5f, 0.8f);
        if (ambushTicks == 0) {
            this.setInvisible(false);
            this.refreshPositionAndAngles(ambushAt.x, ambushAt.y, ambushAt.z, this.getYaw(), this.getPitch());
            triggerAnim(ACTION, "attack");
            sw.spawnParticles(ParticleTypes.EXPLOSION, ambushAt.x, ambushAt.y + 1, ambushAt.z, 2, 0.5, 0.5, 0.5, 0);
            for (LivingEntity e : Abilities.victims(this, 3.2)) {
                if (e.damage(this.getDamageSources().mobAttack(this), 12f)) {
                    e.addVelocity(0, 0.9, 0);
                    e.velocityModified = true;
                }
            }
            if (ambushTarget != null && ambushTarget.isAlive()) {
                Vec3d d = ambushTarget.getPos().subtract(this.getPos()).normalize();
                this.setVelocity(d.multiply(1.2));
                this.velocityDirty = true;
            }
            ambushAt = null;
            ambushTarget = null;
        }
    }

    // ------------------------------------------------------------------ storm surge
    void startSurge() {
        surgeCharge = 22;
        this.playSound(SoundEvents.BLOCK_BEACON_POWER_SELECT, 2.0f, 0.5f);
    }

    private void tickSurge(ServerWorld sw) {
        if (surgeCharge <= 0) return;
        surgeCharge--;
        double r = 7.0 * (1 - surgeCharge / 22.0) + 0.5;
        Abilities.ring(sw, this.getPos().add(0, this.getHeight() * 0.5, 0), r, ParticleTypes.ELECTRIC_SPARK, 24);
        sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1, getZ(), 6, 0.8, 0.6, 0.8, 0.2);
        if (surgeCharge == 0) {
            sw.spawnParticles(ParticleTypes.FLASH, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
            sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 2.0f, 1.6f);
            for (LivingEntity e : Abilities.victims(this, 7.5)) {
                if (e.damage(this.getDamageSources().indirectMagic(this, this), 9f))
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2), this);
            }
        }
    }

    // ------------------------------------------------------------------ persistence
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        NbtList l = new NbtList();
        for (BlockPos p : wards) l.add(NbtHelper.fromBlockPos(p));
        nbt.put("Wards", l);
        nbt.putInt("Scales", scales());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        wards.clear();
        for (NbtElement e : nbt.getList("Wards", NbtElement.COMPOUND_TYPE)) wards.add(NbtHelper.toBlockPos((NbtCompound) e));
        this.dataTracker.set(SCALES, nbt.contains("Scales") ? nbt.getInt("Scales") : Math.min(PLATES, wards.size()));
    }

    // =====================================================================================
    // ability effects (wired in MobSpecs)
    // =====================================================================================
    /** Constrict: hauls the target in against its coils and holds it there. */
    public static Abilities.Effect constrict() {
        return (mob, t) -> {
            if (t == null) return;
            Vec3d to = mob.getPos().add(0, 0.5, 0).subtract(t.getPos());
            if (to.lengthSquared() > 4) { t.setVelocity(to.normalize().multiply(Math.min(1.6, to.length() * 0.25))); t.velocityModified = true; }
            if (t.damage(mob.getDamageSources().mobAttack(mob), 6f))
                t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 4), mob);
            Abilities.ring(Abilities.sw(mob), t.getPos().add(0, 1, 0), 1.3, ParticleTypes.BUBBLE_POP, 16);
            mob.playSound(SoundEvents.ENTITY_GUARDIAN_ATTACK, 1.5f, 0.6f);
        };
    }

    /** Pressure Jet: a 14-block cone of water from the jaws - hurts and blasts everyone in it away. */
    public static Abilities.Effect pressureJet() {
        return (mob, t) -> {
            if (t == null) return;
            ServerWorld sw = Abilities.sw(mob);
            Vec3d from = mob.getPos().add(0, mob.getHeight() * 0.5, 0);
            Vec3d dir = t.getPos().add(0, t.getHeight() * 0.5, 0).subtract(from).normalize();
            for (int i = 1; i <= 28; i++) {
                Vec3d q = from.add(dir.multiply(i * 0.5));
                sw.spawnParticles(ParticleTypes.BUBBLE, q.x, q.y, q.z, 3, 0.15 + i * 0.02, 0.15 + i * 0.02, 0.15 + i * 0.02, 0.05);
            }
            for (LivingEntity e : Abilities.victims(mob, 14)) {
                Vec3d to = e.getPos().add(0, e.getHeight() * 0.5, 0).subtract(from);
                if (to.normalize().dotProduct(dir) < 0.93) continue;               // ~21 degree cone
                if (e.damage(mob.getDamageSources().mobAttack(mob), 5f)) {
                    double kb = 1.0 - e.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
                    e.addVelocity(dir.x * 2.2 * kb, dir.y * 2.2 * kb + 0.2, dir.z * 2.2 * kb);
                    e.velocityModified = true;
                }
            }
            mob.playSound(SoundEvents.ENTITY_GENERIC_SPLASH, 2.0f, 0.5f);
        };
    }

    public static Abilities.Effect ambush() {
        return (mob, t) -> { if (mob instanceof SeaSerpentEntity s) s.startAmbush(t); };
    }

    public static Abilities.Effect surge() {
        return (mob, t) -> { if (mob instanceof SeaSerpentEntity s) s.startSurge(); };
    }

    /** Brood: three void squid, never more than four around it. */
    public static Abilities.Effect brood() {
        Abilities.Effect spawn = Abilities.summon("pixelpirates:void_squid", 3, 4);
        return (mob, t) -> {
            var type = ModMobs.TYPES.get("void_squid");
            if (type != null && mob.getWorld().getEntitiesByType(type, mob.getBoundingBox().expand(24), LivingEntity::isAlive).size() >= 4) return;
            spawn.fire(mob, t);
        };
    }
}
