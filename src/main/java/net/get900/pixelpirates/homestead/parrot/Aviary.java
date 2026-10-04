package net.get900.pixelpirates.homestead.parrot;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.get900.pixelpirates.homestead.HomesteadState;
import net.get900.pixelpirates.homestead.trade.PortTraders;
import net.get900.pixelpirates.world.gen.PortCityLayout;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayList;
import java.util.List;

/**
 * THE PARROT KEEPER'S AVIARY (spawn island townhouse #25, PortCityLayout.parrotKeepersAviary): the domed cage on the
 * street holds four wild parrots. EVERY GAME DAY the keeper turns the stock over (the first time a player is within 48
 * after the day changes, with the cage loaded): last day's untamed birds go and four new ones are drawn by RARITY
 * from every ParrotTypes type (vanilla + our own), so the
 * rare colours are worth checking back for - a rare or very rare bird is announced to players nearby. Walk in, feed one
 * seeds, and it's your ship's parrot (ParrotCompanion). /ppaviary turns the stock over now.
 * Planned (2026-10-03, with the unique NPCs): the keeper SELLS the birds instead of you taming them in the cage.
 */
public final class Aviary {
    private Aviary() {}

    /** Where the birds are put: the two perch slabs and two clear floor cells (all reachable from the door - the old
     *  y73 spots were above the tree's leaf canopy, sealed in under the dome). */
    private static final double[][] SPOTS = {{-20.5, 70.5, 1.5}, {-24.5, 69.5, 5.5}, {-22.5, 68, 1.5}, {-22.5, 68, 5.5}};
    private static final String DAY_KEY = "aviary_day";

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(w -> {
            if (w.getTime() % 100 != 13 || !w.getRegistryKey().equals(PortTraders.DIM)) return;
            long day = w.getTimeOfDay() / 24000L;
            HomesteadState st = HomesteadState.get(w.getServer());
            if (st.number(DAY_KEY, -1) == day) return;
            int[] c = PortCityLayout.AVIARY_CENTRE;
            if (!w.isChunkLoaded(ChunkPos.toLong(new BlockPos(c[0], c[1], c[2])))) return;
            if (w.getClosestPlayer(c[0], c[1], c[2], 48, false) == null) return;
            restock(w);
            st.setNumber(DAY_KEY, day);
        });
    }

    static Box cage() {
        int[] c = PortCityLayout.AVIARY_CENTRE;
        return new Box(c[0] - 3, c[1] - 2, c[2] - 3, c[0] + 4, c[1] + 8, c[2] + 4);
    }

    /** Turn the stock over: the untamed birds in the cage go, four new ones are drawn (all ParrotTypes, by weight). */
    public static List<ParrotTypes.PType> restock(ServerWorld w) {
        for (ParrotEntity p : w.getEntitiesByClass(ParrotEntity.class, cage(), p -> p.isAlive() && !p.isTamed())) p.discard();
        List<ParrotTypes.PType> got = new ArrayList<>();
        for (double[] s : SPOTS) {
            ParrotEntity p = EntityType.PARROT.create(w);
            if (p == null) continue;
            ParrotTypes.PType b = ParrotTypes.roll(ParrotTypes.Tier.COMMON, w.random::nextInt);
            p.refreshPositionAndAngles(s[0], s[1], s[2], w.random.nextFloat() * 360f, 0f);
            ParrotTypes.apply(p, b);
            p.setPersistent();
            w.spawnEntity(p);
            got.add(b);
        }
        announce(w, got);
        return got;
    }

    /** Tell players near the port about EVERY rare-or-better bird that came in (rarest first, "2x" for doubles). */
    private static void announce(ServerWorld w, List<ParrotTypes.PType> got) {
        java.util.Map<ParrotTypes.PType, Integer> rare = new java.util.TreeMap<>(
                java.util.Comparator.comparingInt((ParrotTypes.PType t) -> -t.tier().ordinal()).thenComparing(ParrotTypes.PType::name));
        for (ParrotTypes.PType b : got) if (b.tier().ordinal() >= ParrotTypes.Tier.RARE.ordinal()) rare.merge(b, 1, Integer::sum);
        if (rare.isEmpty()) return;
        net.minecraft.text.MutableText msg = Text.literal(rare.size() == 1 && rare.values().iterator().next() == 1
                ? "[~] The parrot keeper has a rare bird in the aviary today: " : "[~] The parrot keeper has rare birds in the aviary today: ").formatted(Formatting.GOLD);
        boolean first = true;
        for (var e : rare.entrySet()) {
            if (!first) msg.append(Text.literal(", ").formatted(Formatting.GOLD));
            first = false;
            msg.append(Text.literal((e.getValue() > 1 ? e.getValue() + "x " : "") + e.getKey().name()).formatted(e.getKey().tier().colour, Formatting.BOLD))
               .append(Text.literal(" (" + e.getKey().tier().label.toLowerCase() + ")").formatted(Formatting.GOLD));
        }
        msg.append(Text.literal("!").formatted(Formatting.GOLD));
        int[] c = PortCityLayout.AVIARY_CENTRE;
        for (ServerPlayerEntity p : w.getPlayers())
            if (p.squaredDistanceTo(c[0], c[1], c[2]) < 160 * 160) p.sendMessage(msg, false);
    }
}
