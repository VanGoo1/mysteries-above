# Економіка Характеристик — Спек 3 (полювання) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Додати кастомних міфічних істот, що дропають **інгредієнти** (ніколи Характеристики), зі спавном командою, у дикій природі та біля кастомних структур.

**Architecture:** Чисте ядро `me.vangoo.domain.creatures` (Bukkit-вільні VO + правило вибору `CreatureSelector`, юніт-тестоване) + шар ефектів `infrastructure.creatures`/`presentation` (завантажувач конфіга, PDC-тег, спавнер з appearance-швом, лістенери смерті/спавну, команда). Дроп проходить через наявний `LootGenerationService`, який уже відхиляє `characteristic:` id — інваріант «істоти не дають Характеристик» захищений за побудовою.

**Tech Stack:** Java 21, Spigot/Bukkit API 1.21.1, Maven (maven-shade), JUnit 5, ArchUnit.

## Global Constraints

- **Мова рядків:** усі нові user-facing рядки — **українською** (як решта плагіна).
- **Збірка:** `mvn clean package`. Якщо `mvn` не в PATH (exit 127) — запускати **bundled Maven IntelliJ** (`mvn.cmd`) через PowerShell. Default goal = `package`.
- **Юніт-тести:** лише для **чистого domain** (`CreatureSelector`). Інфра/ефекти (кодеки, завантажувачі, спавнер, лістенери, команда) перевіряються **in-server**, не мокаються — як `CharacteristicCodec`/`LootTableConfigLoader` у Спеках 1–2.
- **Інваріант луту:** дроп істоти ЗАВЖДИ йде через `LootGenerationService.generateLoot(...)` → `createItemFromId`, який відхиляє `characteristic:`. Жодного прямого мінту предметів у лістенері смерті.
- **domain Bukkit-вільний:** `me.vangoo.domain.creatures` не імпортує `org.bukkit.*` (тип ентіті/біом/слот — рядки). `ArchitectureTest` пінить це.
- **Без BossBar** у цьому спеку. **Повна заміна** ванільних дропів базового моба (`clearVanillaDrops`, дефолт `true`).

---

### Task 1: Чисте ядро `domain.creatures` (VO + CreatureSelector) + ArchUnit

**Files:**
- Create: `src/main/java/me/vangoo/domain/creatures/CreatureTier.java`
- Create: `src/main/java/me/vangoo/domain/creatures/CreatureStats.java`
- Create: `src/main/java/me/vangoo/domain/creatures/SpawnRule.java`
- Create: `src/main/java/me/vangoo/domain/creatures/CreatureDefinition.java`
- Create: `src/main/java/me/vangoo/domain/creatures/CreatureSelector.java`
- Modify: `src/test/java/me/vangoo/architecture/ArchitectureTest.java:22-27`
- Test: `src/test/java/me/vangoo/domain/creatures/CreatureSelectorTest.java`

**Interfaces:**
- Consumes: `me.vangoo.domain.valueobjects.LootTableData` (наявний record).
- Produces:
  - `enum CreatureTier { COMMON, APEX }`
  - `record CreatureStats(double health, double damage, double speed, double scale)`
  - `record SpawnRule(List<String> naturalBiomes, List<String> naturalReplaces, double naturalChance, List<String> structureKeys, double structureChance)`
  - `record CreatureDefinition(String id, String baseEntityType, String displayName, CreatureTier tier, CreatureStats stats, Map<String,String> equipment, String appearance, LootTableData loot, SpawnRule spawn, boolean clearVanillaDrops)`
  - `class CreatureSelector(Collection<CreatureDefinition>)` з `Optional<CreatureDefinition> pickForBiome(String biome, String baseEntityType, double roll)` та `Optional<CreatureDefinition> pickForStructure(String structureKey, double roll)`.

- [ ] **Step 1: Написати падаючий тест `CreatureSelectorTest`**

Створити `src/test/java/me/vangoo/domain/creatures/CreatureSelectorTest.java`:

```java
package me.vangoo.domain.creatures;

import me.vangoo.domain.valueobjects.LootTableData;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CreatureSelectorTest {

    private CreatureDefinition def(String id, SpawnRule spawn) {
        return new CreatureDefinition(
                id, "GUARDIAN", "§3" + id, CreatureTier.COMMON,
                new CreatureStats(30, 6, 0.25, 1.2),
                Map.of(), "vanilla",
                new LootTableData(List.of(), 1, 2),
                spawn, true);
    }

    private SpawnRule natural(double chance) {
        return new SpawnRule(List.of("OCEAN"), List.of("GUARDIAN"), chance, List.of(), 0.0);
    }

    private SpawnRule structure(double chance) {
        return new SpawnRule(List.of(), List.of(), 0.0, List.of("mysteries"), chance);
    }

    @Test
    void biomeMatchWithinChanceReturnsCreature() {
        CreatureSelector s = new CreatureSelector(List.of(def("a", natural(0.5))));
        assertTrue(s.pickForBiome("OCEAN", "GUARDIAN", 0.4).isPresent());
    }

    @Test
    void biomeRollBeyondChanceReturnsEmpty() {
        CreatureSelector s = new CreatureSelector(List.of(def("a", natural(0.5))));
        assertTrue(s.pickForBiome("OCEAN", "GUARDIAN", 0.6).isEmpty());
    }

    @Test
    void wrongBiomeReturnsEmpty() {
        CreatureSelector s = new CreatureSelector(List.of(def("a", natural(0.5))));
        assertTrue(s.pickForBiome("PLAINS", "GUARDIAN", 0.1).isEmpty());
    }

    @Test
    void wrongBaseEntityReturnsEmpty() {
        CreatureSelector s = new CreatureSelector(List.of(def("a", natural(0.5))));
        assertTrue(s.pickForBiome("OCEAN", "ZOMBIE", 0.1).isEmpty());
    }

    @Test
    void structureKeyContainsMatchReturnsCreature() {
        CreatureSelector s = new CreatureSelector(List.of(def("a", structure(0.5))));
        assertTrue(s.pickForStructure("minecraft:mysteries/dungeon", 0.4).isPresent());
    }

    @Test
    void structureKeyNoMatchReturnsEmpty() {
        CreatureSelector s = new CreatureSelector(List.of(def("a", structure(0.5))));
        assertTrue(s.pickForStructure("minecraft:village/house", 0.1).isEmpty());
    }

    @Test
    void weightedSegmentsPickByRoll() {
        CreatureDefinition a = def("a", natural(0.2));
        CreatureDefinition b = def("b", natural(0.3));
        CreatureSelector s = new CreatureSelector(List.of(a, b));
        assertEquals("a", s.pickForBiome("OCEAN", "GUARDIAN", 0.1).get().id());
        assertEquals("b", s.pickForBiome("OCEAN", "GUARDIAN", 0.35).get().id());
        assertTrue(s.pickForBiome("OCEAN", "GUARDIAN", 0.9).isEmpty());
    }

    @Test
    void emptyRegistryReturnsEmpty() {
        CreatureSelector s = new CreatureSelector(List.of());
        assertTrue(s.pickForBiome("OCEAN", "GUARDIAN", 0.0).isEmpty());
        assertTrue(s.pickForStructure("mysteries", 0.0).isEmpty());
    }
}
```

