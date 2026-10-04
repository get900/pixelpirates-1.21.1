package net.get900.pixelpirates.homestead.gun;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * FLINTLOCK PISTOL / BLUNDERBUSS: hold use to load a round (the crossbow-pull pose); use again to fire.
 * The pistol throws one heavy ball far and flat; the blunderbuss sprays pellets in a cone and kicks you back.
 * Loaded state lives on the stack ("Loaded"). 3D GeckoLib models (tools/mobs/guns.py).
 */
public class GunItem extends Item implements GeoItem {
    public enum Kind { PISTOL, BLUNDERBUSS }

    private final Kind kind;
    private final Supplier<Item> ammo;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    public GunItem(Settings s, Kind kind, Supplier<Item> ammo) {
        super(s);
        this.kind = kind;
        this.ammo = ammo;
        software.bernie.geckolib.animatable.SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, net.minecraft.entity.Entity entity, int slot, boolean selected) {
        net.get900.pixelpirates.item.relic.WeaponAnims.assignId(stack, world);
    }

    public static boolean loaded(ItemStack s) { return s.hasNbt() && s.getNbt().getBoolean("Loaded"); }

    static void setLoaded(ItemStack s, boolean v) { s.getOrCreateNbt().putBoolean("Loaded", v); }

    int reloadTicks() { return kind == Kind.PISTOL ? 30 : 45; }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (loaded(stack)) {
            if (!world.isClient) discharge((ServerWorld) world, user, stack, hand);
            user.getItemCooldownManager().set(this, kind == Kind.PISTOL ? 8 : 14);
            return TypedActionResult.success(stack, world.isClient);
        }
        if (!user.getAbilities().creativeMode && findAmmo(user).isEmpty()) {
            if (!world.isClient) user.sendMessage(Text.literal("No " + ammo.get().getName().getString() + " to load").formatted(Formatting.GRAY), true);
            return TypedActionResult.fail(stack);
        }
        user.setCurrentHand(hand);
        if (!world.isClient) world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ITEM_CROSSBOW_LOADING_START, SoundCategory.PLAYERS, 0.8f, 0.7f);
        return TypedActionResult.consume(stack);
    }

    private ItemStack findAmmo(PlayerEntity p) {
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(ammo.get())) return s;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remaining) {
        // Quick Hands: the round is loaded after the (shortened) reload, not after the item's fixed use time
        int need = Math.max(8, (int) Math.round(reloadTicks() * net.get900.pixelpirates.world.SkillEffects.reloadMult(user instanceof PlayerEntity p ? p : null)));
        if (getMaxUseTime(stack) - remaining >= need) {
            finishUsing(stack, world, user);
            user.stopUsingItem();
        }
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (!world.isClient && user instanceof PlayerEntity p) {
            if (!p.getAbilities().creativeMode) {
                ItemStack a = findAmmo(p);
                if (a.isEmpty()) return stack;
                a.decrement(1);
            }
            setLoaded(stack, true);
            world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_CROSSBOW_LOADING_END, SoundCategory.PLAYERS, 1f, 0.6f);
            world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_TRIPWIRE_CLICK_ON, SoundCategory.PLAYERS, 1f, 1.4f);
        }
        return stack;
    }

    private void discharge(ServerWorld world, PlayerEntity user, ItemStack stack, Hand hand) {
        setLoaded(stack, false);
        net.get900.pixelpirates.item.relic.WeaponAnims.play(this, user, stack, "fire");
        Vec3d look = user.getRotationVec(1f);
        Vec3d muzzle = user.getEyePos().add(look.multiply(1.1)).add(0, -0.15, 0);
        if (kind == Kind.PISTOL) {
            MusketBallEntity b = new MusketBallEntity(world, user, 13f, 30, false);
            b.setVelocity(user, user.getPitch(), user.getYaw(), 0f, 4.6f, 0.4f * net.get900.pixelpirates.world.SkillEffects.spreadMult(user));
            world.spawnEntity(b);
        } else {
            for (int i = 0; i < 7; i++) {
                MusketBallEntity b = new MusketBallEntity(world, user, 4.5f, 8, true);
                b.setVelocity(user, user.getPitch(), user.getYaw(), 0f, 3.0f, 9.0f * net.get900.pixelpirates.world.SkillEffects.spreadMult(user));
                world.spawnEntity(b);
            }
            Vec3d kick = look.multiply(-0.45);
            user.addVelocity(kick.x, Math.max(0.05, kick.y * 0.5), kick.z);
            user.velocityModified = true;
        }
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS,
                kind == Kind.PISTOL ? 0.9f : 1.3f, kind == Kind.PISTOL ? 1.7f : 1.3f);
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 1.2f, 0.5f);
        world.spawnParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 6, 0.05, 0.05, 0.05, 0.02);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, kind == Kind.PISTOL ? 6 : 14, 0.1, 0.1, 0.1, 0.03);
        world.spawnParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 10, 0.2, 0.2, 0.2, 0.02);
        user.incrementStat(Stats.USED.getOrCreateStat(this));
        stack.damage(1, user, p -> p.sendToolBreakStatus(hand));
    }

    @Override
    public int getMaxUseTime(ItemStack stack) { return 72000; }        // loading ends in usageTick (Quick Hands)

    @Override
    // NONE, not CROSSBOW: vanilla only positions the CROSSBOW use action for real crossbows - any other item was drawn
    // at the camera while held (the gun covered the whole screen). NONE keeps the normal first-person hold.
    public UseAction getUseAction(ItemStack stack) { return UseAction.NONE; }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal(loaded(stack) ? "LOADED" : "Unloaded").formatted(loaded(stack) ? Formatting.GOLD : Formatting.DARK_GRAY));
        tooltip.add(Text.literal("Hold use to load a " + ammo.get().getName().getString() + ", use again to shoot").formatted(Formatting.GRAY));
        tooltip.add(Text.literal(kind == Kind.PISTOL ? "One heavy ball: 13 damage" : "7 pellets x 4.5 damage, close range").formatted(Formatting.GRAY));
    }

    // ------------------------------------------------------------------ GeckoLib
    @Override
    public Supplier<Object> getRenderProvider() { return renderProvider; }

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        consumer.accept(new RenderProvider() {
            private net.get900.pixelpirates.homestead.client.GunRenderer renderer;

            @Override
            public net.minecraft.client.render.item.BuiltinModelItemRenderer getCustomRenderer() {
                if (this.renderer == null) this.renderer = new net.get900.pixelpirates.homestead.client.GunRenderer(kind == Kind.PISTOL ? "flintlock_pistol" : "blunderbuss");
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        net.get900.pixelpirates.item.relic.WeaponAnims.controllers(this, controllers, "fire");
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
