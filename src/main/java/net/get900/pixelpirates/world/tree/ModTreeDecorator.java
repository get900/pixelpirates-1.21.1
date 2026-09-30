package net.get900.pixelpirates.world.tree;

import com.mojang.serialization.Codec;
import net.get900.pixelpirates.PixelPirates;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.world.gen.treedecorator.TreeDecorator;
import net.minecraft.world.gen.treedecorator.TreeDecoratorType;

public class ModTreeDecorator {
    public static final TreeDecoratorType<BananaTreeDecorator> BANANA =
            register("banana", BananaTreeDecorator.CODEC);

    public static final TreeDecoratorType<CoconutTreeDecorator> COCONUT =
            register("coconut", CoconutTreeDecorator.CODEC);

    private static <T extends TreeDecorator> TreeDecoratorType<T> register(String name, Codec<T> codec) {
        return Registry.register(
                Registries.TREE_DECORATOR_TYPE,
                new Identifier(PixelPirates.MOD_ID, name),
                new TreeDecoratorType<>(codec)
        );
    }

    public static void init() {
        // Called during PixelPirates.onInitialize() to ensure class loads
    }
}
