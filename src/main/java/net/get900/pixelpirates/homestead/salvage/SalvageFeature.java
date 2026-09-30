package net.get900.pixelpirates.homestead.salvage;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.homestead.HomesteadBlocks;
import net.get900.pixelpirates.world.biome.ModBiomeKeys;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * SALVAGE (#9): little wreck sites on the seabed of the ocean zones - 1-3 sealed SALVAGE CRATEs among broken planks,
 * sometimes a snapped mast. Needs at least 3 blocks of water above. Placed at runtime (BiomeModifications) as the LAST
 * feature of VEGETAL_DECORATION in every ocean biome, so it can never form a feature-order cycle; the configured /
 * placed feature JSON live in resources/data/pixelpirates/worldgen (rarity 1 in 14 chunks).
 */
public class SalvageFeature extends Feature<DefaultFeatureConfig> {
    public static final Identifier LOOT = new Identifier(PixelPirates.MOD_ID, "chests/salvage_crate");
    public static final RegistryKey<PlacedFeature> PLACED = RegistryKey.of(RegistryKeys.PLACED_FEATURE, new Identifier(PixelPirates.MOD_ID, "salvage_site"));

    public SalvageFeature(Codec<DefaultFeatureConfig> codec) { super(codec); }

    public static void register() {
        Registry.register(Registries.FEATURE, new Identifier(PixelPirates.MOD_ID, "salvage_site"), new SalvageFeature(DefaultFeatureConfig.CODEC));
        BiomeModifications.addFeature(BiomeSelectors.includeByKey(ModBiomeKeys.OPEN_OCEAN, ModBiomeKeys.TEMPERATE_SHALLOWS, ModBiomeKeys.CORAL_BAY,
                        ModBiomeKeys.REEF_EDGE, ModBiomeKeys.SIREN_SEA, ModBiomeKeys.PHANTOM_WAKE, ModBiomeKeys.SHIPGRAVE_DEPTHS, ModBiomeKeys.DROWNED_TRENCH,
                        ModBiomeKeys.ABYSSAL_RINGS, ModBiomeKeys.PILLAR_SEA, ModBiomeKeys.MAW_DEPTHS),
                GenerationStep.Feature.VEGETAL_DECORATION, PLACED);
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> ctx) {
        StructureWorldAccess w = ctx.getWorld();
        Random r = ctx.getRandom();
        BlockPos o = ctx.getOrigin();
        BlockPos floor = w.getTopPosition(Heightmap.Type.OCEAN_FLOOR_WG, o);
        if (!w.getFluidState(floor).isIn(FluidTags.WATER) || !w.getFluidState(floor.up(3)).isIn(FluidTags.WATER)) return false;
        return place(w, floor, r);
    }

    /** Build a site with its crates on the floor cell {@code floor} (the first water block above the seabed). */
    public static boolean place(StructureWorldAccess w, BlockPos floor, Random r) {
        int crates = 1 + r.nextInt(3);
        boolean any = false;
        for (int i = 0; i < crates; i++) {
            BlockPos p = floor.add(r.nextInt(5) - 2, 0, r.nextInt(5) - 2);
            p = w.getTopPosition(Heightmap.Type.OCEAN_FLOOR_WG, p);
            if (!w.getFluidState(p).isIn(FluidTags.WATER)) continue;
            BlockState s = HomesteadBlocks.SALVAGE_CRATE.getDefaultState().with(SalvageCrateBlock.WATERLOGGED, true)
                    .with(SalvageCrateBlock.FACING, Direction.Type.HORIZONTAL.random(r));
            w.setBlockState(p, s, Block.NOTIFY_LISTENERS);
            LootableContainerBlockEntity.setLootTable(w, r, p, LOOT);
            any = true;
        }
        for (int i = 0; i < 6 + r.nextInt(6); i++) {                                     // strewn wreckage
            BlockPos p = w.getTopPosition(Heightmap.Type.OCEAN_FLOOR_WG, floor.add(r.nextInt(9) - 4, 0, r.nextInt(9) - 4));
            if (!w.getFluidState(p).isIn(FluidTags.WATER) || !w.getBlockState(p).isOf(Blocks.WATER)) continue;
            BlockState s = switch (r.nextInt(4)) {
                case 0 -> ModBlocks.DESTROYED_PLANKS.getDefaultState();
                case 1 -> Blocks.DARK_OAK_SLAB.getDefaultState().with(net.minecraft.block.SlabBlock.WATERLOGGED, true);
                case 2 -> Blocks.CHAIN.getDefaultState().with(net.minecraft.block.ChainBlock.WATERLOGGED, true)
                        .with(net.minecraft.block.ChainBlock.AXIS, r.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z);
                default -> Blocks.DARK_OAK_PLANKS.getDefaultState();
            };
            w.setBlockState(p, s, Block.NOTIFY_LISTENERS);
        }
        if (r.nextInt(3) == 0) {                                                          // a snapped mast lying in the silt
            Direction d = Direction.Type.HORIZONTAL.random(r);
            BlockPos p = floor.offset(d.rotateYClockwise(), 3);
            for (int i = 0; i < 4 + r.nextInt(4); i++) {
                BlockPos q = w.getTopPosition(Heightmap.Type.OCEAN_FLOOR_WG, p.offset(d, i));
                if (w.getBlockState(q).isOf(Blocks.WATER))
                    w.setBlockState(q, Blocks.STRIPPED_SPRUCE_LOG.getDefaultState().with(net.minecraft.block.PillarBlock.AXIS, d.getAxis()), Block.NOTIFY_LISTENERS);
            }
        }
        return any;
    }
}
