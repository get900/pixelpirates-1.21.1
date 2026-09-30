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
 * The only tool that can crack a Tideward Stone (SerpentWardBlock) - each stone smashed strips a
 * pearl scale plate off the Sea Serpent. Found in the Tidewater Shrine vault (sealed by blast rubble,
 * so dynamite comes first). Each stone costs one point of durability.
 */
public class TidebreakerItem extends Item {
    public TidebreakerItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.pixelpirates.tidebreaker").formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("tooltip.pixelpirates.tidebreaker.lore").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
    }
}
