package net.get900.pixelpirates.homestead.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.get900.pixelpirates.homestead.art.Art;
import net.get900.pixelpirates.homestead.art.EaselBlock;
import net.get900.pixelpirates.homestead.art.EaselBlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

/** The canvas on an easel: the work in progress, painted on the board's front (the model's board: x0..16, y13..29, front z 6). */
@Environment(EnvType.CLIENT)
public class EaselRenderer implements BlockEntityRenderer<EaselBlockEntity> {
    public EaselRenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public void render(EaselBlockEntity be, float tickDelta, MatrixStack m, VertexConsumerProvider v, int light, int overlay) {
        if (be.pixels == null) return;                                             // a blank board shows the model's canvas
        Art.Size s = be.size;
        Direction d = be.getCachedState().get(EaselBlock.FACING);
        m.push();
        m.translate(0.5, 0, 0.5);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-d.asRotation() + 180));
        // model space (facing north): the board's front face at z = 6/16 - 0.5 from the centre, spanning 16 x 16 px
        float side = 14 / 16f, bw = side * s.pw / 32f, bh = side * s.ph / 32f, cy = 21 / 16f;
        ArtTextures.quad(m, v, ArtTextures.get(be.pixels, s.pw, s.ph), -bw / 2, cy - bh / 2, bw / 2, cy + bh / 2, 6 / 16f - 0.5f - 0.003f, light);
        m.pop();
    }
}
