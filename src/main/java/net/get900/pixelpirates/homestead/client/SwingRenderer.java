package net.get900.pixelpirates.homestead.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.BakedModelManagerHelper;
import net.get900.pixelpirates.homestead.swing.SwingBlock;
import net.get900.pixelpirates.homestead.swing.SwingBlockEntity;
import net.get900.pixelpirates.homestead.swing.SwingSeatEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

/** While someone swings, the ropes + seat (model block/swing_seat_moving) drawn at the swing's angle, read off their seat. */
@Environment(EnvType.CLIENT)
public class SwingRenderer implements BlockEntityRenderer<SwingBlockEntity> {
    public static final Identifier SEAT_MODEL = new Identifier("pixelpirates", "block/swing_seat_moving");

    public SwingRenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public void render(SwingBlockEntity be, float tickDelta, MatrixStack m, VertexConsumerProvider v, int light, int overlay) {
        BlockState st = be.getCachedState();
        if (!(st.getBlock() instanceof SwingBlock) || !st.get(SwingBlock.OCCUPIED) || be.getWorld() == null) return;
        Direction f = st.get(SwingBlock.FACING);
        double th = 0;
        var seats = be.getWorld().getEntitiesByClass(SwingSeatEntity.class, new Box(be.getPos()).expand(3), e -> true);
        if (!seats.isEmpty()) th = SwingSeatEntity.angle(be.getPos(), f, seats.get(0).getLerpedPos(tickDelta));
        BakedModel model = BakedModelManagerHelper.getModel(MinecraftClient.getInstance().getBakedModelManager(), SEAT_MODEL);
        if (model == null) return;
        m.push();
        m.translate(0.5, 0, 0.5);
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-f.asRotation() + 180));
        m.translate(0, SwingSeatEntity.PIVOT_Y, 0);
        m.multiply(RotationAxis.POSITIVE_X.rotation((float) th));
        m.translate(-0.5, -SwingSeatEntity.PIVOT_Y, -0.5);
        MinecraftClient.getInstance().getBlockRenderManager().getModelRenderer().render(m.peek(), v.getBuffer(RenderLayer.getCutout()), st, model,
                1f, 1f, 1f, light, OverlayTexture.DEFAULT_UV);
        m.pop();
    }
}
