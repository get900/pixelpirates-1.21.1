package net.get900.pixelpirates.homestead.cat;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.get900.pixelpirates.homestead.HomesteadState;
import net.get900.pixelpirates.homestead.parrot.ParrotTypes.Tier;
import net.get900.pixelpirates.homestead.trade.PortTraders;
import net.get900.pixelpirates.world.gen.PortCityLayout;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.CatEntity;
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
 * SHIP'S CATS (2026-10-04, townhouse #36 the ship's-cat keeper, PortCityLayout.shipsCatKeeper): the keeper's walled cat
 * garden holds four cats. EVERY GAME DAY (the first time a player is within 48 after the day changes, garden loaded) the
 * untamed ones go and four new ones are drawn by RARITY from every CatCoats coat - the aviary's scheme; a rare or better
 * cat is announced to players nearby. BUY one at the keeper's CATTERY COUNTER (coins by rarity - the garden's cats can't be
 * fish-tamed) and it's your SHIP'S CAT, tame on the spot: while one of your tamed
 * cats is within 16 blocks of you (following you, or sitting on your deck) you have Luck (fishing, loot) - Luck II from a
 * rare or better coat. /ppcattery turns the stock over now, /ppcat <coat> gives a tame cat.
 */
public final class Cattery {
    private Cattery() {}

    private static final String DAY_KEY = "cattery_day";
    public static final net.minecraft.util.Identifier COUNTER_OPEN = new net.minecraft.util.Identifier("pixelpirates", "cattery_open");
    public static final net.minecraft.util.Identifier COUNTER_BUY = new net.minecraft.util.Identifier("pixelpirates", "cattery_buy");

    /** A cat's price at the counter, by rarity (coins). */
    public static int price(Tier t) {
        return switch (t) { case COMMON -> 30; case UNCOMMON -> 80; case RARE -> 200; case VERY_RARE -> 500; case LEGENDARY -> 1200; };
    }

