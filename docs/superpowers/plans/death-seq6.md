# Death Pathway — Sequence 6 (Spirit Guide / Провідник духів) Implementation Plan

Branch: `feat/death-pathway` (continues the Sequence 9/8/7 branch — same topic, same
branch, per `.claude/rules/git-workflow.md`).
Wiki source: `docs/pathways/death/Death PathwayAbilities  Lord of the Mysteries Wiki  Fandom.md`
(lines 86–127).
Rule: one milestone at a time; before each — state affected files, architectural impact,
why it's Ponytail; after each — summary + in-server test + save durable decisions to
memory. Wait for confirmation between milestones.

## Gameplay role

Sequence 9 was **the body**, Sequence 8 **the escort**, Sequence 7 **the conversation**.
Sequence 6 is **the command**: the medium stops asking spirits for a favour that expires
and starts keeping a standing retinue that fights, plus the pathway's first true
armour-piercing control — a soul torn out of a living body.

Server role: Death's first **army tier** and its first **anti-tank** answer. Nothing else
on the server ignores armour by targeting the spirit; nothing else converts the corpses of
a finished fight into ten walking servants. Progression is healthy — two new actives
(Language of the Dead, Resurrection) carry mastery, the strengthened Spirit Channeling
grows the existing menu, one passive carries identity.

---

## Part 1 — Wiki extraction (specification, no design)

Every item the Sequence 6 section names, kept separate. <sup>⁂</sup> = wiki-invented
name, not canon.

### Description (line 90)

**D1.** > «It is clear that **Spirit Guides** are very powerful against **Zombies** and
> **Wraiths**, and can even effectively target **Evil Spirits**.»

**D2.** > «They can directly control groups of ownerless **Undead** that are of similar
> status to themselves.»

**D3.** > «However, when facing **Higher Sequence Beyonders** of the **Death Pathway**,
> their slaves are very prone to rebelling, and so are they themselves.»

Ambiguity: "and so are they themselves" — the Guide is also prone to rebelling/losing
control, but against whom is never stated.

### New Abilities

**A1. Language of the Dead** — *Active / Combat*
> «By speaking a mystical language, they can urge a target's **Spirit** to leave a body,
> bypassing the physical protection provided by flesh and blood to target the **Spirit
> Body**.»
- «This is an advancement of a **Spirit Medium's** powers. It could go from direct
  communication with the **Spirit Body** to that of commandeering and **Enslavement**.»
- «As **Marionettes** are considered dead, a **Spirit Guide** can interfere and influence
  a **Marionette**; however, at **Sequence 4**, the core of a **Marionette** can have a
  **Worm of Spirit** inside. Thus, at those stages it also depends on multiple factors
  like relative strength of both sides, condition, how much effort is exerted, etc.»

Ambiguity: no duration given; never stated whether "Enslavement" reaches living humans or
only the undead.

**A2. Resurrection** — *Active / Combat*
> «They can turn corpses into living skeletons and **Zombies**; however, this kind of
> **Resurrection** does not allow the reanimated corpse to have any will or vitality.»

Ambiguity: whose corpses, how many, how long they last.

### Strengthened Abilities

**S1. Spirit Channeling** — *Active + Utility / Combat + Non-combat*
> «They can influence and are involved in the **Spirit World**, able to recruit messengers
> on their own, as well as obtain help from certain **Spirit World** creatures.»

All wiki sub-points, verbatim and separate:

- **S1a** «Compared to a **Spirit Medium** who needs to perform **Spirit Channeling** on
  site to achieve different effects, they can control and drive deceased and natural
  **Spirits** to fight in advance.»
- **S1b** «However, making these deceased and natural **Spirits** always follow their
  commands without being discovered and affected by other factors is a significant problem
  for a **Spirit Guide**.»
- **S1c** «In comparison with a **Spirit Medium's** capabilities, **Spirit Guides** are now
  capable of controlling groups of ownerless **Undead** that are of similar status to
  themselves.»
- **S1d** «The upper limit of **Undead** and **Spirits** they can control numbers in the
  thousands.»
