package net.get900.pixelpirates.homestead.town;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.get900.pixelpirates.entity.mob.BossProgression;
import net.get900.pixelpirates.homestead.beard.BarberChairBlock;
import net.get900.pixelpirates.homestead.beard.Beards;
import net.get900.pixelpirates.homestead.cat.Cattery;
import net.get900.pixelpirates.homestead.cat.CatteryCounterBlock;
import net.get900.pixelpirates.homestead.parrot.Aviary;
import net.get900.pixelpirates.homestead.parrot.ParrotCollection;
import net.get900.pixelpirates.homestead.parrot.ParrotTypes;
import net.get900.pixelpirates.homestead.tattoo.TattooChairBlock;
import net.get900.pixelpirates.homestead.tattoo.Tattoos;
import net.get900.pixelpirates.world.faction.Faction;
import net.get900.pixelpirates.world.faction.FactionManager;
import net.minecraft.block.Block;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Talking to a townsperson: the DIALOGUE screen (client/screen/TownsfolkScreen) shows who they are, what they say, and
 * buttons - Chat (their next line), Trade (their shop), their SERVICE (the barber's book, the tattoo chair, the cattery,
 * Polly's birds - the aviary's daily stock, bought here by rarity -, Madame Zora's fortunes, the Governor's pardons for
 * those who crossed the Armada), Goodbye. Rufus Brine tells a tale about the boss you face next.
 */
public final class TownTalk {
    private TownTalk() {}

