package net.get900.pixelpirates.item.food;

import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.homestead.HomesteadItems;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * THE GALLEY (2026-09-30 playtest: "at least 30 new foods"). Every dish is ONE {@code dish(...)} line below: food values,
 * container (bowl/bottle, handed back per serving), effects, cures, and its recipe (shapeless, or cooked from one item).
 * Items, item models, recipes (ModRecipeProvider), the creative tab and tooltips all come from this table; textures +
 * names from tools/gen_food_textures.py (same ids). Tiers follow the seas: early dishes use island produce (banana,
 * coconut, kelp, berries, wheat), later ones the homestead crops + fish of each zone and their utility effects.
 */
public final class PirateFoods {
    private PirateFoods() {}

    public record Eff(StatusEffect effect, int seconds, int amp) {}

    public record Spec(String id, int hunger, float sat, boolean snack, boolean always, boolean drink, @Nullable Item container,
                       List<Eff> effects, List<StatusEffect> cures, @Nullable String note, int count,
                       Supplier<List<Object>> ingredients, @Nullable Supplier<Item> cookedFrom) {}

    public static final List<Spec> SPECS = new ArrayList<>();
    public static final List<Item> ALL = new ArrayList<>();

    // ------------------------------------------------------------------ the menu
    static {
        // --- island produce (day one) ---
        dish("banana_fritters", 5, 0.6f).snack().count(2).of(() -> List.of(ModItems.BANANA, Items.WHEAT, Items.EGG, Items.SUGAR));
        dish("banana_pudding", 8, 0.7f).bowl().eff(StatusEffects.REGENERATION, 5, 0)
                .of(() -> List.of(Items.BOWL, ModItems.BANANA, ModItems.BANANA, Items.MILK_BUCKET, Items.SUGAR));
        dish("coconut_macaroon", 3, 0.4f).snack().count(4).of(() -> List.of(ModItems.COCONUT, Items.SUGAR, Items.EGG));
        dish("coconut_cream_pie", 8, 0.6f).eff(StatusEffects.ABSORPTION, 60, 0)
                .of(() -> List.of(ModItems.COCONUT, Items.MILK_BUCKET, Items.EGG, Items.SUGAR, Items.WHEAT));
        dish("seaweed_salad", 5, 0.5f).bowl().eff(StatusEffects.WATER_BREATHING, 30, 0)
                .of(() -> List.of(Items.BOWL, Items.KELP, Items.KELP, Items.SWEET_BERRIES));
        dish("kelp_crisps", 2, 0.3f).snack().count(4).of(() -> List.of(Items.DRIED_KELP, Items.DRIED_KELP, Items.SUGAR));
        dish("plum_duff", 8, 0.7f).count(2).note("A sailor's Sunday pudding")
                .of(() -> List.of(Items.WHEAT, Items.SUGAR, Items.SWEET_BERRIES, Items.SWEET_BERRIES, Items.EGG));
        dish("biscuit_porridge", 7, 0.6f).bowl().eff(StatusEffects.SATURATION, 1, 0)
                .of(() -> List.of(Items.BOWL, ModItems.HARDTACK, Items.MILK_BUCKET, Items.SUGAR));
        dish("tropical_smoothie", 5, 0.6f).drink().always().eff(StatusEffects.SPEED, 30, 0).eff(StatusEffects.JUMP_BOOST, 30, 0)
                .of(() -> List.of(Items.GLASS_BOTTLE, ModItems.BANANA, ModItems.COCONUT, Items.MELON_SLICE));
        dish("hot_cocoa", 3, 0.4f).drink().always().eff(StatusEffects.REGENERATION, 4, 0).cure(StatusEffects.SLOWNESS)
                .of(() -> List.of(Items.GLASS_BOTTLE, Items.COCOA_BEANS, Items.MILK_BUCKET, Items.SUGAR));
        dish("salmagundi", 14, 1.0f).bowl().eff(StatusEffects.REGENERATION, 8, 0).eff(StatusEffects.ABSORPTION, 90, 0)
                .note("The pirate's feast: a bit of everything")
                .of(() -> List.of(Items.BOWL, Items.COOKED_CHICKEN, Items.COOKED_COD, Items.EGG, Items.BEETROOT, Items.CARROT));
        dish("grog_glazed_pork", 10, 0.9f).eff(StatusEffects.STRENGTH, 30, 0)
                .of(() -> List.of(Items.COOKED_PORKCHOP, ModItems.GROG, Items.SUGAR));

        // --- sea catch ---
        dish("shark_jerky", 4, 0.5f).snack().count(3).note("Keeps for months at sea")
                .of(() -> List.of(ModItems.COOKED_SHARK_MEAT, Items.DRIED_KELP));
        dish("shark_fin_soup", 10, 0.8f).bowl().eff(StatusEffects.STRENGTH, 45, 0)
                .of(() -> List.of(Items.BOWL, ModItems.RAW_SHARK_MEAT, Items.KELP, Items.BROWN_MUSHROOM));
        dish("fish_and_chips", 10, 0.8f)
                .of(() -> List.of(fillet(), Items.BAKED_POTATO, Items.BAKED_POTATO, Items.PAPER));
        dish("crab_cakes", 6, 0.7f).count(2)
                .of(() -> List.of(HomesteadItems.CRAB_CLAW, HomesteadItems.CRAB_CLAW, Items.WHEAT, Items.EGG));
        dish("seafood_boil", 12, 0.9f).bowl().eff(StatusEffects.WATER_BREATHING, 90, 0)
                .of(() -> List.of(Items.BOWL, HomesteadItems.CRAB_CLAW, Items.COOKED_COD, Items.POTATO, Items.KELP));
        dish("lobster_thermidor", 12, 1.0f).bowl().eff(StatusEffects.RESISTANCE, 60, 0)
                .of(() -> List.of(Items.BOWL, HomesteadItems.COOKED_LOBSTER, Items.MILK_BUCKET, Items.BROWN_MUSHROOM));
        dish("kraken_ink_pasta", 9, 0.8f).bowl().eff(StatusEffects.NIGHT_VISION, 90, 0)
                .of(() -> List.of(Items.BOWL, ModItems.KRAKEN_INK, Items.WHEAT, Items.EGG));

        // --- homestead crops ---
        dish("grilled_pineapple", 5, 0.6f).cooked(() -> HomesteadItems.PINEAPPLE);
        dish("pineapple_upside_down_cake", 7, 0.6f).count(2)
                .of(() -> List.of(HomesteadItems.PINEAPPLE, Items.SUGAR, Items.EGG, Items.WHEAT));
        dish("pineapple_salsa", 6, 0.6f).bowl().eff(StatusEffects.HASTE, 60, 0)
                .of(() -> List.of(Items.BOWL, HomesteadItems.PINEAPPLE, HomesteadItems.CHILI_PEPPER, HomesteadItems.LIME));
        dish("key_lime_pie", 8, 0.6f).eff(StatusEffects.SPEED, 30, 0)
                .of(() -> List.of(HomesteadItems.LIME, HomesteadItems.LIME, Items.EGG, Items.SUGAR, Items.WHEAT));
        dish("limeade", 3, 0.4f).drink().always().eff(StatusEffects.SPEED, 45, 0)
                .of(() -> List.of(Items.GLASS_BOTTLE, HomesteadItems.LIME, Items.SUGAR));
        dish("scurvy_tonic", 2, 0.3f).drink().always().eff(StatusEffects.REGENERATION, 6, 0)
                .cure(StatusEffects.POISON).cure(StatusEffects.NAUSEA).cure(StatusEffects.HUNGER)
                .note("Limes: the sailor's cure")
                .of(() -> List.of(Items.GLASS_BOTTLE, HomesteadItems.LIME, Items.HONEY_BOTTLE, Items.KELP));
        dish("fire_roasted_chili", 3, 0.3f).snack().eff(StatusEffects.FIRE_RESISTANCE, 20, 0).cooked(() -> HomesteadItems.CHILI_PEPPER);
        dish("chili_con_shark", 10, 0.8f).bowl().eff(StatusEffects.FIRE_RESISTANCE, 60, 0)
                .of(() -> List.of(Items.BOWL, ModItems.COOKED_SHARK_MEAT, HomesteadItems.CHILI_PEPPER, Items.BEETROOT));
        dish("coconut_curry", 10, 0.9f).bowl().eff(StatusEffects.FIRE_RESISTANCE, 90, 0)
                .of(() -> List.of(Items.BOWL, ModItems.COCONUT, HomesteadItems.CHILI_PEPPER, fillet(), Items.POTATO));
        dish("blackened_fillet", 8, 0.8f).eff(StatusEffects.STRENGTH, 20, 0)
                .of(() -> List.of(fillet(), HomesteadItems.CHILI_PEPPER, HomesteadItems.LIME));
        dish("fish_taco", 6, 0.7f).count(2)
                .of(() -> List.of(fillet(), Items.WHEAT, HomesteadItems.LIME, HomesteadItems.CHILI_PEPPER));
        dish("rum_cake", 7, 0.6f).count(2).eff(StatusEffects.ABSORPTION, 60, 1)
                .of(() -> List.of(HomesteadItems.RAW_RUM, Items.WHEAT, Items.SUGAR, Items.EGG));
        dish("molasses_cookie", 2, 0.3f).snack().count(8)
                .of(() -> List.of(HomesteadItems.MOLASSES, Items.WHEAT, Items.WHEAT));

        // --- zone fish: each sea's catch helps with that sea ---
        dish("parrotfish_poke", 8, 0.8f).bowl().eff(StatusEffects.DOLPHINS_GRACE, 60, 0)
                .of(() -> List.of(Items.BOWL, HomesteadItems.PARROTFISH, Items.KELP, HomesteadItems.LIME, HomesteadItems.PINEAPPLE));
        dish("mahi_pineapple_skewer", 9, 0.8f).eff(StatusEffects.SPEED, 40, 0)
                .of(() -> List.of(Items.STICK, HomesteadItems.MAHI_MAHI, HomesteadItems.PINEAPPLE));
        dish("moonfish_sashimi", 6, 0.8f).eff(StatusEffects.NIGHT_VISION, 120, 0)
                .of(() -> List.of(HomesteadItems.MOONFISH, Items.KELP, HomesteadItems.LIME));
        dish("ember_eel_skewer", 8, 0.8f).eff(StatusEffects.FIRE_RESISTANCE, 120, 0).note("Volcanic seas survival food")
                .of(() -> List.of(Items.STICK, HomesteadItems.LAVA_EEL, HomesteadItems.CHILI_PEPPER));
        dish("ghostfin_broth", 7, 0.6f).bowl().eff(StatusEffects.INVISIBILITY, 30, 0)
                .of(() -> List.of(Items.BOWL, HomesteadItems.GHOSTFIN, Items.BROWN_MUSHROOM));
        dish("bone_broth", 6, 0.5f).bowl().eff(StatusEffects.RESISTANCE, 20, 0).cure(StatusEffects.WITHER).note("Cursed seas remedy")
                .of(() -> List.of(Items.BOWL, HomesteadItems.BONEFISH, Items.BONE));
        dish("anglerfry_chowder", 10, 0.8f).bowl().eff(StatusEffects.CONDUIT_POWER, 60, 0)
                .of(() -> List.of(Items.BOWL, HomesteadItems.ANGLERFRY, Items.MILK_BUCKET, Items.POTATO));
        dish("voidfin_roe", 4, 1.0f).container(() -> Items.GLASS_BOTTLE).eff(StatusEffects.SLOW_FALLING, 60, 0)
                .of(() -> List.of(Items.GLASS_BOTTLE, HomesteadItems.VOIDFIN));

        // --- phase materials (Materials & Gear Ladder, 2026-10-01) ---
        dish("cooked_lava_crab_claw", 5, 0.6f).eff(StatusEffects.FIRE_RESISTANCE, 30, 0).cooked(() -> ModItems.RAW_LAVA_CRAB_CLAW);
        dish("cinder_chili_sauce", 2, 0.3f).drink().always().eff(StatusEffects.STRENGTH, 60, 0).eff(StatusEffects.FIRE_RESISTANCE, 60, 0)
                .of(() -> List.of(Items.GLASS_BOTTLE, HomesteadItems.CHILI_PEPPER, ModItems.BRIMSTONE, ModItems.VOLCANIC_EMBER));
        dish("brimstone_smoked_fish", 8, 0.8f).eff(StatusEffects.FIRE_RESISTANCE, 90, 0).note("Volcanic seas survival food")
                .of(() -> List.of(Ingredient.ofItems(HomesteadItems.EMBERFIN, HomesteadItems.LAVA_EEL), ModItems.BRIMSTONE));
        dish("ghostly_brew", 2, 0.3f).drink().always().eff(StatusEffects.INVISIBILITY, 30, 0).eff(StatusEffects.NIGHT_VISION, 90, 0)
                .of(() -> List.of(Items.GLASS_BOTTLE, ModItems.ECTOPLASM, HomesteadItems.GHOSTFIN));
        dish("bone_marrow_broth", 7, 0.7f).bowl().eff(StatusEffects.RESISTANCE, 30, 0).cure(StatusEffects.WITHER)
                .of(() -> List.of(Items.BOWL, ModItems.CURSED_BONE, Items.BONE, Items.BROWN_MUSHROOM));
        dish("luminous_draught", 2, 0.3f).drink().always().eff(StatusEffects.NIGHT_VISION, 180, 0).eff(StatusEffects.WATER_BREATHING, 180, 0)
                .of(() -> List.of(Items.GLASS_BOTTLE, ModItems.LUMINOUS_ICHOR, Items.KELP));
        dish("whales_bounty", 14, 1.2f).eff(StatusEffects.SATURATION, 2, 0).eff(StatusEffects.REGENERATION, 10, 0)
                .note("A gift from a well-fed Coral Whale").dropOnly();

        // --- boss signature dishes: dropped by their boss (and in its hoard), never cooked ---
        dish("rackhams_reserve", 4, 0.5f).drink().always().eff(StatusEffects.STRENGTH, 180, 0).eff(StatusEffects.RESISTANCE, 60, 0)
                .eff(StatusEffects.NAUSEA, 6, 0).note("Captain Rackham's private stock").dropOnly();
        dish("serpent_steak", 12, 1.0f).eff(StatusEffects.WATER_BREATHING, 240, 0).eff(StatusEffects.DOLPHINS_GRACE, 240, 0)
                .note("Cut from the Sea Serpent").dropOnly();
        dish("molten_core_chili", 10, 0.9f).bowl().eff(StatusEffects.FIRE_RESISTANCE, 300, 0).eff(StatusEffects.STRENGTH, 120, 0)
                .note("Still glowing - the Molten Warlord's heart-fire").dropOnly();
        dish("phantom_hardtack", 8, 0.8f).eff(StatusEffects.NIGHT_VISION, 300, 0).eff(StatusEffects.SLOW_FALLING, 60, 0)
                .note("From the Flying Dutchman's hold").dropOnly();
        dish("royal_tide_feast", 16, 1.2f).eff(StatusEffects.REGENERATION, 30, 1).eff(StatusEffects.ABSORPTION, 120, 1)
                .note("The Abyssal King's table").dropOnly();
        dish("bloodfin_fillet", 12, 1.0f).eff(StatusEffects.STRENGTH, 120, 1).eff(StatusEffects.HASTE, 120, 0)
                .note("The Bloodfin's own flesh").dropOnly();
        dish("kraken_platter", 14, 1.1f).eff(StatusEffects.RESISTANCE, 180, 0).eff(StatusEffects.WATER_BREATHING, 180, 0)
                .note("A feast of Kraken").dropOnly();
        dish("last_meal", 14, 1.1f).eff(StatusEffects.HEALTH_BOOST, 300, 1).note("The Chained Revenant never ate it").dropOnly();
        dish("heart_tartare", 10, 1.0f).eff(StatusEffects.ABSORPTION, 120, 3).eff(StatusEffects.REGENERATION, 60, 0)
                .note("Cut from the Abyssal Heart").dropOnly();
        dish("leviathan_steak", 20, 1.4f).eff(StatusEffects.HEALTH_BOOST, 300, 1).eff(StatusEffects.STRENGTH, 300, 0)
                .eff(StatusEffects.RESISTANCE, 300, 0).note("There was only one").dropOnly();
    }

