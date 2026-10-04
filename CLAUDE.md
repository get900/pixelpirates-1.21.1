# PIXEL PIRATES — CLAUDE.md
> Always-loaded briefing: project facts, global rules, crash causes, and an INDEX of topic docs.
> **Before working in an area, read its topic doc from the index below** - the detail lives there, not here.
> Older handoff notes: `HANDOFF.md`, `OVERNIGHT_PROGRESS.md`. The full pre-split file: `docs/archive/CLAUDE_full_2026-10-02.md`.

---

## PROJECT OVERVIEW

| Property | Value |
|---|---|
| Mod Name | Pixel Pirates |
| Mod ID | `pixelpirates` |
| Maven Group | `net.get900.pixelpirates` |
| Minecraft | **1.20.1** |
| Mod Loader | **Fabric** |
| Yarn Mappings | 1.20.1+build.10 |
| Fabric Loader | 0.16.14 |
| Fabric API | 0.92.6+1.20.1 |
| GeckoLib | 4.4.9 |
| Valkyrien Skies | 2.4.0 |
| Java | 17 |

**Build Commands:**
```
./gradlew build          # compile and package
./gradlew runClient      # launch game client
./gradlew runDatagen     # regenerate data files into src/main/generated/
```
Data generation output lands in `src/main/generated/` — source-controlled, never hand-edit.

---

## ENTRYPOINTS

| Entrypoint | Class |
|---|---|
| Main | `PixelPirates` |
| Client | `PixelPiratesClient` |
| Datagen | `PixelPiratesDataGenerator` |
| TerraBlender | `world.biome.ModTerrablenderAPI` |

---

## PACKAGE STRUCTURE

All source under `src/main/java/net/get900/pixelpirates/` (the main areas - not exhaustive):

```
block/ item/          blocks + items (item/custom, item/food/PirateFoods, item/relic, item/BossArmor)
entity/               ModEntities; custom/ (shark, ships' crew, captain, crabs...), client/ (renderers)
entity/mob/           data-driven roster: MobSpecs, ModMob, ModBoss, Abilities, BossProgression, every boss entity
world/                ships (AiShipController, ShipSpawner, ShipRegistryState...), PirateLevelingSystem, SkillEffects,
                      PirateXp, MobDamageScale, faction/, biome/
world/gen/            PortCityLayout + SpawnIslandTerrain + SpawnIslandFeature (spawn island), ModFeatures, spawns
world/dungeon/        Dungeons, DungeonPlacement, DungeonBuilder, BossLairs, every lair + GallowsGrotto/TitansChest layouts
world/leviathan/      the Leviathan hunt: route, state, sites, ports
homestead/            own registries; tavern/, chapel/, harbour/, trophy/, hoard/, datagen/
client/ client/screen/ client-only code and screens
datagen/              providers; datagen/biome = PixelPiratesBiomes + VanillaBiomeExtras
command/ network/ mixin/ sound/ util/ enchantment/
```
Asset generators live in `tools/` (Python; mob models `tools/mobs/`, armor `tools/armor/`, previews `tools/previews/`).

---

## CONVENTIONS
- All registries follow `Mod<Type>.java`, called from `PixelPirates.onInitialize()`
- GeckoLib: entity extends `GeoEntity`, model extends `GeoModel`, renderer extends `GeoEntityRenderer`; register renderer in `PixelPiratesClient`
- New mobs: equip starting items in `initialize()` override (not in constructor or external code only) so equipment persists through reloads
- Datagen output to `src/main/generated/` — always run `runDatagen` after adding blocks/items
- **Fabric mod — never use NeoForge/Forge APIs**

---

## VISION
Sea of Thieves–inspired mod. Ring-based progression world centred on 0,0. Players sail ships, fight sea battles, explore, trade, and engage in pirate gameplay. Five radial zones (ring 1 = starter seas, ring 5 = abyss); unlocked by boss kills/advancements. Custom BiomeSource assigns biomes by distance. Ocean dominates between island clusters.

**Ring zones:** 1 Starter (0–1000), 2 Merchant (1–2.5k), 3 Pirate (2.5–4.5k), 4 Cursed (4.5–7k), scattered Volcanic.


