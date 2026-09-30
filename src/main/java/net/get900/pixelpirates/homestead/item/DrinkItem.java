package net.get900.pixelpirates.homestead.item;

import net.minecraft.advancement.criterion.Criteria;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

import java.util.function.Consumer;

/** A bottled drink: drinks like a potion (food component supplies hunger + effects), returns the glass bottle. */
public class DrinkItem extends Item {
    private final Consumer<LivingEntity> onDrink;

    public DrinkItem(Settings settings, Consumer<LivingEntity> onDrink) {
        super(settings);
        this.onDrink = onDrink;
    }

    public DrinkItem(Settings settings) { this(settings, e -> { }); }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (user instanceof ServerPlayerEntity sp) {
            Criteria.CONSUME_ITEM.trigger(sp, stack);
            sp.incrementStat(Stats.USED.getOrCreateStat(this));
        }
        if (!world.isClient) onDrink.accept(user);
        ItemStack rest = stack.isFood() ? user.eatFood(world, stack) : stack;
        if (!stack.isFood() && !(user instanceof PlayerEntity p && p.getAbilities().creativeMode)) stack.decrement(1);
        if (user instanceof PlayerEntity p && !p.getAbilities().creativeMode) {
            if (rest.isEmpty()) return new ItemStack(Items.GLASS_BOTTLE);
            p.getInventory().insertStack(new ItemStack(Items.GLASS_BOTTLE));
        }
        return rest;
    }

    @Override
    public int getMaxUseTime(ItemStack stack) { return 32; }

    @Override
    public UseAction getUseAction(ItemStack stack) { return UseAction.DRINK; }

    @Override
    public SoundEvent getDrinkSound() { return SoundEvents.ENTITY_GENERIC_DRINK; }

    @Override
    public SoundEvent getEatSound() { return SoundEvents.ENTITY_GENERIC_DRINK; }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return ItemUsage.consumeHeldItem(world, user, hand);
    }
}