- **S1e** «The strength of a **Spirit Guide** depends on the deceased or a natural
  **Spirits** they have found and controlled. This is the same with **Spirit Warlocks**.»
  — «However, the inclination for **Spirit Guides** are deceased **Spirits**, while
  **Spirit Warlocks** are better at controlling natural **Spirits**.»
- **S1f. Spirit swap** <sup>⁂</sup> «They can swap their own **Spirit** with the **Undead**
  under their command to be effectively immune to attacks that target the **Soul Body**.»
  — «To combat the **Spell of Harrumph**, Burman used this technique to set a trap for
  Lumian Lee.»
- **S1g. Shadow-Swallowing Python** «A decaying, skeletal python oozing yellowish-green
  pus. An **Undead** creature specialized in consuming shadows and shadowy creatures.» —
  «Its fangless mouth resembles a vortex, emitting a hurried, piercing sound. It can emit a
  suction force that tugs at the surrounding shadows, drawing them in.» — «Under their
  command, they can make the python target only their enemy instead of indiscriminately
  swallowing their allies or the **Spirit Guide** themself hidden in shadows.»
- **S1h. Death Knight** «A towering figure—a knight adorned in tattered black armor and a
  broadsword.» — «Pale flames flicker in its eye sockets and putrid liquid seeps from its
  armor's crevices, with only sticky flesh clinging to its exposed skin.»
- **S1i. Living Shadow** <sup>⁂</sup> «A tall, thin shadow that can emerge from the shadows
  in translucent form.» — «It can lunge at a target, resembling the possession of
  **Wraiths** and evil spirits, but lacks the speed to complete the process in a mere
  blink.»
- **S1j. Goddess of the Lake** «A powerful type of spirit, one that brings horror.» — «It
  appears as a foggy lake shimmering with light, producing a tranquil beauty. In the
  middle, concentric circles ripple out as beautiful and illusory figures float up.»
- **S1k. Various Undead from the void** «Various **Undead** creatures can appear from the
  void and help them complete different tasks.»
  - **S1k-1** «They can drag the **Spirit Guide** into the shadows, disappearing without a
    trace, even if their master is knocked unconscious.»
  - **S1k-2** «They can make the **Spirit Guide** swiftly soar, as if dragged by an unseen
    force.»
  - **S1k-3** «They can grab onto a target to restrain them.»
  - **S1k-4** «They can control a weak spirit from great distances for communication,
    reaching from **Backlund** to **Tingen City**.»

**S2. Knowledge (Spirit World)** — *Passive / Enhancement / Non-combat*
> «They have considerable information about the **Spirit World**.»

### Completeness check

Wiki lines 86–128 re-read line by line: 1 description paragraph (D1–D3), 2 new abilities
(A1–A2), 2 strengthened (S1 with 11 sub-points + 4 sub-sub-points, S2). **Every ability the
wiki mentions is above. Nothing merged, nothing omitted.**

---

## Part 2 — Minecraft adaptation

Decisions below come from the grilling session and are binding.

| Wiki item | Adaptation | Status |
|---|---|---|
| A1 Language of the Dead | soul ripped out for 5 s | implement |
| A2 Resurrection | raise a corpse from the death registry | implement |
| S1a fight in advance | retinue is permanent, not a timed spirit | implement |
| S1b obedience is a problem | expressed as D3 rebellion | implement |
| S1c ownerless undead | area cast subjugates **vanilla** undead | implement |
| S1d thousands | **capped at 10** — deliberate deviation, thousands would kill the server | deviation |
| S1e strength = who you collected | emergent (zombie vs Death Knight); no separate mechanic | emergent |
| S1f spirit swap | ward: spend a servant, 30 s immunity to soul/mind effects | implement (limited) |
| S1g–S1j four creatures | 4 new MythicMobs in `Mobs/spirit-world.yml` | implement |
| S1k-1 drag into shadows | automatic escape below 25 % HP, costs a servant | implement |
| S1k-2 swift soar | menu button, launch without fall damage | implement |
| S1k-3 grab to restrain | **not implemented** — Sequence 8 already has the spirit grasp | skip |
| S1k-4 long-distance comms | **not implemented** — user decision | skip |
| S2 Knowledge (Spirit World) | passive, modelled on `UndeadKnowledge` | implement |
| D1 strong vs undead/wraiths | damage bonus folded into the Seq-6 `PhysicalEnhancement` | implement |
| D2 control groups | same mechanic as S1c | implement |
| D3 rebellion | servants defect near a higher-Sequence Death Beyonder | implement |

