package net.get900.pixelpirates.item;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.homestead.HomesteadItems;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.function.Supplier;

/**
 * THE CREATIVE TABS (2026-09-30, replaces ModItemGroups + the Homestead tab + the vanilla-tab additions).
 * Self-sorting: EVERY item in the pixelpirates namespace is placed by {@link #classify} - explicit id lists first, then
 * by item/block type - so nothing can go missing and new items land somewhere sensible without touching this file.
 * Items keep registration order inside a tab, which keeps sets (armor, woods, stairs/slabs) together.
 * `/pptabs` (op) logs every tab's contents for review.
 */
public final class ModCreativeTabs {
    private ModCreativeTabs() {}

    public enum Tab {
        SEAFARING("seafaring", "Pixel Pirates: Ships & Seafaring", () -> ModBlocks.SHIP_HELM.asItem()),
        WEAPONS("weapons", "Pixel Pirates: Weapons & Tools", () -> ModItems.CUTLASS),
        ARMOR("armor", "Pixel Pirates: Armor", () -> ModItems.PIRATE_CHESTPLATE),
        LEGENDS("legends", "Pixel Pirates: Relics & Legends", () -> ModItems.CROWN_OF_THE_DROWNED),
        FOOD("food", "Pixel Pirates: Galley & Drink", () -> ModItems.BANANA),
        MATERIALS("materials", "Pixel Pirates: Loot & Materials", () -> ModItems.PIRATE_COIN),
        BUILDING("building", "Pixel Pirates: Building Blocks", () -> ModBlocks.DRIFTWOOD_BLOCK.asItem()),
        NATURE("nature", "Pixel Pirates: Islands & Farming", () -> ModBlocks.PALM_LEAVES.asItem()),
        FURNISHING("furnishing", "Pixel Pirates: Homestead & Furnishing", () -> ModBlocks.GROG_BARREL.asItem()),
        DUNGEON("dungeon", "Pixel Pirates: Dungeon & Boss Blocks", () -> ModBlocks.BLAST_RUBBLE.asItem()),
        MOBS("mobs", "Pixel Pirates: Creatures", () -> Items.TURTLE_EGG),
        ADMIN("admin", "Pixel Pirates: Testing Tools", () -> ModItems.DEBUG_WAND);

        public final String key, title;
        final Supplier<Item> icon;

        Tab(String key, String title, Supplier<Item> icon) { this.key = key; this.title = title; this.icon = icon; }
    }

    // ------------------------------------------------------------------ explicit placements (ids), checked first
    private static final Map<String, Tab> BY_ID = new HashMap<>();
    private static void put(Tab t, String... ids) { for (String id : ids) BY_ID.put(id, t); }

    static {
        put(Tab.ADMIN, "debug_wand", "ship_spawner_wand");
        put(Tab.SEAFARING, "raft_item", "rope", "sail", "mast", "mast_with_sails", "ship_repair_kit", "ship_blueprint",
                "ship_helm", "ship_mast", "ship_cannon", "shipwright_table", "ship_waterline", "hammock", "ship_bedroll",
                "cannon_ball", "chain_shot", "grape_shot", "captains_spyglass", "compass_of_desire", "weathered_chronicle",
                "pirate_journal", "bounty_map", "treasure_map_common", "treasure_map_rare", "treasure_map_legendary",
                "dimension_key", "seafarers_token", "powder_barge", "watchers_horn", "anchor_winch", "jolly_roger",
                "jolly_roger_sail_canvas", "spectral_sail");
        put(Tab.WEAPONS, "dynamite", "depth_charge", "harpoon", "chum", "throwing_knife", "grappling_hook", "salvage_hook",
                "paper_cartridge", "scattershot", "flintlock_pistol", "blunderbuss", "tidebreaker", "tideshackle");
        put(Tab.LEGENDS, "gallowbrand", "bane_shaft", "leviathan_scale", "bloodfin_flesh", "heat_amulet", "sanity_amulet");
        put(Tab.DUNGEON, "blast_rubble", "serpent_ward", "quench_valve", "tide_sluice", "harpoon_winch", "manacle_anchor",
                "fort_cannon", "ghost_cannon", "ghost_mast", "phantom_buoy", "galvanic_pylon", "flesh_vein", "living_flesh",
                "heart_valve", "rift_seal", "tide_bell", "bane_ballista", "gallowbrand_stone");
        put(Tab.FOOD, "molasses", "raw_rum", "aged_rum", "vintage_rum", "grog", "coconut_grog", "sea_bandage");
        put(Tab.NATURE, "banana_block", "coconut_block", "pineapple_crown", "lime_seeds", "chili_seeds");
        put(Tab.NATURE, "shell_block", "tide_pool_rock", "coral_rock", "scorched_sand", "grave_silt", "abyssal_slate");
        put(Tab.BUILDING, "pearl_block", "sulfur_block", "soul_barnacle", "luminous_vein");
        put(Tab.FURNISHING, "emerald_token", "sword_block", "treasure_block", "map_block", "pirate_diary_block", "anchor_block");
        put(Tab.SEAFARING, "cannon");
        put(Tab.ADMIN, "boss_slayer");
    }

