# Sun Pathway — Design Spec (Sequences 9→4)

Status: approved for implementation (branch `feat/sun-pathway`), 2026-07-22.
Scope: implement Sequences **9→4 only**. Sequences 3/2/1/0 stay empty scaffolds.

## 1. Identity

Holy hybrid pathway: front-loaded **party support** (buff auras, cleansing,
protection) growing into **light/fire burst damage** with a **purification bias**
— bonus damage and debuffs against "dark/undead" pathways. Late-scope signature:
persisted, binding **Contracts** whose breach is lethal.

Fantasy: you are the daylight the dark pathways fear. Early — keep a group alive;
mid — punish undead and silence enemy casters; Seq 4 — burn a battlefield, bind
foes to oaths, strip a broken enemy's power.

Difference from the 6 implemented pathways: Justiciar controls via *temporary*
zones/prohibitions; Sun's control is **consent-based and persistent** (contracts)
plus **support** (buff auras are unique to Sun). No other pathway mutates another
player's stored progression — Sun's Seq 4 purge does (gated).

Balance anchor: **average of the 6 implemented pathways** (Error, Visionary, Door,
Justiciar, WhiteTower, Fool). Holy damage is *conditional* (full vs dark, reduced
vs neutral), so raw numbers may sit at/above average without being oppressive in
mirror matchups.

## 2. Requirements (locked via grill-me)

- Purification vs players: temporary debuffs/silence + bonus holy damage vs
  dark/undead pathways. Seq 4 gets a **permanent** sequence-purge, gated by target
  being "broken" (HP <20% or interruptible channel) **or** consenting / having
  broken a contract.
- Notary (Seq 6): self-Amplification + target Nullification (silence), **plus** a
  real Contract subsystem.
- Contracts: terms = peace (no-attack) / zone-ban / payment-debt; **mutual
  consent**; **permanent** until settled/broken; **persisted to JSON**; breach =
  **guaranteed death by golden fire, no pathway/sequence loss**.
- Integration: pathway + abilities + PathwayManager/PathwayBranding + RitualMagic
  reuse (Seq 8 holy water/blessing). No new mobs/forage/church.
- **Potions: no brew recipes for now** — Sun keeps colored potions/characteristics
  (already wired) but no `potion-recipes.yml` section yet. Abilities only.

## 3. Architecture integration

Follows the shape of the 6 real pathways.

Reused as-is:
- `Pathway` base; `ActiveAbility` / `PermanentPassiveAbility` /
  `ToggleablePassiveAbility` / `OneTimeUseAbility`; `IAbilityContext`
  sub-interfaces; `SequenceScaler.scaleValue`; `AbilityIdentity` (Seq 5
  strengthened versions replace Seq 7 ones on advance); `AbilityTransformer`
  (lower-seq abilities auto-inherit).
- `PhysicalEnhancement` — instantiated directly (as Justiciar does) for Bard/Notary
  physical traits.
- `RitualMagic` (`pathways.common`) — `new RitualMagic()` in the Seq 8 list gives
  holy water via the existing `SANCTIFICATION` ritual in `RitualCatalog`. No new
  ritual code.
