# Sun Pathway — Implementation Plan

Branch: `feat/sun-pathway`. Spec: `docs/superpowers/specs/sun-pathway.md`.
Rule: one milestone at a time; before each — state affected files, architectural
impact, risks; after each — summary + in-server test. Wait for confirmation
between milestones.

## Milestones

**M0 — Branch + docs** (done)
- `feat/sun-pathway` created; spec + this plan written. No code.

**M1 — Seq 9 Bard**
- Files: `pathways/sun/abilities/Singing.java` (new); `pathways/sun/Sun.java`
  (`sequenceAbilities.put(9, ...)`), reusing `PhysicalEnhancement`.
- Test: `/pathway` grant Sun Seq 9, cast Singing near an ally, verify buffs +
  debuff-clear.

**M2 — Seq 8 + `HolyAffinity` VO**
- Files: `domain/valueobjects/HolyAffinity.java` + `HolyAffinityTest.java` (new);
  `Sunshine`, `Blessing`, `EvilDetection` (new); wire `new RitualMagic()`;
  `Sun.put(8, ...)`.
- Test: `mvn test -Dtest=HolyAffinityTest`; in-server cast each, verify
  anti-dark bonus, ritual holy water, glow detection.

**M3 — Seq 7**
- Files: `HolyLightSummoning`, `SunHalo` + `SunHaloSession`, `CleaveOfPurification`,
  `HorrorImmunity` (new); `Sun.put(7, ...)`.
- Test: pillar damage/AoE; halo session buffs/debuffs + `cleanUp()` on disable;
  cleave on-hit; horror immunity clears fear effects.

**M4 — Seq 6 Notarization**
- Files: `Notarization` (new); `Sun.put(6, ...)` (contracts added in M5).
- Test: Amplification buffs own next ability; Nullification silences target
  (`lockAbilities`).

**M5 — Contract subsystem**
- Files: `domain/contracts/Contract.java`, `ContractTerm.java` + `ContractTest`
  (new); `infrastructure/contracts/JSONContractRepository.java` (new);
  `application/services/ContractService.java` (new);
  `presentation/listeners/ContractListener.java` (new);
  `infrastructure/di/ServiceContainer.java`, `MysteriesAbovePlugin.java` (wire);
  `pathways/sun/abilities/Contract.java` (new); add to `Sun.put(6, ...)`.
- Docs: `.claude/rules/contracts.md` + CLAUDE.md touch (same milestone).
- Test: `mvn test -Dtest=ContractTest`; in-server sign peace/zone/debt, breach
  each → golden-fire death, verify pathway/sequence intact + persistence across
  relog.

**M6 — Seq 5 strengthened**
- Files: `LightOfHoliness`, `PurificationHalo` (new, `AbilityIdentity` replacing
  Seq 7 versions) + one new active; `Sun.put(5, ...)`.
- Test: advance to Seq 5, verify old abilities replaced by stronger versions.

**M7 — Seq 4 combat**
- Files: `FlaringSun`, `UnshadowedSpear`, `UnshadowedDomain` +
  `UnshadowedDomainSession`, `HolyEquipment` (new); `Sun.put(4, ...)`
  (purge added in M8).
- Test: nuke AoE + friendly-fire warning; spear explosion; domain reveals hidden +
  session cleanup; holy equipment armor/buff.

**M8 — Seq 4 Purification purge** (highest risk, isolated)
- Files: `domain/entities/Beyonder.java` or `BeyonderService` (guarded permanent
  `lowerSequence()`); `pathways/sun/abilities/Purification.java` (new) with gating
  (HP <20% / channel, or contract-broken/consent); add to `Sun.put(4, ...)`.
- Test: purge a broken target, verify sequence lowered + persisted across relog;
  verify gate blocks a healthy non-consenting target.

## Notes
- No `potion-recipes.yml` / `custom-items.yml` work (recipes deferred by decision).
- Commit per milestone only when the user asks; Conventional Commits, English,
  scope `sun` / `contracts`.
- Store durable architectural decisions in memory/claude-mem after M5 and M8.
