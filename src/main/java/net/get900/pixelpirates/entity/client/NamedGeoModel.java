package net.get900.pixelpirates.entity.client;

import net.get900.pixelpirates.PixelPirates;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;

/**
 * GeoModel for assets that follow the tools/gen_mob_assets.py layout:
 * geo/NAME.geo.json, textures/entity/NAME.png (+ NAME_glowmask.png), animations/NAME.animation.json.
 */
public class NamedGeoModel<T extends GeoAnimatable> extends GeoModel<T> {
    protected final Identifier model, texture, animation;

    public NamedGeoModel(String name) {
        this.model = new Identifier(PixelPirates.MOD_ID, "geo/" + name + ".geo.json");
        this.texture = new Identifier(PixelPirates.MOD_ID, "textures/entity/" + name + ".png");
        this.animation = new Identifier(PixelPirates.MOD_ID, "animations/" + name + ".animation.json");
    }

    @Override
    public Identifier getModelResource(T animatable) { return model; }

    @Override
    public Identifier getTextureResource(T animatable) { return texture; }

    @Override
    public Identifier getAnimationResource(T animatable) { return animation; }
}
