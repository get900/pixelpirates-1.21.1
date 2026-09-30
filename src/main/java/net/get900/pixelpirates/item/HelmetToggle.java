package net.get900.pixelpirates.item;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.get900.pixelpirates.PixelPirates;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * HIDE HELMET (any helmet or head item, ours or vanilla): a flag on the worn head ItemStack's NBT, so it syncs to every
 * client with the equipment and survives relogs. Toggled by the "Toggle helmet visibility" key (H, a C2S packet) or
 * /pphelmet. Rendering is skipped client-side by mixin/client HelmetHide* (the armor layer + the head-item layer).
 * The helmet still protects - it is only not drawn.
 */
public final class HelmetToggle {
    private HelmetToggle() {}

    public static final String KEY = "PPHidden";
    public static final Identifier PACKET = PixelPirates.id("toggle_helmet");

    public static boolean hidden(ItemStack stack) {
        return !stack.isEmpty() && stack.hasNbt() && stack.getNbt().getBoolean(KEY);
    }

    public static int toggle(ServerPlayerEntity p) {
        ItemStack head = p.getEquippedStack(EquipmentSlot.HEAD);
        if (head.isEmpty()) {
            p.sendMessage(Text.literal("You aren't wearing anything on your head.").formatted(Formatting.GRAY), true);
            return 0;
        }
        boolean hide = !hidden(head);
        ItemStack copy = head.copy();
        if (hide) copy.getOrCreateNbt().putBoolean(KEY, true);
        else if (copy.hasNbt()) { copy.getNbt().remove(KEY); if (copy.getNbt().isEmpty()) copy.setNbt(null); }
        p.equipStack(EquipmentSlot.HEAD, copy);                             // re-equip so the change syncs to everyone
        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.PLAYERS, 0.6f, hide ? 0.8f : 1.2f);
        p.sendMessage(Text.literal(hide ? "Helmet hidden (it still protects you)." : "Helmet shown.").formatted(Formatting.GRAY), true);
        return 1;
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(PACKET, (server, player, handler, buf, sender) -> server.execute(() -> toggle(player)));
    }
}
