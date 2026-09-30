package net.get900.pixelpirates.world;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.get900.pixelpirates.network.ModNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Testing aids toggled by /pptest (runtime only - they reset when the server restarts).
 *
 * GOD MODE makes the player unkillable, NOT invulnerable: hits, knockback, fire, pulls and status
 * effects all land (so abilities can be watched working), health/hunger/air refill every tick, and
 * {@link #allowDeath} cancels any killing blow. It deliberately does NOT use the abilities
 * "invulnerable" flag - in 1.20.1 LivingEntity.canTarget() returns target.canTakeDamage(), which is
 * false for an invulnerable player, so no mob would ever target you. /kill and the void
 * (BYPASSES_INVULNERABILITY damage) still kill.
 *
 * CLEAR SIGHT removes everything that makes phases 4-5 hard to see: the forced zone fog, zone
 * particles and biome ambient particles (client side, synced via S2C_TEST_FLAGS), zone hazards
 * (skipped in ZoneHazardManager / ZoneEffectsServer), and Darkness / Blindness / Nausea from any
 * source; it also keeps Night Vision + Conduit Power on (conduit power = full underwater view).
 */
public final class TestModes {
    private TestModes() {}

    public static boolean god(ServerPlayerEntity p) { return AdminTestState.godMode.contains(p.getUuid()); }

    public static boolean clearSight(ServerPlayerEntity p) { return AdminTestState.clearSight.contains(p.getUuid()); }

    public static void setGod(ServerPlayerEntity p, boolean on) {
        if (on) AdminTestState.godMode.add(p.getUuid());
        else AdminTestState.godMode.remove(p.getUuid());
        // the old god mode set this flag and abilities persist in player NBT - clear it either way
        if (!p.isCreative() && !p.isSpectator() && p.getAbilities().invulnerable) {
            p.getAbilities().invulnerable = false;
            p.sendAbilitiesUpdate();
        }
    }

    /** ServerLivingEntityEvents.ALLOW_DEATH: a god-mode player survives any blow except /kill and the void. */
    public static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
        if (!(entity instanceof ServerPlayerEntity p) || !god(p)) return true;
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
        p.setHealth(p.getMaxHealth());
        return false;
    }

    public static void setClearSight(ServerPlayerEntity p, boolean on) {
        if (on) AdminTestState.clearSight.add(p.getUuid());
        else {
            AdminTestState.clearSight.remove(p.getUuid());
            p.removeStatusEffect(StatusEffects.NIGHT_VISION);
            p.removeStatusEffect(StatusEffects.CONDUIT_POWER);
        }
        sync(p);
    }

    /** Tell the client whether to drop its zone fog / particles. */
    public static void sync(ServerPlayerEntity p) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(clearSight(p));
        ServerPlayNetworking.send(p, ModNetworking.S2C_TEST_FLAGS, buf);
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (god(p)) {
                p.getHungerManager().setFoodLevel(20);
                p.getHungerManager().setSaturationLevel(10f);
                if (p.isOnFire()) p.extinguish();
                if (p.getHealth() < p.getMaxHealth()) p.setHealth(p.getMaxHealth());
                p.setAir(p.getMaxAir());
            }
            if (clearSight(p)) {
                p.removeStatusEffect(StatusEffects.DARKNESS);
                p.removeStatusEffect(StatusEffects.BLINDNESS);
                p.removeStatusEffect(StatusEffects.NAUSEA);
                refresh(p, new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, true, false));
                refresh(p, new StatusEffectInstance(StatusEffects.CONDUIT_POWER, 400, 0, true, false));
            }
        }
    }

    /** Re-apply long before expiry so night vision never hits its end-of-effect flicker. */
    private static void refresh(ServerPlayerEntity p, StatusEffectInstance e) {
        StatusEffectInstance cur = p.getStatusEffect(e.getEffectType());
        if (cur == null || cur.getDuration() < 240) p.addStatusEffect(e);
    }
}
