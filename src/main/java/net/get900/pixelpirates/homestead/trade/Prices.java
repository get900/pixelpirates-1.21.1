package net.get900.pixelpirates.homestead.trade;

import net.get900.pixelpirates.homestead.HomesteadBlocks;
import net.get900.pixelpirates.homestead.HomesteadItems;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the port pays for things, in pirate coins per item. One table for the traders' buy offers (by trader kind) and
 * the Trading Post counter (which pays 75%).
 */
public final class Prices {
    private Prices() {}

    public enum Buyer { FISHMONGER, BARKEEP, CURIO_DEALER }

    public record Price(int coins, Buyer buyer) {}

    public static final Map<Item, Price> BUY = new LinkedHashMap<>();

    static void put(ItemConvertible i, int coins, Buyer b) { BUY.put(i.asItem(), new Price(coins, b)); }

    public static void init() {
        Buyer F = Buyer.FISHMONGER, B = Buyer.BARKEEP, C = Buyer.CURIO_DEALER;
        put(Items.COD, 1, F); put(Items.SALMON, 1, F); put(Items.TROPICAL_FISH, 2, F); put(Items.PUFFERFISH, 2, F);
        put(HomesteadItems.PARROTFISH, 1, F); put(HomesteadItems.RED_SNAPPER, 1, F); put(HomesteadItems.MAHI_MAHI, 2, F);
        put(HomesteadItems.LIONFISH, 3, F); put(HomesteadItems.MOONFISH, 3, F); put(HomesteadItems.EMBERFIN, 4, F); put(HomesteadItems.LAVA_EEL, 4, F);
        put(HomesteadItems.GHOSTFIN, 5, F); put(HomesteadItems.BONEFISH, 4, F); put(HomesteadItems.ANGLERFRY, 6, F); put(HomesteadItems.VOIDFIN, 6, F);
        put(HomesteadItems.LOBSTER, 2, F); put(HomesteadItems.CRAB_CLAW, 1, F); put(ModItems.RAW_SHARK_MEAT, 2, F);
        put(HomesteadBlocks.GOLDEN_MARLIN_TROPHY, 40, F); put(HomesteadBlocks.GHOST_SWORDFISH_TROPHY, 60, F); put(HomesteadBlocks.COELACANTH_TROPHY, 90, F);
        put(HomesteadItems.RAW_RUM, 4, B); put(HomesteadItems.AGED_RUM, 12, B); put(HomesteadItems.VINTAGE_RUM, 32, B);
        put(HomesteadItems.MOLASSES, 1, B); put(HomesteadItems.PINEAPPLE, 1, B); put(HomesteadItems.LIME, 1, B); put(HomesteadItems.CHILI_PEPPER, 1, B);
        put(ModItems.BANANA, 1, B); put(ModItems.COCONUT, 1, B);
        put(ModItems.CURSED_BONE, 3, C); put(ModItems.KRAKEN_SCALE, 8, C); put(ModItems.VOLCANIC_EMBER, 4, C); put(ModItems.KRAKEN_INK, 3, C);
        put(ModItems.BLOODFIN_FLESH, 5, C); put(Items.NAUTILUS_SHELL, 6, C); put(ModItems.TATTERED_CLOTH, 1, C); put(Items.PRISMARINE_SHARD, 1, C);
        put(Items.HEART_OF_THE_SEA, 64, C);
    }

    public static int priceOf(Item i) { Price p = BUY.get(i); return p == null ? 0 : p.coins(); }
}
