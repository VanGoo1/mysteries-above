# Door Creature Behavior: Sealed Path — Design

**Date:** 2026-06-28
**Status:** Approved
**Branch:** feat/hunting-creatures

## Goal

Replace the Door pathway creature's combat behavior. The current `BlinkBehavior` teleports the
creature **behind** the nearest Beyonder every 3–4.5 s — too strong, because it constantly
occupies the player's blind spot and is hard to fight. Replace it with **"Sealed Path"**: a
short, thematic control debuff with **no teleport and no damage**.

## Replacement: `SealBehavior`

- New class `SealBehavior implements CreatureBehavior` in
  `me.vangoo.infrastructure.creatures.behavior`, replacing `BlinkBehavior` (delete the old file).
- `CreatureBehaviorFactory` `case "door"` switches to `new SealBehavior(apex)`.
- `SealBehavior` no longer uses `SafeLocations` or teleport.

### `tick(self, nearbyBeyonders)`
- Cooldown: apex **5000 ms**, common **7000 ms** (longer than the old blink → the debuff is not
  permanent; uptime ≈ half).
- Pick the nearest Beyonder (same `nearest` helper as the old class).
- "Close the door" around that player:
  - **Slowness** for 80 ticks (~4 s); amplifier 1 (Slowness II) for apex, 0 (Slowness I) for common.
  - **Darkness** for 60 ticks (~3 s); amplifier 0. (The soft Warden-style vision dim — the path
    fades.)
  - Both effects with `ambient = false`, `particles = false` (match the project's existing
    `PotionEffect(..., false, false)` style).
  - Spawn `Particle.REVERSE_PORTAL` around the player (converging particles = the door sealing):
    `target.getWorld().spawnParticle(Particle.REVERSE_PORTAL, target.getLocation().add(0,1,0), 40, 0.6, 1.0, 0.6, 0.05)`.
- No teleport, no Levitation, no damage.

### Why this is weaker
The creature stays where the player can hit it (no instant blind-spot repositioning); the debuff
is short with a visible cooldown; there is no positional dominance — only brief disorientation.

## Architecture fit

- Pure effect-layer change (`me.vangoo.infrastructure.creatures.behavior`), Bukkit allowed.
- No `domain` dependency, no balance math in `domain`. Mirrors the other creature behaviors
  (thin Bukkit choreography).
- 1.21 effect names: `PotionEffectType.SLOWNESS`, `PotionEffectType.DARKNESS`,
  `Particle.REVERSE_PORTAL`.

## Testing

Bukkit effect behavior — verified by compile/package + in-server, per project convention (no
headless test, consistent with the other behaviors).

## Notes

- The behaviors design doc (`2026-06-27-creature-pathway-behaviors-design.md`) still names
  `BlinkBehavior` historically; not updated — the authoritative behavior is the code + this spec.
- Re-attach on chunk load (`CreatureLoadListener`) and the factory are name-agnostic via the
  pathway string, so the rename is transparent to persistence.
