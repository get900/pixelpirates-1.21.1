package net.get900.pixelpirates.enchantment;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import net.get900.pixelpirates.PixelPirates;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.function.LootFunction;
import net.minecraft.loot.function.LootFunctionType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.JsonSerializer;
import net.minecraft.util.math.random.Random;

/**
 * Loot function "pixelpirates:frozen_seeker" (2026-10-01): headgear found in zone 3 and 4 chests may carry FROZEN SEEKER,
 * the one enchantment left in the mod (it shields you from the abyss's madness and blizzard fog). Level 1 rolls 15%,
 * every higher level half as often as the one below, up to X (~30% for any level; level X about 1 in 3400).
 */
public class FrozenSeekerLootFunction implements LootFunction {
    public static LootFunctionType TYPE;

    public static void register() {
        TYPE = Registry.register(Registries.LOOT_FUNCTION_TYPE, PixelPirates.id("frozen_seeker"), new LootFunctionType(new Serializer()));
    }

    /** 0 = none, else 1..10. */
    public static int roll(Random random) {
        float x = random.nextFloat(), p = 0.15f, acc = 0;
        for (int level = 1; level <= 10; level++, p *= 0.5f) {
            acc += p;
            if (x < acc) return level;
        }
        return 0;
    }

    @Override
    public LootFunctionType getType() { return TYPE; }

    @Override
    public ItemStack apply(ItemStack stack, LootContext context) {
        int level = roll(context.getRandom());
        if (level > 0) stack.addEnchantment(ModEnchantments.FROZEN_SEEKER, level);
        return stack;
    }

    public static class Serializer implements JsonSerializer<FrozenSeekerLootFunction> {
        @Override
        public void toJson(JsonObject json, FrozenSeekerLootFunction fn, JsonSerializationContext context) {}

        @Override
        public FrozenSeekerLootFunction fromJson(JsonObject json, JsonDeserializationContext context) { return new FrozenSeekerLootFunction(); }
    }
}
