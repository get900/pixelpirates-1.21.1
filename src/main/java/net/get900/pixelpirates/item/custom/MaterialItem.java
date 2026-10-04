package net.get900.pixelpirates.item.custom;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A phase crafting material (Materials & Gear Ladder design): one grey line says where it comes from and what it makes. */
public class MaterialItem extends Item {
    private final String hint;

    public MaterialItem(Settings settings, String hint) {
        super(settings);
        this.hint = hint;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal(hint).formatted(Formatting.GRAY));
    }
}
