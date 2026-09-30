package net.get900.pixelpirates.item.custom;

import net.get900.pixelpirates.entity.custom.PowderBargeEntity;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Set a POWDER BARGE afloat on the water you are looking at (or beside you when you are swimming). */
public class PowderBargeItem extends Item {
    public PowderBargeItem(Settings settings) { super(settings); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        Vec3d at = null;
        if (user.isTouchingWater()) at = user.getPos().add(user.getRotationVec(1f).multiply(2, 0, 2));
        else {
            BlockHitResult hit = world.raycast(new RaycastContext(user.getEyePos(), user.getEyePos().add(user.getRotationVec(1f).multiply(6)),
                    RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.SOURCE_ONLY, user));
            if (hit.getType() == HitResult.Type.BLOCK && world.getFluidState(hit.getBlockPos()).isIn(FluidTags.WATER)) at = Vec3d.ofBottomCenter(hit.getBlockPos().up()).add(0, -0.4, 0);
        }
        if (at == null) return TypedActionResult.fail(stack);
        if (!world.isClient) {
            world.spawnEntity(PowderBargeEntity.at(world, at, user.getYaw()));
            world.playSound(null, BlockPos.ofFloored(at), SoundEvents.ENTITY_BOAT_PADDLE_WATER, SoundCategory.PLAYERS, 1.0f, 0.8f);
        }
        if (!user.getAbilities().creativeMode) stack.decrement(1);
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Set it afloat where the Leviathan feeds - let it swallow this.").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Shoot it or set it alight and it goes off where it floats.").formatted(Formatting.DARK_RED));
    }
}
