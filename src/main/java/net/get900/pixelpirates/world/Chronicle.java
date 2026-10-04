package net.get900.pixelpirates.world;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.get900.pixelpirates.entity.mob.BossProgression;
import net.get900.pixelpirates.homestead.HomesteadBlocks;
import net.get900.pixelpirates.homestead.HomesteadItems;
import net.get900.pixelpirates.homestead.HomesteadState;
import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.network.ModNetworking;
import net.get900.pixelpirates.world.faction.Faction;
import net.get900.pixelpirates.world.faction.FactionManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * THE WEATHERED CHRONICLE (2026-10-01, replaces the Captain's Logbook): the lore book. The WORDS live client-side in
 * assets/pixelpirates/chronicle/*.json (ChronicleScreen lays them out); the server only sends what is personal - how far
 * along the boss chain the reader is (a boss's pages ink themselves in once the boss BEFORE it falls, and it is struck
 * out with a red X when it dies), the fish they have caught, their charted treasure, bounties, hideout and standing.
 * Every player gets one the first time they reach the Pixel Pirates seas; /pptest all (or /pptest chronicle) reveals
 * every page for testing.
 */
public final class Chronicle {
    private Chronicle() {}

    private static final String GIVEN = "chronicle:";

    /** The fish the catch pages list, in order (vanilla, then each sea's, then shellfish and trophies). */
    public static List<Item> fish() {
        List<Item> l = new ArrayList<>(List.of(Items.COD, Items.SALMON, Items.TROPICAL_FISH, Items.PUFFERFISH));
        l.addAll(HomesteadItems.FISH);
        l.add(HomesteadItems.LOBSTER);
        l.add(HomesteadItems.CRAB_CLAW);
        l.add(HomesteadBlocks.GOLDEN_MARLIN_TROPHY.asItem());
        l.add(HomesteadBlocks.GHOST_SWORDFISH_TROPHY.asItem());
        l.add(HomesteadBlocks.COELACANTH_TROPHY.asItem());
        return l;
    }

    public static boolean revealAll(ServerPlayerEntity p) { return AdminTestState.chronicleAll.contains(p.getUuid()); }

    public static void setRevealAll(ServerPlayerEntity p, boolean on) {
        if (on) AdminTestState.chronicleAll.add(p.getUuid());
        else AdminTestState.chronicleAll.remove(p.getUuid());
    }

    /** Right-click with the book: send the reader's page state; the client opens the book on arrival. */
    public static void open(ServerPlayerEntity p) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(BossProgression.progress(p));
        buf.writeBoolean(revealAll(p));
        buf.writeString(p.getName().getString());
        buf.writeVarInt(((net.get900.pixelpirates.util.PlayerProgressionComponent) p).pp_getPirateLevel());

        List<Item> fish = fish();
        buf.writeVarInt(fish.size());
        for (Item f : fish) {
            boolean got = p.getStatHandler().getStat(Stats.PICKED_UP.getOrCreateStat(f)) > 0
                    || p.getStatHandler().getStat(Stats.USED.getOrCreateStat(f)) > 0
                    || p.getStatHandler().getStat(Stats.CRAFTED.getOrCreateStat(f)) > 0;
            buf.writeString(Registries.ITEM.getId(f).toString());
            buf.writeBoolean(got);
        }

        HomesteadState st = HomesteadState.get(p.getServer());
        Set<String> found = st.discoveries(p.getUuid());
        List<String[]> sites = new ArrayList<>();
        for (String k : found) {
            if (!k.startsWith("site:")) continue;
            String[] a = k.split(":");
            if (a.length >= 4) sites.add(new String[]{a[1], a[2], a[3]});
        }
        buf.writeVarInt(sites.size());
        for (String[] s : sites) { buf.writeString(s[0]); buf.writeString(s[1]); buf.writeString(s[2]); }

        buf.writeVarInt(st.bounty(p.getUuid()).getInt("Done"));
        HomesteadState.Hideout h = st.hideout(p.getUuid());
        buf.writeString(h == null ? "" : h.pos().getX() + ", " + h.pos().getZ() + "  (tier " + h.tier() + ")");

        buf.writeVarInt(Faction.values().length);
        for (Faction f : Faction.values()) {
            int r = FactionManager.getReputation(p, f);
            buf.writeString(f.name().toLowerCase());
            buf.writeString(f.displayName.replaceAll("§.", ""));
            buf.writeVarInt(r);
            buf.writeString(FactionManager.standingLabel(r).replaceAll("§.", ""));
        }
        ServerPlayNetworking.send(p, ModNetworking.S2C_CHRONICLE, buf);
        p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1f, 0.9f);
    }

    /** First arrival in the Pixel Pirates seas (and every later join, for players from before the Chronicle). */
    public static void giveStarter(ServerPlayerEntity p) {
        HomesteadState st = HomesteadState.get(p.getServer());
        if (st.flag(GIVEN + p.getUuid())) return;
        st.setFlag(GIVEN + p.getUuid());
        p.getInventory().offerOrDrop(new ItemStack(ModItems.WEATHERED_CHRONICLE));
    }
}
