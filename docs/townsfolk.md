# Townsfolk - the living town (homestead/town, 2026-10-04)

The user's brief: "a lot of unique looking NPCs ... to the tavern to have beer and gamble, play chess against each
other, make their own paintings which they hang in their own houses - a real active town". Dialogue + shops only (no
quests yet). Ideas for later, marked by the user: `docs/town_life_ideas.md`.

## Files
- `Townsfolk` - the roster (36): id, name, title, bed (null = a room at the inn), work spot, WorkStyle, hours (work /
  sleep in day ticks), hobbies, Service, shop (trade list), lines. Also ROUTES (crier / lamplighter / sergeant), the
  children's scale, `attends()` (who goes to the service), Rufus's tales, Zora's fortunes.
- `TownsfolkEntity` - MerchantEntity + GeoEntity. Tracked FOLK id + ACT. Brain in mobTick every 10 ticks: phase ->
  plan (TownLife) -> travel (walk; TELEPORT when no player is within 40 of both ends, or after 200 ticks stuck and
  nobody close) -> arrive (sit via SeatEntity, sleep in the bed, the swing) -> perform. Invulnerable.
- `TownLife` - keeps ONE of each alive (PersistentState "pixelpirates_town": the owning UUID + last position; spawns
  near a player, a duplicate discards itself), plans, venue scans (seats, gaming tables, easels, swings, inn beds -
  cached once their chunks load), reservations (TAKEN), chess pairing (WAITING -> GAMES; after a minute alone they play
  the computer), painting steps + hanging, the chapel service, the crier's news. `/pptown` commands.
- `TownArt` - their procedural paintings (seascapes, sunset, storm, lighthouse, island, flowers), shown filling in row
  by row on the easel; finished ones go to `pending` and are hung on a wall near their bed (max 4 each, oldest goes).
- `TownTalk` - the dialogue card (client/screen/TownsfolkScreen): Chat, Trade, the service (barber/tattoo/cats open the
  existing screens via the nearest chair/counter; BIRDS sells the aviary cage by tier 15/30/60/120/250; FORTUNE 3
  coins; PARDON = Navy rep back to 0 for max(10, -rep/3) coins), Rufus's tale.
- `Lodging` - the market keepers (PortTraderEntity, MapMerchantEntity): booth by day, an inn stool from 13000, an inn
  bed from 16500 to 23500 (`TownLife.innBed`, stable per id).
- Models: `tools/mobs/townsfolk.py` (`python tools/gen_mob_roster.py townsfolk`). Every model has the bones kit_r /
  kit_l / tankard / cup / brush / palette, shown by activity in `homestead/client/TownsfolkRenderer`, and the clips
  idle move talk wave cheer flourish sit sit_drink sit_chess sit_pray sit_gamble stand_chess gamble paint organ preach
  sleep. Traders got a `sit` clip (traders.py `_with_sit`).

## Day (day ticks; 0 = 6 am)
Default: work 1000-11000 (they set off 1000 early), leisure, HOME 1500 before bed, SLEEP 16500-23500. Own hours:
Silas (nights), Hal (3000-17000), Harmonia (3000-9500), the children (bed at 14500), Ginny (dusk round 10500-13500).
SERVICE: every third day 1000-3400 (or `/pptown service`) the attendees leave work for the chapel.

## Places (island coordinates)
Tavern box 42..83 x, 15..46 z; chapel -40..-12, -88..-47 (lectern stand -28,73,-76, organ console -19,71,-78);
inn -77..-42, 13..44; easels: the painter's studio + 3 in the Gardens (`park()`); chess: table 101,71,-30 (stools
x100/102) and the Green (pedestal -30,79,-118, stands -31/-20,79,-114); swings in the Gardens.

## Round 3 (2026-10-05, from the user's marked-up idea list - `docs/town_life_ideas.md`; next list: `town_life_ideas_2.md`)
- `TownMemory` (PersistentState "pixelpirates_town_memory"): NEWS (gossip + the crier), FRIENDSHIP per player per folk
  (levels at 10/30/60/100; +2 first chat a day, +1 trades, gifts +3 / liked +8 / nasty -3 once a day, chess, dice;
  5/10/15% discount from Friend up; friends get waved at), dice records, the memorial roll, weddings, the boss party, the
  gallery, trophies.
