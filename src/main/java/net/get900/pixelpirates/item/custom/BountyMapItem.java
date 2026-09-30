package net.get900.pixelpirates.item.custom;

import net.get900.pixelpirates.world.AiShipController;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.internal.world.VsiServerShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BountyMapItem extends Item {

    public static final int COST = 10;

    public BountyMapItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient) return TypedActionResult.pass(user.getStackInHand(hand));

        ItemStack stack = user.getStackInHand(hand);

        if (AiShipController.AI_SHIPS.isEmpty()) {
            user.sendMessage(Text.literal("§7No enemy ships are currently active in these waters."), true);
            return TypedActionResult.fail(stack);
        }

        VsiServerShipWorld sw = VSGameUtilsKt.getShipObjectWorld((ServerWorld) world);
        if (sw == null) {
            user.sendMessage(Text.literal("§cCould not locate ship data."), true);
            return TypedActionResult.fail(stack);
        }

        List<Long> ids = new ArrayList<>(AiShipController.AI_SHIPS.keySet());
        Random rng = new Random();
        long targetId = ids.get(rng.nextInt(ids.size()));

        ServerShip ship = sw.getLoadedShips().getById(targetId);
        if (ship == null) {
            user.sendMessage(Text.literal("§7The target ship could not be located — try again."), true);
            return TypedActionResult.fail(stack);
        }

        Vector3dc pos = ship.getTransform().getPositionInWorld();
        int tx = (int) pos.x();
        int tz = (int) pos.z();

        AiShipController.AiShipData shipData = AiShipController.AI_SHIPS.get(targetId);
        String blueprint = (shipData != null && !shipData.blueprintName.isEmpty())
            ? shipData.blueprintName : "unknown";
        String shipLabel = Character.toUpperCase(blueprint.charAt(0)) + blueprint.substring(1);

        user.sendMessage(Text.literal("§c[X] Bounty: §f" + shipLabel), false);
        user.sendMessage(Text.literal("§7Last seen near: §e" + tx + "§7, §e" + tz), false);
        user.sendMessage(Text.literal("§7Sink it for your reward!"), false);

        stack.decrement(1);
        return TypedActionResult.success(stack);
    }
}
