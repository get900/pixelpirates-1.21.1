package net.get900.pixelpirates.mixin;

import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets the parrot companion set a shoulder parrot down on purpose. */
@Mixin(PlayerEntity.class)
public interface PlayerShoulderInvoker {
    @Invoker("dropShoulderEntities")
    void pixelpirates$dropShoulderEntities();

    /** Kraken's Pet (parrot types phase 3): taken off the shoulder while it is away, put back when it returns. */
    @Invoker("setShoulderEntityLeft")
    void pixelpirates$setShoulderLeft(net.minecraft.nbt.NbtCompound nbt);

    @Invoker("setShoulderEntityRight")
    void pixelpirates$setShoulderRight(net.minecraft.nbt.NbtCompound nbt);
}
