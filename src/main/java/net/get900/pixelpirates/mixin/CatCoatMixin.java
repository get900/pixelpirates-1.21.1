package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.homestead.cat.CatCoatHolder;
import net.get900.pixelpirates.homestead.cat.CatCoats;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * SHIP'S CATS: a cat carries its custom coat ("PPCoat", homestead/cat/CatCoats) in its save data and in tracked data, and
 * its texture getter (what the vanilla CatEntityRenderer draws) returns the coat's texture.
 */
@Mixin(CatEntity.class)
public abstract class CatCoatMixin implements CatCoatHolder {
    @Unique
    private static final TrackedData<String> PP_COAT = DataTracker.registerData(CatEntity.class, TrackedDataHandlerRegistry.STRING);

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void pixelpirates$track(CallbackInfo ci) { ((CatEntity) (Object) this).getDataTracker().startTracking(PP_COAT, ""); }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void pixelpirates$save(NbtCompound nbt, CallbackInfo ci) {
        String t = pixelpirates$getCoat();
        if (!t.isEmpty()) nbt.putString("PPCoat", t);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void pixelpirates$load(NbtCompound nbt, CallbackInfo ci) { pixelpirates$setCoat(nbt.getString("PPCoat")); }

    @Inject(method = "getTexture", at = @At("HEAD"), cancellable = true)
    private void pixelpirates$coatTexture(CallbackInfoReturnable<Identifier> cir) {
        CatCoats.Coat c = CatCoats.byId(pixelpirates$getCoat());
        if (c != null && c.custom()) cir.setReturnValue(c.texture());
    }

    @Override
    public String pixelpirates$getCoat() { return ((CatEntity) (Object) this).getDataTracker().get(PP_COAT); }

    @Override
    public void pixelpirates$setCoat(String id) { ((CatEntity) (Object) this).getDataTracker().set(PP_COAT, id == null ? "" : id); }
}
