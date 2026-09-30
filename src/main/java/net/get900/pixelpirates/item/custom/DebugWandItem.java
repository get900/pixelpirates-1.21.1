package net.get900.pixelpirates.item.custom;

import net.get900.pixelpirates.world.AiShipController;
import net.get900.pixelpirates.world.ShipHealthState;
import net.get900.pixelpirates.world.ShipRegistryState;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.internal.world.VsiServerShipWorld;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Admin testing item. Right-click to list all nearby AI ships with their faction,
 * state, HP, and distance. Right-click on a block that's part of a VS2 ship to
 * see that ship's detailed info (also works for player ships).
 *
 * Op-only: given out via /give, not in the normal creative tab.
 */
public class DebugWandItem extends Item {

    private static final double SCAN_RADIUS = 250.0;

    public DebugWandItem(Settings settings) {
        super(settings);
    }

    /** Right-click in air: list all AI ships in range. */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient) return TypedActionResult.success(user.getStackInHand(hand));
        ServerWorld sw = (ServerWorld) world;
        ServerPlayerEntity player = (ServerPlayerEntity) user;

        Vec3d playerPos = player.getPos();

        if (AiShipController.AI_SHIPS.isEmpty()) {
            player.sendMessage(Text.literal("§7[DebugWand] No active AI ships."), false);
            return TypedActionResult.success(user.getStackInHand(hand));
        }

        // Collect VS2 ship positions for distance lookup
        VsiServerShipWorld vsWorld = VSGameUtilsKt.getShipObjectWorld(sw);

        List<String> lines = new ArrayList<>();
        lines.add("§6=== AI Ships (" + AiShipController.AI_SHIPS.size() + " total) ===");

        for (AiShipController.AiShipData data : AiShipController.AI_SHIPS.values()) {
            if (data.world != sw) {
                lines.add("§8  [" + data.shipId + "] §7(other world)");
                continue;
            }

            double dist = Double.NaN;
            String posStr = "?";
            if (vsWorld != null) {
                var vsShip = vsWorld.getLoadedShips().getById(data.shipId);
                if (vsShip != null) {
                    Vector3d p = new Vector3d(vsShip.getTransform().getPositionInWorld());
                    dist = Math.sqrt(Math.pow(p.x - playerPos.x, 2) + Math.pow(p.z - playerPos.z, 2));
                    posStr = "(" + (int)p.x + ", " + (int)p.z + ")";
                }
            }

            int hp = ShipHealthState.get(sw).getHealth(data.shipId);
            int maxHp = ShipRegistryState.get(sw.getServer().getOverworld())
                            .getEffectiveMaxHp(data.shipId);
            String hpColor = hp > maxHp * 0.5 ? "§a" : (hp > maxHp * 0.2 ? "§e" : "§c");
            String distStr = Double.isNaN(dist) ? "??" : String.valueOf((int)dist);

            lines.add(String.format("§f  [%d] §e%s §7| %s §8%s/%s HP §7| state: §b%s §7| %s §7| dist: §f%s",
                data.shipId,
                data.faction.id,
                hpColor,
                hp,
                maxHp,
                data.state.name(),
                posStr,
                distStr + "m"
            ));
        }

        for (String line : lines) player.sendMessage(Text.literal(line), false);
        return TypedActionResult.success(user.getStackInHand(hand));
    }

    /** Right-click on block: show detailed info about the ship that block belongs to. */
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (context.getWorld().isClient) return ActionResult.SUCCESS;
        ServerWorld sw = (ServerWorld) context.getWorld();
        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;
        BlockPos pos = context.getBlockPos();

        // Check if this block is part of a VS2 ship
        Ship vsShip = ValkyrienSkies.getShipManagingBlock(sw, pos.getX(), pos.getY(), pos.getZ());
        if (vsShip == null) {
            player.sendMessage(Text.literal("§7[DebugWand] No VS2 ship at this position."), false);
            return ActionResult.SUCCESS;
        }

        long shipId = vsShip.getId();
        ShipRegistryState reg = ShipRegistryState.get(sw.getServer().getOverworld());
        int hp    = ShipHealthState.get(sw).getHealth(shipId);
        int maxHp = reg.getEffectiveMaxHp(shipId);
        String blueprint = reg.getBlueprintName(shipId);

        // Check if it's an AI ship
        AiShipController.AiShipData aiData = AiShipController.AI_SHIPS.get(shipId);

        StringBuilder sb = new StringBuilder("§6=== Ship Info — ID " + shipId + " ===\n");
        sb.append("§7Blueprint: §f").append(blueprint != null ? blueprint : "unknown").append("\n");
        sb.append("§7HP: §f").append(hp).append("/").append(maxHp).append("\n");

        if (aiData != null) {
            sb.append("§7Type: §cAI Ship\n");
            sb.append("§7Faction: §f").append(aiData.faction.displayName).append("\n");
            sb.append("§7State: §b").append(aiData.state.name()).append("\n");
            sb.append("§7Crew UUIDs: §f").append(aiData.crewEntityIds.size()).append("\n");
            sb.append("§7Cannons: §f").append(aiData.cannonShipPositions.size()).append("\n");
            sb.append("§7Reload timer: §f").append(aiData.reloadTimer).append("\n");
            sb.append("§7Idle ticks: §f").append(aiData.idleTicks).append("\n");
        } else {
            // Player ship
            sb.append("§7Type: §aPlayer Ship\n");
            var upgrades = reg.getShipUpgrades(shipId);
            if (!upgrades.isEmpty()) {
                sb.append("§7Upgrades:\n");
                upgrades.forEach((k, v) -> sb.append("  §8").append(k).append(": §f").append(v).append("\n"));
            }
        }

        player.sendMessage(Text.literal(sb.toString()), false);
        return ActionResult.SUCCESS;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world,
                               List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("§7Right-click: list AI ships in range"));
        tooltip.add(Text.literal("§7Right-click block: inspect specific ship"));
    }
}
