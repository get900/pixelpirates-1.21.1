package net.get900.pixelpirates.entity.client;

import net.get900.pixelpirates.entity.custom.ChestCrabEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class ChestCrabEntityRenderer extends GeoEntityRenderer<ChestCrabEntity> {
    public ChestCrabEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new ChestCrabModel());
    }

    @Override
    public Identifier getTextureLocation(ChestCrabEntity entity) {
        return new Identifier("pixelpirates", "textures/entity/chestcrab.png");
    }

    @Override
    public void render(ChestCrabEntity entity, float entityYaw, float partialTick,
                       MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight) {
        poseStack.scale(1.0f, 1.0f, 1.0f);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
