package net.get900.pixelpirates.block.custom;

import net.get900.pixelpirates.entity.custom.HarpoonEntity;
import net.get900.pixelpirates.entity.mob.BloodfinEntity;
import net.get900.pixelpirates.entity.mob.ModMobs;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * HARPOON WINCH - a whaler's harpoon gun on a rope drum (Bloodfin fight, WhalersGrave). Use it:
 *   - its line is in the Bloodfin  -> CRANK: reel the shark 2 blocks closer
 *   - LOADED                       -> FIRE a harpoon (HarpoonEntity) wherever you are looking
 *   - empty + holding a Harpoon    -> reload (instant); it also reloads itself after 20 s
 * Works anywhere (a player can build one on their ship), but it is at home in the Whalers' Grave.
 */
public class HarpoonWinchBlock extends HorizontalFacingBlock {
    public static final BooleanProperty LOADED = BooleanProperty.of("loaded");
    public static final int RELOAD_TICKS = 400;

    public HarpoonWinchBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(LOADED, true));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, LOADED);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        ServerWorld sw = (ServerWorld) world;
        BloodfinEntity shark = hookedShark(sw, pos);
        if (shark != null) {
            shark.reel(pos);
            world.playSound(null, pos, SoundEvents.BLOCK_CHAIN_STEP, SoundCategory.BLOCKS, 1.5f, 0.6f);
            world.playSound(null, pos, SoundEvents.ITEM_CROSSBOW_LOADING_MIDDLE, SoundCategory.BLOCKS, 1.2f, 0.5f);
            player.sendMessage(Text.literal("You crank the winch - the line hauls the Bloodfin in").formatted(Formatting.GOLD), true);
            return ActionResult.CONSUME;
        }
        if (state.get(LOADED)) {
            net.minecraft.util.math.Vec3d from = muzzle(pos, player);
            net.minecraft.util.math.Vec3d target = aimPoint(sw, player);
            net.minecraft.util.math.Vec3d dir = target.subtract(from).normalize();
            HarpoonEntity h = new HarpoonEntity(sw, player, pos);
            h.setPosition(from.x, from.y, from.z);
            h.setVelocity(dir.x * HarpoonEntity.SPEED, dir.y * HarpoonEntity.SPEED, dir.z * HarpoonEntity.SPEED);
            sw.spawnEntity(h);
            sw.spawnParticles(net.minecraft.particle.ParticleTypes.POOF, from.x, from.y, from.z, 12, 0.15, 0.15, 0.15, 0.08);
            sw.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT, from.x, from.y, from.z, 10, 0.2, 0.2, 0.2, 0.3);
            world.setBlockState(pos, state.with(LOADED, false));
            world.scheduleBlockTick(pos, this, RELOAD_TICKS);
            world.playSound(null, pos, SoundEvents.ITEM_CROSSBOW_SHOOT, SoundCategory.BLOCKS, 2.0f, 0.5f);
            world.playSound(null, pos, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 1.5f, 0.7f);
            return ActionResult.CONSUME;
        }
        ItemStack held = player.getStackInHand(hand);
        if (held.isOf(ModItems.HARPOON)) {
            if (!player.getAbilities().creativeMode) held.decrement(1);
            world.setBlockState(pos, state.with(LOADED, true));
            world.playSound(null, pos, SoundEvents.ITEM_CROSSBOW_LOADING_END, SoundCategory.BLOCKS, 1.5f, 0.6f);
            return ActionResult.CONSUME;
        }
        player.sendMessage(Text.literal("The winch is empty - load a Harpoon (it reloads itself in 20 s)").formatted(Formatting.GRAY), true);
        return ActionResult.CONSUME;
    }

    /** Just off the winch, on the side the player is aiming toward (never inside the block). */
    private static net.minecraft.util.math.Vec3d muzzle(BlockPos pos, PlayerEntity player) {
        net.minecraft.util.math.Vec3d look = player.getRotationVec(1.0f).multiply(1, 0, 1);
        look = look.lengthSquared() < 1e-4 ? net.minecraft.util.math.Vec3d.ZERO : look.normalize().multiply(0.9);
        return net.minecraft.util.math.Vec3d.ofCenter(pos).add(look.x, 0.2, look.z);
    }

    /**
     * Where the player is aiming: the Bloodfin if it is anywhere near their line of sight (aim assist - the shot
     * leaves the winch, not the player's eye, so a raw look vector would miss by the parallax), else the first block
     * the look ray hits, ignoring water so you can aim at the shark under the surface.
     */
    private static net.minecraft.util.math.Vec3d aimPoint(ServerWorld sw, PlayerEntity player) {
        net.minecraft.util.math.Vec3d eye = player.getEyePos(), look = player.getRotationVec(1.0f);
        var type = ModMobs.TYPES.get("bloodfin");
        if (type != null) {
            net.minecraft.util.math.Vec3d best = null;
            double bestErr = Double.MAX_VALUE;
            for (Entity e : sw.getEntitiesByType(type, player.getBoundingBox().expand(64), Entity::isAlive)) {
                net.minecraft.util.math.Vec3d c = e.getBoundingBox().getCenter(), to = c.subtract(eye);
                double along = to.dotProduct(look);
                if (along < 2) continue;
                double off = to.subtract(look.multiply(along)).length();
                if (off < 3.5 + along * 0.08 && off < bestErr) { bestErr = off; best = c; }
            }
            if (best != null) return best;
        }
        var hit = sw.raycast(new net.minecraft.world.RaycastContext(eye, eye.add(look.multiply(64)), net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE, player));
        return hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS ? eye.add(look.multiply(64)) : hit.getPos();
    }

    @Nullable
    private static BloodfinEntity hookedShark(ServerWorld sw, BlockPos pos) {
        var type = ModMobs.TYPES.get("bloodfin");
        if (type == null) return null;
        List<? extends Entity> list = sw.getEntitiesByType(type, new Box(pos).expand(48), Entity::isAlive);
        for (Entity e : list) if (e instanceof BloodfinEntity b && b.isHookedFrom(pos)) return b;
        return null;
    }

    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (!state.get(LOADED)) {
            world.setBlockState(pos, state.with(LOADED, true));
            world.playSound(null, pos, SoundEvents.ITEM_CROSSBOW_LOADING_END, SoundCategory.BLOCKS, 1.2f, 0.6f);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable BlockView world, List<Text> tooltip, TooltipContext options) {
        tooltip.add(Text.literal("Use: fire a harpoon where you look / crank a hooked line").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Reload with a Harpoon, or wait 20 s").formatted(Formatting.GRAY));
    }
}
