package net.get900.pixelpirates.homestead.chapel;

import net.get900.pixelpirates.homestead.HomesteadBlockEntities;
import net.get900.pixelpirates.homestead.furniture.FurnitureBlock;
import net.get900.pixelpirates.homestead.furniture.SeatBlock;
import net.minecraft.block.BellBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/** The chapel's working blocks: pews, the bell rope, the organ console. */
public final class ChapelBlocks {
    private ChapelBlocks() {}

    // ------------------------------------------------------------------------------------------ PEW
    /**
     * A PEW: sit on it (like the captain's chair). Pews side by side with the same facing join into one long bench - the
     * arm rests (LEFT / RIGHT, as seen by someone sitting on it) only show at the ends.
     */
    public static class Pew extends SeatBlock {
        public static final BooleanProperty LEFT = BooleanProperty.of("left"), RIGHT = BooleanProperty.of("right");

        public Pew(Settings s) {
            super(s, 0.45, new double[]{0, 0, 3, 16, 9, 14}, new double[]{0, 9, 12, 16, 18, 14});
            setDefaultState(getDefaultState().with(LEFT, true).with(RIGHT, true));
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> b) { super.appendProperties(b); b.add(LEFT, RIGHT); }

        private BlockState connect(BlockState s, WorldAccess w, BlockPos pos) {
            Direction f = s.get(FACING), left = f.rotateYCounterclockwise(), right = f.rotateYClockwise();
            BlockState l = w.getBlockState(pos.offset(left)), r = w.getBlockState(pos.offset(right));
            return s.with(LEFT, !(l.isOf(this) && l.get(FACING) == f)).with(RIGHT, !(r.isOf(this) && r.get(FACING) == f));
        }

        @Override
        public BlockState getPlacementState(ItemPlacementContext ctx) {
            return connect(super.getPlacementState(ctx), ctx.getWorld(), ctx.getBlockPos());
        }

        @SuppressWarnings("deprecation")
        @Override
        public BlockState getStateForNeighborUpdate(BlockState s, Direction d, BlockState n, WorldAccess w, BlockPos pos, BlockPos np) {
            return d.getAxis().isHorizontal() ? connect(s, w, pos) : s;
        }
    }

    // ------------------------------------------------------------------------------------------ BELL ROPE
    /**
     * The BELL ROPE (a sally on a long rope at the foot of a bell tower): pull it to toll the bells hanging above it (any
     * vanilla bell within 40 blocks up, 3 sideways). Every morning it tolls them seven times on its own and calls the town
     * to prayer (ChapelBells).
     */
    public static class BellRope extends FurnitureBlock implements BlockEntityProvider {
        public BellRope(Settings s) { super(s, false, new double[]{6, 0, 6, 10, 16, 10}); }

        @Nullable
        @Override
        public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new BellRopeEntity(pos, state); }

