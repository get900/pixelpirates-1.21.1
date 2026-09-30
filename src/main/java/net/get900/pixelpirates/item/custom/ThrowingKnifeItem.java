package net.get900.pixelpirates.item.custom;

import net.get900.pixelpirates.entity.ModEntities;
import net.get900.pixelpirates.entity.custom.ThrownKnifeEntity;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ThrowingKnifeItem extends Item {
    public ThrowingKnifeItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        world.playSound(null, user.getX(), user.getY(), user.getZ(),
                SoundEvents.ITEM_TRIDENT_THROW, SoundCategory.PLAYERS, 0.6f, 1.5f);
        if (!world.isClient) {
            ThrownKnifeEntity knife = new ThrownKnifeEntity(ModEntities.THROWN_KNIFE, world, user);
            knife.setItem(stack);
            knife.setVelocity(user, user.getPitch(), user.getYaw(), 0.0f, 2.2f, 0.6f);
            world.spawnEntity(knife);
        }
        user.getItemCooldownManager().set(this, 10);
        user.incrementStat(Stats.USED.getOrCreateStat(this));
        if (!user.getAbilities().creativeMode) {
            stack.decrement(1);
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.pixelpirates.throwing_knife.ability").formatted(Formatting.AQUA));
        super.appendTooltip(stack, world, tooltip, context);
    }
}
