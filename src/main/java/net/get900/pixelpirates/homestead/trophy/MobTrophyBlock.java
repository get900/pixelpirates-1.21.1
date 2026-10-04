package net.get900.pixelpirates.homestead.trophy;

import net.get900.pixelpirates.homestead.furniture.FurnitureBlock;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * MOB TROPHY PLAQUE (2026-10-01, user note: "a trophy of a ghost shark - the ghost shark model on a plaque, maybe other
 * mobs too"). A wooden plaque (static block model) with the mob's OWN GeckoLib model mounted on it, playing its idle
 * loop (MobTrophyBlockEntity + client MobTrophyRenderer). One block per mob; the per-mob framing lives in {@link Mount}.
 * Rare drop (2%) when you kill that creature; the Fishmonger buys them.
 */
public class MobTrophyBlock extends FurnitureBlock implements BlockEntityProvider {
    /** How a mob sits on its plaque. scale = model px -> block px; (cx, cy, cz) = model centre in px; depth = block offset
     *  from the block centre toward the wall; profile = turned side-on. idle = the animation to loop. */
    public record Mount(String mob, float scale, float cx, float cy, float cz, float depth, boolean profile, String idle) {}

    private final Mount mount;

    public MobTrophyBlock(Settings s, Mount mount) {
        super(s, false, new double[]{0, 2, 13, 16, 14, 16});
        this.mount = mount;
    }

    public Mount mount() { return mount; }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new MobTrophyBlockEntity(pos, state); }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable BlockView world, List<Text> tooltip, TooltipContext options) {
        tooltip.add(Text.literal("A trophy for the wall").formatted(Formatting.GOLD));
    }
}
