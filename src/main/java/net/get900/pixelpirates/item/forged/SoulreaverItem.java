package net.get900.pixelpirates.item.forged;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterial;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * SOULREAVER (P4, Soulrender + a lost soul + ectoplasm): hits heal 2 like the Soulrender, and every kill made with it
 * STORES a soul (up to 3). Right-click releases them: 3 hearts + 4 s Resistance per soul.
 */
public class SoulreaverItem extends ForgedBlade {
    public static final int MAX_SOULS = 3;

    public SoulreaverItem(ToolMaterial m, int dmg, float speed, Settings s) {
        super(m, dmg, speed, s, "Hits heal you; kills store a soul (max 3)", "Right-click: release the souls - 3 hearts each");
    }

    static int souls(ItemStack s) { return s.hasNbt() ? s.getNbt().getInt("Souls") : 0; }

    @Override
    protected void extraTooltip(ItemStack stack, List<Text> tooltip) {
        tooltip.add(Text.literal("Souls: " + souls(stack) + "/" + MAX_SOULS).formatted(Formatting.LIGHT_PURPLE));
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (target.getWorld() instanceof ServerWorld sw) {
            attacker.heal(2.0f);
            sw.spawnParticles(ParticleTypes.SOUL, target.getX(), target.getBodyY(0.5), target.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
        }
        return super.postHit(stack, target, attacker);
    }

    /** SkillEffects.onKill: a kill made with a Soulreaver in the main hand stores a soul. */
    public static void onKill(ServerPlayerEntity p, LivingEntity killed) {
        ItemStack s = p.getMainHandStack();
        if (!(s.getItem() instanceof SoulreaverItem) || souls(s) >= MAX_SOULS) return;
        s.getOrCreateNbt().putInt("Souls", souls(s) + 1);
        ((ServerWorld) p.getWorld()).spawnParticles(ParticleTypes.SCULK_SOUL, killed.getX(), killed.getBodyY(0.5), killed.getZ(), 8, 0.3, 0.4, 0.3, 0.05);
        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.PARTICLE_SOUL_ESCAPE, SoundCategory.PLAYERS, 1.2f, 0.8f);
        p.sendMessage(Text.literal("Soul stored (" + souls(s) + "/" + MAX_SOULS + ")").formatted(Formatting.LIGHT_PURPLE), true);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        int n = souls(stack);
        if (n == 0) return TypedActionResult.pass(stack);
        if (world instanceof ServerWorld sw) {
            stack.getOrCreateNbt().putInt("Souls", 0);
            user.heal(6.0f * n);
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 80 * n, 0));
            sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, user.getX(), user.getBodyY(0.5), user.getZ(), 20 * n, 0.5, 0.6, 0.5, 0.03);
            world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 1f, 1.4f);
            user.getItemCooldownManager().set(this, 20);
        }
        return TypedActionResult.success(stack, world.isClient);
    }
}
