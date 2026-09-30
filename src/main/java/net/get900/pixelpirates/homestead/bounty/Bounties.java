package net.get900.pixelpirates.homestead.bounty;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.get900.pixelpirates.entity.mob.BossProgression;
import net.get900.pixelpirates.homestead.HomesteadItems;
import net.get900.pixelpirates.homestead.HomesteadState;
import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.world.faction.Faction;
import net.get900.pixelpirates.world.faction.FactionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;



/**
 * BOUNTY CONTRACTS (#16): every in-game day each player gets three contracts from any Bounty Board.
 *   HUNT    - kill N of a creature from your current waters (the pool follows your boss-chain progress);
 *   DELIVER - hand N of something in at a board (fish, rum, crops, monster parts);
 *   SINK    - sink or conquer one ship of a faction (hooked into FactionManager's ship/captain callbacks).
 * Using a board delivers what you carry, pays out every finished contract (pirate coins + reputation with a faction)
 * and lists the rest. Completed but unclaimed contracts survive the daily reroll. Stored per player in
 * HomesteadState.bounty(uuid): {Day, C:[{T,Id,Need,Have,Coins,Rep}]}.
 */
public final class Bounties {
    private Bounties() {}

    public static final String HUNT = "hunt", DELIVER = "deliver", SINK = "sink";

    /** [entity id, min, max] by tier (0 = starter seas .. 4 = abyss). */
    private static final String[][][] HUNTS = {
            {{"pixelpirates:shark", "3", "5"}, {"pixelpirates:cursed_monkey", "3", "5"}, {"minecraft:drowned", "6", "10"},
                    {"pixelpirates:raft_pirate", "3", "5"}, {"pixelpirates:pirate_crew", "4", "6"}, {"pixelpirates:chest_crab", "3", "5"}},
            {{"pixelpirates:siren", "2", "3"}, {"pixelpirates:void_squid", "3", "5"}, {"pixelpirates:kraken_tentacle", "2", "4"},
                    {"pixelpirates:reefback_fish", "3", "5"}, {"pixelpirates:pirate_crew", "6", "8"}},
            {{"pixelpirates:fire_pirate", "4", "6"}, {"pixelpirates:ember_wraith", "3", "5"}, {"pixelpirates:lava_scorpion", "3", "5"},
                    {"pixelpirates:magma_brute", "1", "2"}, {"pixelpirates:flame_sprite", "4", "6"}},
            {{"pixelpirates:skeleton_pirate", "5", "8"}, {"pixelpirates:ghost_shark", "2", "4"}, {"pixelpirates:phantom_pirate", "3", "5"},
                    {"pixelpirates:trident_skeleton", "4", "6"}, {"pixelpirates:mimic", "1", "2"}},
            {{"pixelpirates:abyss_crab", "4", "6"}, {"pixelpirates:abyssal_angler", "2", "3"}, {"pixelpirates:deep_lurker", "2", "4"},
                    {"pixelpirates:corrupted_diver", "3", "5"}, {"pixelpirates:abyss_eel", "3", "5"}}};

    private static Item[][] deliveries() {
        return new Item[][]{
                {HomesteadItems.PARROTFISH, HomesteadItems.RED_SNAPPER, Items.COD, HomesteadItems.PINEAPPLE, ModItems.BANANA, HomesteadItems.RAW_RUM, HomesteadItems.LOBSTER},
                {HomesteadItems.MAHI_MAHI, HomesteadItems.LIONFISH, HomesteadItems.LIME, ModItems.KRAKEN_INK, HomesteadItems.AGED_RUM, Items.PRISMARINE_SHARD},
                {HomesteadItems.EMBERFIN, HomesteadItems.CHILI_PEPPER, ModItems.VOLCANIC_EMBER, Items.MAGMA_CREAM, HomesteadItems.AGED_RUM},
                {HomesteadItems.GHOSTFIN, HomesteadItems.BONEFISH, ModItems.CURSED_BONE, HomesteadItems.VINTAGE_RUM, Items.PHANTOM_MEMBRANE},
                {HomesteadItems.ANGLERFRY, HomesteadItems.VOIDFIN, ModItems.KRAKEN_SCALE, HomesteadItems.VINTAGE_RUM, Items.GLOW_INK_SAC}};
    }

