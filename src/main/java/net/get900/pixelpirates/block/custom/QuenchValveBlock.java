package net.get900.pixelpirates.block.custom;

import net.get900.pixelpirates.entity.mob.ModMobs;
import net.get900.pixelpirates.entity.mob.MoltenWarlordEntity;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * QUENCH VALVE - set into the Crucible's walls (Molten Warlord, boss 3/10). Use it to open a cistern:
 * a cold water cascade crashes down 3 blocks in front of the valve (FACING) for 8 s. A Warlord caught
 * under it is QUENCHED (MoltenWarlordEntity#quench): stunned, cooled to stone, taking extra damage.
 * The valve refills after 45 s, so the fight can never soft-lock. Unbreakable and blast-proof.
 */
public class QuenchValveBlock extends HorizontalFacingBlock {
    public static final BooleanProperty CHARGED = BooleanProperty.of("charged");
    public static final int REFILL_TICKS = 900;

    public QuenchValveBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(CHARGED, true));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, CHARGED);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (!state.get(CHARGED)) {
            player.sendMessage(Text.literal("The cistern is still refilling...").formatted(Formatting.GRAY), true);
            return ActionResult.CONSUME;
        }
        ServerWorld sw = (ServerWorld) world;
        world.setBlockState(pos, state.with(CHARGED, false));
        world.scheduleBlockTick(pos, this, REFILL_TICKS);
        world.playSound(null, pos, SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 1.5f, 0.5f);
        world.playSound(null, pos, SoundEvents.AMBIENT_UNDERWATER_ENTER, SoundCategory.BLOCKS, 2.0f, 0.6f);
        Vec3d at = cascadeCentre(sw, pos, state.get(FACING));
        var type = ModMobs.TYPES.get("molten_warlord");
        List<? extends Entity> lords = type == null ? List.of() : sw.getEntitiesByType(type, new Box(pos).expand(48), Entity::isAlive);
        if (lords.isEmpty()) MoltenWarlordEntity.cascadeFx(sw, at);          // still pour, for the look of it
        for (Entity e : lords) if (e instanceof MoltenWarlordEntity w) w.addCascade(at);
        player.sendMessage(Text.literal("Cold water thunders down from the cistern - lure the Warlord under it!").formatted(Formatting.AQUA), true);
        return ActionResult.CONSUME;
    }

    /** 3 blocks out from the valve face, dropped to the floor below. */
    public static Vec3d cascadeCentre(ServerWorld sw, BlockPos valve, Direction facing) {
        BlockPos p = valve.offset(facing, 3);
        for (int i = 0; i < 12 && sw.getBlockState(p.down()).isAir(); i++) p = p.down();
        return Vec3d.ofBottomCenter(p);
    }

    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        world.setBlockState(pos, state.with(CHARGED, true));
        world.playSound(null, pos, SoundEvents.ITEM_BUCKET_FILL, SoundCategory.BLOCKS, 1.5f, 0.6f);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (state.get(CHARGED) && random.nextInt(2) == 0) {
            Direction f = state.get(FACING);
            world.addParticle(net.minecraft.particle.ParticleTypes.DRIPPING_WATER,
                    pos.getX() + 0.5 + f.getOffsetX() * 0.55, pos.getY() + 0.3, pos.getZ() + 0.5 + f.getOffsetZ() * 0.55, 0, 0, 0);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable BlockView world, List<Text> tooltip, TooltipContext options) {
        tooltip.add(Text.literal("Use: pour a quenching cascade 3 blocks in front (refills in 45 s)").formatted(Formatting.GRAY));
    }
}
