# Природний форедж + тиризація луту — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Додати канал природного фореджу (видимі 3D-ноди допоміжних інгредієнтів на вегетації, збір ЛКМ → дроп на землю) і розділити наявний лут за рідкістю (`base`/`rare`).

**Architecture:** Дзеркалить наявні патерни Спеку 3 — чисте правило вибору (`ForageSelector`, як `CreatureSelector`), планувальник із власним `BukkitTask` (`ForageNodeSpawner`, як `AmbientCreatureSpawner`), PDC-кодек (`ForageNodeCodec`, як `CreatureCodec`), YAML-завантажувач (`ForageConfigLoader`, як `CreatureConfigLoader`). Тиризація — чисте поле `tier` на `LootItem` + `LootTableData.filterByTier`, наявна логіка ролу не змінюється.

**Tech Stack:** Java 21, Spigot/Bukkit API 1.21, JUnit 5, ArchUnit. Без DI-фреймворка — ручний `ServiceContainer`.

## Global Constraints

- **Java 21**, цільова Bukkit/Spigot **API 1.21**.
- **Maven не в PATH** — використовуй bundled mvn з IntelliJ (див. memory `maven-not-on-path-use-intellij-bundled`). Команди `mvn ...` нижче — логічні; адаптуй сам виклик під середовище.
- **`me.vangoo.domain.forage` — Bukkit-вільний** (тип вегетації/біом як рядки/Material резолвляться поза domain). ArchUnit `pureDomainCoreHasNoBukkitOrGuiDependencies` має лишатися зеленим.
- **`domain` не залежить від `me.vangoo.pathways`** (ArchUnit `domainDoesNotDependOnBehaviorLayer`).
- **Інваріант:** форедж видає **тільки допоміжні** інгредієнти (id з `forage.yml`); Характеристики недосяжні (форедж не торкається лут-таблиць; наявний `LootGenerationService.createItemFromId` відхиляє `characteristic:`).
- **User-facing рядки/логи** — узгоджені з наявним стилем (укр. де доречно).

---

### Task 1: Чисте правило вибору фореджу (`ForageSelector`)

**Files:**
- Create: `src/main/java/me/vangoo/domain/forage/ForageEntry.java`
- Create: `src/main/java/me/vangoo/domain/forage/ForageSelector.java`
- Modify: `src/test/java/me/vangoo/architecture/ArchitectureTest.java` (додати `domain.forage` у `PURE_DOMAIN`)
- Test: `src/test/java/me/vangoo/domain/forage/ForageSelectorTest.java`

**Interfaces:**
- Produces: `ForageEntry(String ingredientId, int weight)`; `ForageSelector(Map<String,List<ForageEntry>> byBiome)` з методом `Optional<String> pickForBiome(String biome, double roll)` (`roll ∈ [0,1)`).

- [ ] **Step 1: Написати фейл-тест `ForageSelectorTest`**

```java
package me.vangoo.domain.forage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ForageSelectorTest {

    @Test
    void picksFromKnownBiome() {
        ForageSelector s = new ForageSelector(
                Map.of("PLAINS", List.of(new ForageEntry("elf_flower_petals", 50))));
        assertEquals("elf_flower_petals", s.pickForBiome("PLAINS", 0.5).orElse(null));
    }

    @Test
    void unknownBiomeEmpty() {
        ForageSelector s = new ForageSelector(Map.of("PLAINS", List.of(new ForageEntry("x", 50))));
        assertTrue(s.pickForBiome("DESERT", 0.5).isEmpty());
    }

    @Test
    void nullBiomeEmpty() {
        ForageSelector s = new ForageSelector(Map.of("PLAINS", List.of(new ForageEntry("x", 50))));
        assertTrue(s.pickForBiome(null, 0.5).isEmpty());
    }

    @Test
    void emptyEntriesEmpty() {
        ForageSelector s = new ForageSelector(Map.of("PLAINS", List.<ForageEntry>of()));
        assertTrue(s.pickForBiome("PLAINS", 0.5).isEmpty());
    }

    @Test
    void weightedSegmentsByRoll() {
        // sum 80; a:[0,50) b:[50,80)
        ForageSelector s = new ForageSelector(Map.of("PLAINS",
                List.of(new ForageEntry("a", 50), new ForageEntry("b", 30))));
        assertEquals("a", s.pickForBiome("PLAINS", 0.1).get());   // target 8  -> a
        assertEquals("a", s.pickForBiome("PLAINS", 0.6).get());   // target 48 -> a
        assertEquals("b", s.pickForBiome("PLAINS", 0.7).get());   // target 56 -> b
        assertEquals("b", s.pickForBiome("PLAINS", 0.99).get());  // target ~79 -> b
    }

    @Test
    void zeroWeightIgnored() {
        ForageSelector s = new ForageSelector(Map.of("PLAINS",
                List.of(new ForageEntry("zero", 0), new ForageEntry("real", 10))));
        assertEquals("real", s.pickForBiome("PLAINS", 0.0).get());
    }
}
```

- [ ] **Step 2: Запустити — має не скомпілюватись/впасти**

Run: `mvn test -Dtest=ForageSelectorTest`
Expected: FAIL — `ForageEntry` / `ForageSelector` не існують.

- [ ] **Step 3: Створити `ForageEntry`**

```java
package me.vangoo.domain.forage;

/** Один кандидат фореджу: id допоміжного інгредієнта + вага у таблиці біому. */
public record ForageEntry(String ingredientId, int weight) {}
```

- [ ] **Step 4: Створити `ForageSelector`**

```java
package me.vangoo.domain.forage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Чисте правило вибору допоміжного інгредієнта для фореджу за біомом (аналог CreatureSelector).
 * Зважений детермінований вибір при поданому {@code roll} ∈ [0,1). Без Bukkit, без стану.
 */
public final class ForageSelector {

    private final Map<String, List<ForageEntry>> byBiome;

    public ForageSelector(Map<String, List<ForageEntry>> byBiome) {
        Map<String, List<ForageEntry>> copy = new HashMap<>();
        for (Map.Entry<String, List<ForageEntry>> e : byBiome.entrySet()) {
            copy.put(e.getKey(), List.copyOf(e.getValue()));
        }
        this.byBiome = Map.copyOf(copy);
    }

    public Optional<String> pickForBiome(String biome, double roll) {
        if (biome == null) return Optional.empty();
        List<ForageEntry> all = byBiome.get(biome);
        if (all == null) return Optional.empty();

        List<ForageEntry> entries = new ArrayList<>();
        double sum = 0.0;
        for (ForageEntry e : all) {
            if (e.weight() > 0) {
                entries.add(e);
                sum += e.weight();
            }
        }
        if (entries.isEmpty()) return Optional.empty();

        double target = roll * sum;
        double cumulative = 0.0;
        for (ForageEntry e : entries) {
            cumulative += e.weight();
            if (target < cumulative) return Optional.of(e.ingredientId());
        }
        return Optional.of(entries.get(entries.size() - 1).ingredientId());
    }
}
```

