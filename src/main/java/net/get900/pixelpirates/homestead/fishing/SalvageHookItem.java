package net.get900.pixelpirates.homestead.fishing;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** SALVAGE HOOK (#9): a grapnel on a line. Cast it anywhere and it drags up wreckage instead of fish (loot gameplay/salvage). */
public class SalvageHookItem extends FishingRodItem {
    public SalvageHookItem(Settings s) { super(s); }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Hauls up salvage instead of fish - best over wrecks").formatted(Formatting.GRAY));
    }
}
