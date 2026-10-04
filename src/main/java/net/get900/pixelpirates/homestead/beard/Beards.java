package net.get900.pixelpirates.homestead.beard;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.get900.pixelpirates.homestead.HomesteadState;
import net.get900.pixelpirates.item.ModItems;
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
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * FACIAL HAIR (2026-10-04, the user: "facial hair will slowly grow on the player over time; to remove it they go to the
 * barber and maybe select different kinds - only if it's grown long enough, so someone with stubble can't get a French
 * moustache"). Beards grow while you're ONLINE: stubble after 1 Minecraft day of play, a short beard after 3, a long
 * beard after 7. Left alone it just grows (stubble -> short beard -> full beard). At the BARBER'S CHAIR (#38) you can
 * shave clean, trim to any style your growth allows (trimming cuts the growth back to that style's length - grow it out
 * again for the long ones), pick a colour, or stay clean-shaven (growth stops until you turn it off).
 * Stored per player in HomesteadState "Beards" {G: growth ticks, S: chosen style ("" = let it grow), C: colour, K: keep
 * clean}; every client is told each player's LOOK (style shown + colour) and draws it (client/BeardFeature).
 */
public final class Beards {
    private Beards() {}

    public static final Identifier OPEN = new Identifier("pixelpirates", "barber_open");
    public static final Identifier SYNC = new Identifier("pixelpirates", "beard_sync");
    public static final Identifier ACT = new Identifier("pixelpirates", "barber_act");

    /** Growth needed (ticks of play) for each stage: 0 clean, 1 stubble, 2 short, 3 long. */
    public static final long[] STAGE = {0, 24000, 72000, 168000};
    public static final String[] STAGE_NAMES = {"Clean-shaven", "Stubble", "Short beard", "Long beard"};
    public static final int TRIM_PRICE = 5;

    /** A style: the stage of growth it needs; long = it hangs below the chin (drawn with the extra beard piece). */
    public record Style(String id, String name, int stage, boolean hangs) {}

    public static final List<Style> STYLES = List.of(
            new Style("stubble", "Stubble", 1, false),
            new Style("moustache", "Moustache", 2, false), new Style("goatee", "Goatee", 2, false),
            new Style("chinstrap", "Chinstrap", 2, false), new Style("mutton_chops", "Mutton Chops", 2, false),
            new Style("short_beard", "Short Beard", 2, false),
            new Style("french_moustache", "French Moustache", 3, false), new Style("handlebar", "Handlebar Moustache", 3, false),
            new Style("full_beard", "Full Beard", 3, true), new Style("forked_beard", "Forked Beard", 3, true),
            new Style("braided_beard", "Braided Pirate Beard", 3, true), new Style("captains_beard", "Captain's Beard", 3, true));

    /** Colours: id, name, RGB. */
    public static final String[] COLOURS = {"black", "dark_brown", "brown", "ginger", "blonde", "grey", "white"};
    public static final String[] COLOUR_NAMES = {"Black", "Dark Brown", "Brown", "Ginger", "Blonde", "Grey", "White"};
    public static final int[] COLOUR_RGB = {0x2B2522, 0x4A3222, 0x77502F, 0xB0582A, 0xD9B97A, 0x8D8D8D, 0xE9E6E0};

    @Nullable
    public static Style byId(String id) {
        for (Style s : STYLES) if (s.id.equals(id)) return s;
        return null;
    }

    public static int colourRgb(String id) {
        for (int i = 0; i < COLOURS.length; i++) if (COLOURS[i].equals(id)) return COLOUR_RGB[i];
        return COLOUR_RGB[2];
    }

    public static int stage(long growth) {
        int s = 0;
        for (int i = 0; i < STAGE.length; i++) if (growth >= STAGE[i]) s = i;
        return s;
    }

    /** The style a beard record shows ("" = none). */
    public static String look(NbtCompound b) {
        if (b.getBoolean("K")) return "";
        int st = stage(b.getLong("G"));
        String chosen = b.getString("S");
        if (!chosen.isEmpty()) return chosen;
        return switch (st) { case 1 -> "stubble"; case 2 -> "short_beard"; case 3 -> "full_beard"; default -> ""; };
    }

    static NbtCompound data(MinecraftServer s, UUID u) {
        NbtCompound b = HomesteadState.get(s).beard(u);
        if (!b.contains("C")) b.putString("C", "brown");
        return b;
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0) return;
            HomesteadState st = HomesteadState.get(server);
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                NbtCompound b = data(server, p.getUuid());
                if (b.getBoolean("K")) continue;
                String before = look(b);
                b.putLong("G", b.getLong("G") + 20);
                if (!look(b).equals(before)) { broadcast(server, p.getUuid()); if (b.getString("S").isEmpty() && !look(b).isEmpty())
                    p.sendMessage(Text.literal("[~] Your beard has grown: " + STAGE_NAMES[stage(b.getLong("G"))].toLowerCase() + ".").formatted(Formatting.GRAY), true); }
            }
            st.markDirty();
        });
        ServerPlayNetworking.registerGlobalReceiver(ACT, (server, player, handler, buf, sender) -> {
            BlockPos chair = buf.readBlockPos();
            String what = buf.readString(16), arg = buf.readString(32), colour = buf.readString(16);
            server.execute(() -> act(player, chair, what, arg, colour));
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity joined = handler.getPlayer();
            for (ServerPlayerEntity other : PlayerLookup.all(server))
                if (other != joined) ServerPlayNetworking.send(joined, SYNC, syncBuf(server, other.getUuid()));
            broadcast(server, joined.getUuid());
        });
    }

    static PacketByteBuf syncBuf(MinecraftServer s, UUID u) {
        NbtCompound b = data(s, u);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(u);
        buf.writeString(look(b), 32);
        buf.writeString(b.getString("C"), 16);
        return buf;
    }

    static void broadcast(MinecraftServer s, UUID u) {
        for (ServerPlayerEntity p : PlayerLookup.all(s)) ServerPlayNetworking.send(p, SYNC, syncBuf(s, u));
    }

    /** The barber's chair: open the barber's book (growth, styles, colours, keep-clean). */
    public static void open(ServerPlayerEntity p, BlockPos chair) {
        NbtCompound b = data(p.getServer(), p.getUuid());
        NbtCompound n = b.copy();
        n.putLong("Chair", chair.asLong());
        n.putString("Look", look(b));
        int coins = 0;
        for (int i = 0; i < p.getInventory().size(); i++) if (p.getInventory().getStack(i).isOf(ModItems.COIN)) coins += p.getInventory().getStack(i).getCount();
        n.putInt("Coins", coins);
        n.putInt("Price", net.get900.pixelpirates.world.SkillEffects.haggle(p, TRIM_PRICE));
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeNbt(n);
        ServerPlayNetworking.send(p, OPEN, buf);
    }

    /** what: "style" (arg = style id, "grow" = let it grow), "shave", "keep" (toggle), "colour". */
    private static void act(ServerPlayerEntity p, BlockPos chair, String what, String arg, String colour) {
        if (p.squaredDistanceTo(Vec3d.ofCenter(chair)) > 6 * 6 || !(p.getServerWorld().getBlockState(chair).getBlock() instanceof BarberChairBlock)) return;
        NbtCompound b = data(p.getServer(), p.getUuid());
        boolean known = false;
        for (String c : COLOURS) known |= c.equals(colour);
        switch (what) {
            case "shave" -> { b.putLong("G", 0); b.putString("S", ""); say(p, "A clean shave."); }
            case "keep" -> {
                boolean k = !b.getBoolean("K");
                b.putBoolean("K", k);
                if (k) { b.putLong("G", 0); b.putString("S", ""); }
                say(p, k ? "You'll stay clean-shaven." : "Your beard will grow again.");
            }
            case "colour" -> { if (known) { b.putString("C", colour); say(p, "Dyed " + colour.replace('_', ' ') + "."); } }
            case "style" -> {
                if (arg.equals("grow")) { b.putString("S", ""); say(p, "Let it grow wild, then."); break; }
                Style s = byId(arg);
                if (s == null) return;
                int have = stage(b.getLong("G"));
                if (b.getBoolean("K") || have < s.stage) {
                    p.sendMessage(Text.literal("Not enough to work with - a " + s.name + " needs a " + STAGE_NAMES[s.stage].toLowerCase() + ".").formatted(Formatting.RED), true);
                    return;
                }
                if (!p.isCreative() && !pay(p, net.get900.pixelpirates.world.SkillEffects.haggle(p, TRIM_PRICE))) {
                    p.sendMessage(Text.literal("The barber wants " + TRIM_PRICE + " doubloons.").formatted(Formatting.RED), true);
                    return;
                }
                b.putString("S", s.id);
                b.putLong("G", Math.min(b.getLong("G"), Math.max(STAGE[s.stage], STAGE[s.stage] + 1)));   // trimmed back to that length
                if (known) b.putString("C", colour);
                say(p, "Trimmed to a " + s.name + ".");
            }
            default -> { return; }
        }
        HomesteadState.get(p.getServer()).markDirty();
        broadcast(p.getServer(), p.getUuid());
        p.getServerWorld().playSound(null, chair, SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.PLAYERS, 0.8f, 1.3f);
        open(p, chair);                                                                       // refresh the screen
    }

    /** /ppbeard (op, testing): wear a style now ("none" = shave), or add days of growth. */
    public static String debug(ServerPlayerEntity p, String style, String colour, int days) {
        NbtCompound b = data(p.getServer(), p.getUuid());
        if (days > 0) b.putLong("G", b.getLong("G") + days * 24000L);
        else if (style.equals("none")) { b.putLong("G", 0); b.putString("S", ""); }
        else {
            Style s = byId(style);
            if (s == null) return "Unknown style " + style;
            b.putString("S", s.id); b.putLong("G", Math.max(b.getLong("G"), STAGE[s.stage]));
        }
        if (colour != null) b.putString("C", colour);
        b.putBoolean("K", false);
        HomesteadState.get(p.getServer()).markDirty();
        broadcast(p.getServer(), p.getUuid());
        return "Beard: " + (look(b).isEmpty() ? "none" : look(b)) + " (" + b.getString("C") + "), growth " + b.getLong("G") / 24000.0 + " days";
    }

    private static void say(ServerPlayerEntity p, String s) { p.sendMessage(Text.literal("[~] " + s).formatted(Formatting.GOLD), true); }

    private static boolean pay(ServerPlayerEntity p, int price) {
        int have = 0;
        for (int i = 0; i < p.getInventory().size(); i++) if (p.getInventory().getStack(i).isOf(ModItems.COIN)) have += p.getInventory().getStack(i).getCount();
        if (have < price) return false;
        for (int i = 0; i < p.getInventory().size() && price > 0; i++) {
            var s = p.getInventory().getStack(i);
            if (!s.isOf(ModItems.COIN)) continue;
            int t = Math.min(price, s.getCount()); s.decrement(t); price -= t;
        }
        return true;
    }
}
