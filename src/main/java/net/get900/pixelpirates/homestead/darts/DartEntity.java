package net.get900.pixelpirates.homestead.darts;

import net.get900.pixelpirates.homestead.HomesteadEntities;
import net.get900.pixelpirates.homestead.HomesteadItems;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A thrown dart (2026-10-05). A player's dart flies with light gravity and sticks in the first block it meets (no
 * entity hits - it's a tavern game, not a weapon); a townsperson's dart flies straight to the point its aim chose
 * ({@link Darts#scatter}). Either way it reports to the board ({@link DartboardBlockEntity#landed}). A dart outside a
 * game stays where it stuck and can be picked up by walking into it.
 */
public class DartEntity extends Entity {
    private static final TrackedData<Boolean> STUCK = DataTracker.registerData(DartEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    @Nullable private UUID thrower;
    @Nullable private String folk;
    @Nullable private BlockPos game;
    private boolean foul;
    @Nullable private Vec3d target;
    private int flight, stuckAge;

    public DartEntity(EntityType<? extends DartEntity> type, World world) { super(type, world); }

    /** A player's throw from the eye, along the look (a touch of wobble). */
    public static DartEntity thrown(World w, PlayerEntity p, @Nullable BlockPos game, boolean foul) {
        DartEntity d = new DartEntity(HomesteadEntities.DART, w);
        d.thrower = p.getUuid();
        d.game = game;
        d.foul = foul;
        Vec3d look = p.getRotationVec(1f);
        d.setPosition(p.getEyePos().add(look.multiply(0.3)).add(0, -0.1, 0));
        Vec3d v = look.add(w.random.nextGaussian() * 0.004, w.random.nextGaussian() * 0.004, w.random.nextGaussian() * 0.004).multiply(1.25);
        d.setVelocity(v);
        d.face(v);
        return d;
    }

    /** A townsperson's dart: a straight flight from {@code from} to the chosen point on the face. */
    public static DartEntity aimed(World w, String folk, BlockPos game, Vec3d from, Vec3d to) {
        DartEntity d = new DartEntity(HomesteadEntities.DART, w);
        d.folk = folk;
        d.game = game;
        d.target = to;
        d.setNoGravity(true);
        d.setPosition(from);
        d.flight = Math.max(5, (int) Math.ceil(from.distanceTo(to) / 0.45));
        Vec3d v = to.subtract(from).multiply(1.0 / d.flight);
        d.setVelocity(v);
        d.face(v);
        return d;
    }

    @Nullable public UUID thrower() { return thrower; }
    @Nullable public String folk() { return folk; }
    public boolean foul() { return foul; }
    public boolean stuck() { return this.dataTracker.get(STUCK); }

    private void face(Vec3d v) {
        double h = Math.sqrt(v.x * v.x + v.z * v.z);
        this.setYaw((float) (MathHelper.atan2(v.x, v.z) * 57.2957795));
        this.setPitch((float) (MathHelper.atan2(v.y, h) * 57.2957795));
        this.prevYaw = this.getYaw();
        this.prevPitch = this.getPitch();
    }

    @Override
    protected void initDataTracker() { this.dataTracker.startTracking(STUCK, false); }

    @Override
    public void tick() {
        super.tick();
        if (stuck()) {
            if (!getWorld().isClient && game == null && ++stuckAge > 6000) discard();
            return;
        }
        Vec3d p = getPos(), v = getVelocity(), next = p.add(v);
        if (getWorld() instanceof ServerWorld w) {
            if (target != null) {
                if (--flight <= 0) { setPosition(target); stick(w, BlockPos.ofFloored(target), target, true); return; }
            } else {
                BlockHitResult hr = w.raycast(new RaycastContext(p, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
                if (hr.getType() == HitResult.Type.BLOCK) {
                    Vec3d at = hr.getPos().subtract(v.normalize().multiply(0.02));
                    setPosition(at);
                    stick(w, hr.getBlockPos(), hr.getPos(), false);
                    return;
                }
            }
            if (this.age > 200) { stick(w, null, p, false); discard(); return; }
        }
        setPosition(next);
        if (!hasNoGravity()) v = v.add(0, -0.03, 0);
        v = v.multiply(0.99);
        setVelocity(v);
        prevYaw = getYaw();
        prevPitch = getPitch();
        face(v);
    }

    /** Thunk: stop, and tell the board (the one we hit, or our game's board for a miss). */
    private void stick(ServerWorld w, @Nullable BlockPos block, Vec3d hit, boolean scripted) {
        this.dataTracker.set(STUCK, true);
        setVelocity(Vec3d.ZERO);
        w.playSound(null, getBlockPos(), SoundEvents.ENTITY_ARROW_HIT, SoundCategory.PLAYERS, 0.5f, 1.6f + w.random.nextFloat() * 0.2f);
        BlockEntity be = block == null ? null : w.getBlockEntity(block);
        if (scripted && game != null) be = w.getBlockEntity(game);
        if (be instanceof DartboardBlockEntity board) { board.landed(w, this, hit, true); return; }
        if (game != null && w.getBlockEntity(game) instanceof DartboardBlockEntity board) board.landed(w, this, hit, false);
    }

    @Override
    public void onPlayerCollision(PlayerEntity p) {
        if (getWorld().isClient || !stuck() || game != null || isRemoved()) return;
        if (p.getInventory().insertStack(new ItemStack(HomesteadItems.DART))) {
            p.sendPickup(this, 1);
            discard();
        }
    }

    @Override
    public boolean isAttackable() { return false; }

    @Override
    public boolean canHit() { return false; }

    @Override
    protected void readCustomDataFromNbt(NbtCompound n) {
        if (n.containsUuid("Thrower")) thrower = n.getUuid("Thrower");
        this.dataTracker.set(STUCK, n.getBoolean("Stuck"));
        if (!n.getBoolean("Stuck")) discard();                                // a dart mid-air on load: gone
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound n) {
        if (thrower != null) n.putUuid("Thrower", thrower);
        n.putBoolean("Stuck", stuck());
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() { return new EntitySpawnS2CPacket(this); }

    @Override
    public boolean shouldSave() { return game == null; }                       // game darts are the board's to clear
}
