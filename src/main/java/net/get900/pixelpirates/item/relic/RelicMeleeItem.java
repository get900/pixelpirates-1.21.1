package net.get900.pixelpirates.item.relic;

import net.get900.pixelpirates.item.RelicWeapons;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The five melee relic weapons (RelicWeapons): right-click = the weapon's ability, shift + right-click = find the next
 * lair. Damage 9..13 on netherite, fireproof. On-hit effects in postHit.
 */
public class RelicMeleeItem extends SwordItem implements GeoItem {
    public enum Type {
        //                  id                  bonus speed  cooldown
        FLAIL("everburning_flail", 6, -2.7f, 140),
        MAW("bloodfin_maw", 7, -2.6f, 300),
        CHAINBREAKER("chainbreaker", 9, -2.75f, 120),
        HEARTSEEKER("heartseeker", 10, -2.8f, 200),
        WRATH("tidefathers_wrath", 13, -2.9f, 400);

        final String id;
        final int bonus, cooldown;
        final float speed;

        Type(String id, int bonus, float speed, int cooldown) {
            this.id = id;
            this.bonus = bonus;
            this.speed = speed;
            this.cooldown = cooldown;
        }
    }

    /** The Heartseeker's pulse: one beat every BEAT ticks, a hit within +-BEAT_WINDOW of it lands "on the beat". */
    public static final int BEAT = 30, BEAT_WINDOW = 5;