---

## TOPIC DOCS - read the matching one(s) before you start

| Working on... | Read |
|---|---|
| Spawn island / Wavebreak Port, PortCityLayout, buildings, `/ppisland` (incl. capture of hand edits), `/ppmarket`, the build checker | `docs/spawn-island.md` |
| VS2 ships, helm/mast/cannon blocks, blueprints, AI ships + crew, harbour dues | `docs/ships.md` |
| Mob roster (MobSpecs/ModMob/ModBoss), boss chain basics, relics, dungeons + placement | `docs/mobs-and-dungeons.md` |
| Which structure spawns where: every dungeon + big lair with phase, site, biomes, spacing, editable or not | `docs/structures.md` |
| Bosses 1-4 (Rackham, Serpent, Warlord, Ghost Captain) + lairs | `docs/bosses-early.md` |
| Bosses 5-8 (Abyssal King, Bloodfin, Kraken, Chained Revenant) + lairs | `docs/bosses-mid.md` |
| Bosses 9-10 (Abyssal Heart, Leviathan) + Titan's Chest, `world/leviathan/` | `docs/bosses-late.md` |
| Armor (boss sets, 3D GeckoLib armor), materials ladder, treasure block, THE FORGE (forging/mending, forged weapons) | `docs/gear.md` |
| Relic weapons, galley/food, advancements, XP, skills, traders, bazaar, currencies, loot, tabs, strongboxes, ship's cats, ships in bottles, tattoos | `docs/gameplay-systems.md` |
| `homestead/` (tavern games + drinks, chapel bells + organ, Chronicle) | `docs/homestead.md` |
| Parrots: 15 types + rarities, abilities, the aviary's daily stock, Parrot Roost (collection), Parrot Crate, Perch Branch | `docs/parrot_ideas.md` (plan + per-phase log) |
| Structures authored as layout JSON (ChatGPT packages), `LayoutStructures`, in-game editing with `/ppstruct`, the brief to give ChatGPT | `docs/structure-layouts.md` |
| What existed before 2026-08 / old status log (historical, partly outdated) | `docs/history.md` |

Several source folders also have a short `CLAUDE.md` that points at their doc (loaded automatically when you open a file there).
**Keeping docs current:** new feature notes go in the topic doc for that area, not here. Only add to this file for a
rule that applies everywhere (a crash cause, a convention, a spawning/GeckoLib/VS2 gotcha). New area = new `docs/*.md` +
a row in this table.

