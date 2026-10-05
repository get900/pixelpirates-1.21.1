package net.get900.pixelpirates.entity.client;

import net.get900.pixelpirates.entity.custom.PirateCrewEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.ItemArmorGeoLayer;

/** Pirate crew + Armada marines (the texture comes from PirateCrewModel by type); they show what they hold (HeldItemGeoLayer). */
public class PirateCrewEntityRenderer extends GeoEntityRenderer<PirateCrewEntity> {

    public PirateCrewEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PirateCrewModel());
        this.addRenderLayer(new ItemArmorGeoLayer<>(this));
        this.addRenderLayer(new HeldItemGeoLayer<>(this));
    }

    @Override
    public void render(PirateCrewEntity entity, float entityYaw, float partialTick,
                       MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight) {
        poseStack.scale(0.9375f, 0.9375f, 0.9375f);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
