# Homestead, tavern games, chapel blocks, the Chronicle

> Topic doc split out of CLAUDE.md on 2026-10-02. The root CLAUDE.md (always loaded) holds the global rules;
> this file holds the detail for its topic. Keep it current when you change this area.

**When:** Read before touching anything in `homestead/`, the Chronicle, or the tavern/chapel blocks.

## HOMESTEAD (2026-09-30 overnight build) - base building, economy and survival

All in `homestead/` with its own registries (`HomesteadBlocks/Items/BlockEntities/Entities/Effects`, `Homestead.init()`
from `PixelPirates` after `registerModBlocks`, `client/HomesteadClient.init()`), creative tab, datagen providers
(`homestead/datagen/`: model / loot / recipe; tags merged via `HomesteadTags`), `HomesteadState` (PersistentState
"pixelpirates_homestead": hideouts, discoveries, bounties, world flags). Full feature list + what is unverified:
**`OVERNIGHT_PROGRESS.md`**.
- Assets: textures `tools/gen_homestead_textures.py` (one `build_*` per feature), shaped block models
  `tools/homestead_models.py` on `tools/blockmodels.py` (isometric previews in `tools/previews/homestead/`), lang
  `tools/homestead_lang.py`, check `tools/check_assets.py`. GeckoLib: `tools/mobs/traders.py` (7 trader models + map_merchant),
  `hideout.py` (jolly_roger), `roulette.py` (roulette_table), `guns.py`. GeckoLib blocks have a particle-only block model
  and a flat item sprite written by hand into resources/.
- Server tests: `build/tmp/claude/drive_homestead.py <testserver> <script>` (`t_batch1..4.txt`).
- Mixins: HomesteadDropsMixin, KrakenMiningMixin, SalvageHookBobberMixin, CaptainsSpyglassMixin (spyglass zoom for our
  item), HideoutSpawnMixin (no NATURAL monster spawns in a claim), ParrotShoulderMixin + PlayerShoulderInvoker; client
  TipsyCameraMixin.
- Hooks into existing code: `FactionManager.onShipDestroyed/onCaptainKilled` -> `Bounties.onShip`; `CannonBlock` loads
  `Shot.of(stack)` ammo, `CannonBlockEntity` NBT `Ammo`, `CannonBallEntity.setChainShot()`; `ModSpawnEggs` port_trader egg.
- Roulette: the wheel is spun in `RouletteRenderer` via `GeoModel.setCustomAnimations`; bone rotY = -i*2pi/37 rests
  pocket i under the marker (measured in preview_geo) - keep `POCKETS` in roulette.py and the block entity identical.
- Port traders + the Map Merchant are seated in the bazaar booths (`PortTraders`, flag "market_v2") - see SKILLS, MERCHANTS + THE BAZAAR.


## THE CHAPEL'S WORKING BLOCKS (2026-10-01) - `homestead/chapel/`

