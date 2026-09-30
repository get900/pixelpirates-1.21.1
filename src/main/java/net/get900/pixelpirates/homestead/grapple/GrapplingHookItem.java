package net.get900.pixelpirates.homestead.grapple;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GRAPPLING HOOK (#22): use to throw a three-pronged hook on a line (28 blocks). It bites into a block and reels you
 * to it - up a cliff, onto a mast, across a gap between ships - or yanks a creature it hits toward you. Use again or
 * sneak to let go. 1 durability per throw.
 */
public class GrapplingHookItem extends Item {
    /** thrower -> their hook entity id (server side). */
    static final Map<UUID, Integer> ACTIVE = new ConcurrentHashMap<>();

    public GrapplingHookItem(Settings s) { super(s); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack s = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(s);
        Integer id = ACTIVE.remove(user.getUuid());
        if (id != null) {
            Entity e = ((ServerWorld) world).getEntityById(id);
            if (e instanceof GrappleHookEntity h) {
                h.retract();
                return TypedActionResult.success(s);
            }
        }
        GrappleHookEntity hook = new GrappleHookEntity(world, user);
        hook.setVelocity(user, user.getPitch(), user.getYaw(), 0f, 2.4f, 0.5f);
        world.spawnEntity(hook);
        ACTIVE.put(user.getUuid(), hook.getId());
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_FISHING_BOBBER_THROW, SoundCategory.PLAYERS, 1f, 0.6f);
        s.damage(1, user, p -> p.sendToolBreakStatus(hand));
        user.getItemCooldownManager().set(this, 10);
        return TypedActionResult.success(s);
    }
}
