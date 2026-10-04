# Mob roster, boss chain overview, dungeons

> Topic doc split out of CLAUDE.md on 2026-10-02. The root CLAUDE.md (always loaded) holds the global rules;
> this file holds the detail for its topic. Keep it current when you change this area.

**When:** Read before touching `entity/mob/` (MobSpecs, ModMob, ModBoss, Abilities, BossProgression), `world/dungeon/`, mob models in `tools/mobs/`.
Per-boss detail: `docs/bosses-early.md` (1-4), `docs/bosses-mid.md` (5-8), `docs/bosses-late.md` (9-10).

## DUNGEONS (2026-09-28) — one set per zone, Phase 1 done

Code-built worldgen features in `world/dungeon/`, sharing **`DungeonBuilder`** (relative coords,
random 90° rotation with block states rotated to match, weathered-material picks, loot chests,
spawners). Features may only write in the 3x3 chunks around the decorated chunk, so each design
snaps its origin to the chunk centre and stays within |x|,|z| <= 22. Every `generate()` wraps
`build()` in try/catch — a bad block state logs `[Dungeon] … failed` and skips the site instead of
killing chunk generation (a `STRAIGHT_RAIL_SHAPE`-on-`RAIL` bug proved the point).
- **Smuggler's Grotto** (island land, one per 20x20-chunk cell): boulder + trapdoor hatch → spiral stair → timbered
  rail tunnel → sealed sea cave (pool, dock, rowboat, Pirate Crew spawner) → vault behind a barrel stack.
- **Tidewater Shrine** (island land, one per 22x22-chunk cell): ruined surface platform → stair → crypt; iron door opened by a
  lever in a secret room reached by breaking a cracked-brick panel (lever powers the block beside the door).
- **Sunken Galleon** (shallows + open ocean, one per 24x24-chunk cell): depth-adaptive — fully sunken when ≥13 deep, otherwise
  RUN AGROUND with the hull above the waves (Temperate Shallows is only 0–5 deep; the first version
  never spawned there). Breach entrance, drowned spawner, gun deck with mod cannons, captain's cabin.
- Loot: `data/pixelpirates/loot_tables/chests/phase1_*.json`. The galleon captain's chest now carries the
  Kraken Scale chance that guardians used to supply.
- Test: **`/ppdungeon <id>`** builds one at your feet (aliases grotto/galleon/shrine); **`/ppdungeon locate <id>`** finds the nearest site. Placement: see DungeonPlacement in MOB ROSTER & BOSS LAIRS.
- Feature order: dungeons live in SURFACE_STRUCTURES in `Dungeons.ALL` order.
- Next zones get their own classes in `world/dungeon/` reusing `DungeonBuilder`.

**Wreckers' Beacon (2026-10-02, authored by ChatGPT/Codex as an external package, integrated here)** - Phase 1 landmark
islet: lighthouse (exterior stair + ladders to a lantern deck), keeper's hut with a trapdoor cellar, pier, beached cutter,
3 chests (`chests/wreckers_beacon_supplies` x2, `chests/wreckers_beacon_cache`), 2 Pirate Crew guards, no spawner.
- The design is DATA, not Java: `resources/data/pixelpirates/layouts/wreckers_beacon.json` (palette of block-state
  strings + x-runs `[x1,x2,y,z,palette]`), played back by `LayoutStructures` (was WreckersBeacon.build). Bounds +-17, y -8..29, y=0 = beach,
  water at y<=-2, pier toward +Z. It clears a 35x35 box above the beach and lays a stone slab down to y-8.
- **New `Dungeons.Site.ISLET`**: origin forced to sea level + 1 (not the ground); `DungeonPlacement.isletOk` requires
  all 9 probes (centre + +-16 ring) to have ground in [sea-6, sea+3] and >=5 of the 8 rim probes wet, so it never
  floats over deep water, slices a hill or makes an inland water square; `seawardRotation` turns the pier to the
  deepest edge (deterministic, so `/ppdungeon locate` stays truthful). Biomes temperate_shallows + island_thickets,
  spacing 28, protected footprint 19.
- New props in `ModBlocks`: `wreckers_signal_lantern` (light 15) and `rope_bollard` (`block/custom/PropBlock`, hand
  models in resources/, NOT in ModModelProvider; textures `wreckers_brass`/`wreckers_light`/`weathered_rope` are hand
  art, not from gen_overhaul_textures). The package's own `salvage_crate` block was DROPPED - the id already belongs to
  the homestead Salvage Crate, which the layout now places (empty). Its blockstate/model/texture/loot/recipe were not copied.
