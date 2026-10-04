package net.get900.pixelpirates.homestead.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.get900.pixelpirates.homestead.art.Art;
import net.get900.pixelpirates.homestead.art.CustomPaintingEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** A player's painting on the wall: the picture in a dark wooden frame, the vanilla painting back behind it. */
@Environment(EnvType.CLIENT)
public class CustomPaintingRenderer extends EntityRenderer<CustomPaintingEntity> {
    private static final Identifier BACK = new Identifier("minecraft", "textures/painting/back.png");
    private static final Identifier FRAME = new Identifier("minecraft", "textures/block/dark_oak_planks.png");

    public CustomPaintingRenderer(EntityRendererFactory.Context ctx) { super(ctx); }

    @Override
    public void render(CustomPaintingEntity e, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider v, int light) {
        NbtCompound art = e.art();
        Art.Size s = e.size();
        int[] px = art.getIntArray("Pixels");
        if (px.length != s.pw * s.ph) px = Art.blank(s);
        m.push();
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - yaw));
        float w = s.bw / 2f, h = s.bh / 2f, f = 1 / 16f;
        // the picture (inside a 1-pixel frame), the frame round it, the back
        ArtTextures.quad(m, v, ArtTextures.get(px, s.pw, s.ph), -w + f, -h + f, w - f, h - f, -0.5f / 16f - 0.001f, light);
        ArtTextures.quad(m, v, FRAME, -w, h - f, w, h, -0.5f / 16f - 0.002f, light);
        ArtTextures.quad(m, v, FRAME, -w, -h, w, -h + f, -0.5f / 16f - 0.002f, light);
        ArtTextures.quad(m, v, FRAME, -w, -h, -w + f, h, -0.5f / 16f - 0.002f, light);
        ArtTextures.quad(m, v, FRAME, w - f, -h, w, h, -0.5f / 16f - 0.002f, light);
        ArtTextures.quad(m, v, BACK, -w, -h, w, h, 0.5f / 16f, light);
        m.pop();
        super.render(e, yaw, tickDelta, m, v, light);
    }

    @Override
    public Identifier getTexture(CustomPaintingEntity e) { return BACK; }
}
