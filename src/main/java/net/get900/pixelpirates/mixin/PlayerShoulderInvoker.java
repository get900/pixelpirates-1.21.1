package net.get900.pixelpirates.mixin;

import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the parrot companion set a shoulder parrot down on purpose. */
@Mixin(PlayerEntity.class)
public interface PlayerShoulderInvoker {
    @Invoker("dropShoulderEntities")
    void pixelpirates$dropShoulderEntities();
}
