# Gear: boss armor, 3D armor, materials ladder, relic weapons

> Topic doc split out of CLAUDE.md on 2026-10-02. The root CLAUDE.md (always loaded) holds the global rules;
> this file holds the detail for its topic. Keep it current when you change this area.

**When:** Read before touching armor/weapon items, `item/BossArmor`, `PixelArmorItem`, `tools/armor/`, `tools/gen_weapon_*`, ModArmorMaterials/ModToolMaterials.
Relic weapons + their display transforms are the first bullet of the PLAYTEST FIXES section in `docs/gameplay-systems.md`.

## BOSS ARMOR SETS (2026-09-30, design `D:\Minecraft Modding\Boss Armor - Design.txt`)

Five sets, one per pair of chain bosses: I Powder-Monkey's Brigandine (Rackham+Serpent), II Forgeguard Plate
(Warlord+Ghost Captain), III Tidecourt Regalia (King+Bloodfin), IV Gallowbreaker Harness (Kraken+Revenant),
V Mantle of Thalassar (Heart+Leviathan). Items `<powder_monkey|forgeguard|tidecourt|gallowbreaker|thalassar>_<helmet|
chestplate|leggings|boots>` (`ModItems.bossSet(tier)`, `item/custom/BossArmorItem`: fireproof, tier, tooltip lists ward +
bonus), materials in `ModArmorMaterials` (toughness/knockback PER PIECE). Logic all in **`item/BossArmor`**:
- BOSS WARD 3/4/4/5/6% per piece vs damage "from a boss" (`fromBoss`: attacker/source is a ModBoss, or any of our
  entities within 48 of one, projectiles resolve to their owner; owner-less damage - magic, explosions, burning - counts
  when a ModBoss is within 64). Applied by `mixin/BossArmorDamageMixin` at RETURN of `LivingEntity.modifyAppliedDamage`
  (after armor + Protection), so it reaches magic too.
- Set bonuses (all 4 of one tier, `fullSet`): I boss explosions x0.6, burning near bosses ticks down twice as fast;
  II no fire damage/burning near bosses, boss magic x0.7, dolphin's grace in water; III after 28 ticks riding anything
  of a boss's you are dismounted (`slipped()` guards the re-grab lines in Bloodfin/Kraken/KrakenArm/KrakenHarpoon),
  pulls x0.6 (`pullScale` at the Royal-Tide-Sigil pull sites: Kraken inhale x2, Leviathan lure+gulp, Heart systole), boss
  slowness removed; IV wither/darkness/blindness removed near bosses, nausea capped at 1 s, falls near a boss x0.5, boss
  wither damage 0; V LAST STAND (a boss hit that would take you under 25% stops at 25% and gives Absorption III +
  Resistance 6 s, 90 s cooldown), boss magic x0.7, water breathing/dolphin/night vision in water. Tick = `BossArmor.tick`
  every 5 ticks. NOT covered yet: Revenant hook/noose/shackle (no riding), King maelstrom + `Abilities.pull` (relic skip only).
- LOOT (cut 2026-10-01: they also come from kills + strongboxes): each hoard chest has ONE 60% roll (was 2 + 50% a third): rackham_hoard, serpent_hoard, citadel_hoard, dutchman_hoard, court_hoard,
  whalers_hoard, kraken_hoard, revenant_hoard, heart_hoard; the Leviathan's rift_hoard/gullet_wreck/spire_ruin 1 piece.
  serpent/kraken/dutchman hoards are NEW tables (copies of phaseN_treasure) so the shared tables stay armor-free - the lair
  classes point their main chest at them. KILL: `BossProgression.onBossKilled` -> `BossArmor.onBossKilled` gives every
  credited player one piece of tier index/2+1 they don't own (inventory, equipped or ender chest).
- Textures: `tools/gen_boss_armor.py` repaints corsair/ashen/kraken/cursed-bone shapes with each set's palette - a FIRST
  PASS for balance testing, the real art is still to do (with the old-set visual overhaul, see HANDOFF.md).
