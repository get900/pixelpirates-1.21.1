package net.get900.pixelpirates.homestead.tattoo;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.get900.pixelpirates.entity.mob.BossProgression;
import net.get900.pixelpirates.homestead.HomesteadState;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * TATTOOS (2026-10-04, the user's call: purely cosmetic). Sit down in the TATTOO CHAIR at the tattooist's parlour
 * (townhouse #33) to open the flash sheet: 22 pirate designs for four spots - right forearm, left forearm, chest,
 * back. 17 are open to anyone; five are EARNED by beating a chain boss (Jolly Roger - Captain Rackham, Sea Serpent -
 * the Sea Serpent, Ghost Ship - the Ghost Captain, Kraken - the Kraken, Leviathan - the Leviathan). Inking costs coins; the tattooist also removes (covers) one for a fee.
 * Tattoos are saved per player in HomesteadState ("Tattoos": slot -> design) and synced to every client (SYNC), which
 * draws them over the player model (client/TattooFeature: textures/entity/tattoo/<design>_<slot>.png from
 * tools/gen_tattoo_textures.py). Not drawn on the first-person hand.
 */
public final class Tattoos {
    private Tattoos() {}

    public static final Identifier OPEN = new Identifier("pixelpirates", "tattoo_open");
    public static final Identifier SYNC = new Identifier("pixelpirates", "tattoo_sync");
    public static final Identifier APPLY = new Identifier("pixelpirates", "tattoo_apply");
    public static final Identifier REMOVE = new Identifier("pixelpirates", "tattoo_remove");

    public static final int PRICE = 30, EARNED_PRICE = 60, REMOVE_PRICE = 15;

    /** A design; earnedBy = the chain boss that unlocks it (null = open to anyone). */
    public record Design(String id, String name, @Nullable String earnedBy) {
        public int price() { return earnedBy == null ? PRICE : EARNED_PRICE; }
    }

    public static final List<Design> DESIGNS = List.of(
            new Design("anchor", "Anchor", null), new Design("skull_crossbones", "Skull & Crossbones", null),
            new Design("swallow", "Swallow", null), new Design("rose", "Rose", null), new Design("compass", "Compass Rose", null),
            new Design("mermaid", "Mermaid", null), new Design("ship", "Ship", null), new Design("sweetheart", "Sweetheart", null),
            new Design("dagger", "Dagger", null),
            // the second set (2026-10-04, the user: "about 10 more")
            new Design("shark", "Shark", null), new Design("crossed_cutlasses", "Crossed Cutlasses", null),
            new Design("treasure_chest", "Treasure Chest", null), new Design("parrot", "Parrot", null),
            new Design("nautical_star", "Nautical Star", null), new Design("lighthouse", "Lighthouse", null),
            new Design("rum_bottle", "Rum Bottle", null), new Design("sea_turtle", "Sea Turtle", null),
            // earned
            new Design("jolly_roger", "Jolly Roger", "captain_rackham"), new Design("sea_serpent", "Sea Serpent", "sea_serpent"),
            new Design("ghost_ship", "Ghost Ship", "ghost_captain"), new Design("kraken", "Kraken", "kraken"),
            new Design("leviathan", "Leviathan", "leviathan"));

    /** The four spots, in screen order: id + label. */
    public static final List<String> SLOTS = List.of("right_arm", "left_arm", "chest", "back");
    public static final List<String> SLOT_NAMES = List.of("Right forearm", "Left forearm", "Chest", "Back");

    public static Design byId(String id) {
        for (Design d : DESIGNS) if (d.id().equals(id)) return d;
        return null;
    }

    public static boolean unlocked(ServerPlayerEntity p, Design d) {
        if (d.earnedBy() == null) return true;
        int i = BossProgression.indexOf(d.earnedBy());
        return i >= 0 && BossProgression.progress(p) > i;
    }

    public static String bossName(Design d) {
        int i = d.earnedBy() == null ? -1 : BossProgression.indexOf(d.earnedBy());
        return i < 0 ? "" : BossProgression.CHAIN.get(i).name();
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(APPLY, (server, player, handler, buf, sender) -> {
            BlockPos chair = buf.readBlockPos();
            String slot = buf.readString(32), design = buf.readString(64);
            server.execute(() -> apply(player, chair, slot, design));
        });
        ServerPlayNetworking.registerGlobalReceiver(REMOVE, (server, player, handler, buf, sender) -> {
            BlockPos chair = buf.readBlockPos();
            String slot = buf.readString(32);
            server.execute(() -> remove(player, chair, slot));
        });
        // a joining player gets everyone's tattoos, and everyone gets theirs
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity joined = handler.getPlayer();
            for (ServerPlayerEntity other : PlayerLookup.all(server)) {
                if (other != joined) ServerPlayNetworking.send(joined, SYNC, syncBuf(server, other.getUuid()));
            }
            broadcast(server, joined.getUuid());
        });
    }

    /** The chair: open the flash sheet (designs, what's unlocked, the player's tattoos, their coins). */
    public static void open(ServerPlayerEntity p, BlockPos chair) {
        NbtCompound n = new NbtCompound();
        n.putLong("Chair", chair.asLong());
        n.put("Current", HomesteadState.get(p.getServer()).tattoos(p.getUuid()).copy());
        NbtCompound unlocked = new NbtCompound();
        for (Design d : DESIGNS) unlocked.putBoolean(d.id(), unlocked(p, d));
        n.put("Unlocked", unlocked);
        NbtCompound need = new NbtCompound();
        for (Design d : DESIGNS) if (d.earnedBy() != null) need.putString(d.id(), bossName(d));
        n.put("Need", need);
        n.putInt("Coins", coins(p));
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeNbt(n);
        ServerPlayNetworking.send(p, OPEN, buf);
    }

    private static boolean atChair(ServerPlayerEntity p, BlockPos chair) {
        return p.getWorld().getBlockState(chair).getBlock() instanceof TattooChairBlock && p.squaredDistanceTo(chair.toCenterPos()) < 8 * 8;
    }

    private static void apply(ServerPlayerEntity p, BlockPos chair, String slot, String designId) {
        Design d = byId(designId);
        if (d == null || !SLOTS.contains(slot) || !atChair(p, chair)) return;
        if (!unlocked(p, d)) { p.sendMessage(Text.literal("[~] That design is earned - beat " + bossName(d) + " first.").formatted(Formatting.RED), false); return; }
        NbtCompound mine = HomesteadState.get(p.getServer()).tattoos(p.getUuid());
        if (designId.equals(mine.getString(slot))) return;
        if (!pay(p, d.price())) { p.sendMessage(Text.literal("[~] The tattooist wants " + d.price() + " coins for that one.").formatted(Formatting.RED), false); return; }
        mine.putString(slot, designId);
        HomesteadState.get(p.getServer()).touch();
        p.getWorld().playSound(null, chair, SoundEvents.ENTITY_BEE_LOOP_AGGRESSIVE, SoundCategory.BLOCKS, 0.6f, 1.6f);
        p.sendMessage(Text.literal("[~] Inked: " + d.name() + " on your " + SLOT_NAMES.get(SLOTS.indexOf(slot)).toLowerCase() + ".").formatted(Formatting.GOLD), false);
        broadcast(p.getServer(), p.getUuid());
        open(p, chair);
    }

    private static void remove(ServerPlayerEntity p, BlockPos chair, String slot) {
        if (!SLOTS.contains(slot) || !atChair(p, chair)) return;
        NbtCompound mine = HomesteadState.get(p.getServer()).tattoos(p.getUuid());
        if (!mine.contains(slot)) return;
        if (!pay(p, REMOVE_PRICE)) { p.sendMessage(Text.literal("[~] Covering one up costs " + REMOVE_PRICE + " coins.").formatted(Formatting.RED), false); return; }
        mine.remove(slot);
        HomesteadState.get(p.getServer()).touch();
        p.sendMessage(Text.literal("[~] The tattooist covers it up - your " + SLOT_NAMES.get(SLOTS.indexOf(slot)).toLowerCase() + " is bare again.").formatted(Formatting.GOLD), false);
        broadcast(p.getServer(), p.getUuid());
        open(p, chair);
    }

    private static PacketByteBuf syncBuf(MinecraftServer server, UUID who) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(who);
        buf.writeNbt(HomesteadState.get(server).tattoos(who).copy());
        return buf;
    }

    /** Tell every client what {@code who} has inked now. */
    public static void broadcast(MinecraftServer server, UUID who) {
        for (ServerPlayerEntity p : PlayerLookup.all(server)) ServerPlayNetworking.send(p, SYNC, syncBuf(server, who));
    }

    private static int coins(ServerPlayerEntity p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().size(); i++) { ItemStack s = p.getInventory().getStack(i); if (s.isOf(ModItems.COIN)) n += s.getCount(); }
        return n;
    }

    private static boolean pay(ServerPlayerEntity p, int price) {
        if (p.isCreative()) return true;
        if (coins(p) < price) return false;
        int left = price;
        for (int i = 0; i < p.getInventory().size() && left > 0; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isOf(ModItems.COIN)) continue;
            int take = Math.min(left, s.getCount());
            s.decrement(take);
            left -= take;
        }
        return true;
    }
}
