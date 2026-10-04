package net.get900.pixelpirates.item.relic;

import net.get900.pixelpirates.entity.custom.DynamiteEntity;
import net.get900.pixelpirates.entity.custom.KrakenHarpoonEntity;
import net.get900.pixelpirates.entity.custom.SpectralShotEntity;
import net.get900.pixelpirates.homestead.gun.MusketBallEntity;
import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.item.RelicWeapons;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The three relic guns (RelicWeapons). TAP right-click = fire (then the reload cooldown); HOLD it 1 s and let go = the
 * special (own cooldown, kept on the stack); SHIFT + right-click = find the next lair.
 */
public class RelicGunItem extends Item implements GeoItem {
    public enum Type {
        //                          id                     reload special
        BLUNDERBUSS("rackham_blunderbuss", 24, 200),
        HAND_CANNON("dutchmans_hand_cannon", 60, 320),
        HARPOON_GUN("krakenmaw_harpoon_gun", 40, 240);

        final String id;
        final int reload, special;

        Type(String id, int reload, int special) {
            this.id = id;
            this.reload = reload;
            this.special = special;
        }
    }

    public static final int HOLD = 20;
    private final Type type;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    public RelicGunItem(Type type, Settings settings) {
        super(settings);
        this.type = type;
        software.bernie.geckolib.animatable.SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        RelicWeapons.passive(this, world, entity);
        WeaponAnims.assignId(stack, world);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (RelicWeapons.locate(this, world, user)) return TypedActionResult.success(stack, world.isClient);
        if (user.getItemCooldownManager().isCoolingDown(this)) return TypedActionResult.fail(stack);
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remaining) {
        if (!world.isClient && getMaxUseTime(stack) - remaining == HOLD && user instanceof PlayerEntity p) {
            long left = RelicWeapons.left(stack, world, "Special");
            if (left > 0) RelicWeapons.say(p, specialName() + " - ready in " + (left + 19) / 20 + " s");
            else {
                RelicWeapons.say(p, specialName() + " - let go!");
                RelicWeapons.sound(world, p, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 0.8f, 1.6f);
            }
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remaining) {
        if (!(world instanceof ServerWorld sw) || !(user instanceof PlayerEntity p)) return;
        int held = getMaxUseTime(stack) - remaining;
        if (held >= HOLD) {
            if (RelicWeapons.left(stack, world, "Special") > 0) return;
            if (special(sw, p)) {
                WeaponAnims.play(this, p, stack, "special");
                RelicWeapons.ready(stack, world, "Special", type.special);
                p.getItemCooldownManager().set(this, 10);
            }
            return;
        }
        if (fire(sw, p)) {
            WeaponAnims.play(this, p, stack, "fire");
            p.getItemCooldownManager().set(this, Math.max(6, (int) Math.round(type.reload * net.get900.pixelpirates.world.SkillEffects.reloadMult(p))));
            p.incrementStat(Stats.USED.getOrCreateStat(this));
        }
    }

    private String specialName() {
        return switch (type) {
            case BLUNDERBUSS -> "POWDER KEG";
            case HAND_CANNON -> "PHANTOM BROADSIDE";
            case HARPOON_GUN -> "INK CLOUD";
        };
    }

    private boolean fire(ServerWorld w, PlayerEntity p) {
        Vec3d look = p.getRotationVec(1f);
        Vec3d muzzle = p.getEyePos().add(look.multiply(1.2)).add(0, -0.15, 0);
        switch (type) {
            case BLUNDERBUSS -> {
                if (!RelicWeapons.takeAmmo(p, Items.GUNPOWDER, 1)) { RelicWeapons.say(p, "No gunpowder"); return false; }
                for (int i = 0; i < 6; i++) {
                    MusketBallEntity b = new MusketBallEntity(w, p, 3f, 9, true);
                    b.setVelocity(p, p.getPitch(), p.getYaw(), 0f, 3.2f, 8.0f * net.get900.pixelpirates.world.SkillEffects.spreadMult(p));
                    w.spawnEntity(b);
                }
                Vec3d kick = look.multiply(-0.35);
                p.addVelocity(kick.x, Math.max(0.05, kick.y * 0.5), kick.z);
                p.velocityModified = true;
                RelicWeapons.sound(w, p, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.2f, 1.4f);
                w.spawnParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 8, 0.1, 0.1, 0.1, 0.03);
                w.spawnParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 12, 0.15, 0.15, 0.15, 0.03);
            }
            case HAND_CANNON -> {
                boolean ball = RelicWeapons.takeAmmo(p, ModItems.CANNON_BALL, 1);
                SpectralShotEntity s = new SpectralShotEntity(w, p, ball ? 7f : 4f);
                s.setPosition(muzzle);
                s.setVelocity(p, p.getPitch() - 6, p.getYaw(), 0f, 1.9f, 0.5f);
                w.spawnEntity(s);
                RelicWeapons.sound(w, p, SoundEvents.ENTITY_GENERIC_EXPLODE, 1.4f, 0.8f);
                RelicWeapons.sound(w, p, SoundEvents.PARTICLE_SOUL_ESCAPE, 2f, 0.7f);
                w.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, muzzle.x, muzzle.y, muzzle.z, 14, 0.15, 0.15, 0.15, 0.05);
                w.spawnParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 10, 0.2, 0.2, 0.2, 0.02);
                Vec3d kick = look.multiply(-0.3);
                p.addVelocity(kick.x, 0.05, kick.z);
                p.velocityModified = true;
            }
            case HARPOON_GUN -> {
                if (!RelicWeapons.takeAmmo(p, ModItems.HARPOON, 1)) { RelicWeapons.say(p, "No harpoons"); return false; }
                KrakenHarpoonEntity h = KrakenHarpoonEntity.fired(w, p, 11f);
                w.spawnEntity(h);
                RelicWeapons.sound(w, p, SoundEvents.ITEM_CROSSBOW_SHOOT, 1.4f, 0.5f);
                RelicWeapons.sound(w, p, SoundEvents.ITEM_TRIDENT_THROW, 1.2f, 0.6f);
                w.spawnParticles(ParticleTypes.BUBBLE_POP, muzzle.x, muzzle.y, muzzle.z, 10, 0.1, 0.1, 0.1, 0.05);
            }
        }
        return true;
    }

    private boolean special(ServerWorld w, PlayerEntity p) {
        switch (type) {
            case BLUNDERBUSS -> {                              // POWDER KEG: a lobbed, fused charge (no block damage)
                if (!RelicWeapons.takeAmmo(p, Items.GUNPOWDER, 3)) { RelicWeapons.say(p, "The keg needs 3 gunpowder"); return false; }
                DynamiteEntity d = DynamiteEntity.lobbed(w, p, 40, 8f);
                d.setVelocity(p, p.getPitch() - 10, p.getYaw(), 0f, 1.1f, 0.5f);
                w.spawnEntity(d);
                RelicWeapons.sound(w, p, SoundEvents.ENTITY_TNT_PRIMED, 1f, 0.8f);
                RelicWeapons.say(p, "\"Keg away!\"");
            }
            case HAND_CANNON -> {                              // PHANTOM BROADSIDE: four shells rain on where you look
                HitResult aim = p.raycast(48, 0f, false);
                Vec3d at = aim.getPos();
                RelicWeapons.sound(w, p, SoundEvents.ENTITY_WITHER_SHOOT, 1f, 0.6f);
                w.spawnParticles(ParticleTypes.SOUL, at.x, at.y + 0.3, at.z, 30, 2.5, 0.1, 2.5, 0.02);
                RelicWeapons.run(w, 4 * 8, t -> {
                    if (t % 8 != 0) return;
                    Vec3d spot = at.add((w.random.nextDouble() - 0.5) * 6, 0, (w.random.nextDouble() - 0.5) * 6);
                    SpectralShotEntity s = SpectralShotEntity.at(w, p, spot.add(0, 16, 0), new Vec3d(0, -1.4, 0), 7f);
                    w.spawnEntity(s);
                    w.playSound(null, spot.x, spot.y, spot.z, SoundEvents.ENTITY_GENERIC_EXPLODE, net.minecraft.sound.SoundCategory.PLAYERS, 0.6f, 0.5f);
                });
            }
            case HARPOON_GUN -> {                              // INK CLOUD
                RelicWeapons.sound(w, p, SoundEvents.ENTITY_SQUID_SQUIRT, 1.5f, 0.5f);
                w.spawnParticles(ParticleTypes.SQUID_INK, p.getX(), p.getBodyY(0.5), p.getZ(), 120, 3, 1.2, 3, 0.05);
                for (LivingEntity e : RelicWeapons.around(w, p.getPos(), 6, p)) {
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 80, 0));
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 1));
                    if (e instanceof net.minecraft.entity.mob.MobEntity m && m.getTarget() == p) m.setTarget(null);
                }
            }
        }
        return true;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) { return 72000; }

    @Override
    // NONE, not CROSSBOW: vanilla only positions the CROSSBOW use action for real crossbows - any other item was drawn
    // at the camera while held (the gun covered the whole screen). NONE keeps the normal first-person hold.
    public UseAction getUseAction(ItemStack stack) { return UseAction.NONE; }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        switch (type) {
            case BLUNDERBUSS -> RelicWeapons.tooltip(this, tooltip, "Tap: 6 pellets x 3 damage (1 gunpowder)",
                    "Hold 1 s + release: POWDER KEG - a lobbed charge, 8 damage (3 gunpowder, 10 s)");
            case HAND_CANNON -> RelicWeapons.tooltip(this, tooltip, "Tap: an arcing spectral ball, 7 damage in 3 blocks",
                    "  (uses a Cannon Ball - 4 damage without one)",
                    "Hold 1 s + release: PHANTOM BROADSIDE - 4 shells rain where you look (16 s)");
            case HARPOON_GUN -> RelicWeapons.tooltip(this, tooltip, "Tap: a harpoon that impales and pins, 11 damage (uses Harpoons)",
                    "Hold 1 s + release: INK CLOUD - blinds everything within 6 (12 s)");
        }
        long left = world == null ? 0 : RelicWeapons.left(stack, world, "Special");
        if (left > 0) tooltip.add(Text.literal(specialName() + " ready in " + (left + 19) / 20 + " s").formatted(Formatting.RED));
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
        WeaponAnims.controllers(this, controllers, "fire", "special");
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
