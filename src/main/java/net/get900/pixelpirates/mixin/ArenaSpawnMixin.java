package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.world.dimension.ModDimensions;
import net.get900.pixelpirates.world.leviathan.LeviathanSites;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.SpawnHelper;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.SpawnSettings;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * THE LEVIATHAN'S ARENAS are its alone: nothing spawns naturally in the Rift, the Gullet or the Drowning Spire - neither
 * the mob cycle nor the animals/fish a chunk is populated with when it generates. Hooked at the spawn cycle itself
 * (LairSpawnMixin's MobEntity#canSpawn hook is skipped by every mob that overrides it without calling super).
 */
@Mixin(SpawnHelper.class)
public abstract class ArenaSpawnMixin {
    @Inject(method = "canSpawn(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/SpawnGroup;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/gen/chunk/ChunkGenerator;Lnet/minecraft/world/biome/SpawnSettings$SpawnEntry;Lnet/minecraft/util/math/BlockPos$Mutable;D)Z",
            at = @At("HEAD"), cancellable = true)
    private static void pixelpirates$quietArena(ServerWorld world, SpawnGroup group, StructureAccessor structures, ChunkGenerator gen,
                                                SpawnSettings.SpawnEntry entry, BlockPos.Mutable pos, double sq, CallbackInfoReturnable<Boolean> cir) {
        if (!world.getRegistryKey().equals(ModDimensions.PIXEL_PIRATES_WORLD)) return;
        if (LeviathanSites.inLair(world.getSeed(), pos)) { cir.setReturnValue(false); return; }
        try {                                                               // and the Abyssal Heart's chest
            var ctx = new net.get900.pixelpirates.world.dungeon.DungeonPlacement.Context(world.getSeed(), gen, world.getChunkManager().getNoiseConfig(), world, gen.getSeaLevel());
            if (net.get900.pixelpirates.world.dungeon.TitansChest.insideVolume(ctx, pos)) cir.setReturnValue(false);
        } catch (Exception ignored) {
            // prediction must never break mob spawning
        }
    }

    @Inject(method = "populateEntities", at = @At("HEAD"), cancellable = true)
    private static void pixelpirates$emptyArenaChunk(ServerWorldAccess world, RegistryEntry<Biome> biome, ChunkPos chunk, Random random, CallbackInfo ci) {
        ServerWorld sw = world.toServerWorld();
        if (sw.getRegistryKey().equals(ModDimensions.PIXEL_PIRATES_WORLD) && LeviathanSites.inLair(sw.getSeed(), chunk.getCenterAtY(64))) ci.cancel();
    }
}
