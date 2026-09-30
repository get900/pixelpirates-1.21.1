package net.get900.pixelpirates.homestead.client;

import net.get900.pixelpirates.homestead.grapple.GrappleHookEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** The hook as its item sprite, plus a sagging rope back to the thrower's hand (a line strip like the fishing line). */
public class GrappleHookRenderer extends FlyingItemEntityRenderer<GrappleHookEntity> {
    public GrappleHookRenderer(EntityRendererFactory.Context ctx) { super(ctx, 1.0f, true); }

    @Override
    public void render(GrappleHookEntity hook, float yaw, float tickDelta, MatrixStack ms, VertexConsumerProvider vcp, int light) {
        super.render(hook, yaw, tickDelta, ms, vcp, light);
        Entity o = hook.getOwner();
        if (!(o instanceof PlayerEntity p)) return;
        float bodyYaw = MathHelper.lerp(tickDelta, p.prevBodyYaw, p.bodyYaw) * ((float) Math.PI / 180f);
        int side = p.getMainArm() == Arm.RIGHT ? 1 : -1;
        double sin = MathHelper.sin(bodyYaw), cos = MathHelper.cos(bodyYaw);
        Vec3d hand = p.getLerpedPos(tickDelta).add(-cos * 0.35 * side - sin * 0.3, p.isInSneakingPose() ? 0.9 : 1.1, -sin * 0.35 * side + cos * 0.3);
        Vec3d at = hook.getLerpedPos(tickDelta);
        float dx = (float) (hand.x - at.x), dy = (float) (hand.y - at.y), dz = (float) (hand.z - at.z);
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getLineStrip());
        Matrix4f m = ms.peek().getPositionMatrix();
        float sag = hook.anchored() ? 0.15f : 0.5f;
        int n = 16;
        for (int i = 0; i <= n; i++) {
            float t = i / (float) n;
            float x = dx * t, y = dy * t - sag * 4 * t * (1 - t), z = dz * t;
            float t2 = Math.min(1, (i + 1) / (float) n);
            float nx = dx * t2 - x, ny = dy * t2 - sag * 4 * t2 * (1 - t2) - y, nz = dz * t2 - z;
            float len = MathHelper.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-4f) len = 1;
            vc.vertex(m, x, y, z).color(92, 68, 40, 255).normal(ms.peek().getNormalMatrix(), nx / len, ny / len, nz / len).next();
        }
    }
}
