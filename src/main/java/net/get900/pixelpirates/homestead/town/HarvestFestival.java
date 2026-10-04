package net.get900.pixelpirates.homestead.town;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * THE HARVEST FESTIVAL (2026-10-05, TownCalendar.HARVEST_FESTIVAL; the user's idea: "once a season the chapel collects
 * food; the farmers, the miller and the cider maker bring their best; a big feast in the plaza with temporary tables").
 * MORNING (2000-5000): Hob, Elspeth, Cobb and Wilma carry their best to the chapel - each adds to the harvest display
 * before the altar. ALL DAY until the feast: players give food to the harvest through Father Anselm ("Give to the
 * harvest") - friendship with the whole town, once a day. EVENING (12500-15500): two long trestle tables laden with food
 * appear either side of the bazaar's grand fountain, the whole town comes to eat, Father Anselm says grace and Dan
 * plays; every player there gets a harvest supper, and the day's donors a blessing (Regeneration + Saturation).
 * Everything is display entities (nothing in the world is touched); cleared when the feast ends.
 * Test: /pptown event harvest (the deliveries now, the feast a minute later).
 */
public final class HarvestFestival {
    private HarvestFestival() {}

    static final String TAG = "pp_harvest";
    static final int BRING_FROM = 2000, BRING_TO = 5000, FEAST_FROM = 12500, FEAST_TO = 15500;
    static final int GY = 67, TABLE_Z = 30;                       // the bazaar's paving (PortCityLayout.MARKET_GY), the tables' row
    static final int[][] TABLES = {{-30, -12}, {12, 30}};          // x ranges, either side of the fountain (x -7..7)
    static final Vec3d ALTAR = TownEvents.ALTAR_FRONT;

    /** Who brings what to the chapel, and where it goes in the display (offset from the altar front). */
    static final Map<String, Object[]> BRINGERS = Map.of(
            "hob", new Object[]{"the finest wheat on the island", -2.5, new BlockState[]{Blocks.HAY_BLOCK.getDefaultState(), Blocks.WHEAT.getDefaultState().with(net.minecraft.block.CropBlock.AGE, 7)}},
            "elspeth", new Object[]{"a fleece as white as a sail", -1.0, new BlockState[]{Blocks.WHITE_WOOL.getDefaultState(), Blocks.WHITE_CARPET.getDefaultState()}},
            "cobb", new Object[]{"a cask of his best cider", 1.0, new BlockState[]{Blocks.BARREL.getDefaultState(), Blocks.PUMPKIN.getDefaultState()}},
            "wilma", new Object[]{"sacks of fresh flour and a great loaf", 2.5, new BlockState[]{Blocks.BROWN_WOOL.getDefaultState(), Blocks.MELON.getDefaultState()}});

    private static long forcedAt = Long.MIN_VALUE, lastDay = -1;
    private static final Set<String> delivered = new HashSet<>();
    private static final Set<UUID> donors = new HashSet<>();
    private static boolean tablesUp, graceSaid;

    // ------------------------------------------------------------------ when
    static boolean today(ServerWorld w) {
        return TownCalendar.today(w) == TownCalendar.Big.HARVEST_FESTIVAL || w.getTime() - forcedAt >= 0 && w.getTime() - forcedAt < 4800;
    }

    /** Ticks into the day's programme, in day-time units (a forced festival runs on its own clock). */
    private static int clock(ServerWorld w) {
        if (w.getTime() - forcedAt >= 0 && w.getTime() - forcedAt < 4800) {
            long t = w.getTime() - forcedAt;                                // 0-1200 bringing, 1200-4800 the feast
            return t < 1200 ? BRING_FROM + (int) t : FEAST_FROM + (int) (t - 1200);
        }
        return (int) (w.getTimeOfDay() % 24000L);
    }

    static boolean bringing(ServerWorld w) { int c = clock(w); return today(w) && c >= BRING_FROM && c < BRING_TO; }
    static boolean feast(ServerWorld w) { int c = clock(w); return today(w) && c >= FEAST_FROM && c < FEAST_TO; }
    /** Players may give to the harvest until the feast is over. */
    static boolean collecting(ServerWorld w) { return today(w) && clock(w) < FEAST_TO; }

    static String force(ServerWorld w) {
        forcedAt = w.getTime();
        reset(w);
        TownEvents.broadcastTown(w, "[Harvest Festival] The farmers are bringing their best to the chapel - the feast in the bazaar in a minute!");
        return "Harvest festival: deliveries now, the feast in a minute.";
    }

    // ------------------------------------------------------------------ plans
    static TownLife.Plan plan(ServerWorld w, Townsfolk.Folk f, Townsfolk.Phase ph) {
        if (!today(w) || ph == Townsfolk.Phase.SLEEP) return null;
        if (bringing(w) && BRINGERS.containsKey(f.id()) && !delivered.contains(f.id())) {
            double dx = (double) BRINGERS.get(f.id())[1];
            TownLife.Plan p = new TownLife.Plan(ph, TownLife.standNear(w, BlockPos.ofFloored(ALTAR.add(dx, 0, 2)), 2));
            p.look = ALTAR;
            return p;
        }
        if (feast(w) && (ph == Townsfolk.Phase.LEISURE || ph == Townsfolk.Phase.HOME || ph == Townsfolk.Phase.WORK && f.style() != Townsfolk.WorkStyle.ROUNDS)) {
            if (f.id().equals("dan")) {                                     // at the head of the east table, fiddling
                TownLife.Plan p = new TownLife.Plan(ph, new BlockPos(TABLES[1][1] + 2, GY + 1, TABLE_Z));
                p.act = TownsfolkEntity.Act.FIDDLE;
                p.look = new Vec3d(0, GY + 2, TABLE_Z + 0.5);
                return p;
            }
            if (f.id().equals("anselm")) {                                   // between the tables, by the fountain
                TownLife.Plan p = new TownLife.Plan(ph, new BlockPos(-9, GY + 1, TABLE_Z));
                p.look = new Vec3d(-20, GY + 2, TABLE_Z + 0.5);
                return p;
            }
            // a place at table: which table, which side, how far along - spread by name
            int h = Math.floorMod(Objects.hash(f.id(), "feast"), 4 * 19);
            int[] t = TABLES[h % 2];
            int side = (h / 2) % 2 == 0 ? -1 : 1;
            int x = t[0] + (h / 4) % (t[1] - t[0] + 1);
            TownLife.Plan p = new TownLife.Plan(ph, new BlockPos(x, GY + 1, TABLE_Z + side));
            p.look = new Vec3d(x + 0.5, GY + 1.6, TABLE_Z + 0.5);
            return p;
        }
        return null;
    }

    // ------------------------------------------------------------------ ticking (TownLife, every 40)
    static void tick(ServerWorld w) {
        long day = w.getTimeOfDay() / 24000L;
        boolean forced = w.getTime() - forcedAt >= 0 && w.getTime() - forcedAt < 4800;
        if (!today(w)) { if (tablesUp || !delivered.isEmpty()) reset(w); return; }
        if (!forced && lastDay != day) { lastDay = day; reset(w);
            TownEvents.broadcastTown(w, "[Harvest Festival] It's harvest day! Bring food to Father Anselm at the chapel - and come to the feast in the bazaar at dusk.");
        }
        int c = clock(w);
        if (c >= BRING_FROM && c < BRING_TO)
            for (String id : BRINGERS.keySet()) {
                if (delivered.contains(id)) continue;
                TownsfolkEntity e = TownLife.live(w, id);
                if (e == null) continue;
                if (forced && e.squaredDistanceTo(ALTAR) > 36) {             // a forced day: no time to walk the whole island
                    Vec3d at = ALTAR.add((double) BRINGERS.get(id)[1], 0, 2);
                    e.requestTeleport(at.x, at.y, at.z);
                }
                if (e.squaredDistanceTo(ALTAR) < 6 * 6) deliver(w, e);
            }
        if (c >= FEAST_FROM && c < FEAST_TO) {
            if (!tablesUp) { tables(w); tablesUp = true; TownLife.replanAll(w); }      // everyone comes to the feast
            if (!graceSaid && c >= FEAST_FROM + 200) { graceSaid = true; grace(w); }
            if (w.random.nextInt(3) == 0) {
                List<TownsfolkEntity> diners = w.getEntitiesByClass(TownsfolkEntity.class, new Box(-34, GY, TABLE_Z - 3, 34, GY + 4, TABLE_Z + 3), x -> true);
                if (!diners.isEmpty()) diners.get(w.random.nextInt(diners.size())).triggerAnim("action", w.random.nextBoolean() ? "talk" : "cheer");
            }
        } else if (c >= FEAST_TO && tablesUp) {
            reset(w);
            TownLife.replanAll(w);
            TownEvents.broadcastTown(w, "[Harvest Festival] The feast is over - the tables come down, and what's left goes to the poor box.");
        }
    }

    private static void deliver(ServerWorld w, TownsfolkEntity e) {
        String id = e.folkId();
        delivered.add(id);
        Object[] b = BRINGERS.get(id);
        BlockState[] what = (BlockState[]) b[2];
        double dx = (double) b[1];
        TownEvents.display(w, what[0], ALTAR.x + dx, ALTAR.y, ALTAR.z + 1.2, 0.7f, TAG);
        TownEvents.display(w, what[1], ALTAR.x + dx + 0.35, ALTAR.y, ALTAR.z + 1.7, 0.45f, TAG);
        w.spawnParticles(ParticleTypes.HAPPY_VILLAGER, ALTAR.x + dx, ALTAR.y + 0.8, ALTAR.z + 1.4, 8, 0.4, 0.3, 0.4, 0);
        e.triggerAnim("action", "flourish");
        TownMemory.news(w, "harvest", e.folk().name() + " brought " + b[0] + " to the harvest at the chapel.", id);
        TownEvents.broadcastTown(w, "[Harvest Festival] " + e.folk().name() + " brings " + b[0] + " to the chapel.");
        e.plan = null;                                                       // done: back to their day
    }

    /** Two trestle tables either side of the fountain, laden: cakes, pies (pumpkins), melons, bread (hay), cider (barrels), candles. */
    private static void tables(ServerWorld w) {
        TownEvents.clear(w, TAG, new BlockPos(0, GY, TABLE_Z), 40);
        BlockState top = Blocks.SPRUCE_SLAB.getDefaultState().with(net.minecraft.block.SlabBlock.TYPE, net.minecraft.block.enums.SlabType.TOP);
        BlockState[] food = {Blocks.CAKE.getDefaultState(), Blocks.PUMPKIN.getDefaultState(), Blocks.MELON.getDefaultState(), Blocks.HAY_BLOCK.getDefaultState(),
                Blocks.BARREL.getDefaultState(), Blocks.CANDLE.getDefaultState().with(net.minecraft.block.CandleBlock.CANDLES, 3).with(net.minecraft.block.CandleBlock.LIT, true)};
        float[] size = {0.8f, 0.42f, 0.4f, 0.38f, 0.36f, 1f};
        double y = GY + 1, z = TABLE_Z + 0.5;
        for (int[] t : TABLES)
            for (int x = t[0]; x <= t[1]; x++) {
                TownEvents.display(w, top, x + 0.5, y, z, 1f, TAG);
                if ((x - t[0]) % 4 == 0 || x == t[1]) TownEvents.display(w, Blocks.SPRUCE_FENCE.getDefaultState(), x + 0.5, y, z, 1f, TAG);
                int k = Math.floorMod(x * 7 + 3, food.length);
                TownEvents.display(w, food[k], x + 0.5, y + 1, z, size[k], TAG);
            }
        TownEvents.broadcastTown(w, "[Harvest Festival] The tables are laid in the bazaar - come and eat!");
    }

    private static void grace(ServerWorld w) {
        TownEvents.broadcastTown(w, "[Father Anselm] For the harvest of the land and the sea, for the hands that brought it and the friends who share it - we give thanks. Eat!");
        w.playSound(null, new BlockPos(0, GY + 2, TABLE_Z), SoundEvents.BLOCK_BELL_USE, SoundCategory.NEUTRAL, 2f, 1.2f);
        for (ServerPlayerEntity p : w.getPlayers(p -> p.squaredDistanceTo(0, GY + 1, TABLE_Z) < 30 * 30)) {
            p.getInventory().offerOrDrop(new ItemStack(Items.PUMPKIN_PIE, 2));
            p.getInventory().offerOrDrop(new ItemStack(Items.BREAD, 2));
            p.sendMessage(Text.literal("[Harvest Festival] A plate is passed down the table to you: pumpkin pie and fresh bread.").formatted(Formatting.GOLD), false);
            if (donors.contains(p.getUuid())) {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 20 * 120, 0));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION, 20 * 30, 0));
                p.sendMessage(Text.literal("[Father Anselm] And a blessing on you, captain, who gave to the harvest.").formatted(Formatting.GOLD), false);
            }
        }
    }

    // ------------------------------------------------------------------ players give (TownTalk, Father Anselm)
    static String donate(ServerPlayerEntity p) {
        ItemStack held = p.getMainHandStack();
        if (held.isEmpty() || !held.isFood()) return "Bring me something to eat for the harvest - bread, fish, fruit, anything that feeds a soul.";
        if (donors.contains(p.getUuid())) return "You've given already today, and generously. Come to the feast at dusk!";
        int n = Math.min(16, held.getCount());
        String what = held.getName().getString();
        if (!p.isCreative()) held.decrement(n);
        donors.add(p.getUuid());
        for (Townsfolk.Folk f : Townsfolk.ALL.values()) TownMemory.befriend(p, f, 2);
        TownMemory.news(p.getServerWorld(), "harvest", "Captain " + p.getName().getString() + " gave " + n + " " + what + " to the harvest.", "anselm");
        return "Bless you, captain - " + n + " " + what + " for the harvest table. The whole town will hear of it.";
    }

    private static void reset(ServerWorld w) {
        TownEvents.clear(w, TAG, new BlockPos(0, GY, TABLE_Z), 40);
        TownEvents.clear(w, TAG, BlockPos.ofFloored(ALTAR), 8);
        delivered.clear();
        donors.clear();
        tablesUp = false;
        graceSaid = false;
    }
}
