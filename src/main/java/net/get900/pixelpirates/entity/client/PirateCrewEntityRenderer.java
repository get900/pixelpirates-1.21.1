package net.get900.pixelpirates.entity.client;

import net.get900.pixelpirates.entity.custom.PirateCrewEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.ItemArmorGeoLayer;

public class PirateCrewEntityRenderer extends GeoEntityRenderer<PirateCrewEntity> {

    public PirateCrewEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PirateCrewModel());
        this.addRenderLayer(new ItemArmorGeoLayer<>(this));
    }

    @Override
    public Identifier getTextureLocation(PirateCrewEntity entity) {
        return new Identifier("pixelpirates", "textures/entity/pirate.png");
    }

    @Override
    public void render(PirateCrewEntity entity, float entityYaw, float partialTick,
                       MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight) {
        poseStack.scale(0.9375f, 0.9375f, 0.9375f);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
