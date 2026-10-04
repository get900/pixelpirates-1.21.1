# Layout structures - data-built sites + in-game editing

> Topic doc (2026-10-02). Read before adding or changing a structure authored as a layout file (the ChatGPT/Codex
> packages), or touching `world/dungeon/LayoutStructures` / `StructureEditState` / `/ppstruct`.

## What a layout structure is

`src/main/resources/data/pixelpirates/layouts/<id>.json` - the whole design as data, read by `LayoutStructures`:

```json
{
  "name": "Castaway's Refuge",
  "origin": "Beach ground at y=0; water surface y=-1; sea toward +Z",
  "bounds": [-11, -5, -12, 11, 10, 12],
  "loot":  [ {"pos": [-1, 1, 5], "table": "pixelpirates:chests/castaways_refuge_supplies"} ],
  "mobs":  [ {"id": "pixelpirates:castaway", "pos": [-1, 1, 2]} ],
  "nbt":   [ {"pos": [1, 1, -10], "data": "{front_text:{...}}"} ],
  "node_nbt": [ {"pos": [0, 1, 5], "data": {"Kind": 0, "Role": -1, "DX": 0, "RewardTable": "..."}} ],
  "palette": [ "minecraft:air", "minecraft:sand", "minecraft:oak_stairs[facing=east,half=bottom,shape=straight,waterlogged=false]" ],
  "runs":  [ [x1, x2, y, z, paletteIndex], ... ]
}
```
- Design space: `bounds` within +-22 on x/z (DungeonBuilder.MAX_REACH - every rotation stays in the 3x3 chunks). The
  placement type decides what y=0 means: COAST/ISLET = beach at sea level + 1, SEABED = the seabed block, LAND = the ground.
- `runs` are written in order (a later run overwrites an earlier one). `minecraft:air` cells clear space; cells no run
  touches keep the natural terrain.
- Every palette entry is parsed once with BlockArgumentParser; a bad one throws at load (logged as `[Dungeon] <id> failed`
  in worldgen - the site is skipped, chunk generation survives).
- `loot` = a container already placed by the runs gets the table (its facing/waterlogging untouched). `mobs` = spawned
  persistent through `DungeonBuilder.spawnMob` (safe in worldgen). The beacon's older `"guards": [[x,y,z]]` = pirate crew.
- `node_nbt` = a block entity's WHOLE configuration (ints/strings) - the Phase 5 puzzle stones. Do not move puzzle stones
  while editing: their links are design-space offsets stored in node_nbt, which a save keeps as authored.
- Wide LAND / SEABED layouts can be listed in `DungeonPlacement.FLAT_LAND` / `FLAT_SEABED` (the +-16 ring must match the
  centre within 4 / 3 blocks).
- **Ground blending** (`ground()/sample()/blended()`, 2026-10-02 - the user found sites "spawned on a cube of sand"): a GROUND
  cell = soil (sand, gravel, coarse dirt, dirt, grass...) at y<=0, or soil/foundation rock (sandstone, stone, basalt,
  andesite, cobble...) below y=0; minecraft blocks only, nothing above y=0 (floors, paths, paving stay as designed). At build
  time each design column's natural surface block + the block 3 under it are sampled first. A covered ground cell where the
  island already has natural ground keeps the island's block; a gap gets the column's under-block; an open ground cell gets
  the column's surface block, or the local seabed block when water is above. Columns with nothing usable use the site's most
  common sample. Applies to natural sites, /ppdungeon and /ppstruct restamp alike; a save skips ground cells unless a player
  touched them. Verified: 80 sampled ground cells of two sites built on spawn-island grass held grass/dirt/stone, 0 sand.
