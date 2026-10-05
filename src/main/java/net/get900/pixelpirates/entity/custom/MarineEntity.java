package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.world.faction.Faction;
import net.get900.pixelpirates.world.faction.FactionManager;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;

/**
 * AN ARMADA MARINE (2026-10-05; the user: "I would like the illagers replaced with something"): the Iron Armada's ship
 * crews were vindicators. A marine is the pirate crew's body in Armada blue (textures/entity/navy_marine.png,
 * tools/gen_crew_textures.py) with an iron sword - and unlike a pirate it only goes for captains the Armada counts as
 * enemies (FactionManager.isHostileTo, the same test the navy's ships use); anyone else it leaves be unless struck.
 */
public class MarineEntity extends PirateCrewEntity {
    public MarineEntity(EntityType<? extends PathAwareEntity> type, World world) { super(type, world); }

    @Override
    protected boolean wouldAttack(LivingEntity e) {
        return e instanceof ServerPlayerEntity p && FactionManager.isHostileTo(Faction.NAVY, p);
    }

    @Override
    public ItemStack weapon() { return new ItemStack(Items.IRON_SWORD); }
}
