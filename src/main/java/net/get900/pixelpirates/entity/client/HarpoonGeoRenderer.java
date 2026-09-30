package net.get900.pixelpirates.entity.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * The 3D harpoon model (tools/mobs/kraken.py `harpoon`, point toward -Z) aimed along the projectile's flight.
 * GeckoLib turns a non-living entity by 180 (body yaw 0) so the point faces +Z; we then pitch and yaw it onto the
 * entity's rotation (ProjectileEntity convention: yaw = atan2(vx, vz), pitch = atan2(vy, horizontal)).
 * Used by the Kraken's spat harpoons and the whalers' winch harpoons.
 */
@Environment(EnvType.CLIENT)
public class HarpoonGeoRenderer<T extends Entity & GeoAnimatable> extends GeoEntityRenderer<T> {
    public HarpoonGeoRenderer(EntityRendererFactory.Context ctx, float scale) {
        super(ctx, new NamedGeoModel<>("harpoon"));
        withScale(scale);
        this.shadowRadius = 0.2f;
    }

    @Override
    public void render(T e, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider buffers, int light) {
        m.push();
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.lerp(tickDelta, e.prevYaw, e.getYaw())));
        m.multiply(RotationAxis.NEGATIVE_X.rotationDegrees(MathHelper.lerp(tickDelta, e.prevPitch, e.getPitch())));
        super.render(e, 0, tickDelta, m, buffers, light);
        m.pop();
    }
}
