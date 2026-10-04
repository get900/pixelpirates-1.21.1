package net.get900.pixelpirates.homestead.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.get900.pixelpirates.homestead.nav.Telescopes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Looking through a telescope (client): while {@link #active}, the local player counts as using a spyglass (scope overlay,
 * zoom, slow aim - TelescopeSpyglassMixin), the FOV is cut by the extra {@link #zoom} (TelescopeFovMixin) and the scroll
 * wheel steps it (TelescopeScrollMixin). TelescopeFovMixin does the whole zoom itself: vanilla only zooms a spyglass held up. Ends on sneak, moving off the spot, attack/use, or opening a screen.
 */
@Environment(EnvType.CLIENT)
public final class TelescopeView {
    private TelescopeView() {}

    public static boolean active;
    /** Extra zoom over the spyglass's: 1, 2 or 4 (x10 / x20 / x40 shown). */
    public static int zoom = 1;
    private static Vec3d at = Vec3d.ZERO;
    private static int grace;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(Telescopes.START, (client, handler, buf, sender) -> {
            buf.readBlockPos();
            client.execute(() -> {
                if (client.player == null) return;
                active = true; zoom = 1; at = client.player.getPos(); grace = 6;
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(Telescopes.STOP, (client, handler, buf, sender) -> client.execute(() -> active = false));
        ClientTickEvents.END_CLIENT_TICK.register(c -> {
            if (!active) return;
            if (grace > 0) grace--;
            boolean leave = c.player == null || c.player.isSneaking() || c.player.getPos().squaredDistanceTo(at) > 0.6 * 0.6 || c.currentScreen != null
                    || (grace == 0 && (c.options.attackKey.isPressed() || c.options.useKey.isPressed()));
            if (leave) stop();
        });
        HudRenderCallback.EVENT.register((ctx, delta) -> {
            var c = MinecraftClient.getInstance();
            if (!active || c.player == null) return;
            float yaw = MathHelper.wrapDegrees(c.player.getYaw());
            int bearing = Math.floorMod(Math.round(yaw + 180), 360);                  // 0 = north
            String[] pts = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
            String b = pts[Math.floorMod(Math.round(bearing / 45f), 8)] + "  " + bearing;                 // (no degree sign - unicode renders as a box)
            int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
            ctx.drawCenteredTextWithShadow(c.textRenderer, b, w / 2, 14, 0xFFF4E2B8);
            ctx.drawCenteredTextWithShadow(c.textRenderer, "x" + 10 * zoom + "   scroll to zoom - sneak to step back", w / 2, h - 52, 0xFFC8B890);
        });
    }

    public static void scroll(double amount) {
        if (amount > 0 && zoom < 4) zoom *= 2;
        else if (amount < 0 && zoom > 1) zoom /= 2;
    }

    public static void stop() {
        if (!active) return;
        active = false;
        ClientPlayNetworking.send(Telescopes.STOP, PacketByteBufs.empty());
    }
}
