package net.get900.pixelpirates.entity.client;

import net.get900.pixelpirates.entity.custom.MapMerchantEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class MapMerchantEntityRenderer extends GeoEntityRenderer<MapMerchantEntity> {

    public MapMerchantEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new MapMerchantModel());
    }

    @Override
    public Identifier getTextureLocation(MapMerchantEntity entity) {
        return new Identifier("pixelpirates", "textures/entity/map_merchant.png");
    }

    @Override
    public void render(MapMerchantEntity entity, float entityYaw, float partialTick,
                       MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight) {
        poseStack.scale(1.0f, 1.0f, 1.0f);
        poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0f));
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