### A1 — Language of the Dead / «Мова мертвих»

Cast on the entity in the crosshair (≤ 20 m). The victim's soul is torn out for **5 s**:

- the **body stays** as a Citizens NPC wearing the victim's skin and equipment;
- the **player** goes to `SPECTATOR`, spawned **behind their own back**, frozen in place
  (teleported back every tick) — they watch their motionless body;
- abilities are sealed (`context.cooldown().lockAbilities`);
- the **body is damageable**, and **the first damage to it returns the soul early**.

Not reproducible exactly: Minecraft has no "spirit body" layer, so armour is not bypassed
by damage maths — it is bypassed by removing the player from their body entirely, which is
strictly closer to «urge a target's Spirit to leave a body» than a true-damage number.

Cost 60 · cooldown 70 s · duration 5 s · range 20 m · works on players and mobs.

### A2 — Resurrection / «Воскресіння»

Cast next to a **corpse** — the trace a recent death leaves at its location (registry, 5
min). Mob corpse → `ZOMBIE`; skeletal/player corpse → `SKELETON`. «No will or vitality» is
expressed as no independent AI: the servant's target is cleared every tick and it only
strikes on command.

Cost 35 per servant · cooldown 12 s · shared retinue cap **10** · servants live until
killed or until the owner logs out.

### S1 — strengthened Spirit Channeling

The Sequence 7 ability grows a second menu tab at Sequence 6 (gate on `sequence`, not a
second ability object):

- **Підкорити нежить** (S1c/D2) — area cast, all vanilla undead within 12 m join the
  retinue up to the cap.
- **Four creatures** (S1g–S1j) — summoned via `context.entity().summonCreature(...)`,
  each joins the same retinue and counts against the same cap of 10.
- **Команди** — `СЛІДУВАТИ` / `АТАКУВАТИ ЦІЛЬ` / `СТЕРЕГТИ МІСЦЕ` / `РОЗПУСТИТИ`.
- **Обмін духом** (S1f) — spends one servant, grants 30 s of immunity to soul/mind effects.
- **Зліт** (S1k-2) — launch up-and-forward, no fall damage.

`S1k-1` needs no button: the session watches the owner's HP and, below 25 %, spends a
servant to make them invisible and drop them into a dark spot nearby (internal cooldown
120 s). This is the one place the wiki's «even if their master is knocked unconscious»
argues for automation over a keypress.

### S2 — Knowledge (Spirit World) / «Знання (Світ Духів)»

Passive, modelled on `UndeadKnowledge`: looking at undead / a spirit / a rampager prints an
actionbar line with its kind, HP and weakness. No cost, non-combat.

### D3 — Rebellion

Within 24 m of a Death Beyonder of a **lower Sequence number** (stronger), each servant
rolls per second; on a defection it leaves the retinue, has its attack damage restored and
targets its former owner. «and so are they themselves» → the Guide takes sanity loss while
in that radius.

---

## Part 3 — Architecture

### Reused, not rebuilt

