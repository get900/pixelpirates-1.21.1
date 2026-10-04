package net.get900.pixelpirates.homestead.town;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * Tunes the townsfolk play (2026-10-04, round 3). Format {note-block note 0..24 (F#3 = 0), length in ticks}.
 *  DRUNKEN_SAILOR - traditional (public domain), Fiddler Dan's favourite (the same arrangement as the wreck's music box).
 *  SALT_AND_THUNDER - an ORIGINAL jig written for Pixel Pirates (the user asked for "a version of Thick of It" - that song
 *                     is copyrighted, so this is our own tune in its place).
 *  JOYFUL - "Joyful, Joyful, We Adore Thee" (Beethoven's Ode to Joy, public domain), a hymn for the organ.
 *  BRIDAL - the Bridal Chorus ("Here Comes the Bride", Wagner 1850, public domain), for weddings.
 * Played note by note at a spot (a player near hears it), with note particles.
 */
public final class TownMusic {
    private TownMusic() {}

    public static final int[][] DRUNKEN_SAILOR = {
            {15, 6}, {15, 3}, {15, 3}, {15, 6}, {15, 3}, {15, 3}, {15, 6}, {8, 6}, {11, 6}, {15, 6},
            {13, 6}, {13, 3}, {13, 3}, {13, 6}, {13, 3}, {13, 3}, {13, 6}, {6, 6}, {10, 6}, {13, 6},
            {15, 6}, {15, 3}, {15, 3}, {15, 6}, {15, 3}, {15, 3}, {15, 6}, {17, 6}, {18, 6}, {20, 6},
            {18, 6}, {15, 3}, {13, 3}, {10, 6}, {8, 6}, {8, 12},
            {15, 12}, {15, 6}, {15, 3}, {15, 3}, {15, 6}, {8, 6}, {11, 6}, {15, 6},
            {13, 12}, {13, 6}, {13, 3}, {13, 3}, {13, 6}, {6, 6}, {10, 6}, {13, 6},
            {15, 12}, {15, 6}, {15, 3}, {15, 3}, {15, 6}, {17, 6}, {18, 6}, {20, 6},
            {18, 6}, {15, 3}, {13, 3}, {10, 6}, {8, 6}, {8, 12}};

    /** Amazing Grace (John Newton / "New Britain", public domain), in G - the chapel's own arrangement (OrganNotes). */
    public static final int[][] AMAZING_GRACE = {
            {8, 6}, {13, 12}, {17, 6}, {13, 6}, {17, 12}, {15, 6}, {13, 12}, {10, 6}, {8, 12}, {8, 6},
            {13, 12}, {17, 6}, {13, 6}, {17, 12}, {15, 6}, {20, 30}, {17, 6},
            {20, 12}, {17, 6}, {20, 6}, {17, 6}, {13, 6}, {8, 12}, {10, 6}, {13, 6}, {13, 6}, {10, 6}, {8, 12}, {8, 6},
            {13, 12}, {17, 6}, {13, 6}, {17, 12}, {15, 6}, {13, 30}};

    /** Our own jig, in G. */
    public static final int[][] SALT_AND_THUNDER = {
            {13, 4}, {13, 2}, {15, 2}, {17, 4}, {13, 4}, {20, 4}, {17, 4}, {15, 8},
            {15, 4}, {15, 2}, {17, 2}, {18, 4}, {15, 4}, {20, 4}, {18, 4}, {17, 8},
            {13, 4}, {17, 4}, {20, 4}, {22, 4}, {20, 4}, {17, 4}, {15, 4}, {13, 4},
            {8, 4}, {12, 4}, {15, 4}, {12, 4}, {13, 12},
            {20, 2}, {22, 2}, {20, 4}, {17, 4}, {20, 2}, {22, 2}, {20, 4}, {17, 4},
            {18, 4}, {17, 4}, {15, 4}, {12, 4}, {13, 4}, {15, 4}, {17, 8},
            {20, 2}, {22, 2}, {20, 4}, {17, 4}, {22, 4}, {20, 4}, {18, 4}, {17, 4},
            {15, 4}, {12, 4}, {8, 4}, {12, 4}, {13, 12}};

    /** Ode to Joy in G (quarter = 8 ticks - hymn pace). */
    public static final int[][] JOYFUL = {
            {17, 8}, {17, 8}, {18, 8}, {20, 8}, {20, 8}, {18, 8}, {17, 8}, {15, 8}, {13, 8}, {13, 8}, {15, 8}, {17, 8}, {17, 12}, {15, 4}, {15, 16},
            {17, 8}, {17, 8}, {18, 8}, {20, 8}, {20, 8}, {18, 8}, {17, 8}, {15, 8}, {13, 8}, {13, 8}, {15, 8}, {17, 8}, {15, 12}, {13, 4}, {13, 16},
            {15, 8}, {15, 8}, {17, 8}, {13, 8}, {15, 8}, {17, 4}, {18, 4}, {17, 8}, {13, 8}, {15, 8}, {17, 4}, {18, 4}, {17, 8}, {15, 8}, {13, 8}, {15, 8}, {8, 16},
            {17, 8}, {17, 8}, {18, 8}, {20, 8}, {20, 8}, {18, 8}, {17, 8}, {15, 8}, {13, 8}, {13, 8}, {15, 8}, {17, 8}, {15, 12}, {13, 4}, {13, 16}};

    /** The Bridal Chorus, in G. */
    public static final int[][] BRIDAL = {
            {8, 8}, {13, 6}, {13, 2}, {13, 16}, {8, 8}, {15, 6}, {12, 2}, {13, 16},
            {8, 8}, {13, 6}, {18, 2}, {18, 8}, {17, 6}, {15, 2}, {13, 8}, {12, 6}, {13, 2}, {15, 16},
            {8, 8}, {13, 6}, {13, 2}, {13, 16}, {8, 8}, {15, 6}, {12, 2}, {13, 16},
            {8, 8}, {13, 6}, {17, 2}, {20, 8}, {18, 6}, {17, 2}, {15, 8}, {12, 6}, {15, 2}, {13, 16}};

    private record Note(ServerWorld w, Vec3d at, int note, SoundEvent sound, float vol, long time, String key) {}

    private static final List<Note> QUEUE = new ArrayList<>();
    private static final Set<String> PLAYING = new HashSet<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (QUEUE.isEmpty()) return;
            long now = s.getTicks();
            for (Iterator<Note> it = QUEUE.iterator(); it.hasNext(); ) {
                Note n = it.next();
                if (n.time > now) continue;
                it.remove();
                if (n.note >= 0) {
                    n.w.playSound(null, n.at.x, n.at.y, n.at.z, n.sound, SoundCategory.RECORDS, n.vol, (float) Math.pow(2, (n.note - 12) / 12.0));
                    n.w.spawnParticles(ParticleTypes.NOTE, n.at.x, n.at.y + 2.2, n.at.z, 0, n.note / 24.0, 0, 0, 1);
                } else PLAYING.remove(n.key);
            }
        });
    }

    /** Queue a tune under `key` (one tune per key at a time). Returns false while that key is still playing. */
    public static boolean play(ServerWorld w, String key, Vec3d at, int[][] tune, SoundEvent sound, float vol) {
        if (PLAYING.contains(key)) return false;
        PLAYING.add(key);
        long t = w.getServer().getTicks() + 2;
        for (int[] n : tune) { QUEUE.add(new Note(w, at, n[0], sound, vol, t, key)); t += n[1]; }
        QUEUE.add(new Note(w, at, -1, sound, 0, t + 20, key));                          // the end marker (a breath before the next)
        return true;
    }

    public static boolean playing(String key) { return PLAYING.contains(key); }
}
