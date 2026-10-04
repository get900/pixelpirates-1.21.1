package net.get900.pixelpirates.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.get900.pixelpirates.block.custom.TreasureBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

/**
 * THE TREASURE BLOCK's opening show (client): firework bursts over the heap - made with ClientWorld.addFireworkParticle,
 * the same emitter a real rocket uses (sparks, trails, crackle and the blast sounds) but with no rocket, so it can't hurt
 * anyone and goes off fine under a low lair ceiling. Each emitter fires its explosions 2 ticks apart.
 * tier 0: two gold bursts; 1: three, gold + aqua, a star; 2: four emitters, gold + purple + white, a large ball, crackle.
 */
@Environment(EnvType.CLIENT)
public final class TreasureFx {
    private TreasureFx() {}

    private static final int GOLD = 0xFFC832, LIGHT_GOLD = 0xFFE88A, AQUA = 0x40E0D8, PURPLE = 0xB040FF, WHITE = 0xFFFFFF;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(TreasureBlock.FX, (client, handler, buf, sender) -> {
            BlockPos pos = buf.readBlockPos();
            int tier = buf.readVarInt();
            client.execute(() -> play(client, pos, tier));
        });
    }

    private static void play(MinecraftClient client, BlockPos pos, int tier) {
        if (client.world == null) return;
        Random r = client.world.random;
        int emitters = 2 + tier;
        for (int e = 0; e < emitters; e++) {
            NbtList explosions = new NbtList();
            int bursts = 1 + tier;
            for (int b = 0; b < bursts; b++) {
                NbtCompound ex = new NbtCompound();
                int type = tier == 2 && b == 0 ? 1 : tier >= 1 && b == 1 ? 2 : 0;     // large ball / star / small ball
                ex.putByte("Type", (byte) type);
                int[] colours = switch (tier) {
                    case 0 -> new int[]{GOLD, LIGHT_GOLD};
                    case 1 -> new int[]{GOLD, AQUA, LIGHT_GOLD};
                    default -> new int[]{GOLD, PURPLE, WHITE};
                };
                ex.putIntArray("Colors", colours);
                ex.putIntArray("FadeColors", new int[]{tier == 2 ? PURPLE : LIGHT_GOLD});
                ex.putBoolean("Trail", tier >= 1);
                ex.putBoolean("Flicker", tier == 2 || b > 0);
                explosions.add(ex);
            }
            NbtCompound fw = new NbtCompound();
            fw.put("Explosions", explosions);
            double x = pos.getX() + 0.5 + (r.nextDouble() - 0.5) * 2.5, y = pos.getY() + 2.2 + r.nextDouble() * 1.4 + e * 0.3;
            double z = pos.getZ() + 0.5 + (r.nextDouble() - 0.5) * 2.5;
            client.world.addFireworkParticle(x, y, z, 0, 0, 0, fw);
        }
    }
}
