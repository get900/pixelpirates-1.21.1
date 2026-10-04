package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.homestead.parrot.ParrotTypeHolder;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * PARROT TYPES: a parrot carries its custom type ("PPType", homestead/parrot/ParrotTypes) in its save data - which is
 * also what rides the player's shoulder, so the type survives the shoulder and coming back down - and in tracked data so
 * the client can draw it.
 */
@Mixin(ParrotEntity.class)
public abstract class ParrotTypeMixin implements ParrotTypeHolder {
    @Unique
    private static final TrackedData<String> PP_TYPE = DataTracker.registerData(ParrotEntity.class, TrackedDataHandlerRegistry.STRING);

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void pixelpirates$track(CallbackInfo ci) { ((ParrotEntity) (Object) this).getDataTracker().startTracking(PP_TYPE, ""); }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void pixelpirates$save(NbtCompound nbt, CallbackInfo ci) {
        String t = pixelpirates$getParrotType();
        if (!t.isEmpty()) nbt.putString("PPType", t);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void pixelpirates$load(NbtCompound nbt, CallbackInfo ci) { pixelpirates$setParrotType(nbt.getString("PPType")); }

    @Override
    public String pixelpirates$getParrotType() { return ((ParrotEntity) (Object) this).getDataTracker().get(PP_TYPE); }

    @Override
    public void pixelpirates$setParrotType(String id) { ((ParrotEntity) (Object) this).getDataTracker().set(PP_TYPE, id == null ? "" : id); }
}
