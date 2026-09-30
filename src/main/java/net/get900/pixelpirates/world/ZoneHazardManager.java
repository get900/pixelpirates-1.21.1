package net.get900.pixelpirates.world;

import net.get900.pixelpirates.enchantment.ModEnchantments;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.entity.damage.DamageSources;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.random.Random;

public class ZoneHazardManager {

    private static final double ZONE3_INNER = 2500.0;
    private static final double ZONE3_OUTER = 3500.0;
    private static final double ZONE5_INNER = 4500.0;

    private static final String[] MADNESS_MESSAGES = {
        "§5Something watches you from beneath the waves...",
        "§5The sea is speaking. You shouldn't be able to hear it.",
        "§5Your reflection in the water smiles before you do.",
        "§5The compass spins in perfect circles.",
        "§5You have been this way before. You are certain of it.",
        "§5The stars are in the wrong positions.",
    };

    public static void enforce(ServerPlayerEntity player) {
        if (TestModes.clearSight(player)) return;          // /pptest clearsight
        double bx = player.getX(), bz = player.getZ();
        double dist = Math.sqrt(bx * bx + bz * bz);

        if (dist >= ZONE3_INNER && dist < ZONE3_OUTER) {
            applyHeatHazard(player);
        } else if (dist >= ZONE5_INNER) {
            applyMadnessHazard(player);
        }
    }

    private static boolean hasItem(ServerPlayerEntity player, net.minecraft.item.Item item) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i).isOf(item)) return true;
        }
        return false;
    }

    // Called every 40 ticks (2 seconds) — deal 1 heart of damage if in water without heat amulet
    private static void applyHeatHazard(ServerPlayerEntity player) {
        if (hasItem(player, ModItems.HEAT_AMULET) || hasItem(player, ModItems.SERPENTS_TIDE_PEARL)) return;
        if (!player.isTouchingWater()) return;
        player.damage(player.getServerWorld().getDamageSources().hotFloor(), 2.0f);
        player.sendMessage(Text.literal("§cThe water is scalding hot!"), true);
    }

    // Called every 40 ticks (2 seconds) — apply creeping madness effects without sanity amulet.
    // Frozen Seeker reduces effect chance: level 1 = 50% skip, level 2 = 75% skip, level 3 = immune
    private static void applyMadnessHazard(ServerPlayerEntity player) {
        if (hasItem(player, ModItems.SANITY_AMULET) || hasItem(player, ModItems.SPECTRAL_ANCHOR)) return;
        int frozenLevel = ModEnchantments.getFrozenSeekerLevel(player);
        if (frozenLevel >= 3) return;

        Random rng = player.getRandom();
        // Check skip chance before applying any effects
        float skipChance = frozenLevel == 1 ? 0.50f : frozenLevel == 2 ? 0.75f : 0.0f;
        if (rng.nextFloat() < skipChance) return;

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA,    100, 0, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS,   80, 0, false, false));

        if (rng.nextFloat() < 0.35f) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 50, 0, false, false));
        }
        if (rng.nextFloat() < 0.20f) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 60, 0, false, false));
        }
        if (rng.nextFloat() < 0.25f) {
            int idx = rng.nextInt(MADNESS_MESSAGES.length);
            player.sendMessage(Text.literal(MADNESS_MESSAGES[idx]), true);
        }
    }
}
