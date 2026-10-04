# Parrot ideas - EDIT ME (2026-10-03)

Edit anything: names, looks, rarities, weights, perks. Delete rows you don't want, add your own.
Nothing is built yet - we start from this file next session.

## How it would work (short)
- Same vanilla parrot model, new 32x32 texture per type (drawn by a script, like our other textures).
- Each parrot remembers its type in its save data, so it keeps its colours after a reload AND while it rides your shoulder.
- The aviary's daily restock draws from ALL types by weight (the table below), and the keeper will sell them later.
- Optional: a small perk per type, on top of the normal ship's-parrot powers. Leave the perk blank for "just looks".

## Rarity tiers
Common / Uncommon / Rare / Very Rare / Legendary. Weight = how many "tickets" it gets in each draw (bigger = more often).

## The existing 5 (vanilla) - suggested new weights
| Parrot | Rarity | Weight |
|---|---|---|
| Green Parakeet | Common | 30 |
| Grey Parrot | Common | 25 |
| Scarlet Macaw (red) | Uncommon | 15 |
| Blue-and-Gold Macaw | Rare | 8 |
| Hyacinth Macaw (all blue) | Very Rare | 3 |

## 10 new ideas
| # | Name | Rarity | Weight | Look | Perk idea (optional) |
|---|---|---|---|---|---|
| 1 | Sunset Conure | Common | 20 | orange body fading to yellow head, green wing tips | - |
| 2 | Cockatoo | Uncommon | 12 | white, pale yellow crest, grey beak | louder lookout: warns from further away |
| 3 | Rainbow Lorikeet | Uncommon | 10 | blue head, orange chest, green back | - |
| 4 | Cockatiel | Uncommon | 10 | grey with a yellow face and orange cheek spots | - |
| 5 | Black Palm Cockatoo | Rare | 6 | sooty black, red cheek patch, big dark beak | - |
| 6 | Kakapo (Mossback) | Rare | 5 | mossy green-yellow speckles, owl-like face | Aggressive, will help you attack |
| 7 | Ghost Parrot | Very Rare | 2 | pale see-through blue-white, faint glow (glowmask) | Possesses a lesser monster near you (no bosses) and fights for you in its body; when that body dies it's gone 10 min, then comes back (changed 2026-10-03) |
| 8 | Gilded Parrot | Very Rare | 2 | gold feathers with doubloon-shine highlights | small chance of extra coins from loot chests |
| 9 | Ember Macaw | Legendary | 1 | red-black with glowing orange wing edges (volcanic ring) wearing sunglasses | fire resistance while on your shoulder, shoots fire charges at enemies |
| 10 | Kraken's Pet (Abyssal Parrot) | Legendary | 1 | deep purple-black, teal glowing eyes + wing spots | Has a healing ability, when you are about to take a killing blow it will save you, then dissapear for 10 minutes before coming back. |

## Questions for you
- Should Legendary ones only appear in the aviary at all, or also come from bosses / the Leviathan hunt?	They should only show in the avaiary or in our treasure loot boxes.
- Should rare ones be sellable back to the keeper?		I think we will need a custom block that where you can see the ones you have unlocked and a sillhouette of ones yet to unlock and you can choose between them.
- Prices later (by rarity) - any idea of the coin range you want?		I trust you in determining how expensive they should be, but take into consideration the new abilities.

