# Death Pathway — Sequence 5 (Gatekeeper / Воротар) Implementation Plan

Branch: `feat/death-pathway` (continues the Sequence 9/8/7/6 branch — same topic, same
branch, per `.claude/rules/git-workflow.md`).
Wiki source: `docs/pathways/death/Death PathwayAbilities  Lord of the Mysteries Wiki  Fandom.md`
(lines 129–166).
Rule: one milestone at a time; before each — state affected files, architectural impact,
why it's Ponytail; after each — summary + in-server test + save durable decisions to
memory. Wait for confirmation between milestones.

## Gameplay role

Sequence 9 was **the body**, 8 **the escort**, 7 **the conversation**, 6 **the command**.
Sequence 5 is **the threshold**: the Guide who commanded a retinue becomes the warden of
the gate itself. Two things are new in kind, not degree —

1. **A door that kills**, not a spell that damages: an execute the server has nowhere else.
2. **A container instead of a crowd**: the Gatekeeper stops leading servants and starts
   *swallowing* one, trading a walking body for a power carried inside — at the cost of
   their own flesh (erosion).

Progression is healthy: three new actives (Крок Воротаря, Двері, Внутрішній Загробний
Світ) carry mastery, the strengthened Spirit Channeling grows the existing cap, one shared
passive carries identity.

---

## Part 1 — Wiki extraction (specification, no design)

Every item the Sequence 5 section names, kept separate. <sup>⁂</sup> = wiki-invented name,
not canon. 18 entries.

### Traits (lines 131–134)

**T1. Physical Enhancement** — *Trait / Enhancement / Combat*
> «They become stronger and quicker, one step can allow them to suddenly close the
> distance against opponents and thrust at lightning speed, bringing vigorous wind
> currents.»

**T2. Spiritual Perception** — *Trait / Passive / Non-combat*
> «They can acutely sense if an unknown creature is crossing the **Spirit World** and
> approaching a place near them.»

Ambiguity: no range given; never stated whether it covers creatures already in the real
world or only those in transit.

### New Abilities (lines 136–158)

**A1. Door to the Underworld** *(base sense)* — *Active / Utility*
> «They can sense the entrance to the **Underworld**, allowing them to control the
> **Undead** inside, as though they watched over the gates that separated the dead from
> the living.»

Ambiguity: "control the Undead inside" — commanding undead *within* the Underworld is
never shown mechanically anywhere in the section.

**A2. The door's mark on the palm** — *Active / Utility (preparation)*
> «They can use the powers of the **Door to the Underworld** to some extent. They can
> imprint the door's mark onto the palm of their hand and summon it at critical moments.»

**A3. Dragging targets behind the gate** — *Active / Combat*
> «It can be used to drag targets behind the gate which led to the **Underworld**, where
> all living creatures immediately die.»

**A4. Suction force of the Door** — *Active / Combat*
> «The **Door** has a terrifying suction force. Most of it would be directed towards a
> target, though the remnant forces would continue to pull on the surroundings.»
> «The main target can't help but stumble and be drawn in, while those nearby would find
> it difficult to even take a step.»

**A5. Clearing airborne poison** — *Active / Utility / Non-combat*
> «The suction force generated from the **Door** can also be used to clear the environment
> of airborne poison.»

**A6. Absorbing the Fog of War (Red Priest Pathway)** — *Active / Combat / Counter*
> «**Gatekeepers** are able to use the **Door** to absorb the **Fog of War** of the **Red
> Priest Pathway**.»

Ambiguity: Red Priest is a scaffold pathway in this plugin — no abilities, no Fog of War.

**A7. Grasping limbs from the Door** *(unnamed on the wiki)* — *Active / Combat*
> «Aside from the suction force, bloody, skinless arms, slimy tentacles with teeth, and
> bluish-black vines with baby faces emerge from the **Door to the Underworld** to grab
> targets and drag them within.»

