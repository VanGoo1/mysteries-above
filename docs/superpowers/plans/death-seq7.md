# Death Pathway — Sequence 7 (Spirit Medium / Медіум Духів) Implementation Plan

Branch: `feat/death-pathway` (continues the Sequence 9/8 branch — same topic, same
branch, per `.claude/rules/git-workflow.md`).
Wiki source: `docs/pathways/death/Death PathwayAbilities  Lord of the Mysteries Wiki  Fandom.md`
(lines 40–84).
Rule: one milestone at a time; before each — state affected files, architectural impact,
why it's Ponytail; after each — summary + in-server test + save durable decisions to
memory. Wait for confirmation between milestones.

## Gameplay role

Sequence 9 was **the body** (cold flesh, undead ignore you, you see what others can't).
Sequence 8 was **the escort** (spirits walk with you and hold what you strike).
Sequence 7 is **the conversation**: the medium stops borrowing a spirit's presence and
starts borrowing its *power* — one spirit at a time, each a different phenomenon — and
starts taking *knowledge* out of the dead.

Server role: the pathway's first **information tier** and its first real **zone control**.
Frost Shadow is the Death answer to a held position; Earth Spirit is single-target
lockdown; Illusory Eye is scouting; Voice of the Dead is the only mechanic on the server
that moves a brewing recipe from one player to another. Progression is healthy: two new
actives (Spirit Channeling, Voice of the Dead) carry mastery, three passives carry
identity.

---

## Part 1 — Wiki extraction (specification, no design)

Every item the Sequence 7 section names, kept separate. <sup>⁂</sup> = wiki-invented
name, not canon.

### Traits

**T1. Physical Enhancements** — *Enhancement / Passive / Non-combat*
> «Compared to the previous two **Sequences**, the **Spirit Medium** does not gain
> significant improvements in physique or agility.»

Ambiguity: "not significant" ≠ "none".

**T2. Spirituality** — *Enhancement / Passive / Non-combat*
> «A **Spirit Medium's Spirituality** has been greatly enhanced.»

**T2a. Spiritual Perception** — *Passive / Non-combat*
> «Their **Spiritual Perception** becomes extremely strong.» — «They can sense the hidden
> **Spirits** around them even with their eyes closed.»

**T2b. Spirit Vision** — *Passive, strengthening of an existing ability / Non-combat*
> «Their **Spirit Vision** becomes more powerful.»

Ambiguity: the wiki never says *in what way*.

**T3. Spirit Affinity** <sup>⁂</sup> — *Passive / Non-combat*
> «To the deceased and natural **Spirits** around them, the **Spirit Medium** is akin to
> that of a fellow **Spirit** and thus not dangerous to them.»
> «If the **Spirit Medium** is friendly enough, those deceased and natural **Spirits**
> will even take the initiative to pass on information to them.»

Strictly more than Sequence 9's Gloomy Presence (non-aggression): spirits treat the
medium as one of their own and volunteer information.

### New Abilities

**A1. Spirit Channeling** — *Active / Ritual-adjacent / Combat + Non-combat*
> «Their core ability is **Spirit Channeling**, allowing them to directly communicate
> with the natural **Spirits** and loitering dead souls in the real world.»

All wiki sub-points, verbatim:
- «With the advancement of each **Sequence**, the quantity and quality of natural
  **Spirits**, **Undead** and **Spirit World** creatures they can control and order
  increases exponentially.»
- «They can use different **Spirits** to actualize different kinds of effects, creating
  various kinds of supernatural phenomena in a rather multifaceted way.»
- «Theoretically, a **Spirit Channeling** done by a **Spirit Medium** can involve only
  *1 **Spirit***.»
- «Most **Spirits** need corresponding materials to safely communicate with. These
  ingredients include but are not limited to **Full Moon Essence Oil**, **Corpse Incense
  Medication**, as well as a **Spirit Medium's** own blood.»
- «Since they can directly communicate with natural **Spirits** and ghosts, they have
  informants everywhere that can alert them of any danger or information.»
- «It can also be used on living creatures to directly communicate with their **Spirits**
  to obtain real information, but this method holds a lot of danger for them.» — two
  methods: (1) «use their powerful **Spirituality** to triumph over the will of the other
  person, engaging in a barbaric method of communication»; (2) «use medication to make the
  other party relax… **Amantha Essence** and **Eye of the Spirit** medication».
- «They have the means to protect themselves under the risk of being infected by a living
  **Beyonder's** potent **Spirituality**, which can't be learned or used by **Beyonders**
  of other Pathways.»
- «A **Spirit Medium** is able to directly **Channel** recently deceased **Spirits**, such
  as fallen enemies, in order to obtain even more information from them.»
- «A **Spirit Medium** could communicate with surrounding **Spirits** or through specific
  directions and descriptions without needing **Ritualistic Magic**.» — «However, unlike a
  **Dancer**, they cannot rely on a **Summoning Dance** and a location's uniqueness to
  attract more distant, hidden **Spirits**.»

**A1a. Illusory Eye** <sup>⁂</sup> — *named channelled spirit / Utility / Non-combat*
> «A **Spirit World Creature** with a nearly transparent eye and ghastly-pale eye whites,
> looking down from above without blinking.» — «It allows them to scout areas from afar, a
> **Spirit Medium's** version of a telescope.»

**A1b. Frost Shadow** <sup>⁂</sup> — *named channelled spirit / Combat*
> «A **Spirit** that can create a considerable **Frozen** domain within a *10 to 20 meter
> range*, and can materialize a layer of armor-like **Ice** and a colossal, sharp, and
> crystalline **Frost** scythe.» — «This is among the most powerful **Spirits** they can
> **Channel** without the use of materials.»

**A1c. Earth Spirit** <sup>⁂</sup> — *named channelled spirit / Combat, control*
> «A **Natural Spirit** with a pair of weathered, stone-like hands, it can be used to
> soften the ground like a swamp and drag a target underground, like a **Druid's
> Underground Slink**.» — «This **Spirit** needs their blood to be **Channeled**.»

**A2. Zombie Disguise** <sup>⁂</sup> — *Toggle / Non-combat, survival*
> «They can also **Disguise** themselves as a **Zombie** in order to better endure the
> erosion of **Decay**, **Cold**, **Death** and other auras.»

Ambiguity: the wiki does not say whether others *see* a zombie or it is an internal
transformation only.

### Strengthened Abilities

**S1. Knowledge (Mysticism)** — *Passive / Non-combat*
> «They gain knowledge of various kinds of **Ritualistic Magic** related to **Spirits**.» —
> «They are armed with knowledge on how to deal with the dangers that come with
> **Channeling** a living creature's **Spirit**, as well as the effective communication
> methods in such a state.»

**S2. Eye of Death** — *Active, strengthening of an existing ability / Combat*
> «This **Beyonder** power becomes more powerful.»

Ambiguity: no detail on what is strengthened.

### Completeness check

Lines 40–84 were re-read line by line after extraction. Every wiki item of this Sequence
is present: 3 traits (T1, T2 with T2a/T2b, T3), 1 new ability with three named spirits
(A1 + A1a/A1b/A1c), 1 new ability (A2), 2 strengthened abilities (S1, S2). Nothing merged,
nothing dropped, no lore simplified.

---

## Part 2 — Design decisions taken with the user (grill-me)

| # | Question | Decision |
|---|---|---|
| 1 | Channelling structure | **One ability + selection menu.** «involve only 1 Spirit» → one active channel per caster; re-cast replaces |
| 2 | Materials | **Spirituality + blood (HP) where the wiki says blood.** No new ingredient items |
| 3 | Seq 8 ↔ Seq 7 | **Coexist.** Spirit Communication (retinue) is untouched; Spirit Channeling is a separate ability |
| 4 | Zombie Disguise | **Immunities + sunlight weakness + others see a zombie** (reuses `EntityDisguiseService`) |
| 5 | Information branch | **Its own active ability**, two branches by target type (dead / living) |
| 6 | Dead branch targeting | **A soul lingers at the death spot for 30 min**, particle-rendered, visible only to Death ≤ Seq 7; consumed on interaction |
| 7 | Recipe-farming abuse | **Transfer, not copy: the victim loses the recipe.** Self-limiting — a friend can donate each recipe once, and it costs them |
| 8 | Living branch | **Target dossier**, and a sanity backlash if the target is a stronger Sequence |
| 9 | T1 physique | **Small upgrade** (deliberately small: HP base 7 vs 6 at Seq 8) |
| 10 | S1 Knowledge (Mysticism) | **Add `new RitualMagic()`** |
| 11 | S2 Eye of Death | **Numbers only, zero code** (`GravediggerLore.growth()` already scales it) |
| 12 | T3 Spirit Affinity | **Merged into the soul-perception passive** |

---

## Part 3 — Minecraft adaptation

| # | Wiki item | Adaptation | Code |
|---|---|---|---|
| T1 | Physical Enhancements | `PhysicalEnhancement`, shared identity `death_physique`, HP base **7** | 1 line in `Death.java` |
| T2 | Spirituality | Engine already grants a larger pool per Sequence (`SpiritualityCalculator`) | **none** |
| T2a | Spiritual Perception | Part of `SpiritPerception`: spirits glow through walls, free, always on | M2 |
| T2b | Spirit Vision (stronger) | `SpiritVision` already scales with Sequence; the qualitative jump is delivered by the new passive | **none** |
| T3 | Spirit Affinity | Same passive: actionbar whisper when an enemy closes in from behind or targets the medium | M2 |
| A1 | Spirit Channeling | One active + GUI, one live channel per caster | M5–M7 |
| A1a | Illusory Eye | Eye hangs high above the caster; everything alive within 48 blocks is outlined (`context.glowing()`) for 15 s, **for the caster only** — the medium's telescope | M5 |
| A1b | Frost Shadow | Session: frozen domain **10→20 m** (the wiki's own number), inside it Slowness + `setFreezeTicks` + cold tick damage; the caster gets ice armor (Resistance + absorption) and a frost scythe (bonus melee damage) | M6 |
| A1c | Earth Spirit | Ground softens: target is pulled 1–2 blocks into the earth, immobilised 5 s; swamp ring slows nearby enemies. Costs **blood** (4 HP) | M7 |
| A2 | Zombie Disguise | `EntityDisguiseService` → others see a zombie; immune to Wither/Poison/Hunger/freezing; undead ignore even after provocation; **burns in sunlight**; periodic spirituality cost | M8 |
| S1 | Knowledge (Mysticism) | `new RitualMagic()` | 1 line |
| S2 | Eye of Death (stronger) | `GravediggerLore.growth()` already raises range/multiplier/cooldown at Seq 7 | **none** |
| — | Channel recently deceased | Voice of the Dead, dead branch: soul lingers 30 min → **one recipe moves** from the fallen to the medium | M3 |
| — | Channel the living | Same ability, living branch: dossier; stronger Sequence resists → sanity backlash | M4 |

### What cannot be reproduced exactly, and why

- **«Informants everywhere… from Backlund to Tingen City»** — a world-spanning informant
  network has no gameplay surface without a city map. Closest faithful form: a local
  whisper about danger nearby (T3).
- **Materials (Full Moon Essence Oil, Corpse Incense Medication)** — separate ingredient
  items would drag loot tables, resource-pack textures and a gathering path behind them;
  by decision they are out of scope. Blood stays, because the lore ties it specifically to
  the Earth Spirit.
- **«Means to protect themselves from a living Beyonder's Spirituality, exclusive to this
  Pathway»** — in the lore this is the *absence* of a penalty, not an action. Modelled as
  the fact that the medium can channel a living target at all, and only pays sanity when
  the target resists.
- **«Cannot rely on a Summoning Dance unlike a Dancer»** — a negation of another pathway's
  mechanic; no code, recorded as a limitation in the ability description.

---

## Part 4 — Architecture

### Reused, not rebuilt

| Need | Existing solution |
|---|---|
| Channelling menu | `ContractMenu` as the template (triumph-gui, deferred → `AbilityResourceConsumer`) |
| Frost domain lifecycle | `JurisdictionSession` / `SpiritCompanionSession` — instance registry, own `BukkitTask`, Bukkit inside `tick()` |
| Zombie disguise | `infrastructure.disguise.EntityDisguiseService.disguiseAsMob(player, EntityType.ZOMBIE)` (packetevents already in `pom.xml`) |
| Recipe transfer | `BeyonderContext` **already holds** `RecipeUnlockService` → zero `ServiceContainer` changes |
| Stronger-target resistance | `Ability.getSequenceCheckTarget` — the pipeline rolls it for us |
| Target dossier | `IDataContext` (`getLastDeathLocation`, `getBedSpawnLocation`, `getPlayerKills`, `getDeathsCount`, `getHealth`, `getPlayTimeHours`, `getTargetAnalysis`) |
| Recording deaths | `IEventContext.subscribeToTemporaryEvent` under the ability's **own** key — the handler sees every event of the class (reference: `GloomyPresence`) |
| Earth Spirit's hands | `playGraspingHands` — added for Seq 8, fits one-to-one |
| Domain / wave / ring | `playCircleEffect`, `playWaveEffect`, `playExplosionRingEffect`, `playRisingSpiral`, `playFadingAura` |
| Spirit identification | `pathways.common.Spirits.isSpirit` |
| Undead identification | `Tag.ENTITY_TYPES_UNDEAD` (never `getCategory()` — throws on 1.21+) |

### New classes, and why nothing existing fits

| Class | Single responsibility | Why not extend something |
|---|---|---|
| `domain.valueobjects.SpiritMediumLore` | Every number of the Seq 7 tier | `GravediggerLore` is the single source of truth for tier 8; two tiers = two VOs (precedent: `PrometheusTheft` / `DreamStealerTheft`) |
| `pathways.death.abilities.LingeringSouls` | Soul registry (victim → place + 30 min deadline), in memory, no persistence | Holds `org.bukkit.Location` → cannot live in `domain`. Not a service but a plain object shared by **two abilities of one pathway**: created in `Death.initializeAbilities()` and passed by constructor — no `static`, no `ServiceContainer` |
| `pathways.death.abilities.SpiritPerception` | Passive: record deaths, draw souls, spirits through walls, danger whisper | `DeathSight` is the Seq 9 "see the invisible up close" passive; folding Seq 7 logic in would make one class carry four duties across two tiers |
| `pathways.death.abilities.SpiritChanneling` | Thin entry: open the menu, return `deferred()` | — |
| `pathways.death.abilities.SpiritChannelingMenu` | Spirit selection GUI | Pattern of `ContractMenu` / `SeaMemoryMenu`: an ability's menu lives in the pathway package |
| `pathways.death.abilities.SpiritChannelingRunner` | One-shot effects of Illusory Eye and Earth Spirit | The documented one-shot pattern (recipe → runner → thin adapter); effects do not belong in a GUI class |
| `pathways.death.abilities.FrostShadowSession` | The frozen domain, which lives in time | The only spirit with a `start → tick → cancel` lifecycle |
| `pathways.death.abilities.VoiceOfTheDead` | Two interrogation branches | — |
| `pathways.death.abilities.CorpseGuise` + `CorpseGuiseSession` | Disguise toggle | `ActiveAbility` + session, **not** `ToggleablePassiveAbility`: a toggle passive cannot switch *itself* off, and the guise must drop the moment spirituality runs out. Same shape as `SpiritVision` / `SpiritVisionSession` in this very pathway |

### Edits to existing code (minimal)

- `IVisualEffectsContext` + `VisualEffectsContext`: **one** new method
  `playSoulWisp(UUID viewerId, Location base, Color color)` — a viewer-private soul
  silhouette (`SOUL` column + `DUST` halo). No existing primitive can do "a shape, for one
  viewer only"; reusing `playPillarEffect` would render for everyone and break the rule
  that only the medium sees souls. Reusable later by Darkness.
- `IRecipeUnlockRepository` + `JSONRecipeUnlockRepository` + `RecipeUnlockService`:
  `revokeRecipe(UUID, String pathwayName, int sequence)`.
- `IBeyonderContext` + `BeyonderContext`:
  `Optional<UnlockedRecipe> stealRecipe(UUID victimId, UUID thiefId)` — one transaction,
  "take from the victim / give to the thief". **Zero changes to `ServiceContainer`.**
- `Death.java`: `sequenceAbilities.put(7, …)`.

### Known interactions to keep in mind

1. `SpiritVision` (Seq 9 toggle) and the new passive both draw spirits. The passive draws
   **only spirits and souls**, free of charge; the toggle stays broader (undead + target
   condition). The split is already documented in `SpiritVision`'s javadoc.
2. `GloomyPresence` (undead do not aggro) vs `CorpseGuise` (undead ignore **even after
   provocation**) — the disguise is strictly stronger and must override `PROVOKED_REASONS`.
3. The Seq 7 and Seq 9 passives share no `AbilityIdentity` — they coexist, exactly as
   agreed for `SpiritCommunication`.
4. `SpiritPerception` must **not** implement a destructive `cleanUp()`: it is a shared
   per-pathway instance and `cleanUp()` fires on *every* player's quit.
5. Per-player tick gating uses `player.getTicksLived() % 20`, never an instance counter —
   the ability instance is shared.
6. Two mediums online means two death subscriptions; the registry is keyed by victim UUID,
   so recording is idempotent.

---

## Part 5 — Visuals and sound

Per `.claude/rules/ability-visual-effects.md`: at least two layers, a shape rather than a
cloud, colour from `PathwayBranding.liquidOf("Death")`, thematic sound.

| Effect | Layers | Sound |
|---|---|---|
| Soul lingering | `playSoulWisp` (silhouette + halo), medium only | `PARTICLE_SOUL_ESCAPE`, quiet, occasional |
| Channelling (shared intro) | `playRisingSpiral` + `playExplosionRingEffect` in the pathway colour | `BLOCK_SOUL_SOIL_BREAK` |
| Illusory Eye | `playCircleEffect` (iris) + `playGlowingDust` high above + `playBeamEffect` downward | `BLOCK_BEACON_ACTIVATE`, low pitch |
| Frost Shadow | domain edge `playCircleEffect(SNOWFLAKE)` + `playWaveEffect` on start + `playConeEffect` on the scythe swing | `BLOCK_GLASS_BREAK` + `ENTITY_PLAYER_HURT_FREEZE` |
| Earth Spirit | `playGraspingHands` (stone tone) + `playWaveEffect` for the swamp | `BLOCK_ROOTED_DIRT_BREAK` + `BLOCK_MUD_PLACE` |
| Voice of the Dead | `playFadingAura` on the soul + `playTravelingBeam` soul → medium | `PARTICLE_SOUL_ESCAPE` + `BLOCK_ENCHANTMENT_TABLE_USE` |
| Corpse Guise | `playFadingAura` on enable, `playDustMark` while burning | `ENTITY_ZOMBIE_AMBIENT`, muffled |

Note: `playExplosionRingEffect` accepts **only** `Particle.DUST` (passing `GUST` crashes
every tick), and `Particle.FLASH` requires a `Color` — use `Particle.EXPLOSION`.

---

## Part 6 — Balance (Sequence 7 bases; they grow via `SequenceScaler`)

| Ability | Spirituality | Blood | Cooldown | Duration / radius |
|---|---|---|---|---|
| Illusory Eye | 30 | — | 45 s | 15 s, 48 blocks |
| Frost Shadow | 55 | — | 90 s | 12 s, 10→20 m |
| Earth Spirit | 40 | 4 HP | 60 s | 5 s hold, 12 blocks |
| Voice of the Dead (dead) | 35 | — | 120 s | soul 30 min, radius 8 blocks |
| Voice of the Dead (living) | 35 | — | 60 s | 16 blocks; resistance → sanity penalty |
| Corpse Guise | 20 + 2/s | — | 10 s | until toggled off or spirituality runs out |
| Spirit Perception | 0 | — | — | radius 24 blocks |
| Physique (HP base) | — | — | — | 7 (Seq 8 = 6) |

All of it lives in `SpiritMediumLore`, unit-tested by `SpiritMediumLoreTest`. No magic time
or damage constants in the ability classes.

---

## Part 7 — Files affected

```
src/main/java/me/vangoo/domain/valueobjects/SpiritMediumLore.java              (new)
src/main/java/me/vangoo/domain/abilities/context/IBeyonderContext.java         (+1 method)
src/main/java/me/vangoo/domain/abilities/context/IVisualEffectsContext.java    (+1 method)
src/main/java/me/vangoo/application/services/context/BeyonderContext.java      (+1 method)
src/main/java/me/vangoo/application/services/context/VisualEffectsContext.java (+1 method)
src/main/java/me/vangoo/application/services/RecipeUnlockService.java          (+revoke)
src/main/java/me/vangoo/infrastructure/IRecipeUnlockRepository.java            (+revoke)
src/main/java/me/vangoo/infrastructure/…/JSONRecipeUnlockRepository.java       (+revoke)
src/main/java/me/vangoo/pathways/death/Death.java                              (Sequence 7)
src/main/java/me/vangoo/pathways/death/abilities/LingeringSouls.java           (new)
src/main/java/me/vangoo/pathways/death/abilities/SpiritPerception.java         (new)
src/main/java/me/vangoo/pathways/death/abilities/SpiritChanneling.java         (new)
src/main/java/me/vangoo/pathways/death/abilities/SpiritChannelingMenu.java     (new)
src/main/java/me/vangoo/pathways/death/abilities/SpiritChannelingRunner.java   (new)
src/main/java/me/vangoo/pathways/death/abilities/FrostShadowSession.java       (new)
src/main/java/me/vangoo/pathways/death/abilities/VoiceOfTheDead.java           (new)
src/main/java/me/vangoo/pathways/death/abilities/CorpseGuise.java              (new)
src/main/java/me/vangoo/pathways/death/abilities/CorpseGuiseSession.java       (new)
src/test/java/…/SpiritMediumLoreTest.java                                      (new)
docs/superpowers/plans/death-seq7.md                                           (this file)
.claude/rules/lingering-souls.md                                               (new)
CLAUDE.md, .claude/rules/new-content-checklist.md                              (edits)
```

---

## Part 8 — Milestones

Each is independently testable in-server. Stop after each and wait for confirmation.

| # | Content | In-server verification |
|---|---|---|
| **M1** | `SpiritMediumLore` + `SpiritMediumLoreTest` | `mvn test -Dtest=SpiritMediumLoreTest` — no server needed |
| **M2** | `Death.java` Sequence 7 (physique + `RitualMagic`); `LingeringSouls`; `SpiritPerception`; `playSoulWisp` | `/pathway` → Seq 7; have another player die → the medium sees the soul, a normal player does not; spirits glow through walls; whisper fires when an enemy closes in from behind |
| **M3** | `revokeRecipe` end to end + `stealRecipe` on the context + dead branch of `VoiceOfTheDead` | Cast on a soul → the medium learns a recipe, the victim **no longer has it**, the soul disappears |
| **M4** | Living branch of `VoiceOfTheDead` | Dossier reads for a weaker target; a stronger Sequence resists → sanity penalty and no information |
| **M5** | `SpiritChanneling` + menu + `SpiritChannelingRunner`: Illusory Eye | The menu opens; resources are consumed only on selection; outlines within 48 blocks visible to the caster only |
| **M6** | Frost Shadow + `FrostShadowSession` | 10 m domain, enemies freeze, caster in ice armour; re-cast and relogin do not multiply sessions |
| **M7** | Earth Spirit (blood cost) | 4 HP is deducted, target sinks and cannot move for 5 s |
| **M8** | `CorpseGuise` | Others see a zombie; Wither/Poison do nothing; undead ignore even after being struck; you burn in sunlight |
| **M9** | Docs: `.claude/rules/lingering-souls.md`, edits to `CLAUDE.md` and the new-content checklist | — |

---

## Part 9 — Deliberate non-goals

- **T2, T2b, S2 produce no code.** Spirituality, Spirit Vision and Eye of Death already
  scale with Sequence. They remain separate specification entries but have no milestone.
- **Zombie Disguise is best-effort.** `EntityDisguiseService` sends packets once, so a
  player entering view range later sees the real player until the tick re-sends the mask.
  That is a limitation of the existing service, not a new one.
- **Souls are not persisted.** A restart clears them — the same deliberate mortality as the
  Error pathway's `TheftOfHeavenlySecrets` disguise. Thirty minutes that survive a restart
  would no longer be a lingering soul, it would be a grave.
- **No new managers, listeners, contexts or repositories.** The soul registry is a plain
  object shared by two abilities of the same pathway; the recipe transfer rides on a
  service the context already holds.
