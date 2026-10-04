package net.get900.pixelpirates.item.forged;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterial;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PEARLGUARD RAPIER (P2, Naval Rapier + reef pearls + gold): right-click = EN GARDE for 0.75 s. A melee blow in that window
 * is turned aside (a boss's is halved - SkillEffects.allowDamage) and the attacker is thrown back with Weakness. 3 s cooldown.
 */
public class PearlguardRapierItem extends ForgedBlade {
    private static final Map<UUID, Long> GUARD = new HashMap<>();
    private static final int WINDOW = 15, COOLDOWN = 60;

    public PearlguardRapierItem(ToolMaterial m, int dmg, float speed, Settings s) {
        super(m, dmg, speed, s, "Right-click: En Garde - parry the next melee blow (0.75 s)", "A parry throws the attacker back, weakened");
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient) {
            GUARD.put(user.getUuid(), world.getTime() + WINDOW);
            world.playSound(null, user.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.PLAYERS, 1f, 1.6f);
            user.getItemCooldownManager().set(this, COOLDOWN);
        }
        user.swingHand(hand);
        return TypedActionResult.success(stack, world.isClient);
    }

    /** True if `p` is en garde right now (and spends the guard). */
    public static boolean parry(PlayerEntity p) {
        Long until = GUARD.get(p.getUuid());
        if (until == null || p.getWorld().getTime() > until) return false;
        GUARD.remove(p.getUuid());
        return true;
    }

    /** The riposte after a parried blow from `attacker`. */
    public static void riposte(PlayerEntity p, LivingEntity attacker) {
        attacker.takeKnockback(0.9, p.getX() - attacker.getX(), p.getZ() - attacker.getZ());
        attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 0));
        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1f, 1.5f);
        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1f, 1.2f);
        if (p.getWorld() instanceof ServerWorld sw)
            sw.spawnParticles(ParticleTypes.ENCHANTED_HIT, p.getX(), p.getBodyY(0.6), p.getZ(), 14, 0.4, 0.3, 0.4, 0.2);
    }
}
