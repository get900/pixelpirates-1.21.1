package net.get900.pixelpirates.entity.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.entity.mob.RevenantPartEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Renders a piece of the Chained Revenant with the model for its part (revenant_arm_r/_l, revenant_leg_r/_l,
 * revenant_head - tools/mobs/revenant.py) at the body's scale, enraged skin when raging, and the soul-light glowmask.
 */
@Environment(EnvType.CLIENT)
public class RevenantPartRenderer extends GeoEntityRenderer<RevenantPartEntity> {
    public RevenantPartRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new GeoModel<>() {
            @Override
            public Identifier getModelResource(RevenantPartEntity e) { return PixelPirates.id("geo/" + e.part().model + ".geo.json"); }

            @Override
            public Identifier getTextureResource(RevenantPartEntity e) {
                return PixelPirates.id("textures/entity/" + e.part().model + (e.raging() ? "_enraged" : "") + ".png");
            }

            @Override
            public Identifier getAnimationResource(RevenantPartEntity e) { return PixelPirates.id("animations/" + e.part().model + ".animation.json"); }
        });
        withScale(2.4f);
        this.shadowRadius = 0.8f;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    protected float getDeathMaxRotation(RevenantPartEntity animatable) { return 0f; }
}
