# Creature Ecology & Combat Scaling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make mythical creatures aggressive-on-spawn, gentler-hitting, spawn only far from world spawn, biased toward the nearby Beyonder's pathway/next-sequence (Law of Convergence), and deal sequence-scaled reduced damage to Beyonders.

**Architecture:** Pure rules (creature `sequence`, convergence weighting, damage-reduction fraction, distance check) live in `domain` and are unit-tested with zero Bukkit. Effects (aggression, damage interception, spawn-listener wiring, config) live in `infrastructure`/`presentation` and are verified in-server per project convention. No new `domain`→Bukkit or `domain`→`pathways` dependency.

**Tech Stack:** Java 21, Spigot/Bukkit API 1.21.1, JUnit 5, Maven (shade). Build via IntelliJ-bundled Maven (`mvn` is not on PATH — exit 127). Use the bundled binary, e.g. `"C:/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd"`.

## Global Constraints

- Java 21, Bukkit API 1.21.1. Use NEW attribute/effect names (no `GENERIC_` prefix).
- `domain` packages (`me.vangoo.domain.*` pure core: `creatures`, `services`, `valueobjects`) must not import `org.bukkit.*`. ArchUnit pins this.
- Convergence must NOT change total natural spawn probability — only redistribute it.
- Damage reduction uses WEAK scaling, capped at 0.45 (45%) at Seq 0.
- Distance gate default 2000 blocks, horizontal (X/Z), measured from world spawn point; configurable via `config.yml`.
- User-facing strings in Ukrainian where applicable; keep `§`-colour style.
- Unit tests for pure pieces; Bukkit effects verified in-server (no Bukkit mocking).
- Commit after each task. Branch: `feat/hunting-creatures` (already checked out).

---

### Task 1: Add `sequence` to CreatureDefinition + loader derivation

**Files:**
- Modify: `src/main/java/me/vangoo/domain/creatures/CreatureDefinition.java`
- Modify: `src/main/java/me/vangoo/infrastructure/creatures/CreatureConfigLoader.java:51-75`
- Modify (compile fix): `src/test/java/me/vangoo/domain/creatures/CreatureSelectorTest.java:14-21`

**Interfaces:**
- Produces: `CreatureDefinition(... String pathway, int sequence)` — `int sequence()` accessor (0..9).
- Consumed by: Task 3 (convergence), Task 5 (bias wiring).

- [ ] **Step 1: Add the record component**

Edit `CreatureDefinition.java` — add `int sequence` as the final component:

```java
public record CreatureDefinition(
        String id,
        String baseEntityType,
        String displayName,
        CreatureTier tier,
        CreatureStats stats,
        Map<String, String> equipment,
        String appearance,
        LootTableData loot,
        SpawnRule spawn,
        boolean clearVanillaDrops,
        String pathway,
        int sequence) {}
```

- [ ] **Step 2: Derive `sequence` in the loader**

In `CreatureConfigLoader.parseCreature`, after the `pathway` block (line ~71) and before the `return`, add derivation, then pass it to the constructor:

```java
        int sequence = c.getInt("sequence", -1);
        if (sequence < 0 || sequence > 9) {
            int us = id.lastIndexOf('_');
            sequence = 9;
            if (us >= 0 && us < id.length() - 1) {
                try {
                    int parsed = Integer.parseInt(id.substring(us + 1));
                    if (parsed >= 0 && parsed <= 9) {
                        sequence = parsed;
                    } else {
                        plugin.getLogger().warning("Creature '" + id
                                + "': sequence out of range, defaulting to 9");
                    }
                } catch (NumberFormatException ex) {
                    plugin.getLogger().warning("Creature '" + id
                            + "': cannot derive sequence from id, defaulting to 9");
                }
            }
        }

        return new CreatureDefinition(id, baseEntity.toUpperCase(Locale.ROOT), displayName, tier,
                stats, equipment, appearance, loot, spawn, clearDrops, pathway, sequence);
```

Replace the existing `return new CreatureDefinition(...)` (lines 73-74) with the version above.

- [ ] **Step 3: Fix the test construction site**

In `CreatureSelectorTest.java`, update the `def` helper to pass a sequence (default 9) and add an overload that takes pathway+sequence for later convergence tests:

```java
    private CreatureDefinition def(String id, SpawnRule spawn) {
        return def(id, spawn, "visionary", 9);
    }

    private CreatureDefinition def(String id, SpawnRule spawn, String pathway, int sequence) {
        return new CreatureDefinition(
                id, "GUARDIAN", "§3" + id, CreatureTier.COMMON,
                new CreatureStats(30, 6, 0.25, 1.2),
                Map.of(), "vanilla",
                new LootTableData(List.of(), 1, 2),
                spawn, true, pathway, sequence);
    }
```

