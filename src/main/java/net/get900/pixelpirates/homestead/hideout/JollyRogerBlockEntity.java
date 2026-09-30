package net.get900.pixelpirates.homestead.hideout;

import net.get900.pixelpirates.homestead.HomesteadBlockEntities;
import net.get900.pixelpirates.homestead.HomesteadState;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/** The hideout flag: owner + the waving flag. Tier 3 regenerates its owner inside the claim. */
public class JollyRogerBlockEntity extends BlockEntity implements GeoBlockEntity {
    private static final RawAnimation WAVE = RawAnimation.begin().thenLoop("wave");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private UUID owner;
    private String ownerName = "";

    public JollyRogerBlockEntity(BlockPos pos, BlockState state) { super(HomesteadBlockEntities.JOLLY_ROGER, pos, state); }

    @Nullable
    public UUID owner() { return owner; }

    public String ownerName() { return ownerName; }

    public void setOwner(UUID u, String name) {
        owner = u;
        ownerName = name;
        markDirty();
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    public static void serverTick(World world, BlockPos pos, BlockState state, JollyRogerBlockEntity be) {
        if (be.owner == null || world.getTime() % 80 != 0) return;
        ServerPlayerEntity p = ((ServerWorld) world).getServer().getPlayerManager().getPlayer(be.owner);
        if (p == null || p.getWorld() != world) return;
        HomesteadState.Hideout h = HomesteadState.get(p.getServer()).hideout(be.owner);
        if (h == null || h.tier() < 3 || !h.pos().equals(pos)) return;
        if (p.getBlockPos().getSquaredDistance(pos.getX(), p.getY(), pos.getZ()) <= (double) h.radius() * h.radius())
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0, true, false, true));
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        if (owner != null) nbt.putUuid("Owner", owner);
        nbt.putString("OwnerName", ownerName);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        owner = nbt.containsUuid("Owner") ? nbt.getUuid("Owner") : null;
        ownerName = nbt.getString("OwnerName");
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() { return createNbt(); }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "wave", 0, st -> st.setAndContinue(WAVE)));
        controllers.add(new AnimationController<>(this, "raise", 0, st -> PlayState.STOP).triggerableAnim("raise", RawAnimation.begin().thenPlay("raise")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
