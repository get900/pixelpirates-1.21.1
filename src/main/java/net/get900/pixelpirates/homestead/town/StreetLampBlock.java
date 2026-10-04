package net.get900.pixelpirates.homestead.town;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.LanternBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

/**
 * THE STREET LAMP (2026-10-04, round 3): a lantern that is dark by day and lit at night. Ginny Tallow the lamplighter lights
 * the ones along her dusk round (TownLife.lightLamps - she also turns the old lamppost lanterns of an older island into
 * street lamps as she passes). By morning they go out on their own (random ticks); any she missed light themselves late in
 * the night, so no street stays dark till dawn. The island's lampposts are built with them (PortCityLayout.lamppost).
 */
public class StreetLampBlock extends LanternBlock {
    public static final BooleanProperty LIT = BooleanProperty.of("lit");

    public StreetLampBlock(Settings s) {
        super(s.luminance(st -> st.get(LIT) ? 15 : 0).ticksRandomly());
        setDefaultState(getDefaultState().with(LIT, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> b) {
        super.appendProperties(b);
        b.add(LIT);
    }

    @Override
    public void randomTick(BlockState state, ServerWorld w, BlockPos pos, Random random) {
        int t = (int) Math.floorMod(w.getTimeOfDay(), 24000L);
        boolean day = t < 11800 || t >= 23400;
        if (day && state.get(LIT) && random.nextInt(2) == 0) w.setBlockState(pos, state.with(LIT, false), 3);
        else if (!day && t >= 15000 && !state.get(LIT) && random.nextInt(3) == 0) w.setBlockState(pos, state.with(LIT, true), 3);
    }
}
