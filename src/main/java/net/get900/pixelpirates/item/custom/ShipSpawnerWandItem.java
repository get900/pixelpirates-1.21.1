package net.get900.pixelpirates.item.custom;

import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.world.AiShipController;
import net.get900.pixelpirates.world.ShipSchematic;
import net.get900.pixelpirates.world.ShipSpawner;
import net.get900.pixelpirates.world.faction.Faction;
import net.get900.pixelpirates.world.faction.FactionManager;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.ServerShip;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Admin testing item: spawns AI ships directly from blueprint.
 *
 * Right-click         → spawn the selected blueprint 30 blocks ahead
 * Sneak + right-click → cycle to the next saved blueprint
 *
 * The selected blueprint and faction override are stored in item NBT.
 * Op-only: given out via /give, not in the normal creative tab.
 */
public class ShipSpawnerWandItem extends Item {

    private static final String NBT_BLUEPRINT = "SelectedBlueprint";
    private static final String NBT_FACTION   = "FactionOverride";

    public ShipSpawnerWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient) return TypedActionResult.success(user.getStackInHand(hand));

        ServerWorld sw = (ServerWorld) world;
        ServerPlayerEntity player = (ServerPlayerEntity) user;
        ItemStack stack = user.getStackInHand(hand);

        List<String> blueprints = ShipSchematic.listNames();
        if (blueprints.isEmpty()) {
            player.sendMessage(Text.literal("§c[SpawnWand] No blueprints saved. Use /ppai spawn first."), true);
            return TypedActionResult.success(stack);
        }

        if (user.isSneaking()) {
            // Cycle blueprint
            String current = getSelectedBlueprint(stack);
            int idx = blueprints.indexOf(current);
            int next = (idx + 1) % blueprints.size();
            String nextBlueprint = blueprints.get(next);
            stack.getOrCreateNbt().putString(NBT_BLUEPRINT, nextBlueprint);
            player.sendMessage(Text.literal("§e[SpawnWand] Blueprint: §f" + nextBlueprint
                + " §7(" + (next + 1) + "/" + blueprints.size() + ")"), true);
            return TypedActionResult.success(stack);
        }

        // Spawn ship 30 blocks ahead in look direction
        String blueprint = getSelectedBlueprint(stack);
        if (blueprint.isEmpty() || !blueprints.contains(blueprint)) {
            blueprint = blueprints.get(0);
            stack.getOrCreateNbt().putString(NBT_BLUEPRINT, blueprint);
        }

        Vec3d look = user.getRotationVec(1.0f);
        BlockPos origin = BlockPos.ofFloored(
            user.getX() + look.x * 30, 75, user.getZ() + look.z * 30);

        try {
            ShipSchematic schematic = ShipSchematic.load(blueprint);
            ServerShip ship = ShipSpawner.spawn(sw, schematic, origin);

            Matrix4d worldToShip = new Matrix4d(ship.getTransform().getShipToWorld()).invert();
            List<Vector3d> cannons = new ArrayList<>();
            for (ShipSchematic.Entry e : schematic.getEntries()) {
                net.minecraft.block.BlockState bs = ShipSchematic.restoreState(e.stateNbt());
                if (bs != null && bs.isOf(ModBlocks.SHIP_CANNON)) {
                    BlockPos cp = origin.add(e.relPos());
                    Vector3d wpos = new Vector3d(cp.getX() + 0.5, cp.getY() + 0.5, cp.getZ() + 0.5);
                    cannons.add(worldToShip.transformPosition(wpos, new Vector3d()));
                }
            }

            AiShipController.registerAiShip(ship.getId(), sw, cannons, blueprint);
            player.sendMessage(Text.literal(
                "§a[SpawnWand] §f" + blueprint + " §a(ID " + ship.getId() + ") spawned at "
                + origin.getX() + ", " + origin.getZ()), true);
        } catch (Exception e) {
            player.sendMessage(Text.literal("§c[SpawnWand] Failed: " + e.getMessage()), false);
        }

        return TypedActionResult.success(stack);
    }

    private static String getSelectedBlueprint(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt != null && nbt.contains(NBT_BLUEPRINT) ? nbt.getString(NBT_BLUEPRINT) : "";
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world,
                               List<Text> tooltip, TooltipContext context) {
        String bp = getSelectedBlueprint(stack);
        if (!bp.isEmpty()) {
            tooltip.add(Text.literal("§7Blueprint: §f" + bp));
        } else {
            tooltip.add(Text.literal("§8No blueprint — sneak+right-click to select"));
        }
        tooltip.add(Text.literal("§8Right-click: spawn | Sneak: cycle blueprint"));
    }
}
