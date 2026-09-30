package net.get900.pixelpirates.item.custom;

import net.get900.pixelpirates.item.BossArmor;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** One piece of a BOSS SET (item/BossArmor): tier 1..5, fireproof, Boss Ward + the set bonus in its tooltip. */
public class BossArmorItem extends ArmorItem {
    private static final String[][] BONUS = {{},
            {"Blast-Stitched:", " boss explosions -40%", " boss fire burns half as long"},
            {"Quenched & Warded:", " never set alight near a boss", " boss magic -30%", " dolphin's grace in water"},
            {"Slippery as the Tide:", " twist free of any grab after 1.4 s", " boss pulls -40%", " immune to boss slowness"},
            {"Unshackled:", " no wither, darkness or blindness near bosses", " boss nausea lasts 1 s", " falls near a boss -50%"},
            {"Heart of the Tide Father:", " LAST STAND - a boss hit that would drop you", "   under 25% grants Absorption III (90 s cd)",
                    " boss magic -30%", " breathing, grace + night vision in water"}};
    private final int tier;

    public BossArmorItem(ArmorMaterial material, Type type, int tier, Settings settings) {
        super(material, type, settings.fireproof());
        this.tier = tier;
    }

    public int tier() { return tier; }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal(BossArmor.SET_NAMES[tier] + " (boss set " + "I II III IV V".split(" ")[tier - 1] + ")").formatted(Formatting.GOLD));
        tooltip.add(Text.literal("Boss Ward: -" + Math.round(BossArmor.WARD[tier] * 100) + "% damage from bosses (all types)").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("Full set - " + BONUS[tier][0]).formatted(Formatting.DARK_AQUA));
        for (int i = 1; i < BONUS[tier].length; i++) tooltip.add(Text.literal(BONUS[tier][i]).formatted(Formatting.GRAY));
        super.appendTooltip(stack, world, tooltip, context);
    }
}
