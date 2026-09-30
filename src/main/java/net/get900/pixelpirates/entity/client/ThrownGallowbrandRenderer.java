package net.get900.pixelpirates.entity.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.get900.pixelpirates.entity.custom.ThrownGallowbrandEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * The hurled Gallowbrand: the item's geo (blade up +Y, flats facing +/-X), turned onto the flight heading so the
 * `spin` clip (about X) tumbles it end over end along its path. Stuck in a block, it stands point-down at the angle
 * it flew in.
 */
@Environment(EnvType.CLIENT)
public class ThrownGallowbrandRenderer extends GeoEntityRenderer<ThrownGallowbrandEntity> {
    public ThrownGallowbrandRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new NamedGeoModel<>("gallowbrand"));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0.25f;
    }

    @Override
    public void render(ThrownGallowbrandEntity e, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider buffers, int light) {
        m.push();
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.lerp(tickDelta, e.prevYaw, e.getYaw())));
        if (e.stuck()) {                                   // buried point-first
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180 - 30 - MathHelper.lerp(tickDelta, e.prevPitch, e.getPitch())));
            m.translate(0, -1.2, 0);
        } else {
            m.translate(0, -0.35, 0);                       // spin about the balance point, level with the hitbox
        }
        super.render(e, 0, tickDelta, m, buffers, light);
        m.pop();
    }
}
