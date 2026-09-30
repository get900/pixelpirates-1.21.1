package net.get900.pixelpirates.item.custom;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * TIDESHACKLE - a prismarine-forged shackle on a length of chain, kept in the Coral Temple (phase 2). Strike the
 * Chained Revenant's loose head with it and it is slung into a gibbet cage and locked away until his limbs are broken
 * (RevenantPartEntity#damage). 12 uses.
 */
public class TideshackleItem extends Item {
    public TideshackleItem(Settings settings) { super(settings); }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("A shackle forged to hold the drowned").formatted(Formatting.DARK_AQUA));
        tooltip.add(Text.literal("Strike the Chained Revenant's loose head to lock it in a gibbet cage").formatted(Formatting.GRAY));
    }
}