- `TownEvents`: MARKET (day%7==3, Marco + a display-entity stall at stallPos), FESTIVAL (day%8==5 or the party day after a
  boss kill - `BossProgression.onBossKilled` hook; 128 display lanterns/bunting tagged pp_festival, fireworks via the
  town_fx packet -> client/TownFx), MUSIC night (odd days, Dan at TownEvents.STAGE), WEDDINGS (COUPLES on day%12==6 at
  5000; player weddings from Father Anselm's card), MEMORIAL (AFTER_DEATH at sea -> next morning 4000), RAIN shelter.
  The mode is part of each townsperson's plan key (TownsfolkEntity.planMode).
- `TownMusic`: tunes as {note, ticks} (Drunken Sailor, our own jig "Salt and Thunder", Amazing Grace, Ode to Joy, the
  Bridal Chorus) - the organ and Dan's fiddle (banjo sound) play through it.
- `TownPets` (pets tamed with no player owner, invulnerable, tagged pp_pet:<id>), `SeagullEntity` (custom flight; kept
  at 8 over the harbour by TownLife.gulls; `feed()` from Agnes), `StreetLampBlock` (LIT; Ginny's round lights them and
  converts lantern-on-two-fences lampposts; random ticks put them out by day / light stragglers after 15000;
  `PortCityLayout.lamppost` now places them), chess challenges (TownLife.CHALLENGES, Chess.npcStartVs; Commodore ->
  CHESS_TROPHY), Liar's Dice regulars (LiarsDiceBlockEntity.addRegular/over hooks), the gallery (TownLife.hangInGallery),
  Finn's FISH style, the children's tag (kept on PARK_LAWNS), ships in harbour (HarbourDues -> TownLife.harbourShips).
- New people: elias (surgeon, HEAL), lazlo (smuggler, nights; shares the quartermaster's inn bed by day), ashby + dobbs
  (gates by day) + hale + finch (night watch) - soldiers sleep in the GUARDHOUSE (Townsfolk.BED_POOL), tom + bella,
  marco (market days only - TownEvents.present).
- Test: `/pptown event festival|wedding|memorial|party`, `/pptown friend <id>`.

## The big-events calendar (2026-10-05) - `TownCalendar`
Market (d%7==3), festival (d%8==5) and weddings (d%12==6) stay put. Every EVEN day free of those is a big-event day, and
the five big events take them in turn: CHESS_TOURNAMENT, FISHING_CONTEST, HARVEST_FESTIVAL, GOVERNORS_BALL, REGATTA -
each every 12-16 days (avg 14), never two on one day (the user's "spread out"). `daysUntil()` for boards/the crier.

## The Chess League (2026-10-05) - `ChessLeague`
- Every rated game moves both Elo ratings (K 32) - hooked at the one place every chess game ends (`Chess.moved`), so
  player v player, player v townsperson and townsfolk games all count; the computer is unrated. Townsfolk seed at
  950/1150 by chess level (+-40 by name), players at 1000. PersistentState `pixelpirates_chess_league`.
- LEAGUE_BOARD on the Chess Green (-31,y+1,-112 facing east; `gen_chess_assets.py league_board()`): use = standings
  (top 10 + you, the champion starred, days to the next tournament); sneak + use (empty hand) = sign up / withdraw.
- THE TOURNAMENT (calendar day, from 12500): 4 entrants - signed-up players online (max 2, by rating) + the best-rated
  townsfolk who play chess; seeds 1v4 on the giant board, 2v3 at the toymaker's table (a missing/unloaded board -> that
  game waits for the giant board), final on the giant board. Players always take white v townsfolk; 2.5 min to sit or
  forfeit (getting up = forfeit); draw -> higher seed; townsfolk games over 7.5 min or past 17500 adjudicated by
  rating. Champion in the news + on the board; a player champion gets 30 coins, runner-up 10.
  Townsfolk in a game keep playing past bedtime (`ChessLeague.plan` comes first in `TownLife.plan`).
- Test: `/pptown league start|stop`. VERIFIED in a client 2026-10-05 (the refreshed test world): Ptolemy beat Aldous,
  Pettigrew beat Gideon (both semis at once), Ptolemy won the final - all real games. NOT tested: a player entrant.
- The test world `run/saves/ppshot` was a copy from 2026-10-04 with an old island; it was restamped (`/ppisland restamp
  all`) on 2026-10-05 - restamp it again after island changes before testing there.

## Finn's fishing contest (2026-10-05) - `FishingContest` + `mixin/FishingContestMixin`
- Calendar day, 1000-12000. Players: every fish reeled in within 320 blocks of the island is weighed by species
  (`weigh()`, the roll squared so big ones are rare; cod 1.5-14 lb, salmon 3-22...), the weight goes on the fish's lore,
  and the angler's best is entered (their place on the action bar). The hook: `@ModifyArg` on the FISHING_ROD_HOOKED
  trigger in `FishingBobberEntity.use` (ordinal 1 = the loot branch).
- Finn + Hob, Ned, Jack fish the quay all day (`SPOTS`, before every other plan), a bite now and then, heavier for the
  better anglers. A new leader is called out to the pirate world; Finn's chat gives the top three.
- Sundown: the winner in the news; a player winner gets the GOLDEN MARLIN TROPHY + 20 coins.
- Test: `/pptown event fishing` (moves the clock into the window). VERIFIED in a client: start, Finn leading, beating his
  own best, the sundown result; the mixin applies. NOT tested: a player's catch being weighed (needs a real cast).

## A ship limps in (2026-10-05) - `LimpingShip`
- Quiet days only (no market/festival/wedding/big event), 1 day in 10 at 3000, or `/pptown event wreck`: a smoking
  merchant_lugger at 1/3 hull crawls from (20,285) to a berth at (20,118) between the grand pier and the east pier
  (scripted AI mode, Keelbreaker II), anchors; Dr Marrow, Martha, Finn, Hob work from the pier edges, the town crowds the
  quay, hearts over the ship for 2 min; "Help with the wounded" on Dr Marrow's card takes up to 8 Sea Bandages (5 coins
  each + Merchants rep). Then she sails out and is despawned. VERIFIED once in a client (whole cycle, but she reached the
  berth only by the timeout) - the second run (faster: skill 0.6, closer start) was NOT finished.
- VS2 ship load/unload distance raised to 320/384 blocks at server start (`world/ShipSimDistance`; the defaults 128/196
  dropped ships coming into port, racers and broadsides). NOT yet verified in game.

## The Governor's Ball (2026-10-05) - `GovernorsBall`
- Calendar day. MORNING (1000): every player with Iron Armada rep >= 100 gets an INVITATION card (paper, NBT
  `GovernorsBallInvitation`; rep >= 100 also counts without it). EVENING 12500-16000 in the Residence BALLROOM (#17 east
  wing): the guests take their places (`PLACES` - the Governor at the head, the Commodore, Ashby, Anselm, Agatha,
  Martha, Rufus along the walls), the quartet (Dan, Harmonia, Quill, Silas) in the musicians' corner, seven couples on
  the parquet (`COUPLES`, two columns facing their partners) - from 12800 the waltz (`TownMusic.WALTZ`, after the Blue
  Danube, public domain, harp) and the couples DANCE; at 13600 the Governor's speech + toast: every invited player in
  the room +25 Armada rep and +3 friendship with the guests (once). News the next day.
- The Residence guards (Garrison) turn away anyone WITHOUT an invitation during the ball, politely ("invitation only
  tonight") - wanted players get the usual treatment.
- Test: `/pptown event ball`. VERIFIED in a client: invitation, the room full, waltz + dancing, speech, the toast.

## The harvest festival (2026-10-05) - `HarvestFestival`
- Calendar day. MORNING 2000-5000: Hob (wheat), Elspeth (fleece), Cobb (cider + pumpkin), Wilma (flour + melon) walk to
  the chapel and add to the display before the altar (display entities), each in the chat + the news. ALL DAY until
  the feast: "Give to the harvest" on Father Anselm's card takes up to 16 of the food in your hand - +2 friendship with
  every townsperson, once a day. EVENING 12500-15500: two laden trestle tables either side of the bazaar's fountain
  (x -30..-12 / 12..30, z 30), everyone eats there (a place by name, Dan fiddling at the head of the east table,
  Anselm by the fountain), grace at +200: every player within 30 gets 2 pumpkin pie + 2 bread; donors also Regeneration
  (2 min) + Saturation. All cleared at the end.
- `TownLife.replanAll(w)`: an event starting MID-PHASE (the feast at 12500 - leisure began at 11000) must tell the
  townsfolk to re-plan, or nobody comes; the regatta does the same (and sends the skippers to their helms).
- Test: `/pptown event harvest` (deliveries now - the bringers are walked over - the feast a minute later).
  VERIFIED in a client: the four deliveries + the altar display, the tables, grace, the supper, the town at table.

## The regatta (2026-10-05) - `Regatta` + AiShipController racing mode
- Calendar day, 5 pm (`/pptown event regatta`, `/pptown regatta stop`). The town's three boats line up ABREAST on the
  start line off the grand pier (x -24/0/24, z 215, bows +Z at the turn): Commodore Pettigrew's HMS Swift (navy_cutter),
  Finn Gale's Herring Lass (merchant_lugger, Coral Reef livery), Lazlo Quick's Midnight Eel (pirate_cutter, Midnight
  Raider). No faction crew - each skipper stands at the helm (`Regatta.skippers` keeps them there; ashore at the end).
  Keelbreaker II on all three. Players whose own ship is within 150 of the start at the gun race too.
- Course: out to the TURNING BUOY (0,295), back across the line between the finish posts (x +-42, z 212). Block-display
  buoys + red glow; calls in chat (rounding order, finishing places), player position on the action bar; 40/15/5 coins.
  The town watches from the quay edge and the grand pier, cheering.
- AiShipData.racing: full sail for raceTarget, no targets/guns/idle despawn, never targeted by other AI, never dropped
  as "missing" (a just-assembled ship can be absent from VS2's id map). Racers spawn with `ShipSpawner.spawn(.., margin 6)`
  - the normal 50-block clearance would not allow side by side.
- STEERING FINDING (measured from the logs, all three cutters): a POSITIVE turn input swings the bow AWAY from the side
  `cross2d(fwd, toTarget)` points to - `raceInputs` flips it. GhostShipEncounter found the same for the Dutchman.
  The ordinary AI (`computeInputs` APPROACH/BROADSIDE/RETREAT) does NOT flip - check this first when balancing the ship
  battles (they may be turning away from their targets).
- Deep water: `SpawnIslandTerrain` DEEP_* - beyond r 1.58 (clear of the piers) the floor drops to y50, back to the zone
  floor by r 2.4. New chunks only.
- VERIFIED in a client 2026-10-05: three ships abreast, all rounded the buoy, the Midnight Eel won. Test world note: the
  `ppshot` test world is restored from `build/tmp/claude/ppshot_clean` before each run (`fresh_ppshot.sh`) - killing a
  test client mid-run left stale VS2 ship data that stopped new ships loading.

## Fort security (2026-10-05)
- New guards (models `tools/mobs/townsfolk.py`, Governor's Guard in Armada blue via `soldier(coat=GUARD_BLUE)`):
  pell + quayle (the Residence gate sentry boxes 25/35,71,-51), crane (the guardroom duty desk 22,71,-67), ruddock
  (the fortress gate passage -81,89,-120). Day posts, sleep in the guardhouse.
- `Garrison` (ticked from TownLife): a player with Iron Armada rep < 0 on the Residence grounds or in the Fortress is
  told "Halt!" by the nearest guard on duty (awake, within 48), then teleported out of the gate 100 ticks later.
  Asleep/absent guards = no challenge (sneaking in at night works). The Governor's PARDON ends it.
- VERIFIED in a client 2026-10-05 (scripted, Pell at his post): Halt -> 5 s -> marched out, no repeat warning.

## Verified
Dedicated test server (2026-10-04): spawn, the whole day cycle, tavern seats + den tables, chess pairs on both boards,
easel paintings hung at home, the service (preacher, organist, pews), market keepers at the inn + in inn beds.
Round 3 on the test server (2026-10-05): festival (Dan fiddling, dancers, 128 decorations), a wedding (couple at the
altar, priest, organist), a memorial, rain shelter, Ginny lighting + converting lamps, the night watch, the smuggler,
chess between the guards. (Gulls, the crier, kids approaching and friendship need a player - untested.)
NOT seen in a client yet: the models in game, the dialogue card, props showing/hiding, sit/sleep poses, the swings.
