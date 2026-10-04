package net.get900.pixelpirates.homestead.tavern;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * THE GROG BARREL'S GAMES (2026-10-01): shared plumbing for the tavern game tables (Liar's Dice, Crown & Anchor).
 * Right-clicking a table makes you a WATCHER of it (GameTableBlockEntity.open) and opens its screen; every change is sent
 * to every watcher as one NBT state packet (STATE), built per viewer so hidden dice stay hidden. The screens answer with
 * ACTION packets (pos, action, a, b); "close" stops watching. Stakes are PIRATE COINS (pirate money - see ECONOMY).
 */
public final class TavernGames {
    private TavernGames() {}

    public static final Identifier STATE = new Identifier("pixelpirates", "tavern_game_state");
    public static final Identifier ACTION = new Identifier("pixelpirates", "tavern_game_action");
    public static final double REACH = 10;

    public static void registerNetworking() {
        ServerPlayNetworking.registerGlobalReceiver(ACTION, (server, player, handler, buf, sender) -> {
            BlockPos pos = buf.readBlockPos();
            String action = buf.readString(32);
            int a = buf.readVarInt(), b = buf.readVarInt();
            server.execute(() -> {
                if (!(player.getWorld().getBlockEntity(pos) instanceof GameTableBlockEntity be)) return;
                if ("close".equals(action)) { be.close(player); return; }
                if (player.squaredDistanceTo(Vec3d.ofCenter(pos)) > REACH * REACH) return;
                be.act(player, action, a, b);
            });
        });
    }

    static void sendState(ServerPlayerEntity p, NbtCompound n) {
        var buf = PacketByteBufs.create();
        buf.writeNbt(n);
        ServerPlayNetworking.send(p, STATE, buf);
    }

    /** Pirate coins anywhere in the inventory. */
    public static int coins(PlayerEntity p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(ModItems.PIRATE_COIN)) n += s.getCount();
        }
        return n;
    }

    /** Takes {@code n} pirate coins; false (and nothing taken) if the player has too few. Creative plays for free. */
    public static boolean take(PlayerEntity p, int n) {
        if (n <= 0 || p.getAbilities().creativeMode) return true;
        if (coins(p) < n) return false;
        for (int i = 0; i < p.getInventory().size() && n > 0; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isOf(ModItems.PIRATE_COIN)) continue;
            int k = Math.min(n, s.getCount());
            s.decrement(k);
            n -= k;
        }
        return true;
    }

    public static void pay(PlayerEntity p, int n) {
        if (p.getAbilities().creativeMode) return;
        while (n > 0) {
            int k = Math.min(64, n);
            n -= k;
            p.getInventory().offerOrDrop(new ItemStack(ModItems.PIRATE_COIN, k));
        }
    }
}
