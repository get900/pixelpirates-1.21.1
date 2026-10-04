package net.get900.pixelpirates.util;

import net.get900.pixelpirates.PixelPirates;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public class ModTags {
    public static class Blocks {
        public static final TagKey<Block> NEEDS_PIRATE_TOOL = createTag("needs_pirate_tool");
        public static final TagKey<Block> INCORRECT_FOR_PIRATE_TOOL = createTag("incorrect_for_pirate_tool");
        public static final TagKey<Block> CANNON_IMMUNE = createTag("cannon_immune");
        // Blocks in this tag are never broken off during progressive ship structural damage
        public static final TagKey<Block> SHIP_STRUCTURAL = createTag("ship_structural");
        // Natural seabed a Keelbreaker hull grinds through (world/KeelBreaker): soft = level I, rock = level II
        public static final TagKey<Block> KEEL_SOFT = createTag("keel_soft");
        public static final TagKey<Block> KEEL_ROCK = createTag("keel_rock");

        private static TagKey<Block> createTag(String name) {
            return TagKey.of(RegistryKeys.BLOCK, new Identifier(PixelPirates.MOD_ID, name));
        }
    }

    public static class Items {
            public static final TagKey<Item> SHOOTABLE_ITEMS = createTag("shootable_items");

            private static TagKey<Item> createTag(String name) {
            return TagKey.of(RegistryKeys.ITEM, new Identifier(PixelPirates.MOD_ID, name));
        }
    }
}
