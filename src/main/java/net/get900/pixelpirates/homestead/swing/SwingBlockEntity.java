package net.get900.pixelpirates.homestead.swing;

import net.get900.pixelpirates.homestead.HomesteadBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;

/** Only there so SwingRenderer can draw the moving seat (no data of its own). */
public class SwingBlockEntity extends BlockEntity {
    public SwingBlockEntity(BlockPos pos, BlockState state) { super(HomesteadBlockEntities.SWING, pos, state); }
}
