package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.entity.ModEntities;
import net.get900.pixelpirates.item.RelicWeapons;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * TIDE ARROW (the Serpentspine Longbow's full-draw shot): an arrow that bursts in a splash where it lands - knocks
 * everything within 2.5 blocks back, puts out fire, and hits fire mobs / anything water hurts for +4.
 * Picked back up as a normal arrow.
 */
public class TideArrowEntity extends PersistentProjectileEntity {
    private boolean splashed;

    public TideArrowEntity(EntityType<? extends TideArrowEntity> type, World world) { super(type, world); }

    public TideArrowEntity(World world, LivingEntity owner) { super(ModEntities.TIDE_ARROW, owner, world); }

    @Override
    protected ItemStack asItemStack() { return new ItemStack(Items.ARROW); }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient && !this.inGround && this.age % 2 == 0)
            this.getWorld().addParticle(ParticleTypes.FALLING_WATER, getX(), getY(), getZ(), 0, 0, 0);
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        super.onEntityHit(hit);
        splash();
    }

    @Override
    protected void onBlockHit(BlockHitResult hit) {
        super.onBlockHit(hit);
        splash();
    }

    private void splash() {
        if (splashed || !(this.getWorld() instanceof ServerWorld sw)) return;
        splashed = true;
        Vec3d at = getPos();
        sw.spawnParticles(ParticleTypes.SPLASH, at.x, at.y + 0.5, at.z, 60, 1.2, 0.5, 1.2, 0.3);
        sw.spawnParticles(ParticleTypes.BUBBLE_POP, at.x, at.y + 0.5, at.z, 30, 1.0, 0.5, 1.0, 0.1);
        sw.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 1.2f, 1.3f);
        LivingEntity owner = getOwner() instanceof LivingEntity o ? o : null;
        for (LivingEntity e : RelicWeapons.around(sw, at, 2.5, owner)) {
            if (e.isOnFire()) e.extinguish();
            if (owner != null && (e.isFireImmune() || e instanceof net.get900.pixelpirates.entity.mob.ModMob m && m.spec().isHurtByWater()))
                RelicWeapons.hit(e, owner, 4f);
            RelicWeapons.knock(e, e.getPos().subtract(at), 0.6, 0.25);
        }
    }
}
