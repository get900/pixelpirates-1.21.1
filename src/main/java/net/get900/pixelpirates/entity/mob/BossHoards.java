package net.get900.pixelpirates.entity.mob;

import net.get900.pixelpirates.homestead.HomesteadState;
import net.get900.pixelpirates.mixin.LootableContainerAccessor;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SEALED BOSS HOARDS (2026-10-01, user notes on the Item Progression overview). A boss's hoard (and the Bloodfin's
 * armory) cannot be opened until the player is READY for that boss in the chain - a player could otherwise swim into
 * Bloodfin's Reef on day one and take tier III boss armor. Trying anyway warns; trying again within 15 s WAKES the boss
 * in DEFIANT mode against that player (ModBoss#challenge: x2.5 damage dealt, x0.4 taken). Beat it anyway and you get
 * its drops, the hoard and a boss-armor piece - but no chain step, relic or advancement (defiantVictory).
 * Sealed loot never rolls for hoppers or explosions either (SealedHoardLootMixin); sealed chests can't be broken.
 * The seal follows the chest's unrolled loot table, so it also covers existing worlds; an opened chest is just a chest.
 */
public final class BossHoards {
    private BossHoards() {}

    /** Loot table -> chain index of the boss it belongs to. */
    private static final Map<Identifier, Integer> SEALED = new HashMap<>();
    static {
        seal("rackham_hoard", "captain_rackham");
        seal("serpent_hoard", "sea_serpent");
        seal("citadel_hoard", "molten_warlord");
        seal("dutchman_hoard", "ghost_captain");
        seal("court_hoard", "abyssal_king");
        seal("whalers_hoard", "bloodfin");
        seal("whalers_armory", "bloodfin");
        seal("kraken_hoard", "kraken");
        seal("revenant_hoard", "chained_revenant");
        seal("heart_hoard", "abyssal_heart");
        for (String t : new String[]{"leviathan_rift_hoard", "leviathan_rift_altar", "leviathan_gullet_wreck", "leviathan_gullet_supplies",
                "leviathan_spire_ruin"}) seal(t, "leviathan");
    }

    private static void seal(String table, String boss) {
        SEALED.put(new Identifier("pixelpirates", "chests/" + table), BossProgression.indexOf(boss));
    }

    private record Warning(BlockPos pos, long at) {}
    private static final Map<UUID, Warning> WARNED = new HashMap<>();
    private static final int RETRY_WINDOW = 20 * 15;

    /** Chain index this container is sealed to, or -1. */
    private static int index(BlockEntity be) {
        if (!(be instanceof LootableContainerBlockEntity c)) return -1;
        Identifier t = ((LootableContainerAccessor) c).pp_getLootTableId();
        if (t == null) return -1;
        Integer i = SEALED.get(t);
        return i == null ? -1 : i;
    }

    /** Is this player allowed in? (creative, ready for the boss, or beat it in a defiant fight) */
    private static boolean allowed(PlayerEntity p, int index) {
        if (p.isCreative() || BossProgression.eligible(p, index)) return true;
        return p.getServer() != null && HomesteadState.get(p.getServer()).flag(flag(index, p));
    }

    private static String flag(int index, PlayerEntity p) { return "defiant_won:" + index + ":" + p.getUuidAsString(); }

    /** For SealedHoardLootMixin: true = don't roll the loot now (no player, or one who isn't ready). */
    public static boolean sealedFor(LootableContainerBlockEntity c, @Nullable PlayerEntity p) {
        int i = index(c);
        return i >= 0 && (p == null || !allowed(p, i));
    }

    /** Is any boss hoard within {@code r} blocks still sealed to this player? (lair treasure blocks follow their hoard) */
    public static boolean sealedNear(ServerWorld world, BlockPos pos, PlayerEntity p, int r) {
        for (BlockPos q : BlockPos.iterate(pos.add(-r, -4, -r), pos.add(r, 4, r)))
            if (world.getBlockEntity(q) instanceof LootableContainerBlockEntity c && sealedFor(c, p)) return true;
        return false;
    }

    /** UseBlockCallback: block opening a sealed hoard; a second try wakes its boss. */
    public static ActionResult onUse(ServerPlayerEntity p, ServerWorld world, BlockPos pos) {
        int i = index(world.getBlockEntity(pos));
        if (i < 0 || allowed(p, i)) return ActionResult.PASS;
        String boss = BossProgression.CHAIN.get(i).name();
        world.playSound(null, pos, SoundEvents.BLOCK_CHEST_LOCKED, SoundCategory.BLOCKS, 1.0f, 0.6f);
        if (BossProgression.CHAIN.get(i).boss().equals("leviathan")) {      // there is only one - no early hunt
            p.sendMessage(Text.literal("This belongs to " + boss + ". It is beyond you - first defeat "
                    + nextName(p) + ".").formatted(Formatting.DARK_PURPLE), true);
            return ActionResult.FAIL;
        }
        Warning w = WARNED.get(p.getUuid());
        long now = world.getTime();
        if (w == null || !w.pos().equals(pos) || now - w.at() > RETRY_WINDOW) {
            WARNED.put(p.getUuid(), new Warning(pos, now));
            p.sendMessage(Text.literal("[!] This is " + boss + "'s hoard, and you are not ready for it. Touch it again and "
                    + boss + " wakes - far stronger than you can handle.").formatted(Formatting.RED), false);
            return ActionResult.FAIL;
        }
        WARNED.remove(p.getUuid());
        ModBoss guardian = guardian(world, pos, i);
        if (guardian == null) {
            p.sendMessage(Text.literal("The hoard is bound to " + boss + ", and its master is not here. Face it first.")
                    .formatted(Formatting.DARK_PURPLE), true);
            return ActionResult.FAIL;
        }
        guardian.challenge(p);
        return ActionResult.FAIL;
    }

    /** PlayerBlockBreakEvents.BEFORE: a sealed hoard can't be broken open either. */
    public static boolean mayBreak(PlayerEntity p, BlockEntity be) {
        int i = index(be);
        if (i < 0 || allowed(p, i)) return true;
        p.sendMessage(Text.literal("The hoard is sealed to " + BossProgression.CHAIN.get(i).name() + ".").formatted(Formatting.DARK_PURPLE), true);
        return false;
    }

    /** The boss beaten early by a defiant challenger: its gear, but no progress (ModBoss#onDeath). */
    public static void defiantVictory(ServerPlayerEntity p, int index) {
        net.get900.pixelpirates.item.BossArmor.onBossKilled(p, index);
        if (p.getServer() != null) HomesteadState.get(p.getServer()).setFlag(flag(index, p));
        p.sendMessage(Text.literal("[*] Against all odds, you won. Its hoard is yours - but the chain of the seas does not move: "
                + nextName(p) + " still waits.").formatted(Formatting.GOLD), false);
    }

    private static String nextName(PlayerEntity p) {
        var n = BossProgression.next(p);
        return n == null ? "the bosses before it" : n.name();
    }

    @Nullable
    private static ModBoss guardian(ServerWorld world, BlockPos pos, int index) {
        return world.getEntitiesByClass(ModBoss.class, new Box(pos).expand(160), b -> b.isAlive() && b.chainIndex() == index)
                .stream().min(java.util.Comparator.comparingDouble(b -> b.squaredDistanceTo(pos.toCenterPos()))).orElse(null);
    }
}
