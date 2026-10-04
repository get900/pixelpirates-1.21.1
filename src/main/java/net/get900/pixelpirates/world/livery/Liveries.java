package net.get900.pixelpirates.world.livery;

import net.get900.pixelpirates.entity.ModEntities;
import net.get900.pixelpirates.entity.mob.BossProgression;
import net.get900.pixelpirates.homestead.HomesteadState;
import net.get900.pixelpirates.homestead.parrot.ParrotCollection;
import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.world.AiShipController;
import net.get900.pixelpirates.world.ShipRegistryState;
import net.get900.pixelpirates.world.ShipSchematic;
import net.get900.pixelpirates.world.faction.Faction;
import net.get900.pixelpirates.world.faction.FactionManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Property;
import net.minecraft.stat.Stats;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.*;

/**
 * Applying liveries ({@link Livery}) to a player's ship, unlocks, and the Shipwright's Livery tab data.
 * <p>
 * The FIRST time a ship is repainted, every block of it is classified into a {@link Role} and its ORIGINAL state is
 * remembered ({@link LiveryState}); a repaint then sets each remembered position to (livery block for its role, with the
 * original's shape copied), and "Original" puts the remembered states back. A position whose block is no longer what
 * the last paint left there (shot away, replaced by the player) is skipped. Functional blocks (helm, masts, cannons,
 * anchor, chests, figureheads...) are never touched. Wood swaps are wood-for-wood, so the ship's weight barely moves.
 */
public final class Liveries {
    private Liveries() {}

    public static final Identifier APPLY = new Identifier("pixelpirates", "shipwright_livery");
    /** Fee for switching to a livery you already own (or one you earned), in doubloons. */
    public static final int SWITCH_FEE = 5;

    public enum Role { HULL, DECK, HULL_STAIRS, DECK_SLAB, RAIL, TRIM, BAND, BAND2, GILT, SAIL, SAIL2, EMBLEM, LIGHT }

    private static final Set<String> BAND_BLOCKS = Set.of("red_nether_bricks", "nether_bricks", "blackstone", "polished_blackstone",
            "polished_blackstone_bricks", "prismarine", "prismarine_bricks", "dark_prismarine", "mud_bricks", "mossy_cobblestone", "magma_block");

    /** The role a block plays on a ship, or null = leave it alone (functional or unknown). PLANKS/BAND/SAIL are split later. */
    @Nullable
    static Role classify(BlockState s) {
        Identifier id = Registries.BLOCK.getId(s.getBlock());
        String n = id.getPath();
        if (n.contains("jolly_roger")) return Role.EMBLEM;
        if (n.endsWith("_planks")) return Role.HULL;
        if (s.isIn(BlockTags.WOODEN_STAIRS)) return Role.HULL_STAIRS;
        if (s.isIn(BlockTags.WOODEN_SLABS)) return Role.DECK_SLAB;
        if (s.isIn(BlockTags.WOODEN_FENCES)) return Role.RAIL;
        if (s.isIn(BlockTags.LOGS) || n.equals("bamboo_block") || n.equals("stripped_bamboo_block") || n.equals("bone_block") || n.equals("ghostwood_log")) return Role.TRIM;
        if (n.endsWith("_terracotta") || n.endsWith("_concrete") || BAND_BLOCKS.contains(n)) return Role.BAND;
        if (n.equals("gold_block") || n.equals("shroomlight") || n.equals("sea_lantern")) return Role.GILT;
        if (n.endsWith("_wool") || n.contains("sail_canvas") || n.equals("spectral_sail")) return Role.SAIL;
        if (n.equals("lantern") || n.equals("soul_lantern")) return Role.LIGHT;
        return null;
    }

    /** The livery's block for a role (null = this livery leaves the role as it was). */
    @Nullable
    private static String target(Livery l, Role r) {
        return switch (r) {
            case HULL -> l.hull().planks(); case DECK -> l.deck().planks(); case HULL_STAIRS -> l.hull().stairs();
            case DECK_SLAB -> l.deck().slab(); case RAIL -> l.hull().fence(); case TRIM -> l.trim(); case BAND -> l.band();
            case BAND2 -> l.band2(); case GILT -> l.gilt(); case SAIL -> l.sail(); case SAIL2 -> l.sail2(); case EMBLEM -> l.emblem();
            case LIGHT -> l.light();
        };
    }

