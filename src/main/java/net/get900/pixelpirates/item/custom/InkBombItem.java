package net.get900.pixelpirates.item.custom;

import net.get900.pixelpirates.entity.custom.InkBombEntity;
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

/** Throwable kraken-ink bomb: a blinding cloud where it lands (InkBombEntity). */
public class InkBombItem extends Item {
    public InkBombItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.8f, 0.5f);
        if (!world.isClient) {
            InkBombEntity b = new InkBombEntity(world, user);
            b.setItem(stack);
            b.setVelocity(user, user.getPitch(), user.getYaw(), 0.0f, 1.2f, 1.0f);
            world.spawnEntity(b);
        }
        user.getItemCooldownManager().set(this, 30);
        if (!user.getAbilities().creativeMode) stack.decrement(1);
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Throw: a blinding ink cloud where it lands").formatted(Formatting.GRAY));
    }
}
