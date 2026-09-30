package net.get900.pixelpirates.block.custom;

import net.minecraft.block.Block;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Placed inside a ship hull at the desired interior waterline level.
 * At assembly/spawn, ShipHelmBlock/ShipSpawner compute a per-ship buoyancy target
 * so this block sits exactly at sea level (WATER_Y) when floating.
 * Players walk on top of it — the hull interior stays dry above sea level.
 */
public class ShipWaterlineBlock extends Block {

    public ShipWaterlineBlock(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable BlockView world, List<Text> tooltip, TooltipContext options) {
        tooltip.add(Text.literal("§7Place as the floor inside your ship's hull."));
        tooltip.add(Text.literal("§7The ship floats so this block sits at sea level."));
    }
}
