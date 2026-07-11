# Convergence Law (hidden fate-pull) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement roadmap channel C — the Law of Convergence — as a hidden fate-pull that drifts non-player resonant objects (dropped Characteristics, Warden remnants, same/neighbor-pathway creatures) toward the nearest resonant Beyonder within 128 blocks.

**Architecture:** A pure, unit-tested domain rule (`ConvergencePull`) picks *which* Beyonder an object gravitates to and *how strongly*; a Bukkit scheduler (`ConvergenceDriftScheduler`, mirroring `AmbientCreatureSpawner`) applies the velocity/pathfinder nudge and a rare quiet sound. Players are never force-moved.

**Tech Stack:** Java 21, Spigot/Paper 1.21 API, JUnit 5. No new dependencies.

## Global Constraints

- **Spec:** `docs/superpowers/specs/2026-07-09-ingredient-economy-convergence-design.md` (read before starting).
- **Pure-domain purity:** `me.vangoo.domain.creatures` must NOT import `org.bukkit..`, `dev.triumphteam..`, `net.kyori..`. `ArchitectureTest.pureDomainCoreHasNoBukkitOrGuiDependencies` enforces this. New domain classes use `double x/z` coords and `String` pathway/group — no Bukkit types.
- **User-facing text:** Ukrainian (there is none here except log lines, which stay English).
- **Maven is NOT on PATH** (memory). Run builds/tests with IntelliJ's bundled Maven via PowerShell:
  `& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" <args>`
- **Commit messages:** Conventional Commits, English only, imperative subject ≤72 chars, no `-` as separator. End body with `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`. Commit via `-F <file>` (memory: here-strings leave literal `@`).
- **Only non-player objects move.** Living Beyonders are magnets only, never nudged.
- **No UI:** no particles, no HUD, no chat text. Only a rare quiet non-directional sound to the drawn Beyonder.

---

## File Structure

**New (Task 1 — pure domain):**
- `src/main/java/me/vangoo/domain/creatures/ResonantBeyonder.java` — record: a candidate magnet.
- `src/main/java/me/vangoo/domain/creatures/ConvergenceSource.java` — record: an object that gravitates.
- `src/main/java/me/vangoo/domain/creatures/PullResult.java` — record: chosen target + strength.
- `src/main/java/me/vangoo/domain/creatures/ConvergencePull.java` — the rule.
- `src/test/java/me/vangoo/domain/creatures/ConvergencePullTest.java` — unit tests.

**New (Task 2 — effect):**
- `src/main/java/me/vangoo/infrastructure/schedulers/ConvergenceDriftScheduler.java` — the scheduler.

**Modified (Task 2):**
- `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java` — field, construction, start/stop, getter.
- `src/main/resources/config.yml` — `convergence:` section.

**Modified (Task 3 — docs):**
- `.claude/rules/mythic-creatures.md` — "Відомі спрощення" note.
- `CLAUDE.md` — config note.

---

## Task 1: Pure domain rule `ConvergencePull` + records

**Files:**
- Create: `src/main/java/me/vangoo/domain/creatures/ResonantBeyonder.java`
- Create: `src/main/java/me/vangoo/domain/creatures/ConvergenceSource.java`
- Create: `src/main/java/me/vangoo/domain/creatures/PullResult.java`
- Create: `src/main/java/me/vangoo/domain/creatures/ConvergencePull.java`
- Test: `src/test/java/me/vangoo/domain/creatures/ConvergencePullTest.java`