| Need | Reuse | Source |
|---|---|---|
| corpse registry | **`LingeringSouls`** — already "what a death left at a point", absolute expiry, self-cleaning `near()` | `death/abilities/LingeringSouls.java` |
| recording deaths | **`SpiritPerception`** — already owns an event subscription under its own key | Seq 7 |
| shared registry for several abilities | **`LingeringSouls` pattern** — plain object built in `Death.initializeAbilities()`, passed by constructor | `.claude/rules/lingering-souls.md` |
| session lifecycle | **`SpiritCompanionSession` pattern** — instance registry, own `BukkitTask`, dies when the owner goes offline | `.claude/rules/pathway-abilities.md` §4 |
| menu + deferred resources | **`SpiritChanneling` + `SpiritChannelingMenu`** (`AbilityResourceConsumer`) | Seq 7 |
| summoning creatures | **`context.entity().summonCreature(id, loc)`** | `.claude/rules/summoned-creatures.md` |
| identifying our mobs | scoreboard tag, like `Spirits.isSpirit` | same rule |
| body NPC with player skin | **`MarionettistControl.swapIn` technique** — Citizens `SkinTrait` + `Equipment`, auto-skin disabled **before** `setName` | `fool/abilities/MarionettistControl.java` |
| sealing abilities | **`context.cooldown().lockAbilities`** | `ICooldownContext:17` |
| knowledge passive | **`UndeadKnowledge`** | Seq 9 |
| Seq-6 body | **`PhysicalEnhancement`** under the shared `DEATH_PHYSIQUE` identity | `pathways/common` |
| undead / spirit / rampager test | **`EyeOfDeath.isDeadSoul`** (`Tag.ENTITY_TYPES_UNDEAD`, `Spirits.isSpirit`, `context.rampage()`) | Seq 8 |

**No new manager, service, context, listener or `ServiceContainer` entry.**

### New classes (8)

| Class | Why an existing class cannot be extended | Single responsibility |
|---|---|---|
| `LanguageOfTheDead` | new wiki ability | entry point + cost of the soul rip |
| `SoulRipSession` | stateful (NPC body, spectator lock, 5 s, break-on-damage); `MarionettistControl` is 900+ lines of another pathway — embedding into it violates change discipline | lifecycle of one torn-out soul |
| `Resurrection` | new wiki ability | raise one corpse into a servant |
| `UndeadRetinue` | shared state of **three** intake points (resurrect / subjugate / summon); modelled on `LingeringSouls` | registry `Map<UUID, UndeadRetinueSession>` |
| `UndeadRetinueSession` | `SpiritCompanionSession` deliberately **never fights**, flies without gravity and orbits; a walking combat retinue is different behaviour, not a parameter | retinue: commands, rebellion, shadow escape |
| `SpiritWorldKnowledge` | new wiki ability | passive Spirit World knowledge |
| `SpiritGuideLore` (domain VO) | rule: scaled and shared numbers live in `domain` | every Sequence 6 number |
| `SpiritGuideLoreTest` | — | balance unit test |

### Modified files (10)

`Death.java` · `SpiritChanneling.java` · `SpiritChannelingMenu.java` · `LingeringSouls.java` ·
`SpiritPerception.java` · `MythicPackInstaller.java` (`PACK_FILES`) · `creatures.yml` ·
`CLAUDE.md` · `.claude/rules/lingering-souls.md` · `.claude/rules/new-content-checklist.md`

Optional, pending approval: `MarionettistControl.java` (two guards — see Open questions).

### New resource — `mythic-pack/Mobs/spirit-world.yml`

Named by the **kind** of creature, not by the pathway that uses it first (the wiki's own
term is "Spirit World creatures"), exactly as spirits live in `spirits.yml`. These
creatures belong to no pathway, so `creatures.yml` sets `pathway:` and `sequence:`
explicitly and they take no `MA_<Pathway>_S<seq>` template. Tag: `ma_spirit_world`.

| Mob id | Base | Role |
|---|---|---|
| `death_knight` | `WITHER_SKELETON` | armoured melee tank, broadsword |
| `shadow_swallowing_python` | `CAVE_SPIDER` + particles (no vanilla snake) | suction pull; reveals invisible targets |
| `living_shadow` | `VEX`, black | lunge "possession": blindness + weakness |
| `lake_goddess` | `DROWNED` | horror aura (nausea + slowness) near water |

### Visual effects — no new `VisualEffectsContext` methods

Every effect composes ≥ 2 existing primitives, colour from
`PathwayBranding.liquidOf("Death")`:

| Moment | Composition |
|---|---|
| soul rip | `playRisingSpiral` from the body + `playGraspingHands` on the victim + `playFadingAura` on the body + `ENTITY_WARDEN_HEARTBEAT` |
| area subjugation | `playExplosionRingEffect` + `playWaveEffect` + `BLOCK_SOUL_SAND_BREAK` |
| resurrection | `playPillarEffect` from the corpse + `playDustMark` + `ENTITY_ZOMBIE_VILLAGER_CONVERTED` |
| spirit swap | `playWardingShell` on the caster + `playSoulWisp` on the servant + `PARTICLE_SOUL_ESCAPE` |
| shadow escape | `playVortexEffect` + `playFadingAura` + `ENTITY_ENDERMAN_TELEPORT` |
| soar | `playGroundTrail` + `playHelixEffect` + `ENTITY_PHANTOM_FLAP` |

