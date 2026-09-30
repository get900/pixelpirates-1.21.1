package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.homestead.HomesteadState;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** HIDEOUTS: no monster spawns naturally inside a hideout claim. Spawners/eggs/summons unaffected. */
@Mixin(MobEntity.class)
public abstract class HideoutSpawnMixin {
    @Inject(method = "canSpawn(Lnet/minecraft/world/WorldAccess;Lnet/minecraft/entity/SpawnReason;)Z", at = @At("HEAD"), cancellable = true)
    private void pixelpirates$hideoutPeace(WorldAccess world, SpawnReason reason, CallbackInfoReturnable<Boolean> cir) {
        if (reason != SpawnReason.NATURAL) return;                  // (chunk-gen spawns run off-thread: leave them)
        MobEntity self = (MobEntity) (Object) this;
        if (!(self instanceof Monster) || !(world instanceof net.minecraft.world.ServerWorldAccess sw)) return;
        var w = sw.toServerWorld();
        if (HomesteadState.get(w.getServer()).hideoutAt(w.getRegistryKey(), self.getBlockPos()) != null) cir.setReturnValue(false);
    }
}
