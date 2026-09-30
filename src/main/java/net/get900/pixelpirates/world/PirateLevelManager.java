package net.get900.pixelpirates.world;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.get900.pixelpirates.network.ModNetworking;
import net.get900.pixelpirates.util.PlayerProgressionComponent;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side logic for the pirate leveling system.
 * Handles XP award, level-up, skill spending, attribute modifiers, and per-tick effects.
 */
public final class PirateLevelManager {

    private PirateLevelManager() {}

    // ── Attribute modifier UUIDs ──────────────────────────────────────────────

    public static final UUID UUID_BLADE_MASTERY  = uuid("pp_blade_mastery");
    public static final UUID UUID_SWIFT_STRIKES  = uuid("pp_swift_strikes");
    public static final UUID UUID_SEA_LEGS        = uuid("pp_sea_legs");
    public static final UUID UUID_IRON_SKIN       = uuid("pp_iron_skin");
    public static final UUID UUID_SEA_TOUGHNESS  = uuid("pp_sea_toughness");

    private static UUID uuid(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    // ── Skill index cache ─────────────────────────────────────────────────────

    private static final Map<String, Integer> SKILL_INDICES;

    static {
        Map<String, Integer> m = new HashMap<>();
        List<PirateLevelingSystem.SkillDef> all = PirateLevelingSystem.ALL_SKILLS;
        for (int i = 0; i < all.size(); i++) m.put(all.get(i).key(), i);
        SKILL_INDICES = Collections.unmodifiableMap(m);
    }

    public static int getSkillIndex(String key) {
        return SKILL_INDICES.getOrDefault(key, -1);
    }

    public static int getSkillLevel(PlayerEntity player, String key) {
        int idx = getSkillIndex(key);
        return (idx >= 0) ? ((PlayerProgressionComponent) player).pp_getSkillLevel(idx) : 0;
    }

    // ── XP and leveling ───────────────────────────────────────────────────────

    public static void awardXp(ServerPlayerEntity player, int amount) {
        awardXp(player, amount, false);
    }

    public static void awardXp(ServerPlayerEntity player, int amount, boolean isDiscovery) {
        PlayerProgressionComponent comp = (PlayerProgressionComponent) player;
        int curLevel = comp.pp_getPirateLevel();
        if (curLevel >= PirateLevelingSystem.MAX_LEVEL) return;

        // Explorer skill bonus for discovery XP (barrel loots, zone unlocks)
        if (isDiscovery) {
            int explorerLvl = getSkillLevel(player, "explorer");
            if (explorerLvl > 0) amount = (int)(amount * (1.0 + explorerLvl * 0.05));
        }

        int xp = comp.pp_getPirateXp() + amount;
        boolean levelled = false;

        while (curLevel < PirateLevelingSystem.MAX_LEVEL) {
            int needed = PirateLevelingSystem.xpToNextLevel(curLevel);
            if (xp < needed) break;
            xp -= needed;
            curLevel++;
            levelled = true;

            int pts = PirateLevelingSystem.skillPointsAt(curLevel);
            comp.pp_setSkillPoints(comp.pp_getSkillPoints() + pts);

            String rank  = PirateLevelingSystem.rankName(curLevel);
            String color = PirateLevelingSystem.rankColor(curLevel);
            player.sendMessage(Text.literal(
                "§6** Level Up! §fYou are now §b" + curLevel +
                " §7[" + color + rank + "§7] §f+§a" + pts +
                " skill point" + (pts == 1 ? "" : "s")), false);
            player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1f, 1.2f);
        }

        comp.pp_setPirateLevel(curLevel);
        comp.pp_setPirateXp(xp);

        if (levelled) applyAttributeModifiers(player);
        syncToClient(player);
    }

    // ── Skill spending ────────────────────────────────────────────────────────

    public static boolean spendSkillPoint(ServerPlayerEntity player, String skillKey) {
        PlayerProgressionComponent comp = (PlayerProgressionComponent) player;
        if (comp.pp_getSkillPoints() <= 0) return false;

        PirateLevelingSystem.SkillDef def = PirateLevelingSystem.BY_KEY.get(skillKey);
        if (def == null) return false;

        int idx = getSkillIndex(skillKey);
        if (idx < 0) return false;

        int curSkillLvl = comp.pp_getSkillLevel(idx);
        if (curSkillLvl >= def.maxLevel()) return false;

        comp.pp_setSkillLevel(idx, curSkillLvl + 1);
        comp.pp_setSkillPoints(comp.pp_getSkillPoints() - 1);

        applyAttributeModifiers(player);
        syncToClient(player);

        player.sendMessage(Text.literal(
            "§a+" + def.displayName() + " §7→ §bLevel " + (curSkillLvl + 1) +
            "/" + def.maxLevel()), true);
        return true;
    }

