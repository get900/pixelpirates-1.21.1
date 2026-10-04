# Ships, AI ships and harbour dues

> Topic doc split out of CLAUDE.md on 2026-10-02. The root CLAUDE.md (always loaded) holds the global rules;
> this file holds the detail for its topic. Keep it current when you change this area.

**When:** Read before touching VS2 code, ship blocks, `AiShipController`, `ShipSpawner`, blueprints, `homestead/harbour/`.

## SHIP SYSTEM (Valkyrien Skies)
**Gun Crew upgrade (2026-10-01, Shipwright refit `gun_crew`, 2 levels, 8 gunpowder + 4 rope + 6 iron each):** loading one
`ship_cannon` on a ship also loads every EMPTY cannon facing the same way (= that broadside) - I: same deck (same y),
II: every deck. Each extra cannon takes one more of the same shot (ball/chain/grape) from the inventory, stops when out;
creative is free (`CannonBlock.loadBroadside`, ship bounds from VS2 `shipAABB`). Without the upgrade each cannon is loaded by hand.
**Building on ships (2026-10-01, Shipwright refit `carpentry` "Ship's Carpenter", 4 levels, 16 sticks + 4 rope + 4 iron each):**
survival players may place blocks on an assembled ship, even under sail, up to 3/8/16/32/64 blocks (`world/ShipBuilding.SLOTS`).
`mixin/ShipBuildLimitMixin` (BlockItem.place HEAD = refuse when full, RETURN = record) stores shipyard positions in
`ShipRegistryState` "custom_blocks"; PlayerBlockBreakEvents.AFTER frees a slot, other losses (cannon fire) are pruned as
air whenever the count is read. Blueprint blocks and creative players never count. Any player can build on any ship
(no owner check). NOT verified in a client.
**Keelbreaker (2026-10-01, Shipwright refit `keelbreaker`, 2 levels, 12 copper + 10 iron each) = `world/KeelBreaker`:** every 2 ticks a
moving ship (speed >= 0.6 m/s, or the helm pushing/turning while stuck; never anchored/sinking) turns seabed blocks that touch its
hull into water. I = tag `keel_soft` (sand, dirt, gravel, clay, mud, coral blocks, shell/silt/scorched sand, coral_rock), II adds
`keel_rock` (overworld stone, ores - ores drop their loot - sandstone, basalt, the phase boulders). NEVER LAND: a block is cut only
if y < sea level AND its column's OCEAN_FLOOR top is at or below the surface (islands, beaches, piers, reefs that break the surface
are untouched - you still run aground on real land), it has a collision box and no block entity, its chunk is loaded, and it is
not within 12 of a dungeon/grotto/chest/Leviathan site (PP dimension). "Touches the hull" is tested in SHIP space against the real
hull blocks (in it, under it, beside it, or up to 3 blocks ahead along the travel direction), so it cuts a hull-shaped channel.
Cap 64 blocks/pass, 6 break effects. VS2 has no getBlockState mixin serving ship blocks at world positions (checked in the 2.4.0
source - the ShipSpawner comment is a guess); world setBlockState just reports a terrain change to VS2 collision.
Shipwright refit rows are now 19 px (`ROW_Y0` 45) so 7 fit above Scuttle. Test: `/ppship upgrade <key> <level>` (op).
NOT verified in a client (VS2 physics does not run headless).
Ships assembled from real VS blocks. `ShipHelmBlock` assembles + steers; `ShipMastBlock` drives thrust. `ShipHealthState` (500 HP, 4 thresholds, structural damage). Ship sinks at 0 HP (roll torque + gravity). `ShipRegistryState` persists mast counts + AI cannon positions across reloads. 1-ship-per-player limit enforced at shipwright, helm, and derelict claim.

