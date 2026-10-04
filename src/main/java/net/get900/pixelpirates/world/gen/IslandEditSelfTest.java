package net.get900.pixelpirates.world.gen;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.function.Consumer;

/**
 * /ppedittest (op): a fake player makes three real changes on the island - breaks a park bench (-88,71,-38), places a gold
 * block in the park (-85,71,-15) and one on the upper street (-30,71,-45, outside every building) - then
 * /ppisland capture all runs. Expected: Park +2, Streets & Grounds +1. Undo with /ppisland discard all + restamp.
 */
public final class IslandEditSelfTest {
    private IslandEditSelfTest() {}

    public static void run(ServerWorld w, Consumer<String> out) throws java.io.IOException {
        var fp = net.fabricmc.fabric.api.entity.FakePlayer.get(w);
        fp.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
        BlockPos bench = new BlockPos(-88, 71, -38);
        w.getChunk(bench);
        fp.refreshPositionAndAngles(-88, 72, -36, 0, 0);
        boolean broke = fp.interactionManager.tryBreakBlock(bench);
        for (BlockPos ground : new BlockPos[]{new BlockPos(-85, 70, -15), new BlockPos(-30, 70, -45)}) {
            w.getChunk(ground);
            fp.refreshPositionAndAngles(ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 2.5, 180, 60);
            ItemStack gold = new ItemStack(Items.GOLD_BLOCK);
            fp.setStackInHand(Hand.MAIN_HAND, gold);
            fp.interactionManager.interactBlock(fp, w, gold, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(ground).add(0, 0.5, 0), Direction.UP, ground, false));
        }
        out.accept("bench broken: " + broke + ", gold placed: " + w.getBlockState(new BlockPos(-85, 71, -15)).getBlock().getName().getString()
                + " / " + w.getBlockState(new BlockPos(-30, 71, -45)).getBlock().getName().getString());
        var r = IslandEdits.captureAll(w);
        out.accept("capture all: " + r.edits() + " edit(s) in " + r.sections() + " section(s), " + r.spots() + " spots checked");
        for (var e : PortCityLayout.edits().entrySet()) out.accept("  " + e.getKey() + ": " + e.getValue().size());
    }
}