    // ── Attribute modifiers ───────────────────────────────────────────────────

    public static void applyAttributeModifiers(ServerPlayerEntity player) {
        PlayerProgressionComponent comp = (PlayerProgressionComponent) player;

        applyMult(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, UUID_BLADE_MASTERY,
            "pp_blade_mastery", getSkillLevel(player, "blade_mastery") * 0.04,
            EntityAttributeModifier.Operation.MULTIPLY_BASE);

        applyMult(player, EntityAttributes.GENERIC_ATTACK_SPEED, UUID_SWIFT_STRIKES,
            "pp_swift_strikes", getSkillLevel(player, "swift_strikes") * 0.05,
            EntityAttributeModifier.Operation.MULTIPLY_BASE);

        applyMult(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, UUID_SEA_LEGS,
            "pp_sea_legs", getSkillLevel(player, "sea_legs") * 0.03,
            EntityAttributeModifier.Operation.MULTIPLY_BASE);

        applyMult(player, EntityAttributes.GENERIC_MAX_HEALTH, UUID_IRON_SKIN,
            "pp_iron_skin", getSkillLevel(player, "iron_skin") * 2.0,
            EntityAttributeModifier.Operation.ADDITION);

        applyMult(player, EntityAttributes.GENERIC_ARMOR_TOUGHNESS, UUID_SEA_TOUGHNESS,
            "pp_sea_toughness", getSkillLevel(player, "sea_toughness") * 0.5,
            EntityAttributeModifier.Operation.ADDITION);
    }

    private static void applyMult(ServerPlayerEntity player,
            net.minecraft.entity.attribute.EntityAttribute attr,
            UUID id, String name, double value,
            EntityAttributeModifier.Operation op) {
        EntityAttributeInstance inst = player.getAttributeInstance(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        if (value > 0) inst.addTemporaryModifier(new EntityAttributeModifier(id, name, value, op));
    }

    public static void removeAllModifiers(ServerPlayerEntity player) {
        removeModifier(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, UUID_BLADE_MASTERY);
        removeModifier(player, EntityAttributes.GENERIC_ATTACK_SPEED,  UUID_SWIFT_STRIKES);
        removeModifier(player, EntityAttributes.GENERIC_MOVEMENT_SPEED, UUID_SEA_LEGS);
        removeModifier(player, EntityAttributes.GENERIC_MAX_HEALTH,     UUID_IRON_SKIN);
        removeModifier(player, EntityAttributes.GENERIC_ARMOR_TOUGHNESS, UUID_SEA_TOUGHNESS);
    }

    private static void removeModifier(ServerPlayerEntity player,
            net.minecraft.entity.attribute.EntityAttribute attr, UUID id) {
        EntityAttributeInstance inst = player.getAttributeInstance(attr);
        if (inst != null) inst.removeModifier(id);
    }

    // ── Per-tick effects ──────────────────────────────────────────────────────

    public static void tickPlayer(ServerPlayerEntity player) {
        PlayerProgressionComponent comp = (PlayerProgressionComponent) player;

        if (comp.pp_getSecondWindCooldown() > 0)
            comp.pp_setSecondWindCooldown(comp.pp_getSecondWindCooldown() - 1);
        if (comp.pp_getDavysLuckCooldown() > 0)
            comp.pp_setDavysLuckCooldown(comp.pp_getDavysLuckCooldown() - 1);

        int secondWindLvl = getSkillLevel(player, "second_wind");
        if (secondWindLvl > 0 && comp.pp_getSecondWindCooldown() == 0) {
            float maxHp = player.getMaxHealth();
            float curHp = player.getHealth();
            if (curHp > 0 && curHp <= maxHp * 0.25f) {
                player.heal(4.0f);
                int cooldownSecs = 120 - secondWindLvl * 20;
                comp.pp_setSecondWindCooldown(cooldownSecs * 20);
                player.sendMessage(Text.literal("§a+ Second Wind!"), true);
            }
        }
    }

    // ── Network sync ──────────────────────────────────────────────────────────

    public static void syncToClient(ServerPlayerEntity player) {
        PlayerProgressionComponent comp = (PlayerProgressionComponent) player;
        var buf = PacketByteBufs.create();
        buf.writeInt(comp.pp_getPirateLevel());
        buf.writeInt(comp.pp_getPirateXp());
        buf.writeInt(comp.pp_getSkillPoints());
        int count = PirateLevelingSystem.ALL_SKILLS.size();
        for (int i = 0; i < count; i++) buf.writeInt(comp.pp_getSkillLevel(i));
        ServerPlayNetworking.send(player, ModNetworking.S2C_LEVEL_SYNC, buf);
    }

    public static void sendOpenSkillScreen(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, ModNetworking.S2C_OPEN_SKILL_SCREEN,
            PacketByteBufs.empty());
    }
}
