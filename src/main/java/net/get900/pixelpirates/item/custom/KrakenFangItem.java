package net.get900.pixelpirates.item.custom;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Abyssal dagger carved from a kraken tooth — lightning-fast, venomous bite.
 */
public class KrakenFangItem extends SwordItem {
    public KrakenFangItem(ToolMaterial material, int attackDamage, float attackSpeed, Settings settings) {
        super(material, attackDamage, attackSpeed, settings);
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!target.getWorld().isClient) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 80, 1), attacker);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 0), attacker);
            if (target.getWorld() instanceof ServerWorld serverWorld) {
                serverWorld.spawnParticles(ParticleTypes.SQUID_INK,
                        target.getX(), target.getBodyY(0.5), target.getZ(),
                        5, 0.25, 0.25, 0.25, 0.02);
            }
        }
        return super.postHit(stack, target, attacker);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.pixelpirates.kraken_fang.ability").formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("tooltip.pixelpirates.kraken_fang.lore").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
        super.appendTooltip(stack, world, tooltip, context);
    }
}
