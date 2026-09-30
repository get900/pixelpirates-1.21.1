package net.get900.pixelpirates.block.custom;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;

/**
 * FLESH VEIN - the glowing vessels of the Titan's Chest (Abyssal Heart, boss 9/10). The Heart flares every one of them
 * on each beat (LIT) and lets them fade a moment later, so the whole ribcage pulses with its rhythm; they go dark while
 * the heart is in arrest. The Heart sets the state itself (no neighbour updates), so nothing else ever changes it.
 */
public class FleshVeinBlock extends Block {
    public static final BooleanProperty LIT = BooleanProperty.of("lit");

    public FleshVeinBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(LIT, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }
}
