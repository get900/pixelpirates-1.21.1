package net.get900.pixelpirates.homestead.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.homestead.HomesteadItems;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.data.server.recipe.RecipeJsonProvider;
import net.minecraft.data.server.recipe.ShapedRecipeJsonBuilder;
import net.minecraft.data.server.recipe.ShapelessRecipeJsonBuilder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.book.RecipeCategory;
import net.minecraft.util.Identifier;

import java.util.function.Consumer;

/** Recipes for the homestead set. */
public class HomesteadRecipeProvider extends FabricRecipeProvider {
    public HomesteadRecipeProvider(FabricDataOutput output) { super(output); }

    @Override
    public String getName() { return "Homestead Recipes"; }

    static Identifier id(String name) { return new Identifier(PixelPirates.MOD_ID, "homestead/" + name); }

    ShapelessRecipeJsonBuilder shapeless(RecipeCategory cat, ItemConvertible out, int count, ItemConvertible... in) {
        ShapelessRecipeJsonBuilder b = ShapelessRecipeJsonBuilder.create(cat, out, count);
        for (ItemConvertible i : in) b.input(i);
        return b.criterion(hasItem(in[0]), conditionsFromItem(in[0]));
    }

    @Override
    public void generate(Consumer<RecipeJsonProvider> ex) {
        // ---------------- #11 the galley
        shapeless(RecipeCategory.FOOD, HomesteadItems.FRUIT_SALAD, 1, Items.BOWL, HomesteadItems.PINEAPPLE, ModItems.BANANA, ModItems.COCONUT)
                .offerTo(ex, id("tropical_fruit_salad"));
        ShapelessRecipeJsonBuilder.create(RecipeCategory.FOOD, HomesteadItems.CEVICHE)
                .input(Items.BOWL).input(Ingredient.ofItems(Items.COD, Items.SALMON, Items.TROPICAL_FISH)).input(HomesteadItems.LIME).input(HomesteadItems.CHILI_PEPPER)
                .criterion(hasItem(HomesteadItems.LIME), conditionsFromItem(HomesteadItems.LIME)).offerTo(ex, id("ceviche"));
        ShapelessRecipeJsonBuilder.create(RecipeCategory.FOOD, HomesteadItems.SPICY_CHOWDER)
                .input(Items.BOWL).input(Ingredient.ofItems(Items.COD, Items.SALMON, Items.COOKED_COD, Items.COOKED_SALMON))
                .input(Items.POTATO).input(HomesteadItems.CHILI_PEPPER).input(Items.MILK_BUCKET)
                .criterion(hasItem(HomesteadItems.CHILI_PEPPER), conditionsFromItem(HomesteadItems.CHILI_PEPPER)).offerTo(ex, id("spicy_chowder"));
        shapeless(RecipeCategory.FOOD, HomesteadItems.CHOCOLATE_DOUBLOON, 2, Items.COCOA_BEANS, Items.SUGAR, Items.GOLD_NUGGET)
                .offerTo(ex, id("chocolate_doubloon"));
        shapeless(RecipeCategory.FOOD, HomesteadItems.PINEAPPLE_GROG, 1, HomesteadItems.PINEAPPLE, ModItems.GROG, Items.SUGAR)
                .offerTo(ex, id("pineapple_grog"));
        HomesteadRecipes.more(this, ex);
    }

    // helpers reachable from HomesteadRecipes
    public static net.minecraft.advancement.criterion.InventoryChangedCriterion.Conditions has(ItemConvertible i) { return conditionsFromItem(i); }

    public static String hasName(ItemConvertible i) { return hasItem(i); }
}
