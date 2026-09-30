package net.get900.pixelpirates.mixin;

import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * THE TITAN'S CHEST is the Abyssal Heart's arena alone: nothing spawns naturally anywhere inside its built volume
 * (cavern, lungs, chambers, the wound). The Heart's own summons (clots, embolisms, phantasms) are MOB_SUMMONED - unaffected.
 */
@Mixin(MobEntity.class)
public abstract class LairSpawnMixin {
    @Inject(method = "canSpawn(Lnet/minecraft/world/WorldAccess;Lnet/minecraft/entity/SpawnReason;)Z", at = @At("HEAD"), cancellable = true)
    private void pixelpirates$quietChest(WorldAccess world, SpawnReason reason, CallbackInfoReturnable<Boolean> cir) {
        if (reason != SpawnReason.NATURAL || !(world instanceof net.minecraft.world.ServerWorldAccess swa)) return;
        var sw = swa.toServerWorld();
        var gen = sw.getChunkManager().getChunkGenerator();
        try {
            var ctx = new net.get900.pixelpirates.world.dungeon.DungeonPlacement.Context(sw.getSeed(), gen, sw.getChunkManager().getNoiseConfig(), sw, gen.getSeaLevel());
            var pos = ((MobEntity) (Object) this).getBlockPos();
            if (net.get900.pixelpirates.world.dungeon.TitansChest.insideVolume(ctx, pos)
                    || net.get900.pixelpirates.world.leviathan.LeviathanSites.inLair(sw.getSeed(), pos)) cir.setReturnValue(false);
        } catch (Exception ignored) {
            // prediction must never break mob spawning
        }
    }
}