**Interfaces:**
- Consumes: nothing (leaf domain).
- Produces (Task 2 relies on these exact signatures):
  - `record ResonantBeyonder(UUID id, String pathway, String group, int sequenceLevel, double x, double z)`
  - `record ConvergenceSource(String pathway, String group, int sequence, double x, double z)`
  - `record PullResult(UUID targetId, double strength)`
  - `Optional<PullResult> ConvergencePull.computePull(ConvergenceSource source, Collection<ResonantBeyonder> beyonders, double radius)`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/me/vangoo/domain/creatures/ConvergencePullTest.java`:

```java
package me.vangoo.domain.creatures;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConvergencePullTest {

    private final ConvergencePull pull = new ConvergencePull();

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);

    @Test
    void samePathwayBeatsNeighborAtEqualDistance() {
        ConvergenceSource src = new ConvergenceSource("Fool", "LordOfMysteries", 7, 0, 0);
        ResonantBeyonder same = new ResonantBeyonder(A, "Fool", "LordOfMysteries", 9, 10, 0);
        ResonantBeyonder neighbor = new ResonantBeyonder(B, "Door", "LordOfMysteries", 9, 0, 10);
        Optional<PullResult> res = pull.computePull(src, List.of(same, neighbor), 128);
        assertTrue(res.isPresent());
        assertEquals(A, res.get().targetId());
    }

    @Test
    void nextNeededSequenceGivesStrongerStrengthThanOther() {
        // Beyonder at seq 8 needs seq 7 next.
        ConvergenceSource needed = new ConvergenceSource("Fool", "LordOfMysteries", 7, 0, 0);
        ConvergenceSource other = new ConvergenceSource("Fool", "LordOfMysteries", 3, 0, 0);
        ResonantBeyonder b = new ResonantBeyonder(A, "Fool", "LordOfMysteries", 8, 5, 0);
        double sNeeded = pull.computePull(needed, List.of(b), 128).orElseThrow().strength();
        double sOther = pull.computePull(other, List.of(b), 128).orElseThrow().strength();
        assertTrue(sNeeded > sOther);
    }

    @Test
    void outOfRadiusExcluded() {
        ConvergenceSource src = new ConvergenceSource("Fool", "LordOfMysteries", 7, 0, 0);
        ResonantBeyonder far = new ResonantBeyonder(A, "Fool", "LordOfMysteries", 9, 200, 0);
        assertTrue(pull.computePull(src, List.of(far), 128).isEmpty());
    }

    @Test
    void noResonanceReturnsEmpty() {
        ConvergenceSource src = new ConvergenceSource("Fool", "LordOfMysteries", 7, 0, 0);
        ResonantBeyonder foreign = new ResonantBeyonder(A, "Visionary", "GodAlmighty", 9, 5, 0);
        assertTrue(pull.computePull(src, List.of(foreign), 128).isEmpty());
    }

    @Test
    void neighborResonatesForeignGroupDoesNot() {
        ConvergenceSource src = new ConvergenceSource("Fool", "LordOfMysteries", 7, 0, 0);
        ResonantBeyonder neighbor = new ResonantBeyonder(A, "Error", "LordOfMysteries", 9, 5, 0);
        ResonantBeyonder foreign = new ResonantBeyonder(B, "Justiciar", "TheAnarchy", 9, 1, 0);
        Optional<PullResult> res = pull.computePull(src, List.of(neighbor, foreign), 128);
        assertTrue(res.isPresent());
        assertEquals(A, res.get().targetId());
    }

    @Test
    void nearestWinsAmongEqualResonance() {
        ConvergenceSource src = new ConvergenceSource("Fool", "LordOfMysteries", 9, 0, 0);
        ResonantBeyonder near = new ResonantBeyonder(A, "Fool", "LordOfMysteries", 9, 3, 0);
        ResonantBeyonder far = new ResonantBeyonder(B, "Fool", "LordOfMysteries", 9, 20, 0);
        assertEquals(A, pull.computePull(src, List.of(far, near), 128).orElseThrow().targetId());
    }

    @Test
    void maxStrengthIsOneForSamePathwayNextNeeded() {
        ConvergenceSource src = new ConvergenceSource("Fool", "LordOfMysteries", 7, 0, 0);
        ResonantBeyonder b = new ResonantBeyonder(A, "Fool", "LordOfMysteries", 8, 5, 0);
        double strength = pull.computePull(src, List.of(b), 128).orElseThrow().strength();
        assertEquals(1.0, strength, 1e-9);
        assertTrue(strength > 0.0 && strength <= 1.0);
    }

    @Test
    void emptyCandidatesReturnsEmpty() {
        ConvergenceSource src = new ConvergenceSource("Fool", "LordOfMysteries", 7, 0, 0);
        assertTrue(pull.computePull(src, List.of(), 128).isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:
```
& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" test -Dtest=ConvergencePullTest
```
Expected: FAIL — compilation error, `ConvergencePull`/records do not exist.

- [ ] **Step 3: Create the three records**

Create `src/main/java/me/vangoo/domain/creatures/ResonantBeyonder.java`:

```java
package me.vangoo.domain.creatures;

import java.util.UUID;

/**
 * Кандидат-магніт Закону Конвергенції: онлайн-Beyonder, до якого може тяжіти резонансне джерело.
 *
 * @param id            UUID гравця
 * @param pathway       назва шляху (порівняння без регістру)
 * @param group         назва PathwayGroup (для резонансу «сусід»)
 * @param sequenceLevel поточний рівень послідовності (9 = найслабший, 0 = найсильніший)
 * @param x,z           горизонтальні координати
 */
public record ResonantBeyonder(UUID id, String pathway, String group, int sequenceLevel,
                               double x, double z) {}
```

Create `src/main/java/me/vangoo/domain/creatures/ConvergenceSource.java`:

```java
package me.vangoo.domain.creatures;

/**
 * Резонансне джерело, що тяжіє до найближчого кревного Beyonder'а: впала Характеристика, рештка
 * або міфічна істота. Ключоване шляхом+послідовністю.
 *
 * @param pathway  назва шляху джерела (порівняння без регістру)
 * @param group    назва PathwayGroup джерела
 * @param sequence послідовність джерела
 * @param x,z      горизонтальні координати
 */
public record ConvergenceSource(String pathway, String group, int sequence, double x, double z) {}
```

Create `src/main/java/me/vangoo/domain/creatures/PullResult.java`:

```java
package me.vangoo.domain.creatures;

import java.util.UUID;

/**
 * Результат правила тяжіння: до кого тяжіє джерело і наскільки сильно.
 *
 * @param targetId UUID обраного магніта
 * @param strength сила нуджа ∈ (0,1]
 */
public record PullResult(UUID targetId, double strength) {}
```

- [ ] **Step 4: Implement the rule**

Create `src/main/java/me/vangoo/domain/creatures/ConvergencePull.java`:

```java
package me.vangoo.domain.creatures;

import java.util.Collection;
import java.util.Optional;

/**
 * Чисте правило Закону Конвергенції (аналог CreatureSelector). Обирає, до якого резонансного
 * Beyonder'а тяжіє джерело, і з якою силою. Детерміноване, без Bukkit, без стану.
 *
 * <p>Резонанс: той самий шлях (сильний) або та сама PathwayGroup (сусід). Магніт обирається за
 * вагою {@code resonanceWeight × seqMultiplier / (1 + distance)} (найбільша вага; тайбрейк —
 * найближчий, далі — менший UUID). Сила нуджа {@code strength = resonanceWeight × seqMultiplier /
 * MAX_SCORE} ∈ (0,1] і НЕ залежить від відстані — потрібна есенція тяжіє однаково відчутно на
 * всьому радіусі.
 */
public final class ConvergencePull {

    private static final double SAME_PATHWAY_WEIGHT = 2.0;
    private static final double NEIGHBOR_WEIGHT = 1.0;
    private static final double NEXT_NEEDED_MULT = 4.0; // source.sequence == beyonder.seq - 1
    private static final double CURRENT_MULT = 2.0;     // source.sequence == beyonder.seq
    private static final double MAX_SCORE = SAME_PATHWAY_WEIGHT * NEXT_NEEDED_MULT; // 8.0

    public Optional<PullResult> computePull(ConvergenceSource source,
                                            Collection<ResonantBeyonder> beyonders,
                                            double radius) {
        if (source == null || beyonders == null || beyonders.isEmpty()) return Optional.empty();
        double r2 = radius * radius;

        ResonantBeyonder best = null;
        double bestWeight = -1.0;
        double bestDistSq = Double.MAX_VALUE;
        double bestScore = 0.0;

        for (ResonantBeyonder b : beyonders) {
            double resonance = resonanceWeight(source, b);
            if (resonance <= 0.0) continue;

            double dx = b.x() - source.x();
            double dz = b.z() - source.z();
            double distSq = dx * dx + dz * dz;
            if (distSq > r2) continue;

            double score = resonance * seqMultiplier(source, b);
            double weight = score / (1.0 + Math.sqrt(distSq));

            boolean better = weight > bestWeight
                    || (weight == bestWeight && distSq < bestDistSq)
                    || (weight == bestWeight && distSq == bestDistSq
                        && (best == null || b.id().compareTo(best.id()) < 0));
            if (better) {
                best = b;
                bestWeight = weight;
                bestDistSq = distSq;
                bestScore = score;
            }
        }

        if (best == null) return Optional.empty();
        return Optional.of(new PullResult(best.id(), bestScore / MAX_SCORE));
    }

    private double resonanceWeight(ConvergenceSource s, ResonantBeyonder b) {
        if (s.pathway() == null || b.pathway() == null) return 0.0;
        if (s.pathway().equalsIgnoreCase(b.pathway())) return SAME_PATHWAY_WEIGHT;
        if (s.group() != null && b.group() != null && s.group().equalsIgnoreCase(b.group())) {
            return NEIGHBOR_WEIGHT;
        }
        return 0.0;
    }

    private double seqMultiplier(ConvergenceSource s, ResonantBeyonder b) {
        if (s.sequence() == b.sequenceLevel() - 1) return NEXT_NEEDED_MULT;
        if (s.sequence() == b.sequenceLevel()) return CURRENT_MULT;
        return 1.0;
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run:
```
& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" test -Dtest=ConvergencePullTest
```
Expected: PASS — all 8 tests green.

- [ ] **Step 6: Run ArchitectureTest to confirm domain purity**

Run:
```
& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" test -Dtest=ArchitectureTest
```
Expected: PASS — new classes in `domain.creatures` import no Bukkit.

- [ ] **Step 7: Commit**

Write the message to `commit-msg.txt`:
```
feat(creatures): pure ConvergencePull rule for fate-pull magnet selection

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```
Then:
```
git add src/main/java/me/vangoo/domain/creatures/ResonantBeyonder.java src/main/java/me/vangoo/domain/creatures/ConvergenceSource.java src/main/java/me/vangoo/domain/creatures/PullResult.java src/main/java/me/vangoo/domain/creatures/ConvergencePull.java src/test/java/me/vangoo/domain/creatures/ConvergencePullTest.java
git commit -F commit-msg.txt
rm commit-msg.txt
```

---

## Task 2: `ConvergenceDriftScheduler` + config + wiring (in-server deliverable)

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/schedulers/ConvergenceDriftScheduler.java`
- Modify: `src/main/resources/config.yml` (append `convergence:` under root)
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java` (field ~line 76-area, construction in `initializeSchedulers` ~line 244, `startSchedulers` ~line 358, `stopSchedulers` ~line 382, getter ~line 333)

**Interfaces:**
- Consumes (from Task 1): `ConvergencePull.computePull(ConvergenceSource, Collection<ResonantBeyonder>, double)`, records `ConvergenceSource`, `ResonantBeyonder`, `PullResult`.
- Consumes (existing codebase, verified signatures):
  - `BeyonderService.getBeyonder(UUID) -> Beyonder` (null if none)
  - `Beyonder.getPathway().getName() -> String`, `Beyonder.getSequenceLevel() -> int`
  - `PathwayManager.getPathway(String) -> Pathway` (may return null); `Pathway.getGroup() -> PathwayGroup`; `PathwayGroup.name() -> String`
  - `MythicCreatureGateway.creatureId(Entity) -> Optional<String>`
  - `Map<String,CreatureDefinition> registry`; `CreatureDefinition.pathway() -> String`, `.sequence() -> int`
  - `CharacteristicCodec.read(ItemStack) -> Optional<Characteristic>`; `Characteristic.pathwayName() -> String`, `.sequence() -> int`
  - `WardenRemnantCodec.isRemnant(Entity) -> boolean`, `.read(Entity) -> Optional<Characteristic>`
- Produces: `ServiceContainer.getConvergenceDriftScheduler() -> ConvergenceDriftScheduler` with `start()` / `stop()`.

- [ ] **Step 1: Add the config section**

Append to `src/main/resources/config.yml` (after the existing `creatures:` block, at root level):

```yaml

# Закон Конвергенції: приховане тяжіння резонансних об'єктів (впалих Характеристик, решток,
# істот твого/сусіднього шляху) до найближчого кревного потойбічного. Гравців не рухає; єдиний
# прояв — вкрай рідкісний тихий ненапрямний звук.
convergence:
  interval-ticks: 200      # як часто тікає доля-поле (10 с)
  radius: 128              # радіус виявлення (блоки)
  item-drift-speed: 0.08   # базовий горизонтальний velocity-нудж есенції-предмета
  mob-nudge-chance: 0.5    # базова P(pathfind до магніта) при strength = 1
  whisper-chance: 0.03     # рідкісний тихий ненапрямний звук притягнутому Beyonder'у
```

- [ ] **Step 2: Write the scheduler**

Create `src/main/java/me/vangoo/infrastructure/schedulers/ConvergenceDriftScheduler.java`:

```java
package me.vangoo.infrastructure.schedulers;

import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.application.services.BeyonderService;
import me.vangoo.application.services.PathwayManager;
import me.vangoo.domain.brewing.Characteristic;
import me.vangoo.domain.creatures.ConvergencePull;
import me.vangoo.domain.creatures.ConvergenceSource;
import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.domain.creatures.PullResult;
import me.vangoo.domain.creatures.ResonantBeyonder;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.infrastructure.items.CharacteristicCodec;
import me.vangoo.infrastructure.items.WardenRemnantCodec;
import me.vangoo.infrastructure.mythic.MythicCreatureGateway;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Закон Конвергенції як приховане тяжіння. Періодично, для кожного онлайн-потойбічного, збирає
 * резонансні НЕ-гравцеві джерела в радіусі (впалі Характеристики, рештки, міфічні істоти) і дає
 * кожному дуже слабкий нудж до найближчого кревного Beyonder'а. Кого й наскільки сильно вирішує
 * чисте {@link ConvergencePull}. Гравців ніколи не рухаємо; єдиний прояв — рідкісний тихий звук.
 * Дзеркалить життєвий цикл {@link AmbientCreatureSpawner} (start/stop + один BukkitTask).
 */
public final class ConvergenceDriftScheduler {

    private final MysteriesAbovePlugin plugin;
    private final BeyonderService beyonderService;
    private final PathwayManager pathwayManager;
    private final MythicCreatureGateway gateway;
    private final java.util.Map<String, CreatureDefinition> registry;
    private final CharacteristicCodec characteristicCodec;
    private final WardenRemnantCodec wardenRemnantCodec;
    private final ConvergencePull pull = new ConvergencePull();

    private final long intervalTicks;
    private final double radius;
    private final double itemDriftSpeed;
    private final double mobNudgeChance;
    private final double whisperChance;
    private final Random random = new Random();

    private BukkitTask task;

    public ConvergenceDriftScheduler(MysteriesAbovePlugin plugin, BeyonderService beyonderService,
                                     PathwayManager pathwayManager, MythicCreatureGateway gateway,
                                     java.util.Map<String, CreatureDefinition> registry,
                                     CharacteristicCodec characteristicCodec,
                                     WardenRemnantCodec wardenRemnantCodec,
                                     long intervalTicks, double radius, double itemDriftSpeed,
                                     double mobNudgeChance, double whisperChance) {
        this.plugin = plugin;
        this.beyonderService = beyonderService;
        this.pathwayManager = pathwayManager;
        this.gateway = gateway;
        this.registry = registry;
        this.characteristicCodec = characteristicCodec;
        this.wardenRemnantCodec = wardenRemnantCodec;
        this.intervalTicks = Math.max(20L, intervalTicks);
        this.radius = radius;
        this.itemDriftSpeed = itemDriftSpeed;
        this.mobNudgeChance = mobNudgeChance;
        this.whisperChance = whisperChance;
    }

    public void start() {
        if (task != null && !task.isCancelled()) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, intervalTicks, intervalTicks);
        plugin.getLogger().info("ConvergenceDriftScheduler started");
    }

    public void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
            task = null;
            plugin.getLogger().info("ConvergenceDriftScheduler stopped");
        }
    }

    private void tick() {
        List<ResonantBeyonder> magnets = collectMagnets();
        if (magnets.isEmpty()) return;

        Set<UUID> handled = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                driftAround(player, magnets, handled);
            } catch (Exception e) {
                plugin.getLogger().warning("Convergence drift error for " + player.getName() + ": " + e);
            }
        }
    }

    /** Усі онлайн-Beyonder'и як кандидати-магніти. */
    private List<ResonantBeyonder> collectMagnets() {
        List<ResonantBeyonder> magnets = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Beyonder b = beyonderService.getBeyonder(player.getUniqueId());
            if (b == null) continue;
            String pathway = b.getPathway().getName();
            String group = groupOf(pathway);
            if (group == null) continue;
            Location loc = player.getLocation();
            magnets.add(new ResonantBeyonder(player.getUniqueId(), pathway, group,
                    b.getSequenceLevel(), loc.getX(), loc.getZ()));
        }
        return magnets;
    }

    private void driftAround(Player center, List<ResonantBeyonder> magnets, Set<UUID> handled) {
        for (Entity e : center.getNearbyEntities(radius, radius, radius)) {
            if (!handled.add(e.getUniqueId())) continue; // dedup across scan centers

            Optional<ConvergenceSource> source = describeSource(e);
            if (source.isEmpty()) continue;

            Optional<PullResult> result = pull.computePull(source.get(), magnets, radius);
            if (result.isEmpty()) continue;

            Player target = Bukkit.getPlayer(result.get().targetId());
            if (target == null) continue;

            applyNudge(e, target.getLocation(), result.get().strength());
            maybeWhisper(target);
        }
    }

    /** Розпізнає резонансне джерело з сутності; empty якщо не джерело або невідомий шлях. */
    private Optional<ConvergenceSource> describeSource(Entity e) {
        Characteristic c = null;
        if (e instanceof Item item) {
            c = characteristicCodec.read(item.getItemStack()).orElse(null);
        } else if (wardenRemnantCodec.isRemnant(e)) {
            c = wardenRemnantCodec.read(e).orElse(null);
        } else {
            String id = gateway.creatureId(e).orElse(null);
            if (id != null) {
                CreatureDefinition def = registry.get(id);
                if (def != null && def.pathway() != null) {
                    c = new Characteristic(def.pathway(), def.sequence());
                }
            }
        }
        if (c == null || c.pathwayName() == null) return Optional.empty();
        String group = groupOf(c.pathwayName());
        if (group == null) return Optional.empty();
        Location loc = e.getLocation();
        return Optional.of(new ConvergenceSource(c.pathwayName(), group, c.sequence(),
                loc.getX(), loc.getZ()));
    }

    private void applyNudge(Entity e, Location targetLoc, double strength) {
        Vector dir = targetLoc.toVector().subtract(e.getLocation().toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 1.0e-6) return;
        dir.normalize();

        if (e instanceof Item item) {
            Vector v = item.getVelocity().add(dir.multiply(itemDriftSpeed * strength));
            item.setVelocity(v);
        } else if (e instanceof Mob mob) {
            if (random.nextDouble() < mobNudgeChance * strength) {
                mob.getPathfinder().moveTo(targetLoc);
            }
        }
    }

    private void maybeWhisper(Player target) {
        if (random.nextDouble() >= whisperChance) return;
        target.playSound(target.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                SoundCategory.AMBIENT, 0.15f, 1.4f);
    }

    private String groupOf(String pathwayName) {
        Pathway p = pathwayManager.getPathway(pathwayName);
        return p == null ? null : p.getGroup().name();
    }
}
```

- [ ] **Step 3: Wire the field + construction in `ServiceContainer`**

In `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java`, add the field near the other scheduler field (around line 76, next to `ambientCreatureSpawner`):

```java
    private me.vangoo.infrastructure.schedulers.ConvergenceDriftScheduler convergenceDriftScheduler;
