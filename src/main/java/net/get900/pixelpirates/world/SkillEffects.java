package net.get900.pixelpirates.world;

import net.get900.pixelpirates.entity.mob.ModBoss;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * WHAT EVERY SKILL DOES (2026-10-01; definitions in PirateLevelingSystem). One place per hook:
 *  - {@link #modifyDamage}: from mixin/BossArmorDamageMixin (LivingEntity#modifyAppliedDamage RETURN, after armor):
 *    outgoing bonuses of the attacking player (Executioner, Berserker, Heavy Shot, Demolitions, Crippling Shot) and
 *    incoming reductions of a player (Salt Skin, Wave Dancer, a boss's parried hit).
 *  - {@link #allowDamage}: ServerLivingEntityEvents.ALLOW_DAMAGE - Parry, Powder Soul, Davy's Luck, Unsinkable.
 *  - {@link #tick}: Berserker speed, Sea Wolf, Second Wind (PirateLevelManager.tickPlayer).
 *  - the numbers used at the call sites: cannons, guns, ship thrust, prices, bounties, loot, XP.
 * Chance skills never fully cancel a BOSS's blow - parry halves it - so no skill trivialises a boss fight.
 */
public final class SkillEffects {
    private SkillEffects() {}

    public static int lvl(PlayerEntity p, String key) { return p == null ? 0 : PirateLevelManager.getSkillLevel(p, key); }

    public static PlayerEntity playerOf(Entity e) { return e instanceof PlayerEntity p ? p : null; }

    // ------------------------------------------------------------------ numbers for the call sites
    /** Quick Hands: cannon charge / gun reload time multiplier. */
    public static double reloadMult(PlayerEntity p) { return 1 - 0.08 * lvl(p, "quick_hands"); }
    /** Heavy Shot: ship hull damage multiplier for a cannonball fired by `p`. */
    public static double hullMult(PlayerEntity p) { return 1 + 0.10 * lvl(p, "heavy_shot"); }
    /** Dead Eye: gun spread multiplier. */
    public static float spreadMult(PlayerEntity p) { return (float) (1 - 0.15 * lvl(p, "dead_eye")); }
    /** Wind Reader: thrust multiplier of the ship `p` steers. */
    public static double thrustMult(PlayerEntity p) { return 1 + 0.06 * lvl(p, "wind_reader"); }
    /** Deep Diver: chance factor a shark still picks `p` as prey (1 = always). */
    public static float sharkInterest(PlayerEntity p) { return 1 - 0.10f * lvl(p, "deep_diver"); }
    /** Haggler: a price after the discount (never below 1 if it was above 0). */
    public static int haggle(PlayerEntity p, int price) {
        if (price <= 0) return price;
        return Math.max(1, (int) Math.round(price * (1 - 0.04 * lvl(p, "haggler"))));
    }
    /** Fence: coins for goods sold to the port. */
    public static int fence(PlayerEntity p, int coins) { return (int) Math.round(coins * (1 + 0.05 * lvl(p, "fence"))); }
    /** Bounty Hunter: bounty payout. */
    public static int bounty(PlayerEntity p, int coins) { return (int) Math.round(coins * (1 + 0.10 * lvl(p, "bounty_hunter"))); }
    /** Treasure Hunter / Plunderer: does this opening of a loot container roll its table twice? */
    public static boolean doubleLoot(PlayerEntity p, boolean bossHoard) {
        if (bossHoard && lvl(p, "plunderer") > 0) return true;
        return p != null && p.getRandom().nextFloat() < 0.05f * lvl(p, "treasure_hunter");
    }
    /** Explorer: discovery XP multiplier. */
    public static double explorerMult(PlayerEntity p) { return 1 + 0.06 * lvl(p, "explorer"); }

    // ------------------------------------------------------------------ damage (after armor)
    public static float modifyDamage(LivingEntity target, DamageSource source, float amount) {
        if (amount <= 0) return amount;
        // ---- outgoing: the attacker is a player
        if (source.getAttacker() instanceof PlayerEntity p && p != target) {
            Entity direct = source.getSource();
            boolean melee = direct == p;
            boolean shot = direct instanceof net.get900.pixelpirates.entity.custom.CannonBallEntity
                    || direct instanceof net.get900.pixelpirates.homestead.gun.MusketBallEntity;
            boolean blast = source.isIn(DamageTypeTags.IS_EXPLOSION);
            float mult = 1;
            int ex = lvl(p, "executioner");
            if (ex > 0 && target.getHealth() < target.getMaxHealth() * 0.5f) mult += 0.06f * ex;
            if (melee && lvl(p, "berserker") > 0 && p.getHealth() < p.getMaxHealth() * 0.4f) mult += 0.25f;
            if (shot) mult += 0.06f * lvl(p, "heavy_shot");
            if (blast) mult += 0.10f * lvl(p, "demolitions");
            boolean boss = target instanceof ModBoss;
            boolean monster = !boss && isMonster(target);
            if (monster) mult += 0.05f * lvl(p, "beast_slayer");
            if (boss) mult += 0.04f * lvl(p, "boss_hunter");
            int tr = lvl(p, "tracker");
            if (tr > 0 && (monster || boss)) target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, tr * 40, 0, false, false), p);
            int cs = lvl(p, "chain_shot");
            if ((shot || (blast && direct instanceof net.get900.pixelpirates.entity.custom.CannonBallEntity)) && cs > 0
                    && p.getRandom().nextFloat() < 0.06f * cs)
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1), p);
            amount *= mult;
        }
        // ---- incoming: the target is a player
        if (target instanceof PlayerEntity p) {
            int salt = lvl(p, "salt_skin");
            if (salt > 0 && (source.isIn(DamageTypeTags.IS_FIRE) || p.hasStatusEffect(StatusEffects.POISON) && "magic".equals(source.getName())
                    || "wither".equals(source.getName()))) amount *= 1 - 0.06f * salt;
            int wave = lvl(p, "wave_dancer");
            if (wave > 0 && source.isIn(DamageTypeTags.IS_FALL)) amount *= 1 - 0.12f * wave;
            if (PARRIED_HALF.remove(p.getUuid()) != null) amount *= 0.5f;
            if (source.getAttacker() instanceof LivingEntity att && !(att instanceof ModBoss) && isMonster(att))
                amount *= 1 - 0.05f * lvl(p, "monster_lore");
        }
        return amount;
    }

    /** A "monster" for the hunter skills: any hostile creature that isn't a player (bosses are counted apart). */
    public static boolean isMonster(Entity e) {
        return e instanceof net.minecraft.entity.mob.Monster || e instanceof net.get900.pixelpirates.entity.mob.ModMob
                || e instanceof net.get900.pixelpirates.entity.custom.SharkEntity
                || e instanceof net.minecraft.entity.mob.MobEntity m && m.getTarget() instanceof PlayerEntity;
    }

    /** Monster Hunter on a kill: Trophy Hunter (roll the loot again) and Apex Predator (a buff). */
    public static void onKill(ServerPlayerEntity p, LivingEntity killed) {
        net.get900.pixelpirates.item.forged.SoulreaverItem.onKill(p, killed);
        if (killed instanceof ModBoss || !isMonster(killed)) return;
        if (lvl(p, "apex_predator") > 0) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 160, 0));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 160, 0));
        }
        int th = lvl(p, "trophy_hunter");
        if (th > 0 && p.getRandom().nextFloat() < 0.06f * th && killed.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
            var table = sw.getServer().getLootManager().getLootTable(killed.getLootTable());
            var params = new net.minecraft.loot.context.LootContextParameterSet.Builder(sw)
                    .add(net.minecraft.loot.context.LootContextParameters.THIS_ENTITY, killed)
                    .add(net.minecraft.loot.context.LootContextParameters.ORIGIN, killed.getPos())
                    .add(net.minecraft.loot.context.LootContextParameters.DAMAGE_SOURCE, sw.getDamageSources().playerAttack(p))
                    .add(net.minecraft.loot.context.LootContextParameters.KILLER_ENTITY, p)
                    .add(net.minecraft.loot.context.LootContextParameters.LAST_DAMAGE_PLAYER, p)
                    .luck(p.getLuck())
                    .build(net.minecraft.loot.context.LootContextTypes.ENTITY);
            table.generateLoot(params, killed::dropStack);
            p.sendMessage(Text.literal("Trophy Hunter: a second trophy!").formatted(Formatting.LIGHT_PURPLE), true);
        }
    }

    /** A boss's parried blow is let through (allowDamage) but halved in modifyDamage. */
    private static final Map<UUID, Boolean> PARRIED_HALF = new HashMap<>();
    private static final Map<UUID, Long> UNSINKABLE = new HashMap<>();

    // ------------------------------------------------------------------ allow / cancel damage
    public static boolean allowDamage(ServerPlayerEntity player, DamageSource source, float amount) {
        // Powder Soul: your own explosions never hurt you
        if (source.isIn(DamageTypeTags.IS_EXPLOSION) && source.getAttacker() == player && lvl(player, "powder_soul") > 0) return false;

        // Parry: melee blows only; a boss's is halved instead of stopped
        boolean melee = source.getAttacker() instanceof LivingEntity && source.getSource() == source.getAttacker()
                && !source.isIn(DamageTypeTags.IS_PROJECTILE) && !source.isIn(DamageTypeTags.IS_EXPLOSION);
        // the Pearlguard Rapier's En Garde: a blow in the window is turned aside (a boss's halved)
        if (melee && net.get900.pixelpirates.item.forged.PearlguardRapierItem.parry(player)) {
            net.get900.pixelpirates.item.forged.PearlguardRapierItem.riposte(player, (LivingEntity) source.getAttacker());
            if (source.getAttacker() instanceof ModBoss) PARRIED_HALF.put(player.getUuid(), true);
            else { player.sendMessage(Text.literal("Parried!").formatted(Formatting.GREEN), true); return false; }
        }
        int parry = lvl(player, "parry");
        if (parry > 0 && melee && player.getRandom().nextFloat() < parry * 0.06f) {
            if (source.getAttacker() instanceof ModBoss) {
                PARRIED_HALF.put(player.getUuid(), true);
                player.sendMessage(Text.literal("Parried - half damage!").formatted(Formatting.GREEN), true);
            } else {
                player.sendMessage(Text.literal("Parried!").formatted(Formatting.GREEN), true);
                return false;
            }
        }

        boolean fatal = player.getHealth() + player.getAbsorptionAmount() <= amount;
        if (!fatal) return true;

        // Unsinkable (capstone): once every 5 minutes
        long now = player.getServer().getTicks();
        if (lvl(player, "unsinkable") > 0 && now >= UNSINKABLE.getOrDefault(player.getUuid(), 0L)) {
            UNSINKABLE.put(player.getUuid(), now + 5 * 60 * 20);
            player.setHealth(8f);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 80, 1));
            player.sendMessage(Text.literal("** Unsinkable! **").formatted(Formatting.GOLD, Formatting.BOLD), true);
            return false;
        }
        // Davy's Luck
        var comp = (net.get900.pixelpirates.util.PlayerProgressionComponent) player;
        int davy = lvl(player, "davys_luck");
        if (davy > 0 && comp.pp_getDavysLuckCooldown() == 0 && player.getRandom().nextFloat() < davy * 0.04f) {
            comp.pp_setDavysLuckCooldown(60 * 20);
            player.setHealth(1f);
            player.sendMessage(Text.literal("** Davy's Luck saved you!").formatted(Formatting.GOLD), true);
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ per tick (every tick, cheap checks)
    public static void tick(ServerPlayerEntity p) {
        if (p.age % 10 != 0) return;
        if (lvl(p, "berserker") > 0 && p.getHealth() < p.getMaxHealth() * 0.4f)
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 30, 0, true, false, true));
        if (p.age % 40 == 0 && lvl(p, "sea_wolf") > 0 && onShip(p)) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 60, 0, true, false, true));
            p.getHungerManager().setExhaustion(0);
        }
    }

    /** Standing on (or in) a Valkyrien Skies ship. */
    public static boolean onShip(PlayerEntity p) {
        try {
            var box = p.getBoundingBox().expand(0.5, 1.0, 0.5);
            var ships = org.valkyrienskies.mod.common.VSGameUtilsKt.getShipsIntersecting(p.getWorld(),
                    new org.joml.primitives.AABBd(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
            return ships.iterator().hasNext();
        } catch (Exception e) {
            return false;
        }
    }
}
