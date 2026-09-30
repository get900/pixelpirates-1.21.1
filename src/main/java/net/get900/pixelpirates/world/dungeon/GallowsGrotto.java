package net.get900.pixelpirates.world.dungeon;

import com.mojang.serialization.Codec;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.entity.mob.ChainedRevenantEntity;
import net.get900.pixelpirates.world.gen.density.ZoneTerrainFunction;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
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
 * Renders the Gallows Grotto ({@link GallowsGrottoLayout}) - the Chained Revenant's lair - one chunk at a time, like
 * the spawn-island city: the site is the predicted {@code revenant_crypt} dungeon site (DungeonPlacement grid), the
 * layout is built once per site (cached), and every chunk inside its ~185 x 250 footprint copies its own column
 * slice. This feature is placed in EVERY biome (SURFACE_STRUCTURES, after the dungeons) because the footprint spills
 * over biome borders; outside a footprint it costs one cached grid lookup per nearby cell.
 */
public class GallowsGrotto extends Feature<DefaultFeatureConfig> {
    public static final String ID = "revenant_crypt";

    public GallowsGrotto(Codec<DefaultFeatureConfig> codec) { super(codec); }

    /** A built layout + its parsed palette. */
    record Site(BlockPos centre, GallowsGrottoLayout layout, BlockState[] states) {}

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
        long seed = centre.getX() * 341873128712L ^ centre.getZ() * 132897987541L ^ 0x6A11057L;
        GallowsGrottoLayout L = GallowsGrottoLayout.build(seed, (x, z) -> ZoneTerrainFunction.terrainTop(centre.getX() + x, centre.getZ() + z));
        BlockState[] states = new BlockState[L.palette().size()];
        for (int i = 0; i < states.length; i++) {
            String desc = L.palette().get(i);
            try {
                states[i] = BlockArgumentParser.block(Registries.BLOCK.getReadOnlyWrapper(), desc, false).blockState();
            } catch (Exception e) {
                PixelPirates.LOGGER.error("[Grotto] bad palette entry '{}' - using deepslate", desc);
                states[i] = Blocks.DEEPSLATE.getDefaultState();
            }
        }
        Site s = new Site(centre.toImmutable(), L, states);
        synchronized (CACHE) { CACHE.put(k, s); }
        return s;
    }

    /** Every predicted grotto site whose footprint overlaps the chunk at (minX, minZ). */
    static List<BlockPos> sitesNear(DungeonPlacement.Context ctx, int minX, int minZ) {
        Dungeons.Type t = Dungeons.byId(ID);
        List<BlockPos> out = new ArrayList<>();
        if (t == null) return out;
        int sp = t.spacing(), cx = Math.floorDiv(minX >> 4, sp), cz = Math.floorDiv(minZ >> 4, sp);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            ChunkPos c = DungeonPlacement.candidate(t, ctx, cx + dx, cz + dz);
            if (c == null) continue;
            BlockPos centre = new BlockPos(c.getCenterX(), 0, c.getCenterZ());
            if (minX + 15 < centre.getX() + GallowsGrottoLayout.X0 || minX > centre.getX() + GallowsGrottoLayout.X1) continue;
            if (minZ + 15 < centre.getZ() + GallowsGrottoLayout.Z0 || minZ > centre.getZ() + GallowsGrottoLayout.Z1) continue;
            out.add(centre);
        }
        return out;
    }

    /** True inside any grotto's built volume (for FeatureExclusionMixin: keep springs, geodes etc. out of it). */
    public static boolean insideVolume(DungeonPlacement.Context ctx, BlockPos pos) {
        for (BlockPos c : sitesNear(ctx, pos.getX() & ~15, pos.getZ() & ~15)) {
            Site s = site(c);
            int rx = pos.getX() - c.getX(), rz = pos.getZ() - c.getZ();
            // inside the built volume - or standing right on top of it (trees, boulders on Hangman's Rock)
            if (s.layout().get(rx, pos.getY(), rz) != 0 || s.layout().get(rx, pos.getY() - 1, rz) != 0 || s.layout().get(rx, pos.getY() - 2, rz) != 0) return true;
        }
        return false;
    }

    /** Within {@code pad} blocks of a site's whole bounding box (any depth) - big features (geodes) stay clear of it. */
    public static boolean nearFootprint(DungeonPlacement.Context ctx, BlockPos pos, int pad) {
        Dungeons.Type t = Dungeons.byId(ID);
        if (t == null || pos.getY() < GallowsGrottoLayout.Y0 - pad || pos.getY() > GallowsGrottoLayout.Y1 + pad) return false;
        int sp = t.spacing(), cx = Math.floorDiv(pos.getX() >> 4, sp), cz = Math.floorDiv(pos.getZ() >> 4, sp);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            ChunkPos c = DungeonPlacement.candidate(t, ctx, cx + dx, cz + dz);
            if (c == null) continue;
            int rx = pos.getX() - c.getCenterX(), rz = pos.getZ() - c.getCenterZ();
            if (rx >= GallowsGrottoLayout.X0 - pad && rx <= GallowsGrottoLayout.X1 + pad && rz >= GallowsGrottoLayout.Z0 - pad && rz <= GallowsGrottoLayout.Z1 + pad) return true;
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
            PixelPirates.LOGGER.error("[Grotto] chunk {} {} failed", minX, minZ, e);
        }
        return any;
    }

    /** Copy one chunk's slice of the site into the world (+ loot, spawners, and the Revenant in its chunk). */
    static boolean renderChunk(StructureWorldAccess world, Site s, int minX, int minZ, int flags) {
        GallowsGrottoLayout L = s.layout();
        BlockPos c = s.centre();
        BlockPos.Mutable p = new BlockPos.Mutable();
        boolean any = false;
        for (int x = minX; x < minX + 16; x++) for (int z = minZ; z < minZ + 16; z++) {
            int rx = x - c.getX(), rz = z - c.getZ();
            if (rx < GallowsGrottoLayout.X0 || rx > GallowsGrottoLayout.X1 || rz < GallowsGrottoLayout.Z0 || rz > GallowsGrottoLayout.Z1) continue;
            for (int y = GallowsGrottoLayout.Y0; y <= GallowsGrottoLayout.Y1; y++) {
                int id = L.get(rx, y, rz);
                if (id == 0) continue;
                p.set(x, y, z);
                world.setBlockState(p, s.states()[id - 1], flags);
                any = true;
                long k = GallowsGrottoLayout.key(rx, y, rz);
                String loot = L.loot.get(k);
                if (loot != null) LootableContainerBlockEntity.setLootTable(world, world.getRandom(), p, new Identifier(loot));
                String sp = L.spawners.get(k);
                if (sp != null) configureSpawner(world, p, sp);
                String[] sign = L.signs.get(k);
                if (sign != null && world.getBlockEntity(p) instanceof net.minecraft.block.entity.SignBlockEntity sbe) {
                    // via NBT: setText() calls world.updateListeners, and a worldgen block entity has no world yet (NPE)
                    NbtCompound front = new NbtCompound();
                    net.minecraft.nbt.NbtList msgs = new net.minecraft.nbt.NbtList();
                    for (int i = 0; i < 4; i++)
                        msgs.add(net.minecraft.nbt.NbtString.of(net.minecraft.text.Text.Serializer.toJson(net.minecraft.text.Text.literal(i < sign.length ? sign[i] : ""))));
                    front.put("messages", msgs);
                    front.putString("color", "black");
                    front.putBoolean("has_glowing_text", false);
                    NbtCompound nbt = sbe.createNbt();
                    nbt.put("front_text", front);
                    nbt.putBoolean("is_waxed", true);
                    sbe.readNbt(nbt);
                }
            }
        }
        int[] b = L.bossSpawn;
        if (b != null && (c.getX() + b[0]) >> 4 == minX >> 4 && (c.getZ() + b[2]) >> 4 == minZ >> 4) spawnBoss(world, s);
        for (Object[] n : L.npcs) {                                                  // Old Wick & co., once, in their own chunk
            int[] q = (int[]) n[0];
            if ((c.getX() + q[0]) >> 4 != minX >> 4 || (c.getZ() + q[2]) >> 4 != minZ >> 4) continue;
            var e = Registries.ENTITY_TYPE.get(new Identifier((String) n[1])).create(world.toServerWorld());
            if (!(e instanceof net.minecraft.entity.mob.MobEntity mob)) continue;
            BlockPos at = c.add(q[0], q[1], q[2]);
            mob.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 90f, 0);
            mob.initialize(world, world.getLocalDifficulty(at), SpawnReason.STRUCTURE, null, null);
            mob.setPersistent();
            world.spawnEntityAndPassengers(mob);
        }
        return any;
    }

    private static void spawnBoss(StructureWorldAccess world, Site s) {
        BlockPos c = s.centre();
        GallowsGrottoLayout L = s.layout();
        EntityType<?> type = Registries.ENTITY_TYPE.get(PixelPirates.id("chained_revenant"));
        var e = type.create(world.toServerWorld());
        if (!(e instanceof ChainedRevenantEntity r)) return;
        BlockPos at = c.add(L.bossSpawn[0], L.bossSpawn[1], L.bossSpawn[2]);
        // x on the block edge between the two wrist chains; hung half a block lower so his fists meet the chain ends
        r.refreshPositionAndAngles(at.getX(), at.getY() + 0.8, at.getZ() + 0.5, 180f, 0);
        r.initialize(world, world.getLocalDifficulty(at), SpawnReason.STRUCTURE, null, null);
        r.setPersistent();
        List<int[]> anchors = new ArrayList<>();
        for (int[] a : L.anchors) anchors.add(new int[]{c.getX() + a[0], a[1], c.getZ() + a[2], a[3]});
        List<BlockPos> cages = new ArrayList<>();
        for (int[] k : L.cages) cages.add(c.add(k[0], k[1], k[2]));
        r.setLair(anchors, cages);                       // before the spawn: a ProtoChunk serialises it immediately
        if (L.bladeStone != null) {
            List<BlockPos> chains = new ArrayList<>();
            for (int[] k : L.hangChains) chains.add(c.add(k[0], k[1], k[2]));
            List<BlockPos> lights = new ArrayList<>();
            for (int[] k : L.lightWall) lights.add(c.add(k[0], k[1], k[2]));
            r.setGallows(chains, c.add(L.bladeStone[0], L.bladeStone[1], L.bladeStone[2]), lights);
        }
        world.spawnEntityAndPassengers(r);
    }

    private static void configureSpawner(StructureWorldAccess world, BlockPos p, String entity) {
        BlockEntity be = world.getBlockEntity(p);
        if (!(be instanceof MobSpawnerBlockEntity)) return;
        NbtCompound ent = new NbtCompound();
        ent.putString("id", entity);
        NbtCompound rules = new NbtCompound();
        rules.putIntArray("block_light_limit", new int[]{0, 15});
        rules.putIntArray("sky_light_limit", new int[]{0, 15});
        NbtCompound data = new NbtCompound();
        data.put("entity", ent);
        data.put("custom_spawn_rules", rules);
        NbtCompound nbt = be.createNbt();
        nbt.put("SpawnData", data);
        nbt.remove("SpawnPotentials");
        nbt.putShort("Delay", (short) 20);
        nbt.putShort("MinSpawnDelay", (short) 240);
        nbt.putShort("MaxSpawnDelay", (short) 700);
        nbt.putShort("SpawnCount", (short) 2);
        nbt.putShort("MaxNearbyEntities", (short) 4);
        nbt.putShort("RequiredPlayerRange", (short) 16);
        nbt.putShort("SpawnRange", (short) 3);
        be.readNbt(nbt);
        be.markDirty();
    }

    /** /ppdungeon revenant_crypt: build the whole grotto with its centre here (live world; takes a few seconds). */
    public static int buildAll(net.minecraft.server.world.ServerWorld world, BlockPos centre) {
        Site s = site(new BlockPos(centre.getX(), 0, centre.getZ()));
        int n = 0;
        for (int cx = (centre.getX() + GallowsGrottoLayout.X0) >> 4; cx <= (centre.getX() + GallowsGrottoLayout.X1) >> 4; cx++)
            for (int cz = (centre.getZ() + GallowsGrottoLayout.Z0) >> 4; cz <= (centre.getZ() + GallowsGrottoLayout.Z1) >> 4; cz++) {
                world.getChunk(cx, cz);
                if (renderChunk(world, s, cx << 4, cz << 4, Block.NOTIFY_LISTENERS | Block.FORCE_STATE)) n++;
            }
        return n;
    }
}
