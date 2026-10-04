package net.get900.pixelpirates.homestead.tavern;

import net.get900.pixelpirates.homestead.HomesteadItems;
import net.get900.pixelpirates.homestead.furniture.FurnitureBlock;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** The Grog Barrel's decorative blocks. */
public final class TavernDecor {
    private TavernDecor() {}

    /** Tankards (1-3) or spirit bottles (1-4) on a table: place more of the same item on it to add one. */
    public abstract static class Stack extends FurnitureBlock {
        protected Stack(Settings s, double[]... boxes) { super(s, true, boxes); }

        protected abstract IntProperty count();

        protected abstract int max();

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> b) { super.appendProperties(b); b.add(count()); }

        @Override
        public BlockState getPlacementState(ItemPlacementContext ctx) {
            BlockState here = ctx.getWorld().getBlockState(ctx.getBlockPos());
            if (here.isOf(this)) return here.with(count(), Math.min(max(), here.get(count()) + 1));
            return super.getPlacementState(ctx);
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean canReplace(BlockState state, ItemPlacementContext ctx) {
            return !ctx.shouldCancelInteraction() && ctx.getStack().isOf(asItem()) && state.get(count()) < max() || super.canReplace(state, ctx);
        }
    }

    public static final IntProperty COUNT3 = IntProperty.of("count", 1, 3), COUNT4 = IntProperty.of("count", 1, 4);

    public static class Tankards extends Stack {
        public Tankards(Settings s) { super(s, new double[]{3, 0, 3, 13, 8, 13}); }
        @Override protected IntProperty count() { return COUNT3; }
        @Override protected int max() { return 3; }
    }

    public static class Bottles extends Stack {
        public Bottles(Settings s) { super(s, new double[]{3, 0, 3, 13, 12, 13}); }
        @Override protected IntProperty count() { return COUNT4; }
        @Override protected int max() { return 4; }
    }

    /**
     * The tavern KEG: a cask on a cradle with a brass tap. Use it with a doubloon to buy a drink of what is in it (ale,
     * mead or spiced wine - DRINK 0/1/2); sneak-use changes what it holds (it is your tavern).
     */
    public static class Keg extends FurnitureBlock {
        public static final IntProperty DRINK = IntProperty.of("drink", 0, 2);
        static final TavernDrinks.Kind[] KINDS = {TavernDrinks.Kind.ALE, TavernDrinks.Kind.HONEY_MEAD, TavernDrinks.Kind.SPICED_WINE};

        public Keg(Settings s) {
            super(s, true, new double[]{1, 0, 2, 15, 14, 14});
            setDefaultState(getDefaultState().with(DRINK, 0));
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> b) { super.appendProperties(b); b.add(DRINK); }

        static Item drinkItem(TavernDrinks.Kind k) {
            return switch (k) { case ALE -> HomesteadItems.ALE; case HONEY_MEAD -> HomesteadItems.HONEY_MEAD; default -> HomesteadItems.SPICED_WINE; };
        }

        @SuppressWarnings("deprecation")
        @Override
        public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity p, Hand hand, BlockHitResult hit) {
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (world.isClient) return ActionResult.SUCCESS;
            TavernDrinks.Kind k = KINDS[state.get(DRINK)];
            if (p.isSneaking()) {
                BlockState next = state.cycle(DRINK);
                world.setBlockState(pos, next, 3);
                p.sendMessage(Text.literal("The keg now holds " + KINDS[next.get(DRINK)].title).formatted(Formatting.GOLD), true);
                return ActionResult.CONSUME;
            }
            ItemStack held = p.getMainHandStack();
            if (!held.isOf(ModItems.COIN)) {
                p.sendMessage(Text.literal(k.title + " on tap - one doubloon a serving.").formatted(Formatting.GRAY), true);
                return ActionResult.CONSUME;
            }
            if (!p.getAbilities().creativeMode) held.decrement(1);
            p.getInventory().offerOrDrop(new ItemStack(drinkItem(k)));
            world.playSound(null, pos, SoundEvents.ITEM_BUCKET_FILL, SoundCategory.BLOCKS, 0.7f, 1.3f);
            world.playSound(null, pos, SoundEvents.BLOCK_BARREL_OPEN, SoundCategory.BLOCKS, 0.4f, 1.4f);
            return ActionResult.CONSUME;
        }
    }
}
