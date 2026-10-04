package net.get900.pixelpirates.homestead.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.get900.pixelpirates.homestead.HomesteadItems;
import net.get900.pixelpirates.homestead.darts.DartEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A dart in flight or stuck in the board: the dart's own 3D item model (models/item/dart.json - authored with the
 * tip toward +Z at z=15), turned to point along its yaw/pitch, drawn so the tip sits at the entity position.
 */
@Environment(EnvType.CLIENT)
public class DartRenderer extends EntityRenderer<DartEntity> {
    private static final float SCALE = 0.5f;
    private final ItemRenderer items;
    private final ItemStack stack = new ItemStack(HomesteadItems.DART);

    public DartRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.items = ctx.getItemRenderer();
        this.shadowRadius = 0;
    }

    @Override
    public void render(DartEntity e, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider buf, int light) {
        float y = (float) Math.toRadians(MathHelper.lerp(tickDelta, e.prevYaw, e.getYaw()));
        float p = (float) Math.toRadians(MathHelper.lerp(tickDelta, e.prevPitch, e.getPitch()));
        Vector3f dir = new Vector3f(MathHelper.sin(y) * MathHelper.cos(p), MathHelper.sin(p), MathHelper.cos(y) * MathHelper.cos(p));
        m.push();
        m.translate(-dir.x * (7f / 16 * SCALE - 0.03f), -dir.y * (7f / 16 * SCALE - 0.03f), -dir.z * (7f / 16 * SCALE - 0.03f));
        m.multiply(new Quaternionf().rotationTo(new Vector3f(0, 0, 1), dir));
        m.scale(SCALE, SCALE, SCALE);
        items.renderItem(stack, ModelTransformationMode.NONE, light, OverlayTexture.DEFAULT_UV, m, buf, e.getWorld(), e.getId());
        m.pop();
        super.render(e, yaw, tickDelta, m, buf, light);
    }

    @Override
    public Identifier getTexture(DartEntity e) { return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE; }
}
