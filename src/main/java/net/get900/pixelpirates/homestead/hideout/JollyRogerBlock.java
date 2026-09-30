package net.get900.pixelpirates.homestead.hideout;

import net.get900.pixelpirates.homestead.HomesteadBlockEntities;
import net.get900.pixelpirates.homestead.HomesteadState;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * THE JOLLY ROGER (#1 hideouts): plant your flag to claim a HIDEOUT. One per player - striking (breaking) your flag
 * gives the claim up. Within the claim radius (32 / 48 / 64 by tier):
 *   - no hostile mob spawns naturally (HideoutSpawnMixin);
 *   - the Compass of Desire's HOME and the map table point here;
 *   - tier 3: the owner gets Regeneration I while inside.
 * Use it (owner): set your respawn point at the flag and see its tier. Use it holding pirate coins: raise the tier
 * (64 coins -> tier 2, 128 more -> tier 3). Mooring posts inside your claim are your HOME PORT (double repairs).
 */
public class JollyRogerBlock extends BlockWithEntity {
    private static final VoxelShape SHAPE = Block.createCuboidShape(4, 0, 4, 12, 16, 12);
    public static final int[] UPGRADE_COST = {0, 64, 128};

    public JollyRogerBlock(Settings s) { super(s); }

    @Override
    public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.ENTITYBLOCK_ANIMATED; }

    @Override
    public VoxelShape getOutlineShape(BlockState s, BlockView w, BlockPos p, ShapeContext c) { return SHAPE; }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new JollyRogerBlockEntity(pos, state); }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : checkType(type, HomesteadBlockEntities.JOLLY_ROGER, JollyRogerBlockEntity::serverTick);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (world.isClient || !(placer instanceof ServerPlayerEntity p) || !(world.getBlockEntity(pos) instanceof JollyRogerBlockEntity be)) return;
        HomesteadState st = HomesteadState.get(p.getServer());
        HomesteadState.Hideout old = st.hideout(p.getUuid());
        if (old != null && !(old.pos().equals(pos) && old.dim().equals(world.getRegistryKey()))) {
            p.sendMessage(Text.literal("You already fly your colours at " + old.pos().toShortString() + ". Strike that flag first.").formatted(Formatting.RED), false);
            world.breakBlock(pos, true);
            return;
        }
        HomesteadState.Hideout other = st.hideoutAt(world.getRegistryKey(), pos);
        if (other != null) {
            p.sendMessage(Text.literal("Another crew already claims these waters.").formatted(Formatting.RED), false);
            world.breakBlock(pos, true);
            return;
        }
        be.setOwner(p.getUuid(), p.getName().getString());
        st.setHideout(p.getUuid(), new HomesteadState.Hideout(pos.toImmutable(), world.getRegistryKey(), 1));
        be.triggerAnim("raise", "raise");
        world.playSound(null, pos, SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.BLOCKS, 0.5f, 1.4f);
        p.sendMessage(Text.literal("[X] Hideout claimed! No monster will spawn within 32 blocks. Use the flag to make it your respawn point.")
                .formatted(Formatting.GOLD), false);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world instanceof ServerWorld sw && world.getBlockEntity(pos) instanceof JollyRogerBlockEntity be && be.owner() != null) {
            HomesteadState st = HomesteadState.get(sw.getServer());
            HomesteadState.Hideout h = st.hideout(be.owner());
            if (h != null && h.pos().equals(pos) && h.dim().equals(world.getRegistryKey())) {
                st.setHideout(be.owner(), null);
                ServerPlayerEntity p = sw.getServer().getPlayerManager().getPlayer(be.owner());
                if (p != null) p.sendMessage(Text.literal("Your colours have been struck - the hideout is lost.").formatted(Formatting.RED), false);
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(world.getBlockEntity(pos) instanceof JollyRogerBlockEntity be) || !(player instanceof ServerPlayerEntity p)) return ActionResult.PASS;
        UUID owner = be.owner();
        if (owner == null || !owner.equals(p.getUuid())) {
            p.sendMessage(Text.literal("These are " + (be.ownerName().isEmpty() ? "nobody's" : be.ownerName() + "'s") + " colours.").formatted(Formatting.GRAY), true);
            return ActionResult.CONSUME;
        }
        HomesteadState st = HomesteadState.get(p.getServer());
        HomesteadState.Hideout h = st.hideout(owner);
        int tier = h == null ? 1 : h.tier();
        ItemStack held = p.getMainHandStack();
        if (held.isOf(ModItems.PIRATE_COIN) && tier < 3) {
            int cost = UPGRADE_COST[tier];
            if (held.getCount() < cost && !p.getAbilities().creativeMode) {
                p.sendMessage(Text.literal("Raising the hideout to tier " + (tier + 1) + " costs " + cost + " pirate coins (one stack at a time).").formatted(Formatting.RED), true);
                return ActionResult.CONSUME;
            }
            if (!p.getAbilities().creativeMode) held.decrement(cost);
            st.setHideout(owner, new HomesteadState.Hideout(pos.toImmutable(), world.getRegistryKey(), tier + 1));
            HomesteadState.Hideout nh = st.hideout(owner);
            world.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.BLOCKS, 0.8f, 1f);
            p.sendMessage(Text.literal("Hideout raised to tier " + nh.tier() + ": claim radius " + nh.radius()
                    + (nh.tier() >= 3 ? ", and the old flag heals you while you're home." : ".")).formatted(Formatting.GOLD), false);
            return ActionResult.CONSUME;
        }
        p.setSpawnPoint(world.getRegistryKey(), pos.up(), p.getYaw(), true, false);
        world.playSound(null, pos, SoundEvents.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, SoundCategory.BLOCKS, 0.7f, 1.2f);
        p.sendMessage(Text.literal("Respawn set at your hideout (tier " + tier + ", radius " + (h == null ? 32 : h.radius()) + ")."
                + (tier < 3 ? " Use it holding " + UPGRADE_COST[tier] + " pirate coins to raise it." : "")).formatted(Formatting.GOLD), false);
        return ActionResult.CONSUME;
    }
}