## PERCH BRANCH - progress log (started 2026-10-03, pick up from the last ticked step)
Plan: `pixelpirates:perch_branch` - a jungle-log branch that sticks out from a wall (placed on a wall, facing out), with
a twig and an azalea tuft; parrots stand on it. Tagged `minecraft:logs` so wild parrots' vanilla "fly onto tree" goal
picks it. A few go in the aviary (PortCityLayout.avAviary) on the cage walls.
- [x] 1. block registered (HomesteadBlocks.PERCH_BRANCH, FurnitureBlock with facing) + cutout render layer
- [x] 2. model + blockstate + item model (tools/gen_tavern_assets.py `perch_branch()`), lang, recipe, loot (datagen)
- [x] 3. logs tag (datagen ModBlockTagProvider)
- [x] 4. placed in the aviary + LayoutCheck WALL list + checker run
- [x] 5a. compile + datagen (loot, recipe 4x from jungle log + stick + azalea leaves, logs + axe tags) - all OK
- [ ] 5b. IN GAME (user): `/ppisland restamp` the aviary (#25) and look - (a) the model reads as a branch, the leaf tufts
      sit right, facing is correct on all 3; (b) parrots land on the branches (vanilla goal - may take a while, or need
      the LOGS tag to be enough); (c) placing one by hand against a wall points it outward.
      If parrots ignore them: add a small "fly to perch" goal to ParrotEntity (mixin) that targets perch_branch.
Placed: (-23,71,1) facing south, (-21,71,4) facing west, (-24,71,5) facing north. Model: tools/gen_tavern_assets.py
`perch_branch()` (jungle bark + azalea leaves, limb top at y 9/16). NOT seen in a client yet.

## PARROT TYPES - build plan (written 2026-10-03 from the answers above; tick as we go, pick up at the first unticked)
Phase 1 - the types exist (core, no abilities)
- [x] 1.1 parrot type saved on the parrot (custom id in its data, survives reload) - mixin on ParrotEntity
- [x] 1.2 renderer draws the custom texture (world) + on the shoulder (ShoulderParrotFeatureRenderer reads shoulder data)
- [x] 1.3 10 textures by recolouring the vanilla parrot texture (script in tools/), Ember's sunglasses painted on for now
- [x] 1.4 aviary daily draw uses all 15 types + the weights above; rare+ announcement already exists
- [x] 1.5 Legendary/rare parrot "eggs" or cages in the treasure loot boxes (only aviary + treasure, per answer)
  Phase 1 DONE in code 2026-10-03 (compiles, all mixin targets resolve in the refmap) - NOT yet seen in game:
  - homestead/parrot/ParrotTypes (all 15 types, tiers, weights, roll/apply), ParrotTypeHolder; mixin ParrotTypeMixin
    (tracked "PPType" + NBT), client ParrotTextureMixin + ShoulderParrotTypeMixin (lambda method_17958 + ThreadLocal)
  - textures: tools/gen_parrot_textures.py -> textures/entity/parrot/<id>.png + preview tools/previews/parrots.png
    (Cockatiel made a pale-yellow "lutino" - the vanilla Grey Parrot already IS a grey cockatiel)
  - Aviary draws all 15; PARROT_CRATE item (HomesteadItems, ParrotCrateItem): use on a block -> tamed parrot of its type;
    TreasureLoot: RARE pool 5% a Rare+ crate, LEGENDARY pool 8% a Very Rare+ crate
  - TEST (user): `/ppparrot <type>` gives a tame parrot of any type -> check each texture in the world, let it hop on your
    shoulder (colours must stay), log out/in, set it down (sneak 2 s) - type kept. `/ppaviary` for the cage draw.
    Texture parts were mapped from the UV layout without a render: if a face looks wrong (e.g. the Lorikeet's orange
    chest on the wrong side) say which and fix it in gen_parrot_textures.py.
Phase 2 - glow + the simple abilities
- [x] 2.1 glow layer for Ghost / Ember / Kraken's Pet
- [x] 2.2 Gilded: chance of extra coins from loot chests
- [x] 2.3 Cockatoo: longer lookout range
- [x] 2.4 Ember: fire resistance on the shoulder
  Phase 2 DONE in code 2026-10-03 (compiles, mixin targets resolve) - NOT yet seen in game. Abilities work only while
  the parrot rides your shoulder (ParrotCompanion.onShoulder reads the shoulder data's PPType).
  - glow: textures/entity/parrot/<id>_glow.png from gen_parrot_textures.py `glows()` (ghost = dim copy of the whole bird,
    ember = orange edges/crest, kraken = teal eyes + spots); world = homestead/client/ParrotGlowFeature (Fabric feature
    callback in HomesteadClient), shoulder = ShoulderParrotTypeMixin re-draws poseOnShoulder with RenderLayer.getEyes
  - Gilded: 35% on a loot chest's first open, 3-10 coins into an empty slot (else to you) - LootChestXpMixin -> gildedFind
  - Cockatoo: lookout range 14 -> 24.  Ember: fire resistance refreshed every 2 s while on the shoulder
  - aviary announcement now lists EVERY rare+ bird that came in (rarest first, "2x" for doubles) - user report
  - TEST (user): `/ppparrot ghost_parrot` / `ember_macaw` / `krakens_pet` at night (glow in the world + on the shoulder);
    Ember on the shoulder -> walk into lava/fire; Gilded on the shoulder -> open a fresh loot chest a few times.
Phase 3 - the big abilities (one per session is realistic)
- [x] 3.1 Kraken's Pet: cheats a killing blow, heals you, vanishes for 10 min (timer survives logout), comes back
- [x] 3.2 Kakapo: fights with you (attack goal when your target / attacker is near)
- [x] 3.3 Ember: shoots fire charges at enemies (cooldown); sunglasses as a real model piece if the painted ones look flat
- [x] 3.4 Ghost: flies through walls + fetches items off the ground
  Phase 3 DONE in code 2026-10-03 (compiles) - NOT yet seen in game. homestead/parrot/ParrotAbilities, all from the shoulder:
  - Kraken's Pet: Fabric ALLOW_DEATH - not the void//kill, a held totem goes first; half health + Regen II + Absorption II
    (10 s), the bird comes off the shoulder into HomesteadState.parrotsAway {Parrot, Back = overworld time + 12000} and
    returns to a free shoulder (or lands by you) - survives logout. PlayerShoulderInvoker got setShoulderLeft/Right.
  - Kakapo: 3 damage every 1.5 s to your foe within 6 (foe = your last target hit < 5 s ago, else nearest monster after you).
  - Ember: a small fireball every 4 s at a foe within 16 it can see; anonymous SmallFireballEntity whose onBlockHit only
    discards (no fires in the wooden port). Sunglasses are still painted on (3.3's model piece: only if they look flat).
  - Ghost (REWORKED 2026-10-03, user's call - item fetching removed): every 3 s, if active and not away, POSSESSES the
    nearest lesser monster within 12 (prefers one after you): a monster (vanilla Monster marker OR a MONSTER spawn-group
    type - our ModMob roster is PathAwareEntity, so HostileEntity alone missed it; Kakapo/Ember foe() use the same), max health <= 60, not ModBoss/KrakenArm/
    Lamplighter/Wither/Warden/Elder Guardian, not ridden. The bird goes into parrotsAway {Possessing: host uuid}; the host
    is renamed "Possessed X", made persistent, and MobPossessionMixin cancels setTarget(player); every 0.5 s steer()
    aims it at the nearest monster within 16 of you (else walks it back to you). Host dies (AFTER_DEATH) or is lost
    (unloaded / 48+ away, checked when you're online) -> Back = now + 12000 -> returns like the Kraken's Pet.
    parrotsAway is now per TYPE: player -> {type -> {Parrot, Back, Possessing?}} (a Ghost leaving no longer overwrites
    an away Kraken's Pet). TEST: Ghost on the shoulder near a zombie + skeleton; kill the host; `/time add 12000`.
  - TEST (user): Kraken - on the shoulder, take a killing blow (e.g. /damage @s 100), then `/time add 12000` to bring it
    back; Kakapo/Ember - fight a zombie.
Phase 4 - the collection block ("Parrot Roost"?)
- [x] 4.1 per-player unlocked types (unlock = tamed/bought one of that type)
- [x] 4.2 the block + screen: unlocked types shown, locked ones as silhouettes, pick one to call it to your shoulder
- [ ] 4.3 the keeper sells birds (with the unique NPCs) - prices by rarity AND ability:
      Common 20-40 coins, Uncommon 60-100, Rare 150-250, Very Rare 400-600, Legendary 1000+ (to tune)

  Phase 4.1 + 4.2 DONE in code 2026-10-03 (compiles, datagen, checker clean) - NOT yet seen in game. 4.3 waits for the
  unique NPCs (the port traders are tied to the bazaar booths).
  - homestead/parrot/ParrotCollection: unlock on setOwner (mixin TameableOwnerMixin) -> HomesteadState discoveries
    "parrot:<id>"; ONE OF EACH: seeds refused on an untamed parrot of an owned type (UseEntityCallback), a crate of an
    owned type stays shut, TreasureLoot.roll(pool, rng, owned) never rolls an owned type (TreasureMapItem passes it).
  - ACTIVE PARROT (answer 2): ParrotCompanion.activeType = left shoulder > right shoulder > nearest own following
    (tamed, not sitting) parrot within 16 - only that one's ability works. Kraken's Pet saves from a follower too.
  - PARROT_ROOST block (ParrotRoostBlock, model gen_tavern_assets.py `parrot_roost()`, recipe 3 sticks/jungle log/3
    jungle planks), one in the keeper's parlour at (-31,68,-7). Use -> ROOST_OPEN packet -> client ParrotRoostScreen:
    5x3 grid, owned = the live bird, locked = black silhouette (shader colour), rarity stripe, tooltip; click ->
    ROOST_RECALL -> recall(): the other bird of that type (loaded world / shoulder) is sent home, a fresh one to your
    shoulder; refused while a Kraken's Pet is away.
  - Roost screen skinned like the other GUIs (2026-10-03): textures/gui/roost.png from tools/gen_gui_textures.py
    `roost()` - dark-wood frame, brass studs, jungle-green felt with faint leaves, title plate, slot plates (normal /
    hover / locked) with a perch bar the bird stands on. Preview tools/previews/gui_roost.png.
  - TEST (user): tame a parrot -> "New parrot for your Roost"; open the Roost; recall; try seeding a 2nd of the same type.
OPEN QUESTIONS (answered by the user 2026-10-03):
- Collection block: once a type is unlocked, can you call up a NEW one any time (so losing a parrot doesn't lose it), or
  is it a stable for the parrots you actually own? When you unlock a parrot you can recall it at any moment.
- Abilities work only while the parrot rides your shoulder, or also when it's sitting/following? 	I think the abilities should be useable when riding your shoulder or following / flying near you but only 1 parrot can be active at anytime.
- Can you own several of one type? No, if you own a parrot it wont show up in loot boxes and you can only own one.
