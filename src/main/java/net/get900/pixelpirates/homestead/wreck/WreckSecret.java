package net.get900.pixelpirates.homestead.wreck;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.get900.pixelpirates.homestead.HomesteadBlocks;
import net.get900.pixelpirates.homestead.HomesteadState;
import net.get900.pixelpirates.homestead.trade.PortTraders;
import net.get900.pixelpirates.world.gen.PortCityLayout;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

/**
 * THE SECRET OF THE MERRY WREN (Beach Wreck #45, 2026-10-04). Captain Wren's chest lies buried in the silt under the
 * wreck's mainmast ({@link PortCityLayout#WRECK_CHEST}); the clues are the crew's camp sign and the captain's last words
 * in his flooded cabin. The first time EACH player opens that chest, Captain Wren's music box is waiting in it (a
 * per-player discovery, so every player finds their own; restamps refill the rest of the chest).
 */
public final class WreckSecret {
    private WreckSecret() {}

    public static final String KEY = "wreck_music_box";

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(MusicBoxBlock::tick);
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity sp) || !world.getRegistryKey().equals(PortTraders.DIM)) return ActionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            int[] c = PortCityLayout.WRECK_CHEST;
            if (pos.getX() != c[0] || pos.getY() != c[1] || pos.getZ() != c[2] || !(world.getBlockEntity(pos) instanceof ChestBlockEntity chest)) return ActionResult.PASS;
            if (!HomesteadState.get(sp.getServer()).discover(sp.getUuid(), KEY)) return ActionResult.PASS;
            ItemStack box = new ItemStack(HomesteadBlocks.WREN_MUSIC_BOX);
            int slot = -1;
            for (int i = 0; i < chest.size() && slot < 0; i++) if (chest.getStack(i).isEmpty()) slot = i;
            if (slot >= 0) chest.setStack(slot, box);
            else if (!sp.getInventory().insertStack(box)) sp.dropItem(box, false);
            sp.sendMessage(Text.literal("* Wrapped in oilcloth under the silt: Captain Wren's music box. It still plays.").formatted(Formatting.GOLD), false);
            world.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.7f, 1.2f);
            return ActionResult.PASS;                                                   // the chest still opens as normal
        });
    }
}
