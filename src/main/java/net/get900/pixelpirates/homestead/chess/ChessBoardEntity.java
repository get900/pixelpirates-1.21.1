package net.get900.pixelpirates.homestead.chess;

import net.get900.pixelpirates.homestead.HomesteadBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.UUID;

/**
 * One game of chess on a CHESS TABLE or a GIANT CHESS set: the position, who sits where (a player, the computer at
 * level 1/2, or nobody), the last move (+ when it was made and what it took - the renderer animates it), the result.
 * The computer moves from the server ticker after a short think.
 */
public class ChessBoardEntity extends BlockEntity {
    public ChessRules game = ChessRules.start();
    /** [0] white, [1] black: a player's uuid (null = empty seat); ai[i] > 0 = the computer at that level. */
    @Nullable public UUID[] seat = new UUID[2];
    public String[] names = {"", ""};
    public int[] ai = {0, 0};
    public int lastMove = -1, captured, capturedAt = -1;
    public long moveTime;
    public String result = "";
    private long aiDue = -1;

    public ChessBoardEntity(BlockPos pos, BlockState state) { super(HomesteadBlockEntities.CHESS, pos, state); }

    public boolean giant() { return getCachedState().getBlock() instanceof GiantChessBlock; }

    /** Make a move (assumed legal): record it for the animation, settle the result, wake the computer. */
    void play(int m) {
        int t = ChessRules.to(m), f = ChessRules.from(m);
        boolean epCap = Math.abs(game.sq[f]) == ChessRules.P && t == game.ep && game.sq[t] == 0;
        int side = game.whiteToMove ? 1 : -1;
        captured = game.play(m);
        capturedAt = captured == 0 ? -1 : epCap ? t - 8 * side : t;
        lastMove = m;
        moveTime = world == null ? 0 : world.getTime();
        result = game.result();
        scheduleAi();
        changed();
    }

    void reset() {
        game = ChessRules.start();
        lastMove = -1; captured = 0; capturedAt = -1; result = "";
        scheduleAi();
        changed();
    }

    void scheduleAi() {
        int side = game.whiteToMove ? 0 : 1;
        aiDue = result.isEmpty() && ai[side] > 0 && world != null ? world.getTime() + 25 : -1;
    }

    void changed() {
        markDirty();
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        if (world instanceof ServerWorld sw) Chess.refresh(sw, this);
    }

    public static void serverTick(World world, BlockPos pos, BlockState state, ChessBoardEntity be) {
        if (be.aiDue < 0 || world.getTime() < be.aiDue) return;
        be.aiDue = -1;
        int side = be.game.whiteToMove ? 0 : 1;
        if (be.ai[side] <= 0 || !be.result.isEmpty()) return;
        int m = ChessAI.choose(be.game, be.ai[side], new Random(world.getTime() ^ pos.asLong()));
        if (m >= 0) { be.play(m); Chess.moved((ServerWorld) world, be, m); }
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putIntArray("Game", game.toArray());
        for (int i = 0; i < 2; i++) {
            if (seat[i] != null) nbt.putUuid("Seat" + i, seat[i]);
            nbt.putString("Name" + i, names[i]);
            nbt.putInt("Ai" + i, ai[i]);
        }
        nbt.putInt("Last", lastMove);
        nbt.putInt("Captured", captured);
        nbt.putInt("CapturedAt", capturedAt);
        nbt.putLong("MoveTime", moveTime);
        nbt.putString("Result", result);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        game = ChessRules.fromArray(nbt.getIntArray("Game"));
        for (int i = 0; i < 2; i++) {
            seat[i] = nbt.containsUuid("Seat" + i) ? nbt.getUuid("Seat" + i) : null;
            names[i] = nbt.getString("Name" + i);
            ai[i] = nbt.getInt("Ai" + i);
        }
        lastMove = nbt.contains("Last") ? nbt.getInt("Last") : -1;
        captured = nbt.getInt("Captured");
        capturedAt = nbt.contains("CapturedAt") ? nbt.getInt("CapturedAt") : -1;
        moveTime = nbt.getLong("MoveTime");
        result = nbt.getString("Result");
    }

    @Override
    public void setWorld(World world) {
        super.setWorld(world);
        if (!world.isClient) scheduleAi();                                           // a computer to move when the chunk loads
    }

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }

    @Override
    public NbtCompound toInitialChunkDataNbt() { return createNbt(); }
}