    public static void register() {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(COUNTER_BUY, (server, player, handler, buf, sender) -> {
            BlockPos counter = buf.readBlockPos();
            java.util.UUID cat = buf.readUuid();
            server.execute(() -> buy(player, counter, cat));
        });
        // the keeper's cats are for sale: no taming them with fish in the garden
        net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient || !(entity instanceof CatEntity c) || c.isTamed() || !world.getRegistryKey().equals(PortTraders.DIM)) return net.minecraft.util.ActionResult.PASS;
            var s = player.getStackInHand(hand);
            if (!(s.isOf(net.minecraft.item.Items.COD) || s.isOf(net.minecraft.item.Items.SALMON)) || !garden().contains(c.getPos())) return net.minecraft.util.ActionResult.PASS;
            player.sendMessage(Text.literal("The keeper's cats are for sale - see the counter in the cottage.").formatted(Formatting.YELLOW), true);
            return net.minecraft.util.ActionResult.FAIL;
        });
        ServerTickEvents.END_WORLD_TICK.register(w -> {
            if (w.getTime() % 40 == 7) shipsCats(w);
            if (w.getTime() % 100 != 17 || !w.getRegistryKey().equals(PortTraders.DIM)) return;
            long day = w.getTimeOfDay() / 24000L;
            HomesteadState st = HomesteadState.get(w.getServer());
            if (st.number(DAY_KEY, -1) == day) return;
            int[] c = PortCityLayout.CATTERY_CENTRE;
            if (!w.isChunkLoaded(ChunkPos.toLong(new BlockPos(c[0], c[1], c[2])))) return;
            if (w.getClosestPlayer(c[0], c[1], c[2], 48, false) == null) return;
            restock(w);
            st.setNumber(DAY_KEY, day);
        });
    }

    /** The cat garden + the cottage: where the keeper's untamed cats live. */
    static Box garden() {
        int[] b = PortCityLayout.CATTERY_BOX;
        return new Box(b[0], b[1], b[2], b[3] + 1, b[4] + 1, b[5] + 1);
    }

    /** Turn the stock over: the untamed cats go, four new ones are drawn (all CatCoats, by weight). */
    public static List<CatCoats.Coat> restock(ServerWorld w) {
        for (CatEntity c : w.getEntitiesByClass(CatEntity.class, garden(), c -> c.isAlive() && !c.isTamed())) c.discard();
        List<CatCoats.Coat> got = new ArrayList<>();
        for (double[] s : PortCityLayout.CATTERY_SPOTS) {
            CatEntity cat = EntityType.CAT.create(w);
            if (cat == null) continue;
            CatCoats.Coat coat = CatCoats.roll(Tier.COMMON, w.random::nextInt);
            cat.refreshPositionAndAngles(s[0], s[1], s[2], w.random.nextFloat() * 360f, 0f);
            CatCoats.apply(cat, coat);
            cat.setPersistent();
            w.spawnEntity(cat);
            got.add(coat);
        }
        announce(w, got);
        return got;
    }

    /** The counter: today's cats (the untamed ones in the garden), their coats + prices, the player's coins. */
    public static void openCounter(ServerPlayerEntity p, BlockPos counter) {
        net.minecraft.nbt.NbtCompound n = new net.minecraft.nbt.NbtCompound();
        n.putLong("Counter", counter.asLong());
        net.minecraft.nbt.NbtList cats = new net.minecraft.nbt.NbtList();
        for (CatEntity c : p.getServerWorld().getEntitiesByClass(CatEntity.class, garden(), c -> c.isAlive() && !c.isTamed())) {
            CatCoats.Coat coat = CatCoats.of(c);
            net.minecraft.nbt.NbtCompound e = new net.minecraft.nbt.NbtCompound();
            e.putUuid("Id", c.getUuid());
            e.putString("Coat", coat.id());
            e.putInt("Price", price(coat.tier()));
            cats.add(e);
        }
        n.put("Cats", cats);
        int coins = 0;
        for (int i = 0; i < p.getInventory().size(); i++) { var s = p.getInventory().getStack(i); if (s.isOf(net.get900.pixelpirates.item.ModItems.COIN)) coins += s.getCount(); }
        n.putInt("Coins", coins);
        net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeNbt(n);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, COUNTER_OPEN, buf);
    }

    private static void buy(ServerPlayerEntity p, BlockPos counter, java.util.UUID id) {
        ServerWorld w = p.getServerWorld();
        if (!(w.getBlockState(counter).getBlock() instanceof CatteryCounterBlock) || p.squaredDistanceTo(counter.toCenterPos()) > 64) return;
        if (!(w.getEntity(id) instanceof CatEntity c) || c.isTamed() || !c.isAlive() || !garden().contains(c.getPos())) {
            p.sendMessage(Text.literal("[~] That cat's already gone.").formatted(Formatting.RED), false);
            openCounter(p, counter);
            return;
        }
        CatCoats.Coat coat = CatCoats.of(c);
        int price = price(coat.tier());
        if (!p.isCreative()) {
            int have = 0;
            for (int i = 0; i < p.getInventory().size(); i++) { var s = p.getInventory().getStack(i); if (s.isOf(net.get900.pixelpirates.item.ModItems.COIN)) have += s.getCount(); }
            if (have < price) { p.sendMessage(Text.literal("[~] The keeper wants " + price + " coins for the " + coat.name() + ".").formatted(Formatting.RED), false); return; }
            int left = price;
            for (int i = 0; i < p.getInventory().size() && left > 0; i++) {
                var s = p.getInventory().getStack(i);
                if (!s.isOf(net.get900.pixelpirates.item.ModItems.COIN)) continue;
                int take = Math.min(left, s.getCount()); s.decrement(take); left -= take;
            }
        }
        c.setOwner(p);
        c.setSitting(false);
        c.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw(), 0f);
        w.playSound(null, p.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_CAT_PURREOW, net.minecraft.sound.SoundCategory.NEUTRAL, 1f, 1f);
        p.sendMessage(Text.literal("[~] The " + coat.name() + " is yours - your ship's cat now.").formatted(coat.tier().colour), false);
        openCounter(p, counter);
    }

    private static void announce(ServerWorld w, List<CatCoats.Coat> got) {
        List<CatCoats.Coat> rare = new ArrayList<>();
        for (CatCoats.Coat c : got) if (c.tier().ordinal() >= Tier.RARE.ordinal()) rare.add(c);
        if (rare.isEmpty()) return;
        rare.sort((a, b) -> b.tier().ordinal() - a.tier().ordinal());
        net.minecraft.text.MutableText msg = Text.literal(rare.size() == 1 ? "[~] The cat keeper has a rare cat in today: "
                : "[~] The cat keeper has rare cats in today: ").formatted(Formatting.GOLD);
        for (int i = 0; i < rare.size(); i++) {
            if (i > 0) msg.append(Text.literal(", ").formatted(Formatting.GOLD));
            msg.append(Text.literal(rare.get(i).name()).formatted(rare.get(i).tier().colour, Formatting.BOLD))
               .append(Text.literal(" (" + rare.get(i).tier().label.toLowerCase() + ")").formatted(Formatting.GOLD));
        }
        msg.append(Text.literal("!").formatted(Formatting.GOLD));
        int[] c = PortCityLayout.CATTERY_CENTRE;
        for (ServerPlayerEntity p : w.getPlayers())
            if (p.squaredDistanceTo(c[0], c[1], c[2]) < 160 * 160) p.sendMessage(msg, false);
    }

    /** THE SHIP'S CAT: Luck for every player with one of their own tamed cats within 16 blocks (the best coat counts). */
    private static void shipsCats(ServerWorld w) {
        for (ServerPlayerEntity p : w.getPlayers()) {
            int best = -1;
            for (CatEntity c : w.getEntitiesByClass(CatEntity.class, p.getBoundingBox().expand(16),
                    c -> c.isAlive() && c.isTamed() && p.getUuid().equals(c.getOwnerUuid())))
                best = Math.max(best, CatCoats.of(c).luck());
            if (best >= 0) p.addStatusEffect(new StatusEffectInstance(StatusEffects.LUCK, 60, best, true, false, true));
        }
    }
}