- **CALL TO PRAYER = `ChapelBells`** (the NPC hook): every BELL_ROPE (`ChapelBlocks.BellRope`, block entity) tolls the
  vanilla bells above it (40 up, 3 sideways; `BellBlock.ring` + a loud toll) 7 times every morning at time-of-day 1000
  (~7 a.m.), tells players within 160, logs `[Chapel]`, and fires **`ChapelBells.PRAYER_CALL`** (Fabric Event: `(world,
  bellRopePos)`). Also `isPrayerTime(world)` (true for SERVICE_TICKS = 2400 after the call) and `chapels(world)`
  (registered when a rope's BE ticks). Players pull the rope for one toll (ops: sneak-pull = a 3-toll peal + call).
- **THE ORGAN**: ORGAN_CONSOLE (use = client `OrganScreen`, opened through `OrganConsole.CLIENT_OPEN`) + ORGAN_PIPES (TOP
  crown course auto-set). Keyboard F#3..F#5 (25 keys, note-block range), mouse or keys Z S X D C V G B H N J M , / Q 2 W 3
  E R 5 T 6 Y 7 U I; stops Diapason (synthesized `block.organ.diapason`, tools/gen_chapel_assets.py sound) / Flute /
  Chimes; "Play a hymn" = Amazing Grace (public domain). `OrganNotes`: C2S organ_note / organ_hymn, played server-side at
  the console for everyone (RECORDS category, rate limit 24/s).
- **Pews**: CHAPEL_PEW (SeatBlock: sit), LEFT/RIGHT arm rests join neighbours of the same facing (getStateForNeighbor-
  Update; layouts set them explicitly). Decor: CHAPEL_ALTAR, ALTAR_CROSS, WALL_CROSS, CANDELABRA (light 13), VOTIVE_RACK
  (11), BAPTISMAL_FONT, HYMN_BOARD, MEMORIAL_PLAQUE ("lost at sea"), VOTIVE_SHIP. Assets `python tools/gen_chapel_assets.py`
  (sound/tex/gui/models/lang; previews tools/previews/chapel/).
- NOT verified in a client: the organ screen + sound, sitting on pews, how the blocks look.

## THE GROG BARREL'S GAMES + DRINKS (2026-10-01) - `homestead/tavern/`

- **Game tables** = `GameTableBlock` (FACING, static BM model) + a `GameTableBlockEntity` subclass. Right-click = become a
  WATCHER and open the screen; every change goes to every watcher as one per-viewer NBT packet (`TavernGames.STATE`,
  hidden dice stay hidden), screens answer with `TavernGames.ACTION` (pos, action, a, b; "close" stops watching, reach 10).
  Stakes are PIRATE COINS (`TavernGames.take/pay`; creative plays free). State (seats, dice, pot, stakes, log) is saved.
- **LIAR'S DICE** (`LiarsDiceBlockEntity`, screen `client/screen/LiarsDiceScreen`): up to 6 seats - players + TAVERN
  REGULARS (bots: One-Eyed Jack, Salty Sal...). Host = first to sit; sets the ante (0/5/10/25/50) while alone, adds/removes
  regulars, starts. 5 dice each under a cup; raise (more dice, or same count of a higher face) or call LIAR; the cups lift
  (REVEAL 5.5 s), the wrong side loses a die; last seat with dice takes the pot (house matches the ante per regular; a
  regular winning keeps it). Turns time out at 30 s (auto minimum raise / call). Regular AI = binomial estimate of the bid
  from its own dice (calls under ~30-45%, bluffs on 20%). Leaving mid-game forfeits.
- **CROWN & ANCHOR** (`CrownAnchorBlockEntity`, `CrownAnchorScreen`): 6 symbols, stake 1/2/5/10/16 per click (64 max per
  symbol), everyone at once; the first stake opens 15 s, any bettor can hurry the throw; 3 dice, each match pays the stake
  again and you keep it. "Take back" before the throw refunds.
- Screens share `TavernGameScreen` (felt panel atlas `textures/gui/tavern_games.png` 512x256 - coordinates fixed in the
  screens; log strip; countdown counted down locally; `drawOver` draws icons on top of buttons).
- **Decor:** TAVERN_SIGN (hanging "The Grog Barrel" board, 64x32 painted texture), DRINKS_MENU (chalkboard), TANKARD (1-3,
  place more on it) + SPIRIT_BOTTLES (1-4) (`TavernDecor.Stack`, drop their count), TAVERN_KEG (drink=0 ale/1 mead/2 wine:
  use with a DOUBLOON = one serving, sneak-use changes the drink), DICE_CUP. Assets: `python tools/gen_tavern_assets.py`
  (textures, GUI atlas, BM models + blockstates, lang; previews tools/previews/tavern/).
- **Drinks** (`TavernDrinks`, all TIPSY like rum): Tankard of Ale (Haste, gives the tankard back), Honey Mead (Regen),
  Spiced Wine (Fire Res), Bilge-Rat Whiskey (Resistance + nausea, Tipsy +2), Kraken's Kiss (aged rum + kraken ink + lime:
  water breathing + night vision, Tipsy +2). Recipes in HomesteadRecipes; the Barkeep sells ale/mead/wine/whiskey/tankards
  (OFFERS_VERSION 4).