**Blueprint orientation (all ships):** helm = origin (0,0,0) on deck, BOW = +Z, stern -Z, +Y up; thrust pushes ship-space
+Z. Saving/assembly flood-fill from the helm, so every block must be face-connected to it. Speed = count of
`ship_mast` blocks vs mass; the first `ship_waterline` block sits at sea level. Figureheads must face `south` on a +Z bow.
**Bundled blueprints (2026-10-01):** `src/main/resources/data/pixelpirates/ships/*.nbt` (sloop, skipper, brigantine +
the four faction ships) are copied into `config/pixelpirates/ships/` on SERVER_STARTED if missing
(`ShipSchematic.installBundled`, never overwrites). Faction ships come from **`python tools/gen_ship_blueprints.py`**
(kit: `hull()` round-bilge shell, `mast`, `square_sail`, `lateen`, `sprit`, connectivity + size check, previews in
`tools/previews/ships/`; **BALANCE:** every ship prints its centre of mass using VS2's own block masses
(`Ship.WEIGHT`, from data/valkyrienskies/vs_mass in the VS2 jar) and `trim()` adds hidden bilge ballast on the light side
until |x| < 0.01 - a heavier side makes a VS2 ship list and pull sideways. The hand-built sloop/skipper/brigantine
measure x 0.000/0.000/+0.004. All-angle photos: `build/tmp/claude/make_shipangles.py <CUE> [ships]` + `shoot_ships.ps1`,
sheets saved as `tools/previews/ships/<name>_angles.png`): **merchantman** (merchants, zone 1), **corsair_xebec** (pirates, zone 2, also in the pirate AI
size pool), **navy_frigate** (navy, zone 4, 14 guns), **drowned_hulk** (undead, zone 4). AI defaults incl. faction in
`AiShipConfig.generateDefaults`; tiers in `ShipTiers`. `/ppship place <name>` stamps a blueprint UNASSEMBLED 12 blocks
east of you (no rotation) to inspect/edit/re-save. Client-verified: all four spawn via `/ppai spawn`, float level, crew
board, sail bow-first (`build/tmp/claude/make_shipphoto.py` + `shoot_ships.ps1`). Not verified: player-steered feel, balance.


## THE FLEET (2026-10-04) - three more ships per faction, `tools/gen_fleet_ships.py`
Built with the same kit as gen_ship_blueprints.py (it imports it) plus shared pieces: `castle()` (deckhouses/sterncastles
that follow the hull), `gaff()` (fore-and-aft sail aft of a mast), `square_rig()`, `jib`/`bow()` (the jib now stands on
the sprit column by column so it stays connected), `gun_deck()` + `guns()` (cannons in the hull side at the hull's own
width for that height), `paint()` bands, `patchwork()`, `ensign()`/`flag()`, `waterline()`. Every faction now sails a
ladder of four hulls; sheet `tools/previews/ships/fleet_sheet.png`, one sheet per ship beside it.

| Faction | Small | | Middle | Flagship |
|---|---|---|---|---|
| Pirates | **pirate_cutter** "Bilge Rat" (326 blocks, patched hull, black gaff + Jolly Roger) | corsair_xebec | **pirate_brig** "Blackheart" (1351, 2 masts black square sails, kraken bow, 4 guns a side) | **pirate_galleon** "Dread Galleon" (2578, 2-storey gilded stern, gun deck 6 a side + 2, crimson/black sails, mizzen lateen) |
| Merchants | **merchant_lugger** "Herring Lass" (445, striped lug sails, crates/pots/nets, 1 gun a side) | merchantman | **merchant_fluyt** "Silver Herring" (1340, painted stern, cargo hatches, striped sails) | **merchant_indiaman** "Emerald Empress" (2246, ochre gun band, 4 a side below, emblem sails) |
| Navy | **navy_cutter** "HMS Swift" (429, gaff + topsail, fastest in zone 1) | **navy_corvette** "HMS Vigilant" (1176, 4 a side, spanker mizzen) | navy_frigate | **navy_man_o_war** "HMS Sovereign" (2948, TWO gun decks 6+6 a side + 2, three tiers of sail) |
| Undead | **undead_wraith** (306, translucent ghostwood, spectral sails, soul lanterns) | **undead_bone_galley** (613, bone-ribbed oared galley, tattered black lateen) | drowned_hulk | **undead_phantom_galleon** (2264, ghostwood over dark prismarine, tattered spectral square sails, weed) |

- WHERE THEY SAIL: `AiShipConfig.minZone/maxZone` (new, -1 = the built-in `DEFAULT_ZONES` table by blueprint name, so old
  JSON files pick it up). `PixelPirates.pickAiBlueprintForZone` now picks a ship of the faction whose range covers the zone,
  else the faction ship whose range is nearest, else anything (the old size lists are gone). Zones: cutters/lugger 1-2,
  sloop 1, skipper 1-2, merchantman 1-3, xebec/fluyt/corvette/brigantine 2-3, frigate 3-4, brig 3-4, wraith 3, bone galley
  3-4, drowned hulk 4-5, galleon 4+, indiaman 3+, man o' war + phantom galleon 5+. Faction odds per zone are unchanged.
- AI TUNING: `AiShipConfig.fleet(...)` writes each new ship's JSON on first start (speed, ranges, reload, accuracy, retreat,
  captain HP/damage/armour scale with the hull). Existing JSON files are never overwritten.
- PLAYERS: all 12 are in `ShipTiers` (now `Map.ofEntries`) - commission zones 1/1/1, 2/2, 3/3/3/3, 4, 5/5.
- `ShipSchematic.MAX_BLOCKS` 2048 -> 4096 (the flagships are 2.2-3k blocks; saving in game would have cut them off;
  assembly already allowed 50k). The generators refuse 4000+.
