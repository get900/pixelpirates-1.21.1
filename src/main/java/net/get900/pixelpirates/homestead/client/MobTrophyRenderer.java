package net.get900.pixelpirates.homestead.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.homestead.trophy.MobTrophyBlock;
import net.get900.pixelpirates.homestead.trophy.MobTrophyBlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Draws a trophy's mob with that mob's own geo/texture/animation, shrunk and framed on the plaque (MobTrophyBlock.Mount). */
@Environment(EnvType.CLIENT)
public class MobTrophyRenderer extends GeoBlockRenderer<MobTrophyBlockEntity> {
    public MobTrophyRenderer(BlockEntityRendererFactory.Context ctx) {
        super(new GeoModel<>() {
            private String mob(MobTrophyBlockEntity be) { var m = be.mount(); return m == null ? "shark" : m.mob(); }
            @Override public Identifier getModelResource(MobTrophyBlockEntity be) { return new Identifier(PixelPirates.MOD_ID, "geo/" + mob(be) + ".geo.json"); }
            @Override public Identifier getTextureResource(MobTrophyBlockEntity be) { return new Identifier(PixelPirates.MOD_ID, "textures/entity/" + mob(be) + ".png"); }
            @Override public Identifier getAnimationResource(MobTrophyBlockEntity be) { return new Identifier(PixelPirates.MOD_ID, "animations/" + mob(be) + ".animation.json"); }
        });
        // glowing parts (eyes, lures) only for mobs that ship a glowmask - an empty mask crashes GeckoLib (crash cause #8)
        addRenderLayer(new AutoGlowingGeoLayer<>(this) {
            @Override
            public void render(MatrixStack poseStack, MobTrophyBlockEntity animatable, software.bernie.geckolib.cache.object.BakedGeoModel bakedModel,
                               net.minecraft.client.render.RenderLayer renderType, net.minecraft.client.render.VertexConsumerProvider bufferSource,
                               net.minecraft.client.render.VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
                var m = animatable.mount();
                if (m != null && hasGlow(m.mob())) super.render(poseStack, animatable, bakedModel, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
            }
        });
    }

    private static final java.util.Map<String, Boolean> GLOW = new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean hasGlow(String mob) {
        return GLOW.computeIfAbsent(mob, n -> net.minecraft.client.MinecraftClient.getInstance().getResourceManager()
                .getResource(new Identifier(PixelPirates.MOD_ID, "textures/entity/" + n + "_glowmask.png")).isPresent());
    }

    /** After GeckoLib's facing turn (model -Z = out of the wall): lift to the plaque, push toward the wall, turn side-on,
     *  shrink, and centre the model. */
    @Override
    protected void rotateBlock(Direction facing, MatrixStack poseStack) {
        super.rotateBlock(facing, poseStack);
        MobTrophyBlock.Mount m = this.animatable == null ? null : this.animatable.mount();     // set by GeoBlockRenderer.render
        if (m == null) return;
        poseStack.translate(0, 0.5, m.depth());
        if (m.profile()) poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90));
        poseStack.scale(m.scale(), m.scale(), m.scale());
        poseStack.translate(-m.cx() / 16f, -m.cy() / 16f, -m.cz() / 16f);
    }
}
