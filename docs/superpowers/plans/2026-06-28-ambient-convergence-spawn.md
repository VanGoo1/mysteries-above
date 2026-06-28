# Ambient Convergence Spawn Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make mythical creatures also spawn on their own (additively, replacing nobody) near far-out Beyonders, driven by the Law of Convergence, at a very small chance — and cut the existing natural-replacement chance to ~1/4.

**Architecture:** A new pure selector method `pickForAmbient` chooses a creature of the Beyonder's pathway+biome weighted by convergence (no spawn-gate — the scheduler owns that). A new `AmbientCreatureSpawner` scheduler (mirrors `PassiveAbilityScheduler`) ticks every 60 s, gates on distance + small chance + a nearby-creature cap, finds a safe surface spot 24–48 blocks away via `AmbientSpawnLocation`, and spawns through the existing `CreatureSpawner`. Config-driven; `creatures.yml` natural chances scaled ×0.25.

**Tech Stack:** Java 21, Spigot/Bukkit API 1.21.1, JUnit 5, Maven (shade). Build via IntelliJ-bundled Maven (`mvn` not on PATH, exits 127): `"C:/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd"`.

## Global Constraints

- Java 21, Bukkit API 1.21.1. New attribute/effect names (no `GENERIC_`).
- `me.vangoo.domain.*` pure core (`creatures`, `services`, `valueobjects`) must NOT import `org.bukkit.*`. ArchUnit pins this. `CreatureSelector` and `ConvergenceBias` are pure domain.
- Ambient spawns are **additive** (no `setCancelled`, no vanilla mob involved) and **convergence-only** (require a Beyonder with pathway+sequence; non-Beyonder never triggers one).
- Ambient defaults: interval 60 s, chance 0.022 (~1 spawn / 45 min per eligible Beyonder), max-nearby 3, distance band 24–48 blocks, surface only. All of interval/chance/max-nearby are config-driven under `creatures.ambient.*`; distance gate reuses `creatures.min-spawn-distance` (default 2000).
- Cut existing `spawn.natural.chance` in `creatures.yml` by ×0.25 (keep > 0).
- Unit-test the pure piece (`pickForAmbient`); the scheduler + location finder are Bukkit and verified in-server (no headless test), per project convention.
- User-facing strings Ukrainian where applicable. Commit after each task. Branch: `feat/hunting-creatures` (already checked out).

---

### Task 1: `CreatureSelector.pickForAmbient`

**Files:**
- Modify: `src/main/java/me/vangoo/domain/creatures/CreatureSelector.java`
- Test: `src/test/java/me/vangoo/domain/creatures/CreatureSelectorTest.java`

**Interfaces:**
- Consumes: `ConvergenceBias(String pathway, int sequenceLevel)`, the existing private `multiplier(CreatureDefinition, ConvergenceBias)`, `CreatureDefinition.sequence()/pathway()/spawn()`.
- Produces: `Optional<CreatureDefinition> CreatureSelector.pickForAmbient(String biome, ConvergenceBias bias, double roll)`.
- Consumed by: Task 3 (`AmbientCreatureSpawner`).

- [ ] **Step 1: Write the failing tests**

Add to `CreatureSelectorTest.java`:

```java
    @Test
    void ambientPicksPathwayAndBiomeMatch() {
        CreatureDefinition a = def("a", natural(0.2), "visionary", 9);
        CreatureSelector s = new CreatureSelector(List.of(a));
        ConvergenceBias bias = new ConvergenceBias("Visionary", 9);
        assertEquals("a", s.pickForAmbient("OCEAN", bias, 0.5).get().id());
    }

    @Test
    void ambientEmptyWhenPathwayMismatch() {
        CreatureDefinition a = def("a", natural(0.2), "visionary", 9);
        CreatureSelector s = new CreatureSelector(List.of(a));
        ConvergenceBias bias = new ConvergenceBias("Error", 5);
        assertTrue(s.pickForAmbient("OCEAN", bias, 0.5).isEmpty());
    }

    @Test
    void ambientEmptyWhenBiomeMismatch() {
        CreatureDefinition a = def("a", natural(0.2), "visionary", 9);
        CreatureSelector s = new CreatureSelector(List.of(a));
        ConvergenceBias bias = new ConvergenceBias("Visionary", 9);
        assertTrue(s.pickForAmbient("PLAINS", bias, 0.5).isEmpty());
    }

    @Test
    void ambientNullBiasEmpty() {
        CreatureDefinition a = def("a", natural(0.2), "visionary", 9);
        CreatureSelector s = new CreatureSelector(List.of(a));
        assertTrue(s.pickForAmbient("OCEAN", null, 0.5).isEmpty());
    }

    @Test
    void ambientFavorsNextNeededSequence() {
        CreatureDefinition a = def("a", natural(0.2), "visionary", 9); // current -> x2
        CreatureDefinition b = def("b", natural(0.2), "visionary", 8); // next-needed -> x4
        CreatureSelector s = new CreatureSelector(List.of(a, b));
        ConvergenceBias bias = new ConvergenceBias("Visionary", 9);
        // weights a=0.4, b=0.8, sum=1.2; target = roll*1.2; a in [0,0.4), b in [0.4,1.2)
        assertEquals("a", s.pickForAmbient("OCEAN", bias, 0.1).get().id());
        assertEquals("b", s.pickForAmbient("OCEAN", bias, 0.5).get().id());
        assertEquals("b", s.pickForAmbient("OCEAN", bias, 0.99).get().id());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `"C:/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd" -q test -Dtest=CreatureSelectorTest`
Expected: FAIL — `pickForAmbient` not defined.

- [ ] **Step 3: Implement `pickForAmbient`**

Add to `CreatureSelector.java` (after `pickForBiome`, reusing the existing private `multiplier`):

```java
    /**
     * Вибір істоти для ambient-спавну (закон конвергенції): серед істот ШЛЯХУ гравця, чий
     * natural-біом збігається з поточним. Без «порогу неспавну» — рішення «спавнити» вже
     * прийняв планувальник; цей метод лише обирає, КОГО. {@code roll} у [0,1).
     */
    public Optional<CreatureDefinition> pickForAmbient(String biome, ConvergenceBias bias, double roll) {
        if (bias == null) return Optional.empty();
        List<CreatureDefinition> candidates = new ArrayList<>();
        double[] weights;
        double sumWeights = 0.0;
        for (CreatureDefinition def : creatures) {
            SpawnRule s = def.spawn();
            if (s.naturalChance() <= 0.0) continue;
            if (!s.naturalBiomes().contains(biome)) continue;
            if (def.pathway() == null || !def.pathway().equalsIgnoreCase(bias.pathway())) continue;
            candidates.add(def);
        }
        if (candidates.isEmpty()) return Optional.empty();

        weights = new double[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            weights[i] = candidates.get(i).spawn().naturalChance() * multiplier(candidates.get(i), bias);
            sumWeights += weights[i];
        }
        if (sumWeights <= 0.0) return Optional.of(candidates.get(0));

        double target = roll * sumWeights;
        double cumulative = 0.0;
        for (int i = 0; i < candidates.size(); i++) {
            cumulative += weights[i];
            if (target < cumulative) return Optional.of(candidates.get(i));
        }
        return Optional.of(candidates.get(candidates.size() - 1));
    }
```

(`java.util.ArrayList` and `SpawnRule` are already imported from the earlier convergence work.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `"C:/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd" -q test -Dtest=CreatureSelectorTest`
Expected: PASS (13 prior + 5 new = 18 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/vangoo/domain/creatures/CreatureSelector.java \
        src/test/java/me/vangoo/domain/creatures/CreatureSelectorTest.java
git commit -m "feat(creatures): pickForAmbient — convergence pick by pathway+biome, no spawn gate"
```

---

### Task 2: `AmbientSpawnLocation` surface finder

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/creatures/AmbientSpawnLocation.java`

**Interfaces:**
- Produces: `static Optional<Location> AmbientSpawnLocation.findSurfaceNear(Location center, double minR, double maxR)`.
- Consumed by: Task 3.

**Verification:** Bukkit-only (uses `World.getHighestBlockAt`); compile-verified here, behaviour verified in-server. No headless test.

- [ ] **Step 1: Create the finder**

`src/main/java/me/vangoo/infrastructure/creatures/AmbientSpawnLocation.java`:

```java
package me.vangoo.infrastructure.creatures;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Шукає безпечну ПОВЕРХНЕВУ точку на відстані [minR, maxR] від центру для ambient-спавну істоти:
 * тверда (не лава) земля + 2 блоки прохідного простору над нею. Кілька випадкових спроб; якщо
 * жодна не підходить — порожньо (планувальник просто пропускає тік).
 */