    /** {@code to}'s default state with every property it shares with {@code from} copied over (facing, half, axis, fence sides...). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static BlockState reshape(Block to, BlockState from) {
        BlockState out = to.getDefaultState();
        for (Property p : out.getProperties()) {
            for (Property q : from.getProperties()) {
                if (!q.getName().equals(p.getName())) continue;
                Optional v = p.parse(q.name(from.get(q)));
                if (v.isPresent()) out = out.with(p, (Comparable) v.get());
            }
        }
        return out;
    }

    @Nullable
    private static BlockState paintOf(@Nullable Livery l, Role r, BlockState original) {
        if (l == null) return original;
        String t = target(l, r);
        if (t == null) return original;
        Block b = Registries.BLOCK.get(new Identifier(t));
        if (b == net.minecraft.block.Blocks.AIR) return original;                       // unknown id
        return reshape(b, original);
    }

    // ------------------------------------------------------------------ unlocks
    public static boolean owns(ServerPlayerEntity p, Livery l) {
        return HomesteadState.get(p.getServer()).discoveries(p.getUuid()).contains("livery:" + l.id());
    }

    /** Can this player have it at all (standing / deed met)? */
    public static boolean eligible(ServerPlayerEntity p, Livery l) {
        if (l.kind() == Livery.Kind.BUY) return true;
        if (l.kind() == Livery.Kind.FACTION) return FactionManager.getReputation(p, Faction.fromId(l.unlock())) >= Faction.REP_FRIENDLY;
        String[] u = l.unlock().split(":");
        return switch (u[0]) {
            case "boss" -> { int i = BossProgression.indexOf(u[1]); yield i >= 0 && BossProgression.progress(p) > i; }
            case "sharks" -> p.getStatHandler().getStat(Stats.KILLED.getOrCreateStat(ModEntities.SHARK)) >= Integer.parseInt(u[1]);
            case "parrots" -> ParrotCollection.owned(p.getServer(), p.getUuid()).size() >= Integer.parseInt(u[1]);
            default -> false;
        };
    }

    /** 0 = locked, 1 = can buy, 2 = yours (owned or earned). */
    public static int status(ServerPlayerEntity p, Livery l) {
        if (!eligible(p, l)) return 0;
        if (l.kind() == Livery.Kind.SPECIAL || owns(p, l)) return 2;
        return 1;
    }

    /** The Livery tab's data, appended to the Shipwright menu packet: the ship's current livery, then each one's status. */
    public static void writeMenu(PacketByteBuf buf, ServerPlayerEntity p, long shipId) {
        String cur = shipId == -1L ? "" : LiveryState.get(p.getServer()).current(shipId);
        buf.writeVarInt(Livery.indexOf(cur));
        for (Livery l : Livery.ALL) buf.writeByte(status(p, l));
    }

