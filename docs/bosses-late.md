# Bosses 9-10: Abyssal Heart, the Leviathan

> Topic doc split out of CLAUDE.md on 2026-10-02. The root CLAUDE.md (always loaded) holds the global rules;
> this file holds the detail for its topic. Keep it current when you change this area.

**When:** Read with `docs/mobs-and-dungeons.md` before touching the Heart, TitansChestLayout or anything in `world/leviathan/`.

  - **9 Abyssal Heart / THE TITAN'S CHEST (2026-09-30, design doc `D:\Minecraft Modding\Abyssal Heart - Boss Design.txt`):**
    Story: the ancients cut out the heart of THALASSAR the Tide Father (the Leviathan's rival) and sealed it in a dead
    titan's chest; while it beats the Leviathan is too afraid to wake. `AbyssalHeartEntity` (STATIONARY, pinned, yaw -90,
    1024 HP, armor 10, tracked PHASE BEATING/CLOTTED/RUPTURED/FLATLINE + ARREST). THE RHYTHM: `beat()` every 40->30 ticks
    (enraged 28->20; 50 idle) flares the lair's FLESH_VEIN lamps (LIT), rolls a pressure ring, surges the blood ring's
    current; attacks are telegraphed on one beat and land on the next (`pending`, `resolve`). Pool: Systole->Diastole
    (pull, then blast), Vein Lash (lanes = the lair's artery segments), Embolism (`EmbolismEntity` rides the ring), Hemorrhage
    (blood fog: darkness+slow), Triple Beat
    (3 fast beats -> nausea + `HeartPhantasmEntity` visible ONLY to its victim). THE PACEMAKER: x0.15 damage until 4
    GALVANIC_PYLONs are struck in the beat window (STATE WINDOW = green, +-5/6 ticks; left click via AttackBlockCallback,
    right click onUse; a charge holds 16 s; off-beat = zap) -> CARDIAC ARREST 12 s x2, lamps dark, then a knockback restart.
    50% -> CLOTTED (invulnerable, no regen): after 2.5 s every fighter is teleported to the valve hub of the air-filled
    CHAMBERS under the floor; 5 valve doorways (HEART_VALVE) slam on every beat (crush 8); 4 `HeartNodeEntity` only take
    damage in `inBeatWindow()`; `BloodClotEntity` spawn (max 6); latecomers swim under the heart to be swallowed. All nodes
    -> RUPTURE: players ejected into the chest, `forceEnrage()` ("Ruptured"). 25% -> FLATLINE (x0.8): silence 5 s, the
    `RivalEyeEntity` (Eye of Thalassar, north wall behind bone bars, invulnerable, reacts: stir/open/blink/flinch/glare/
    charge/scream/dead; its iris follows each client's own player - `GlowingMobRenderer.rivalEye`) opens and SHOCKS the
    heart on an erratic rhythm: each shock = a beat + 0.5% heal + 6 dmg within 7, and 70% a DROWNED MEMORY: Rackham's
    broadside, Serpent's pressure jet, Kraken's harpoons (`KrakenHarpoonEntity.spit`), Revenant's quartering (vertical
    spokes), King's crushing depths, Ghost Captain's soul beam. Death: stone skin, a tuff/calcite statue built where it hung
    (`petrify`), the Eye screams then goes DEAD, `DrownedKeeperEntity` (the Last Keeper, ghost) carves a crack across the
    floor, rises and speaks 5 lines, fades. Credit range 96 (`ModBoss.creditRange()` hook). Co-op: 2+ fighters -> relics
    silenced (see Relics). Skins: enraged, arrest, clotted, flatline, stone; eye: dead. Models `tools/mobs/heart.py`
    (heart, rival_eye, heart_node, blood_clot, embolism, heart_phantasm, drowned_keeper; ghosts use `Rig.alpha` +
    `GlowingMobRenderer.ghost` = translucent and skipped for players they are invisible to - GeckoLib ignores invisibility).
    Sounds are vanilla overrides (warden heartbeat etc.), no synthesized set. Block textures + lang: `tools/gen_heart_assets.py`.
    LAIR = `TitansChestLayout` (pure Java, `main()` previews) rendered per chunk by `TitansChest` (every biome, after the
    grotto; site = the `abyssal_heart_lair` prediction, which now also needs `TitansChest.terrainOk` - no maw pit under the
    footprint; minDepth 1). Half-ellipsoid cavern r50x56, 56 tall, floor y-28, flooded: 11 bone ribs, sternum, spine,
    6 flesh arteries + aorta (lamps), heart seat valve, blood ring r27-32 sunk 4, 4 copper pylon columns r17 + broken ring
    walkway, the Eye socket
    (north), the ancients' HARPOON standing in the wound (east; its shaft breaks the surface as a landmark; the tunnel runs
    beside it), the RELIQUARY (south: `chests/heart_hoard`, phase5 chests, mural), the CHAMBERS (y-48: hub + 4 chambers of
    living flesh, weeping-vine chordae, blood dust, 5 valve passages). `/ppdungeon abyssal_heart_lair` builds it live.
    Test: `/ppboss heart status|arrest|clot|nodes|flatline|shock|triple|lash|hemorrhage|systole|embolism|memory <0-5>`
    (keeps it "fighting" 60 s with nobody there). Server check `build/tmp/claude/drive_heart.py <server dir>`.
    Round 2 (2026-09-30, user playtest - "that boss battle was epic"): the LUNGS and EXHALE are gone (everyone has water
    breathing by now); killing an EMBOLISM (by a player) wounds the heart for 2% max HP straight through its guard
    (`onEmbolismKilled`, `backflow` flag, embolism NBT Heart); in the FLATLINE the pressure ring only shoves on every
    second beat (`ringPushes`/`flatBeats`).
    No natural spawns anywhere inside the chest's built volume (`mixin/LairSpawnMixin` -> `TitansChest.insideVolume`); the
    Heart's own summons are MOB_SUMMONED and unaffected.
    **Verified on a server** (fresh world seed 424242, natural site 3928 ~ -5240): chest generated (pylons, lung air over
    water, chambers air, seat valve, flooded cavern), Heart + Eye spawned with lair wiring (69 lamps, 5 valves, 2 lungs),
    lamps flare on the beat, every attack starts + lands, exhale floods one lung and it drains again, embolism rides, arrest
    idles the pylons, clot -> 4 nodes + valves slamming -> nodes burst -> RUPTURED, flatline -> Eye OPEN + shocks + all 6
    memories, death -> stone statue, Last Keeper rises/speaks/fades, Eye DEAD; no exceptions. Idle regen (1%/s after 60 s
    unhurt) still applies in every phase but CLOTTED. NOT verified: anything against a real player (pylon timing feel,
    swallow/eject teleports, valve crush, phantasm visibility, relic silence, balance) and the in-game look (models, eye
    tracking, translucency, the chest's lighting).
  - **10 THE LEVIATHAN / THE HUNT (2026-09-30, design doc `D:\Minecraft Modding\Leviathan - Boss Design.txt`, reference
    `mobrenders\phase5\Leviathan.png`):** ONE per world, never respawns once slain. Hunted across THREE lairs with ONE
    health bar in thirds (100->66% Rift, 66->33% Gullet, 33->0 Spire); at each floor it RETREATS, swims the route in real
    time and DESTROYS the port in between. All in `world/leviathan/`:
    - `LeviathanRoute` - seeded bearing 155-195 deg: RIFT r5000, SALTMARROW r4150, GULLET r3050, BRIGHTWATER r2150,
      SPIRE r1850 (bearing +25). `nearAnySite` keeps other dungeons 96 away; DungeonPlacement's `leviathan_rift`
      candidate IS the route's rift (BossLairs.leviathanRift is a no-op). `LeviathanState` (PersistentState): stage
      SLEEPING/PHASE1/FLIGHT1/PHASE2/FLIGHT2/PHASE3/FLED/SLAIN, carried health, flight distance, ports ruined + done
      chunks, 4 chains, crust bits, Bane parts (Heartstone/Shaft/16 powder), horn timer, tide layers, channel seal, UUID, hunters.
    - Sites = pure-Java layouts on `SiteLayout` (grid kit, `ground`/`blend` terrain reconcile, `clearAbove` carves the
      Pillar Sea's pillars; every layout has a `main()` preview): `RiftLayout`, `GulletLayout`, `SpireLayout`,
      `SaltmarrowLayout`/`BrightwaterLayout` (intact AND ruined plans). One feature `LeviathanSites` in every biome
      renders per chunk (`built()` cache per seed). `LeviathanSites.check` = how much of a plan stands in the world.
      `mixin/LairSpawnMixin` -> `inLair` (no natural spawns in the 3 lairs; ports keep wildlife); FeatureExclusionMixin
      -> `insideVolume`. Gullet floor has NO magma (it made ~11.8k blocks of down-dragging bubble columns).
    - `LeviathanHunt` (server tick): keeps exactly one Leviathan in the current lair while a player is within 150 (waits
      80 ticks for the saved one to load); FLIGHT = a virtual position at 0.6 b/t along lair->port->lair, a
      `LeviathanWakeEntity` shows it to anyone within 180; the port on the way falls when it passes within 0.6 r.
      `LeviathanPorts`: loaded chunks are wrecked as a live wave (1500 blocks/tick, diff intact->ruined only);
      unloaded ones are QUEUED on CHUNK_LOAD and rewritten next tick (see crash cause #10). `restore` = the future
      rebuild. Every hunt broadcast is also logged (`[Leviathan] ...`).
    - WAKING: the RIFT_SEAL prompts (clickable `/ppleviathan awaken`, eligible player within 16) - "there is only ONE".
    - `LeviathanEntity` (ModBoss, the HEAD; 13 `LeviathanSegmentEntity` follow its path history, ~78 blocks, not saved,
      orphan-discard; hits on segments route to `hurtPart`). Fully scripted (noClip), steered inside the lair: soft wall
      + HARD floor/ceiling/wall on its heading (it drifted 50 blocks under the Spire, then 89 out through the rim); immune to in-wall/cramming/fall/
      drowning. Enters a lair clamped to that phase's third (`setup`). Retreat target stays INSIDE the lair (outside the
      loaded area it stopped ticking and never handed over). 1024 HP, armor 12, tough 0.75, renderScale 3.0; models
      `tools/mobs/leviathan.py` (head/body/tail x 3 forms + fin; head p1 "dark" skin), `entity/client/LeviathanRenderers`.
      FORM 1 THE WAKING (Rift): CRUST (x0.1 until blasted - explosions x3, projectiles x1.5), THE BINDINGS - 4 chains
      from the ANCHOR_WINCH pillars (winch +9%), its struggles strain the nearest; all snapped = early retreat; leash
      26+14/snapped. Yawn (wave ring), Coil Crush, Chain Lash (snapped chains), Rift Quake (dripstone `DebrisEntity`),
      Sleepwalk Ambush (lights out, erupts under you). FORM 2 THE HUNGER (Gullet, eclipse): hunger grows, it hunts the
      biggest meal - POWDER_BARGE (recipe GBG/GTG/SSS, `PowderBargeEntity`) swallowed = THE POISONED MEAL (5% direct,
      STUNNED x2.5 to the head), chum, boats, you; starving -> WEAK. Devouring Breach, Tendril Rake, Blood Lure, Undertow
      Gulp (spits debris), Blood Tide, Eclipse Hunt. FORM 3 THE UNMAKING (Spire): the arch collapses and SEALS the
      channel; 4 TIDE_BELLs ring (6 hits = silent 90 s, slows the tide; Tidal Call re-rings every 40 s) and THE LAST TIDE
      rises a layer every 12 s (x(1+0.6*silenced)); the whirlpool pulls swimmers; it REARS against the Spire - knock the
      BANE_SHAFT out of its crown (3 hits or a blast), load BANE_BALLISTA (Heartstone + shaft + 16 gunpowder), fire while
      reared -> `BaneBoltEntity` PINS it (x3). Apocalypse wave (shelter by the Spire), Maelstrom Spin, Wreck Rain, Storm
      Call, Drowned Memories (shared `DrownedMemories`). Tide at the crown (y99) = FAIL: it dives, heals to 33%, FLED;
      WATCHERS_HORN calls it back after 5 min. Death credits every hunter; relic CROWN_OF_THE_DROWNED; LEVIATHAN_SCALE drops.
    - Client: `client/LeviathanClient` (eclipse strength) + `BackgroundRendererMixin` (red eclipse fog, zone fog off near it).
    - Assets `tools/gen_leviathan_assets.py` (textures, shaped ballista/bell/winch/horn models, blockstates, loot, lang).
    - Test: `/ppleviathan status` (stage, sites, live state) | `build <rift|saltmarrow|gullet|brightwater|spire> [ruined]`
      | `do <op> [arg]`: wake, watch, summon (spawn in the current lair with no player near), stage <0-7>, flee, arrive (skips
      the swim, still ruins the port), ruin/restore <1|2>, tide <n>, drain, bane, bell <i>, horn, kill, reset,
      check <site>[.ruined], act <yawn|coil|lash|quake|sleepwalk|breach|rake|lure|gulp|bloodtide|poison|rear|spin|
      wreckrain|storm|apocalypse|memory|pin|shaft|retreat>. Server driver `build/tmp/claude/drive_leviathan.py <server>`
      (full hunt, ~17 min) and `drive_leviathan_p3.py` (Spire only).
    - **Verified on a server** (fresh world seed 424242): all 5 sites generate naturally and match their plans (Rift 99.6%,
      Gullet 99.9%, Spire 98.7% - the rest is vanilla ore blobs; Saltmarrow 100%); wake -> 13 segments -> P1 attacks,
      chain strain; retreat -> the real swim -> SALTMARROW FALLS live (51,866 blocks; ruined plan 100%) -> arrives at the
      Gullet on its own -> P2 at 66% (all attacks, poisoned meal stun) -> swim -> Brightwater falls while unloaded and
      rewrites itself when loaded (99.4%, no hang) -> Spire at 33%: channel seal, tide layers, bane pin, fail -> FLED ->
      horn -> back -> kill -> SLAIN -> reset restores both ports. NOT verified: anything against a real player (crust,
      winches, barge, bells, ballista aim, whirlpool feel, balance), the eclipse fog and models in a client, VS2 ships.
    - Round 2 (2026-09-30, user playtest - "that was really cool"): (1) SPIRE STAIR: every rise is a stone-brick stair
      block, 3 blocks of headroom, and a slot cut through the crown where it arrives (it used to dead-end under the
      platform); (2) the TIDE BELLS stand on balconies built out from the stair (~2 blocks from it, 4 heights) - they used
      to hang on the Spire's side at angles the stair never passed; (3) NO NATURAL SPAWNS in the three arenas (and the
      Titan's Chest): `mixin/ArenaSpawnMixin` on `SpawnHelper.canSpawn` (the spawn cycle) + `populateEntities` (chunk-gen
      fish/animals) - LairSpawnMixin's MobEntity#canSpawn hook is skipped by any mob that overrides it; (4) the RIFT SEAL
      (and `/ppleviathan awaken`) no longer lets creative players through - only a player who has beaten the Abyssal
      Heart (or `/pptest god`); (5) ALWAYS IN SIGHT: the retreating Leviathan is no longer swapped for a fin - the same
      entity goes into TRAVEL mode (`beginTravel`, LeviathanHunt drives it with `travelTo` along the route, invulnerable,
      never saved) and on arrival `arrive()` makes it the next lair's boss in its next form; with nobody within 256 of the
      route position it is dropped and respawned on the route when someone comes near. `LeviathanWakeEntity` is no longer
      spawned. Head tracking range 256 blocks, update every tick (was 160 / every 3); (6) the TAIL GAP: segments are
      spaced by the taper (`LeviathanSegmentEntity.sizeOf`, half length 2.625 x size, 5% overlap) - a fixed 5-block
      spacing left the shrunken rear segments and the tail floating loose. Test op `do watch` = treat the route as
      watched (headless servers). Driver `build/tmp/claude/drive_leviathan_r2.py`.
    - Round 3 (2026-09-30, user playtest): (1) CROWN OF THE DROWNED is wearable - `BossRelicItem implements Equipment`
      (HEAD for the crown, MAINHAND for the rest), right-click puts it on, 3D elements model (ring + 8 points) rendered by
      vanilla HeadFeatureRenderer; its powers work from any slot. (2) The head stared UP at players while travelling:
      vanilla LookControl runs after mobTick and overrode the scripted pitch - `getMaxLookPitchChange()`/
      `getMaxHeadRotation()` now return 0. (3) SPIRE CAMPING: the Apocalypse wave's "sheltered within 10 of the Spire"
      covered the whole top - it now breaks OVER the Spire (anyone within 16, any height); new act SPIRE_COIL (weight 5 in
      the pool while someone is perched = >10 above the water within 16 of the axis, forced after 6 s perched): position
      scripted up a 12-block helix to the crown in 3 s (red rings climb ahead), then 13 dmg + flung outward for everyone on
      the tower (`do act spirecoil`). (4) STRAY BLOCKS in structures: FeatureExclusionMixin now skips EVERY non-dungeon
      feature (ores/kelp/trees/boulders, any depth) with origin within +10 of a Leviathan site, +6 of the grotto/chest
      footprints and +6 of every dungeon's +-22 footprint (it only tested the origin cell, so things grew in from beside).
      Verified: fresh Spire 722098/722098 and Rift 327860/327860 plan blocks match (were ~98.7% / 99.6%). New chunks only.
      (5) POWDER BARGES: Gullet supplies always 2-3, every gullet wreck 1-2 (was a 2-in-48 weight) - edited in
      `tools/gen_leviathan_assets.py` too, which now also writes the Thalassar armor pool (`THALASSAR`).
