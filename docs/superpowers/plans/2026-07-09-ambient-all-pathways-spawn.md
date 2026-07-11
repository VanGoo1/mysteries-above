# Free Ambient Spawn Of All Pathways Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let ambient creature spawns include every pathway (biome-matched), keeping the convergence law as a weighting bias instead of a hard pathway filter.

**Architecture:** Single behavioural change in the pure domain selector `CreatureSelector.pickForAmbient`: drop the line that rejects creatures whose pathway differs from the player's. The existing `multiplier(def, bias)` already favours the player's own pathway (×4 next-needed sequence, ×2 current, ×1 everything else), so removing the filter yields "all pathways, own favoured" with no other logic change. Spawn rate, other spawn channels, and balance numbers are untouched.

**Tech Stack:** Java 21, JUnit 5. Pure-domain unit tests (no Bukkit).

## Global Constraints

- Pure-domain code: `me.vangoo.domain.creatures` must not import `org.bukkit..`, `dev.triumphteam..`, or `net.kyori..` (pinned by `ArchitectureTest.pureDomainCoreHasNoBukkitOrGuiDependencies`). This change adds no imports.
- User-facing text stays Ukrainian; code identifiers, logs, and Javadoc language follow the surrounding file.
- Commit messages: Conventional Commits, English only, imperative subject, no trailing period.
- `mvn` is not on PATH. Run the IntelliJ-bundled Maven via its full path (call operator):
  `& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" ...`
- Work happens on branch `feat/ambient-all-pathways-spawn` (already created; spec already committed there).

---

### Task 1: Relax the ambient pathway filter (test-first)

**Files:**
- Modify: `src/main/java/me/vangoo/domain/creatures/CreatureSelector.java:72-98` (method `pickForAmbient`, and its Javadoc at lines 67-71)
- Test: `src/test/java/me/vangoo/domain/creatures/CreatureSelectorTest.java`

**Interfaces:**
- Consumes: existing `CreatureSelector.pickForAmbient(String biome, ConvergenceBias bias, double roll)` returning `Optional<CreatureDefinition>`; existing private `multiplier(CreatureDefinition, ConvergenceBias)`; test helpers `def(String id, SpawnRule, String pathway, int sequence)` and `natural(double chance)` (biome `OCEAN`, replaces `GUARDIAN`).
- Produces: same method signature, new behaviour — off-pathway biome-matched creatures become eligible, own-pathway stays weighted higher.

- [ ] **Step 1: Rewrite the pathway-mismatch test and add a mixed-pool test**

In `CreatureSelectorTest.java`, replace the whole `ambientEmptyWhenPathwayMismatch` test (lines 147-153):

```java
    @Test
    void ambientIncludesOtherPathways() {
        CreatureDefinition a = def("a", natural(0.2), "visionary", 9);
        CreatureSelector s = new CreatureSelector(List.of(a));
        ConvergenceBias bias = new ConvergenceBias("Error", 5); // different pathway
        // off-pathway creature is now eligible because its biome matches
        assertEquals("a", s.pickForAmbient("OCEAN", bias, 0.5).get().id());
    }

    @Test
    void ambientMixesPathwaysOwnFavored() {
        CreatureDefinition own = def("own", natural(0.2), "visionary", 8);   // next-needed for seq9 bias -> x4
        CreatureDefinition other = def("other", natural(0.2), "error", 8);   // off-pathway -> x1
        CreatureSelector s = new CreatureSelector(List.of(own, other));
        ConvergenceBias bias = new ConvergenceBias("Visionary", 9);
        // weights: own = 0.2*4 = 0.8, other = 0.2*1 = 0.2, sum = 1.0
        // target = roll*1.0; own in [0,0.8), other in [0.8,1.0)
        assertEquals("own", s.pickForAmbient("OCEAN", bias, 0.5).get().id());
        assertEquals("other", s.pickForAmbient("OCEAN", bias, 0.9).get().id());
    }
```

- [ ] **Step 2: Run the tests to verify the new ones fail**

Run: `& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q test -Dtest=CreatureSelectorTest`
Expected: FAIL — `ambientIncludesOtherPathways` gets empty Optional (current filter rejects the "Error" bias), `ambientMixesPathwaysOwnFavored` returns "own" for both rolls (only "own" survives the filter, so `other` is never reachable).

- [ ] **Step 3: Remove the hard pathway filter in `pickForAmbient`**

In `CreatureSelector.java`, inside `pickForAmbient`, delete this line from the candidate loop (currently line 80):

```java
            if (def.pathway() == null || !def.pathway().equalsIgnoreCase(bias.pathway())) continue;
```

The loop keeps the `naturalChance <= 0.0` and biome checks; the `if (bias == null) return Optional.empty();` guard at the top of the method stays. Update the method Javadoc (lines 67-71) to:

```java
    /**
     * Вибір істоти для ambient-спавну: серед усіх істот, чий natural-біом збігається з поточним,
     * незалежно від шляху. Закон конвергенції лишається ВАГОВИМ ухилом (свій шлях і «наступна
     * потрібна» послідовність важать більше через {@link #multiplier}), а не жорстким фільтром.
     * Без «порогу неспавну» — рішення «спавнити» вже прийняв планувальник; цей метод лише обирає,
     * КОГО. {@code roll} у [0,1).
     */
```

