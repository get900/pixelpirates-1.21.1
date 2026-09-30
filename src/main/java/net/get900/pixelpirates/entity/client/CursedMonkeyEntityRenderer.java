package net.get900.pixelpirates.entity.client;

import net.get900.pixelpirates.entity.custom.CursedMonkeyEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class CursedMonkeyEntityRenderer extends GeoEntityRenderer<CursedMonkeyEntity> {
    public CursedMonkeyEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new CursedMonkeyModel());
    }

    @Override
    public Identifier getTextureLocation(CursedMonkeyEntity entity) {
        return new Identifier("pixelpirates", "textures/entity/cursed_monkey.png");
    }

    @Override
    public void render(CursedMonkeyEntity entity, float entityYaw, float partialTick,
                       MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight) {
        poseStack.scale(1.0f, 1.0f, 1.0f);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
