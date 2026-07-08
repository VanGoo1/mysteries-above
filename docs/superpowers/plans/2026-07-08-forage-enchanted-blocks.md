# Forage Enchanted Blocks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Спек:** [2026-07-08-forage-block-retexture-design.md](../specs/2026-07-08-forage-block-retexture-design.md)

**Goal:** Замінити форедж-ноди-сутності (ItemDisplay + Interaction, ПКМ) на «зачаровані» блоки: фізична підміна вегетації/листя на блок-донор (ресурспак малює його зачарованим), тихі GLOW-частинки, ламання → падає лише інгредієнт, TTL/креш → блок відновлюється.

**Architecture:** Чисте правило мапінгу донорів — `domain.forage.ForageDonorMap` (юніт-тест). Уся мутація світу — infrastructure: `ForageNode` (place/restore), `ForageNodeCodec` (PDC чанка для креш-відновлення), `ForageNodeSpawner` (реєстр позицій + particle-тік), `ForageHarvestListener` (BlockBreak + крайові/чанкові події). Текстури донорів — 4 PNG у `mysteries-resourcepack/`.

**Tech Stack:** Java 21, Paper API 1.21, JUnit 5, Maven (shade), PDC (PersistentDataContainer) чанків.

## Global Constraints

- Java 21, Spigot/Bukkit API 1.21; сервер — Paper.
- `domain.forage` — БЕЗ Bukkit-імпортів (`ArchitectureTest.pureDomainCoreHasNoBukkitOrGuiDependencies` пінить пакет).
- User-facing текст — українською; логи (`plugin.getLogger()`) — англійською; коментарі — як у сусідньому файлі.
- Коміти — Conventional Commits, тільки англійською, `type(scope): subject` ≤ 72 символів; повідомлення через `git commit -F <файл>` (here-strings лишають літеральні `@`).
- `mvn` НЕ на PATH. Використовуй bundled Maven IntelliJ (PowerShell):
  ```powershell
  $mvn = (Get-ChildItem "C:\Program Files\JetBrains" -Recurse -Filter mvn.cmd | Select-Object -First 1).FullName
  & $mvn -o clean package -q
  ```
- `*.yml` у `src/main/resources` — Maven-filtered: тестуй через збірку, не сирі файли.
- Ability/session-правила проєкту не зачіпаються — це infrastructure/presentation-механіка.

---

### Task 1: `ForageDonorMap` — чисте правило мапінгу донорів

**Files:**
- Create: `src/main/java/me/vangoo/domain/forage/ForageDonorMap.java`
- Test: `src/test/java/me/vangoo/domain/forage/ForageDonorMapTest.java`

**Interfaces:**
- Consumes: нічого (чистий VO).
- Produces: `new ForageDonorMap(String defaultPlantDonor, String defaultLeavesDonor, Map<String,String> overrides)`; `String donorFor(String originalMaterial, boolean leaves)` — повертає ім'я матеріалу-донора (UPPER_CASE). Task 2 кладе його у `ForageConfig`, Task 4 викликає `donorFor`.

- [ ] **Step 1: Write the failing test**

`src/test/java/me/vangoo/domain/forage/ForageDonorMapTest.java`:

```java
package me.vangoo.domain.forage;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForageDonorMapTest {

    private ForageDonorMap map() {
        return new ForageDonorMap("WARPED_ROOTS", "AZALEA_LEAVES",
                Map.of("POPPY", "CRIMSON_ROOTS"));
    }

    @Test
    void plantDefaultWhenNoOverride() {
        assertEquals("WARPED_ROOTS", map().donorFor("SHORT_GRASS", false));
    }

    @Test
    void leavesDefaultWhenNoOverride() {
        assertEquals("AZALEA_LEAVES", map().donorFor("OAK_LEAVES", true));
    }

    @Test
    void overrideBeatsDefault() {
        assertEquals("CRIMSON_ROOTS", map().donorFor("POPPY", false));
    }

    @Test
    void namesAreCaseInsensitive() {
        assertEquals("CRIMSON_ROOTS", map().donorFor("poppy", false));
    }

    @Test
    void unknownOriginalFallsBackToClassDefault() {
        assertEquals("WARPED_ROOTS", map().donorFor("DANDELION", false));
        assertEquals("AZALEA_LEAVES", map().donorFor("BIRCH_LEAVES", true));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
$mvn = (Get-ChildItem "C:\Program Files\JetBrains" -Recurse -Filter mvn.cmd | Select-Object -First 1).FullName
& $mvn -o test "-Dtest=ForageDonorMapTest"
```
Expected: COMPILATION ERROR — `ForageDonorMap` не існує.

- [ ] **Step 3: Write minimal implementation**

`src/main/java/me/vangoo/domain/forage/ForageDonorMap.java`:

