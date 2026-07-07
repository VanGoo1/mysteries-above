---
name: designing-pathway-abilities
description: Use when designing or implementing a new Beyonder ability for a Sequence of a Pathway in the mysteries-above plugin (Error, Door, Justiciar, Visionary, WhiteTower) — making it lore-accurate, balanced, and effect-rich
---

# Designing Pathway Abilities

## Overview
Create abilities that are **lore-accurate** (true to the sequence's identity), **distinct** (not a clone of a sibling), **balanced** (scaled by sequence), and **juicy** (sound, particles, two-sided feedback). The Bukkit API and code patterns are easy to copy from existing abilities — the hard part is judgment: picking the right power for the right sequence and grounding it. This skill is that judgment.

**Core principle: never invent the sequence mapping or the lore from memory. Derive both from the code and the reference.** A confident wrong guess about which sequence a power belongs to produces a lore-*inaccurate* ability that looks correct.

## The mapping trap (read this first)
`PathwayManager` stores each pathway's names in a list ordered **index 0 → 9 = Sequence 0 → 9**. The sequence level *is* the list index. Sequence 9 = weakest (first potion), Sequence 0 = strongest.

Real failure this skill exists to prevent: an agent asked for "Sequence 7 Visionary" built a **Hypnotist** ability — but in this plugin Sequence 7 is **Psychiatrist**; Hypnotist is Sequence 6. It guessed the mapping instead of reading it. The result was confidently lore-wrong.

→ **Always look up the target sequence's name in `pathways-reference.md` (and confirm against live `PathwayManager`) before designing anything.**

## Process

1. **Resolve identity.** Open `pathways-reference.md`. Find the exact name + theme for `<pathway>` at `<sequence level>`. If the user said a name ("the Hypnotist"), translate it to its level via the table — don't assume.
2. **Ground the lore.** `Glob` `domain/pathways/<pathway>/abilities/` and read 2–3 sibling abilities. Match their tone and Ukrainian voice. Use the reference's design hook + canonical LotM powers. If unsure of a canonical power, WebSearch the LotM wiki — don't fabricate.
3. **Check for duplication.** Confirm the power isn't already implemented in a sibling (or at a nearby sequence). If it overlaps, pick a different facet of the sequence's identity.
4. **Design spec FIRST — present to the user, do not write code yet.** One short block: name (Ukrainian), sequence, base class, what it does, target/range, cost & cooldown, scaling dimension+strategy, the effect/feedback beats. Get a thumbs-up or adjust. (See "Don't skip the spec" below.)
5. **Implement** per `api-reference.md`: right base class, contexts, `SequenceScaler`, guards with clear failure messages, juice checklist.
6. **Register** in the pathway's `initializeAbilities()` at the correct sequence (merge into the existing `List.of`).
7. **Mirror into creature kits (manual sync).** Creature ability kits in
   `src/main/resources/mythic-pack/` do NOT inherit player abilities — they are separate
   YAML metaskills balanced independently. If the new ability is COMBAT-relevant, add its
   mob version: a `MA_<Pathway>_S<seq>_<Ability>` metaskill in
   `mythic-pack/Skills/<pathway>.yml` and a kit entry in every template
   `MA_<Pathway>_S<N≤seq>` in `Mobs/templates.yml` (offensive → the `kitcast{skills=...}`
   line; reactive/passive → its own trigger line with `Cooldown:`). See
   `.claude/rules/mythic-creatures.md` § «Кіти здібностей (kitcast)». Utility/GUI
   abilities are NOT mirrored.
8. **Verify**: `mvn -q -DskipTests compile`. Report the result honestly.

## Balance heuristics
- Cost/cooldown scale **down** as sequence strengthens: `base / SequenceScaler.calculateMultiplier(seq, ...)`.
- Effect magnitude scales **up**: `scaleValue(base, seq, strategy)`. Signature powers → `DIVINE`/`STRONG`; utility → `WEAK`/`MODERATE`; drawbacks → `PENALTY_*`.
- Sanity-check cost/cooldown against sibling abilities at the same sequence, not against absolutes.
- Powerful control on other Beyonders → opt into `getSequenceCheckTarget` so higher sequences can resist.

## Don't skip the spec
Jumping straight to a Java file is the most common failure: it bakes in a wrong sequence, a duplicate, or off-theme flavor that's expensive to unwind. The spec is one paragraph and catches all three. Present it and wait — unless the user explicitly said "just build it."

## Red flags — STOP
- "Sequence 7 is the Hypnotist" (or any sequence→name from memory) → **read the table.**
- "I'll register it later" → unusable until registered; do it.
- "Particles/sound are optional" → juice is the point; add cast + resolve effects and two-sided feedback.
- "This is basically like \<existing ability\>" → that's a duplicate; pick another facet.
- "I'll just write the class" → write the spec first and confirm.
- "Creature kits will pick this up automatically" → they won't; mirror combat abilities
  into the mythic-pack kit or consciously skip (utility/GUI).

## Files
- `pathways-reference.md` — authoritative sequence↔name↔theme tables for all 5 pathways.
- `api-reference.md` — base classes, contexts, scaling, registration, juice checklist, build command.
