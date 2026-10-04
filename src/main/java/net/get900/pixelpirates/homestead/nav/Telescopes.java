package net.get900.pixelpirates.homestead.nav;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.get900.pixelpirates.homestead.furniture.TelescopeBlock;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * THE TELESCOPE (2026-10-04, the user: "make the telescope something that can actually be used"). Use a telescope block
 * to put your eye to it: a scope view zoomed well past a spyglass (the scroll wheel steps x10 / x20 / x40), a compass
 * bearing, and - like the Captain's Spyglass - the name, health and range of a creature under the crosshair (to 320
 * blocks) or the flag and hull of a ship (to 480). Sneak, walk away or use anything to step back. The view is client
 * side (client/TelescopeView + three small client mixins); the server keeps the session and does the reading.
 */
public final class Telescopes {
    private Telescopes() {}

    public static final Identifier START = new Identifier("pixelpirates", "telescope_start");
    public static final Identifier STOP = new Identifier("pixelpirates", "telescope_stop");
    private static final Map<UUID, BlockPos> LOOKING = new HashMap<>();

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(STOP, (server, player, handler, buf, sender) -> server.execute(() -> stop(player, false)));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (LOOKING.isEmpty() || server.getTicks() % 5 != 0) return;
            LOOKING.entrySet().removeIf(e -> {
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
                if (p == null) return true;
                BlockPos t = e.getValue();
                if (!(p.getServerWorld().getBlockState(t).getBlock() instanceof TelescopeBlock) || p.squaredDistanceTo(Vec3d.ofCenter(t)) > 4 * 4) {
                    ServerPlayNetworking.send(p, STOP, PacketByteBufs.empty());
                    return true;
                }
                CaptainsSpyglassItem.identify(p.getServerWorld(), p, 320, 480);
                return false;
            });
        });
    }

    /** The telescope was used: look through it. */
    public static void start(ServerPlayerEntity p, BlockPos telescope) {
        LOOKING.put(p.getUuid(), telescope.toImmutable());
        var buf = PacketByteBufs.create();
        buf.writeBlockPos(telescope);
        ServerPlayNetworking.send(p, START, buf);
        p.getServerWorld().playSound(null, telescope, SoundEvents.ITEM_SPYGLASS_USE, SoundCategory.PLAYERS, 1f, 0.8f);
    }

    static void stop(ServerPlayerEntity p, boolean tell) {
        if (LOOKING.remove(p.getUuid()) != null) p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ITEM_SPYGLASS_STOP_USING, SoundCategory.PLAYERS, 1f, 0.8f);
        if (tell) ServerPlayNetworking.send(p, STOP, PacketByteBufs.empty());
    }
}
