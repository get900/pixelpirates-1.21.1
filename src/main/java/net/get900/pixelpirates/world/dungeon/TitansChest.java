package net.get900.pixelpirates.world.dungeon;

import com.mojang.serialization.Codec;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.entity.mob.AbyssalHeartEntity;
import net.get900.pixelpirates.world.gen.density.ZoneTerrainFunction;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the Titan's Chest ({@link TitansChestLayout}) - the Abyssal Heart's lair - one chunk at a time, exactly like
 * {@link GallowsGrotto}: the site is the predicted {@code abyssal_heart_lair} dungeon site, the layout is built once per
 * site (cached), and every chunk inside its ~170 x 150 footprint copies its own column slice. Placed in EVERY biome
 * (after the grotto) because the footprint spills over biome borders. The Heart and the Eye of Thalassar are spawned
 * in their own chunks, configured BEFORE the spawn (a ProtoChunk serialises an entity the moment it is added).
 */
public class TitansChest extends Feature<DefaultFeatureConfig> {
    public static final String ID = "abyssal_heart_lair";

    public TitansChest(Codec<DefaultFeatureConfig> codec) { super(codec); }

    record Site(BlockPos centre, TitansChestLayout layout, BlockState[] states) {}

    private static final Map<Long, Site> CACHE = new LinkedHashMap<>(4, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Site> e) { return size() > 3; }
    };

    static Site site(BlockPos centre) {
        long k = centre.asLong();
        synchronized (CACHE) {
            Site s = CACHE.get(k);
            if (s != null) return s;
        }
        long seed = centre.getX() * 341873128712L ^ centre.getZ() * 132897987541L ^ 0x7EA27L;
        TitansChestLayout L = TitansChestLayout.build(seed, (x, z) -> ZoneTerrainFunction.terrainTop(centre.getX() + x, centre.getZ() + z));
        BlockState[] states = new BlockState[L.palette().size()];
        for (int i = 0; i < states.length; i++) {
            String desc = L.palette().get(i);
            try {
                states[i] = BlockArgumentParser.block(Registries.BLOCK.getReadOnlyWrapper(), desc, false).blockState();
            } catch (Exception e) {
                PixelPirates.LOGGER.error("[TitansChest] bad palette entry '{}' - using deepslate", desc);
                states[i] = Blocks.DEEPSLATE.getDefaultState();
            }
        }
        Site s = new Site(centre.toImmutable(), L, states);
        synchronized (CACHE) { CACHE.put(k, s); }
        return s;
    }

    /**
     * Site test for DungeonPlacement: the whole chest must sit under solid seabed - the Maw Depths' pits plunge to the
     * bottom of the world and would swallow the cavern. Samples the analytic terrain over the footprint.
     */
    public static boolean terrainOk(int cx, int cz) {
        for (int x = -60; x <= 60; x += 15) for (int z = -64; z <= 64; z += 16)
            if (ZoneTerrainFunction.terrainTop(cx + x, cz + z) < 40) return false;
        return true;
    }

    /** Every predicted chest site whose footprint overlaps the chunk at (minX, minZ). */
    static List<BlockPos> sitesNear(DungeonPlacement.Context ctx, int minX, int minZ) {
        Dungeons.Type t = Dungeons.byId(ID);
        List<BlockPos> out = new ArrayList<>();
        if (t == null) return out;
        int sp = t.spacing(), cx = Math.floorDiv(minX >> 4, sp), cz = Math.floorDiv(minZ >> 4, sp);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            ChunkPos c = DungeonPlacement.candidate(t, ctx, cx + dx, cz + dz);
            if (c == null) continue;
            BlockPos centre = new BlockPos(c.getCenterX(), 0, c.getCenterZ());
            if (minX + 15 < centre.getX() + TitansChestLayout.X0 || minX > centre.getX() + TitansChestLayout.X1) continue;
            if (minZ + 15 < centre.getZ() + TitansChestLayout.Z0 || minZ > centre.getZ() + TitansChestLayout.Z1) continue;
            out.add(centre);
        }
        return out;
    }

    /** True inside (or on top of) a chest's built volume - FeatureExclusionMixin keeps springs, geodes, kelp out of it. */
    public static boolean insideVolume(DungeonPlacement.Context ctx, BlockPos pos) {
        for (BlockPos c : sitesNear(ctx, pos.getX() & ~15, pos.getZ() & ~15)) {
            Site s = site(c);
            int rx = pos.getX() - c.getX(), rz = pos.getZ() - c.getZ();
            if (s.layout().get(rx, pos.getY(), rz) != 0 || s.layout().get(rx, pos.getY() - 1, rz) != 0) return true;
        }
        return false;
    }

    /** Within {@code pad} blocks of a site's whole bounding box (any depth) - big features (geodes) stay clear of it. */
    public static boolean nearFootprint(DungeonPlacement.Context ctx, BlockPos pos, int pad) {
        Dungeons.Type t = Dungeons.byId(ID);
        if (t == null || pos.getY() < TitansChestLayout.Y0 - pad || pos.getY() > TitansChestLayout.Y1 + pad) return false;
        int sp = t.spacing(), cx = Math.floorDiv(pos.getX() >> 4, sp), cz = Math.floorDiv(pos.getZ() >> 4, sp);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            ChunkPos c = DungeonPlacement.candidate(t, ctx, cx + dx, cz + dz);
            if (c == null) continue;
            int rx = pos.getX() - c.getCenterX(), rz = pos.getZ() - c.getCenterZ();
            if (rx >= TitansChestLayout.X0 - pad && rx <= TitansChestLayout.X1 + pad && rz >= TitansChestLayout.Z0 - pad && rz <= TitansChestLayout.Z1 + pad) return true;
        }
        return false;
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> ctx) {
        StructureWorldAccess world = ctx.getWorld();
        int minX = ctx.getOrigin().getX() & ~15, minZ = ctx.getOrigin().getZ() & ~15;
        boolean any = false;
        try {
            for (BlockPos c : sitesNear(DungeonFeature.placementContext(world, ctx.getGenerator()), minX, minZ))
                any |= renderChunk(world, site(c), minX, minZ, Block.NOTIFY_LISTENERS);
        } catch (Exception e) {
            PixelPirates.LOGGER.error("[TitansChest] chunk {} {} failed", minX, minZ, e);
        }
        return any;
    }

    /** Copy one chunk's slice of the site into the world (+ loot, and the Heart / the Eye in their chunks). */
    static boolean renderChunk(StructureWorldAccess world, Site s, int minX, int minZ, int flags) {
        TitansChestLayout L = s.layout();
        BlockPos c = s.centre();
        BlockPos.Mutable p = new BlockPos.Mutable();
        boolean any = false;
        for (int x = minX; x < minX + 16; x++) for (int z = minZ; z < minZ + 16; z++) {
            int rx = x - c.getX(), rz = z - c.getZ();
            if (rx < TitansChestLayout.X0 || rx > TitansChestLayout.X1 || rz < TitansChestLayout.Z0 || rz > TitansChestLayout.Z1) continue;
            for (int y = TitansChestLayout.Y0; y <= TitansChestLayout.Y1; y++) {
                int id = L.get(rx, y, rz);
                if (id == 0) continue;
                p.set(x, y, z);
                world.setBlockState(p, s.states()[id - 1], flags);
                any = true;
                String loot = L.loot.get(TitansChestLayout.key(rx, y, rz));
                if (loot != null) LootableContainerBlockEntity.setLootTable(world, world.getRandom(), p, new Identifier(loot));
            }
        }
        if (inChunk(c, L.heartSpawn, minX, minZ)) spawnHeart(world, s);
        if (inChunk(c, L.eyeSpawn, minX, minZ)) spawnEye(world, s);
        return any;
    }

    private static boolean inChunk(BlockPos c, int[] rel, int minX, int minZ) {
        return (c.getX() + rel[0]) >> 4 == minX >> 4 && (c.getZ() + rel[2]) >> 4 == minZ >> 4;
    }

    private static BlockPos w(BlockPos c, int[] rel) { return c.add(rel[0], rel[1], rel[2]); }

    private static List<BlockPos> w(BlockPos c, List<int[]> rel) {
        List<BlockPos> out = new ArrayList<>();
        for (int[] r : rel) out.add(w(c, r));
        return out;
    }

    private static void spawnHeart(StructureWorldAccess world, Site s) {
        BlockPos c = s.centre();
        TitansChestLayout L = s.layout();
        var e = Registries.ENTITY_TYPE.get(PixelPirates.id("abyssal_heart")).create(world.toServerWorld());
        if (!(e instanceof AbyssalHeartEntity heart)) return;
        BlockPos at = w(c, L.heartSpawn);
        heart.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180f, 0);
        heart.initialize(world, world.getLocalDifficulty(at), SpawnReason.STRUCTURE, null, null);
        heart.setPersistent();
        List<List<BlockPos>> valves = new ArrayList<>();
        for (List<int[]> v : L.valves) valves.add(w(c, v));
        List<double[]> arteries = new ArrayList<>();
        for (double[] a : L.arteries)
            arteries.add(new double[]{a[0] + c.getX() + 0.5, a[1], a[2] + c.getZ() + 0.5, a[3] + c.getX() + 0.5, a[4], a[5] + c.getZ() + 0.5});
        heart.setLair(new AbyssalHeartEntity.Lair(w(c, new int[]{0, TitansChestLayout.F, 0}), w(c, L.pylons), w(c, L.lamps), arteries,
                valves, w(c, L.nodes), w(c, L.clotSpots), w(c, L.hubSpots), w(c, L.ejectSpots), w(c, L.eyeSpawn)));
        world.spawnEntityAndPassengers(heart);
    }

    private static void spawnEye(StructureWorldAccess world, Site s) {
        BlockPos at = w(s.centre(), s.layout().eyeSpawn);
        var e = Registries.ENTITY_TYPE.get(PixelPirates.id("rival_eye")).create(world.toServerWorld());
        if (!(e instanceof MobEntity eye)) return;
        eye.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0);       // yaw 0 = looking south, into the chest
        eye.setHeadYaw(0f);
        eye.setBodyYaw(0f);
        eye.initialize(world, world.getLocalDifficulty(at), SpawnReason.STRUCTURE, null, null);
        eye.setPersistent();
        world.spawnEntityAndPassengers(eye);
    }

    /** /ppdungeon abyssal_heart_lair: build the whole chest with its centre here (live world; takes a few seconds). */
    public static int buildAll(net.minecraft.server.world.ServerWorld world, BlockPos centre) {
        Site s = site(new BlockPos(centre.getX(), 0, centre.getZ()));
        int n = 0;
        for (int cx = (centre.getX() + TitansChestLayout.X0) >> 4; cx <= (centre.getX() + TitansChestLayout.X1) >> 4; cx++)
            for (int cz = (centre.getZ() + TitansChestLayout.Z0) >> 4; cz <= (centre.getZ() + TitansChestLayout.Z1) >> 4; cz++) {
                world.getChunk(cx, cz);
                if (renderChunk(world, s, cx << 4, cz << 4, Block.NOTIFY_LISTENERS | Block.FORCE_STATE)) n++;
            }
        return n;
    }
}
