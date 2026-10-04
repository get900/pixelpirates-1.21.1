package net.get900.pixelpirates.item.relic;

import net.get900.pixelpirates.entity.custom.TideArrowEntity;
import net.get900.pixelpirates.item.RelicWeapons;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
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
 * SERPENTSPINE LONGBOW (the Sea Serpent's relic weapon): a bow whose arrows fly 30% faster. A FULL draw looses a TIDE
 * ARROW (splash burst: knockback, puts out fire, +4 vs fire mobs / anything water hurts); hold the full draw 2 s for a
 * TIDE VOLLEY of three (costs one arrow). Shift + right-click finds the next lair.
 */
public class SerpentspineBowItem extends BowItem implements GeoItem {
    public static final int VOLLEY_HOLD = 40;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    public SerpentspineBowItem(Settings settings) { super(settings); }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        RelicWeapons.passive(this, world, entity);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (RelicWeapons.locate(this, world, user)) return TypedActionResult.success(user.getStackInHand(hand), world.isClient);
        return super.use(world, user, hand);
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remaining) {
        if (!world.isClient && getMaxUseTime(stack) - remaining == VOLLEY_HOLD && user instanceof PlayerEntity p) {
            RelicWeapons.say(p, "TIDE VOLLEY - let go!");
            RelicWeapons.sound(world, p, SoundEvents.ITEM_BUCKET_FILL, 0.8f, 1.4f);
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remaining) {
        if (!(user instanceof PlayerEntity p)) return;
        boolean infinite = p.getAbilities().creativeMode || EnchantmentHelper.getLevel(Enchantments.INFINITY, stack) > 0;
        ItemStack ammo = p.getProjectileType(stack);
        if (ammo.isEmpty() && !infinite) return;
        if (ammo.isEmpty()) ammo = new ItemStack(Items.ARROW);
        int held = getMaxUseTime(stack) - remaining;
        float pull = getPullProgress(held);
        if (pull < 0.1f) return;
        boolean free = infinite && ammo.isOf(Items.ARROW);
        boolean full = pull >= 1.0f, volley = full && held >= VOLLEY_HOLD;
        if (world instanceof ServerWorld sw) {
            int n = volley ? 3 : 1;
            for (int i = 0; i < n; i++) {
                PersistentProjectileEntity a;
                if (full) a = new TideArrowEntity(sw, p);
                else a = ((ArrowItem) (ammo.getItem() instanceof ArrowItem ai ? ai : Items.ARROW)).createArrow(sw, ammo, p);
                a.setVelocity(p, p.getPitch(), p.getYaw() + (n == 1 ? 0 : (i - 1) * 7f), 0f, pull * 3.9f, 0.6f);
                if (full) a.setCritical(true);
                int power = EnchantmentHelper.getLevel(Enchantments.POWER, stack);
                if (power > 0) a.setDamage(a.getDamage() + power * 0.5 + 0.5);
                int punch = EnchantmentHelper.getLevel(Enchantments.PUNCH, stack);
                if (punch > 0) a.setPunch(punch);
                if (EnchantmentHelper.getLevel(Enchantments.FLAME, stack) > 0) a.setOnFireFor(100);
                if (free || i > 0 || p.getAbilities().creativeMode) a.pickupType = PersistentProjectileEntity.PickupPermission.CREATIVE_ONLY;
                sw.spawnEntity(a);
            }
            stack.damage(1, p, q -> q.sendToolBreakStatus(p.getActiveHand()));
            if (full) RelicWeapons.sound(sw, p, SoundEvents.ENTITY_DOLPHIN_SPLASH, 1f, 1.2f);
        }
        world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.0f,
                1.0f / (world.getRandom().nextFloat() * 0.4f + 1.2f) + pull * 0.5f);
        if (!free && !p.getAbilities().creativeMode) {
            ammo.decrement(1);
            if (ammo.isEmpty()) p.getInventory().removeOne(ammo);
        }
        p.incrementStat(Stats.USED.getOrCreateStat(this));
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        RelicWeapons.tooltip(this, tooltip, "Arrows fly 30% faster",
                "Full draw: TIDE ARROW - bursts in a splash (knockback, +4 vs fire mobs)",
                "Hold a full draw 2 s: TIDE VOLLEY - three at once");
    }

    // ------------------------------------------------------------------ GeckoLib
    @Override
    public Supplier<Object> getRenderProvider() { return renderProvider; }

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        consumer.accept(net.get900.pixelpirates.item.client.RelicWeaponRenderer.provider("serpentspine_longbow"));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        WeaponAnims.controllers(this, controllers);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
