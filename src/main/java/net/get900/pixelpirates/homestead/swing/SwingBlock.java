package net.get900.pixelpirates.homestead.swing;

import net.get900.pixelpirates.homestead.TallFurniture;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * SWINGS (2026-10-04): the FRAMED swing (a wooden A-frame, 2 blocks tall, a plank seat on ropes) and the HANGING swing
 * (just ropes and a seat - place it under a beam, a branch, a fence). Right-click to sit: the seat swings you back and
 * forth along the way it faces (SwingSeatEntity); sneak to get off. While someone swings, OCCUPIED hides the resting
 * seat and SwingRenderer draws it swinging with them. The ropes + seat are modelled on the LOWER block (up to 2 tall).
 */
public class SwingBlock extends TallFurniture implements BlockEntityProvider {
    public static final BooleanProperty OCCUPIED = BooleanProperty.of("occupied");

    public SwingBlock(Settings s, boolean hangs) {
        super(s, new double[]{1, 0, 3, 15, 16, 13}, hangs ? new double[]{3, 0, 6, 13, 16, 10} : new double[]{0, 0, 3, 16, 16, 13}, hangs);
        setDefaultState(getDefaultState().with(OCCUPIED, false));
    }

    public boolean hangs() { return hangs; }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> b) { super.appendProperties(b); b.add(OCCUPIED); }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (player.isSneaking() || player.hasVehicle() || !player.getStackInHand(hand).isEmpty()) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        BlockPos lower = state.get(HALF) == DoubleBlockHalf.LOWER ? pos : pos.down();
        return SwingSeatEntity.sit(world, lower, player) ? ActionResult.SUCCESS : ActionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return state.get(HALF) == DoubleBlockHalf.LOWER ? new SwingBlockEntity(pos, state) : null;
    }

    /** Set OCCUPIED on both halves. */
    static void setOccupied(World world, BlockPos lower, boolean on) {
        for (BlockPos p : new BlockPos[]{lower, lower.up()}) {
            BlockState s = world.getBlockState(p);
            if (s.getBlock() instanceof SwingBlock && s.get(OCCUPIED) != on) world.setBlockState(p, s.with(OCCUPIED, on), 3);
        }
    }
}
