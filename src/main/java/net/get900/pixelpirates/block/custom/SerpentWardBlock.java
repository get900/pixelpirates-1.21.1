package net.get900.pixelpirates.block.custom;

import net.get900.pixelpirates.item.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * TIDEWARD STONE - the Sea Serpent's armour anchors. While a stone stands, the serpent keeps one
 * pearl scale plate (SeaSerpentEntity recounts the stones recorded at worldgen every second). Only a
 * Tidebreaker cracks one (~1.5 s); other tools make no progress, and explosions do nothing.
 */
public class SerpentWardBlock extends Block {
    public SerpentWardBlock(Settings settings) {
        super(settings);
    }

    @Override
    public float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
        if (player.isCreative()) return 1f;
        return player.getMainHandStack().isOf(ModItems.TIDEBREAKER) ? 1f / 30f : 0f;
    }

    @Override
    public void onBlockBreakStart(BlockState state, World world, BlockPos pos, PlayerEntity player) {
        if (!world.isClient && !player.getMainHandStack().isOf(ModItems.TIDEBREAKER) && !player.isCreative())
            player.sendMessage(Text.literal("The ward stone hums and shrugs off your blow - only a Tidebreaker can crack it.")
                    .formatted(Formatting.AQUA), true);
    }

    @Override
    public void afterBreak(World world, PlayerEntity player, BlockPos pos, BlockState state, @Nullable BlockEntity be, ItemStack tool) {
        super.afterBreak(world, player, pos, state, be, tool);
        if (tool.isOf(ModItems.TIDEBREAKER)) tool.damage(1, player, p -> p.sendToolBreakStatus(p.getActiveHand()));
        if (world instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0.15);
            sw.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.BLOCKS, 2.0f, 0.6f);
            sw.playSound(null, pos, SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 0.6f, 1.6f);
        }
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        world.addParticle(ParticleTypes.GLOW, pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(), 0, 0.03, 0);
        if (random.nextInt(3) == 0)
            world.addParticle(ParticleTypes.BUBBLE_COLUMN_UP, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0, 0.1, 0);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable BlockView world, List<Text> tooltip, TooltipContext options) {
        tooltip.add(Text.literal("Anchors the Sea Serpent's scales - break with a Tidebreaker").formatted(Formatting.GRAY));
    }
}
