package net.get900.pixelpirates.item;

import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.item.custom.BossRelicItem;
import net.get900.pixelpirates.item.custom.BountyMapItem;
import net.get900.pixelpirates.item.custom.DebugWandItem;
import net.get900.pixelpirates.item.custom.DimensionKeyItem;
import net.get900.pixelpirates.item.custom.CutlassItem;
import net.get900.pixelpirates.item.custom.DynamiteItem;
import net.get900.pixelpirates.item.custom.GrogItem;
import net.get900.pixelpirates.item.custom.ModArmorItem;
import net.get900.pixelpirates.item.custom.PirateJournalItem;
import net.get900.pixelpirates.item.custom.RaftItem;
import net.get900.pixelpirates.item.custom.ShipBlueprintItem;
import net.get900.pixelpirates.item.custom.ShipRepairKitItem;
import net.get900.pixelpirates.item.custom.ShipSpawnerWandItem;
import net.get900.pixelpirates.item.custom.TreasureMapItem;
import net.get900.pixelpirates.item.custom.AbyssalHarpoonItem;
import net.get900.pixelpirates.item.custom.EmberbrandItem;
import net.get900.pixelpirates.item.custom.KrakenFangItem;
import net.get900.pixelpirates.item.custom.SoulrenderItem;
import net.get900.pixelpirates.item.custom.StormcallerItem;
import net.get900.pixelpirates.item.custom.ThrowingKnifeItem;
import net.get900.pixelpirates.item.custom.WraithbladeItem;
import net.get900.pixelpirates.sound.ModSounds;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.*;
import net.minecraft.util.Rarity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;
import java.util.List;

public class ModItems {
    public static final Item DIMENSION_KEY = registerItem("dimension_key",
            new DimensionKeyItem(new Item.Settings().maxCount(1)));

    public static final Item FROST_HELM = registerItem("frost_helm",
            new ArmorItem(ModArmorMaterials.PIRATE_ARMOR, ArmorItem.Type.HELMET, new Item.Settings()));

    public static final Item HEAT_AMULET = registerItem("heat_amulet",
            new Item(new Item.Settings().maxCount(1)));
    public static final Item SANITY_AMULET = registerItem("sanity_amulet",
            new Item(new Item.Settings().maxCount(1)));

    public static final Item COIN = registerItem("coin", new Item(new Item.Settings()));
    public static final Item PIRATE_COIN = registerItem("pirate_coin", new Item(new Item.Settings()));
    public static final Item KRAKEN_INK = registerItem("kraken_ink", new Item(new Item.Settings()));
    public static final Item ROPE = registerItem("rope", new Item(new Item.Settings()));
    public static final Item CANNON = registerItem("cannon", new Item(new Item.Settings()));
    public static final Item SAIL = registerItem("sail", new Item(new Item.Settings()));
    public static final Item MAST_WITH_SAILS = registerItem("mast_with_sails", new Item(new Item.Settings()));
    public static final Item MAST = registerItem("mast", new Item(new Item.Settings()));
    public static final Item DRIFTWOOD = registerItem("driftwood", new Item(new Item.Settings()));
    public static final Item TATTERED_CLOTH = registerItem("tattered_cloth", new Item(new Item.Settings()));

    // Region crafting materials — dropped/looted in their home regions
    public static final Item CURSED_BONE = registerItem("cursed_bone", new Item(new Item.Settings()));
    public static final Item KRAKEN_SCALE = registerItem("kraken_scale", new Item(new Item.Settings()));
    public static final Item VOLCANIC_EMBER = registerItem("volcanic_ember", new Item(new Item.Settings().fireproof()));

    public static final Item CANNON_BALL = registerItem("cannon_ball", new Item(new Item.Settings()));
    public static final Item SHIP_REPAIR_KIT = registerItem("ship_repair_kit",
            new ShipRepairKitItem(new Item.Settings().maxCount(16)));
    public static final Item SHIP_BLUEPRINT = registerItem("ship_blueprint",
            new ShipBlueprintItem(new Item.Settings().maxCount(1)));

    // Admin / testing tools
    /** DEBUG: kills anything it hits / aims at (for testing boss kills). /pptest slayer gives one. */
    public static final Item BOSS_SLAYER = registerItem("boss_slayer",
            new net.get900.pixelpirates.item.custom.BossSlayerItem(new Item.Settings()));
    public static final Item DEBUG_WAND = registerItem("debug_wand",
            new DebugWandItem(new Item.Settings().maxCount(1)));
    public static final Item SHIP_SPAWNER_WAND = registerItem("ship_spawner_wand",
            new ShipSpawnerWandItem(new Item.Settings().maxCount(1)));