- [ ] **Step 4: Build to verify compilation + existing tests pass**

Run: `"C:/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd" -q test -Dtest=CreatureSelectorTest`
Expected: PASS (8 existing tests still green; project compiles with the new component).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/vangoo/domain/creatures/CreatureDefinition.java \
        src/main/java/me/vangoo/infrastructure/creatures/CreatureConfigLoader.java \
        src/test/java/me/vangoo/domain/creatures/CreatureSelectorTest.java
git commit -m "feat(creatures): add sequence to CreatureDefinition (derived from id suffix)"
```

---

### Task 2: `SequenceScaler.creatureDamageReduction`

**Files:**
- Modify: `src/main/java/me/vangoo/domain/services/SequenceScaler.java`
- Test: `src/test/java/me/vangoo/domain/services/SequenceScalerTest.java` (create if absent; otherwise add methods)

**Interfaces:**
- Produces: `static double SequenceScaler.creatureDamageReduction(int sequenceLevel)` → fraction in `[0.0, 0.45]`.
- Consumed by: Task 7 (`CreatureDamageListener`).

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/vangoo/domain/services/SequenceScalerTest.java` (or add these methods if the file exists):

```java
package me.vangoo.domain.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SequenceScalerTest {

    @Test
    void creatureDamageReductionWeakAtSeq9IsZero() {
        assertEquals(0.0, SequenceScaler.creatureDamageReduction(9), 1e-9);
    }

    @Test
    void creatureDamageReductionGrowsFivePercentPerLevel() {
        assertEquals(0.20, SequenceScaler.creatureDamageReduction(5), 1e-9); // power 4 * 0.05
        assertEquals(0.45, SequenceScaler.creatureDamageReduction(0), 1e-9); // power 9 * 0.05 = 0.45 (cap)
    }

    @Test
    void creatureDamageReductionCappedAt45Percent() {
        // even if an out-of-range stronger level were passed, never exceed the cap
        assertEquals(0.45, SequenceScaler.creatureDamageReduction(-1), 1e-9);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `"C:/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd" -q test -Dtest=SequenceScalerTest`
Expected: FAIL — `cannot find symbol: method creatureDamageReduction`.

- [ ] **Step 3: Implement the method**

In `SequenceScaler.java`, add (after `scaleDamagePenalty`):

```java
    /**
     * Скейлінг ЗАХИСТУ від потойбічних істот (WEAK): чим сильніший Beyonder (нижчий sequence),
     * тим менше урону по ньому проходить. Seq 9 → 0%, Seq 5 → 20%, Seq 0 → 45% (стеля).
     *
     * @return частка зниження урону в діапазоні [0.0, 0.45]
     */
    public static double creatureDamageReduction(int sequenceLevel) {
        int power = 9 - sequenceLevel; // Seq 9 = 0, Seq 0 = 9
        double reduction = power * (ScalingStrategy.WEAK.getPercentPerLevel() / 100.0);
        return Math.max(0.0, Math.min(0.45, reduction));
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `"C:/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd" -q test -Dtest=SequenceScalerTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/vangoo/domain/services/SequenceScaler.java \
        src/test/java/me/vangoo/domain/services/SequenceScalerTest.java
git commit -m "feat(scaler): WEAK creature-damage-reduction (cap 45% at Seq 0)"
```

---

### Task 3: `ConvergenceBias` VO + biased `CreatureSelector.pickForBiome`

**Files:**
- Create: `src/main/java/me/vangoo/domain/creatures/ConvergenceBias.java`
- Modify: `src/main/java/me/vangoo/domain/creatures/CreatureSelector.java`
- Test: `src/test/java/me/vangoo/domain/creatures/CreatureSelectorTest.java`

**Interfaces:**
- Produces: `ConvergenceBias(String pathway, int sequenceLevel)`.
- Produces: `Optional<CreatureDefinition> CreatureSelector.pickForBiome(String biome, String baseEntityType, double roll, ConvergenceBias bias)`. The 3-arg overload is retained and delegates with `bias = null`.
- Consumed by: Task 5 (`NaturalCreatureSpawnListener`).

- [ ] **Step 1: Write the failing tests**

Add to `CreatureSelectorTest.java`:

```java
    @Test
    void noBiasOverloadMatchesThreeArg() {
        CreatureSelector s = new CreatureSelector(List.of(def("a", natural(0.5))));
        assertEquals(s.pickForBiome("OCEAN", "GUARDIAN", 0.4),
                     s.pickForBiome("OCEAN", "GUARDIAN", 0.4, null));
    }

    @Test
    void convergencePreservesTotalSpawnProbability() {
        // two visionary candidates, seq 9 and seq 8, base chance 0.2 each -> total 0.4
        CreatureDefinition a = def("a", natural(0.2), "visionary", 9);
        CreatureDefinition b = def("b", natural(0.2), "visionary", 8);
        CreatureSelector s = new CreatureSelector(List.of(a, b));
        ConvergenceBias bias = new ConvergenceBias("Visionary", 9); // next-needed = 8 (x4), current = 9 (x2)

        // total spawn probability unchanged: roll just under 0.4 spawns, 0.4 does not
        assertTrue(s.pickForBiome("OCEAN", "GUARDIAN", 0.399, bias).isPresent());
        assertTrue(s.pickForBiome("OCEAN", "GUARDIAN", 0.4, bias).isEmpty());
    }

    @Test
    void convergenceFavorsNextNeededSequence() {
        CreatureDefinition a = def("a", natural(0.2), "visionary", 9); // current -> x2
        CreatureDefinition b = def("b", natural(0.2), "visionary", 8); // next-needed -> x4
        CreatureSelector s = new CreatureSelector(List.of(a, b));
        ConvergenceBias bias = new ConvergenceBias("Visionary", 9);
        // weights: a=0.4, b=0.8, sum=1.2, scale=0.4/1.2 -> a=0.1333, b=0.2667
        assertEquals("a", s.pickForBiome("OCEAN", "GUARDIAN", 0.10, bias).get().id());
        assertEquals("b", s.pickForBiome("OCEAN", "GUARDIAN", 0.20, bias).get().id());
        assertEquals("b", s.pickForBiome("OCEAN", "GUARDIAN", 0.39, bias).get().id());
    }

    @Test
    void convergenceForUnrelatedPathwayIsNoOp() {
        CreatureDefinition a = def("a", natural(0.2), "visionary", 9);
        CreatureDefinition b = def("b", natural(0.2), "visionary", 8);
        CreatureSelector s = new CreatureSelector(List.of(a, b));
        ConvergenceBias unrelated = new ConvergenceBias("Error", 5);
        // no candidate matches -> same segments as no-bias: a in [0,0.2), b in [0.2,0.4)
        assertEquals("a", s.pickForBiome("OCEAN", "GUARDIAN", 0.10, unrelated).get().id());
        assertEquals("b", s.pickForBiome("OCEAN", "GUARDIAN", 0.30, unrelated).get().id());
        assertTrue(s.pickForBiome("OCEAN", "GUARDIAN", 0.40, unrelated).isEmpty());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `"C:/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd" -q test -Dtest=CreatureSelectorTest`
Expected: FAIL — `ConvergenceBias` symbol not found / no 4-arg `pickForBiome`.

- [ ] **Step 3: Create `ConvergenceBias`**

`src/main/java/me/vangoo/domain/creatures/ConvergenceBias.java`:

```java
package me.vangoo.domain.creatures;

/**
 * Закон конвергенції: схиляє ВИБІР істоти на природному спавні до шляху гравця та послідовності,
 * яка йому скоро знадобиться. Не змінює загальну ймовірність спавну — лише перерозподіляє її.
 *
 * @param pathway       назва шляху гравця (порівняння без урахування регістру з CreatureDefinition.pathway)
 * @param sequenceLevel поточний рівень послідовності гравця (9 = найслабший, 0 = найсильніший)
 */
public record ConvergenceBias(String pathway, int sequenceLevel) {}
```

- [ ] **Step 4: Implement the biased overload**

Replace `CreatureSelector.pickForBiome(String, String, double)` with the delegating + weighted version:

```java
    private static final double NEXT_NEEDED_WEIGHT = 4.0; // sequenceLevel - 1
    private static final double CURRENT_WEIGHT = 2.0;     // sequenceLevel

    public Optional<CreatureDefinition> pickForBiome(String biome, String baseEntityType, double roll) {
        return pickForBiome(biome, baseEntityType, roll, null);
    }

    public Optional<CreatureDefinition> pickForBiome(String biome, String baseEntityType, double roll,
                                                     ConvergenceBias bias) {
        List<CreatureDefinition> candidates = new ArrayList<>();
        double totalChance = 0.0;
        for (CreatureDefinition def : creatures) {
            SpawnRule s = def.spawn();
            if (s.naturalChance() <= 0.0) continue;
            if (!s.naturalBiomes().contains(biome)) continue;
            if (!s.naturalReplaces().contains(baseEntityType)) continue;
            candidates.add(def);
            totalChance += s.naturalChance();
        }
        if (candidates.isEmpty() || totalChance <= 0.0) return Optional.empty();

        double sumWeights = 0.0;
        double[] weights = new double[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            weights[i] = candidates.get(i).spawn().naturalChance() * multiplier(candidates.get(i), bias);
            sumWeights += weights[i];
        }
        // renormalize so the picked segments sum to the ORIGINAL totalChance (preserve P(spawn))
        double scale = sumWeights > 0.0 ? totalChance / sumWeights : 0.0;

        double cumulative = 0.0;
        for (int i = 0; i < candidates.size(); i++) {
            cumulative += weights[i] * scale;
            if (roll < cumulative) return Optional.of(candidates.get(i));
        }
        return Optional.empty();
    }

    private double multiplier(CreatureDefinition def, ConvergenceBias bias) {
        if (bias == null || bias.pathway() == null) return 1.0;
        if (!def.pathway().equalsIgnoreCase(bias.pathway())) return 1.0;
        if (def.sequence() == bias.sequenceLevel() - 1) return NEXT_NEEDED_WEIGHT;
        if (def.sequence() == bias.sequenceLevel()) return CURRENT_WEIGHT;
        return 1.0;
    }
```

Add the import `java.util.ArrayList;` at the top of `CreatureSelector.java` if not present.

- [ ] **Step 5: Run tests to verify they pass**

Run: `"C:/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd" -q test -Dtest=CreatureSelectorTest`
Expected: PASS (existing 8 + 4 new = 12 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/me/vangoo/domain/creatures/ConvergenceBias.java \
        src/main/java/me/vangoo/domain/creatures/CreatureSelector.java \
        src/test/java/me/vangoo/domain/creatures/CreatureSelectorTest.java
git commit -m "feat(creatures): Law of Convergence — bias natural spawn selection, preserve P(spawn)"
```

---

### Task 4: Distance gate (pure helper + config + both listeners)

**Files:**
- Create: `src/main/java/me/vangoo/domain/creatures/SpawnDistanceGate.java`
- Test: `src/test/java/me/vangoo/domain/creatures/SpawnDistanceGateTest.java`
- Modify: `src/main/resources/config.yml`
- Modify: `src/main/java/me/vangoo/presentation/listeners/NaturalCreatureSpawnListener.java`
- Modify: `src/main/java/me/vangoo/presentation/listeners/StructureCreatureSpawnListener.java`
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java:199-202`

**Interfaces:**
- Produces: `static boolean SpawnDistanceGate.isFarEnough(double dx, double dz, double minDistance)`.
- Both listener constructors gain a trailing `double minSpawnDistance` parameter.

- [ ] **Step 1: Write the failing test**

`src/test/java/me/vangoo/domain/creatures/SpawnDistanceGateTest.java`:

```java
package me.vangoo.domain.creatures;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpawnDistanceGateTest {

    @Test
    void insideRadiusIsNotFarEnough() {
        assertFalse(SpawnDistanceGate.isFarEnough(1000, 0, 2000));
        assertFalse(SpawnDistanceGate.isFarEnough(1000, 1000, 2000)); // ~1414 < 2000
    }

    @Test
    void onOrBeyondRadiusIsFarEnough() {
        assertTrue(SpawnDistanceGate.isFarEnough(2000, 0, 2000));
        assertTrue(SpawnDistanceGate.isFarEnough(0, 2500, 2000));
        assertTrue(SpawnDistanceGate.isFarEnough(1500, 1500, 2000)); // ~2121 >= 2000
    }

    @Test
    void zeroMinDistanceAlwaysFarEnough() {
        assertTrue(SpawnDistanceGate.isFarEnough(0, 0, 0));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `"C:/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd" -q test -Dtest=SpawnDistanceGateTest`
Expected: FAIL — `SpawnDistanceGate` not found.

- [ ] **Step 3: Implement the helper**

`src/main/java/me/vangoo/domain/creatures/SpawnDistanceGate.java`:

```java
package me.vangoo.domain.creatures;

/** Чисте правило: чи достатньо далеко (по горизонталі) точка від спавну світу для появи істот. */
public final class SpawnDistanceGate {

    private SpawnDistanceGate() {}

    /**
     * @param dx          різниця X від точки спавну світу
     * @param dz          різниця Z від точки спавну світу
     * @param minDistance мінімальна горизонтальна відстань у блоках
     * @return true, якщо sqrt(dx^2 + dz^2) >= minDistance
     */
    public static boolean isFarEnough(double dx, double dz, double minDistance) {
        if (minDistance <= 0.0) return true;
        return (dx * dx + dz * dz) >= (minDistance * minDistance);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `"C:/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd" -q test -Dtest=SpawnDistanceGateTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Add config default**

Append to `src/main/resources/config.yml` (top-level):

```yaml
# Потойбічні істоти
creatures:
  # Мінімальна горизонтальна відстань (у блоках) від точки спавну світу, на якій починають
  # з'являтися міфічні істоти (природний спавн + спавн у структурах). /creature spawn ігнорує це.
  min-spawn-distance: 2000
```

- [ ] **Step 6: Gate the natural spawn listener**

Edit `NaturalCreatureSpawnListener.java` — add the field, constructor param, and gate. Replace the class body’s constructor + handler:

```java
    private final CreatureSelector selector;
    private final CreatureSpawner spawner;
    private final double minSpawnDistance;
    private final Random random = new Random();

    public NaturalCreatureSpawnListener(CreatureSelector selector, CreatureSpawner spawner,
                                        double minSpawnDistance) {
        this.selector = selector;
        this.spawner = spawner;
        this.minSpawnDistance = minSpawnDistance;
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;

        org.bukkit.Location loc = event.getLocation();
        if (loc.getWorld() != null) {
            org.bukkit.Location ws = loc.getWorld().getSpawnLocation();
            if (!me.vangoo.domain.creatures.SpawnDistanceGate.isFarEnough(
                    loc.getX() - ws.getX(), loc.getZ() - ws.getZ(), minSpawnDistance)) {
                return;
            }
        }

        String biome = event.getLocation().getBlock().getBiome().name();
        String type = event.getEntityType().name();

        Optional<CreatureDefinition> pick = selector.pickForBiome(biome, type, random.nextDouble());
        if (pick.isEmpty()) return;

        event.setCancelled(true);
        spawner.spawn(pick.get(), event.getLocation());
    }
```

(Task 5 replaces the `pickForBiome` call with the biased variant — leave the 3-arg form here for now.)

- [ ] **Step 7: Gate the structure spawn listener**

Edit `StructureCreatureSpawnListener.java` — add field + constructor param, and an early return inside `onLootGenerate` after the null check on `loc`:

```java
    private final double minSpawnDistance;
```

Constructor:

```java
    public StructureCreatureSpawnListener(CreatureSelector selector, CreatureSpawner spawner,
                                          double minSpawnDistance) {
        this.selector = selector;
        this.spawner = spawner;
        this.minSpawnDistance = minSpawnDistance;
    }
```

Inside `onLootGenerate`, immediately after `if (loc == null || loc.getWorld() == null) return;`:

```java
        org.bukkit.Location ws = loc.getWorld().getSpawnLocation();
        if (!me.vangoo.domain.creatures.SpawnDistanceGate.isFarEnough(
                loc.getX() - ws.getX(), loc.getZ() - ws.getZ(), minSpawnDistance)) {
            return;
        }
```

- [ ] **Step 8: Pass the config value in ServiceContainer**

In `ServiceContainer.java`, where the two listeners are constructed (lines ~199-202), read the config and pass it:

```java
        double minSpawnDistance = plugin.getConfig().getDouble("creatures.min-spawn-distance", 2000.0);
        this.naturalCreatureSpawnListener = new me.vangoo.presentation.listeners.NaturalCreatureSpawnListener(
                creatureSelector, creatureSpawner, minSpawnDistance);
        this.structureCreatureSpawnListener = new me.vangoo.presentation.listeners.StructureCreatureSpawnListener(
                creatureSelector, creatureSpawner, minSpawnDistance);
```

- [ ] **Step 9: Build to verify compilation + full test suite**

Run: `"C:/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd" -q test`
Expected: PASS (all tests, including new gate tests). Project compiles.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/me/vangoo/domain/creatures/SpawnDistanceGate.java \
        src/test/java/me/vangoo/domain/creatures/SpawnDistanceGateTest.java \
        src/main/resources/config.yml \
        src/main/java/me/vangoo/presentation/listeners/NaturalCreatureSpawnListener.java \
        src/main/java/me/vangoo/presentation/listeners/StructureCreatureSpawnListener.java \
        src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java
git commit -m "feat(creatures): gate spawns to >=2000 blocks from world spawn (config-driven)"
```

---

### Task 5: Wire Law of Convergence into the natural spawn listener

**Files:**
- Modify: `src/main/java/me/vangoo/presentation/listeners/NaturalCreatureSpawnListener.java`
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java:199-200`

**Interfaces:**
- Consumes: `CreatureSelector.pickForBiome(biome, type, roll, ConvergenceBias)`, `BeyonderService.getBeyonder(UUID)`, `Beyonder.getPathway().getName()`, `Beyonder.getSequenceLevel()`.
- The natural listener constructor gains a `BeyonderService beyonderService` parameter.

**Verification:** in-server (Bukkit-dependent; no headless test).

- [ ] **Step 1: Add `BeyonderService` to the listener and compute bias**

Edit `NaturalCreatureSpawnListener.java`. Add the field + constructor param:

```java
    private final me.vangoo.application.services.BeyonderService beyonderService;
```

Constructor (add `beyonderService` after `minSpawnDistance`):

```java
    public NaturalCreatureSpawnListener(CreatureSelector selector, CreatureSpawner spawner,
                                        double minSpawnDistance,
                                        me.vangoo.application.services.BeyonderService beyonderService) {
        this.selector = selector;
        this.spawner = spawner;
        this.minSpawnDistance = minSpawnDistance;
        this.beyonderService = beyonderService;
    }
```

Replace the `pickForBiome(biome, type, random.nextDouble())` call with a bias-aware version. After computing `biome`/`type`:

```java
        me.vangoo.domain.creatures.ConvergenceBias bias = nearestBias(event.getLocation());
        Optional<CreatureDefinition> pick =
                selector.pickForBiome(biome, type, random.nextDouble(), bias);
        if (pick.isEmpty()) return;
```

Add the helper method `nearestBias` to the class:

```java
    /** Знаходить найближчого онлайн-потойбічного в радіусі 48 блоків і будує з нього зсув. */
    private me.vangoo.domain.creatures.ConvergenceBias nearestBias(org.bukkit.Location loc) {
        if (loc.getWorld() == null) return null;
        org.bukkit.entity.Player nearest = null;
        double best = 48.0 * 48.0;
        for (org.bukkit.entity.Player p : loc.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(loc);
            if (d <= best) {
                me.vangoo.domain.entities.Beyonder b = beyonderService.getBeyonder(p.getUniqueId());
                if (b != null) {
                    best = d;
                    nearest = p;
                }
            }
        }
        if (nearest == null) return null;
        me.vangoo.domain.entities.Beyonder b = beyonderService.getBeyonder(nearest.getUniqueId());
        return new me.vangoo.domain.creatures.ConvergenceBias(
                b.getPathway().getName(), b.getSequenceLevel());
    }
```

- [ ] **Step 2: Pass `beyonderService` in ServiceContainer**

Update the natural listener construction (Task 4 left it 3-arg):

```java
        this.naturalCreatureSpawnListener = new me.vangoo.presentation.listeners.NaturalCreatureSpawnListener(
                creatureSelector, creatureSpawner, minSpawnDistance, beyonderService);
```

- [ ] **Step 3: Build to verify compilation**

Run: `"C:/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd" -q -DskipTests package`
Expected: BUILD SUCCESS; shaded jar produced in `target/`.

- [ ] **Step 4: In-server smoke test (manual)**

1. Stop the test server; delete `plugins/Mysteries-Above/creatures.yml`; drop the new jar in `plugins/`.
2. Start the server, become a Visionary at e.g. Sequence 9, travel >2000 blocks out, stand in a matching biome, and observe natural spawns over a few minutes.
3. Expect a noticeably higher share of `visionary_*` creatures of sequence 9/8 than other pathways, but spawns still occur for others (rate not exploding).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/vangoo/presentation/listeners/NaturalCreatureSpawnListener.java \
        src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java
git commit -m "feat(creatures): apply Law of Convergence using nearest Beyonder's pathway+sequence"
```

---

### Task 6: Aggressive-from-spawn (`CreatureAggression` + spawner + manager tick)

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/creatures/CreatureAggression.java`
- Modify: `src/main/java/me/vangoo/infrastructure/creatures/CreatureSpawner.java:60-62`
- Modify: `src/main/java/me/vangoo/infrastructure/creatures/behavior/CreatureBehaviorManager.java:52-69`

**Interfaces:**
- Produces: `static void CreatureAggression.acquireTarget(LivingEntity entity, double range)`.

**Verification:** in-server (Bukkit-dependent; no headless test).

- [ ] **Step 1: Create the helper**

`src/main/java/me/vangoo/infrastructure/creatures/CreatureAggression.java`:

```java
package me.vangoo.infrastructure.creatures;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/** Робить істоту агресивною: одразу націлюється на найближчого гравця, якщо ціль відсутня/мертва. */
public final class CreatureAggression {

    private CreatureAggression() {}

    public static void acquireTarget(LivingEntity entity, double range) {
        if (!(entity instanceof Mob mob)) return;
        LivingEntity current = mob.getTarget();
        if (current != null && !current.isDead()) return;

        Player nearest = null;
        double best = range * range;
        for (Entity e : entity.getNearbyEntities(range, range, range)) {
            if (e instanceof Player p && !p.isDead() && p.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
                double d = p.getLocation().distanceSquared(entity.getLocation());
                if (d <= best) {
                    best = d;
                    nearest = p;
                }
            }
        }
        if (nearest != null) {
            mob.setTarget(nearest);
        }
    }
}
```

- [ ] **Step 2: Aggro on spawn**

In `CreatureSpawner.spawn(...)`, after `behaviorManager.start(living, def);` and before `return Optional.of(living);`:

```java
        CreatureAggression.acquireTarget(living, 24.0);
```

- [ ] **Step 3: Re-aggro each manager tick**

In `CreatureBehaviorManager.tickAll()`, inside the loop, after confirming `self` is a live `LivingEntity` (right after the `instanceof`/`isDead` guard, before the nearby-Beyonder gather), add:

```java
            me.vangoo.infrastructure.creatures.CreatureAggression.acquireTarget(self, 24.0);
```

This runs for every registered creature every tick of the manager (period 20t). All current creatures have a pathway → a behavior, so all are registered and all re-aggro.

- [ ] **Step 4: Build to verify compilation**

Run: `"C:/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd" -q -DskipTests package`
Expected: BUILD SUCCESS.

- [ ] **Step 5: In-server smoke test (manual)**

Spawn a creature with `/creature spawn <id>` near you in Survival; it should path toward and attack you without being hit first. Walk out of range until it loses the target, then return — it re-acquires within ~1s.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/creatures/CreatureAggression.java \
        src/main/java/me/vangoo/infrastructure/creatures/CreatureSpawner.java \
        src/main/java/me/vangoo/infrastructure/creatures/behavior/CreatureBehaviorManager.java
git commit -m "feat(creatures): aggressive-from-spawn + periodic re-aggro"
```

---

### Task 7: `CreatureDamageListener` (sequence-scaled defense)

**Files:**
- Create: `src/main/java/me/vangoo/presentation/listeners/CreatureDamageListener.java`
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java` (field + getter + construction)
- Modify: `src/main/java/me/vangoo/MysteriesAbovePlugin.java:241` area (register)

**Interfaces:**
- Consumes: `CreatureCodec.isCreature(Entity)`, `BeyonderService.getBeyonder(UUID)`, `Beyonder.getSequenceLevel()`, `SequenceScaler.creatureDamageReduction(int)`.

**Verification:** in-server (Bukkit-dependent; no headless test).

- [ ] **Step 1: Create the listener**

`src/main/java/me/vangoo/presentation/listeners/CreatureDamageListener.java`:

```java
package me.vangoo.presentation.listeners;

import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.infrastructure.creatures.CreatureCodec;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Скейлінг ЗАХИСТУ: коли тегована потойбічна істота б'є потойбічного-гравця, урон множиться на
 * (1 - creatureDamageReduction(sequence)). Чим сильніший гравець (нижчий sequence), тим менше урону.
 */
public class CreatureDamageListener implements Listener {

    private final CreatureCodec codec;
    private final BeyonderService beyonderService;

    public CreatureDamageListener(CreatureCodec codec, BeyonderService beyonderService) {
        this.codec = codec;
        this.beyonderService = beyonderService;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Beyonder beyonder = beyonderService.getBeyonder(victim.getUniqueId());
        if (beyonder == null) return;

        if (!isCreatureSource(event.getDamager())) return;

        double reduction = SequenceScaler.creatureDamageReduction(beyonder.getSequenceLevel());
        if (reduction <= 0.0) return;
        event.setDamage(event.getDamage() * (1.0 - reduction));
    }

    /** Прямий удар істоти або снаряд, випущений істотою. */
    private boolean isCreatureSource(Entity damager) {
        if (codec.isCreature(damager)) return true;
        if (damager instanceof Projectile proj) {
            ProjectileSource shooter = proj.getShooter();
            return shooter instanceof Entity e && codec.isCreature(e);
        }
        return false;
    }
}
```

- [ ] **Step 2: Wire in ServiceContainer**

Add a field near the other creature listeners (around line 82-90 block):

```java
    private me.vangoo.presentation.listeners.CreatureDamageListener creatureDamageListener;
```

Construct it after `creatureLoadListener` (around line 204):

```java
        this.creatureDamageListener = new me.vangoo.presentation.listeners.CreatureDamageListener(
                creatureCodec, beyonderService);
```

Add a getter alongside the other creature-listener getters:

```java
    public me.vangoo.presentation.listeners.CreatureDamageListener getCreatureDamageListener() {
        return creatureDamageListener;
    }
```

- [ ] **Step 3: Register the listener**

In `MysteriesAbovePlugin.registerEvents()`, after the `getCreatureLoadListener()` registration (line ~241):

```java
        getServer().getPluginManager().registerEvents(services.getCreatureDamageListener(), this);
```

- [ ] **Step 4: Build to verify compilation**

Run: `"C:/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd" -q -DskipTests package`
Expected: BUILD SUCCESS.

- [ ] **Step 5: In-server smoke test (manual)**

As a high-sequence Beyonder (e.g. Seq 0), let a tagged creature hit you — damage should be ~45% lower than the creature's raw `stats.damage`. As a Seq 9 Beyonder, damage should be ~full. Non-Beyonder players take full damage.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/me/vangoo/presentation/listeners/CreatureDamageListener.java \
        src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java \
        src/main/java/me/vangoo/MysteriesAbovePlugin.java
git commit -m "feat(creatures): sequence-scaled damage reduction from creatures (WEAK)"
```

---

### Task 8: Lower base creature damage in `creatures.yml`

**Files:**
- Modify: `src/main/resources/creatures.yml`

**Verification:** config-only; verify by reload/reading values. No code change.

- [ ] **Step 1: Reduce every creature's `stats.damage` by ~30%**

For each creature block in `creatures.yml`, multiply `stats.damage` by ~0.7 and round to a sensible value (keep ≥ 1). Example transform:

```yaml
# before
    stats:
      health: 30
      damage: 6
      speed: 0.25
      scale: 1.2
# after
    stats:
      health: 30
      damage: 4
      speed: 0.25
      scale: 1.2
```

Apply consistently across all 28 creatures (apex creatures keep proportionally higher damage than commons, just ~30% lower than their current value). Do not touch `health`, `speed`, `scale`, loot, or spawn.

- [ ] **Step 2: Sanity-check the YAML parses**

Run: `"C:/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd" -q -DskipTests package`
Expected: BUILD SUCCESS (resource is filtered/copied without error). Optionally load on the test server (after deleting the stale `plugins/Mysteries-Above/creatures.yml`) and confirm the startup log reads `Loaded 28 creatures`.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/creatures.yml
git commit -m "balance(creatures): lower base damage ~30% (pairs with defense scaling)"
```

---

## Self-Review

**Spec coverage:**
- §1 Distance gate → Task 4 (helper + config + both listeners). ✓
- §2 Convergence (sequence field, bias VO, selector weighting, renormalization, wiring, tests) → Tasks 1, 3, 5. ✓
- §3 Aggressive from spawn → Task 6. ✓
- §4 Damage scaling defense (scaler method + listener) → Tasks 2, 7. ✓
- §5 Lower base damage → Task 8. ✓
- Deployment note (stale `creatures.yml`) → surfaced in Tasks 5/8 smoke steps. ✓

**Type consistency:**
- `CreatureDefinition(... String pathway, int sequence)` — defined Task 1, consumed Tasks 3/5. ✓
- `ConvergenceBias(String pathway, int sequenceLevel)` — defined Task 3, consumed Task 5. ✓
- `pickForBiome(...,ConvergenceBias)` — defined Task 3, called Task 5. ✓
- `creatureDamageReduction(int)` — defined Task 2, called Task 7. ✓
- `SpawnDistanceGate.isFarEnough(double,double,double)` — defined Task 4, called in both listeners. ✓
- `CreatureAggression.acquireTarget(LivingEntity,double)` — defined Task 6, called in spawner + manager. ✓
- Listener constructor arity changes (`NaturalCreatureSpawnListener`, `StructureCreatureSpawnListener`) are threaded through `ServiceContainer` in the same tasks that change them (Tasks 4, 5). ✓

**Placeholder scan:** none — every code step shows full code; manual in-server steps are explicit because the project forbids Bukkit mocking.

**Notes:**
- Pathway-name match relies on `Pathway.getName()` returning the concrete class simple name (`Error`, `Door`, `Visionary`, `Fool`, `Justiciar`, `WhiteTower`), which lowercases to the creature `pathway` strings — so `equalsIgnoreCase` matches with no extra normalization.
- `/creature spawn` bypasses the distance gate and convergence because it calls `CreatureSpawner.spawn` directly, not the listeners. No change needed.
