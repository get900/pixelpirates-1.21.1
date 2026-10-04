# Spawn island - Wavebreak Port

> Topic doc split out of CLAUDE.md on 2026-10-02. The root CLAUDE.md (always loaded) holds the global rules;
> this file holds the detail for its topic. Keep it current when you change this area.

**When:** Read before touching `world/gen/PortCityLayout`, `SpawnIslandTerrain`, `SpawnIslandFeature`, `/ppisland`, `/ppmarket`.

## SPAWN ISLAND — "WAVEBREAK PORT" (rewritten 2026-07-15)

Large island (~370×340 blocks) at 0,0 in the `pixelpirates:pixel_pirates` dimension; the port
city covers the whole south half. Three cooperating pieces in `world/gen/`:

- **`SpawnIslandTerrain`** — pure-Java analytic island shape (superellipse + harbor bay mask +
  terraced city plateau + north hills with lighthouse/fort headland bumps). Single source of
  truth: called by `ZoneTerrainFunction` (density) AND by the layout, so terrain and buildings
  always agree. City terraces: harbor Y65 (z≥50), mid Y67 (z≥-10), upper Y70; sea level 63.
- **`PortCityLayout`** — pure-Java deterministic block plan (palette-indexed short[] grid,
  world coords X±165, Z −160..160, Y 48–118). Contents: quay+3 piers+cranes, shipwright yard
  (hall, slipway, ship-skeleton under construction; the **shipwright_table sits on the grand
  pier end platform** at (−2,66,148) so purchased ships spawn over open water — ship spawn
  origin is player pos + look×20 at Y75, so an indoor table spawns ships into land),
  market square = the Wavebreak Bazaar (8 keeper booths, grand fountain - see SKILLS, MERCHANTS + THE BAZAAR), tavern "The Grog Barrel", inn, chandlery, bakery,
  harbormaster, 2 warehouses, fish market, dock office, chapel, manor, guardhouse, smithy,
  park, 21 townhouses, city wall+gatehouse+towers, lighthouse, fort, north paths, palms.
  5 loot chests (vanilla loot tables). **Run its `main()` (compiles standalone, no MC) to
  render a top-down preview PNG — iterate visually before booting the game.**
- **`SpawnIslandFeature`** — thin per-chunk renderer. A feature may only write near its own
  chunk, so every chunk in the spawn_island biome copies its own 16×16 slice of the layout.
  Palette strings parsed once via `BlockArgumentParser`; bad entries log + fall back to stone.

**Interiors (2026-09-28):** `furnishBuildings()` + `furnishHouse()` (end of `house()`) furnish every building — rugs, dining sets, shelving, kitchens, fireplaces, bedrooms, themed townhouses (sailor/fisher/scholar/merchant/family). All furnishing goes through `put()`, which only writes into existing interior AIR outside `keepClear()` boxes — **register door approaches and stair runs with `keepClear` before furnishing a new building.** `main()` now also checks building footprints for overlaps (the guardhouse used to sit inside the chapel; moved to x-28..-10, z-38..-26) and has a floor-plan mode: `PortCityLayout slice <y> <x1> <z1> <x2> <z2> out.png` (reserved walkways tinted yellow).

Gotchas: authored leaves need `[persistent=true]`; layout palette may only reference blocks
that are actually REGISTERED (textures existing ≠ block exists — gunpowder_barrel bit us);
beds/doors that straddle a chunk border can pop off (keep them interior). Verification recipe:
run dedicated server, `execute in pixelpirates:pixel_pirates run forceload add <quadrants>`
(≤256 chunks each), then render region files with the pure-python mca renderer (scratchpad
`render_region.py` pattern). Shipwright textures: `tools/gen_shipwright_texture.ps1`.


## SPAWN ISLAND OVERHAUL (started 2026-10-01) - building by building with the user

- **Labelled map:** `PortCityLayout` tracks every building - `named("Name", fn)` wraps each building function (houses,
  warehouses, gatehouse label themselves) and `set()` grows that name's box. `PortCityLayout.buildings()` = name ->
  {x1,z1,x2,z2} in map-number order. Render: compile the layout standalone and run `PortCityLayout map out.png`
  (javac PortCityLayout.java + SpawnIslandTerrain.java; Java 17). Current map: `tools/previews/spawn/wavebreak_map.png`
  (45 entries; numbers change if a named entry is inserted - re-render after layout edits).
- **In-game:** `/ppisland list` | `restamp <name|number|all>` (re-stamps the box +3 from the layout; only changed blocks)
  | `tp <name|number>` (stands you south of it, looking north). Restamp only writes layout cells (id 0 = untouched terrain).
- **Previews without the game:** `Export x1 y1 z1 x2 y2 z2 out.txt` (a tiny class beside the layout: prints "x y z state"
  for every placed block) + `python tools/render_layout_iso.py out.txt out.png [view 0-3] [scale]` (isometric voxels,
  colours from block names). Before/after shots live in `tools/previews/spawn/`.
- **Fixed:** the terrace stair runs (main avenue + x=+-70 side avenues) were overwritten by street paving (top stair of
  each run became a full block) - `terraceStairs()` now runs after streets/spawnPlaza.
- **#14 Fish Market -> the WAVEBREAK FISH HALL** (`fishMarket()`, furnishes itself): basilica plan - tall nave with
  clerestory (ridge along x) between lean-to aisles, dark-oak posts + corbels, stone plinth + spruce floor, ice fish
  counters, stock shelves, smoking hearth + smokers, troughs, nets, shark trophies over both arches, quay apron.
  Palette verified on a server (no bad entries). NOT yet seen in a client by the user.
  Round 2 (`fishMarketExtras()`, 2026-10-01): 4 sunken AQUARIUM TANKS in the nave (x15..18 / x26..29, z65..66 / z68..69:
  water 2 deep over sand + sea lanterns, coral, seagrass, sea pickles, lily pads), FISH_SIGNs (new block) on both gables,
  AUCTION YARD (x10..19 z76..81: podium, lectern, bell, benches, the catch on pallets), DRYING YARD (x25..34: net racks,
  dried kelp, salt piles), SMOKEHOUSE (x37..43 z60..67, chimney x41 z61) and turf-roofed ICE HOUSE (x37..43 z70..79,
  packed-ice lining, blue ice). The sign font in tools/gen_tavern_assets.py now has F J Q V X Z 0 7 8 9.
