package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.world.AiShipController;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.internal.world.VsiServerShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FloatingBarrelEntity extends Entity {

    private static final int LIFETIME = 6000; // 5 minutes at 20 tps

    public FloatingBarrelEntity(EntityType<? extends FloatingBarrelEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initDataTracker() {}

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {}

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {}

    @Override
    public void tick() {
        super.tick(); // increments age, updates water/fire state

        if (!this.getWorld().isClient && this.age > LIFETIME) {
            this.discard();
            return;
        }

        if (!this.getWorld().isClient) {
            ServerWorld sw = (ServerWorld) this.getWorld();

            // ── Ship collision detection (every 2 ticks) ──────────────────────
            // Distance-based check against all loaded player-owned VS2 ships.
            // Using ship-center XZ distance avoids dependence on integer block
            // alignment, which is unreliable for moving ships at angles.
            if (this.age % 2 == 0) {
                VsiServerShipWorld vsWorld = VSGameUtilsKt.getShipObjectWorld(sw);
                if (vsWorld != null) {
                    Vec3d barrelPos = this.getPos();
                    for (Map.Entry<Long, ? extends LoadedServerShip> entry
                            : vsWorld.getLoadedShips().getIdToShipData().entrySet()) {
                        if (AiShipController.AI_SHIPS.containsKey(entry.getKey())) continue;
                        Vector3dc center = entry.getValue().getTransform().getPositionInWorld();
                        double dx = center.x() - barrelPos.x;
                        double dz = center.z() - barrelPos.z;
                        if (dx * dx + dz * dz > 225.0) continue; // > 15 blocks XZ
                        if (Math.abs(center.y() - barrelPos.y) > 10) continue;
                        PlayerEntity captain = sw.getClosestPlayer(
                            this.getX(), this.getY(), this.getZ(), 50, false);
                        if (captain != null) {
                            List<ItemStack> loot = generateLoot();
                            for (ItemStack stack : loot) captain.giveItemStack(stack);
                            if (captain instanceof net.minecraft.server.network.ServerPlayerEntity sp)
                                net.get900.pixelpirates.world.PirateXp.discovery(sp, net.get900.pixelpirates.world.PirateLevelingSystem.XP_BARREL_LOOT);
                            captain.sendMessage(
                                Text.literal("§6Your ship scooped up a floating barrel!"), true);
                        }
                        this.discard();
                        return;
                    }
                }
            }

            // ── Red smoke signal (every 15 ticks) ────────────────────────────
            if (this.age % 15 == 0) {
                sw.spawnParticles(
                    new DustParticleEffect(new Vector3f(0.9f, 0.08f, 0.05f), 2.5f),
                    this.getX(), this.getY() + 1.1, this.getZ(),
                    4, 0.12, 0.12, 0.12, 0.05
                );
            }

            // ── Floating physics ──────────────────────────────────────────────
            double targetY = sw.getSeaLevel();
            double dy = targetY - this.getY();

            Vec3d vel = this.getVelocity();
            if (this.isTouchingWater()) {
                double force = dy > 0
                    ? Math.min(dy * 0.15, 0.10)
                    : Math.max(dy * 0.08, -0.04);
                vel = vel.add(0, force, 0);
            } else {
                vel = vel.add(0, -0.08, 0);
            }
            this.setVelocity(vel.multiply(0.85, 0.65, 0.85));
            try {
                this.move(MovementType.SELF, this.getVelocity());
            } catch (NullPointerException e) {
                // VS2 entity-section race condition (fastutil LongAVLTreeSet concurrent access).
                // Skip movement this tick — barrel stays alive and retries next tick.
            }
        }
    }

    // Kept as a fallback for when the player isn't on a ship
    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (this.getWorld().isClient) return ActionResult.SUCCESS;
        List<ItemStack> loot = generateLoot();
        if (net.get900.pixelpirates.world.SkillEffects.doubleLoot(player, false)) loot.addAll(generateLoot());      // Treasure Hunter
        player.sendMessage(Text.literal("§6You rummage through the floating barrel..."), true);
        for (ItemStack stack : loot) player.giveItemStack(stack);
        if (player instanceof net.minecraft.server.network.ServerPlayerEntity sp)
            net.get900.pixelpirates.world.PirateXp.discovery(sp, net.get900.pixelpirates.world.PirateLevelingSystem.XP_BARREL_LOOT);
        this.discard();
        return ActionResult.SUCCESS;
    }

    private List<ItemStack> generateLoot() {
        var rng = this.getWorld().getRandom();
        List<ItemStack> loot = new ArrayList<>();
        int count = 2 + rng.nextInt(3);

        for (int i = 0; i < count; i++) {
            int roll = rng.nextInt(100);
            if (roll < 28) {
                loot.add(new ItemStack(ModItems.BANANA, 2 + rng.nextInt(5)));
            } else if (roll < 50) {
                loot.add(new ItemStack(ModItems.COOKED_SHARK_MEAT, 1 + rng.nextInt(3)));
            } else if (roll < 65) {
                loot.add(new ItemStack(ModItems.COOKED_SALTED_SWIMMER, 1 + rng.nextInt(3)));
            } else if (roll < 76) {
                loot.add(new ItemStack(ModItems.CANNON_BALL, 4 + rng.nextInt(9)));
            } else if (roll < 84) {
                loot.add(new ItemStack(ModItems.GROG, 1 + rng.nextInt(3)));
            } else if (roll < 91) {
                loot.add(new ItemStack(ModItems.COIN, 2 + rng.nextInt(6)));
            } else if (roll < 97) {
                loot.add(new ItemStack(ModItems.BOUNTY_MAP));
            } else {
                loot.add(new ItemStack(ModItems.SHIP_REPAIR_KIT));
            }
        }
        return loot;
    }
}
