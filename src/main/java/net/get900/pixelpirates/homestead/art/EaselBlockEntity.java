package net.get900.pixelpirates.homestead.art;

import net.get900.pixelpirates.homestead.HomesteadBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/** The canvas on an easel (on its lower block): shape, title, pixels (null = untouched). Synced so it shows on the easel. */
public class EaselBlockEntity extends BlockEntity {
    public Art.Size size = Art.Size.SQUARE;
    public String title = "";
    @Nullable public int[] pixels;
    /** A townsperson's id while they have a painting under way here (homestead/town) - "" = free. */
    public String owner = "";

    public EaselBlockEntity(BlockPos pos, BlockState state) { super(HomesteadBlockEntities.EASEL, pos, state); }

    public void set(Art.Size s, String t, @Nullable int[] px) {
        size = s; title = t; pixels = px;
        markDirty();
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putByte("Size", (byte) size.ordinal());
        nbt.putString("Title", title);
        if (pixels != null) nbt.putIntArray("Pixels", pixels);
        if (!owner.isEmpty()) nbt.putString("Npc", owner);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        size = Art.Size.of(nbt.getByte("Size"));
        title = nbt.getString("Title");
        pixels = nbt.contains("Pixels") ? nbt.getIntArray("Pixels") : null;
        owner = nbt.getString("Npc");
        if (pixels != null && pixels.length != size.pw * size.ph) pixels = null;
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }

    @Override
    public NbtCompound toInitialChunkDataNbt() { return createNbt(); }
}
