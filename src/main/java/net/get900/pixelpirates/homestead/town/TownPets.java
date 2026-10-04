package net.get900.pixelpirates.homestead.town;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Townsfolk pets (2026-10-04, round 3): Agatha's ginger cat Marmalade, the Commodore's cat Admiral, Silas's old dog Biscuit,
 * Elspeth's sheepdog Shep, Hob's dog Barley and Rufus's parrot Captain Squawk (Townsfolk.PETS). They follow their person about,
 * sit by them while they sleep, and can't be hurt or tamed away (tamed with no player owner). If lost they come back.
 */
final class TownPets {
    private TownPets() {}

    static void tick(ServerWorld w, TownsfolkEntity e) {
        String[] pet = Townsfolk.PETS.get(e.folkId());
        if (pet == null || (e.age + e.getId()) % 40 != 0) return;
        Entity p = e.petUuid == null ? null : w.getEntity(e.petUuid);
        if (!(p instanceof TameableEntity t) || !p.isAlive()) {
            if (++e.petMissing < 4) return;
            e.petMissing = 0;
            spawn(w, e, pet);
            return;
        }
        e.petMissing = 0;
        boolean rest = e.isSleeping() || e.hasVehicle();
        t.setSitting(rest);
        t.setInSittingPose(rest);
        double d = p.squaredDistanceTo(e);
        if (d > 24 * 24 || (d > 10 * 10 && !TownLife.watched(w, p.getPos(), 32))) {
            Vec3d at = e.getPos().add(w.random.nextDouble() * 2 - 1, 0, w.random.nextDouble() * 2 - 1);
            p.refreshPositionAndAngles(at.x, at.y + (p instanceof ParrotEntity ? 1 : 0), at.z, p.getYaw(), 0);
        } else if (d > 4 * 4 && !rest) t.getNavigation().startMovingTo(e, 1.1);
        if (e.age % 400 < 40) {                                                         // the odd stray from an older save: only one of each
            for (Entity o : w.getOtherEntities(p, new Box(e.getBlockPos()).expand(48), o -> o.getCommandTags().contains("pp_pet:" + e.folkId()))) o.discard();
        }
    }

    private static void spawn(ServerWorld w, TownsfolkEntity e, String[] pet) {
        if (!w.isChunkLoaded(e.getBlockPos())) return;
        TameableEntity t = switch (pet[0]) {
            case "cat" -> EntityType.CAT.create(w);
            case "wolf" -> EntityType.WOLF.create(w);
            default -> EntityType.PARROT.create(w);
        };
        if (t == null) return;
        t.refreshPositionAndAngles(e.getX() + 0.5, e.getY(), e.getZ() + 0.5, e.getYaw(), 0);
        t.initialize(w, w.getLocalDifficulty(e.getBlockPos()), SpawnReason.MOB_SUMMONED, null, null);
        t.setTamed(true);
        t.setCustomName(Text.literal(pet[1]));
        t.setPersistent();
        t.setInvulnerable(true);
        t.addCommandTag("pp_pet:" + e.folkId());
        if (t instanceof WolfEntity wolf) wolf.setCollarColor(pet[1].equals("Shep") ? DyeColor.BLUE : DyeColor.RED);
        if (t instanceof CatEntity cat && pet[1].equals("Marmalade"))
            cat.setVariant(net.minecraft.registry.Registries.CAT_VARIANT.get(net.minecraft.entity.passive.CatVariant.RED));
        if (t instanceof ParrotEntity parrot) parrot.setVariant(ParrotEntity.Variant.RED_BLUE);
        w.spawnEntity(t);
        e.petUuid = t.getUuid();
    }
}