- `/pparmortest` (op): damage per loadout (none/diamond/netherite/I-V) vs the nearest boss (spawns a still Rackham if
  none), same pipeline as applyDamage. Verified 2026-09-30: melee 16 -> none 16, diamond 5.8, netherite 5.2, I 8.6,
  II 5.4, III 4.1, IV 3.2, V 2.4; magic 8 -> 8 / 8 / 8 / 7.0 / 4.7 / 6.7 / 6.4 / 4.3; loot rolls give the right pieces.
  NOT verified: set bonuses in a real fight (slip, pulls, last stand feel), textures in game.

## 3D ARMOR OVERHAUL (2026-09-30) - every set is a GeckoLib armor model

All 12 armor sets (7 crafted: pirate_armor, castaway, navy_officer, corsair, ashen, cursed_bone, kraken_scale; 5 boss:
powder_monkey, forgeguard, tidecourt, gallowbreaker, thalassar) are 3D, built from the user's ChatGPT concept sheets in
`D:\Minecraft Moddingrmorrenders\<set>.png` (brief: `D:\Minecraft Modding\Armor Art - Concept Brief.txt`).
- **Pipeline:** `python tools/gen_armor_models.py [set ...]` -> `geo/armor/<material>.geo.json`, `textures/armor/<material>.png`
  (+ `_glowmask.png` only if something glows), an empty `animations/armor/<material>.animation.json`, a preview sheet
  `tools/previews/armor/<set>.png` (on a grey mannequin) and the 4 inventory icons (32x32 renders; legs drawn apart).
  Builders: `tools/armor/sets.py` (one function per set) on `tools/armor/kit.py` (class `AR`: the GeckoLib armor
  skeleton, slot shells `helm/chest/arms/legs/boots`, `R()` right+mirrored-left, `rag()` torn hems, `cape()`, `strands()`,
  `spike()`). Player space: feet y0, faces -Z, RIGHT side = -X. `spike()` rot z = lean OUTWARD (sign flipped inside -
  every spike leaned inward before that). `tools/preview_geo.py` gained `geo_file/tex_file/only/solids/pad`.
- **Java:** `item/custom/PixelArmorItem` (ArmorItem + GeoItem; model name = material name) is the base of every piece;
  `ModArmorItem` (old set helmets) and `BossArmorItem` extend it; the rest are plain PixelArmorItem. The renderer
  (`entity/client/PixelArmorRenderer`, GeoArmorRenderer + AutoGlowingGeoLayer when a glowmask exists) is handed in by the
  client via `PixelArmorItem.CLIENT_PROVIDER` (set in PixelPiratesClient) - no client class on a dedicated server.
  `FROST_HELM` is still a vanilla ArmorItem (pirate material, 2D layer texture). The old `models/armor/*_layer_N.png`
  files are now unused by these items.
- **HIDE HELMET:** `item/HelmetToggle` - H key (C2S packet `toggle_helmet`) or `/pphelmet` flags the worn head stack
  (NBT `PPHidden`); client mixins `HelmetHideArmorMixin` (ArmorFeatureRenderer.renderArmor, HEAD slot - GeckoLib only swaps
  the model in there, so this covers both vanilla and 3D) and `HelmetHideHeadMixin` (HeadFeatureRenderer: the Crown,
  skulls). Still protects; tooltip notes it.
- **Verified in a real client** (photo world `build/tmp/claude/make_armorphoto.py <CUE>` + `shoot_armor.ps1`, 12 armor stands):
  every set renders, glowmasks work, fits the body, the hidden-helmet stand shows no helmet; resource reload clean.
  Round 2 (same day, after a side-by-side with every concept): kit materials `plates` (riveted rows + seams), `scales`
  (overlapping), `leather` (stitched), `cloth` (folds), `network` (branching ember/gold cracks), `trim` (subtle top edge);
  helpers `pauldron` (stacked tiers hugging the shoulder), `gauntlet`, `knee`, `cuff`, `flare_cape` (layered, flared back),
  `blade` (flat fins/feathers/crystals), `chain`, `ring`, `tentacle`. Lessons: bright trims on every plate read as stripes;
  thin blades trimmed on both sides turn gold; outward-flared tiers look like wings. Compare sheets: concept left, model
  right (build them from `D:\Minecraft Moddingrmorrenders` + `tools/previews/armor/<set>.png`).
  Dedicated server boots clean. NOT verified: on a moving player (walk/sneak/swim clipping of capes and spikes), elytra/
  capes overlap, first-person arm view.