- [ ] **Step 2: Запустити тест — переконатися, що падає (не компілюється)**

Run: `mvn test -Dtest=CreatureSelectorTest`
Expected: FAIL — класи `CreatureDefinition`/`CreatureSelector`/… не існують (compilation error).

- [ ] **Step 3: Створити VO-класи**

`CreatureTier.java`:
```java
package me.vangoo.domain.creatures;

public enum CreatureTier {
    COMMON,
    APEX
}
```

`CreatureStats.java`:
```java
package me.vangoo.domain.creatures;

public record CreatureStats(double health, double damage, double speed, double scale) {}
```

`SpawnRule.java`:
```java
package me.vangoo.domain.creatures;

import java.util.List;

public record SpawnRule(
        List<String> naturalBiomes,
        List<String> naturalReplaces,
        double naturalChance,
        List<String> structureKeys,
        double structureChance) {}
```

`CreatureDefinition.java`:
```java
package me.vangoo.domain.creatures;

import me.vangoo.domain.valueobjects.LootTableData;

import java.util.Map;

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
        boolean clearVanillaDrops) {}
```

- [ ] **Step 4: Реалізувати `CreatureSelector`**

`CreatureSelector.java`:
```java
package me.vangoo.domain.creatures;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Чисте правило вибору істоти для спавну (аналог BrewMatcher). Детерміноване при поданому roll.
 * Кожен кандидат займає сегмент [cumulative, cumulative+chance) на осі [0,1); roll за сумою
 * всіх шансів означає «спавну немає».
 */
public final class CreatureSelector {

    private final List<CreatureDefinition> creatures;

    public CreatureSelector(Collection<CreatureDefinition> creatures) {
        this.creatures = List.copyOf(creatures);
    }

    public Optional<CreatureDefinition> pickForBiome(String biome, String baseEntityType, double roll) {
        double cumulative = 0.0;
        for (CreatureDefinition def : creatures) {
            SpawnRule s = def.spawn();
            if (s.naturalChance() <= 0.0) continue;
            if (!s.naturalBiomes().contains(biome)) continue;
            if (!s.naturalReplaces().contains(baseEntityType)) continue;
            cumulative += s.naturalChance();
            if (roll < cumulative) return Optional.of(def);
        }
        return Optional.empty();
    }

    public Optional<CreatureDefinition> pickForStructure(String structureKey, double roll) {
        double cumulative = 0.0;
        for (CreatureDefinition def : creatures) {
            SpawnRule s = def.spawn();
            if (s.structureChance() <= 0.0) continue;
            if (s.structureKeys().stream().noneMatch(structureKey::contains)) continue;
            cumulative += s.structureChance();
            if (roll < cumulative) return Optional.of(def);
        }
        return Optional.empty();
    }
}
```

- [ ] **Step 5: Додати `domain.creatures` до чистого скоупу ArchUnit**

У `src/test/java/me/vangoo/architecture/ArchitectureTest.java` розширити масив `PURE_DOMAIN`:
```java
    private static final String[] PURE_DOMAIN = {
            "me.vangoo.domain.entities",
            "me.vangoo.domain.services",
            "me.vangoo.domain.spells",
            "me.vangoo.domain.brewing",
            "me.vangoo.domain.creatures"
    };
```

- [ ] **Step 6: Запустити тести — переконатися, що проходять**

Run: `mvn test -Dtest=CreatureSelectorTest,ArchitectureTest`
Expected: PASS (усі кейси селектора зелені; ArchUnit зелений — domain.creatures Bukkit-вільний).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/me/vangoo/domain/creatures src/test/java/me/vangoo/domain/creatures src/test/java/me/vangoo/architecture/ArchitectureTest.java
git commit -m "feat(hunting): pure domain.creatures VOs + CreatureSelector"
```

---

### Task 2: `CreatureConfigLoader` + ресурс `creatures.yml`

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/creatures/CreatureConfigLoader.java`
- Create: `src/main/resources/creatures.yml`

**Interfaces:**
- Consumes: `CreatureDefinition`, `CreatureStats`, `SpawnRule`, `CreatureTier` (Task 1); `LootTableData`, `LootItem` (наявні).
- Produces: `class CreatureConfigLoader(Plugin)` з `Map<String, CreatureDefinition> load()`.

