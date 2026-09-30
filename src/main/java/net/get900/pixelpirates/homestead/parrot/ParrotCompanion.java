package net.get900.pixelpirates.homestead.parrot;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.get900.pixelpirates.homestead.Navigation;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * PARROT COMPANION (#29): a parrot riding your shoulder is a proper ship's parrot (wild ones now live in the island
 * thickets - tame one with seeds as usual).
 *   - STAYS PUT: it no longer flaps off when you swim, fall or get hit (ParrotShoulderMixin); sneak on solid ground for
 *     2 s to set it down (sleeping and dying still drop it, as in vanilla).
 *   - LOOKOUT: a monster closing on you makes it squawk ("Behind ye!" when it is behind you) and marks the brute
 *     with Glowing for 3 s.
 *   - TREASURE NOSE: every 15 s it sniffs out the nearest unopened loot container within 24 blocks you haven't been told
 *     about - "Treasure, cap'n!" with a bearing and a sparkle over it.
 */
public final class ParrotCompanion {
    private ParrotCompanion() {}

    /** Set while WE drop the shoulder parrots, so the mixin lets it through. */
    public static final ThreadLocal<Boolean> ALLOW_DROP = ThreadLocal.withInitial(() -> false);
    private static final Map<UUID, Integer> SNEAK = new HashMap<>();
    private static final Map<UUID, Map<Integer, Long>> WARNED = new HashMap<>();
    private static final Map<UUID, Set<Long>> TOLD = new HashMap<>();

    public static boolean hasParrot(PlayerEntity p) { return isParrot(p.getShoulderEntityLeft()) || isParrot(p.getShoulderEntityRight()); }

    private static boolean isParrot(NbtCompound n) { return n != null && "minecraft:parrot".equals(n.getString("id")); }

    public static void register() {
        // wild parrots in the tropical island jungle (CREATURE is the parrot's own group; its canSpawn needs no darkness)
        net.fabricmc.fabric.api.biome.v1.BiomeModifications.addSpawn(
                net.fabricmc.fabric.api.biome.v1.BiomeSelectors.includeByKey(net.get900.pixelpirates.world.biome.ModBiomeKeys.ISLAND_THICKETS),
                net.minecraft.entity.SpawnGroup.CREATURE, net.minecraft.entity.EntityType.PARROT, 5, 1, 2);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long t = server.getTicks();
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (!hasParrot(p)) {
                    SNEAK.remove(p.getUuid());
                    continue;
                }
                setDown(p);
                if (t % 10 == 3) lookout(p, t);
                if (t % 300 == 17) treasure(p);
            }
        });
    }

    private static void setDown(ServerPlayerEntity p) {
        if (p.isSneaking() && p.isOnGround()) {
            int n = SNEAK.merge(p.getUuid(), 1, Integer::sum);
            if (n == 40) {
                ALLOW_DROP.set(true);
                try {
                    ((net.get900.pixelpirates.mixin.PlayerShoulderInvoker) p).pixelpirates$dropShoulderEntities();
                } finally {
                    ALLOW_DROP.set(false);
                }
                SNEAK.remove(p.getUuid());
            }
        } else SNEAK.remove(p.getUuid());
    }

    private static void lookout(ServerPlayerEntity p, long t) {
        Map<Integer, Long> warned = WARNED.computeIfAbsent(p.getUuid(), k -> new HashMap<>());
        warned.values().removeIf(v -> v < t - 400);
        for (MobEntity m : p.getServerWorld().getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(14),
                e -> e instanceof HostileEntity && e.isAlive() && e.getTarget() == p)) {
            if (warned.containsKey(m.getId())) continue;
            warned.put(m.getId(), t);
            m.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0, false, false));
            Vec3d look = p.getRotationVec(1f), to = m.getPos().subtract(p.getPos()).normalize();
            boolean behind = look.x * to.x + look.z * to.z < -0.2;
            p.getServerWorld().playSound(null, p.getX(), p.getY() + 1.6, p.getZ(), SoundEvents.ENTITY_PARROT_AMBIENT, SoundCategory.NEUTRAL, 1.2f, 1.5f);
            p.sendMessage(Text.literal(behind ? "*SQUAWK* Behind ye! " : "*SQUAWK* ").formatted(Formatting.RED)
                    .append(m.getDisplayName().copy().formatted(Formatting.WHITE)), true);
            return;                                                         // one squawk at a time
        }
    }

    private static void treasure(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        Set<Long> told = TOLD.computeIfAbsent(p.getUuid(), k -> new HashSet<>());
        BlockPos best = null;
        double bd = 24 * 24;
        ChunkPos c = p.getChunkPos();
        for (int cx = c.x - 2; cx <= c.x + 2; cx++)
            for (int cz = c.z - 2; cz <= c.z + 2; cz++) {
                WorldChunk ch = w.getChunkManager().getWorldChunk(cx, cz, false);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    if (!(be instanceof LootableContainerBlockEntity) || told.contains(be.getPos().asLong())) continue;
                    double d = be.getPos().getSquaredDistance(p.getPos());
                    if (d < bd && be.createNbt().contains("LootTable")) { bd = d; best = be.getPos(); }
                }
            }
        if (best == null) return;
        told.add(best.asLong());
        w.playSound(null, p.getX(), p.getY() + 1.6, p.getZ(), SoundEvents.ENTITY_PARROT_IMITATE_WITCH, SoundCategory.NEUTRAL, 0.8f, 1.8f);
        p.sendMessage(Text.literal("*SQUAWK* Treasure, cap'n! " + Navigation.bearing(p.getBlockPos(), best)).formatted(Formatting.GOLD), false);
        w.spawnParticles(p, ParticleTypes.WAX_ON, true, best.getX() + 0.5, best.getY() + 1.2, best.getZ() + 0.5, 20, 0.4, 0.5, 0.4, 0.05);
    }
}
