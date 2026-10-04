package net.get900.pixelpirates.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.util.Random;

/**
 * Festival fireworks (homestead/town/TownEvents.fireworks): one burst at a point, drawn with ClientWorld.addFireworkParticle -
 * the full firework effect + sound, with no rocket, so nothing is hurt (see client/TreasureFx, CLAUDE.md "Harmless fireworks").
 */
@Environment(EnvType.CLIENT)
public final class TownFx {
    private TownFx() {}

    private static final int[] COLOURS = {0xFF3030, 0xFFC832, 0x40E0D8, 0xB040FF, 0xFFFFFF, 0x50FF60, 0xFF80D0, 0x4080FF};

    public static void burst(MinecraftClient client, double x, double y, double z, int seed) {
        if (client.world == null) return;
        Random r = new Random(seed);
        NbtCompound e = new NbtCompound();
        e.putByte("Type", (byte) r.nextInt(5));
        e.putIntArray("Colors", new int[]{COLOURS[r.nextInt(COLOURS.length)], COLOURS[r.nextInt(COLOURS.length)]});
        e.putIntArray("FadeColors", new int[]{COLOURS[r.nextInt(COLOURS.length)]});
        e.putBoolean("Trail", r.nextBoolean());
        e.putBoolean("Flicker", r.nextBoolean());
        NbtList explosions = new NbtList();
        explosions.add(e);
        NbtCompound fw = new NbtCompound();
        fw.put("Explosions", explosions);
        client.world.addFireworkParticle(x, y, z, 0, 0, 0, fw);
    }
}
