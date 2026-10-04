package net.get900.pixelpirates.homestead.chapel;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * THE CALL TO PRAYER (2026-10-01). Every BELL ROPE (BellRopeBlock) tolls the bells above it each morning at
 * {@link #MORNING} (time of day 1000, about 7 a.m.) and then fires {@link #PRAYER_CALL}. Hook for the coming town NPCs:
 * listen to PRAYER_CALL, or poll {@link #isPrayerTime} / {@link #chapels} - a service lasts {@link #SERVICE_TICKS} after
 * the first toll. Chapels register themselves when their bell rope's block entity loads.
 *
 * <pre>ChapelBells.PRAYER_CALL.register((world, chapel) -> villagersNear(chapel).forEach(npc -> npc.walkTo(chapel)));</pre>
 */
public final class ChapelBells {
    private ChapelBells() {}

    public static final long MORNING = 1000;
    public static final int SERVICE_TICKS = 2400;

    @FunctionalInterface
    public interface PrayerCall { void onCall(ServerWorld world, BlockPos bellRope); }

    public static final Event<PrayerCall> PRAYER_CALL = EventFactory.createArrayBacked(PrayerCall.class, listeners -> (world, pos) -> {
        for (PrayerCall l : listeners) l.onCall(world, pos);
    });

    private static final Map<RegistryKey<World>, Long> LAST_CALL = new HashMap<>();
    private static final Map<RegistryKey<World>, Set<BlockPos>> CHAPELS = new HashMap<>();

    static void register(ServerWorld w, BlockPos pos) { CHAPELS.computeIfAbsent(w.getRegistryKey(), k -> new LinkedHashSet<>()).add(pos.toImmutable()); }

    static void unregister(ServerWorld w, BlockPos pos) { Set<BlockPos> s = CHAPELS.get(w.getRegistryKey()); if (s != null) s.remove(pos); }

    static void call(ServerWorld w, BlockPos pos) {
        LAST_CALL.put(w.getRegistryKey(), w.getTime());
        net.get900.pixelpirates.PixelPirates.LOGGER.info("[Chapel] the morning bell at {} calls the town to prayer", pos.toShortString());
        PRAYER_CALL.invoker().onCall(w, pos);
    }

    /** True for {@link #SERVICE_TICKS} after a morning bell in this world. */
    public static boolean isPrayerTime(ServerWorld w) {
        Long t = LAST_CALL.get(w.getRegistryKey());
        return t != null && w.getTime() - t < SERVICE_TICKS;
    }

    /** Every chapel (bell rope position) known in this world. */
    public static Collection<BlockPos> chapels(ServerWorld w) {
        Set<BlockPos> s = CHAPELS.get(w.getRegistryKey());
        return s == null ? Collections.emptySet() : Collections.unmodifiableSet(s);
    }
}