- [ ] **Step 5: Додати `domain.forage` у `PURE_DOMAIN`**

У `src/test/java/me/vangoo/architecture/ArchitectureTest.java` масив `PURE_DOMAIN` (зараз закінчується `"me.vangoo.domain.creatures"`) — додати рядок:

```java
    private static final String[] PURE_DOMAIN = {
            "me.vangoo.domain.entities",
            "me.vangoo.domain.services",
            "me.vangoo.domain.spells",
            "me.vangoo.domain.brewing",
            "me.vangoo.domain.creatures",
            "me.vangoo.domain.forage"
    };
```

- [ ] **Step 6: Запустити тести — мають пройти**

Run: `mvn test -Dtest=ForageSelectorTest,ArchitectureTest`
Expected: PASS (усі тести зелені).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/me/vangoo/domain/forage/ForageEntry.java \
        src/main/java/me/vangoo/domain/forage/ForageSelector.java \
        src/test/java/me/vangoo/domain/forage/ForageSelectorTest.java \
        src/test/java/me/vangoo/architecture/ArchitectureTest.java
git commit -m "feat(forage): ForageSelector — чисте правило вибору допоміжного за біомом"
```

---

### Task 2: Конфіг фореджу (`forage.yml` + `ForageConfig` + `ForageConfigLoader`)

**Files:**
- Create: `src/main/resources/forage.yml`
- Create: `src/main/java/me/vangoo/infrastructure/forage/ForageConfig.java`
- Create: `src/main/java/me/vangoo/infrastructure/forage/ForageConfigLoader.java`

**Interfaces:**
- Consumes: `ForageEntry` (Task 1).
- Produces: `ForageConfig(long intervalSeconds, double chance, int maxNearby, long ttlSeconds, int searchRadius, Set<Material> vegetation, Map<String,List<ForageEntry>> biomes)`; `ForageConfigLoader(Plugin)` з `ForageConfig load()`.

> Завантажувач (Bukkit YAML) перевіряється in-server за конвенцією проєкту — юніт-тесту немає. Deliverable цього таску: компілюється і готовий до wiring.

- [ ] **Step 1: Створити `src/main/resources/forage.yml`**

```yaml
# forage.yml
# Природний форедж допоміжних інгредієнтів: на вегетації біля гравця з'являється видима 3D-нода
# допоміжного інгредієнта; ЛКМ по ній -> інгредієнт падає на землю. Лише наявні auxiliary з
# potion-recipes.yml. Біом-тематично, для всіх гравців.
forage:
  spawn:
    interval-seconds: 40      # як часто планувальник перевіряє гравців
    chance: 0.5               # шанс спробувати спавн ноди за тік на гравця
    max-nearby: 5             # не спавнити, якщо стільки нод вже в радіусі 32 блоки
    ttl-seconds: 120          # нода зникає, якщо її не зібрали
    search-radius: 12         # радіус пошуку вегетації біля гравця
    vegetation:               # на чому з'являються ноди
      - SHORT_GRASS
      - TALL_GRASS
      - FERN
      - LARGE_FERN
      - OAK_LEAVES
      - BIRCH_LEAVES
      - SPRUCE_LEAVES
      - DARK_OAK_LEAVES
      - JUNGLE_LEAVES
      - SWEET_BERRY_BUSH
      - DANDELION
      - POPPY
      - CORNFLOWER
      - OXEYE_DAISY
      - BLUE_ORCHID
  biomes:                     # біом -> зважена таблиця допоміжних id (усі — наявні auxiliary)
    PLAINS:
      - { id: elf_flower_petals, weight: 50 }
      - { id: autumn_crocus_essence, weight: 30 }
      - { id: bone_of_the_unyielding_frame, weight: 15 }
    SUNFLOWER_PLAINS:
      - { id: elf_flower_petals, weight: 50 }
      - { id: peppermint_extract, weight: 30 }
    MEADOW:
      - { id: memory_flower_petals, weight: 40 }
      - { id: crystalline_moon_orchid, weight: 25 }
    FOREST:
      - { id: red_chestnut_flower, weight: 40 }
      - { id: memory_flower_petals, weight: 40 }
    FLOWER_FOREST:
      - { id: memory_flower_petals, weight: 50 }
      - { id: peppermint_extract, weight: 30 }
      - { id: crystalline_moon_orchid, weight: 20 }
    BIRCH_FOREST:
      - { id: tree_of_wisdom_sap, weight: 40 }
      - { id: yellow_amber_powder, weight: 30 }
    DARK_FOREST:
      - { id: depths_shadow_essence, weight: 40 }
      - { id: soul_wax, weight: 25 }
    JUNGLE:
      - { id: ever_shifting_lotus, weight: 40 }
      - { id: chameleon_slime, weight: 30 }
    TAIGA:
      - { id: tree_of_elders_fruit, weight: 40 }
      - { id: string_grass_powder, weight: 30 }
    SWAMP:
      - { id: black_mosquito, weight: 50 }
      - { id: dust_of_lake_spirit, weight: 25 }
    DESERT:
      - { id: dragon_savageland_pollen, weight: 60 }
    SAVANNA:
      - { id: dragon_savageland_pollen, weight: 40 }
      - { id: ashes_of_a_broken_oath_seal, weight: 20 }
```

- [ ] **Step 2: Створити `ForageConfig`**

```java
package me.vangoo.infrastructure.forage;

import me.vangoo.domain.forage.ForageEntry;
import org.bukkit.Material;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Незмінний знімок forage.yml: параметри спавну, множина вегетації, таблиці біомів. */
public record ForageConfig(
        long intervalSeconds,
        double chance,
        int maxNearby,
        long ttlSeconds,
        int searchRadius,
        Set<Material> vegetation,
        Map<String, List<ForageEntry>> biomes
) {}
```

- [ ] **Step 3: Створити `ForageConfigLoader`**

```java
package me.vangoo.infrastructure.forage;

import me.vangoo.domain.forage.ForageEntry;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Завантажує forage.yml у ForageConfig. Дзеркалить CreatureConfigLoader. */
public class ForageConfigLoader {

    private final Plugin plugin;

