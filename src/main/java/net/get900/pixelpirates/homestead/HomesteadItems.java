package net.get900.pixelpirates.homestead;

import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.homestead.item.DrinkItem;
import net.minecraft.item.AliasedBlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.StewItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Homestead items, in creative-tab order ({@link #ALL}). */
public final class HomesteadItems {
    private HomesteadItems() {}

    public static final List<Item> ALL = new ArrayList<>();
    /** Items drawn as flat sprites (item/generated model); others (3D, handheld...) set up their own model. */
    public static final List<Item> FLAT = new ArrayList<>();
    public static final List<Item> HANDHELD = new ArrayList<>();

    static <T extends Item> T item(String name, T item) {
        Registry.register(Registries.ITEM, new Identifier(PixelPirates.MOD_ID, name), item);
        ALL.add(item);
        FLAT.add(item);
        return item;
    }

    // SEALED STRONGBOXES (2026-10-01): cracked open at a Treasure Hoard - a spinning reel of prizes (hoard/Strongboxes)
    public static final Item COMMON_STRONGBOX = item("common_strongbox", new net.get900.pixelpirates.homestead.hoard.StrongboxItem(new Item.Settings().maxCount(16), 0));
    public static final Item RARE_STRONGBOX = item("rare_strongbox", new net.get900.pixelpirates.homestead.hoard.StrongboxItem(new Item.Settings().maxCount(16).rarity(net.minecraft.util.Rarity.UNCOMMON), 1));
    // PARROT TYPES (2026-10-03): a crate holding one parrot of a set type - treasure loot (homestead/parrot/ParrotCrateItem)
    public static final Item PARROT_CRATE = item("parrot_crate", new net.get900.pixelpirates.homestead.parrot.ParrotCrateItem(new Item.Settings().maxCount(1).rarity(net.minecraft.util.Rarity.RARE)));
    public static final Item LEGENDARY_STRONGBOX = item("legendary_strongbox", new net.get900.pixelpirates.homestead.hoard.StrongboxItem(new Item.Settings().maxCount(16).rarity(net.minecraft.util.Rarity.RARE), 2));

    static <T extends Item> T tool(String name, T item) {
        Registry.register(Registries.ITEM, new Identifier(PixelPirates.MOD_ID, name), item);
        ALL.add(item);
        HANDHELD.add(item);
        return item;
    }

    static <T extends Item> T special(String name, T item) {
        Registry.register(Registries.ITEM, new Identifier(PixelPirates.MOD_ID, name), item);
        ALL.add(item);
        return item;
    }

    // ================================================================== #11 TROPICAL CROPS + the galley
    public static final Item PINEAPPLE_CROWN = item("pineapple_crown", new AliasedBlockItem(HomesteadBlocks.PINEAPPLE_CROP, new Item.Settings()));
    public static final Item LIME_SEEDS = item("lime_seeds", new AliasedBlockItem(HomesteadBlocks.LIME_CROP, new Item.Settings()));
    public static final Item CHILI_SEEDS = item("chili_seeds", new AliasedBlockItem(HomesteadBlocks.CHILI_CROP, new Item.Settings()));
    public static final Item PINEAPPLE = item("pineapple", new Item(new Item.Settings().food(HomesteadFoods.PINEAPPLE)));
    public static final Item LIME = item("lime", new Item(new Item.Settings().food(HomesteadFoods.LIME)));
    public static final Item CHILI_PEPPER = item("chili_pepper", new Item(new Item.Settings().food(HomesteadFoods.CHILI_PEPPER)));
    public static final Item FRUIT_SALAD = item("tropical_fruit_salad", new net.get900.pixelpirates.item.food.BowlFoodItem(new Item.Settings().food(HomesteadFoods.FRUIT_SALAD).maxCount(16)));
    public static final Item CEVICHE = item("ceviche", new net.get900.pixelpirates.item.food.BowlFoodItem(new Item.Settings().food(HomesteadFoods.CEVICHE).maxCount(16)));
    public static final Item SPICY_CHOWDER = item("spicy_chowder", new net.get900.pixelpirates.item.food.BowlFoodItem(new Item.Settings().food(HomesteadFoods.SPICY_CHOWDER).maxCount(16)));
    public static final Item CHOCOLATE_DOUBLOON = item("chocolate_doubloon", new Item(new Item.Settings().food(HomesteadFoods.CHOCOLATE_DOUBLOON)));
    public static final Item PINEAPPLE_GROG = item("pineapple_grog", new DrinkItem(new Item.Settings().food(HomesteadFoods.PINEAPPLE_GROG).maxCount(16)
            .recipeRemainder(Items.GLASS_BOTTLE)));

    // ================================================================== #12 RUM
    public static final Item MOLASSES = item("molasses", new Item(new Item.Settings().maxCount(16).recipeRemainder(Items.GLASS_BOTTLE)));
    public static final Item RAW_RUM = item("raw_rum", new DrinkItem(new Item.Settings().maxCount(16),
            e -> net.get900.pixelpirates.homestead.rum.Rum.drink(e, 0)));
    public static final Item AGED_RUM = item("aged_rum", new DrinkItem(new Item.Settings().maxCount(16).rarity(net.minecraft.util.Rarity.UNCOMMON),
            e -> net.get900.pixelpirates.homestead.rum.Rum.drink(e, 1)));
    public static final Item VINTAGE_RUM = item("vintage_rum", new DrinkItem(new Item.Settings().maxCount(16).rarity(net.minecraft.util.Rarity.RARE),
            e -> net.get900.pixelpirates.homestead.rum.Rum.drink(e, 2)));

    // ================================================================== THE GROG BARREL'S DRINKS (tavern/TavernDrinks)
    public static final Item ALE = item("tankard_of_ale", new net.get900.pixelpirates.homestead.tavern.TavernDrinks.TavernDrinkItem(new Item.Settings().maxCount(16),
            net.get900.pixelpirates.homestead.tavern.TavernDrinks.Kind.ALE));
    public static final Item HONEY_MEAD = item("honey_mead", new net.get900.pixelpirates.homestead.tavern.TavernDrinks.TavernDrinkItem(new Item.Settings().maxCount(16),
            net.get900.pixelpirates.homestead.tavern.TavernDrinks.Kind.HONEY_MEAD));
    public static final Item SPICED_WINE = item("spiced_wine", new net.get900.pixelpirates.homestead.tavern.TavernDrinks.TavernDrinkItem(new Item.Settings().maxCount(16),
            net.get900.pixelpirates.homestead.tavern.TavernDrinks.Kind.SPICED_WINE));
    public static final Item BILGE_WHISKEY = item("bilge_whiskey", new net.get900.pixelpirates.homestead.tavern.TavernDrinks.TavernDrinkItem(new Item.Settings().maxCount(16),
            net.get900.pixelpirates.homestead.tavern.TavernDrinks.Kind.BILGE_WHISKEY));
    public static final Item KRAKENS_KISS = item("krakens_kiss", new net.get900.pixelpirates.homestead.tavern.TavernDrinks.TavernDrinkItem(new Item.Settings().maxCount(16)
            .rarity(net.minecraft.util.Rarity.UNCOMMON), net.get900.pixelpirates.homestead.tavern.TavernDrinks.Kind.KRAKENS_KISS));

    // ================================================================== THE FORGE (homestead/forge)
    public static final Item SMITHS_HAMMER = tool("smiths_hammer", new net.get900.pixelpirates.homestead.forge.SmithsHammerItem(new Item.Settings().maxDamage(400)));

    // ================================================================== #21 GUNS
    public static final Item PAPER_CARTRIDGE = item("paper_cartridge", new Item(new Item.Settings()));
    public static final Item SCATTERSHOT = item("scattershot", new Item(new Item.Settings()));
    public static final Item FLINTLOCK_PISTOL = special("flintlock_pistol", new net.get900.pixelpirates.homestead.gun.GunItem(new Item.Settings().maxDamage(250),
            net.get900.pixelpirates.homestead.gun.GunItem.Kind.PISTOL, () -> HomesteadItems.PAPER_CARTRIDGE));
    public static final Item BLUNDERBUSS = special("blunderbuss", new net.get900.pixelpirates.homestead.gun.GunItem(new Item.Settings().maxDamage(200),
            net.get900.pixelpirates.homestead.gun.GunItem.Kind.BLUNDERBUSS, () -> HomesteadItems.SCATTERSHOT));

    // ================================================================== #24 REGION TOOLS
    static Item regionTool(String name, Item item, net.get900.pixelpirates.homestead.tool.RegionTools.Kind kind) {
        net.get900.pixelpirates.homestead.tool.RegionTools.KIND.put(item, kind);
        return tool(name, item);
    }

    public static final Item EMBER_PICKAXE = regionTool("ember_pickaxe", new net.get900.pixelpirates.homestead.tool.RegionTools.Pickaxe(net.get900.pixelpirates.item.ModToolMaterials.VOLCANIC, new Item.Settings().fireproof()), net.get900.pixelpirates.homestead.tool.RegionTools.Kind.EMBER);
    public static final Item EMBER_AXE = regionTool("ember_axe", new net.get900.pixelpirates.homestead.tool.RegionTools.Axe(net.get900.pixelpirates.item.ModToolMaterials.VOLCANIC, new Item.Settings().fireproof()), net.get900.pixelpirates.homestead.tool.RegionTools.Kind.EMBER);
    public static final Item EMBER_SHOVEL = regionTool("ember_shovel", new net.get900.pixelpirates.homestead.tool.RegionTools.Shovel(net.get900.pixelpirates.item.ModToolMaterials.VOLCANIC, new Item.Settings().fireproof()), net.get900.pixelpirates.homestead.tool.RegionTools.Kind.EMBER);
    public static final Item EMBER_HOE = regionTool("ember_hoe", new net.get900.pixelpirates.homestead.tool.RegionTools.Hoe(net.get900.pixelpirates.item.ModToolMaterials.VOLCANIC, new Item.Settings().fireproof()), net.get900.pixelpirates.homestead.tool.RegionTools.Kind.EMBER);
    public static final Item KRAKEN_PICKAXE = regionTool("kraken_pickaxe", new net.get900.pixelpirates.homestead.tool.RegionTools.Pickaxe(net.get900.pixelpirates.item.ModToolMaterials.ABYSSAL, new Item.Settings()), net.get900.pixelpirates.homestead.tool.RegionTools.Kind.KRAKEN);
    public static final Item KRAKEN_AXE = regionTool("kraken_axe", new net.get900.pixelpirates.homestead.tool.RegionTools.Axe(net.get900.pixelpirates.item.ModToolMaterials.ABYSSAL, new Item.Settings()), net.get900.pixelpirates.homestead.tool.RegionTools.Kind.KRAKEN);
    public static final Item KRAKEN_SHOVEL = regionTool("kraken_shovel", new net.get900.pixelpirates.homestead.tool.RegionTools.Shovel(net.get900.pixelpirates.item.ModToolMaterials.ABYSSAL, new Item.Settings()), net.get900.pixelpirates.homestead.tool.RegionTools.Kind.KRAKEN);
    public static final Item KRAKEN_HOE = regionTool("kraken_hoe", new net.get900.pixelpirates.homestead.tool.RegionTools.Hoe(net.get900.pixelpirates.item.ModToolMaterials.ABYSSAL, new Item.Settings()), net.get900.pixelpirates.homestead.tool.RegionTools.Kind.KRAKEN);
    public static final Item BONE_PICKAXE = regionTool("bone_pickaxe", new net.get900.pixelpirates.homestead.tool.RegionTools.Pickaxe(net.get900.pixelpirates.item.ModToolMaterials.CURSED, new Item.Settings()), net.get900.pixelpirates.homestead.tool.RegionTools.Kind.BONE);
    public static final Item BONE_AXE = regionTool("bone_axe", new net.get900.pixelpirates.homestead.tool.RegionTools.Axe(net.get900.pixelpirates.item.ModToolMaterials.CURSED, new Item.Settings()), net.get900.pixelpirates.homestead.tool.RegionTools.Kind.BONE);
    public static final Item BONE_SHOVEL = regionTool("bone_shovel", new net.get900.pixelpirates.homestead.tool.RegionTools.Shovel(net.get900.pixelpirates.item.ModToolMaterials.CURSED, new Item.Settings()), net.get900.pixelpirates.homestead.tool.RegionTools.Kind.BONE);
    public static final Item BONE_HOE = regionTool("bone_hoe", new net.get900.pixelpirates.homestead.tool.RegionTools.Hoe(net.get900.pixelpirates.item.ModToolMaterials.CURSED, new Item.Settings()), net.get900.pixelpirates.homestead.tool.RegionTools.Kind.BONE);

    // ================================================================== #13 FISHING
    public static final List<Item> FISH = new ArrayList<>();
    static Item fish(String name, net.minecraft.item.FoodComponent food) { Item i = item(name, new Item(new Item.Settings().food(food))); FISH.add(i); return i; }
    public static final Item PARROTFISH = fish("parrotfish", HomesteadFoods.PARROTFISH);
    public static final Item RED_SNAPPER = fish("red_snapper", HomesteadFoods.RED_SNAPPER);
    public static final Item MAHI_MAHI = fish("mahi_mahi", HomesteadFoods.MAHI_MAHI);
    public static final Item LIONFISH = fish("lionfish", HomesteadFoods.LIONFISH);
    public static final Item MOONFISH = fish("moonfish", HomesteadFoods.MOONFISH);
    public static final Item EMBERFIN = fish("emberfin", HomesteadFoods.EMBERFIN);
    public static final Item LAVA_EEL = fish("lava_eel", HomesteadFoods.LAVA_EEL);
    public static final Item GHOSTFIN = fish("ghostfin", HomesteadFoods.GHOSTFIN);
    public static final Item BONEFISH = fish("bonefish", HomesteadFoods.BONEFISH);
    public static final Item ANGLERFRY = fish("anglerfry", HomesteadFoods.ANGLERFRY);
    public static final Item VOIDFIN = fish("voidfin", HomesteadFoods.VOIDFIN);
    public static final Item COOKED_FISH_FILLET = item("cooked_fish_fillet", new Item(new Item.Settings().food(HomesteadFoods.COOKED_FILLET)));
    public static final Item LOBSTER = item("lobster", new Item(new Item.Settings().food(HomesteadFoods.LOBSTER)));
    public static final Item COOKED_LOBSTER = item("cooked_lobster", new Item(new Item.Settings().food(HomesteadFoods.COOKED_LOBSTER)));
    public static final Item CRAB_CLAW = item("crab_claw", new Item(new Item.Settings().food(HomesteadFoods.CRAB_CLAW)));
    public static final Item SALVAGE_HOOK = special("salvage_hook", new net.get900.pixelpirates.homestead.fishing.SalvageHookItem(new Item.Settings().maxDamage(96)));

    // ================================================================== #17 SPYGLASS + #18 COMPASS OF DESIRE
    public static final Item CAPTAINS_SPYGLASS = item("captains_spyglass", new net.get900.pixelpirates.homestead.nav.CaptainsSpyglassItem(new Item.Settings().maxCount(1)));
    public static final Item COMPASS_OF_DESIRE = special("compass_of_desire", new net.get900.pixelpirates.homestead.nav.CompassOfDesireItem(new Item.Settings().maxCount(1)));

    // ================================================================== #22 GRAPPLING HOOK
    public static final Item GRAPPLING_HOOK = item("grappling_hook", new net.get900.pixelpirates.homestead.grapple.GrapplingHookItem(new Item.Settings().maxDamage(128)));

    // ================================================================== #25 SPECIAL SHOT
    public static final Item CHAIN_SHOT = item("chain_shot", new Item(new Item.Settings().maxCount(16)));
    public static final Item GRAPE_SHOT = item("grape_shot", new Item(new Item.Settings().maxCount(16)));

    // #20 the Captain's Logbook was replaced by the Weathered Chronicle (ModItems.WEATHERED_CHRONICLE, world/Chronicle)

    public static void init() {}
}
