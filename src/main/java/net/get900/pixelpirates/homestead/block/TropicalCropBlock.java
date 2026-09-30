package net.get900.pixelpirates.homestead.block;

import net.minecraft.block.CropBlock;
import net.minecraft.item.ItemConvertible;

import java.util.function.Supplier;

/** A farmland crop (age 0-7) whose seed item is supplied lazily (items register after blocks). */
public class TropicalCropBlock extends CropBlock {
    private final Supplier<ItemConvertible> seeds;

    public TropicalCropBlock(Settings settings, Supplier<ItemConvertible> seeds) {
        super(settings);
        this.seeds = seeds;
    }

    @Override
    protected ItemConvertible getSeedsItem() { return seeds.get(); }
}