- Verified 2026-10-02 on the dedicated server: `/ppdungeon wreckers_beacon`, locate (5 sites within ~2k of spawn), and two
  natural sites in fresh chunks (rotations NONE + 180, guards present, temperate_shallows). NOT yet checked in a client:
  look, prop models, stair/ladder walkability, cellar, waterline fit on real coasts.

**Eight more authored sites (2026-10-02, ChatGPT/Codex packages, same layout-JSON format as the beacon):**
- Phase 1 coastal (layout files, see docs/structure-layouts.md) - **`Site.COAST`**, biomes temperate_shallows + island_thickets: powderwatch_battery
  (spacing 30, protected 19; 2 pirate crew, 3 `ship_cannon`s - not fort cannons, a powder store below), castaways_refuge
  (24/14; a NEUTRAL castaway - not captive, the rescue dialogue belongs to Rackham's brig), pearl_divers_camp (26/17; no
  mobs, an underwater cache at (0,-4,11)), the_saltworks (30/19; 1 pirate crew, salt pans, a storehouse hatch at (0,0,-5)).
  Design y=0 = beach at sea+1, sea toward +Z, the land side at -Z.
- Phase 2 reef (layout files) - `Site.SEABED` with minDepth = tallest part + 2 (the builder throws below that):
  sunken_counting_house (coral_bay + reef_edge, 14 deep, spacing 28, protected 19; 2 void squid), sirens_bellcourt (siren_sea,
  15, 30/19; 1 siren), reefcutters_quarry (reef_edge + coral_bay, 12, 28/21; coral jelly + reefback), tideglass_observatory
  (coral_bay + siren_sea, 17, 32/20; void squid + reefback). `DungeonPlacement.FLAT_SEABED` = every +-16 ring probe within
  3 blocks of the centre's seabed (their 5-block foundation must not bridge a ravine).
- **`Site.COAST`** (`DungeonPlacement.coastRotation`, used by placement AND the feature, so locate is truthful): origin
  sea+1; tries the four rotations in order and takes the first where the centre line is a shore - dry land 12 behind
  (sea+1..sea+5), beach in the middle (sea-3..sea+5), water 14 in front (seabed sea-8..sea-1) - while the side probes
  (u=+-14) are only near sea level and at least one front side is wet. COAST cells try 48 chunks (others 16). The first,
  all-nine-probes-exact version passed 0.5% of Phase 1 chunks and found NO site within 12 cells; this one passes ~4%.
  **`/ppdungeon coastscan <r>`** (op) prints the pass counts + the land-height histogram for tuning.
- `Dungeons.PROTECTED` is now `Map.ofEntries` (`Map.of` stops at 10 pairs). The NBT templates + vanilla showcase datapacks
  in the packages were NOT copied; only the layouts and chest loot tables. All nine layout sites (beacon included) now go
  through `LayoutStructures` and can be hand-edited with `/ppstruct` - see docs/structure-layouts.md.
- Verified 2026-10-02 on a dedicated server (throwaway world): `/ppdungeon` builds of the four coastal sites; locate finds
  every type (coastal 600-1300 blocks from spawn, reef ~1500); all eight generated naturally in fresh chunks with their
  first loot container in place (found at the predicted rotation) and every authored mob present; 0 build failures, 0 bad
  palette entries. NOT seen in a client: looks, terrain blending at the land edge, cannons, the hatch/ladders, balance.