- [ ] **Step 1: Створити ресурс `creatures.yml`**

`src/main/resources/creatures.yml` (чернетковий вміст; id інгредієнтів узяті з `global_loot.yml`):
```yaml
# creatures.yml — міфічні істоти (полювання). Дають ЛИШЕ інгредієнти, ніколи Характеристики.
# tier: common (Seq 9-6, дика природа) | apex (Seq 5-0, біля структур).
creatures:
  manhal_fish:
    base_entity: GUARDIAN
    display_name: "§3Манхал-риба"
    tier: common
    stats: { health: 30, damage: 6, speed: 0.25, scale: 1.2 }
    appearance: vanilla
    clear_vanilla_drops: true
    loot:
      min_items: 1
      max_items: 2
      items:
        - { id: manhal_fish_eyeball, weight: 70, min: 1, max: 2 }
        - { id: "recipe:Visionary:9", weight: 10, min: 1, max: 1 }
    spawn:
      natural: { biomes: [OCEAN, DEEP_OCEAN], replace: [GUARDIAN, DROWNED], chance: 0.03 }
      structure: { keys: [], chance: 0.0 }

  mind_dragon:
    base_entity: RAVAGER
    display_name: "§5Розумовий Дракон"
    tier: apex
    stats: { health: 200, damage: 18, speed: 0.30, scale: 1.6 }
    appearance: vanilla
    clear_vanilla_drops: true
    loot:
      min_items: 1
      max_items: 2
      items:
        - { id: adult_mind_dragon_blood, weight: 60, min: 1, max: 1 }
    spawn:
      natural: { biomes: [], replace: [], chance: 0.0 }
      structure: { keys: [mysteries, nova_structures, ancient_city], chance: 0.35 }
```

- [ ] **Step 2: Реалізувати `CreatureConfigLoader`**

`CreatureConfigLoader.java`:
```java
package me.vangoo.infrastructure.creatures;

import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.domain.creatures.CreatureStats;
import me.vangoo.domain.creatures.CreatureTier;
import me.vangoo.domain.creatures.SpawnRule;
import me.vangoo.domain.valueobjects.LootItem;
import me.vangoo.domain.valueobjects.LootTableData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;

/** Завантажує creatures.yml у Map&lt;id, CreatureDefinition&gt;. Дзеркалить LootTableConfigLoader. */
public class CreatureConfigLoader {

    private final Plugin plugin;

    public CreatureConfigLoader(Plugin plugin) {
        this.plugin = plugin;
    }

    public Map<String, CreatureDefinition> load() {
        File file = new File(plugin.getDataFolder(), "creatures.yml");
        if (!file.exists()) {
            plugin.saveResource("creatures.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        Map<String, CreatureDefinition> result = new LinkedHashMap<>();

        ConfigurationSection root = config.getConfigurationSection("creatures");
        if (root == null) {
            plugin.getLogger().warning("No 'creatures' section in creatures.yml. No creatures loaded.");
            return result;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection c = root.getConfigurationSection(id);
            if (c == null) continue;
            try {
                result.put(id, parseCreature(id, c));
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipping creature '" + id + "': " + ex.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + result.size() + " creatures from creatures.yml");
        return result;
    }

    private CreatureDefinition parseCreature(String id, ConfigurationSection c) {
        String baseEntity = c.getString("base_entity");
        if (baseEntity == null || baseEntity.isEmpty()) {
            throw new IllegalArgumentException("missing base_entity");
        }
        String displayName = c.getString("display_name", id);
        CreatureTier tier = CreatureTier.valueOf(c.getString("tier", "common").toUpperCase(Locale.ROOT));
        boolean clearDrops = c.getBoolean("clear_vanilla_drops", true);
        String appearance = c.getString("appearance", "vanilla");

        CreatureStats stats = parseStats(c.getConfigurationSection("stats"));
        Map<String, String> equipment = parseEquipment(c.getConfigurationSection("equipment"));
        LootTableData loot = parseLoot(c.getConfigurationSection("loot"));
        SpawnRule spawn = parseSpawn(c.getConfigurationSection("spawn"));

        return new CreatureDefinition(id, baseEntity.toUpperCase(Locale.ROOT), displayName, tier,
                stats, equipment, appearance, loot, spawn, clearDrops);
    }

    private CreatureStats parseStats(ConfigurationSection s) {
        if (s == null) return new CreatureStats(20, 4, 0.25, 1.0);
        return new CreatureStats(
                s.getDouble("health", 20),
                s.getDouble("damage", 4),
                s.getDouble("speed", 0.25),
                s.getDouble("scale", 1.0));
    }

    private Map<String, String> parseEquipment(ConfigurationSection s) {
        Map<String, String> map = new LinkedHashMap<>();
        if (s == null) return map;
        for (String slot : s.getKeys(false)) {
            map.put(slot.toUpperCase(Locale.ROOT), s.getString(slot));
        }
        return map;
    }

    private LootTableData parseLoot(ConfigurationSection s) {
        if (s == null) return new LootTableData(new ArrayList<>(), 1, 1);
        int min = s.getInt("min_items", 1);
        int max = s.getInt("max_items", 1);
        List<LootItem> items = new ArrayList<>();
        for (Map<?, ?> raw : s.getMapList("items")) {
            Object idObj = raw.get("id");
            if (idObj == null) {
                plugin.getLogger().warning("Loot item missing 'id' in a creature; skipping entry");
                continue;
            }
            String itemId = String.valueOf(idObj);
            int weight = toInt(raw.get("weight"), 1);
            int amin = toInt(raw.get("min"), 1);
            int amax = toInt(raw.get("max"), amin);
            if (weight <= 0) weight = 1;
            if (amin < 1) amin = 1;
            if (amax < amin) amax = amin;
            items.add(new LootItem(itemId, weight, amin, amax));
        }
        return new LootTableData(items, min, max);
    }

    private SpawnRule parseSpawn(ConfigurationSection s) {
        if (s == null) {
            return new SpawnRule(List.of(), List.of(), 0.0, List.of(), 0.0);
        }
        ConfigurationSection nat = s.getConfigurationSection("natural");
        ConfigurationSection str = s.getConfigurationSection("structure");
        List<String> biomes = nat == null ? List.of() : upper(nat.getStringList("biomes"));
        List<String> replace = nat == null ? List.of() : upper(nat.getStringList("replace"));
        double natChance = nat == null ? 0.0 : nat.getDouble("chance", 0.0);
        List<String> keys = str == null ? List.of() : str.getStringList("keys");
        double strChance = str == null ? 0.0 : str.getDouble("chance", 0.0);
        return new SpawnRule(biomes, replace, natChance, keys, strChance);
    }

    private List<String> upper(List<String> in) {
        List<String> out = new ArrayList<>(in.size());
        for (String v : in) out.add(v.toUpperCase(Locale.ROOT));
        return out;
    }

    private int toInt(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        if (o == null) return def;
        try { return Integer.parseInt(String.valueOf(o)); } catch (NumberFormatException e) { return def; }
    }
}
```

