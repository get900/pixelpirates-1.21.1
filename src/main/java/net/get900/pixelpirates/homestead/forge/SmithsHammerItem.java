package net.get900.pixelpirates.homestead.forge;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** SMITH'S HAMMER: strikes a Forge Anvil (ForgeAnvilBlock). One durability per strike. */
public class SmithsHammerItem extends Item {
    public SmithsHammerItem(Settings s) { super(s); }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Right-click a Forge Anvil to strike").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Forge new blades, mend old ones - no XP cost").formatted(Formatting.DARK_GRAY));
    }
}
