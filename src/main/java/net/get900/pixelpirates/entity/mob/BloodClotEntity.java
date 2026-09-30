package net.get900.pixelpirates.entity.mob;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

/** BLOOD CLOT - crawls out of the Abyssal Heart's chamber walls in phase 2 and gums up anyone it hits (slowness, MobSpecs). */
public class BloodClotEntity extends ModMob {
    public BloodClotEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected SoundEvent sound(String kind) {
        return switch (kind) {
            case "hurt" -> SoundEvents.ENTITY_SLIME_HURT_SMALL;
            case "death" -> SoundEvents.ENTITY_SLIME_DEATH_SMALL;
            case "attack" -> SoundEvents.ENTITY_SLIME_ATTACK;
            case "ambient" -> SoundEvents.ENTITY_SLIME_SQUISH_SMALL;
            default -> null;
        };
    }

    @Override
    public float getSoundPitch() { return 0.6f + this.random.nextFloat() * 0.2f; }
}