```java
package me.vangoo.domain.forage;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Чисте правило мапінгу «оригінальний блок → блок-донор» фореджу. Донор — блок, якого немає
 * в Оверворлді природно; ресурспак малює його «зачарованим». Overrides перемагають дефолти;
 * дефолт обирається за класом цілі (рослина/листя). Імена матеріалів — рядки (без Bukkit);
 * їхню валідність перевіряє завантажувач конфіга.
 */
public final class ForageDonorMap {

    private final String defaultPlantDonor;
    private final String defaultLeavesDonor;
    private final Map<String, String> overrides;

    public ForageDonorMap(String defaultPlantDonor, String defaultLeavesDonor, Map<String, String> overrides) {
        this.defaultPlantDonor = normalize(defaultPlantDonor);
        this.defaultLeavesDonor = normalize(defaultLeavesDonor);
        Map<String, String> copy = new HashMap<>();
        for (Map.Entry<String, String> e : overrides.entrySet()) {
            copy.put(normalize(e.getKey()), normalize(e.getValue()));
        }
        this.overrides = Map.copyOf(copy);
    }

    /** Донор для оригінального блока; {@code leaves} — чи належить ціль до листя. */
    public String donorFor(String originalMaterial, boolean leaves) {
        String override = overrides.get(normalize(originalMaterial));
        if (override != null) return override;
        return leaves ? defaultLeavesDonor : defaultPlantDonor;
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toUpperCase(Locale.ROOT);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```powershell
& $mvn -o test "-Dtest=ForageDonorMapTest"
```
Expected: `Tests run: 5, Failures: 0` + BUILD SUCCESS. `ArchitectureTest` теж зелений (пакет уже в `PURE_DOMAIN`).

- [ ] **Step 5: Commit**

```powershell
Set-Content "$env:TEMP\cm.txt" -Value "feat(forage): add pure donor map rule for enchanted blocks" -NoNewline
git add src/main/java/me/vangoo/domain/forage/ForageDonorMap.java src/test/java/me/vangoo/domain/forage/ForageDonorMapTest.java
git commit -F "$env:TEMP\cm.txt"
```

---

### Task 2: Конфіг — `forage.yml` + `ForageConfig` + `ForageConfigLoader`

**Files:**
- Modify: `src/main/resources/forage.yml`
- Modify: `src/main/java/me/vangoo/infrastructure/forage/ForageConfig.java`
- Modify: `src/main/java/me/vangoo/infrastructure/forage/ForageConfigLoader.java`

**Interfaces:**
- Consumes: `ForageDonorMap` (Task 1).
- Produces: розширений record `ForageConfig(long intervalSeconds, double chance, int maxNearby, long ttlSeconds, int searchRadius, long particlePeriodTicks, Set<Material> vegetation, Set<Material> leaves, ForageDonorMap donors, Map<String, List<ForageEntry>> biomes)`. Task 4 читає `leaves()`, `donors()`, `particlePeriodTicks()`.

- [ ] **Step 1: Доповнити `forage.yml`**

У секцію `spawn:` (після `search-radius: 8`) додати:

```yaml
    particle-period-ticks: 40 # період тихих GLOW-частинок над зачарованими блоками
    leaves:                   # цілі в кронах (повні блоки листя)
      - OAK_LEAVES
      - BIRCH_LEAVES
      - SPRUCE_LEAVES
      - DARK_OAK_LEAVES
      - JUNGLE_LEAVES
      - ACACIA_LEAVES
```

Після секції `spawn:` (на рівні `spawn`/`biomes`, перед `biomes:`) додати:

```yaml
  donors:                     # оригінал -> блок-донор; ресурспак малює донора «зачарованим»
    default-plant: WARPED_ROOTS
    default-leaves: AZALEA_LEAVES
    overrides:
      POPPY: CRIMSON_ROOTS
      DANDELION: CRIMSON_ROOTS
      CORNFLOWER: CRIMSON_ROOTS
      OXEYE_DAISY: CRIMSON_ROOTS
      BLUE_ORCHID: CRIMSON_ROOTS
      AZURE_BLUET: CRIMSON_ROOTS
      ALLIUM: CRIMSON_ROOTS
      RED_TULIP: CRIMSON_ROOTS
      LILY_OF_THE_VALLEY: CRIMSON_ROOTS
      SWEET_BERRY_BUSH: NETHER_SPROUTS
```

Також онови шапку-коментар файла: `ПКМ по ній -> інгредієнт падає` → `блок підмінюється на «зачарованого» донора; ламаєш -> падає лише інгредієнт`.

- [ ] **Step 2: Розширити `ForageConfig`**

Повний новий вміст `ForageConfig.java`:

```java
package me.vangoo.infrastructure.forage;

import me.vangoo.domain.forage.ForageDonorMap;
import me.vangoo.domain.forage.ForageEntry;
import org.bukkit.Material;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Незмінний знімок forage.yml: параметри спавну, цілі (флора/листя), донори, таблиці біомів. */
public record ForageConfig(
        long intervalSeconds,
        double chance,
        int maxNearby,
        long ttlSeconds,
        int searchRadius,
        long particlePeriodTicks,
        Set<Material> vegetation,
        Set<Material> leaves,
        ForageDonorMap donors,
        Map<String, List<ForageEntry>> biomes
) {}
```

- [ ] **Step 3: Розширити `ForageConfigLoader`**

У `load()` — нові локальні дефолти поряд з наявними (`long particlePeriod = 40L;`, `Set<Material> leaves = new HashSet<>();`), у гілці `root == null` і у фінальному `return` — новий конструктор:

```java
return new ForageConfig(interval, chance, maxNearby, ttl, radius, particlePeriod,
        vegetation, leaves, donorMap, biomes);
```

(для гілки `root == null` — `new ForageDonorMap("WARPED_ROOTS", "AZALEA_LEAVES", Map.of())`).

У блоці `if (spawn != null)` додати:

```java
            particlePeriod = spawn.getLong("particle-period-ticks", particlePeriod);
            for (String name : spawn.getStringList("leaves")) {
                Material m = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
                if (m == null) {
                    plugin.getLogger().warning("forage.yml: unknown leaves material '" + name + "', skipping");
                } else {
                    leaves.add(m);
                }
            }
```

Після блоку `spawn`, перед `biomes` — парсинг донорів:

```java
        String defaultPlant = "WARPED_ROOTS";
        String defaultLeaves = "AZALEA_LEAVES";
        Map<String, String> donorOverrides = new LinkedHashMap<>();
        ConfigurationSection donors = root.getConfigurationSection("donors");
        if (donors != null) {
            defaultPlant = validDonorOr(donors.getString("default-plant"), defaultPlant);
            defaultLeaves = validDonorOr(donors.getString("default-leaves"), defaultLeaves);
            ConfigurationSection ov = donors.getConfigurationSection("overrides");
            if (ov != null) {
                for (String original : ov.getKeys(false)) {
                    Material origM = Material.matchMaterial(original.toUpperCase(Locale.ROOT));
                    String donorName = ov.getString(original, "");
                    Material donorM = Material.matchMaterial(donorName.toUpperCase(Locale.ROOT));
                    if (origM == null || donorM == null || !donorM.isBlock()) {
                        plugin.getLogger().warning("forage.yml: invalid donor override '"
                                + original + ": " + donorName + "', skipping");
                        continue;
                    }
                    donorOverrides.put(origM.name(), donorM.name());
                }
            }
        }
        ForageDonorMap donorMap = new ForageDonorMap(defaultPlant, defaultLeaves, donorOverrides);
```

І приватний хелпер (поруч із `toInt`):

```java
    /** Ім'я валідного блок-матеріалу або fallback (warning). */
    private String validDonorOr(String candidate, String fallback) {
        if (candidate == null) return fallback;
        Material m = Material.matchMaterial(candidate.toUpperCase(Locale.ROOT));
        if (m == null || !m.isBlock()) {
            plugin.getLogger().warning("forage.yml: invalid donor '" + candidate
                    + "', falling back to " + fallback);
            return fallback;
        }
        return m.name();
    }