    public static final Item RAFT_ITEM = Registry.register(Registries.ITEM,
            new Identifier(PixelPirates.MOD_ID, "raft_item"),
            new RaftItem(new Item.Settings().maxCount(1)));

    // Food items
    public static final Item GROG = Registry.register(Registries.ITEM,
            new Identifier(PixelPirates.MOD_ID, "grog"),
            new GrogItem(new Item.Settings().maxCount(16).food(FoodComponents.HONEY_BOTTLE)));
    public static final Item COOKED_SHARK_MEAT = registerItem("cooked_shark_meat",
            new Item(new Item.Settings().food(ModFoodComponents.COOKED_SHARK_MEAT)));
    public static final Item RAW_SHARK_MEAT = registerItem("raw_shark_meat",
            new Item(new Item.Settings().food(ModFoodComponents.RAW_SHARK_MEAT)));
    public static final Item RAW_SALTED_SWIMMER = registerItem("raw_salted_swimmer",
            new Item(new Item.Settings().food(ModFoodComponents.RAW_SALTED_SWIMMER)));
    public static final Item COOKED_SALTED_SWIMMER = registerItem("cooked_salted_swimmer",
            new Item(new Item.Settings().food(ModFoodComponents.COOKED_SALTED_SWIMMER)));
    public static final Item BANANA = registerItem("banana",
            new Item(new Item.Settings().food(ModFoodComponents.BANANA)));
    public static final Item COCONUT = registerItem("coconut",
            new Item(new Item.Settings().food(ModFoodComponents.COCONUT)));

    // Galley cooking
    public static final Item BANANA_BREAD = registerItem("banana_bread",
            new Item(new Item.Settings().food(ModFoodComponents.BANANA_BREAD)));
    public static final Item HARDTACK = registerItem("hardtack",
            new Item(new Item.Settings().food(ModFoodComponents.HARDTACK)));
    public static final Item KRAKEN_CALAMARI = registerItem("kraken_calamari",
            new Item(new Item.Settings().food(ModFoodComponents.KRAKEN_CALAMARI)));
    public static final Item COCONUT_GROG = registerItem("coconut_grog",
            new Item(new Item.Settings().maxCount(16).food(ModFoodComponents.COCONUT_GROG)));
    public static final Item PIRATES_STEW = registerItem("pirates_stew",
            new StewItem(new Item.Settings().maxCount(1).food(ModFoodComponents.PIRATES_STEW)));

    // Armor items
    public static final Item PIRATE_HELMET = registerItem("pirate_helmet",
            new ModArmorItem(ModArmorMaterials.PIRATE_ARMOR, ArmorItem.Type.HELMET, new Item.Settings()));
    public static final Item PIRATE_CHESTPLATE = registerItem("pirate_chestplate",
            new ArmorItem(ModArmorMaterials.PIRATE_ARMOR, ArmorItem.Type.CHESTPLATE, new Item.Settings()));
    public static final Item PIRATE_LEGGINGS = registerItem("pirate_leggings",
            new ArmorItem(ModArmorMaterials.PIRATE_ARMOR, ArmorItem.Type.LEGGINGS, new Item.Settings()));
    public static final Item PIRATE_BOOTS = registerItem("pirate_boots",
            new ArmorItem(ModArmorMaterials.PIRATE_ARMOR, ArmorItem.Type.BOOTS, new Item.Settings()));

    // ================= REGION ARMOR SETS =================
    // Ring 1 — Castaway rags (no set bonus; it's driftwood and patched sailcloth)
    public static final Item CASTAWAY_HELMET = registerItem("castaway_helmet",
            new ArmorItem(ModArmorMaterials.CASTAWAY, ArmorItem.Type.HELMET, new Item.Settings()));
    public static final Item CASTAWAY_CHESTPLATE = registerItem("castaway_chestplate",
            new ArmorItem(ModArmorMaterials.CASTAWAY, ArmorItem.Type.CHESTPLATE, new Item.Settings()));
    public static final Item CASTAWAY_LEGGINGS = registerItem("castaway_leggings",
            new ArmorItem(ModArmorMaterials.CASTAWAY, ArmorItem.Type.LEGGINGS, new Item.Settings()));
    public static final Item CASTAWAY_BOOTS = registerItem("castaway_boots",
            new ArmorItem(ModArmorMaterials.CASTAWAY, ArmorItem.Type.BOOTS, new Item.Settings()));

