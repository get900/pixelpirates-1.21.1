package net.get900.pixelpirates.block.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.get900.pixelpirates.block.custom.GallowbrandStoneBlockEntity;
import net.get900.pixelpirates.entity.client.NamedGeoModel;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** The Gallowbrand in the Stone, drawn at 1.25x (the rock spans ~3 blocks, the blade stands ~5 tall). */
@Environment(EnvType.CLIENT)
public class GallowbrandStoneRenderer extends GeoBlockRenderer<GallowbrandStoneBlockEntity> {
    public static final float SCALE = 1.25f;

    public GallowbrandStoneRenderer() {
        super(new NamedGeoModel<>("gallowbrand_stone"));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void preRender(MatrixStack poseStack, GallowbrandStoneBlockEntity animatable, BakedGeoModel model, VertexConsumerProvider bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
        if (!isReRender) poseStack.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public boolean rendersOutsideBoundingBox(GallowbrandStoneBlockEntity be) { return true; }

    @Override
    public int getRenderDistance() { return 96; }
}
