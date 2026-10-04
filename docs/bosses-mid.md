# Bosses 5-8: Abyssal King, Bloodfin, Kraken, Chained Revenant

> Topic doc split out of CLAUDE.md on 2026-10-02. The root CLAUDE.md (always loaded) holds the global rules;
> this file holds the detail for its topic. Keep it current when you change this area.

**When:** Read with `docs/mobs-and-dungeons.md` before touching these bosses or their lairs (incl. GallowsGrottoLayout).

  - **5 Abyssal King / THE FLOODGATES (2026-09-29):** `AbyssalKingEntity` fights in a SEALED FLOODED HALL and is
    Tide-Blessed (takes 35% damage). Four `TIDE_SLUICE` blocks (`TideSluiceBlock`, OPEN state = source of truth, recounted
    every 10 ticks) each drain a quarter of the hall: the King really removes water layer by layer (`layer()`, flags
    NOTIFY_LISTENERS|FORCE_STATE so no fluid updates; toggles waterlogging; only cells inside `Court.inside`) and the
    blessing weakens 35/50/65/80%. All four open -> STRANDED 20 s (14 s enraged): kneels, -50% speed, x1.5 damage,
    "stranded" salt-bleached skin, only Gasping Sweep; then TIDE CALL closes + locks every sluice 20 s and refloods.
    He fights back with RESEAL (2.5 s channel at the oldest open sluice; 24 damage or a Depth Charge interrupts + staggers)
    and `TideWardenEntity` guards (walk to an open sluice, wind it shut in 3 s). Other attacks: Riptide Charge (boiling
    lane telegraph, 14-block spinning dash), Trident Volley, Crushing Depths (shrinking bubble ring under every player,
    then implosion - only in water), Abyssal Decree, Royal Guard; phase 2 "Wrath of the Deep" (navy/violet skin): Maelstrom
    (spiral pull), Undertow (pressure ring at his height - swim over/under). Idle reset only after 15 s with no eligible
    player within 48 (a target-based reset closed sluices the instant a sneaking player opened them).
    `MobSpec.seabed()` = walks the flooded floor (no SwimGoal, land-style travel in water, breathes water, not pushed).
    New item DEPTH_CHARGE (`DepthChargeItem`/`DepthChargeEntity`: sinks, 2 s fuse or contact, never hurts players or
    blocks; recipe 2 gunpowder + prismarine crystals + iron -> 3; armory loot, warden drops). Test: `/ppboss court
    status|open <n>|close` (nearest King with a court; `open` grants a 2-min idle grace). Model `tools/mobs/abyssking.py`
    (3 skins + tide_warden). Lair `world/dungeon/SunkenCourt` (see Dungeons below). Court kings carry the tag
    `sunken_court`. **Verified on a server** (`build/tmp/claude/drive_king.py`): court data on the King, 2 open -> water
    y56->47, 4 -> drained + STRANDED, probe cell air, tide call refloods + locks, wardens walk over and close a sluice.
    NOT verified: the fight against a real player, balance, in-game look. Old worlds keep their old court-less King
    (normal boss, no floodgates) - only new chunks get the Sunken Court.
  - **6 Bloodfin / THE WHALERS' HARPOONS (2026-09-29):** `BloodfinEntity` (SWIM, `.reach(40)`: also hunts players
    within 40 blocks out of the water). HARPOON_WINCH (`HarpoonWinchBlock`, FACING+LOADED): use = fire a `HarpoonEntity`
    where you look (speed 3.2, water drag cancelled, rope particles to the winch) / crank a hooked line (-2 blocks) /
    reload with a HARPOON item (auto-reload 20 s). A hook tethers it (leash pull back to the line length) and slows it
    25%; it thrashes one loose every 6 s (4.5 enraged). THREE hooks -> PINNED (tonic immobility) 10 s (7 enraged): floats
    belly-up under the surface, x2 damage, then snaps every line. While hooked it can only tail-slap.
    FLESH: tracked TORN 0..6 hides bones flesh0..5 (glowing wound cubes under them); tears at 90/80/70/62/55% HP, each
    dropping a floating BLOODFIN_FLESH item; enrage tears the rest (only the last drops). It swims to floating flesh /
    thrown CHUM (`ChumItem`/`ChumEntity`, bobs at the surface in a blood cloud 12 s) and feeds 1.6-2.2 s standing still
    (harpoon window); its own flesh heals it 3%. DEVOUR: passenger-based grab (`startRiding(this, true)`,
    `updatePassengerPosition` = mouth, re-mounted if they sneak out), smashes boats, DIVE -> RISE -> scripted LEAP
    (parabola 22 up / 28 enraged, 14 forward, straight up if the landing isn't water; `ModMob.scriptedMotion()` skips
    travel) -> crash 14/18 to the held + 8 around, then dazed 1.5 s. 20 player damage while it holds someone = gag.
    BLOOD FRENZY at 50% (`frenzy` anim, flayed skin): blood cloud, heartbeat per player (faster when closer), darkness
    pulse every 12 s, THE HUNT (invisible, circles 5 below the target with a particle fin on the surface, then strikes
    into a Devour from below), bites bleed (1.5/s 5 s), frenzied devour, ghost school. Test: `/ppboss bloodfin
    status|pin|tear|devour` (devour feeds the nearest non-mob creature into its jaws). Model `tools/mobs/bloodfin.py`
    (designed from scratch - no concept render; 15 clips). Lair `world/dungeon/WhalersGrave` (see Dungeons).
    **Verified on a server** (`build/tmp/claude/drive_bloodfin.py`): tear -> flesh item -> it feeds (+HP) -> flesh gone;
    harpoon hook; pin (surfaces, x2); devour a pig -> pig rides the whole dive/rise/leap (y 24 -> 68) and is released at
    the crash; enrage tears all 6. NOT verified: against a real player (grab feel, shift re-mount, hunt), balance, look.
    Round 2 (user playtest): harpoons were invisible + fired from too high. Now `HarpoonEntityRenderer` draws the item
    2.5x along the flight path (every-tick tracking), muzzle poof/crit, speed 2.2; the shot leaves the winch toward
    `aimPoint` (the Bloodfin if within ~3.5 blocks of the look ray - aim assist for the winch-vs-eye parallax - else
    the look-ray block, ignoring water) and homes the last 4 blocks. Platforms are flush with the water (winch at
    depth+1). Shark summons removed (spec + lair) - the fight is him alone. Aggro: follow 64, `reach(40)` (targets players
    out of the water within 40), `leashRange()` 72 (ModBoss hook, default 40). Raft pirates: `MobSpec.awayFrom(
    "bloodfin_reef", 50)` -> `DungeonPlacement.nearSite` blocks natural spawns within 50 blocks of a predicted site.
    Round 3: ONE harpoon pins it (`HOOKS_TO_PIN` = 1; three at once was near impossible), then it is WARY 10 s
    (harpoons glance off) so it can't be chain-pinned. Lagoon widened to r18 (atoll 18..22 = the +-22 reach limit;
    platforms at +-13, wreck x0 20). ARENA: WhalersGrave passes `setArena(centre, 17)` (NBT Arena/ArenaR) -> soft wall
    at the lagoon edge (`confine()`), breaches that would land outside arc toward the middle instead, the hunt gives up
    on prey outside, bait outside is ignored. Egg/summoned Bloodfins have no arena. Leap landing check now scans down
    for water at the landing column (it used a surface measured from the shark, which was already above the water ->
    every leap near the edge went straight up). Verified: 1 harpoon -> PINNED -> wary; roams stay <= 15/17; an edge
    breach facing outward lands at d1.
  - **7 Kraken / THE DROWNING DEEP (2026-09-29):** `KrakenEntity` (STATIONARY, `scriptedMotion()` always - rooted,
    never pushed; tracked PHASE). PHASE 1 ARMS: it lurks at the bottom of the abyss, invulnerable (hint), bites anyone
    within 6; 20 ticks in it spawns 6 `KrakenArmEntity` at the lair's burrows (`setLair(spots, riseTo)` from worldgen;
    ring r9 if egg-spawned) - arms spawned at runtime, not worldgen. The boss bar shows "The Kraken's Arms (n)" + their
    summed HP (set every tick: ModBoss overwrites the percent). Last arm dead -> RISING 3 s (smoothstep to riseTo,
    `rise` clip) -> HEAD. Arms regrow at 66% (2, 3 enraged) and 33% (3); while any live it takes x0.5.
    SWALLOW (`inhale` 3 s): pulls players within 20 toward the maw (Royal Tide Sigil halves), one reaching it rides
    as a passenger 2.5 s (darkness, 2/0.5 s) and deals x3 damage from within; then SPIT - jetted up until clear of the
    water + 8 ticks, then a SWAT: red ring at the victim, an ink/splash column erupts, 14/18 damage and slammed down.
    Also Beak Crush, Ink Eruption (cloud + 6 ink blobs), Quake (floor ring, swim up over it), Maelstrom (enraged).
    Arms: ~9 blocks, 90 HP each; Slam (red line races out, crash 14), Sweep (front arc 9 + sideways), Grab (rides the
    tip 2.2 s, 12 dmg frees you, then flung toward the abyss); rise clip on spawn, `retract` on death (deathTime 36,
    `GlowingMobRenderer.getDeathMaxRotation` = 0 for STATIONARY mobs so they don't topple). All three passenger
    bosses override `updatePassengerForDismount` - vanilla drops a dismounting rider on TOP of the vehicle (the spat
    pig landed on the Kraken's head). Test: `/ppboss kraken status|arms|swallow`. Models `tools/mobs/kraken.py` (head:
    hooded mantle, 4 slit-pupil eyes, fanged maw + beak, 7 sprawling arms, 2 club-tipped whips; enraged ember skin;
    and kraken_arm). Lair `world/dungeon/KrakensMaw`. **Verified on a server** (`build/tmp/claude/drive_kraken.py`):
    6 arms, invulnerable in phase 1, rise y30->46, regrowth at 66%, swallow -> spit (y48 -> 76, surface ~61) -> swat
    (-> 56), enrage. NOT verified: arm attacks/grab vs a real player, balance, in-game look.
    Round 2 (user playtest): Ink Eruption removed (HEAVY_INK blobs just sank). HARPOON SPIT (`KrakenHarpoonEntity`,
    GeckoLib 3D `harpoon` model shared with the winch harpoons via `HarpoonGeoRenderer` - point -Z, rotated onto the
    projectile yaw/pitch after GeckoLib's own 180 for non-living entities): straight 1.4 b/t, no water drag; a hit
    impales (victim rides it, 8 dmg) and carries them up to 20 blocks; a wall on the way PINS them 5 s (6 + 2/s,
    re-mounted if they sneak off); 3 in a fan enraged. TENTACLE BULWARK (`shield` 2.6 s clip, x0.1 damage, rising
    conduit hum + converging bubbles, actionbar "GET BACK"), then BURST (`burst` clip): 5-18 dmg and a hurl of up to
    2.8 b/t within 16. Maelstrom 2.5 s -> 7 s (`WHIRL_TICKS` 140, clip re-triggered every 50 ticks). Test ops
    `/ppboss kraken harpoon|bulwark`. Verified on a server (`drive_kraken2.py`): pig impaled, carried ~11 blocks into
    the bowl wall, pinned ~5 s, released (200 -> 180 hp); bulwark: 100 dmg -> 9 taken, a pig 5 out hurled to ~16.
  - **8 Chained Revenant / THE GALLOWS GROTTO (2026-09-29):** drawn and quartered and hung in chains - only the chains
    hold him together. `ChainedRevenantEntity` (MODE DORMANT on the pit gallows until an eligible player is within 18 ->
    WHOLE -> SPLIT). Split pattern rolled at wake: 75/50/25 or 66/33 (NBT Pattern/Splits). SUNDER: limbs
    (`RevenantPartEntity`, one entity type `revenant_part`, tracked PART/STATE/RAGE; models revenant_arm_r/_l,
    revenant_leg_r/_l, revenant_head via `RevenantPartRenderer`) fly to 4 of the 8 MANACLE_ANCHORs (`setLair` from
    worldgen), hanging 2 blocks out along the true direction to the pit centre (`mountPoint`; the block facing pointed into
    the curved wall - user saw limbs stuck in the wall). Body goes invisible + unattackable and follows the head.
    Limb blows -> `takeLimbDamage` (clears the body's hit cooldown first, or simultaneous limb hits were eaten); a limb
    falls after 5% max HP (6% on 66/33). Arm R hook (drags to the wall), arm L shackles (slowness 5 + jump boost 250 =
    rooted), legs stomp (rubble circles) + enraged chain sweep ring. Head hops/bites (wither)/shrieks, invulnerable;
    TIDESHACKLE (item, Coral Temple chest `phase2_temple_tideshackle`) slings it into a gibbet cage. All limbs down ->
    reeled to the head -> REFORM (stunned 4 s, x2 damage). Whole attacks: Chain Whip, Grave Slam, THE NOOSE (hoisted),
    GIBBET DROP (real iron-bar cage around the target, removed after 5 s, slam at 1.5 s). Enraged "Hanged Wrath": wither
    aura, faster limbs, a fallen limb left 20 s is hoisted back. Sounds: vanilla wither-skeleton + chain, pitched down
    (the synthesized "undead" set was grating); body silent while split.
    LAIR = `GallowsGrottoLayout` (pure Java, ~185 x 250 x 160, `main()` renders plans/slices) rendered per chunk by the
    `GallowsGrotto` feature (placed in EVERY biome via `Dungeons.inject`, after the dungeons; site = the predicted
    `revenant_crypt` DungeonPlacement site, now SEABED minDepth 4; layout cached per site, LRU 3). Caves are clamped 5
    below `ZoneTerrainFunction.terrainTop` (the terrain is an analytic heightfield) except the entrance, and sealed in a
    3-block shell. `FeatureExclusionMixin` cancels every feature whose origin is inside the built volume or standing on it
    (springs would flood it; trees grew on Hangman's Rock). Areas: Hangman's Rock (crag + great gallows) -> the
    Hangman's Drop (spiral shaft ~110 down, plunge pool) -> Gibbet Gallery (70 hanging cages, chasm + river, bridges)
    -> the Gaol (cells, barracks, warden's office, question room) with the Weeping Garden (west) and Oubliette (east)
    -> Hanging Chapel -> the Gallows Pit (r30, 8 anchors, 4 gibbet cages, 4 pillars, dais + gallows) -> the Hoard
    (blast rubble). Loot `chests/grotto_*`, `gaol_*`, `revenant_hoard`. `/ppdungeon revenant_crypt` builds the whole
    thing at your feet (live, a few seconds). Test: `/ppboss revenant status|wake|sunder|limbs`.
    Verified on a server: natural generation (all areas carved, boss DORMANT with 8 anchors + 4 cages), wake -> pattern,
    sunder -> 4 limbs MOUNTED + head, all limbs fall -> reel -> REFORGED x2. In-game screenshots taken (photo world
    needs `/ppunlock 5` first - the zone barrier teleports you out of locked zones - and `/pptest all` for light).
    Round 2 (2026-09-30, user playtest): (1) Tideshackle never caged the head - the invisible body rode on the head and
    caught every swing; `canHit()` is false while SPLIT. (2) HUNG IN THE GALLOWS: DORMANT with a lair = hung by both
    wrists from 2x4 chain blocks on a taller pit gallows (beam PIT_FLOOR+18, feet ~6.5 up, `hang` clip via the new
    `ModMob.movementOverride()` hook), noGravity, pinned to `hangAt`, INVULNERABLE (hint), no proximity wake. Under him
    THE GALLOWBRAND IN THE STONE (`GALLOWBRAND_STONE`, GeckoLib block entity, model `gallowbrand_stone` drawn 1.25x,
    clips idle/empty/pull, HAS_SWORD, no block item): right-click by an eligible player (or creative) -> you get THE
    GALLOWBRAND, the chain blocks break, he falls (`release`), lands with a shockwave (knockback 8, 6 dmg) and wakes.
    `setGallows(chains, stone)` from worldgen, NBT HangChains/BladeStone/Hang*/Released. `/ppboss revenant wake` = as if
    pulled. (3) THE GALLOWBRAND (`GallowbrandItem`, 3D GeoItem, netherite sword +7, slow): right-click throws it
    (`ThrownGallowbrandEntity`, PersistentProjectile, `spin` clip end over end, soul trail) for 14, x2.5 to mounted
    Revenant limbs; it returns like Loyalty IV after a hit / 6 ticks stuck / 24 ticks of flight and drops as an item if the
    thrower is gone. Display transforms in models/item/gallowbrand.json. The mod's first ranged weapon. (4) GALLOWS
    LANDING - the surface was bare and the shaft rail was a closed ring (no way in): `hangmansRock()` now builds a
    stilted quay round the crag, south pier + T-head with OLD WICK'S LAMP HOUSE, east pier + a sloop sunk at her
    moorings, "The Last Rope" tavern on stilts, net-mender's shack, roofless warehouse, crane, overturned boats, channel
    buoys; a stair cut up the cliff to the WELL-HOUSE over the Drop (open south door, rail gap at the spiral start,
    collapsed roof, winch beam); the great gallows moved north of it. Signs via `L.signs` (text written as NBT - a
    worldgen SignBlockEntity has no world, `setText` NPE'd and killed whole chunks). NPCs via `L.npcs`. (5) OLD WICK,
    the Lamplighter (`LamplighterEntity`, PASSIVE spec `lamplighter`, model in `tools/mobs/gallows.py` with the sword
    models): right-click = the next line of his story (warning -> lore -> Tideshackle/sword/way-down hints; thanks you
    once the Revenant is beaten), `talk` clip, keeps within 9 of home, takes no damage, wandering-trader voice.
    (6) Fullbright for tests: `mixin/LightmapFullbrightMixin` paints the lightmap white while
    `ZoneEffectsClient.clearSight` (`/pptest clearsight|all`). Verified on a server (`drive_revenant2.py`, fresh natural
    site): HANGING with 8 chains, stone has_sword, Wick spawned, sign text present, wake -> FALLING -> lands -> WHOLE,
    stone empty + chains gone, a thrown Gallowbrand into a wall limb 633 -> 606.
    (7) Well-house is +-7 (it was +-5, almost all shaft - you fell in through the door); gallows at cz-11, cliff stair
    ends at cz+8. (8) DETAIL PASS after a fullbright photo review (`furnish()` in GallowsGrottoLayout, `pa()` = only into
    air): gaol rib vaulting, drain grates, soul torches + black banners, guard post; every cell gets straw, slop bucket,
    wall manacles and a random leftover; barracks hearth (`hearth()`), rug, dummies, workbenches, `chandelier()`;
    warden's rug, soul hearth, cabinets, map table; question room rack, lava cauldron, hooks, blood (redstone dust);
    oubliette slab walkway + rail round the water and kelp/seagrass/pickles below; an abandoned camp in the gallery;
    railed graves, wither roses, lantern posts and a flagstone path in the garden; gold mounds, pots, pillars and an
    empty skull throne in the hoard. Photo tooling: `build/tmp/claude/make_grottophoto2.py <CUE> [cam,cam...]`.
    Round 3 (2026-09-30, user playtest): (1) THE ENTRANCE WAS CAPPED - the 3-block rock shell around every cave also
    closed 3 blocks OVER the top of the shaft; the well-house now clears every interior cell (shaft included) above
    ROCK_TOP. (2) THE GRAND STAIR: shaft r 8 (carved from the gallery floor, not below it), a 3-wide polished-deepslate
    spiral (r 5..8) that falls 18 blocks per 3/4 turn then a flat landing (`stairDrop`), helical underside, a balustrade
    with gaps and lantern posts, soul lanterns/barnacles in the wall, an alcove off every landing (hanged man, candle
    shrine, gibbet, bench + loot barrel), the chain down the middle with a lantern every 20. The open well (r < 5, WELL_R)
    still drops straight into a bigger plunge pool (r 5.5, 4 deep). Well-house +-10, crag r 18 -> 13, quay ring 16.5..22.5,
    gallows cz-14, cliff stair on stone piers z cz+20..cz+11; buildings moved out to match. (3) Warden's bookshelves
    walled up the doorway to the question room (x31 z-16..-14) - gap left. The oubliette tunnel now ends at walkway
    level (G-5, it came out under the water) with stair blocks down its slope. (4) THE SKULL GATE (`skullGate()`): the
    chapel's south wall is a 21x17 skull, 3 deep (ART char map: B bone/calcite, C cracks, E sockets with soul fire over
    soul soil + crying obsidian, N nose, T/F teeth + fangs, M the jaw doorway 15 wide), chains weeping from the eyes,
    braziers, hanged men, wither skulls on pikes, the organ moved to flank it; behind the jaw the THROAT (bone rib
    arches, checker floor, candles, a noose) to the stair down. (5) He GRIPS A BAR: a horizontal chain (x -4..3 at +14)
    slung on two chains outside his fists (hang y +0.8). (6) LIMB AoEs while split (all mounted limbs together, every
    12 s / 8.5 s enraged, needs 2+ limbs; limbs' own attacks pause): THE QUARTERING - 4 (6 enraged) spokes of red dust
    from the pit centre, 2 s, then chains slam along them (11/15 + knocked sideways), rotating half a spoke per wave,
    3-4 waves each faster; THE HANGMAN'S FLOOR - checkerboard of 5x5 trapdoors marked in red, 2.5 s, the marked ones drop
    (12/16, yanked down, slowness), then the other colour. (7) THE WALL OF LIGHTS: 16 sea-lantern pairs round the pit
    wall (`lightWall`, NBT Lights/LightsOut/LightTicks). One dies (-> dark prismarine, smoke, beacon-off) every 15 s
    while an eligible player is within 48; the last one = darkness pulse + ENRAGE (`ModBoss.healthEnrage()` false,
    `forceEnrage()` - new hooks). All relight when he dies. Test: `/ppboss revenant quartering|floor|lights|dark`.
    Verified on a server + photos: shaft open, door, jaw, eyes, bar, 16 lights, both AoEs run all waves, at 74% HP he
    stays calm until the last light dies -> Hanged Wrath.
    Round 4 (2026-09-30, user playtest): (1) a blow on a wall limb is capped at that limb's remaining share
    (`takeLimbDamage(source, amount, cap)`) - one big hit used to punch through into his health; (2) a re-split needs 15%
    of his max HP taken WHOLE since he last reformed (`wholeDamage`, NBT WholeDamage) and never happens below 10% - the
    ~20% the limbs take used to leave him on his next threshold, so one hit after reforming split him again; (3) THE SKULL
    GATE is drawn as a mirrored half (`HALF` in `skullGate()`, jaw x -7..7, fangs at -6/-2/2/6) and `dressCaves` skips its
    face (`skullFace`). Only new chunks get the new skull.
