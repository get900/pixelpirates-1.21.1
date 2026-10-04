# Gameplay systems: galley, advancements, XP, skills, merchants, economy, loot

> Topic doc split out of CLAUDE.md on 2026-10-02. The root CLAUDE.md (always loaded) holds the global rules;
> this file holds the detail for its topic. Keep it current when you change this area.

**When:** Read before touching food, advancements, PirateXp, skills, port traders, the bazaar, currencies, loot tables, creative tabs, strongboxes, bounties.

## PLAYTEST FIXES + THE GALLEY + ADVANCEMENTS (2026-09-30 evening, while the user playtested)

- **RELIC WEAPONS** (`item/RelicWeapons`, `item/relic/`, models `tools/gen_weapon_models.py`, item display
  `tools/gen_weapon_item_models.py`): each boss relic + materials -> that boss's weapon (shapeless recipes); the weapon
  anywhere in the inventory gives its relic's passive (`BossProgression.relicActive` also accepts the weapon); shift +
  right-click = locate the next lair. Guns tap = fire / hold 1 s = special; trident tap = Riptide Charge / hold = throw
  (`ThrownRelicEntity`, returns); longbow full draw = `TideArrowEntity`, 2 s = 3-arrow volley; melee right-click = ability.
  `/ppweapontest` (op) = a fake player uses all 10 on a row of zombies.
  **3D ITEM DISPLAY TRANSFORMS ARE COMPUTED** by `tools/gen_weapon_item_models.py` (all 10 relic weapons + gallowbrand +
  flintlock_pistol + blunderbuss) from the real hand frames - never guess them: third person hand frame = +X outward,
  +Y FORWARD, +Z UP (vanilla sword handle at (0,-2,1.5) px); first person = camera frame, handle at (1.1,-1.3,-0.5);
  rotation order Rx*Ry*Rz; GeckoLib mirrors geo X. Per-weapon knobs: GUNS grip point, MELEE_GRIP, FACE_Z (broad face in
  the XY plane, e.g. the trident), FP_CAP. Photo-verified 2026-09-30 (`build/tmp/claude/make_weaponphoto.py <CUE>` +
  `shoot_weapons.ps1`: armor stands, frames, drops, first person, and HOLD = right-click held).
  **WEAPON + ARMOR ANIMATION (2026-09-30):** weapon models (tools/gen_weapon_models.py) are split into moving bones with
  an "idle" loop + one-shot action clips; `item/relic/WeaponAnims` gives each item an "idle" controller and an "action"
  controller whose clips are TRIGGERED server-side (`WeaponAnims.play` -> GeoItem triggerAnim; items call
  `SingletonGeoAnimatable.registerSyncedAnimatable` and get a GeckoLib id in inventoryTick). Clips: guns "fire"/"special"
  (+ flintlock/blunderbuss "fire", tools/mobs/guns.py), melee "ability", trident "dash". Per-frame poses live in
  `RelicWeaponRenderer.Model.setCustomAnimations` (runs AFTER the clips - preRender is too early, the clips overwrite it):
  the LONGBOW's draw from the real pull of whoever draws that stack (bones upper/lower_limb, string_top/bottom, arrow;
  signs = the "draw_full" preview clip, keyframe X a == setRotX(-rad a)) and the HEARTSEEKER's heart on the real beat
  (world time % BEAT). Armor: `tools/armor/kit.py` gives every loose part its own bone (`moving`/`hang`: capes, strands,
  tentacles, hanging chains, blades >= 4 long, fringe, skirts) and writes an "idle" sway; `PixelArmorItem` plays it,
  faster with the wearer's speed (DataTickets.ENTITY). Photo check of a drawn bow: the hotbar icon shows the pose too.
  **Never give a non-crossbow item UseAction.CROSSBOW**: vanilla's first-person code only positions that action for real
  crossbows, so any other item is drawn AT THE CAMERA while used (the guns covered the screen). Guns use NONE.
- **Raft pirates** were the only MONSTER-group mob that could spawn on open water, so they filled the monster cap: weight
  3 -> 1 and `MobSpec.sparse(96, 1)` (natural spawns refused while one is within 96; generic, `ModMobs.canSpawn`).
- **Helm dismount** threw players back to where they took the wheel (the seat is in shipyard space and the server copy
  of the rider never moves): `world/HelmDismountGuard` checks for 10 ticks after leaving a `ShipMountingEntity` and puts
  the player on the helm's CURRENT world position if >4 blocks off. Not reproducible headless - verify in a client.
