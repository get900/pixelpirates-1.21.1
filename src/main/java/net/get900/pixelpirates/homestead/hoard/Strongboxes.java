package net.get900.pixelpirates.homestead.hoard;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.item.food.PirateFoods;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * SEALED STRONGBOXES (2026-10-01, user note: "make the treasure block almost loot box like - it scrolls along and gives you
 * an item based on percentages"). Strongboxes (common / rare / legendary) come from dungeon treasure, boss hoards and
 * treasure maps. Use one on a TREASURE HOARD: the hoard pays the locksmith in pirate coins, the client spins a reel
 * (CaseScreen, S2C {@link #SPIN}) and the prize - rolled HERE, by weight - is handed over when the reel stops (6 s).
 */
public final class Strongboxes {
    private Strongboxes() {}

    public static final Identifier SPIN = new Identifier("pixelpirates", "strongbox_spin");
    public static final String[] RARITY = {"Common", "Uncommon", "Rare", "Epic", "Legendary"};
    public static final int REEL = 40, WIN_AT = 33, SPIN_TICKS = 120;

    /** One possible prize: rarity 0..4, weight, and the stack it hands out. */
    public record Prize(int rarity, int weight, Function<Random, ItemStack> stack) {}

    private static Prize p(int rarity, int weight, Item item, int lo, int hi) {
        return new Prize(rarity, weight, r -> new ItemStack(item, lo + (hi > lo ? r.nextInt(hi - lo + 1) : 0)));
    }

    private static Prize oneOf(int rarity, int weight, Item... items) {
        return new Prize(rarity, weight, r -> new ItemStack(items[r.nextInt(items.length)]));
    }

    private static Item dish(String id) { return PirateFoods.item(id); }

    /** Prize pools by tier (0 common, 1 rare, 2 legendary). Rarity bands ~ 60 / 25 / 12 / 2.6 / 0.4 %. */
    static List<Prize> pool(int tier) {
        List<Prize> l = new ArrayList<>();
        switch (tier) {
            case 0 -> {
                l.add(p(0, 15, ModItems.COIN, 20, 40)); l.add(p(0, 12, Items.IRON_INGOT, 2, 5)); l.add(p(0, 11, ModItems.ROPE, 4, 8));
                l.add(p(0, 11, ModItems.HARDTACK, 3, 6)); l.add(p(0, 11, ModItems.CANNON_BALL, 6, 12));
                l.add(p(1, 6, ModItems.CRAB_SHELL, 3, 6)); l.add(p(1, 6, ModItems.TATTERED_CLOTH, 4, 8)); l.add(p(1, 5, ModItems.SIREN_SCALE, 2, 4));
                l.add(p(1, 4, ModItems.REEF_PEARL, 1, 3)); l.add(p(1, 4, ModItems.DYNAMITE, 2, 4));
                l.add(oneOf(2, 6, ModItems.CASTAWAY_HELMET, ModItems.CASTAWAY_CHESTPLATE, ModItems.CASTAWAY_LEGGINGS, ModItems.CASTAWAY_BOOTS,
                        ModItems.NAVY_OFFICER_HELMET, ModItems.NAVY_OFFICER_CHESTPLATE, ModItems.NAVY_OFFICER_LEGGINGS, ModItems.NAVY_OFFICER_BOOTS,
                        ModItems.CORSAIR_HELMET, ModItems.CORSAIR_CHESTPLATE, ModItems.CORSAIR_LEGGINGS, ModItems.CORSAIR_BOOTS));
                l.add(oneOf(2, 6, ModItems.MARLINSPIKE, ModItems.BOARDING_SABRE, ModItems.NAVAL_RAPIER, ModItems.OFFICERS_SABRE, ModItems.BOARDING_AXE));
                l.add(oneOf(3, 2, ModItems.SIREN_CONCH, dish("rackhams_reserve"), dish("serpent_steak")));
                l.add(p(3, 1, ModItems.PIRATE_COIN, 32, 48));
                l.add(p(4, 1, ModItems.SEAFARERS_TOKEN, 1, 1));
            }
            case 1 -> {
                l.add(p(0, 14, ModItems.COIN, 40, 80)); l.add(p(0, 12, ModItems.BRIMSTONE, 3, 6)); l.add(p(0, 12, ModItems.VOLCANIC_EMBER, 3, 6));
                l.add(p(0, 11, ModItems.ECTOPLASM, 2, 4)); l.add(p(0, 11, ModItems.CURSED_BONE, 3, 6));
                l.add(p(1, 8, ModItems.OBSIDIAN_SHARD, 2, 4)); l.add(p(1, 6, ModItems.LOST_SOUL, 1, 1)); l.add(p(1, 6, ModItems.DEPTH_CHARGE, 2, 4));
                l.add(p(1, 5, ModItems.SHIP_REPAIR_KIT, 2, 3));
                l.add(oneOf(2, 6, ModItems.ASHEN_HELMET, ModItems.ASHEN_CHESTPLATE, ModItems.ASHEN_LEGGINGS, ModItems.ASHEN_BOOTS,
                        ModItems.CURSED_BONE_HELMET, ModItems.CURSED_BONE_CHESTPLATE, ModItems.CURSED_BONE_LEGGINGS, ModItems.CURSED_BONE_BOOTS));
                l.add(oneOf(2, 6, ModItems.CORSAIR_CUTLASS, ModItems.BOARDING_PIKE, ModItems.EMBERBRAND, ModItems.SOULRENDER, ModItems.WRAITHBLADE));
                List<Item> boss = new ArrayList<>(List.of(ModItems.bossSet(1)));
                boss.addAll(List.of(ModItems.bossSet(2)));
                l.add(oneOf(3, 2, boss.toArray(Item[]::new)));
                l.add(oneOf(3, 1, dish("molten_core_chili"), dish("phantom_hardtack")));
                l.add(p(4, 1, ModItems.SEAFARERS_TOKEN, 1, 1));
            }
            default -> {
                l.add(p(0, 15, ModItems.COIN, 90, 160)); l.add(p(0, 13, ModItems.KRAKEN_SCALE, 3, 6)); l.add(p(0, 12, ModItems.LUMINOUS_ICHOR, 2, 4));
                l.add(p(0, 10, ModItems.ABYSSAL_PEARL, 1, 3));
                l.add(p(1, 8, ModItems.ABYSSAL_PEARL, 3, 5)); l.add(p(1, 7, ModItems.LOST_SOUL, 2, 2)); l.add(p(1, 6, ModItems.KRILL_CLUSTER, 3, 6));
                l.add(p(1, 4, ModItems.PIRATE_COIN, 32, 64));
                l.add(oneOf(2, 6, ModItems.KRAKEN_SCALE_HELMET, ModItems.KRAKEN_SCALE_CHESTPLATE, ModItems.KRAKEN_SCALE_LEGGINGS, ModItems.KRAKEN_SCALE_BOOTS));
                l.add(oneOf(2, 6, ModItems.KRAKEN_FANG, ModItems.STORMCALLER, ModItems.ABYSSAL_HARPOON));
                List<Item> boss = new ArrayList<>(List.of(ModItems.bossSet(3)));
                boss.addAll(List.of(ModItems.bossSet(4)));
                l.add(oneOf(3, 2, boss.toArray(Item[]::new)));
                l.add(oneOf(3, 1, dish("royal_tide_feast"), dish("bloodfin_fillet"), dish("kraken_platter"), dish("last_meal")));
                l.add(p(4, 1, ModItems.SEAFARERS_TOKEN, 1, 2));
            }
        }
        return l;
    }

    static Prize roll(List<Prize> pool, Random r) {
        int total = pool.stream().mapToInt(Prize::weight).sum(), x = r.nextInt(total);
        for (Prize p : pool) if ((x -= p.weight()) < 0) return p;
        return pool.get(0);
    }

    /** Coins the hoard pays to crack a strongbox of this tier. */
    public static int cost(int tier) { return new int[]{16, 48, 128}[tier]; }

    private record Pending(ItemStack prize, int rarity, long at, BlockPos hoard) {}
    private static final ConcurrentHashMap<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    /** TreasureHoardBlock: the player used a strongbox on the hoard. */
    public static void open(ServerPlayerEntity p, TreasureHoardBlockEntity hoard, ItemStack box, int tier) {
        if (PENDING.containsKey(p.getUuid())) { p.sendMessage(Text.literal("The reel is still spinning...").formatted(Formatting.GRAY), true); return; }
        int cost = cost(tier);
        if (hoard.coins() < cost && !p.isCreative()) {
            p.sendMessage(Text.literal("The locksmith wants " + cost + " pirate coins from this hoard (it holds " + hoard.coins() + ").")
                    .formatted(Formatting.RED), true);
            return;
        }
        if (!p.isCreative()) { hoard.add(-cost); box.decrement(1); }
        spin(p, tier, hoard.getPos());
    }

    /** Roll a prize of this tier, spin the reel on the player's screen, hand it over when it stops (origin = where an
     *  offline player's prize is dropped). Used by hoards (strongboxes) and lair TREASURE BLOCKS (one free spin each). */
    public static void spin(ServerPlayerEntity p, int tier, BlockPos origin) {
        Random r = p.getRandom();
        List<Prize> pool = pool(tier);
        Prize win = roll(pool, r);
        ItemStack prize = win.stack().apply(r);
        // the reel: random fillers by weight, the prize at WIN_AT
        NbtCompound out = new NbtCompound();
        NbtList reel = new NbtList();
        for (int i = 0; i < REEL; i++) {
            Prize f = i == WIN_AT ? win : roll(pool, r);
            ItemStack s = i == WIN_AT ? prize : f.stack().apply(r);
            NbtCompound e = new NbtCompound();
            e.putString("Id", Registries.ITEM.getId(s.getItem()).toString());
            e.putInt("N", s.getCount());
            e.putInt("R", f.rarity());
            reel.add(e);
        }
        out.put("Reel", reel);
        out.putInt("Win", WIN_AT);
        out.putInt("Tier", tier);
        var buf = PacketByteBufs.create();
        buf.writeNbt(out);
        ServerPlayNetworking.send(p, SPIN, buf);
        p.getServerWorld().playSound(null, origin, SoundEvents.BLOCK_CHEST_LOCKED, SoundCategory.BLOCKS, 1f, 0.7f);
        PENDING.put(p.getUuid(), new Pending(prize, win.rarity(), p.getServer().getTicks() + SPIN_TICKS, origin));
    }

    /** Server tick: hand over prizes whose reel has stopped. */
    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        long now = server.getTicks();
        PENDING.entrySet().removeIf(e -> {
            if (now < e.getValue().at()) return false;
            Pending pd = e.getValue();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
            if (p != null) {
                p.getInventory().offerOrDrop(pd.prize());
                p.sendMessage(Text.literal("[*] " + RARITY[pd.rarity()] + ": " + pd.prize().getCount() + " x " + pd.prize().getName().getString())
                        .formatted(new Formatting[]{Formatting.GRAY, Formatting.GREEN, Formatting.AQUA, Formatting.LIGHT_PURPLE, Formatting.GOLD}[pd.rarity()]), false);
                p.getServerWorld().playSound(null, p.getBlockPos(), pd.rarity() >= 3 ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.ENTITY_PLAYER_LEVELUP,
                        SoundCategory.PLAYERS, 0.8f, 1.2f);
            } else {                                                     // left mid-spin: the prize waits on the hoard
                var world = server.getOverworld();
                for (var w : server.getWorlds()) if (!w.getBlockState(pd.hoard()).isAir()) { world = w; break; }
                net.minecraft.util.ItemScatterer.spawn(world, pd.hoard().getX() + 0.5, pd.hoard().getY() + 1, pd.hoard().getZ() + 0.5, pd.prize());
            }
            return true;
        });
    }

    public static boolean busy(PlayerEntity p) { return PENDING.containsKey(p.getUuid()); }
}