    public ForageConfigLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    public ForageConfig load() {
        File file = new File(plugin.getDataFolder(), "forage.yml");
        if (!file.exists()) {
            plugin.saveResource("forage.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("forage");

        long interval = 40L;
        double chance = 0.5;
        int maxNearby = 5;
        long ttl = 120L;
        int radius = 12;
        Set<Material> vegetation = new HashSet<>();
        Map<String, List<ForageEntry>> biomes = new LinkedHashMap<>();

        if (root == null) {
            plugin.getLogger().warning("No 'forage' section in forage.yml. Foraging disabled (empty config).");
            return new ForageConfig(interval, chance, maxNearby, ttl, radius, vegetation, biomes);
        }

        ConfigurationSection spawn = root.getConfigurationSection("spawn");
        if (spawn != null) {
            interval = spawn.getLong("interval-seconds", interval);
            chance = spawn.getDouble("chance", chance);
            maxNearby = spawn.getInt("max-nearby", maxNearby);
            ttl = spawn.getLong("ttl-seconds", ttl);
            radius = spawn.getInt("search-radius", radius);
            for (String name : spawn.getStringList("vegetation")) {
                Material m = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
                if (m == null) {
                    plugin.getLogger().warning("forage.yml: unknown vegetation material '" + name + "', skipping");
                } else {
                    vegetation.add(m);
                }
            }
        }

        ConfigurationSection biomesSection = root.getConfigurationSection("biomes");
        if (biomesSection != null) {
            for (String biome : biomesSection.getKeys(false)) {
                List<ForageEntry> entries = new ArrayList<>();
                for (Map<?, ?> raw : biomesSection.getMapList(biome)) {
                    Object idObj = raw.get("id");
                    if (idObj == null) {
                        plugin.getLogger().warning("forage.yml: entry missing 'id' in biome '" + biome + "', skipping");
                        continue;
                    }
                    int weight = toInt(raw.get("weight"), 1);
                    if (weight <= 0) weight = 1;
                    entries.add(new ForageEntry(String.valueOf(idObj), weight));
                }
                if (!entries.isEmpty()) {
                    biomes.put(biome.toUpperCase(Locale.ROOT), entries);
                }
            }
        }

        plugin.getLogger().info("Loaded forage config: " + biomes.size() + " biomes, "
                + vegetation.size() + " vegetation materials");
        return new ForageConfig(interval, chance, maxNearby, ttl, radius, vegetation, biomes);
    }

    private int toInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        if (o == null) return def;
        try { return Integer.parseInt(String.valueOf(o)); } catch (NumberFormatException e) { return def; }
    }
}
```

- [ ] **Step 4: Перевірити компіляцію**

Run: `mvn test-compile`
Expected: BUILD SUCCESS (компілюється; тести не торкаємось).

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/forage.yml \
        src/main/java/me/vangoo/infrastructure/forage/ForageConfig.java \
        src/main/java/me/vangoo/infrastructure/forage/ForageConfigLoader.java
git commit -m "feat(forage): forage.yml + ForageConfig/ForageConfigLoader"
```

---

### Task 3: Сутність-нода (`ForageNodeCodec`, `ForageNode`, `ForageNodeLocation`)

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/forage/ForageNodeCodec.java`
- Create: `src/main/java/me/vangoo/infrastructure/forage/ForageNode.java`
- Create: `src/main/java/me/vangoo/infrastructure/forage/ForageNodeLocation.java`

**Interfaces:**
- Produces:
  - `ForageNodeCodec(Plugin)`: `void tag(Entity, String ingredientId, UUID partner)`, `boolean isForageNode(Entity)`, `Optional<String> readIngredient(Entity)`, `Optional<UUID> readPartner(Entity)`.
  - `ForageNode.spawn(Location, ItemStack model, String ingredientId, ForageNodeCodec) -> ForageNode`; instance: `boolean isAlive()`, `long ageMillis()`, `void remove()`, `Location getLocation()`, `String getIngredientId()`.
  - `ForageNodeLocation.findVegetationNear(Player, Set<Material>, int radius) -> Optional<Location>`.

> Bukkit-ефекти, перевіряються in-server (Task 5). Deliverable: компілюється.

- [ ] **Step 1: Створити `ForageNodeCodec`** (PDC-тег, патерн `CreatureCodec`)

```java
package me.vangoo.infrastructure.forage;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;

/** PDC-тег ноди фореджу на сутності (патерн CreatureCodec). Тегуються обидві сутності ноди. */
public class ForageNodeCodec {

    private final NamespacedKey nodeKey;
    private final NamespacedKey ingredientKey;
    private final NamespacedKey partnerKey;

    public ForageNodeCodec(Plugin plugin) {
        this.nodeKey = new NamespacedKey(plugin, "forage_node");
        this.ingredientKey = new NamespacedKey(plugin, "forage_ingredient");
        this.partnerKey = new NamespacedKey(plugin, "forage_partner");
    }

    public void tag(Entity e, String ingredientId, UUID partner) {
        e.getPersistentDataContainer().set(nodeKey, PersistentDataType.BYTE, (byte) 1);
        e.getPersistentDataContainer().set(ingredientKey, PersistentDataType.STRING, ingredientId);
        e.getPersistentDataContainer().set(partnerKey, PersistentDataType.STRING, partner.toString());
    }

    public boolean isForageNode(Entity e) {
        return e != null && e.getPersistentDataContainer().has(nodeKey, PersistentDataType.BYTE);
    }

    public Optional<String> readIngredient(Entity e) {
        if (e == null) return Optional.empty();
        return Optional.ofNullable(e.getPersistentDataContainer().get(ingredientKey, PersistentDataType.STRING));
    }

    public Optional<UUID> readPartner(Entity e) {
        if (e == null) return Optional.empty();
        String s = e.getPersistentDataContainer().get(partnerKey, PersistentDataType.STRING);
        if (s == null) return Optional.empty();
        try { return Optional.of(UUID.fromString(s)); } catch (IllegalArgumentException ex) { return Optional.empty(); }
    }
}
```

- [ ] **Step 2: Створити `ForageNode`** (ItemDisplay-візуал + ArmorStand-хітбокс)

```java
package me.vangoo.infrastructure.forage;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;

/**
 * Логічна нода фореджу = видимий ItemDisplay (3D-модель інгредієнта) + невидимий ArmorStand
 * (клікабельний хітбокс для ЛКМ). Обидві сутності тегуються ForageNodeCodec і прибираються парою.
 */
public final class ForageNode {

    private final ItemDisplay display;
    private final ArmorStand hitbox;
    private final String ingredientId;
    private final long createdAtMillis;

    private ForageNode(ItemDisplay display, ArmorStand hitbox, String ingredientId, long createdAtMillis) {
        this.display = display;
        this.hitbox = hitbox;
        this.ingredientId = ingredientId;
        this.createdAtMillis = createdAtMillis;
    }

