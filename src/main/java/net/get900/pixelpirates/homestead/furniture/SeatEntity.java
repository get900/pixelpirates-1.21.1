package net.get900.pixelpirates.homestead.furniture;

import net.get900.pixelpirates.homestead.HomesteadEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** An invisible seat on a chair or stool: sit by right-clicking the block, sneak to stand. Gone the moment it's empty. */
public class SeatEntity extends Entity {
    public SeatEntity(EntityType<?> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
        this.setInvisible(true);
    }

    public static boolean sit(World world, BlockPos pos, double height, LivingEntity who) {
        if (!world.getEntitiesByClass(SeatEntity.class, new net.minecraft.util.math.Box(pos), e -> true).isEmpty()) return false;
        SeatEntity s = new SeatEntity(HomesteadEntities.SEAT, world);
        s.setPosition(pos.getX() + 0.5, pos.getY() + height, pos.getZ() + 0.5);
        world.spawnEntity(s);
        return who.startRiding(s, true);
    }

    @Override
    public void tick() {
        super.tick();
        if (!getWorld().isClient && (getPassengerList().isEmpty() || !(getWorld().getBlockState(getBlockPos()).getBlock() instanceof SeatBlock))) discard();
    }

    @Override
    public Vec3d updatePassengerForDismount(LivingEntity passenger) {
        return Vec3d.ofBottomCenter(getBlockPos().up());
    }

    @Override
    public double getMountedHeightOffset() { return 0; }

    @Override
    protected void initDataTracker() {}

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {}

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {}

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() { return new EntitySpawnS2CPacket(this); }
}
