package net.get900.pixelpirates.world.gen;

import com.mojang.serialization.Codec;
import net.get900.pixelpirates.PixelPirates;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.List;

/**
 * Renders the spawn-island port city ({@link PortCityLayout}) one chunk at a
 * time. The layout is a deterministic block plan in absolute world
 * coordinates; every chunk that intersects it copies its own 16x16 slice.
 * This is how the city can span hundreds of blocks even though a feature may
 * only write near its own chunk.
 */
public class SpawnIslandFeature extends Feature<DefaultFeatureConfig> {

    private static volatile BlockState[] paletteStates;

    public SpawnIslandFeature(Codec<DefaultFeatureConfig> codec) {
        super(codec);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
        StructureWorldAccess world = context.getWorld();
        BlockPos origin = context.getOrigin();

        int chunkMinX = origin.getX() & ~15;
        int chunkMinZ = origin.getZ() & ~15;
        if (!PortCityLayout.chunkIntersects(chunkMinX, chunkMinZ)) return false;

        PortCityLayout.ensureBuilt();
        BlockState[] states = paletteStates();

        BlockPos.Mutable pos = new BlockPos.Mutable();
        boolean placedAny = false;

        int x1 = Math.max(chunkMinX, PortCityLayout.X0);
        int x2 = Math.min(chunkMinX + 15, PortCityLayout.X1);
        int z1 = Math.max(chunkMinZ, PortCityLayout.Z0);
        int z2 = Math.min(chunkMinZ + 15, PortCityLayout.Z1);

        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                for (int y = PortCityLayout.Y0; y <= PortCityLayout.Y1; y++) {
                    int id = PortCityLayout.get(x, y, z);
                    if (id == 0) continue;
                    BlockState state = states[id - 1];
                    pos.set(x, y, z);
                    if (state.isAir()) {
                        // clearing pass — don't churn already-empty blocks
                        if (world.getBlockState(pos).isAir()) continue;
                        world.setBlockState(pos, state, Block.NOTIFY_ALL);
                        placedAny = true;
                        continue;
                    }
                    world.setBlockState(pos, state, Block.NOTIFY_ALL);
                    placedAny = true;

                    String loot = PortCityLayout.lootAt(x, y, z);
                    if (loot != null) {
                        LootableContainerBlockEntity.setLootTable(world, world.getRandom(), pos, new Identifier(loot));
                    }
                }
            }
        }

        if (placedAny && chunkMinX == 0 && chunkMinZ == 0) {
            PixelPirates.LOGGER.info("[PixelPirates] Port city generating around spawn (layout {} palette entries)",
                    PortCityLayout.palette().size());
        }
        return placedAny;
    }

    /** Parses the layout's palette strings into block states once. */
    private static BlockState[] paletteStates() {
        BlockState[] states = paletteStates;
        if (states != null) return states;
        synchronized (SpawnIslandFeature.class) {
            if (paletteStates != null) return paletteStates;
            List<String> palette = PortCityLayout.palette();
            states = new BlockState[palette.size()];
            for (int i = 0; i < palette.size(); i++) {
                String desc = palette.get(i);
                try {
                    states[i] = BlockArgumentParser
                            .block(Registries.BLOCK.getReadOnlyWrapper(), desc, false)
                            .blockState();
                } catch (Exception e) {
                    PixelPirates.LOGGER.error("[PixelPirates] Bad palette entry '{}' — using stone", desc, e);
                    states[i] = net.minecraft.block.Blocks.STONE.getDefaultState();
                }
            }
            paletteStates = states;
            return states;
        }
    }
}
