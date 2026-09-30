package net.get900.pixelpirates.entity.client;

import net.get900.pixelpirates.entity.custom.LavaCrabEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class LavaCrabEntityRenderer extends GeoEntityRenderer<LavaCrabEntity> {
    public LavaCrabEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new LavaCrabModel());
        // Model is authored at shell ~1.1 blocks / 2.25 across the legs — far too big for a crab,
        // so it renders at half. Keep in step with LAVA_CRAB's EntityDimensions in ModEntities;
        // scale does not move the hitbox.
        // Do NOT override getTextureLocation() here — that bypasses LavaCrabModel.getTextureResource().
        withScale(0.5f);
    }
}