```

At the end of `initializeSchedulers()` (right after the `ambientCreatureSpawner` construction block, before the method's closing brace), add:

```java
        long convInterval = plugin.getConfig().getLong("convergence.interval-ticks", 200L);
        double convRadius = plugin.getConfig().getDouble("convergence.radius", 128.0);
        double convDrift = plugin.getConfig().getDouble("convergence.item-drift-speed", 0.08);
        double convMobNudge = plugin.getConfig().getDouble("convergence.mob-nudge-chance", 0.5);
        double convWhisper = plugin.getConfig().getDouble("convergence.whisper-chance", 0.03);
        this.convergenceDriftScheduler = new me.vangoo.infrastructure.schedulers.ConvergenceDriftScheduler(
                (MysteriesAbovePlugin) plugin, beyonderService, pathwayManager, mythicCreatureGateway,
                creatureRegistry, characteristicCodec, wardenRemnantCodec,
                convInterval, convRadius, convDrift, convMobNudge, convWhisper);
```

- [ ] **Step 4: Wire start/stop + getter in `ServiceContainer`**

In `startSchedulers()` (after `ambientCreatureSpawner.start();`, around line 363) add:

```java
        convergenceDriftScheduler.start();
```

In `stopSchedulers()` (after the `ambientCreatureSpawner` stop block, around line 397) add:

```java
        if (convergenceDriftScheduler != null) {
            convergenceDriftScheduler.stop();
        }
