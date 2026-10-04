package net.get900.pixelpirates.homestead.forge;

import net.get900.pixelpirates.item.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * FORGE HEARTH: a brick hearth that takes fuel by hand (coal / charcoal +2, brimstone +3, blaze rod +4, coal block or a lava
 * bucket fills it). FUEL 0-8, lit while above 0, burns down one step every 30 s - a full hearth runs 4 minutes. A Forge
 * Anvil within 2 blocks works only while a hearth is lit; bellows touching the hearth make the hammer work faster.
 * A hearth that was never fed (the town forge, placed lit by the layout) has no burn-down tick scheduled and stays lit.
 */
public class ForgeHearthBlock extends HorizontalFacingBlock {
    public static final IntProperty FUEL = IntProperty.of("fuel", 0, 8);
    static final int BURN_TICKS = 600;

    public ForgeHearthBlock(Settings s) {
        super(s);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(FUEL, 0));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> b) { b.add(FACING, FUEL); }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) { return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite()); }

    public static boolean lit(BlockState s) { return s.getBlock() instanceof ForgeHearthBlock && s.get(FUEL) > 0; }

    static int fuelValue(ItemStack s) {
        if (s.isOf(Items.COAL) || s.isOf(Items.CHARCOAL)) return 2;
        if (s.isOf(ModItems.BRIMSTONE)) return 3;
        if (s.isOf(Items.BLAZE_ROD)) return 4;
        if (s.isOf(Items.COAL_BLOCK) || s.isOf(Items.LAVA_BUCKET)) return 8;
        return 0;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        ItemStack held = player.getStackInHand(hand);
        int v = fuelValue(held);
        if (v == 0) {
            if (!world.isClient) player.sendMessage(state.get(FUEL) > 0
                    ? Text.literal("The hearth burns (" + state.get(FUEL) + "/8). Feed it coal, charcoal, brimstone or a lava bucket.").formatted(Formatting.GOLD)
                    : Text.literal("The hearth is cold. Feed it coal, charcoal, brimstone or a lava bucket.").formatted(Formatting.GRAY), true);
            return ActionResult.success(world.isClient);
        }
        int fuel = state.get(FUEL);
        if (fuel >= 8) {
            if (!world.isClient) player.sendMessage(Text.literal("The hearth is already roaring.").formatted(Formatting.GOLD), true);
            return ActionResult.success(world.isClient);
        }
        if (world.isClient) return ActionResult.SUCCESS;
        if (!player.getAbilities().creativeMode) {
            if (held.isOf(Items.LAVA_BUCKET)) player.setStackInHand(hand, new ItemStack(Items.BUCKET));
            else held.decrement(1);
        }
        world.setBlockState(pos, state.with(FUEL, Math.min(8, fuel + v)), Block.NOTIFY_ALL);
        if (!world.getBlockTickScheduler().isQueued(pos, this)) world.scheduleBlockTick(pos, this, BURN_TICKS);
        world.playSound(null, pos, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.BLOCKS, 0.7f, 0.8f + world.random.nextFloat() * 0.3f);
        ((ServerWorld) world).spawnParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 12, 0.25, 0.1, 0.25, 0.02);
        return ActionResult.SUCCESS;
    }

    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        int fuel = state.get(FUEL);
        if (fuel <= 0) return;
        world.setBlockState(pos, state.with(FUEL, fuel - 1), Block.NOTIFY_ALL);
        if (fuel - 1 > 0) world.scheduleBlockTick(pos, this, BURN_TICKS);
        else world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.5f, 1.2f);
    }

    @Override
    public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
        if (lit(state) && entity instanceof LivingEntity le && !le.isSneaking() && !le.isFireImmune())
            entity.damage(world.getDamageSources().hotFloor(), 1.0f);
        super.onSteppedOn(world, pos, state, entity);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random r) {
        if (!lit(state)) return;
        double x = pos.getX() + 0.5, y = pos.getY() + 1.0, z = pos.getZ() + 0.5;
        world.addParticle(ParticleTypes.SMOKE, x + (r.nextDouble() - 0.5) * 0.6, y, z + (r.nextDouble() - 0.5) * 0.6, 0, 0.04, 0);
        if (r.nextInt(2) == 0) world.addParticle(ParticleTypes.FLAME, x + (r.nextDouble() - 0.5) * 0.5, y - 0.05, z + (r.nextDouble() - 0.5) * 0.5, 0, 0.01, 0);
        if (r.nextInt(5) == 0) world.addParticle(ParticleTypes.LAVA, x, y, z, 0, 0, 0);
        if (r.nextInt(8) == 0) world.playSound(x, y, z, SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE, SoundCategory.BLOCKS, 0.8f, 0.8f + r.nextFloat() * 0.3f, false);
    }
}
