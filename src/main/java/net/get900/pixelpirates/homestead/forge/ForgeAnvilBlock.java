package net.get900.pixelpirates.homestead.forge;

import net.get900.pixelpirates.homestead.furniture.FurnitureBlock;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * FORGE ANVIL - forging and mending without a screen. Right-click with:
 *  - a weapon / tool / armour piece: lay it on the anvil (the piece being worked);
 *  - anything else: add it as material (one; sneak = the whole stack), up to four kinds;
 *  - the SMITH'S HAMMER: strike. Needs a lit Forge Hearth within 2 blocks. A forging pattern (Forging.PATTERNS) takes 6
 *    strikes (4 with bellows touching the hearth), a mending 3 (each repair material restores a third);
 *  - an empty hand: take everything back.
 * The action bar says what is on the anvil and what it can become.
 */
public class ForgeAnvilBlock extends FurnitureBlock implements BlockEntityProvider {
    public ForgeAnvilBlock(Settings s, double[]... boxes) { super(s, true, boxes); }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new ForgeAnvilBlockEntity(pos, state); }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND || !(world.getBlockEntity(pos) instanceof ForgeAnvilBlockEntity be)) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        ItemStack held = player.getStackInHand(hand);
        boolean creative = player.getAbilities().creativeMode;

        if (held.isEmpty()) {                                                         // take everything back
            if (be.piece.isEmpty() && be.mats.isEmpty()) { player.sendMessage(hint(), true); return ActionResult.SUCCESS; }
            if (!be.piece.isEmpty()) player.getInventory().offerOrDrop(be.piece);
            for (ItemStack m : be.mats) player.getInventory().offerOrDrop(m);
            be.piece = ItemStack.EMPTY; be.mats.clear(); be.strikes = 0; be.changed();
            world.playSound(null, pos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.BLOCKS, 0.6f, 1.0f);
            return ActionResult.SUCCESS;
        }
        if (held.getItem() instanceof SmithsHammerItem) return strike(world, pos, player, held, be);

        if (be.piece.isEmpty()) {
            if (!Forging.workable(held)) {
                player.sendMessage(Text.literal("Lay a weapon, tool or armour piece on the anvil first.").formatted(Formatting.GRAY), true);
                return ActionResult.SUCCESS;
            }
            be.piece = creative ? held.copyWithCount(1) : held.split(1);
            be.strikes = 0;
        } else {
            ItemStack add = player.isSneaking() ? held.copy() : held.copyWithCount(1);
            ItemStack into = null;
            for (ItemStack m : be.mats) if (ItemStack.canCombine(m, add) && m.getCount() < m.getMaxCount()) { into = m; break; }
            if (into == null && be.mats.size() >= ForgeAnvilBlockEntity.MAX_MATS) {
                player.sendMessage(Text.literal("No room for more on the anvil - empty hand takes it all back.").formatted(Formatting.GRAY), true);
                return ActionResult.SUCCESS;
            }
            int n = into == null ? add.getCount() : Math.min(add.getCount(), into.getMaxCount() - into.getCount());
            if (into == null) be.mats.add(add.copyWithCount(n)); else into.increment(n);
            if (!creative) held.decrement(n);
        }
        be.changed();
        world.playSound(null, pos, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 0.7f, 1.2f);
        player.sendMessage(status(world, pos, be), true);
        return ActionResult.SUCCESS;
    }

    private ActionResult strike(World world, BlockPos pos, PlayerEntity player, ItemStack hammer, ForgeAnvilBlockEntity be) {
        if (player.getItemCooldownManager().isCoolingDown(hammer.getItem())) return ActionResult.SUCCESS;
        if (be.piece.isEmpty()) { player.sendMessage(hint(), true); return ActionResult.SUCCESS; }
        BlockPos hearth = findHearth(world, pos);
        if (hearth == null) {
            player.sendMessage(Text.literal("The iron is cold - light a Forge Hearth within 2 blocks.").formatted(Formatting.RED), true);
            world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 0.3f, 1.8f);
            return ActionResult.SUCCESS;
        }
        Forging.Pattern pattern = Forging.match(be.piece, be.mats);
        Item mendWith = pattern == null ? Forging.mendingMaterial(be.piece, be.mats) : null;
        if (pattern == null && mendWith == null) {
            player.sendMessage(status(world, pos, be), true);
            world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.BLOCKS, 0.3f, 1.8f);
            return ActionResult.SUCCESS;
        }
        int need = pattern != null ? (bellowsAt(world, hearth) ? Forging.FORGE_STRIKES_BELLOWS : Forging.FORGE_STRIKES) : Forging.MEND_STRIKES;
        be.strikes++;
        player.getItemCooldownManager().set(hammer.getItem(), 8);
        player.swingHand(Hand.MAIN_HAND, true);
        hammer.damage(1, player, p -> p.sendToolBreakStatus(Hand.MAIN_HAND));
        ServerWorld sw = (ServerWorld) world;
        double x = pos.getX() + 0.5, y = pos.getY() + 1.0, z = pos.getZ() + 0.5;
        world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.6f, 0.9f + world.random.nextFloat() * 0.3f);
        sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 10, 0.2, 0.1, 0.2, 0.25);
        sw.spawnParticles(ParticleTypes.LAVA, x, y, z, 1, 0.1, 0.05, 0.1, 0.0);
        if (be.strikes < need) {
            player.sendMessage(Text.literal((pattern != null ? "Forging " : "Mending ") + "... " + be.strikes + "/" + need).formatted(Formatting.GOLD), true);
            be.changed();
            return ActionResult.SUCCESS;
        }
        be.strikes = 0;
        if (pattern != null) {
            for (Forging.Mat m : pattern.mats()) Forging.take(be.mats, m.item().get(), m.count());
            be.piece = Forging.forge(be.piece, pattern);
            player.sendMessage(Text.literal("Forged: ").formatted(Formatting.GOLD).append(be.piece.getName().copy().formatted(Formatting.YELLOW)), true);
            world.playSound(null, pos, SoundEvents.BLOCK_SMITHING_TABLE_USE, SoundCategory.BLOCKS, 1.0f, 0.8f);
            world.playSound(null, pos, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 0.6f, 1.0f);
            sw.spawnParticles(ParticleTypes.FLAME, x, y, z, 30, 0.3, 0.2, 0.3, 0.06);
            sw.spawnParticles(ParticleTypes.CLOUD, x, y + 0.2, z, 12, 0.3, 0.2, 0.3, 0.02);
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp) net.get900.pixelpirates.util.AdvancementHelper.grant(sp, "forged_weapon");
        } else {
            int used = Forging.mend(be.piece, be.mats, mendWith);
            player.sendMessage(Text.literal("Mended with " + used + " " + mendWith.getName().getString()
                    + (be.piece.isDamaged() ? " - more will finish it" : " - good as new")).formatted(Formatting.GREEN), true);
            world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.8f, 1.4f);
        }
        be.changed();
        return ActionResult.SUCCESS;
    }

    /** A lit Forge Hearth within 2 blocks (one up or down), or null. */
    public static BlockPos findHearth(World world, BlockPos pos) {
        for (BlockPos p : BlockPos.iterate(pos.add(-2, -1, -2), pos.add(2, 1, 2)))
            if (ForgeHearthBlock.lit(world.getBlockState(p))) return p.toImmutable();
        return null;
    }

    static boolean bellowsAt(World world, BlockPos hearth) {
        for (Direction d : Direction.values()) if (world.getBlockState(hearth.offset(d)).getBlock() instanceof BellowsBlock) return true;
        return false;
    }

    private static Text hint() {
        return Text.literal("Lay a weapon on the anvil, add materials, strike with a Smith's Hammer by a lit hearth.").formatted(Formatting.GRAY);
    }

    /** What is on the anvil, and what it needs. */
    static Text status(World world, BlockPos pos, ForgeAnvilBlockEntity be) {
        if (be.piece.isEmpty()) return hint();
        Forging.Pattern ready = Forging.match(be.piece, be.mats);
        if (ready != null) return Text.literal("Ready to forge ").formatted(Formatting.GOLD).append(ready.result().get().getName().copy().formatted(Formatting.YELLOW))
                .append(Text.literal(findHearth(world, pos) == null ? " - light the hearth" : " - strike!").formatted(Formatting.GRAY));
        Item mend = Forging.mendingMaterial(be.piece, be.mats);
        if (mend != null) return Text.literal("Ready to mend with ").formatted(Formatting.GREEN).append(mend.getName())
                .append(Text.literal(findHearth(world, pos) == null ? " - light the hearth" : " - strike!").formatted(Formatting.GRAY));
        var options = Forging.patternsFor(be.piece.getItem());
        if (!options.isEmpty()) {
            Forging.Pattern p = options.get(0);
            MutableText t = Text.literal("Needs: ").formatted(Formatting.GRAY);
            for (int i = 0; i < p.mats().length; i++) {
                Forging.Mat m = p.mats()[i];
                int have = Forging.count(be.mats, m.item().get());
                if (i > 0) t.append(Text.literal(", "));
                t.append(Text.literal(Math.min(have, m.count()) + "/" + m.count() + " ").formatted(have >= m.count() ? Formatting.GREEN : Formatting.YELLOW)).append(m.item().get().getName());
            }
            return t.append(Text.literal(" -> ").formatted(Formatting.GRAY)).append(p.result().get().getName().copy().formatted(Formatting.GOLD));
        }
        if (be.piece.isDamaged()) return Text.literal("Add its repair material to mend it.").formatted(Formatting.GRAY);
        return Text.literal("Nothing to forge or mend on that one.").formatted(Formatting.GRAY);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof ForgeAnvilBlockEntity be) {
            if (!be.piece.isEmpty()) ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, be.piece);
            for (ItemStack m : be.mats) ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, m);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
