package net.get900.pixelpirates.item.forged;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterial;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** OBSIDIAN HALBERD (P3, Boarding Pike + obsidian + brimstone): right-click CLEAVE - a wide arc in front, 8 damage + knockback to all in it. 6 s cooldown. */
public class ObsidianHalberdItem extends ForgedBlade {
    public ObsidianHalberdItem(ToolMaterial m, int dmg, float speed, Settings s) {
        super(m, dmg, speed, s, "Right-click: Cleave - an arc in front, 8 damage to each foe (6 s)");
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        user.swingHand(hand);
        if (world.isClient) return TypedActionResult.success(stack, true);
        Vec3d look = user.getRotationVec(1f).multiply(1, 0, 1).normalize();
        int hit = 0;
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, user.getBoundingBox().expand(3.5, 1.0, 3.5),
                e -> e != user && e.isAlive() && !e.isTeammate(user))) {
            Vec3d to = e.getPos().subtract(user.getPos()).multiply(1, 0, 1);
            if (to.lengthSquared() > 12.25 || to.normalize().dotProduct(look) < 0.35) continue;
            bonusHit(e, user, 8.0f);
            e.takeKnockback(0.6, -look.x, -look.z);
            hit++;
        }
        ServerWorld sw = (ServerWorld) world;
        for (int i = -3; i <= 3; i++) {
            Vec3d d = look.rotateY((float) Math.toRadians(i * 18)).multiply(2.4);
            sw.spawnParticles(ParticleTypes.SWEEP_ATTACK, user.getX() + d.x, user.getBodyY(0.5), user.getZ() + d.z, 1, 0, 0, 0, 0);
        }
        sw.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1f, 0.6f);
        user.getItemCooldownManager().set(this, 120);
        stack.damage(1 + hit / 2, user, p -> p.sendToolBreakStatus(hand));
        return TypedActionResult.success(stack, false);
    }
}
