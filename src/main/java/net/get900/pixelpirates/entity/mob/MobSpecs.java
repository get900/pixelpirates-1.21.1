package net.get900.pixelpirates.entity.mob;

import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.world.biome.ModBiomeKeys;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static net.get900.pixelpirates.entity.mob.Abilities.*;
import static net.get900.pixelpirates.entity.mob.MobSpec.Kind.*;
import static net.get900.pixelpirates.entity.mob.MobSpec.Temper.*;

/**
 * THE MOB CATALOGUE - every data-driven mob, per phase, from the concept renders in
 * D:\Minecraft Modding\mobrenders. Model/texture/animations: tools/gen_mob_roster.py (same id).
 * Sounds: tools/gen_mob_sounds.py ROSTER (same id). Hitbox = model size x render scale.
 *
 * Gameplay loop: regular mobs spawn in their phase's biomes and drop that phase's materials;
 * bosses live only in dungeons and are fought in the fixed order of BossProgression.CHAIN
 * (stats scale with chain position, not phase); each awards a relic for the next fight.
 */
public final class MobSpecs {
    private MobSpecs() {}

    private static final Map<String, MobSpec> SPECS = new LinkedHashMap<>();

    public static MobSpec get(String id) {
        MobSpec s = SPECS.get(id);
        if (s == null) throw new IllegalStateException("No MobSpec for " + id);
        return s;
    }

    public static Collection<MobSpec> all() { return SPECS.values(); }

    private static MobSpec def(MobSpec s) { SPECS.put(s.id, s); return s; }

    // ---- shared shots
    private static final Shot FIREBALL = Shot.of(Items.FIRE_CHARGE, 5f, ParticleTypes.FLAME).withFire(4);
    private static final Shot KNIFE = Shot.of(ModItems.THROWING_KNIFE, 4f, ParticleTypes.CRIT).withGravity(0.03f);
    private static final Shot DYNAMITE = Shot.of(ModItems.DYNAMITE, 7f, ParticleTypes.SMOKE).withGravity(0.05f).withFire(2);
    private static final Shot PISTOL = Shot.of(Items.IRON_NUGGET, 6f, ParticleTypes.SMOKE).withGravity(0f);
    private static final Shot TRIDENT = Shot.of(Items.TRIDENT, 6f, ParticleTypes.BUBBLE).withGravity(0.02f);
    private static final Shot INK = Shot.of(Items.INK_SAC, 3f, ParticleTypes.SQUID_INK).withEffect(StatusEffects.BLINDNESS, 60, 0);
    private static final Shot HEAVY_INK = Shot.of(Items.INK_SAC, 6f, ParticleTypes.SQUID_INK).withEffect(StatusEffects.BLINDNESS, 80, 0);
    private static final Shot SHARD = Shot.of(Items.AMETHYST_SHARD, 5f, ParticleTypes.END_ROD).withEffect(StatusEffects.SLOWNESS, 60, 1).withGravity(0f);
    private static final Shot BONE = Shot.of(Items.BONE, 4f, ParticleTypes.ASH).withEffect(StatusEffects.WITHER, 60, 0);
    private static final Shot HARPOON = Shot.of(ModItems.ABYSSAL_HARPOON, 6f, ParticleTypes.BUBBLE).withGravity(0.02f).withEffect(StatusEffects.SLOWNESS, 40, 2);
    private static final Shot VENOM = Shot.of(Items.SLIME_BALL, 3f, ParticleTypes.ITEM_SLIME).withEffect(StatusEffects.POISON, 80, 1);

