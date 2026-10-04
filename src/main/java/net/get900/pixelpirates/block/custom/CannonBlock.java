package net.get900.pixelpirates.block.custom;

import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
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
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class CannonBlock extends HorizontalFacingBlock implements BlockEntityProvider {

    public static final BooleanProperty LOADED = BooleanProperty.of("loaded");

    public CannonBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState()
            .with(FACING, Direction.NORTH)
            .with(LOADED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, LOADED);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing());
    }

    // ── Block entity ──────────────────────────────────────────────────────────

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CannonBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient || type != ModBlocks.CANNON_BLOCK_ENTITY) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<CannonBlockEntity>) CannonBlockEntity::serverTick;
    }

    // ── Gun Crew upgrade ──────────────────────────────────────────────────────

    /**
     * GUN CREW (Shipwright upgrade "gun_crew"): loading a cannon on a ship also loads every empty cannon of the same
     * block type that faces the same way (= the same broadside) - level I only on the same deck (same y), level II
     * the whole side. Each extra cannon takes one more of the same shot from the player's inventory (creative: free);
     * it stops when they run out. Returns how many extra cannons were loaded.
     */
    private int loadBroadside(ServerWorld world, BlockPos pos, BlockState state, PlayerEntity player, ItemStack held, int ammo) {
        var ship = org.valkyrienskies.mod.api.ValkyrienSkies.getShipManagingBlock(world, pos.getX(), pos.getY(), pos.getZ());
        if (ship == null || ship.getShipAABB() == null) return 0;
        int level = net.get900.pixelpirates.world.ShipRegistryState.get(world.getServer().getOverworld()).getUpgradeLevel(ship.getId(), "gun_crew");
        if (level <= 0) return 0;
        var box = ship.getShipAABB();
        Direction side = state.get(FACING);
        net.minecraft.item.Item shotItem = held.isEmpty() ? shotItemOf(ammo) : held.getItem();
        int loaded = 0;
        BlockPos.Mutable p = new BlockPos.Mutable();
        int y0 = level >= 2 ? box.minY() : pos.getY(), y1 = level >= 2 ? box.maxY() : pos.getY();
        for (int y = y0; y <= y1; y++)
            for (int x = box.minX(); x <= box.maxX(); x++)
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    p.set(x, y, z);
                    if (p.equals(pos)) continue;
                    BlockState o = world.getBlockState(p);
                    if (!o.isOf(this) || o.get(FACING) != side || o.get(LOADED)) continue;
                    if (!(world.getBlockEntity(p) instanceof CannonBlockEntity obe)) continue;
                    if (!player.isCreative() && !takeOne(player, shotItem)) {
                        if (loaded > 0) player.sendMessage(Text.literal("§7Out of shot."), false);
                        return loaded;
                    }
                    obe.setAmmo(ammo);
                    world.setBlockState(p, o.with(LOADED, true));
                    loaded++;
                }
        if (loaded > 0) world.playSound(null, pos, SoundEvents.ITEM_ARMOR_EQUIP_CHAIN, SoundCategory.BLOCKS, 1.0f, 0.7f);
        return loaded;
    }

    private static net.minecraft.item.Item shotItemOf(int ammo) {
        return switch (ammo) {
            case net.get900.pixelpirates.homestead.ship.Shot.CHAIN -> net.get900.pixelpirates.homestead.HomesteadItems.CHAIN_SHOT;
            case net.get900.pixelpirates.homestead.ship.Shot.GRAPE -> net.get900.pixelpirates.homestead.HomesteadItems.GRAPE_SHOT;
            default -> ModItems.CANNON_BALL;
        };
    }

    private static boolean takeOne(PlayerEntity player, net.minecraft.item.Item item) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(item)) { s.decrement(1); return true; }
        }
        return false;
    }

    // ── Interaction ───────────────────────────────────────────────────────────

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(world.getBlockEntity(pos) instanceof CannonBlockEntity be)) return ActionResult.PASS;

        if (!state.get(LOADED)) {
            ItemStack held = player.getStackInHand(hand);
            int ammo = net.get900.pixelpirates.homestead.ship.Shot.of(held);
            if (ammo >= 0) {
                if (!player.isCreative()) held.decrement(1);
                be.setAmmo(ammo);
                world.setBlockState(pos, state.with(LOADED, true));
                world.playSound(null, pos, SoundEvents.ITEM_ARMOR_EQUIP_IRON, SoundCategory.BLOCKS, 1.0f, 0.8f);
                int more = loadBroadside((ServerWorld) world, pos, state, player, held, ammo);
                player.sendMessage(Text.literal("§eLoaded" + (more > 0 ? " " + (more + 1) + " cannons" : "")
                        + net.get900.pixelpirates.homestead.ship.Shot.label(ammo) + "! Right-click again to charge."), true);
            } else {
                player.sendMessage(Text.literal("§7Hold a cannonball to load."), true);
            }
            return ActionResult.SUCCESS;
        }

        if (!be.isCharging()) {
            // First click on a loaded cannon — light the fuse
            be.startCharge(player);
            world.playSound(null, pos, SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.BLOCKS, 0.5f, 1.5f);
        } else {
            // Second click — fire at current charge level
            be.fireFromPlayer((ServerWorld) world, pos, state, player);
        }
        return ActionResult.SUCCESS;
    }
}
