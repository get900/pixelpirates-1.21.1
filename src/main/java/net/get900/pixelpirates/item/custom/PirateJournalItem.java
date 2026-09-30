package net.get900.pixelpirates.item.custom;

import net.get900.pixelpirates.world.PirateLevelManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class PirateJournalItem extends Item {

    public PirateJournalItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient() && user instanceof ServerPlayerEntity sp) {
            PirateLevelManager.sendOpenSkillScreen(sp);
        }
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient());
    }
}