- Session pattern (`JurisdictionSession` reference) for stateful auras — instance
  registry, own `BukkitTask`, `cleanUp()` cancels. **No `static` state** (do NOT
  copy `PowerProhibition`'s legacy static approach).
- Persistence pattern (`JSONOrderMembershipRepository` reference) for contracts.
- Wiring via `ServiceContainer` phases; Sun already in
  `PathwayManager`/`PotionManager`/`PathwayBranding`.

## 4. Files

### Modify (existing)
- `pathways/sun/Sun.java` — fill `initializeAbilities()`.
- `infrastructure/di/ServiceContainer.java` — wire contract subsystem.
- `domain/entities/Beyonder.java` (or `BeyonderService`) — guarded permanent
  `lowerSequence()` (persists via existing `beyonders.json`, backward compatible).
- `MysteriesAbovePlugin.java` — register `ContractListener`.

*(No `potion-recipes.yml` / `custom-items.yml` changes — recipes deferred.)*

### New — abilities (`pathways/sun/abilities/`, thin effects only)
`Singing`, `Sunshine`, `Blessing`, `EvilDetection`, `HolyLightSummoning`,
`SunHalo` + `SunHaloSession`, `CleaveOfPurification`, `HorrorImmunity`,
`Notarization`, `Contract`, `LightOfHoliness` / `PurificationHalo` (Seq 5,
`AbilityIdentity`), `FlaringSun`, `UnshadowedSpear`, `UnshadowedDomain` +
`UnshadowedDomainSession`, `HolyEquipment`, `Purification` (Seq 4 purge).

### New — domain (pure, unit-tested)
- `domain/valueobjects/HolyAffinity` — classifies a pathway name as "dark/undead"
  and returns the holy-damage multiplier. Shared by ~6 abilities. The dark-pathway
  set is a **tunable constant** (calibration knob).
- `domain/contracts/Contract` (record: parties, term, params, state) +
  `ContractTerm` enum (PEACE, ZONE_BAN, DEBT) + pure validation. Persisted
  consented contracts do not exist today; `PowerProhibition` only does temporary
  non-consented zones.

### New — contract subsystem
- `infrastructure/contracts/JSONContractRepository` — Gson `contracts.json`,
  corrupt/missing → empty (mirrors `JSONOrderMembershipRepository`).
- `application/services/ContractService` — in-memory registry + repo;
  `propose/sign/settle/breach`; applies golden-fire death on breach.
- `presentation/listeners/ContractListener` — breach detection
  (`EntityDamageByEntityEvent` = peace, `PlayerMoveEvent` = zone) + debt deadline.

Justification: same layering every subsystem uses (pure `domain` ← `application`
service ← `infrastructure` repo ← `presentation` listener), wired once in
`ServiceContainer`. No new layer, no framework.

## 5. Abilities (Sequence 9 → 4)

Names index by level (9 = weakest). Sequence names from `PathwayManager`:
9 Bard · 8 Light Petitioner · 7 Solar High Priest · 6 Notary ·
5 Priest of Light · 4 Shadowless.

**Seq 9 — Bard**
- Фізичне посилення (passive) — reuse `PhysicalEnhancement` (STRENGTH/SPEED).
- Спів (active) — buff aura: nearby allies regen/strength/speed + clears negative
  effects. Low cost, medium cooldown.

**Seq 8 — Light Petitioner**
- Сонячне сяйво (active) — light/fire burst at target, bonus vs dark (HolyAffinity),
  blinds enemies.
- Благословення (toggle) — self+allies fire resist/resistance, clears
  wither/darkness; +dmg vs undead.
- Ритуальна магія — reuse `new RitualMagic()` (holy water = SANCTIFICATION).
- Виявлення зла (passive) — glow nearby undead/dark beyonders (`glowing()`).

**Seq 7 — Solar High Priest**
- Святе світло (active) — pillar of light from sky, AoE holy-fire, extra vs dark.
- Німб Сонця (toggle → session) — aura buffing allies + debuffing/damaging dark
  enemies.
- Очищувальний удар (active buff) — next N melee hits deal bonus holy dmg.
- Імунітет до жаху (passive) — blocks/clears fear/insanity effects.

**Seq 6 — Notary**
- Нотаріальність (active, mode-switch) — Amplification (buff own next abilities) /
  Nullification (silence via existing `cooldown().lockAbilities`).
- Контракт (active + subsystem) — propose→mutual sign; peace/zone/debt; persisted;
  breach = golden-fire death, no progression loss.
- Physical trait boost — reuse `PhysicalEnhancement`.

**Seq 5 — Priest of Light** (strengthened via `AbilityIdentity`)
- Стовп святості — stronger Holy Light + auto night vision.
- Німб очищення — stronger Sun Halo.
- One new active (mass-cleanse burst).

**Seq 4 — Shadowless**
- Пекуче Сонце (active) — large AoE light/fire nuke, huge vs dark; friendly-fire
  warned (thematic drawback).
- Спис Несхилого (active) — light projectile → mini-sun explosion.
- Домен Несхилого (toggle → session) — light zone: reveals invisible/hidden (glow),
  protects allies from darkness, debuffs dark enemies.
- Святе спорядження (toggle) — temp holy armor + strength.
- Очищення (active, **gated**) — permanent sequence-lower. Gate: HP <20% or
  interruptible channel, **or** contract-broken/consenting.

## 6. Testing

- Unit (no Bukkit): `HolyAffinityTest` (classification + multipliers),
  `ContractTest` (term validation, state transitions, breach), purge-gating
  predicate. Style per `SpellRecipeTest` / `BrewMatcherTest`.
- In-server: cast each ability; contract sign→breach→death→progression intact;
  purge lowers sequence + persists across relog.

## 7. Risks / notes

- **Seq 4 permanent purge** is the highest-risk piece: it mutates stored player
  progression. Kept in its own milestone (M8), isolated from combat abilities.
- Contract subsystem is the only genuinely new cross-cutting mechanic → gets its
  own `.claude/rules/contracts.md` and a CLAUDE.md touch in the same branch.
- Potions have no recipes yet (deferred) — Sun remains "abilities-only" for now;
  `Pathway.hasAnyAbility()` will report true once M1 lands.
