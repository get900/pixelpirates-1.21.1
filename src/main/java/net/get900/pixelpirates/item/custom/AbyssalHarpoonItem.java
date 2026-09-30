package net.get900.pixelpirates.item.custom;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
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
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Abyssal boarding weapon — right-click "The Reel" spears a target up to 16 blocks
 * away and yanks it to the wielder's feet. Get over here.
 */
public class AbyssalHarpoonItem extends SwordItem {
    private static final int COOLDOWN_TICKS = 160; // 8s
    private static final double RANGE = 16.0;

    public AbyssalHarpoonItem(ToolMaterial material, int attackDamage, float attackSpeed, Settings settings) {
        super(material, attackDamage, attackSpeed, settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        Vec3d start = user.getEyePos();
        Vec3d direction = user.getRotationVec(1.0f);
        Vec3d end = start.add(direction.multiply(RANGE));
        Box searchBox = user.getBoundingBox().stretch(direction.multiply(RANGE)).expand(1.5);
        EntityHitResult hit = ProjectileUtil.raycast(user, start, end, searchBox,
                e -> e instanceof LivingEntity && e.isAlive() && e != user && !e.isSpectator(), RANGE * RANGE);
        if (hit == null) {
            return TypedActionResult.pass(stack);
        }

        if (!world.isClient) {
            Entity target = hit.getEntity();
            target.damage(user.getDamageSources().playerAttack(user), 3.0f);

            Vec3d pull = start.subtract(target.getPos());
            double distance = pull.length();
            Vec3d velocity = pull.normalize().multiply(Math.min(distance * 0.35, 2.8)).add(0, 0.4, 0);
            target.setVelocity(velocity);
            target.velocityModified = true;

            ServerWorld serverWorld = (ServerWorld) world;
            Vec3d step = target.getPos().subtract(start).multiply(1.0 / 8.0);
            Vec3d cursor = start;
            for (int i = 0; i < 8; i++) {
                serverWorld.spawnParticles(ParticleTypes.BUBBLE_POP, cursor.x, cursor.y, cursor.z, 2, 0.05, 0.05, 0.05, 0.0);
                cursor = cursor.add(step);
            }
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ITEM_TRIDENT_RETURN, SoundCategory.PLAYERS, 1.0f, 0.7f);

            user.getItemCooldownManager().set(this, COOLDOWN_TICKS);
            stack.damage(2, user, p -> p.sendToolBreakStatus(hand));
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.pixelpirates.abyssal_harpoon.ability").formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("tooltip.pixelpirates.abyssal_harpoon.lore").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
        super.appendTooltip(stack, world, tooltip, context);
    }
}
