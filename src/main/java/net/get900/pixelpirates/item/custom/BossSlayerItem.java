package net.get900.pixelpirates.item.custom;

import net.minecraft.block.BlockState;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * DEBUG / TESTING weapon (/pptest slayer, or the admin creative tab): kills whatever it touches.
 * Left-click: any entity it hits dies outright. Right-click: kills the living entity under the
 * crosshair up to 64 blocks away - for flying bosses, bosses in deep water, or the Leviathan's
 * 27-block body. Deaths are credited to the player, so boss loot, XP and zone unlocks all fire.
 * Never loses durability.
 */
public class BossSlayerItem extends SwordItem {
    private static final double RANGE = 64.0;

    public BossSlayerItem(Settings settings) {
        super(ToolMaterials.NETHERITE, 9995, 0f, settings.maxCount(1).fireproof());
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        slay(target, attacker);
        return true;                                                   // no durability loss (super would damage it)
    }

    @Override
    public boolean postMine(ItemStack stack, World world, BlockState state, BlockPos pos, LivingEntity miner) {
        return true;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack);
        Vec3d eye = user.getEyePos();
        Vec3d end = eye.add(user.getRotationVec(1f).multiply(RANGE));
        Box sweep = user.getBoundingBox().stretch(user.getRotationVec(1f).multiply(RANGE)).expand(1.0);
        EntityHitResult hit = ProjectileUtil.raycast(user, eye, end, sweep,
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != user, RANGE * RANGE);
        if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) {
            user.sendMessage(Text.literal("[X] Nothing in your sights").formatted(Formatting.GRAY), true);
            return TypedActionResult.fail(stack);
        }
        slay(target, user);
        return TypedActionResult.success(stack);
    }

    private static void slay(LivingEntity target, LivingEntity attacker) {
        if (target.getWorld().isClient || !target.isAlive()) return;
        if (attacker instanceof PlayerEntity player) {
            target.timeUntilRegen = 0;                                 // ignore i-frames from the swing that just landed
            target.damage(target.getDamageSources().playerAttack(player), Float.MAX_VALUE);
        }
        if (target.isAlive()) target.kill();                          // anything that shrugged it off (damage filters)
        if (target.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, target.getX(), target.getBodyY(0.5), target.getZ(), 40,
                    target.getWidth() / 2, target.getHeight() / 2, target.getWidth() / 2, 0.05);
            sw.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.PLAYERS, 0.8f, 1.4f);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("DEBUG - kills anything it touches").formatted(Formatting.RED));
        tooltip.add(Text.literal("Right-click: slay what you aim at (64 blocks)").formatted(Formatting.GRAY));
    }

    @Override
    public boolean hasGlint(ItemStack stack) { return true; }

    @Override
    public boolean isDamageable() { return false; }
}
