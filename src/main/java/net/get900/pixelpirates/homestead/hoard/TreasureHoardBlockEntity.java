package net.get900.pixelpirates.homestead.hoard;

import net.get900.pixelpirates.homestead.HomesteadBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

public class TreasureHoardBlockEntity extends BlockEntity {
    long coins;

    public TreasureHoardBlockEntity(BlockPos pos, BlockState state) { super(HomesteadBlockEntities.TREASURE_HOARD, pos, state); }

    public long coins() { return coins; }

    void add(long n) {
        coins = Math.max(0, coins + n);
        markDirty();
        if (world != null) {
            int lvl = TreasureHoardBlock.levelFor(coins);
            BlockState s = getCachedState();
            if (s.get(TreasureHoardBlock.LEVEL) != lvl) world.setBlockState(pos, s.with(TreasureHoardBlock.LEVEL, lvl), 3);
        }
    }

    Text status() {
        String mood = coins >= 2000 ? " - the crew is in fine spirits" : coins >= 500 ? " - fortune smiles on this place" : "";
        return Text.literal("Hoard: " + coins + " pirate coins" + mood).formatted(Formatting.GOLD);
    }

    public static void tick(World world, BlockPos pos, BlockState state, TreasureHoardBlockEntity be) {
        if (world.getTime() % 100 != 0 || be.coins < 500) return;
        boolean rich = be.coins >= 2000;
        for (PlayerEntity p : world.getEntitiesByClass(PlayerEntity.class, new Box(pos).expand(12), e -> true)) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.LUCK, 140, rich ? 1 : 0, true, false, true));
            if (rich) p.addStatusEffect(new StatusEffectInstance(StatusEffects.HERO_OF_THE_VILLAGE, 140, 0, true, false, true));
        }
        if (world instanceof ServerWorld sw) sw.spawnParticles(ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 0.3 + state.get(TreasureHoardBlock.LEVEL) * 0.12,
                pos.getZ() + 0.5, 3, 0.35, 0.1, 0.35, 0);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) { nbt.putLong("Coins", coins); }

    @Override
    public void readNbt(NbtCompound nbt) { coins = nbt.getLong("Coins"); }
}