    public static ForageNode spawn(Location loc, ItemStack model, String ingredientId, ForageNodeCodec codec) {
        World world = loc.getWorld();
        ItemDisplay display = world.spawn(loc, ItemDisplay.class, d -> {
            d.setItemStack(model);
            d.setBillboard(Display.Billboard.CENTER);
            d.setGlowing(true);
            d.setPersistent(false);
        });
        ArmorStand hitbox = world.spawn(loc.clone().subtract(0, 0.4, 0), ArmorStand.class, a -> {
            a.setVisible(false);
            a.setGravity(false);
            a.setMarker(false);
            a.setSmall(true);
            a.setBasePlate(false);
            a.setArms(false);
            a.setPersistent(false);
            a.setCanPickupItems(false);
        });
        codec.tag(hitbox, ingredientId, display.getUniqueId());
        codec.tag(display, ingredientId, hitbox.getUniqueId());
        return new ForageNode(display, hitbox, ingredientId, System.currentTimeMillis());
    }

    public boolean isAlive() {
        return display != null && hitbox != null && display.isValid() && hitbox.isValid();
    }

    public long ageMillis() { return System.currentTimeMillis() - createdAtMillis; }

    public Location getLocation() { return hitbox.getLocation(); }

    public String getIngredientId() { return ingredientId; }

    public void remove() {
        if (display != null && !display.isDead()) display.remove();
        if (hitbox != null && !hitbox.isDead()) hitbox.remove();
    }
}
```

- [ ] **Step 3: Створити `ForageNodeLocation`** (пошук вегетації, патерн `AmbientSpawnLocation`)

```java
package me.vangoo.infrastructure.forage;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** Шукає блок вегетації біля гравця для розміщення ноди фореджу. */
public final class ForageNodeLocation {

    private static final int ATTEMPTS = 10;

    private ForageNodeLocation() {}

    public static Optional<Location> findVegetationNear(Player player, Set<Material> vegetation, int radius) {
        Location center = player.getLocation();
        World world = center.getWorld();
        if (world == null || vegetation.isEmpty()) return Optional.empty();

        for (int i = 0; i < ATTEMPTS; i++) {
            int dx = ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int dz = ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int x = center.getBlockX() + dx;
            int z = center.getBlockZ() + dz;

            Block top = world.getHighestBlockAt(x, z);
            for (int dy = 0; dy >= -2; dy--) {
                Block b = top.getRelative(0, dy, 0);
                if (vegetation.contains(b.getType())) {
                    return Optional.of(b.getLocation().add(0.5, 0.6, 0.5));
                }
            }
        }
        return Optional.empty();
    }
}
```

- [ ] **Step 4: Перевірити компіляцію**

Run: `mvn test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/forage/ForageNodeCodec.java \
        src/main/java/me/vangoo/infrastructure/forage/ForageNode.java \
        src/main/java/me/vangoo/infrastructure/forage/ForageNodeLocation.java
git commit -m "feat(forage): ForageNode (ItemDisplay+ArmorStand) + codec + vegetation finder"
```

---

### Task 4: Планувальник (`ForageNodeSpawner`) + wiring

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/schedulers/ForageNodeSpawner.java`
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java`

**Interfaces:**
- Consumes: `ForageSelector` (Task 1), `ForageConfig`/`ForageConfigLoader` (Task 2), `ForageNode`/`ForageNodeCodec`/`ForageNodeLocation` (Task 3), наявний `CustomItemService` (метод `Optional<ItemStack> createItemStack(String id)`).
- Produces: `ForageNodeSpawner(MysteriesAbovePlugin, ForageSelector, CustomItemService, ForageNodeCodec, ForageConfig)` з `start()` / `stop()`. Геттери `getForageNodeSpawner()`, `getForageNodeCodec()` у `ServiceContainer`.

> In-server (Task 5). Deliverable: компілюється + wiring на місці.

- [ ] **Step 1: Створити `ForageNodeSpawner`** (дзеркалить `AmbientCreatureSpawner`)

```java
package me.vangoo.infrastructure.schedulers;

import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.application.services.CustomItemService;
import me.vangoo.domain.forage.ForageSelector;
import me.vangoo.infrastructure.forage.ForageConfig;
import me.vangoo.infrastructure.forage.ForageNode;
import me.vangoo.infrastructure.forage.ForageNodeCodec;
import me.vangoo.infrastructure.forage.ForageNodeLocation;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * Ambient-спавн нод фореджу: періодично для кожного онлайн-гравця з шансом підкидає на найближчу
 * вегетацію видиму ноду допоміжного інгредієнта (біом-тематично). Без дистанційного гейту —
 * форедж усюди. Дзеркалить життєвий цикл AmbientCreatureSpawner (start/stop + один BukkitTask).
 */
public final class ForageNodeSpawner {

    private static final double NEARBY_RADIUS = 32.0;

    private final MysteriesAbovePlugin plugin;
    private final ForageSelector selector;
    private final CustomItemService customItemService;
    private final ForageNodeCodec codec;
    private final ForageConfig config;
    private final Random random = new Random();
    private final List<ForageNode> nodes = new ArrayList<>();
    private final long intervalTicks;
    private final long ttlMillis;

    private BukkitTask task;

    public ForageNodeSpawner(MysteriesAbovePlugin plugin, ForageSelector selector,
                             CustomItemService customItemService, ForageNodeCodec codec, ForageConfig config) {
        this.plugin = plugin;
        this.selector = selector;
        this.customItemService = customItemService;
        this.codec = codec;
        this.config = config;
        this.intervalTicks = Math.max(20L, config.intervalSeconds() * 20L);
        this.ttlMillis = Math.max(1000L, config.ttlSeconds() * 1000L);
    }

    public void start() {
        if (task != null && !task.isCancelled()) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, intervalTicks, intervalTicks);
        plugin.getLogger().info("ForageNodeSpawner started");
    }

    public void stop() {
        if (task != null && !task.isCancelled()) {
            task.cancel();
            task = null;
        }
        for (ForageNode n : nodes) n.remove();
        nodes.clear();
        plugin.getLogger().info("ForageNodeSpawner stopped");
    }

    private void tick() {
        pruneNodes();
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                trySpawnFor(player);
            } catch (Exception e) {
                plugin.getLogger().warning("Forage spawn error for " + player.getName() + ": " + e);
            }
        }
    }

    private void pruneNodes() {
        nodes.removeIf(n -> {
            if (!n.isAlive() || n.ageMillis() > ttlMillis) {
                n.remove();
                return true;
            }
            return false;
        });
    }

    private void trySpawnFor(Player player) {
        if (player.getGameMode() == GameMode.SPECTATOR) return;
        if (random.nextDouble() >= config.chance()) return;

        int near = 0;
        for (Entity e : player.getNearbyEntities(NEARBY_RADIUS, NEARBY_RADIUS, NEARBY_RADIUS)) {
            if (e instanceof ArmorStand && codec.isForageNode(e)) near++;
        }
        if (near >= config.maxNearby()) return;

        Optional<Location> spot = ForageNodeLocation.findVegetationNear(player, config.vegetation(), config.searchRadius());
        if (spot.isEmpty()) return;

        String biome = spot.get().getBlock().getBiome().name();
        Optional<String> pick = selector.pickForBiome(biome, random.nextDouble());
        if (pick.isEmpty()) return;

        Optional<ItemStack> model = customItemService.createItemStack(pick.get());
        if (model.isEmpty()) return;

        nodes.add(ForageNode.spawn(spot.get(), model.get(), pick.get(), codec));
    }
}
```

- [ ] **Step 2: Оголосити поля у `ServiceContainer`**

У блоці приватних полів (поряд із `private me.vangoo.infrastructure.schedulers.AmbientCreatureSpawner ambientCreatureSpawner;`, ~рядок 76) додати:

```java
    private me.vangoo.infrastructure.forage.ForageNodeCodec forageNodeCodec;
    private me.vangoo.infrastructure.schedulers.ForageNodeSpawner forageNodeSpawner;
