package net.get900.pixelpirates.homestead.furniture;

import net.get900.pixelpirates.homestead.HomesteadBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;

/** Holds the one item a treasure pedestal shows; synced to clients for the spinning render. */
public class DisplayBlockEntity extends BlockEntity {
    private ItemStack item = ItemStack.EMPTY;

    public DisplayBlockEntity(BlockPos pos, BlockState state) { super(HomesteadBlockEntities.DISPLAY, pos, state); }

    public ItemStack item() { return item; }

    public void setItem(ItemStack s) {
        item = s;
        markDirty();
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) { if (!item.isEmpty()) nbt.put("Item", item.writeNbt(new NbtCompound())); }

    @Override
    public void readNbt(NbtCompound nbt) { item = nbt.contains("Item") ? ItemStack.fromNbt(nbt.getCompound("Item")) : ItemStack.EMPTY; }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }

    @Override
    public NbtCompound toInitialChunkDataNbt() { return createNbt(); }
}
