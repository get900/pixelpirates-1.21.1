package net.get900.pixelpirates.homestead.rum;

import net.get900.pixelpirates.homestead.HomesteadBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class RumStillBlockEntity extends BlockEntity {
    public static final int CAP = 8, TICKS_PER_BOTTLE = 400;
    public int molasses, rum, progress;

    public RumStillBlockEntity(BlockPos pos, BlockState state) { super(HomesteadBlockEntities.RUM_STILL, pos, state); }

    /** Something hot directly under the still. */
    public static boolean heated(World w, BlockPos pos) {
        BlockState b = w.getBlockState(pos.down());
        if (b.isIn(BlockTags.CAMPFIRES)) return b.contains(CampfireBlock.LIT) && b.get(CampfireBlock.LIT);
        return b.isIn(BlockTags.FIRE) || b.isOf(Blocks.MAGMA_BLOCK) || w.getFluidState(pos.down()).isIn(FluidTags.LAVA);
    }

    public static void tick(World w, BlockPos pos, BlockState state, RumStillBlockEntity be) {
        boolean working = be.molasses > 0 && be.rum < CAP && heated(w, pos);
        if (working && ++be.progress >= TICKS_PER_BOTTLE) {
            be.progress = 0;
            be.molasses--;
            be.rum++;
            be.markDirty();
        }
        if (state.get(RumStillBlock.LIT) != working) w.setBlockState(pos, state.with(RumStillBlock.LIT, working), 3);
    }

    public Text status() {
        MutableText t = Text.literal("Molasses " + molasses + "/" + CAP + "  |  Rum " + rum + "/" + CAP).formatted(Formatting.GOLD);
        if (world != null && !heated(world, pos)) t.append(Text.literal("  - needs a fire beneath").formatted(Formatting.RED));
        else if (molasses > 0 && rum < CAP) t.append(Text.literal("  - distilling " + (progress * 100 / TICKS_PER_BOTTLE) + "%").formatted(Formatting.YELLOW));
        if (rum > 0) t.append(Text.literal("  (use glass bottles to draw it off)").formatted(Formatting.GRAY));
        return t;
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        nbt.putInt("Molasses", molasses);
        nbt.putInt("Rum", rum);
        nbt.putInt("Progress", progress);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        molasses = nbt.getInt("Molasses");
        rum = nbt.getInt("Rum");
        progress = nbt.getInt("Progress");
    }
}