```

- [ ] **Step 3: Сконструювати кодек у `initializeInfrastructure()`**

Одразу після рядка `this.creatureCodec = new me.vangoo.infrastructure.creatures.CreatureCodec(plugin);` (~рядок 190) додати:

```java
        this.forageNodeCodec = new me.vangoo.infrastructure.forage.ForageNodeCodec(plugin);
```

- [ ] **Step 4: Сконструювати спавнер біля ambient-спавнера**

У місці, де будується `this.ambientCreatureSpawner = ...` (~рядки 273–279), одразу після нього додати:

```java
        me.vangoo.infrastructure.forage.ForageConfigLoader forageConfigLoader =
                new me.vangoo.infrastructure.forage.ForageConfigLoader(plugin);
        me.vangoo.infrastructure.forage.ForageConfig forageConfig = forageConfigLoader.load();
        me.vangoo.domain.forage.ForageSelector forageSelector =
                new me.vangoo.domain.forage.ForageSelector(forageConfig.biomes());
        this.forageNodeSpawner = new me.vangoo.infrastructure.schedulers.ForageNodeSpawner(
                (MysteriesAbovePlugin) plugin, forageSelector, customItemService, forageNodeCodec, forageConfig);
```

> `customItemService` уже є полем `ServiceContainer` (використовується вище для `VanillaAppearance`); якщо в цьому методі воно недоступне як локальна змінна — звернись через поле (`this.customItemService`).

- [ ] **Step 5: Додати геттери**

Поряд із `public ... getAmbientCreatureSpawner() { return ambientCreatureSpawner; }` (~рядок 339) додати:

```java
    public me.vangoo.infrastructure.forage.ForageNodeCodec getForageNodeCodec() { return forageNodeCodec; }
    public me.vangoo.infrastructure.schedulers.ForageNodeSpawner getForageNodeSpawner() { return forageNodeSpawner; }
```

- [ ] **Step 6: Старт/стоп у schedulers**

У `startSchedulers()` після `ambientCreatureSpawner.start();` (~рядок 369) додати:

```java
        forageNodeSpawner.start();
```

У `stopSchedulers()` поряд із блоком зупинки ambient-спавнера (~рядки 400–402) додати:

```java
        if (forageNodeSpawner != null) {
            forageNodeSpawner.stop();
        }
```

- [ ] **Step 7: Перевірити компіляцію**

Run: `mvn test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/schedulers/ForageNodeSpawner.java \
        src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java
git commit -m "feat(forage): ForageNodeSpawner scheduler + ServiceContainer wiring"
```

---

### Task 5: Лістенер збору (`ForageHarvestListener`) + реєстрація + in-server

**Files:**
- Create: `src/main/java/me/vangoo/presentation/listeners/ForageHarvestListener.java`
- Modify: `src/main/java/me/vangoo/MysteriesAbovePlugin.java` (реєстрація лістенера)

**Interfaces:**
- Consumes: `ForageNodeCodec` (Task 3), `CustomItemService`, `getForageNodeCodec()` (Task 4).
- Produces: зареєстрований `ForageHarvestListener`.

- [ ] **Step 1: Створити `ForageHarvestListener`**

```java
package me.vangoo.presentation.listeners;

import me.vangoo.application.services.CustomItemService;
import me.vangoo.infrastructure.forage.ForageNodeCodec;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Optional;
import java.util.UUID;

/** Збір ноди фореджу: ЛКМ по хітбоксу-armor stand -> інгредієнт падає на землю, нода прибирається. */
public class ForageHarvestListener implements Listener {

    private final ForageNodeCodec codec;
    private final CustomItemService customItemService;
    private final Plugin plugin;

    public ForageHarvestListener(ForageNodeCodec codec, CustomItemService customItemService, Plugin plugin) {
        this.codec = codec;
        this.customItemService = customItemService;
        this.plugin = plugin;
    }

