package net.get900.pixelpirates.item.custom;

import net.get900.pixelpirates.entity.mob.BossProgression;
import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.world.dimension.ModDimensions;
import net.get900.pixelpirates.world.leviathan.LeviathanPorts;
import net.get900.pixelpirates.world.leviathan.LeviathanRoute;
import net.get900.pixelpirates.world.leviathan.LeviathanState;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Rarity;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * SEAFARER'S TOKEN (Materials & Gear Ladder 6.3, 2026-10-01): an extremely rare late-game currency (bosses 5-10,
 * legendary treasure, the deep-abyss treasure chests). Spend it on:
 *  - REBUILDING a port the Leviathan destroyed: use {@link #REBUILD_COST} tokens while standing in the ruins;
 *  - a TOKEN SPIN at a roulette table (RouletteTableBlock): one random armor piece or weapon up to your boss tier.
 */
public class SeafarersTokenItem extends Item {
    public static final int REBUILD_COST = 3;

    public SeafarersTokenItem(Settings settings) {
        super(settings.rarity(Rarity.RARE));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!(world instanceof ServerWorld sw) || !(user instanceof ServerPlayerEntity sp)) return TypedActionResult.success(stack, true);
        if (!sw.getRegistryKey().equals(ModDimensions.PIXEL_PIRATES_WORLD)) return TypedActionResult.pass(stack);
        LeviathanState st = LeviathanState.get(sw);
        var route = LeviathanRoute.of(sw.getSeed());
        for (int i = 0; i < LeviathanPorts.IDS.length; i++) {
            var site = route.byId(LeviathanPorts.IDS[i]);
            if (site == null || site.dist(user.getX(), user.getZ()) > site.radius() + 16) continue;
            if (!st.portRuined[i]) {
                sp.sendMessage(Text.literal(LeviathanPorts.NAMES[i] + " stands - there is nothing to rebuild.").formatted(Formatting.GRAY), true);
                return TypedActionResult.fail(stack);
            }
            int have = user.getInventory().count(ModItems.SEAFARERS_TOKEN);
            if (!user.isCreative() && have < REBUILD_COST) {
                sp.sendMessage(Text.literal("Rebuilding " + LeviathanPorts.NAMES[i] + " takes " + REBUILD_COST
                        + " Seafarer's Tokens (you have " + have + ").").formatted(Formatting.RED), true);
                return TypedActionResult.fail(stack);
            }
            if (!user.isCreative()) user.getInventory().remove(s -> s.isOf(ModItems.SEAFARERS_TOKEN), REBUILD_COST, user.playerScreenHandler.getCraftingInput());
            LeviathanPorts.restore(sw, i);
            sw.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_BELL_USE, SoundCategory.PLAYERS, 2.0f, 0.8f);
            sw.getServer().getPlayerManager().broadcast(Text.literal("[~] " + user.getName().getString() + " has paid to rebuild "
                    + LeviathanPorts.NAMES[i] + ". The port rises again.").formatted(Formatting.GOLD), false);
            return TypedActionResult.success(stack, false);
        }
        sp.sendMessage(Text.literal("Spend tokens to rebuild a port the Leviathan destroyed, or at a roulette table for a Token Spin.")
                .formatted(Formatting.GRAY), true);
        return TypedActionResult.pass(stack);
    }

    // ------------------------------------------------------------------ TOKEN SPIN (roulette table)
    /** One random armor piece or weapon from everything up to the player's boss tier. */
    public static ItemStack spin(PlayerEntity p) {
        List<Item> pool = gearUpTo(BossProgression.progress(p));
        return new ItemStack(pool.get(p.getRandom().nextInt(pool.size())));
    }

    /** The gear ladder, cut at the stage this many bosses beaten have reached. */
    static List<Item> gearUpTo(int bossesBeaten) {
        List<Item> out = new ArrayList<>(List.of(ModItems.CASTAWAY_HELMET, ModItems.CASTAWAY_CHESTPLATE, ModItems.CASTAWAY_LEGGINGS,
                ModItems.CASTAWAY_BOOTS, ModItems.NAVY_OFFICER_HELMET, ModItems.NAVY_OFFICER_CHESTPLATE, ModItems.NAVY_OFFICER_LEGGINGS,
                ModItems.NAVY_OFFICER_BOOTS, ModItems.MARLINSPIKE, ModItems.BOARDING_SABRE, ModItems.CUTLASS, ModItems.BOARDING_AXE));
        if (bossesBeaten >= 1) out.addAll(List.of(ModItems.CORSAIR_HELMET, ModItems.CORSAIR_CHESTPLATE, ModItems.CORSAIR_LEGGINGS,
                ModItems.CORSAIR_BOOTS, ModItems.NAVAL_RAPIER, ModItems.OFFICERS_SABRE));
        if (bossesBeaten >= 2) out.addAll(List.of(ModItems.ASHEN_HELMET, ModItems.ASHEN_CHESTPLATE, ModItems.ASHEN_LEGGINGS,
                ModItems.ASHEN_BOOTS, ModItems.CORSAIR_CUTLASS, ModItems.BOARDING_PIKE, ModItems.EMBERBRAND));
        if (bossesBeaten >= 3) out.addAll(List.of(ModItems.CURSED_BONE_HELMET, ModItems.CURSED_BONE_CHESTPLATE,
                ModItems.CURSED_BONE_LEGGINGS, ModItems.CURSED_BONE_BOOTS, ModItems.SOULRENDER, ModItems.WRAITHBLADE));
        if (bossesBeaten >= 4) out.addAll(List.of(ModItems.KRAKEN_SCALE_HELMET, ModItems.KRAKEN_SCALE_CHESTPLATE,
                ModItems.KRAKEN_SCALE_LEGGINGS, ModItems.KRAKEN_SCALE_BOOTS, ModItems.KRAKEN_FANG, ModItems.STORMCALLER, ModItems.ABYSSAL_HARPOON));
        // boss sets: tier I after boss 1, II after 3, III after 5, IV after 7, V after 9
        for (int tier = 1; tier <= 5; tier++)
            if (bossesBeaten >= tier * 2 - 1) out.addAll(List.of(ModItems.bossSet(tier)));
        return out;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Rare currency of the deep seas").formatted(Formatting.GOLD));
        tooltip.add(Text.literal(REBUILD_COST + " rebuild a port the Leviathan destroyed (use it in the ruins)").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("1 buys a Token Spin at a roulette table").formatted(Formatting.GRAY));
    }
}