## MATERIALS & GEAR LADDER (built 2026-10-01, design `D:\Minecraft Modding\Materials & Gear Ladder - Design.txt`)

- **Phase materials** (ModItems, `MaterialItem` = one tooltip line; icons `python tools/gen_material_textures.py`):
  P1 crab_shell (chest crabs), P2 siren_scale (sirens) + reef_pearl (jellies, reefback), P3 brimstone (volcanic mobs,
  sulfur_block drops 2-4) + obsidian_shard (golems, brutes), P4 ectoplasm (ghosts) + lost_soul (rare), P5 abyssal_pearl
  + luminous_ichor + krill_cluster, boss-only tidal_core (Heart, Leviathan). RULE: no recipe converts one phase's
  material into another's (the bone/prismarine + kraken-ink conversions are gone). Brimstone + charcoal -> gunpowder.
- **Kraken ink** only drops for a killer who has beaten the Kraken (`ModMob.inkUnlocked`), or from the Kraken. Void squid
  drop fish. Ink -> Ink Bomb (thrown, blinding cloud, `InkBombEntity`).
- **No diamonds / netherite / nether stars / emeralds / totems / enchanted golden apples / vanilla tridents** in any
  loot table, boss drop or recipe (tools/gen_leviathan_assets.py edited too). Relic recipes: Flail = obsidian shards,
  Maw = abyssal pearls, Chainbreaker = cursed bone + lost soul, Heartseeker = forged from a KRAKEN FANG, Wrath = from a
  STORMCALLER + tidal core, Sunken Trident = from an ABYSSAL HARPOON.
- **Ladder numbers:** ModArmorMaterials (Castaway 7 -> Navy 10 -> Pirate 11 -> Powder-Monkey 12 -> Corsair 13 -> Ashen 15
  -> Forgeguard 16 -> Cursed Bone 17 -> Kraken 18 -> Tidecourt 19 -> Gallowbreaker 20 -> Thalassar 20/t3). Weapons: one
  attack baseline per phase in ModToolMaterials + per-item bonus/speed (DPS ~7 in P1 -> ~16 in P5, relic melee 14-20).
  Cursed Bone set bonus lost Resistance. Armor recipes: Navy = cloth + crab shell, Corsair = siren scale + pirate coins,
  Ashen/Cursed/Kraken = `offerTrimmedArmorSet` (19 main + 5 accent: brimstone / ectoplasm / abyssal pearl).
- **Mob damage pass:** `world/MobDamageScale` (called from BossArmorDamageMixin before Boss Ward) scales damage OUR mobs,
  their projectiles/abilities and boss arenas deal to players: by roster phase (0.75/0.68/0.70/0.72/0.65) or chain boss
  (0.68-0.75). Vanilla mobs untouched. /pparmortest numbers changed accordingly. NOT balance-tested in play.
- **Boss signature dishes** (PirateFoods `dropOnly()` - no recipe): rackhams_reserve, serpent_steak, molten_core_chili,
  phantom_hardtack, royal_tide_feast, bloodfin_fillet, kraken_platter, last_meal, heart_tartare, leviathan_steak - each
  boss drops 1-3, each hoard has one. New dishes: cooked_lava_crab_claw, cinder_chili_sauce, brimstone_smoked_fish,
  ghostly_brew, bone_marrow_broth, luminous_draught, whales_bounty. Sea Serpent drops the Officer's Sabre (not Stormcaller).