    // Ring 2 — Navy Officer (full set: Hero of the Village — merchants respect the uniform)
    public static final Item NAVY_OFFICER_HELMET = registerItem("navy_officer_helmet",
            new ModArmorItem(ModArmorMaterials.NAVY_OFFICER, ArmorItem.Type.HELMET, new Item.Settings()));
    public static final Item NAVY_OFFICER_CHESTPLATE = registerItem("navy_officer_chestplate",
            new ArmorItem(ModArmorMaterials.NAVY_OFFICER, ArmorItem.Type.CHESTPLATE, new Item.Settings()));
    public static final Item NAVY_OFFICER_LEGGINGS = registerItem("navy_officer_leggings",
            new ArmorItem(ModArmorMaterials.NAVY_OFFICER, ArmorItem.Type.LEGGINGS, new Item.Settings()));
    public static final Item NAVY_OFFICER_BOOTS = registerItem("navy_officer_boots",
            new ArmorItem(ModArmorMaterials.NAVY_OFFICER, ArmorItem.Type.BOOTS, new Item.Settings()));

    // Ring 3 — Corsair (full set: Speed + Jump Boost — boarding agility)
    public static final Item CORSAIR_HELMET = registerItem("corsair_helmet",
            new ModArmorItem(ModArmorMaterials.CORSAIR, ArmorItem.Type.HELMET, new Item.Settings()));
    public static final Item CORSAIR_CHESTPLATE = registerItem("corsair_chestplate",
            new ArmorItem(ModArmorMaterials.CORSAIR, ArmorItem.Type.CHESTPLATE, new Item.Settings()));
    public static final Item CORSAIR_LEGGINGS = registerItem("corsair_leggings",
            new ArmorItem(ModArmorMaterials.CORSAIR, ArmorItem.Type.LEGGINGS, new Item.Settings()));
    public static final Item CORSAIR_BOOTS = registerItem("corsair_boots",
            new ArmorItem(ModArmorMaterials.CORSAIR, ArmorItem.Type.BOOTS, new Item.Settings()));

    // Volcanic Isles — Ashen plate (full set: Fire Resistance)
    public static final Item ASHEN_HELMET = registerItem("ashen_helmet",
            new ModArmorItem(ModArmorMaterials.ASHEN, ArmorItem.Type.HELMET, new Item.Settings().fireproof()));
    public static final Item ASHEN_CHESTPLATE = registerItem("ashen_chestplate",
            new ArmorItem(ModArmorMaterials.ASHEN, ArmorItem.Type.CHESTPLATE, new Item.Settings().fireproof()));
    public static final Item ASHEN_LEGGINGS = registerItem("ashen_leggings",
            new ArmorItem(ModArmorMaterials.ASHEN, ArmorItem.Type.LEGGINGS, new Item.Settings().fireproof()));
    public static final Item ASHEN_BOOTS = registerItem("ashen_boots",
            new ArmorItem(ModArmorMaterials.ASHEN, ArmorItem.Type.BOOTS, new Item.Settings().fireproof()));

    // Ring 4 — Cursed Bone (full set: Night Vision + Resistance)
    public static final Item CURSED_BONE_HELMET = registerItem("cursed_bone_helmet",
            new ModArmorItem(ModArmorMaterials.CURSED_BONE, ArmorItem.Type.HELMET, new Item.Settings()));
    public static final Item CURSED_BONE_CHESTPLATE = registerItem("cursed_bone_chestplate",
            new ArmorItem(ModArmorMaterials.CURSED_BONE, ArmorItem.Type.CHESTPLATE, new Item.Settings()));
    public static final Item CURSED_BONE_LEGGINGS = registerItem("cursed_bone_leggings",
            new ArmorItem(ModArmorMaterials.CURSED_BONE, ArmorItem.Type.LEGGINGS, new Item.Settings()));
    public static final Item CURSED_BONE_BOOTS = registerItem("cursed_bone_boots",
            new ArmorItem(ModArmorMaterials.CURSED_BONE, ArmorItem.Type.BOOTS, new Item.Settings()));

