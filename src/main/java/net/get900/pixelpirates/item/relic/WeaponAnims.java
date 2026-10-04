package net.get900.pixelpirates.item.relic;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

/**
 * Weapon animations (2026-09-30): every 3D weapon loops its "idle" clip (tools/gen_weapon_models.py) and has an "action"
 * controller whose one-shot clips (fire / special / ability / dash) are TRIGGERED from the server when the weapon does
 * its thing, so everyone nearby sees them. The bow's draw and the Heartseeker's beat are driven per frame instead
 * (item/client/RelicWeaponRenderer). Items must call SingletonGeoAnimatable.registerSyncedAnimatable in their constructor.
 */
public final class WeaponAnims {
    private WeaponAnims() {}

    public static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");

    public static void controllers(GeoItem item, AnimatableManager.ControllerRegistrar c, String... actions) {
        c.add(new AnimationController<>(item, "idle", 0, s -> s.setAndContinue(IDLE)));
        AnimationController<GeoItem> action = new AnimationController<>(item, "action", 0, s -> PlayState.STOP);
        for (String a : actions) action.triggerableAnim(a, RawAnimation.begin().thenPlay(a));
        c.add(action);
    }

    /** Give the stack its GeckoLib id early (inventoryTick) so the first triggered clip already finds it on the client. */
    public static void assignId(ItemStack stack, World world) {
        if (world instanceof ServerWorld sw && (!stack.hasNbt() || !stack.getNbt().contains(GeoItem.ID_NBT_KEY))) GeoItem.getOrAssignId(stack, sw);
    }

    /** Play one of the item's action clips for everyone tracking `holder`. */
    public static void play(GeoItem item, Entity holder, ItemStack stack, String anim) {
        if (holder.getWorld() instanceof ServerWorld sw)
            item.triggerAnim(holder, GeoItem.getOrAssignId(stack, sw), "action", anim);
    }
}