    public static final Identifier OPEN = new Identifier("pixelpirates", "town_talk_open");
    public static final Identifier ACT = new Identifier("pixelpirates", "town_talk_act");

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ACT, (server, player, handler, buf, sender) -> {
            int id = buf.readVarInt();
            String key = buf.readString(64);
            server.execute(() -> {
                if (player.getServerWorld().getEntityById(id) instanceof TownsfolkEntity e && e.squaredDistanceTo(player) < 8 * 8) act(player, e, key);
            });
        });
    }

    private record Opt(String key, String label) {}

    /** Open (or refresh) the screen. `say` = what they say this time (null = their greeting). */
    static void open(ServerPlayerEntity p, TownsfolkEntity e, @Nullable String say) {
        Townsfolk.Folk f = e.folk();
        if (f == null) return;
        if (say == null) {
            if (TownMemory.firstTalkToday(p, f.id(), TownMemory.day(p.getServerWorld()))) TownMemory.befriend(p, f, 2);
            say = greeting(p, e, f);
        }
        List<Opt> opts = new ArrayList<>();
        opts.add(new Opt("chat", "Chat"));
        if (!p.getMainHandStack().isEmpty()) opts.add(new Opt("gift", "Give a gift (" + p.getMainHandStack().getName().getString() + ")"));
        if (!e.getOffers().isEmpty()) opts.add(new Opt("trade", "Trade"));
        switch (f.service()) {
            case BIRDS -> opts.add(new Opt("birds", "See today's birds"));
            case BARBER -> opts.add(new Opt("barber", "A shave and a trim"));
            case TATTOO -> opts.add(new Opt("tattoo", "Get a tattoo"));
            case CATS -> opts.add(new Opt("cats", "Adopt a ship's cat"));
            case FORTUNE -> opts.add(new Opt("fortune", "Hear your fortune (3 doubloons)"));
            case PARDON -> opts.add(new Opt("pardon", "Ask about a pardon"));
            case HEAL -> opts.add(new Opt("heal", "Patch me up"));
            case GALLERY -> { if (p.getMainHandStack().isOf(net.get900.pixelpirates.homestead.HomesteadItems.PAINTING)) opts.add(new Opt("gallery", "Offer this painting to the gallery")); }
            default -> { }
        }
        if (TownLife.canChallenge(f)) opts.add(new Opt("challenge", "Challenge to a game of chess"));
        if (f.id().equals("anselm") && partner(p) != null) opts.add(new Opt("marry", "Marry us! (with " + partner(p).getName().getString() + ")"));
        if (f.id().equals("rufus")) opts.add(new Opt("tale", "Ask for a tale"));
        if (f.id().equals("anselm") && HarvestFestival.collecting(p.getServerWorld())) opts.add(new Opt("harvest", "Give to the harvest"));
        if (f.id().equals("elias") && LimpingShip.needsBandages()) opts.add(new Opt("wounded", "Help with the wounded"));
        opts.add(new Opt("bye", "Goodbye"));
        send(p, e, f, say, opts);
    }

    private static void send(ServerPlayerEntity p, TownsfolkEntity e, Townsfolk.Folk f, String say, List<Opt> opts) {
        e.keepTalking(p);
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(e.getId());
        buf.writeString(f.name());
        buf.writeString(f.title());
        buf.writeString(say);
        int sc = TownMemory.score(p, f.id());
        buf.writeString(TownMemory.LEVELS[TownMemory.level(sc)] + " (" + sc + ")");
        buf.writeVarInt(opts.size());
        for (Opt o : opts) { buf.writeString(o.key); buf.writeString(o.label); }
        ServerPlayNetworking.send(p, OPEN, buf);
    }

    private static String greeting(ServerPlayerEntity p, TownsfolkEntity e, Townsfolk.Folk f) {
        long t = Math.floorMod(e.getWorld().getTimeOfDay(), 24000L);
        String hi = t < 5000 ? "Morning!" : t < 11500 ? "Afternoon." : "Evening.";
        String doing = switch (e.act()) {
            case DRINK -> " Pull up a stool, the ale's not bad tonight.";
            case GAMBLE -> " Shh - I'm on a winning streak. Probably.";
            case CHESS_SIT, CHESS_STAND -> " Quiet please, I'm thinking. Three moves ahead. Or two.";
            case PAINT -> " Mind the wet paint!";
            case PRAY -> " Peace be with you.";
            default -> "";
        };
        ServerWorld w = p.getServerWorld();
        String me = p.getName().getString();
        int lvl = TownMemory.level(p, f.id());
        if (lvl >= 2) hi = (t < 5000 ? "Morning, " : t < 11500 ? "Afternoon, " : "Evening, ") + me + "!";
        StringBuilder extra = new StringBuilder();
        List<TownMemory.News> recent = TownMemory.recent(w, 2);
        TownMemory.News congrats = recent.stream().filter(n -> n.kind().equals("boss") && n.about().equals(me)).findFirst().orElse(null);
        if (congrats != null) extra.append(" Congratulations, captain - the whole town's talking about it! ");
        else if (!recent.isEmpty() && w.random.nextInt(5) < 2) {
            TownMemory.News n = recent.get(w.random.nextInt(recent.size()));
            if (!n.about().equals(f.id())) extra.append(" Did you hear? ").append(n.text()).append(" ");
            else if (n.kind().equals("wedding")) extra.append(" I'm a married ").append(f.name().split(" ")[0].endsWith("a") ? "woman" : "soul").append(" now, you know! ");
        }
        int[] dice = TownMemory.diceRecord(p, f.id());
        if (dice != null && w.random.nextInt(3) == 0) extra.append(" (Liar's Dice, you and me: ").append(dice[0]).append(" to you, ").append(dice[1]).append(" to me.) ");
        return hi + doing + extra + line(e, f);
    }

    /** Another player standing at the chapel altar with this one (for a wedding). */
    @Nullable
    private static ServerPlayerEntity partner(ServerPlayerEntity p) {
        net.minecraft.util.math.Vec3d altar = TownEvents.ALTAR_FRONT;
        if (p.squaredDistanceTo(altar) > 10 * 10) return null;
        for (ServerPlayerEntity o : p.getServerWorld().getPlayers()) if (o != p && o.squaredDistanceTo(altar) < 10 * 10) return o;
        return null;
    }

    private static String line(TownsfolkEntity e, Townsfolk.Folk f) {
        if (f.id().equals("finn") && e.getWorld() instanceof ServerWorld w && FishingContest.on(w))
            return "Contest's on till sundown! Biggest so far: " + FishingContest.standings();
        String[] l = f.lines();
        return l[Math.floorMod(e.line++, l.length)];
    }

    private static void act(ServerPlayerEntity p, TownsfolkEntity e, String key) {
        Townsfolk.Folk f = e.folk();
        if (f == null) return;
        ServerWorld w = p.getServerWorld();
        switch (key) {
            case "chat" -> { e.triggerAnim("action", "talk"); open(p, e, line(e, f)); }
            case "gift" -> gift(p, e, f);
            case "wounded" -> { e.triggerAnim("action", "talk"); open(p, e, LimpingShip.help(p)); }
            case "harvest" -> { e.triggerAnim("action", "talk"); open(p, e, HarvestFestival.donate(p)); }
            case "challenge" -> { e.triggerAnim("action", "talk"); open(p, e, TownLife.challenge(p, e)); }
            case "heal" -> heal(p, e, f);
            case "gallery" -> gallery(p, e, f);
            case "marry" -> {
                ServerPlayerEntity o = partner(p);
                if (o == null) { open(p, e, "A wedding needs two, my child. Stand at the altar together."); return; }
                TownEvents.playerWedding(w, p.getName().getString(), o.getName().getString());
                o.sendMessage(net.minecraft.text.Text.literal("[Father Anselm] " + p.getName().getString() + " has asked me to marry the two of you. Stand before the altar!").formatted(net.minecraft.util.Formatting.GOLD), false);
                open(p, e, "A wedding! Splendid! Stand before the altar, the pair of you - I'll fetch Mistress Bellweather for the organ.");
            }
            case "trade" -> e.openShop(p);
            case "bye" -> { e.triggerAnim("action", "wave"); e.keepTalking(p); }
            case "tale" -> {
                int step = Math.max(0, Math.min(Townsfolk.RUFUS_TALES.length - 1, BossProgression.progress(p)));
                e.triggerAnim("action", "talk");
                open(p, e, Townsfolk.RUFUS_TALES[step]);
            }
            case "barber" -> { BlockPos c = near(w, e.getBlockPos(), BarberChairBlock.class); if (c != null) Beards.open(p, c); else open(p, e, "My chair's back at the shop, friend. Come and find me there."); }
            case "tattoo" -> { BlockPos c = near(w, e.getBlockPos(), TattooChairBlock.class); if (c != null) Tattoos.open(p, c); else open(p, e, "Not here. The chair's in my parlour - come by when I'm working."); }
            case "cats" -> { BlockPos c = near(w, e.getBlockPos(), CatteryCounterBlock.class); if (c != null) Cattery.openCounter(p, c); else open(p, e, "The cats are at the cattery, dear. Come by in the day."); }
            case "birds" -> birds(p, e, f);
            case "fortune" -> {
                if (!TownsfolkEntity.pay(p, 3)) { open(p, e, "The spirits do not work for free, darling. Three doubloons."); return; }
                String fortune;
                BossProgression.Step next = BossProgression.next(p);
                if (next != null && w.random.nextInt(3) == 0) fortune = "The cards show " + next.name() + ". It waits for you, and it is not patient.";
                else fortune = Townsfolk.FORTUNES[w.random.nextInt(Townsfolk.FORTUNES.length)];
                w.playSound(null, e.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.NEUTRAL, 1f, 0.8f);
                e.triggerAnim("action", "flourish");
                open(p, e, fortune);
            }
            case "pardon" -> {
                int rep = FactionManager.getReputation(p, Faction.NAVY);
                if (rep >= 0) { open(p, e, "Your record with the Armada is clean, captain. Keep it that way and my guns will keep their opinions to themselves."); return; }
                int cost = pardonCost(rep);
                List<Opt> o = List.of(new Opt("pardon_pay", "Pay " + cost + " doubloons"), new Opt("bye", "Not today"));
                send(p, e, f, "You've crossed the Armada (standing " + rep + "). For " + cost + " doubloons the ledger forgets - and so do the fort's cannons.", o);
            }
            case "pardon_pay" -> {
                int rep = FactionManager.getReputation(p, Faction.NAVY);
                if (rep >= 0) { open(p, e, "There is nothing to pardon."); return; }
                if (!TownsfolkEntity.pay(p, pardonCost(rep))) { open(p, e, "Come back when your purse matches your crimes."); return; }
                FactionManager.modifyReputation(p, Faction.NAVY, -rep);
                w.playSound(null, e.getBlockPos(), SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundCategory.NEUTRAL, 1f, 1f);
                open(p, e, "Signed, sealed and stamped. Welcome back to the right side of the law, captain. Try to stay on it.");
            }
            default -> {
                if (key.startsWith("buy:")) buy(p, e, f, key.substring(4));
            }
        }
    }

    // ------------------------------------------------------------------ round 3: gifts, the surgeon, the gallery
    private static final java.util.Set<net.minecraft.item.Item> NASTY = java.util.Set.of(net.minecraft.item.Items.ROTTEN_FLESH,
            net.minecraft.item.Items.POISONOUS_POTATO, net.minecraft.item.Items.SPIDER_EYE, net.minecraft.item.Items.DIRT, net.minecraft.item.Items.BONE);

    private static void gift(ServerPlayerEntity p, TownsfolkEntity e, Townsfolk.Folk f) {
        net.minecraft.item.ItemStack held = p.getMainHandStack();
        if (held.isEmpty()) { open(p, e, "Your hands are empty, friend!"); return; }
        long d = TownMemory.day(p.getServerWorld());
        if (TownMemory.giftedToday(p, f.id(), d)) { open(p, e, "You've already brought me something today - you're too kind!"); return; }
        java.util.Set<net.minecraft.item.Item> likes = Townsfolk.LIKES.getOrDefault(f.id(), java.util.Set.of());
        String what = held.getName().getString();
        boolean loved = likes.contains(held.getItem()), nasty = NASTY.contains(held.getItem()) && !loved;
        if (!p.isCreative()) held.decrement(1);
        TownMemory.gave(p, f.id(), d);
        if (loved) { TownMemory.befriend(p, f, 8); e.triggerAnim("action", "cheer"); open(p, e, "A " + what + "! For me? Oh, that's exactly the thing! Thank you!"); }
        else if (nasty) { TownMemory.befriend(p, f, -3); open(p, e, "...a " + what + ". How... thoughtful."); }
        else { TownMemory.befriend(p, f, 3); e.triggerAnim("action", "wave"); open(p, e, "A " + what + "? Why, thank you, captain."); }
    }

    /** The surgeon: full health and the nasty effects gone, for 3 doubloons + one per missing heart-half. */
    private static void heal(ServerPlayerEntity p, TownsfolkEntity e, Townsfolk.Folk f) {
        int missing = (int) Math.ceil(p.getMaxHealth() - p.getHealth());
        boolean bad = p.getStatusEffects().stream().anyMatch(s -> !s.getEffectType().isBeneficial());
        if (missing <= 0 && !bad) { open(p, e, "You're fit as a fiddle, captain. Come back when something's fallen off."); return; }
        int cost = 3 + missing;
        if (!TownsfolkEntity.pay(p, cost)) { open(p, e, "That'll be " + cost + " doubloons. I don't do credit - too many patients don't come back."); return; }
        p.setHealth(p.getMaxHealth());
        for (var s : new ArrayList<>(p.getStatusEffects())) if (!s.getEffectType().isBeneficial()) p.removeStatusEffect(s.getEffectType());
        p.getServerWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1.6f);
        e.triggerAnim("action", "flourish");
        open(p, e, "There. Stitched, splinted and sorted. " + cost + " doubloons, and try not to get stabbed for at least a day.");
    }

    /** Isadora buys a player's own painting for the gallery and hangs it in her studio (price by how much is painted). */
    private static void gallery(ServerPlayerEntity p, TownsfolkEntity e, Townsfolk.Folk f) {
        net.minecraft.item.ItemStack held = p.getMainHandStack();
        if (!held.isOf(net.get900.pixelpirates.homestead.HomesteadItems.PAINTING)) { open(p, e, "Bring me a painting, darling - in your hand, so I can see it."); return; }
        net.minecraft.nbt.NbtCompound art = held.getOrCreateSubNbt("Art").copy();
        if (!art.getString("Author").equals(p.getName().getString())) { open(p, e, "I only buy from the artist, dear. Paint me one of your own!"); return; }
        ServerWorld w = p.getServerWorld();
        TownMemory m = TownMemory.get(w.getServer());
        String k = p.getUuid().toString();
        long d = TownMemory.day(w);
        if (m.lastSale.getOrDefault(k, -1L) == d) { open(p, e, "One a day, darling - the gallery walls aren't endless."); return; }
        int[] px = art.getIntArray("Pixels");
        java.util.Set<Integer> cols = new java.util.HashSet<>();
        int painted = 0;
        for (int c : px) { cols.add(c & 0xFFFFFF); if ((c & 0xFFFFFF) != (net.get900.pixelpirates.homestead.art.Art.WHITE & 0xFFFFFF)) painted++; }
        double cover = px.length == 0 ? 0 : painted / (double) px.length;
        if (cover < 0.25 || cols.size() < 3) { open(p, e, "Hmm. It's... a start. Fill the canvas, use more colours, and come back."); return; }
        int price = (int) Math.max(5, Math.min(40, 5 + cols.size() / 2 + cover * 20));
        if (!TownLife.hangInGallery(w, art)) { open(p, e, "The walls are full just now - I'll make room tomorrow."); return; }
        if (!p.isCreative()) held.decrement(1);
        p.getInventory().offerOrDrop(new net.minecraft.item.ItemStack(net.get900.pixelpirates.item.ModItems.COIN, price));
        m.lastSale.put(k, d);
        m.markDirty();
        TownMemory.befriend(p, f, 5);
        TownMemory.news(w, "gallery", "Isadora Vane has hung Captain " + p.getName().getString() + "'s \"" + art.getString("Title") + "\" in her gallery!", p.getName().getString());
        e.triggerAnim("action", "cheer");
        open(p, e, "Oh, I like this. " + price + " doubloons, and it goes on my gallery wall with your name on it. Come and see!");
    }

    private static int pardonCost(int rep) { return Math.max(10, -rep / 3); }

    @Nullable
    private static BlockPos near(ServerWorld w, BlockPos c, Class<? extends Block> type) {
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -10; dx <= 10; dx++)
            for (int dz = -10; dz <= 10; dz++)
                for (int dy = -3; dy <= 3; dy++)
                    if (type.isInstance(w.getBlockState(m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz)).getBlock())) return m.toImmutable();
        return null;
    }

    // ------------------------------------------------------------------ Polly's birds (the aviary's daily stock)
    static int price(ParrotTypes.Tier t) {
        return switch (t) { case COMMON -> 15; case UNCOMMON -> 30; case RARE -> 60; case VERY_RARE -> 120; case LEGENDARY -> 250; };
    }

    private static void birds(ServerPlayerEntity p, TownsfolkEntity e, Townsfolk.Folk f) {
        List<ParrotEntity> stock = p.getServerWorld().getEntitiesByClass(ParrotEntity.class, Aviary.cage(), b -> b.isAlive() && !b.isTamed());
        if (stock.isEmpty()) { open(p, e, "Sold out today, love! New birds come in every morning."); return; }
        List<Opt> o = new ArrayList<>();
        for (ParrotEntity b : stock) {
            ParrotTypes.PType t = ParrotTypes.of(b);
            o.add(new Opt("buy:" + b.getUuid(), t.name() + " (" + t.tier().label + ") - " + price(t.tier()) + " doubloons"));
        }
        o.add(new Opt("bye", "Just looking"));
        send(p, e, f, "Here's today's lot - each one hand-fed by yours truly. Rarer birds cost more, naturally.", o);
    }

    private static void buy(ServerPlayerEntity p, TownsfolkEntity e, Townsfolk.Folk f, String uuid) {
        ServerWorld w = p.getServerWorld();
        UUID u;
        try { u = UUID.fromString(uuid); } catch (IllegalArgumentException ex) { return; }
        if (!(w.getEntity(u) instanceof ParrotEntity b) || b.isTamed() || !Aviary.cage().contains(b.getPos())) { open(p, e, "Ah - that one's gone already."); return; }
        ParrotTypes.PType t = ParrotTypes.of(b);
        if (!TownsfolkEntity.pay(p, price(t.tier()))) { open(p, e, "That's " + price(t.tier()) + " doubloons for the " + t.name() + ", love. No credit - birds fly off."); return; }
        b.setOwner(p);
        b.setPersistent();
        b.refreshPositionAndAngles(p.getX(), p.getY() + 1, p.getZ(), p.getYaw(), 0);
        ParrotCollection.unlock(p, t);
        b.mountOnto(p);
        w.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PARROT_AMBIENT, SoundCategory.NEUTRAL, 1f, 1.2f);
        e.triggerAnim("action", "cheer");
        open(p, e, "A fine choice! Your " + t.name() + " will look after you. Feed it now and then, and don't let it near the rum.");
    }
}