**The fifteen-location expansion (2026-10-03, ChatGPT; data-only + a puzzle patch):** P1 turtleback_orchard (LAND,
castaway), driftwood_ropewalk + gullwing_boathouse (COAST); P2 azure_mosaic_baths, pearl_courier_waystation,
coral_conservatory (SEABED); P3 scoria_switchback, cinderwake_caravansary, brimstone_railhead (LAND); P4 last_echo_theatre,
blackwake_auction_court (LAND), sundered_prison_barge (SEABED); P5 PUZZLES verdict_of_the_four (kind 5: logic riddle),
processional_orrery (kind 6: sort the procession, reward chest at (0,1,-4)), measured_depths_reservoir (kind 7: water
jugs). `AbyssPuzzleLogic` kinds 5-7 came as a patch (applied; kinds 0-4 unchanged, ChatGPT's old + new tests pass).
ChatGPT already gave their chests the zone-loot pools; spacings are its 80-92 except the changes listed in
docs/structure-layouts.md (FLAT_LAND tolerance section). Solutions: docs/abyss-puzzle-solutions.md.

**All 24 layout sites, later on 2026-10-02:** spacing doubled (user: far too common), ground blending into the local terrain
instead of their sand/stone slab, and zone loot added to their chests - details in docs/structure-layouts.md. The spacing
numbers quoted below and above are the AUTHORED ones; Dungeons.ALL holds double.

**Fifteen more layout sites, Phases 3-5 (2026-10-02, ChatGPT packages; all through LayoutStructures, all /ppstruct-editable):**
- Phase 3, LAND, `DungeonPlacement.FLAT_LAND` (the +-16 ring dry and within 4 of the centre - these are 35-39 wide):
  cinderchain_tollgate (ash_reef + magma_sea, 28/20; 2 fire pirates), sulfur_prospectors_camp (ash_reef + boiling_basin,
  26/19; 2 lava scorpions), ashglass_kilnworks (ash_reef + boiling_basin, 30/21; 2 fire pirates), basalt_signal_redoubt
  (ash_reef + magma_sea, 30/19; 2 fire pirates, 2 ship cannons), emberfall_cistern (ASH_REEF added to boiling_basin +
  magma_sea, 32/21; 1 obsidian golem - with only its two package biomes it found no site in 6 locate centres).
- Phase 4: widows_lantern_hospice (LAND, phantom_wake, 28/19), chainbreak_salvage_yard (LAND, shipgrave_depths +
  phantom_wake, 28/21), mourning_archive (LAND, 30/20; `mourning_archive_sealed` has a 12% Lost Soul), drowned_customs_house
  (SEABED 15, drowned_trench + shipgrave_depths, 30/20; ghost shark), keelbone_ossuary (SEABED 16, 32/21; ghost shark).
- Phase 5 PUZZLE sites, SEABED 16, FLAT_SEABED, no mobs: hushed_bell_court (abyssal_rings), lantern_confluence (maw_depths +
  pillar_sea), ferrymans_balance (abyssal_rings + maw_depths, 34), tidewheel_oracle (pillar_sea), mnemonic_reliquary
  (pillar_sea + abyssal_rings, 34); spacing 32 unless noted, protected 21. **ABYSS_PUZZLE_NODE** (`AbyssPuzzleNodes`,
  "Abyssal Inscription Stone", registered in onInitialize, drops nothing, pickaxe): the master at design (0,1,5) stores
  progress + Rewarded in its block entity; controls at y1/z0 (x = `AbyssPuzzleLogic.nodeX`) link to it by design deltas
  turned with their facing; solving puts the seal loot table into the EMPTY reward chest at (0,1,-8) once. Puzzle setup
  comes from the layout's `node_nbt` (LayoutStructures.applyNode). Solutions + edge cases: `docs/abyss-puzzle-solutions.md`.
  Changed from the package: the seal chests' 1-2 DIAMONDS -> 1-2 Luminous Ichor (no diamonds in this mod); the puzzle chat
  text used en dashes and filled/empty circles (squares in game) -> ASCII ("x"/"o"); its own builder class, pickaxe tag
  file and block loot file were not copied (LayoutStructures / the tag provider / dropsNothing cover them).
- Verified 2026-10-02 on a dedicated server (throwaway worlds): ChatGPT's puzzle-logic tests pass on the ASCII version;
  locate finds all 15; all 15 generated naturally in fresh chunks with their first loot container, every authored mob,
  and (puzzle sites) the master stone carrying its Role/Kind data. NOT verified: solving a puzzle as a player (onUse needs
  a real player), the reward release, looks, balance.

**Removed spawns (2026-09-28):** guardians, elder guardians and phantoms are gone from every biome (user
request). Kraken Scale now comes only from the Abyssal Angler (25%) and galleon captain chests.


## MOB ROSTER & BOSS LAIRS (2026-09-28) — the mob gameplay loop

**39 data-driven mobs** (from `D:\Minecraft Modding\mobrenders\phase1-5`) live in `entity/mob/`:
- `MobSpecs` — ONE `def(MobSpec.of(id, phase)...)` per mob: kind (GROUND/SWIM/FLY/STATIONARY), temper,
  stats, size+renderScale, traits (fireImmune, hurtByWater, lifesteal, onHit…), abilities, drops, natural
  spawns, `boss(color, unlocksZone)`, egg colours. **Adding a mob = one spec + assets; no new classes.**
- `ModMob` (PathAwareEntity+GeoEntity) reads its spec by registry path. Controllers `movement`
  (idle/move) + `action` (triggers `attack` and every ability anim). Sounds are
  `pixelpirates:entity.<id>.{ambient,hurt,death,attack,special}`.
- `/pptest god` = **unkillable, not invulnerable** (ALLOW_DEATH cancels death, health refills): never use the
  abilities `invulnerable` flag — `LivingEntity.canTarget` returns `canTakeDamage()`, so mobs would ignore you.
- `ModBoss` — boss bar, 40-block home leash (home = spawn spot, NBT), regen 1%/s **only after 60 s without taking damage** (`TicksSinceHurt`, NBT), **enrage at 50%**
  (phase-2 abilities, +30% speed), on death `PlayerProgressionManager.unlockZone` for players within 48.
- `Abilities` — reusable effects (slam, projectile via `MobProjectileEntity`, pull, dash, aura, summon,
  blink, heal, grab, cloud, beam, lightning, both). `ModMobs.register()` builds types, attributes, sound
  events, spawns (`BiomeModifications` + own `canSpawn`, never `isSpawnDark`), and `TYPES` for eggs/renderers.
- Client: `GlowingMobRenderer.of(id, renderScale, shadow)` for all; its glow layer is skipped for any mob
  with no `_glowmask.png` (so a mob with nothing emissive can never hit crash cause #8).
  Sounds from `tools/gen_mob_sounds.py` (ROSTER map id → family/pitch).
- **Models (rebuilt 2026-09-28 against the concept renders):** one hand-authored builder per mob in
  `tools/mobs/p1.py`..`p5.py` (siren + coral jelly are in p2), run by `tools/gen_mob_roster.py [ids]`, on the kit
  `tools/mobkit.py`: rotated cubes (`rot`/`pivot`), pixel-art faces `A(rows)` / `A.at(w,h,{(x,y):ch})` where
  `!color` glows and `_` is a cutout pixel, `bands`/`noise`/`counter`/`grad` materials, `over(...)` overlays
  (veins, spots, drips), `m.pair`/`m.mirror` for left-right parts (art flips too). Shared biped rig + clip set in
  `tools/mobs/common.py`. **Preview before booting the game:** `python tools/preview_geo.py <mob> out.png
  [anim:t] [yaw=] [pitch=]` is GeckoLib-exact (see the GeckoLib conventions bullet in KEY TECHNICAL DECISIONS).
  magma_brute / mimic / abyssal_angler still come from `gen_mob_assets.py` (it no longer emits siren/coral_jelly).

**BOSS CHAIN (2026-09-29) — `entity/mob/BossProgression.CHAIN` is the single source of truth.** Bosses are fought in
one fixed order per player (NBT `PPBossStep` = bosses beaten; old saves migrate from `PPUnlockedZone`):
1 captain_rackham (→zone 2) · 2 sea_serpent (→3) · 3 molten_warlord (→4) · 4 ghost_captain (→5) · 5 abyssal_king ·
6 bloodfin · 7 kraken · 8 chained_revenant · 9 abyssal_heart (opens the Leviathan's Rift) · 10 leviathan.
- **Sealed bosses:** a boss further along the chain ignores the player (`ModMob.mayTarget`), cancels their damage and
  shows an actionbar hint (`ModBoss`). `/pptest god` bypasses the seal. `/ppboss status|set <0-10>`.
- **Stats scale with chain position, not phase** (160 HP → 1024 HP). Vanilla caps max health at **1024**, so late bosses
  use `tough(x)` (damage-taken multiplier) for effective HP. `lightning(float)` sets bolt damage per boss.
- **Relics** (`item/custom/BossRelicItem`, fireproof, given straight into each credited player's inventory; re-kill
  replaces a lost one). Each counters the NEXT boss/region; right-click locates the target lair:
  diving charm (water breathing+dolphin) · tide pearl (= heat amulet, +50% vs `hurtByWater`) · everburning lantern
  (no darkness/blindness, undead glow) · spectral anchor (= sanity amulet, no mining fatigue, conduit) · royal tide
  sigil (immune to `Abilities.pull`, no slowness) · bloodfin razor tooth (`Abilities.grab` can't hold, +50% vs
  STATIONARY) · kraken's ink heart (no wither) · broken shackle (hit bosses can't heal 8 s, no poison) · abyssal
  heartstone (no weakness, resistance underwater). Hooks: `Abilities.pull/grab`, `ModMob.relicBonus`,
  `ModBoss.heal`, `ZoneHazardManager`. Icons in `gen_overhaul_textures.py` (needs `pip install pillow numpy`).

  - **Goal gotcha:** vanilla only calls `canStart` every OTHER tick (fixed parity per entity), so an `age % 10` gate can
    never line up for half the mobs - use an odd interval (the wardens never moved until this was fixed).
- **Worldgen entity gotcha (fixed 2026-09-29):** in natural generation the ProtoChunk serialises an entity THE MOMENT
  it is spawned, so changing it afterwards is silently lost (serpents spawned with 0 scales, castaways weren't captive).
  Configure through `DungeonBuilder.spawnMob(id, x, y, z, setup)` - `setup` runs before the spawn. `/ppdungeon` builds
  into a live world and hides this bug, so verify entity state on a NATURALLY generated site.
- **Natural decoration kept out of dungeons (2026-09-29):** `Dungeons.PROTECTED` (id -> half-size in blocks) opts a
  dungeon in; `mixin/FeatureExclusionMixin` cancels every non-dungeon `ConfiguredFeature.generate` whose origin is in a
  predicted site footprint (`DungeonPlacement.insideProtectedSite`, one cached grid lookup) and within 8 blocks of the
  surface - trees, boulders, flowers, kelp, from ANY chunk in any decoration order; ores deeper down are untouched.
  **Geodes (2026-09-30):** a geode grows ~12 blocks from its origin, so the origin test let one carve into the Gallows
  Grotto. `GeodeFeature` is now cancelled within 16 blocks of ANY dungeon site's +-22 footprint (`DungeonPlacement.nearAnySite`)
  or the grotto's / chest's whole bounding box (`nearFootprint`), at any depth. Only new chunks.
  Opted in: rackham_fort (26), serpent_trench (24), cinder_forge (24), ghost_ship (24), abyssal_throne (24), bloodfin_reef (24), kraken_maw (23). **Add each dungeon as it is overhauled.** Verified: 2 fresh forts
  had exactly their own 14 palm logs inside vs 35-98 in same-sized patches around them. Note the mod's tree features
  write `persistent=true` leaves, so count logs, not non-persistent leaves, when testing. Only affects NEW chunks.
  - Screenshots: `build/tmp/claude/make_fortphoto.py` datapack + `shoot.ps1` (unique PPSHOTx cue per run - old logs
    still contain earlier cues).
- **Progress survives death** (fixed 2026-09-29): `ServerPlayerEvents.COPY_FROM` copies `pp_writeProgression` →
  `pp_readProgression`. Before this, dying wiped zone unlocks, level/skills and faction rep.

**Dungeons** — `world/dungeon/Dungeons.ALL` is the registry AND the canonical SURFACE_STRUCTURES order
(append only, never reorder — feature-order-cycle rule). Each `Type(id, phase, LAND|SEABED, minDepth,
spacing, biomes, builder)`; every type (grotto/shrine/galleon included) is a generic `DungeonFeature`
registered by loops in `ModFeatures`/`ModConfiguredFeatures`/`ModPlacedFeatures` (placed feature = just a
heightmap modifier). Biomes pick them up via `Dungeons.inject(generation, placed, key)` at the top of each
`PixelPiratesBiomes` builder — do not hand-add dungeon features to biomes. Builders live in `BossLairs`
(design space y=0 = ground/seabed, |x|,|z| ≤ 22, flood to the real waterline with `depth`); the three Phase 1
classes now only hold static `build(...)` methods. Guards/bosses placed with `DungeonBuilder.spawnMob`
(persistent). Loot: `chests/phase1_*`, `phaseN_common`, `phaseN_treasure`.
- **Frequency = `DungeonPlacement` (2026-09-28, replaced 1-in-N rarity which spawned "way too often").** The
  world is a grid of `spacing`×`spacing` chunk cells per type; each cell holds AT MOST ONE site, chosen
  deterministically from the chunk generator's own predictions (biome at chunk centre + WG heightmaps; LAND
  = dry + flat, SEABED = water ≥ minDepth), up to 16 tries per cell, never within 4 chunks of an earlier type's
  site. Spacings: P1 dungeons 20–24 chunks, P1 bosses 36, P2–P4 bosses 40, P5 bosses 44–48, other P2–P5
  dungeons 24–28. Tune `spacing` in `Dungeons.ALL`. Because it's deterministic, **`/ppdungeon locate <id>`**
  finds the nearest site (ungenerated too); `/ppdungeon <id>` builds one at your feet.
- **Dungeon spawners** (`DungeonBuilder.spawner`) carry `custom_spawn_rules` (light 0–15), which replaces the
  mob's natural spawn predicate — vanilla monsters need block light 0, so lanterns or a sunlit deck (the
  run-aground galleon) silently stopped them. They still need a player within 16 and respect peaceful.
| Phase | Dungeons (boss) |
|---|---|
| 1 | smugglers_grotto, tidewater_shrine, sunken_galleon, rackham_fort (captain_rackham), bloodfin_reef (bloodfin), wreckers_beacon (islet), powderwatch_battery, castaways_refuge, pearl_divers_camp, the_saltworks (coast) |
| 2 | kraken_maw (kraken), serpent_trench (sea_serpent), coral_temple (void_squid spawner), sunken_counting_house, sirens_bellcourt, reefcutters_quarry, tideglass_observatory |
| 3 | cinder_forge (molten_warlord), obsidian_vault (obsidian_golem), fire_camp (fire_pirate spawner, caged castaway) |
| 4 | ghost_ship (ghost_captain), revenant_crypt (chained_revenant), drowned_graveyard (2 mimics among the chests) |
| 5 | abyssal_throne (abyssal_king), abyssal_heart_lair (abyssal_heart), leviathan_rift (leviathan), luminous_grotto (crystal_golem) |

**One biome per boss (2026-09-29):** rackham_fort island_thickets · bloodfin_reef open_ocean · kraken_maw coral_bay ·
serpent_trench siren_sea · cinder_forge (only P3 boss) all P3 · ghost_ship phantom_wake · revenant_crypt shipgrave_depths +
drowned_trench · abyssal_throne abyssal_rings · abyssal_heart_lair maw_depths · leviathan_rift pillar_sea. Keep new boss
lairs off these biomes. Verified with `/ppdungeon locate` + `execute if biome` at every hit.
**Kraken's Maw = `KrakensMaw` (enlarged 2026-09-29, same design):** bowl carved into the seabed r13 -> r20 (centre (0,-1,0),
ry 9, floor ~-10), an ABYSS shaft r5 dropping 16 more where the Kraken lurks (rises to the bowl floor), 6 arm burrows on
a ring r12 (cracked deepslate + ink stains), amber egg clutches (shroomlight) round the abyss mouth, bones, a ring of
sea lanterns, three wrecked hulls dragged over the rim (one snapped halfway down the slope), 3 chests. Protected 23.
**Bloodfin's Reef = `WhalersGrave` (rebuilt 2026-09-29):** atoll ring of jagged rock (r 16-21) breaking the surface with
north/south boat channels; the lagoon ("the Gut") dug into a bowl 10 deep for dives + breaches, fire coral and bones,
the victims' hoard (`chests/whalers_hoard`) at the bottom; 4 whalers' platforms (NE/NW/SE/SW) each with a HARPOON_WINCH
facing the lagoon and a `chests/whalers_armory` barrel (harpoons + chum); a whale skeleton across the lagoon (ribs out of
the water); a whaler's hull on the east rocks with a flensing rig and tryworks. Protected footprint 24.
**Abyssal Throne = `SunkenCourt` (2nd rebuild 2026-09-29)**: the palace (walled forecourt, gate towers + guardian statues,
domed corner towers, coral gardens, avenue, great hall, empty throne, treasury under the dais) is now the approach. In the
forecourt at (0,-13) the WELL OF TIDES (pillared well-head, 5x5 shaft, spiral steps round a lantern column) drops 30
blocks to a passage and THE COURT DOOR (a vanilla door: doors never pass water, so it holds the ocean back when the hall
is drained - the door plane sits exactly on the hall edge, outside the drain mask). THE KING'S COURT below the palace:
centre (0, CZ=6), radius 14, floor y-30, walls to y-20 + dome ry 9, 6 columns, dome ribs, 3-tier dais, throne, hoard
(`chests/court_hoard`), conduits, 4 sluices at (+-10, -28, 6+-10) with copper pipes and floor drain grates. Build order
palace -> court -> well (the court shell would wall up the passage). `SunkenCourt.inside` must equal
`AbyssalKingEntity.Court.inside`. Protected footprint 24. Depth charges in `chests/court_armory` (well barrels, hall chest).
`/ppdungeon <id> [rotation 0-3]` - the fixed rotation makes screenshot/test framing reproducible.
