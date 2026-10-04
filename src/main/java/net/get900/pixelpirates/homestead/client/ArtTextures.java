package net.get900.pixelpirates.homestead.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.get900.pixelpirates.homestead.art.Art;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** Players' paintings as textures: one dynamic texture per distinct picture, the 256 most recently drawn kept. */
@Environment(EnvType.CLIENT)
public final class ArtTextures {
    private ArtTextures() {}

    private static final Map<Long, Identifier> CACHE = new LinkedHashMap<>(64, 0.75f, true);

    public static Identifier get(int[] px, int w, int h) {
        long key = ((long) Arrays.hashCode(px) << 16) ^ (w * 131L + h);
        Identifier id = CACHE.get(key);
        if (id != null) return id;
        NativeImage img = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int c = y * w + x < px.length ? px[y * w + x] : Art.WHITE;
                img.setColor(x, y, 0xFF000000 | (c & 0xFF) << 16 | (c & 0xFF00) | (c >> 16 & 0xFF));       // ARGB -> ABGR
            }
        id = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("pp_painting", new NativeImageBackedTexture(img));
        CACHE.put(key, id);
        if (CACHE.size() > 256) {
            var it = CACHE.entrySet().iterator();
            Identifier old = it.next().getValue();
            it.remove();
            MinecraftClient.getInstance().getTextureManager().destroyTexture(old);
        }
        return id;
    }

    /** A picture quad in the XY plane at depth z, facing -Z, from (x0,y0) to (x1,y1) (texture upright, as seen from -Z). */
    public static void quad(MatrixStack m, VertexConsumerProvider v, Identifier tex, float x0, float y0, float x1, float y1, float z, int light) {
        VertexConsumer c = v.getBuffer(RenderLayer.getEntityCutoutNoCull(tex));
        Matrix4f p = m.peek().getPositionMatrix();
        Matrix3f n = m.peek().getNormalMatrix();
        // seen from -Z the viewer's right is -X: the image's left edge (u=0) sits at x1, its right edge at x0
        vert(c, p, n, x1, y1, z, 0, 0, light);
        vert(c, p, n, x0, y1, z, 1, 0, light);
        vert(c, p, n, x0, y0, z, 1, 1, light);
        vert(c, p, n, x1, y0, z, 0, 1, light);
    }

    private static void vert(VertexConsumer c, Matrix4f p, Matrix3f n, float x, float y, float z, float u, float v, int light) {
        c.vertex(p, x, y, z).color(255, 255, 255, 255).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(n, 0, 0, -1).next();
    }
}