    @EventHandler
    public void onForageHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        Entity target = event.getEntity();
        if (!codec.isForageNode(target)) return;
        event.setCancelled(true);
        harvest(target);
    }

    @EventHandler
    public void onManipulate(PlayerArmorStandManipulateEvent event) {
        if (codec.isForageNode(event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    private void harvest(Entity clicked) {
        Optional<String> ingredient = codec.readIngredient(clicked);
        Location loc = clicked.getLocation();
        Optional<ItemStack> stack = ingredient.flatMap(customItemService::createItemStack);
        removePair(clicked);
        if (stack.isPresent() && loc.getWorld() != null) {
            loc.getWorld().dropItem(loc, stack.get());
            loc.getWorld().playSound(loc, Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1.0f, 1.2f);
            loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 8, 0.3, 0.3, 0.3, 0.0);
        } else {
            plugin.getLogger().warning("Forage harvest: could not resolve ingredient item for "
                    + ingredient.orElse("<none>"));
        }
    }

    private void removePair(Entity clicked) {
        Optional<UUID> partner = codec.readPartner(clicked);
        partner.ifPresent(id -> {
            Entity p = Bukkit.getEntity(id);
            if (p != null) p.remove();
        });
        clicked.remove();
    }
}
```

- [ ] **Step 2: Зареєструвати лістенер у `MysteriesAbovePlugin.registerEvents()`**

У кінці методу `registerEvents()` (після `registerEvents(services.getCreatureDamageListener(), this);`, ~рядок 244) додати:

```java
        ForageHarvestListener forageHarvestListener = new ForageHarvestListener(
                services.getForageNodeCodec(), services.getCustomItemService(), this);
        getServer().getPluginManager().registerEvents(forageHarvestListener, this);
```

Додати імпорт на початку файлу поряд з іншими `me.vangoo.presentation.listeners.*` імпортами (якщо вони не зіркові):

```java
import me.vangoo.presentation.listeners.ForageHarvestListener;
```

> Якщо `registerEvents()` уже у пакеті `me.vangoo` і лістенери імпортуються по одному — додай імпорт; якщо там wildcard-імпорт `me.vangoo.presentation.listeners.*` — імпорт не потрібен.

- [ ] **Step 3: Зібрати плагін**

Run: `mvn clean package`
Expected: BUILD SUCCESS — шейдований JAR у `target/`.

- [ ] **Step 4: In-server перевірка фореджу (ручна)**

Розгорнути JAR на тест-сервері (видалити стару `plugins/Mysteries-Above/forage.yml`, щоб згенерувалась нова). Перевірити:
1. Ходити біомами зі списку `biomes` → на вегетації з'являються 3D-ноди (видимі, з підсвіткою) тематичних допоміжних.
2. ЛКМ по ноді → інгредієнт **падає на землю**, нода (обидві сутності) зникає, чути звук + партикли.
3. Не чіпати ноду `ttl-seconds` (120) → зникає сама; `max-nearby` (5) обмежує щільність у радіусі 32.
4. Форедж працює для **не-Beyonder** гравця; працює біля спавну світу (без дистанційного гейту).
5. `/reload` або зупинка сервера → усі ноди прибрані (нема «осиротілих» armor stand / ItemDisplay).
6. ПКМ по ноді не «забирає» предмет armor stand і не ламає її (manipulate скасовано).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/vangoo/presentation/listeners/ForageHarvestListener.java \
        src/main/java/me/vangoo/MysteriesAbovePlugin.java
git commit -m "feat(forage): ForageHarvestListener — ЛКМ-збір ноди -> дроп інгредієнта"
```

---

### Task 6: Тиризація — чисте ядро (`LootTier`, `LootItem.tier`, `filterByTier`)

**Files:**
- Create: `src/main/java/me/vangoo/domain/valueobjects/LootTier.java`
- Modify: `src/main/java/me/vangoo/domain/valueobjects/LootItem.java`
- Modify: `src/main/java/me/vangoo/domain/valueobjects/LootTableData.java`
- Modify: `src/main/java/me/vangoo/infrastructure/structures/LootTableConfigLoader.java`
- Test: `src/test/java/me/vangoo/domain/valueobjects/LootTableDataTest.java`

**Interfaces:**
- Produces: `enum LootTier { BASE, RARE }`; `LootItem(..., LootTier tier)` (+ 4-арг конструктор, дефолт BASE); `LootTableData.filterByTier(Set<LootTier>) -> LootTableData`.

> `domain.valueobjects` **не** в `PURE_DOMAIN` (там є Bukkit-залежні CustomItem/RecordedEvent), тож тест — звичайний JUnit. 4-арг конструктор `LootItem` зберігає всі наявні виклики (`CreatureConfigLoader`, тести) без змін.

- [ ] **Step 1: Написати фейл-тест `LootTableDataTest`**

```java
package me.vangoo.domain.valueobjects;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LootTableDataTest {

    @Test
    void filterKeepsOnlyAllowedTier() {
        LootItem base = new LootItem("a", 1, 1, 1, LootTier.BASE);
        LootItem rare = new LootItem("b", 1, 1, 1, LootTier.RARE);
        LootTableData t = new LootTableData(List.of(base, rare), 1, 2);
        LootTableData onlyBase = t.filterByTier(EnumSet.of(LootTier.BASE));
        assertEquals(1, onlyBase.items().size());
        assertEquals("a", onlyBase.items().get(0).itemId());
    }

    @Test
    void filterBothTiersKeepsAll() {
        LootTableData t = new LootTableData(List.of(
                new LootItem("a", 1, 1, 1, LootTier.BASE),
                new LootItem("b", 1, 1, 1, LootTier.RARE)), 1, 2);
        assertEquals(2, t.filterByTier(EnumSet.allOf(LootTier.class)).items().size());
    }

    @Test
    void defaultConstructorIsBase() {
        assertEquals(LootTier.BASE, new LootItem("a", 1, 1, 1).tier());
    }

    @Test
    void filterPreservesMinMax() {
        LootTableData t = new LootTableData(List.of(new LootItem("a", 1, 1, 1, LootTier.BASE)), 3, 5);
        LootTableData f = t.filterByTier(EnumSet.of(LootTier.BASE));
        assertEquals(3, f.minItems());
        assertEquals(5, f.maxItems());
    }

    @Test
    void emptyAllowedYieldsEmpty() {
        LootTableData t = new LootTableData(List.of(new LootItem("a", 1, 1, 1, LootTier.BASE)), 1, 2);
        assertTrue(t.filterByTier(EnumSet.noneOf(LootTier.class)).items().isEmpty());
    }
}
```

- [ ] **Step 2: Запустити — має не скомпілюватись**

Run: `mvn test -Dtest=LootTableDataTest`
Expected: FAIL — `LootTier` та 5-арг `LootItem` / `filterByTier` не існують.

- [ ] **Step 3: Створити `LootTier`**

```java
package me.vangoo.domain.valueobjects;

/** Рівень рідкості предмета лута: base (звичайні скрині) / rare (данжі/спец-структури). */
public enum LootTier { BASE, RARE }
```

- [ ] **Step 4: Додати поле `tier` у `LootItem`** (повний новий вміст файлу)

```java
package me.vangoo.domain.valueobjects;

public record LootItem(
        String itemId,
        int weight,
        int minAmount,
        int maxAmount,
        LootTier tier
) {
    /** Зворотно-сумісний конструктор: без указаного тіру предмет = BASE. */
    public LootItem(String itemId, int weight, int minAmount, int maxAmount) {
        this(itemId, weight, minAmount, maxAmount, LootTier.BASE);
    }
}
```

- [ ] **Step 5: Додати `filterByTier` у `LootTableData`** (повний новий вміст файлу)

```java
package me.vangoo.domain.valueobjects;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record LootTableData(List<LootItem> items, int minItems, int maxItems) {
    public LootTableData(List<LootItem> items) {
        this(items, 3, 8);
    }

    public LootTableData {
        Objects.requireNonNull(items, "items must not be null");
        if (minItems < 0) minItems = 0;
        if (maxItems < minItems) maxItems = minItems;
    }

    /** Підмножина таблиці лише з предметами дозволених тірів (зберігає minItems/maxItems). */
    public LootTableData filterByTier(Set<LootTier> allowed) {
        List<LootItem> filtered = new ArrayList<>();
        for (LootItem i : items) {
            if (allowed.contains(i.tier())) filtered.add(i);
        }
        return new LootTableData(filtered, minItems, maxItems);
    }
}
```

- [ ] **Step 6: Читати `tier` у `LootTableConfigLoader`**

У `parseLootItems(...)`, після рядків читання `amount_min`/`amount_max` і перед `items.add(...)` (зараз рядок 106), замінити блок:

```java
            int weight = itemSection.getInt("weight", 1);
            int amountMin = itemSection.getInt("amount_min", 1);
            int amountMax = itemSection.getInt("amount_max", 1);
```
…додавши читання тіру, а в кінці — оновити конструктор. Конкретно:

1) Після `int amountMax = itemSection.getInt("amount_max", 1);` додати:

