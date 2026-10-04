package net.get900.pixelpirates.block.custom;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.function.Supplier;

/**
 * Fruit that hangs under palm/shorewood canopies (banana bunch, coconut).
 * Placed by the tree decorators; only the outline is reduced so the hanging model
 * is what the player targets, not a full invisible cube.
 * A REGROWING fruit (the banana bunch, 2026-09-30) is picked with right-click: it drops 2-4 fruit and turns green
 * (RIPE=false), then ripens again on a random tick (~1 in 8, so roughly 9 minutes). Breaking an unripe bunch drops nothing.
 */
public class HangingFruitBlock extends Block {
    public static final BooleanProperty RIPE = BooleanProperty.of("ripe");
    private final VoxelShape shape;
    private final Supplier<Item> fruit;                 // null = not pickable (coconut: break it)

    public HangingFruitBlock(VoxelShape shape, Settings settings) { this(shape, settings, null); }

    public HangingFruitBlock(VoxelShape shape, Settings settings, Supplier<Item> fruit) {
        super(settings);
        this.shape = shape;
        this.fruit = fruit;
        setDefaultState(getStateManager().getDefaultState().with(RIPE, true));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(RIPE); }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return shape;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (fruit == null) return ActionResult.PASS;
        if (!state.get(RIPE)) return ActionResult.PASS;
        if (!world.isClient) {
            dropStack(world, pos, new ItemStack(fruit.get(), 2 + world.random.nextInt(3)));
            world.setBlockState(pos, state.with(RIPE, false), Block.NOTIFY_LISTENERS);
            world.playSound(null, pos, SoundEvents.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, SoundCategory.BLOCKS, 1f, 0.9f + world.random.nextFloat() * 0.2f);
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public boolean hasRandomTicks(BlockState state) { return fruit != null && !state.get(RIPE); }

    @Override
    public void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (random.nextInt(8) == 0) world.setBlockState(pos, state.with(RIPE, true), Block.NOTIFY_LISTENERS);
    }
}