- **#3 QUAY & HARBOUR** (`quayAndHarbor()` + `westBattery/cargoYard/landingStairs/netRacks/anchorPlaza/quayStall/
  customsHouse`, rebuilt 2026-10-01): worn sett paving, kerb, polished edge walk z94-96, smooth coping; sea-wall face with
  a prismarine barnacle course, buttresses (x%16==8), tripwire-hook mooring rings, blackstone bollards. W->E: Saluting
  Battery (x-92..-84, raised, crenellated, 4 display cannons, flagstaff), cargo yard (treadwheel crane x-72..-63 with a
  crate on its hoist over the water, tarped crate stacks, pallets, cart; warehouse door lane x-58..-50 kept open), landing
  stairs at x-29 and x27 (down to a stage at y62, rowboats moored beside), net racks, ANCHOR PLAZA (compass-rose paving
  r7.5, weathered-copper anchor monument, banners flanking the grand pier), rope-maker + net-mender stalls, Customs House
  (x42..48). Keep clear: pier heads (`nearQuayGap`), the fish hall apron x20..24, shipwright yard x>=52.
  Palette verified on a server. NOT yet seen in a client by the user.
- **#4 PIERS** (`piers()` + `pier()/fishermensHead/shipwrightsLanding/cargoHead`, rebuilt 2026-10-01): pile bents every
  4 (piles to y50, cross beams + braces under the deck), stripped-log stringers, spruce deck with dark-oak joint rows,
  connected rope-rail fences with boarding gaps (rope coils), arm lamps, harbour lights. West = Fishermen's Pier (T-head
  x-86..-70 z124..130: net shed, drying rack, stools, pots); grand pier = gate arch at the quay (z98, anchor on the
  lintel), bench bay z117..123, THE SHIPWRIGHT'S LANDING (x-7..7 z140..152): **shipwright_table stays at (-2,66,148) +
  lectern (-3,66,148), nothing within 2 of them**, striped canopy (posts 3+ away, roof y70), bell (0,66,151), timber
  stock/anchor/cartography table on the east half, steps to a water landing z154..155. Keep everything near the landing
  under y75 and the water around it empty - bought ships spawn ~20 out at Y75. East = Cargo Pier (T-head x34..48
  z128..134: jib crane over the water, tarped crates, harbour lamp). `harborBoats()` adds 2 rowboats and `gangplank()`s
  to both moored sloops (after the sloops, it opens their rail). Palette verified on a server. NOT yet seen in a client.
- **#5 SHIPWRIGHT YARD** (`shipwrightYard()` hall + `hallLoftAndCupola/coveredSlip/shipOnTheSlip/yardWorks`, 2026-10-01):
  hall keeps its shell/interior, gains a sail loft (y70, z61..66, stair x83..86 at z67) and a bell cupola on the ridge
  (y85..90). COVERED BUILDING SLIP x62..82 from z86 into the harbour (z124), `slipY(z)` = 65 - (z-86)/6, cleared to y100
  (water below 63), sliding ways x69/75; a great open shed (posts x60/84 every 4, tie beams y83, gable roof ridge x72 y95,
  boarded over z86..105, bare trusses beyond, a hoist lowering a rib). THE SHIP ON THE SLIP: hull z88..110 (keel y67),
  stern half planked with a quarterdeck, open ribs + ribbands forward, stem + sternpost, cradle blocks + shores,
  scaffold walks x64/x80 at y71/75 with ladders at z87. Yard: gateway from the quay (x57 z83..86), seasoning stacks,
  sawpit, timber lean-to, steam box, pitch kettle, spare masts + anchors, worn ground. Palette verified on a server.
  NOT yet seen in a client.
- **#8 INN -> THE MERMAID'S REST** (`inn()` + `furnishInn()`, 2026-10-01): coaching inn round a courtyard light well
  (x-76..-48 z14..42, well interior x-65..-59 z24..32). Floors y67/72/77, ceiling y82, `innRoof()` = hipped ring roof
  (height = min(distance to eaves, distance to the well) - mangrove), 2 brick chimneys on the west face (hearths on every
  storey at x-75 z28 + z37), teal belvedere over the east wing (stair from floor 3 at z27). Facades: stone ground storey,
  dark-oak frame (posts every 6) with pastel terracotta plaster per side (N lime, S pink, W light blue, E yellow),
  shutters (crimson/birch/cherry/warped) + azalea flower boxes, striped veranda on the market side; two INN_SIGNs (2026-10-03, teal
  board + mermaid, `inn_board()` in tools/gen_tavern_assets.py) at (-47,73,26) facing east and (-70,73,43) facing south. Ground: lobby
  (main doors x-48 z27..29, stair z25), taproom, games snug, common room (hearth, stage, avenue doors), kitchen (range,
  hatch), bathhouse (sunken tub), laundry (back door), courtyard (fountain, beds, vines, lantern strings). Floors 2+3:
  corridor ring + rooms A/B/C (north), D/E/F (south), G (west hammock bunkroom), east lounge; every room its own colours
  and door wood (`guestRoom()` in a u/v room frame, `washroom`, `linenRoom`, `bunkroom`, `lounge`). Furnishing uses
  `ip()` (air only, never a reserved door approach - `innDoor` reserves both sides). Palette verified on a server.
  NOT yet seen in a client. `render_layout_iso.py` now knows dye colours (terracotta/wool/carpet/beds) + coloured woods.
- **#7 TAVERN -> THE GROG BARREL** (`tavern()` + `furnishTavern()`, 2026-10-01): entrance through a GIANT BARREL lying
  against the west front (x43..47, the door in its end, copper tap, `tavBarrelEntrance`), two TAVERN_SIGNs. GREAT HALL
  x49..62 open to the slate roof (`tavRoof`, ridge y93, spruce underside) with tie beams y78 + wagon-wheel chandeliers;
  the BAR along z22 (kegs, spirit bottles, rum rack, grog barrels, stools), trestle tables, stage (SW), hearth + chimney
  (south wall); a U GALLERY at y72 (north z17..20, west x49..51, south z40..43); THE DEN under the east upper floor
  (x63..75: 2 Liar's Dice tables, Crown & Anchor, roulette, card table, dartboard); back room with the stair (z20); CELLAR
  under the bar (y62..66, hatch + ladder at x51 z19). Upstairs: HIGH ROLLERS' ROOM (box over the hall, private dice table,
  Crown & Anchor) + the landlord's cabin. BEER GARDEN x77..83 (umbrella tables, kegs, grill, lantern strings). Hanging
  lanterns under the open roof use `hangLantern` (chain up to the next block). INN_RES is no longer cleared in inn() -
  door reservations of every building must survive until furnishBuildings runs. Palette verified on a server.

- **#9 CHANDLERY** (`chandlery()` + `furnishChandlery()`, 2026-10-01): ship chandler's, x-112..-86 z14..36 - dark clapboard
  (dark oak) with white trim (stripped birch bands + corners, calcite window frames, `chWindows`), verdigris copper roof
  (`chRoof`, ridge y90, tie beams y78), east front: 2 bay windows (z17..21, z29..33) with lanterns on the sills, door
  with sidelights + copper hood, CHANDLERY_SIGN (new block, same hanging model as the tavern sign), loft door + HOIST BEAM
  with a crate on the tackle. Shop: lantern ceiling (some soul lanterns), rope wall, two-tier stock racks, counter z33 +
  keeper's shelves, chart corner, ship's wheels, display cannon, four FIGUREHEADS for sale, anchors + chain; stair z16.
  Upstairs SAIL LOFT: a sail spread in the floor (canvas blocks), sails hanging under the beams, bolts, spools, benches.
  West: RIGGING YARD (spars, capstan, anchor, tar kettle, chain, lean-to). Palette verified on a server.

- **#10 BAKERY** (`bakery()` + `furnishBakery()`, 2026-10-01): thatched cottage-bakery x86..112 z16..38 - stone base, oak
  frame + smooth-sandstone plaster, cherry shutters, moss window boxes with flowers (`bkWindows`), thatch roof (`bkRoof`,
  pixelpirates:thatch blocks, ridge z27 y91). Shop front on the STREET (north): doors x98..99, display windows, a yellow/
  white awning, two BAKERY_SIGNs (new block, same hanging model), bread baskets (composter level 7) + candle cake outside.
  Shop: counter z23 (cakes, baskets, candle cakes, bell), wall shelves over the bakers' aisle z24, cafe corner, menu.
  Bakehouse: BEEHIVE OVEN bulging out of the east wall (`bkOven`, fire inside, mouth at x112 z28, chimney x114), kneading
  island, flour sacks, cooling racks, firewood, mixing bowls; stair z26. Upstairs round corridor z26: kitchen-parlour,
  bedroom, children's room (hearth + chimney x87 z33), flour loft. WINDMILL x116..120 z34..38 (`bkWindmill`, calcite,
  thatch cap, X sails on the north face). BAKER'S GARDEN z39..47 (cafe terrace + umbrellas, wheat on farmland, beehives,
  scarecrow, gate x98..99). Palette verified on a server.

- **#11 HARBOURMASTER** (`harbormaster()` + `furnishHarbormaster()`, 2026-10-01): port authority x-28..-12 z60..76 (floor
  y65, F2 y70, ceiling y75): stone ground storey, calcite plaster upstairs with dark-prismarine bands, warped shutters,
  HIPPED PRISMARINE ROOF (`hmRoof`, dark-prismarine hip caps), CLOCK TOWER x-16..-12 z72..76 (`hmTower`: dials south + east
  at y84..88 reading 3 o'clock, lookout gallery y92 with bell + 2 telescopes, prismarine cap, flag mast to y107; ladder
  x-13 z73 from the signal room), PORTICO (quartz columns) carrying the balcony, salute guns, HARBOUR_SIGNs (new block),
  SIGNAL MAST with flags in the west yard (x-33 z68). Ground: public office (clerks' counter z65, chiseled-bookshelf
  pigeon holes, benches, model ship, chart wall), the harbourmaster's office (x-16..-13), STRONGROOM in the tower base
  (iron door + button at x-17). Upstairs: records room, quarters, signal room (flag lockers, signal lamps). Palette
  verified on a server. The preview renderer now colours prismarine and quartz.

- **#12 WAREHOUSE (east) -> the TRADING COMPANY's bonded warehouse** (`tradingWarehouse()` + `furnishTradingWarehouse()`,
  2026-10-01; the west warehouse still uses the generic `warehouse()`): x-70..-38 z60..84, floor y65, loft y72, walls to
  y78. Red brick with stone quoins/bands + mud-brick pilasters, round-headed windows (`twWindows`), low-pitch mud-brick
  slab roof (`twRoof`, one block per two rows, ridge z72 y85) with a glazed MONITOR on the ridge (x-64..-44) that lights
  the loft. Quay side: loading door x-56..-52 (the quay cargo-yard lane), dock, sliding door leaf, LOOPHOLE DOORS +
  ground bays at x-67/-42 with JIB BEAMS and hanging crates. Street side: office door under a stone pediment, two
  WAREHOUSE_SIGNs ("Trading Company", new block), banners. Inside: 4 racks (spices / tea + silk / rum + wine / naval
  stores), rail track x-54, weighbridge, glass tally office (NE), freight hatch + hanging pallet, stair x-69. Loft: grain
  + tobacco, crates, sail bolts, cooper's corner. Palette verified on a server.

- **#13 WAREHOUSE (west) -> the BLACKWATER RUM DISTILLERY** (`distillery()` + `furnishDistillery()`, 2026-10-01; the generic
  `warehouse()`/`furnishWarehouse()` are now unused): x-128..-98 z58..80, floor y65, loft y72 over the west half only.
  Rough stone ground storey, spruce boards on dark-oak posts above, cobbled-deepslate gable (`dsRoof`, ridge z69 y91), a
  copper PAGODA VENT over the still hall (x-109..-103 z66..72) and a brick CHIMNEY (x-100..-99 z60..61, to y98). STILL
  HALL (east, open to the rafters): two decorative `potStill`s (brick firebox + campfire, copper body, lightning-rod swan
  neck to a worm tub), THREE WORKING RUM STILLS over campfires at x-100 z66/69/72 (homestead rum - bring molasses), two
  molasses vats, bamboo-block cane bundles, copper pipe runs. West: TASTING ROOM on the street (bar, casks as tables,
  menu, DISTILLERY_SIGN - new block), CASK HALL (rows of aging casks two high, cross aisle x-121), stair x-127; upstairs
  the master distiller's office + lab and the cask loft (rail at x-114 over the stills). BARREL YARD west (cask pyramid,
  cart, sugar cane on a water channel) and a loading stage + crane over the bay (z81..84). Palette verified on a server.

- **#15 DOCK OFFICE + THE EAST BERTHS** (`dockOffice()` + `dkWindows/dkRoof/eastBerths` + `furnishDockOffice()`, 2026-10-01):
  office x102..116 z58..72 (floor y65, upstairs y70): stone ground storey, light-blue terracotta boards above, white birch
  trim + shutters, red nether-brick hipped roof with a glazed lantern cupola, DOCK_SIGNs (new block). Public hall with two
  DUES LEDGERs in the counter (x106/x112 z64), DUES BOARDs, berth chart; dockmaster's desk/lockers/hawsers/strongbox behind;
  stair x115 rising north; upstairs WATCH ROOM (south, wide windows, telescope, signal lamp) + quarters. South veranda +
  BOARDWALK x108..111 z76..95 on pilings (piles only where `islandSurfaceY` < 64.5) down to THE EAST BERTHS jetty x98..132
  z96..99 (berth bollard every 6 on the seaward edge, lamps, water lights, crane, supplies) and a DUES KIOSK x112..115
  z92..95 served across the boardwalk rail. Berth bollards were also added to the quay edge (x-56/-40/-24/24 at z96), every
  pier boarding gap and the shipwright's landing. Palette verified on a server.


- **#16 CHAPEL -> THE SAILORS' CHAPEL** (`chapel()` + `chRoof2/chTower/chYard` + `furnishChapel()`, 2026-10-01): nave
  x-33..-17 z-80..-58 (floor y70, walls to y80) in polished diorite with stone quoins, buttresses + stained-glass lancets,
  polished-blackstone-brick roof (ridge x-25 y90), quartz-pillar arcades (x-29/-21) with tie beams (y81) + chandeliers.
  APSE north (half-dome, glass ring), CHANCEL raised (altar, altar cross, candelabras, communion rail, pulpit, hymn boards),
  PEWS (CHAPEL_PEW rows z-71..-61, red aisle x-25), ORGAN in the east aisle (pipes x-18 z-79..-76 three courses, console
  (-20,71,-77) facing west + its bench), font, memorial plaques, wall crosses, VOTIVE SHIP over the nave. BELL TOWER
  x-28..-22 z-58..-52: 3 doors, ROSE WINDOW (y82), belfry y92..96 with 3 bells under a beam, BELL ROPE at (-27,71,-55) +
  chain up the shaft, spire to y108 + gilt cross. CHURCHYARD x-40..-12 z-88..-49: wall, lychgate on the street,
  headstones + flowers, sailors' memorial, cypresses. Palette verified on a server; the morning bell tolled there.

- **#17 MANOR -> THE GOVERNOR'S RESIDENCE** (`manor()` + `mnWindows/mnRoof/mnPortico/mnPavilions/mnGrounds` +
  `furnishManor()`, 2026-10-02; constants MG=70, MF2=76, MCEIL=82, MROOF=83): main block x18..42 z-79..-61 in quartz bricks
  on a smooth-stone base, quartz pilasters, cornice + diorite balustrade, weathered-copper hipped roof, glazed CUPOLA
  (x28..32 z-72..-68, chandelier, flag mast to y105). PORTICO south (6 quartz columns x23..37 z-57, pediment with the
  anchor + gold). Inside: ENTRANCE HALL x26..34 (marble chequer, grand quartz stair z-66..-71 to the landing z-72..-78,
  side galleries x26-27/33-34 with balustrades, chandelier, busts, GOVERNOR_PORTRAIT); west: STATE DINING (z-78..-71) +
  GUARDROOM (z-69..-62, duty desk, weapon racks - security later); east: BALLROOM (chandeliers, organ console, sofas).
  Upstairs: GOVERNOR'S SUITE (west, door onto the orangery terrace), LIBRARY (east, door onto the kitchen terrace),
  STRONGROOM (iron door at (35,77,-66) + button on the gallery side). Pavilions: ORANGERY x12..17 (glass) and KITCHEN
  x43..49, both z-76..-64 with balustraded roof terraces. Grounds x12..53 z-88..-49: SEA_GOD_STATUE fountain (30,-52),
  parterres x13..19/x41..47, iron fence on quartz piers, the GATE x28..32 with LION_STATUEs on its piers, two SENTRY BOXES
  (x24-25 / x35-36 z-52..-51 = guard posts for the coming NPCs), back garden: reflecting pool, GOVERNOR_STATUE (30,73,-86),
  copper-domed gazebo (18,-84), roses + topiary. New blocks (HomesteadBlocks, `python tools/gen_manor_assets.py`, previews
  tools/previews/manor/): GOVERNOR_STATUE + SEA_GOD_STATUE (2 tall), LION_STATUE, MARBLE_BUST, GARDEN_URN, CRYSTAL_CHANDELIER
  (light 15), GOVERNOR_PORTRAIT. Palette verified on a server (`/ppisland restamp 17`, no bad entries). Previews
  tools/previews/spawn/manor_after_v0/v2.png. NOT seen in a client.

- **#18 GUARDHOUSE -> THE WAVEBREAK WATCH** (`guardhouse()` + `ghGround/ghTower/ghMess/ghYard/ghForecourt` +
  `furnishGuardhouse()`, 2026-10-02; constants GHG=70, GHF=75, GHD=80 roof deck): the plot is now x-55..-5 z-41..-13 (was an
  18x12 shed). WATCH HOUSE x-43..-18 z-38..-27: stone-brick ashlar (`ghStone` mixes cracked/mossy) on a deepslate-brick
  plinth, polished-andesite quoins + floor course, pilasters (x-35/-26), machicolation corbels + crenellated parapet, a
  ROOF WALK with a slate-roofed HATCH TURRET (x-33..-29 z-37..-35, ladder x-31 z-37) and two salute guns. Ground: THE BRIG
  (x-42..-34: three cells behind iron bars, iron doors x-40/-37/-35 opened by stone buttons on the dividers at x-39/-36,
  the gaoler's desk + key hooks), DUTY HALL (double doors x-31/-30 front and back, sergeant's counter x-29 with a bell,
  WANTED_POSTERs, stair x-28 rising north to F2 at z-33), ARMOURY (x-26..-19, door from the sergeant's pocket at x-27 z-35:
  WEAPON_RACKs, powder barrels, a display cannon, smith's bench). F2: BARRACKS (bunk beds two high on slabs + lockers,
  card table), landing, CAPTAIN OF THE WATCH (desk, governor's portrait, map table, bed). WATCHTOWER x-17..-11 z-40..-34:
  arched gate passage N-S, ladder x-12 z-38 up to y90, floors y75/80/86, door at y81 onto the roof walk, corbelled LOOKOUT
  y91 (9x9) with the ALARM BELL under a slate bell-cote (-14,94,-36), signal brazier (campfire, -14,93,-39), blue flag mast.
  MESS x-53..-44 against the west wall (slate gable, hearth + chimney stack x-55..-54 z-34..-32 to y87, doors to the brig
  and the yard). DRILL YARD walled x-53..-11 to z-13, GATE east z-22..-20 + path to the avenue: archery butts, sparring
  ring, dummies, trough, well (-48,-20), woodpile lean-to, kennel. FORECOURT: working BOUNTY_BOARD (master -22,71,-39),
  pillory + stocks, WATCH_SIGN (-33,73,-39), banners, lamps. New blocks (HomesteadBlocks, `python tools/gen_watch_assets.py`,
  previews tools/previews/watch/): WATCH_SIGN (shared hanging-sign model), WANTED_POSTER, WEAPON_RACK (cutlass/pike/sabre
  sprites on rails, cutout layer). Verified on a server: `/ppisland restamp 18`, no bad palette entries, 7 block spot-checks
  passed. Previews tools/previews/spawn/guardhouse_*.png. NOT seen in a client. Guard NPCs still to come (sentry posts:
  the tower passage, the manor gate boxes).

- **#19 SMITHY -> THE WAVEBREAK FORGE** (`smithy()` + `smGreatForge/smOpenForge/smShed/smYard` + `furnishSmithy()`,
  2026-10-02; SG=70): moved to x42..66 z-41..-12, facing the x=70 side avenue. The old box (x54..76 z-44..-24) sat across
  the avenue AND the upper street - streets() paves first, so not building there reopened both; the Smithy label box is
  widened to the old footprint so `/ppisland restamp 19` clears the old walls in an existing world (verified). FORGE HALL
  x47..65 z-38..-23: cobble/stone ground storey, dark-oak frame + brick infill, dark-oak gable roof (ridge y87) on tie beams
  (x51/59/63), OPEN ARCADE on the avenue (posts every 4 + braces). THE GREAT FORGE: two working FORGE_HEARTHs (55/57,71,-36,
  fuel 8 = always lit), BELLOWS (54 / 58), a lava crucible between, brick hood, chimney x55..57 z-38..-36 to y93 with a
  hidden campfire smoking at the top; FORGE_ANVILs at (55/57,71,-34). THE OPEN FORGE in the arcade: hearth (63,71,-25),
  bellows (63,-24), anvil (63,71,-27), its own flue. PATTERN_BOARDs (52/60,73,-37 and 62,72,-24), quench trough, weapon
  racks + tool shelves (west wall), benches (south), iron/coal stock, the order counter in the arcade, SMITHY_SIGNs
  (66,76,-31 east / 63,76,-39 north). CHARCOAL SHED lean-to x42..46 (coal, cordwood, raw iron). SMITH'S YARD z-21..-12:
  trip hammer, cannon-casting pit + gantry, a forged anchor, scrap heap, fence + gate. The forge's gameplay: docs/gear.md
  "THE FORGE". Verified on a server: restamp, no bad palette entries, block spot-checks. NOT seen in a client.

- **#20 PARK -> THE WAVEBREAK GARDENS** (`park()` + `pkPaths/pkGate/pkPond/pkBandstand/pkPlayShip/pkPromenade/pkBeds`,
  `gardenPalm`, 2026-10-02; PK=70): x-89..-57 z-41..-12 on both sides of the x=-70 side avenue, which now runs through it
  as a PROMENADE (the old park put its pond in the road and its path on the street; its old box is kept inside the label so
  `restamp 20` clears it). Street railing with two garden gates + the GARDEN GATE arch over the avenue (beam y75, PARK_SIGN
  hung at (-70,74,-42) - new block, `python tools/gen_park_assets.py`). WEST: the BOATING POND (ellipse round (-82,-29.5),
  water y67..69 = fishable), ISLAND GAZEBO (-82,-30, copper roof, benches, lantern on a chain), FOOTBRIDGE x-79..-76 z-31..-29,
  FISHING JETTY x-88..-86, lilies/seagrass/pickles; FRUIT PALMS with ripe BANANA bunches (right-click to pick, they regrow).
  EAST: the BANDSTAND (centre -62,-27, raised birch floor, copper bell roof, crystal chandelier, a working JUKEBOX at
  (-60,72,-27), note blocks, music stands, steps from the promenade), the PLAY SHIP x-65..-56 z-19..-15 (hull, quarterdeck +
  wheel, mast + sail + flag, two little cannons), the flower parterre with an urn, the hedge at x-57 against the guardhouse.
  South: the OVERLOOK benches at z-12 facing the harbour. `gardenPalm` draws palms face-connected (BUILD RULES #7) - the
  island's other palms still use the old `palm()`. Build checker: 0 issues. Verified on a server (restamp, no bad palette
  entries, spot checks). NOT seen in a client.

- **THE TOWNHOUSES (#21-#41) - one character each** (the user is writing a resident for every house, so each one is
  rebuilt as its own house with its own personality; the house label names stay "Townhouse N" so numbers and saved hand
  edits don't move). The mid-terrace row (#21-#30) now faces SOUTH onto the market street - the old generic `house()` put
  their doors on the north side, facing the terrace wall; the not-yet-rebuilt ones still do. Lots run z-10 (back garden)
  .. z7 (front fence), `thLot()` clears + grasses one. Signs name the trade, never the resident (the user names them; a
  sign edited in game is kept by /ppisland capture). Layout sign text: `signText(x,y,z, lines...)` writes block-entity
  data in the exact form the game saves (`PLAN_NBT`, applied by the feature + restamp; a capture ignores it unless changed).
  - **#21 THE OLD CAPTAIN'S HOUSE** (`captainsHouse()` + `furnishCaptainsHouse`, `roofHip()`; x-136..-124, floors 67/72/77,
    ceiling 81): navy clapboard (blue terracotta) on deepslate, birch trim/posts/shutters, mangrove door, the balcony over
    it (ship's wheel in the rail, dark-oak hanging sign "The Old Captain" under it at (-129,71,5)), mermaid figurehead
    (-130,79,4), hipped dark-oak roof with a WIDOW'S WALK deck at y85 (railing, brass telescope, his colours on a mast),
    brick chimney x-136..-135 (hearth at -134,-2). Parlour (fire, captain's chair, map table, model ship under the beams,
    sea chest, rum rack, galley corner), his cabin (bed, desk, logbook), the chart room, ladder up a mast-post to the deck
    hatch (-128,85,-2). Picket fence + gate, anchor; an upturned rowboat on trestles behind.
  - **#22 THE HERBALIST'S COTTAGE** (`herbalistsCottage()` + `hbGreenhouse/hbGardens` + `furnishHerbalistsCottage`;
    x-121..-108): packed-mud walls on bamboo posts and a mossy-cobble plinth, mossy-cobblestone roof with a flowering azalea
    crest, fieldstone chimney (west), jungle door, bamboo hanging sign "Herbs & Remedies" (-118,71,4). Inside: hearth +
    cauldron, a working BREWING STAND on the herb bench, jars and potted herbs on slab shelves, glow-berry vines and a spore
    blossom under the loft, books + lectern, the loft bed. GREENHOUSE x-112..-108 (glass on bamboo, beds of chili / lime /
    pineapple round a spring). HERB GARDEN behind (beehives, chili + beetroot rows on a channel, composter, berry bushes),
    flower front garden with a birdbath, bamboo fences.
  Both: build checker 0 issues; verified on a server (restamp, no bad palette entries, sign text reads back, an immediate
  capture finds 0 edits). NOT seen in a client. Previews tools/previews/spawn/townhouse_1_2_*.png.
  - **#23 THE FORTUNE TELLER'S PARLOUR** (`fortuneTellersParlour()` + `furnishFortuneTeller`, `hangSoul`; x-66..-52):
    plum walls (purple terracotta) between dark-oak posts on polished blackstone, crimson trim/door/shutters, purple glass,
    a teal (warped) roof; the round-cornered TURRET x-65..-61 z0..4 (three floors, ladder at (-64,*,1), magenta glass, hipped
    teal roof, amethyst-crystal spire (-63,87,2)) opens into the house. Reading room (round table with a cloth, the crystal
    ball, purple candles, curiosities shelf, beaded curtain), her rooms (brewing stand, cauldron), the STAR CHAMBER at the
    turret top. Garden: moonflowers, a twisted dark-oak tree, the MOON POOL (sea lanterns under the water), soul lanterns on
    a blackstone garden wall. Sign "Fortunes Told" (crimson, on a bracket at (-56,71,3)).
  - **#24 THE INSTRUMENT MAKER'S SHOP** (`instrumentMakersShop()` + `furnishInstrumentMaker`; x-49..-35): red brick,
    andesite quoins, smooth-stone bands and sills, a glass BOW WINDOW (x-46..-43, the wall opened behind it), the CLOCK FACE
    above it (white concrete, black hands at three, copper frame), a bright copper hipped roof with a flat deck and a
    weathervane. Display (lodestone, sundials), the counter of instruments, the workbench row, the grandfather clock, ladder
    (-38,*,-4); upstairs his rooms and the ORRERY (a copper sun + rods) on a chain from the deck. Sign "Compasses / Sextants
    / & Clocks" (dark oak, (-38,71,3)).
  - **#25 THE PARROT KEEPER'S AVIARY** (`parrotKeepersAviary()` + `avAviary` + `furnishParrotKeeper`; x-33..-19): a pink
    cherry house (stripped-cherry posts, jungle shutters, red-sandstone gable roof facing the street), a veranda with a
    hammock, bunting and seed sacks, sunflowers behind. THE AVIARY (centre `AVIARY_CENTRE` (-23,69,3)): a 7x7 iron cage on a
    mossy base with a face-connected copper-and-bars dome, a jungle tree, perches, feeders, water, a nest box, a door on the
    west side. `homestead/parrot/Aviary` (2026-10-03): EVERY GAME DAY (first time a player is within 48 after the day
    changes; HomesteadState number "aviary_day") the untamed birds go and 4 new ones are drawn by rarity - Green Parakeet
    35 / Grey Parrot 30 (common), Scarlet Macaw 20 (uncommon), Blue-and-Gold 12 (rare), Hyacinth (vanilla BLUE) 3 (very
    rare, ~11% of days); a rare+ bird is announced to players within 160. Spots: the two perch slabs + two floor cells (the
    old y73 spots were above the leaf canopy, sealed under the dome - unreachable). `/ppaviary` (op) turns the stock over now. The draw now covers all 15 ParrotTypes (docs/parrot_ideas.md). Three PERCH_BRANCHes inside the cage at y71 (2026-10-03, see docs/parrot_ideas.md).
    Tame one with seeds for a ship's parrot (ParrotCompanion). PLANNED with the unique NPCs: the keeper SELLS the birds
    (price by rarity) instead of players taming them in the cage. Daily turnover NOT yet seen in game.
    Sign "Parrots & Seed" (jungle, (-27,71,-1)).
  `bracketSign(x,y,z,wood,lines)` = a wall hanging sign facing east, hung from the wall at z-1 - readable along the street.
  The build checker now knows wall hanging signs hang from the side (rotated facing) and ceiling ones need a block above.
  #23-#25 verified on a server (restamp, no bad palette entries, the three signs read back, /ppaviary added 4 parrots then 0,
  captures right after restamp find 0 edits). NOT seen in a client (the parrots staying in the cage over time, the copper
  colour, the clock) - previews tools/previews/spawn/townhouse_3_4_5_*.png.

  - **#26 THE PEARL DIVER'S HOUSE** (`pearlDiversHouse()` + `pdWindow` + `furnishPearlDiver`; x20..32, PDG/PDF2/PDR =
    67/72/77): whitewashed calcite on a prismarine-brick plinth studded with shell blocks (+ the odd glowing pearl block),
    2-wide arched windows (quartz-stair corners) with warped shutters, warped door + balcony door, a wrought-iron balcony
    (glazed wall behind it), sign "Pearls & Diving" under it (28,71,2). FLAT TERRACOTTA ROOF TERRACE: calcite parapet, potted
    plants, a cyan/white parasol (24,-3), two deck chairs facing the sea, and the stair KIOSK x27..29 z-6..-4 with a
    dark-prismarine dome (ladder x28 z-5 from the upper floor). Inside: cyan/white chequer tiles, birch stair along the north
    wall, the sorting table (white candles = pearls), dive gear (copper "helmet", rinsing tub, nets), THE PEARL on a quartz
    pillar (29,68,0); upstairs the diver's room. Yard: DIVING POOL x27..31 z3..6 lit from below, a copper DIVING BELL on a
    davit (post x31 z4), sponge rack, bougainvillea, diorite-wall front with a gate.
  - **#27 THE FIREWORKS MAKER** (`fireworksMakersTower()` + `furnishFireworksMaker`, `fwIn/fwEdge`; x36..48): half-timbered
    WORKSHOP x37..42 z-6..3 (dark oak + orange terracotta, mangrove gable roof facing the street, red/yellow wool awning,
    a shop window of lit coloured candles = "stars", sign "Fireworks & Flares" (38,72,4)); THE POWDER TOWER = octagon centre
    (45,-6) (|dx|,|dz|<=3, |dx|+|dz|<=5), yellow terracotta with red/white bands, slit windows, floors 67/73/79, ladder
    x45 z-8 to the LAUNCH DECK y85 (red merlons, scorch marks, a rack of three rockets (fence + wool + lightning rod) under a
    bar, mortar, powder keg, flag mast). Door workshop<->tower at (42,-5). Yard: a scorched TEST PIT x44..48 z3..6.
  - **#28 THE BEEKEEPER & CHANDLER** (`beekeepersSkep()` + `furnishBeekeeper`, `hwD/hwIn/hwEdge/hwDome`; x52..64): a round
    SKEP HOUSE, centre (58,-3) r5.4 - hay coiled along the curve (axis by tangent) on a mud-brick plinth, a honeycomb band,
    a beehive DOME (each course covers from its radius in to just under the next, so it closes face-to-face) capped by a
    bee nest (58,82,-3), a thatched porch, amber windows. Inside: wax vats, the dipping table, candle racks, honey jars,
    the counter; loft over the north half (ladder x58 z-7). Meadow: 8 beehives on stands, wildflowers, lilacs + peonies.
  `thEaves(lx1, lx2)` writes air in the old generic house's eave columns (one block outside each lot, z-7..7) so a restamp
  clears them; #21-#25 did not do this, so an OLD world may still have a stray eave column beside those five lots.
  #26-#28: build checker 0 issues; verified on a server (restamp 1790/2118/1280 blocks, no bad palette entries, 10 block
  spot checks, the three signs read back). Previews tools/previews/spawn/townhouse_6_7_8_*.png. NOT seen in a client.

  **#29-#36 (2026-10-04)** - the last two market-street lots and all six UPPER-ROW (row A) lots, which now face NORTH onto
  the upper street (z-48..-42). `uaLot(x1, x2, pathX)` clears + grasses an upper lot (z-40 front fence .. z-12; the old
  eaves only where no earlier pass built - lampposts on z-41 and the park's palms stay), `uaBox` grows the label over it,
  `UA_LOTS` keeps lawnDecor out of these designed yards. New helpers: `gableRoofXId` (a gable in any stair id - the thatch
  roofs), `patternBanner` (wall banner + Patterns NBT in the game's saved form), `hangingSign` (ceiling hanging sign + text).
  - **#29 THE GLASSBLOWER** (`glassblowersKiln()`, x108..120): salmon (smooth red sandstone) shop on dark-oak posts, brick
    roof gable to the street, a RAINBOW stained-glass shop window of SHIPS IN BOTTLES (new blocks), sign "Glass & Bottles".
    THE BOTTLE KILN behind (centre 117,-7, `gbR(y)`): brick base with deepslate hoops, curving shoulder, neck, rim; in its
    heart four lit blast furnaces round a signal campfire (on hay) whose smoke climbs the open flue. Kiln yard: sand, cullet
    heap, potash barrels, firewood, trough; glass sculptures (amethyst) on posts out front.
  - **#30 THE OLD BOATSWAIN** (`boatswainsHull()`, x124..136): an UPTURNED HULL as the roof (`bsW/bsHw`: half-beam 5, fining
    to the stem post at z-10; tarred strakes, red-and-white sheer stripe, barnacles on top, keel along the ridge), the flat
    TRANSOM to the street (stern windows, door, wall sign "Rope & Rigging", stern lanterns on beams) on low cobble walls.
    Inside: ribs every third frame, hammock, stove + pipe out through the planking, nets, sea chest, chart. Yard: bell on a
    frame, capstan (lightning-rod bars), anchor + chain, a hoist of signal flags, lobster pots behind.
  - **#31 THE MILLER'S WINDMILL** (`millersWindmill()`, x-136..-124): diorite TOWER MILL (centre -130,-31, `wmR(y)` tapers
    4.4 -> 3.0), floors 70/75/80/85, the main post with the ladder + sack-hoist chain, two millstones fed by hoppers, the
    railed STAGE at y80 (stage door), dark-oak CAP, windshaft + brake wheel, four SAILS in an X on the plane z-38 (hub y88).
    Gate arch with the hanging sign "Mill & Flour". Behind: the miller's thatched cottage (oven, a cake on the table) and a
    wheat strip on a water channel with a scarecrow.
  - **#32 THE SAILMAKER'S LOFT** (`sailmakersLoft()`, x-120..-108): board-and-batten walls, GAMBREL slate roof
    (`SL_TOP/SL_BOT`), wagon doors, the LOFT DOOR with a jib beam + a rolled sail on the hoist, roof lights; canvas store /
    cutting floor / THE SAIL LOFT (a half-made sail on its yard). Drying yard: two lines of sails (cream, red-and-white).
  - **#33 THE TATTOOIST** (`tattooParlour()`, x-104..-92): dark timber, the upper floor JETTIED over the street on corbels,
    red lacquer panels, paper-screen windows, blackstone roof, paper lanterns (shroomlights) + hanging sign "Ink & Needle"
    under the jetty. FLASH WALL of patterned banners (skull, rose, sea serpent, eye, globe), tattoo chair, inks + needles;
    a tiger-stripe rug upstairs. Yard: bamboo grove, stone lantern, lily pond, bench.
  - **#34 THE TREASURE HUNTER** (`treasureHuntersHouse()`, x80..92): flat-roofed sandstone house, arched door, LOOKOUT TOWER
    with an orange dome (telescope inside), roof terrace with a striped awning. Trophy hall: gilded-mask sarcophagus, relics
    on quartz pillars (conduit, pearl block, pot), globe banner, charts, map table. THE DIG behind: a pit (a shelf 1 deep, the
    pit 3 deep) with a sea serpent's skeleton (bone blocks), ladder, winch + bucket, camp tent, sieve.
  - **#35 THE TOYMAKER** (`toymakersHouse()`, x96..108): peach walls, CANDY-STRIPED corner posts, bamboo trim, a steep purpur
    roof (2 blocks + a stair per step, ridge y91) with round gable windows, a crooked chimney, a bay window of toys. Inside a
    train set (rails) round a little harbour town, puppet theatre, music box, workbench; a patchwork rug upstairs; the attic
    with model ships hung from the rafters. Yard: swing, seesaw, sandpit + castle, hopscotch, a KITE on a chain string.
  - **#36 THE SHIP'S-CAT KEEPER** (`shipsCatKeeper()`, x120..132): red-brick cottage, thatched roof, fireplace chimney, cat
    tower, bowls, a CAT FLAP (open trapdoor) to THE CAT GARDEN - fenced all round, its corners closed against the cottage and
    nothing climbable beside the fence (cats escaped both ways in a test), gate east. `CATTERY_CENTRE/BOX/SPOTS` drive
    homestead/cat/Cattery (docs/gameplay-systems.md "SHIP'S CATS").
  #29-#36: build checker 0 issues (whole island 714 -> 671); verified on a server: restamp all eight (no bad palette entries),
  12 block spot checks, banner patterns + sign text read back, `/ppisland capture` right after a restamp = 0 edits each, the
  cattery restocks and its cats stayed in the garden for 5 minutes. NOT seen in a client. Previews
  tools/previews/spawn/townhouse_29_glassblower.png .. townhouse_36_cat_keeper.png. `/ppisland tp` stands SOUTH of a
  building - for the upper row that is the back garden.

  **#37-#41 (2026-10-04) - THE WALL ROW (row B)**: doors SOUTH onto long front lawns down to the upper street (z-48); the
  city wall behind. `ubLot(x1, x2, pathX)` clears a lot z-84 (the wall's towers reach z-85) .. z-50 (front fence,
  `ubFence`), path from z-64 to the street; `ubBox`; all rebuilt lots are in `UA_LOTS` {x1, x2, z1, z2} (lawnDecor skips
  them). `standingBanner` = a standing banner with Patterns (the painter's easels). Every townhouse #21-#41 is now its own.
  - **#37 THE COOPER** (`coopersCask()`, x-66..-56): the old lot sat ON the west side avenue - narrowed, and x-71..-67
    re-paved (`sideAvenueColumn`) with the old house cleared off it. A house INSIDE A GIANT CASK on its side (`coR(z)`:
    belly 4.6, heads 3.7; axis (-61, 74.4)): oak/spruce staves, deepslate hoops, spruce heads, door in the south head up
    two steps, porthole north, a tap, chocks. The cooperage on the lawn: lean-to shed, hoop fire, shaving horse, a barrel
    pyramid, sign "Casks & Barrels" on the shed post.
  - **#38 THE BARBER-SURGEON** (`barberSurgeon()`, x-54..-42): brick ground floor, quartz-brick upper in a dark-oak frame,
    nether-brick roof, the red-and-white BARBER'S POLE with a lamp on top, sign "Barber & Surgeon". Barber's chair, basin,
    apothecary shelves (coloured glass + brewing stands), operating table, a rack of saws, the anatomy skeleton (bone
    blocks + skull); upstairs the surgeon's room. Waiting bench + herb bed out front.
  - **#39 THE SPICE MERCHANT** (`spiceMerchant()`, x56..68): tall polished-granite souk house, acacia trim, an open ARCADE
    (three arches) with a sloping striped awning, heaps of spice (coloured concrete powder), sacks, scales, grinder; store
    rooms; the merchant's cushioned room; a ROOF TERRACE with spice drying mats and an acacia pavilion (`roofHip`) with
    strings of peppers (glow-berry cave vines) from its eaves. Sign "Spices & Teas".
  - **#40 THE LAUNDRESS** (`laundress()`, x72..84): blue-washed wash-house, stone-brick roof, a wide arch, tubs +
    washboards, mangle, copper boiler on a fire, ironing table, washing lines out front (shirts, trousers, a sheet), sign
    "Washing & Mending". SECRET: a trapdoor under a white rug at (80,70,-73) by the basket, a ladder down to THE SMUGGLERS'
    CELLAR (x76..82 z-79..-73, y64..68): casks, contraband crates, bottles, a loot chest (pixelpirates:chests/phase1_smuggler
    at 81,65,-75), a tunnel north to z-84 ending in rubble.
  - **#41 THE MARINE PAINTER** (`marinePainter()`, x96..108): a studio under a lean-to prismarine-brick roof (`pnRoof(z)`
    84 north -> 78 south) rising to a NORTH-LIGHT wall of glass; birch walls in a dark-oak frame. Easels (fence + standing
    banner seascapes: calm sea + sun, sunset, storm), paintings on the walls, the paint table, a galleon in a bottle; a
    mezzanine bedroom. An easel on the lawn facing the harbour, a cottage garden. Sign "Seascapes & Portraits".
  #37-#41: build checker 0 issues (whole island 671 -> 629); verified on a server: restamp all five (+ #36 with the cattery
  counter), spot checks (cask door, re-paved avenue, pavilion roof, pepper vines, the trapdoor, the cellar loot chest with
  its table, the easel banner's patterns), captures right after restamp = 0 edits. NOT seen in a client. Previews
  tools/previews/spawn/townhouse_37_cooper.png .. townhouse_41_marine_painter.png.

- **#42 THE NORTH WALL + THE GREAT NORTH GATE (rebuilt 2026-10-04)** (`cityWall()` -> `clearOldWall/northWall/wallTowerD/
  greatGate/processionalWay`, constants NWG 70 / NWT 85 / WZN -91 / WZS -87). THE PROBLEM it fixes: the city is a bowl cut
  into the hill (city y70, the hillside outside ~y81) - the old wall (3 thick, top y76) was buried on its north side and
  the gate opened into an 11-block bank of earth: no way out of the city to the north.
  - The whole wall is under the "Gatehouse" label (map #42, box x-137..137 z-118..-84), so `/ppisland restamp 42` redoes it all.
  - CURTAIN WALL x-136..136: 5 thick (z-91..-87 - the city face stays at z-87, the chapel + manor gardens touch z-86 and
    their overlap with the band is in LayoutCheck.INTENDED), wall-walk floor y85 (15 above the city), north parapet with
    merlons/crenels, a city-side rail with lanterns, a corbel table both sides, string course, pilasters every 12 (only
    where z-86 is free), arrow loops, a battered foot on the hillside, Jolly Roger banners every 24 (`jollyRoger()`), stone
    weathered by height (`wallStone`).
  - SIX D-TOWERS (`wallTowerD(tx, r, top)`: flat back on the city face, round front over the hill) at x-136/-96/-48/48/96/136,
    r5 to y95: door from the city (tx+2), doors onto the walk both ways, ladder up the back wall, floors y70/85/top,
    windows in the back, arrow loops, a brazier (campfire) and a Jolly Roger flag on top.
  - THE GREAT NORTH GATE (`greatGate()`): two drum towers (D, r6, round (+-9,-89), to y97) with the gatehouse RECESSED between
    them over the vaulted passage x-2..2 z-91..-87 (rounded vault, murder hole, a portcullis half up on chains to a windlass),
    the guard room above (two cannons at gun ports, powder, racks; doors from both towers, a y77 floor in the towers to meet
    it), the walk over the gate, machicolations, the plaque WAVEBREAK PORT (0,78,-92), Jolly Rogers both faces, the inner
    arch framed with a hood + braziers + "To the North Downs", and a great 7x5 wool Jolly Roger on a pole above it all.
  - THE PROCESSIONAL WAY (`processionalWay()`): a flat court between the drums (z-92..-95), then the road climbs half a block
    per block (full block / slab) from y70 at z-96 to y80 at z-116, x-3..3, between retaining walls (x+-4..5, 12 tall at the
    gate, sinking into the hill) with lantern posts and ivy; two brazier pylons + a beam with the hanging sign "Wavebreak
    Port" at z-117. In front of the gate the clearing stops at the arch (so the plaque + banners survive).
  - THE NORTH PATHS now start at the pylons (northPaths: (0,-119)->(0,-121), forks to the lighthouse and to the foot of the
    FORT STEPS (-78,-112); countryside: (0,-121)->(4,-131) to the ridge lookout). `smoothPaths()` adds a cobblestone-slab step
    wherever a path rises a block (PATH_CELLS) - the north paths need no jumping. `levelPad` keeps off `wallZone()`; the
    wheat + carrot fields were shortened to z-99 (their pads overwrote the wall + the x-48 tower).
  - FORT (#44): THE FORT STEPS - a walled stone stairway from the hillside up to the gate (it stood 4 blocks up with no way
    in), the gate lanterns on plinths. LIGHTHOUSE (#43): the lower ladder hung in the air inside the wide part - now on the
    wall, a landing at base+13, the upper ladder above it.
  - WALK CHECK (scratch `Walk.java`, BFS over the plan + terrain with half-block steps): from the main avenue the ridge
    lookout, the fort and the lighthouse are all reachable WITHOUT JUMPING; with the gate passage blocked none are - the
    route really is the gate.
  - The old [Gatehouse] hand edits (34 cells: air over the old arch, an old rail) were RETIRED from edits.txt (and from
    run/pixelpirates/island_edits.txt - backup island_edits.txt.bak-2026-10-04): they would have punched holes in the new
    gatehouse.
  - Verified on a server: restamp 42/43/44, spot checks (passage, portcullis, cannons, plaque text, banner patterns, flag,
    braziers, the ramp top, the fort steps, the walk), `/ppisland capture 42` after a restamp = 0 edits. Checker: island
    629 -> 624, #42's only entries are the island's old palm-leaf DIAGONALs that now fall inside its box. NOT seen in a
    client. Previews tools/previews/spawn/gate_north.png, gate_north_east.png, gate_city.png, wall_west.png.
  - Existing worlds: `/ppisland restamp 42` (wall, gate, way), `43`, `44`; the path slabs + shortened fields are in the
    unlabelled passes - `restamp all` (or new chunks) picks them up.

- **#43 THE WAVEBREAK LIGHT (rebuilt 2026-10-04)** (`lighthouse()` + `furnishLighthouse`, centre LHX/LHZ (108,-102), bastion
  floor LHB 85, watch-room floor LHG 106; the grid's top is y118 and everything fits under it). An octagonal stone BASTION
  (r10, kept off z-92 = the wall's foot; parapet + lamps; steps in on the west at x97 where the lighthouse path now ends,
  (95,-102)); the tower tapers 4.6 -> 3.2 (`lhR`) in SPIRAL red-terracotta/calcite bands with a real SPIRAL STAIR (stone
  brick stairs round a newel, 8-cell ring, a continuous run - no landings to jump) to the watch room (chart table, lectern,
  oil casks; the floor left open over the last steps), a ladder up into the LANTERN ROOM: glass in a copper frame, a
  BEACON on a 3x3 iron base (Levels 1, no effect set - just the beam) under a yellow-glass oculus in the copper dome = a
  golden beam into the sky (the lightning rod is off-centre so it never blocks it), end rods round it, a railed copper
  GALLERY. The keeper's cottage (x114..118, slate roof, door to the tower, bed, stove, telescope at the sea window), the
  FOG BELL on a frame, a display cannon facing the sea, a flagstaff of signal flags, the plaque "The Wavebreak Light".
  Checker: 0 issues (island 624 -> 580, mostly palms no longer landing on the headland); walk check still reaches the
  lookout, fort + lighthouse without jumping. Server: restamp, spot checks, the beacon reports Levels 1, plaque text,
  capture after restamp = 0 edits. NOT seen in a client. Previews tools/previews/spawn/lighthouse_sw.png, lighthouse_ne.png.

- **#44 THE GOVERNOR'S FORTRESS (rebuilt 2026-10-04)** (`fort()` -> `gatehouse/governorsHall/furnishGovernor/fortGrounds`,
  constants FX/FZ (-98,-120), FG 88 courtyard floor, FW 95 wall-walk floor, curtain FX1..FX2 x-114..-82 / FZ1..FZ2
  z-136..-104). The island belongs to the IRON ARMADA (NAVY) and this is its governor's seat; the old 27x27 fort is gone.
  - A STAR FORT: curtain walls 3 thick from the hillside up to the walk (string course, arrow loops, merlons, an inner
    rail), four ARROWHEAD BASTIONS (diamonds r7 round the corners, solid to the gun deck, parapets on the point) - the north
    side stands ~25 high on the slope. Moved 4 blocks north of the old fort so the south bastions stay off the city wall's
    `wallZone` (z >= -96). The courtyard is filled down to the hill (no hollow columns), the slopes round it re-turfed.
  - THE GATEHOUSE (east, z-121..-119): a vaulted passage with a raised portcullis, two GUN TOWERS (x-84..-80, z-127..-123 and
    -117..-113: a guard room from the courtyard, a room at the walk with doors both ways, ladder to the roof gun), Armada
    banners, the plaque "The Governor's Fortress" (-81,94,-120), walled steps x-81..-73 from y88 down to the path's end.
    The fort path now ends at (-70,-120) (`northPaths`). Wall stair: inside the south curtain (x-88..-94, z-107).
  - THE GOVERNOR'S HALL (x-108..-88, z-132..-124, quoins/pilasters of polished diorite, andesite panels, tall windows):
    ground floor THE WAR ROOM (a 7x3 map table with the map block at its heart, captain's chairs, the four factions' banners,
    sea charts, the governor's portrait, a bust, a display cannon, candelabras); floor 2 THE GOVERNOR'S STUDY + QUARTERS
    (desk facing the BALCONY over the portico, bookcases, chest, bed, sea chest); floor 3 THE SIGNAL ROOM (four telescopes,
    signal-flag wool, a bell, lectern); the ROOF TERRACE (two guns over the town, a telescope); THE GOVERNOR'S TOWER (NE
    corner, door from the roof, ladder, to y114) with the Armada's colours on a pole to y118. Three stair flights against
    the north wall (`innReserve`d), an open arch + quartz-pillared portico.
  - THE COURTYARD: barracks (west: 4 bunks, racks, lockers, a mess table), armoury (east: weapon racks, smithing table,
    grindstone, powder, a pillager-outpost loot chest), the sunken POWDER MAGAZINE (deepslate vault, iron door + buttons,
    stairs down, kegs), the chequered PARADE GROUND (a 17-tall flagpole with a blue/white Armada ensign, shot piles, drill
    dummies), a roofed well, lamp posts.
  - THE FORTRESS GUNS (12, `FORT_GUNS`): `pixelpirates:fort_cannon` - 8 on the bastions (two per point, in embrasures,
    shot + powder beside them), 2 on the gate towers (east), 2 on the keep roof (south). They are the existing fort cannon
    block (Rackham's lair) with an ISLAND MODE in `FortCannonBlockEntity` - see docs/bosses-early.md / gameplay below:
    always manned, fire only on players with reputation < 0 with `ISLAND_FACTION` (NAVY - one constant), range 60, a red
    chat warning + bell first, 3 s grace, then fuse-telegraphed terrain-safe shots (7 dmg falloff blast, knockback) that
    only hurt PLAYERS (`CannonBallEntity.setPlayersOnly` - villagers/traders/pets in the line of fire are safe).
    Test: `/ppreputation set navy -50` near the fort (survival). The guns are breakable (pickaxe, like Rackham's).
  - Checks: LayoutCheck - Fort 0 issues (island 580 -> 525: the old fort's palm-leaf strays went with it); Walk.java - the
    fort is reached from the avenue without jumping, and so are the north walk, both bastions, the keep's three floors,
    roof + balcony, magazine, barracks, armoury (the tower tops are by ladder, not modelled). Server: restamp 44, all 12
    guns present with ticking block entities, plaque/banners/map/flagpoles spot-checked, `capture 44` = 0. NOT seen in a
    client; the guns' targeting was NOT tested with a real player (no client on the test server).
    Previews tools/previews/spawn/fortress_se.png, fortress_nw.png.
  - Existing worlds: `/ppisland restamp 44` (fort) + `restamp all` or new chunks for the moved path end and palms.

- **#45 THE WRECK OF THE MERRY WREN (rebuilt 2026-10-04) - an EASTER EGG** (`beachWreck()`, centre line WRZ = 80,
  `wreckKeel(x)`, `WRECK_CHEST`). A merchant brig driven bow-first onto the south-east beach and broken in two: the BOW
  section (x135..149) silted up on the slope (hold full of sand, starboard stove in, ribs, the mermaid figurehead + the
  bowsprit, the foremast + MAINMAST stumps), a debris field in the gap (x150..152: ribs out of the sand, crates, her bell
  on the sea floor), the STERN section (x153..163) sunk upright in the shallows with the captain's cabin in the stern
  castle (floor y62, just clear of the sea; door from the drowned deck at (158,63,80)): Captain Wren's skull at his desk,
  a candle, an emptied sea chest, cobwebs, the sign "Capn Wren - My heart lies under the mainmast" (159,64,78), the
  nameboard "MERRY WREN of Wavebreak" on the transom. Ashore: the crew's camp (a tent cut from the sail, a cold fire, a
  DECOY salvage chest with shipwreck_supply loot, the sign "Merry Wren crew - Day 41 - Capn Wren went down with her").
  - THE SECRET: a missing deck plank beside the mainmast shows SUSPICIOUS SAND (145,63,81); dig down through it and the
    sand below to Captain Wren's chest at WRECK_CHEST (145,61,81): his last log (a written book, 3 pages, pointing at
    "the foot of her mainmast"), 9 gold nuggets, a compass - and, the first time EACH player opens it, CAPTAIN WREN'S
    MUSIC BOX (`homestead/wreck/WreckSecret`: a UseBlockCallback on that exact chest, a per-player
    `HomesteadState.discover(uuid, "wreck_music_box")`; the box goes in the first free slot, or the player's inventory
    if the chest is full; gold message + challenge chime). Restamps refill the book/coins; the box stays once per player.
  - The old wreck's 5 hand edits (a mast stub at (148,62..63,76) + seagrass) were RETIRED from edits.txt and
    run/pixelpirates/island_edits.txt - they would have landed in the new debris field. The old footprint is reset to
    sand + sea in the plan (cells nothing else claims), so restamping an old world clears the old wreck.
  - Checks: LayoutCheck - no Beach Wreck issues (the old one had a FLOAT); server: restamp 45, the chest's book/coins
    parsed, suspicious sand + sand + hole + mast + skull + figurehead + both signs + the nameboard spot-checked, the music
    box block placed, `capture 45` = 0. The chest hook was NOT tested with a real player. Previews
    tools/previews/spawn/wreck_se.png, wreck_nw.png (the export shows seabed as grass), tools/previews/props/wren_music_box.png.
  - Existing worlds: `/ppisland restamp 45`.

## HAND EDITS - build in game, then save (2026-10-02, `world/gen/IslandEdits`)

- **`/ppisland capture all`** = "save everything I changed": `IslandEditTracker` (overworld saved data, survives restarts)
  remembers every spot a player broke, placed or used a block at on the island (+ the 26 around it, for door/bed halves);
  capture all checks exactly those spots, files each into its building's section or **Streets & Grounds** (everything
  outside every label box; a cell inside several boxes belongs to the SMALLEST), replaces what was saved there, drops spots
  put back as planned, and clears the list. NOT seen by the tracker: /fill, /setblock, buckets, explosions - use the
  per-building capture for those.
- **`/ppisland capture <building|number|grounds>`** = a full compare of that section's area against the plan. WARNING: in
  a world generated from an OLDER layout this also picks up old-layout blocks and the random decor (palms, flowers, benches)
  that shifts whenever the layout changes - restamp that building first, or use capture all. (A blanket full compare of a
  test world found 4581 "edits", none made by hand - which is why capture all is tracker-based.)
- What is saved: the block state (properties that change by themselves - open, powered, lit, age, distance, fuel, snowy -
  don't count; grass<->dirt and flowing water are drift) and, for signs, banners, chests/barrels with contents, pedestals,
  lecterns, heads and named blocks, the block-entity data. `/ppisland edits <b|all>` lists, `/ppisland discard <b|all>`
  drops (then `restamp` puts the plan back). `/ppedittest` (op): a fake player changes 3 spots, then capture all
  (expect Park 2, Streets & Grounds 1).
- Saved to `<game dir>/pixelpirates/island_edits.txt` and, when running from the dev workspace (run/), ALSO straight into
  `src/main/resources/data/pixelpirates/island/edits.txt` - commit that file and every new world gets the edits. The layout
  reads the shipped file, then the local one (a building's local section replaces the shipped one), and applies the edits
  LAST in build() (`applyEdits`), so restamps, new chunks and LayoutCheck all include them. `PortCityLayout.planGet` = the
  plan without edits (what a capture compares against); `nbtAt` = the saved block-entity data (applied by the feature and by
  restamp via `IslandEdits.applyData`). A capture can grow the palette: `SpawnIslandFeature.paletteStates()` re-parses when
  the size changes.
- **Before I change a building you've hand-edited:** read its section in edits.txt first - the edits are tied to x/y/z, so
  a redesign can leave them pointing at the wrong spot. LayoutCheck sees them like any other block.
- Not captured: entities (item frames, armor stands, paintings, mobs), cells the plan never touches (deep terrain under the
  city, above y118). Verified on a server: capture (state + sign text), restamp restores them, survives a restart, discard; capture all saved
  exactly the 3 changes /ppedittest made (99 touched spots checked), survived a restart, cleared its list, discard all.

## BUILD CHECKER + BUILD RULES (2026-10-02)

**Run it after every layout edit, before the server/restamp step:**
`javac PortCityLayout.java SpawnIslandTerrain.java LayoutCheck.java Connections.java` (into a scratch dir, Java 17) then
`java -cp "<dir>;src/main/resources" net.get900.pixelpirates.world.gen.LayoutCheck <out_dir>` (resources on the classpath = the shipped hand edits are checked too) (~2 s). Writes `report.txt` (every issue by
building, with x y z) + `issues_map.png`. Last full report: `tools/previews/spawn/check/`. A building is "clean" when
its section is empty (palm DIAGONALs aside, until the palms are redrawn).

Checks (`LayoutCheck`, pure Java; `PortCityLayout.TRACK` turns on per-cell ownership in `set()` - off in the game):
- **FLOAT** - a group of blocks that touches nothing leading to the ground (water counts). **DIAGONAL** - joined only by an
  edge/corner (looks loose in game). Stairs/slabs joined across an edge count as connected (stepped roofs).
- **SUPPORT** - wall-mounted things (banners, signs, our boards/signs/racks, ladders, buttons, hooks) need a block behind
  them (= opposite their facing); hanging lanterns/chandeliers/nets/ropes/ceiling bells need a block above; standing
  lanterns, candles, carpets, plants, pots, rails need a SOLID block below (not a carpet, not an open trapdoor).
- **HALF** - doors, beds, tall plants must have both halves. **DOORWAY** - both sides of a door must be walkable (a bottom
  slab or stair counts as a step).
- **CLASH** - a block one building placed that a later writer replaced (`pass("Name", ...)` names every unlabelled pass:
  furnishing counts as its building, the city wall / countryside / greenery passes by name). Building over the ground
  passes (City Paving, Streets, Terraces) is expected; `LayoutCheck.INTENDED` lists overlaps that are the design.
- **CONNECT** (2026-10-03) - a fence / wall / glass pane / iron bar whose arms don't match its neighbours. The island is
  written without block updates, so the game never joins these up itself: a plain `spruce_fence` stood as a lone post
  (the user's screenshots: blackstone garden walls, stone-brick wall caps, fences). Fixed for good by
  `PortCityLayout.connectAll()` - runs before and after the hand edits, recomputes EVERY such cell (edits too: ~6% of the
  saved ones were stale - arms into air) with `Connections` (FenceBlock/WallBlock/PaneBlock rules in plain Java: wooden
  fences join fences, gates across the line and full sides; walls also join panes, tall sides under a covering block, the
  post only at ends/corners/junctions or under a lantern/torch). Joined up 7620 cells; CONNECT should stay 0. Checked
  against the game: the rules agreed with 94% of the fences/walls the game saved in edits.txt, the rest were stale states.
  "Full side" is guessed from the block id - a new mod building block that should take fence arms goes in
  `Connections.fullSide`.

**BUILD RULES** (each one came from a real bug the checker found - add to this list, and to the checker, as new ones turn up):
1. Every hanging thing hangs from something: use `hangLantern()` (it runs a chain up to the next block) - never a bare
   `LANTERN_HANGING` under open roof space or a lantern "string" with no anchor. Chandeliers/bells under a cupola or
   in a tall room need a chain to the dome/beam.
2. Anything on a wall is placed against a wall cell that actually exists at that height (check windows/openings).
3. Candles, pots and lanterns stand on a full or top-half block - not on carpet, not on a `shelf()` (open trapdoor is a
   vertical plate). Use a `slabTop` shelf when something must sit on it.
4. Nothing hovers at `floor + 2`: furniture on a floor goes at `floor + 1` (the manor urns were at MG + 2).
5. Global passes that run AFTER the buildings (city wall, countryside, meadow, greenery, decor) must check the cell is
   theirs to write (`getRaw(...) == 0` / air) before writing - including the block BELOW what they place.
6. A building's own grounds must not straddle the city wall line (z = -86 north) - keep gardens inside it.
7. Organic shapes (palms, anchors, flags) are drawn with face-connected blocks; diagonal-only steps read as loose cubes.
8. Moving or shrinking a building: keep its old footprint inside its label box (see the Smithy) so restamp clears it.
9. Don't hand-set fence/wall/pane connections - write plain `minecraft:oak_fence` and let `connectAll()` join it. Anything
   written AFTER build()'s last connectAll (there should be nothing) shows up as CONNECT.