- **Treasure maps** (`TreasureLoot`, entries can be one-of): common P1 / rare P2-3 / legendary P4-5, doubloons + pirate
  coins + that stage's materials, 10% armor piece, 8% weapon, boss dishes; maps cost 10/30/75 doubloons.
- **Seafarer's Token** (`SeafarersTokenItem`): bosses 5-10 drop 1, legendary maps 3%, phase5_treasure 2%, Brightwater
  governor. 3 tokens used inside a ruined Leviathan port = `LeviathanPorts.restore`; 1 on a roulette table = TOKEN SPIN
  (random armor/weapon up to your boss tier, `gearUpTo`).
- **Frozen Seeker** (the only enchantment): max level 10 (10% protection per level, X = immune/clear fog); loot function
  `pixelpirates:frozen_seeker` on every helmet entry in zone 3/4 chests: L1 15%, each level half as likely (~30% any).
  Verified on a server: 127 helmets rolled -> 30 enchanted, 17 at level I.
- **Siren's Conch** (sirens 5%): hostiles within 12 forget you 6 s (`SirenConchItem.tick`), bosses immune, 60 s cooldown.
- **Coral Whale** = `CoralWhaleEntity`: feed a krill cluster -> "roll" clip (tools/mobs/p5.py) -> Whale's Bounty, 5 min cooldown.
- **Shell Buckler** (crab shell + driftwood) = ShieldItem subclass, drawn flat (vanilla's 3D shield renderer is shield-only).
- **Trading Post:** 64 items per player per game day (`HomesteadState.postSold`).
- Mimics drop 2 rolls of `chests/phase4_common`. Crew drop a Pirate armor piece 1%.
- NOT DONE from the design: the beach driftwood pickup and +1 reach on the Boarding Pike (suggestions only).
- Verified 2026-10-01: build, datagen, server boot, all 60 loot tables rolled with 0 errors, Frozen Seeker distribution.
  NOT verified in a client: any of the new items/foods in hand, the conch, ink bomb, whale roll, token spin, port
  rebuild, the feel of the new armor/damage numbers.


- **Lair TREASURE BLOCK** (`block/custom/TreasureBlock`): right-click = one free strongbox reel spin per player per block
  (flag treasure_spin:<dim>:<pos>:<uuid>), tier by zone (1-2 common, 3-4 rare, 5 legendary), sealed while a nearby boss
  hoard is sealed to the player, decorative inside the port (|x|,|z| < 220). Placed in: Rackham hoard room, Obsidian Vault,
  Smuggler's Grotto vault, Tidewater Shrine crypt.
  2026-10-03 REWORK: a 3D treasure heap (tools/gen_treasure_assets.py - coin mound, open chest spilling coins, goblet,
  gems; textures/block/treasure_coins.png; nonOpaque + an outline shape) with a gold glint now and then. Opening it:
  coin spray + chimes (server) and FIREWORKS - TreasureBlock.FX packet -> client/TreasureFx calls
  ClientWorld.addFireworkParticle (no rocket entity: real rockets hurt anyone within 5 blocks, lairs have low ceilings).
  Tier 0 gold x2, 1 gold+aqua x3 with a star, 2 gold+purple+white x4 with a large ball + crackle. The old cube's
  json files had UTF-8 BOMs - rewritten clean. NOT yet seen in a client.

## THE FORGE - forging + mending (2026-10-02) - `homestead/forge/`, forged weapons in `item/forged/`

- **How it works (no GUI):** a lit **FORGE HEARTH** (`ForgeHearthBlock`, FUEL 0-8: coal/charcoal +2, brimstone +3, blaze rod
  +4, coal block / lava bucket fill it; -1 every 30 s via a scheduled tick; a hearth never fed by hand - the town forge,
  placed at fuel 8 by the layout - has no tick and stays lit; standing on a lit one burns) within 2 blocks (one up/down) of
  a **FORGE ANVIL** (`ForgeAnvilBlock` + `ForgeAnvilBlockEntity`, synced, `client/ForgeAnvilRenderer` lays the piece flat
  on the face + materials in a row). Right-click the anvil with: a weapon/tool/armour = lay it; anything else = add as
  material (sneak = whole stack, max 4 kinds); the **SMITH'S HAMMER** (HomesteadItems, 400 durability, 8-tick cooldown) =
  strike; empty hand = take everything back. A pattern takes 6 strikes, 4 with **BELLOWS** touching the hearth
  (`BellowsBlock`, right-click = a puff). **MENDING:** a damaged piece + its own repair ingredient (`Item.canRepair`) -
  3 strikes, each material restores 1/3, no XP cost, no "too expensive". The action bar always says what is on the anvil
  and what it still needs. **PATTERN BOARD** (`PatternBoardBlock`) right-click = every pattern in chat.
- **Patterns** = `Forging.PATTERNS` (one line each; the board, the anvil hints and `/ppforgetest` all read it). Forged
  weapons have NO crafting recipe and NO loot entry - only the anvil makes them:
  | Base + materials | Result | Numbers (dmg @ speed) | Ability (`item/forged/*`) |
  |---|---|---|---|
  | Rusted Cutlass + 2 iron ingot | Cutlass | 6.0 @ 1.6 | (restore) |
  | Boarding Sabre + 4 crab shell + 1 iron | CRABCLAW SABRE (P1) | 7.0 @ 1.6 | every 3rd hit on one foe: +3, Slowness II 2 s |
  | Naval Rapier + 2 reef pearl + 1 gold | PEARLGUARD RAPIER (P2) | 6.0 @ 2.1 | use = En Garde 0.75 s: next melee blow parried (boss: halved), attacker knocked back + Weakness; 3 s cd |
  | Corsair Cutlass + Flintlock Pistol + 2 obsidian shard | PISTOL CUTLASS (P3) | 8.0 @ 1.6 | use = fire a ball (11 dmg, 1 Paper Cartridge), 2.5 s reload (Quick Hands) |
  | Boarding Pike + 3 obsidian shard + 2 brimstone | OBSIDIAN HALBERD (P3) | 12.0 @ 1.0, fireproof | use = Cleave: 8 dmg + knockback to all in a front arc (3.5), 6 s cd |
  | Soulrender + 1 lost soul + 2 ectoplasm | SOULREAVER (P4) | 10.0 @ 1.5 | hits heal 2; kills store a soul (max 3, NBT "Souls"); use = release: 3 hearts + 4 s Resistance each |
  | Kraken Fang + 2 kraken ink + 1 luminous ichor | INKFANG (P5, post-Kraken) | 7.5 @ 2.2 | hits blind 2 s; use = vanish: 5 s invisibility, non-boss mobs within 12 drop you; 30 s cd |
  Hooks: Pearlguard parry in `SkillEffects.allowDamage` (before the Parry skill), Soulreaver souls in `SkillEffects.onKill`.
  `ForgedBlade` = shared base (tooltip lines + `bonusHit`, which resets the target's invulnerability so follow-ups land).
- **Recipes:** hearth (bricks + campfire + stone bricks), anvil (anvil + 3 iron + oak log), bellows, pattern board, smithy
  sign, hammer (5 iron + 2 sticks). Hearth/anvil are pickaxe-mineable (the manor's statues/bust/urn were added to the
  pickaxe tag at the same time - they `requiresTool()` and dropped nothing before). Advancements (gen_advancements.py):
  `forged_weapon` "Hammer and Tongs" (granted in code on the first forging) -> `master_smith` (own all six).
- **Art:** `python tools/gen_forge_assets.py` (icons through gen_overhaul_textures' sprite engine, the smithy sign board,
  the pattern board, block models + previews in tools/previews/forge/).
- **Verified on a server** (`/ppforgetest`, op: a fake player runs every pattern through the real blocks, a mending, a
  wrong-material and a cold-hearth case, and each weapon's ability - 16/16 OK). NOT verified in a client: the anvil
  renderer, the models, the hammer feel, the numbers in a real fight.