- [ ] **Step 3: Зібрати проєкт**

Run: `mvn clean package`
Expected: BUILD SUCCESS (компілюється; наявні тести зелені). Коректність парсингу перевіряється in-server у Task 10.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/creatures/CreatureConfigLoader.java src/main/resources/creatures.yml
git commit -m "feat(hunting): creatures.yml + CreatureConfigLoader"
```

---

### Task 3: `CreatureCodec` (PDC-тег істоти)

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/creatures/CreatureCodec.java`

**Interfaces:**
- Consumes: `org.bukkit.plugin.Plugin`.
- Produces: `class CreatureCodec(Plugin)` з `void tag(Entity, String creatureId)`, `boolean isCreature(Entity)`, `Optional<String> readId(Entity)`.

- [ ] **Step 1: Реалізувати `CreatureCodec`**

`CreatureCodec.java` (патерн `WardenRemnantCodec`):
```java
package me.vangoo.infrastructure.creatures;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.Optional;

/** Тегує сутність id кастомної істоти у її PersistentDataContainer. */
public final class CreatureCodec {

    private final NamespacedKey idKey;

    public CreatureCodec(Plugin plugin) {
        this.idKey = new NamespacedKey(plugin, "creature_id");
    }

    public void tag(Entity entity, String creatureId) {
        if (entity == null || creatureId == null) return;
        entity.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, creatureId);
    }

    public boolean isCreature(Entity entity) {
        return entity != null
                && entity.getPersistentDataContainer().has(idKey, PersistentDataType.STRING);
    }

    public Optional<String> readId(Entity entity) {
        if (entity == null) return Optional.empty();
        PersistentDataContainer pdc = entity.getPersistentDataContainer();
        return Optional.ofNullable(pdc.get(idKey, PersistentDataType.STRING));
    }
}
```

- [ ] **Step 2: Зібрати проєкт**

Run: `mvn clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/creatures/CreatureCodec.java
git commit -m "feat(hunting): CreatureCodec PDC tag"
```

---

### Task 4: `CreatureAppearance` + `VanillaAppearance` (шов вигляду)

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/creatures/CreatureAppearance.java`
- Create: `src/main/java/me/vangoo/infrastructure/creatures/VanillaAppearance.java`

**Interfaces:**
- Consumes: `CreatureDefinition` (Task 1); `me.vangoo.application.services.CustomItemService` (наявний, `Optional<ItemStack> createItemStack(String)`).
- Produces: `interface CreatureAppearance { void apply(LivingEntity, CreatureDefinition); }`; `class VanillaAppearance(CustomItemService) implements CreatureAppearance`.

- [ ] **Step 1: Створити інтерфейс `CreatureAppearance`**

`CreatureAppearance.java`:
```java
package me.vangoo.infrastructure.creatures;

import me.vangoo.domain.creatures.CreatureDefinition;
import org.bukkit.entity.LivingEntity;

/**
 * Шов вигляду істоти. Зараз єдина реалізація — {@link VanillaAppearance} (ванільний моб + ім'я +
 * розмір + екіп). Майбутнє: ModelEngineAppearance — підміна без зміни логіки спавну/дропу.
 */
public interface CreatureAppearance {
    void apply(LivingEntity entity, CreatureDefinition def);
}
```

- [ ] **Step 2: Реалізувати `VanillaAppearance`**

`VanillaAppearance.java`:
```java
package me.vangoo.infrastructure.creatures;

import me.vangoo.application.services.CustomItemService;
import me.vangoo.domain.creatures.CreatureDefinition;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Map;

/** Ванільний вигляд: кастомне ім'я, розмір (SCALE), опційний екіп. */
public final class VanillaAppearance implements CreatureAppearance {

    private final CustomItemService customItemService;

    public VanillaAppearance(CustomItemService customItemService) {
        this.customItemService = customItemService;
    }

    @Override
    public void apply(LivingEntity entity, CreatureDefinition def) {
        entity.setCustomName(def.displayName());
        entity.setCustomNameVisible(true);

        AttributeInstance scale = entity.getAttribute(Attribute.SCALE);
        if (scale != null) {
            scale.setBaseValue(def.stats().scale());
        }

        EntityEquipment eq = entity.getEquipment();
        if (eq != null && !def.equipment().isEmpty()) {
            for (Map.Entry<String, String> e : def.equipment().entrySet()) {
                EquipmentSlot slot = parseSlot(e.getKey());
                if (slot == null || e.getValue() == null) continue;
                ItemStack stack = customItemService.createItemStack(e.getValue()).orElse(null);
                if (stack == null) continue;
                eq.setItem(slot, stack);
                eq.setDropChance(slot, 0f); // екіп не падає окремо — дроп лише з лут-таблиці
            }
        }
    }