- Test: `/pptavern liars <pos> <2-6>` = a bots-only game at pos (places a table); `/pptavern status <pos>`. Verified on a
  server: a 3-regular and a 2-regular game run start -> bids -> calls -> reveals -> dice lost -> winner -> lobby, no
  exceptions. NOT verified in a client: both screens, joining/betting with real players, payouts, the decor models.

## DARTS (2026-10-05) - `homestead/darts/` + `homestead/town/TownDarts`
- DARTBOARD block (wall, `FurnitureBlock` facing; back plate + board, its face `Darts.FACE_DEPTH` 2.5 px out) and DART
  item (3D model `models/item/dart.json`, tip +Z; `special()` so datagen leaves it alone). Recipes: board = dark oak +
  black/red wool, 4 darts = iron nugget + stick + feather. Assets: `python tools/gen_darts_assets.py` (the 128px face is
  drawn from the scoring geometry - keep `R_*`/`ORDER` in step with `Darts.java`). Placed in the Grog Barrel's den
  (75,69,36 facing west) and the inn's games snug (-52,69,41 facing north) - `/ppisland restamp 7` / `8` in old worlds.
- `Darts`: rings in board px (game-sized: bull 0.6, outer 1.3, treble 3.6-4.3, double 6.2-7.0), segments clockwise from
  20 as the thrower sees them; `aim(left)` (finishing rules) + `scatter(skill)` for townsfolk.
- `DartboardBlockEntity` runs 301 (exactly 0 wins, below 0 = bust, no double-out): use the board to start - a regular
  (GAMBLE/DRINK hobby, drinking/gambling/sitting within 24) is recruited and walks to the oche (3 blocks out), else
  practice; other players join by using it in the lobby; sneak+use to leave. Players' darts must be thrown from >= 2
  blocks on their turn and come back after it. Dart calls on the action bar, 100+/180/bust/checkout in chat.
- `DartEntity`: player darts fly with light gravity and stick in any block (no entity hits); townsfolk darts fly
  straight to the scattered point. Both end in `landed()`. Free darts (no game) can be picked up.
- Townsfolk: Act.DARTS + the "throw" clip (all 49 regenerated). Leisure GAMBLE: join a regular waiting at a board, or
  (1 evening in 3) go and wait; skill per person (`TownDarts.SKILL`: Brannoc 0.9, Lazlo 0.85, Rufus 0.8...). Wins make
  the news; playing them adds friendship. Test: `/pptown darts <a> <b>` (nearest board).
- VERIFIED in a client 2026-10-05: Brannoc v Rufus played to a checkout (134 turn, a bust, the win), darts stuck the
  right way round. NOT tested: a player throwing (aim/turns/darts handed back), the inn board, leisure pairing.

## THE WEATHERED CHRONICLE (2026-10-01) - the lore book (replaced the Captain's Logbook)

Item `weathered_chronicle` (`item/custom/ChronicleItem`). Everyone gets one the first time they reach the PP dimension
(`world/Chronicle.giveStarter`, HomesteadState flag `chronicle:<uuid>`, also covers old players on their next join);
recipe = the old logbook's (feather, ink sac, book, compass). Right-click -> server sends `S2C_CHRONICLE` (boss step,
reveal-all flag, name, level, fish caught, charted sites, bounties, hideout, faction standing) -> `client/screen/ChronicleScreen`.
- **The words are data:** `assets/pixelpirates/chronicle/tabs.json` (tab ids + labels) and one `<id>.json` per tab: a flat
  element list (`title`, `h` (+`icon` item id), `p`, `quote`, `img` (+`w`, `caption`), `rule`, `space`, `row` (+`icon`),
  `torn`, `page: next|spread`, `boss` (portrait page: `name`, `epithet`, `lair`, `sea`, `portrait`, `hint`), `dyn: hunt|fish|
  captain|treasure|standing`). Any element takes `"if": "met:N" | "slain:N"` (or `!`, several joined with `&`): boss N's pages appear when boss
  N-1 dies (next fight readable in advance); a sealed page only NAMES the boss before it once that one is known too
  (torn stubs come in pairs: `!met:N & met:N-1` named, `!met:N-1` vague); slain = red X + skull stamp on its portrait. The screen flows elements onto
  pages (keeps short paragraphs/quotes whole, no widow lines, headings keep with the next lines, empty pages get a faint
  compass). Keys: arrows/keypad/PgUp-Dn/scroll turn, 1-6 pick a tab; the Hunt index is clickable.