- **Bananas**: 4 hunger, snack, Regen II 4 s; the bunch (`HangingFruitBlock`, RIPE) is picked with right-click (2-4),
  turns green and ripens on a 1-in-8 random tick; breaking a ripe one drops 2-4, an unripe one nothing. Coconut 3/0.4.
  Early healing: Roasted Banana (cook), Coconut Water (coconut + bottle: +2 hearts, cures poison), Sea Bandage
  (`SeaBandageItem`: paper + string + kelp -> 2; hold 1.5 s: +3 hearts, regen, cures poison, 4 s cooldown), Island Skewer.
- **THE GALLEY** = `item/food/PirateFoods`: 40 dishes, one `dish(...)` line each (food, container, effects, cures, recipe
  or cooked-from) -> items (`PirateFoodItem`: returns ONE bowl/bottle per serving, tooltip lists effects/cures), models,
  recipes (ModRecipeProvider loop), creative tab. Icons + names: `python tools/gen_food_textures.py` (templates bowl/
  bottle/skewer/pie/slice/cookie/plate/fillet/taco/jerky/crisps/pudding; preview tools/previews/food_sheet.png).
  Zone fish dishes carry that zone's survival effect (ember eel = fire res, anglerfry = conduit, bone broth cures wither).
  Homestead stews switched from StewItem to `BowlFoodItem` (StewItem swapped a whole 16-stack for one bowl).
- **ADVANCEMENTS** are generated: `python tools/gen_advancements.py` (writes data/pixelpirates/advancements + lang,
  deletes the token-era/zone ones). Branches: seafaring, survival & galley, the boss chain (`boss_<chain id>` granted in
  `BossProgression.onBossKilled` for every credited player; relic weapons, boss armor, Gallowbrand hang off it), life
  ashore (homestead), the merchant. Code-granted ids use trigger impossible + criterion `got_here` (AdvancementHelper).
  Root uses minecraft:tick so the tab is always there. The old "3 sharks unlock the next zone" placeholder was REMOVED
  (it let players skip the Ghost Captain).

- **PIRATE XP SOURCES** = `world/PirateXp` (before: only vanilla hostiles, sharks, pillagers and captains paid - roster
  mobs are PathAwareEntity, so none of the 39 or the bosses gave anything). Roster kills by phase 10/18/28/40/55 (neutral
  half, passive 0, non-chain bosses 400); chain bosses 500 + 500 x step to EVERY credited player (re-kill 20%,
  `BossProgression.onBossKilled`); advancements task 40 / goal 120 / challenge 300 (`mixin/AdvancementXpMixin`, pays once,
  not for boss_*); opening a loot chest (`mixin/LootChestXpMixin`: hoards 250, treasure 80, other mod chests 40, vanilla
  15); first taste of each galley dish 25; ship sunk 250 / captain boarded 400 (FactionManager); floating barrel 10.
  Level 50 = ~63,700 XP. Discovery XP gets the Explorer skill bonus.
- **Sharks never spawned naturally**: SharkEntity had no `canSpawn(WorldView)` override (the default rejects water) -
  fixed like the roster swimmers.

- **CREATIVE TABS** = `item/ModCreativeTabs` (replaced ModItemGroups, the Homestead tab and the vanilla-tab additions):
  12 tabs (Ships & Seafaring, Weapons & Tools, Armor, Relics & Legends, Galley & Drink, Loot & Materials, Building Blocks,
  Islands & Farming, Homestead & Furnishing, Dungeon & Boss Blocks, Creatures, Testing Tools). SELF-SORTING: every
  pixelpirates item is placed by `classify` (explicit id lists first, then item/block type), so new items never go
  missing - only add an id to a `put(...)` list when the automatic guess is wrong. `/pptabs` logs each tab's contents.
  Spawn eggs are also still in the vanilla Spawn Eggs tab.

## SKILLS, MERCHANTS + THE BAZAAR (2026-10-01)

