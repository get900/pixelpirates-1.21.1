package net.get900.pixelpirates.item.custom;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Abyssal cutlass — right-click "Tempest" calls a lightning bolt down on whatever
 * the wielder is looking at (up to 40 blocks away).
 */
public class StormcallerItem extends SwordItem {
    private static final int COOLDOWN_TICKS = 400; // 20s
    private static final double RANGE = 40.0;

    public StormcallerItem(ToolMaterial material, int attackDamage, float attackSpeed, Settings settings) {
        super(material, attackDamage, attackSpeed, settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient) {
            Vec3d start = user.getEyePos();
            Vec3d direction = user.getRotationVec(1.0f);
            Vec3d end = start.add(direction.multiply(RANGE));

            // Prefer a living target under the crosshair, fall back to the block hit / terrain.
            Box searchBox = user.getBoundingBox().stretch(direction.multiply(RANGE)).expand(1.0);
            EntityHitResult entityHit = ProjectileUtil.raycast(user, start, end, searchBox,
                    e -> e instanceof LivingEntity && e.isAlive() && e != user, RANGE * RANGE);

            Vec3d strikePos;
            if (entityHit != null) {
                strikePos = entityHit.getEntity().getPos();
            } else {
                HitResult blockHit = user.raycast(RANGE, 1.0f, false);
                if (blockHit.getType() == HitResult.Type.MISS) {
                    int gx = MathHelper.floor(end.x);
                    int gz = MathHelper.floor(end.z);
                    strikePos = new Vec3d(end.x, world.getTopY(Heightmap.Type.MOTION_BLOCKING, gx, gz), end.z);
                } else {
                    strikePos = blockHit.getPos();
                }
            }

            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
            if (bolt != null) {
                bolt.refreshPositionAfterTeleport(strikePos.x, strikePos.y, strikePos.z);
                bolt.setChanneler(user instanceof ServerPlayerEntity serverPlayer ? serverPlayer : null);
                world.spawnEntity(bolt);
            }
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ITEM_TRIDENT_THUNDER, SoundCategory.PLAYERS, 2.0f, 1.0f);

            user.getItemCooldownManager().set(this, COOLDOWN_TICKS);
            stack.damage(3, user, p -> p.sendToolBreakStatus(hand));
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("tooltip.pixelpirates.stormcaller.ability").formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("tooltip.pixelpirates.stormcaller.lore").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
        super.appendTooltip(stack, world, tooltip, context);
    }
}
