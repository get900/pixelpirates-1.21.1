package net.get900.pixelpirates.item.custom;

import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.world.ShipRegistryState;
import net.get900.pixelpirates.world.ShipSchematic;
import net.get900.pixelpirates.world.ShipSpawner;
import net.get900.pixelpirates.world.ShipSteeringManager;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.get900.pixelpirates.network.ModNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;

import java.util.List;

public class ShipBlueprintItem extends Item {

    private static final String TAG_SHIP_NAME = "ShipName";

    public ShipBlueprintItem(Settings settings) {
        super(settings);
    }

    // Sneak + right-click on an assembled ship helm → save blueprint.
    // Item's useOnBlock() runs before the block's onUse(), so this intercepts
    // that interaction and returns SUCCESS before the helm block can handle it.
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        PlayerEntity player = context.getPlayer();
        BlockPos pos = context.getBlockPos();
        if (player == null) return ActionResult.PASS;

        // Only trigger when sneak-clicking an assembled ship helm
        if (!world.getBlockState(pos).isOf(ModBlocks.SHIP_HELM)) return ActionResult.PASS;
        if (!player.isSneaking()) return ActionResult.PASS;

        if (world.isClient) return ActionResult.SUCCESS;

        if (!ValkyrienSkies.isBlockInShipyard(world, pos.getX(), pos.getY(), pos.getZ())) {
            player.sendMessage(Text.literal("§cAssemble the ship first (sneak + right-click helm without a blueprint)."), true);
            return ActionResult.FAIL;
        }

        Ship ship = ValkyrienSkies.getShipManagingBlock(world, pos.getX(), pos.getY(), pos.getZ());
        if (ship == null) {
            player.sendMessage(Text.literal("§cCould not locate ship."), true);
            return ActionResult.FAIL;
        }

        ItemStack stack = context.getStack();
        String shipName = stack.hasCustomName()
            ? ShipSchematic.sanitize(stack.getName().getString())
            : "ship_" + Long.toHexString(ship.getId() & 0xFFFL);

        try {
            ShipSchematic.save((ServerWorld) world, ship, pos, shipName);
            NbtCompound tag = stack.getOrCreateNbt();
            tag.putString(TAG_SHIP_NAME, shipName);
            player.sendMessage(Text.literal("§a+ Blueprint saved: §f" + shipName), true);
        } catch (Exception e) {
            player.sendMessage(Text.literal("§cSave failed: " + e.getMessage()), true);
        }

        return ActionResult.SUCCESS;
    }

    // Right-click (in air or on any non-helm block) → spawn ship 20 blocks ahead
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack);

        // Prefer the stamped ShipName tag (set when saving); fall back to the
        // item's custom name so blueprints can be used in new worlds by just
        // naming the item at an anvil to match the saved file.
        NbtCompound tag = stack.getNbt();
        String shipName;
        if (tag != null && tag.contains(TAG_SHIP_NAME)) {
            shipName = tag.getString(TAG_SHIP_NAME);
        } else if (stack.hasCustomName()) {
            shipName = ShipSchematic.sanitize(stack.getName().getString());
        } else {
            // No name — open the blueprint selection menu so the player can pick one
            ModNetworking.sendBlueprintMenu((ServerPlayerEntity) player);
            return TypedActionResult.success(stack);
        }

        Vec3d look = player.getRotationVec(1.0f);
        // Horizontal offset only; spawn at fixed Y=75 so it assembles above water
        BlockPos spawnOrigin = BlockPos.ofFloored(
            player.getX() + look.x * 20,
            75,
            player.getZ() + look.z * 20
        );

        ShipRegistryState registry = ShipRegistryState.get(world.getServer().getOverworld());

        // 1-ship limit — same check as Shipwright
        Long ownedShipId = registry.getOwnedShip(player.getUuid());
        if (ownedShipId != null) {
            if (ShipSteeringManager.MAST_COUNTS.containsKey(ownedShipId)) {
                player.sendMessage(Text.literal(
                    "§cYou already own a ship. Use /scuttle to destroy it first."), true);
                return TypedActionResult.fail(stack);
            }
            registry.clearOwnership(player.getUuid());
        }

        try {
            ShipSchematic schematic = ShipSchematic.load(shipName);
            ServerShip ship = ShipSpawner.spawn((ServerWorld) world, schematic, spawnOrigin);
            int mastCount = ShipSteeringManager.MAST_COUNTS.getOrDefault(ship.getId(), 1);
            registry.saveMastCount(ship.getId(), mastCount);
            registry.setOwnership(player.getUuid(), ship.getId());
            player.sendMessage(Text.literal(
                "§a+ §f" + shipName + " §7spawned at " + spawnOrigin.toShortString() + " (ship ID " + ship.getId() + ")"), true);
        } catch (Exception e) {
            player.sendMessage(Text.literal("§cSpawn failed: " + e.getMessage()), true);
        }

        return TypedActionResult.success(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        NbtCompound tag = stack.getNbt();
        if (tag != null && tag.contains(TAG_SHIP_NAME)) {
            tooltip.add(Text.literal("§7Blueprint: §f" + tag.getString(TAG_SHIP_NAME)));
        } else if (stack.hasCustomName()) {
            tooltip.add(Text.literal("§7Will load: §f" + ShipSchematic.sanitize(stack.getName().getString())));
        }
        tooltip.add(Text.literal("§7Right-click §eto spawn 20 blocks ahead"));
        tooltip.add(Text.literal("§6Sneak + right-click helm §7to save ship")  );
    }
}
