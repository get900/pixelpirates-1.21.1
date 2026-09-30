package net.get900.pixelpirates.world.tree;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.get900.pixelpirates.block.ModBlocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
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

    @Override
    public void generate(Generator generator) {
        Random random = generator.getRandom();
        for (BlockPos logPos : generator.getLogPositions()) {
            if (random.nextFloat() < this.chance) {
                Direction direction = Direction.Type.HORIZONTAL.random(random);
                BlockPos bananaPos = logPos.down().offset(direction);
                if (generator.isAir(bananaPos)) {
                    generator.replace(bananaPos, ModBlocks.BANANA_BLOCK.getDefaultState());
                }
            }
        }
    }
}

