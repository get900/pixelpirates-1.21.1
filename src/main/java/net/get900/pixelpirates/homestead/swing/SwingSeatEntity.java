package net.get900.pixelpirates.homestead.swing;

import net.get900.pixelpirates.homestead.HomesteadEntities;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The swinging seat: an invisible entity the player rides, moved every tick along the arc under the swing's pivot -
 * the swing builds up to +-32 degrees over three seconds, a 2.4 s pendulum along the way the swing faces. Gone (and the
 * swing's resting seat back) the moment it's empty or the swing is broken.
 */
public class SwingSeatEntity extends Entity {
    /** The pivot sits 1.9 blocks above the swing's lower block, the seat top 1.4 below the pivot. */
    public static final double PIVOT_Y = 1.9, ROPE = 1.4, MAX_DEG = 32, PERIOD = 48;
    private BlockPos swing = BlockPos.ORIGIN;
    private int t;

    public SwingSeatEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
        this.setInvisible(true);
    }

    public static boolean sit(World world, BlockPos lower, LivingEntity who) {
        BlockState s = world.getBlockState(lower);
        if (!(s.getBlock() instanceof SwingBlock) || s.get(SwingBlock.OCCUPIED)) return false;
        SwingSeatEntity e = new SwingSeatEntity(HomesteadEntities.SWING_SEAT, world);
        e.swing = lower.toImmutable();
        e.setPosition(pivot(lower).add(0, -ROPE, 0));
        world.spawnEntity(e);
        if (!who.startRiding(e, true)) { e.discard(); return false; }
        who.setYaw(s.get(SwingBlock.FACING).asRotation());
        SwingBlock.setOccupied(world, lower, true);
        return true;
    }

    public static Vec3d pivot(BlockPos lower) { return new Vec3d(lower.getX() + 0.5, lower.getY() + PIVOT_Y, lower.getZ() + 0.5); }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;
        BlockState s = getWorld().getBlockState(swing);
        if (getPassengerList().isEmpty() || !(s.getBlock() instanceof SwingBlock)) {
            SwingBlock.setOccupied(getWorld(), swing, false);
            discard();
            return;
        }
        t++;
        double amp = Math.toRadians(MAX_DEG) * Math.min(1.0, t / 60.0);
        double th = amp * Math.sin(2 * Math.PI * t / PERIOD);
        Direction d = s.get(SwingBlock.FACING);
        Vec3d p = pivot(swing);
        setPosition(p.x + d.getOffsetX() * ROPE * Math.sin(th), p.y - ROPE * Math.cos(th), p.z + d.getOffsetZ() * ROPE * Math.sin(th));
    }

    /** The swing's angle (radians, + toward where it faces) from this seat's position - what the renderer draws. */
    public static double angle(BlockPos lower, Direction facing, Vec3d seat) {
        Vec3d p = pivot(lower);
        double along = (seat.x - p.x) * facing.getOffsetX() + (seat.z - p.z) * facing.getOffsetZ();
        return Math.atan2(along, p.y - seat.y);
    }

    @Override
    public Vec3d updatePassengerForDismount(LivingEntity passenger) {
        BlockState s = getWorld().getBlockState(swing);
        Direction d = s.getBlock() instanceof SwingBlock ? s.get(SwingBlock.FACING) : Direction.NORTH;
        return Vec3d.ofBottomCenter(swing.offset(d));
    }

    @Override
    public double getMountedHeightOffset() { return 0.05; }

    @Override
    protected void initDataTracker() {}

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) { swing = BlockPos.fromLong(nbt.getLong("Swing")); }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) { nbt.putLong("Swing", swing.asLong()); }

    @Override
    public boolean shouldSave() { return false; }                                       // a fresh sit each time

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() { return new EntitySpawnS2CPacket(this); }

    static float deg(double rad) { return (float) Math.toDegrees(MathHelper.wrapDegrees(rad)); }
}
