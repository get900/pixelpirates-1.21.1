package net.get900.pixelpirates.sound;

import net.get900.pixelpirates.PixelPirates;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.util.List;

public class ModSounds {

    public static final SoundEvent HALYARD_SONG        = register("music.halyard_song");
    public static final SoundEvent HAULED_BY_THE_TIDE  = register("music.hauled_by_the_tide");
    public static final SoundEvent HOLD_THE_LINE       = register("music.hold_the_line");
    public static final SoundEvent PIRATES_HOLLOW      = register("music.pirates_hollow");
    public static final SoundEvent STORMBOUND_HORIZON  = register("music.stormbound_horizon");
    public static final SoundEvent THE_KRAKENS_BATTLE  = register("music.the_krakens_battle");
    public static final SoundEvent THE_KRAKENS_CALL    = register("music.the_krakens_call");
    public static final SoundEvent THE_RISING_CRIME    = register("music.the_rising_crime");
    public static final SoundEvent THE_RISING_TIDE     = register("music.the_rising_tide");
    public static final SoundEvent THE_RISING_VIBE     = register("music.the_rising_vibe");

    // Mob sounds - synthesized by tools/gen_mob_sounds.py (ids must match its SOUNDS table)
    public static final SoundEvent SIREN_AMBIENT = register("entity.siren.ambient");
    public static final SoundEvent SIREN_SONG = register("entity.siren.song");
    public static final SoundEvent SIREN_HURT = register("entity.siren.hurt");
    public static final SoundEvent SIREN_DEATH = register("entity.siren.death");
    public static final SoundEvent SIREN_ATTACK = register("entity.siren.attack");
    public static final SoundEvent CORAL_JELLY_AMBIENT = register("entity.coral_jelly.ambient");
    public static final SoundEvent CORAL_JELLY_STING = register("entity.coral_jelly.sting");
    public static final SoundEvent CORAL_JELLY_HURT = register("entity.coral_jelly.hurt");
    public static final SoundEvent CORAL_JELLY_DEATH = register("entity.coral_jelly.death");
    public static final SoundEvent MAGMA_BRUTE_AMBIENT = register("entity.magma_brute.ambient");
    public static final SoundEvent MAGMA_BRUTE_HURT = register("entity.magma_brute.hurt");
    public static final SoundEvent MAGMA_BRUTE_DEATH = register("entity.magma_brute.death");
    public static final SoundEvent MAGMA_BRUTE_SLAM = register("entity.magma_brute.slam");
    public static final SoundEvent MAGMA_BRUTE_STEP = register("entity.magma_brute.step");
    public static final SoundEvent MAGMA_BRUTE_ROAR = register("entity.magma_brute.roar");
    public static final SoundEvent MIMIC_AMBIENT = register("entity.mimic.ambient");
    public static final SoundEvent MIMIC_AWAKEN = register("entity.mimic.awaken");
    public static final SoundEvent MIMIC_BITE = register("entity.mimic.bite");
    public static final SoundEvent MIMIC_HURT = register("entity.mimic.hurt");
    public static final SoundEvent MIMIC_DEATH = register("entity.mimic.death");
    public static final SoundEvent ANGLER_AMBIENT = register("entity.abyssal_angler.ambient");
    public static final SoundEvent ANGLER_LURE = register("entity.abyssal_angler.lure");
    public static final SoundEvent ANGLER_BITE = register("entity.abyssal_angler.bite");
    public static final SoundEvent ANGLER_HURT = register("entity.abyssal_angler.hurt");
    public static final SoundEvent ANGLER_DEATH = register("entity.abyssal_angler.death");

    // Sounds for the older custom mobs (same generator)
    public static final SoundEvent SHARK_AMBIENT = register("entity.shark.ambient");
    public static final SoundEvent SHARK_BITE = register("entity.shark.bite");
    public static final SoundEvent SHARK_HURT = register("entity.shark.hurt");
    public static final SoundEvent SHARK_DEATH = register("entity.shark.death");
    public static final SoundEvent SHARK_BREACH = register("entity.shark.breach");
    public static final SoundEvent CHEST_CRAB_AMBIENT = register("entity.chest_crab.ambient");
    public static final SoundEvent CHEST_CRAB_HURT = register("entity.chest_crab.hurt");
    public static final SoundEvent CHEST_CRAB_DEATH = register("entity.chest_crab.death");
    public static final SoundEvent CHEST_CRAB_STEP = register("entity.chest_crab.step");
    public static final SoundEvent CHEST_CRAB_HIDE = register("entity.chest_crab.hide");
    public static final SoundEvent CHEST_CRAB_COIN = register("entity.chest_crab.coin");
    public static final SoundEvent LAVA_CRAB_AMBIENT = register("entity.lava_crab.ambient");
    public static final SoundEvent LAVA_CRAB_HURT = register("entity.lava_crab.hurt");
    public static final SoundEvent LAVA_CRAB_DEATH = register("entity.lava_crab.death");
    public static final SoundEvent LAVA_CRAB_STEP = register("entity.lava_crab.step");
    public static final SoundEvent CASTAWAY_AMBIENT = register("entity.castaway.ambient");
    public static final SoundEvent CASTAWAY_HURT = register("entity.castaway.hurt");
    public static final SoundEvent CASTAWAY_DEATH = register("entity.castaway.death");
    public static final SoundEvent CASTAWAY_DRINK = register("entity.castaway.drink");
    public static final SoundEvent CASTAWAY_THROW = register("entity.castaway.throw");
    public static final SoundEvent CURSED_MONKEY_AMBIENT = register("entity.cursed_monkey.ambient");
    public static final SoundEvent CURSED_MONKEY_HURT = register("entity.cursed_monkey.hurt");
    public static final SoundEvent CURSED_MONKEY_DEATH = register("entity.cursed_monkey.death");
    public static final SoundEvent CURSED_MONKEY_STEP = register("entity.cursed_monkey.step");
    public static final SoundEvent MAP_MERCHANT_AMBIENT = register("entity.map_merchant.ambient");
    public static final SoundEvent MAP_MERCHANT_TRADE = register("entity.map_merchant.trade");
    public static final SoundEvent MAP_MERCHANT_HURT = register("entity.map_merchant.hurt");
    public static final SoundEvent MAP_MERCHANT_DEATH = register("entity.map_merchant.death");
    public static final SoundEvent PIRATE_CREW_AMBIENT = register("entity.pirate_crew.ambient");
    public static final SoundEvent PIRATE_CREW_HURT = register("entity.pirate_crew.hurt");
    public static final SoundEvent PIRATE_CREW_DEATH = register("entity.pirate_crew.death");
    public static final SoundEvent SHIP_CAPTAIN_AMBIENT = register("entity.ship_captain.ambient");
    public static final SoundEvent SHIP_CAPTAIN_HURT = register("entity.ship_captain.hurt");
    public static final SoundEvent SHIP_CAPTAIN_DEATH = register("entity.ship_captain.death");

    public static final List<SoundEvent> SHIP_TRACKS = List.of(
        HALYARD_SONG, HAULED_BY_THE_TIDE, HOLD_THE_LINE, PIRATES_HOLLOW,
        STORMBOUND_HORIZON, THE_KRAKENS_BATTLE, THE_KRAKENS_CALL,
        THE_RISING_CRIME, THE_RISING_TIDE, THE_RISING_VIBE
    );

    private static SoundEvent register(String name) {
        Identifier id = PixelPirates.id(name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void register() {
        PixelPirates.LOGGER.info("[ModSounds] Registered {} ship music tracks", SHIP_TRACKS.size());
    }
}
