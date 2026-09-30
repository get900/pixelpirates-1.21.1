package net.get900.pixelpirates.entity.client;

import net.get900.pixelpirates.entity.custom.PowderKegEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.TntMinecartEntityRenderer;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

/** A barrel rolling end-over-end along its heading, flashing white like TNT in its last second. */
public class PowderKegEntityRenderer extends EntityRenderer<PowderKegEntity> {
    private static final BlockState KEG = Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.EAST);
    private final BlockRenderManager blocks;

    public PowderKegEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.4f;
        this.blocks = ctx.getBlockRenderManager();
    }

    @Override
    public void render(PowderKegEntity keg, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vcp, int light) {
        matrices.push();
        matrices.translate(0, 0.4, 0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-keg.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((keg.age + tickDelta) * 22f));
        matrices.scale(0.8f, 0.8f, 0.8f);
        matrices.translate(-0.5, -0.5, -0.5);
        int fuse = keg.getFuse();
        TntMinecartEntityRenderer.renderFlashingBlock(blocks, KEG, matrices, vcp, light, fuse < 20 && fuse / 3 % 2 == 0);
        matrices.pop();
        super.render(keg, yaw, tickDelta, matrices, vcp, light);
    }

    @Override
    public Identifier getTexture(PowderKegEntity entity) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }
}
