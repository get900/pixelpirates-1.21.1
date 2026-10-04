package net.get900.pixelpirates.world.tree;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.get900.pixelpirates.block.ModBlocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.gen.treedecorator.TreeDecorator;
import net.minecraft.world.gen.treedecorator.TreeDecoratorType;

public class BananaTreeDecorator extends TreeDecorator {
    public static final Codec<BananaTreeDecorator> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Codec.floatRange(0.0f, 1.0f).fieldOf("chance").forGetter(d -> d.chance)
            ).apply(instance, BananaTreeDecorator::new)
    );

    private final float chance;

    public BananaTreeDecorator(float chance) {
        this.chance = chance;
    }

    @Override
    protected TreeDecoratorType<?> getType() {
        return ModTreeDecorator.BANANA;
    }

    /** Fruit only hangs from the UNDERSIDE of the canopy (2026-10-01 user note): each leaf with open air below it
     *  is a candidate, and `chance` is per candidate. It used to hang off the trunk at any height. */
    @Override
    public void generate(Generator generator) {
        Random random = generator.getRandom();
        for (BlockPos leafPos : generator.getLeavesPositions()) {
            BlockPos below = leafPos.down();
            if (generator.isAir(below) && generator.isAir(below.down()) && random.nextFloat() < this.chance) {
                generator.replace(below, ModBlocks.BANANA_BLOCK.getDefaultState());
            }
        }
    }
}

