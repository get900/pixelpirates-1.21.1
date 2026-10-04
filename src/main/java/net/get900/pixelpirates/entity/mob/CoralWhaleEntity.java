package net.get900.pixelpirates.entity.mob;

import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.item.food.PirateFoods;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

import java.util.List;

/**
 * CORAL WHALE (phase 5, passive). Feed it a KRILL CLUSTER and it does a happy barrel roll ("roll" clip), sprays,
 * and leaves you a WHALE'S BOUNTY. Each whale wants feeding once every 5 minutes.
 */
public class CoralWhaleEntity extends ModMob {
    private static final int FED_COOLDOWN = 6000, ROLL_TICKS = 32;
    private long fedAt = -FED_COOLDOWN;
    private int bountyIn = -1;

    public CoralWhaleEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected List<String> extraAnims() { return List.of("roll"); }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        ItemStack held = player.getStackInHand(hand);
        if (!held.isOf(ModItems.KRILL_CLUSTER)) return super.interactMob(player, hand);
        if (this.getWorld().isClient) return ActionResult.SUCCESS;
        long now = this.getWorld().getTime();
        if (now - fedAt < FED_COOLDOWN) {
            player.sendMessage(Text.literal("The whale is still full.").formatted(Formatting.GRAY), true);
            return ActionResult.CONSUME;
        }
        fedAt = now;
        if (!player.getAbilities().creativeMode) held.decrement(1);
        triggerAnim(ACTION, "roll");
        this.playSound(SoundEvents.ENTITY_DOLPHIN_PLAY, 1.5f, 0.6f);
        bountyIn = ROLL_TICKS;
        return ActionResult.CONSUME;
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (bountyIn < 0) return;
        if (this.getWorld() instanceof ServerWorld sw && bountyIn % 4 == 0)
            sw.spawnParticles(ParticleTypes.SPLASH, getX(), getY() + getHeight(), getZ(), 12, 0.6, 0.3, 0.6, 0.3);
        if (--bountyIn == 0) {
            if (this.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, getX(), getY() + getHeight() * 0.5, getZ(), 30, 0.8, 0.5, 0.8, 0.1);
                sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + getHeight() + 0.5, getZ(), 8, 0.8, 0.3, 0.8, 0.0);
            }
            this.dropStack(new ItemStack(PirateFoods.item("whales_bounty")), (float) getHeight());
            bountyIn = -1;
        }
    }
}
