package net.get900.pixelpirates.block.custom;

import net.get900.pixelpirates.entity.mob.ChainedRevenantEntity;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
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
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * THE GALLOWBRAND IN THE STONE - under the Chained Revenant's gallows in the Gallows Pit. The hangman's greatsword,
 * driven into a boulder and chained down: it is what keeps the Revenant asleep in his chains. Pulling it (right-click)
 * gives you the sword - and snaps the gallows chains: he drops, and the fight begins. Only a player who has earned the
 * fight (boss chain) can move it. GeckoLib model gallowbrand_stone (tools/mobs/gallows.py), rendered at 1.25x.
 */
public class GallowbrandStoneBlock extends BlockWithEntity {
    public static final BooleanProperty HAS_SWORD = BooleanProperty.of("has_sword");
    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 16, 16);

    public GallowbrandStoneBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getStateManager().getDefaultState().with(HAS_SWORD, true));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(HAS_SWORD); }

    @Override
    public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.ENTITYBLOCK_ANIMATED; }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) { return SHAPE; }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new GallowbrandStoneBlockEntity(pos, state); }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(player instanceof ServerPlayerEntity sp) || !(world instanceof ServerWorld sw)) return ActionResult.PASS;
        if (!state.get(HAS_SWORD)) {
            sp.sendMessage(Text.literal("Only the broken chains remain in the rock.").formatted(Formatting.GRAY), true);
            return ActionResult.SUCCESS;
        }
        List<ChainedRevenantEntity> bosses = sw.getEntitiesByClass(ChainedRevenantEntity.class, new Box(pos).expand(32), e -> e.isAlive());
        ChainedRevenantEntity boss = bosses.isEmpty() ? null : bosses.get(0);
        if (boss != null && boss.isHanging() && !boss.canBeFreedBy(sp)) {
            sp.sendMessage(Text.literal("The blade will not move. Something in you is not yet worthy of this fight.").formatted(Formatting.DARK_AQUA), true);
            sw.playSound(null, pos, SoundEvents.BLOCK_CHAIN_HIT, SoundCategory.BLOCKS, 1.5f, 0.5f);
            return ActionResult.SUCCESS;
        }
        // wrench it free
        sw.setBlockState(pos, state.with(HAS_SWORD, false), Block.NOTIFY_ALL);
        if (sw.getBlockEntity(pos) instanceof GallowbrandStoneBlockEntity be) be.triggerAnim("pull", "pull");
        ItemStack blade = new ItemStack(ModItems.GALLOWBRAND);
        if (!sp.getInventory().insertStack(blade)) sp.dropItem(blade, false);
        sw.playSound(null, pos, SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.BLOCKS, 2.5f, 0.5f);
        sw.playSound(null, pos, SoundEvents.ITEM_TRIDENT_RETURN, SoundCategory.BLOCKS, 2.0f, 0.6f);
        sw.playSound(null, pos, SoundEvents.BLOCK_DEEPSLATE_BREAK, SoundCategory.BLOCKS, 2.0f, 0.5f);
        sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 2.5, pos.getZ() + 0.5, 60, 0.4, 1.5, 0.4, 0.04);
        sw.spawnParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 30, 1.2, 0.4, 1.2, 0.02);
        sp.sendMessage(Text.literal("You wrench the GALLOWBRAND from the stone...").formatted(Formatting.AQUA, Formatting.BOLD), false);
        if (boss != null && boss.isHanging()) boss.release(sp);
        return ActionResult.SUCCESS;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (!state.get(HAS_SWORD)) return;
        if (random.nextInt(2) == 0)
            world.addParticle(ParticleTypes.SOUL, pos.getX() + 0.5 + random.nextGaussian() * 0.15, pos.getY() + 1.4 + random.nextDouble() * 3.2,
                    pos.getZ() + 0.5 + random.nextGaussian() * 0.15, 0, 0.02, 0);
        if (random.nextInt(4) == 0)
            world.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5 + random.nextGaussian() * 0.9, pos.getY() + 1.2,
                    pos.getZ() + 0.5 + random.nextGaussian() * 0.9, 0, 0.01, 0);
    }
}
