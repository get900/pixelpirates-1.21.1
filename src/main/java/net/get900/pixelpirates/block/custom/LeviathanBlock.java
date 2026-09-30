package net.get900.pixelpirates.block.custom;

import net.get900.pixelpirates.world.leviathan.LeviathanHunt;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * The Leviathan hunt's working blocks (boss 10/10). All unbreakable; every use goes to {@link LeviathanHunt}, which owns
 * the rules. Left clicks on the tide bell arrive through AttackBlockCallback (PixelPirates).
 * <ul>
 *   <li>RIFT_SEAL - the ancients' seal on the Rift's north rim: wake the Leviathan here (with a confirmation)</li>
 *   <li>ANCHOR_WINCH - one per binding chain: wind it to reinforce the chain (phase 1)</li>
 *   <li>TIDE_BELL - rings while the Last Tide rises; strike it to silence it (phase 3). RINGING property</li>
 *   <li>BANE_BALLISTA - load it (Heartstone, Bane Shaft, gunpowder) and fire it when the Leviathan rears (phase 3). LOADED</li>
 *   <li>WATCHERS_HORN - after a failed Last Tide, blow it to call the Leviathan back (5 minutes later)</li>
 * </ul>
 */
public class LeviathanBlock extends HorizontalFacingBlock {
    public enum Kind { SEAL, WINCH, BELL, BALLISTA, HORN }
    public static final BooleanProperty RINGING = BooleanProperty.of("ringing");
    public static final BooleanProperty LOADED = BooleanProperty.of("loaded");
    private final Kind kind;

    public LeviathanBlock(Kind kind, Settings settings) {
        super(settings);
        this.kind = kind;
        BlockState d = getStateManager().getDefaultState().with(FACING, Direction.NORTH);
        if (kind == Kind.BELL) d = d.with(RINGING, false);
        if (kind == Kind.BALLISTA) d = d.with(LOADED, false);
        setDefaultState(d);
    }

    public Kind kind() { return kind; }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, RINGING, LOADED);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (world instanceof ServerWorld sw) LeviathanHunt.onBlockUsed(sw, pos, kind, player, false);
        return ActionResult.SUCCESS;
    }
}
