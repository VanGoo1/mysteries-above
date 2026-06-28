# Creature Ecology & Combat Scaling — Design

**Date:** 2026-06-28
**Status:** Approved
**Branch:** feat/hunting-creatures

## Goal

Make the mythical creature ecosystem feel deliberate and progression-aware:

1. Creatures are aggressive from spawn (hunt the player, not wait to be hit).
2. Creatures hit a bit softer (base damage reduced).
3. Creatures only spawn far from civilization (≥ 2000 blocks from world spawn).
4. A **Law of Convergence** biases *which* creature spawns near a Beyonder toward their
   pathway and the sequence they are about to need — without making farming trivial.
5. A **defense scaling** so stronger Beyonders (lower sequence number) take less damage
   from otherworldly creatures, reusing the existing `SequenceScaler`.

These are tuning/ecology changes layered on the existing creature system
(`CreatureSelector`, `CreatureSpawner`, `CreatureBehaviorManager`, the two spawn listeners).

## Non-Goals

- No new creatures, no loot changes, no BossBar.
- Convergence does **not** change the *total* natural spawn rate — only redistributes which
  creature wins an already-rolled spawn.
- Structure spawns are NOT affected by convergence (they are rare, apex, location-driven).

---

## 1. Distance gate

Both `NaturalCreatureSpawnListener` and `StructureCreatureSpawnListener` skip the spawn when
the location is within `min-spawn-distance` of the **world spawn point**, measured on the
horizontal plane (X/Z only — "у всі сторони" = all horizontal directions; Y ignored).

- Config: `config.yml` → `creatures.min-spawn-distance` (blocks), default **2000**.
- Comparison uses squared horizontal distance to avoid `sqrt`.
- The admin `/creature spawn` command **bypasses** the gate (testing/debug).
- A pure helper encapsulates the check so it is unit-testable:
  `SpawnDistanceGate.isFarEnough(double dx, double dz, double minDistance)`
  (or equivalent), keeping the listeners thin.

## 2. Law of Convergence (natural spawns only)

### Data
- Add `int sequence` to `CreatureDefinition` (0..9). `CreatureConfigLoader` derives it from an
  explicit `sequence:` key if present, otherwise from the id suffix after the last `_`
  (e.g. `visionary_manhal_9` → 9). If neither yields a valid 0..9 value, default to 9 and log
  a warning.

### Selection bias
- New pure value object `ConvergenceBias(String pathway, int sequenceLevel)`.
- `CreatureSelector` gains an overload:
  `pickForBiome(String biome, String type, double roll, ConvergenceBias bias)`.
  The existing no-bias `pickForBiome(biome, type, roll)` is retained (delegates with a
  null/no-op bias) so current tests and the structure path are unaffected.
- Weighting rule for each candidate that already passes biome+type+`naturalChance>0`:
  - base weight = `naturalChance`.
  - if `bias != null` and candidate `pathway` equals `bias.pathway` (case-insensitive):
    - creature `sequence == bias.sequenceLevel - 1` (the next-needed) → weight ×**4**.
    - creature `sequence == bias.sequenceLevel` (current) → weight ×**2**.
    - otherwise unchanged.
  - **Renormalize**: after applying multipliers, scale every candidate weight so the sum of
    weights equals the original sum of `naturalChance` values. This preserves
    `P(any spawn)` exactly — convergence only redistributes the probability mass among the
    matching candidates.
  - Pick by cumulative weight against `roll` (same segment logic as today; "no spawn" when
    `roll ≥ totalChance`, where `totalChance` is the unchanged original sum).
- Multipliers (×4 / ×2) live as named constants in the selector.

