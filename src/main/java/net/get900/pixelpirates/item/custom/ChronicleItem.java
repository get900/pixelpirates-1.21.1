package net.get900.pixelpirates.item.custom;

import net.get900.pixelpirates.world.Chronicle;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The Weathered Chronicle: right-click to read. The server sends the reader's pages (Chronicle#open), the client opens the book. */
public class ChronicleItem extends Item {
    public ChronicleItem(Settings settings) { super(settings); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient() && user instanceof ServerPlayerEntity sp) Chronicle.open(sp);
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("The lore of the seas, and what waits in them.").formatted(Formatting.GRAY, Formatting.ITALIC));
        tooltip.add(Text.literal("New pages ink themselves in as you hunt.").formatted(Formatting.DARK_GRAY));
    }
}