    private EquipmentSlot parseSlot(String key) {
        try {
            return EquipmentSlot.valueOf(key.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
```

- [ ] **Step 3: Зібрати проєкт**

Run: `mvn clean package`
Expected: BUILD SUCCESS. (Цей проєкт використовує НОВУ номенклатуру `Attribute` без префікса `GENERIC_`: `Attribute.SCALE`, `Attribute.MAX_HEALTH`, `Attribute.ATTACK_DAMAGE`, `Attribute.MOVEMENT_SPEED` — як у наявному `BeyonderService`/`EntityContext`.)

- [ ] **Step 4: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/creatures/CreatureAppearance.java src/main/java/me/vangoo/infrastructure/creatures/VanillaAppearance.java
git commit -m "feat(hunting): CreatureAppearance seam + VanillaAppearance"
```

---

### Task 5: `CreatureSpawner`

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/creatures/CreatureSpawner.java`

**Interfaces:**
- Consumes: `CreatureDefinition`, `CreatureStats` (Task 1); `CreatureCodec` (Task 3); `CreatureAppearance` (Task 4); `org.bukkit.plugin.Plugin`.
- Produces: `class CreatureSpawner(Map<String,CreatureAppearance> appearances, CreatureCodec codec, Plugin plugin)` з `Optional<LivingEntity> spawn(CreatureDefinition def, Location loc)`.

- [ ] **Step 1: Реалізувати `CreatureSpawner`**

`CreatureSpawner.java`:
```java
package me.vangoo.infrastructure.creatures;

import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.domain.creatures.CreatureStats;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Спавнить кастомну істоту: ванільний базовий ентіті + стати + вигляд + PDC-тег. */
public final class CreatureSpawner {

    private final Map<String, CreatureAppearance> appearances;
    private final CreatureCodec codec;
    private final Plugin plugin;

    public CreatureSpawner(Map<String, CreatureAppearance> appearances, CreatureCodec codec, Plugin plugin) {
        this.appearances = appearances;
        this.codec = codec;
        this.plugin = plugin;
    }

    public Optional<LivingEntity> spawn(CreatureDefinition def, Location loc) {
        if (loc == null || loc.getWorld() == null) return Optional.empty();

        EntityType type;
        try {
            type = EntityType.valueOf(def.baseEntityType().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Creature '" + def.id() + "': unknown base_entity '"
                    + def.baseEntityType() + "'; skipping spawn");
            return Optional.empty();
        }

        Entity spawned = loc.getWorld().spawnEntity(loc, type);
        if (!(spawned instanceof LivingEntity living)) {
            plugin.getLogger().warning("Creature '" + def.id() + "': base_entity '"
                    + def.baseEntityType() + "' is not a LivingEntity; removing");
            spawned.remove();
            return Optional.empty();
        }

        applyStats(living, def.stats());

        CreatureAppearance appearance = appearances.getOrDefault(def.appearance(), appearances.get("vanilla"));
        if (appearance != null) {
            appearance.apply(living, def);
        }
        codec.tag(living, def.id());
        return Optional.of(living);
    }

    private void applyStats(LivingEntity e, CreatureStats s) {
        setAttr(e, Attribute.MAX_HEALTH, s.health());
        AttributeInstance maxHp = e.getAttribute(Attribute.MAX_HEALTH);
        if (maxHp != null) {
            e.setHealth(Math.min(s.health(), maxHp.getValue()));
        }
        setAttr(e, Attribute.ATTACK_DAMAGE, s.damage());
        setAttr(e, Attribute.MOVEMENT_SPEED, s.speed());
    }

    private void setAttr(LivingEntity e, Attribute attribute, double value) {
        AttributeInstance inst = e.getAttribute(attribute);
        if (inst != null) {
            inst.setBaseValue(value);
        }
    }
}
```

- [ ] **Step 2: Зібрати проєкт**

Run: `mvn clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/creatures/CreatureSpawner.java
git commit -m "feat(hunting): CreatureSpawner (entity + stats + appearance + tag)"
```

---

### Task 6: `CreatureDeathListener` (дроп інгредієнтів)

**Files:**
- Create: `src/main/java/me/vangoo/presentation/listeners/CreatureDeathListener.java`

**Interfaces:**
- Consumes: `CreatureCodec` (Task 3); `CreatureDefinition`, `LootTableData` (Task 1/наявне); `LootGenerationService` (наявний, `List<ItemStack> generateLoot(LootTableData, int, boolean, Beyonder)`); `BeyonderService` (наявний, `Beyonder getBeyonder(UUID)`).
- Produces: `class CreatureDeathListener(CreatureCodec, Map<String,CreatureDefinition>, LootGenerationService, BeyonderService) implements Listener`.

- [ ] **Step 1: Реалізувати `CreatureDeathListener`**

`CreatureDeathListener.java`:
```java
package me.vangoo.presentation.listeners;

import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.LootTableData;
import me.vangoo.infrastructure.creatures.CreatureCodec;
import me.vangoo.infrastructure.structures.LootGenerationService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Дроп кастомної істоти: повна заміна ванільних дропів кастомними інгредієнтами з лут-таблиці
 * істоти. Характеристики неможливі — LootGenerationService.createItemFromId відхиляє characteristic:.
 */
public class CreatureDeathListener implements Listener {

    private final CreatureCodec codec;
    private final Map<String, CreatureDefinition> registry;
    private final LootGenerationService lootService;
    private final BeyonderService beyonderService;
    private final Random random = new Random();

    public CreatureDeathListener(CreatureCodec codec,
                                 Map<String, CreatureDefinition> registry,
                                 LootGenerationService lootService,
                                 BeyonderService beyonderService) {
        this.codec = codec;
        this.registry = registry;
        this.lootService = lootService;
        this.beyonderService = beyonderService;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Optional<String> id = codec.readId(event.getEntity());
        if (id.isEmpty()) return;
        CreatureDefinition def = registry.get(id.get());
        if (def == null) return;

        if (def.clearVanillaDrops()) {
            event.getDrops().clear();
        }

        Beyonder killer = null;
        Player p = event.getEntity().getKiller();
        if (p != null) {
            killer = beyonderService.getBeyonder(p.getUniqueId());
        }

        LootTableData loot = def.loot();
        int count = rollCount(loot.minItems(), loot.maxItems());
        List<ItemStack> drops = lootService.generateLoot(loot, count, false, killer);
        event.getDrops().addAll(drops);
    }

    private int rollCount(int min, int max) {
        if (max <= min) return min;
        return min + random.nextInt(max - min + 1);
    }
}
```

- [ ] **Step 2: Зібрати проєкт**

Run: `mvn clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/me/vangoo/presentation/listeners/CreatureDeathListener.java
git commit -m "feat(hunting): CreatureDeathListener — replace vanilla drops with ingredients"
```

---

### Task 7: `CreatureCommand` + `plugin.yml`

**Files:**
- Create: `src/main/java/me/vangoo/presentation/commands/CreatureCommand.java`
- Modify: `src/main/resources/plugin.yml` (додати команду `creature` після блоку `characteristic`)

**Interfaces:**
- Consumes: `CreatureDefinition` (Task 1); `CreatureSpawner` (Task 5).
- Produces: `class CreatureCommand(Map<String,CreatureDefinition> registry, CreatureSpawner spawner) implements CommandExecutor, TabCompleter`.

- [ ] **Step 1: Додати команду в `plugin.yml`**

У `src/main/resources/plugin.yml`, у блоці `commands:` після `characteristic:` додати:
```yaml
  creature:
    description: Spawn custom mythical creatures (admin/testing)
    usage: /creature spawn <id> [amount]
    permission: mysteriesabove.admin
```

- [ ] **Step 2: Реалізувати `CreatureCommand`**

`CreatureCommand.java` (патерн `CharacteristicCommand`):
```java
package me.vangoo.presentation.commands;

import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.infrastructure.creatures.CreatureSpawner;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** /creature spawn &lt;id&gt; [amount] — адмін-спавн кастомної істоти на місці гравця. */
public class CreatureCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = ChatColor.DARK_AQUA + "[Creature] " + ChatColor.RESET;

    private final Map<String, CreatureDefinition> registry;
    private final CreatureSpawner spawner;

    public CreatureCommand(Map<String, CreatureDefinition> registry, CreatureSpawner spawner) {
        this.registry = registry;
        this.spawner = spawner;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Лише для гравця.");
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("spawn")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Використання: /creature spawn <id> [кількість]");
            return true;
        }
        CreatureDefinition def = registry.get(args[1]);
        if (def == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Невідома істота: " + args[1]);
            return true;
        }
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Невірна кількість: " + args[2]);
                return true;
            }
            if (amount < 1 || amount > 20) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Кількість має бути від 1 до 20");
                return true;
            }
        }
        int spawned = 0;
        for (int i = 0; i < amount; i++) {
            if (spawner.spawn(def, player.getLocation()).isPresent()) spawned++;
        }
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Заспавнено " + spawned + "x " + args[1]);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("spawn"));
        }
        if (args.length == 2) {
            return filter(args[1], new ArrayList<>(registry.keySet()));
        }
        if (args.length == 3) {
            return filter(args[2], List.of("1", "3", "5", "10"));
        }
        return List.of();
    }

    private List<String> filter(String input, List<String> options) {
        String lower = input.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase().startsWith(lower)) out.add(o);
        }
        return out;
    }
}
```

- [ ] **Step 3: Зібрати проєкт**

Run: `mvn clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/me/vangoo/presentation/commands/CreatureCommand.java src/main/resources/plugin.yml
git commit -m "feat(hunting): /creature spawn command"
```

---

### Task 8: `NaturalCreatureSpawnListener` (дикий спавн)

**Files:**
- Create: `src/main/java/me/vangoo/presentation/listeners/NaturalCreatureSpawnListener.java`

**Interfaces:**
- Consumes: `CreatureSelector` (Task 1); `CreatureSpawner` (Task 5).
- Produces: `class NaturalCreatureSpawnListener(CreatureSelector, CreatureSpawner) implements Listener`.

- [ ] **Step 1: Реалізувати `NaturalCreatureSpawnListener`**

`NaturalCreatureSpawnListener.java`:
```java
package me.vangoo.presentation.listeners;

