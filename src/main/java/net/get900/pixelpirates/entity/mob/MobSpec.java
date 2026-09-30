package net.get900.pixelpirates.entity.mob;

import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything that makes one data-driven mob: movement kind, temperament, stats, hitbox/render
 * size, traits, abilities, drops and natural spawning. One {@link ModMob} (or {@link ModBoss})
 * class serves every spec; see {@link MobSpecs} for the catalogue.
 *
 * Model/texture/animations come from tools/gen_mob_assets.py under the same id, and must provide
 * the animations "idle", "move", "attack" plus every ability's anim ("special", "special2").
 */
public class MobSpec {
    public enum Kind { GROUND, SWIM, FLY, STATIONARY }
    public enum Temper { HOSTILE, NEUTRAL, PASSIVE }

    public record Drop(Item item, int min, int max, float chance) {}

    public final String id;
    public final int phase;
    Kind kind = Kind.GROUND;
    Temper temper = Temper.HOSTILE;
    double health = 20, damage = 4, speed = 0.25, armor = 0, knockbackRes = 0, follow = 24, chaseSpeed = 1.1;
    float width = 0.6f, height = 1.9f, renderScale = 1f, shadow = 0.5f;
    int xp = 5;
    boolean melee = true, fireImmune, hurtByWater, boss, floats, seabed;
    double reach = 8;
    String awayFrom; int awayRadius;              // SWIM: also hunts players out of the water within this many blocks
    float damageTaken = 1f, lifesteal;
    StatusEffect onHitEffect; int onHitTicks, onHitAmp, onHitFire;
    ParticleEffect trail; int trailChance = 6;
    final List<Abilities.Ability> abilities = new ArrayList<>();
    final List<Abilities.Ability> phase2 = new ArrayList<>();
    final List<Drop> drops = new ArrayList<>();
    // natural spawning
    SpawnGroup group = SpawnGroup.MONSTER;
    final List<RegistryKey<Biome>> biomes = new ArrayList<>();
    int weight, minGroup = 1, maxGroup = 1;
    // bosses
    BossBar.Color barColor = BossBar.Color.RED;
    int unlocksZone;               // killing this boss unlocks the given zone for nearby players (0 = none)
    String enrageAnim = "special";  // clip played when a boss enrages
    String enrageTitle = "Enraged"; // boss bar suffix while enraged
    /** Entity class override for bosses with bespoke behaviour (default ModBoss / ModMob). */
    net.minecraft.entity.EntityType.EntityFactory<? extends ModMob> factory;
    int eggPrimary = 0x333333, eggSecondary = 0xAAAAAA;

    private MobSpec(String id, int phase) { this.id = id; this.phase = phase; }

    public static MobSpec of(String id, int phase) { return new MobSpec(id, phase); }

    public MobSpec kind(Kind k) { kind = k; if (k == Kind.SWIM && group == SpawnGroup.MONSTER) group = SpawnGroup.MONSTER; return this; }
    public MobSpec temper(Temper t) { temper = t; return this; }
    public MobSpec stats(double health, double damage, double speed, double armor) {
        this.health = health; this.damage = damage; this.speed = speed; this.armor = armor; return this;
    }
    public MobSpec knockbackRes(double k) { knockbackRes = k; return this; }
    public MobSpec follow(double f) { follow = f; return this; }
    public MobSpec chase(double c) { chaseSpeed = c; return this; }
    /** Hitbox and renderer scale - the two must describe the same size (model px * scale / 16 ~ hitbox). */
    public MobSpec size(float w, float h, float renderScale, float shadow) { width = w; height = h; this.renderScale = renderScale; this.shadow = shadow; return this; }
    public MobSpec xp(int x) { xp = x; return this; }
    public MobSpec noMelee() { melee = false; return this; }
    public MobSpec fireImmune() { fireImmune = true; return this; }
    /** SWIM mob that rides the surface (feet at the waterline) instead of diving - raft pirates. */
    public MobSpec floats() { floats = true; return this; }
    /** GROUND mob that walks the sea floor like a diver in lead boots: never swims up, breathes water, keeps its pace. */
    public MobSpec seabed() { seabed = true; return this; }
    public MobSpec reach(double r) { reach = r; return this; }
    /** Never spawn naturally within `radius` blocks of a site of this dungeon type (DungeonPlacement.nearSite). */
    public MobSpec awayFrom(String dungeonId, int radius) { awayFrom = dungeonId; awayRadius = radius; return this; }
    public MobSpec hurtByWater() { hurtByWater = true; return this; }
    public MobSpec tough(float damageTakenMultiplier) { damageTaken = damageTakenMultiplier; return this; }
    public MobSpec lifesteal(float f) { lifesteal = f; return this; }
    public MobSpec onHit(StatusEffect e, int ticks, int amp) { onHitEffect = e; onHitTicks = ticks; onHitAmp = amp; return this; }
    public MobSpec onHitFire(int secs) { onHitFire = secs; return this; }
    public MobSpec trail(ParticleEffect p, int oneIn) { trail = p; trailChance = oneIn; return this; }
    public MobSpec ability(Abilities.Ability a) { abilities.add(a); return this; }
    public MobSpec phase2(Abilities.Ability a) { phase2.add(a); return this; }
    public MobSpec drop(Item item, int min, int max, float chance) { drops.add(new Drop(item, min, max, chance)); return this; }
    @SafeVarargs
    public final MobSpec spawns(SpawnGroup g, int weight, int min, int max, RegistryKey<Biome>... in) {
        group = g; this.weight = weight; minGroup = min; maxGroup = max; biomes.addAll(List.of(in)); return this;
    }
    public MobSpec group(SpawnGroup g) { group = g; return this; }
    public MobSpec boss(BossBar.Color color, int unlocksZone) { boss = true; barColor = color; this.unlocksZone = unlocksZone; return this; }
    public MobSpec enrage(String anim, String title) { enrageAnim = anim; enrageTitle = title; return this; }
    public MobSpec entity(net.minecraft.entity.EntityType.EntityFactory<? extends ModMob> f) { factory = f; return this; }
    public MobSpec egg(int primary, int secondary) { eggPrimary = primary; eggSecondary = secondary; return this; }

    public Kind kind() { return kind; }
    public String enrageAnim() { return enrageAnim; }
    public int unlocksZone() { return unlocksZone; }
    public int eggPrimary() { return eggPrimary; }
    public int eggSecondary() { return eggSecondary; }
    public float renderScale() { return renderScale; }
    public float shadow() { return shadow; }
    public boolean hostile() { return temper == Temper.HOSTILE; }
}