**Current state (2026-10-03):** all 10 chain bosses overhauled; economy/materials/skills reworked; the spawn island is
being overhauled building by building with the user (done #3-#20, ALL townhouses #21-#41 done, one character each - see `docs/spawn-island.md`; hand edits saved in game with `/ppisland capture`).
2026-10-04 session (compiled + server-tested, NOT seen in a client; uncommitted): townhouses #29-#41, #42 THE NORTH WALL + GREAT NORTH GATE (+ the way up to the hills), #43 THE WAVEBREAK LIGHT (beacon beam), #44 THE GOVERNOR'S FORTRESS (star fort; its fort cannons fire on players with Iron Armada rep < 0), #45 THE WRECK OF THE MERRY WREN (easter egg: Captain Wren's music box, homestead/wreck/), THE FLEET (12 new faction ships + AI, zone ladders - `docs/ships.md`), SHIP LIVERIES (26 skins, the Shipwright's Livery tab, `world/livery/`), SHIP'S CATS (+ the Cattery Counter)
(`homestead/cat/`) + SHIPS IN BOTTLES + TATTOOS (cosmetic, the chair at #33) (`docs/gameplay-systems.md`), Tidewater Shrine + Smuggler's Grotto converted to layout
JSON (`/ppstruct export`, `docs/structure-layouts.md`), `docs/structures.md` (what spawns where).
2026-10-03 session (ALL compiled, NONE seen in a client yet - the user tests in game):
- island hand edits committed (8cb918f) - **the push to GitHub is still the user's to do** (needs their sign-in);
  everything below is UNCOMMITTED in the working tree.
- Inn: INN_SIGN block (2 placed). Fence/wall/pane/bar CONNECTIONS: `world/gen/Connections` + `PortCityLayout.connectAll()`
  + the checker's CONNECT rule (`docs/spawn-island.md` build rule 9).
- PARROTS phases 1-4 (`docs/parrot_ideas.md`): 15 types, glow, abilities (Ghost = possession), daily aviary restock with
  rarity, Parrot Crate in treasure, Parrot Roost collection screen, Perch Branch. Open: 4.3 keeper sells birds (waits for
  the unique NPCs - see memory "planned-unique-npcs"); the in-game test lists in that doc.
- ALL vanilla container GUIs repainted parchment/wood (`tools/gen_container_textures.py`, `docs/gameplay-systems.md`);
  the Roost screen skinned like our GUIs (`gen_gui_textures.py roost()`).
- TREASURE BLOCK: 3D heap model + glint + fireworks on opening (`docs/gear.md`).
Older work is verified on a dedicated server but NOT in a client; each doc says what is unverified.

---

## SPAWNING — CRITICAL RULES

**`MobEntity::canMobSpawn` is only correct for `SpawnGroup.MONSTER` entities.** It internally calls `isSpawnDark()`. The MONSTER cycle pre-selects dark positions so this always passes; the CREATURE cycle picks lit positions where `isSpawnDark()` always returns false — every CREATURE spawn attempt silently fails. Use `SpawnGroup.MONSTER` for any hostile or disguised mob that should spawn via the natural mob cycle. Use `SpawnGroup.CREATURE` only with a custom predicate that does not check light.

**A spawn entry must be listed under the entity's OWN spawn group** (fixed 2026-09-29). The cap counts mobs by
their type's group, so salmon/cod (WATER_AMBIENT) and glow squid (UNDERGROUND_WATER_CREATURE) listed under
WATER_CREATURE were never counted and spawned without limit (thousands of salmon in island_thickets, glow squid in
maw_depths). `PixelPiratesBiomes.spawn(...)` now derives the group from `entry.type` - never call
`spawns.spawn(SpawnGroup.X, ...)` directly. Same rule for `BiomeModifications.addSpawn`: group == type's group.

**Shark `canSpawn` must not use `world.getSeaLevel()`** as a Y-floor. Custom terrain noise can produce ocean floors above `seaLevel-2`, permanently blocking every spawn. Check only that both `pos` and `pos.up()` are water fluid.

**Monkey (and crab) over-spawning:** If an entity is the only mob in its `SpawnGroup.MONSTER` pool for a biome, it fills 100% of the 70-mob monster cap. Keep group size at 1 and restrict to the smallest appropriate biome set.

**Biome/feature JSONs: `src/main/generated/` is the single source of truth** (2026-07-14: the stale duplicates in `src/main/resources/data/pixelpirates/worldgen/{biome,configured_feature,placed_feature}` were deleted — they were old skeletons missing the atmosphere/features that provably work in-game; `noise_settings` and dimension files remain in resources). Regenerate with `runDatagen` after touching `PixelPiratesBiomes`/`ModConfiguredFeatures`/`ModPlacedFeatures`. `BiomeModifications.addSpawn()` still adds spawns at runtime. Always verify changes in fresh chunks (existing chunks don't re-populate). `ModWorldGeneration` no longer re-adds shorewood/ashen trees at runtime — trees are baked only.

**Current spawn assignments (`ModEntitySpawns`):**
| Entity | Group | Biomes | Weight | Group size |
|--------|-------|--------|--------|------------|
| Shark | WATER_CREATURE | OPEN_OCEAN, TEMPERATE_SHALLOWS | 8 | 1–3 |
| CursedMonkey | MONSTER | ISLAND_THICKETS | 4 | 1–1 |
| ChestCrab | MONSTER | TEMPERATE_SHALLOWS, ISLAND_THICKETS | 5 | 1–2 |
| LavaCrab | MONSTER | ASH_REEF, BOILING_BASIN, MAGMA_SEA | 12 | 1–2 |
| Castaway | CREATURE | ISLAND_THICKETS | 2 | 1–1 |
| Siren | MONSTER (IN_WATER) | SIREN_SEA (6), REEF_EDGE (2) | 6/2 | 1–1 |
| CoralJelly | WATER_AMBIENT | CORAL_BAY, REEF_EDGE, SIREN_SEA | 10 | 2–4 |
| MagmaBrute | MONSTER | ASH_REEF, BOILING_BASIN, MAGMA_SEA | 4 | 1–1 |
| Mimic | MONSTER | PHANTOM_WAKE, SHIPGRAVE_DEPTHS, DROWNED_TRENCH | 3 | 1–1 |
| AbyssalAngler | MONSTER (IN_WATER) | ABYSSAL_RINGS, PILLAR_SEA, MAW_DEPTHS | 5 | 1–1 |

**Castaway is the mod's only CREATURE-group natural spawn** — it therefore uses its own
`CastawayEntity::canSpawn` (solid block below + light > 8), never `MobEntity::canMobSpawn`.
Copy that pattern for any future non-hostile mob; see the SPAWNING rules above for why.

---

## KEY TECHNICAL DECISIONS
- **GeckoLib 4.4.9 conventions (read from its source 2026-09-28 — the old preview script had Y/Z backwards):**
  X is mirrored at bake time, so the character's RIGHT hand is at **-X in the geo file**. Rotations (bone,
  cube, keyframe) become radians(-x), radians(-y), radians(z) applied Rz·Ry·Rx in that mirrored space:
  +X pitches the front (-Z) down (arm swings forward with −X), **+Y turns the front toward the character's
  right, +Z rolls the bottom toward the character's right** (right arm flares out with +Z, left with −Z).
  Keyframe positions translate (−px, py, pz). **Keyframe rotations are ADDED to the bone's rest rotation** —
  put curls/splays on the bone and animate offsets only, or the rest pose doubles in game. Box-UV faces:
  up/down textures have the model's BACK at their top row. `tools/preview_geo.py` implements all of this.
- **Serpent swimming = travelling wave around a STRAIGHT spine** (`serpent_anims` in tools/mobs/p2.py): each segment
  bends amp_i·sin(q − k·i) relative to its parent, amp growing toward the tail. Never give a swimmer a rest coil —
  the wave then oscillates around the coil and the tail only swings to one side (the 2026-09-29 bug). (The Leviathan
  no longer uses this - since 2026-09-30 it is a head + 13 follow-the-leader segment entities, see boss 10.)
- **Water mobs must override `canSpawn(WorldView)`.** `MobEntity`'s default rejects any bounding box that
  contains fluid, so natural spawns AND spawners of every swimmer silently failed. `ModMob` (SWIM /
  STATIONARY kinds) and `AquaticHostileEntity` now return `world.doesNotIntersectEntities(this)` like
  vanilla `WaterCreatureEntity`.
- **Non-fish swimmers need their own `travel()`.** Vanilla `LivingEntity.travel` gives a plain
  `PathAwareEntity` a fixed 0.02 acceleration against 0.8 water drag (~0.3 blocks/s) — the shark "didn't
  swim". `SharkEntity.travel` now accelerates along the (normalised) move-control input × speed × 0.15 with
  0.9 drag and no gravity; `swim(Vec3d)` only sets velocity (travel does the single move). The move control
  splits thrust into forward/upward by pitch so it actually climbs/dives. Don't feed a sub-1 speed attribute
  through `updateVelocity` un-normalised: the input is already speed-scaled, so it squares.
- **"Is it a monster?" - never test `instanceof HostileEntity` alone.** Our whole roster (`ModMob`, bosses too) extends
  `PathAwareEntity`, so that check silently skips every mod monster (found 2026-10-03 in the parrot abilities). Use
  `m instanceof Monster || m.getType().getSpawnGroup() == SpawnGroup.MONSTER` (see `ParrotAbilities.isMonster`).
- **Harmless fireworks:** `ClientWorld.addFireworkParticle(x,y,z,0,0,0, nbt{Explosions:[...]})` from a client packet gives
  the full burst + sounds with no rocket - real `FireworkRocketEntity` explosions damage everything within 5 blocks
  (`client/TreasureFx`).
- **Mob equipment:** Always set in `entity.initialize()` — this is called once on first spawn and the item is then NBT-persisted. Setting equipment only from external spawn code (e.g. `AiShipController.spawnCrew`) means reloaded entities lose the item.
- **VS2 race condition:** Wrap `move()` in `try { } catch (NullPointerException ignored) { }` for any entity near ships. Applied in `FloatingBarrelEntity` and `CannonBallEntity`.
- **Unicode in player strings:** Do NOT use Unicode symbols (⚓ ☠ etc.) in `Text.literal()` — renders as squares. Use ASCII: `[~]` anchor, `[X]` skull, `*` star.
- **PowerShell JSON files:** Never write `.geo.json`/`.animation.json` with PowerShell default encoding — UTF-8 BOM crashes GeckoLib. Always use `[System.IO.File]::WriteAllBytes()` or strip BOM. Verify first byte is `7B` not `EF`.
- **GeckoLib geo format:** `format_version` MUST be exactly `"1.12.0"` — GeckoLib 4.4.9 rejects every other value outright, and Blockbench exports `"1.16.0"` by default. **Check this first on every new/re-exported `.geo.json`**; it is a one-token fix but it fails the whole resource reload (see crash cause #4). Per-cube `pivot`/`rotation` and zero-size cubes are fine and need no rework. Remove any `binding` fields. No negative UVs.
- **GeckoLib renderer texture:** Let the `GeoModel` return the texture via `getTextureResource()`. Overriding `getTextureLocation()` in the renderer silently bypasses the model's texture path.
- **GeckoLib animation names must match the JSON key EXACTLY.** `RawAnimation.begin().thenLoop("x")` looks up the literal key under `"animations"` in the `.animation.json`. Blockbench sometimes exports bare keys (`"swim"`) and sometimes prefixed ones (`"animation.shark.swim"`) — open the file and copy what is actually there. A mismatch is not a crash: the mob renders frozen in its bind pose and the log prints `Unable to find animation: <name> for <Entity>`. This silently broke the shark (`animation.shark.idle_animation` vs key `idle_animation`) and still affects MapMerchant (`animation.map_merchant.walk` — the file is a 52-byte stub with no such animation).
- **GeckoLib entity render scale:** use `withScale(float)` in the renderer constructor, not `poseStack.scale()` in an overridden `render()`. A large scale factor (the shark had 10×) is a symptom of a model authored at the wrong size — fix the model, not the renderer.
- **Zero-size cubes ARE fine in GeckoLib 4.4.9** (verified in-game 2026-08-05): `"size": [1, 1, 0]` is the standard Bedrock idiom for flat planes (teeth, fins) and bakes without error. Ignore the older warning in crash cause #4 below for this case; that entry's real triggers are `binding` fields, per-cube 1.16.0-format `pivot`/`rotation`, and negative UVs.
- **Shark water confinement:** `SharkAttackGoal` and `OrbitRaftGoal` steer the shark by hand with `setVelocity` + `move(MovementType.SELF, …)`, bypassing `SwimNavigation` entirely — so no amount of pathfinding config keeps it in water. **All goal-driven shark movement must go through `SharkEntity.swim(Vec3d)`**, which refuses to self-propel out of water, per-axis-clamps against a 1.25-block look-ahead water probe (so it slides along a shoreline instead of stalling), and carries the VS2 `move()` NPE guard. `enforceWaterConfinement()` in `tick()` is the backstop: a beached shark stops navigating, flops toward the nearest water, and dries out after 100 ticks (grace period long enough that a breach never triggers it). The breach requires `isSubmergedInWater()` so it cannot launch off a shallow shore. Never add a new shark goal that calls `move()` directly.
- **Model vs hitbox:** `EntityDimensions` is independent of the rendered model. `withScale()` in the renderer does NOT move the hitbox — every scale change needs a matching `.dimensions(...)` edit in `ModEntities`, and the two comments should cross-reference each other. A model rescaled without updating `.dimensions(...)` in `ModEntities` leaves an invisible mismatched hitbox — the shark rendered multiple blocks long against a `0.3×0.2` box, so melee mostly whiffed.
- **GeckoLib item in entity hand:** `ItemArmorGeoLayer` + `rightItem`/`leftItem` bones in geo file. Works with standard items. GeoItem (e.g. Cutlass) held by another GeoEntity may have render-state conflicts — test in-game if items appear invisible.
- **Biome system:** Custom `PixelPiratesBiomeSource` — use `BiomeModifications` (Fabric API) to add spawns/features; selectors work via registry key regardless of biome source type.
- **VS2 assembleToShip:** Three-layer defence — AABB clearance 250 XZ, per-block pre-scan (VS2 mixin returns virtual blocks → abort on non-air), `IllegalStateException` catch. Use `catch (Exception)` NOT `catch (Throwable)`.
- **VS2 getShipManagingBlock:** Checks by chunk, not block — always returns null for world chunks. Use `ValkyrienSkies.getShipsIntersecting(world, aabb)`.
- **1.20.1 Screen API:** `render(DrawContext, int, int, float)`, `renderBackground(context)`, `context.fill(...)`, `context.drawCenteredTextWithShadow(...)`.
- **1.20.1 PersistentState:** `getOrCreate(readFn, factory, key)` — no `PersistentState.Type<>`.
- **1.20.1 Networking:** `ServerPlayNetworking` / `ClientPlayNetworking` with `Identifier` — pre-payload API.
- **Client-only guard:** Never reference `MinecraftClient` or `net.minecraft.client.*` outside `@Environment(EnvType.CLIENT)` — crashes server with `NoClassDefFoundError`.
- **1.20.1 worn-armor textures:** Vanilla resolves them as `minecraft:textures/models/armor/<ArmorMaterial.getName()>_layer_N.png` — the file MUST live under `assets/minecraft/textures/models/armor/`, not the mod namespace (pirate set = `pirate_armor_layer_N`). **All 7 sets are drawn by `tools/gen_armor_layers.py`** (2026-09-29 rework: per-face pixel art on the vanilla 64x32 layout - trims, buttons, belts, face openings; writes `tools/armor_sheet.png`). The old `gen_gear_textures.ps1` was deleted - it overwrote these files.
- **Texture generation (overhauled 2026-09-28):** `tools/gen_overhaul_textures.py` (`pip install pillow numpy`) owns **every item/block texture** except shipwright_table (`gen_shipwright_texture.ps1`) and the 64×64 shroud/volcanic/ethereal/pirate building sets (hand art, untouched). Three sections: (1) the hand-painted 1024px item masters in **`art_src/`** (NOT shipped — they were ~55 MB of the jar) are cropped, box-downscaled to **32×32**, palette-quantized and edge-darkened; (2) 16×16 char-grid icons through a `sprite()` engine that lights each region from the top-left and adds a dark outline (all weapons, armor icons, foods, maps, journal, token, amulets…); (3) tileable procedural 16×16 blocks (bark, single-ring log tops, **cutout** leaves, planks, stone) on wrap-around value noise. Writes `tools/overhaul_sheet.png` for review. `water_light_block_on` is an 8-frame animated strip + `.mcmeta`. worn armor layers come from `gen_armor_layers.py`; the old block/planks/food/gear PS scripts were deleted — don't resurrect them, they'd overwrite the overhaul.
- **Never put a 1024px image straight into `textures/`.** Minecraft stitches it into the atlas at full size (and non-square ones stretch). Put masters in `art_src/` and add the name to `ILLUSTRATED` in the generator.
- **resources/ vs generated/ ownership:** a file must live in exactly one. `processResources` uses `DuplicatesStrategy.EXCLUDE` with resources/ first, so a stale hand-written copy silently shadows datagen (the grog barrel, blueprint, repair kit and armor-trim models were all shadowed until 2026-09-28). Custom-shaped blocks (hammock, bedroll, helm, cannon, mast, banana/coconut) are hand-authored in resources/ and deliberately **excluded** from `ModModelProvider`.

---

## KNOWN CRASH CAUSES

### 1. VS2 Duplicate Key — `IllegalStateException: Collectors.toMap duplicate key`
> **Triage first:** if the duplicate key is a `Node{...}` (not a `BlockPos`), or the stack mentions
> `PathNodeNavigator` / `mobTick`, this is NOT the entry you want — go to **#1b**. That mistake cost
> real time on 2026-08-05.

VS2's `getBlockState` mixin makes ship blocks appear as virtual world blocks. `world.setBlockState()` into a virtual position triggers VS2 CORE's `onSetBlock` → `Collectors.toMap` without merge function → crash. **Three-layer fix in `ShipSpawner.spawn()`:** (1) AABB clearance 250 XZ, (2) per-block pre-scan aborts on non-air/fluid, (3) `IllegalStateException` catch cleans up placed blocks. AI ships spawn at Y=90, 1 per trigger. **Never `catch (Throwable)`** — use `catch (Exception)`.

### 1b. VS2 Pathfinding Duplicate Key — `IllegalStateException: Duplicate key Node{...}` (FIXED 2026-08-05)
**This is a DIFFERENT crash from #1 above and was the single most frequent server killer** — 11
crash reports between 2026-05-23 and 2026-08-05, every one of them `Ticking entity` →
`VillagerEntity.mobTick` → `FindPointOfInterestTask.findPathToPoi` → `PathNodeNavigator.findPathToAny`.
Do NOT confuse it with #1 (`ShipSpawner`/`setBlockState`); nothing in our block code is involved.

Cause: VS2 2.4.0's `MixinPathFinder.onCollectPath` wraps the `collect(...)` inside
`PathNodeNavigator.findPathToAny` and swaps in its own **two-argument
`Collectors.toMap(keyFn, Function.identity())` — no merge function**. Its key function maps each
candidate `BlockPos` to a path `Node`, translating shipyard-space blocks into world space. When a
real world block and a ship block land on the same node the collector throws. The giveaway is the
second value being an absurd shipyard coordinate (`BlockPos{x=-28665855, y=126, z=12290041}`).
Parking a ship near the port city villagers reproduces it.

**Fix:** `mixin/PathfindingCrashGuardMixin` — `@Redirect` on the single vanilla call site
(`EntityNavigation.findPathToAny(Set,IZIF)`) wrapping it in try/catch. Only swallows messages
starting `Duplicate key Node` (anything else is rethrown), returns `null`, and rate-limits a warn
to once a minute. Null is safe: vanilla already null-checks that exact call and treats it as
"no path found", so the mob skips pathing for one tick. The bug lives inside a mixin handler
injected into a vanilla class, so it cannot be patched directly — intercepting the caller is the
only reachable fix short of upgrading VS2.

### 2. VS2 Client Render Crash — `NullPointerException: $$17 is null`
VS2 2.4.0 vanilla render path NPE. **Fix:** Add Sodium 0.5.x + Indium 1.0.x for 1.20.1 — VS2 uses its Sodium module instead of the broken vanilla mixin.

### 3. GeckoLib `MalformedJsonException: line 1 column 1`
UTF-8 BOM in `.geo.json` or `.animation.json` (PowerShell default encoding). GeckoLib GSON rejects at byte 1, entire resource pack fails, all text renders as squares. **Fix:** `[System.IO.File]::WriteAllBytes($path, $bytes[3..($bytes.Length-1)])` to strip BOM. Verify first byte is `7B`.

### 4. GeckoLib `Error loading model file`
**By far the most common cause: `format_version` is not exactly `"1.12.0"`.** GeckoLib 4.4.9's
`GeckoLibCache.loadModels` hard-validates the string and throws
`IllegalArgumentException: Unsupported geometry json version. Supported versions: 1.12.0`.
Blockbench exports **1.16.0** by default, so every fresh export must be downgraded (the castaway
hit this 2026-08-05). Note the enum `FormatVersion` also declares `V_1_14_0`, but the cache
rejects it anyway — 1.12.0 is the only value that works.

**Per-cube `pivot`/`rotation` are FINE** (verified 2026-08-05, castaway head has 10 rotated
cubes): the `Cube` record parses both and `BakedModelFactory$Builtin` bakes them. Only the
version string had to change. Other genuine triggers: `binding` fields (Bedrock Molang) and
negative UVs. Zero-dimension cubes are also fine (see the technical-decisions note).

**A failed geo parse fails the ENTIRE resource reload**, dropping the client into recovery mode —
so the visible crash often lands in an unrelated mod (Create's `OpenCreateMenuButton` NPE'd on a
null `BakedModel` at the title screen). Always grep the log for `GeckoLibException` before
believing the stack trace's mod.

### 5. VS2 Physics Thread NPE in `move()` / `super.tick()`
VS2 physics thread and server thread race on fastutil `LongAVLTreeSet`. **Fix:** `try { this.move(...); } catch (NullPointerException ignored) { }`. Applied in `FloatingBarrelEntity`, `CannonBallEntity`.

### 7. Client exits `-1073741819` (0xC0000005) at launch — Java 21 vs LWJGL 3.3.1
`runClient` dies natively right after `[LWJGL] [ThreadLocalUtil] Unsupported JNI version detected` in `run/logs/latest.log`. MC 1.20.1 ships LWJGL 3.3.1, which doesn't support Java 20+ JNI; JAVA_HOME points at Adoptium JDK 21 (installed for 1.21.x work). **Fix:** Java 17 toolchain is pinned in build.gradle; if the crash recurs, add `org.gradle.java.home=C:/Program Files/Eclipse Adoptium/jdk-17.0.15.6-hotspot` to gradle.properties (then `gradlew --stop`). The warning is intermittent — a successful launch on 21 doesn't mean it's safe.

### 8. GeckoLib `IllegalStateException: Invalid glow layer texture provided, must have at least one pixel!` (FIXED 2026-09-28)
Client crash the first time a **dormant Mimic** rendered. `AutoGlowingGeoLayer` loads `<texture>_glowmask.png`
and GeckoLib 4.4.9 THROWS if that mask has zero lit pixels — the dormant-chest mask was deliberately blank.
It only surfaces on first render (not at resource reload, not on a server), so a clean startup log proves nothing.
**Fix:** `GlowingMobRenderer`'s glow layer skips rendering while `MimicEntity.isDormant()`; `gen_mob_assets.py`
now deletes (never writes) an empty glowmask. **Rule: never ship an all-transparent glowmask — skip the layer.**

### 9. `LambdaConversionException: ... LivingEntity is not a subtype of interface GeoAnimatable` (FIXED 2026-09-30)
Client resource reload fails while building entity renderers (the visible crash is again Create's title-screen button -
see #4, always read the log). Cause: a lambda whose parameter type is an intersection generic (`T extends LivingEntity &
GeoAnimatable`) passed to a generic functional interface - javac erases the lambda to one bound and the interface to the
other, and it only blows up when the lambda is linked at runtime. **Rule: callbacks used from `GlowingMobRenderer` factories
take concrete types (`GlowingMobRenderer.Pose` takes `LivingEntity`), or use an anonymous class.**

### 10. Server watchdog "A single server tick took 60.00 seconds" - blocks written inside CHUNK_LOAD (FIXED 2026-09-30)
`LeviathanPorts.onChunkLoad` rewrote a fallen port's chunk straight from `ServerChunkEvents.CHUNK_LOAD`; a block at the
chunk edge touched a neighbour chunk that was itself mid-load and the server thread parked forever inside `getChunk`
(stack: `LeviathanPorts.set <- onChunkLoad <- ...getChunk`). **Rule: never write blocks (or read other chunks) inside
CHUNK_LOAD - queue the chunk and do the work on the next server tick.**

### 6. Corrupt `world/level.dat` (~1110 bytes)
Caused by killing the server process mid-run. Symptom: `WorldGenSettings: No key dimensions in MapLike[{}]` on all subsequent starts. **Fix:** Delete `world/` and `server.properties`, restart — Minecraft recreates them cleanly.
