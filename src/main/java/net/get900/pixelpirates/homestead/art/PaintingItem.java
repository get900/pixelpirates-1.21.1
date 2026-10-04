package net.get900.pixelpirates.homestead.art;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A finished painting: right-click a wall to hang it (CustomPaintingEntity). Carries {Size, Title, Author, Pixels}. */
public class PaintingItem extends Item {
    public PaintingItem(Settings s) { super(s); }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        Direction side = ctx.getSide();
        if (side.getAxis().isVertical()) return ActionResult.FAIL;
        ItemStack stack = ctx.getStack();
        NbtCompound art = stack.getSubNbt("Art");
        if (art == null || !art.contains("Pixels")) return ActionResult.FAIL;
        PlayerEntity p = ctx.getPlayer();
        BlockPos pos = ctx.getBlockPos().offset(side);
        if (p != null && !p.canPlaceOn(pos, side, stack)) return ActionResult.FAIL;
        World w = ctx.getWorld();
        CustomPaintingEntity e = new CustomPaintingEntity(w, pos, side, art);
        if (!e.canStayAttached()) return ActionResult.FAIL;
        if (!w.isClient) {
            e.onPlace();
            w.spawnEntity(e);
        }
        if (p == null || !p.getAbilities().creativeMode) stack.decrement(1);
        return ActionResult.success(w.isClient);
    }

    @Override
    public Text getName(ItemStack stack) {
        NbtCompound art = stack.getSubNbt("Art");
        return art != null && !art.getString("Title").isEmpty() ? Text.literal("\"" + art.getString("Title") + "\"") : super.getName(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        NbtCompound art = stack.getSubNbt("Art");
        if (art == null) { tooltip.add(Text.literal("A blank painting?").formatted(Formatting.GRAY)); return; }
        tooltip.add(Text.literal("by " + art.getString("Author")).formatted(Formatting.GRAY, Formatting.ITALIC));
        tooltip.add(Text.literal(Art.Size.of(art.getByte("Size")).label + " blocks").formatted(Formatting.DARK_GRAY));
    }
}
