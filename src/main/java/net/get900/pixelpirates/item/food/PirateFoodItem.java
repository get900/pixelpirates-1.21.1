package net.get900.pixelpirates.item.food;

import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A galley dish (PirateFoods). Eaten or drunk like vanilla food; afterwards it CURES the listed effects and hands back its
 * container (bowl / glass bottle) - one per serving, so a stack of 16 stews stays 15 stews + a bowl (vanilla StewItem
 * swaps the whole stack for a single bowl). The tooltip lists what it does, since vanilla shows nothing for food.
 */
public class PirateFoodItem extends Item {
    private final PirateFoods.Spec spec;

    public PirateFoodItem(Settings settings, PirateFoods.Spec spec) {
        super(settings);
        this.spec = spec;
    }

    public PirateFoods.Spec spec() { return spec; }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (!world.isClient) for (StatusEffect e : spec.cures()) user.removeStatusEffect(e);
        if (user instanceof ServerPlayerEntity sp && sp.getStatHandler().getStat(Stats.USED.getOrCreateStat(this)) == 0) {
            net.get900.pixelpirates.world.PirateXp.discovery(sp, net.get900.pixelpirates.world.PirateXp.DISH_FIRST_TASTE);
            sp.sendMessage(Text.literal("New dish! +" + net.get900.pixelpirates.world.PirateXp.DISH_FIRST_TASTE + " pirate XP").formatted(Formatting.AQUA), true);
        }
        if (spec.drink() && user instanceof ServerPlayerEntity sp) Criteria.CONSUME_ITEM.trigger(sp, stack);
        ItemStack rest = user.eatFood(world, stack);                     // hunger, effects, consume_item for food
        Item container = spec.container();
        if (container == null || !(user instanceof PlayerEntity p) || p.getAbilities().creativeMode) return rest;
        if (rest.isEmpty()) return new ItemStack(container);
        if (!p.getInventory().insertStack(new ItemStack(container))) p.dropItem(new ItemStack(container), false);
        return rest;
    }

    @Override
    public UseAction getUseAction(ItemStack stack) { return spec.drink() ? UseAction.DRINK : UseAction.EAT; }

    @Override
    public SoundEvent getDrinkSound() { return SoundEvents.ENTITY_GENERIC_DRINK; }

    @Override
    public SoundEvent getEatSound() { return spec.drink() ? SoundEvents.ENTITY_GENERIC_DRINK : SoundEvents.ENTITY_GENERIC_EAT; }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (spec.drink()) {
            if (!user.canConsume(spec.always())) return TypedActionResult.fail(user.getStackInHand(hand));
            return ItemUsage.consumeHeldItem(world, user, hand);
        }
        return super.use(world, user, hand);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        for (PirateFoods.Eff e : spec.effects()) {
            String lvl = e.amp() > 0 ? " " + "I".repeat(e.amp() + 1).replace("IIII", "IV") : "";
            tooltip.add(Text.translatable(e.effect().getTranslationKey()).append(lvl + " (" + e.seconds() / 60 + ":"
                    + String.format("%02d", e.seconds() % 60) + ")").formatted(e.effect().isBeneficial() ? Formatting.BLUE : Formatting.RED));
        }
        for (StatusEffect c : spec.cures())
            tooltip.add(Text.literal("Cures ").append(Text.translatable(c.getTranslationKey())).formatted(Formatting.DARK_GREEN));
        if (spec.note() != null) tooltip.add(Text.literal(spec.note()).formatted(Formatting.GRAY, Formatting.ITALIC));
    }
}
