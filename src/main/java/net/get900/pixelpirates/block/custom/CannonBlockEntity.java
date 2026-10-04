package net.get900.pixelpirates.block.custom;

import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.entity.custom.CannonBallEntity;
import net.get900.pixelpirates.util.AdvancementHelper;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;

import java.util.UUID;

public class CannonBlockEntity extends BlockEntity {

    private static final int    MAX_CHARGE      = 60;   // ticks to full power (3 s), less with Quick Hands
    private int chargeMax = MAX_CHARGE;                   // this charge's length (the charger's Quick Hands)
    private static final int    AUTO_FIRE_AT    = 100;  // ticks before auto-fire if player delays
    private static final double BASE_SPEED      = 2.5;  // blocks/tick at full power
    private static final double MIN_POWER       = 0.35; // fraction at zero charge
    private static final double SPAWN_OFFSET    = 1.5;  // blocks ahead of barrel
    private static final double MAX_YAW_OFFSET  = 45.0; // max horizontal deviation from cannon axis (degrees)
    private static final int    BROADSIDE_RANGE = 24;   // max blocks along the broadside line
    private static final int    BROADSIDE_Y     = 3;    // vertical search range (multi-deck)

    private int  chargeTick = 0;
    /** What is in the barrel: Shot.ROUND / CHAIN / GRAPE (homestead #25). */
    private int  ammo       = 0;

    public void setAmmo(int a) { ammo = a; markDirty(); }
    private UUID ownerUUID  = null;

