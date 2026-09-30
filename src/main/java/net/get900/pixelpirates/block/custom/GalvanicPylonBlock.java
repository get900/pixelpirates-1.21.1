package net.get900.pixelpirates.block.custom;

import net.get900.pixelpirates.entity.mob.AbyssalHeartEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * GALVANIC PYLON - the ancients' pacemaker coil, four of them round the Abyssal Heart (boss 9/10). The Heart drives the
 * STATE: IDLE between beats, WINDOW (green) for a few ticks either side of every beat, CHARGED (blue-white) once a
 * bolt has been struck into it. Hit it (left or right click) during the WINDOW and it fires a bolt into the heart;
 * with all four charged at once the heart goes into CARDIAC ARREST (AbyssalHeartEntity#onPylonStruck).
 * Unbreakable. Left clicks arrive through AttackBlockCallback (PixelPirates).
 */
public class GalvanicPylonBlock extends Block {
    public static final int IDLE = 0, WINDOW = 1, CHARGED = 2;
    public static final IntProperty STATE = IntProperty.of("state", 0, 2);

    public GalvanicPylonBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(STATE, IDLE));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(STATE);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world instanceof ServerWorld sw && hand == Hand.MAIN_HAND) strike(sw, pos, player);
        return ActionResult.SUCCESS;
    }

    /** A player struck the coil (either click). */
    public static void strike(ServerWorld sw, BlockPos pos, PlayerEntity player) {
        AbyssalHeartEntity heart = AbyssalHeartEntity.nearest(sw, pos, 80);
        if (heart == null) {
            player.sendMessage(Text.literal("The coil is cold. Nothing down here beats.").formatted(Formatting.GRAY), true);
            return;
        }
        heart.onPylonStruck(pos, player);
    }
}
