package net.get900.pixelpirates.homestead.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.get900.pixelpirates.homestead.beard.Beards;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FACIAL HAIR (client): every player's beard (Beards.SYNC: style shown + colour) drawn on their head - a face layer just
 * over the head (textures/entity/beard/<style>.png, the skin's 64x64 head layout at 4x, white and tinted to the colour)
 * and, for the long styles, a piece hanging below the chin. Turns and nods with the head. {@link #PREVIEW} lets the
 * barber's screen try a style on the local player.
 */
@Environment(EnvType.CLIENT)
public class BeardFeature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    /** uuid -> {style, colour}. */
    public static final Map<UUID, String[]> LOOKS = new ConcurrentHashMap<>();
    /** The barber's screen: a style/colour tried on the local player (null = their own). */
    @Nullable public static String[] PREVIEW;

    private static ModelPart face, hang;

    public BeardFeature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx) {
        super(ctx);
        if (face == null) {
            ModelData md = new ModelData();
            md.getRoot().addChild("face", ModelPartBuilder.create().uv(0, 0).cuboid(-4, -8, -4, 8, 8, 8, new Dilation(0.08f)), ModelTransform.NONE);
            md.getRoot().addChild("hang", ModelPartBuilder.create().uv(0, 32).cuboid(-4, 0, -4.6f, 8, 8, 2), ModelTransform.NONE);
            ModelPart root = TexturedModelData.of(md, 64, 64).createModel();
            face = root.getChild("face");
            hang = root.getChild("hang");
        }
    }

    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(Beards.SYNC, (client, handler, buf, sender) -> {
            UUID who = buf.readUuid();
            String style = buf.readString(32), colour = buf.readString(16);
            client.execute(() -> { if (style.isEmpty()) LOOKS.remove(who); else LOOKS.put(who, new String[]{style, colour}); });
        });
        ClientPlayNetworking.registerGlobalReceiver(Beards.OPEN, (client, handler, buf, sender) -> {
            var n = buf.readNbt();
            client.execute(() -> {
                if (n == null) return;
                if (client.currentScreen instanceof net.get900.pixelpirates.client.screen.BarberScreen s) s.update(n);
                else client.setScreen(new net.get900.pixelpirates.client.screen.BarberScreen(n));
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LOOKS.clear());
    }

    public static Identifier texture(String style) { return new Identifier("pixelpirates", "textures/entity/beard/" + style + ".png"); }

    @Override
    public void render(MatrixStack m, VertexConsumerProvider v, int light, AbstractClientPlayerEntity p, float limbAngle, float limbDistance,
                       float tickDelta, float age, float headYaw, float headPitch) {
        String[] look = LOOKS.get(p.getUuid());
        if (PREVIEW != null && p == net.minecraft.client.MinecraftClient.getInstance().player) look = PREVIEW;
        if (look == null || look[0].isEmpty() || p.isInvisible()) return;
        Beards.Style s = Beards.byId(look[0]);
        if (s == null) return;
        int rgb = Beards.colourRgb(look[1]);
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        var vc = v.getBuffer(RenderLayer.getEntityTranslucent(texture(s.id())));
        m.push();
        getContextModel().head.rotate(m);
        face.render(m, vc, light, OverlayTexture.DEFAULT_UV, r, g, b, 1f);
        if (s.hangs()) hang.render(m, vc, light, OverlayTexture.DEFAULT_UV, r, g, b, 1f);
        m.pop();
    }
}
