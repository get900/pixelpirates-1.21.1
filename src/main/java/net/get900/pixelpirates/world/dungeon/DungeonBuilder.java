package net.get900.pixelpirates.world.dungeon;

import net.get900.pixelpirates.PixelPirates;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;

/**
 * Small helper for code-built dungeons. Coordinates are RELATIVE to {@link #origin} and
 * turned by {@link #rot} (so one design can face four ways); block states are rotated to
 * match. Features may only write within the 3x3 chunks around the decorated chunk, so
 * callers snap the origin to a chunk centre and keep every design inside |x|,|z| <= 22.
 */
public class DungeonBuilder {
    public static final int MAX_REACH = 22;

    public final StructureWorldAccess world;
    public final BlockPos origin;
    public final BlockRotation rot;
    public final Random random;

    public DungeonBuilder(StructureWorldAccess world, BlockPos origin, BlockRotation rot, Random random) {
        this.world = world;
        this.origin = origin;
        this.rot = rot;
        this.random = random;
    }

    /** Snap any placement origin to the centre of its chunk (maximises the writable margin). */
    public static BlockPos chunkCentre(BlockPos pos) {
        return new BlockPos((pos.getX() & ~15) + 8, pos.getY(), (pos.getZ() & ~15) + 8);
    }

    public BlockPos pos(int x, int y, int z) {
        int rx, rz;
        switch (rot) {
            case CLOCKWISE_90 -> { rx = -z; rz = x; }
            case CLOCKWISE_180 -> { rx = -x; rz = -z; }
            case COUNTERCLOCKWISE_90 -> { rx = z; rz = -x; }
            default -> { rx = x; rz = z; }
        }
        return origin.add(rx, y, rz);
    }

    /** Design-space direction -> world direction. */
    public Direction dir(Direction d) { return rot.rotate(d); }

    public void set(int x, int y, int z, BlockState state) {
        world.setBlockState(pos(x, y, z), state.rotate(rot), Block.NOTIFY_LISTENERS);
    }

    public BlockState get(int x, int y, int z) { return world.getBlockState(pos(x, y, z)); }