```java
            String tierStr = itemSection.getString("tier", "base");
            me.vangoo.domain.valueobjects.LootTier tier;
            try {
                tier = me.vangoo.domain.valueobjects.LootTier.valueOf(tierStr.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid tier '" + tierStr + "' for item '" + key + "'. Using BASE");
                tier = me.vangoo.domain.valueobjects.LootTier.BASE;
            }
```

2) Замінити рядок `items.add(new LootItem(itemId, weight, amountMin, amountMax));` на:

```java
            items.add(new LootItem(itemId, weight, amountMin, amountMax, tier));
```

- [ ] **Step 7: Запустити тести — мають пройти**

Run: `mvn test -Dtest=LootTableDataTest`
Expected: PASS.

- [ ] **Step 8: Прогнати весь набір тестів (нічого не зламали)**

Run: `mvn test`
Expected: PASS (усі тести, включно з `CreatureSelectorTest` — він використовує `new LootTableData(List.of(), 1, 2)`, що лишилось валідним; `LootItem` 4-арг конструктор збережено).

- [ ] **Step 9: Commit**

```bash
git add src/main/java/me/vangoo/domain/valueobjects/LootTier.java \
        src/main/java/me/vangoo/domain/valueobjects/LootItem.java \
        src/main/java/me/vangoo/domain/valueobjects/LootTableData.java \
        src/main/java/me/vangoo/infrastructure/structures/LootTableConfigLoader.java \
        src/test/java/me/vangoo/domain/valueobjects/LootTableDataTest.java
git commit -m "feat(loot): LootTier + LootItem.tier + LootTableData.filterByTier + parse"
```

---

### Task 7: Тиро-залежні джерела + розмітка `global_loot.yml`

**Files:**
- Modify: `src/main/java/me/vangoo/presentation/listeners/VanillaStructureLootListener.java`
- Modify: `src/main/java/me/vangoo/presentation/listeners/ArchaeologyLootListener.java`
- Modify: `src/main/resources/global_loot.yml`

**Interfaces:**
- Consumes: `LootTableData.filterByTier`, `LootTier` (Task 6).

> In-server перевірка. Логіка ролу (`LootGenerationService`) не змінюється — лістенери лише фільтрують таблицю перед генерацією.

- [ ] **Step 1: `VanillaStructureLootListener` — мапа ключ→(шанс, тіри) + фільтр** (повний новий вміст файлу)

```java
package me.vangoo.presentation.listeners;

import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.LootTier;
import me.vangoo.infrastructure.structures.LootGenerationService;
import me.vangoo.domain.valueobjects.LootTableData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.logging.Logger;

/**
 * Listener для додавання кастомного лута до ванільних структур Minecraft.
 * Тиризація: звичайні скрині тягнуть лише BASE; данжі/спец-структури — BASE + RARE.
 */
public class VanillaStructureLootListener implements Listener {

    private final Logger logger;
    private final LootGenerationService lootService;
    private final LootTableData globalLootTable;
    private final BeyonderService beyonderService;
    private final Random random = new Random();

    private final Map<String, StructureLootRule> enabledStructures;

    /** Правило луту структури: шанс додати кастомний лут + дозволені тіри. */
    private record StructureLootRule(double chance, Set<LootTier> tiers) {}

    public VanillaStructureLootListener(
            Plugin plugin,
            LootGenerationService lootService,
            LootTableData globalLootTable,
            BeyonderService beyonderService) {
        this.logger = plugin.getLogger();
        this.lootService = lootService;
        this.globalLootTable = globalLootTable;
        this.beyonderService = beyonderService;
        this.enabledStructures = loadEnabledVanillaStructures();

        logger.info("VanillaStructureLootListener initialized for " + enabledStructures.size() + " structure types");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLootGenerate(LootGenerateEvent event) {
        String lootTableKey = event.getLootTable().getKey().toString();

        StructureLootRule rule = getRule(lootTableKey);
        if (rule == null || rule.chance() <= 0.0) {
            return;
        }
        if (random.nextDouble() > rule.chance()) {
            return;
        }

        addCustomLoot(event, lootTableKey, rule.tiers());
    }

    private StructureLootRule getRule(String lootTableKey) {
        for (Map.Entry<String, StructureLootRule> entry : enabledStructures.entrySet()) {
            if (lootTableKey.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Map<String, StructureLootRule> loadEnabledVanillaStructures() {
        Map<String, StructureLootRule> structures = new HashMap<>();

        Set<LootTier> base = EnumSet.of(LootTier.BASE);
        Set<LootTier> baseRare = EnumSet.of(LootTier.BASE, LootTier.RARE);

        // Звичайні скрині -> лише BASE
        structures.put("shipwreck", new StructureLootRule(0.10, base));
        structures.put("mineshaft", new StructureLootRule(0.10, base));
        structures.put("desert_pyramid", new StructureLootRule(0.10, base));
        structures.put("jungle_temple", new StructureLootRule(0.10, base));
        structures.put("buried_treasure", new StructureLootRule(0.15, base));
        structures.put("ocean_ruin_warm", new StructureLootRule(0.10, base));
        structures.put("ocean_ruin_cold", new StructureLootRule(0.10, base));
        structures.put("ruined_portal", new StructureLootRule(0.20, base));
        structures.put("simple_dungeon", new StructureLootRule(0.10, base));
        structures.put("trial_chambers/supply", new StructureLootRule(0.10, base));

        // Данжі / спец-структури -> BASE + RARE
        structures.put("mansion", new StructureLootRule(0.20, baseRare));
        structures.put("ancient_city", new StructureLootRule(0.25, baseRare));
        structures.put("bastion", new StructureLootRule(0.20, baseRare));
        structures.put("nether_bridge", new StructureLootRule(0.05, baseRare));
        structures.put("end_city", new StructureLootRule(0.10, baseRare));
        structures.put("stronghold", new StructureLootRule(0.10, baseRare));
        structures.put("pillager_outpost", new StructureLootRule(0.20, baseRare));
        structures.put("trial_chambers/corridor", new StructureLootRule(0.10, baseRare));
        structures.put("mysteries", new StructureLootRule(0.15, baseRare));
        structures.put("nova_structures", new StructureLootRule(0.15, baseRare));

        logger.info("Enabled vanilla structures for custom loot: " + structures.size());
        return structures;
    }

    private void addCustomLoot(LootGenerateEvent event, String lootTableKey, Set<LootTier> tiers) {
        if (globalLootTable == null || globalLootTable.items().isEmpty()) {
            return;
        }

        LootTableData tiered = globalLootTable.filterByTier(tiers);
        if (tiered.items().isEmpty()) {
            return;
        }

        Beyonder beyonder = null;
        if (event.getEntity() != null) {
            beyonder = beyonderService.getBeyonder(event.getEntity().getUniqueId());
        }

        List<ItemStack> currentLoot = event.getLoot();
        int itemsToAdd = (Math.random() <= 0.20) ? 2 : 1;

        logger.fine("Adding " + itemsToAdd + " custom items (tiers " + tiers + ") to " + lootTableKey);

        List<ItemStack> generatedLoot = lootService.generateLoot(tiered, itemsToAdd, false, beyonder);
        currentLoot.addAll(generatedLoot);
    }
}
```

