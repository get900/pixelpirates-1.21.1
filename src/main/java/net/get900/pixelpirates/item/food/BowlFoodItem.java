package net.get900.pixelpirates.item.food;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.world.World;

/**
 * A stackable bowl food: hands back ONE bowl per serving. Vanilla StewItem replaces the whole stack with a single bowl,
 * which ate 15 servings of every 16-stack of the homestead stews (fixed 2026-09-30).
 */
public class BowlFoodItem extends Item {
    public BowlFoodItem(Settings settings) { super(settings); }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        ItemStack rest = super.finishUsing(stack, world, user);
        if (!(user instanceof PlayerEntity p) || p.getAbilities().creativeMode) return rest;
        if (rest.isEmpty()) return new ItemStack(Items.BOWL);
        if (!p.getInventory().insertStack(new ItemStack(Items.BOWL))) p.dropItem(new ItemStack(Items.BOWL), false);
        return rest;
    }
}
