package net.get900.pixelpirates.homestead.parrot;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.get900.pixelpirates.homestead.HomesteadState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * PARROT TYPES phase 4 (2026-10-03, docs/parrot_ideas.md): your parrot COLLECTION.
 *  - A type is UNLOCKED for good the first time a parrot of it becomes yours (tamed, out of a crate, recalled) -
 *    TameableOwnerMixin -> unlock. Kept per player in HomesteadState discoveries as "parrot:<id>".
 *  - ONE OF EACH: you can't tame a second parrot of a type you own (seeds refused), a crate of an owned type stays shut,
 *    and treasure crates never roll a type you own (TreasureLoot.roll(..., owned)).
 *  - THE PARROT ROOST (block) opens the collection screen (ROOST_OPEN, client ParrotRoostScreen): unlocked types to pick
 *    from, the rest as silhouettes. Picking one (ROOST_RECALL) sends any other parrot of that type of yours home and
 *    brings a fresh one to your shoulder - so a lost parrot is never lost for good.
 */
public final class ParrotCollection {
    private ParrotCollection() {}

    public static final Identifier ROOST_OPEN = new Identifier("pixelpirates", "parrot_roost_open");
    public static final Identifier ROOST_RECALL = new Identifier("pixelpirates", "parrot_roost_recall");
    private static final String KEY = "parrot:";

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ROOST_RECALL, (server, player, handler, buf, sender) -> {
            String id = buf.readString(64);
            server.execute(() -> recall(player, id));
        });
        // one of each: no taming a second parrot of a type you already own
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient || !(entity instanceof ParrotEntity p) || p.isTamed() || !tamingFood(player.getStackInHand(hand))) return ActionResult.PASS;
            ParrotTypes.PType t = ParrotTypes.of(p);
            if (!owns(world.getServer(), player.getUuid(), t.id())) return ActionResult.PASS;
            player.sendMessage(Text.literal("You already have a " + t.name() + " - call it from your Parrot Roost.").formatted(Formatting.YELLOW), true);
            return ActionResult.FAIL;
        });
    }

    private static boolean tamingFood(ItemStack s) {
        return s.isOf(Items.WHEAT_SEEDS) || s.isOf(Items.MELON_SEEDS) || s.isOf(Items.PUMPKIN_SEEDS) || s.isOf(Items.BEETROOT_SEEDS)
                || s.isOf(Items.TORCHFLOWER_SEEDS) || s.isOf(Items.PITCHER_POD);
    }

    public static Set<String> owned(MinecraftServer server, UUID u) {
        Set<String> out = new LinkedHashSet<>();
        for (String k : HomesteadState.get(server).discoveries(u)) if (k.startsWith(KEY)) out.add(k.substring(KEY.length()));
        return out;
    }

    public static boolean owns(MinecraftServer server, UUID u, String id) { return HomesteadState.get(server).discoveries(u).contains(KEY + id); }

    /** A parrot just became this player's: unlock its type (announced the first time). */
    public static void unlock(ServerPlayerEntity p, ParrotTypes.PType t) {
        if (!HomesteadState.get(p.getServer()).discover(p.getUuid(), KEY + t.id())) return;
        p.sendMessage(Text.literal("[~] New parrot for your Roost: ").formatted(Formatting.GOLD)
                .append(Text.literal(t.name()).formatted(t.tier().colour, Formatting.BOLD))
                .append(Text.literal(" (" + t.tier().label.toLowerCase() + ")").formatted(Formatting.GOLD)), false);
    }

    /** The Roost was used: send the player their collection. */
    public static void open(ServerPlayerEntity p) {
        PacketByteBuf buf = PacketByteBufs.create();
        Set<String> own = owned(p.getServer(), p.getUuid());
        buf.writeVarInt(own.size());
        for (String s : own) buf.writeString(s);
        ServerPlayNetworking.send(p, ROOST_OPEN, buf);
    }

    /** Call a type you own: the old one goes home, a fresh one comes to your shoulder (or your side if both are taken). */
    public static void recall(ServerPlayerEntity p, String id) {
        ParrotTypes.PType t = ParrotTypes.byId(id);
        if (t == null || !owns(p.getServer(), p.getUuid(), id)) return;
        NbtCompound away = ParrotAbilities.away(p.getServer(), p.getUuid(), id);      // the Kraken's Pet / Ghost Parrot is off somewhere
        if (away != null) {
            long mins = Math.max(1, (away.getLong("Back") - p.getServer().getOverworld().getTime()) / 1200);
            p.sendMessage(Text.literal(away.containsUuid("Possessing") ? "Your " + t.name() + " is busy possessing a monster."
                    : "Your " + t.name() + " is still away - about " + mins + " more minute(s).").formatted(Formatting.DARK_AQUA), false);
            return;
        }
        sendHome(p, t);
        ServerWorld w = p.getServerWorld();
        ParrotEntity bird = EntityType.PARROT.create(w);
        if (bird == null) return;
        bird.refreshPositionAndAngles(p.getX(), p.getY() + 1, p.getZ(), p.getYaw(), 0f);
        ParrotTypes.apply(bird, t);
        bird.setOwner(p);
        bird.setPersistent();
        w.spawnEntity(bird);
        bird.mountOnto(p);                                       // false (both shoulders taken): it just stays by you
        w.playSound(null, p.getX(), p.getY() + 1.6, p.getZ(), SoundEvents.ENTITY_PARROT_AMBIENT, SoundCategory.NEUTRAL, 1f, 1.2f);
        p.sendMessage(Text.literal("Your " + t.name() + " flies to you.").formatted(t.tier().colour), true);
    }

    /** Only one of each type: the player's other parrots of this type (loaded, or on a shoulder) go back to the roost. */
    private static void sendHome(ServerPlayerEntity p, ParrotTypes.PType t) {
        java.util.List<ParrotEntity> old = new java.util.ArrayList<>();
        for (ServerWorld w : p.getServer().getWorlds())
            for (Entity e : w.iterateEntities())
                if (e instanceof ParrotEntity b && b.isAlive() && p.getUuid().equals(b.getOwnerUuid()) && ParrotTypes.of(b) == t) old.add(b);
        old.forEach(Entity::discard);
        var inv = (net.get900.pixelpirates.mixin.PlayerShoulderInvoker) p;
        if (t.id().equals(type(p.getShoulderEntityLeft()))) inv.pixelpirates$setShoulderLeft(new NbtCompound());
        if (t.id().equals(type(p.getShoulderEntityRight()))) inv.pixelpirates$setShoulderRight(new NbtCompound());
    }

    /** The type id of a shoulder parrot's data (vanilla colours by variant), or null. */
    static String type(NbtCompound n) {
        if (n == null || !"minecraft:parrot".equals(n.getString("id"))) return null;
        String c = n.getString("PPType");
        if (!c.isEmpty()) return c;
        int v = n.getInt("Variant");
        for (ParrotTypes.PType t : ParrotTypes.ALL) if (!t.custom() && t.base().getId() == v) return t.id();
        return null;
    }
}
