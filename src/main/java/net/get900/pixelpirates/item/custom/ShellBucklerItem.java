package net.get900.pixelpirates.item.custom;

import net.get900.pixelpirates.item.ModItems;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;

/** SHELL BUCKLER (phase 1): a small round shield of crab shell on a driftwood frame. Blocks like a shield; repaired with
 *  crab shell. (It is drawn as a flat item - vanilla's 3D shield renderer only handles minecraft:shield.) */
public class ShellBucklerItem extends ShieldItem {
    public ShellBucklerItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean canRepair(ItemStack stack, ItemStack ingredient) {
        return ingredient.isOf(ModItems.CRAB_SHELL);
    }

    @Override
    public String getTranslationKey() {
        return "item.pixelpirates.shell_buckler";       // ShieldItem would otherwise append the banner colour
    }

    @Override
    public String getTranslationKey(ItemStack stack) {
        return getTranslationKey();
    }
}