- [ ] **Step 2: `ArchaeologyLootListener` — фільтр BASE**

Додати імпорти на початку файлу (поряд із наявними):

```java
import me.vangoo.domain.valueobjects.LootTier;
import java.util.EnumSet;
```

У методі `replaceBlockItem(...)` замінити виклик генерації:

```java
        // Генеруємо 1 кастомний предмет
        List<ItemStack> generatedLoot = lootService.generateLoot(
                globalLootTable,
                1,
                false,
                beyonder
        );
```
на (археологія = лише BASE):

```java
        // Археологія -> лише BASE-тір
        LootTableData baseTable = globalLootTable.filterByTier(EnumSet.of(LootTier.BASE));
        if (baseTable.items().isEmpty()) {
            logger.warning("ArchaeologyLootListener: no BASE-tier items to generate");
            return;
        }
        List<ItemStack> generatedLoot = lootService.generateLoot(
                baseTable,
                1,
                false,
                beyonder
        );
```

> `LootTableData` вже імпортовано у цьому файлі. `globalLootTable` — поле класу.

- [ ] **Step 3: Розмітити `tier: rare` у `global_loot.yml`**

Для **кожного** з наведених ключів додати рядок `      tier: rare` (6 пробелів відступу — той самий рівень, що `item_id`) всередину блоку предмета. **Усі інші предмети не чіпати** (дефолт = base).

Visionary: `black_hunting_lizard_spinal_fluid`, `illusory_chime_tree_fruit`, `mind_crystal_powder`, `split_personality_essence`, `dream_catcher_heart`, `mind_illusion_crystal`, `adult_mind_dragon_blood`, `visionary_recipe_6`, `visionary_recipe_5`, `visionary_potion_7`.

Door: `asmann_complete_brain`, `cursed_wraith_artifact`, `imbued_ink_sac`, `void_drifter_heart`, `astral_mist_essence`, `wayfinder_tree_root`, `door_recipe_6`, `door_recipe_5`, `door_potion_7`.

Justiciar: `heart_of_the_silent_verdict`, `fragment_of_a_sealed_domain`, `ashes_of_a_forbidden_decree`, `chains_of_collective_fear`, `core_of_retributive_authority`, `brand_of_the_unforgiving_oath`, `ashes_of_a_public_execution`, `shackles_of_the_guilty`, `justiciar_recipe_6`, `justiciar_recipe_5`, `justiciar_potion_7`.

WhiteTower: `prismatic_chameleon_heart`, `liquid_silver_phantom_residue`, `mimicry_moss_spores`, `devastated_blank_mask`, `void_beholder_petrified_eye`, `condensed_astral_nebula`, `ancient_rune_powder`, `pure_spirituality_tear`, `white_tower_recipe_6`, `white_tower_recipe_5`, `white_tower_potion_7`.

Приклад (для `dream_catcher_heart`):
```yaml
    dream_catcher_heart:
      item_id: "dream_catcher_heart"
      weight: 5
      amount_min: 1
      amount_max: 1
      tier: rare
```

> Зауваж: розклад base/rare у роадмапі/спеку — чернетка; це **дані**, користувач їх вільно правитиме. Завдання тут — закласти консистентний стартовий стан.

- [ ] **Step 4: Зібрати плагін**

Run: `mvn clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 5: In-server перевірка тиризації (ручна)**

Розгорнути JAR (видалити стару `plugins/Mysteries-Above/global_loot.yml`, щоб перечиталась із tier). Перевірити:
1. Відкрити звичайну скриню (`shipwreck`/`mineshaft`/`buried_treasure`) → серед доданого кастомного лута **немає** rare-предметів (лише Seq 9–7 інгредієнти, рецепти 9–7, зілля 9–8).
2. Активувати данж/спец-структуру (`ancient_city` / `trial_chambers/corridor` / `mysteries`) → можливі **rare** (Seq 6–5 інгредієнти, рецепти 6–5, зілля 7).
3. Археологія (brushing) → лише base.
4. **Характеристика не випадає ніде** (наявний `createItemFromId` відхиляє `characteristic:`).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/me/vangoo/presentation/listeners/VanillaStructureLootListener.java \
        src/main/java/me/vangoo/presentation/listeners/ArchaeologyLootListener.java \
        src/main/resources/global_loot.yml
git commit -m "feat(loot): тиро-залежні джерела (base/rare) + розмітка global_loot.yml"
```

---

## Self-Review

**Spec coverage:**
- Канал форедж: чисте правило (Task 1) → конфіг (Task 2) → нода+кодек+пошук (Task 3) → планувальник+wiring (Task 4) → збір ЛКМ+реєстрація (Task 5). ✔
- Біом-тематично, для всіх: `ForageSelector.pickForBiome` без bias; спавнер не вимагає Beyonder, без дистанційного гейту. ✔
- Видима 3D-модель + ЛКМ-збір→дроп: `ForageNode` (ItemDisplay+ArmorStand) + `ForageHarvestListener` (`EntityDamageByEntityEvent`). ✔
- Лише auxiliary; Характеристики недосяжні: forage.yml містить лише auxiliary; форедж не торкається лут-таблиць. ✔
- TTL + max-nearby + чистка на disable: `ForageNodeSpawner` (prune + stop()). ✔
- Тиризація base/rare: `LootTier`+`LootItem.tier`+`filterByTier` (Task 6) → джерела фільтрують (Task 7). ✔
- ArchUnit `domain.forage` чистий: додано в PURE_DOMAIN (Task 1), пакет без Bukkit. ✔

**Placeholder scan:** немає TBD/TODO; усі кроки з кодом мають повний код; in-server кроки — конкретні чеклісти.

**Type consistency:** `ForageConfig` аксесори (`intervalSeconds/chance/maxNearby/ttlSeconds/searchRadius/vegetation/biomes`) збігаються між Task 2 і Task 4. `ForageNodeCodec` сигнатури (`tag/isForageNode/readIngredient/readPartner`) збігаються між Task 3, 4, 5. `LootItem(...,tier)` + 4-арг конструктор узгоджені (Task 6) і не ламають наявні виклики. `filterByTier(Set<LootTier>)` однаковий у Task 6/7. `ForageNode.spawn(Location, ItemStack, String, ForageNodeCodec)` узгоджено зі спавнером (Task 4). ✔
