package net.get900.pixelpirates.item.custom;

import net.get900.pixelpirates.entity.custom.ChumEntity;
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

/** Throwable bait (ChumEntity). CHUM draws the Bloodfin for a moment; its own FLESH holds it longer but heals it. */
public class ChumItem extends Item {
    private final boolean flesh;

    public ChumItem(Settings settings, boolean flesh) {
        super(settings);
        this.flesh = flesh;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_SLIME_SQUISH_SMALL, SoundCategory.PLAYERS, 1.0f, 0.6f);
        if (!world.isClient) {
            ChumEntity c = new ChumEntity(world, user, flesh);
            c.setItem(stack);
            c.setVelocity(user, user.getPitch(), user.getYaw(), 0.0f, 1.0f, 1.0f);
            world.spawnEntity(c);
        }
        user.getItemCooldownManager().set(this, 20);
        if (!user.getAbilities().creativeMode) stack.decrement(1);
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Throw into the sea: blood in the water draws the Bloodfin to feed").formatted(Formatting.GRAY));
        if (flesh) tooltip.add(Text.literal("Holds it longer - but its own flesh heals it").formatted(Formatting.DARK_RED));
    }
}
