package net.get900.pixelpirates.homestead.forge;

import net.get900.pixelpirates.homestead.HomesteadBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** What lies on a Forge Anvil: the piece being worked, up to four material stacks, and the strikes so far. Synced for the renderer. */
public class ForgeAnvilBlockEntity extends BlockEntity {
    public static final int MAX_MATS = 4;
    ItemStack piece = ItemStack.EMPTY;
    final List<ItemStack> mats = new ArrayList<>();
    int strikes;

    public ForgeAnvilBlockEntity(BlockPos pos, BlockState state) { super(HomesteadBlockEntities.FORGE_ANVIL, pos, state); }

    public ItemStack piece() { return piece; }

    public List<ItemStack> mats() { return mats; }

    void changed() {
        markDirty();
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        if (!piece.isEmpty()) nbt.put("Piece", piece.writeNbt(new NbtCompound()));
        NbtList l = new NbtList();
        for (ItemStack s : mats) l.add(s.writeNbt(new NbtCompound()));
        nbt.put("Mats", l);
        nbt.putInt("Strikes", strikes);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        piece = nbt.contains("Piece") ? ItemStack.fromNbt(nbt.getCompound("Piece")) : ItemStack.EMPTY;
        mats.clear();
        for (NbtElement e : nbt.getList("Mats", NbtElement.COMPOUND_TYPE)) {
            ItemStack s = ItemStack.fromNbt((NbtCompound) e);
            if (!s.isEmpty()) mats.add(s);
        }
        strikes = nbt.getInt("Strikes");
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }

    @Override
    public NbtCompound toInitialChunkDataNbt() { return createNbt(); }
}
