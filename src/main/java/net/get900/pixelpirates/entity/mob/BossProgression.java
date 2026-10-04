package net.get900.pixelpirates.entity.mob;

import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.util.PlayerProgressionComponent;
import net.get900.pixelpirates.world.PlayerProgressionManager;
import net.get900.pixelpirates.world.TestModes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * THE BOSS CHAIN - the ten bosses are fought in one fixed order, per player:
 * <pre>
 *  1 Captain Rackham  (Z1) -> unlocks zone 2       6 Bloodfin         (Z1)
 *  2 Sea Serpent      (Z2) -> unlocks zone 3       7 Kraken           (Z2)
 *  3 Molten Warlord   (Z3) -> unlocks zone 4       8 Chained Revenant (Z4)
 *  4 Ghost Captain    (Z4) -> unlocks zone 5       9 Abyssal Heart    (Z5) -> opens the Leviathan's Rift
 *  5 Abyssal King     (Z5)                        10 Leviathan        (Z5) - final boss
 * </pre>
 * A player's progress is the number of chain bosses they have beaten (NBT "PPBossStep"). A boss
 * further along the chain is SEALED to that player: it ignores them and shrugs off their attacks
 * (see ModBoss). Killing the boss you are on advances you, unlocks its zone (MobSpec.unlocksZone)
 * and hands you its relic - a {@link net.get900.pixelpirates.item.custom.BossRelicItem} that
 * counters the NEXT boss or its region and points the way to that boss's lair.
 * /pptest god bypasses the seal (testing aid); /ppboss status|set|next manage it.
 */
public final class BossProgression {
    private BossProgression() {}

    /** One link of the chain: boss id, the dungeon that holds it, display name, relic it awards (null = none). */
    public record Step(String boss, String lair, String name, @Nullable Item relic) {}

    public static final List<Step> CHAIN = List.of(
            new Step("captain_rackham", "rackham_fort", "Captain Rackham", ModItems.RACKHAMS_DIVING_CHARM),
            new Step("sea_serpent", "serpent_trench", "the Sea Serpent", ModItems.SERPENTS_TIDE_PEARL),
            new Step("molten_warlord", "cinder_forge", "the Molten Warlord", ModItems.EVERBURNING_LANTERN),
            new Step("ghost_captain", "ghost_ship", "the Ghost Captain", ModItems.SPECTRAL_ANCHOR),
            new Step("abyssal_king", "abyssal_throne", "the Abyssal King", ModItems.ROYAL_TIDE_SIGIL),
            new Step("bloodfin", "bloodfin_reef", "the Bloodfin", ModItems.BLOODFIN_RAZOR_TOOTH),
            new Step("kraken", "kraken_maw", "the Kraken", ModItems.KRAKENS_INK_HEART),
            new Step("chained_revenant", "revenant_crypt", "the Chained Revenant", ModItems.BROKEN_SHACKLE),
            new Step("abyssal_heart", "abyssal_heart_lair", "the Abyssal Heart", ModItems.ABYSSAL_HEARTSTONE),
            new Step("leviathan", "leviathan_rift", "the Leviathan", ModItems.CROWN_OF_THE_DROWNED));

    /** Chain index of a boss id, or -1 if it isn't a chain boss. */
    public static int indexOf(String bossId) {
        for (int i = 0; i < CHAIN.size(); i++) if (CHAIN.get(i).boss().equals(bossId)) return i;
        return -1;
    }

    /** The boss a relic was made to beat (the step after the one that awards it). */
    @Nullable
    public static Step targetOf(Item relic) {
        for (int i = 0; i < CHAIN.size() - 1; i++) if (CHAIN.get(i).relic() == relic) return CHAIN.get(i + 1);
        return null;
    }

    public static int progress(PlayerEntity p) { return ((PlayerProgressionComponent) p).pp_getBossStep(); }

    public static void setProgress(PlayerEntity p, int step) {
        ((PlayerProgressionComponent) p).pp_setBossStep(Math.max(0, Math.min(CHAIN.size(), step)));
    }

    /** May this player fight the boss at chain index {@code index}? */
    public static boolean eligible(PlayerEntity p, int index) {
        if (index < 0) return true;
        if (p instanceof ServerPlayerEntity sp && TestModes.god(sp)) return true;
        return progress(p) >= index;
    }

