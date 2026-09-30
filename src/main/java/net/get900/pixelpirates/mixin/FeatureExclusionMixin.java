package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.world.dungeon.DungeonFeature;
import net.get900.pixelpirates.world.dungeon.DungeonPlacement;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps natural decoration out of protected dungeons (Dungeons.PROTECTED): any non-dungeon feature whose
 * origin is inside a predicted site footprint and near the surface is skipped. Decoration runs per chunk
 * in any order, so a neighbouring chunk could otherwise grow a tree into a fort AFTER it was built;
 * checking every feature's origin against the deterministic site grid works regardless of order.
 * Underground features (ores, geodes deeper than 8 below the surface) are left alone.
 */
@Mixin(ConfiguredFeature.class)
public abstract class FeatureExclusionMixin {
    @Shadow @Final private Feature<?> feature;
    private static final int GEODE_PAD = 16, SITE_PAD = 6;

    @Inject(method = "generate", at = @At("HEAD"), cancellable = true)
    private void pixelpirates$skipInsideDungeons(StructureWorldAccess world, ChunkGenerator generator, Random random, BlockPos origin,
                                                 CallbackInfoReturnable<Boolean> cir) {
        if (feature instanceof DungeonFeature || feature instanceof net.get900.pixelpirates.world.dungeon.GallowsGrotto
                || feature instanceof net.get900.pixelpirates.world.dungeon.TitansChest
                || feature instanceof net.get900.pixelpirates.world.leviathan.LeviathanSites) return;
        try {
            var pctx = DungeonFeature.placementContext(world, generator);
            // a geode grows ~12 blocks out from its origin, so the origin test below misses one that carves in from beside a
            // structure (one turned up in the middle of the Gallows Grotto): keep geodes 16 blocks clear of every site, any depth
            if (feature instanceof net.minecraft.world.gen.feature.GeodeFeature
                    && (DungeonPlacement.nearAnySite(pctx, origin, GEODE_PAD)
                    || net.get900.pixelpirates.world.dungeon.GallowsGrotto.nearFootprint(pctx, origin, GEODE_PAD)
                    || net.get900.pixelpirates.world.dungeon.TitansChest.nearFootprint(pctx, origin, GEODE_PAD))) {
                cir.setReturnValue(false);
                return;
            }
            // Nothing natural grows in, onto or out of a built site: an origin just OUTSIDE a structure still let trees,
            // boulders, kelp and ore blobs grow into or on top of it (user playtest 2026-09-30). So every feature whose
            // origin is within a margin of any site is skipped, at any depth: the Leviathan's five sites (+10), the grotto
            // and the chest (+6), and every dungeon (+6 round its +-22 footprint).
            if (net.get900.pixelpirates.world.leviathan.LeviathanRoute.nearAnySite(world.getSeed(), origin.getX(), origin.getZ(), 10)
                    || net.get900.pixelpirates.world.dungeon.GallowsGrotto.nearFootprint(pctx, origin, SITE_PAD)
                    || net.get900.pixelpirates.world.dungeon.TitansChest.nearFootprint(pctx, origin, SITE_PAD)
                    || DungeonPlacement.nearAnySite(pctx, origin, SITE_PAD)) {
                cir.setReturnValue(false);
            }
        } catch (Exception ignored) {
            // prediction must never break chunk generation - decorate normally instead
        }
    }
}
