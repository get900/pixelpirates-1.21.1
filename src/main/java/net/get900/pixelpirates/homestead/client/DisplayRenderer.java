package net.get900.pixelpirates.homestead.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.get900.pixelpirates.homestead.furniture.DisplayBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

/** The treasure pedestal's item, turning slowly and bobbing above the velvet. */
@Environment(EnvType.CLIENT)
public class DisplayRenderer implements BlockEntityRenderer<DisplayBlockEntity> {
    public DisplayRenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public void render(DisplayBlockEntity be, float tickDelta, MatrixStack m, VertexConsumerProvider buffers, int light, int overlay) {
        if (be.item().isEmpty() || be.getWorld() == null) return;
        float t = be.getWorld().getTime() + tickDelta;
        m.push();
        m.translate(0.5, 1.05 + Math.sin(t / 12.0) * 0.04, 0.5);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(t * 2.0f));
        m.scale(0.6f, 0.6f, 0.6f);
        MinecraftClient.getInstance().getItemRenderer().renderItem(be.item(), ModelTransformationMode.FIXED, light, overlay, m, buffers, be.getWorld(), 0);
        m.pop();
    }
}
