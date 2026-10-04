package net.get900.pixelpirates.world;

import net.get900.pixelpirates.item.ModItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

import java.util.List;

public final class ShipUpgrades {

    public record CostEntry(Item item, int count) {}

    public record Def(
        String key,
        String displayName,
        String description,
        int maxLevel,
        List<CostEntry> costPerLevel
    ) {}

    public static final List<Def> ALL = List.of(
        new Def("hull",   "Hull Reinforcement", "+100 HP per level",           3,
            List.of(new CostEntry(Items.IRON_INGOT, 8))),
        new Def("cannon", "Cannon Mounts",       "+1 cannon slot per level",    2,
            List.of(new CostEntry(ModItems.CANNON_BALL, 4), new CostEntry(Items.IRON_INGOT, 4))),
        new Def("speed",  "Speed Rigging",       "+1 effective mast per level", 2,
            List.of(new CostEntry(ModItems.SAIL, 2), new CostEntry(ModItems.ROPE, 3))),
        new Def("armor",  "Armor Plating",       "-10% damage per level",       2,
            List.of(new CostEntry(Items.IRON_INGOT, 12))),
        // Loading one cannon loads its neighbours too (CannonBlock.loadBroadside): I = that side of the same deck,
        // II = that whole side, every deck. Each extra cannon still takes its own shot from your inventory.
        new Def("gun_crew", "Gun Crew",          "I: one deck's side  II: whole side", 2,
            List.of(new CostEntry(Items.GUNPOWDER, 8), new CostEntry(ModItems.ROPE, 4), new CostEntry(Items.IRON_INGOT, 6))),
        // Blocks players may add to the ship after it is built (world/ShipBuilding.SLOTS)
        new Def("carpentry", "Ship's Carpenter", "Build slots 3 > 8 > 16 > 32 > 64", 4,
            List.of(new CostEntry(Items.STICK, 16), new CostEntry(ModItems.ROPE, 4), new CostEntry(Items.IRON_INGOT, 4))),
        // Grinds away the seabed in the hull's path while sailing (world/KeelBreaker): I = sand, gravel, coral, silt;
        // II = natural rock too. Never land: only blocks in columns whose top is at or below the water surface.
        new Def(KeelBreaker.KEY, "Keelbreaker", "I: sand, coral, silt  II: rock too", 2,
            List.of(new CostEntry(Items.COPPER_INGOT, 12), new CostEntry(Items.IRON_INGOT, 10)))
    );

    public static Def find(String key) {
        return ALL.stream().filter(d -> d.key().equals(key)).findFirst().orElse(null);
    }

    public static boolean canAfford(PlayerEntity player, Def def, int currentLevel) {
        if (currentLevel >= def.maxLevel()) return false;
        for (CostEntry c : def.costPerLevel()) {
            if (player.getInventory().count(c.item()) < count(player, c)) return false;
        }
        return true;
    }

    public static void consume(PlayerEntity player, Def def) {
        for (CostEntry c : def.costPerLevel()) {
            int remaining = count(player, c);
            for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
                var stack = player.getInventory().getStack(i);
                if (!stack.isOf(c.item())) continue;
                int take = Math.min(stack.getCount(), remaining);
                stack.decrement(take);
                remaining -= take;
            }
        }
    }

    /** Haggler trims the materials too. */
    public static int count(PlayerEntity player, CostEntry c) { return net.get900.pixelpirates.world.SkillEffects.haggle(player, c.count()); }

    public static String costString(Def def) {
        var sb = new StringBuilder();
        for (CostEntry c : def.costPerLevel()) {
            if (!sb.isEmpty()) sb.append(" + ");
            sb.append(c.count()).append("x ").append(c.item().getName().getString());
        }
        return sb.toString();
    }
}