        @Nullable
        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
            return world.isClient || type != HomesteadBlockEntities.BELL_ROPE ? null : (w, p, s, be) -> ((BellRopeEntity) be).tick((ServerWorld) w);
        }

        @SuppressWarnings("deprecation")
        @Override
        public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (world.isClient) return ActionResult.SUCCESS;
            if (world.getBlockEntity(pos) instanceof BellRopeEntity be) {
                if (player.isSneaking() && player.hasPermissionLevel(2)) be.startToll(3, true);
                else if (be.toll() == 0) player.sendMessage(Text.literal("There is no bell above this rope.").formatted(Formatting.GRAY), true);
            }
            return ActionResult.CONSUME;
        }
    }

    public static class BellRopeEntity extends BlockEntity {
        private int tollsLeft, nextToll;
        private long lastMorning = -1;
        private boolean registered, callPrayers;

        public BellRopeEntity(BlockPos pos, BlockState state) { super(HomesteadBlockEntities.BELL_ROPE, pos, state); }

        void tick(ServerWorld w) {
            if (!registered) { ChapelBells.register(w, pos); registered = true; }
            long day = w.getTimeOfDay() / 24000L, tod = w.getTimeOfDay() % 24000L;
            if (tod >= ChapelBells.MORNING && tod < ChapelBells.MORNING + 200 && day != lastMorning) {
                lastMorning = day;
                markDirty();
                startToll(7, true);
            }
            if (tollsLeft > 0 && --nextToll <= 0) {
                toll();
                tollsLeft--;
                nextToll = 40;
            }
        }

        /** Starts a peal of {@code n} tolls; the first one may call the town to prayer. */
        public void startToll(int n, boolean prayers) {
            tollsLeft = n;
            nextToll = 1;
            callPrayers = prayers;
        }

        /** One toll of every bell above the rope; returns how many bells rang. */
        public int toll() {
            if (!(world instanceof ServerWorld w)) return 0;
            int rung = 0;
            for (int y = 1; y <= 40; y++)
                for (int dx = -3; dx <= 3; dx++)
                    for (int dz = -3; dz <= 3; dz++) {
                        BlockPos b = pos.add(dx, y, dz);
                        BlockState s = w.getBlockState(b);
                        if (s.isOf(Blocks.BELL) && s.getBlock() instanceof BellBlock bell) {
                            bell.ring(w, b, null);
                            w.playSound(null, b, SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 4.0f, 0.75f);   // carries over the town
                            w.spawnParticles(ParticleTypes.NOTE, b.getX() + 0.5, b.getY() + 1.2, b.getZ() + 0.5, 3, 0.4, 0.2, 0.4, 1);
                            rung++;
                        }
                    }
            w.playSound(null, pos, SoundEvents.ENTITY_LEASH_KNOT_PLACE, SoundCategory.BLOCKS, 0.6f, 0.8f);
            if (rung > 0 && callPrayers) {
                callPrayers = false;
                ChapelBells.call(w, pos);
                for (PlayerEntity p : w.getEntitiesByClass(PlayerEntity.class, new Box(pos).expand(160), e -> true))
                    p.sendMessage(Text.literal("The chapel bell tolls - morning prayers.").formatted(Formatting.GOLD), true);
            }
            return rung;
        }

        @Override
        public void markRemoved() {
            if (world instanceof ServerWorld w) ChapelBells.unregister(w, pos);
            super.markRemoved();
        }

        @Override
        protected void writeNbt(NbtCompound nbt) { super.writeNbt(nbt); nbt.putLong("LastMorning", lastMorning); }

        @Override
        public void readNbt(NbtCompound nbt) { super.readNbt(nbt); lastMorning = nbt.contains("LastMorning") ? nbt.getLong("LastMorning") : -1; }
    }

    // ------------------------------------------------------------------------------------------ ORGAN
    /** The ORGAN CONSOLE: use it to sit at the keyboard (opens OrganScreen on the client; notes go to OrganNotes). */
    public static class OrganConsole extends FurnitureBlock {
        /** Set by the client (HomesteadClient) - opens the keyboard screen for this console. */
        public static Consumer<BlockPos> CLIENT_OPEN = p -> { };

        public OrganConsole(Settings s) { super(s, true, new double[]{0, 0, 4, 16, 14, 16}); }

        @SuppressWarnings("deprecation")
        @Override
        public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (world.isClient) { CLIENT_OPEN.accept(pos); return ActionResult.SUCCESS; }
            return ActionResult.CONSUME;
        }
    }

    /** ORGAN PIPES: decorative; TOP = the crown course (pipe mouths, tapered tips) - stack plain courses under it. */
    public static class OrganPipes extends FurnitureBlock {
        public static final BooleanProperty TOP = BooleanProperty.of("top");

        public OrganPipes(Settings s) {
            super(s, true, new double[]{0, 0, 8, 16, 16, 16});
            setDefaultState(getDefaultState().with(TOP, true));
        }

        @Override
        protected void appendProperties(StateManager.Builder<Block, BlockState> b) { super.appendProperties(b); b.add(TOP); }

        @SuppressWarnings("deprecation")
        @Override
        public BlockState getStateForNeighborUpdate(BlockState s, Direction d, BlockState n, WorldAccess w, BlockPos pos, BlockPos np) {
            return d == Direction.UP ? s.with(TOP, !(n.isOf(this))) : s;
        }

        @Override
        public BlockState getPlacementState(ItemPlacementContext ctx) {
            BlockState s = super.getPlacementState(ctx);
            return s.with(TOP, !ctx.getWorld().getBlockState(ctx.getBlockPos().up()).isOf(this));
        }
    }
}