- [ ] **Step 4: Run the CreatureSelector tests to verify they pass**

Run: `& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q test -Dtest=CreatureSelectorTest`
Expected: PASS — all tests including `ambientIncludesOtherPathways`, `ambientMixesPathwaysOwnFavored`, and the unchanged `ambientFavorsNextNeededSequence`, `ambientEmptyWhenBiomeMismatch`, `ambientNullBiasEmpty`, `ambientPicksPathwayAndBiomeMatch`.

- [ ] **Step 5: Run the architecture tests to confirm domain purity holds**

Run: `& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q test -Dtest=ArchitectureTest`
Expected: PASS (no new imports were added; this is a guard, not expected to change).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/me/vangoo/domain/creatures/CreatureSelector.java src/test/java/me/vangoo/domain/creatures/CreatureSelectorTest.java
git commit -m "feat(creatures): ambient spawn includes all pathways, own weighted higher"
```

---

### Task 2: Align comments and rules with the new behaviour

**Files:**
- Modify: `src/main/java/me/vangoo/infrastructure/schedulers/AmbientCreatureSpawner.java:23-27` (class Javadoc)
- Modify: `src/main/resources/config.yml:6-7` (comment on `creatures.ambient`)
- Modify: `.claude/rules/mythic-creatures.md` (section "Відомі спрощення")

**Interfaces:**
- Consumes: nothing (comment/doc-only changes).
- Produces: nothing executable.

- [ ] **Step 1: Update the `AmbientCreatureSpawner` class Javadoc**

Replace the class comment (lines 23-27) so it no longer claims own-pathway-only:

```java
/**
 * Ambient-спавн міфічних істот: періодично, для кожного потойбічного далеко від спавну світу, з
 * малим шансом підкидає поряд істоту, чий біом збігається з поточним — нікого не заміняючи. Вибір
 * КОГО робить {@code CreatureSelector.pickForAmbient}: усі шляхи, але свій шлях+послідовність
 * вагово пріоритетні (закон конвергенції як ухил). Дзеркалить життєвий цикл інших планувальників
 * (start/stop + один BukkitTask).
 */
```

- [ ] **Step 2: Update the `config.yml` ambient comment**

Replace the comment block above `ambient:` (lines 6-7) with:

```yaml
  # Ambient-спавн: істоти з'являються самі (нікого не заміняючи) біля потойбічного за межами
  # min-spawn-distance. Обираються серед усіх істот по біому; свій шлях+послідовність гравця —
  # вагово частіше (закон конвергенції як ухил, а не жорсткий фільтр).
```

- [ ] **Step 3: Update the mythic-creatures rule**

In `.claude/rules/mythic-creatures.md`, under "## Відомі спрощення", add a bullet:

```markdown
- Ambient-спавн більше НЕ прив'язаний жорстко до шляху гравця: обираються всі істоти по біому,
  свій шлях+«наступна потрібна» послідовність лише вагово пріоритетні (`CreatureSelector.pickForAmbient`).
```

- [ ] **Step 4: Verify nothing compiles differently (doc-only sanity build of the changed source file's module)**

Run: `& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q test-compile`
Expected: PASS (comment-only Java change compiles cleanly).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/schedulers/AmbientCreatureSpawner.java src/main/resources/config.yml .claude/rules/mythic-creatures.md
git commit -m "docs(creatures): align ambient spawn comments with all-pathways behavior"
```

---

## Self-Review

**Spec coverage:**
- "Прибрати жорсткий фільтр за шляхом у `pickForAmbient`" → Task 1, Step 3. ✓
- "Наявне вагове правило `multiplier` без змін" → Task 1 keeps `multiplier`; tests assert the weighting. ✓
- "Apex рідкісні через `naturalChance`" → unchanged weighting base; no code touches it. ✓
- "Темп/баланс/природний/структурний канал не чіпаємо" → no change to `config.yml` numbers, `pickForBiome`, `pickForStructure`, or spawner rate logic; only comments in Task 2. ✓
- "Null-bias лишається empty" → guard kept (Task 1 Step 3); `ambientNullBiasEmpty` stays green (Task 1 Step 4). ✓
- Tests: rewrite `ambientEmptyWhenPathwayMismatch`, add mixed-pool test, keep the four listed → Task 1 Steps 1-4. ✓
- Docs: Javadoc of `pickForAmbient` (Task 1 Step 3), `AmbientCreatureSpawner` comment, `config.yml` comment, `mythic-creatures.md` (Task 2). ✓

**Placeholder scan:** No TBD/TODO; all code and commands are concrete. ✓

**Type consistency:** `pickForAmbient(String, ConvergenceBias, double)` and `def(String, SpawnRule, String, int)` / `natural(double)` used consistently with the existing file. Weight arithmetic in `ambientMixesPathwaysOwnFavored` matches `multiplier` (×4 next-needed, ×1 off-pathway) and the `target = roll * sumWeights` segmentation in `pickForAmbient`. ✓
