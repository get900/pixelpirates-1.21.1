package net.get900.pixelpirates.homestead.art;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.get900.pixelpirates.homestead.HomesteadBlocks;
import net.get900.pixelpirates.homestead.HomesteadItems;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;

/**
 * PAINTINGS (2026-10-04, the user: "an easel that players can actually paint on and then place those paintings down -
 * they don't do anything, just look cool"). Use an EASEL (2 blocks tall) to open the canvas: up to 32 x 32 pixels, any
 * colour, four shapes (1x1, 2x2, 2x1, 1x2 blocks). The work in progress stays on the easel (anyone can carry on with
 * it, and it shows on the easel). FINISH uses up one BLANK CANVAS and gives a PAINTING item; hang it on a wall like a
 * vanilla painting (CustomPaintingEntity). Pure decoration.
 */
public final class Art {
    private Art() {}

    public static final Identifier OPEN = new Identifier("pixelpirates", "art_open");
    public static final Identifier SAVE = new Identifier("pixelpirates", "art_save");

    /** The shapes: blocks wide x high, pixels wide x high (at most 32 x 32). */
    public enum Size {
        SQUARE(1, 1, 32, 32, "1 x 1"), LARGE(2, 2, 32, 32, "2 x 2"), WIDE(2, 1, 32, 16, "2 x 1 (wide)"), TALL(1, 2, 16, 32, "1 x 2 (tall)");
        public final int bw, bh, pw, ph;
        public final String label;
        Size(int bw, int bh, int pw, int ph, String label) { this.bw = bw; this.bh = bh; this.pw = pw; this.ph = ph; this.label = label; }
        public static Size of(int i) { return values()[Math.max(0, Math.min(values().length - 1, i))]; }
    }

    public static final int WHITE = 0xFFF4EEDC;                                     // fresh canvas

    public static int[] blank(Size s) { int[] p = new int[s.pw * s.ph]; Arrays.fill(p, WHITE); return p; }

    /** The painting item's data: {Size, Title, Author, Pixels}. */
    public static NbtCompound art(ItemStack painting) { return painting.getOrCreateSubNbt("Art"); }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(SAVE, (server, player, handler, buf, sender) -> {
            BlockPos pos = buf.readBlockPos();
            int size = buf.readByte();
            String title = buf.readString(32);
            int[] px = buf.readIntArray(32 * 32);
            boolean finish = buf.readBoolean();
            server.execute(() -> save(player, pos, Size.of(size), title, px, finish));
        });
    }

    /** The easel was used: send its canvas to the player's screen. */
    public static void open(ServerPlayerEntity p, BlockPos lower, EaselBlockEntity be) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(lower);
        buf.writeByte(be.size.ordinal());
        buf.writeString(be.title, 32);
        buf.writeIntArray(be.pixels == null ? blank(be.size) : be.pixels);
        buf.writeBoolean(p.isCreative() || p.getInventory().count(HomesteadItems.BLANK_CANVAS) > 0);
        ServerPlayNetworking.send(p, OPEN, buf);
    }

    private static void save(ServerPlayerEntity p, BlockPos pos, Size size, String title, int[] px, boolean finish) {
        if (p.squaredDistanceTo(Vec3d.ofCenter(pos)) > 8 * 8 || !p.getServerWorld().getBlockState(pos).isOf(HomesteadBlocks.EASEL)) return;
        if (!(p.getServerWorld().getBlockEntity(pos) instanceof EaselBlockEntity be)) return;
        if (px.length != size.pw * size.ph) return;
        for (int i = 0; i < px.length; i++) px[i] |= 0xFF000000;                       // no see-through paint
        title = title.replaceAll("[^\\x20-\\x7E]", "").trim();
        be.owner = "";                                                                  // a player's brush beats a townsperson's
        if (!finish) { be.set(size, title, px); return; }
        if (!p.isCreative()) {
            int slot = -1;
            for (int i = 0; i < p.getInventory().size() && slot < 0; i++) if (p.getInventory().getStack(i).isOf(HomesteadItems.BLANK_CANVAS)) slot = i;
            if (slot < 0) { p.sendMessage(Text.literal("You need a Blank Canvas to finish a painting.").formatted(Formatting.RED), true); be.set(size, title, px); return; }
            p.getInventory().getStack(slot).decrement(1);
        }
        ItemStack art = new ItemStack(HomesteadItems.PAINTING);
        NbtCompound n = art(art);
        n.putByte("Size", (byte) size.ordinal());
        n.putString("Title", title.isEmpty() ? "Untitled" : title);
        n.putString("Author", p.getName().getString());
        n.putIntArray("Pixels", px);
        if (!p.getInventory().insertStack(art)) p.dropItem(art, false);
        be.set(size, "", null);                                                         // a fresh canvas on the easel
        p.sendMessage(Text.literal("* Finished \"" + n.getString("Title") + "\" - hang it on a wall.").formatted(Formatting.GOLD), true);
        p.getServerWorld().playSound(null, pos, SoundEvents.ENTITY_PAINTING_PLACE, SoundCategory.BLOCKS, 1f, 1f);
    }
}