- **Spawn rate:** a layout site's Dungeons.ALL spacing is its WANTED average spacing (the first 24 were doubled on
  2026-10-02; the 15-location expansion is authored at 80-92). DungeonPlacement searches layout sites on a grid of at most
  `LAYOUT_GRID` = 32 chunks and keeps a site in a cell with chance (grid/spacing)^2 (`grid()/keep()/tries()`): same
  density, but a fine grid keeps finding the rare spots. (Big cells with a few random tries found NO site for 7 of 39
  types on 2026-10-03, although `/ppdungeon scan <id> <r>` showed dozens of qualifying chunks near spawn.) A KEPT cell is
  then searched SYSTEMATICALLY - every 2nd chunk (per-cell lattice offset) - and one passing spot is picked at random
  (conflicts tested last), so rare ground is never missed; random tries also failed at the fine grid. The original
  dungeons keep grid = spacing, keep = 1 and 16 random tries - their sites never move. `/ppdungeon scan <id> <r>` (op)
  prints sampled / in biome / site test / clear and, for FLAT_LAND types, the ring-height histogram; it runs on the server
  thread: keep r <= 100 (r 150 over several types stalled a test server). `layout()` is cached - an uncached file lookup
  there (it runs for every feature placement via FeatureExclusionMixin) stalled spawn generation.
- **FLAT_LAND tolerance** (`flatTolerance`): the +-16 ring may differ from the centre by 8 blocks on Phase 3 (rough volcanic
  ground) and 6 elsewhere - at 4, a scan found 5 qualifying volcanic chunks (49 at 8). Scarce-biome types also got a
  second biome (emberfall_cistern + brimstone_railhead: ash_reef; coral_conservatory: reef_edge) and turtleback_orchard
  48 spacing + temperate_shallows. Verified 2026-10-03: across throwaway worlds every one of the 39 layout types located
  and generated (loot, mobs, puzzle masters); the last three re-checked in two worlds.
- **Loot rule** for layout sites: each site's MAIN (hidden/guarded) container's table ends with a pool of one roll of its
  zone's dungeon table (`chests/phase1_smuggler`, `phase2_common` ... `phase5_common`, as a `minecraft:loot_table` entry);
  its other containers get the same pool behind a 0.3 random_chance. The puzzle seal tables count as main. Apply this to
  every new package (their own tables are only 2-3 fixed items). Verified with /loot spawn: main 6-9 items, others 3-10.
- Sand/gravel/concrete powder on the layout's BOTTOM layer is placed as sandstone/cobblestone/concrete (`placed()`), so a
  foundation over open water cannot collapse.
