package net.get900.pixelpirates.item.custom;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Volcanic isles blade — hits ignite the target; right-click "Fire Nova"
 * detonates a ring of flame around the wielder.
 */
public class EmberbrandItem extends SwordItem {
    private static final int COOLDOWN_TICKS = 300; // 15s
    private static final double NOVA_RADIUS = 4.0;

    public EmberbrandItem(ToolMaterial material, int attackDamage, float attackSpeed, Settings settings) {
        super(material, attackDamage, attackSpeed, settings);
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        target.setOnFireFor(4);
        return super.postHit(stack, target, attacker);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient) {
            List<LivingEntity> victims = world.getEntitiesByClass(LivingEntity.class,
                    user.getBoundingBox().expand(NOVA_RADIUS, 1.5, NOVA_RADIUS),
                    e -> e != user && e.isAlive() && !e.isTeammate(user));
            for (LivingEntity victim : victims) {
                victim.damage(user.getDamageSources().playerAttack(user), 5.0f);
                victim.setOnFireFor(5);
            }

            ServerWorld serverWorld = (ServerWorld) world;
            for (int i = 0; i < 32; i++) {
                double angle = i * Math.PI / 16.0;
                double px = user.getX() + Math.cos(angle) * 2.5;
                double pz = user.getZ() + Math.sin(angle) * 2.5;
                serverWorld.spawnParticles(ParticleTypes.FLAME, px, user.getY() + 0.3, pz, 3, 0.15, 0.1, 0.15, 0.02);
            }
            serverWorld.spawnParticles(ParticleTypes.LAVA, user.getX(), user.getY() + 0.5, user.getZ(), 8, 1.0, 0.3, 1.0, 0.0);
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.0f, 0.7f);

            user.getItemCooldownManager().set(this, COOLDOWN_TICKS);
            stack.damage(2, user, p -> p.sendToolBreakStatus(hand));
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.pixelpirates.emberbrand.ability").formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("tooltip.pixelpirates.emberbrand.lore").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
        super.appendTooltip(stack, world, tooltip, context);
    }
}
