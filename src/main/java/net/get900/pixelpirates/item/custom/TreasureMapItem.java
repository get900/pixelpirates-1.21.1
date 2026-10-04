package net.get900.pixelpirates.item.custom;

import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.item.TreasureLoot;
import net.get900.pixelpirates.util.AdvancementHelper;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.List;
import java.util.Random;

public class TreasureMapItem extends Item {

    public enum Tier {
        COMMON    ("Common",    10, 200, 1200),
        RARE      ("Rare",      30, 2000, 4000),
        LEGENDARY ("Legendary", 75, 4000, 6500);

        public final String label;
        public final int cost;
        public final int minRange;
        public final int maxRange;

        Tier(String label, int cost, int minRange, int maxRange) {
            this.label    = label;
            this.cost     = cost;
            this.minRange = minRange;
            this.maxRange = maxRange;
        }
    }

    public final Tier tier;

    public TreasureMapItem(Tier tier, Settings settings) {
        super(settings);
        this.tier = tier;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient) return TypedActionResult.pass(user.getStackInHand(hand));

        ServerWorld sw = (ServerWorld) world;
        ItemStack stack = user.getStackInHand(hand);
        Random rng = new Random();

        double angle = rng.nextDouble() * 2 * Math.PI;
        double dist  = tier.minRange + rng.nextDouble() * (tier.maxRange - tier.minRange);
        int tx = (int)(user.getX() + Math.cos(angle) * dist);
        int tz = (int)(user.getZ() + Math.sin(angle) * dist);

        // Force-load the chunk so getTopY works and block placement succeeds
        ChunkPos cp = new ChunkPos(new BlockPos(tx, 64, tz));
        sw.getChunkManager().getChunk(cp.x, cp.z, ChunkStatus.FULL, true);

        int ty = sw.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, tx, tz) - 1;
        // Bury the chest 3 blocks underground — player must dig to find it
        BlockPos chestPos = new BlockPos(tx, ty - 3, tz);

        sw.setBlockState(chestPos, Blocks.CHEST.getDefaultState());
        if (sw.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
            fillChest(chest, rng, user);
        }

        // Direction hint — no exact coordinates
        double ddx = tx - user.getX();
        double ddz = tz - user.getZ();
        int totalDist = (int) Math.sqrt(ddx * ddx + ddz * ddz);
        String compass = compassDirection(ddx, ddz);
        user.sendMessage(Text.literal(
            "§6A treasure chest lies buried §e" + totalDist + " §6blocks to the §e" + compass + "§6. Dig to find it."), false);

        // Xaero's Minimap waypoint — silently intercepted if Xaero's is installed, ignored otherwise
        user.sendMessage(Text.literal(
            "xaero-waypoint:" + tier.label + " Treasure:T:" + tx + ":" + (ty - 3) + ":" + tz + ":5:false:0:gui.xaero_default"), false);

        if (user instanceof net.minecraft.server.network.ServerPlayerEntity spe) {
            AdvancementHelper.grant(spe, "x_marks_the_spot");
        }

        stack.decrement(1);
        return TypedActionResult.success(stack);
    }

    private static String compassDirection(double dx, double dz) {
        // In Minecraft: +X = East, +Z = South, -Z = North
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        if (angle < -157.5 || angle >= 157.5) return "West";
        if (angle < -112.5) return "Southwest";
        if (angle < -67.5)  return "South";
        if (angle < -22.5)  return "Southeast";
        if (angle < 22.5)   return "East";
        if (angle < 67.5)   return "Northeast";
        if (angle < 112.5)  return "North";
        return "Northwest";
    }

    private void fillChest(ChestBlockEntity chest, Random rng, PlayerEntity user) {
        List<TreasureLoot.LootEntry> pool = switch (tier) {
            case COMMON    -> TreasureLoot.COMMON;
            case RARE      -> TreasureLoot.RARE;
            case LEGENDARY -> TreasureLoot.LEGENDARY;
        };
        List<ItemStack> loot = TreasureLoot.roll(pool, rng, user.getServer() == null ? java.util.Set.of()
                : net.get900.pixelpirates.homestead.parrot.ParrotCollection.owned(user.getServer(), user.getUuid()));
        for (int i = 0; i < loot.size() && i < 27; i++) {
            chest.setStack(i, loot.get(i));
        }
    }
}