### Wiring
- `NaturalCreatureSpawnListener` finds the **nearest online Beyonder** within a fixed radius
  (e.g. 48 blocks) of the spawn location. If found, it builds
  `ConvergenceBias(beyonder.getPathway().getName(), beyonder.getSequenceLevel())` and passes
  it to the biased overload. If none, it calls the no-bias overload (today's behavior).
- Pathway name match: creature `pathway` strings are lowercase
  (`error`, `door`, `justiciar`, `visionary`, `whitetower`, `fool`); `Pathway.getName()`
  returns the concrete class simple name. The listener normalizes both to a common form
  (lowercase, and strips a trailing `pathway` suffix if the class is e.g. `ErrorPathway`)
  before constructing the bias, OR a small mapping helper is used. The exact normalization is
  decided during implementation by inspecting the concrete pathway class names; whatever the
  form, matching must be case-insensitive and robust.

### Tests
- `CreatureSelectorTest`: bias toward `level-1` makes the next-needed creature far more likely
  while total spawn probability (sum) is unchanged; no-bias overload identical to before;
  bias for a pathway with no matching candidates is a no-op.

## 3. Aggressive from spawn

A small infrastructure helper `CreatureAggression`:
- `acquireTarget(LivingEntity, double range)` — if the entity is a `Mob` and has no live
  target (null or dead), set its target to the nearest `Player` within `range`.
- Called:
  - on spawn in `CreatureSpawner.spawn(...)` after the behavior is started, and
  - on every `CreatureBehaviorManager` tick for each live tagged creature (re-aggro after a
    target is lost or dies).
- Range ties to the creature's follow/tracking; a sensible constant (e.g. 24 blocks) is used.
- This is independent of pathway behaviors, so creatures with a null behavior would still
  aggro — but in practice all current creatures have a pathway behavior. To guarantee
  universal aggression, `CreatureBehaviorManager` iterates all tagged creatures for the
  aggression pass regardless of whether a `CreatureBehavior` is registered (the registry may
  need to track all tagged creatures, or aggression is driven off the codec tag rather than
  the behavior map — decided in implementation).

## 4. Damage scaling defense (reuse `SequenceScaler`)

- Add `SequenceScaler.creatureDamageReduction(int sequenceLevel)` returning a fraction in
  `[0, 0.45]`: `min(0.45, power * 0.05)` where `power = 9 - sequenceLevel`.
  Seq 9 → 0%, Seq 5 → 20%, Seq 0 → 45%. Pure, unit-tested.
  (Existing scaler methods stay; we only add this one. Light cleanup/Javadoc allowed.)
- New `CreatureDamageListener` (presentation/listeners) on `EntityDamageByEntityEvent`:
  - If the damager is a tagged creature (via `CreatureCodec`) AND the victim is a `Player`
    who is a Beyonder, multiply final damage by `(1 - creatureDamageReduction(level))`.
  - Non-Beyonder / non-player victims: untouched.
  - Damager not a tagged creature: untouched.
  - Guard the damager resolution (projectiles: if the creature attacks via a projectile, the
    direct damager may be the projectile — resolve the shooter where feasible; otherwise only
    melee is covered, which is acceptable for v1).
- Wire in `ServiceContainer` and register the listener in the plugin.

## 5. Lower base creature damage

Reduce `stats.damage` for creatures in `creatures.yml` by ~30% (rounded sensibly per tier),
so even an un-reduced hit on a weak Beyonder is gentler. This pairs with #4 instead of
double-nerfing strong players.

---

## Architecture fit

- **Pure domain (unit-tested, no Bukkit):** `ConvergenceBias` VO, `CreatureSelector` bias
  overload + renormalization, `SequenceScaler.creatureDamageReduction`, the distance-gate
  helper, `CreatureDefinition.sequence`.
- **Effect layer / infrastructure / presentation (verified in-server):** `CreatureAggression`,
  `CreatureBehaviorManager` aggression pass, `CreatureSpawner` aggro-on-spawn,
  `CreatureDamageListener`, the two listeners' distance gate + convergence wiring, config
  read, `creatures.yml` tuning.
- No `domain` → `pathways`/Bukkit dependency added; ArchUnit invariants preserved.

## Deployment note

`creatures.yml` gains a derived/added `sequence` per creature (or relies on id suffix). Because
`saveResource("creatures.yml", false)` does not overwrite an existing server file, the server's
`plugins/Mysteries-Above/creatures.yml` must be deleted/refreshed for the lowered damage values
to take effect (the new jar regenerates it).
