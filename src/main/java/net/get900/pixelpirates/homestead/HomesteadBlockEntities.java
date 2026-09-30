package net.get900.pixelpirates.homestead;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.homestead.rum.AgingCaskBlockEntity;
import net.get900.pixelpirates.homestead.rum.RumStillBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class HomesteadBlockEntities {
    private HomesteadBlockEntities() {}

    static <T extends BlockEntity> BlockEntityType<T> be(String name, FabricBlockEntityTypeBuilder.Factory<T> f, Block... blocks) {
        return Registry.register(Registries.BLOCK_ENTITY_TYPE, new Identifier(PixelPirates.MOD_ID, name), FabricBlockEntityTypeBuilder.create(f, blocks).build());
    }

    public static final BlockEntityType<RumStillBlockEntity> RUM_STILL = be("rum_still", RumStillBlockEntity::new, HomesteadBlocks.RUM_STILL);
    public static final BlockEntityType<AgingCaskBlockEntity> AGING_CASK = be("aging_cask", AgingCaskBlockEntity::new, HomesteadBlocks.AGING_CASK);

    public static final BlockEntityType<net.get900.pixelpirates.homestead.furniture.StorageBlockEntity> STORAGE = be("homestead_storage", net.get900.pixelpirates.homestead.furniture.StorageBlockEntity::new,
            HomesteadBlocks.SEA_CHEST, HomesteadBlocks.CARGO_CRATE, HomesteadBlocks.SALVAGE_CRATE);
    public static final BlockEntityType<net.get900.pixelpirates.homestead.furniture.DisplayBlockEntity> DISPLAY = be("treasure_pedestal", net.get900.pixelpirates.homestead.furniture.DisplayBlockEntity::new, HomesteadBlocks.TREASURE_PEDESTAL);
    public static final BlockEntityType<net.get900.pixelpirates.homestead.furniture.RumRackBlock.Entity> RUM_RACK = be("rum_rack", net.get900.pixelpirates.homestead.furniture.RumRackBlock.Entity::new, HomesteadBlocks.RUM_RACK);

    public static final BlockEntityType<net.get900.pixelpirates.homestead.hoard.TreasureHoardBlockEntity> TREASURE_HOARD = be("treasure_hoard",
            net.get900.pixelpirates.homestead.hoard.TreasureHoardBlockEntity::new, HomesteadBlocks.TREASURE_HOARD);

    public static final BlockEntityType<net.get900.pixelpirates.homestead.fishing.TrapBlockEntity> TRAP = be("fishing_trap",
            net.get900.pixelpirates.homestead.fishing.TrapBlockEntity::new, HomesteadBlocks.FISH_TRAP, HomesteadBlocks.LOBSTER_POT);

    public static final BlockEntityType<net.get900.pixelpirates.homestead.hideout.JollyRogerBlockEntity> JOLLY_ROGER = be("jolly_roger",
            net.get900.pixelpirates.homestead.hideout.JollyRogerBlockEntity::new, HomesteadBlocks.JOLLY_ROGER);
    public static final BlockEntityType<net.get900.pixelpirates.homestead.hideout.MooringPostBlockEntity> MOORING_POST = be("mooring_post",
            net.get900.pixelpirates.homestead.hideout.MooringPostBlockEntity::new, HomesteadBlocks.MOORING_POST);

    public static final BlockEntityType<net.get900.pixelpirates.homestead.ship.FigureheadBlock.Entity> FIGUREHEAD = be("figurehead",
            net.get900.pixelpirates.homestead.ship.FigureheadBlock.Entity::new, HomesteadBlocks.MERMAID_FIGUREHEAD, HomesteadBlocks.KRAKEN_FIGUREHEAD,
            HomesteadBlocks.DREAD_SKULL_FIGUREHEAD, HomesteadBlocks.NAVY_EAGLE_FIGUREHEAD);

    public static final BlockEntityType<net.get900.pixelpirates.homestead.roulette.RouletteTableBlockEntity> ROULETTE_TABLE = be("roulette_table",
            net.get900.pixelpirates.homestead.roulette.RouletteTableBlockEntity::new, HomesteadBlocks.ROULETTE_TABLE);

    public static void init() {}
}
