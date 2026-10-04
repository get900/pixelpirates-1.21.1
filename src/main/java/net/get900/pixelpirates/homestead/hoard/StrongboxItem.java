package net.get900.pixelpirates.homestead.hoard;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A SEALED STRONGBOX (tier 0 common, 1 rare, 2 legendary): crack it open at a Treasure Hoard (Strongboxes). */
public class StrongboxItem extends Item {
    private final int tier;

    public StrongboxItem(Settings settings, int tier) {
        super(settings);
        this.tier = tier;
    }

    public int tier() { return tier; }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Use it on a Treasure Hoard to crack it open").formatted(Formatting.GOLD));
        tooltip.add(Text.literal("The locksmith takes " + Strongboxes.cost(tier) + " pirate coins from the hoard").formatted(Formatting.GRAY));
    }
}
