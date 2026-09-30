# Overnight build - progress log (2026-09-30)

Everything new lives in `src/main/java/net/get900/pixelpirates/homestead/` with its own registries, creative tab
("Pixel Pirates: Homestead"), datagen providers (`homestead/datagen/`), textures (`tools/gen_homestead_textures.py`),
shaped block models (`tools/homestead_models.py` on the new `tools/blockmodels.py`, which also renders isometric
previews into `tools/previews/homestead/`), lang (`tools/homestead_lang.py`) and an asset checker
(`tools/check_assets.py` - every block/item has a blockstate, model, real texture and a name). GeckoLib models (traders,
flag, roulette table, guns) are mobkit builders in `tools/mobs/` (traders.py, hideout.py, roulette.py, guns.py).

No screenshots this round. Everything is verified by compile + datagen + asset check + dedicated-server scripts
(`build/tmp/claude/drive_homestead.py <server> <script>`, scripts `t_rum/t_batch1..4.txt`). **Nothing has been seen in
a real client yet**, so looks, animations and "feel" are unverified.

All 18 items from your list are done: 55 blocks and 104 items in all.

| # | Feature | What you get | Server-tested |
|---|---|---|---|
| 11 | Tropical crops | pineapple, lime, chili (seeds from island grass 3.5%, or the Quartermaster) + galley food (fruit salad, ceviche, spicy chowder, chocolate doubloon, pineapple grog) | crop drops |
| 12 | Rum distillery | rum still (needs a fire under it) -> raw rum; aging cask -> aged -> vintage; Tipsy effect (wobbly camera) | distils over fire only; cask NBT; tipsy |
| 5 | Palm & tropical building | palm stairs/slab/fence/gate/door/trapdoor, thatch (+stairs/slab), woven palm screen and mat, driftwood fence, rope ladder, rope bridge, tiki torch | block states |
| 2 | Furniture | captain's desk, sea chest (54 slots, keeps its contents), cargo crate, treasure pedestal, rum rack, hanging net, rope coil, hanging rope, ship's wheel, display cannon, map table, captain's chair and barrel stool (you can sit on them) | all states; chest contents; pedestal |
| 21 | Flintlock pistol + blunderbuss | 3D GeckoLib guns; paper cartridge / scattershot ammo; reload times; musket balls | musket-ball damage |
| 24 | Region tool sets | ember (auto-smelts), kraken (full speed underwater and in the air), bone tools | tags / recipes |
| 3 | Treasure hoard | a coin pile that grows through 8 levels as you add coins; Luck, then Hero of the Village nearby | - |
| 13 | Fishing | 11 new fish by phase (via the fishing loot table), 3 trophy fish, lobster and crab claw, fish trap, lobster pot | biome fish; trap catches; lobster pot table |
| 9 | Salvage | sea-floor wreck sites with salvage crates; the salvage hook reels up salvage instead of fish | hook table; the site places |
| 15 | **Trading post + NPC traders** | 4 Port Traders (Quartermaster, Fishmonger, Barkeep, Curio Dealer: GeckoLib, one model with 4 skins) that set up at Wavebreak Port's market stalls the first time you visit; vanilla trade screen in pirate coins; restock every 2 min; can't be hurt. **Trading Post** counter: sell a held stack for 75%; sneak-use with 32 coins hires a trader to stand at it | trader offers; stall spots open |
| 16 | **Bounty board** | 3 contracts per player per in-game day (hunt / deliver / sink a faction ship), scaled to your boss progress; the board takes deliveries and pays coins + reputation | board places *(contract flow needs a real player)* |
| 17 | **Captain's Spyglass** | zooms like the vanilla spyglass; shows a creature's name + HP, or a ship's flag + hull; treasure glints on unopened loot within 64 | *(needs a real player)* |
| 18 | **Compass of Desire** | sneak-use cycles HOME (hideout, then bed, then spawn) / DEATH / TREASURE (nearest undiscovered dungeon; reaching it marks it found) / QUARRY (next boss lair); 32-frame needle | *(needs a real player)* |
| 1 | **Hideouts** | the Jolly Roger flag (GeckoLib, waves, raises when you claim). One per player. No monster spawns within 32/48/64 blocks; use = respawn point; 64 then 128 coins upgrade it; tier 3 gives Regeneration at home | flag placed with its block entity |
| 4 | **Dock & mooring** | Mooring Post: moor a ship within 12 blocks (held like an anchor, re-asserted after restarts); repairs 8 HP every 30 s, 16 in your home port (a post inside your hideout) | post block entity *(no ship on a headless server)* |
| 22 | **Grappling hook** | throw (28 blocks), bites a block and reels you in, no fall damage, sneak / use again to let go; yanks creatures it hits; rope rendered | an ownerless hook cleans itself up |
| 25 | **Ship upgrades** | chain shot (40% hull damage, the target ship is fouled for 5 s) and grape shot (12-pellet cone) for the ship cannon; 4 figureheads that buff everyone aboard (mermaid: dolphin's grace + water breathing; kraken: strength; dread skull: resistance; navy eagle: speed + haste); 5 sail canvases (white, black, crimson, striped, Jolly Roger) | cannon keeps its ammo type; figurehead |
| 29 | **Parrot companion** | a shoulder parrot no longer flies off when you swim, fall or get hit (sneak for 2 s to set it down); it squawks and makes monsters glow when they close in on you; it sniffs out loot within 24 blocks. Wild parrots now spawn in island thickets | *(needs a real player)* |
| 20 | **Captain's Logbook** | writes itself: bosses beaten, the boss chain, a fish codex (caught / uncaught), charted treasure, faction standing. Opens in the vanilla book screen | *(needs a real player)* |
| + | **Roulette table** | a GeckoLib table with a procedurally spun wheel that stops on the real result; bet up to 16 coins on red/black/odd/even/low/high (2x) or zero (36x); 3 s for others to join, 5 s spin | full round: bets -> spin -> the winner paid (ball on 15 black) |

## Things to check first in-game
1. **Traders** at the market stalls (north row, x -29 / -17 / 17 / 29, z 20): do they stand in their stalls? Trading
   screen, skins.
2. **Roulette wheel**: does the ball come to rest on the pocket that is announced? The direction was measured with
   `preview_geo`, not in a client.
3. **Jolly Roger** flag render (GeckoLib block) and its item sprite; the waving.
4. **Grappling hook** feel: the reel speed (1.1 blocks/tick) and whether the rope draws from your hand.
5. **Spyglass zoom** (mixin `CaptainsSpyglassMixin`) and the ship read-out while looking at an AI ship.
6. **Parrot**: does it still ride through swimming? Does sneaking for 2 s set it down?
7. **Logbook**: the first open after crafting. The pages are written on craft and every 5 s after.

## Known limits
- The roulette table is about 2 blocks long but collides as 1 block (the wheel end).
- Bounties, spyglass, compass, parrot and logbook have only been compile- and boot-tested, because they need a player.
- The Curio Dealer's glowmask exists, but the trader renderer has no glow layer yet.
