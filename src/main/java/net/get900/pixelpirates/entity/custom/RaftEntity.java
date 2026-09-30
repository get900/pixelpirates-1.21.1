package net.get900.pixelpirates.entity.custom;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;

public class RaftEntity extends BoatEntity implements GeoAnimatable {

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private boolean sailsLowered = false;
    private float movementSpeed = 0.14f;
    private float targetYaw;

    private float damage = 0f;
    private final float maxDamage = 20f;

    public RaftEntity(EntityType<? extends BoatEntity> type, World world) {
        super(type, world);
        this.setCustomName(Text.literal("Raft"));
    }

    @Override
    public double getTick(Object object) {
        return this.age;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> PlayState.STOP));
    }

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient) return;

        if (sailsLowered && this.hasPassengers() && this.getFirstPassenger() instanceof PlayerEntity player) {
            this.targetYaw = player.getYaw();
            float yawDiff = MathHelper.wrapDegrees(this.targetYaw - this.getYaw());
            this.setYaw(this.getYaw() + yawDiff * 0.05f);
        }

        if (sailsLowered) {
            Vec3d forward = this.getRotationVector().normalize().multiply(movementSpeed);
            this.move(MovementType.SELF, forward);
        } else {
            this.setVelocity(Vec3d.ZERO);
        }

        if (!this.getWorld().isClient) {
            for (Entity passenger : this.getPassengerList()) {
                passenger.setPosition(this.getX(), this.getY() + 0.5, this.getZ());
            }
        }

        Vec3d raftVelocity = this.getVelocity();

        this.getWorld().getOtherEntities(this, this.getBoundingBox().expand(0.1), entity ->
                entity instanceof PlayerEntity &&
                        !entity.hasVehicle() &&
                        entity.getY() > this.getY() + 0.1
        ).forEach(entity -> {
            entity.addVelocity(raftVelocity.x * 0.9, 0, raftVelocity.z * 0.9);
        });
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (player.isSneaking()) {
            this.sailsLowered = !this.sailsLowered;
            this.setCustomName(Text.literal("Sails " + (sailsLowered ? "Down" : "Up")));

            if (!this.getWorld().isClient) {
                String anim = sailsLowered ? "sails_down" : "sails_up";
                this.getAnimatableInstanceCache()
                        .getManagerForId(this.getId())
                        .tryTriggerAnimation("controller", anim);
            }

            if (this.hasPassengers() && this.getFirstPassenger() instanceof PlayerEntity rider) {
                this.targetYaw = rider.getYaw();
            }

            return ActionResult.SUCCESS;
        }

        if (!player.hasVehicle()) {
            player.startRiding(this);
            return ActionResult.SUCCESS;
        }

        return ActionResult.PASS;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return !(passenger instanceof SharkEntity) && super.canAddPassenger(passenger);
    }

    public void damageRaft(float amount) {
        this.damage = Math.min(this.damage + amount, maxDamage);
        if (this.damage >= maxDamage) {
            this.kill();
        }
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) return false;
        this.damageRaft(amount);
        this.scheduleVelocityUpdate();
        return true;
    }

    public float getSinkingAmount() {
        return (damage / maxDamage) * 6f;
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("SailsLowered", sailsLowered);
        nbt.putFloat("Damage", damage);
        nbt.putFloat("TargetYaw", this.targetYaw);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        sailsLowered = nbt.getBoolean("SailsLowered");
        damage = nbt.getFloat("Damage");
        targetYaw = nbt.getFloat("TargetYaw");
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