```

Імпорт: `me.vangoo.domain.forage.ForageDonorMap`. Фінальний лог доповни: `+ leaves.size() + " leaf materials"`.

- [ ] **Step 4: Збірка**

```powershell
& $mvn -o clean package -q
```
Expected: BUILD SUCCESS (усі тести зелені; спавнер компілюється — він читає лише старі акцесори).

- [ ] **Step 5: Commit**

```powershell
Set-Content "$env:TEMP\cm.txt" -Value "feat(forage): load leaves, donors and particle period from forage.yml" -NoNewline
git add src/main/resources/forage.yml src/main/java/me/vangoo/infrastructure/forage/ForageConfig.java src/main/java/me/vangoo/infrastructure/forage/ForageConfigLoader.java
git commit -F "$env:TEMP\cm.txt"
```

---

### Task 3: `ForageNodeLocation` — пошук повертає `Block` + скан крон

**Files:**
- Modify: `src/main/java/me/vangoo/infrastructure/forage/ForageNodeLocation.java`
- Modify: `src/main/java/me/vangoo/infrastructure/schedulers/ForageNodeSpawner.java` (адаптація єдиного call-site, поведінка не змінюється)

**Interfaces:**
- Produces: `static Optional<Block> findVegetationNear(Player, Set<Material>, int radius)` (було `Optional<Location>`); НОВЕ `static Optional<Block> findLeavesNear(Player, Set<Material> leaves, int radius)`. Task 4 викликає обидва.

- [ ] **Step 1: Змінити сигнатуру `findVegetationNear` і додати `findLeavesNear`**

У `ForageNodeLocation.java`: заміни тип повернення і рядок `return Optional.of(b.getLocation().add(0.5, 0.5, 0.5));` на `return Optional.of(b);`, зміни `Optional<Location>` на `Optional<Block>` у сигнатурі (імпорт `Location` прибрати, якщо не потрібен). Додай константу і метод:

```java
    private static final int CANOPY_SCAN_DEPTH = 4; // блоків углиб крони від найвищого блока

    /** Шукає блок листя у кронах: згори (найвищий блок колонки) вниз кілька блоків. */
    public static Optional<Block> findLeavesNear(Player player, Set<Material> leaves, int radius) {
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null || leaves.isEmpty()) return Optional.empty();

        for (int i = 0; i < ATTEMPTS; i++) {
            int x = center.getBlockX() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int z = center.getBlockZ() + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int topY = world.getHighestBlockYAt(x, z);
            for (int dy = 0; dy < CANOPY_SCAN_DEPTH; dy++) {
                Block b = world.getBlockAt(x, topY - dy, z);
                if (leaves.contains(b.getType())) return Optional.of(b);
            }
        }
        return Optional.empty();
    }
```

Онови javadoc класу: він шукає і наземну флору (біля ніг), і листя (у кронах).

- [ ] **Step 2: Адаптувати старий call-site у `ForageNodeSpawner.trySpawnFor`**

Замінити:

```java
        Optional<Location> spot = ForageNodeLocation.findVegetationNear(player, config.vegetation(), config.searchRadius());
        if (spot.isEmpty()) return;

        String biome = spot.get().getBlock().getBiome().name();
```

на:

```java
        Optional<org.bukkit.block.Block> spot = ForageNodeLocation.findVegetationNear(player, config.vegetation(), config.searchRadius());
        if (spot.isEmpty()) return;

        String biome = spot.get().getBiome().name();
```

і рядок спавну ноди на:

```java
        nodes.add(ForageNode.spawn(spot.get().getLocation().add(0.5, 0.5, 0.5), model.get(), pick.get(), codec));
```

(Поведінка ідентична — це тимчасовий місток до Task 4.)

- [ ] **Step 3: Збірка**

```powershell
& $mvn -o clean package -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```powershell
Set-Content "$env:TEMP\cm.txt" -Value "feat(forage): block-returning vegetation search and canopy leaves scan" -NoNewline
git add src/main/java/me/vangoo/infrastructure/forage/ForageNodeLocation.java src/main/java/me/vangoo/infrastructure/schedulers/ForageNodeSpawner.java
git commit -F "$env:TEMP\cm.txt"
```

---

### Task 4: Ядро — зачаровані блоки замість сутностей

Повна заміна `ForageNode`, `ForageNodeCodec`, `ForageNodeSpawner`, `ForageHarvestListener` + wiring. Один атомарний перемикач (старий і новий механізми не співіснують).

**Files:**
- Rewrite: `src/main/java/me/vangoo/infrastructure/forage/ForageNode.java`
- Rewrite: `src/main/java/me/vangoo/infrastructure/forage/ForageNodeCodec.java`
- Rewrite: `src/main/java/me/vangoo/infrastructure/schedulers/ForageNodeSpawner.java`
- Rewrite: `src/main/java/me/vangoo/presentation/listeners/ForageHarvestListener.java`
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java:272-273`
- Modify: `src/main/java/me/vangoo/MysteriesAbovePlugin.java:251-253`

**Interfaces:**
- Consumes: `ForageConfig.leaves()/donors()/particlePeriodTicks()` (Task 2); `ForageNodeLocation.findVegetationNear/findLeavesNear → Optional<Block>` (Task 3); `ForageDonorMap.donorFor(String, boolean)` (Task 1).
- Produces (для Task 5): на `ForageNodeSpawner` — `Optional<ForageNode> nodeAt(Block)`, `void onHarvested(ForageNode)`, `void restoreAndRemove(ForageNode)`, `List<ForageNode> nodesInChunk(Chunk)`, `void healChunk(Chunk)`.

- [ ] **Step 1: Переписати `ForageNode` (block-based)**

Повний новий вміст `ForageNode.java`:

```java
package me.vangoo.infrastructure.forage;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;

/**
 * «Зачарований» блок фореджу: оригінальну вегетацію фізично підмінено на блок-донор, який
 * ресурспак малює зачарованим. Нода пам'ятає оригінальний BlockData (для двоблокової флори —
 * обидві половини) і вміє відновитись. Життєвий цикл: place -> (збір через BlockBreakEvent
 * у лістенері | restore за TTL/нештатною подією).
 */
public final class ForageNode {

    private final Block block;              // нижній/єдиний блок (донор)
    private final BlockData originalLower;
    private final BlockData originalUpper;  // null, якщо флора одноблокова
    private final Material donor;
    private final String ingredientId;
    private final long createdAtMillis;

