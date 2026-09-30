package net.get900.pixelpirates.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.get900.pixelpirates.entity.mob.LeviathanEntity;
import net.get900.pixelpirates.entity.mob.LeviathanWakeEntity;
import net.minecraft.entity.Entity;

/**
 * THE ECLIPSE (Leviathan phases 2 and 3, and its flight between them): near it the world goes blood-red and the fog
 * closes in. {@link #strength} eases 0..1 with distance to the nearest eclipsing Leviathan (or its wake);
 * BackgroundRendererMixin tints the fog and the sky's clear colour with it.
 */
@Environment(EnvType.CLIENT)
public final class LeviathanClient {
    private LeviathanClient() {}

    public static volatile float strength;
    /** A Leviathan is close: the zone's blizzard/mist fog steps aside so you can see the whole of it. */
    public static volatile boolean near;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            float target = 0;
            boolean close = false;
            if (client.world != null && client.player != null) {
                for (Entity e : client.world.getEntities()) {
                    if ((e instanceof LeviathanEntity || e instanceof LeviathanWakeEntity) && e.squaredDistanceTo(client.player) < 200 * 200) close = true;
                    boolean ec = e instanceof LeviathanEntity l && l.eclipse() || e instanceof LeviathanWakeEntity w && w.eclipse();
                    if (!ec) continue;
                    double d = Math.sqrt(e.squaredDistanceTo(client.player));
                    target = Math.max(target, (float) Math.max(0, Math.min(1, (220 - d) / 80)));
                }
            }
            near = close;
            strength += (target - strength) * 0.03f;
            if (strength < 0.002f) strength = 0;
        });
    }
}
