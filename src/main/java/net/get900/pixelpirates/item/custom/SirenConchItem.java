package net.get900.pixelpirates.item.custom;

import net.get900.pixelpirates.entity.mob.ModBoss;
import net.get900.pixelpirates.sound.ModSounds;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * SIREN'S CONCH (phase 2, rare siren drop): blow it and the siren's song lulls every hostile within 12 blocks - they
 * forget you and stand still for 6 seconds. Bosses don't listen. 60 s cooldown.
 */
public class SirenConchItem extends Item {
    private static final double RANGE = 12;
    private static final int LULL_TICKS = 120, COOLDOWN = 1200;
    /** Lulled mob -> server tick the lull ends. */
    private static final Map<MobEntity, Long> LULLED = new WeakHashMap<>();

    public SirenConchItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        world.playSound(null, user.getX(), user.getY(), user.getZ(), ModSounds.SIREN_SONG, SoundCategory.PLAYERS, 1.2f, 1.1f);
        if (world instanceof ServerWorld sw) {
            long until = sw.getServer().getTicks() + LULL_TICKS;
            for (MobEntity m : sw.getEntitiesByClass(MobEntity.class, user.getBoundingBox().expand(RANGE),
                    m -> hostile(m) && !(m instanceof ModBoss) && m.squaredDistanceTo(user) <= RANGE * RANGE)) {
                LULLED.put(m, until);
                sw.spawnParticles(ParticleTypes.NOTE, m.getX(), m.getEyeY() + 0.5, m.getZ(), 3, 0.3, 0.2, 0.3, 1.0);
            }
            sw.spawnParticles(ParticleTypes.NOTE, user.getX(), user.getEyeY() + 0.3, user.getZ(), 12, 1.5, 0.5, 1.5, 1.0);
            stack.damage(1, user, p -> p.sendToolBreakStatus(hand));
        }
        user.getItemCooldownManager().set(this, COOLDOWN);
        return TypedActionResult.success(stack, world.isClient());
    }

    private static boolean hostile(MobEntity m) {
        if (m instanceof net.get900.pixelpirates.entity.mob.ModMob mm)
            return mm.spec().temper() != net.get900.pixelpirates.entity.mob.MobSpec.Temper.PASSIVE;
        return m instanceof Monster || m.getTarget() != null;
    }

    /** Server tick: lulled mobs keep forgetting their target until the song fades. */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (LULLED.isEmpty()) return;
        long now = server.getTicks();
        for (Iterator<Map.Entry<MobEntity, Long>> it = LULLED.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            MobEntity m = e.getKey();
            if (m == null || !m.isAlive() || now >= e.getValue()) { it.remove(); continue; }
            m.setTarget(null);
            m.getNavigation().stop();
            if (now % 20 == 0 && m.getWorld() instanceof ServerWorld sw)
                sw.spawnParticles(ParticleTypes.NOTE, m.getX(), m.getEyeY() + 0.5, m.getZ(), 1, 0.2, 0.1, 0.2, 1.0);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Blow it: hostiles within 12 blocks forget you for 6 s").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("Bosses won't listen - 60 s cooldown").formatted(Formatting.GRAY));
    }
}