import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.domain.creatures.CreatureSelector;
import me.vangoo.infrastructure.creatures.CreatureSpawner;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.Optional;
import java.util.Random;

/**
 * Підвищує природний (NATURAL) спавн ванільного моба у кастомну істоту, якщо біом+тип збігаються з
 * правилом істоти. Заміна-спавн іде з reason CUSTOM, тож рекурсії немає.
 */
public class NaturalCreatureSpawnListener implements Listener {

    private final CreatureSelector selector;
    private final CreatureSpawner spawner;
    private final Random random = new Random();

    public NaturalCreatureSpawnListener(CreatureSelector selector, CreatureSpawner spawner) {
        this.selector = selector;
        this.spawner = spawner;
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;

        String biome = event.getLocation().getBlock().getBiome().name();
        String type = event.getEntityType().name();

        Optional<CreatureDefinition> pick = selector.pickForBiome(biome, type, random.nextDouble());
        if (pick.isEmpty()) return;

        event.setCancelled(true);
        spawner.spawn(pick.get(), event.getLocation());
    }
}
```

- [ ] **Step 2: Зібрати проєкт**

Run: `mvn clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/me/vangoo/presentation/listeners/NaturalCreatureSpawnListener.java
git commit -m "feat(hunting): natural creature spawn listener"
```

---

### Task 9: `StructureCreatureSpawnListener` (спавн біля структур)

**Files:**
- Create: `src/main/java/me/vangoo/presentation/listeners/StructureCreatureSpawnListener.java`

**Interfaces:**
- Consumes: `CreatureSelector` (Task 1); `CreatureSpawner` (Task 5).
- Produces: `class StructureCreatureSpawnListener(CreatureSelector, CreatureSpawner) implements Listener`.

- [ ] **Step 1: Реалізувати `StructureCreatureSpawnListener`**

`StructureCreatureSpawnListener.java` (окремий лістенер; `VanillaStructureLootListener` НЕ чіпаємо):
```java
package me.vangoo.presentation.listeners;

