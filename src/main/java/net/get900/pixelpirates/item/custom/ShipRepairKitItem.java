package net.get900.pixelpirates.item.custom;

import net.get900.pixelpirates.util.AdvancementHelper;
import net.get900.pixelpirates.world.PlayerProgressionManager;
import net.get900.pixelpirates.world.ShipHealthState;
import net.get900.pixelpirates.world.ShipRegistryState;
import net.get900.pixelpirates.world.ShipSteeringManager;
import net.get900.pixelpirates.world.ShipTiers;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;

import java.util.List;

public class ShipRepairKitItem extends Item {

    private static final int REPAIR_AMOUNT = 100;

    public ShipRepairKitItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (context.getWorld().isClient) return ActionResult.SUCCESS;

        ServerWorld world   = (ServerWorld) context.getWorld();
        BlockPos pos        = context.getBlockPos();
        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;

        Ship ship = ValkyrienSkies.getShipManagingBlock(world, pos.getX(), pos.getY(), pos.getZ());
        if (ship == null) {
            player.sendMessage(Text.literal("§cRight-click a ship block to inspect or repair the hull."), true);
            return ActionResult.FAIL;
        }

        ShipHealthState health = ShipHealthState.get(world);

        if (player.isSneaking()) {
            // Sneak + right-click: inspect only, no consume
            int effMax = ShipRegistryState.get(world.getServer().getOverworld()).getEffectiveMaxHp(ship.getId());
            showHp(player, health.getHealth(ship.getId()), effMax, "§e[~] Hull status:");
            return ActionResult.SUCCESS;
        }

        // Regular right-click: repair
        long shipId = ship.getId();
        int before = health.getHealth(shipId);
        int effMax = ShipRegistryState.get(world.getServer().getOverworld()).getEffectiveMaxHp(shipId);
        if (before >= effMax) {
            showHp(player, before, effMax, "§a[~] Hull is already full!");
            return ActionResult.FAIL; // full — don't consume
        }

        // If this is a derelict AI ship, claim it and cancel deletion timer
        if (ShipSteeringManager.DERELICT_EXPIRY.containsKey(shipId)) {
            ShipRegistryState reg = ShipRegistryState.get(world.getServer().getOverworld());
            if (!player.isCreative()) {
                Long ownedShipId = reg.getOwnedShip(player.getUuid());
                if (ownedShipId != null) {
                    if (ShipSteeringManager.MAST_COUNTS.containsKey(ownedShipId)) {
                        player.sendMessage(Text.literal(
                            "§cYou already own a ship. Scuttle it first before claiming this one."), true);
                        return ActionResult.FAIL;
                    }
                    reg.clearOwnership(player.getUuid());
                }
            }
            ShipSteeringManager.DERELICT_EXPIRY.remove(shipId);
            ShipSteeringManager.DERELICT_WORLDS.remove(shipId);
            int mastCount = ShipSteeringManager.MAST_COUNTS.getOrDefault(shipId, 1);
            reg.saveMastCount(shipId, mastCount);
            reg.setOwnership(player.getUuid(), shipId);

            // zones open only through the boss chain - claiming a derelict no longer unlocks one (2026-10-01)
            ShipSteeringManager.DERELICT_BLUEPRINT.remove(shipId);

            player.sendMessage(Text.literal("§aDerelict ship claimed!"), false);
        }

        int after = health.repair(world, shipId, REPAIR_AMOUNT);
        showHp(player, after, effMax, "§a[~] Hull repaired!");

        if (player instanceof net.minecraft.server.network.ServerPlayerEntity spe) {
            AdvancementHelper.grant(spe, "she_holds");
        }

        if (!player.isCreative()) {
            context.getStack().decrement(1);
        }

        return ActionResult.SUCCESS;
    }

    private static void showHp(PlayerEntity player, int hp, int maxHp, String prefix) {
        int pct = maxHp > 0 ? (hp * 100) / maxHp : 0;
        String color = hp > maxHp * 0.6 ? "§a"
                     : hp > maxHp * 0.25 ? "§e"
                     : "§c";
        player.sendMessage(Text.literal(
            prefix + " " + hpBar(hp, maxHp) + " " + color + hp + "§7/§f" + maxHp + " §7(" + pct + "%)"
        ), true);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("§7Right-click ship block: §arepair §7(" + REPAIR_AMOUNT + " HP)"));
        tooltip.add(Text.literal("§7Sneak + right-click: §einspect hull HP"));
    }

    private static String hpBar(int hp, int maxHp) {
        int filled = maxHp > 0 ? Math.round((float) hp / maxHp * 10) : 0;
        String color = hp > maxHp * 0.6 ? "§a"
                     : hp > maxHp * 0.25 ? "§e"
                     : "§c";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(i < filled ? color + "█" : "§8░");
        }
        return sb.toString();
    }
}