```

Add a getter near the other scheduler getters (around line 333):

```java
    public me.vangoo.infrastructure.schedulers.ConvergenceDriftScheduler getConvergenceDriftScheduler() { return convergenceDriftScheduler; }
```

- [ ] **Step 5: Build to verify compilation and packaging**

Run:
```
& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" clean package
```
Expected: BUILD SUCCESS — shaded JAR produced under `target/`, all tests (incl. `ConvergencePullTest`, `ArchitectureTest`) green.

- [ ] **Step 6: Commit**

Write `commit-msg.txt`:
```
feat(creatures): hidden convergence drift scheduler pulls resonant objects

Non-player resonant objects (dropped Characteristics, Warden remnants,
same/neighbor-pathway creatures) drift toward the nearest resonant
Beyonder within 128 blocks. Players are never moved; the only cue is a
rare quiet non-directional sound.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```
Then:
```
git add src/main/java/me/vangoo/infrastructure/schedulers/ConvergenceDriftScheduler.java src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java src/main/resources/config.yml
git commit -F commit-msg.txt
rm commit-msg.txt
```

- [ ] **Step 7: In-server verification (manual, per project convention)**

Deploy the JAR; on the server:
1. `/pathway` — give yourself Fool at some sequence. Drop a `Характеристика[Fool, <seq>]` (`/characteristic give`) ~30 blocks away → within ~10 s it drifts slowly toward you.
2. Spawn a Fool creature nearby (`/mm mobs spawn <id>`) → it occasionally wanders toward you (not teleport-aggressive).
3. Drop a Characteristic of a foreign pathway AND foreign group (e.g. Justiciar) → it stays put.
4. A neighbor-pathway essence (same group, e.g. Error/Door while you are Fool) → drifts, but weaker than your own.
5. Occasionally a faint non-directional chime; confirm NO particles / HUD / chat.

---

## Task 3: Documentation updates

**Files:**
- Modify: `.claude/rules/mythic-creatures.md` (section "Відомі спрощення")
- Modify: `CLAUDE.md` (Config & persistence section)

**Interfaces:** none (docs only).

- [ ] **Step 1: Update `.claude/rules/mythic-creatures.md`**

In the "## Відомі спрощення" list, add a new bullet (after the ambient-spawn bullet):

```markdown
- Закон Конвергенції (канал C) — тепер **приховане тяжіння**, не компас: резонансні НЕ-гравцеві
  об'єкти (впалі Характеристики, рештки, істоти твого/сусіднього `PathwayGroup`) дуже слабко
  дрейфують до найближчого кревного Beyonder'а в радіусі 128 бл (`ConvergencePull` +
  `ConvergenceDriftScheduler`). Гравців не рухаємо; єдиний прояв — рідкісний тихий ненапрямний звук.
  Магніт і сила — чистий `domain.creatures.ConvergencePull` (юніт-тест); нудж/звук — планувальник.
