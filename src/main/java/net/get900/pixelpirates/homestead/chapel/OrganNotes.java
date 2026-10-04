package net.get900.pixelpirates.homestead.chapel;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.get900.pixelpirates.homestead.HomesteadBlocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * THE PLAYABLE ORGAN (2026-10-01). The console's screen (client OrganScreen) sends NOTE packets (pos, note 0..24, stop);
 * the server plays them at the console for everyone around: note 0 = F#3 .. 24 = F#5 (the note-block range, pitch
 * 2^((n-12)/12)). STOPS: 0 Diapason (our synthesized organ pipe, tools/gen_chapel_assets.py), 1 Flute, 2 Chimes. HYMN
 * plays "Amazing Grace" (public domain) on the chosen stop. Rate-limited to 24 notes a second per player.
 */
public final class OrganNotes {
    private OrganNotes() {}

    public static final Identifier NOTE = new Identifier("pixelpirates", "organ_note");
    public static final Identifier HYMN = new Identifier("pixelpirates", "organ_hymn");
    public static final SoundEvent DIAPASON = Registry.register(Registries.SOUND_EVENT, new Identifier("pixelpirates", "block.organ.diapason"),
            SoundEvent.of(new Identifier("pixelpirates", "block.organ.diapason")));

    private static final Map<UUID, long[]> RATE = new HashMap<>();
    private record Queued(ServerWorld world, BlockPos pos, int note, int stop, long at) {}
    private static final List<Queued> QUEUE = new ArrayList<>();

    /** Amazing Grace (John Newton / "New Britain", public domain), in G: {note, beats}; quarter beat = 6 ticks. */
    private static final int[][] AMAZING_GRACE = {
            {8, 1}, {13, 2}, {17, 1}, {13, 1}, {17, 2}, {15, 1}, {13, 2}, {10, 1}, {8, 2}, {8, 1},
            {13, 2}, {17, 1}, {13, 1}, {17, 2}, {15, 1}, {20, 5}, {17, 1},
            {20, 2}, {17, 1}, {20, 1}, {17, 1}, {13, 1}, {8, 2}, {10, 1}, {13, 1}, {13, 1}, {10, 1}, {8, 2}, {8, 1},
            {13, 2}, {17, 1}, {13, 1}, {17, 2}, {15, 1}, {13, 5}};

    public static void registerNetworking() {
        ServerPlayNetworking.registerGlobalReceiver(NOTE, (server, player, handler, buf, sender) -> {
            BlockPos pos = buf.readBlockPos();
            int note = buf.readVarInt(), stop = buf.readVarInt();
            server.execute(() -> { if (atConsole(player, pos) && allowed(player)) play(player.getServerWorld(), pos, note, stop); });
        });
        ServerPlayNetworking.registerGlobalReceiver(HYMN, (server, player, handler, buf, sender) -> {
            BlockPos pos = buf.readBlockPos();
            int stop = buf.readVarInt();
            server.execute(() -> { if (atConsole(player, pos)) hymn(player.getServerWorld(), pos, stop); });
        });
    }

    private static boolean atConsole(ServerPlayerEntity p, BlockPos pos) {
        return p.squaredDistanceTo(Vec3d.ofCenter(pos)) < 8 * 8 && p.getServerWorld().getBlockState(pos).isOf(HomesteadBlocks.ORGAN_CONSOLE);
    }

    private static boolean allowed(ServerPlayerEntity p) {
        long t = p.getServer().getTicks();
        long[] r = RATE.computeIfAbsent(p.getUuid(), k -> new long[]{t, 0});
        if (t - r[0] >= 20) { r[0] = t; r[1] = 0; }
        return ++r[1] <= 24;
    }

    public static void play(ServerWorld w, BlockPos pos, int note, int stop) {
        note = Math.max(0, Math.min(24, note));
        float pitch = (float) Math.pow(2, (note - 12) / 12.0);
        SoundEvent s = switch (stop) { case 1 -> SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value(); case 2 -> SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(); default -> DIAPASON; };
        w.playSound(null, pos, s, SoundCategory.RECORDS, stop == 0 ? 2.5f : 2.0f, pitch);
        w.spawnParticles(ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5, 0, note / 24.0, 0, 0, 1);
    }

    public static void hymn(ServerWorld w, BlockPos pos, int stop) {
        long t = w.getServer().getTicks() + 2;
        for (Queued q : QUEUE) if (q.pos.equals(pos)) return;           // already playing here
        for (int[] n : AMAZING_GRACE) { QUEUE.add(new Queued(w, pos, n[0], stop, t)); t += n[1] * 6L; }
    }

    public static void tick(MinecraftServer server) {
        if (QUEUE.isEmpty()) return;
        long now = server.getTicks();
        for (Iterator<Queued> it = QUEUE.iterator(); it.hasNext(); ) {
            Queued q = it.next();
            if (q.at > now) continue;
            it.remove();
            if (q.world.getBlockState(q.pos).isOf(HomesteadBlocks.ORGAN_CONSOLE)) play(q.world, q.pos, q.note, q.stop);
        }
    }

    public static void init() { }
}
