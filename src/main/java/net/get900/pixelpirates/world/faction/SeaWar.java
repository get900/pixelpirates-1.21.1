package net.get900.pixelpirates.world.faction;

import net.get900.pixelpirates.homestead.town.TownMemory;
import net.get900.pixelpirates.world.AiShipController;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.PersistentState;
import org.joml.Vector3d;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * THE WAR AT SEA (2026-10-05; the user: "the factions actually will fight each other and have some proper implications
 * in the world"). AI ships of enemy factions already engage (Faction.isEnemyFaction, AiShipController.selectTarget); this
 * keeps score and makes it matter:
 *  - every cannonball knows who fired it (a ship id, or a player via its owner); {@link #hit} remembers a hull's last
 *    attackers for {@link #MEMORY} ticks;
 *  - {@link #sunk}: an AI ship goes down -> who sank it. A faction kill is announced to captains within
 *    {@link #NEWS_R}, makes the town news, and shifts the BALANCE OF POWER; a player's sinking (cannon fire - before
 *    this only boarding counted) runs FactionManager.onShipDestroyed (rep, bounties, XP); a player who joined in on
 *    the side of the victim's enemy also earns that faction's thanks ({@link #THANKS});
 *  - the balance ({@link State#power}, -10..10 per faction, eases back to 0 by one a day) scales each faction's share of
 *    natural spawns ({@link #spawnWeight}) - the side that is winning puts more sail on the sea;
 *  - the wrecks are lootable derelicts (AiShipController.makeDerelict) - a battle leaves salvage.
 * `/ppwar` shows the balance.
 */
public final class SeaWar {
    private SeaWar() {}

    static final int MEMORY = 1200, NEWS_R = 400, THANKS = 25, POWER_MAX = 10;

    /** victim ship -> its recent attackers: ship id -> (faction, last tick), player -> last tick. */
    private static final Map<Long, Map<Long, Object[]>> SHIP_HITS = new HashMap<>();
    private static final Map<Long, Map<UUID, Long>> PLAYER_HITS = new HashMap<>();

    /** A cannonball struck {@code victim}'s hull. firedBy = the firing AI ship (-1 = none); player = the firing player (or null). */
    public static void hit(ServerWorld w, long victim, long firedBy, ServerPlayerEntity player) {
        long now = w.getTime();
        if (firedBy >= 0 && firedBy != victim) {
            AiShipController.AiShipData d = AiShipController.AI_SHIPS.get(firedBy);
            if (d != null) SHIP_HITS.computeIfAbsent(victim, k -> new HashMap<>()).put(firedBy, new Object[]{d.faction, now, d.blueprintName});
        }
        if (player != null) PLAYER_HITS.computeIfAbsent(victim, k -> new HashMap<>()).put(player.getUuid(), now);
    }

    /** AiShipController: an AI ship's hull reached 0. */
    public static void sunk(ServerWorld w, AiShipController.AiShipData victim, Vector3d at) {
        long now = w.getTime();
        Faction vf = victim.faction;
        String vname = shipName(victim.blueprintName);
        // the ship that hit it last, within MEMORY
        Faction killer = null; String kname = null; long best = -1;
        for (Object[] h : SHIP_HITS.getOrDefault(victim.shipId, Map.of()).values())
            if (now - (long) h[1] <= MEMORY && (long) h[1] > best) { best = (long) h[1]; killer = (Faction) h[0]; kname = shipName((String) h[2]); }
        Map<UUID, Long> players = PLAYER_HITS.getOrDefault(victim.shipId, Map.of());
        UUID lastPlayer = null; long bestP = -1;
        for (var en : players.entrySet()) if (now - en.getValue() <= MEMORY && en.getValue() > bestP) { bestP = en.getValue(); lastPlayer = en.getKey(); }
        boolean playerKill = lastPlayer != null && bestP >= best;
        // the players who fought: the one who sank her pays the price / takes the credit, the others who fought her
        // alongside her enemy are thanked by that enemy
        Set<UUID> fought = new HashSet<>();
        for (var en : players.entrySet()) if (now - en.getValue() <= MEMORY) fought.add(en.getKey());
        if (playerKill) {
            ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(lastPlayer);
            if (p != null) FactionManager.onShipDestroyed(p, vf);
        }
        if (killer != null && killer != vf) {
            State s = state(w.getServer());
            s.shift(killer, 1); s.shift(vf, -1);
            String line = cap(kname) + " (" + clean(killer.displayName) + ") sank " + vname + " (" + clean(vf.displayName) + ")!";
            for (ServerPlayerEntity p : w.getPlayers(p -> p.squaredDistanceTo(at.x, p.getY(), at.z) < (double) NEWS_R * NEWS_R))
                p.sendMessage(Text.literal("[Sea] " + line + (playerKill ? "" : " Her wreck is there for the taking.")).formatted(Formatting.GOLD), false);
            TownMemory.news(w, "sea", line, "ship" + victim.shipId);
            for (UUID u : fought) {
                if (u.equals(lastPlayer) && playerKill) continue;
                ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(u);
                if (p == null) continue;
                FactionManager.modifyReputation(p, killer, THANKS);
                p.sendMessage(Text.literal("[Sea] " + clean(killer.displayName) + " saw you fight on their side. (+" + THANKS + " standing)").formatted(Formatting.AQUA), false);
            }
        } else if (playerKill) {
            TownMemory.news(w, "sea", "A captain sank " + clean(vf.displayName) + "'s " + vname + " at sea.", "ship" + victim.shipId);
            state(w.getServer()).shift(vf, -1);
        }
        SHIP_HITS.remove(victim.shipId);
        PLAYER_HITS.remove(victim.shipId);
    }

    /** The share of natural spawns, scaled by how the war is going for that faction: x0.5 (beaten) .. x2 (winning). */
    public static double spawnWeight(MinecraftServer s, Faction f, double base) {
        int p = state(s).power.getOrDefault(f, 0);
        return base * Math.pow(2, p / (double) POWER_MAX);
    }

    /** /ppwar. */
    public static String report(MinecraftServer s) {
        State st = state(s);
        StringBuilder b = new StringBuilder("[War at Sea] ");
        for (Faction f : Faction.values()) b.append(clean(f.displayName)).append(" ").append(String.format("%+d", st.power.getOrDefault(f, 0))).append("   ");
        return b.toString().trim();
    }

    /** Once a day each faction's standing in the war eases one step back toward even. */
    public static void daily(MinecraftServer s, long day) {
        State st = state(s);
        if (st.lastDay == day) return;
        st.lastDay = day;
        for (Faction f : Faction.values()) { int v = st.power.getOrDefault(f, 0); if (v != 0) st.power.put(f, v - Integer.signum(v)); }
        st.markDirty();
    }

    static String clean(String s) { return s.replaceAll("§.", ""); }

    static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    static String shipName(String blueprint) {
        return switch (blueprint) {
            case "pirate_cutter" -> "the Bilge Rat"; case "pirate_brig" -> "the Blackheart"; case "pirate_galleon" -> "the Dread Galleon";
            case "corsair_xebec" -> "a xebec"; case "merchant_lugger" -> "the Herring Lass"; case "merchant_fluyt" -> "the Silver Herring";
            case "merchant_indiaman" -> "the Emerald Empress"; case "merchantman" -> "a merchantman"; case "navy_cutter" -> "HMS Swift";
            case "navy_corvette" -> "HMS Vigilant"; case "navy_frigate" -> "a frigate"; case "navy_man_o_war" -> "HMS Sovereign";
            case "undead_wraith" -> "a wraith"; case "undead_bone_galley" -> "a bone galley"; case "drowned_hulk" -> "a drowned hulk";
            case "undead_phantom_galleon" -> "the Phantom Galleon"; default -> "a " + blueprint.replace('_', ' ');
        };
    }

    // ------------------------------------------------------------------ the balance of power (saved)
    public static final class State extends PersistentState {
        final Map<Faction, Integer> power = new EnumMap<>(Faction.class);
        long lastDay = -1;

        void shift(Faction f, int d) {
            power.put(f, Math.max(-POWER_MAX, Math.min(POWER_MAX, power.getOrDefault(f, 0) + d)));
            markDirty();
        }

        static State read(NbtCompound n) {
            State s = new State();
            for (Faction f : Faction.values()) if (n.contains(f.id)) s.power.put(f, n.getInt(f.id));
            s.lastDay = n.contains("LastDay") ? n.getLong("LastDay") : -1;
            return s;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound n) {
            for (var en : power.entrySet()) n.putInt(en.getKey().id, en.getValue());
            n.putLong("LastDay", lastDay);
            return n;
        }
    }

    static State state(MinecraftServer s) { return s.getOverworld().getPersistentStateManager().getOrCreate(State::read, State::new, "pixelpirates_sea_war"); }
}
