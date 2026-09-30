package net.get900.pixelpirates.homestead.fishing;

import net.get900.pixelpirates.homestead.HomesteadBlockEntities;
import net.get900.pixelpirates.homestead.HomesteadItems;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Nine slots of catch + (pots) a bait count. A catch every ~1-2 minutes while it sits in water. */
public class TrapBlockEntity extends BlockEntity implements net.minecraft.inventory.Inventory {
    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(9, ItemStack.EMPTY);
    int bait, cooldown = 1200;

    public TrapBlockEntity(BlockPos pos, BlockState state) { super(HomesteadBlockEntities.TRAP, pos, state); }

    static boolean isBait(ItemStack s) {
        return s.isOf(ModItems.CHUM) || s.isOf(Items.COD) || s.isOf(Items.SALMON) || Fishing.isFish(s.getItem());
    }

    boolean pot() { return getCachedState().getBlock() instanceof TrapBlock t && t.pot; }

    /** Water cells within 2 blocks: an open-water net fishes faster. */
    int openWater(World w) {
        int n = 0;
        for (BlockPos p : BlockPos.iterate(pos.add(-2, -1, -2), pos.add(2, 1, 2))) if (w.getFluidState(p).isIn(FluidTags.WATER)) n++;
        return n;
    }

    public static void tick(World world, BlockPos pos, BlockState state, TrapBlockEntity be) {
        if (!(world instanceof ServerWorld sw) || !state.get(TrapBlock.WATERLOGGED)) return;
        if (--be.cooldown > 0) return;
        boolean pot = be.pot();
        int water = be.openWater(world);
        be.cooldown = pot ? 1200 + world.random.nextInt(600) : Math.max(900, 2600 - water * 30) + world.random.nextInt(600);
        if (pot && be.bait <= 0) return;
        if (!pot && water < 12) return;
        var catches = Fishing.catchFish(sw, pos, pot ? Fishing.LOBSTER_POT : Fishing.FISH);
        if (catches.isEmpty()) return;
        SimpleInventory inv = new SimpleInventory(be.items.toArray(new ItemStack[0]));
        boolean stored = false;
        for (ItemStack c : catches) if (inv.canInsert(c)) { inv.addStack(c); stored = true; }
        if (!stored) return;                                                     // full: nothing caught, bait kept
        for (int i = 0; i < 9; i++) be.items.set(i, inv.getStack(i));
        if (pot) be.bait--;
        be.markDirty();
    }

    Text status() {
        int n = 0;
        for (ItemStack s : items) n += s.getCount();
        String what = pot() ? "Lobster pot: bait " + bait + "/8, " : "Fish trap: ";
        String note = !getCachedState().get(TrapBlock.WATERLOGGED) ? " - it needs to be in water" : pot() && bait == 0 ? " - no bait" : "";
        return Text.literal(what + n + " caught" + note).formatted(Formatting.AQUA);
    }

    // ------------------------------------------------------------------ Inventory
    @Override public int size() { return 9; }
    @Override public boolean isEmpty() { for (ItemStack s : items) if (!s.isEmpty()) return false; return true; }
    @Override public ItemStack getStack(int slot) { return items.get(slot); }
    @Override public ItemStack removeStack(int slot, int amount) { ItemStack s = Inventories.splitStack(items, slot, amount); markDirty(); return s; }
    @Override public ItemStack removeStack(int slot) { ItemStack s = Inventories.removeStack(items, slot); markDirty(); return s; }
    @Override public void setStack(int slot, ItemStack stack) { items.set(slot, stack); markDirty(); }
    @Override public boolean canPlayerUse(net.minecraft.entity.player.PlayerEntity player) { return false; }
    @Override public void clear() { items.clear(); }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        Inventories.writeNbt(nbt, items);
        nbt.putInt("Bait", bait);
        nbt.putInt("Cooldown", cooldown);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        Inventories.readNbt(nbt, items);
        bait = nbt.getInt("Bait");
        cooldown = nbt.contains("Cooldown") ? nbt.getInt("Cooldown") : 1200;
    }

    static void unused() { HomesteadItems.class.getName(); }
}
