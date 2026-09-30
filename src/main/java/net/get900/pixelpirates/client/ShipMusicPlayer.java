package net.get900.pixelpirates.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.get900.pixelpirates.network.ModNetworking;
import net.get900.pixelpirates.sound.ModSounds;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;

import java.util.List;

@Environment(EnvType.CLIENT)
public class ShipMusicPlayer {

    private static SoundInstance currentTrack = null;

    public static void toggle() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        // Stop immediately if music is already playing
        if (currentTrack != null && client.getSoundManager().isPlaying(currentTrack)) {
            client.getSoundManager().stop(currentTrack);
            currentTrack = null;
            client.player.sendMessage(Text.literal("§7♪ Music stopped."), true);
            return;
        }

        // Ask the server to verify the player is on a ship, then it sends back the track index
        ClientPlayNetworking.send(ModNetworking.SHIP_MUSIC_REQUEST, PacketByteBufs.empty());
    }

    /** Called from the S2C packet handler with the track index chosen server-side. */
    public static void playTrack(int trackIndex) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        List<SoundEvent> tracks = ModSounds.SHIP_TRACKS;
        if (trackIndex < 0 || trackIndex >= tracks.size()) return;

        SoundEvent chosen = tracks.get(trackIndex);
        // Mirror vanilla MusicTracker: MUSIC category, relative=true at 0,0,0 (listener position).
        // Using world-space coords + relative=false sometimes silently fails; relative=true always works.
        currentTrack = new PositionedSoundInstance(
            chosen.getId(), SoundCategory.MUSIC,
            1.0f, 1.0f, SoundInstance.createRandom(),
            false, 0, SoundInstance.AttenuationType.NONE,
            0.0, 0.0, 0.0, true
        );
        client.getSoundManager().play(currentTrack);

        String raw = chosen.getId().getPath().replace("music.", "").replace("_", " ");
        client.player.sendMessage(Text.literal("§6♪ Now playing: §f" + capitalise(raw)), true);
    }

    private static String capitalise(String s) {
        if (s.isEmpty()) return s;
        String[] words = s.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }
}
