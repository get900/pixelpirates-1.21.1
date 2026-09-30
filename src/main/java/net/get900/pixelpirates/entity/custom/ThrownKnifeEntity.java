package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.item.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

public class ThrownKnifeEntity extends ThrownItemEntity {

    public ThrownKnifeEntity(EntityType<? extends ThrownItemEntity> type, World world) {
        super(type, world);
    }

    public ThrownKnifeEntity(EntityType<? extends ThrownItemEntity> type, World world, LivingEntity owner) {
        super(type, owner, world);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.THROWING_KNIFE;
    }

    @Override
    protected void onEntityHit(EntityHitResult entityHitResult) {
        super.onEntityHit(entityHitResult);
        if (!this.getWorld().isClient) {
            entityHitResult.getEntity().damage(this.getDamageSources().thrown(this, this.getOwner()), 5.0f);
            this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ITEM_TRIDENT_HIT, SoundCategory.PLAYERS, 0.8f, 1.4f);
        }
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (!this.getWorld().isClient) {
            if (hitResult.getType() == HitResult.Type.BLOCK) {
                this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.ITEM_TRIDENT_HIT_GROUND, SoundCategory.PLAYERS, 0.6f, 1.6f);
            }
            this.discard();
        }
    }
}
