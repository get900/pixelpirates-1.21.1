package net.get900.pixelpirates.item.forged;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Base of the FORGED weapons (homestead/forge/Forging): blades that only come off a Forge Anvil. Each subclass adds its
 * ability; the tooltip says what it does and that it was forged.
 */
public abstract class ForgedBlade extends SwordItem {
    private final String[] lines;

    protected ForgedBlade(ToolMaterial m, int dmg, float speed, Settings s, String... lines) {
        super(m, dmg, speed, s);
        this.lines = lines;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        for (String l : lines) tooltip.add(Text.literal(l).formatted(Formatting.AQUA));
        extraTooltip(stack, tooltip);
        tooltip.add(Text.literal("Forged at the Wavebreak Forge").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
        super.appendTooltip(stack, world, tooltip, context);
    }

    protected void extraTooltip(ItemStack stack, List<Text> tooltip) {}

    /** A follow-up hit inside the target's invulnerability window (a pinch, a cleave) - reset it so the extra damage lands. */
    protected static void bonusHit(LivingEntity target, LivingEntity attacker, float dmg) {
        target.timeUntilRegen = 0;
        target.damage(attacker instanceof PlayerEntity p ? attacker.getDamageSources().playerAttack(p) : attacker.getDamageSources().mobAttack(attacker), dmg);
    }
}
