# Structures - where everything spawns
> Every structure, its phase and its biomes. Source of truth: `world/dungeon/Dungeons.java` (`Dungeons.ALL`).
> Update this table when you add, move or re-space a dungeon. As of 2026-10-04.

There are 57 dungeon types in `Dungeons.ALL`, plus the spawn island and the big boss structures that are placed separately.
Phase = ring zone (1 Starter ... 5 Abyss).

- **Site** - LAND: on dry ground. SEABED: on the sea floor. COAST: on a shoreline, facing the water. ISLET: at sea level in
  shallow water.
- **Min depth** - SEABED only: how many blocks of water must be above the floor.
- **Spacing** - grid cell size in chunks; at most one of that dungeon per cell (bigger = rarer).
- **Editable** - Yes = layout JSON, so `/ppstruct save` works on it (see `docs/structure-layouts.md`). No = built in Java.

## Dungeons

Sorted by phase, then site. Boss lairs show the boss in brackets.

| Structure | Phase | Site | Biomes | Min depth | Spacing | Editable |
|---|---|---|---|---|---|---|
| smugglers_grotto | 1 | LAND | Temperate Shallows, Island Thickets | - | 20 | Yes |
| tidewater_shrine | 1 | LAND | Temperate Shallows, Island Thickets | - | 22 | Yes |
| rackham_fort (Rackham) | 1 | LAND | Island Thickets | - | 36 | No |
| turtleback_orchard | 1 | LAND | Island Thickets, Temperate Shallows | - | 48 | Yes |
| sunken_galleon | 1 | SEABED | Temperate Shallows, Open Ocean | 2 | 24 | No |
| bloodfin_reef (Bloodfin) | 1 | SEABED | Open Ocean | 7 | 36 | No |
| wreckers_beacon | 1 | ISLET | Temperate Shallows, Island Thickets | - | 56 | Yes |
| castaways_refuge | 1 | COAST | Temperate Shallows, Island Thickets | - | 48 | Yes |
| pearl_divers_camp | 1 | COAST | Temperate Shallows, Island Thickets | - | 52 | Yes |
| powderwatch_battery | 1 | COAST | Temperate Shallows, Island Thickets | - | 60 | Yes |
| the_saltworks | 1 | COAST | Temperate Shallows, Island Thickets | - | 60 | Yes |
| driftwood_ropewalk | 1 | COAST | Temperate Shallows, Island Thickets | - | 84 | Yes |
| gullwing_boathouse | 1 | COAST | Temperate Shallows, Island Thickets | - | 84 | Yes |
| coral_temple | 2 | SEABED | Coral Bay, Reef Edge, Siren Sea | 4 | 24 | No |
| kraken_maw (Kraken) | 2 | SEABED | Coral Bay | 5 | 40 | No |
| serpent_trench (Serpent) | 2 | SEABED | Siren Sea | 6 | 40 | No |
| sunken_counting_house | 2 | SEABED | Coral Bay, Reef Edge | 14 | 56 | Yes |
| reefcutters_quarry | 2 | SEABED | Reef Edge, Coral Bay | 12 | 56 | Yes |
| sirens_bellcourt | 2 | SEABED | Siren Sea | 15 | 60 | Yes |
| tideglass_observatory | 2 | SEABED | Coral Bay, Siren Sea | 17 | 64 | Yes |
| azure_mosaic_baths | 2 | SEABED | Coral Bay, Reef Edge | 14 | 84 | Yes |
| pearl_courier_waystation | 2 | SEABED | Siren Sea, Reef Edge | 15 | 84 | Yes |
| coral_conservatory | 2 | SEABED | Coral Bay, Reef Edge | 15 | 84 | Yes |
| fire_camp | 3 | LAND | Ash Reef, Boiling Basin, Magma Sea | - | 24 | No |
| obsidian_vault | 3 | LAND | Ash Reef, Boiling Basin, Magma Sea | - | 28 | No |
| cinder_forge (Warlord) | 3 | LAND | Ash Reef, Boiling Basin, Magma Sea | - | 40 | No |
| sulfur_prospectors_camp | 3 | LAND | Ash Reef, Boiling Basin | - | 52 | Yes |
| cinderchain_tollgate | 3 | LAND | Ash Reef, Magma Sea | - | 56 | Yes |
| ashglass_kilnworks | 3 | LAND | Ash Reef, Boiling Basin | - | 60 | Yes |
| basalt_signal_redoubt | 3 | LAND | Ash Reef, Magma Sea | - | 60 | Yes |
| emberfall_cistern | 3 | LAND | Ash Reef, Boiling Basin, Magma Sea | - | 64 | Yes |
| scoria_switchback | 3 | LAND | Ash Reef, Magma Sea | - | 88 | Yes |
| cinderwake_caravansary | 3 | LAND | Ash Reef, Boiling Basin | - | 88 | Yes |
| brimstone_railhead | 3 | LAND | Ash Reef, Boiling Basin, Magma Sea | - | 88 | Yes |
| drowned_graveyard | 4 | LAND | Phantom Wake, Shipgrave Depths, Drowned Trench | - | 26 | No |
| widows_lantern_hospice | 4 | LAND | Phantom Wake | - | 56 | Yes |
| chainbreak_salvage_yard | 4 | LAND | Shipgrave Depths, Phantom Wake | - | 56 | Yes |
| mourning_archive | 4 | LAND | Phantom Wake, Shipgrave Depths | - | 60 | Yes |
| last_echo_theatre | 4 | LAND | Phantom Wake | - | 88 | Yes |
| blackwake_auction_court | 4 | LAND | Phantom Wake, Shipgrave Depths | - | 88 | Yes |
| ghost_ship (Ghost Captain) | 4 | SEABED | Phantom Wake | 2 | 40 | No |
| revenant_crypt (Chained Revenant - Gallows Grotto site) | 4 | SEABED | Shipgrave Depths, Drowned Trench | 4 | 40 | No |
| drowned_customs_house | 4 | SEABED | Drowned Trench, Shipgrave Depths | 15 | 60 | Yes |
| keelbone_ossuary | 4 | SEABED | Shipgrave Depths, Drowned Trench | 16 | 64 | Yes |
| sundered_prison_barge | 4 | SEABED | Shipgrave Depths, Drowned Trench | 14 | 88 | Yes |
| luminous_grotto | 5 | SEABED | Pillar Sea, Abyssal Rings, Maw Depths | 4 | 28 | No |
| abyssal_throne (Abyssal King) | 5 | SEABED | Abyssal Rings | 6 | 44 | No |
| abyssal_heart_lair (Abyssal Heart - Titan's Chest site) | 5 | SEABED | Maw Depths | 1 | 44 | No |
| leviathan_rift (Leviathan) | 5 | SEABED | Pillar Sea | 8 | 48 | No |
| hushed_bell_court | 5 | SEABED | Abyssal Rings | 16 | 64 | Yes |
| lantern_confluence | 5 | SEABED | Maw Depths, Pillar Sea | 16 | 64 | Yes |
| tidewheel_oracle | 5 | SEABED | Pillar Sea | 16 | 64 | Yes |
| ferrymans_balance | 5 | SEABED | Abyssal Rings, Maw Depths | 16 | 68 | Yes |
| mnemonic_reliquary | 5 | SEABED | Pillar Sea, Abyssal Rings | 16 | 68 | Yes |
| verdict_of_the_four | 5 | SEABED | Abyssal Rings | 16 | 92 | Yes |
| processional_orrery | 5 | SEABED | Pillar Sea | 16 | 92 | Yes |
| measured_depths_reservoir | 5 | SEABED | Maw Depths, Abyssal Rings | 16 | 92 | Yes |

The phase 5 layout sites are puzzle sites (one-time seal chest each).

## Big structures placed separately

Built in Java and rendered chunk by chunk in every biome, after the dungeons. None can be edited with `/ppstruct save`.

| Structure | Phase | Where it spawns | Boss | Notes |
|---|---|---|---|---|
| Wavebreak Port (spawn island) | 1 | Fixed at 0,0 | - | Hand edits: `/ppisland capture all` (`docs/spawn-island.md`) |
| Gallows Grotto | 4 | The `revenant_crypt` site (Shipgrave Depths, Drowned Trench) | Chained Revenant | ~185 x 250 x 160 (`docs/bosses-mid.md`) |
| Titan's Chest | 5 | The `abyssal_heart_lair` site (Maw Depths), only with no maw pit under it | Abyssal Heart | `docs/bosses-late.md` |
| Leviathan Rift | 5 | Leviathan route, radius 5000, seeded bearing 155-195 deg (Pillar Sea) | Leviathan, 1st third | One per world |
| Saltmarrow (port) | - | Route, radius 4150 | - | Ruined when the Leviathan passes |
| Leviathan Gullet | - | Route, radius 3050 | Leviathan, 2nd third | |
| Brightwater (port) | - | Route, radius 2150 | - | Ruined when the Leviathan passes |
| Leviathan Spire | - | Route, radius 1850, bearing +25 deg | Leviathan, final third | |

Other dungeons keep 96 blocks away from every Leviathan site. The route crosses rings, so those sites have no single phase.
