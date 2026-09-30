package net.get900.pixelpirates.item.custom;

import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.world.dimension.ModDimensions;
import net.minecraft.advancement.Advancement;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DimensionKeyItem extends Item {

    private static final Identifier PIRATES_LIFE_ADV = PixelPirates.id("pirates_life");

    public DimensionKeyItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient) return TypedActionResult.success(user.getStackInHand(hand));

        ServerPlayerEntity player = (ServerPlayerEntity) user;
        MinecraftServer server = player.getServer();

        if (player.getWorld().getRegistryKey().equals(ModDimensions.PIXEL_PIRATES_WORLD)) {
            // Return to overworld spawn
            ServerWorld overworld = server.getWorld(World.OVERWORLD);
            BlockPos spawn = overworld.getSpawnPos();
            int surfaceY = overworld.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
            player.teleport(overworld, spawn.getX() + 0.5, surfaceY, spawn.getZ() + 0.5,
                    Set.of(), player.getYaw(), player.getPitch());
            player.sendMessage(Text.literal("§bReturned to the surface world."), true);
        } else {
            // Entering PP dimension
            ServerWorld ppWorld = server.getWorld(ModDimensions.PIXEL_PIRATES_WORLD);
            if (ppWorld == null) {
                player.sendMessage(Text.literal("§cThe Pixel Pirates seas are not yet loaded!"), true);
                return TypedActionResult.fail(user.getStackInHand(hand));
            }

            // Save overworld position, store inventory, clear, then teleport
            ServerWorld overworld = server.getWorld(World.OVERWORLD);
            storeInventoryInBarrels(player, overworld);

            player.teleport(ppWorld, 0.5, 80.0, 0.5, Set.of(), 0.0f, 0.0f);
            player.sendMessage(Text.literal("§eWelcome to the Pixel Pirates seas, captain!"), true);

            grantAdvancement(player, PIRATES_LIFE_ADV);
        }

        return TypedActionResult.success(user.getStackInHand(hand));
    }

    // ---------- inventory storage ----------

    private static void storeInventoryInBarrels(ServerPlayerEntity player, ServerWorld overworld) {
        // Collect all non-empty stacks before we touch the inventory
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (!s.isEmpty()) items.add(s.copy());
        }

        // Find surface position at player's overworld location
        int bx = (int) player.getX(), bz = (int) player.getZ();
        int surfaceY = overworld.getTopY(Heightmap.Type.MOTION_BLOCKING, bx, bz);
        BlockPos pos1 = clearAndFind(overworld, new BlockPos(bx, surfaceY, bz));
        BlockPos pos2 = clearAndFind(overworld, pos1.east());

        // Place two barrels (pirate-appropriate containers, no double-chest merging hassle)
        overworld.setBlockState(pos1,
                Blocks.BARREL.getDefaultState().with(BarrelBlock.FACING, Direction.SOUTH),
                Block.NOTIFY_ALL);
        overworld.setBlockState(pos2,
                Blocks.BARREL.getDefaultState().with(BarrelBlock.FACING, Direction.SOUTH),
                Block.NOTIFY_ALL);

        String name = player.getName().getString();
        fillBarrel(overworld, pos1, items, 0,  27, name + "'s Belongings (1/2)");
        fillBarrel(overworld, pos2, items, 27, 54, name + "'s Belongings (2/2)");

        // Wipe inventory, then give back one key so they can return
        player.getInventory().clear();
        player.getInventory().insertStack(new ItemStack(ModItems.DIMENSION_KEY));

        player.sendMessage(Text.literal(
                "§aYour belongings are stored in barrels at §e" + pos1.toShortString() + "§a in the overworld."), false);
    }

    private static BlockPos clearAndFind(ServerWorld world, BlockPos start) {
        BlockPos pos = start;
        // Walk up until we hit air
        while (!world.getBlockState(pos).isAir() && pos.getY() < world.getTopY() - 1) {
            pos = pos.up();
        }
        return pos;
    }

    private static void fillBarrel(ServerWorld world, BlockPos pos, List<ItemStack> items,
                                   int from, int toExcl, String label) {
        if (!(world.getBlockEntity(pos) instanceof BarrelBlockEntity barrel)) return;
        for (int i = from; i < Math.min(items.size(), toExcl); i++) {
            barrel.setStack(i - from, items.get(i));
        }
    }

    // ---------- advancement ----------

    private static void grantAdvancement(ServerPlayerEntity player, Identifier id) {
        Advancement entry = player.getServer().getAdvancementLoader().get(id);
        if (entry == null) return;
        var tracker = player.getAdvancementTracker();
        if (!tracker.getProgress(entry).isDone()) {
            tracker.grantCriterion(entry, "got_here");
        }
    }
}