- **SKILL TREES (redesigned):** `world/PirateLevelingSystem` - SIX trees x 6 skills (BRAWLER, CANNONEER, NAVIGATOR, MERCHANT,
  SURVIVOR, MONSTER_HUNTER). `SkillDef(key, name, tree, maxLevel, tier, bossGate, icon, descAt)`; tiers need 0/0/5/10/15
  points in the tree (`TIER_POINTS`), rank 4 needs 2 bosses beaten, rank 5 needs 5, each capstone needs a chain boss
  (`blockedReason`). ALL effects live in `world/SkillEffects` (damage in/out via BossArmorDamageMixin, ALLOW_DAMAGE hooks,
  tick, onKill + numeric helpers called from cannons/guns/ships/traders/loot). Monster Hunter: beast_slayer, trophy_hunter
  (re-rolls the kill's loot table), tracker (glow), monster_lore, boss_hunter, apex_predator (gate: boss 6). Skills save by
  KEY (NBT "PPSkills"); old index-array saves are refunded. Respec costs 10 coins. `/ppskills level|learn|reset`.
  Screen `client/screen/PirateSkillScreen` (journal, keys 1-6 = trees), art `tools/gen_gui_textures.py` (journal.png).
- **SHIPWRIGHT:** 3D drafting-table block (`tools/gen_shipwright_model.py`, FACING) + blueprint-ledger screen (shipyard.png).
- **MERCHANTS:** `PortTraderEntity.Kind` = QUARTERMASTER, FISHMONGER, BARKEEP, CURIO_DEALER, GUNSMITH, CHANDLER, COOK - each
  its own GeckoLib model/texture/clips `trader_<kind>` (`tools/mobs/traders.py`, also map_merchant; clips idle/move/talk/
  flourish; talk on trade open, flourish every 15-30 s), its own stock, tethered 2 blocks to its booth. The Map Merchant
  got the same clips + a home tether and is invulnerable.
- **TRADE SCREENS:** port traders keep vanilla's MerchantScreen but `mixin/MerchantScreenSkinMixin` swaps its texture for
  `textures/gui/merchant.png` (vanilla villager2 repainted pixel for pixel by `gen_gui_textures.py merchant()` - reads the
  vanilla PNG out of the loom client jar) when the screen opens within 3 s of right-clicking a port trader
  (`PortTraderEntity.clientOpenedAt`); `PressableWidgetSkinMixin` draws the 88x20 offer buttons from the same atlas. Vanilla
  villagers are untouched. The Map Merchant has a custom sea-chart screen (`MapMerchantScreen`, chart.png): 3x3 cards,
  Haggler prices, click to buy - keep ENTRIES in the server's `SHOP_COSTS` order.
- **THE WAVEBREAK BAZAAR:** `PortCityLayout.marketSquare()` rebuilt - 8 themed booths (`booth()` frame + one dresser per
  keeper; `MARKET_BOOTHS` / `boothStand(i)` = keeper spots, index = Kind ordinal, 7 = Map Merchant), grand fountain, flag
  masts + lantern strings, palms, benches, cart, bounty board, bell. `PortTraders.seatAll` MOVES existing keepers (and the
  old spawn-plaza Map Merchant; the SERVER_STARTED merchant spawn is gone) to their booths and spawns missing ones - once per
  world (flag "market_v2"). Existing worlds keep the old square until **`/ppmarket rebuild`** (re-stamps x-48..48 z8..49 from
  the layout via `SpawnIslandFeature.restamp`, then seats the keepers); `/ppmarket seat` only seats.
  Booth keepers stand at booth u5 v2 behind the counter: never put counter decorations at u5 (dy2, v4) - they sit on the
  line between the customer's eyes and the keeper and can eat the right-click.
- **Verified in a real client 2026-10-01** (photo world `build/tmp/claude/make_marketphoto.py <CUE>` + `shoot_market.ps1`;
  the user also looked it over - "looked amazing"): all 8 booths build and are dressed, the fountain/masts/strings/palms
  render, `seatAll` on an old world spawned 8 then `/ppmarket rebuild` MOVED those 8 (no duplicates), the Quartermaster
  opens the parchment counter skin, the Map Merchant opens the sea chart, the journal shows the Monster Hunter tab.
  Follow-up fixes after the photos (compiled, not re-shot): chart descriptions trimmed to the card (`fit`), stack-count
  overlay removed, subtitle moved under the title; the journal's red gate line is kept above the Improve button.
  NOT verified: buying through the chart/counter end to end, the skin NOT showing on a vanilla villager, animations
  (talk/flourish) on the new trader models in motion.

## ECONOMY + PROGRESSION REWORK (2026-10-01, from `D:\Minecraft Modding\Item Progression - Overview.txt` + user notes)

- **TWO CURRENCIES.** `coin` (item id unchanged) is now named **Doubloon** = town money: every port trader, the Map
  Merchant, bounty boards, the Trading Post, skill respec, ship commissions and the starter purse (30). `pirate_coin` =
  pirate money: pirate mobs (raft pirates, crew, fire/skeleton/phantom pirates), boss drops + boss hoards, pirate lairs
  (smuggler, Rackham, phase 3/4 chests), and what only pirates take (Corsair armor, roulette, hideout flag, treasure
  hoard, pirate journal). Non-pirate chests pay doubloons (shrine, galleon, phase 2/5, Gallows Grotto, Saltmarrow +
  Brightwater - those two are written by tools/gen_leviathan_assets.py, edit it too). Future pirate towns trade in pirate coin.
- **Ship prices** = `ShipTiers.commissionCost` (doubloons by hull zone: 25/50/90/140/200/260), shown per card.
- **Traders:** restock once a Minecraft day (24000 ticks), buy offers 6 uses; nobody buys bananas/coconuts. The port
  sells the metal now: Chandler iron + copper, Curio Dealer gold, Quartermaster coal + string. Saved traders rebuild
  their offers when `PortTraderEntity.OFFERS_VERSION` is newer than theirs - bump it whenever stock lists change.
  The Trading Post still buys anything at 75% with no limit (open faucet, flagged).
- **NO ORES:** VanillaBiomeExtras BASE has only seabed disks + stone variety (ORDER still lists the ores so the
  canonical order never changes). New chunks only.
- **Zones open only through the boss chain:** derelict captures no longer unlock zones (getCaptureZoneUnlock deleted)
  and the Seafarer's Token no longer passes the zone-2 waves (it is to become a rare late-game currency).
- **Map Merchant freebies** (cannon balls, shipwright table, cutlass, rations) are once per player, world flag
  `mm_free:<index>:<uuid>` in HomesteadState; the menu packet carries a claimed bitmask.
- **Data folder names:** 1.20.1 reads `recipes/`, `loot_tables/`, `advancements/`, `structures/`. The 1.21 singular
  folders (`recipe/`, `loot_table/`, `advancement/`, `enchantment/`) were dead and are deleted - the ship basics (rope,
  sail, mast, cannon ball, ship cannon/helm/mast, repair kit = driftwood + rope + cloth, cutlass, dagger, grog, grog
  barrel, campfire/smoker shark + swimmer) now live in ModRecipeProvider. `structure/pirate_port_01.nbt` is still in
  the singular folder (referenced by Phase1Biomes) - check whether it ever loaded.
- **No enchantments** in loot (all enchanted books removed). FROZEN_SEEKER (Java enchantment, zone hazards + fog) still
  exists - decide its fate. Diamonds/netherite/nether stars are gone from every loot table, drop and recipe (done with the
  materials ladder - the relic recipes now use mod materials, see `docs/gear.md`).
- **Drops:** sharks (`loot_tables/entities/shark.json`: 1-3 meat, cooked if burning, 4% chum), salted swimmer is a
  phase 1/2 catch, pirate crew add cannon balls / repair kit 5% / a low weapon 8%, chest crabs give a doubloon when
  rummaged and nothing on death, mimics no coins, salvage hook no nuggets (iron/coal instead), no nautilus in pots,
  no kraken scale in galleon chests, Rackham's galley = 22 phase-1 foods.
- Monkey dupe fixed: MonkeyThiefGoal stole a COPY of the whole stack (killing it returned the stack) - now one item.
- Verified 2026-10-01: build, datagen, dedicated-server boot with all 60 mod loot tables rolled (0 errors).
  NOT verified in a client: Map Merchant claimed cards, shipwright per-hull prices, trader stock refresh on old saves.


## OVERVIEW FOLLOW-UPS (2026-10-01, the remaining user notes on the Item Progression overview)

- **Sealed boss hoards** = `entity/mob/BossHoards`: a hoard chest (by its UNROLLED loot table: rackham/serpent/citadel/
  dutchman/court/whalers hoard + whalers_armory, kraken/revenant/heart hoard, the 5 Leviathan tables) won't open or break
  until the player is eligible for that boss. First try warns; a second within 15 s calls `ModBoss.challenge(p)` = DEFIANT:
  x2.5 damage dealt to that player (MobDamageScale), x0.4 taken from them, purple "DEFIANT" bar, lapses a minute after
  they leave/die. Win = `defiantVictory`: boss-armor piece + flag `defiant_won:<idx>:<uuid>` (hoard opens), no chain step,
  relic or advancement. `ModBoss.fights(p)` = eligible || defiant (used by ModBoss, AbyssalKing, Bloodfin). Leviathan:
  sealed only, no early fight. `mixin/SealedHoardLootMixin` stops hoppers/explosions rolling sealed loot (verified: a
  hopper under a sealed hoard stayed empty, a normal chest drained); `LootableContainerAccessor` reads the table id.
- **Fruit + tree variants:** Banana/CoconutTreeDecorator hang fruit only under leaves with open air below (chance per
  candidate leaf); palms get coconuts. Every signature tree key (shorewood, palm, tidewood, cinder, wispwood, voidbloom)
  is `variants(...)` = SIMPLE_RANDOM_SELECTOR of 3 shapes (bending/fancy/cherry/spruce/bush/random-spread placers).
  Placed keys unchanged (no feature-order risk). Verified: 100 trees placed via /place, 28 bananas + 48 coconuts.
- **Bounty board** = 3 wide x 2 high: `BountyBoardBlock` PART 0-5 = (dx+1)+3*dy along facing.rotateYClockwise(),
  MASTER = 1 draws a 48x32 model (x -16..32), other parts use the empty `bounty_board_part`. Breaking any part breaks
  the master (drops once, loot conditioned on part=1). Use -> S2C `Bounties.OPEN_BOARD` -> client `BountyScreen` (cork
  board, wanted posters, "Hand in & collect" = C2S HAND_IN). PortCityLayout places all 6 parts (`/ppmarket rebuild`
  for old worlds). Deliveries no longer ask for kraken ink. Art: gen_gui_textures.py bounty(), model homestead_models.py.
- **Ship radar** = brass compass (`ShipRadarHud`, textures/gui/radar.png from gen_gui_textures.py radar()): chart dial,
  rose, needle turning with your yaw, gold ship / red skull pips, glass glint. Same function and 300-block range.
- **Mob trophies** = `homestead/trophy/MobTrophyBlock` (+ BE, client `MobTrophyRenderer` = GeoBlockRenderer reusing the
  mob's own geo/texture/animation, framed by `Mount`): shark, reefback, lava crab, ghost shark, abyssal angler, abyss eel.
  2% kill drops, Fishmonger buys them (OFFERS_VERSION 3). Plaques from homestead_models.py `mob_plaque`. Framing
  (scale/centre/depth) NOT checked in a client.
- **Strongboxes** (`homestead/hoard/Strongboxes`, `StrongboxItem`): common / rare / legendary, in phase treasure chests,
  every boss hoard and treasure maps. Use on a TREASURE HOARD: costs 16/48/128 pirate coins from the hoard, server rolls a
  weighted prize (rarity bands ~60/25/12/2.6/0.4%), client `CaseScreen` spins a reel (S2C SPIN) and the prize arrives
  when it stops (6 s; left mid-spin = dropped at the hoard). Hoard model redrawn as an overflowing treasure chest.
- **Crown of the Drowned + Leviathan Head** = `tools/gen_wearables.py` (item JSON models on vanilla block textures +
  `textures/block/leviathan_hide.png`). LEVIATHAN_HEAD = `WearableHeadItem` (HEAD Equipment), the Leviathan drops one.
- **Rope** = `RopeItem`: use on a ledge/side/ceiling -> a climbable line unrolls down (4 blocks per rope, max 24, stops
  at solid/water), HangingRopeBlock DEPLOYED=true (holds by its knot, no self-break); breaking any of it reels everything
  below back as rope (4:1, no block drops; loot only drops hand-placed rope). Verified the deployed/placed difference.
- NOT verified in a client: the bounty screen, radar look, trophy framing, strongbox reel, crown/head on a player, the
  defiant fight, rope climbing feel.

## VANILLA CONTAINER SCREENS, REPAINTED (2026-10-03)
`tools/gen_container_textures.py` recolours vanilla GUI textures (read from the client jar - never committed as vanilla
art) into our look: parchment panel with grain, tan slots, dark-wood outlines - an exact-grey swap, so layouts are untouched.
Output overrides vanilla at `assets/minecraft/textures/gui/container/<name>.png` (a player's resource pack still wins).
ALL container screens (26 files: inventory, chests/barrel (generic_54), crafting table, furnace/smoker/blast furnace,
hopper, dispenser, anvil, enchanting table, shulker box, brewing stand, beacon, grindstone, stonecutter, loom, cartography
table, smithing (+legacy), horse, bundle, vanilla villagers (villager2), and the 4 creative-inventory files). Left vanilla:
gamemode_switcher (F3+F4 overlay), stats_icons. Preview tools/previews/gui_containers.png. Vanilla's dark labels must stay readable -
keep the panel light (a dark panel needs a text-colour mixin like MerchantScreenSkinMixin). NOT yet seen in a client.

## SHIP'S CATS + SHIPS IN BOTTLES (2026-10-04, with townhouses #36 and #29)

**Ship's cats** (`homestead/cat/`): the parrots' scheme for cats. `CatCoats` = the 11 vanilla coats + 8 of ours, by tier
(ParrotTypes.Tier) and weight: Common tabby 22 / ginger 20 / tuxedo 18 / MARMALADE 16 / white 12; Uncommon british
shorthair 10 / calico 10 / siamese 8 / TORTOISESHELL 8 / SMOKE 8 / SNOWSHOE 7; Rare persian 6 / ragdoll 6 / BENGAL 5 /
jellie 4; Very rare midnight (all black) 3 / GHOST CAT 2 / GILDED CAT 2; Legendary SEA WITCH'S CAT 1. A custom coat is
saved on the cat as "PPCoat" (`mixin/CatCoatMixin`: tracked data + NBT + `getTexture`), texture
`textures/entity/cat/<id>.png` from `tools/gen_cat_textures.py` (vanilla cat textures re-ramped by brightness; preview
tools/previews/cats.png); Ghost + Sea Witch glow (`<id>_glow.png`, `homestead/client/CatGlowFeature`). `Cattery`: every
game day (a player within 48 of the garden) the untamed cats in `PortCityLayout.CATTERY_BOX` go and four are drawn at
`CATTERY_SPOTS`; rare+ is announced within 160. THE CATTERY COUNTER (2026-10-04, `CatteryCounterBlock` in the keeper's cottage at (124,71,-36), model
`tools/gen_props_assets.py counter`, screen `client/screen/CatteryScreen`): today's cats, alive in their coats, with rarity +
price - Common 30 / Uncommon 80 / Rare 200 / Very Rare 500 / Legendary 1200 coins (`Cattery.price`); Buy = the cat is
yours and tame on the spot and comes to you. The garden's cats can't be fish-tamed any more (UseEntityCallback, a hint
to use the counter). THE SHIP'S CAT: every 2 s a player with one of their own tamed cats within
16 blocks gets Luck (fishing + loot) for 3 s - Luck II from a rare or better coat. `/ppcattery` restocks now, `/ppcat <coat>`
gives a tame one. Verified on a server: restock (custom coats saved, e.g. sea_witch, bengal), a restock replaces the old
stock, the cats stay in the garden. NOT seen in a client (coat textures + glow, the Luck).

**Ships in bottles** (`HomesteadBlocks.SHIP_IN_BOTTLE_*`, models + textures `tools/gen_props_assets.py bottles`): sloop,
brig, galleon and the ghost ship (loot only, light 6) - a green glass bottle on a cradle with a tiny ship inside,
translucent render layer. Recipes (shapeless): glass bottle + oak planks + string + paper (sloop); + dark oak + red dye
(brig); + dark oak + gold nugget + red dye (galleon). TreasureLoot: common pool 5% sloop/brig, rare 6% brig/galleon,
legendary 7% galleon/ghost. The glassblower's window shows them. NOT seen in a client.

## TATTOOS (2026-10-04, the user's call: PURELY COSMETIC - see memory "tattoos-plan")

`homestead/tattoo/`: the TATTOO CHAIR (`TattooChairBlock`, model `tools/gen_props_assets.py chair`, recipe leather x4 +
2 dark oak slabs) in the tattooist's parlour (townhouse #33, (-97,71,-32)) opens THE FLASH SHEET (`client/screen/TattooScreen`):
your pirate on the left wearing the picked design (a preview, before you pay), the four spots (right forearm, left forearm,
chest, back), the 22 designs (anchor, skull & crossbones, swallow, rose, compass rose, mermaid, ship, sweetheart, dagger,
+ round 2: shark, crossed cutlasses, treasure chest, parrot, nautical star, lighthouse, rum bottle, sea turtle; EARNED: Jolly
Roger - Captain Rackham, Sea Serpent - the Sea Serpent, Ghost Ship - the Ghost Captain, Kraken - the Kraken, Leviathan - the
Leviathan; `BossProgression.progress >`
the boss's chain index), "Ink it" (30 coins, earned designs 60; free in creative) and "Cover it up" (15 coins). The server
validates (at a chair within 8, unlocked, coins), saves to `HomesteadState.tattoos(uuid)` (slot -> design, NBT "Tattoos")
and broadcasts SYNC to every client (on join everyone's tattoos go to the joiner). Client `homestead/client/TattooFeature`
re-renders the player model per tattoo with its overlay `textures/entity/tattoo/<design>_<slot>.png` (256x256 = 4x the skin:
arm designs 16x16 on the forearm's outer face, chest/back doubled; slim arms use `left_arm_slim`) via
`RenderLayer.getEntityTranslucent` - armour hides it, NOT drawn on the first-person hand. Art: `tools/gen_tattoo_textures.py`
(16x16 grids, preview tools/previews/tattoos.png). NOT seen in a client (the overlay placement on arms/chest, the screen).
Round 2 (2026-10-04, the user's screenshots): the chair rebuilt with no tilted elements (iron pedestal, tufted leather
`tattoo_leather`, a back reclining in steps); the preview TURNS - picking a spot turns your pirate to show it (back = 180),
< > buttons and dragging turn it by hand (`drawTurned`); the grid is 5 x 5 with 24 px icons.
TEST (user): sit at the chair (right-click), pick a spot + design, ink it; look in F5; relog (kept); a 2nd player sees it;
cover it up. Ideas for later: more designs, a tattoo on the hand in first person (mixin on PlayerEntityRenderer.renderArm).

## CAPTAIN WREN'S MUSIC BOX (easter egg, 2026-10-04)
`homestead/wreck/`: `MusicBoxBlock` (`HomesteadBlocks.WREN_MUSIC_BOX`, "Captain Wren's Music Box") - a decorative
FurnitureBlock; right-click winds it and it plays "What Shall We Do with the Drunken Sailor" (traditional, verse + chorus)
on the note-block bell from a server-side note queue (`MusicBoxBlock.tick`, END_SERVER_TICK; one tune per box at a time),
note particles. NOT craftable: one per player from the Beach Wreck's buried chest (`WreckSecret`, see docs/spawn-island.md
#45). Model `tools/gen_props_assets.py musicbox` (dark oak, brass corners, open velvet lid, cylinder + comb, key).
NOT seen in a client.

## PAINTINGS, FACIAL HAIR, SWINGS (2026-10-04)
- PAINTINGS (`homestead/art/`): the EASEL (2 blocks tall, `TallFurniture`) opens `client/screen/PaintScreen` - a canvas of
  up to 32 x 32 px in ANY colour (hue/shade picker, 32 swatches, recent colours, eyedropper = right-click), pencil, 2x2
  brush, fill, undo (ctrl+Z), clear, a title; shapes 1x1 (32x32 px), 2x2 (32x32 shown big), 2x1 (32x16), 1x2 (16x32) -
  changing shape starts fresh. The work in progress lives on the easel (`EaselBlockEntity`, synced; `EaselRenderer` shows
  it on the board); closing the screen saves it. FINISH uses one BLANK CANVAS (2 from 8 sticks + white wool) and gives a
  PAINTING item {Art: Size, Title, Author, Pixels} - hang it on a wall like a vanilla painting (`CustomPaintingEntity`,
  `CustomPaintingRenderer`: the picture in a dark-oak frame; pictures become dynamic textures, `ArtTextures`, 256 kept).
  Purely decorative. Easel recipe: sticks + planks. The marine painter (#41) has a real easel with a half-finished
  "Harbour at Dawn" on it (`PortCityLayout.seascape()`).
- FACIAL HAIR (`homestead/beard/Beards`): grows while you're ONLINE - stubble after 1 MC day of play (24000 ticks),
  a short beard after 3, a long beard after 7; left alone it goes stubble -> short beard -> full beard. The BARBER'S CHAIR
  (#38, recipe red wool + iron + gold) opens `BarberScreen` (your pirate wearing what you look at, growth bar + time to the
  next stage): 12 styles - stubble; moustache, goatee, chinstrap, mutton chops, short beard (short); French moustache,
  handlebar, full beard, forked beard, braided pirate beard, captain's beard (long) - greyed until you've grown enough.
  Trimming costs 5 doubloons (Haggler) and cuts the growth back to that style's length; 7 colours (a dye job on what you
  wear is free); Shave clean; Stay clean-shaven (stops growth). Stored in HomesteadState "Beards"; every client gets each
  player's look (Beards.SYNC) and `client/BeardFeature` draws it on the head (a face layer + a hanging piece for long
  styles, tinted; textures from tools/gen_beard_textures.py). Test: `/ppbeard <style|none> [colour]`, `/ppbeard grow <days>`.
- SWINGS (`homestead/swing/`): the SWING (an A-frame, 2 tall) and the HANGING SWING (ropes + seat; place it under a
  solid block or a fence - it hangs down 2). Right-click to sit: `SwingSeatEntity` carries you along a 2.4 s pendulum,
  building to +-32 degrees along the way it faces; sneak to get off. While occupied the resting seat hides (OCCUPIED) and
  `SwingRenderer` draws the seat model (block/swing_seat_moving, loaded via ModelLoadingPlugin) at the rider's angle.
  Two swings stand in the park's west garden. Test: `/ppswing <pos>`.
- Models/icons: tools/gen_leisure_assets.py (previews tools/previews/leisure/). Verified in the dev client: easel showing
  its canvas, a 2x2 painting on a wall (both the right way up), barber chair, framed + hanging swings, riding both swings,
  9 beard styles incl. the long ones in several colours (tools/previews/leisure/ingame_1/2.png). NOT verified: painting in
  the PaintScreen by hand, Finish/hanging from the item, the BarberScreen itself, natural growth over days.

## THE TELESCOPE (2026-10-04) - `homestead/nav/Telescopes` + `homestead/client/TelescopeView`
Use a TELESCOPE block to put your eye to it: the spyglass scope overlay, the view zoomed x10 (the scroll wheel steps
x10 / x20 / x40), a compass bearing at the top, and - like the Captain's Spyglass (`CaptainsSpyglassItem.identify`, now
with a range) - the name, health and range of a creature under the crosshair (to 320) or the flag + hull of a ship (to
480) on the action bar, every 5 ticks, server side. Sneak, step off the spot, attack/use or open a screen to step back;
the server also ends it if you walk 4+ blocks away or the telescope goes. Client mixins (pixelpirates.mixins.json client):
TelescopeSpyglassMixin (the local player counts as using a spyglass: overlay + slow aim), TelescopeFovMixin (the zoom -
vanilla only zooms a spyglass that is being held up, isUsingItem), TelescopeScrollMixin (the wheel zooms). Test:
`/pptelescope <pos>`. Verified in the dev client (scope, zoom, bearing). The Ridge Lookout (#49) has four on its gallery.

## CHESS (2026-10-04) - `homestead/chess/`
Two variants, both played through the chess screen (`client/screen/ChessScreen`):
- the CHESS TABLE (one block, a little ivory/ebony set on its board; recipe wool + planks + sticks) - one stands in the
  toymaker's shop (#35, Townhouse 15) between two stools;
- GIANT CHESS (a pedestal with a chess clock; recipe clock + quartz + blackstone + stone bricks): the board is the 8 x 8
  floor IN FRONT of it (a1 straight ahead, files to the right, white plays from the pedestal; `GiantChessBlock.square`),
  the pieces knee- to waist-high (the king ~1.4 blocks). It stands on #50 THE CHESS GREEN, just out of the North Gate
  between the fort road and the wheat field (quartz + blackstone squares, benches, lamps, a sign).
- Seats: sit as white or black, or put the COMPUTER in an empty seat - easy (one move ahead, noisy) or normal (3-ply
  alpha-beta, captures first, material + development/centre). Two players, one player vs the computer, or watch.
- `ChessRules`: full rules - castling, en passant, promotion (the screen asks which piece), check, checkmate, stalemate,
  fifty moves, bare kings. Perft-verified (start d4 197281, Kiwipete d3 97862, position 3 d4 43238). The computer answers
  in ~20-35 ms on the server thread.
- `ChessBoardEntity` (one per board) keeps the game + seats + the last move; synced to clients. `ChessRenderer` draws the
  pieces from boxes and ANIMATES the last move over 10 ticks (glide, a knight hops, a castling rook slides, the taken piece
  sinks and shrinks). Everyone with the screen open gets each move live (`Chess.STATE`); check / mate / draws are
  announced to players within 16 blocks.
- Art: tools/gen_chess_assets.py (table + pedestal models, piece textures, 12 screen icons). Test: `/ppchess <pos> demo`
  (the computer plays itself), `/ppchess <pos> open`. Verified in the dev client: both boards playing themselves, the
  screen mid-game. NOT verified: clicking moves / promotion by hand, two players.