Three kinds of limb are described; the wiki gives them no collective name.

**A8. Letting the Underworld descend upon reality** — *Active / Combat / zone*
> «It can be used let the **Underworld** descend upon reality.»

Ambiguity: the thinnest line in the section — no radius, no effect, no duration.

**A9. Internal Underworld** — *Passive container + Active / Utility*
> «Using their bodies as a cage, they can house numerous **Souls**, deceased, and natural
> **Spirits**, allowing them to bring an **Undead** army wherever they go in a non
> eye-catching manner.»
> «Normally, the entrance to the **Internal Underworld** is at the center of the
> **Gatekeeper's** brow.»
> «With this, they gain various unique abilities and possess powerful helpers, for
> example:»

**A10. Death Envoy** *(Ludwell's unique ability)* — *Active / Combat*
> «It turns his arms illusory, letting it instantly cover a significant distance and
> either remotely extract a target's **Spirit Body** or squash them.»
> «As the pale palm approaches, the target would seem to be possessed by a **Wraith** or
> **Evil Spirit**. The soft sobbing sounds of the **Death Envoy** causes the target's body
> to go numb, as though their blood had frozen.»
> «This makes it difficult to produce an effective response, the target can only watch in
> despair as death approaches, feeling their vitality deplete at an increasing rate.»
> «It also allows **Ludwell** to produce a silent screech, striking and temporarily
> freezing attacks midair.»

**A11. Pus of Man**<sup>⁂</sup> *(the spirit Ulika fused with)* — *Passive / Survival*
> «A strange deceased spirit that **Ulika** fused with, it will quickly dissipate if it
> doesn't use a human body as a "house".»
> «Even after undergoing **Purification** from the **Golden Sundial**, **Ulika** survived
> by transforming into a miniature form with raven-black skin and flowing, sticky pus.»

**A12. Erosion by housed spirits** *(unnamed)* — *Passive / Drawback*
> «Powerful **Spirits** and **Undead** creatures may erode their body from within.»
> «The **Death Envoy** within **Ludwell** turned him into a half-human, half-dead
> existence, allowing him to enter the **Underworld** without perishing.»

Ambiguity: the same line is both a penalty and a boon (half-dead survives the Underworld).

**A13. Prime target of possession** *(unnamed)* — *Passive / Drawback*
> «As this can be considered a miniature **Underworld**, **Gatekeepers** are a prime
> target of possession for **Evil Spirits** as they provide an adequate environment for
> their existence.»

### Strengthened Abilities (lines 160–166)

**S1. Spirit Channeling** — *Active / strengthening of an existing ability*
> «The maximum number of **Undead** and **Spirits** that a **Gatekeeper** can control has
> slightly increased, but it doesn't change much.»

**S2. Pale Girl**<sup>⁂</sup> — *Spirit / Passive protection*
> «A nearly illusory girl with pale skin, bluish-green eyes, and jet black lips.»
> «She has ghastly pale and translucent limbs that could bring a chill to the **Spirit
> Body**, which can help the **Gatekeeper** resist the influence of a **Nightmare**.»

**S3. Illusory ferocious sea creatures** *(unnamed)* — *Active / Combat / swarm*
> «Aside from controlling the **Undead** manning the **Black Tulip**, **Ludwell** summoned
> translucent, illusory ferocious creatures from the sea, charging forward like an
> unending tidal wave.»

### Completeness check

Lines 129–166 read twice, line by line. All 38 lines of the section map onto the 18
entries above: 2 traits (T1–T2), 8 Door entries (A1–A8), 5 Internal Underworld entries
(A9–A13), 3 strengthened-Channeling entries (S1–S3). Nothing merged, nothing omitted.

---

## Part 2 — Minecraft adaptation

### T1 — Physical Enhancement / «Тіло воротаря» + «Крок Воротаря»

Two halves, per the approved design. The **body** is the next rung of the shared
`PhysicalEnhancement` (identity `death_physique`, HP base 10, keeps `SPEED`). The **step**
is a separate active: a dash 8–12 blocks along the look vector via `setVelocity`, a wind
wall drawn along the path, and a small impact hit on whatever the Gatekeeper lands next
to. Cost 20, cooldown 8 s (shrinks WEAK, floor 4).

Why separate: "one step closes the distance" is a discrete action a player triggers, not a
stat. Folding it into the passive would make it uncastable.

### T2 — Spiritual Perception / «Духовне сприйняття»

No new class. The existing `SpiritPerception` (Sequence 7) gains a Sequence ≤ 5 branch: on
each per-player gated tick it reports Spirit World creatures
(`SpiritWorldCreatures.isSpiritWorldCreature`) and undead (`Tag.ENTITY_TYPES_UNDEAD` —
never `getCategory()`, which throws on 1.21+) that entered a 40-block radius, throttled so
it cannot spam. Sequence 7/6 behaviour must be untouched.

### A1–A8 — Door to the Underworld / «Двері в Загробний Світ»

One `ActiveAbility` with four **modes**, switched by **shift + scroll** (not a menu).
Mechanism: `context.events().subscribeToTemporaryEvent(ownKey, PlayerItemHeldEvent.class,
…, Integer.MAX_VALUE)` — the pattern already used by `SpiritualIntuition` (door pathway).
The handler requires `player.isSneaking()`, calls `event.setCancelled(true)` so the hotbar
slot does not actually change (`PlayerItemHeldEvent` is `Cancellable` — confirmed against
Paper 1.21.11 javadoc), rotates the mode by `sign(newSlot − previousSlot)` and writes the
new mode to the action bar. Mode lives in an **instance** `Map<UUID, Mode>`.

A1 (sensing the gate) and A2 (the mark on the palm) have no mechanics of their own on the
wiki — they are exactly what "a mode is already selected, the cast opens the door" models,
so they are represented by the mode-selection step itself rather than by extra casts.

| Mode | Wiki | Effect | Cost / CD |
|---|---|---|---|
| `DRAG` | A3, A4 | look-target within 20 m is pulled toward the gate; 8 true damage + `WITHER`; **execute** (`setHealth(0)`) if the target is below 25 % HP. Everyone else within 8 m of the gate: `SLOWNESS III` 3 s + a pull vector | 70 / 60 s (floor 30) |
| `PURGE` | A5, A6 | removes `AreaEffectCloud`s within 10 m, clears `POISON`/`WITHER`/`NAUSEA` from allies, extinguishes fire | 25 / 20 s |
| `BIND` | A7 | enemies within 6 m: `SLOWNESS V` + negative `JUMP_BOOST` (no jump) for 4 s, drawn with `playGraspingHands` | 40 / 30 s |
| `DESCEND` | A8 | `UnderworldDescentSession`: 12 s, radius 12 m — darkness, `WITHER I` per second to enemies, +20 % damage to the owner's undead inside, owner heals 1 HP/s | 90 / 180 s (floor 90) |

**A6 cannot be reproduced exactly**: the Red Priest pathway is a scaffold and no Fog of War
exists in the plugin. `PURGE` already covers the class "harmful cloud hanging in the air",
so when Red Priest is implemented its cloud falls under the same code with no edit.

**A8 is interpretation, not canon**: the wiki gives one sentence and no numbers. 12 s /
12 m is a deliberate reading and is documented as such.

### A9–A13 — Internal Underworld / «Внутрішній Загробний Світ»

**Capacity: exactly one occupant** at Sequence 5, scaled by Sequence later (the constant is
a method from day one so the later tier needs no refactor).

- **Filling it**: shift + right-click one of your own retinue servants (the
  `PlayerInteractEntityEvent` route `SpiritPact` already uses). The servant despawns and
  becomes the occupant — the retinue shrinks by one.
- **Using it**: one cast, whose effect switches on the occupant (`enum Occupant`).
- **Emptying it**: sneak-cast releases the occupant; the body recovers.
- **A12 (erosion)** is the price and is mandatory: while occupied, −2…−8 max HP scaled by
  the occupant's strength plus a slow `SanityLoss` drain. The boon from the same wiki line
  (half-dead) is expressed as `WITHER` immunity.
- **A13 (possession)** is *not* a separate mechanic: no evil-spirit entities exist in the
  plugin, and the risk is already carried by erosion. It stays in the ability description.

Occupants and their powers:

| Occupant | Source | Power | Cost / CD |
|---|---|---|---|
| Death Knight | `spirit-world.yml` (existing) | phantom plate + blade: armour + melee damage buff, 20 s | 40 / 30 s |
| Shadow-Swallowing Python | existing | devours shadows: strips invisibility/`DARKNESS` in 10 m and pulls victims in | 45 / 30 s |
| Living Shadow | existing | shadow-step: short blink into the target's shadow | 35 / 20 s |
| Goddess of the Lake | existing | misty lake: 8 m zone, enemies `SLOWNESS`+`BLINDNESS`, allies regenerate | 50 / 45 s |
| Wandering Spirit | `spirits.yml` (existing) | scout: reveals living entities in 60 m for 15 s | 30 / 25 s |
| Resurrected servant | `Resurrection` | crude: a burst of true damage around the caster | 30 / 20 s |
| **Death Envoy** (A10) | **new** | illusory arm to 25 m: extracts the spirit (armour-ignoring true damage + `SLOWNESS`+`BLINDNESS`) or squashes (downward knockback + damage) | 60 / 25 s |
| **Pus of Man** (A11) | **new** | *passive*: once per 5 min a lethal blow leaves 1 HP + `INVISIBILITY` 5 s + `WEAKNESS` | — |
| **Pale Girl** (S2) | **new** | *passive*: immune to `NAUSEA`/`BLINDNESS`/`DARKNESS`, 50 % resistance to soul effects (Language of the Dead, Fool's threads) | — |
| **Illusory sea beasts** (S3) | **new** | 4 translucent beasts charge the target in a wave and fade after 10 s | 55 / 40 s |

### S1 — strengthened Spirit Channeling

Retinue cap 10 → **12** at Sequence 5 ("slightly increased, but it doesn't change much").
The wiki's "thousands" stays rejected for the same reason as at Sequence 6 — the server
cannot carry it. `SpiritGuideLore.RETINUE_CAP` becomes a Sequence-aware method and the cap
is passed into `enroll`/`isFull` by the calling ability, which already knows its Sequence.

### S2, S3, A10, A11 — the four new spirits

They go into the **existing** `mythic-pack/Mobs/spirit-world.yml`, not a new file: they are
the same genus as the four already there (Spirit World spirits, tag `ma_spirit_world`, plus
their own species tag), so `.claude/rules/summoned-creatures.md` puts them in that file and
`MythicPackInstaller.PACK_FILES` needs no change. They spawn naturally via `creatures.yml`
(`pathway: spirit-world`, which keeps them out of Convergence bias and church/order HUNT
quests), are recruited by the existing `SpiritPact`, and only then can be absorbed.

---

## Part 3 — Architecture

### Reused, not rebuilt

- `ActiveAbility` + the `execute` pipeline; `AbilityResult.deferred()` for the
  mode-switch/no-target branches (never consume cooldown on a mode switch).
- `IEventContext.subscribeToTemporaryEvent` under an **own** subscription key
  (`SpiritualIntuition` pattern) for both scroll and shift-right-click.
- `UndeadRetinue` (`isServant`, `sacrificeOne`, `enroll`) — absorption removes a servant
  through the registry, never by killing the mob behind its back.
- `SpiritPact` + `spirit-world.yml` + `creatures.yml` — the discovery/binding flow for the
  four new spirits, unchanged.
- `SpiritPerception` — extended, not duplicated.
- `PhysicalEnhancement` with identity `death_physique` — the stronger version replaces the
  weaker one, never stacks.
- `SequenceScaler`, `PathwayBranding.liquidOf("Death")`, `Spirits`,
  `SpiritWorldCreatures`, `SoulWard`.
- Visual primitives: `playGraspingHands` (A7 and the Envoy's arm), `playVortexEffect`
  (suction), `playSurgingWave` (dash wind, sea-beast wave), `playDustMark` (the brow mark),
  `playSphereEffect` + `playCircleEffect` (descent zone), `playTravelingBeam`,
  `playFadingAura`.

### New classes (7)

| Class | Why nothing existing can be extended | Single responsibility |
|---|---|---|
| `domain.valueobjects.GatekeeperLore` | `SpiritGuideLore` holds Sequence-6 numbers; mixing tiers is forbidden by the tier-per-VO rule | Sequence-5 balance constants + scaling |
| `pathways.death.abilities.GatekeeperStep` | distinct `getIdentity()`, distinct cast | the dash |
| `…abilities.DoorToTheUnderworld` | distinct ability with its own modes | the gate and its four modes |
| `…abilities.UnderworldDescentSession` | existing Death sessions hold different state (companion, retinue, soul-rip) | the descent zone's `start→tick→cancel` |
| `…abilities.InternalUnderworld` | distinct ability; the retinue registry is a registry, not an ability | the cage: absorb / use / release |
| `InternalUnderworld.Occupant` (nested enum) | — | who is inside and what they grant |
| `test GatekeeperLoreTest` | domain-purity rule requires a unit test per balance VO | pins the numbers |

### Modified files (11)

`Death.java` · `SpiritPerception.java` · `UndeadRetinue.java` (cap parameter) ·
`Resurrection.java`, `SpiritChanneling.java`, `SpiritPact.java` (cap argument only) ·
`IVisualEffectsContext.java` + `VisualEffectsContext.java` (one new effect) ·
`mythic-pack/Mobs/spirit-world.yml` · `creatures.yml` · `.claude/rules/lingering-souls.md`
(+ `new-content-checklist.md`, `CLAUDE.md` in the docs milestone).

### Visual effects — exactly one new method

Everything else composes from existing primitives. What has no honest substitute is **the
gate itself**: a vertical rectangular portal with a dark interior and suction rings.
`playStandingCurtain` is a rippling curtain, `playCubeEffect` is a cube — neither reads as a
door, and `.claude/rules/visual-effects-reuse.md` forbids forcing an inaccurate effect.

```java
void playUnderworldGate(Location center, Vector facing, double width, double height,
                        Color color, int durationTicks);
```

Reusable and parameterised (size, colour, duration) so a future Sequence-4 gate needs no
second method.

### Sounds

Gate opening — `BLOCK_RESPAWN_ANCHOR_DEPLETE` (pitch 0.4) + `ENTITY_WITHER_SPAWN` (0.3,
0.5); suction — looping `BLOCK_PORTAL_AMBIENT`; execute — `ENTITY_WARDEN_SONIC_BOOM`;
absorption — `ENTITY_ALLAY_DEATH` + `BLOCK_SCULK_CATALYST_BLOOM`; the Envoy's sobbing —
`ENTITY_ALLAY_HURT` (pitch 0.5); dash — `ENTITY_BREEZE_SHOOT`; descent — `AMBIENT_CAVE` +
`ENTITY_WITHER_AMBIENT`.

---

## Part 4 — Balance (all constants in `GatekeeperLore`)

| Ability | Cost | Cooldown (Seq 5 → floor) | Range / duration |
|---|---|---|---|
| Крок Воротаря | 20 | 8 s → 4 | 8–12 m dash |
| Двері `DRAG` | 70 | 60 s → 30 | 20 m; execute < 25 % HP; 8 m suction |
| Двері `PURGE` | 25 | 20 s → 10 | 10 m |
| Двері `BIND` | 40 | 30 s → 15 | 6 m; 4 s |
| Двері `DESCEND` | 90 | 180 s → 90 | 12 m; 12 s |
| ВЗС absorption | 0 | 10 s | shift-right-click, melee range |
| ВЗС cast | 30–60 by occupant | 20–45 s by occupant | by occupant |
| Erosion | — | — | −2…−8 max HP while occupied |
| Retinue cap | — | — | 12 at Seq 5 (10 at Seq 6) |

Scaling: costs are flat; cooldowns shrink `WEAK` toward a floor; ranges and durations grow
`WEAK`/`MODERATE` — the same shape as `SpiritGuideLore`, so nothing new to learn.

---

## Part 5 — Milestones

| # | Deliverable | In-server check |
|---|---|---|
| **M1** | `GatekeeperLore` + `GatekeeperLoreTest`; «Тіло воротаря» wired into `Death` | `mvn test -Dtest=GatekeeperLoreTest`; `/pathway` → Seq 5 grants the body |
| **M2** | `GatekeeperStep` (dash) | dash fires, wind wall renders, cooldown holds |
| **M3** | Sequence-5 branch in `SpiritPerception` | warning fires on approach; Seq 7/6 unchanged |
| **M4** | `playUnderworldGate` + `DoorToTheUnderworld` with shift+scroll and `DRAG` | scroll changes mode without changing the hotbar slot; execute below 25 % HP |
| **M5** | Modes `PURGE` + `BIND` | potion cloud vanishes; target cannot move or jump |
| **M6** | Mode `DESCEND` + `UnderworldDescentSession` | zone expires on its own; `cleanUp()` does not touch other players' zones |
| **M7** | `InternalUnderworld`: absorb, one occupant, erosion, cast — with the **six already-available** occupants | absorbing shrinks the retinue; release restores max HP |
| **M8** | Retinue cap 10 → 12 by Sequence | Sequence 6 still stops at 10 |
| **M9** | Four new spirits in `spirit-world.yml` + `creatures.yml` + their occupant powers | `/mm mobs spawn pale_girl`; pact → absorb → power |
| **M10** | Docs: this plan's decisions folded into `.claude/rules/lingering-souls.md`, `new-content-checklist.md`, `CLAUDE.md` | — |

---

## Resolved decisions (2026-08-04)

1. **Death Envoy / Pus of Man are not a fixed kit** — they are *inhabitants* of the
   Internal Underworld. The Gatekeeper's power is whoever they are carrying.
2. **Absorption comes from the retinue**, not from a separate hunt: shift-right-click your
   own servant. The Internal Underworld is an upgrade of a retinue slot.
3. **One occupant at Sequence 5**, capacity scaled by Sequence later — so the constant is a
   method from the start.
4. **One ability, effect switches on the occupant** — not one ability per spirit, and not
   dynamically injected off-pathway abilities.
5. **Erosion is mandatory** and scales with the occupant's strength. Power is not free.
6. **The Door does not one-shot from full HP**: execute below a HP threshold instead of the
   wiki's literal instant death.
7. **The Door has modes, switched by shift+scroll**, not by a menu — including `DESCEND`.
8. **Scroll belongs to the Door only.** The Internal Underworld uses cast + shift-right-click.
   The mode is announced in the action bar.
9. **The dash is a separate active**, not part of the passive.
10. **Pale Girl and the sea beasts are occupants**, not new Spirit Channeling forms.
11. **No potion recipes and no Gatekeeper enemy mob** in this scope — recipes remain a
    separate topic for the whole pathway.
12. **The four new spirits go into the existing `spirit-world.yml`** — same genus as the
    four already there, so no new pack file and no `MythicPackInstaller` change.
