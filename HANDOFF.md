# PIXEL PIRATES - SESSION HANDOFF (2026-09-30)

Written for the next Claude session picking this project up. **Read `CLAUDE.md` first** (project root) - it is the
master briefing: every system, gotcha, crash cause, verification recipe and boss write-up lives there. This file only
covers where things stand right now and what to do next.

---

## 1. How this user likes to work

- **Big features are designed in a Notepad `.txt` first** (not Word, not a chat plan). Write the design to
  `D:\Minecraft Modding\<Name> - Design.txt`, open it with `notepad.exe`, and let the user edit it. They answer
  questions inline in the file. Build only after they say go.
- **Boss overhaul template** (all 10 bosses are now done): design with the user -> entity -> model (tools/mobs/*.py)
  -> lair -> test hooks (`/ppboss ...` ops) -> verify on a server on a NATURALLY generated site -> document in
  CLAUDE.md -> the user playtests and sends "round 2/3" fixes.
- **The user playtests in-game themselves.** Don't run long client sessions for them. Verify on a headless test
  server instead, then tell them exactly what to look at and which commands to use.
- Be honest about what is verified vs not; they rely on the "NOT verified" lists.
- They get excited about big, detailed, unique content ("take your time and make sure we don't miss anything").

## 2. Environment / tooling

- Repo: `D:\Minecraft Modding\pixel-pirates-1.21.X` (despite the name: **MC 1.20.1, Fabric**, Java 17 toolchain).
- Build: `./gradlew build -x test` -> `build/libs/pixelpirates-1.0.0.jar`. `./gradlew runDatagen` after adding
  blocks/items/recipes/loot (output in `src/main/generated/`, never hand-edit).
- **Isolated test server** (safe to wipe): `C:\Users\get90\.claude\jobs\d76078dd\tmp\ts` (seed 424242, port 25599).
  If that job folder is gone, copy `D:\Minecraft Modding\PixelPirates-Modpack\PixelPirates-Server` somewhere scratch.
  Never touch the user's own server `D:\Minecraft Modding\server`.
- Server driver scripts (Python, pipe commands into the server's stdin and grep the log):
  `build/tmp/claude/drive_*.py` - e.g. `drive_leviathan.py <server dir>` (full hunt, ~17 min),
  `drive_leviathan_r2.py`, `drive_leviathan_r3.py` (Spire gen + Coil the Spire), `drive_heart.py`. Copy the pattern for new tests. Watch out: only ONE driver per
  server dir at a time (two once wrote into the same log), and `forceload add` silently fails above 256 chunks.
- Java 17 is at `C:\Program Files\Eclipse Adoptium\jdk-17.0.15.6-hotspot\bin\java.exe` (JAVA_HOME is 21).
- Asset generators (Python, `pip install pillow numpy`): mob models `tools/gen_mob_roster.py` + `tools/mobs/*.py`,
  item/block textures `tools/gen_overhaul_textures.py`, worn armor layers `tools/gen_armor_layers.py`,
  Leviathan assets `tools/gen_leviathan_assets.py`, previews `tools/preview_geo.py`, z-fight check
  `tools/check_zfight.py`.

## 3. State right now (updated end of 2026-09-30)

- **All 10 chain bosses are overhauled**, including 9 Abyssal Heart and 10 The Leviathan (a 3-lair world hunt with
  2 ports that get destroyed). Full details in CLAUDE.md under "MOB ROSTER & BOSS LAIRS".
- Leviathan playtest rounds 2 AND 3 are done and server-verified (CLAUDE.md, boss 10 "Round 2" / "Round 3"):
  Spire stair exit + bells on the stair, no natural spawns in the arenas, Rift Seal needs the Heart beaten, the
  Leviathan stays visible between lairs, tail gap; then the wearable Crown of the Drowned, the head no longer stares
  up while travelling, Coil the Spire + the Apocalypse wave reaching the Spire top (no camping), no stray natural
  features in/around ANY structure (new chunks only), reliable powder barges in the Gullet.
- **Five boss armor sets are built** (section 4).
- **Modpack for friends - CURRENT, re-exported and boot-tested at the end of this session:**
  `D:\Minecraft Modding\PixelPirates-Modpack\PixelPirates-Server.zip` + `PixelPirates-Client.zip` (+ unzipped folders).
  Server README has a boss-testing cheat sheet incl. `/pparmortest`.
  - **If you change the mod, rebuild the jar and replace `pixelpirates-1.0.0.jar` in BOTH folders, then re-zip**
    (server and clients must have the identical jar; friends only need to swap that one jar).
  - The user and friends are now BALANCE TESTING all the bosses + the armor with it. Expect feedback rounds.
  - Worldgen fixes only apply to new chunks - tell them to start a fresh world when a change touches structures/loot.
- Git: everything is committed on branch `pixelpirates` (latest `f72be90`). Commit when the user asks, with the
  scratch files left out (`org/`, `data/`, `MsgFmt*`, `Capture.PNG`, `port_city_preview.png` are deliberately untracked).

## 4. Boss armor revamp - BOSS SETS DONE, old-set visual overhaul NEXT

**Done (2026-09-30, same session):** the five boss sets are built, server-verified and documented - see the
"BOSS ARMOR SETS" section in CLAUDE.md (items, Boss Ward, set bonuses, loot, kill drops, `/pparmortest`).
Design file with the user's answers: `D:\Minecraft Modding\Boss Armor - Design.txt`.

**Still to do:**
1. **Real art for the 5 boss sets** - today's textures are palette repaints of existing sets (`tools/gen_boss_armor.py`),
   good enough for balance testing only. The looks are described per set in the design file.
2. **Visual overhaul of the 6 old region sets** (castaway, navy officer, corsair, ashen, cursed bone, kraken scale) - the
   user wants them kept but made to "look a lot better": generate reference images for yourself and base the looks on
   them; **custom 3D models are allowed** (GeckoLib 4.4.9 `GeoArmorRenderer`). Worn textures otherwise live in
   `assets/minecraft/textures/models/armor/<material>_layer_1/2.png` (drawn by `tools/gen_armor_layers.py`).
3. Set-bonus gaps listed in CLAUDE.md ("NOT covered yet": Revenant hook/noose/shackle, King maelstrom /
   `Abilities.pull`) if playtesting shows they matter.
5. Not yet seen in-game by anyone: the boss armor set bonuses in a real fight, the worn Crown's 3D model
   (`models/item/crown_of_the_drowned.json`, elements + `display.head`), Coil the Spire from the player's side.
4. The user + friends are balance-testing with the modpack; expect tuning requests (numbers live in `ModArmorMaterials`
   and `BossArmor.WARD`, bonuses in `BossArmor.modify/tick`).

## 5. Useful commands (op)

`/ppboss status|set <0-10>` · `/ppunlock 5` · `/pptest god|clearsight|all` · `/ppdungeon locate <id>` ·
`/ppdungeon <id>` (build a lair at your feet) · `/ppleviathan status|build <site>|do <op>` (ops listed in CLAUDE.md) ·
`/give @s pixelpirates:dimension_key` (survival players have no other way into the mod's dimension).