public final class AmbientSpawnLocation {

    private static final int ATTEMPTS = 8;

    private AmbientSpawnLocation() {}

    public static Optional<Location> findSurfaceNear(Location center, double minR, double maxR) {
        World world = center.getWorld();
        if (world == null) return Optional.empty();

        for (int i = 0; i < ATTEMPTS; i++) {
            double angle = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2.0);
            double r = minR + ThreadLocalRandom.current().nextDouble() * (maxR - minR);
            int x = (int) Math.floor(center.getX() + Math.cos(angle) * r);
            int z = (int) Math.floor(center.getZ() + Math.sin(angle) * r);

            Block ground = world.getHighestBlockAt(x, z);
            if (!ground.getType().isSolid() || ground.getType() == Material.LAVA) continue;

            Location loc = ground.getLocation().add(0.5, 1.0, 0.5);
            if (loc.getBlock().isPassable() && loc.clone().add(0, 1, 0).getBlock().isPassable()) {
                return Optional.of(loc);
            }
        }
        return Optional.empty();
    }
}
```

- [ ] **Step 2: Compile to verify**

Run: `"C:/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd" -q -DskipTests package`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/creatures/AmbientSpawnLocation.java
git commit -m "feat(creatures): AmbientSpawnLocation — safe surface spot finder for ambient spawns"
```

---

### Task 3: `AmbientCreatureSpawner` scheduler + config + wiring

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/schedulers/AmbientCreatureSpawner.java`
- Modify: `src/main/resources/config.yml`
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java` (field + `initializeSchedulers` + getter + `startSchedulers` + `stopSchedulers`)

**Interfaces:**
- Consumes: `CreatureSelector.pickForAmbient(String, ConvergenceBias, double)` (Task 1), `AmbientSpawnLocation.findSurfaceNear(Location, double, double)` (Task 2), `SpawnDistanceGate.isFarEnough(double, double, double)`, `CreatureSpawner.spawn(CreatureDefinition, Location)`, `CreatureCodec.isCreature(Entity)`, `BeyonderService.getBeyonder(UUID)`, `Beyonder.getPathway().getName()` + `getSequenceLevel()`, `ConvergenceBias(String, int)`.

**Verification:** Bukkit-only; compile-verified + existing suite green; behaviour verified in-server. No headless test.

- [ ] **Step 1: Create the scheduler**

`src/main/java/me/vangoo/infrastructure/schedulers/AmbientCreatureSpawner.java`:

```java
package me.vangoo.infrastructure.schedulers;

import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.creatures.ConvergenceBias;
import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.domain.creatures.CreatureSelector;
import me.vangoo.domain.creatures.SpawnDistanceGate;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.infrastructure.creatures.AmbientSpawnLocation;
import me.vangoo.infrastructure.creatures.CreatureCodec;
import me.vangoo.infrastructure.creatures.CreatureSpawner;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Ambient-спавн міфічних істот: періодично, для кожного потойбічного далеко від спавну світу, з
 * малим шансом підкидає поряд істоту ЙОГО шляху+послідовності (закон конвергенції) — нікого не
 * заміняючи. Дзеркалить життєвий цикл інших планувальників (start/stop + один BukkitTask).
 */
public final class AmbientCreatureSpawner {

    private static final double SPAWN_MIN_R = 24.0;
    private static final double SPAWN_MAX_R = 48.0;
    private static final double NEARBY_RADIUS = 64.0;

    private final MysteriesAbovePlugin plugin;
    private final BeyonderService beyonderService;
    private final CreatureSelector selector;
    private final CreatureSpawner spawner;
    private final CreatureCodec codec;
    private final double minSpawnDistance;
    private final long intervalTicks;
    private final double chance;
    private final int maxNearby;
    private final Random random = new Random();

    private BukkitTask task;

    public AmbientCreatureSpawner(MysteriesAbovePlugin plugin, BeyonderService beyonderService,
                                  CreatureSelector selector, CreatureSpawner spawner, CreatureCodec codec,
                                  double minSpawnDistance, long intervalSeconds, double chance, int maxNearby) {
        this.plugin = plugin;
        this.beyonderService = beyonderService;
        this.selector = selector;
        this.spawner = spawner;
        this.codec = codec;
        this.minSpawnDistance = minSpawnDistance;
        this.intervalTicks = Math.max(20L, intervalSeconds * 20L);
        this.chance = chance;
        this.maxNearby = maxNearby;
    }

    public void start() {
        if (task != null && !task.isCancelled()) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, intervalTicks, intervalTicks);
        plugin.getLogger().info("AmbientCreatureSpawner started");
    }

    public void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
            task = null;
        }
        plugin.getLogger().info("AmbientCreatureSpawner stopped");
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                trySpawnFor(player);
            } catch (Exception e) {
                plugin.getLogger().warning("Ambient spawn error for " + player.getName() + ": " + e.getMessage());
            }
        }
    }

    private void trySpawnFor(Player player) {
        UUID id = player.getUniqueId();
        Beyonder beyonder = beyonderService.getBeyonder(id);
        if (beyonder == null) return;

        Location loc = player.getLocation();
        if (loc.getWorld() == null) return;
        Location ws = loc.getWorld().getSpawnLocation();
        if (!SpawnDistanceGate.isFarEnough(loc.getX() - ws.getX(), loc.getZ() - ws.getZ(), minSpawnDistance)) return;

        if (random.nextDouble() >= chance) return;

        int near = 0;
        for (Entity e : player.getNearbyEntities(NEARBY_RADIUS, NEARBY_RADIUS, NEARBY_RADIUS)) {
            if (codec.isCreature(e)) near++;
        }
        if (near >= maxNearby) return;

        ConvergenceBias bias = new ConvergenceBias(beyonder.getPathway().getName(), beyonder.getSequenceLevel());
        String biome = loc.getBlock().getBiome().name();
        Optional<CreatureDefinition> pick = selector.pickForAmbient(biome, bias, random.nextDouble());
        if (pick.isEmpty()) return;

        Optional<Location> spot = AmbientSpawnLocation.findSurfaceNear(loc, SPAWN_MIN_R, SPAWN_MAX_R);
        if (spot.isEmpty()) return;

        spawner.spawn(pick.get(), spot.get());
    }
}
```

- [ ] **Step 2: Add config keys**

In `src/main/resources/config.yml`, extend the existing `creatures:` block so it reads:

```yaml
# Потойбічні істоти
creatures:
  # Мінімальна горизонтальна відстань (у блоках) від точки спавну світу, на якій починають
  # з'являтися міфічні істоти (природний спавн + спавн у структурах). /creature spawn ігнорує це.
  min-spawn-distance: 2000
  # Ambient-спавн: істоти з'являються самі (нікого не заміняючи) біля потойбічного за межами
  # min-spawn-distance, за законом конвергенції (його шлях+послідовність).
  ambient:
    interval-seconds: 60   # як часто виконується перевірка
    chance: 0.022          # шанс спавну за перевірку на одного потойбічного (~1 / 45 хв)
    max-nearby: 3          # пропустити, якщо стільки тегованих істот уже в радіусі 64 блоків
```

(Keep the existing `min-spawn-distance` line; only add the `ambient:` sub-block. If the file currently has only `min-spawn-distance`, the result is the block shown above.)

- [ ] **Step 3: Wire in ServiceContainer**

In `ServiceContainer.java`:

(a) Add a field beside the other schedulers (near line 71-73):
```java
    private me.vangoo.infrastructure.schedulers.AmbientCreatureSpawner ambientCreatureSpawner;
```

(b) In `initializeSchedulers()` (after `this.rampageScheduler = ...`, ~line 270), read config and construct it from the already-initialized creature fields:
```java
        double ambientMinDistance = plugin.getConfig().getDouble("creatures.min-spawn-distance", 2000.0);
        long ambientInterval = plugin.getConfig().getLong("creatures.ambient.interval-seconds", 60L);
        double ambientChance = plugin.getConfig().getDouble("creatures.ambient.chance", 0.022);
        int ambientMaxNearby = plugin.getConfig().getInt("creatures.ambient.max-nearby", 3);
        this.ambientCreatureSpawner = new me.vangoo.infrastructure.schedulers.AmbientCreatureSpawner(
                (MysteriesAbovePlugin) plugin, beyonderService, creatureSelector, creatureSpawner, creatureCodec,
                ambientMinDistance, ambientInterval, ambientChance, ambientMaxNearby);
```

