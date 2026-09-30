package net.get900.pixelpirates.homestead.nav;

import net.get900.pixelpirates.entity.custom.FloatingBarrelEntity;
import net.get900.pixelpirates.world.AiShipController;
import net.get900.pixelpirates.world.ShipHealthState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpyglassItem;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;

/**
 * THE CAPTAIN'S SPYGLASS (#17): a brass spyglass that reads what it sees while you look through it -
 *  - a creature under the crosshair (up to 160 blocks): its name and health;
 *  - a ship under the crosshair (up to 240): the flag it flies (AI faction) and its hull;
 *  - TREASURE GLINTS: unopened loot chests / barrels / salvage crates and floating loot barrels within 64 blocks in
 *    the field of view sparkle (particles sent to the looker only).
 * All server side; the zoom is vanilla's spyglass (the client zooms for any SpyglassItem while using).
 */
public class CaptainsSpyglassItem extends SpyglassItem {
    public CaptainsSpyglassItem(Settings s) { super(s); }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remaining) {
        if (world.isClient || !(user instanceof ServerPlayerEntity p)) return;
        int used = getMaxUseTime(stack) - remaining;
        if (used % 5 == 0) identify((ServerWorld) world, p);
        if (used % 20 == 10) glints((ServerWorld) world, p);
    }

    private static void identify(ServerWorld w, ServerPlayerEntity p) {
        Vec3d eye = p.getEyePos(), look = p.getRotationVec(1f);
        Vec3d end = eye.add(look.multiply(160));
        EntityHitResult hit = ProjectileUtil.raycast(p, eye, end, p.getBoundingBox().stretch(look.multiply(160)).expand(1),
                e -> e instanceof LivingEntity && !e.isSpectator() && e != p && !e.isInvisible(), 160 * 160);
        if (hit != null && hit.getEntity() instanceof LivingEntity le) {
            int d = (int) le.distanceTo(p);
            p.sendMessage(Text.literal("").append(le.getDisplayName().copy().formatted(Formatting.GOLD))
                    .append(Text.literal("  " + (int) Math.ceil(le.getHealth()) + "/" + (int) le.getMaxHealth() + " hp  -  " + d + " blocks")
                            .formatted(Formatting.WHITE)), true);
            return;
        }
        for (int i = 8; i <= 240; i += 4) {                                  // march along the look ray for a ship hull
            Vec3d q = eye.add(look.multiply(i));
            org.joml.primitives.AABBd b = new org.joml.primitives.AABBd(q.x - 1.5, q.y - 1.5, q.z - 1.5, q.x + 1.5, q.y + 1.5, q.z + 1.5);
            try {
                for (Ship s : ValkyrienSkies.getShipsIntersecting(w, b)) {
                    long id = s.getId();
                    int hp = ShipHealthState.get(w).getHealth(id);
                    String flag = AiShipController.AI_SHIPS.containsKey(id) ? AiShipController.getFactionForShip(id).displayName : "an unflagged ship";
                    p.sendMessage(Text.literal(flag + "§r  hull " + hp + "/" + ShipHealthState.MAX_HP + "  -  " + i + " blocks").formatted(Formatting.WHITE), true);
                    return;
                }
            } catch (Exception ignored) {
                return;                                                      // VS not ready in this world
            }
        }
    }

    private static void glints(ServerWorld w, ServerPlayerEntity p) {
        Vec3d eye = p.getEyePos(), look = p.getRotationVec(1f);
        ChunkPos c = p.getChunkPos();
        for (int cx = c.x - 4; cx <= c.x + 4; cx++)
            for (int cz = c.z - 4; cz <= c.z + 4; cz++) {
                WorldChunk ch = w.getChunkManager().getWorldChunk(cx, cz, false);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    if (!(be instanceof LootableContainerBlockEntity) || !be.createNbt().contains("LootTable")) continue;
                    spark(w, p, eye, look, Vec3d.ofCenter(be.getPos()));
                }
            }
        for (FloatingBarrelEntity b : w.getEntitiesByClass(FloatingBarrelEntity.class, p.getBoundingBox().expand(64), e -> true))
            spark(w, p, eye, look, b.getPos().add(0, 0.8, 0));
    }

    private static void spark(ServerWorld w, ServerPlayerEntity p, Vec3d eye, Vec3d look, Vec3d at) {
        Vec3d to = at.subtract(eye);
        double d = to.length();
        if (d > 64 || d < 2 || to.normalize().dotProduct(look) < 0.94) return;
        w.spawnParticles(p, ParticleTypes.WAX_ON, true, at.x, at.y + 0.6, at.z, 6, 0.4, 0.4, 0.4, 0.02);
        w.spawnParticles(p, ParticleTypes.END_ROD, true, at.x, at.y + 1.2, at.z, 1, 0, 0.3, 0, 0.01);
    }
}