Only candidate for a new primitive: `playSoulTear` (a silhouette peeling off a body) — add
**only if** the composition reads poorly in-server. Not added up front.

---

## Part 4 — Balance (all constants in `SpiritGuideLore`)

| Parameter | Value | Scaling |
|---|---|---|
| `PHYSIQUE_HP_BASE` | 8 (Seq 9→6: 7 / 6 / 7 / **8**) | — |
| Language of the Dead: cost / cooldown / duration / range | 60 / 70 s / 5 s / 20 m | cooldown ↓, range ↑ |
| Resurrection: cost / cooldown | 35 / 12 s | cooldown ↓ |
| Subjugation: cost / radius / cooldown | 45 / 12 m / 45 s | radius ↑ |
| Retinue cap | **10** (fixed) | — |
| Creature summon | Living Shadow 50 / Python 60 / Death Knight 70 / Lake Goddess 80 | — |
| Spirit swap: cost / ward / cooldown | 40 + 1 servant / 30 s / 90 s | ward ↑ |
| Soar | 15 / 20 s | — |
| Shadow escape | auto at HP ≤ 25 %, 1 servant, internal cooldown 120 s | — |
| Rebellion | radius 24 m, per-second chance grows with the Sequence gap | — |
| Corpse lifetime | 5 min (a soul lasts 30 min; a body spoils faster) | — |

Cost scale stays consistent with the tier ladder: Seq 8 = 12–25, Seq 7 = 20–55,
Seq 6 = 15–80.

---

## Part 5 — Milestones

One at a time. Each builds and is verifiable in-server on its own.

| # | Scope | In-server check |
|---|---|---|
| **M1** | `SpiritGuideLore` + `SpiritGuideLoreTest` | `mvn test -Dtest=SpiritGuideLoreTest` |
| **M2** | `SpiritWorldKnowledge` + Seq-6 `PhysicalEnhancement` + register Sequence 6 in `Death.java` | `/pathway` → Sequence 6 exists, passive prints undead info |
| **M3** | Corpses: extend `LingeringSouls`, subscribe `SpiritPerception` to `EntityDeathEvent` | a killed mob leaves a corpse trace for 5 min |
| **M4** | `UndeadRetinue` + `UndeadRetinueSession` + `Resurrection` (raise, follow, cap 10) | raised zombie follows you, vanishes on logout |
| **M5** | Area subjugation + command tab in `SpiritChannelingMenu` | nearby undead join and obey orders |
| **M6** | `spirit-world.yml` (4 mobs) + `creatures.yml` + `MythicPackInstaller` + summon buttons | `/mm mobs spawn death_knight`, then summon from the menu |
| **M7** | `LanguageOfTheDead` + `SoulRipSession` | soul leaves, NPC body stands, a hit breaks it early |
| **M8** | Marionette interference + spirit-swap ward | ⚠️ needs approval for two guards in `MarionettistControl` |
| **M9** | Rebellion + shadow escape + soar | retinue defects near a Sequence-5 Death Beyonder |
| **M10** | `CLAUDE.md` + rewrite `.claude/rules/lingering-souls.md` + checklist | — |

---

## Resolved decisions (2026-08-04)

1. **M8 may touch `MarionettistControl`** — approved. Two guard lines: one so the
   spirit-swap ward blocks threading, one so Language of the Dead can interfere with a
   marionette. Guards only; no refactor of that class.
2. **`.claude/rules/lingering-souls.md` gets rewritten in M10** — the rule currently forbids
   a third consumer of the registry, and `Resurrection` is the third. The subsystem boundary
   (one pathway) is unchanged, so the rule moves, not the design.
3. **S1d "thousands" → 10** — confirmed as a deliberate, provisional deviation from the wiki.
