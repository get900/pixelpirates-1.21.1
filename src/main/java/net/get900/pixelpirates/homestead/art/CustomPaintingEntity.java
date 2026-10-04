package net.get900.pixelpirates.homestead.art;

import net.get900.pixelpirates.homestead.HomesteadEntities;
import net.get900.pixelpirates.homestead.HomesteadItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** A player's painting hung on a wall: a vanilla-style hanging decoration of 1x1 .. 2x2 blocks showing its pixels. */
public class CustomPaintingEntity extends AbstractDecorationEntity {
    /** {Size, Title, Author, Pixels} - the same compound the painting item carries. */
    private static final TrackedData<NbtCompound> ART = DataTracker.registerData(CustomPaintingEntity.class, TrackedDataHandlerRegistry.NBT_COMPOUND);

    public CustomPaintingEntity(EntityType<? extends CustomPaintingEntity> type, World world) { super(type, world); }

    public CustomPaintingEntity(World world, BlockPos pos, Direction facing, NbtCompound art) {
        super(HomesteadEntities.CUSTOM_PAINTING, world, pos);
        dataTracker.set(ART, art.copy());
        setFacing(facing);
    }

    @Override
    protected void initDataTracker() { dataTracker.startTracking(ART, new NbtCompound()); }

    public NbtCompound art() { return dataTracker.get(ART); }

    public Art.Size size() { return Art.Size.of(art().getByte("Size")); }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (ART.equals(data)) updateAttachmentPosition();
    }

    @Override
    public int getWidthPixels() { return size().bw * 16; }

    @Override
    public int getHeightPixels() { return size().bh * 16; }

    @Override
    public void onBreak(@Nullable Entity breaker) {
        if (!getWorld().getGameRules().getBoolean(GameRules.DO_ENTITY_DROPS)) return;
        playSound(SoundEvents.ENTITY_PAINTING_BREAK, 1f, 1f);
        if (breaker instanceof PlayerEntity p && p.getAbilities().creativeMode) return;
        ItemStack s = new ItemStack(HomesteadItems.PAINTING);
        s.getOrCreateNbt().put("Art", art().copy());
        dropStack(s);
    }

    @Override
    public void onPlace() { playSound(SoundEvents.ENTITY_PAINTING_PLACE, 1f, 1f); }

    @Override
    public ItemStack getPickBlockStack() {
        ItemStack s = new ItemStack(HomesteadItems.PAINTING);
        s.getOrCreateNbt().put("Art", art().copy());
        return s;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.put("Art", art().copy());
        nbt.putByte("facing", (byte) facing.getHorizontal());
        super.writeCustomDataToNbt(nbt);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        dataTracker.set(ART, nbt.getCompound("Art"));
        facing = Direction.fromHorizontal(nbt.getByte("facing"));
        super.readCustomDataFromNbt(nbt);
        setFacing(facing);
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this, facing.getId(), getDecorationBlockPos());
    }

    @Override
    public void onSpawnPacket(EntitySpawnS2CPacket packet) {
        super.onSpawnPacket(packet);
        setFacing(Direction.byId(packet.getEntityData()));
    }
}