    /** Any cooked fish counts where a recipe asks for "fish". */
    private static Ingredient fillet() {
        return Ingredient.ofItems(HomesteadItems.COOKED_FISH_FILLET, Items.COOKED_COD, Items.COOKED_SALMON);
    }

    // ------------------------------------------------------------------ builder
    private static B dish(String id, int hunger, float sat) { return new B(id, hunger, sat); }

    private static final class B {
        final String id; final int hunger; final float sat;
        boolean snack, always, drink; Supplier<Item> container; String note; int count = 1;
        final List<Eff> effects = new ArrayList<>(); final List<StatusEffect> cures = new ArrayList<>();

        B(String id, int hunger, float sat) { this.id = id; this.hunger = hunger; this.sat = sat; }

        B snack() { snack = true; return this; }
        B always() { always = true; return this; }
        B bowl() { container = () -> Items.BOWL; return this; }
        B drink() { drink = true; container = () -> Items.GLASS_BOTTLE; return this; }
        B container(Supplier<Item> c) { container = c; return this; }
        B eff(StatusEffect e, int seconds, int amp) { effects.add(new Eff(e, seconds, amp)); return this; }
        B cure(StatusEffect e) { cures.add(e); return this; }
        B note(String n) { note = n; return this; }
        B count(int n) { count = n; return this; }
        void of(Supplier<List<Object>> ingredients) { done(ingredients, null); }
        void cooked(Supplier<Item> from) { done(null, from); }
        /** No recipe: only dropped (boss signature dishes, the coral whale's bounty). */
        void dropOnly() { done(null, null); }

