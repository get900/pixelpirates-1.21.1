package net.get900.pixelpirates.block.custom;

import net.get900.pixelpirates.entity.mob.AbyssalKingEntity;
import net.get900.pixelpirates.entity.mob.ModMobs;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
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
 * TIDE SLUICE - one of the four floodgate wheels in the Sunken Court (Abyssal King, boss 5/10).
 * Turn it to open a drain: every open sluice lowers the flooded hall by a quarter, and with all four
 * open the hall runs dry and the King is STRANDED. He fights back by resealing them (AbyssalKingEntity)
 * and his Tide Wardens walk over to wind them shut. The block's OPEN state is the single source of truth;
 * the King recounts it every half second. Unbreakable and blast-proof.
 */
public class TideSluiceBlock extends HorizontalFacingBlock {
    public static final BooleanProperty OPEN = BooleanProperty.of("open");

    public TideSluiceBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(OPEN, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (state.get(OPEN)) {
            player.sendMessage(Text.literal("The sluice is already open - the court drains through it.").formatted(Formatting.GRAY), true);
            return ActionResult.CONSUME;
        }
        ServerWorld sw = (ServerWorld) world;
        AbyssalKingEntity king = nearestKing(sw, pos);
        if (king == null) {
            player.sendMessage(Text.literal("The wheel will not turn - no tide answers here.").formatted(Formatting.GRAY), true);
            return ActionResult.CONSUME;
        }
        if (!king.acceptsSluice(player)) return ActionResult.CONSUME;
        world.setBlockState(pos, state.with(OPEN, true));
        king.onSluiceOpened(pos, player);
        return ActionResult.CONSUME;
    }

    @Nullable
    public static AbyssalKingEntity nearestKing(ServerWorld sw, BlockPos pos) {
        var type = ModMobs.TYPES.get("abyssal_king");
        if (type == null) return null;
        List<? extends Entity> kings = sw.getEntitiesByType(type, new Box(pos).expand(40), Entity::isAlive);
        for (Entity e : kings) if (e instanceof AbyssalKingEntity k && k.ownsSluice(pos)) return k;
        return null;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        Direction f = state.get(FACING);
        double x = pos.getX() + 0.5 + f.getOffsetX() * 0.6, y = pos.getY() + 0.5, z = pos.getZ() + 0.5 + f.getOffsetZ() * 0.6;
        if (state.get(OPEN)) {
            // water rushing into the drain: bubbles drawn in toward the face
            for (int i = 0; i < 3; i++) {
                double ox = (random.nextDouble() - 0.5) * 2.4, oy = (random.nextDouble() - 0.5) * 2.0, oz = (random.nextDouble() - 0.5) * 2.4;
                world.addParticle(ParticleTypes.BUBBLE, x + ox + f.getOffsetX(), y + oy, z + oz + f.getOffsetZ(), -ox * 0.15, -oy * 0.15, -oz * 0.15);
            }
            world.addParticle(ParticleTypes.CURRENT_DOWN, x, y, z, 0, 0, 0);
        } else if (random.nextInt(3) == 0) {
            world.addParticle(ParticleTypes.GLOW, x, y + 0.3, z, 0, 0.02, 0);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable BlockView world, List<Text> tooltip, TooltipContext options) {
        tooltip.add(Text.literal("Sunken Court floodgate: turn it to drain the King's hall").formatted(Formatting.GRAY));
    }
}
