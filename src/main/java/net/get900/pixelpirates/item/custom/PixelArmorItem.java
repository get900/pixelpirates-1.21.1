package net.get900.pixelpirates.item.custom;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Every Pixel Pirates armor piece is a 3D GeckoLib armor model (2026-09-30 armor overhaul): geo
 * {@code geo/armor/<material>.geo.json}, texture {@code textures/armor/<material>.png} (+ {@code _glowmask}), built by
 * {@code tools/gen_armor_models.py} from the concept art. The renderer is supplied by the CLIENT through
 * {@link #CLIENT_PROVIDER} (set in PixelPiratesClient) so this class never touches client code on a dedicated server.
 */
public class PixelArmorItem extends ArmorItem implements GeoItem {
    /** Client hook: builds the GeckoLib RenderProvider for an item. Null on a dedicated server. */
    public static Function<PixelArmorItem, Object> CLIENT_PROVIDER;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    public PixelArmorItem(ArmorMaterial material, Type type, Settings settings) {
        super(material, type, settings);
    }

    /** The model/texture name: the armor material's name (pirate_armor, castaway, ..., thalassar). */
    public String modelName() { return getMaterial().getName(); }

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        if (CLIENT_PROVIDER != null) consumer.accept(CLIENT_PROVIDER.apply(this));
    }

    @Override
    public Supplier<Object> getRenderProvider() { return renderProvider; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // loose parts (capes, tails, tentacles, chains, fins) sway in the "idle" clip (tools/armor/kit.py `moving`);
        // it plays faster the faster the wearer moves, so capes stream behind a running player
        controllers.add(new software.bernie.geckolib.core.animation.AnimationController<>(this, "sway", 4, state -> {
            net.minecraft.entity.Entity e = state.getData(software.bernie.geckolib.constant.DataTickets.ENTITY);
            double v = e == null ? 0 : Math.hypot(e.getX() - e.prevX, e.getZ() - e.prevZ);
            state.getController().setAnimationSpeed(1 + Math.min(2.5, v * 9));
            return state.setAndContinue(SWAY);
        }));
    }

    private static final software.bernie.geckolib.core.animation.RawAnimation SWAY =
            software.bernie.geckolib.core.animation.RawAnimation.begin().thenLoop("idle");

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
