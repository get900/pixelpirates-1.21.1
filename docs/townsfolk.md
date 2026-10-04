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

## Verified
Dedicated test server (2026-10-04): spawn, the whole day cycle, tavern seats + den tables, chess pairs on both boards,
easel paintings hung at home, the service (preacher, organist, pews), market keepers at the inn + in inn beds.
Round 3 on the test server (2026-10-05): festival (Dan fiddling, dancers, 128 decorations), a wedding (couple at the
altar, priest, organist), a memorial, rain shelter, Ginny lighting + converting lamps, the night watch, the smuggler,
chess between the guards. (Gulls, the crier, kids approaching and friendship need a player - untested.)
NOT seen in a client yet: the models in game, the dialogue card, props showing/hiding, sit/sleep poses, the swings.
