# Bosses 1-4: Rackham, Sea Serpent, Molten Warlord, Ghost Captain

> Topic doc split out of CLAUDE.md on 2026-10-02. The root CLAUDE.md (always loaded) holds the global rules;
> this file holds the detail for its topic. Keep it current when you change this area.

**When:** Read with `docs/mobs-and-dungeons.md` before touching these bosses or their lairs.

- **Per-boss overhauls (one at a time, with the user) - done: 1 Rackham, 2 Serpent, 3 Warlord, 4 Ghost Captain, 5 Abyssal King, 6 Bloodfin, 7 Kraken, 8 Chained Revenant (2026-09-29), 9 Abyssal Heart, 10 Leviathan (2026-09-30) - the whole chain.**
  - `entity/mob/CaptainRackhamEntity` (spec `.entity(...)` factory + `.enrage(anim, title)`): Flintlock, Cutlass Flurry
    (3 frontal slashes), Powder Keg (`PowderKegEntity`, rolling barrel, shoot it to detonate), Polly (parrot peck),
    Bosun's Whistle (crew capped at 4); Powder-Mad at 50%: Dynamite Barrage (fused `DynamiteEntity.lobbed`) + BROADSIDE
    (red rings, then the fort's cannons / falling shells hit them); Grog Swig once <25%. Voice lines in chat. 200 HP.
  - Enraged skin: `ModBoss` syncs ENRAGED; `GlowingMobRenderer.of` swaps to `NAME_enraged.png` if it exists. Skins come
    from `SKINS` in tools/mobs/pN.py, and `gen_mob_roster.py` refuses a skin whose cubes differ from the base (UVs).
  - **Z-fighting:** see CLAUDE.md "Z-fighting" (2026-10-05: every model fixed, the writers fix new ones).
  - Model: `tools/mobs/rackham.py` (peg leg, tricorn, braids, bandolier, parrot bones `parrot/parrot_head/pwing_*`).
  - Lair: `world/dungeon/RackhamFort` (Rackham's Hold, full +-22 footprint): gatehouse, 4 towers, keep (war room,
    captain's cabin, Jolly Roger), vault under the keep sealed by BLAST_RUBBLE, powder magazine (dynamite loot), brig
    (lever-opened cells: 2 captive castaways who give a gift when talked to, 1 map merchant), barracks (crew spawner),
    galley. Loot: `chests/rackham_{armory,barracks,galley,captain,hoard}`.
  - `FORT_CANNON` block (+`FortCannonBlockEntity`): fires terrain-safe `CannonBallEntity.fort` shots at survival players
    in its +-80 degree arc with line of sight, only while a captain_rackham lives within 64; ballistic solver `solve()`.
    ISLAND MODE (2026-10-04): the same block on the spawn island (pirate dim, inside PortCityLayout's bounds) is always
    manned and fires only on players with rep < 0 with `ISLAND_FACTION` (NAVY), range 60, warning + 3 s grace first,
    players-only blast - the Governor's Fortress (#44, docs/spawn-island.md).
  - **Dynamite = early-game key:** `BLAST_RUBBLE` is unbreakable and bedrock-blast-proof; only thrown dynamite clears it
    (`DynamiteEntity.clearRubble`). Sources: recipe (gunpowder+paper+string -> 2), crew (`entities/pirate_crew` loot:
    gunpowder/dynamite), raft pirates, fort loot, castaway gifts, Rackham drops 4-8. Player dynamite = TNT-type blast.
  - **2 Sea Serpent (2026-09-29):** `entity/mob/SeaSerpentEntity` - Bite, Tail Lash, Constrict, Pressure Jet (cone
    knockback), Silt Ambush (invisible, bubble column marks where it erupts); Stormscale at 50% (cyan-lightning skin):
    Maelstrom, Storm Surge (1 s charge ring then discharge), Brood (void squid, max 4). 300 HP, renderScale 1.6 (~12 blocks).
    **TIDEPLATE objective:** 4 `SERPENT_WARD` (Tideward Stone) blocks in the lair; their world positions are stored on the
    serpent at worldgen (`setWards`, NBT `Wards`) and recounted every second -> tracked `Scales`. Damage taken =
    1 - 0.15*scales (40% with all four). Model bones `plate0..3` hidden via the generic `ModMob.toggleBones/isBoneHidden`
    hook (GlowingMobRenderer applies it). Only the `TIDEBREAKER` item breaks a ward (`calcBlockBreakingDelta`), found in
    the Tidewater Shrine vault, whose iron door is now BLAST_RUBBLE (dynamite gate) - loot `chests/phase1_shrine_tidebreaker`.
    Lair `world/dungeon/SerpentHollow`: flooded dome under the seabed (radii 18.5x13, floor y-28, ~20 tall), 6 rib arches,
    sinkhole shaft, central coral spire + hoard, 2 floor + 2 ledge wards. Model `tools/mobs/serpent.py` (+enraged skin).
    Verified on server: wards 4 -> setblock two away -> Scales 2.
    Entrance = the SERPENT GATE (`SerpentHollow.entrance/head/coil`): colossal stone serpent head, maw over the
    sinkhole (swim into the mouth, down the throat), ochre-froglight slit eyes, horns, crest, neck bowing into the sand
    into two coils + tail, plaza, 4 lit obelisks; usually breaks the surface so it is visible from a ship.
  - **3 Molten Warlord (2026-09-29, built but NOT yet in-game tested):** `MoltenWarlordEntity` - Mace Lash (dust line
    telegraph 12t, then an 11-block lane), Flail Whirl (danger band 2.5-7), Fissure Slam (3 racing cracks), Chain Hook;
    Molten Core at 50%: Meteor Rain, Forge Call (max 4 sprites), Core Vent. QUENCH mechanic: armour halves damage;
    `QUENCH_VALVE` blocks pour an 8 s cascade 3 blocks in front (refill 45 s) - Warlord inside -> quenched 10 s
    (x1.25 dmg, 2 s stun, "quenched" skin). Generic `ModMob.skinVariant()` drives skin swaps (ModBoss: "enraged").
    3 skins from `tools/mobs/warlord.py` (flail = chain bone + link1..4 + mace_head, lash translates links out).
    Lair `world/dungeon/CinderCitadel`: 4 dynamite doors (gate, keep, foundry tower, crucible), each section stocks
    dynamite (`chests/citadel_*`), half-slab spiral stair down 26 blocks, underground Crucible arena r14.
  - **4 Ghost Captain / the FLYING DUTCHMAN (2026-09-29):** `world/GhostShipEncounter` + `GhostShipDesign` (the ship is a
    code-generated `ShipSchematic.of(...)`, spawned by ShipSpawner, sailed by AiShipController as UNDEAD blueprint
    "flying_dutchman": 8 GHOST_CANNON broadside positions). Summoned by right-clicking a vanilla BELL standing on a
    PHANTOM_BUOY (UseBlockCallback in PixelPirates) at the lair `world/dungeon/DutchmansRest` (bell islet + mast graveyard;
    the boss is NOT spawned by worldgen). `GhostCaptainEntity` is BOUND while the ship floats (pinned to its poop deck from
    ship-space every tick, invulnerable, only Soul Beam + Phantom Broadside); AiShipController calls
    `GhostShipEncounter.onSunk` at hull HP 0 instead of makeDerelict -> captain breaks free and boards the nearest
    player's ship; the wreck is deleted 20 s later. If his ship is missing for 10 s he breaks free (never stuck immune).
    New blocks: ghostwood log/planks, spectral_sail (cutout), ghost_cannon (ship_cannon model, verdigris textures),
    phantom_buoy. Test: `/ppboss dutchman` (summon at you), `/ppboss dutchman sink`. Verified on server: ship spawns +
    registers, captain bound on deck, sink -> breaks free, wreck deleted. NOT yet verified: boarding a real player, the
    duel, sinking it with player cannons.
    Fixes 2026-09-29 (user playtest): (1) bow is ship-space +z - AI + thrust treat +z as forward; the design is authored
    bow -z and MIRRORED on export. (2) UNDEAD is not always-hostile, so the Dutchman skips the reputation check in
    `selectTarget` (it never fired before). (3) Its turn input is negated in `computeInputs` (measured: positive turn
    swung the +z bow away from the target) - check other AI ships for the same inversion. (4) 11 GHOST_MAST blocks
    (5 under the masts + 6 in the keel, round 2 "still too slow") x2 thrust each; mastCount is COUNTED from the placed
    GHOST_MAST blocks, so adding one is enough (`GhostShipDesign.THRUST_PER_GHOST_MAST`). (5) ghostwood/sails/ghost mast render TRANSLUCENT
    (alpha 150-200 textures, nonOpaque). Client-verified: spots at 78 blocks, broadsides every ~5 s, moves bow-first.
    phantom_pirate speed 0.35 -> 0.16 (flyers get FLYING_SPEED = speed*1.6; 0.35 was absurd).
    DutchmansRest: `wrecks()` used to call itself (infinite recursion -> StackOverflowError, which the dungeon
    try/catch(Exception) does NOT catch); flotsam/bones/wrecks now run from `build()`.
    VS2 physics does not run on a headless server with no player - ship motion can only be tested in a client.
    **VS2/test gotcha:** `forceload add` silently fails above 256 chunks - entities spawned into the not-really-loaded
    area vanish from the entity index (looked like a VS2 bug for an hour). Keep test boxes <= 16x16 chunks.