    private ForageNode(Block block, BlockData originalLower, BlockData originalUpper,
                       Material donor, String ingredientId) {
        this.block = block;
        this.originalLower = originalLower;
        this.originalUpper = originalUpper;
        this.donor = donor;
        this.ingredientId = ingredientId;
        this.createdAtMillis = System.currentTimeMillis();
    }

    /** Підмінити блок на донора. Верхня половина двоблокової флори нормалізується вниз. */
    public static ForageNode place(Block target, Material donor, String ingredientId) {
        Block lower = normalizeToLower(target);
        BlockData originalLower = lower.getBlockData();
        Block above = lower.getRelative(BlockFace.UP);
        BlockData originalUpper = isUpperHalfOf(originalLower, above) ? above.getBlockData() : null;

        if (originalUpper != null) above.setType(Material.AIR, false);
        lower.setType(donor, false); // без фізики, щоб донор не «стрельнув» одразу
        if (lower.getBlockData() instanceof Leaves leavesData) {
            leavesData.setPersistent(true); // листя-донор не має осипатися
            lower.setBlockData(leavesData, false);
        }
        return new ForageNode(lower, originalLower, originalUpper, donor, ingredientId);
    }

    private static Block normalizeToLower(Block b) {
        if (b.getBlockData() instanceof Bisected bis && bis.getHalf() == Bisected.Half.TOP) {
            return b.getRelative(BlockFace.DOWN);
        }
        return b;
    }

    private static boolean isUpperHalfOf(BlockData lowerData, Block above) {
        return lowerData instanceof Bisected
                && above.getType() == lowerData.getMaterial()
                && above.getBlockData() instanceof Bisected bis
                && bis.getHalf() == Bisected.Half.TOP;
    }

    /** Блок досі є донором (його не знесли фізикою чи іншим шляхом повз лістенер). */
    public boolean isIntact() {
        return block.getType() == donor;
    }

    public long ageMillis() {
        return System.currentTimeMillis() - createdAtMillis;
    }

    public Block getBlock() {
        return block;
    }

    public Location particleLocation() {
        return block.getLocation().add(0.5, 0.7, 0.5);
    }

    public String getIngredientId() {
        return ingredientId;
    }

    public BlockData originalLower() {
        return originalLower;
    }

    public BlockData originalUpper() {
        return originalUpper;
    }

    /** Повернути оригінальний блок (обидві половини для двоблокової флори). */
    public void restore() {
        if (!isIntact()) return; // блок уже знесено інакше — не воскрешаємо рослину з повітря
        block.setBlockData(originalLower, false);
        if (originalUpper != null) {
            block.getRelative(BlockFace.UP).setBlockData(originalUpper, false);
        }
    }
}
```

- [ ] **Step 2: Переписати `ForageNodeCodec` (PDC чанка)**

Повний новий вміст `ForageNodeCodec.java`:

```java
package me.vangoo.infrastructure.forage;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Серіалізація живих нод фореджу в PDC чанка — страховка від крешу: якщо сервер упав,
 * записи лишаються в чанку, і при його завантаженні блоки відновлюються (healChunk).
 * Формат значення: ноди через '\n', поля через '|': x|y|z|lowerBlockData|upperBlockData
 * ('' якщо верхньої половини нема). BlockData-рядки не містять цих символів.
 */
public final class ForageNodeCodec {

    private final NamespacedKey nodesKey;

    public ForageNodeCodec(Plugin plugin) {
        this.nodesKey = new NamespacedKey(plugin, "forage_chunk_nodes");
    }

    /** Один запис ноди в PDC чанка. {@code upperData} — null для одноблокової флори. */
    public record StoredNode(int x, int y, int z, String lowerData, String upperData) {

        private String serialize() {
            return x + "|" + y + "|" + z + "|" + lowerData + "|" + (upperData == null ? "" : upperData);
        }