```

- [ ] **Step 2: Update `CLAUDE.md`**

In the "## Config & persistence" section, in the sentence listing `config.yml`, note the new `convergence` keys. Change the `config.yml` mention so it reads (find the `config.yml` reference and append):

```markdown
`config.yml` (в т.ч. ключі `creatures.*` та `convergence.*`)
```

If no such parenthetical exists, add a short sentence after the resources bullet:

```markdown
- `config.yml` містить секції `creatures.*` (спавн істот) і `convergence.*` (приховане тяжіння Закону Конвергенції); обидві читає `ServiceContainer` через `plugin.getConfig()`.
```

- [ ] **Step 3: Commit**

Write `commit-msg.txt`:
```
docs(creatures): document hidden convergence drift mechanism

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```
Then:
```
git add .claude/rules/mythic-creatures.md CLAUDE.md
git commit -F commit-msg.txt
rm commit-msg.txt
```

---

## Self-Review Notes

- **Spec coverage:** §1 rule → Task 1; §2 scheduler → Task 2 (Steps 2-4); §3 config → Task 2 (Step 1); §4 wiring → Task 2 (Steps 3-4); §5 testing (unit) → Task 1; §5 testing (in-server) → Task 2 Step 7; doc updates → Task 3.
- **Only non-player objects move:** `applyNudge` handles only `Item` and `Mob`; players (also entities) are never passed as sources — `describeSource` never matches a `Player`.
- **Dedup:** `handled` set is per-tick, shared across scan centers (Task 2 Step 2, `driftAround`).
- **Type consistency:** record accessors (`pathway()`, `group()`, `sequence()`, `sequenceLevel()`, `x()`, `z()`, `targetId()`, `strength()`) match between Task 1 definitions and Task 2 usage; `Characteristic.pathwayName()`/`sequence()` verified against source.
