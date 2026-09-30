package net.get900.pixelpirates.entity.client;

import net.get900.pixelpirates.entity.custom.SharkEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SharkEntityRenderer extends GeoEntityRenderer<SharkEntity> {
    public SharkEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new SharkModel());
        // Model is authored ~2.1 blocks nose to tail; 2x renders it as a ~4.2 block predator.
        // Keep in step with SHARK's EntityDimensions in ModEntities — scale does not move the hitbox.
        // Do NOT override getTextureLocation() here — that bypasses SharkModel.getTextureResource().
        withScale(2.0f);
    }
}