        private static StoredNode parse(String line) {
            String[] p = line.split("\\|", 5);
            if (p.length < 5) return null;
            try {
                return new StoredNode(Integer.parseInt(p[0]), Integer.parseInt(p[1]),
                        Integer.parseInt(p[2]), p[3], p[4].isEmpty() ? null : p[4]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    public void add(Chunk chunk, StoredNode node) {
        List<StoredNode> all = read(chunk);
        all.removeIf(n -> n.x() == node.x() && n.y() == node.y() && n.z() == node.z());
        all.add(node);
        write(chunk, all);
    }

    public void remove(Chunk chunk, int x, int y, int z) {
        List<StoredNode> all = read(chunk);
        if (all.removeIf(n -> n.x() == x && n.y() == y && n.z() == z)) {
            write(chunk, all);
        }
    }

    public List<StoredNode> read(Chunk chunk) {
        String raw = chunk.getPersistentDataContainer().get(nodesKey, PersistentDataType.STRING);
        List<StoredNode> result = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return result;
        for (String line : raw.split("\n")) {
            StoredNode n = StoredNode.parse(line);
            if (n != null) result.add(n);
        }
        return result;
    }

    public void clear(Chunk chunk) {
        chunk.getPersistentDataContainer().remove(nodesKey);
    }

    private void write(Chunk chunk, List<StoredNode> all) {
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        if (all.isEmpty()) {
            pdc.remove(nodesKey);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (StoredNode n : all) {
            if (!sb.isEmpty()) sb.append('\n');
            sb.append(n.serialize());
        }
        pdc.set(nodesKey, PersistentDataType.STRING, sb.toString());
    }
}
```

- [ ] **Step 3: Переписати `ForageNodeSpawner`**

Повний новий вміст `ForageNodeSpawner.java`:

```java
package me.vangoo.infrastructure.schedulers;

import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.domain.forage.ForageSelector;
import me.vangoo.infrastructure.forage.ForageConfig;
import me.vangoo.infrastructure.forage.ForageNode;
import me.vangoo.infrastructure.forage.ForageNodeCodec;
import me.vangoo.infrastructure.forage.ForageNodeLocation;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Ambient-спавн «зачарованих» блоків фореджу: періодично для кожного онлайн-гравця з шансом
 * підміняє найближчу вегетацію/листя на блок-донор (ресурспак малює його зачарованим) і
 * пам'ятає оригінал для відновлення (TTL/stop/креш через PDC чанка). Без дистанційного
 * гейту — форедж усюди. Інваріант: живі ноди існують лише в завантажених чанках.
 */
public final class ForageNodeSpawner {

    private static final double NEARBY_RADIUS_SQ = 32.0 * 32.0;

    private final MysteriesAbovePlugin plugin;
    private final ForageSelector selector;
    private final ForageNodeCodec codec;
    private final ForageConfig config;
    private final Random random = new Random();
    /** Живі ноди за позицією блока (ключ — key(block)). */
    private final Map<String, ForageNode> nodes = new HashMap<>();
    private final long intervalTicks;
    private final long ttlMillis;

    private BukkitTask spawnTask;
    private BukkitTask particleTask;

    public ForageNodeSpawner(MysteriesAbovePlugin plugin, ForageSelector selector,
                             ForageNodeCodec codec, ForageConfig config) {
        this.plugin = plugin;
        this.selector = selector;
        this.codec = codec;
        this.config = config;
        this.intervalTicks = Math.max(20L, config.intervalSeconds() * 20L);
        this.ttlMillis = Math.max(1000L, config.ttlSeconds() * 1000L);
    }

    public void start() {
        if (spawnTask != null && !spawnTask.isCancelled()) return;
        healAllLoadedChunks();
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, intervalTicks, intervalTicks);
        long particlePeriod = Math.max(10L, config.particlePeriodTicks());
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::particleTick, particlePeriod, particlePeriod);
        plugin.getLogger().info("ForageNodeSpawner started");
    }

    public void stop() {
        if (spawnTask != null) { spawnTask.cancel(); spawnTask = null; }
        if (particleTask != null) { particleTask.cancel(); particleTask = null; }
        for (ForageNode n : List.copyOf(nodes.values())) restoreAndRemove(n);
        plugin.getLogger().info("ForageNodeSpawner stopped");
    }

    /** Жива нода на цьому блоці. */
    public Optional<ForageNode> nodeAt(Block block) {
        return Optional.ofNullable(nodes.get(key(block)));
    }

    /** Збір гравцем: блок уже ламає подія — лише зняти з реєстру та PDC чанка. */
    public void onHarvested(ForageNode node) {
        deregister(node);
    }

    /** Дострокове/нештатне зняття: повернути оригінальний блок і зняти ноду. */
    public void restoreAndRemove(ForageNode node) {
        node.restore();
        deregister(node);
    }

    public List<ForageNode> nodesInChunk(Chunk chunk) {
        List<ForageNode> result = new ArrayList<>();
        for (ForageNode n : nodes.values()) {
            Block b = n.getBlock();
            if (b.getWorld().equals(chunk.getWorld())
                    && (b.getX() >> 4) == chunk.getX() && (b.getZ() >> 4) == chunk.getZ()) {
                result.add(n);
            }
        }
        return result;
    }

    /**
     * Відновити «осиротілі» записи PDC чанка (лишились після крешу). Викликати лише коли
     * в чанку немає живих нод (завантаження чанка; старт плагіна).
     */
    public void healChunk(Chunk chunk) {
        List<ForageNodeCodec.StoredNode> stored = codec.read(chunk);
        if (stored.isEmpty()) return;
        World world = chunk.getWorld();
        for (ForageNodeCodec.StoredNode s : stored) {
            try {
                Block b = world.getBlockAt(s.x(), s.y(), s.z());
                b.setBlockData(Bukkit.createBlockData(s.lowerData()), false);
                if (s.upperData() != null) {
                    b.getRelative(BlockFace.UP).setBlockData(Bukkit.createBlockData(s.upperData()), false);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Forage heal: bad block data in chunk PDC: " + e.getMessage());
            }
        }
        codec.clear(chunk);
        plugin.getLogger().info("Forage: healed " + stored.size() + " stale node(s) in chunk "
                + chunk.getX() + "," + chunk.getZ());
    }

    private void healAllLoadedChunks() {
        for (World w : Bukkit.getWorlds()) {
            for (Chunk c : w.getLoadedChunks()) healChunk(c);
        }
    }

    private void tick() {
        prune();
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                trySpawnFor(player);
            } catch (Exception e) {
                plugin.getLogger().warning("Forage spawn error for " + player.getName() + ": " + e);
            }
        }
    }

    private void particleTick() {
        for (ForageNode n : nodes.values()) {
            n.getBlock().getWorld().spawnParticle(
                    Particle.GLOW, n.particleLocation(), 2, 0.25, 0.25, 0.25, 0.0);
        }
    }

    private void prune() {
        for (ForageNode n : List.copyOf(nodes.values())) {
            if (!n.isIntact()) {
                deregister(n); // блок знесено повз лістенер — не воскрешаємо рослину з повітря
            } else if (n.ageMillis() > ttlMillis) {
                restoreAndRemove(n); // не зібрали — тихо повертаємо оригінал
            }
        }
    }

    private void trySpawnFor(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR
                || player.getGameMode() == GameMode.CREATIVE) return;
        if (random.nextDouble() >= config.chance()) return;
        if (countNear(player) >= config.maxNearby()) return;

        Optional<Block> target = findTarget(player);
        if (target.isEmpty()) return;
        Block block = target.get();
        if (nodes.containsKey(key(block))) return;

        boolean isLeaves = config.leaves().contains(block.getType());
        Material donor = Material.matchMaterial(
                config.donors().donorFor(block.getType().name(), isLeaves));
        if (donor == null) return; // донори валідує лоадер; це страховка

        Optional<String> pick = selector.pickForBiome(block.getBiome().name(), random.nextDouble());
        if (pick.isEmpty()) return;

        register(ForageNode.place(block, donor, pick.get()));
    }

    private Optional<Block> findTarget(Player player) {
        boolean leavesFirst = !config.leaves().isEmpty() && random.nextBoolean();
        if (leavesFirst) {
            Optional<Block> leaves = ForageNodeLocation.findLeavesNear(
                    player, config.leaves(), config.searchRadius());
            if (leaves.isPresent()) return leaves;
            return ForageNodeLocation.findVegetationNear(player, config.vegetation(), config.searchRadius());
        }
        Optional<Block> flora = ForageNodeLocation.findVegetationNear(
                player, config.vegetation(), config.searchRadius());
        if (flora.isPresent()) return flora;
        return ForageNodeLocation.findLeavesNear(player, config.leaves(), config.searchRadius());
    }

    private int countNear(Player player) {
        int count = 0;
        for (ForageNode n : nodes.values()) {
            Block b = n.getBlock();
            if (b.getWorld().equals(player.getWorld())
                    && b.getLocation().add(0.5, 0.5, 0.5).distanceSquared(player.getLocation()) <= NEARBY_RADIUS_SQ) {
                count++;
            }
        }
        return count;
    }

