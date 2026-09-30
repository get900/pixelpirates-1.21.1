package net.get900.pixelpirates.entity.custom;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.control.AquaticMoveControl;
import net.minecraft.entity.ai.control.YawAdjustingLookControl;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.ai.pathing.SwimNavigation;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;

/**
 * Shared base for water-bound hostiles (Siren, Abyssal Angler): dolphin-style swimming,
 * breathes water, and flops + dries out when stranded on land so it can't be cheesed from
 * the shore forever. All self-propelled movement goes through {@link #safeMove} (VS2 NPE guard,
 * see CLAUDE.md crash cause #5).
 */
public abstract class AquaticHostileEntity extends HostileEntity {
    private int dryTicks;

    protected AquaticHostileEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.moveControl = new AquaticMoveControl(this, 85, 10, 0.02f, 0.1f, true);
        this.lookControl = new YawAdjustingLookControl(this, 10);
        this.setPathfindingPenalty(PathNodeType.WATER, 0.0f);
    }

    @Override
    protected EntityNavigation createNavigation(World world) {
        return new SwimNavigation(this, world);
    }

    @Override
    public boolean canBreatheInWater() { return true; }

    @Override
    public boolean isPushedByFluids() { return false; }

    @Override
    public boolean canSpawn(net.minecraft.world.WorldView world) {
        // MobEntity's default rejects fluid in the box - i.e. every water spawn (see ModMob#canSpawn)
        return world.doesNotIntersectEntities(this);
    }

    @Override
    public void travel(Vec3d input) {
        if (this.canMoveVoluntarily() && this.isTouchingWater()) {
            this.updateVelocity(this.getMovementSpeed(), input);
            safeMove(this.getVelocity());
            this.setVelocity(this.getVelocity().multiply(0.9));
            if (this.getTarget() == null) {
                this.setVelocity(this.getVelocity().add(0.0, -0.005, 0.0));
            }
        } else {
            super.travel(input);
        }
    }

    protected void safeMove(Vec3d v) {
        try {
            this.move(MovementType.SELF, v);
        } catch (NullPointerException ignored) {
            // VS2 physics-thread race; skip this tick's move
        }
    }

    @Override
    public void tickMovement() {
        if (!this.isTouchingWater() && this.isOnGround() && this.verticalCollision) {
            // Stranded: flop toward a random direction like a beached fish
            this.setVelocity(this.getVelocity().add((this.random.nextFloat() * 2 - 1) * 0.05, 0.4,
                    (this.random.nextFloat() * 2 - 1) * 0.05));
            this.setOnGround(false);
            this.velocityDirty = true;
        }
        super.tickMovement();
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (this.isTouchingWater()) {
            dryTicks = 0;
        } else if (++dryTicks > 120 && dryTicks % 20 == 0) {
            this.damage(this.getDamageSources().dryOut(), 2.0f);
        }
    }

    /** Spawn predicate: needs two blocks of water; not in peaceful. Never uses sea level (see CLAUDE.md shark rule). */
    public static boolean canSpawnInWater(EntityType<? extends AquaticHostileEntity> type, ServerWorldAccess world,
                                          SpawnReason reason, BlockPos pos, Random random) {
        return world.getDifficulty() != Difficulty.PEACEFUL
                && world.getFluidState(pos).isIn(FluidTags.WATER)
                && world.getFluidState(pos.up()).isIn(FluidTags.WATER);
    }
}