(c) Add a getter beside the other scheduler getters (~line 326-328):
```java
    public me.vangoo.infrastructure.schedulers.AmbientCreatureSpawner getAmbientCreatureSpawner() {
        return ambientCreatureSpawner;
    }
```

(d) In `startSchedulers()` (after `rampageScheduler.start();`):
```java
        ambientCreatureSpawner.start();
```

(e) In `stopSchedulers()` (after the `rampageScheduler` stop block):
```java
        if (ambientCreatureSpawner != null) {
            ambientCreatureSpawner.stop();
        }
```

- [ ] **Step 4: Build + run the suite**

Run: `"C:/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd" -q -DskipTests package`
Expected: BUILD SUCCESS (shaded jar in `target/`).
Then: `"C:/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd" -q test`
Expected: PASS (all existing + Task 1 tests, e.g. 53 total).

- [ ] **Step 5: In-server smoke test (manual, deferred to user)**

Become a Beyonder, travel >2000 blocks out into a biome your pathway has creatures for, and idle on the surface. Within a few checks a creature of your pathway (favoring your current/next sequence) should appear 24–48 blocks away without any vanilla mob being replaced. No more than 3 creatures accumulate within 64 blocks.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/schedulers/AmbientCreatureSpawner.java \
        src/main/resources/config.yml \
        src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java
git commit -m "feat(creatures): AmbientCreatureSpawner — convergence-driven additive spawns"
```

---

### Task 4: Cut existing natural-replacement chance ×0.25

**Files:**
- Modify: `src/main/resources/creatures.yml`

**Verification:** config-only; build confirms it parses.

- [ ] **Step 1: Scale every `spawn.natural.chance` by 0.25**

For each creature that has a `spawn.natural.chance`, multiply the value by 0.25 and keep it
strictly > 0, rounding to a sensible precision (3–4 significant decimals). Examples:

```yaml
# before -> after
chance: 0.04   ->  chance: 0.01
chance: 0.02   ->  chance: 0.005
chance: 0.03   ->  chance: 0.0075
chance: 0.012  ->  chance: 0.003
```

For any value not listed, compute `round(value * 0.25, 4 sig-figs)`, floor to a small positive
number (never 0) if the input was > 0. Do NOT touch `structure.chance`, `stats`, `loot`,
`biomes`, `replace`, or any other field — only `natural.chance`.

- [ ] **Step 2: Verify it parses**

Run: `"C:/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd" -q -DskipTests package`
Expected: BUILD SUCCESS (resource copied without YAML error).

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/creatures.yml
git commit -m "balance(creatures): cut natural-replacement spawn chance to ~1/4 (ambient is primary now)"
```

---

## Self-Review

**Spec coverage:**
- §1 `pickForAmbient` (pathway+biome filter, convergence weighting, no spawn gate, requires bias) → Task 1. ✓
- §2 `AmbientCreatureSpawner` (interval, distance gate, chance, cap, bias, biome, spawn) → Task 3. ✓
- §3 location finder (surface, 24–48, passable, non-lava) → Task 2. ✓
- §4 config keys → Task 3 Step 2. ✓
- §5 cut natural chance ×0.25 → Task 4. ✓
- Wiring (construct + getter + start/stop) → Task 3 Step 3. ✓

**Type consistency:**
- `pickForAmbient(String, ConvergenceBias, double)` — defined Task 1, called Task 3. ✓
- `AmbientSpawnLocation.findSurfaceNear(Location, double, double)` — defined Task 2, called Task 3. ✓
- `AmbientCreatureSpawner(MysteriesAbovePlugin, BeyonderService, CreatureSelector, CreatureSpawner, CreatureCodec, double, long, double, int)` — defined Task 3, constructed in ServiceContainer same task. ✓
- Reuses existing `SpawnDistanceGate.isFarEnough`, `ConvergenceBias`, `CreatureSpawner.spawn`, `CreatureCodec.isCreature`, scheduler start/stop pattern. ✓

**Placeholder scan:** none — every code step is complete; Bukkit pieces (finder, scheduler) carry compile + explicit in-server smoke steps because the project forbids Bukkit mocking.

**Notes:**
- `pickForAmbient` deliberately ignores `naturalReplaces` (no vanilla mob is involved in ambient spawns).
- Reading `creatures.min-spawn-distance` again in `initializeSchedulers` duplicates the read done for the listeners — harmless (same config value).