    // Ring 5 — Kraken-Scale (full set: Conduit Power + Dolphin's Grace)
    public static final Item KRAKEN_SCALE_HELMET = registerItem("kraken_scale_helmet",
            new ModArmorItem(ModArmorMaterials.KRAKEN_SCALE, ArmorItem.Type.HELMET, new Item.Settings()));
    public static final Item KRAKEN_SCALE_CHESTPLATE = registerItem("kraken_scale_chestplate",
            new ArmorItem(ModArmorMaterials.KRAKEN_SCALE, ArmorItem.Type.CHESTPLATE, new Item.Settings()));
    public static final Item KRAKEN_SCALE_LEGGINGS = registerItem("kraken_scale_leggings",
            new ArmorItem(ModArmorMaterials.KRAKEN_SCALE, ArmorItem.Type.LEGGINGS, new Item.Settings()));
    public static final Item KRAKEN_SCALE_BOOTS = registerItem("kraken_scale_boots",
            new ArmorItem(ModArmorMaterials.KRAKEN_SCALE, ArmorItem.Type.BOOTS, new Item.Settings()));

    // BOSS SETS (item/BossArmor) - boss hoards + kills only. Index [tier][0..3] = helmet, chestplate, leggings, boots.
    public static final String[] BOSS_SET_IDS = {"", "powder_monkey", "forgeguard", "tidecourt", "gallowbreaker", "thalassar"};
    private static final Item[][] BOSS_SETS = new Item[6][];
    static {
        ModArmorMaterials[] mats = {null, ModArmorMaterials.POWDER_MONKEY, ModArmorMaterials.FORGEGUARD, ModArmorMaterials.TIDECOURT,
                ModArmorMaterials.GALLOWBREAKER, ModArmorMaterials.THALASSAR};
        ArmorItem.Type[] types = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS, ArmorItem.Type.BOOTS};
        String[] names = {"helmet", "chestplate", "leggings", "boots"};
        for (int t = 1; t <= 5; t++) {
            BOSS_SETS[t] = new Item[4];
            for (int i = 0; i < 4; i++)
                BOSS_SETS[t][i] = registerItem(BOSS_SET_IDS[t] + "_" + names[i],
                        new net.get900.pixelpirates.item.custom.BossArmorItem(mats[t], types[i], t, new Item.Settings()));
        }
    }

    /** The four pieces of boss set `tier` (1..5): helmet, chestplate, leggings, boots. */
    public static Item[] bossSet(int tier) { return BOSS_SETS[tier]; }

    // Weapon items
    public static final Item CUTLASS = registerItem("cutlass",
            new CutlassItem(ModToolMaterials.PIRATE, 3, -2.4f, new Item.Settings()));
    public static final Item DAGGER = registerItem("dagger",
            new SwordItem(ModToolMaterials.PIRATE, 1, -1.5f, new Item.Settings()));
    public static final Item RUSTED_CUTLASS = registerItem("rusted_cutlass",
            new SwordItem(ModToolMaterials.PIRATE, 1, -2.8f, new Item.Settings()));
    public static final Item BROKEN_SHOVEL = registerItem("broken_shovel",
            new ShovelItem(ModToolMaterials.PIRATE, -1.0f, -3.0f, new Item.Settings()));
    public static final Item BOARDING_AXE = registerItem("boarding_axe",
            new AxeItem(ModToolMaterials.PIRATE, 5.0f, -3.0f, new Item.Settings()) {
        @Override
        public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
            tooltip.add(Text.translatable("tooltip.pixelpirates.boarding_axe.tooltip"));
            super.appendTooltip(stack, world, tooltip, context);
        }
    });
    public static final Item DYNAMITE = registerItem("dynamite",
            new DynamiteItem(new Item.Settings().maxCount(16)));
    /** Chained Revenant fight: strike its free head to sling it into a gibbet cage (Coral Temple loot). */
    public static final Item TIDESHACKLE = registerItem("tideshackle",
            new net.get900.pixelpirates.item.custom.TideshackleItem(new Item.Settings().maxDamage(12).rarity(Rarity.RARE)));
    /** The hangman's greatsword, pulled from the stone under the Chained Revenant - throwable, returns (GallowbrandItem). */
    public static final Item GALLOWBRAND = registerItem("gallowbrand",
            new net.get900.pixelpirates.item.custom.GallowbrandItem(new Item.Settings().maxCount(1).fireproof().rarity(Rarity.EPIC)));
    /** Bloodfin fight: winch ammo, thrown bait, and the flesh that tears off it (also bait). */
    public static final Item HARPOON = registerItem("harpoon", new Item(new Item.Settings().maxCount(16)));
    public static final Item CHUM = registerItem("chum",
            new net.get900.pixelpirates.item.custom.ChumItem(new Item.Settings().maxCount(16), false));
    public static final Item BLOODFIN_FLESH = registerItem("bloodfin_flesh",
            new net.get900.pixelpirates.item.custom.ChumItem(new Item.Settings().maxCount(16), true));
    /** Underwater charge for the Abyssal King fight (DepthChargeEntity). */
    public static final Item DEPTH_CHARGE = registerItem("depth_charge",
            new net.get900.pixelpirates.item.custom.DepthChargeItem(new Item.Settings().maxCount(16)));

    // ================= REGION WEAPONS =================
    // Ring 1 — Starter Seas
    public static final Item MARLINSPIKE = registerItem("marlinspike",
            new SwordItem(ModToolMaterials.CASTAWAY, 2, -1.6f, new Item.Settings()));
    public static final Item BOARDING_SABRE = registerItem("boarding_sabre",
            new SwordItem(ModToolMaterials.CASTAWAY, 4, -2.4f, new Item.Settings()));
    // Ring 2 — Merchant Waters
    public static final Item NAVAL_RAPIER = registerItem("naval_rapier",
            new SwordItem(ModToolMaterials.NAVAL, 2, -1.2f, new Item.Settings()));
    public static final Item OFFICERS_SABRE = registerItem("officers_sabre",
            new SwordItem(ModToolMaterials.NAVAL, 4, -2.2f, new Item.Settings()));
    public static final Item THROWING_KNIFE = registerItem("throwing_knife",
            new ThrowingKnifeItem(new Item.Settings().maxCount(16)));
    // Ring 3 — Pirate Territory
    public static final Item CORSAIR_CUTLASS = registerItem("corsair_cutlass",
            new SwordItem(ModToolMaterials.CORSAIR, 4, -2.2f, new Item.Settings()));
    public static final Item BOARDING_PIKE = registerItem("boarding_pike",
            new SwordItem(ModToolMaterials.CORSAIR, 7, -3.0f, new Item.Settings()));
    // Volcanic Isles
    public static final Item EMBERBRAND = registerItem("emberbrand",
            new EmberbrandItem(ModToolMaterials.VOLCANIC, 6, -2.4f, new Item.Settings().fireproof()));
    // Ring 4 — Cursed Seas
    public static final Item SOULRENDER = registerItem("soulrender",
            new SoulrenderItem(ModToolMaterials.CURSED, 6, -2.4f, new Item.Settings()));
    public static final Item WRAITHBLADE = registerItem("wraithblade",
            new WraithbladeItem(ModToolMaterials.CURSED, 5, -2.0f, new Item.Settings()));
    // Ring 5 — The Abyss
    public static final Item KRAKEN_FANG = registerItem("kraken_fang",
            new KrakenFangItem(ModToolMaterials.ABYSSAL, 3, -1.4f, new Item.Settings()));
    public static final Item STORMCALLER = registerItem("stormcaller",
            new StormcallerItem(ModToolMaterials.ABYSSAL, 7, -2.6f, new Item.Settings()));
    public static final Item ABYSSAL_HARPOON = registerItem("abyssal_harpoon",
            new AbyssalHarpoonItem(ModToolMaterials.ABYSSAL, 8, -2.9f, new Item.Settings()));

    // Boss relics - one per chain boss, each counters the NEXT boss (entity/mob/BossProgression)
    public static final Item RACKHAMS_DIVING_CHARM = registerItem("rackhams_diving_charm",
            new BossRelicItem(BossRelicItem.Kind.DIVING_CHARM, new Item.Settings().maxCount(1).fireproof().rarity(Rarity.EPIC)));
    public static final Item SERPENTS_TIDE_PEARL = registerItem("serpents_tide_pearl",
            new BossRelicItem(BossRelicItem.Kind.TIDE_PEARL, new Item.Settings().maxCount(1).fireproof().rarity(Rarity.EPIC)));
    public static final Item EVERBURNING_LANTERN = registerItem("everburning_lantern",
            new BossRelicItem(BossRelicItem.Kind.LANTERN, new Item.Settings().maxCount(1).fireproof().rarity(Rarity.EPIC)));
    public static final Item SPECTRAL_ANCHOR = registerItem("spectral_anchor",
            new BossRelicItem(BossRelicItem.Kind.ANCHOR, new Item.Settings().maxCount(1).fireproof().rarity(Rarity.EPIC)));
    public static final Item ROYAL_TIDE_SIGIL = registerItem("royal_tide_sigil",
            new BossRelicItem(BossRelicItem.Kind.TIDE_SIGIL, new Item.Settings().maxCount(1).fireproof().rarity(Rarity.EPIC)));
    public static final Item BLOODFIN_RAZOR_TOOTH = registerItem("bloodfin_razor_tooth",
            new BossRelicItem(BossRelicItem.Kind.RAZOR_TOOTH, new Item.Settings().maxCount(1).fireproof().rarity(Rarity.EPIC)));
    public static final Item KRAKENS_INK_HEART = registerItem("krakens_ink_heart",
            new BossRelicItem(BossRelicItem.Kind.INK_HEART, new Item.Settings().maxCount(1).fireproof().rarity(Rarity.EPIC)));
    public static final Item BROKEN_SHACKLE = registerItem("broken_shackle",
            new BossRelicItem(BossRelicItem.Kind.SHACKLE, new Item.Settings().maxCount(1).fireproof().rarity(Rarity.EPIC)));
    public static final Item ABYSSAL_HEARTSTONE = registerItem("abyssal_heartstone",
            new BossRelicItem(BossRelicItem.Kind.HEARTSTONE, new Item.Settings().maxCount(1).fireproof().rarity(Rarity.EPIC)));

    /** Breaks Tideward Stones in the Sea Serpent's hollow (found in the Tidewater Shrine vault). */
    // ---- The Leviathan hunt (boss 10/10)
    /** THE POISONED MEAL: float it where the Leviathan feeds - swallowed, it blows up inside it (PowderBargeEntity). */
    public static final Item POWDER_BARGE = registerItem("powder_barge",
            new net.get900.pixelpirates.item.custom.PowderBargeItem(new Item.Settings().maxCount(4)));
    /** The Bane's harpoon shaft - it wears it in its crown of wreckage (phase 3). One of the Bane's three loads. */
    public static final Item BANE_SHAFT = registerItem("bane_shaft", new Item(new Item.Settings().maxCount(1).fireproof().rarity(Rarity.EPIC)));
    /** A scale it shed - left in the ports it destroyed, and dropped in every lair. */
    public static final Item LEVIATHAN_SCALE = registerItem("leviathan_scale", new Item(new Item.Settings().fireproof().rarity(Rarity.RARE)));
    /** The final relic: the Leviathan's crown, from the Drowning Spire. */
    public static final Item CROWN_OF_THE_DROWNED = registerItem("crown_of_the_drowned",
            new BossRelicItem(BossRelicItem.Kind.CROWN, new Item.Settings().maxCount(1).fireproof().rarity(Rarity.EPIC)));

    public static final Item TIDEBREAKER = registerItem("tidebreaker",
            new net.get900.pixelpirates.item.custom.TidebreakerItem(new Item.Settings().maxDamage(12).rarity(Rarity.RARE)));

    // Zone access items
    public static final Item SEAFARERS_TOKEN = registerItem("seafarers_token",
            new Item(new Item.Settings().maxCount(16)));

    // Map Merchant shop items
    public static final Item TREASURE_MAP_COMMON = registerItem("treasure_map_common",
            new TreasureMapItem(TreasureMapItem.Tier.COMMON, new Item.Settings().maxCount(16)));
    public static final Item TREASURE_MAP_RARE = registerItem("treasure_map_rare",
            new TreasureMapItem(TreasureMapItem.Tier.RARE, new Item.Settings().maxCount(16)));
    public static final Item TREASURE_MAP_LEGENDARY = registerItem("treasure_map_legendary",
            new TreasureMapItem(TreasureMapItem.Tier.LEGENDARY, new Item.Settings().maxCount(16)));
    public static final Item BOUNTY_MAP = registerItem("bounty_map",
            new BountyMapItem(new Item.Settings().maxCount(16)));

    // Pirate Journal — opens the skill tree screen
    public static final Item PIRATE_JOURNAL = registerItem("pirate_journal",
            new PirateJournalItem(new Item.Settings().maxCount(1)));

    // Test disc — lets us verify sound events work via jukebox before relying on ShipMusicPlayer
    public static final Item DISC_HALYARD_SONG = registerItem("disc_halyard_song",
            new MusicDiscItem(1, ModSounds.HALYARD_SONG, new Item.Settings().maxCount(1), 180));

    private static Item registerItem(String name, Item item) {
        return Registry.register(Registries.ITEM, new Identifier(PixelPirates.MOD_ID, name), item);
    }

    public static void registerModItems() {
        PixelPirates.LOGGER.info("Registering Mod Items for " + PixelPirates.MOD_ID);
    }
}