    private static final Map<Tab, List<Item>> CONTENTS = new EnumMap<>(Tab.class);

    /** Which tab an item belongs in. */
    public static Tab classify(Item item) {
        String id = Registries.ITEM.getId(item).getPath();
        Tab t = BY_ID.get(id);
        if (t != null) return t;
        if (id.contains("wand") || id.startsWith("test_")) return Tab.ADMIN;
        if (item instanceof SpawnEggItem) return Tab.MOBS;
        if (item instanceof ArmorItem) return Tab.ARMOR;
        if (item instanceof net.get900.pixelpirates.item.custom.BossRelicItem || RelicWeapons.relicOf(item) != null) return Tab.LEGENDS;
        if (item instanceof BlockItem bi) return blockTab(id, bi.getBlock());
        if (item.isFood() || item instanceof net.get900.pixelpirates.homestead.item.DrinkItem) return Tab.FOOD;
        if (item instanceof SwordItem || item instanceof ToolItem || item instanceof RangedWeaponItem || item instanceof TridentItem
                || item instanceof net.get900.pixelpirates.item.relic.RelicGunItem
                || item instanceof net.get900.pixelpirates.item.relic.SunkenTridentItem
                || item instanceof net.get900.pixelpirates.homestead.gun.GunItem) return Tab.WEAPONS;
        if (id.contains("map") || id.contains("spyglass") || id.contains("compass")) return Tab.SEAFARING;
        return Tab.MATERIALS;
    }

    private static Tab blockTab(String id, Block b) {
        if (b instanceof SaplingBlock || b instanceof CropBlock || b instanceof LeavesBlock || b instanceof PlantBlock
                || b instanceof FlowerPotBlock || b instanceof net.get900.pixelpirates.block.custom.HangingFruitBlock
                || b instanceof CoralParentBlock) return Tab.NATURE;
        if (b instanceof PillarBlock && (id.endsWith("_log") || id.endsWith("_wood") || id.contains("stripped"))) return Tab.BUILDING;
        if (b instanceof StairsBlock || b instanceof SlabBlock || b instanceof WallBlock || b instanceof FenceBlock
                || b instanceof FenceGateBlock || b instanceof DoorBlock || b instanceof TrapdoorBlock
                || b instanceof PressurePlateBlock || b instanceof ButtonBlock || b instanceof PaneBlock) return Tab.BUILDING;
        if (b instanceof BlockWithEntity || b instanceof BedBlock || b instanceof CarpetBlock || b instanceof LanternBlock
                || b instanceof TorchBlock || b instanceof ChainBlock || b instanceof LadderBlock) return Tab.FURNISHING;
        if (b.getClass() == Block.class || b instanceof PillarBlock || b instanceof TransparentBlock) return Tab.BUILDING;
        return Tab.FURNISHING;                                             // shaped/custom blocks: furniture, props, machines
    }

    private static synchronized List<Item> contents(Tab tab) {
        if (CONTENTS.isEmpty()) {
            for (Tab t : Tab.values()) CONTENTS.put(t, new ArrayList<>());
            for (Item item : Registries.ITEM) {                            // registration order
                if (!Registries.ITEM.getId(item).getNamespace().equals(PixelPirates.MOD_ID)) continue;
                CONTENTS.get(classify(item)).add(item);
            }
        }
        return CONTENTS.get(tab);
    }

    public static void register() {
        for (Tab tab : Tab.values()) {
            Registry.register(Registries.ITEM_GROUP, new Identifier(PixelPirates.MOD_ID, tab.key),
                    FabricItemGroup.builder().icon(() -> new ItemStack(tab.icon.get()))
                            .displayName(Text.translatable("itemgroup.pixelpirates." + tab.key))
                            .entries((ctx, entries) -> contents(tab).forEach(entries::add)).build());
        }
    }

    /** `/pptabs`: every tab's contents, for review. */
    public static List<String> dump() {
        List<String> out = new ArrayList<>();
        for (Tab t : Tab.values()) {
            List<String> ids = new ArrayList<>();
            for (Item i : contents(t)) ids.add(Registries.ITEM.getId(i).getPath());
            out.add(t.key.toUpperCase() + " (" + ids.size() + "): " + String.join(", ", ids));
        }
        return out;
    }
}