- **Test:** `/pptest chronicle [on|off]` (also in `/pptest all`) reveals every page (stamps still show real kills).
- **Art:** `python tools/gen_chronicle_art.py [book|art|painted|icon]` -> `textures/gui/chronicle/book.png` (spread +
  parts atlas at 2 texels/GUI px; B_* constants in the screen) and `art/*.png`. PAINTED art (2026-10-01) = the user's
  ChatGPT images from `D:\Minecraft Modding\Chronicle Art - Image Prompts.txt`, masters kept as JPEG in
  `art_src/chronicle/gen/` (not shipped): `boss_<id>` portrait cards on the boss pages, `place_<lair>` at the top of each
  boss briefing (+ wavebreak in World, rackhams_hold in Seas), `sea_1..5`, `chapter_<tab>` openers (paper dissolved into
  the page), `cover` -> item icon. 3 texels/GUI px, 256-colour dithered PNG (whole folder ~3 MB), drawn with LINEAR
  filtering (`ChronicleScreen.drawArt`). Paintings on a white surround are flood-keyed from the edge; full-bleed ones get
  the generated torn edge. A few concept-sheet crops remain (seascape, compass, flags, creature sketches, items).
  `sketches` = the old model-render ink portraits (sketch_<id>.png, unused).
- Photo check: `build/tmp/claude/make_chronphoto.py <CUE>` + `shoot_chron.ps1 <CUE>` (every spread of every tab).
  Verified in a real client 2026-10-01 at boss step 3.
- **Dimension Key is consumed on use** (both directions, creative too); entering still packs the inventory into barrels.

- **The forge** (`homestead/forge/`: hearth, anvil, bellows, pattern board, smith's hammer) is documented in `docs/gear.md` (THE FORGE).

- **Props (2026-10-02, `python tools/gen_props_assets.py`, previews tools/previews/props/):** TELESCOPE (HomesteadBlocks,
  `furniture/TelescopeBlock` - a brass tube on a tripod that points the way you face when you place it; recipe glass pane +
  2 copper + 3 sticks), SEA_CHART (a chart framed for the wall; 8 sticks + a map), and new 3D models for two old ModBlocks:
  `anchor_block` (ring, wooden stock, shank, arms + flukes; now has FACING and is not a full cube - it still drops/raises a
  ship's anchor) and `map_block` (an open chart lying flat: brass weights, a rolled chart, dividers, inkwell + quill; a
  2px-high FurnitureBlock). Texture `block/sea_chart` (32x32 hand-drawn chart) + `block/parchment`. In the spawn island the
  seven fence-and-lightning-rod "telescopes" became real ones, and every chart WALL (harbourmaster, dock office, shipwright
  hall, the captain's chart room, the fortune teller's star chamber) now hangs SEA_CHARTs on a real wall. Existing worlds:
  restamp #5, #8, #11, #15, #17, #21, #23 (old map-block walls would otherwise show as flat charts with holes). Not seen in
  a client.

## PARROTS (`homestead/parrot/`, 2026-10-03) - full detail in `docs/parrot_ideas.md`
ParrotTypes (15 types: vanilla 5 + 10 custom, tier + weight), ParrotTypeHolder + mixins ParrotTypeMixin (PPType tracked +
NBT), client ParrotTextureMixin / ShoulderParrotTypeMixin / ParrotGlowFeature; ParrotCompanion (shoulder powers + the ACTIVE
parrot: left shoulder > right > nearest follower, one at a time), ParrotAbilities (Kraken save, Kakapo, Ember, Ghost
possession + MobPossessionMixin; parrots away per type in HomesteadState.parrotsAway), ParrotCollection (unlocks in
discoveries "parrot:<id>", one of each, the Roost packets) + ParrotRoostBlock + client ParrotRoostScreen, ParrotCrateItem,
Aviary (daily restock by rarity, announces every rare+ bird). Tests: `/ppparrot <type>`, `/ppaviary`.