    private final Type type;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    public RelicMeleeItem(Type type, Settings settings) {
        super(ToolMaterials.NETHERITE, type.bonus, type.speed, settings);
        this.type = type;
        software.bernie.geckolib.animatable.SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    public static boolean onBeat(World w) {
        long t = w.getTime() % BEAT;
        return t <= BEAT_WINDOW || t >= BEAT - BEAT_WINDOW;
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        RelicWeapons.passive(this, world, entity);
        WeaponAnims.assignId(stack, world);
        if (type == Type.HEARTSEEKER && selected && world.isClient && entity instanceof PlayerEntity p && world.getTime() % BEAT == 0)
            p.playSound(SoundEvents.ENTITY_WARDEN_HEARTBEAT, 0.6f, 1.2f);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (RelicWeapons.locate(this, world, user)) return TypedActionResult.success(stack, world.isClient);
        if (user.getItemCooldownManager().isCoolingDown(this)) return TypedActionResult.fail(stack);
        if (world instanceof ServerWorld sw && !ability(sw, user)) return TypedActionResult.fail(stack);
        WeaponAnims.play(this, user, stack, "ability");
        user.getItemCooldownManager().set(this, type.cooldown);
        user.swingHand(hand, true);
        return TypedActionResult.success(stack, world.isClient);
    }

    private boolean ability(ServerWorld w, PlayerEntity p) {
        Vec3d look = p.getRotationVec(1f);
        Vec3d flat = new Vec3d(look.x, 0, look.z).normalize();
        switch (type) {
            case FLAIL -> {                                   // FISSURE SLAM: three racing fire cracks
                RelicWeapons.sound(w, p, SoundEvents.ENTITY_GENERIC_EXPLODE, 0.9f, 0.6f);
                RelicWeapons.sound(w, p, SoundEvents.ITEM_FIRECHARGE_USE, 1.2f, 0.7f);
                w.spawnParticles(ParticleTypes.LAVA, p.getX(), p.getY() + 0.2, p.getZ(), 12, 0.6, 0.1, 0.6, 0);
                for (int a = -1; a <= 1; a++) {
                    Vec3d d = flat.rotateY((float) Math.toRadians(22 * a));
                    RelicWeapons.lane(w, p, p.getPos().add(d), d, 11, 1.3, 12, ParticleTypes.FLAME, e -> {
                        RelicWeapons.hit(e, p, 7f);
                        e.setOnFireFor(3);
                        e.addVelocity(0, 0.45, 0);
                        e.velocityModified = true;
                    });
                }
            }
            case MAW -> {                                     // BLOOD FRENZY
                RelicWeapons.frenzy(p, 120);
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 120, 0, false, false, true));
                RelicWeapons.sound(w, p, SoundEvents.ENTITY_RAVAGER_ROAR, 0.8f, 1.5f);
                w.spawnParticles(new DustParticleEffect(new Vector3f(0.75f, 0.05f, 0.05f), 1.6f), p.getX(), p.getBodyY(0.5), p.getZ(), 40, 0.8, 0.8, 0.8, 0);
                RelicWeapons.say(p, "BLOOD FRENZY - your bites draw blood");
            }
            case CHAINBREAKER -> { return hook(w, p, look); }
            case HEARTSEEKER -> {                             // SYSTOLE -> DIASTOLE: draw them in, then blast them away
                RelicWeapons.sound(w, p, SoundEvents.ENTITY_WARDEN_HEARTBEAT, 2f, 0.6f);
                RelicWeapons.run(w, 22, t -> {
                    if (!p.isAlive()) return;
                    if (t < 12) {
                        for (LivingEntity e : RelicWeapons.around(w, p.getPos(), 8, p)) {
                            Vec3d in = p.getPos().subtract(e.getPos()).normalize().multiply(0.35);
                            e.setVelocity(in.x, e.getVelocity().y * 0.5, in.z);
                            e.velocityModified = true;
                        }
                        if (t % 3 == 0) w.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, p.getX(), p.getBodyY(0.5), p.getZ(), 6, 3, 0.5, 3, 0.1);
                    } else if (t == 21) {
                        RelicWeapons.sound(w, p, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 1f, 1.4f);
                        w.spawnParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getBodyY(0.5), p.getZ(), 1, 0, 0, 0, 0);
                        for (LivingEntity e : RelicWeapons.around(w, p.getPos(), 5, p)) {
                            RelicWeapons.hit(e, p, 6f);
                            RelicWeapons.knock(e, e.getPos().subtract(p.getPos()), 1.4, 0.45);
                        }
                    }
                });
            }
            case WRATH -> {                                   // THE LAST TIDE
                RelicWeapons.sound(w, p, SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 0.6f);
                RelicWeapons.sound(w, p, SoundEvents.ENTITY_GENERIC_SPLASH, 2f, 0.5f);
                RelicWeapons.lane(w, p, p.getPos().add(flat), flat, 20, 2.6, 20, ParticleTypes.SPLASH, e -> {
                    RelicWeapons.hit(e, p, 12f);
                    RelicWeapons.knock(e, flat, 1.3, 0.5);
                    if (e.isOnFire()) e.extinguish();
                });
                Vec3d side = flat.rotateY((float) Math.PI / 2);
                RelicWeapons.run(w, 20, t -> {
                    Vec3d q = p.getPos().add(flat.multiply(1 + t));
                    for (int i = -3; i <= 3; i++) {
                        Vec3d c = q.add(side.multiply(i * 0.8));
                        w.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, c.x, c.y + 0.3, c.z, 3, 0.2, 0.6, 0.2, 0.1);
                        w.spawnParticles(ParticleTypes.FALLING_WATER, c.x, c.y + 2.0, c.z, 4, 0.3, 0.4, 0.3, 0);
                    }
                });
            }
        }
        return true;
    }

    /** HANGMAN'S HOOK: a mob in the line is dragged to you; otherwise you are reeled to the wall you hook. */
    private boolean hook(ServerWorld w, PlayerEntity p, Vec3d look) {
        double range = 18;
        Vec3d eye = p.getEyePos(), end = eye.add(look.multiply(range));
        BlockHitResult block = w.raycast(new net.minecraft.world.RaycastContext(eye, end,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER, net.minecraft.world.RaycastContext.FluidHandling.NONE, p));
        Vec3d stop = block.getType() == HitResult.Type.MISS ? end : block.getPos();
        EntityHitResult ent = ProjectileUtil.raycast(p, eye, stop, p.getBoundingBox().stretch(look.multiply(range)).expand(1),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator(), range * range);
        Vec3d to;
        if (ent != null && ent.getEntity() instanceof LivingEntity target) {
            to = target.getPos();
            Vec3d pull = eye.subtract(target.getPos());
            double d = pull.length();
            boolean heavy = target instanceof net.get900.pixelpirates.entity.mob.ModBoss || target.getWidth() > 2.2f;
            if (heavy) RelicWeapons.hit(target, p, 5f);                       // a boss won't budge - it just bites
            else {
                target.setVelocity(pull.normalize().multiply(Math.min(2.4, 0.5 + d * 0.16)).add(0, 0.35, 0));
                target.velocityModified = true;
                RelicWeapons.hit(target, p, 4f);
            }
        } else if (block.getType() == HitResult.Type.BLOCK) {
            to = block.getPos();
            Vec3d go = to.subtract(p.getPos());
            p.setVelocity(go.normalize().multiply(Math.min(2.6, 0.6 + go.length() * 0.17)).add(0, 0.4, 0));
            p.velocityModified = true;
            p.fallDistance = 0;
            RelicWeapons.run(w, 30, t -> p.fallDistance = 0);
        } else {
            RelicWeapons.sound(w, p, SoundEvents.BLOCK_CHAIN_FALL, 0.8f, 1.4f);
            return false;
        }
        RelicWeapons.sound(w, p, SoundEvents.BLOCK_CHAIN_PLACE, 1.4f, 0.6f);
        RelicWeapons.sound(w, p, SoundEvents.ITEM_TRIDENT_THROW, 1f, 0.5f);
        Vec3d from = p.getPos().add(0, 1.2, 0), line = to.add(0, 0.5, 0).subtract(from);
        for (double s = 0; s < line.length(); s += 0.4) {
            Vec3d q = from.add(line.normalize().multiply(s));
            w.spawnParticles(new DustParticleEffect(new Vector3f(0.35f, 0.35f, 0.38f), 1f), q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
        return true;
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean r = super.postHit(stack, target, attacker);
        World w = attacker.getWorld();
        if (w.isClient) return r;
        switch (type) {
            case FLAIL -> target.setOnFireFor(4);
            case MAW -> {
                if (attacker instanceof PlayerEntity p && RelicWeapons.inFrenzy(p)) {
                    if (RelicWeapons.bleeding(target)) p.heal(2f);
                    RelicWeapons.bleed(target, p, 100);
                }
            }
            case HEARTSEEKER -> {
                if (onBeat(w)) {
                    RelicWeapons.hit(target, attacker, getAttackDamage() + 1);
                    attacker.heal(2f);
                    RelicWeapons.sound(w, attacker, SoundEvents.ENTITY_WARDEN_HEARTBEAT, 1.5f, 1.6f);
                    ((ServerWorld) w).spawnParticles(ParticleTypes.HEART, target.getX(), target.getBodyY(0.8), target.getZ(), 3, 0.3, 0.3, 0.3, 0);
                }
            }
            case WRATH -> {
                if (attacker.isTouchingWater()) {                                // every blow in water sends a small wave
                    ServerWorld sw = (ServerWorld) w;
                    sw.spawnParticles(ParticleTypes.SPLASH, target.getX(), target.getBodyY(0.5), target.getZ(), 30, 1.2, 0.4, 1.2, 0.2);
                    for (LivingEntity e : RelicWeapons.around(sw, target.getPos(), 2.5, attacker)) {
                        RelicWeapons.hit(e, attacker, 3f);
                        RelicWeapons.knock(e, e.getPos().subtract(attacker.getPos()), 0.7, 0.2);
                    }
                }
            }
            default -> {}
        }
        return r;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        switch (type) {
            case FLAIL -> RelicWeapons.tooltip(this, tooltip, "Sets what it hits ablaze",
                    "Right-click: FISSURE SLAM - three cracks of fire race ahead (7 s)");
            case MAW -> RelicWeapons.tooltip(this, tooltip, "Right-click: BLOOD FRENZY for 6 s (15 s)",
                    "  hits make them bleed; hitting a bleeding foe heals 1 heart");
            case CHAINBREAKER -> RelicWeapons.tooltip(this, tooltip, "Right-click: HANGMAN'S HOOK (6 s)",
                    "  drags a foe to you, or reels you to the wall you hook");
            case HEARTSEEKER -> RelicWeapons.tooltip(this, tooltip, "It beats. Strike on the beat: double damage, heal 1 heart",
                    "Right-click: SYSTOLE - draw foes in, then blast them away (10 s)");
            case WRATH -> RelicWeapons.tooltip(this, tooltip, "Right-click: THE LAST TIDE - a 20-block wave, 12 damage (20 s)",
                    "In water every blow sends a small wave");
        }
        super.appendTooltip(stack, world, tooltip, context);
    }

    // ------------------------------------------------------------------ GeckoLib
    @Override
    public Supplier<Object> getRenderProvider() { return renderProvider; }

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        consumer.accept(net.get900.pixelpirates.item.client.RelicWeaponRenderer.provider(type.id));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        WeaponAnims.controllers(this, controllers, "ability");
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
