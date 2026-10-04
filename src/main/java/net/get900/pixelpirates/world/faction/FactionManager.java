package net.get900.pixelpirates.world.faction;

import net.get900.pixelpirates.util.PlayerProgressionComponent;
import net.get900.pixelpirates.world.AdminTestState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class FactionManager {

    public static final int REP_DESTROY_SHIP = -150;
    public static final int REP_KILL_CAPTAIN  =  -75;
    public static final int REP_BONUS_ENEMY   =   50;

    private FactionManager() {}

    public static int getReputation(ServerPlayerEntity player, Faction faction) {
        return ((PlayerProgressionComponent) player).pp_getFactionRep(faction.ordinal());
    }

    public static void modifyReputation(ServerPlayerEntity player, Faction faction, int delta) {
        if (delta == 0) return;
        PlayerProgressionComponent comp = (PlayerProgressionComponent) player;
        int current = comp.pp_getFactionRep(faction.ordinal());
        int next = Math.max(Faction.REP_MIN, Math.min(Faction.REP_MAX, current + delta));
        comp.pp_setFactionRep(faction.ordinal(), next);
        String sign  = delta > 0 ? "§a+" : "§c";
        String color = delta > 0 ? "§a" : "§c";
        player.sendMessage(Text.literal(
            faction.displayName + " §f" + color + sign + delta + " §7(" + next + ")"), true);
    }

    /**
     * Returns true if this faction's ships should actively target the given player.
     * Pirates always attack. Others only attack when player reputation is below REP_HOSTILE.
     */
    public static boolean isHostileTo(Faction faction, ServerPlayerEntity player) {
        if (faction.alwaysHostile) return true;
        if (AdminTestState.forcedHostile.contains(faction)) return true;
        return getReputation(player, faction) < Faction.REP_HOSTILE;
    }

    /**
     * Called when a player destroys (boards or sinks) a faction ship.
     * Loses reputation with that faction; gains small rep with enemy factions.
     */
    public static void onShipDestroyed(ServerPlayerEntity player, Faction destroyedFaction) {
        net.get900.pixelpirates.homestead.bounty.Bounties.onShip(player, destroyedFaction);
        net.get900.pixelpirates.world.PirateLevelManager.awardXp(player, net.get900.pixelpirates.world.PirateLevelingSystem.XP_SINK_SHIP, false);
        modifyReputation(player, destroyedFaction, REP_DESTROY_SHIP);
        for (Faction f : Faction.values()) {
            if (f != destroyedFaction && f.isEnemyFaction(destroyedFaction)) {
                modifyReputation(player, f, REP_BONUS_ENEMY);
            }
        }
    }

    /** Called when a player kills the captain of a faction ship (boarding path). */
    public static void onCaptainKilled(ServerPlayerEntity player, Faction captainFaction) {
        net.get900.pixelpirates.homestead.bounty.Bounties.onShip(player, captainFaction);
        net.get900.pixelpirates.world.PirateLevelManager.awardXp(player, net.get900.pixelpirates.world.PirateLevelingSystem.XP_BOARD_SHIP, false);
        modifyReputation(player, captainFaction, REP_KILL_CAPTAIN);
        for (Faction f : Faction.values()) {
            if (f != captainFaction && f.isEnemyFaction(captainFaction)) {
                modifyReputation(player, f, REP_BONUS_ENEMY / 2);
            }
        }
    }

    /** Returns a human-readable standing label for a reputation value. */
    public static String standingLabel(int rep) {
        if (rep >= Faction.REP_HONORED)  return "§6Honored";
        if (rep >= Faction.REP_FRIENDLY) return "§aFriendly";
        if (rep >= Faction.REP_NEUTRAL)  return "§7Neutral";
        if (rep >= Faction.REP_HOSTILE)  return "§eUnfriendly";
        return "§cHostile";
    }
}