        void done(Supplier<List<Object>> ingredients, Supplier<Item> from) {
            SPECS.add(new Spec(id, hunger, sat, snack, always, drink, container == null ? null : container.get(),
                    List.copyOf(effects), List.copyOf(cures), note, count, ingredients, from));
        }
    }

    // ------------------------------------------------------------------ registration (PixelPirates, after Homestead.init)
    public static void register() {
        for (Spec s : SPECS) {
            FoodComponent.Builder f = new FoodComponent.Builder().hunger(s.hunger()).saturationModifier(s.sat());
            if (s.snack()) f.snack();
            if (s.always()) f.alwaysEdible();
            for (Eff e : s.effects()) f.statusEffect(new StatusEffectInstance(e.effect(), e.seconds() * 20, e.amp()), 1.0f);
            Item.Settings settings = new Item.Settings().food(f.build()).maxCount(s.container() == null ? 64 : 16);
            Item item = Registry.register(Registries.ITEM, new Identifier(PixelPirates.MOD_ID, s.id()), new PirateFoodItem(settings, s));
            ALL.add(item);
        }
        PixelPirates.LOGGER.info("Galley: " + ALL.size() + " dishes");
    }

    /** The recipe ingredients as Ingredients (datagen). */
    public static List<Ingredient> ingredients(Spec s) {
        List<Ingredient> out = new ArrayList<>();
        for (Object o : s.ingredients().get())
            out.add(o instanceof Ingredient i ? i : Ingredient.ofItems((ItemConvertible) o));
        return out;
    }

    public static Item item(String id) { return Registries.ITEM.get(new Identifier(PixelPirates.MOD_ID, id)); }
}