    public CannonBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.CANNON_BLOCK_ENTITY, pos, state);
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(World world, BlockPos pos, BlockState state, CannonBlockEntity be) {
        if (world.isClient || be.chargeTick <= 0) return;

        be.chargeTick++;
        be.markDirty();

        ServerWorld sw = (ServerWorld) world;
        ServerPlayerEntity owner = be.ownerUUID != null
            ? sw.getServer().getPlayerManager().getPlayer(be.ownerUUID)
            : null;

        Vec3d worldCannonPos = worldPos(sw, pos);

        // Cancel if owner logged off or walked away (>6 blocks)
        if (owner == null || owner.squaredDistanceTo(worldCannonPos.x, worldCannonPos.y, worldCannonPos.z) > 36.0) {
            be.reset();
            return;
        }

        float progress = Math.min((float) be.chargeTick / be.chargeMax, 1.0f);

        // Action-bar charge indicator
        owner.sendMessage(buildBar(progress), true);

        // Trajectory preview arc every 4 ticks — primary cannon + all broadside cannons
        if (be.chargeTick % 4 == 0) {
            Direction facing      = state.get(CannonBlock.FACING);
            Vec3d     worldFacing = worldFacing(sw, pos, facing);
            Vec3d     hAimDir     = horizontalAimDir(worldFacing, owner.getYaw());
            float     pitch       = owner.getPitch();
            spawnArc(sw, owner, worldCannonPos, hAimDir, pitch, progress);
            spawnBroadsideArcs(sw, owner, pos, facing, hAimDir, pitch, progress);
        }

        // Auto-fire once charge has been full for a while
        if (be.chargeTick >= AUTO_FIRE_AT) {
            be.doFire(sw, pos, state, owner, 1.0f);
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void startCharge(PlayerEntity player) {
        chargeMax = Math.max(20, (int) Math.round(MAX_CHARGE * net.get900.pixelpirates.world.SkillEffects.reloadMult(player)));
        chargeTick = 1;
        ownerUUID  = player.getUuid();
        markDirty();
    }

    public boolean isCharging() { return chargeTick > 0; }

    public float getCurrentPower() {
        float t = Math.min((float) chargeTick / chargeMax, 1.0f);
        return (float) (MIN_POWER + (1.0 - MIN_POWER) * t);
    }

    public void fireFromPlayer(ServerWorld world, BlockPos pos, BlockState state, PlayerEntity player) {
        doFire(world, pos, state, player, getCurrentPower());
    }

    // ── Fire logic ────────────────────────────────────────────────────────────

    private void doFire(ServerWorld world, BlockPos pos, BlockState state, PlayerEntity player, float power) {
        doFire(world, pos, state, player, power, true);
    }

    private void doFire(ServerWorld world, BlockPos pos, BlockState state, PlayerEntity player, float power, boolean broadside) {
        BlockState current = world.getBlockState(pos);
        if (!current.isOf(state.getBlock())) return;
        if (!current.get(CannonBlock.LOADED)) return;

        Direction facing         = current.get(CannonBlock.FACING);
        Vec3d     worldCannonPos = worldPos(world, pos);
        Vec3d     hFacing        = worldFacing(world, pos, facing);
        Vec3d     hAimDir        = horizontalAimDir(hFacing, player.getYaw());

        // Combine clamped horizontal aim with player's vertical pitch
        double rad  = Math.toRadians(player.getPitch());
        double cosP = Math.cos(rad);
        double sinP = Math.sin(rad);
        Vec3d fireDir = new Vec3d(
            hAimDir.x * cosP,
            -sinP,              // MC pitch: negative = up, so negate for Y
            hAimDir.z * cosP
        ).normalize();

        // Inherit ship velocity so ball doesn't lag behind a moving ship
        Vec3d shipVel = Vec3d.ZERO;
        Ship ship = ValkyrienSkies.getShipManagingBlock(world, pos.getX(), pos.getY(), pos.getZ());
        if (ship instanceof LoadedServerShip ls) {
            shipVel = new Vec3d(ls.getVelocity().x(), ls.getVelocity().y(), ls.getVelocity().z());
        }

        double speed    = BASE_SPEED * power;
        Vec3d  origin   = worldCannonPos.add(fireDir.multiply(SPAWN_OFFSET));
        Vec3d  velocity = fireDir.multiply(speed).add(shipVel);

        if (ammo == net.get900.pixelpirates.homestead.ship.Shot.GRAPE) {
            net.get900.pixelpirates.homestead.ship.Shot.grape(world, player, origin, velocity);
        } else {
            CannonBallEntity ball = new CannonBallEntity(world, player, origin.x, origin.y, origin.z);
            ball.setVelocity(velocity.x, velocity.y, velocity.z);
            if (ammo == net.get900.pixelpirates.homestead.ship.Shot.CHAIN) ball.setChainShot();
            world.spawnEntity(ball);
        }
        ammo = 0;
        if (player instanceof ServerPlayerEntity spe) AdvancementHelper.grant(spe, "broadside");

        world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 3.0f, 0.5f);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE,
            origin.x, origin.y, origin.z, 30, 0.4, 0.4, 0.4, 0.05);

        reset();
        world.setBlockState(pos, current.with(CannonBlock.LOADED, false));

        if (broadside) {
            int extra = fireBroadside(world, pos, facing, player, power);
            player.sendMessage(extra > 0
                ? Text.literal("§c§lBROADSIDE! §f(" + (extra + 1) + " cannons)")
                : Text.literal("§c§lBOOM!"), true);
        } else {
            player.sendMessage(Text.literal("§c§lBOOM!"), true);
        }
    }

    // Scans the broadside line (perpendicular to facing) and fires all loaded cannons.
    // Returns the number of additional cannons fired.
    private static int fireBroadside(ServerWorld world, BlockPos origin, Direction facing, PlayerEntity player, float power) {
        Direction left  = facing.rotateYClockwise();
        Direction right = facing.rotateYCounterclockwise();

        int count = 0;
        for (Direction sweep : new Direction[]{left, right}) {
            for (int dist = 1; dist <= BROADSIDE_RANGE; dist++) {
                for (int dy = -BROADSIDE_Y; dy <= BROADSIDE_Y; dy++) {
                    BlockPos check = origin.offset(sweep, dist).up(dy);
                    BlockState cs  = world.getBlockState(check);
                    if (cs.isOf(ModBlocks.SHIP_CANNON)
                            && cs.get(CannonBlock.FACING) == facing
                            && cs.get(CannonBlock.LOADED)
                            && world.getBlockEntity(check) instanceof CannonBlockEntity other) {
                        other.doFire(world, check, cs, player, power, false);
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public void reset() {
        chargeTick = 0;
        ownerUUID  = null;
        markDirty();
    }

    // ── VS2 space helpers ─────────────────────────────────────────────────────

    private static Vec3d worldPos(ServerWorld world, BlockPos pos) {
        Ship ship = ValkyrienSkies.getShipManagingBlock(world, pos.getX(), pos.getY(), pos.getZ());
        if (ship != null) {
            Vector3d v = ship.getTransform().getShipToWorld().transformPosition(
                new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), new Vector3d());
            return new Vec3d(v.x, v.y, v.z);
        }
        return Vec3d.ofCenter(pos);
    }

    private static Vec3d worldFacing(ServerWorld world, BlockPos pos, Direction facing) {
        Ship ship = ValkyrienSkies.getShipManagingBlock(world, pos.getX(), pos.getY(), pos.getZ());
        if (ship != null) {
            Vector3d d = new Vector3d(facing.getOffsetX(), 0, facing.getOffsetZ());
            ship.getTransform().getShipToWorld().transformDirection(d).normalize();
            return new Vec3d(d.x, d.y, d.z);
        }
        return new Vec3d(facing.getOffsetX(), 0, facing.getOffsetZ());
    }

    // Returns the horizontal fire direction clamped to ±MAX_YAW_OFFSET from the cannon axis.
    // playerYaw is the standard Minecraft yaw (0=south, 90=west, -90=east, 180=north).
    private static Vec3d horizontalAimDir(Vec3d cannonFacing, float playerYaw) {
        double cannonYaw = Math.toDegrees(Math.atan2(-cannonFacing.x, cannonFacing.z));
        double delta = playerYaw - cannonYaw;
        // Normalise to [-180, 180]
        while (delta >  180) delta -= 360;
        while (delta < -180) delta += 360;
        delta = Math.max(-MAX_YAW_OFFSET, Math.min(MAX_YAW_OFFSET, delta));
        double aimRad = Math.toRadians(cannonYaw + delta);
        return new Vec3d(-Math.sin(aimRad), 0, Math.cos(aimRad));
    }

    // ── Trajectory arc particles ──────────────────────────────────────────────

    private static void spawnArc(ServerWorld world, ServerPlayerEntity owner, Vec3d origin, Vec3d hFacing,
                                  float pitch, float progress) {
        double speed    = BASE_SPEED * (MIN_POWER + (1.0 - MIN_POWER) * progress);
        double rad      = Math.toRadians(pitch);
        double cosP     = Math.cos(rad);
        double sinP     = Math.sin(rad);

        double vx = hFacing.x * cosP * speed;
        double vy = -sinP * speed;
        double vz = hFacing.z * cosP * speed;
        double x  = origin.x + hFacing.x * SPAWN_OFFSET;
        double y  = origin.y;
        double z  = origin.z + hFacing.z * SPAWN_OFFSET;

        for (int t = 0; t < 80; t++) {
            vx *= 0.99;
            vy -= 0.04;
            vz *= 0.99;
            x  += vx;
            y  += vy;
            z  += vz;
            if (y < world.getBottomY()) break;
            if (t % 3 == 0) {
                world.spawnParticles(owner, ParticleTypes.END_ROD, true, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    // Scans the broadside line and spawns arc particles for every other loaded same-facing cannon.
    // hAimDir is reused from the primary cannon — all broadside cannons are on the same ship
    // and share the same FACING, so their world aim direction is identical.
    private static void spawnBroadsideArcs(ServerWorld world, ServerPlayerEntity owner, BlockPos origin, Direction facing,
                                            Vec3d hAimDir, float pitch, float progress) {
        Direction left  = facing.rotateYClockwise();
        Direction right = facing.rotateYCounterclockwise();
        for (Direction sweep : new Direction[]{left, right}) {
            for (int dist = 1; dist <= BROADSIDE_RANGE; dist++) {
                for (int dy = -BROADSIDE_Y; dy <= BROADSIDE_Y; dy++) {
                    BlockPos check = origin.offset(sweep, dist).up(dy);
                    BlockState cs  = world.getBlockState(check);
                    if (cs.isOf(ModBlocks.SHIP_CANNON)
                            && cs.get(CannonBlock.FACING) == facing
                            && cs.get(CannonBlock.LOADED)) {
                        spawnArc(world, owner, worldPos(world, check), hAimDir, pitch, progress);
                    }
                }
            }
        }
    }

    // ── Action-bar display ────────────────────────────────────────────────────

    private static Text buildBar(float progress) {
        int filled = Math.round(progress * 12);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) sb.append(i < filled ? "§a█" : "§8░");
        int pct = Math.round(progress * 100);
        if (progress >= 1.0f) {
            return Text.literal("§c⚡ §lFULL POWER §e[" + sb + "§e] §f" + pct + "% — §eRight-click to FIRE!");
        }
        return Text.literal("§6⚡ §fCharging §e[" + sb + "§e] §f" + pct + "%");
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("ChargeTick", chargeTick);
        nbt.putInt("Ammo", ammo);
        if (ownerUUID != null) nbt.putUuid("OwnerUUID", ownerUUID);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        chargeTick = nbt.getInt("ChargeTick");
        ammo       = nbt.getInt("Ammo");
        ownerUUID  = nbt.containsUuid("OwnerUUID") ? nbt.getUuid("OwnerUUID") : null;
    }
}