    private void register(ForageNode node) {
        Block b = node.getBlock();
        nodes.put(key(b), node);
        codec.add(b.getChunk(), new ForageNodeCodec.StoredNode(
                b.getX(), b.getY(), b.getZ(),
                node.originalLower().getAsString(),
                node.originalUpper() == null ? null : node.originalUpper().getAsString()));
    }

    private void deregister(ForageNode node) {
        Block b = node.getBlock();
        nodes.remove(key(b));
        codec.remove(b.getChunk(), b.getX(), b.getY(), b.getZ());
    }

    private static String key(Block b) {
        return b.getWorld().getUID() + ":" + b.getX() + ":" + b.getY() + ":" + b.getZ();
    }
}
```

- [ ] **Step 4: Переписати `ForageHarvestListener`**

Повний новий вміст `ForageHarvestListener.java` (крайові/чанкові події — Task 5):

```java
package me.vangoo.presentation.listeners;

import me.vangoo.application.services.CustomItemService;
import me.vangoo.infrastructure.forage.ForageNode;
import me.vangoo.infrastructure.schedulers.ForageNodeSpawner;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/**
 * Збір фореджу: гравець ламає «зачарований» блок-донор -> ванільний дроп скасовується,
 * падає лише допоміжний інгредієнт; нода знімається з реєстру та PDC чанка.
 */
public class ForageHarvestListener implements Listener {

    private final ForageNodeSpawner spawner;
    private final CustomItemService customItemService;
    private final Plugin plugin;

    public ForageHarvestListener(ForageNodeSpawner spawner, CustomItemService customItemService, Plugin plugin) {
        this.spawner = spawner;
        this.customItemService = customItemService;
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Optional<ForageNode> node = spawner.nodeAt(event.getBlock());
        if (node.isEmpty()) return;
        event.setDropItems(false); // ванільний дроп донора скасовано — падає лише інгредієнт
        harvest(node.get(), event.getBlock());
    }

