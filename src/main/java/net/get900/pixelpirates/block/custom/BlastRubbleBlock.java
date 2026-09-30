package net.get900.pixelpirates.block.custom;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Collapsed rock and timber sealing a structure's vault. Unbreakable by tools and immune to
 * creepers/TNT/cannons (bedrock-level blast resistance) - only thrown dynamite clears it
 * ({@link net.get900.pixelpirates.entity.custom.DynamiteEntity#clearRubble}). The dynamite gate for
 * structure progression.
 */
public class BlastRubbleBlock extends Block {
    public BlastRubbleBlock(Settings settings) {
        super(settings);
    }

    @Override
    public void onBlockBreakStart(BlockState state, World world, BlockPos pos, PlayerEntity player) {
        if (!world.isClient && !player.isCreative())
            player.sendMessage(Text.literal("The rubble won't budge - it needs a blast of dynamite.").formatted(Formatting.GOLD), true);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable BlockView world, List<Text> tooltip, TooltipContext options) {
        tooltip.add(Text.literal("Only dynamite can clear it").formatted(Formatting.GRAY));
    }
}
