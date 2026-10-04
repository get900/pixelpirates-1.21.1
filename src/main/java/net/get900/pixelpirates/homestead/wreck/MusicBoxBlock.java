package net.get900.pixelpirates.homestead.wreck;

import net.get900.pixelpirates.homestead.furniture.FurnitureBlock;
import net.minecraft.block.BlockState;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * CAPTAIN WREN'S MUSIC BOX (2026-10-04): the easter egg of the Beach Wreck (#45, see {@link WreckSecret}) - a little
 * brass-cornered box that plays "What Shall We Do with the Drunken Sailor" (traditional, public domain) on the note-block
 * bell when wound (right-click). Purely decorative; not craftable - one per player, from the wreck.
 */
public class MusicBoxBlock extends FurnitureBlock {
    public MusicBoxBlock(Settings s) { super(s, true, new double[]{3, 0, 4, 13, 7, 12}, new double[]{3, 7, 11, 13, 15, 12}); }

    /** {note-block note 0-24 (F#3 = 0), length in ticks}. D dorian: D 8, E 10, F 11, G 13, A 15, B 17, C 18, D 20. */
    private static final int[][] SHANTY = {
            {15, 6}, {15, 3}, {15, 3}, {15, 6}, {15, 3}, {15, 3}, {15, 6}, {8, 6}, {11, 6}, {15, 6},
            {13, 6}, {13, 3}, {13, 3}, {13, 6}, {13, 3}, {13, 3}, {13, 6}, {6, 6}, {10, 6}, {13, 6},
            {15, 6}, {15, 3}, {15, 3}, {15, 6}, {15, 3}, {15, 3}, {15, 6}, {17, 6}, {18, 6}, {20, 6},
            {18, 6}, {15, 3}, {13, 3}, {10, 6}, {8, 6}, {8, 12},
            // the chorus: "Way hay and up she rises" x3, "early in the morning"
            {15, 12}, {15, 6}, {15, 3}, {15, 3}, {15, 6}, {8, 6}, {11, 6}, {15, 6},
            {13, 12}, {13, 6}, {13, 3}, {13, 3}, {13, 6}, {6, 6}, {10, 6}, {13, 6},
            {15, 12}, {15, 6}, {15, 3}, {15, 3}, {15, 6}, {17, 6}, {18, 6}, {20, 6},
            {18, 6}, {15, 3}, {13, 3}, {10, 6}, {8, 6}, {8, 12}};

    private record Note(ServerWorld world, BlockPos pos, int note, long at) {}
    private static final List<Note> QUEUE = new ArrayList<>();

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(world instanceof ServerWorld sw)) return ActionResult.SUCCESS;
        for (Note n : QUEUE) if (n.pos.equals(pos) && n.world == sw) return ActionResult.SUCCESS;       // already playing
        sw.playSound(null, pos, SoundEvents.BLOCK_CHAIN_STEP, SoundCategory.BLOCKS, 0.6f, 1.8f);          // winding the key
        long t = sw.getServer().getTicks() + 12;
        for (int[] n : SHANTY) { QUEUE.add(new Note(sw, pos.toImmutable(), n[0], t)); t += n[1]; }
        return ActionResult.SUCCESS;
    }

    public static void tick(MinecraftServer server) {
        if (QUEUE.isEmpty()) return;
        long now = server.getTicks();
        for (Iterator<Note> it = QUEUE.iterator(); it.hasNext(); ) {
            Note n = it.next();
            if (n.at > now) continue;
            it.remove();
            if (!(n.world.getBlockState(n.pos).getBlock() instanceof MusicBoxBlock)) continue;
            n.world.playSound(null, n.pos, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.RECORDS, 0.8f, (float) Math.pow(2, (n.note - 12) / 12.0));
            n.world.spawnParticles(ParticleTypes.NOTE, n.pos.getX() + 0.5, n.pos.getY() + 0.8, n.pos.getZ() + 0.5, 0, n.note / 24.0, 0, 0, 1);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable BlockView world, List<Text> tooltip, TooltipContext options) {
        tooltip.add(Text.literal("Found in the wreck of the Merry Wren").formatted(Formatting.GOLD, Formatting.ITALIC));
        tooltip.add(Text.literal("Right-click to wind it").formatted(Formatting.GRAY));
    }
}
