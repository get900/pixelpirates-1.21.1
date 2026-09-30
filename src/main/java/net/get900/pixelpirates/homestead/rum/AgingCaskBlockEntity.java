package net.get900.pixelpirates.homestead.rum;

import net.get900.pixelpirates.homestead.HomesteadBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class AgingCaskBlockEntity extends BlockEntity {
    public static final int CAP = 4;
    private final int[] tier = new int[CAP];
    private final long[] since = new long[CAP];
    private int n;

    public AgingCaskBlockEntity(BlockPos pos, BlockState state) { super(HomesteadBlockEntities.AGING_CASK, pos, state); }

    public int count() { return n; }

    public boolean insert(int t, long now) {
        if (n >= CAP) return false;
        tier[n] = t;
        since[n] = now;
        n++;
        markDirty();
        return true;
    }

    public List<ItemStack> drain(long now) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(new ItemStack(Rum.item(Rum.aged(tier[i], now - since[i]))));
        n = 0;
        markDirty();
        return out;
    }

    public Text status(long now) {
        if (n == 0) return Text.literal("An empty cask. Lay down raw or aged rum to mellow it (up to " + CAP + " bottles).").formatted(Formatting.GRAY);
        MutableText t = Text.literal("Cask: ").formatted(Formatting.GOLD);
        String[] names = {"raw", "aged", "VINTAGE"};
        for (int i = 0; i < n; i++) {
            long age = now - since[i];
            int cur = Rum.aged(tier[i], age);
            String next = "";
            if (cur < 2) {
                long need = (tier[i] == 0 && cur == 0) ? Rum.AGE_1 - age : (tier[i] == 0 ? Rum.AGE_1 + Rum.AGE_2 - age : Rum.AGE_2 - age);
                next = " (" + Math.max(1, need / 1200) + " min)";
            }
            t.append(Text.literal((i > 0 ? ", " : "") + names[cur] + next).formatted(cur == 2 ? Formatting.LIGHT_PURPLE : cur == 1 ? Formatting.YELLOW : Formatting.WHITE));
        }
        return t;
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        nbt.putInt("N", n);
        nbt.putIntArray("Tier", tier);
        nbt.putLongArray("Since", since);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        n = Math.min(CAP, nbt.getInt("N"));
        int[] t = nbt.getIntArray("Tier");
        long[] s = nbt.getLongArray("Since");
        for (int i = 0; i < CAP; i++) { tier[i] = i < t.length ? t[i] : 0; since[i] = i < s.length ? s[i] : 0; }
    }
}
