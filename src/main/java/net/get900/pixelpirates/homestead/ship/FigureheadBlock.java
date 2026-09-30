package net.get900.pixelpirates.homestead.ship;

import net.get900.pixelpirates.homestead.HomesteadBlockEntities;
import net.get900.pixelpirates.homestead.furniture.FurnitureBlock;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.primitives.AABBdc;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;

import java.util.List;

/**
 * FIGUREHEADS (#25): carved bow ornaments. Mounted on an assembled ship they bless everyone aboard (every 2 s, 6 s
 * effects; nothing when placed on land):
 *   MERMAID     - Dolphin's Grace + Water Breathing (a man overboard swims home);
 *   KRAKEN      - Strength I (boarding actions);
 *   DREAD SKULL - Resistance I;
 *   NAVY EAGLE  - Speed I + Haste I (working the deck).
 */
public class FigureheadBlock extends FurnitureBlock implements BlockEntityProvider {
    public enum Kind { MERMAID, KRAKEN, DREAD_SKULL, NAVY_EAGLE }

    public final Kind kind;

    public FigureheadBlock(Settings s, Kind kind) {
        super(s, true, new double[]{3, 0, 0, 13, 16, 12});
        this.kind = kind;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new Entity(pos, state); }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient || type != HomesteadBlockEntities.FIGUREHEAD) return null;
        return (w, p, s, be) -> bless((ServerWorld) w, p);
    }

    private void bless(ServerWorld w, BlockPos pos) {
        if (w.getTime() % 40 != (pos.asLong() & 31) % 40) return;
        Ship ship;
        try {
            ship = ValkyrienSkies.getShipManagingBlock(w, pos.getX(), pos.getY(), pos.getZ());
        } catch (Exception e) {
            return;
        }
        if (ship == null) return;
        AABBdc a = ship.getWorldAABB();
        Box box = new Box(a.minX(), a.minY(), a.minZ(), a.maxX(), a.maxY(), a.maxZ()).expand(2, 4, 2);
        List<PlayerEntity> aboard = w.getEntitiesByClass(PlayerEntity.class, box, p -> p.isAlive() && !p.isSpectator());
        for (PlayerEntity p : aboard) {
            switch (kind) {
                case MERMAID -> { give(p, new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, 120, 0, true, false, true));
                                  give(p, new StatusEffectInstance(StatusEffects.WATER_BREATHING, 120, 0, true, false, true)); }
                case KRAKEN -> give(p, new StatusEffectInstance(StatusEffects.STRENGTH, 120, 0, true, false, true));
                case DREAD_SKULL -> give(p, new StatusEffectInstance(StatusEffects.RESISTANCE, 120, 0, true, false, true));
                case NAVY_EAGLE -> { give(p, new StatusEffectInstance(StatusEffects.SPEED, 120, 0, true, false, true));
                                     give(p, new StatusEffectInstance(StatusEffects.HASTE, 120, 0, true, false, true)); }
            }
        }
    }

    private static void give(PlayerEntity p, StatusEffectInstance e) { p.addStatusEffect(e); }

    /** Marker block entity so the figurehead ticks. */
    public static class Entity extends BlockEntity {
        public Entity(BlockPos pos, BlockState state) { super(HomesteadBlockEntities.FIGUREHEAD, pos, state); }
    }
}
