# Ambient Convergence Spawn — Design

**Date:** 2026-06-28
**Status:** Approved
**Branch:** feat/hunting-creatures

## Goal

Mythical creatures should also appear **on their own** in the open world — not only by
replacing a vanilla mob — and that ambient appearance must be **driven by the Law of
Convergence** (a Beyonder draws creatures of their own pathway and the sequence they are about
to need). The chance must be **very small**, and the existing natural-**replacement** channel
must be **cut to ~1/4** so the world does not over-populate.

This builds on the existing creature system (`CreatureSelector`, `CreatureSpawner`,
`ConvergenceBias`, `SpawnDistanceGate`, the two spawn listeners).

## Non-Goals

- No change to the structure-spawn channel (`StructureCreatureSpawnListener`) — still additive,
  apex, location-driven.
- No BossBar, no new creatures, no loot changes.
- Ambient spawns are **convergence-only**: they require a nearby Beyonder with a pathway +
  sequence. A non-Beyonder never triggers an ambient spawn.

## Channels after this change

| Channel | Replaces a mob? | Convergence? | Chance |
|---|---|---|---|
| Natural replacement (`NaturalCreatureSpawnListener`) | Yes (cancels vanilla spawn) | Yes (selection bias) | existing × 0.25 |
| **Ambient (new)** | **No (additive)** | **Yes (pathway+sequence restricted)** | ~1 per 45 min per eligible Beyonder |
| Structure (`StructureCreatureSpawnListener`) | No (additive) | No | unchanged |
| Admin `/creature spawn` | No | No | n/a (bypasses gates) |

---

## 1. Selector: `pickForAmbient` (pure domain)

New method on `CreatureSelector`:

```
Optional<CreatureDefinition> pickForAmbient(String biome, ConvergenceBias bias)
```

- `bias` is required (non-null); if null, return empty.
- Eligible candidate: `naturalChance > 0` AND `naturalBiomes` contains `biome` AND
  `pathway.equalsIgnoreCase(bias.pathway())`.
- Weighting: same multipliers as convergence — creature `sequence == bias.sequenceLevel - 1`
  → ×4 (next-needed), `== bias.sequenceLevel` → ×2 (current), else ×1.
  Reuse the existing private `multiplier(def, bias)`.
- **No "no-spawn" gate.** The scheduler already decided to spawn; this method only chooses
  *which* creature. Pick by cumulative normalized weight using a fresh `roll` drawn inside the
  method (it owns its own randomness, or accepts a `roll` for testability — see Testing).
- Returns empty only when there are zero eligible candidates.

This reuses the biome data already in `creatures.yml` and the pathway/sequence convergence
rule, so ambient spawns are immersive (right biome) and convergence-bound (your pathway, your
upcoming sequence).

**Testability note:** to keep the method unit-testable deterministically, it takes an explicit
`double roll` parameter: `pickForAmbient(String biome, ConvergenceBias bias, double roll)`. The
scheduler passes `random.nextDouble() * totalWeight`-style input via the method's own
normalization, OR the method normalizes internally and the caller passes `random.nextDouble()`
in `[0,1)`. Chosen contract: `pickForAmbient(biome, bias, roll)` where `roll` is in `[0,1)` and
is applied against the internally renormalized cumulative weights (so any eligible candidate is
always returned for `roll` in `[0,1)`; empty only when no candidate is eligible).

## 2. `AmbientCreatureSpawner` (scheduler)

Mirrors `PassiveAbilityScheduler`: a class with `start()` / `stop()` owning a single
`BukkitTask` from `runTaskTimer`.

- **Interval:** `creatures.ambient.interval-seconds` (default 60) → ticks every 60 s.
- Each tick, for every online player who is a Beyonder:
  1. **Distance gate:** skip unless `SpawnDistanceGate.isFarEnough(dx, dz, minSpawnDistance)`
     from world spawn (reuse `creatures.min-spawn-distance`, default 2000).
  2. **Chance roll:** `random.nextDouble() < creatures.ambient.chance` (default 0.022); else
     skip. (60 s / 0.022 ≈ 2700 s ≈ 45 min mean.)
  3. **Anti-accumulation cap:** count tagged creatures (via `CreatureCodec.isCreature`) within
     64 blocks of the player; skip if ≥ `creatures.ambient.max-nearby` (default 3).
  4. Build `ConvergenceBias(beyonder.getPathway().getName(), beyonder.getSequenceLevel())`,
     read the player's current biome, call
     `selector.pickForAmbient(biome, bias, random.nextDouble())`.
  5. If present, find a safe spawn location 24–48 blocks from the player (see §3); if found,
     `creatureSpawner.spawn(def, loc)` — additive, no replacement. The spawner already applies
     stats, the PDC tag, aggression-on-spawn, and the pathway behavior.
- Wrap each player's processing in try/catch so one player's error never stops the loop
  (same pattern as `PassiveAbilityScheduler.tickAllPlayers`).

## 3. Spawn-location finder

A small helper (e.g. `AmbientSpawnLocation.findSurfaceNear(Location center, double minR, double maxR)`
→ `Optional<Location>`):

- Try a handful of random attempts: random angle, random radius in `[minR, maxR]`.
- Use the world surface: `world.getHighestBlockAt(x, z)`; candidate is one block above it.
- Require: candidate block and the block above are passable (2-high clearance); the block below
  is solid and not `LAVA`.
- Return the first qualifying candidate, else empty (and the scheduler simply skips this tick).

This keeps ambient spawns on the surface and out of the player's lap (≥24 blocks).

## 4. Config additions (`config.yml`)

```yaml
creatures:
  min-spawn-distance: 2000   # existing
  ambient:
    interval-seconds: 60     # how often the ambient check runs
    chance: 0.022            # per-check spawn probability per eligible Beyonder (~1 / 45 min)
    max-nearby: 3            # skip if this many tagged creatures already within 64 blocks
```

## 5. Cut existing replacement chance

Multiply every `spawn.natural.chance` in `creatures.yml` by **0.25** (round sensibly, keep > 0
for creatures that had a non-zero chance). Do not touch structure chances, stats, loot, biomes.

---

## Wiring & architecture fit

- **Pure domain (unit-tested):** `CreatureSelector.pickForAmbient` (+ reuses `ConvergenceBias`,
  `multiplier`). No Bukkit.
- **Infrastructure (in-server verified):** `AmbientCreatureSpawner` scheduler,
  `AmbientSpawnLocation` finder, config reads.
- Construct `AmbientCreatureSpawner` in `ServiceContainer` (needs `MysteriesAbovePlugin`,
  `BeyonderService`, `CreatureSelector`, `CreatureSpawner`, `CreatureCodec`, and the config
  values). Expose a getter; call `start()` from `startSchedulers()` and `stop()` from
  `stopSchedulers()`.
- No new `domain` → Bukkit or `domain` → `pathways` dependency. ArchUnit invariants preserved.

## Deployment note

`creatures.yml` changes (lowered natural chances) need the server's stale
`plugins/Mysteries-Above/creatures.yml` deleted to regenerate, as before
(`saveResource(..., false)` does not overwrite). The new `config.yml` ambient keys regenerate
the same way (and `saveDefaultConfig()` — added earlier — writes the file on first enable).

## Testing

- `CreatureSelectorTest`: `pickForAmbient` returns a pathway+biome match weighted toward
  next-needed; empty when no candidate matches pathway or biome; empty when bias is null;
  ignores `replace`-type entirely (no vanilla mob involved).
- Scheduler, location finder, config reads: Bukkit — verified in-server per project convention.