- The generators no longer write into `../server/config` (the user's own server) - only the bundle + run/config.
- Test photos: `build/tmp/claude/make_fleetphoto.py <CUE>` + `shoot_ships.ps1 <CUE>`.
- Verified in the dev client 2026-10-04: all 12 spawn with /ppai spawn, assemble, float upright, sail off and fight (cannon counts registered: cutters/wraith/bone galley 4, lugger 2, fluyt 4, brig/corvette/indiaman 8, phantom 10, galleon 16, man o war 28); photos tools/previews/ships/fleet_ingame_1/2.png. Crew on the small hulls (cutters, lugger, wraith, bone galley) were snapped back aboard a few times in the first seconds (keepCrewAboard). NOT verified: player-steered feel, natural zone spawning in each ring, capturing them.

## EDITING SHIPS BY HAND - `/ppship place | capture | remove` (2026-10-04, `world/ShipCapture`)
Like `/ppisland capture`: `/ppship place <name>` stamps the blueprint as plain blocks (helm 12 east of you, bow +Z - stand
HIGH up or over open water so the hull is clear of the ground) and remembers it; edit by hand (no assembling); then
`/ppship capture <name>` saves every block face-joined to the helm (inside the placed box +8; never air, fluids or natural
ground; waterlogged blocks saved dry; warns if there is no ship_waterline) as blueprint <name> - a new name makes a new ship.
`/ppship remove <name>` clears the placed blocks away again. The save goes to config/pixelpirates/ships AND (dev workspace)
src/main/resources/data/pixelpirates/ships + `captured.txt` there; tools/gen_ship_blueprints.py + gen_fleet_ships.py SKIP
captured ships unless run with `--force`. `ShipSchematic.installBundled` now UPDATES installed blueprints when the mod brings
a new version (config/pixelpirates/ships/.bundled holds the installed hashes; the old file is kept as .nbt.bak; a copy you
changed locally is kept). New ship names default to zone 1 in ShipTiers and have no AI config until one is added.
Verified in the dev client: place brigantine -> capture = identical 818 blocks; blocks joined to the helm are picked up,
floating ones are not; the bundle copy + captured.txt are written. `remove` and the .bundled update path NOT client-tested.
NOTE: the bundled ships folder src/main/resources/data/pixelpirates/ships/ is not in git yet - commit it.

## SHIP LIVERIES (2026-10-04) - `world/livery/`, the Shipwright's LIVERY tab
A livery repaints the player's own ship (the nearest within 150 blocks, as for refits) by MATERIAL ROLE, keeping every
block's shape (facing/half/axis/fence sides copied - `Liveries.reshape`), wood swapped for wood so the weight barely moves.
- ROLES (`Liveries.classify`): HULL (the ship's most common planks) / DECK (other planks), HULL_STAIRS (wooden stairs),
  DECK_SLAB, RAIL (wooden fences), TRIM (logs/stems/bamboo/bone block/ghostwood log), BAND (most common terracotta/concrete/
  nether brick/blackstone/prismarine...) / BAND2 (the others), GILT (gold/shroomlight/sea lantern), SAIL (most common wool/
  canvas/spectral) / SAIL2 (the others - so the merchantman's green stripes become the livery's two sail colours), EMBLEM
  (Jolly Roger canvas), LIGHT (lanterns). Helm, masts, cannons, anchor, map, chests, crates, figureheads... are never touched.
- FIRST REPAINT surveys the ship (its shipyard AABB) and `LiveryState` ("pixelpirates_liveries") stores each paintable
  block's position, role and ORIGINAL state; later repaints work from that record, so any livery -> any livery -> "Her own
  colours" round-trips exactly. A block that is no longer what the last paint left (shot away, rebuilt by a player) is skipped.
- 26 LIVERIES (`Livery.ALL`; the screen lists "Her own colours" first, 8 cards a page with hull/band/sail swatches from the
  blocks' map colours, a tooltip with the blurb and how to get it):
  - FACTION (60 doubloons + Friendly 300 with the faction): Crimson Corsairs, Emerald Company, Iron Armada.
  - BUY: Royal Regalia (150), Volcanic Ember (90), Tropical Paradise (70), Midnight Raider (80), Cherry Blossom (70),
    Frostbite (70), Desert Sun (60), Coral Reef (70), Jade Dragon (90), Bumblebee (60), Storm Petrel (60).
  - SPECIAL, earned only (free to wear once earned): Drowned Fleet (beat the Ghost Captain), Rackham's Red, Serpent Scale,
    Molten Forge, Tide King, Bloodfin, Kraken's Ink, Bone Reaper (Chained Revenant), Heart of the Abyss, Leviathan (one per
    chain boss, via BossProgression), Great White (kill 20 sharks - vanilla kill stat), Parrot Plumage (8 parrot types in the Roost).
- Bought liveries are kept for good (`HomesteadState` discovery "livery:<id>"); switching to one you own/earned costs
  `SWITCH_FEE` 5 doubloons; Haggler applies. C2S `Liveries.APPLY` (shipId, id | "original"); the menu packet carries the
  ship's current livery + each livery's status (0 locked / 1 buyable / 2 yours).
- Op commands: `/pplivery check` (every livery block id exists), `/pplivery spawn <blueprint>` (an owned, non-AI test ship),
  `/pplivery apply <id|original>` (your ship, free), `/pplivery near <id|original>` (the nearest ship, any).
- Verified in the dev client 2026-10-04: brigantine through 12 liveries and back, merchantman through 6 and back - all
  repaint, stay afloat, restore exactly (tools/previews/ships/livery_ingame.png). Hulls without paint bands (the hand-built
  sloop/skipper/brigantine) only change wood + sails, so faction liveries read more subtly on them. NOT verified: the
  Livery tab itself (buying, the locks, paging) - only the repaint was driven by command; AI ships keep their own colours.


## AI SHIPS
**Crew stay aboard (2026-09-29):** `ShipPatrolGoal` tethers to a WORLD point, so crew walked off moving ships. Each
crew member's post is stored in SHIP space (`AiShipData.crewPosts`); `keepCrewAboard` re-aims the tether every tick
(patrol home, or `setPositionTarget(post, 4)` for villager/vindicator/drowned crews) and only snaps someone back as a last
resort: in water / 3.5 below the deck / >11 blocks off post (14 while fighting) - each snap is logged `[AI] crew ... put back`.
**Fixed 2026-10-01 (the crew "bouncing"):** posts used to come from a scan straight down from above the centre of mass,
which hit the top of a SAIL - crew fell to the deck and were teleported back up every tick. Posts now come from
`AiShipController.deckSpots` (solid-topped blocks with 2 free above, around the helm's deck level, the busiest level
preferred, 3+ apart). Client-verified on all four faction ships: 0 snaps while they sailed ~100 blocks, crew flush on
deck. Drowned crew wear a skeleton skull (no drop) so they don't burn in daylight.
Raft pirates: `MobSpec.floats()` - SWIM mob whose travel() eases its feet to the water surface, and it targets
players out of the water too.
Blueprint-driven. `AiShipController`: PATROL→APPROACH→BROADSIDE→RETREAT state machine. 4 factions (PIRATES/MERCHANTS/NAVY/UNDEAD), zone-weighted selection. Crew (PirateCrewEntity or faction vanilla mob) + Captain spawned 40 ticks after ship assembles. Killing captain triggers boarding conquest. AI ships despawn after 8 min without nearby player. `/ppai`, `/ppship`, `/ppfaction`, `/ppreputation`, `/scuttle`, `/ppzone` admin commands.


## HARBOUR DUES (2026-10-01) - `homestead/harbour/`

- `HarbourDues` (PersistentState "pixelpirates_harbour", ticked every 40 from PixelPirates): the HARBOUR = PP dimension
  x-150..175 z76..230. A PLAYER-OWNED ship (`ShipRegistryState.ownerOf`, new) that stands still (velocity < 0.6) in the
  harbour for GRACE = 2 min without a valid PERMIT gets its HELM CHAINED: `ModNetworking` HELM_STEER zeroes its inputs
  (`blockSteering`, actionbar nag every 5 s), the owner gets a chat notice. Moving / leaving the harbour stops the count.
  Every ship's FIRST sighting grants a 1-day permit (new players don't get chained at the shipwright's landing).
- Fee = commission price / 10 (ShipTiers), clamped 3..30 doubloons, for a 3-day permit (stacks). Paid at a DUES_LEDGER
  (use with doubloons; empty hand = your account) or a BERTH_BOLLARD (doubloons = pay; use = make your ship fast here if it
  lies within 18 blocks -> ANCHORED_SHIPS + berth recorded; sneak = cast off). A berthed, paid-up ship is repaired +8 hull
  every 10 s by the harbour crew. Creative pays nothing.
- Blocks (HomesteadBlocks, models/textures in tools/gen_tavern_assets.py): DUES_LEDGER, BERTH_BOLLARD, DUES_BOARD (decor),
  DOCK_SIGN. Test: `/ppharbour status` (every ship on the books) | `/ppharbour expire` (your ship loses its permit and
  its grace - the next pass chains it if it lies in the harbour).
- Verified: build, server boot, the watch ticks with no errors. NOT verified (VS2 ships need a client): chaining a real
  ship, paying it free, mooring/repairs.
