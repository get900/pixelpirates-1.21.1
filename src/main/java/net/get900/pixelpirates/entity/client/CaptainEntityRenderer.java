package net.get900.pixelpirates.entity.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.get900.pixelpirates.entity.custom.CaptainEntity;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.ItemArmorGeoLayer;

public class CaptainEntityRenderer extends GeoEntityRenderer<CaptainEntity> {

    private static final Identifier WHITE_TEX =
            new Identifier("minecraft", "textures/block/white_concrete.png");

    // Max distance (blocks²) at which the health bar is visible
    private static final double BAR_RANGE_SQ = 48.0 * 48.0;

    public CaptainEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new CaptainModel());
        this.addRenderLayer(new ItemArmorGeoLayer<>(this));
    }

    // ── Draw health bar below the nametag ─────────────────────────────────────

    @Override
    protected void renderLabelIfPresent(CaptainEntity entity, Text text, MatrixStack matrices,
                                         VertexConsumerProvider vertexConsumers, int light) {
        if (this.dispatcher.getSquaredDistanceToCamera(entity) < BAR_RANGE_SQ) {
            renderHealthBar(entity, matrices, vertexConsumers);
        }
        super.renderLabelIfPresent(entity, text, matrices, vertexConsumers, light);
    }

    /**
     * Renders a billboard health bar in world space below the entity's nametag.
     *
     * Coordinate note: the matrix is translated to entity-origin (0,0,0), so we manually
     * translate to just above the entity head and apply the camera billboard rotation.
     * The -0.025f Y scale inverts the Y axis, so positive Y in scaled space moves down
     * in world space — placing barY=12 below the nametag baseline.
     */
    private void renderHealthBar(CaptainEntity entity, MatrixStack matrices,
                                  VertexConsumerProvider vertexConsumers) {
        float fraction = Math.max(0f, entity.getHealth() / entity.getMaxHealth());

        matrices.push();
        matrices.translate(0.0, entity.getHeight() + 0.5, 0.0);
        matrices.multiply(this.dispatcher.getRotation());
        matrices.scale(-0.025f, -0.025f, 0.025f);

        Matrix4f mat = matrices.peek().getPositionMatrix();

        // Use full brightness so the bar is visible in dark areas
        int fullBright = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(WHITE_TEX));

        float barW = 42.0f;
        float barH = 5.0f;
        float x0   = -barW / 2f;
        // barY > 0 = moves downward in world space (due to inverted Y scale)
        float barY  = 12.0f;

        // Dark background
        drawQuad(vc, mat, x0,            x0 + barW,
                          barY,          barY + barH, 0f,
                          20, 20, 20, 255, fullBright);

        // Health fill: red at 0 %, green at 100 %
        int r = (int)(255 * Math.min(1f, 2f * (1f - fraction)));
        int g = (int)(255 * Math.min(1f, 2f * fraction));
        drawQuad(vc, mat, x0 + 0.5f,     x0 + 0.5f + (barW - 1f) * fraction,
                          barY + 0.5f,   barY + barH - 0.5f, 0.1f,
                          r, g, 0, 255, fullBright);

        matrices.pop();
    }

    private static void drawQuad(VertexConsumer vc, Matrix4f mat,
                                  float x0, float x1, float y0, float y1, float z,
                                  int r, int g, int b, int a, int light) {
        vc.vertex(mat, x0, y0, z).color(r, g, b, a).texture(0f, 0f)
          .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0f, 0f, 1f).next();
        vc.vertex(mat, x0, y1, z).color(r, g, b, a).texture(0f, 1f)
          .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0f, 0f, 1f).next();
        vc.vertex(mat, x1, y1, z).color(r, g, b, a).texture(1f, 1f)
          .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0f, 0f, 1f).next();
        vc.vertex(mat, x1, y0, z).color(r, g, b, a).texture(1f, 0f)
          .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(0f, 0f, 1f).next();
    }
}