    public boolean isWater(int x, int y, int z) { return world.getFluidState(pos(x, y, z)).isIn(FluidTags.WATER); }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    set(x, y, z, s);
    }

    /** Hollow box: walls/floor/ceiling from {@code wall}, interior {@code inside}. */
    public void room(int x1, int y1, int z1, int x2, int y2, int z2, Weathered wall, BlockState inside) {
        for (int x = x1; x <= x2; x++)
            for (int y = y1; y <= y2; y++)
                for (int z = z1; z <= z2; z++) {
                    boolean edge = x == x1 || x == x2 || y == y1 || y == y2 || z == z1 || z == z2;
                    set(x, y, z, edge ? wall.pick(random) : inside);
                }
    }

    /** Stone brick with moss and cracks mixed in; also used for any themed set of alternatives. */
    public record Weathered(BlockState main, BlockState alt1, BlockState alt2, float altChance) {
        public BlockState pick(Random r) {
            float f = r.nextFloat();
            if (f < altChance / 2) return alt1;
            if (f < altChance) return alt2;
            return main;
        }
    }

    public static final Weathered MOSSY_BRICKS = new Weathered(Blocks.STONE_BRICKS.getDefaultState(),
            Blocks.MOSSY_STONE_BRICKS.getDefaultState(), Blocks.CRACKED_STONE_BRICKS.getDefaultState(), 0.45f);
    public static final Weathered COBBLE_MIX = new Weathered(Blocks.COBBLESTONE.getDefaultState(),
            Blocks.MOSSY_COBBLESTONE.getDefaultState(), Blocks.ANDESITE.getDefaultState(), 0.4f);

    public void chest(int x, int y, int z, Direction facing, String lootTable) {
        set(x, y, z, Blocks.CHEST.getDefaultState().with(Properties.HORIZONTAL_FACING, facing)
                .with(Properties.WATERLOGGED, isWater(x, y, z)));
        LootableContainerBlockEntity.setLootTable(world, random, pos(x, y, z), new Identifier(PixelPirates.MOD_ID, lootTable));
    }

    public void barrelLoot(int x, int y, int z, String lootTable) {
        set(x, y, z, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        LootableContainerBlockEntity.setLootTable(world, random, pos(x, y, z), new Identifier(PixelPirates.MOD_ID, lootTable));
    }

    /**
     * Mob spawner that works regardless of light. Vanilla spawners otherwise run each mob's natural
     * spawn predicate (monsters need block light 0 + darkness), so lanterns or an open, sunlit deck
     * (the run-aground galleon) silently stopped them. custom_spawn_rules replaces that predicate with
     * an always-true light range; a peaceful world still suppresses hostile mobs.
     */
    public void spawner(int x, int y, int z, EntityType<?> type) {
        BlockPos p = pos(x, y, z);
        world.setBlockState(p, Blocks.SPAWNER.getDefaultState(), Block.NOTIFY_LISTENERS);
        BlockEntity be = world.getBlockEntity(p);
        if (!(be instanceof MobSpawnerBlockEntity)) return;
        net.minecraft.nbt.NbtCompound entity = new net.minecraft.nbt.NbtCompound();
        entity.putString("id", net.minecraft.registry.Registries.ENTITY_TYPE.getId(type).toString());
        net.minecraft.nbt.NbtCompound rules = new net.minecraft.nbt.NbtCompound();
        rules.putIntArray("block_light_limit", new int[]{0, 15});
        rules.putIntArray("sky_light_limit", new int[]{0, 15});
        net.minecraft.nbt.NbtCompound spawnData = new net.minecraft.nbt.NbtCompound();
        spawnData.put("entity", entity);
        spawnData.put("custom_spawn_rules", rules);
        net.minecraft.nbt.NbtCompound nbt = be.createNbt();
        nbt.put("SpawnData", spawnData);
        nbt.remove("SpawnPotentials");
        nbt.putShort("Delay", (short) 20);
        nbt.putShort("MinSpawnDelay", (short) 200);
        nbt.putShort("MaxSpawnDelay", (short) 600);
        nbt.putShort("SpawnCount", (short) 3);
        nbt.putShort("MaxNearbyEntities", (short) 5);
        nbt.putShort("RequiredPlayerRange", (short) 16);
        nbt.putShort("SpawnRange", (short) 3);
        be.readNbt(nbt);
        be.markDirty();
    }

    /** Ellipsoid: interior set to {@code inside(y)} (air/water by height), optional shell of {@code thick}. */
    public void ellipsoid(int cx, int cy, int cz, double rx, double ry, double rz,
                          java.util.function.IntFunction<BlockState> inside, Weathered shell, double thick) {
        int ex = (int) Math.ceil(rx + thick), ey = (int) Math.ceil(ry + thick), ez = (int) Math.ceil(rz + thick);
        for (int x = -ex; x <= ex; x++)
            for (int y = -ey; y <= ey; y++)
                for (int z = -ez; z <= ez; z++) {
                    double d = Math.sqrt((x * x) / (rx * rx) + (y * y) / (ry * ry) + (z * z) / (rz * rz));
                    if (d < 1.0) set(cx + x, cy + y, cz + z, inside.apply(cy + y));
                    else if (shell != null && d < 1.0 + thick / Math.min(rx, Math.min(ry, rz))) set(cx + x, cy + y, cz + z, shell.pick(random));
                }
    }

    /** Vertical cylinder; {@code hollow} leaves the inside as {@code inside(y)}. */
    public void cylinder(int cx, int cz, double r, int y1, int y2, Weathered wall, java.util.function.IntFunction<BlockState> inside, boolean hollow) {
        int R = (int) Math.ceil(r);
        for (int x = -R; x <= R; x++)
            for (int z = -R; z <= R; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > r) continue;
                for (int y = y1; y <= y2; y++) {
                    boolean edge = d > r - 1.0;
                    if (!hollow || edge) set(cx + x, y, cz + z, wall.pick(random));
                    else set(cx + x, y, cz + z, inside.apply(y));
                }
            }
    }

    /**
     * Place a persistent mob (guard or boss) by id. Bosses remember this spot as their arena home
     * (ModBoss#initialize). Works during worldgen: StructureWorldAccess is a ServerWorldAccess.
     */
    public net.minecraft.entity.Entity spawnMob(String id, int x, int y, int z) {
        return spawnMob(id, x, y, z, e -> {});
    }

    /**
     * As {@link #spawnMob(String, int, int, int)}, running {@code setup} BEFORE the entity is added.
     * During worldgen the chunk (ProtoChunk) serialises an entity the moment it is spawned, so any change
     * made to the returned entity afterwards is silently lost - the serpent's ward list and captive
     * castaways were (2026-09-29). /ppdungeon builds into a live world, which is why it hid the bug.
     */
    public net.minecraft.entity.Entity spawnMob(String id, int x, int y, int z, java.util.function.Consumer<net.minecraft.entity.Entity> setup) {
        EntityType<?> type = net.minecraft.registry.Registries.ENTITY_TYPE.get(new Identifier(id));
        net.minecraft.entity.Entity e = type.create(world.toServerWorld());
        if (e == null) return null;
        BlockPos p = pos(x, y, z);
        e.refreshPositionAndAngles(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, random.nextFloat() * 360f, 0);
        if (e instanceof net.minecraft.entity.mob.MobEntity mob) {
            mob.initialize(world, world.getLocalDifficulty(p), net.minecraft.entity.SpawnReason.STRUCTURE, null, null);
            mob.setPersistent();
        }
        setup.accept(e);
        world.spawnEntityAndPassengers(e);
        return e;
    }

    /** Clear to air, or to water where the design sits below the waterline. */
    public BlockState airOrWater(int y, int waterTop) {
        return y <= waterTop ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState();
    }

    public static BlockState water() { return Fluids.WATER.getStill().getDefaultState().getBlockState(); }
}
