package net.get900.pixelpirates.homestead.parrot;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * PARROT CRATE (parrot types phase 1): a wicker travelling crate holding one parrot of a set type ("PPType" on the stack).
 * Comes out of RARE (rare or better) and LEGENDARY (very rare or better) treasure (TreasureLoot) - with the aviary, the
 * only places the top tiers turn up. Use it on a block: the parrot steps out, already yours.
 */
public class ParrotCrateItem extends Item {
    public ParrotCrateItem(Settings s) { super(s); }

    public static ItemStack of(Item item, ParrotTypes.PType t) {
        ItemStack s = new ItemStack(item);
        s.getOrCreateNbt().putString("PPType", t.id());
        return s;
    }

    @Nullable
    static ParrotTypes.PType type(ItemStack s) {
        return s.hasNbt() ? ParrotTypes.byId(s.getNbt().getString("PPType")) : null;
    }

    @Override
    public Text getName(ItemStack s) {
        ParrotTypes.PType t = type(s);
        return t == null ? super.getName(s) : Text.translatable(getTranslationKey()).append(" (" + t.name() + ")");
    }

    @Override
    public void appendTooltip(ItemStack s, @Nullable World w, List<Text> tip, TooltipContext ctx) {
        ParrotTypes.PType t = type(s);
        if (t == null) return;
        tip.add(Text.literal(t.tier().label).formatted(t.tier().colour, Formatting.BOLD));
        tip.add(Text.literal("Use on a block to let it out - it's already yours").formatted(Formatting.GRAY));
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        ParrotTypes.PType t = type(ctx.getStack());
        if (t == null) return ActionResult.PASS;
        if (!(ctx.getWorld() instanceof ServerWorld w)) return ActionResult.SUCCESS;
        if (ctx.getPlayer() != null && ParrotCollection.owns(w.getServer(), ctx.getPlayer().getUuid(), t.id())) {   // one of each
            ctx.getPlayer().sendMessage(Text.literal("You already have a " + t.name() + " - this one would rather stay in its crate.").formatted(Formatting.YELLOW), true);
            return ActionResult.FAIL;
        }
        BlockPos at = ctx.getBlockPos().offset(ctx.getSide());
        ParrotEntity p = EntityType.PARROT.create(w);
        if (p == null) return ActionResult.FAIL;
        p.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, ctx.getPlayerYaw(), 0f);
        ParrotTypes.apply(p, t);
        PlayerEntity pl = ctx.getPlayer();
        if (pl != null) p.setOwner(pl);
        p.setPersistent();
        w.spawnEntity(p);
        w.playSound(null, at, SoundEvents.ENTITY_PARROT_AMBIENT, SoundCategory.NEUTRAL, 1f, 1.2f);
        if (pl == null || !pl.getAbilities().creativeMode) ctx.getStack().decrement(1);
        return ActionResult.CONSUME;
    }
}