    // ------------------------------------------------------------------ applying
    /** The Shipwright's Livery button: buy / switch / restore, then repaint. {@code id} "original" = put the ship back. */
    public static void handle(ServerPlayerEntity p, long shipId, String id) {
        ServerWorld world = p.getServerWorld();
        ShipRegistryState reg = ShipRegistryState.get(world.getServer().getOverworld());
        if (AiShipController.AI_SHIPS.containsKey(shipId) || !p.getUuid().equals(reg.ownerOf(shipId))) {
            p.sendMessage(Text.literal("That isn't your ship.").formatted(Formatting.RED), true); return;
        }
        ServerShip ship = VSGameUtilsKt.getShipObjectWorld(world).getLoadedShips().getById(shipId);
        if (ship == null || ship.getShipAABB() == null) { p.sendMessage(Text.literal("Your ship must be close by.").formatted(Formatting.RED), true); return; }
        Livery l = id.equals("original") ? null : Livery.byId(id);
        if (l == null && !id.equals("original")) return;
        LiveryState st = LiveryState.get(p.getServer());
        if (Objects.equals(st.current(shipId), l == null ? "" : l.id())) { p.sendMessage(Text.literal("She already wears that.").formatted(Formatting.GRAY), true); return; }
        int price = 0;
        if (l != null) {
            int s = status(p, l);
            if (s == 0) { p.sendMessage(Text.literal("Locked - " + l.unlockText() + ".").formatted(Formatting.RED), true); return; }
            price = s == 1 ? l.price() : SWITCH_FEE;
        }
        if (!p.isCreative() && price > 0) {
            price = net.get900.pixelpirates.world.SkillEffects.haggle(p, price);
            if (p.getInventory().count(ModItems.COIN) < price) { p.sendMessage(Text.literal("You need " + price + " doubloons.").formatted(Formatting.RED), true); return; }
            int left = price;
            for (int i = 0; i < p.getInventory().size() && left > 0; i++) {
                var stack = p.getInventory().getStack(i);
                if (!stack.isOf(ModItems.COIN)) continue;
                int take = Math.min(stack.getCount(), left); stack.decrement(take); left -= take;
            }
        }
        if (l != null && l.kind() != Livery.Kind.SPECIAL) HomesteadState.get(p.getServer()).discover(p.getUuid(), "livery:" + l.id());
        int n = repaint(world, ship, st, l);
        p.sendMessage(Text.literal("[~] " + (l == null ? "Your ship is back in her own colours" : "Your ship now wears " + l.name())
                + " (" + n + " blocks repainted).").formatted(Formatting.GOLD), false);
        world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_VILLAGER_WORK_LEATHERWORKER, SoundCategory.PLAYERS, 1f, 1f);
    }

    /** Repaint a ship (l == null = the original colours). Returns how many blocks changed. */
    public static int repaint(ServerWorld world, ServerShip ship, LiveryState st, @Nullable Livery l) {
        LiveryState.Paint paint = st.paint(ship.getId());
        if (paint == null) paint = st.record(ship.getId(), survey(world, ship));
        Livery prev = Livery.byId(paint.current);
        int changed = 0;
        List<BlockState> pal = new ArrayList<>();
        for (var nbt : paint.palette) pal.add(ShipSchematic.restoreState(nbt));
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int i = 0; i < paint.pos.length; i++) {
            m.set(paint.pos[i]);
            BlockState original = pal.get(paint.orig[i]);
            if (original == null) continue;
            Role r = Role.values()[paint.role[i]];
            BlockState expected = paintOf(prev, r, original), now = world.getBlockState(m);
            if (expected == null || now.getBlock() != expected.getBlock()) continue;          // shot away / rebuilt by the player
            BlockState next = paintOf(l, r, original);
            if (next == null || next == now) continue;
            world.setBlockState(m, next, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
            changed++;
        }
        paint.current = l == null ? "" : l.id();
        st.markDirty();
        return changed;
    }

    /** First repaint: classify every block of the ship and decide the split roles (main hull wood vs deck wood,
     *  main band vs second band, main sail vs second sail - the most common one of each is the main). */
    static List<Object[]> survey(ServerWorld world, ServerShip ship) {
        var box = ship.getShipAABB();
        List<Object[]> out = new ArrayList<>();                                    // {long pos, Role, BlockState}
        Map<Block, Integer> planks = new HashMap<>(), bands = new HashMap<>(), sails = new HashMap<>();
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int x = box.minX(); x <= box.maxX(); x++)
            for (int y = box.minY(); y <= box.maxY(); y++)
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockState s = world.getBlockState(m.set(x, y, z));
                    if (s.isAir()) continue;
                    Role r = classify(s);
                    if (r == null) continue;
                    if (r == Role.HULL) planks.merge(s.getBlock(), 1, Integer::sum);
                    if (r == Role.BAND) bands.merge(s.getBlock(), 1, Integer::sum);
                    if (r == Role.SAIL) sails.merge(s.getBlock(), 1, Integer::sum);
                    out.add(new Object[]{m.asLong(), r, s});
                }
        Block mainPlank = most(planks), mainBand = most(bands), mainSail = most(sails);
        for (Object[] o : out) {
            BlockState s = (BlockState) o[2];
            if (o[1] == Role.HULL && s.getBlock() != mainPlank) o[1] = Role.DECK;
            if (o[1] == Role.BAND && s.getBlock() != mainBand) o[1] = Role.BAND2;
            if (o[1] == Role.SAIL && s.getBlock() != mainSail) o[1] = Role.SAIL2;
        }
        return out;
    }

    @Nullable
    private static Block most(Map<Block, Integer> m) {
        return m.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    static net.minecraft.nbt.NbtCompound stateNbt(BlockState s) { return NbtHelper.fromBlockState(s); }
}
