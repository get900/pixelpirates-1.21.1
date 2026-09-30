package net.get900.pixelpirates.homestead.tool;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.AxeItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.RecipeType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * REGION TOOLS (#24): pickaxe / axe / shovel / hoe in the three region materials, each with a perk -
 *  EMBER (volcanic ember): smelts what it mines (mixin/HomesteadDropsMixin).
 *  KRAKEN (kraken scale): mines at full speed underwater and while swimming (mixin/KrakenMiningMixin).
 *  BONE (cursed bone): Haste in the dark, and each block has a chance to shed soul XP.
 */
public final class RegionTools {
    private RegionTools() {}

    public enum Kind {
        EMBER("Smelts what it mines", Formatting.GOLD),
        KRAKEN("Full speed underwater", Formatting.AQUA),
        BONE("Haste in darkness; mining sheds soul XP", Formatting.DARK_AQUA);
        final String perk;
        final Formatting color;
        Kind(String perk, Formatting color) { this.perk = perk; this.color = color; }
    }

    public static final Map<Item, Kind> KIND = new IdentityHashMap<>();

    @Nullable
    public static Kind of(ItemStack s) { return s.isEmpty() ? null : KIND.get(s.getItem()); }

    static void tick(ItemStack stack, World world, Entity e, boolean selected) {
        if (!selected || world.isClient || !(e instanceof PlayerEntity p) || world.getTime() % 20 != 0) return;
        if (of(stack) == Kind.BONE && world.getLightLevel(LightType.BLOCK, p.getBlockPos()) <= 6 && world.getLightLevel(p.getBlockPos()) <= 7)
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 40, 0, true, false, true));
    }

    static void tooltip(ItemStack stack, List<Text> tooltip) {
        Kind k = of(stack);
        if (k != null) tooltip.add(Text.literal(k.perk).formatted(k.color));
    }

    /** EMBER: drops that have a smelting recipe come out smelted. */
    public static List<ItemStack> smelt(ServerWorld world, List<ItemStack> drops) {
        List<ItemStack> out = new ArrayList<>(drops.size());
        for (ItemStack d : drops) {
            var r = world.getRecipeManager().getFirstMatch(RecipeType.SMELTING, new SimpleInventory(d), world);
            if (r.isPresent()) {
                ItemStack o = r.get().getOutput(world.getRegistryManager()).copy();
                o.setCount(o.getCount() * d.getCount());
                out.add(o);
            } else out.add(d);
        }
        return out;
    }

    // ------------------------------------------------------------------ the four tool shapes
    public static class Pickaxe extends PickaxeItem {
        public Pickaxe(ToolMaterial m, Settings s) { super(m, -2, -2.8f, s); }
        @Override public void inventoryTick(ItemStack st, World w, Entity e, int slot, boolean sel) { tick(st, w, e, sel); }
        @Override public void appendTooltip(ItemStack st, @Nullable World w, List<Text> t, TooltipContext c) { tooltip(st, t); }
    }

    public static class Axe extends AxeItem {
        public Axe(ToolMaterial m, Settings s) { super(m, 1.0f, -3.1f, s); }
        @Override public void inventoryTick(ItemStack st, World w, Entity e, int slot, boolean sel) { tick(st, w, e, sel); }
        @Override public void appendTooltip(ItemStack st, @Nullable World w, List<Text> t, TooltipContext c) { tooltip(st, t); }
    }

    public static class Shovel extends ShovelItem {
        public Shovel(ToolMaterial m, Settings s) { super(m, -2.5f, -3.0f, s); }
        @Override public void inventoryTick(ItemStack st, World w, Entity e, int slot, boolean sel) { tick(st, w, e, sel); }
        @Override public void appendTooltip(ItemStack st, @Nullable World w, List<Text> t, TooltipContext c) { tooltip(st, t); }
    }

    public static class Hoe extends HoeItem {
        public Hoe(ToolMaterial m, Settings s) { super(m, -6, 0.0f, s); }
        @Override public void inventoryTick(ItemStack st, World w, Entity e, int slot, boolean sel) { tick(st, w, e, sel); }
        @Override public void appendTooltip(ItemStack st, @Nullable World w, List<Text> t, TooltipContext c) { tooltip(st, t); }
    }
}
