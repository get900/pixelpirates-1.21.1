package net.get900.pixelpirates.item.custom;

import net.get900.pixelpirates.entity.custom.ThrownGallowbrandEntity;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
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
 * THE GALLOWBRAND - the hangman's greatsword, pulled from the stone under the Chained Revenant. A slow, heavy two-hander
 * in melee; RIGHT-CLICK hurls it end over end (ThrownGallowbrandEntity) and it flies home to your hand like a loyal
 * trident. The mod's first ranged weapon, made for the limbs the Revenant chains to the pit walls (x2.5 damage).
 */
public class GallowbrandItem extends SwordItem implements GeoItem {
    public static final int THROW_COOLDOWN = 16;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    public GallowbrandItem(Item.Settings settings) {
        super(ToolMaterials.NETHERITE, 7, -2.7f, settings);       // 12 @1.3
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.getItemCooldownManager().isCoolingDown(this)) return TypedActionResult.fail(stack);
        user.getItemCooldownManager().set(this, THROW_COOLDOWN);
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 1.2f, 0.6f);
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.PLAYERS, 0.8f, 0.7f);
        if (!world.isClient) {
            ThrownGallowbrandEntity e = new ThrownGallowbrandEntity(world, user, stack.copy());
            e.setVelocity(user, user.getPitch(), user.getYaw(), 0.0f, 2.4f, 0.4f);
            if (user.getAbilities().creativeMode) e.pickupType = net.minecraft.entity.projectile.PersistentProjectileEntity.PickupPermission.CREATIVE_ONLY;
            world.spawnEntity(e);
            if (!user.getAbilities().creativeMode) user.setStackInHand(hand, ItemStack.EMPTY);
        }
        user.incrementStat(Stats.USED.getOrCreateStat(this));
        return TypedActionResult.success(stack, world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("The hangman's greatsword").formatted(Formatting.DARK_AQUA, Formatting.ITALIC));
        tooltip.add(Text.literal("Right-click: hurl it - it always flies home").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("x2.5 damage to the Revenant's chained limbs").formatted(Formatting.GRAY));
        super.appendTooltip(stack, world, tooltip, context);
    }

    // ------------------------------------------------------------------ GeckoLib
    @Override
    public Supplier<Object> getRenderProvider() { return renderProvider; }

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        consumer.accept(new RenderProvider() {
            private net.get900.pixelpirates.item.client.GallowbrandItemRenderer renderer;

            @Override
            public net.minecraft.client.render.item.BuiltinModelItemRenderer getCustomRenderer() {
                if (this.renderer == null) this.renderer = new net.get900.pixelpirates.item.client.GallowbrandItemRenderer();
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {}

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
