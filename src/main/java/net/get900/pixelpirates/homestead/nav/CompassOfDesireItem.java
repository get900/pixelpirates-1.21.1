package net.get900.pixelpirates.homestead.nav;

import net.get900.pixelpirates.homestead.HomesteadState;
import net.get900.pixelpirates.homestead.Navigation;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * THE COMPASS OF DESIRE (#18): points at what you want most. Sneak-use changes what that is; use re-reads it.
 *   HOME     - your hideout flag, else your bed/hammock, else world spawn;
 *   DEATH    - where you last died (vanilla keeps it);
 *   TREASURE - the nearest dungeon you have not yet found (reaching one within 24 blocks marks it found - the
 *              logbook shares the list; boss lairs are excluded);
 *   QUARRY   - the lair of the next boss on your chain.
 * The target lives in the stack's NBT ({Mode, TX, TY, TZ, TDim}); the client's needle reads it
 * (CompassAnglePredicateProvider in HomesteadClient) and spins when it points to another dimension or nowhere.
 */
public class CompassOfDesireItem extends Item {
    public enum Mode { HOME, DEATH, TREASURE, QUARRY }

    public static final List<String> TREASURES = List.of("smugglers_grotto", "tidewater_shrine", "sunken_galleon", "coral_temple",
            "obsidian_vault", "fire_camp", "drowned_graveyard", "luminous_grotto");

    public CompassOfDesireItem(Settings s) { super(s); }

    public static Mode mode(ItemStack s) {
        NbtCompound n = s.getNbt();
        return n == null ? Mode.HOME : Mode.values()[Math.floorMod(n.getInt("Mode"), Mode.values().length)];
    }

    @Nullable
    public static GlobalPos target(ItemStack s) {
        NbtCompound n = s.getNbt();
        if (n == null || !n.contains("TDim")) return null;
        return GlobalPos.create(RegistryKey.of(RegistryKeys.WORLD, new Identifier(n.getString("TDim"))), new BlockPos(n.getInt("TX"), n.getInt("TY"), n.getInt("TZ")));
    }

    static void setTarget(ItemStack s, @Nullable GlobalPos g) {
        NbtCompound n = s.getOrCreateNbt();
        if (g == null) {
            n.remove("TDim");
            return;
        }
        n.putString("TDim", g.getDimension().getValue().toString());
        n.putInt("TX", g.getPos().getX());
        n.putInt("TY", g.getPos().getY());
        n.putInt("TZ", g.getPos().getZ());
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack s = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(s);
        ServerPlayerEntity p = (ServerPlayerEntity) user;
        if (user.isSneaking()) s.getOrCreateNbt().putInt("Mode", (mode(s).ordinal() + 1) % Mode.values().length);
        GlobalPos g = find(p, mode(s), s);
        setTarget(s, g);
        String what = switch (mode(s)) { case HOME -> "home"; case DEATH -> "your last death"; case TREASURE -> "undiscovered treasure"; case QUARRY -> "your next quarry"; };
        if (g == null) p.sendMessage(Text.literal("The needle wanders - it cannot find " + what + ".").formatted(Formatting.GRAY), true);
        else if (!g.getDimension().equals(world.getRegistryKey())) p.sendMessage(Text.literal("It desires " + what + " - in another world.").formatted(Formatting.GRAY), true);
        else p.sendMessage(Text.literal("The needle swings toward " + what + ": " + Navigation.bearing(p.getBlockPos(), g.getPos())).formatted(Formatting.GOLD), true);
        world.playSound(null, p.getBlockPos(), SoundEvents.ITEM_LODESTONE_COMPASS_LOCK, SoundCategory.PLAYERS, 0.8f, 1.2f);
        user.getItemCooldownManager().set(this, 20);
        return TypedActionResult.success(s);
    }

    @Nullable
    static GlobalPos find(ServerPlayerEntity p, Mode m, ItemStack stack) {
        ServerWorld w = p.getServerWorld();
        switch (m) {
            case HOME -> {
                HomesteadState.Hideout h = HomesteadState.get(p.getServer()).hideout(p.getUuid());
                if (h != null) return GlobalPos.create(h.dim(), h.pos());
                if (p.getSpawnPointPosition() != null) return GlobalPos.create(p.getSpawnPointDimension(), p.getSpawnPointPosition());
                return GlobalPos.create(World.OVERWORLD, p.getServer().getOverworld().getSpawnPos());
            }
            case DEATH -> { return p.getLastDeathPos().orElse(null); }
            case TREASURE -> {
                BlockPos best = null;
                String bestKey = "";
                double bd = Double.MAX_VALUE;
                var found = HomesteadState.get(p.getServer()).discoveries(p.getUuid());
                for (String id : TREASURES) {
                    BlockPos s = Navigation.locate(w, id, p.getBlockPos(), 8);
                    if (s == null || found.contains(siteKey(id, s))) continue;
                    double d = s.getSquaredDistance(p.getBlockPos());
                    if (d < bd) { bd = d; best = s; bestKey = siteKey(id, s); }
                }
                stack.getOrCreateNbt().putString("TKey", bestKey);
                return best == null ? null : GlobalPos.create(w.getRegistryKey(), best);
            }
            case QUARRY -> {
                BlockPos l = Navigation.nextLair(p);
                return l == null ? null : GlobalPos.create(w.getRegistryKey(), l);
            }
        }
        return null;
    }

    public static String siteKey(String id, BlockPos s) { return "site:" + id + ":" + s.getX() + ":" + s.getZ(); }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (world.isClient || !(entity instanceof ServerPlayerEntity p) || world.getTime() % 20 != 0) return;
        Mode m = mode(stack);
        if (m == Mode.TREASURE) {
            GlobalPos g = target(stack);
            NbtCompound n = stack.getNbt();
            if (g != null && n != null && g.getDimension().equals(world.getRegistryKey()) && g.getPos().getSquaredDistance(p.getX(), g.getPos().getY(), p.getZ()) < 24 * 24) {
                String key = n.getString("TKey");
                if (!key.isEmpty() && HomesteadState.get(p.getServer()).discover(p.getUuid(), key)) {
                    p.sendMessage(Text.literal("[Logbook] Treasure found - the Compass of Desire turns to the next.").formatted(Formatting.GOLD), false);
                    p.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.6f, 1.4f);
                }
                n.remove("TDim");
                n.remove("TKey");
            }
        } else if (m == Mode.HOME || m == Mode.DEATH) {
            if (world.getTime() % 100 == 0) setTarget(stack, find(p, m, stack));                     // cheap: follow a moved bed/death
        }
    }

    @Override
    public boolean hasGlint(ItemStack stack) { return target(stack) != null; }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Desires: " + mode(stack).name().toLowerCase()).formatted(Formatting.GOLD));
        tooltip.add(Text.literal("Use to seek, sneak-use to change its desire").formatted(Formatting.GRAY));
    }
}
