package net.get900.pixelpirates.item.custom;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GrogItem extends Item {
    public GrogItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);

        if (!world.isClient) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 15 * 20, 1));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10 * 20, 0));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 10 * 20, 1));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 10 * 20, 0));

            if (!player.getAbilities().creativeMode) {
                stack.decrement(1);
            }

            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 1.0F, 1.0F);
        }

        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        if (Screen.hasShiftDown()) {
            tooltip.add(Text.translatable("tooltip.pixelpirates.grog_item.shifttooltip"));
        } else {
            tooltip.add(Text.translatable("tooltip.pixelpirates.grog_item.tooltip"));
        }
        super.appendTooltip(stack, world, tooltip, context);
    }
}