import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.domain.creatures.CreatureSelector;
import me.vangoo.infrastructure.creatures.CreatureSpawner;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;

import java.util.Optional;
import java.util.Random;

/**
 * Спавнить apex-істот біля кастомних/ванільних структур, реагуючи на генерацію їхнього луту
 * (LootGenerateEvent). Окремий лістенер: спавн ІСТОТИ — не генерація предмета (тому не в
 * VanillaStructureLootListener).
 */
public class StructureCreatureSpawnListener implements Listener {

    private final CreatureSelector selector;
    private final CreatureSpawner spawner;
    private final Random random = new Random();

    public StructureCreatureSpawnListener(CreatureSelector selector, CreatureSpawner spawner) {
        this.selector = selector;
        this.spawner = spawner;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLootGenerate(LootGenerateEvent event) {
        Location loc = event.getLootContext().getLocation();
        if (loc == null || loc.getWorld() == null) return;

        String key = event.getLootTable().getKey().toString();
        Optional<CreatureDefinition> pick = selector.pickForStructure(key, random.nextDouble());
        if (pick.isEmpty()) return;

        spawner.spawn(pick.get(), loc);
    }
}
```

- [ ] **Step 2: Зібрати проєкт**

Run: `mvn clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/me/vangoo/presentation/listeners/StructureCreatureSpawnListener.java
git commit -m "feat(hunting): structure creature spawn listener"
```

---

### Task 10: Wiring (`ServiceContainer` + `MysteriesAbovePlugin`) + in-server перевірка

**Files:**
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java` (поля ~79-81, конструювання ~156-245, геттери ~292-294)
- Modify: `src/main/java/me/vangoo/MysteriesAbovePlugin.java` (`registerEvents()` ~218-233, `registerCommands()` ~248-262)

**Interfaces:**
- Consumes: усе з Task 1–9.
- Produces: повністю зведена фіча; геттери `getCreatureDeathListener()`, `getNaturalCreatureSpawnListener()`, `getStructureCreatureSpawnListener()`, `getCreatureCommand()`, `getCreatureRegistry()`, `getCreatureSpawner()`.

- [ ] **Step 1: Додати поля у `ServiceContainer`**

У `ServiceContainer.java` поряд із полями Спека 2 (після рядка `private RampageRemnantDeathListener rampageRemnantDeathListener;`) додати:
```java
    private java.util.Map<String, me.vangoo.domain.creatures.CreatureDefinition> creatureRegistry;
    private me.vangoo.domain.creatures.CreatureSelector creatureSelector;
    private me.vangoo.infrastructure.creatures.CreatureCodec creatureCodec;
    private me.vangoo.infrastructure.creatures.CreatureSpawner creatureSpawner;
    private me.vangoo.presentation.listeners.CreatureDeathListener creatureDeathListener;
    private me.vangoo.presentation.listeners.NaturalCreatureSpawnListener naturalCreatureSpawnListener;
    private me.vangoo.presentation.listeners.StructureCreatureSpawnListener structureCreatureSpawnListener;
    private me.vangoo.presentation.commands.CreatureCommand creatureCommand;
```
(Якщо у файлі є блок `import` — за бажанням винеси ці типи в imports; нижче wiring використовує прості імена, тож додай відповідні import-и:
`me.vangoo.domain.creatures.CreatureDefinition`, `me.vangoo.domain.creatures.CreatureSelector`,
`me.vangoo.infrastructure.creatures.*`, `me.vangoo.presentation.listeners.CreatureDeathListener`,
`me.vangoo.presentation.listeners.NaturalCreatureSpawnListener`,
`me.vangoo.presentation.listeners.StructureCreatureSpawnListener`,
`me.vangoo.presentation.commands.CreatureCommand`, `java.util.Map`.)

- [ ] **Step 2: Сконструювати сервіси у `ServiceContainer`**

Після конструювання `lootGenerationService` (рядок ~164) і `beyonderService` (рядок ~170) додати (порядок: після обох, бо death-лістенер їх потребує). Вставити блок одразу після `this.beyonderService = new BeyonderService(...)`:
```java
        // --- Спек 3: полювання (істоти) ---
        me.vangoo.infrastructure.creatures.CreatureConfigLoader creatureConfigLoader =
                new me.vangoo.infrastructure.creatures.CreatureConfigLoader(plugin);
        this.creatureRegistry = creatureConfigLoader.load();
        this.creatureSelector = new me.vangoo.domain.creatures.CreatureSelector(creatureRegistry.values());
        this.creatureCodec = new me.vangoo.infrastructure.creatures.CreatureCodec(plugin);
        this.creatureSpawner = new me.vangoo.infrastructure.creatures.CreatureSpawner(
                java.util.Map.of("vanilla",
                        new me.vangoo.infrastructure.creatures.VanillaAppearance(customItemService)),
                creatureCodec, plugin);
        this.creatureDeathListener = new me.vangoo.presentation.listeners.CreatureDeathListener(
                creatureCodec, creatureRegistry, lootGenerationService, beyonderService);
        this.naturalCreatureSpawnListener = new me.vangoo.presentation.listeners.NaturalCreatureSpawnListener(
                creatureSelector, creatureSpawner);
        this.structureCreatureSpawnListener = new me.vangoo.presentation.listeners.StructureCreatureSpawnListener(
                creatureSelector, creatureSpawner);
        this.creatureCommand = new me.vangoo.presentation.commands.CreatureCommand(
                creatureRegistry, creatureSpawner);
```

- [ ] **Step 3: Додати геттери у `ServiceContainer`**

Поряд із геттерами Спека 2 (після `public RampageRemnantDeathListener getRampageRemnantDeathListener() {...}`):
```java
    public me.vangoo.presentation.listeners.CreatureDeathListener getCreatureDeathListener() { return creatureDeathListener; }
    public me.vangoo.presentation.listeners.NaturalCreatureSpawnListener getNaturalCreatureSpawnListener() { return naturalCreatureSpawnListener; }
    public me.vangoo.presentation.listeners.StructureCreatureSpawnListener getStructureCreatureSpawnListener() { return structureCreatureSpawnListener; }
    public me.vangoo.presentation.commands.CreatureCommand getCreatureCommand() { return creatureCommand; }
```

- [ ] **Step 4: Зареєструвати лістенери у `MysteriesAbovePlugin.registerEvents()`**

Після `getServer().getPluginManager().registerEvents(services.getRampageRemnantDeathListener(), this);` (рядок ~233) додати:
```java
        getServer().getPluginManager().registerEvents(services.getCreatureDeathListener(), this);
        getServer().getPluginManager().registerEvents(services.getNaturalCreatureSpawnListener(), this);
        getServer().getPluginManager().registerEvents(services.getStructureCreatureSpawnListener(), this);
```

- [ ] **Step 5: Зареєструвати команду у `MysteriesAbovePlugin.registerCommands()`**

Після блоку `characteristic` (рядок ~262) додати:
```java
        getCommand("creature").setExecutor(services.getCreatureCommand());
        getCommand("creature").setTabCompleter(services.getCreatureCommand());
```

- [ ] **Step 6: Зібрати проєкт**

Run: `mvn clean package`
Expected: BUILD SUCCESS; усі юніт-тести (включно з `CreatureSelectorTest`, `ArchitectureTest`, `BrewMatcherTest`) зелені.

- [ ] **Step 7: In-server smoke test (ручне)**

Розгорнути JAR на тест-сервері (1.21.1) і перевірити:
1. `/creature spawn mind_dragon` → з'являється великий Ravager з ім'ям «§5Розумовий Дракон», підвищеним хп. Убити → падають **інгредієнти** (`adult_mind_dragon_blood`), **жодної Характеристики**, ванільних дропів Ravager (сідло) **немає**.
2. `/creature spawn manhal_fish` → Guardian з ім'ям; убити → `manhal_fish_eyeball` (іноді recipe-книга Visionary 9).
3. Перебувати біля океану з природним спавном мобів → інколи (chance 0.03) Guardian/Drowned замінюється на «Манхал-рибу».
4. Згенерувати лут структури з ключем `mysteries`/`ancient_city` (відкрити скриню) → з шансом 0.35 поряд спавниться apex-істота з луту вищих Seq.
5. Звичайний ванільний моб (незатеганий) гине як завжди — наш дроп/заміна не застосовуються.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java src/main/java/me/vangoo/MysteriesAbovePlugin.java
git commit -m "feat(hunting): wire creatures (loader, spawner, listeners, command)"
```

---

## Self-Review

**Spec coverage:**
- Істоти → лише інгредієнти, ніколи Характеристики → Task 6 (через `LootGenerationService`, відхилення `characteristic:`) ✔
- Поділ common/apex (складність + Seq + канал) → дані в `creatures.yml` (Task 2) + `SpawnRule` (Task 1) ✔
- Чисте ядро `domain.creatures` + `CreatureSelector` юніт-тест → Task 1 ✔
- `CreatureConfigLoader` + `creatures.yml` → Task 2 ✔
- `CreatureCodec` (PDC) → Task 3 ✔
- `CreatureAppearance`/`VanillaAppearance` шов → Task 4 ✔
- `CreatureSpawner` → Task 5 ✔
- `CreatureDeathListener` (повна заміна дропів) → Task 6 ✔
- `/creature spawn` + plugin.yml → Task 7 ✔
- Природний спавн → Task 8 ✔
- Структурний спавн окремим лістенером → Task 9 ✔
- Wiring + реєстрація + in-server → Task 10 ✔
- ArchUnit зелений (domain.creatures чистий) → Task 1 Step 5/6, Task 10 Step 6 ✔
- Без BossBar → не реалізується (узгоджено) ✔

**Placeholder scan:** немає TBD/TODO; усі кроки містять реальний код і команди.

**Type consistency:** `pickForBiome(String,String,double)`/`pickForStructure(String,double)` (Task 1) використані ідентично в Task 8/9; `CreatureSpawner.spawn(CreatureDefinition,Location)→Optional<LivingEntity>` (Task 5) — у Task 7/8/9; `CreatureCodec.readId→Optional<String>`/`tag(Entity,String)` (Task 3) — у Task 5/6; `LootTableData.minItems()/maxItems()/items()` — наявні аксесори record; `LootGenerationService.generateLoot(LootTableData,int,boolean,Beyonder)` — наявна сигнатура. Узгоджено.