    /** The boss this player must beat next, or null when the chain is complete. */
    @Nullable
    public static Step next(PlayerEntity p) {
        int s = progress(p);
        return s < CHAIN.size() ? CHAIN.get(s) : null;
    }

    /** Message shown to a player who can't fight this boss yet. */
    public static Text sealedMessage(PlayerEntity p, int index) {
        Step need = next(p);
        String who = CHAIN.get(index).name();
        String first = need != null ? need.name() : "the bosses before it";
        return Text.literal(capitalise(who) + " is beyond you - first defeat " + first + ".").formatted(Formatting.DARK_PURPLE);
    }

    /** Called for every player credited with a chain boss kill (ModBoss#onDeath). */
    public static void onBossKilled(ServerPlayerEntity p, int index, int unlocksZone) {
        if (index < 0) return;
        Step step = CHAIN.get(index);
        net.get900.pixelpirates.util.AdvancementHelper.grant(p, "boss_" + step.boss());   // tools/gen_advancements.py
        net.get900.pixelpirates.world.PirateXp.boss(p, index, progress(p) == index);        // pirate XP: big on the first kill
        net.get900.pixelpirates.item.BossArmor.onBossKilled(p, index);     // a boss-set piece they don't own yet
        boolean advanced = progress(p) == index;
        if (advanced) {
            setProgress(p, index + 1);
            if (unlocksZone > 0) PlayerProgressionManager.unlockZone(p, unlocksZone);
        }
        // relic: on the first kill, or to replace a lost one on a re-kill
        if (step.relic() != null && (advanced || !has(p, step.relic()))) {
            p.getInventory().offerOrDrop(new ItemStack(step.relic()));
            p.sendMessage(Text.literal("[*] You claim ").formatted(Formatting.GOLD)
                    .append(Text.translatable(step.relic().getTranslationKey()).formatted(Formatting.LIGHT_PURPLE)), false);
        }
        if (!advanced) return;
        Step nxt = next(p);
        if (index == indexOf("abyssal_heart")) {
            p.sendMessage(Text.literal("[~] The Leviathan's Rift has opened. The Heartstone will lead you to it.")
                    .formatted(Formatting.DARK_AQUA, Formatting.BOLD), false);
        } else if (nxt != null) {
            p.sendMessage(Text.literal("[~] New pages in your Chronicle: " + capitalise(nxt.name()) + ". Right-click your relic to find its lair.")
                    .formatted(Formatting.AQUA), false);
        } else {
            p.sendMessage(Text.literal("[X] The Leviathan is slain. Every sea is yours, Captain.")
                    .formatted(Formatting.GOLD, Formatting.BOLD), false);
        }
    }

    /** Relics SILENCED until this server tick (the Abyssal Heart drowns them out in a co-op fight). */
    private static final java.util.Map<java.util.UUID, Long> SILENCED = new java.util.concurrent.ConcurrentHashMap<>();

    /** Silence this player's relics for `ticks` (refreshed every second while the co-op Heart fight lasts). */
    public static void silenceRelics(PlayerEntity p, int ticks) {
        if (p.getServer() != null) SILENCED.put(p.getUuid(), p.getServer().getTicks() + (long) ticks);
    }

    /** Are this player's relics silenced right now? (Only the Diving Charm's water breathing still works.) */
    public static boolean relicsSilenced(PlayerEntity p) {
        Long until = SILENCED.get(p.getUuid());
        if (until == null || p.getServer() == null) return false;
        if (p.getServer().getTicks() > until) { SILENCED.remove(p.getUuid()); return false; }
        return true;
    }

    /** Carries the relic AND its power answers (not silenced). Use for combat effects; {@link #has} for ownership. */
    public static boolean relicActive(PlayerEntity p, Item item) {
        if (relicsSilenced(p)) return false;
        if (has(p, item)) return true;
        Item weapon = net.get900.pixelpirates.item.RelicWeapons.weaponOf(item);    // or the relic weapon forged from it
        return weapon != null && has(p, weapon);
    }

    /** True if the player carries the item anywhere in their inventory (hotbar, main, offhand, armor). */
    public static boolean has(PlayerEntity p, Item item) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) if (inv.getStack(i).isOf(item)) return true;
        return false;
    }

    /** Old saves only stored the unlocked zone; derive how far along the chain that implies. */
    public static int migrateFromZone(int unlockedZone) {
        return Math.max(0, Math.min(4, unlockedZone - 1));
    }

    static String capitalise(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
}
