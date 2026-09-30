package net.get900.pixelpirates.item.custom;

import net.get900.pixelpirates.entity.custom.DepthChargeEntity;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Throwable underwater charge (DepthChargeEntity). One per second. */
public class DepthChargeItem extends Item {
    public DepthChargeItem(Settings settings) { super(settings); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.8f, 0.5f);
        if (!world.isClient) {
            DepthChargeEntity c = new DepthChargeEntity(world, user);
            c.setItem(stack);
            c.setVelocity(user, user.getPitch(), user.getYaw(), 0.0f, user.isTouchingWater() ? 1.1f : 0.9f, 1.0f);
            world.spawnEntity(c);
        }
        user.getItemCooldownManager().set(this, 20);
        if (!user.getAbilities().creativeMode) stack.decrement(1);
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Sinks, then bursts after 2 s (or on contact). Harmless to players and blocks.").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Breaks the Abyssal King's concentration").formatted(Formatting.DARK_AQUA));
    }
}
