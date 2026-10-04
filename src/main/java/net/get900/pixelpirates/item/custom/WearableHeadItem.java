package net.get900.pixelpirates.item.custom;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Equipment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A trophy worn on the head (vanilla HeadFeatureRenderer draws its item model): right-click to put it on. */
public class WearableHeadItem extends Item implements Equipment {
    private final String lore;

    public WearableHeadItem(Settings settings, String lore) {
        super(settings);
        this.lore = lore;
    }

    @Override
    public EquipmentSlot getSlotType() { return EquipmentSlot.HEAD; }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        return this.equipAndSwap(this, world, user, hand);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal(lore).formatted(Formatting.DARK_RED, Formatting.ITALIC));
        tooltip.add(Text.literal("Wear it on your head").formatted(Formatting.GRAY));
    }
}
