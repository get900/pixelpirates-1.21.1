# Spoilers and acceptance cases

Number controls I onward from design-left to right while facing into the site. The central inscription is separate from the row. All solutions are available through its clues; no external guide is necessary to play.

1. **Hushed Bell Court:** I, III, II, IV, III. An incorrect note clears progress, except I immediately begins the sequence again. Test a mistake after two notes and then the full sequence. Each note plays a different bell pitch by control index.
2. **Lantern Confluence:** from the authored I/III/V-lit state, press I, III, V. Pressing a control flips itself and its immediate neighbours; endpoints affect only two stones. The order of these three presses does not matter. Reset restores the original lit pattern.
3. **Ferryman's Balance:** select weights II and III, worth 3 and 7. The four weights are 1, 3, 7, 12; this is the only subset totalling 10. Selected weights are lit. Wrong totals cause no damage and can be toggled back.
4. **Tidewheel Oracle:** starting at North/0 on all four, press I once, II three times, III twice, IV once. Final values are Dawn/1, Dusk/3, Zenith/2, Dawn/1. Matching dials light up. Cycles wrap at four.
5. **Mnemonic Reliquary:** pair I–VI (Anchor), II–V (Crown), III–VIII (Eye), IV–VII (Wave). Reveal either member first. Mismatches close the current pair without clearing previously matched pairs. Clicking the same stone twice never matches it with itself. There is no timer.

For each site: try a wrong input; reset; solve halfway; save/reload; finish; open the rear plinth chest; revisit/reload and confirm no second reward. Try using controls after completion. Check all four rotations and separate nearby sites for state isolation. Two players share one site's state; coordinate turns. Rapid duplicate clicks within four server ticks are ignored.

Before solving, the reward chest has no loot table and is empty. If a player has stored items there, solving asks them to empty it, then use a control again; it must not overwrite player items. If the chest was removed, replacing a chest on its original plinth and pressing a control should release the pending reward. After treasure is released, replacing that chest must not give more loot.

Architectural blocks and controls retain normal survival mining. Breaking the central controller destroys that site's saved puzzle state; breaking individual controls can prevent solving. Player-altered sites are not automatically repaired. The puzzle reset repairs progress only. Do not rebuild a structure on chunk load: a manual rebuild is a new placement and can reset its containers/state.

---

# Expansion puzzles (kinds 5-7, the fifteen-location expansion, 2026-10-03)

# Phase 5 solutions (spoilers)

Controls are numbered left to right in design space when approaching from +Z. Interact with the inscription to read the full clue and current state. All puzzles are untimed; wrong inputs allow another attempt.

## Verdict of the Four — kind 5

Choose I. Assuming seals I, II, III or IV respectively makes 1, 2, 0 or 3 witness statements true. Only seal I satisfies exactly one truth. Wrong choices do not consume a key or damage the player.

## Processional Orrery — kind 6

Starting order: Dusk, Dawn, Night, Noon (3,1,4,2). I swaps positions 1–2, II swaps 2–3, III swaps 3–4; IV submits. Press I, III, II, IV. The submitted order must be Dawn, Noon, Dusk, Night. An incorrect submission leaves the current order available for further swaps. All 24 permutations can reach the solution.

## Measured Depths Reservoir — kind 7

I fills the small vessel; II fills large; III pours small into large; IV pours large into small; V empties small; VI empties large. Transfers stop when the source empties or destination fills.

Press II, IV, V, IV, II, IV. (Small, large) becomes (0,5), (3,2), (0,2), (2,0), (2,5), (3,4). Four units remain in the large vessel, releasing the seal. All 16 reachable measure states have a route to success.