    private void harvest(ForageNode node, Block block) {
        spawner.onHarvested(node);
        Optional<ItemStack> stack = customItemService.createItemStack(node.getIngredientId());
        if (stack.isEmpty()) {
            plugin.getLogger().warning("Forage harvest: could not resolve ingredient item for "
                    + node.getIngredientId());
            return;
        }
        World world = block.getWorld();
        Location loc = block.getLocation().add(0.5, 0.3, 0.5);
        world.dropItem(loc, stack.get());
        world.playSound(loc, Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1.0f, 1.2f);
        world.spawnParticle(Particle.HAPPY_VILLAGER, loc, 8, 0.3, 0.3, 0.3, 0.0);
    }
}
```

- [ ] **Step 5: Wiring**

`ServiceContainer.java` (~рядок 272) — прибрати `customItemService` з конструктора спавнера:

```java
        this.forageNodeSpawner = new me.vangoo.infrastructure.schedulers.ForageNodeSpawner(
                (MysteriesAbovePlugin) plugin, forageSelector, forageNodeCodec, forageConfig);
```

`MysteriesAbovePlugin.java` (~рядок 251) — новий конструктор лістенера:

```java
        ForageHarvestListener forageHarvestListener = new ForageHarvestListener(
                services.getForageNodeSpawner(), services.getCustomItemService(), this);
        getServer().getPluginManager().registerEvents(forageHarvestListener, this);
```

- [ ] **Step 6: Збірка**

```powershell
& $mvn -o clean package -q
```
Expected: BUILD SUCCESS. Якщо компілятор знайде інші згадки старого API (`codec.isForageNode`, `ForageNode.spawn(Location, ...)`) — це пропущені call-sites, онови їх за цим самим зразком.

- [ ] **Step 7: In-server smoke (якщо доступний сервер)**

JAR з `target/` → `plugins/`; на сервері: походити по рівнині → трава підміняється на warped roots (без ресурспаку — виглядає як незерське коріння, це очікувано до Task 6); зламати → падає інгредієнт, насіння немає; почекати TTL → блок повертається; `stop` сервера → у логах "ForageNodeSpawner stopped", блоки відновлені.

- [ ] **Step 8: Commit**

```powershell
Set-Content "$env:TEMP\cm.txt" -Value "feat(forage): replace entity nodes with enchanted donor blocks" -NoNewline
git add src/main/java/me/vangoo/infrastructure/forage/ForageNode.java src/main/java/me/vangoo/infrastructure/forage/ForageNodeCodec.java src/main/java/me/vangoo/infrastructure/schedulers/ForageNodeSpawner.java src/main/java/me/vangoo/presentation/listeners/ForageHarvestListener.java src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java src/main/java/me/vangoo/MysteriesAbovePlugin.java
git commit -F "$env:TEMP\cm.txt"
```

---

### Task 5: Крайові події + життєвий цикл чанків

**Files:**
- Modify: `src/main/java/me/vangoo/presentation/listeners/ForageHarvestListener.java`

**Interfaces:**
- Consumes: `spawner.nodeAt(Block)`, `spawner.restoreAndRemove(ForageNode)`, `spawner.nodesInChunk(Chunk)`, `spawner.healChunk(Chunk)` (Task 4).
- Produces: нічого нового для наступних тасок.

- [ ] **Step 1: Додати обробники в `ForageHarvestListener`**

Додати імпорти:

```java
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import java.util.List;
```

Додати методи в кінець класу:

```java
    // ---- Нештатне знищення ноди: повертаємо оригінал БЕЗ дропу інгредієнта (анти-автофарм) ----

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        restoreAll(event.getBlocks());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        restoreAll(event.getBlocks());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        restoreAll(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        restoreAll(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        restoreOne(event.getBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent event) {
        restoreOne(event.getToBlock());
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        // Донор ставиться persistent=true; це страховка, якщо стан збили ззовні.
        spawner.nodeAt(event.getBlock()).ifPresent(n -> {
            event.setCancelled(true);
            spawner.restoreAndRemove(n);
        });
    }

    // ---- Життєвий цикл чанків: живі ноди існують лише в завантажених чанках ----

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (ForageNode n : spawner.nodesInChunk(event.getChunk())) {
            spawner.restoreAndRemove(n);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        spawner.healChunk(event.getChunk()); // «осиротілі» записи після крешу
    }

    private void restoreAll(List<Block> blocks) {
        for (Block b : blocks) restoreOne(b);
    }

    private void restoreOne(Block block) {
        spawner.nodeAt(block).ifPresent(spawner::restoreAndRemove);
    }
```

- [ ] **Step 2: Збірка**

```powershell
& $mvn -o clean package -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```powershell
Set-Content "$env:TEMP\cm.txt" -Value "feat(forage): restore nodes on world edits and heal chunks after crash" -NoNewline
git add src/main/java/me/vangoo/presentation/listeners/ForageHarvestListener.java
git commit -F "$env:TEMP\cm.txt"
```

---

### Task 6: Ресурспак — текстури донорів, README, чистка старого

**Files:**
- Commit as-is: `mysteries-datapack/`, `mysteries-resourcepack/` (нові в git)
- Create: `mysteries-resourcepack/assets/minecraft/textures/block/{warped_roots,crimson_roots,nether_sprouts,azalea_leaves}.png` (заглушки)
- Create: `mysteries-resourcepack/README.md`
- Delete: `resourcepack/` (весь скаффолд), `mysteries.zip`

**Interfaces:** нічого програмного; клієнтські ассети.

- [ ] **Step 1: Закомітити знайдені паки як базу**

```powershell
Set-Content "$env:TEMP\cm.txt" -Value "chore(pack): add server datapack and resourcepack sources" -NoNewline
git add mysteries-datapack mysteries-resourcepack
git commit -F "$env:TEMP\cm.txt"
```

- [ ] **Step 2: Згенерувати PNG-заглушки донорів**

PowerShell (шаховий візерунок «колір/прозорий», 16×16 — помітно в грі, користувач замінить своїми):

```powershell
Add-Type -AssemblyName System.Drawing
$dir = "mysteries-resourcepack\assets\minecraft\textures\block"
New-Item -ItemType Directory -Force $dir | Out-Null
$specs = @{
  'warped_roots'   = [System.Drawing.Color]::Cyan
  'crimson_roots'  = [System.Drawing.Color]::Magenta
  'nether_sprouts' = [System.Drawing.Color]::Yellow
  'azalea_leaves'  = [System.Drawing.Color]::LawnGreen
}
foreach ($name in $specs.Keys) {
  $bmp = New-Object System.Drawing.Bitmap 16,16
  for ($x = 0; $x -lt 16; $x++) {
    for ($y = 0; $y -lt 16; $y++) {
      if ((($x + $y) % 2) -eq 0) { $bmp.SetPixel($x, $y, $specs[$name]) }
      else { $bmp.SetPixel($x, $y, [System.Drawing.Color]::Transparent) }
    }
  }
  $bmp.Save("$dir\$name.png", [System.Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
}
Get-ChildItem $dir
```
Expected: 4 PNG-файли у `textures/block/`.

- [ ] **Step 3: Написати `mysteries-resourcepack/README.md`**

```markdown
# Mysteries Above — серверний ресурспак

Джерело правди для клієнтських ассетів сервера: текстури/моделі кастомних інгредієнтів
і «зачарованих» блоків фореджу. Датапак (структури) — окремо, у `mysteries-datapack/`.

## Кастомні предмети (інгредієнти)

Кожен інгредієнт = base material `PAPER` + рядковий `custom_model_data` (= id інгредієнта
з `src/main/resources/custom-items.yml`). Плагін виставляє компонент у `CustomItemFactory`;
пак ловить рядок у `assets/minecraft/items/paper.json` і підставляє модель.

Додати новий інгредієнт:
1. Спрайт → `assets/minecraft/textures/item/<id>.png` (16×16 або 32×32).
2. Модель → `assets/minecraft/models/item/<id>.json` (скопіюй сусідню, заміни id).
3. Прив'язка → новий `case` в `assets/minecraft/items/paper.json`:
   `{ "when": "<id>", "model": { "type": "minecraft:model", "model": "minecraft:item/<id>" } }`

## «Зачаровані» блоки фореджу (донори)

Плагін фізично підміняє вегетацію на блок-донор; пак перемальовує донора ЦІЛКОМ —
лише PNG, без JSON-моделей (ванільні моделі підхоплюють текстури за іменем):

| Файл у `textures/block/` | Що малює | Де з'являється |
|---|---|---|
| `warped_roots.png`   | зачарована трава/папороть | трава, папороть |
| `crimson_roots.png`  | зачарована квітка         | квіти |
| `nether_sprouts.png` | зачарований кущик         | ягідний кущ та інше |
| `azalea_leaves.png`  | зачароване листя          | крони дерев |

Поточні PNG — ЗАГЛУШКИ (шаховий візерунок): заміни своїми текстурами тих самих імен.
Свідома жертва: донори так виглядатимуть і в рідному вимірі (Незер / люш-печери).
Мапінг оригінал→донор конфігурується в `src/main/resources/forage.yml` (`donors:`).

## Роздача гравцям

1. Заархівуй ВМІСТ цієї теки в zip (щоб `pack.mcmeta` був у корені архіву).
2. Захости zip за прямим URL.
3. SHA-1: `Get-FileHash pack.zip -Algorithm SHA1`.
4. `server.properties`:
   ```
   resource-pack=https://.../pack.zip
   resource-pack-sha1=<хеш>
   require-resource-pack=true
   ```

`pack.mcmeta` тримає `min_format`/`max_format` під версію клієнта — онови при апдейті MC.
```

- [ ] **Step 4: Видалити старий скаффолд і застарілий zip**

```powershell
git rm -r resourcepack
git rm mysteries.zip
```
(`mysteries.zip` відстежується git-ом? Якщо `git rm` каже "did not match" — просто `Remove-Item mysteries.zip`.)

- [ ] **Step 5: Збірка (санітарна)**

```powershell
& $mvn -o clean package -q
```
Expected: BUILD SUCCESS (ассети не впливають на Java, це перевірка що нічого не зачепили).

- [ ] **Step 6: Commit**

```powershell
Set-Content "$env:TEMP\cm.txt" -Value "feat(pack): placeholder donor textures and resourcepack readme" -NoNewline
git add mysteries-resourcepack
git commit -F "$env:TEMP\cm.txt"
Set-Content "$env:TEMP\cm.txt" -Value "chore(pack): drop legacy resourcepack scaffold and mysteries.zip" -NoNewline
git add -A
git commit -F "$env:TEMP\cm.txt"
```

---

### Task 7: Документація — правило фореджу + оновлення спека/CLAUDE.md

**Files:**
- Create: `.claude/rules/forage.md`
- Modify: `CLAUDE.md` (розділ «Config & persistence» — додати `forage.yml` і згадку паків)
- Modify: `docs/superpowers/specs/2026-07-08-forage-block-retexture-design.md` (розділ «Відомі спрощення»)

**Interfaces:** документація.

- [ ] **Step 1: Створити `.claude/rules/forage.md`**

```markdown
---
paths:
  - "src/main/java/me/vangoo/domain/forage/**"
  - "src/main/java/me/vangoo/infrastructure/forage/**"
  - "src/main/java/me/vangoo/infrastructure/schedulers/ForageNodeSpawner.java"
  - "src/main/java/me/vangoo/presentation/listeners/ForageHarvestListener.java"
  - "src/main/resources/forage.yml"
  - "mysteries-resourcepack/**"
---

# Форедж: «зачаровані» блоки

Канал допоміжних інгредієнтів: `ForageNodeSpawner` періодично підміняє вегетацію/листя біля
гравця на блок-донор (`forage.yml` → `donors:`), який ресурспак `mysteries-resourcepack/`
малює «зачарованим». Ламаєш донора → падає ЛИШЕ інгредієнт (`BlockBreakEvent`,
`setDropItems(false)`). TTL/stop/чанк-анлоад → блок тихо відновлюється.

## Межі шарів

- Чисте (юніт-тести, без Bukkit): `domain.forage.ForageSelector` (вибір інгредієнта за
  біомом), `domain.forage.ForageDonorMap` (оригінал → донор). Пакет пінить `ArchitectureTest`.
- Мутація світу: `infrastructure.forage.ForageNode` (place/restore, двоблокова флора),
  `ForageNodeCodec` (PDC чанка), `ForageNodeSpawner` (реєстр + тіки), край-кейси —
  `presentation.listeners.ForageHarvestListener`.

## Інваріанти (не ламати)

- Живі ноди існують ЛИШЕ в завантажених чанках: `ChunkUnloadEvent` відновлює достроково;
  `ChunkLoadEvent`/старт → `healChunk` лікує «осиротілі» записи PDC після крешу.
- Кожен `place()` має дзеркальний шлях відновлення: збір (блок ламається), TTL-прюн,
  `stop()`, нештатні події (поршень/вибух/вогонь/рідина/декей) — БЕЗ дропу інгредієнта.
- Реєстр нод — інстанс-поле спавнера; ключ — позиція блока. Ніякого static.
- Донор мусить бути блоком, якого нема в Оверворлді природно (незерські/ендові рослини),
  інакше звичайні блоки світу виглядатимуть зачарованими.

## Додати новий донор/текстуру

1. `forage.yml` → `donors.overrides.<ОРИГІНАЛ>: <ДОНОР>` (або зміни дефолт).
2. PNG у `mysteries-resourcepack/assets/minecraft/textures/block/<донор>.png`
   (перемальовується ввесь блок-донор; JSON-моделі не потрібні).
3. Перезбери й перероздай ресурспак (SHA-1 у `server.properties`).

## Відомі спрощення

- Знесення ОПОРИ під донором (блок під рослиною) фізикою дропає предмет донора (не
  інгредієнт); нода тихо знімається наступним прюном (`isIntact()`).
- Донори виглядають «зачаровано» й у рідному вимірі (Незер/люш-печери) — свідома жертва.
```

- [ ] **Step 2: Оновити `CLAUDE.md`**

У розділі «Config & persistence», рядок зі списком ресурсів — додати `forage.yml`:

`...`creatures.yml`, `potion-recipes.yml`.` → `...`creatures.yml`, `potion-recipes.yml`, `forage.yml` (форедж: цілі/донори/біомні таблиці — див. `.claude/rules/forage.md`).`

Туди ж, після абзацу про MythicMobs-пак, додати рядок:

```markdown
- Клієнтські ассети (текстури інгредієнтів, «зачаровані» блоки фореджу) — серверний ресурспак `mysteries-resourcepack/`; датапак структур — `mysteries-datapack/`. Обидва роздаються поза Maven-збіркою (див. README ресурспаку).
```

- [ ] **Step 3: Доповнити спек розділом «Відомі спрощення»**

У кінець `docs/superpowers/specs/2026-07-08-forage-block-retexture-design.md` (перед «Файли (орієнтовно)») додати:

```markdown
## Відомі спрощення

- Знесення опори під донором (наприклад, блок землі під рослиною) дропає фізикою предмет
  самого донора, а не інгредієнт; нода тихо знімається наступним прюном (`isIntact()`).
- Донори виглядають «зачаровано» й у рідному вимірі (Незер/люш-печери).
- Вибір цілі флора/листя — 50/50 з фолбеком на інший клас, без окремих ваг.
```

- [ ] **Step 4: Commit**

```powershell
Set-Content "$env:TEMP\cm.txt" -Value "docs(forage): rules for enchanted-block forage and donor textures" -NoNewline
git add .claude/rules/forage.md CLAUDE.md docs/superpowers/specs/2026-07-08-forage-block-retexture-design.md
git commit -F "$env:TEMP\cm.txt"
```

---

## Фінальна in-server перевірка (ручна, після всіх тасок)

JAR → `plugins/`, оновлений ресурспак → клієнт (локально можна просто покласти zip у `resourcepacks/` і ввімкнути вручну):

1. Ходити біомами → флора/листя біля гравця підміняються на донорів із шахово-кольоровими заглушками; рідкі GLOW-частинки видно.
2. Зламати зачарований блок → падає лише інгредієнт; звук + партикли.
3. TTL (90 с) → блок тихо повертається; перевірити двоблокову траву (`TALL_GRASS`).
4. Поршень/вибух/вода по ноді → блок відновлено, інгредієнт не випав.
5. Відійти далеко (чанк вивантажився) → блок відновлено.
6. Kill процесу сервера з живими нодами → після рестарту при завантаженні чанків у логах "Forage: healed N stale node(s)...", блоки оригінальні.
7. `/reload confirm` або stop → "ForageNodeSpawner stopped", усі блоки відновлені.
8. Не-Beyonder гравець теж бачить і збирає ноди.
9. Донори тримаються на траві/землі без фізичного «пострілу» (особливо `NETHER_SPROUTS` на ягідних місцях); якщо якийсь донор попається — заміни його в `donors:` на інший (наприклад, `CRIMSON_ROOTS`).
```