- Registering = ONE line in `Dungeons.ALL` (append only): `LayoutStructures.builder("id")`, or `LayoutStructures.seabed("id")`
  for SEABED designs (refuses a site shallower than the design's top + 2). Add a `Dungeons.PROTECTED` entry, run datagen.
  No Java class per structure (the three per-package classes were folded into LayoutStructures on 2026-10-02).

## Editing in game - `/ppstruct` (op)

1. `/ppdungeon <id> [rotation 0-3]` - builds a copy at your feet AND remembers it (id, place, rotation) for editing.
   (Reef/SEABED designs need water: stand on a deep seabed, or the build refuses.)
2. Change it by hand - break, place, rewrite signs, add chests.
3. `/ppstruct save [id]` - the nearest remembered copy (64 blocks) is read back into the layout, rotated back to design
   space. Saved to `<game dir>/pixelpirates/layouts/<id>.json` (overrides the shipped one for that game/server) and, when
   running from the dev workspace (`run/`), straight into `src/main/resources/.../layouts/<id>.json` - commit that and every
   new world gets it. Every NEW copy (natural or /ppdungeon) uses the saved layout; already-generated ones don't change.

What a save picks up:
- every cell the layout already owns, compared with the world (so `/fill` and `/setblock` there count too);
- cells outside the layout only if a player broke/placed/used a block there (or next to it) AND it no longer holds what it
  had before (`StructureEditState` records the before-state at the first touch);
- sign text, banner patterns, named blocks, filled non-loot containers (`IslandEdits.data`) -> the `nbt` list;
- a new EMPTY chest/barrel -> a loot container with the nearest existing loot table; a loot container you removed loses
  its marker. Mob markers are not changed by a save (edit the JSON for those).
- ignored: properties that change by themselves (open, powered, lit, age, distance...), flowing water, grass<->dirt, and
  sand/gravel that fell without you touching it.
- The capture reach is the layout's bounds + 4 sideways (max +-22) and up to 8 above its top.

Other commands: `/ppstruct restamp` (rebuild the nearest copy from its layout, no mobs - see the saved result or undo
unsaved edits), `/ppstruct list` (editable ids + remembered copies), `/ppstruct forget` (stop tracking the nearest copy),
`/ppstruct revert <id>` (delete the game-dir copy so the shipped layout applies; in the dev workspace also `git checkout` the
resources file).

### Converting a code-built dungeon - `/ppstruct export <id>` (2026-10-04)

`LayoutExport` runs a dungeon's old Java builder against a recording `DungeonBuilder` (no world) and writes every cell it
sets as a layout: loot chests/barrels -> `loot`, spawners -> `nbt` (`DungeonBuilder.spawnerNbt`), `spawnMob` -> `mobs`
(any `setup` callback is lost). The builder's random weathering is frozen into ONE variant (seed = id hash). Writes beyond
+-22 are dropped and reported. The file gets `"blend": false` - ground blending would turn the design's own cave floors and
shells into the island's grass. Then switch the `Dungeons.ALL` entry to `LayoutStructures.builder(id)` and delete the Java
class. Refuses ids that are already layouts. Not usable for designs that depend on the site (the sunken galleon reads the
water depth: fully sunken vs run aground) or that spawn bosses with setup (boss lairs).
- Converted: `tidewater_shrine` (3530 cells, 4 loot, 2 skeleton spawners) and `smugglers_grotto` (3940 cells, 3 loot,
  1 pirate-crew spawner; 15 outer shell cells at z=23 dropped, the cave is still sealed). Verified on the test server:
  built with /ppdungeon, loot tables + spawner mobs + the shrine's blast-rubble seal in place, an edit saves as 1 change.
  Not seen in a client.
- A spawner's settings now survive `/ppstruct save` (`IslandEdits.data` keeps everything but its `Delay` countdown).
- `"blend": false` works for any layout (default true).

Verified 2026-10-02 on a dedicated server: a copy built at rotation 1, three /setblock edits (a stair, a sign with text, an
empty chest), save = exactly 3 changes + 1 new loot container + 1 data entry; a second save = "nothing changed"; a fresh
copy at rotation 0 had the stair facing the right way, the sign text, and the chest with the nearest loot table; revert
removed the file. Player break/place tracking (cells outside the layout) is the same hook as the island tracker but was
NOT exercised by a real player in that test.

## Brief for ChatGPT (paste this when asking for new structures)

> Target: Pixel Pirates, Minecraft 1.20.1 Fabric. Deliver each structure as ONE layout JSON
> (`data/pixelpirates/layouts/<id>.json`) in exactly this format: `name`, `origin` (one sentence: what y=0 is and which way
> the sea/entrance faces), `bounds` [minX,minY,minZ,maxX,maxY,maxZ] with |x|,|z| <= 20, `loot` [{pos, table}], `mobs`
> [{id, pos}], `palette` (full block-state strings, vanilla or existing `pixelpirates:` blocks only), `runs`
> [[x1,x2,y,z,paletteIndex]]. Coordinates are design space; +Z is the "front" (sea for coastal sites). Put air runs where
> terrain must be cleared. Every loot `pos` must hold a chest or barrel from the runs. Use only existing mob ids. Also
> deliver the chest loot tables (`data/pixelpirates/loot_tables/chests/<id>_*.json`). No Java, no NBT templates needed.
> For each structure say: phase (1-5), placement type (COAST = beach at sea level+1 with sea at +Z; ISLET = small island
> at sea level+1; SEABED = y=0 is the seabed, give the minimum water depth; LAND = flat dry ground), biomes, suggested
> spacing in chunks, and the protected half-size. Avoid unsupported sand/gravel, keep doors/beds whole, and leave
> walkable headroom on every route.
