package net.get900.pixelpirates.item.custom;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Ring 4 cursed blade — hits wither the target; right-click "Phantom Rush"
 * hurls the wielder forward like a wraith (with slow falling so the landing doesn't kill you).
 */
public class WraithbladeItem extends SwordItem {
    private static final int COOLDOWN_TICKS = 240; // 12s

    public WraithbladeItem(ToolMaterial material, int attackDamage, float attackSpeed, Settings settings) {
        super(material, attackDamage, attackSpeed, settings);
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!target.getWorld().isClient) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 60, 1), attacker);
        }
        return super.postHit(stack, target, attacker);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient) {
            Vec3d rot = user.getRotationVector();
            Vec3d boost = new Vec3d(rot.x, MathHelper.clamp(rot.y * 0.5 + 0.15, -0.2, 0.6), rot.z)
                    .normalize().multiply(2.4);
            user.setVelocity(boost);
            user.velocityModified = true;
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 60, 0, false, false));

            ServerWorld serverWorld = (ServerWorld) world;
            serverWorld.spawnParticles(ParticleTypes.SMOKE,
                    user.getX(), user.getBodyY(0.5), user.getZ(), 24, 0.4, 0.6, 0.4, 0.03);
            serverWorld.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    user.getX(), user.getBodyY(0.5), user.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ENTITY_PHANTOM_SWOOP, SoundCategory.PLAYERS, 1.0f, 0.8f);

            user.getItemCooldownManager().set(this, COOLDOWN_TICKS);
            stack.damage(1, user, p -> p.sendToolBreakStatus(hand));
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.pixelpirates.wraithblade.ability").formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("tooltip.pixelpirates.wraithblade.lore").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
        super.appendTooltip(stack, world, tooltip, context);
    }
}
