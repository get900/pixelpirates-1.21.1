package net.get900.pixelpirates.entity.client;

import net.get900.pixelpirates.entity.custom.CastawayEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class CastawayEntityRenderer extends GeoEntityRenderer<CastawayEntity> {
    public CastawayEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new CastawayModel());
        // Model is authored at exactly vanilla humanoid size (1.0 x 2.0 blocks), so it renders 1:1.
        // Keep in step with CASTAWAY's EntityDimensions in ModEntities — scale does not move the hitbox.
        // Do NOT override getTextureLocation() here — that bypasses CastawayModel.getTextureResource().
        withScale(1.0f);
    }
}