    static {
        // ============================================================ PHASE 1 - Starter Seas
        def(MobSpec.of("raft_pirate", 1).kind(SWIM).floats().awayFrom("bloodfin_reef", 50).stats(22, 4, 0.9, 2).size(1.1f, 2.0f, 1.0f, 0.8f).xp(8).noMelee()
                .ability(Ability.of("knives", "special2", 50, 3, 16, 7, projectile(KNIFE, 3, 18, 1.2f)))
                .ability(Ability.of("dynamite", "special2", 140, 5, 14, 7, projectile(DYNAMITE, 1, 0, 0.9f)))
                .drop(ModItems.PIRATE_COIN, 1, 3, 0.8f).drop(ModItems.ROPE, 1, 2, 0.4f).drop(ModItems.GROG, 1, 1, 0.15f)
                .drop(ModItems.DYNAMITE, 1, 2, 0.25f).drop(Items.GUNPOWDER, 1, 2, 0.35f)
                .spawns(SpawnGroup.MONSTER, 3, 1, 1, ModBiomeKeys.OPEN_OCEAN, ModBiomeKeys.TEMPERATE_SHALLOWS).egg(0x3a5a7a, 0xb8322c));

        // BOSS CHAIN 1/10 (see BossProgression) - the first real fight; unlocks zone 2. Bespoke class:
        // CaptainRackhamEntity (flurry, powder keg, Polly, broadside with the fort's wall cannons, grog swig).
        def(MobSpec.of("captain_rackham", 1).stats(200, 8, 0.29, 8).size(0.9f, 2.8f, 1.3f, 1.0f).xp(150).knockbackRes(0.6).follow(32)
                .boss(BossBar.Color.YELLOW, 2).entity(CaptainRackhamEntity::new).enrage("powder_mad", "Powder-Mad")
                .ability(new Ability("flintlock", "pistol", 70, 5, 24, 14, 22, false, CaptainRackhamEntity.flintlock()))
                .ability(new Ability("cutlass_flurry", "flurry", 110, 0, 7, 6, 24, false, CaptainRackhamEntity.flurry()))
                .ability(new Ability("powder_keg", "keg", 170, 5, 20, 16, 24, false, CaptainRackhamEntity.powderKeg()))
                .ability(new Ability("polly", "polly", 220, 3, 16, 12, 24, false, CaptainRackhamEntity.polly()))
                .ability(new Ability("bosuns_whistle", "whistle", 480, 0, 24, 18, 26, false, CaptainRackhamEntity.whistle()))
                .phase2(new Ability("dynamite_barrage", "barrage", 150, 4, 20, 12, 22, false, CaptainRackhamEntity.dynamiteBarrage()))
                .phase2(new Ability("broadside", "broadside", 320, 0, 40, 16, 30, false, CaptainRackhamEntity.broadside()))
                .drop(ModItems.BOARDING_SABRE, 1, 1, 1f).drop(ModItems.PIRATE_COIN, 16, 26, 1f).drop(ModItems.SEAFARERS_TOKEN, 1, 1, 1f)
                .drop(ModItems.DYNAMITE, 4, 8, 1f).drop(ModItems.TREASURE_MAP_RARE, 1, 1, 0.5f).egg(0x8a1e22, 0xe0b84a));

        // BOSS CHAIN 6/10 - a late-game return to zone 1: the reef's apex hunter, sealed until the Abyssal King falls.
        // Bespoke class BloodfinEntity (harpoon winches -> tonic immobility, tearing flesh + blood bait, Devour breach,
        // Blood Frenzy hunt). ~64 px nose-to-tail x renderScale 1.6 = ~6.4 blocks; hitbox is the thick of the body.
        def(MobSpec.of("bloodfin", 1).kind(SWIM).stats(650, 17, 1.45, 14).size(2.6f, 1.7f, 1.6f, 1.8f).xp(420).knockbackRes(1).follow(64)
                .boss(BossBar.Color.RED, 0).entity(BloodfinEntity::new).enrage("frenzy", "Blood Frenzy").reach(40).lifesteal(0.2f)
                .trail(ParticleTypes.DAMAGE_INDICATOR, 12)
                .ability(new Ability("lunge", "lunge", 60, 4, 16, 4, 16, false, dash(1.8)))
                .ability(new Ability("devour", "devour", 240, 0, 4.5, 9, 18, false, BloodfinEntity.devourEffect()))
                .ability(new Ability("tail_slap", "tail_slap", 90, 0, 6, 10, 18, false, slam(6, 15, 0, ParticleTypes.SPLASH)))
                .ability(new Ability("wake_slam", "wake_slam", 200, 0, 14, 12, 22, false, both(pull(14, 1.0), slam(4, 8, 0, ParticleTypes.SPLASH))))
                .phase2(new Ability("the_hunt", "hunt", 300, 3, 24, 4, 20, false, BloodfinEntity.hunt()))
                .phase2(new Ability("frenzied_devour", "devour", 170, 0, 4.5, 9, 18, false, BloodfinEntity.devourEffect()))
                .drop(ModItems.KRAKEN_FANG, 1, 1, 1f).drop(ModItems.RAW_SHARK_MEAT, 6, 10, 1f).drop(ModItems.PIRATE_COIN, 32, 48, 1f)
                .drop(ModItems.KRAKEN_SCALE, 4, 8, 1f).drop(ModItems.HARPOON, 4, 8, 1f).drop(Items.HEART_OF_THE_SEA, 1, 1, 0.5f).egg(0x5a6e7e, 0xa01818));

        // ============================================================ PHASE 2 - Reefs & Siren Sea
        def(MobSpec.of("kraken_tentacle", 2).kind(STATIONARY).stats(40, 7, 0, 4).size(0.8f, 2.2f, 1.0f, 0.6f).xp(12).knockbackRes(1)
                .ability(Ability.of("grab", "attack", 50, 0, 4, 5, grab(6, 60)))
                .ability(Ability.of("slam", "special", 110, 0, 5, 8, slam(4.5, 8, 0, ParticleTypes.BUBBLE)))
                .drop(ModItems.KRAKEN_INK, 1, 2, 0.6f).drop(Items.PRISMARINE_SHARD, 1, 3, 0.5f)
                .spawns(SpawnGroup.MONSTER, 3, 1, 1, ModBiomeKeys.CORAL_BAY, ModBiomeKeys.REEF_EDGE).egg(0x5a2a4a, 0xff6a8a));

        def(MobSpec.of("reefback_fish", 2).kind(SWIM).temper(PASSIVE).stats(24, 0, 0.8, 2).size(1.2f, 1.0f, 1.0f, 0.8f).xp(3).noMelee()
                .drop(Items.COD, 2, 4, 1f).drop(Items.TUBE_CORAL, 1, 2, 0.4f).drop(Items.BRAIN_CORAL, 1, 2, 0.4f).drop(Items.FIRE_CORAL, 1, 1, 0.3f)
                .spawns(SpawnGroup.WATER_CREATURE, 6, 1, 2, ModBiomeKeys.CORAL_BAY, ModBiomeKeys.REEF_EDGE, ModBiomeKeys.SIREN_SEA).egg(0x4aa0d6, 0xd23c3c));

        def(MobSpec.of("void_squid", 2).kind(SWIM).stats(22, 5, 1.1, 0).size(0.7f, 1.3f, 1.0f, 0.5f).xp(8)
                .trail(ParticleTypes.SQUID_INK, 10)
                .ability(Ability.of("ink", "special", 90, 2, 12, 6, cloud(StatusEffects.BLINDNESS, 60, 2.5f, ParticleTypes.SQUID_INK)))
                .ability(Ability.of("grab", "attack", 70, 0, 4, 4, grab(4, 40)))
                .drop(Items.INK_SAC, 1, 3, 1f).drop(ModItems.KRAKEN_INK, 1, 1, 0.3f)
                .spawns(SpawnGroup.MONSTER, 4, 1, 2, ModBiomeKeys.SIREN_SEA, ModBiomeKeys.REEF_EDGE).egg(0x141820, 0x2fd8ff));

        // BOSS CHAIN 2/10 - unlocks zone 3. Bespoke class SeaSerpentEntity: four pearl plates anchored by
        // Tideward Stones in the Serpent's Hollow (40% damage taken with all four; Tidebreaker breaks them).
        // renderScale 1.6 -> ~12 blocks nose to tail; hitbox covers the head + front coils.
        def(MobSpec.of("sea_serpent", 2).kind(SWIM).stats(300, 11, 1.45, 6).size(2.4f, 1.7f, 1.6f, 1.8f).xp(220).knockbackRes(1).follow(44)
                .boss(BossBar.Color.BLUE, 3).entity(SeaSerpentEntity::new).enrage("stormscale", "Stormscale")
                .ability(Ability.of("bite", "attack", 55, 4, 16, 3, dash(1.6)))
                .ability(new Ability("tail_lash", "lash", 110, 0, 7, 10, 18, false, slam(7, 10, 0, ParticleTypes.SPLASH)))
                .ability(new Ability("constrict", "coil", 170, 3, 14, 12, 26, false, SeaSerpentEntity.constrict()))
                .ability(new Ability("pressure_jet", "jet", 130, 5, 14, 16, 24, false, SeaSerpentEntity.pressureJet()))
                .ability(new Ability("silt_ambush", "ambush", 260, 6, 24, 8, 36, false, SeaSerpentEntity.ambush()))
                .phase2(new Ability("maelstrom", "maelstrom", 240, 0, 20, 14, 28, false, both(pull(18, 0.8), aura(StatusEffects.MINING_FATIGUE, 200, 1, 14, ParticleTypes.BUBBLE_POP))))
                .phase2(new Ability("storm_surge", "surge", 200, 0, 9, 2, 30, false, SeaSerpentEntity.surge()))
                .phase2(new Ability("brood", "brood", 420, 0, 24, 12, 22, false, SeaSerpentEntity.brood()))
                .drop(ModItems.STORMCALLER, 1, 1, 1f).drop(ModItems.KRAKEN_SCALE, 3, 6, 1f).drop(ModItems.PIRATE_COIN, 20, 30, 1f)
                .drop(Items.NAUTILUS_SHELL, 1, 2, 1f).drop(ModItems.TREASURE_MAP_LEGENDARY, 1, 1, 0.4f).egg(0x1f5e6a, 0xffd84a));

        // BOSS CHAIN 7/10 - sealed until the Bloodfin falls.
        // BOSS CHAIN 7/10 - bespoke class KrakenEntity: phase 1 six KRAKEN ARMS (below) while it lurks in the abyss,
        // phase 2 it rises: Swallow -> spit -> swat, Harpoon Spit, Tentacle Bulwark, Quake; arms regrow at 66/33%. ~56 px x renderScale 2.6.
        def(MobSpec.of("kraken", 2).kind(STATIONARY).stats(760, 17, 0, 18).size(5.0f, 8.0f, 2.6f, 4.0f).xp(460).knockbackRes(1).follow(40)
                .boss(BossBar.Color.PURPLE, 0).tough(0.9f).noMelee().entity(KrakenEntity::new).enrage("wrath", "Abyssal Wrath")
                .ability(new Ability("beak_crush", "attack", 60, 0, 7.5, 8, 16, false, slam(7.5, 16, 0, ParticleTypes.BUBBLE)))
                .ability(new Ability("swallow", "inhale", 260, 0, 20, 2, 64, false, KrakenEntity.inhale()))
                .ability(new Ability("harpoon_spit", "harpoon_spit", 110, 4, 30, 12, 20, false, KrakenEntity.harpoonSpit()))
                .ability(new Ability("bulwark", "shield", 320, 0, 16, 1, 76, false, KrakenEntity.bulwark()))
                .ability(new Ability("quake", "quake", 200, 0, 22, 14, 24, false, KrakenEntity.quake()))
                .phase2(new Ability("maelstrom", "whirl", 360, 0, 24, 4, 52, false, KrakenEntity.whirl()))
                .drop(ModItems.KRAKEN_FANG, 1, 1, 1f).drop(ModItems.KRAKEN_INK, 8, 14, 1f).drop(ModItems.KRAKEN_SCALE, 6, 10, 1f)
                .drop(ModItems.PIRATE_COIN, 36, 52, 1f).egg(0x5a2a4a, 0xffd84a));

        // Phase-1 arm of the Kraken (KrakenArmEntity): ~70 px x renderScale 2.0 = ~9 blocks tall, spawned by the Kraken.
        def(MobSpec.of("kraken_arm", 2).kind(STATIONARY).stats(90, 12, 0, 8).size(1.8f, 8.5f, 2.0f, 1.2f).xp(25).knockbackRes(1).follow(24)
                .noMelee().entity(KrakenArmEntity::new)
                .ability(new Ability("arm_slam", "attack", 70, 0, 10, 1, 24, false, KrakenArmEntity.slam()))
                .ability(new Ability("arm_sweep", "sweep", 90, 0, 8, 1, 20, false, KrakenArmEntity.sweep()))
                .ability(new Ability("arm_grab", "grab", 170, 0, 7, 1, 56, false, KrakenArmEntity.grab()))
                .drop(ModItems.KRAKEN_INK, 1, 3, 0.8f).drop(ModItems.KRAKEN_SCALE, 1, 1, 0.3f).egg(0x12151a, 0xc08838));

        // ============================================================ PHASE 3 - Volcanic
        def(MobSpec.of("obsidian_golem", 3).stats(110, 12, 0.2, 14).size(1.7f, 2.6f, 1.4f, 1.2f).xp(40).knockbackRes(1).tough(0.6f)
                .fireImmune().hurtByWater().trail(ParticleTypes.PORTAL, 8)
                .ability(Ability.of("slam", "special", 120, 0, 5, 16, slam(5, 12, 3, ParticleTypes.FLAME)))
                .ability(Ability.of("fire_charge", "special2", 80, 4, 18, 6, projectile(FIREBALL, 1, 0, 1.3f)))
                .drop(Items.OBSIDIAN, 2, 4, 1f).drop(ModItems.VOLCANIC_EMBER, 2, 4, 1f).drop(Items.CRYING_OBSIDIAN, 1, 2, 0.4f)
                .spawns(SpawnGroup.MONSTER, 2, 1, 1, ModBiomeKeys.ASH_REEF, ModBiomeKeys.BOILING_BASIN, ModBiomeKeys.MAGMA_SEA).egg(0x1c1828, 0xb060ff));

        def(MobSpec.of("flame_sprite", 3).kind(FLY).stats(14, 3, 0.35, 0).size(0.6f, 1.0f, 0.8f, 0.3f).xp(6).fireImmune().hurtByWater()
                .noMelee().trail(ParticleTypes.FLAME, 2)
                .ability(Ability.of("fireballs", "attack", 45, 3, 16, 5, projectile(FIREBALL, 2, 12, 1.1f)))
                .ability(Ability.of("flicker", "special2", 140, 0, 6, 4, blink(ParticleTypes.FLAME)))
                .drop(ModItems.VOLCANIC_EMBER, 1, 2, 0.7f).drop(Items.BLAZE_POWDER, 1, 1, 0.3f)
                .spawns(SpawnGroup.MONSTER, 6, 1, 3, ModBiomeKeys.ASH_REEF, ModBiomeKeys.BOILING_BASIN, ModBiomeKeys.MAGMA_SEA).egg(0xff8a2a, 0xffd070));

        def(MobSpec.of("ember_wraith", 3).stats(30, 6, 0.3, 4).size(0.7f, 2.0f, 1.0f, 0.5f).xp(10).fireImmune().hurtByWater().onHitFire(4)
                .trail(ParticleTypes.SMALL_FLAME, 3)
                .ability(Ability.of("lunge", "special2", 90, 3, 10, 4, dash(1.1)))
                .ability(Ability.of("heat_wave", "special", 160, 0, 5, 10, aura(StatusEffects.WEAKNESS, 100, 0, 5, ParticleTypes.FLAME)))
                .drop(ModItems.VOLCANIC_EMBER, 1, 2, 0.6f).drop(Items.MAGMA_CREAM, 1, 1, 0.3f)
                .spawns(SpawnGroup.MONSTER, 6, 1, 2, ModBiomeKeys.ASH_REEF, ModBiomeKeys.BOILING_BASIN, ModBiomeKeys.MAGMA_SEA).egg(0x2a2727, 0xffae3a));

        def(MobSpec.of("lava_scorpion", 3).stats(28, 5, 0.3, 6).size(1.2f, 0.9f, 0.8f, 0.8f).xp(10).fireImmune()
                .onHit(StatusEffects.POISON, 80, 0).onHitFire(2)
                .ability(Ability.of("sting", "attack", 60, 0, 3.5, 4, both(slam(2.5, 5, 3, ParticleTypes.FLAME), aura(StatusEffects.POISON, 80, 1, 2.5, ParticleTypes.ITEM_SLIME))))
                .ability(Ability.of("burrow", "special2", 200, 4, 16, 10, blink(ParticleTypes.LAVA)))
                .drop(ModItems.VOLCANIC_EMBER, 1, 2, 0.6f).drop(Items.SPIDER_EYE, 1, 1, 0.4f)
                .spawns(SpawnGroup.MONSTER, 6, 1, 2, ModBiomeKeys.ASH_REEF, ModBiomeKeys.BOILING_BASIN, ModBiomeKeys.MAGMA_SEA).egg(0x3a2218, 0xff7a1e));

        def(MobSpec.of("fire_pirate", 3).stats(34, 7, 0.3, 4).size(0.7f, 2.1f, 1.0f, 0.5f).xp(12).fireImmune().onHitFire(5)
                .trail(ParticleTypes.SMALL_FLAME, 5)
                .ability(Ability.of("firebomb", "special2", 90, 4, 14, 7, projectile(FIREBALL, 1, 0, 1.0f)))
                .drop(ModItems.PIRATE_COIN, 1, 4, 0.8f).drop(ModItems.VOLCANIC_EMBER, 1, 1, 0.5f).drop(ModItems.GROG, 1, 1, 0.2f)
                .spawns(SpawnGroup.MONSTER, 5, 1, 2, ModBiomeKeys.ASH_REEF, ModBiomeKeys.BOILING_BASIN, ModBiomeKeys.MAGMA_SEA).egg(0x5a1a14, 0xffae3a));

        // BOSS CHAIN 3/10 - unlocks zone 4. Bespoke class MoltenWarlordEntity (chain mace, Quench Valves - molten armour
        // halves damage until he is lured under a cascade). ~5 blocks tall: model ~2 blocks x renderScale 2.4.
        def(MobSpec.of("molten_warlord", 3).stats(420, 14, 0.25, 10).size(2.4f, 4.9f, 2.4f, 2.4f).xp(320).knockbackRes(1).follow(36)
                .boss(BossBar.Color.RED, 4).entity(MoltenWarlordEntity::new).enrage("molten_core", "Molten Core")
                .fireImmune().hurtByWater().onHitFire(5).trail(ParticleTypes.LAVA, 6)
                .ability(new Ability("mace_lash", "lash", 100, 4, 12, 2, 24, false, MoltenWarlordEntity.lash()))
                .ability(new Ability("flail_whirl", "whirl", 150, 0, 7, 2, 30, false, MoltenWarlordEntity.whirl()))
                .ability(new Ability("fissure_slam", "slam", 140, 3, 15, 14, 24, false, MoltenWarlordEntity.fissure()))
                .ability(new Ability("chain_hook", "hook", 170, 7, 16, 10, 20, false, MoltenWarlordEntity.hook()))
                .phase2(new Ability("meteor_rain", "meteors", 220, 3, 22, 12, 26, false, MoltenWarlordEntity.meteors()))
                .phase2(new Ability("forge_call", "forge_call", 380, 0, 24, 12, 22, false, MoltenWarlordEntity.forgeCall()))
                .phase2(new Ability("core_vent", "core_vent", 260, 0, 8, 12, 22, false,
                        both(aura(StatusEffects.WEAKNESS, 160, 1, 8, ParticleTypes.LAVA), selfBuff(StatusEffects.RESISTANCE, 160, 0, ParticleTypes.FLAME))))
                .drop(ModItems.EMBERBRAND, 1, 1, 1f).drop(ModItems.VOLCANIC_EMBER, 10, 16, 1f).drop(ModItems.PIRATE_COIN, 24, 36, 1f)
                .drop(Items.NETHERITE_SCRAP, 1, 2, 0.6f).drop(ModItems.DYNAMITE, 3, 6, 1f).egg(0x2a2727, 0xff8a2a));

        // ============================================================ PHASE 4 - Cursed Seas
        def(MobSpec.of("skeleton_pirate", 4).stats(26, 6, 0.3, 4).size(0.6f, 2.0f, 1.0f, 0.5f).xp(8)
                .ability(Ability.of("bone_toss", "special2", 100, 4, 14, 7, projectile(BONE, 1, 0, 1.2f)))
                .drop(ModItems.CURSED_BONE, 1, 2, 0.5f).drop(Items.BONE, 1, 3, 0.8f).drop(ModItems.PIRATE_COIN, 1, 3, 0.6f)
                .spawns(SpawnGroup.MONSTER, 8, 1, 3, ModBiomeKeys.PHANTOM_WAKE, ModBiomeKeys.SHIPGRAVE_DEPTHS, ModBiomeKeys.DROWNED_TRENCH).egg(0xd8d2bc, 0x7a2a22));

        def(MobSpec.of("ghost_shark", 4).kind(SWIM).stats(36, 8, 1.2, 2).size(1.4f, 1.0f, 1.0f, 0.8f).xp(12).lifesteal(0.3f)
                .trail(ParticleTypes.SOUL, 8)
                .ability(Ability.of("phase_lunge", "attack", 70, 3, 14, 3, dash(1.4)))
                .drop(ModItems.CURSED_BONE, 1, 3, 0.8f).drop(Items.PHANTOM_MEMBRANE, 1, 1, 0.3f)
                .spawns(SpawnGroup.MONSTER, 4, 1, 1, ModBiomeKeys.PHANTOM_WAKE, ModBiomeKeys.SHIPGRAVE_DEPTHS, ModBiomeKeys.DROWNED_TRENCH).egg(0x8ae0e0, 0x3a6a6a));

        def(MobSpec.of("drowned_hands", 4).kind(STATIONARY).stats(24, 5, 0, 2).size(1.0f, 1.2f, 1.0f, 0.8f).xp(8).knockbackRes(1)
                .ability(Ability.of("clutch", "attack", 40, 0, 3, 4, grab(4, 60)))
                .ability(Ability.of("drag_under", "special", 140, 0, 6, 8, both(pull(6, 0.5), aura(StatusEffects.SLOWNESS, 60, 2, 5, ParticleTypes.SOUL))))
                .drop(ModItems.CURSED_BONE, 1, 2, 0.6f).drop(Items.ROTTEN_FLESH, 1, 3, 0.8f)
                .spawns(SpawnGroup.MONSTER, 3, 1, 1, ModBiomeKeys.PHANTOM_WAKE, ModBiomeKeys.SHIPGRAVE_DEPTHS).egg(0x6a9a8a, 0x2a3a34));

        def(MobSpec.of("trident_skeleton", 4).stats(24, 5, 0.3, 4).size(0.6f, 2.1f, 1.0f, 0.5f).xp(9).noMelee()
                .ability(Ability.of("hurl_trident", "special2", 50, 3, 20, 7, projectile(TRIDENT, 1, 0, 1.6f)))
                .ability(Ability.of("jab", "attack", 30, 0, 3, 3, slam(2.5, 5, 0, ParticleTypes.SWEEP_ATTACK)))
                .drop(ModItems.CURSED_BONE, 1, 2, 0.5f).drop(Items.PRISMARINE_SHARD, 1, 2, 0.4f).drop(Items.TRIDENT, 1, 1, 0.03f)
                .spawns(SpawnGroup.MONSTER, 6, 1, 2, ModBiomeKeys.PHANTOM_WAKE, ModBiomeKeys.SHIPGRAVE_DEPTHS, ModBiomeKeys.DROWNED_TRENCH).egg(0xd8d2bc, 0x3a6a6a));

        def(MobSpec.of("phantom_pirate", 4).kind(FLY).stats(28, 6, 0.16, 0).size(0.7f, 2.1f, 1.0f, 0.4f).xp(12).lifesteal(0.25f)
                .onHit(StatusEffects.WEAKNESS, 80, 0).trail(ParticleTypes.SOUL_FIRE_FLAME, 6)
                .ability(Ability.of("haunt", "special2", 120, 3, 16, 4, blink(ParticleTypes.SOUL)))
                .drop(ModItems.CURSED_BONE, 1, 2, 0.6f).drop(Items.PHANTOM_MEMBRANE, 1, 1, 0.4f).drop(ModItems.PIRATE_COIN, 1, 3, 0.6f)
                .spawns(SpawnGroup.MONSTER, 4, 1, 1, ModBiomeKeys.PHANTOM_WAKE, ModBiomeKeys.SHIPGRAVE_DEPTHS, ModBiomeKeys.DROWNED_TRENCH).egg(0x3fbfb0, 0x0e2020));

        // BOSS CHAIN 8/10 - sealed until the Kraken falls
        // Bespoke class ChainedRevenantEntity: hung in chains, he SUNDERS (limbs to the wall anchors, head loose) at a random
        // 75/50/25 or 66/33 pattern; lair = the Gallows Grotto. ~44 px x renderScale 2.4 = ~6.6 blocks.
        def(MobSpec.of("chained_revenant", 4).stats(860, 19, 0.27, 18).size(2.2f, 6.4f, 2.4f, 2.0f).xp(500).knockbackRes(1).follow(40)
                .boss(BossBar.Color.WHITE, 0).tough(0.9f).entity(ChainedRevenantEntity::new).enrage("wrath", "Hanged Wrath")
                .onHit(StatusEffects.WITHER, 100, 1).trail(ParticleTypes.SOUL, 5)
                .ability(new Ability("chain_whip", "chain_whip", 90, 3, 16, 14, 24, false, both(pull(16, 1.0), grab(10, 50))))
                .ability(new Ability("grave_slam", "grave_slam", 120, 0, 7, 14, 26, false, slam(7, 18, 0, ParticleTypes.SOUL)))
                .ability(new Ability("noose", "noose", 220, 3, 18, 12, 28, false, ChainedRevenantEntity.noose()))
                .ability(new Ability("gibbet_drop", "gibbet", 260, 2, 14, 2, 32, false, ChainedRevenantEntity.gibbet()))
                .phase2(new Ability("dread", "wrath", 240, 0, 12, 10, 30, false, aura(StatusEffects.WITHER, 140, 1, 12, ParticleTypes.SOUL_FIRE_FLAME)))
                .drop(ModItems.SOULRENDER, 1, 1, 1f).drop(ModItems.CURSED_BONE, 8, 14, 1f).drop(ModItems.PIRATE_COIN, 40, 56, 1f)
                .drop(Items.NETHERITE_SCRAP, 2, 3, 1f).egg(0x1c1e20, 0x3fe0c0));

        // Gallows Landing's last soul (LamplighterEntity): talk to him (right-click) - he warns you off the Gallows Grotto,
        // then tells you how to fight what hangs in it. Spawned once by the grotto's surface port; unkillable, no spawns.
        def(MobSpec.of("lamplighter", 4).temper(PASSIVE).noMelee().stats(40, 0, 0.2, 0).size(0.7f, 1.9f, 1.0f, 0.5f).xp(0)
                .entity(LamplighterEntity::new).egg(0xa88e40, 0x5ff0d8));

        // BOSS CHAIN 4/10 - unlocks zone 5. Bespoke class GhostCaptainEntity: summoned aboard the FLYING DUTCHMAN
        // (world/GhostShipEncounter) - bound and unhurtable until players sink his ship, then he boards theirs.
        def(MobSpec.of("ghost_captain", 4).kind(FLY).stats(460, 14, 0.3, 12).size(1.3f, 3.4f, 1.6f, 1.2f).xp(340).knockbackRes(1).follow(40)
                .boss(BossBar.Color.GREEN, 5).entity(GhostCaptainEntity::new).enrage("cursed_tide", "Cursed Tide")
                .lifesteal(0.2f).trail(ParticleTypes.SOUL_FIRE_FLAME, 3)
                .ability(new Ability("cursed_slash", "slash", 60, 0, 4.5, 6, 14, false, slam(4.5, 12, 0, ParticleTypes.SOUL_FIRE_FLAME)))
                .ability(new Ability("spectral_step", "blink", 110, 4, 20, 6, 14, false, blink(ParticleTypes.SOUL)))
                .ability(new Ability("soul_beam", "beam", 90, 4, 26, 12, 20, false, beam(10, StatusEffects.WITHER, 80, ParticleTypes.SOUL_FIRE_FLAME)))
                .ability(new Ability("anchor_toss", "anchor", 150, 5, 18, 12, 22, false, GhostCaptainEntity.anchorToss()))
                .ability(new Ability("phantom_broadside", "broadside", 260, 0, 40, 16, 26, false, GhostCaptainEntity.phantomBroadside()))
                .phase2(new Ability("ghost_crew", "crew", 380, 0, 24, 14, 22, false, GhostCaptainEntity.ghostCrew()))
                .phase2(new Ability("dead_calm", "calm", 280, 0, 14, 12, 20, false, aura(StatusEffects.DARKNESS, 120, 0, 14, ParticleTypes.SCULK_SOUL)))
                .phase2(new Ability("drowning_curse", "curse", 240, 0, 10, 12, 20, false,
                        both(aura(StatusEffects.SLOWNESS, 100, 1, 10, ParticleTypes.SOUL), aura(StatusEffects.WITHER, 80, 0, 10, ParticleTypes.BUBBLE_POP))))
                .drop(ModItems.WRAITHBLADE, 1, 1, 1f).drop(ModItems.CURSED_BONE, 8, 14, 1f).drop(ModItems.PIRATE_COIN, 30, 44, 1f)
                .drop(ModItems.TREASURE_MAP_LEGENDARY, 1, 1, 0.7f).drop(ModItems.SHIP_REPAIR_KIT, 2, 3, 1f).egg(0x2a8a80, 0x0e1818));

        // ============================================================ PHASE 5 - The Abyss
        def(MobSpec.of("abyss_crab", 5).stats(34, 7, 0.28, 10).size(1.3f, 0.6f, 1.0f, 0.8f).xp(10).knockbackRes(0.5).trail(ParticleTypes.GLOW, 12)
                .ability(Ability.of("crush", "attack", 70, 0, 3, 4, slam(3, 8, 0, ParticleTypes.BUBBLE)))
                .drop(ModItems.KRAKEN_SCALE, 1, 2, 0.4f).drop(Items.PRISMARINE_CRYSTALS, 1, 3, 0.6f)
                .spawns(SpawnGroup.MONSTER, 6, 1, 2, ModBiomeKeys.ABYSSAL_RINGS, ModBiomeKeys.PILLAR_SEA, ModBiomeKeys.MAW_DEPTHS).egg(0x1a242c, 0x3fd0ff));

        def(MobSpec.of("brain_fish", 5).kind(SWIM).stats(30, 3, 0.9, 2).size(1.1f, 0.9f, 0.9f, 0.7f).xp(12).noMelee().trail(ParticleTypes.ENCHANT, 3)
                .ability(Ability.of("psychic_beam", "special", 70, 3, 18, 8, beam(5, StatusEffects.NAUSEA, 120, ParticleTypes.ENCHANT)))
                .ability(Ability.of("mind_fog", "special2", 180, 0, 8, 8, aura(StatusEffects.SLOWNESS, 100, 1, 8, ParticleTypes.WITCH)))
                .drop(ModItems.KRAKEN_SCALE, 1, 1, 0.3f).drop(Items.EXPERIENCE_BOTTLE, 1, 2, 0.5f)
                .spawns(SpawnGroup.MONSTER, 3, 1, 1, ModBiomeKeys.ABYSSAL_RINGS, ModBiomeKeys.MAW_DEPTHS).egg(0x1a2a4a, 0x7a8aff));

        def(MobSpec.of("abyssal_centipede", 5).stats(36, 7, 0.34, 6).size(1.0f, 0.5f, 0.8f, 0.6f).xp(12).onHit(StatusEffects.POISON, 100, 1)
                .ability(Ability.of("strike", "special2", 70, 3, 10, 3, dash(1.3)))
                .drop(ModItems.KRAKEN_SCALE, 1, 1, 0.3f).drop(Items.SPIDER_EYE, 1, 2, 0.5f)
                .spawns(SpawnGroup.MONSTER, 4, 1, 1, ModBiomeKeys.ABYSSAL_RINGS, ModBiomeKeys.PILLAR_SEA).egg(0x1c2228, 0x3fd0c0));

        def(MobSpec.of("shadow", 5).stats(30, 8, 0.33, 2).size(0.6f, 2.0f, 1.0f, 0.4f).xp(14).onHit(StatusEffects.BLINDNESS, 40, 0)
                .trail(ParticleTypes.SMOKE, 4)
                .ability(Ability.of("shadowstep", "special2", 90, 3, 18, 3, blink(ParticleTypes.SMOKE)))
                .drop(Items.ECHO_SHARD, 1, 1, 0.1f).drop(Items.INK_SAC, 1, 2, 0.6f)
                .spawns(SpawnGroup.MONSTER, 3, 1, 1, ModBiomeKeys.ABYSSAL_RINGS, ModBiomeKeys.PILLAR_SEA, ModBiomeKeys.MAW_DEPTHS).egg(0x08080c, 0xb080ff));

        def(MobSpec.of("coral_whale", 5).kind(SWIM).temper(PASSIVE).stats(120, 0, 0.7, 6).size(3.5f, 2.0f, 1.5f, 3.0f).xp(10).noMelee()
                .knockbackRes(1)
                .drop(Items.COD, 6, 10, 1f).drop(Items.HORN_CORAL_BLOCK, 1, 3, 0.6f).drop(Items.NAUTILUS_SHELL, 1, 1, 0.3f)
                .spawns(SpawnGroup.WATER_CREATURE, 2, 1, 1, ModBiomeKeys.ABYSSAL_RINGS, ModBiomeKeys.PILLAR_SEA).egg(0x1e4a8a, 0xd23c3c));

        def(MobSpec.of("luminous_isopod", 5).temper(NEUTRAL).stats(20, 4, 0.22, 8).size(0.9f, 0.5f, 0.7f, 0.5f).xp(4).trail(ParticleTypes.GLOW, 10)
                .drop(Items.GLOW_INK_SAC, 1, 2, 0.8f).drop(Items.GLOW_BERRIES, 1, 3, 0.5f)
                .spawns(SpawnGroup.MONSTER, 5, 1, 3, ModBiomeKeys.ABYSSAL_RINGS, ModBiomeKeys.PILLAR_SEA, ModBiomeKeys.MAW_DEPTHS).egg(0x6a8a90, 0x6fe8ff));

        def(MobSpec.of("deep_lurker", 5).stats(40, 9, 0.32, 6).size(1.2f, 0.95f, 1.0f, 0.8f).xp(14).lifesteal(0.2f)
                .ability(Ability.of("pounce", "special2", 80, 3, 12, 4, dash(1.5)))
                .drop(ModItems.KRAKEN_SCALE, 1, 2, 0.4f).drop(Items.LEATHER, 1, 3, 0.6f)
                .spawns(SpawnGroup.MONSTER, 4, 1, 1, ModBiomeKeys.PILLAR_SEA, ModBiomeKeys.MAW_DEPTHS).egg(0x2a4a2a, 0x6fffc0));

        def(MobSpec.of("corrupted_diver", 5).stats(34, 7, 0.28, 6).size(0.6f, 2.1f, 1.0f, 0.5f).xp(10).onHit(StatusEffects.POISON, 60, 0)
                .trail(ParticleTypes.GLOW, 14)
                .drop(Items.ROTTEN_FLESH, 1, 3, 0.7f).drop(Items.PRISMARINE_SHARD, 1, 2, 0.5f).drop(ModItems.KRAKEN_SCALE, 1, 1, 0.2f)
                .spawns(SpawnGroup.MONSTER, 6, 1, 2, ModBiomeKeys.ABYSSAL_RINGS, ModBiomeKeys.PILLAR_SEA, ModBiomeKeys.MAW_DEPTHS).egg(0x4a6a64, 0xe0508a));

        def(MobSpec.of("abyss_eel", 5).kind(SWIM).stats(32, 7, 1.3, 2).size(0.9f, 0.6f, 0.9f, 0.6f).xp(12)
                .ability(Ability.of("shock", "special", 110, 0, 5, 6, both(aura(StatusEffects.SLOWNESS, 60, 2, 5, ParticleTypes.ELECTRIC_SPARK), slam(4, 6, 0, ParticleTypes.ELECTRIC_SPARK))))
                .ability(Ability.of("strike", "attack", 60, 3, 12, 3, dash(1.4)))
                .drop(ModItems.KRAKEN_SCALE, 1, 1, 0.4f).drop(Items.GLOW_INK_SAC, 1, 1, 0.5f)
                .spawns(SpawnGroup.MONSTER, 4, 1, 2, ModBiomeKeys.ABYSSAL_RINGS, ModBiomeKeys.MAW_DEPTHS).egg(0x3a1a2a, 0x6fe0d0));

        def(MobSpec.of("crystal_golem", 5).stats(120, 12, 0.22, 16).size(1.7f, 2.5f, 1.3f, 1.2f).xp(40).knockbackRes(1).tough(0.7f)
                .trail(ParticleTypes.END_ROD, 10)
                .ability(Ability.of("shatter", "special", 120, 0, 5, 16, slam(5, 12, 0, ParticleTypes.END_ROD)))
                .ability(Ability.of("shard_volley", "special2", 80, 4, 18, 8, projectile(SHARD, 4, 30, 1.3f)))
                .drop(Items.AMETHYST_SHARD, 3, 6, 1f).drop(ModItems.KRAKEN_SCALE, 1, 2, 0.5f).drop(Items.DIAMOND, 1, 1, 0.1f)
                .spawns(SpawnGroup.MONSTER, 2, 1, 1, ModBiomeKeys.PILLAR_SEA, ModBiomeKeys.MAW_DEPTHS).egg(0x22343e, 0x6fe8ff));

        def(MobSpec.of("drowned_sailor", 5).stats(30, 6, 0.28, 4).size(0.6f, 2.0f, 1.0f, 0.5f).xp(10)
                .ability(Ability.of("harpoon", "special2", 80, 4, 16, 7, projectile(HARPOON, 1, 0, 1.4f)))
                .drop(Items.ROTTEN_FLESH, 1, 3, 0.7f).drop(ModItems.PIRATE_COIN, 1, 4, 0.6f).drop(Items.NAUTILUS_SHELL, 1, 1, 0.05f)
                .spawns(SpawnGroup.MONSTER, 6, 1, 2, ModBiomeKeys.ABYSSAL_RINGS, ModBiomeKeys.PILLAR_SEA, ModBiomeKeys.MAW_DEPTHS).egg(0x5a7a70, 0x6fffe0));

        def(MobSpec.of("jelly_skull", 5).kind(SWIM).stats(24, 5, 0.8, 0).size(0.7f, 1.2f, 0.9f, 0.4f).xp(10).noMelee()
                .onHit(StatusEffects.POISON, 80, 1).trail(ParticleTypes.GLOW, 6)
                .ability(Ability.of("zap", "special", 80, 2, 12, 6, beam(5, StatusEffects.POISON, 60, ParticleTypes.ELECTRIC_SPARK)))
                .ability(Ability.of("spores", "special2", 160, 0, 10, 6, cloud(StatusEffects.POISON, 80, 2.5f, ParticleTypes.GLOW)))
                .drop(Items.GLOW_INK_SAC, 1, 2, 0.8f).drop(Items.BONE, 1, 2, 0.5f)
                .spawns(SpawnGroup.MONSTER, 5, 1, 2, ModBiomeKeys.ABYSSAL_RINGS, ModBiomeKeys.PILLAR_SEA, ModBiomeKeys.MAW_DEPTHS).egg(0x7a2a4a, 0xff5a8a));

        def(MobSpec.of("anemone_eye", 5).kind(STATIONARY).stats(30, 5, 0, 4).size(0.7f, 1.3f, 1.0f, 0.5f).xp(10).knockbackRes(1).noMelee()
                .ability(Ability.of("gaze", "special", 60, 0, 16, 8, beam(5, StatusEffects.SLOWNESS, 80, ParticleTypes.DAMAGE_INDICATOR)))
                .ability(Ability.of("sting", "attack", 40, 0, 3, 4, grab(5, 40)))
                .drop(Items.RED_DYE, 1, 2, 0.6f).drop(Items.SPIDER_EYE, 1, 1, 0.6f).drop(ModItems.KRAKEN_SCALE, 1, 1, 0.2f)
                .spawns(SpawnGroup.MONSTER, 3, 1, 1, ModBiomeKeys.ABYSSAL_RINGS, ModBiomeKeys.PILLAR_SEA).egg(0x8a3a3a, 0xff3030));

        def(MobSpec.of("void_manta", 5).kind(SWIM).stats(40, 7, 1.3, 4).size(2.0f, 0.5f, 1.2f, 1.2f).xp(12)
                .ability(Ability.of("swoop", "attack", 60, 3, 16, 3, dash(1.6)))
                .ability(Ability.of("barrel_roll", "special", 160, 0, 6, 6, slam(5, 7, 0, ParticleTypes.BUBBLE)))
                .drop(Items.PHANTOM_MEMBRANE, 1, 2, 0.6f).drop(ModItems.KRAKEN_SCALE, 1, 1, 0.3f)
                .spawns(SpawnGroup.MONSTER, 3, 1, 1, ModBiomeKeys.ABYSSAL_RINGS, ModBiomeKeys.PILLAR_SEA, ModBiomeKeys.MAW_DEPTHS).egg(0x141820, 0x6fe8ff));

        def(MobSpec.of("lantern_squid", 5).kind(SWIM).stats(28, 5, 1.0, 0).size(0.7f, 1.4f, 1.0f, 0.5f).xp(10)
                .trail(ParticleTypes.GLOW, 5)
                .ability(Ability.of("flash", "special", 120, 0, 12, 6, aura(StatusEffects.BLINDNESS, 60, 0, 10, ParticleTypes.FLASH)))
                .ability(Ability.of("ink", "special2", 90, 2, 12, 6, projectile(INK, 1, 0, 1.2f)))
                .drop(Items.GLOW_INK_SAC, 1, 3, 1f).drop(ModItems.KRAKEN_INK, 1, 1, 0.3f)
                .spawns(SpawnGroup.MONSTER, 4, 1, 2, ModBiomeKeys.ABYSSAL_RINGS, ModBiomeKeys.MAW_DEPTHS).egg(0x1a1c22, 0x6fe8ff));

        // BOSS CHAIN 5/10 - the first abyss boss; after him the chain loops back to the Bloodfin in zone 1.
        // Bespoke class AbyssalKingEntity (THE FLOODGATES: Tide-Blessed in his flooded court until the four Tide
        // Sluices drain it; stranded when dry). ~46 px model x renderScale 1.75 = ~5 blocks; hitbox to match.
        def(MobSpec.of("abyssal_king", 5).stats(560, 16, 0.28, 16).size(1.6f, 4.6f, 1.75f, 1.5f).xp(380).knockbackRes(1).follow(36)
                .boss(BossBar.Color.BLUE, 0).entity(AbyssalKingEntity::new).enrage("wrath", "Wrath of the Deep").seabed()
                .trail(ParticleTypes.GLOW, 4)
                .ability(new Ability("riptide_charge", "riptide", 130, 5, 16, 1, 30, false, AbyssalKingEntity.riptide()))
                .ability(new Ability("trident_volley", "throw", 90, 4, 22, 9, 18, false, projectile(TRIDENT, 3, 20, 1.6f)))
                .ability(new Ability("crushing_depths", "pressure", 170, 0, 24, 16, 26, false, AbyssalKingEntity.pressure()))
                .ability(new Ability("abyssal_decree", "decree", 300, 0, 14, 10, 24, false, aura(StatusEffects.MINING_FATIGUE, 300, 2, 14, ParticleTypes.GLOW)))
                .ability(new Ability("royal_guard", "summon", 520, 0, 24, 14, 28, false, AbyssalKingEntity.royalGuard()))
                .ability(new Ability("reseal", "reseal", 300, 0, 48, 1, 52, false, AbyssalKingEntity.reseal()))
                .ability(new Ability("gasping_sweep", "sweep", 50, 0, 5.5, 10, 18, false, slam(5.5, 11, 0, ParticleTypes.SPLASH)))
                .phase2(new Ability("maelstrom", "maelstrom", 280, 0, 20, 6, 52, false, AbyssalKingEntity.maelstrom()))
                .phase2(new Ability("undertow", "undertow", 190, 0, 18, 12, 24, false, AbyssalKingEntity.undertow()))
                .drop(ModItems.ABYSSAL_HARPOON, 1, 1, 1f).drop(ModItems.KRAKEN_SCALE, 10, 16, 1f).drop(ModItems.PIRATE_COIN, 32, 48, 1f)
                .drop(Items.HEART_OF_THE_SEA, 1, 1, 1f).egg(0x1e3a36, 0x6fe8d0));

        // The King's guard: walks the flooded floor and winds open Tide Sluices shut (TideWardenEntity).
        def(MobSpec.of("tide_warden", 5).stats(44, 8, 0.27, 10).size(0.7f, 2.1f, 1.05f, 0.5f).xp(20).seabed().entity(TideWardenEntity::new)
                .trail(ParticleTypes.BUBBLE, 8)
                .ability(Ability.of("warden_throw", "special2", 110, 4, 16, 8, projectile(TRIDENT, 1, 0, 1.4f)))
                .drop(ModItems.DEPTH_CHARGE, 1, 2, 0.5f).drop(Items.PRISMARINE_SHARD, 1, 3, 0.8f).drop(Items.PRISMARINE_CRYSTALS, 1, 2, 0.4f)
                .egg(0x327672, 0x8affe8));

        // BOSS CHAIN 9/10 - sealed until the Chained Revenant falls; its death opens the Leviathan's Rift.
        // Bespoke class AbyssalHeartEntity: THE RHYTHM (attacks land on its beat), THE PACEMAKER (4 galvanic pylons struck on
        // the beat -> cardiac arrest, x2), INTO THE HEART at 50% (chambers + nodes), FLATLINE at 25% (the Eye of Thalassar
        // shocks it back, drowned memories). Lair: the Titan's Chest. ~64 px x renderScale 2.0 = ~8 blocks, hangs in the arteries.
        def(MobSpec.of("abyssal_heart", 5).kind(STATIONARY).stats(1024, 15, 0, 10).size(6.0f, 8.0f, 2.0f, 0f).xp(700).knockbackRes(1).follow(72)
                .boss(BossBar.Color.PINK, 0).noMelee().entity(AbyssalHeartEntity::new).enrage("rupture", "Ruptured")
                .trail(ParticleTypes.CRIMSON_SPORE, 2)
                .drop(ModItems.KRAKEN_SCALE, 12, 20, 1f).drop(Items.HEART_OF_THE_SEA, 1, 2, 1f).drop(Items.NETHER_STAR, 1, 1, 1f)
                .drop(Items.NETHERITE_INGOT, 1, 2, 1f).drop(ModItems.PIRATE_COIN, 48, 64, 1f).egg(0x7a222c, 0xffd070));

        // The Titan's Chest cast (all spawned by the Heart or its lair, never naturally):
        // the rival's eye in the north wall - invulnerable, only watches and reacts (~56 px x 3.4 = ~12 blocks)
        def(MobSpec.of("rival_eye", 5).kind(STATIONARY).temper(PASSIVE).noMelee().stats(500, 0, 0, 0).size(9.0f, 11.0f, 3.4f, 0f).xp(0)
                .knockbackRes(1).entity(RivalEyeEntity::new).egg(0x1a3a4a, 0x7affff));
        // a node in each chamber - only yields on the beat
        def(MobSpec.of("heart_node", 5).kind(STATIONARY).temper(PASSIVE).noMelee().stats(70, 0, 0, 4).size(1.6f, 2.2f, 1.0f, 0.8f).xp(20)
                .knockbackRes(1).entity(HeartNodeEntity::new).trail(ParticleTypes.CRIMSON_SPORE, 3).egg(0x5a0a14, 0x6aff7a));
        def(MobSpec.of("blood_clot", 5).stats(18, 4, 0.28, 2).size(0.9f, 0.8f, 1.0f, 0.5f).xp(4).onHit(StatusEffects.SLOWNESS, 60, 1)
                .entity(BloodClotEntity::new).drop(Items.REDSTONE, 0, 2, 0.6f).egg(0x6a0a10, 0x2a0406));
        // rides the blood ring's current
        def(MobSpec.of("embolism", 5).kind(STATIONARY).noMelee().stats(24, 7, 0, 0).size(1.4f, 1.2f, 1.0f, 0.6f).xp(3).knockbackRes(1)
                .entity(EmbolismEntity::new).egg(0x4a0408, 0xa82028));
        // the Triple Beat's hallucination - only its victim can see it, it does no harm
        def(MobSpec.of("heart_phantasm", 5).kind(FLY).stats(1, 0, 0.28, 0).size(0.7f, 1.9f, 1.0f, 0f).xp(0)
                .entity(HeartPhantasmEntity::new).egg(0x2a1a3a, 0xb07aff));
        // the last of the ancients - rises from the split floor when the Heart dies
        def(MobSpec.of("drowned_keeper", 5).kind(FLY).temper(PASSIVE).noMelee().stats(40, 0, 0, 0).size(0.7f, 1.9f, 1.0f, 0f).xp(0)
                .entity(DrownedKeeperEntity::new).egg(0x1e4a4a, 0x9ff4ff));

        // BOSS CHAIN 10/10 - THE LEVIATHAN, the finale. Bespoke class LeviathanEntity (the HEAD; its body is 12 segments + a tail,
        // LeviathanSegmentEntity, ~78 blocks). One per world, hunted across three lairs (world/leviathan/LeviathanHunt).
        // Max health caps at 1024 (vanilla), so tough(0.75) makes it ~1365 effective across all three phases.
        // Head model ~48 px x renderScale 3.0 = ~9 blocks; hitbox 6 x 5.
        def(MobSpec.of("leviathan", 5).kind(SWIM).stats(1024, 20, 0, 12).size(6.0f, 5.0f, 3.0f, 0f).xp(2000).knockbackRes(1).follow(96)
                .boss(BossBar.Color.PURPLE, 0).tough(0.75f).noMelee().entity(LeviathanEntity::new)
                .drop(ModItems.LEVIATHAN_SCALE, 8, 14, 1f).drop(ModItems.KRAKEN_FANG, 1, 1, 1f).drop(ModItems.STORMCALLER, 1, 1, 0.5f)
                .drop(ModItems.KRAKEN_SCALE, 16, 24, 1f).drop(ModItems.PIRATE_COIN, 64, 96, 1f).drop(Items.NETHER_STAR, 1, 2, 1f)
                .drop(Items.NETHERITE_INGOT, 2, 4, 1f).egg(0x12181e, 0xff2a2a));
    }
}
