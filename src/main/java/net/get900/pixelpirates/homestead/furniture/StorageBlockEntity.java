package net.get900.pixelpirates.homestead.furniture;

import net.get900.pixelpirates.homestead.HomesteadBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

/** Chest-like storage: the SEA CHEST (54 slots, one block) and the CARGO CRATE (27). */
public class StorageBlockEntity extends LootableContainerBlockEntity {
    private DefaultedList<ItemStack> items;
    private final int rows;

    public StorageBlockEntity(BlockPos pos, BlockState state) {
        super(HomesteadBlockEntities.STORAGE, pos, state);
        this.rows = state.getBlock() instanceof StorageBlock sb ? sb.rows : 3;
        this.items = DefaultedList.ofSize(rows * 9, ItemStack.EMPTY);
    }

    @Override
    protected DefaultedList<ItemStack> getInvStackList() { return items; }

    @Override
    protected void setInvStackList(DefaultedList<ItemStack> list) { this.items = list; }

    @Override
    protected Text getContainerName() { return getCachedState().getBlock().getName(); }

    @Override
    protected ScreenHandler createScreenHandler(int syncId, PlayerInventory inv) {
        return rows == 6 ? GenericContainerScreenHandler.createGeneric9x6(syncId, inv, this) : GenericContainerScreenHandler.createGeneric9x3(syncId, inv, this);
    }

    @Override
    public int size() { return rows * 9; }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        if (!serializeLootTable(nbt)) Inventories.writeNbt(nbt, items);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        items = DefaultedList.ofSize(size(), ItemStack.EMPTY);
        if (!deserializeLootTable(nbt)) Inventories.readNbt(nbt, items);
    }

    public boolean isEmptyInv() {
        for (ItemStack s : items) if (!s.isEmpty()) return false;
        return true;
    }
}
