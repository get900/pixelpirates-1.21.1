package net.get900.pixelpirates.entity.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.get900.pixelpirates.entity.custom.ThrownRelicEntity;
import net.get900.pixelpirates.entity.custom.TideArrowEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.ProjectileEntityRenderer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** Renderers for the relic weapons' projectiles (item/RelicWeapons). The spectral shot uses FlyingItemEntityRenderer. */
@Environment(EnvType.CLIENT)
public final class RelicProjectileRenderers {
    private RelicProjectileRenderers() {}

    /** The Serpentspine Longbow's tide arrow: a vanilla arrow (it leaves a water trail of its own). */
    public static class TideArrow extends ProjectileEntityRenderer<TideArrowEntity> {
        private static final Identifier TEXTURE = new Identifier("textures/entity/projectiles/arrow.png");

        public TideArrow(EntityRendererFactory.Context ctx) { super(ctx); }

        @Override
        public Identifier getTexture(TideArrowEntity e) { return TEXTURE; }
    }

    /**
     * A thrown relic weapon: its own 3D item model (builtin/entity -> GeckoLib), point (+Y in the geo) turned onto the
     * flight path, at half size.
     */
    public static class Thrown extends EntityRenderer<ThrownRelicEntity> {
        private final ItemRenderer items;

        public Thrown(EntityRendererFactory.Context ctx) {
            super(ctx);
            this.items = ctx.getItemRenderer();
            this.shadowRadius = 0.2f;
        }

        @Override
        public void render(ThrownRelicEntity e, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider buffers, int light) {
            m.push();
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.lerp(tickDelta, e.prevYaw, e.getYaw())));
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90 - MathHelper.lerp(tickDelta, e.prevPitch, e.getPitch())));
            m.scale(0.5f, 0.5f, 0.5f);
            items.renderItem(e.stack(), ModelTransformationMode.NONE, light, OverlayTexture.DEFAULT_UV, m, buffers, e.getWorld(), e.getId());
            m.pop();
            super.render(e, yaw, tickDelta, m, buffers, light);
        }

        @Override
        public Identifier getTexture(ThrownRelicEntity e) { return new Identifier("textures/misc/white.png"); }
    }
}
