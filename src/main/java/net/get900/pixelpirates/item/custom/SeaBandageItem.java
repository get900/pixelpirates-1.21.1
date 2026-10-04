package net.get900.pixelpirates.item.custom;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * SEA BANDAGE (early-game healing, 2026-09-30 playtest): hold right-click 1.5 s to bind a wound - heals 3 hearts at
 * once plus a short Regeneration, and stops any bleeding/poison. 4 s cooldown so it can't be spammed mid-fight.
 */
public class SeaBandageItem extends Item {
    public static final int USE_TICKS = 30, COOLDOWN = 80;

    public SeaBandageItem(Settings settings) { super(settings); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (user.getHealth() >= user.getMaxHealth() && !user.hasStatusEffect(StatusEffects.POISON)) {
            if (world.isClient) user.sendMessage(Text.literal("You're not hurt.").formatted(Formatting.GRAY), true);
            return TypedActionResult.fail(user.getStackInHand(hand));
        }
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.PLAYERS, 0.8f, 1.2f);
        return ItemUsage.consumeHeldItem(world, user, hand);
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (!world.isClient) {
            user.heal(6f);
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0));
            user.removeStatusEffect(StatusEffects.POISON);
            world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_WOOL_PLACE, SoundCategory.PLAYERS, 1f, 1.1f);
            ((ServerWorld) world).spawnParticles(ParticleTypes.HEART, user.getX(), user.getBodyY(0.7), user.getZ(), 4, 0.3, 0.3, 0.3, 0);
        }
        if (user instanceof PlayerEntity p) {
            p.getItemCooldownManager().set(this, COOLDOWN);
            if (!p.getAbilities().creativeMode) stack.decrement(1);
        }
        return stack;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) { return USE_TICKS; }

    @Override
    public UseAction getUseAction(ItemStack stack) { return UseAction.BOW; }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Hold right-click: heal 3 hearts + Regeneration, cure poison").formatted(Formatting.GRAY));
    }
}
