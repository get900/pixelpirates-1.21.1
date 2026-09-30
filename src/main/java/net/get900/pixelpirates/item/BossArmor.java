package net.get900.pixelpirates.item;

import net.get900.pixelpirates.entity.mob.ModBoss;
import net.get900.pixelpirates.item.custom.BossArmorItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * THE BOSS SETS (design: D:\Minecraft Modding\Boss Armor - Design.txt). Five sets, one per pair of chain bosses; pieces
 * come from those bosses' hoard chests and one missing piece per credited player per kill ({@link #onBossKilled}).
 * <ul>
 *   <li>BOSS WARD: every piece cuts damage from BOSSES (a ModBoss, anything of ours fighting near one, their projectiles,
 *       and owner-less damage - magic, explosions, burning - taken near one) by its tier's %, ALL damage types.</li>
 *   <li>SET BONUSES (all four pieces of one tier) - each counters the next pair of bosses:
 *     I Blast-Stitched (boss explosions -40%, boss fire burns half as long), II Quenched &amp; Warded (no burning near
 *     bosses, boss magic -30%, dolphin's grace), III Slippery as the Tide (grabs let go after 1.4 s, pulls -40%, no boss
 *     slowness), IV Unshackled (no wither/darkness/blindness near bosses, nausea 1 s max, boss-launch falls -50%),
 *     V Heart of the Tide Father (LAST STAND, water perks, boss magic -30%).</li>
 * </ul>
 * Hooks: {@code mixin/BossArmorDamageMixin} (damage), {@link #tick} (server tick), {@link #pullScale},
 * {@link #slipped} (boss re-grab guards), {@link #onBossKilled} (BossProgression).
 */
public final class BossArmor {
    private BossArmor() {}

    public static final String[] SET_NAMES = {"", "Powder-Monkey's Brigandine", "Forgeguard Plate", "Tidecourt Regalia",
            "Gallowbreaker Harness", "Mantle of Thalassar"};
    public static final float[] WARD = {0, 0.03f, 0.04f, 0.04f, 0.05f, 0.06f};       // per piece
    static final double BOSS_RANGE = 64;
    static final int SLIP_TICKS = 28, LAST_STAND_COOLDOWN = 1800;

    private static final Map<UUID, Integer> ridingBoss = new HashMap<>();
    private static final Map<UUID, Long> slippedAt = new HashMap<>(), lastStandAt = new HashMap<>();

    // ------------------------------------------------------------------ what the player wears
    public static int tierOf(ItemStack s) { return s.getItem() instanceof BossArmorItem b ? b.tier() : 0; }

    /** Boss Ward fraction from every boss piece worn. */
    public static float ward(LivingEntity e) {
        float w = 0;
        for (ItemStack s : e.getArmorItems()) w += WARD[tierOf(s)];
        return w;
    }

    /** The tier of a complete set (all four pieces the same tier), else 0. */
    public static int fullSet(LivingEntity e) {
        int t = -1;
        for (ItemStack s : e.getArmorItems()) {
            int k = tierOf(s);
            if (k == 0 || t >= 0 && k != t) return 0;
            t = k;
        }
        return Math.max(t, 0);
    }

    // ------------------------------------------------------------------ is this a boss's doing?
    public static boolean nearBoss(Entity e, double r) {
        return !e.getWorld().getEntitiesByClass(ModBoss.class, new Box(e.getBlockPos()).expand(r), Entity::isAlive).isEmpty();
    }

    static boolean bossy(Entity e) {
        if (e instanceof ProjectileEntity p && p.getOwner() != null) e = p.getOwner();
        if (e instanceof ModBoss) return true;
        // anything of ours fighting beside a boss: arms, limbs, segments, summons, clots, wardens, debris...
        return e.getClass().getName().startsWith("net.get900.pixelpirates") && !(e instanceof PlayerEntity) && nearBoss(e, 48);
    }

    public static boolean fromBoss(PlayerEntity p, DamageSource src) {
        Entity e = src.getAttacker() != null ? src.getAttacker() : src.getSource();
        if (e != null) return e != p && bossy(e);
        return nearBoss(p, BOSS_RANGE);                                  // magic / explosions / burning with no owner
    }

    // ------------------------------------------------------------------ DAMAGE (mixin, after armor and enchantments)
    public static float modify(PlayerEntity p, DamageSource src, float amount) {
        if (amount <= 0 || src.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return amount;
        int set = fullSet(p);
        boolean fall = src.isIn(DamageTypeTags.IS_FALL);
        if (fall) {                                                      // IV: launched by a boss -> softer landing
            return set == 4 && nearBoss(p, BOSS_RANGE) ? amount * 0.5f : amount;
        }
        if (!fromBoss(p, src)) return amount;
        amount *= Math.max(0, 1 - ward(p));
        boolean magic = src.isIn(DamageTypeTags.WITCH_RESISTANT_TO) || src.isOf(DamageTypes.MAGIC) || src.isOf(DamageTypes.INDIRECT_MAGIC);
        if (set == 1 && src.isIn(DamageTypeTags.IS_EXPLOSION)) amount *= 0.6f;
        if (set == 2 && src.isIn(DamageTypeTags.IS_FIRE)) return 0;
        if ((set == 2 || set == 5) && magic) amount *= 0.7f;
        if (set == 4 && src.isOf(DamageTypes.WITHER)) return 0;
        if (set == 5) amount = lastStand(p, amount);
        return amount;
    }

    /** V: a boss hit that would take you under 25% becomes Absorption III + Resistance I instead (90 s cooldown). */
    static float lastStand(PlayerEntity p, float amount) {
        float floor = p.getMaxHealth() * 0.25f;
        if (p.getHealth() - amount >= floor || p.getHealth() <= floor) return amount;
        long now = p.getWorld().getTime();
        Long last = lastStandAt.get(p.getUuid());
        if (last != null && now - last < LAST_STAND_COOLDOWN) return amount;
        lastStandAt.put(p.getUuid(), now);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 120, 2));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 120, 0));
        p.getWorld().playSound(null, p.getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 2.0f, 0.7f);
        p.sendMessage(Text.literal("LAST STAND - the Tide Father's heart beats in your chest!").formatted(Formatting.GOLD, Formatting.BOLD), true);
        return Math.max(0, p.getHealth() - floor);
    }

    // ------------------------------------------------------------------ pulls + grabs (III)
    /** Multiply a boss's pull on this player by this (Kraken inhale, Leviathan lure/gulp, Heart systole...). */
    public static double pullScale(PlayerEntity p) { return fullSet(p) == 3 ? 0.6 : 1.0; }

    /** A boss's re-grab guard: true while this player has just slipped its grip. */
    public static boolean slipped(Entity e) {
        Long t = slippedAt.get(e.getUuid());
        return t != null && e.getWorld().getTime() - t < 60;
    }

    // ------------------------------------------------------------------ the server tick (every 5 ticks)
    public static void tick(MinecraftServer server) {
        if (server.getTicks() % 5 != 0) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            int set = fullSet(p);
            if (set == 0) { ridingBoss.remove(p.getUuid()); continue; }
            boolean near = nearBoss(p, BOSS_RANGE);
            // III: slip out of anything of a boss's that holds you
            if (set == 3 && p.hasVehicle() && bossy(p.getVehicle())) {
                int held = ridingBoss.merge(p.getUuid(), 5, Integer::sum);
                if (held >= SLIP_TICKS) {
                    p.stopRiding();
                    slippedAt.put(p.getUuid(), p.getWorld().getTime());
                    ridingBoss.remove(p.getUuid());
                    p.sendMessage(Text.literal("You twist free of its grip!").formatted(Formatting.AQUA), true);
                }
            } else ridingBoss.remove(p.getUuid());
            if (near) {
                if (set == 1 && p.isOnFire()) p.setFireTicks(Math.max(0, p.getFireTicks() - 5));         // burns half as long
                if (set == 2 && p.isOnFire()) p.extinguish();
                if (set == 3) clear(p, StatusEffects.SLOWNESS);
                if (set == 4) {
                    clear(p, StatusEffects.WITHER); clear(p, StatusEffects.DARKNESS); clear(p, StatusEffects.BLINDNESS);
                    StatusEffectInstance n = p.getStatusEffect(StatusEffects.NAUSEA);
                    if (n != null && n.getDuration() > 20) { p.removeStatusEffect(StatusEffects.NAUSEA); p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 20, 0)); }
                }
            }
            if (p.isTouchingWater()) {
                if (set == 2 || set == 5) give(p, StatusEffects.DOLPHINS_GRACE);
                if (set == 5) { give(p, StatusEffects.WATER_BREATHING); give(p, StatusEffects.NIGHT_VISION); }
            }
        }
    }

    private static void clear(PlayerEntity p, StatusEffect e) { if (p.hasStatusEffect(e)) p.removeStatusEffect(e); }

    private static void give(PlayerEntity p, StatusEffect e) {
        StatusEffectInstance cur = p.getStatusEffect(e);
        if (cur == null || cur.getDuration() < 220) p.addStatusEffect(new StatusEffectInstance(e, 300, 0, true, false, true));
    }

    // ------------------------------------------------------------------ THE KILL: one missing piece per credited player
    /** Chain boss `index` (0..9) died and credited this player: tier = the pair it belongs to. */
    public static void onBossKilled(ServerPlayerEntity p, int index) {
        if (index < 0 || index > 9) return;
        int tier = index / 2 + 1;
        List<Item> missing = new ArrayList<>();
        for (Item piece : ModItems.bossSet(tier)) if (!owns(p, piece)) missing.add(piece);
        if (missing.isEmpty()) return;
        Item give = missing.get(p.getRandom().nextInt(missing.size()));
        p.getInventory().offerOrDrop(new ItemStack(give));
        p.sendMessage(Text.literal("[*] From the wreck of the fight you claim ").formatted(Formatting.GOLD)
                .append(Text.translatable(give.getTranslationKey()).formatted(Formatting.AQUA))
                .append(Text.literal(" (" + SET_NAMES[tier] + ")").formatted(Formatting.GRAY)), false);
    }

    // ------------------------------------------------------------------ /pparmortest: measured damage per loadout
    /** Hits a fake player beside the nearest boss (spawns a Rackham if none) with typical boss attacks, per loadout. */
    public static List<String> test(net.minecraft.server.world.ServerWorld w, net.minecraft.util.math.Vec3d at) {
        List<String> out = new ArrayList<>();
        var bosses = w.getEntitiesByClass(ModBoss.class, new Box(net.minecraft.util.math.BlockPos.ofFloored(at)).expand(64), Entity::isAlive);
        ModBoss boss;
        boolean spawned = false;
        if (bosses.isEmpty()) {
            var type = net.get900.pixelpirates.entity.mob.ModMobs.TYPES.get("captain_rackham");
            if (type == null || !(type.create(w) instanceof ModBoss b)) { out.add("no boss to test against"); return out; }
            b.refreshPositionAndAngles(at.x + 4, at.y, at.z, 0, 0);
            b.setAiDisabled(true);
            w.spawnEntity(b);
            boss = b; spawned = true;
        } else boss = bosses.get(0);
        var fp = net.fabricmc.fabric.api.entity.FakePlayer.get(w);
        fp.refreshPositionAndAngles(boss.getX() + 2, boss.getY(), boss.getZ(), 0, 0);
        net.minecraft.item.Item[][] loadouts = {
                {null, null, null, null},
                {net.minecraft.item.Items.DIAMOND_HELMET, net.minecraft.item.Items.DIAMOND_CHESTPLATE, net.minecraft.item.Items.DIAMOND_LEGGINGS, net.minecraft.item.Items.DIAMOND_BOOTS},
                {net.minecraft.item.Items.NETHERITE_HELMET, net.minecraft.item.Items.NETHERITE_CHESTPLATE, net.minecraft.item.Items.NETHERITE_LEGGINGS, net.minecraft.item.Items.NETHERITE_BOOTS},
                ModItems.bossSet(1), ModItems.bossSet(2), ModItems.bossSet(3), ModItems.bossSet(4), ModItems.bossSet(5)};
        String[] names = {"no armor", "diamond", "netherite", "I brigandine", "II forgeguard", "III tidecourt", "IV gallowbreaker", "V thalassar"};
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        out.add(String.format("vs %s - damage taken from: melee 16 | magic 8 | explosion 12 | fire 4 | fall 10", boss.getName().getString()));
        for (int k = 0; k < loadouts.length; k++) {
            for (int s = 0; s < 4; s++) fp.equipStack(slots[s], loadouts[k][s] == null ? ItemStack.EMPTY : new ItemStack(loadouts[k][s]));
            float[] taken = {
                    hit(fp, w.getDamageSources().mobAttack(boss), 16),
                    hit(fp, w.getDamageSources().magic(), 8),
                    hit(fp, w.getDamageSources().explosion(boss, boss), 12),
                    hit(fp, w.getDamageSources().onFire(), 4),
                    hit(fp, w.getDamageSources().fall(), 10)};
            out.add(String.format("%-17s %5.1f | %4.1f | %4.1f | %3.1f | %4.1f", names[k], taken[0], taken[1], taken[2], taken[3], taken[4]));
        }
        for (EquipmentSlot s : slots) fp.equipStack(s, ItemStack.EMPTY);
        if (spawned) boss.discard();
        return out;
    }

    /** The same pipeline as LivingEntity#applyDamage: armor (vanilla formula, from the worn items) then Boss Ward & bonuses.
     *  (A FakePlayer never ticks, so it keeps its join invulnerability and never gets its armor attributes.) */
    private static float hit(PlayerEntity p, DamageSource src, float amount) {
        lastStandAt.remove(p.getUuid());
        float armor = 0, tough = 0;
        for (ItemStack s : p.getArmorItems())
            if (s.getItem() instanceof net.minecraft.item.ArmorItem a) { armor += a.getProtection(); tough += a.getMaterial().getToughness(); }
        float after = src.isIn(DamageTypeTags.BYPASSES_ARMOR) ? amount : net.minecraft.entity.DamageUtil.getDamageLeft(amount, armor, tough);
        return modify(p, src, after);
    }

    static boolean owns(PlayerEntity p, Item item) {
        for (EquipmentSlot s : EquipmentSlot.values()) if (p.getEquippedStack(s).isOf(item)) return true;
        return p.getInventory().contains(new ItemStack(item)) || p.getEnderChestInventory().containsAny(st -> st.isOf(item));
    }
}
