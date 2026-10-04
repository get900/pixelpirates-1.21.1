package net.get900.pixelpirates.item.relic;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.get900.pixelpirates.entity.custom.ThrownRelicEntity;
import net.get900.pixelpirates.item.RelicWeapons;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
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
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * TRIDENT OF THE SUNKEN COURT (the Abyssal King's relic weapon): 10 damage in melee. TAP right-click = RIPTIDE CHARGE
 * (a 10-block dash that rams everything in the way, works on land); HOLD and release = throw it (ThrownRelicEntity,
 * returns like Loyalty). Shift + right-click finds the next lair.
 */
public class SunkenTridentItem extends Item implements GeoItem {
    public static final int TAP = 8, DASH_COOLDOWN = 120;
    private final Multimap<EntityAttribute, EntityAttributeModifier> modifiers;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    public SunkenTridentItem(Settings settings) {
        super(settings);
        software.bernie.geckolib.animatable.SingletonGeoAnimatable.registerSyncedAnimatable(this);
        ImmutableMultimap.Builder<EntityAttribute, EntityAttributeModifier> b = ImmutableMultimap.builder();
        b.put(EntityAttributes.GENERIC_ATTACK_DAMAGE, new EntityAttributeModifier(ATTACK_DAMAGE_MODIFIER_ID, "Weapon modifier", 9.0, EntityAttributeModifier.Operation.ADDITION));
        b.put(EntityAttributes.GENERIC_ATTACK_SPEED, new EntityAttributeModifier(ATTACK_SPEED_MODIFIER_ID, "Weapon modifier", -2.8, EntityAttributeModifier.Operation.ADDITION));
        this.modifiers = b.build();
    }

    @Override
    public Multimap<EntityAttribute, EntityAttributeModifier> getAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? modifiers : super.getAttributeModifiers(slot);
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.damage(1, attacker, e -> e.sendEquipmentBreakStatus(EquipmentSlot.MAINHAND));
        return true;
    }

    @Override
    public int getEnchantability() { return 1; }

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
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remaining) {
        if (!(world instanceof ServerWorld sw) || !(user instanceof PlayerEntity p)) return;
        int held = getMaxUseTime(stack) - remaining;
        if (held < TAP) {
            if (!RelicWeapons.ready(stack, world, "Dash", DASH_COOLDOWN)) {
                RelicWeapons.say(p, "Riptide Charge - ready in " + (RelicWeapons.left(stack, world, "Dash") + 19) / 20 + " s");
                return;
            }
            dash(sw, p);
            WeaponAnims.play(this, p, stack, "dash");
            return;
        }
        ThrownRelicEntity t = new ThrownRelicEntity(sw, p, stack, 10f);
        t.setVelocity(p, p.getPitch(), p.getYaw(), 0f, 2.5f, 0.5f);
        sw.spawnEntity(t);
        RelicWeapons.sound(sw, p, SoundEvents.ITEM_TRIDENT_THROW, 1.2f, 0.8f);
        stack.damage(1, p, e -> e.sendToolBreakStatus(p.getActiveHand()));
        if (!p.getAbilities().creativeMode) p.getInventory().removeOne(stack);
        p.getItemCooldownManager().set(this, 10);
        p.incrementStat(Stats.USED.getOrCreateStat(this));
    }

    /** RIPTIDE CHARGE: shoot forward ~10 blocks in a torrent, ramming (6 + knockback) everything in the way. */
    private void dash(ServerWorld w, PlayerEntity p) {
        Vec3d look = p.getRotationVec(1f);
        Vec3d dir = new Vec3d(look.x, Math.max(-0.2, Math.min(0.35, look.y)), look.z).normalize();
        p.setVelocity(dir.multiply(1.9).add(0, 0.15, 0));
        p.velocityModified = true;
        p.useRiptide(12);
        RelicWeapons.sound(w, p, SoundEvents.ITEM_TRIDENT_RIPTIDE_2, 1.2f, 1f);
        Set<UUID> hit = new HashSet<>();
        RelicWeapons.run(w, 12, t -> {
            if (!p.isAlive()) return;
            p.fallDistance = 0;
            w.spawnParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 0.8, p.getZ(), 10, 0.4, 0.4, 0.4, 0.1);
            w.spawnParticles(ParticleTypes.BUBBLE, p.getX(), p.getY() + 0.8, p.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
            for (LivingEntity e : RelicWeapons.around(w, p.getPos().add(0, 0.9, 0), 1.8, p)) {
                if (!hit.add(e.getUuid())) continue;
                RelicWeapons.hit(e, p, 6f);
                RelicWeapons.knock(e, dir, 1.0, 0.4);
            }
        });
    }

    @Override
    public int getMaxUseTime(ItemStack stack) { return 72000; }

    @Override
    public UseAction getUseAction(ItemStack stack) { return UseAction.SPEAR; }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        RelicWeapons.tooltip(this, tooltip, "Tap right-click: RIPTIDE CHARGE - dash 10 blocks, ramming foes (6 s)",
                "Hold + release: throw it (10 damage) - it always comes home");
        long left = world == null ? 0 : RelicWeapons.left(stack, world, "Dash");
        if (left > 0) tooltip.add(Text.literal("Riptide Charge ready in " + (left + 19) / 20 + " s").formatted(Formatting.RED));
    }

    // ------------------------------------------------------------------ GeckoLib
    @Override
    public Supplier<Object> getRenderProvider() { return renderProvider; }

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        consumer.accept(net.get900.pixelpirates.item.client.RelicWeaponRenderer.provider("sunken_trident"));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        WeaponAnims.controllers(this, controllers, "dash");
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