    public static int tier(PlayerEntity p) { return Math.min(4, BossProgression.progress(p) / 2); }

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (source.getAttacker() instanceof ServerPlayerEntity p)
                progress(p, HUNT, Registries.ENTITY_TYPE.getId(entity.getType()).toString(), 1);
        });
    }

    /** Called from FactionManager when a player sinks/conquers a faction ship. */
    public static void onShip(ServerPlayerEntity p, Faction f) { progress(p, SINK, f.name(), 1); }

    // ------------------------------------------------------------------ contracts
    static NbtCompound data(ServerPlayerEntity p) { return HomesteadState.get(p.getServer()).bounty(p.getUuid()); }

    /** Today's contracts for the player, rerolled when the day changes (finished ones are kept until paid). */
    public static NbtList contracts(ServerPlayerEntity p) {
        NbtCompound d = data(p);
        long day = p.getServerWorld().getTimeOfDay() / 24000L;
        if (!d.contains("C") || d.getLong("Day") != day) {
            NbtList keep = new NbtList();
            for (NbtElement e : d.getList("C", NbtElement.COMPOUND_TYPE)) if (done((NbtCompound) e)) keep.add(e);
            Random r = Random.create(p.getUuid().getLeastSignificantBits() ^ day * 31L);
            int t = tier(p);
            keep.add(hunt(r, t));
            keep.add(deliver(r, t));
            keep.add(r.nextInt(3) == 0 ? sink(r, t) : hunt(r, Math.max(0, t - r.nextInt(2))));
            d.put("C", keep);
            d.putLong("Day", day);
            HomesteadState.get(p.getServer()).touch();
        }
        return d.getList("C", NbtElement.COMPOUND_TYPE);
    }

    private static NbtCompound c(String type, String id, int need, int coins, String rep) {
        NbtCompound n = new NbtCompound();
        n.putString("T", type);
        n.putString("Id", id);
        n.putInt("Need", need);
        n.putInt("Have", 0);
        n.putInt("Coins", coins);
        n.putString("Rep", rep);
        return n;
    }

    private static NbtCompound hunt(Random r, int t) {
        String[][] pool = HUNTS[t];
        String[] h = pool[r.nextInt(pool.length)];
        int lo = Integer.parseInt(h[1]), hi = Integer.parseInt(h[2]);
        int need = lo + r.nextInt(hi - lo + 1);
        return c(HUNT, h[0], need, (4 + 3 * t) * need, Faction.MERCHANTS.name());
    }

    private static NbtCompound deliver(Random r, int t) {
        Item[] pool = deliveries()[t];
        Item i = pool[r.nextInt(pool.length)];
        int need = 4 + r.nextInt(9);
        return c(DELIVER, Registries.ITEM.getId(i).toString(), need, (2 + 2 * t) * need + 6, Faction.MERCHANTS.name());
    }

    private static NbtCompound sink(Random r, int t) {
        Faction[] targets = {Faction.PIRATES, Faction.NAVY, Faction.UNDEAD};
        Faction f = targets[r.nextInt(targets.length)];
        Faction payer = f == Faction.NAVY ? Faction.PIRATES : Faction.NAVY;
        return c(SINK, f.name(), 1, 60 + 30 * t, payer.name());
    }

    static boolean done(NbtCompound c) { return c.getInt("Have") >= c.getInt("Need"); }

    static void progress(ServerPlayerEntity p, String type, String id, int n) {
        NbtCompound d = data(p);
        if (!d.contains("C")) return;
        for (NbtElement e : d.getList("C", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) e;
            if (!c.getString("T").equals(type) || !c.getString("Id").equals(id) || done(c)) continue;
            c.putInt("Have", Math.min(c.getInt("Need"), c.getInt("Have") + n));
            HomesteadState.get(p.getServer()).touch();
            if (done(c)) {
                p.sendMessage(Text.literal("[Bounty] Contract complete: " + describe(c).getString() + " - collect at a Bounty Board").formatted(Formatting.GOLD), false);
                p.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.6f, 1.2f);
            } else {
                p.sendMessage(Text.literal("[Bounty] " + describe(c).getString() + " (" + c.getInt("Have") + "/" + c.getInt("Need") + ")").formatted(Formatting.YELLOW), true);
            }
            return;
        }
    }

    public static MutableText describe(NbtCompound c) {
        String id = c.getString("Id");
        int need = c.getInt("Need");
        return switch (c.getString("T")) {
            case HUNT -> Text.literal("Hunt " + need + " ").append(Registries.ENTITY_TYPE.get(new Identifier(id)).getName());
            case DELIVER -> Text.literal("Deliver " + need + " ").append(Registries.ITEM.get(new Identifier(id)).getName());
            default -> Text.literal("Sink or take a " + Faction.valueOf(id).name().toLowerCase() + " ship");
        };
    }

    // ------------------------------------------------------------------ the board
    /** Hand in deliveries, pay out finished contracts, list the rest. */
    public static void useBoard(ServerPlayerEntity p) {
        NbtList list = contracts(p);
        int paid = 0;
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            if (c.getString("T").equals(DELIVER) && !done(c)) {
                Item it = Registries.ITEM.get(new Identifier(c.getString("Id")));
                int want = c.getInt("Need") - c.getInt("Have");
                for (int s = 0; s < p.getInventory().size() && want > 0; s++) {
                    ItemStack st = p.getInventory().getStack(s);
                    if (!st.isOf(it)) continue;
                    int take = Math.min(want, st.getCount());
                    st.decrement(take);
                    want -= take;
                    c.putInt("Have", c.getInt("Have") + take);
                }
            }
        }
        p.sendMessage(Text.literal("=== Bounty Board - day " + (p.getServerWorld().getTimeOfDay() / 24000L) + " ===").formatted(Formatting.GOLD), false);
        for (int i = list.size() - 1; i >= 0; i--) {
            NbtCompound c = list.getCompound(i);
            if (!done(c)) continue;
            int coins = c.getInt("Coins");
            give(p, coins);
            Faction f = Faction.valueOf(c.getString("Rep"));
            FactionManager.modifyReputation(p, f, c.getString("T").equals(SINK) ? 40 : 15);
            p.sendMessage(Text.literal(" PAID  ").formatted(Formatting.GREEN).append(describe(c).formatted(Formatting.WHITE))
                    .append(Text.literal("  +" + coins + " coins, rep with " + f.name().toLowerCase()).formatted(Formatting.GOLD)), false);
            list.remove(i);
            paid++;
            data(p).putInt("Done", data(p).getInt("Done") + 1);
        }
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            p.sendMessage(Text.literal(" [ ]  ").formatted(Formatting.GRAY).append(describe(c).formatted(Formatting.WHITE))
                    .append(Text.literal("  " + c.getInt("Have") + "/" + c.getInt("Need") + "  reward " + c.getInt("Coins") + " coins").formatted(Formatting.GRAY)), false);
        }
        if (list.isEmpty()) p.sendMessage(Text.literal(" No more work today, sailor. New contracts at dawn.").formatted(Formatting.GRAY), false);
        HomesteadState.get(p.getServer()).touch();
        p.getServerWorld().playSound(null, p.getBlockPos(), paid > 0 ? SoundEvents.ENTITY_VILLAGER_YES : SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.BLOCKS, 0.8f, 1f);
    }

    static void give(PlayerEntity player, int coins) {
        while (coins > 0) {
            int n = Math.min(64, coins);
            coins -= n;
            ItemStack s = new ItemStack(ModItems.PIRATE_COIN, n);
            if (!player.getInventory().insertStack(s)) player.dropItem(s, false);
        }
    }

}
