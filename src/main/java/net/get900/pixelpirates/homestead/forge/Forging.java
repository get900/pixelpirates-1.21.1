package net.get900.pixelpirates.homestead.forge;

import net.get900.pixelpirates.homestead.HomesteadItems;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * THE FORGE's patterns (2026-10-02): a weapon laid on a FORGE ANVIL + the listed materials, struck with a Smith's Hammer
 * beside a lit FORGE HEARTH, becomes the result. The forged weapons exist nowhere else (no crafting recipe, no loot) -
 * each is the next step of a weapon you already carry, paid for in that phase's materials (Materials & Gear Ladder).
 * The anvil also MENDS: any damaged tool/weapon/armour + its own repair material, no XP cost.
 * One line per pattern; the Pattern Board and the anvil's hints read this list.
 */
public final class Forging {
    private Forging() {}

    public record Mat(Supplier<Item> item, int count) {}

    public record Pattern(Supplier<Item> base, Supplier<Item> result, String note, Mat... mats) {}

    public static final List<Pattern> PATTERNS = List.of(
            new Pattern(() -> ModItems.RUSTED_CUTLASS, () -> ModItems.CUTLASS, "restore the blade",
                    new Mat(() -> Items.IRON_INGOT, 2)),
            new Pattern(() -> ModItems.BOARDING_SABRE, () -> ModItems.CRABCLAW_SABRE, "every 3rd hit pinches",
                    new Mat(() -> ModItems.CRAB_SHELL, 4), new Mat(() -> Items.IRON_INGOT, 1)),
            new Pattern(() -> ModItems.NAVAL_RAPIER, () -> ModItems.PEARLGUARD_RAPIER, "parry + riposte",
                    new Mat(() -> ModItems.REEF_PEARL, 2), new Mat(() -> Items.GOLD_INGOT, 1)),
            new Pattern(() -> ModItems.CORSAIR_CUTLASS, () -> ModItems.PISTOL_CUTLASS, "a pistol in the guard",
                    new Mat(() -> HomesteadItems.FLINTLOCK_PISTOL, 1), new Mat(() -> ModItems.OBSIDIAN_SHARD, 2)),
            new Pattern(() -> ModItems.BOARDING_PIKE, () -> ModItems.OBSIDIAN_HALBERD, "cleaving sweep",
                    new Mat(() -> ModItems.OBSIDIAN_SHARD, 3), new Mat(() -> ModItems.BRIMSTONE, 2)),
            new Pattern(() -> ModItems.SOULRENDER, () -> ModItems.SOULREAVER, "stores the souls it reaps",
                    new Mat(() -> ModItems.LOST_SOUL, 1), new Mat(() -> ModItems.ECTOPLASM, 2)),
            new Pattern(() -> ModItems.KRAKEN_FANG, () -> ModItems.INKFANG, "blinding bite, vanish in ink",
                    new Mat(() -> ModItems.KRAKEN_INK, 2), new Mat(() -> ModItems.LUMINOUS_ICHOR, 1)));

    /** Hammer strikes a forging takes (fewer with bellows at the hearth) and a mending takes. */
    public static final int FORGE_STRIKES = 6, FORGE_STRIKES_BELLOWS = 4, MEND_STRIKES = 3;

    public static List<Pattern> patternsFor(Item base) {
        List<Pattern> out = new ArrayList<>();
        for (Pattern p : PATTERNS) if (p.base().get() == base) out.add(p);
        return out;
    }

    public static boolean isBase(Item i) { return !patternsFor(i).isEmpty(); }

    /** Can a stack of this go on the anvil as the piece being worked? */
    public static boolean workable(ItemStack s) { return isBase(s.getItem()) || s.isDamageable(); }

    public static int count(List<ItemStack> mats, Item item) {
        int n = 0;
        for (ItemStack s : mats) if (s.isOf(item)) n += s.getCount();
        return n;
    }

    /** The first pattern for this base whose materials are all on the anvil. */
    public static Pattern match(ItemStack base, List<ItemStack> mats) {
        for (Pattern p : patternsFor(base.getItem())) {
            boolean ok = true;
            for (Mat m : p.mats()) if (count(mats, m.item().get()) < m.count()) { ok = false; break; }
            if (ok) return p;
        }
        return null;
    }

    /** The repair material on the anvil for a damaged base, or null. */
    public static Item mendingMaterial(ItemStack base, List<ItemStack> mats) {
        if (!base.isDamageable() || !base.isDamaged()) return null;
        for (ItemStack m : mats) if (base.getItem().canRepair(base, m)) return m.getItem();
        return null;
    }

    public static void take(List<ItemStack> mats, Item item, int n) {
        for (ItemStack s : mats) {
            if (n <= 0) break;
            if (!s.isOf(item)) continue;
            int t = Math.min(n, s.getCount());
            s.decrement(t);
            n -= t;
        }
        mats.removeIf(ItemStack::isEmpty);
    }

    /** The forged piece: a fresh result that keeps the old blade's enchantments and name. */
    public static ItemStack forge(ItemStack base, Pattern p) {
        ItemStack out = new ItemStack(p.result().get());
        EnchantmentHelper.set(EnchantmentHelper.get(base), out);
        if (base.hasCustomName()) out.setCustomName(base.getName());
        return out;
    }

    /** Mend `base` with up to the available material: each piece restores a third of its durability. */
    public static int mend(ItemStack base, List<ItemStack> mats, Item mat) {
        int per = Math.max(1, (int) Math.ceil(base.getMaxDamage() / 3.0));
        int used = 0;
        while (base.getDamage() > 0 && count(mats, mat) > 0) {
            take(mats, mat, 1);
            base.setDamage(Math.max(0, base.getDamage() - per));
            used++;
        }
        return used;
    }

    public static MutableText describe(Pattern p) {
        MutableText t = Text.literal("").append(p.base().get().getName()).append(Text.literal(" + "));
        for (int i = 0; i < p.mats().length; i++) {
            Mat m = p.mats()[i];
            if (i > 0) t.append(Text.literal(", "));
            t.append(Text.literal(m.count() + " ")).append(m.item().get().getName());
        }
        return t.append(Text.literal(" -> ").formatted(Formatting.GRAY))
                .append(p.result().get().getName().copy().formatted(Formatting.GOLD))
                .append(Text.literal("  (" + p.note() + ")").formatted(Formatting.DARK_GRAY));
    }
}
