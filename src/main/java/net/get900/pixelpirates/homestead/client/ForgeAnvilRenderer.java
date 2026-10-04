package net.get900.pixelpirates.homestead.client;

import net.get900.pixelpirates.homestead.forge.ForgeAnvilBlockEntity;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;

/** The Forge Anvil's load: the piece lying flat along the anvil's face, the materials in a row at its foot. */
public class ForgeAnvilRenderer implements BlockEntityRenderer<ForgeAnvilBlockEntity> {
    public ForgeAnvilRenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public void render(ForgeAnvilBlockEntity be, float tickDelta, MatrixStack m, VertexConsumerProvider buffers, int light, int overlay) {
        if (be.getWorld() == null) return;
        float yaw = -be.getCachedState().get(HorizontalFacingBlock.FACING).asRotation();
        var ir = MinecraftClient.getInstance().getItemRenderer();
        m.push();
        m.translate(0.5, 0.94, 0.5);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        if (!be.piece().isEmpty()) {
            m.push();
            m.translate(0, 0.02, 0);
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(45));
            m.scale(0.75f, 0.75f, 0.75f);
            ir.renderItem(be.piece(), ModelTransformationMode.FIXED, light, overlay, m, buffers, be.getWorld(), 0);
            m.pop();
        }
        int i = 0;
        for (ItemStack s : be.mats()) {
            m.push();
            m.translate(-0.33 + i * 0.22, 0.03, 0.30);
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90));
            m.scale(0.28f, 0.28f, 0.28f);
            ir.renderItem(s, ModelTransformationMode.FIXED, light, overlay, m, buffers, be.getWorld(), 0);
            m.pop();
            i++;
        }
        m.pop();
    }
}
