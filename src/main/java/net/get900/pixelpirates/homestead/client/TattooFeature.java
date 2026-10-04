package net.get900.pixelpirates.homestead.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.get900.pixelpirates.homestead.tattoo.Tattoos;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TATTOOS (client): every player's tattoos (Tattoos.SYNC) drawn over their model - the model rendered once more per
 * tattoo with its mostly transparent overlay (textures/entity/tattoo/<design>_<slot>.png, 4x the skin), so the ink follows
 * arms and body like the skin does; armour drawn over it hides it. Slim (Alex) arms use the left_arm_slim overlay.
 */
@Environment(EnvType.CLIENT)
public class TattooFeature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    /** uuid -> slot -> design, as the server last told us. */
    public static final Map<UUID, NbtCompound> TATTOOS = new ConcurrentHashMap<>();

    public TattooFeature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx) { super(ctx); }

    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(Tattoos.SYNC, (client, handler, buf, sender) -> {
            UUID who = buf.readUuid();
            NbtCompound t = buf.readNbt();
            client.execute(() -> { if (t == null || t.isEmpty()) TATTOOS.remove(who); else TATTOOS.put(who, t); });
        });
        ClientPlayNetworking.registerGlobalReceiver(Tattoos.OPEN, (client, handler, buf, sender) -> {
            NbtCompound n = buf.readNbt();
            client.execute(() -> {
                if (n == null) return;
                if (client.currentScreen instanceof net.get900.pixelpirates.client.screen.TattooScreen s) s.update(n);
                else client.setScreen(new net.get900.pixelpirates.client.screen.TattooScreen(n));
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> TATTOOS.clear());
        // the cattery counter (homestead/cat/Cattery) - here so the client registrations stay in one place
        ClientPlayNetworking.registerGlobalReceiver(net.get900.pixelpirates.homestead.cat.Cattery.COUNTER_OPEN, (client, handler, buf, sender) -> {
            NbtCompound n = buf.readNbt();
            client.execute(() -> { if (n != null) client.setScreen(new net.get900.pixelpirates.client.screen.CatteryScreen(n)); });
        });
    }

    public static Identifier texture(String design, String slot, boolean slim) {
        String s = slot.equals("left_arm") && slim ? "left_arm_slim" : slot;
        return new Identifier("pixelpirates", "textures/entity/tattoo/" + design + "_" + s + ".png");
    }

    @Override
    public void render(MatrixStack m, VertexConsumerProvider v, int light, AbstractClientPlayerEntity p, float limbAngle, float limbDistance,
                       float tickDelta, float age, float headYaw, float headPitch) {
        NbtCompound t = TATTOOS.get(p.getUuid());
        if (t == null || p.isInvisible()) return;
        boolean slim = "slim".equals(p.getModel());
        for (String slot : Tattoos.SLOTS) {
            String d = t.getString(slot);
            if (d.isEmpty() || Tattoos.byId(d) == null) continue;
            getContextModel().render(m, v.getBuffer(RenderLayer.getEntityTranslucent(texture(d, slot, slim))), light,
                    OverlayTexture.DEFAULT_UV, 1f, 1f, 1f, 1f);
        }
    }
}
