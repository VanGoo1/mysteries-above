# Поведінка міфічних істот за шляхом — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Додати міфічним істотам бойову поведінку, що залежить від шляху (аура/засідка/блінк/контроль/викриття/хаос), з малим впливом на розсудок у ментальних шляхів.

**Architecture:** Один центральний тік-менеджер (`CreatureBehaviorManager`) ітерує живі тегувані істоти раз на секунду, і для кожної (якщо поряд є потойбічний) делегує до per-entity об'єкта `CreatureBehavior`, обраного фабрикою за шляхом істоти. Усе — ефекти в `infrastructure.creatures.behavior` (Bukkit дозволено), перевіряється in-server; чистого domain майже нема (лише нове поле `pathway`).

**Tech Stack:** Java 21, Spigot/Bukkit API 1.21.1, Maven (shade), JUnit 5.

## Global Constraints

- **Мова:** усі нові user-facing рядки (імена «ляльок» тощо) — **українською**.
- **Збірка:** `mvn` не в PATH → bundled IntelliJ Maven: `"/c/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd" clean package`.
- **API-імена (НОВІ, без старих префіксів):** `Attribute.MAX_HEALTH`; `PotionEffectType.SLOWNESS`, `MINING_FATIGUE`, `NAUSEA`, `GLOWING`, `BLINDNESS`, `DARKNESS`, `INVISIBILITY`, `LEVITATION`, `WEAKNESS`, `BAD_OMEN`. НЕ `SLOW`/`CONFUSION`/`SLOW_DIGGING`/`GENERIC_*`.
- **Ефекти націлені лише на потойбічних** (`BeyonderService.getBeyonder(uuid) != null`); менеджер уже фільтрує й передає список таких гравців у `tick`.
- **Дрен розсудку — дуже малий:** лише Visionary/Fool (ймовірність ~0.2 за застосований тік) і Error (~0.1), через `Beyonder.increaseSanityLoss(1)`.
- **Без headless юніт-тестів** для ефект-класів (in-server). Виняток: Task 1 чіпає `CreatureSelectorTest` (компіляція). `ArchitectureTest` лишається зеленим — усе нове в `infrastructure`.
- **`random` — інстанс-поле/`ThreadLocalRandom`, ніколи `static` mutable.** Реєстр менеджера — інстанс-поле.

---

### Task 1: Поле `pathway` у `CreatureDefinition` + деривація в лоадері + фікс тесту

**Files:**
- Modify: `src/main/java/me/vangoo/domain/creatures/CreatureDefinition.java`
- Modify: `src/main/java/me/vangoo/infrastructure/creatures/CreatureConfigLoader.java:51-68`
- Modify: `src/test/java/me/vangoo/domain/creatures/CreatureSelectorTest.java:14-21`

**Interfaces:**
- Produces: `CreatureDefinition` з додатковим компонентом `String pathway` (11-й, останній — ні: вставляється ПЕРЕД `loot`? Ні — додаємо в кінець для мінімальних змін). Точний порядок — див. код нижче. Аксесор `def.pathway()`.

- [ ] **Step 1: Додати компонент `pathway` у record**

`CreatureDefinition.java` — додати `String pathway` як ОСТАННІЙ компонент:
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
        boolean clearVanillaDrops,
        String pathway) {}
```

- [ ] **Step 2: Заповнити `pathway` у лоадері (явне поле або деривація з префікса id)**

`CreatureConfigLoader.parseCreature` — додати перед `return` обчислення pathway і передати в конструктор:
```java
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

        String pathway = c.getString("pathway");
        if (pathway == null || pathway.isEmpty()) {
            int underscore = id.indexOf('_');
            pathway = (underscore > 0 ? id.substring(0, underscore) : id);
        }
        pathway = pathway.toLowerCase(Locale.ROOT);

        return new CreatureDefinition(id, baseEntity.toUpperCase(Locale.ROOT), displayName, tier,
                stats, equipment, appearance, loot, spawn, clearDrops, pathway);
    }
```

- [ ] **Step 3: Оновити конструктор `CreatureDefinition` у тесті**

`CreatureSelectorTest.def(...)` — додати `pathway` останнім аргументом:
```java
    private CreatureDefinition def(String id, SpawnRule spawn) {
        return new CreatureDefinition(
                id, "GUARDIAN", "§3" + id, CreatureTier.COMMON,
                new CreatureStats(30, 6, 0.25, 1.2),
                Map.of(), "vanilla",
                new LootTableData(List.of(), 1, 2),
                spawn, true, "visionary");
    }
```

- [ ] **Step 4: Зібрати й прогнати тести**

Run: `"/c/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd" test -Dtest=CreatureSelectorTest,ArchitectureTest`
Expected: PASS (8 + 2). Потім повна збірка `... clean package` → BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/vangoo/domain/creatures/CreatureDefinition.java src/main/java/me/vangoo/infrastructure/creatures/CreatureConfigLoader.java src/test/java/me/vangoo/domain/creatures/CreatureSelectorTest.java
git commit -m "feat(behaviors): add pathway field to CreatureDefinition (derive from id prefix)"
```

---

### Task 2: Спільний util `SafeLocations` + рефактор структурного лістенера

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/creatures/SafeLocations.java`
- Modify: `src/main/java/me/vangoo/presentation/listeners/StructureCreatureSpawnListener.java:70-79`

**Interfaces:**
- Produces: `SafeLocations.passableNear(Location origin) -> Location` (статичний; шукає прохідне 2-блокове місце поряд).

- [ ] **Step 1: Створити `SafeLocations`**

```java
package me.vangoo.infrastructure.creatures;

import org.bukkit.Location;

/** Знаходить прохідне (2 блоки заввишки) місце поряд із origin для безпечного спавну/телепорту. */
public final class SafeLocations {

    private SafeLocations() {}

    public static Location passableNear(Location origin) {
        int[][] offsets = { {0, 0, 0}, {0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {2, 0, 0}, {0, 0, 2} };
        for (int[] o : offsets) {
            Location cand = origin.clone().add(o[0] + 0.5, o[1], o[2] + 0.5);
            if (cand.getBlock().isPassable() && cand.clone().add(0, 1, 0).getBlock().isPassable()) {
                return cand;
            }
        }
        return origin.clone().add(0.5, 1.0, 0.5);
    }
}
```

- [ ] **Step 2: Перевести `StructureCreatureSpawnListener` на util**

Видалити приватний метод `safeSpawnLocation(...)` і його виклик замінити на `SafeLocations.passableNear(loc)`. Конкретно — рядок спавну стає:
```java
        spawner.spawn(pick.get(), me.vangoo.infrastructure.creatures.SafeLocations.passableNear(loc));
```
і повністю видалити метод `private Location safeSpawnLocation(Location origin) { ... }`.

- [ ] **Step 3: Зібрати**

Run: `"/c/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd" clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/creatures/SafeLocations.java src/main/java/me/vangoo/presentation/listeners/StructureCreatureSpawnListener.java
git commit -m "refactor(behaviors): extract SafeLocations util; reuse in structure listener"
```

---

### Task 3: Каркас поведінки (interface + factory-заглушка + manager) + wiring

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/creatures/behavior/CreatureBehavior.java`
- Create: `src/main/java/me/vangoo/infrastructure/creatures/behavior/CreatureBehaviorFactory.java`
- Create: `src/main/java/me/vangoo/infrastructure/creatures/behavior/CreatureBehaviorManager.java`
- Modify: `src/main/java/me/vangoo/infrastructure/creatures/CreatureSpawner.java`
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java:180-189` (+ поле/геттер)
- Modify: `src/main/java/me/vangoo/MysteriesAbovePlugin.java` (`onDisable`)

**Interfaces:**
- Produces:
  - `interface CreatureBehavior { void tick(LivingEntity self, java.util.List<org.bukkit.entity.Player> nearbyBeyonders); }`
  - `class CreatureBehaviorFactory(BeyonderService, Plugin)` з `CreatureBehavior create(CreatureDefinition def)` (поки повертає `null` для всіх шляхів).
  - `class CreatureBehaviorManager(Plugin, BeyonderService, CreatureBehaviorFactory)` з `void start(LivingEntity, CreatureDefinition)` та `void stopAll()`.
  - `CreatureSpawner` отримує `CreatureBehaviorManager` 4-м параметром і викликає `start(...)` наприкінці `spawn`.

- [ ] **Step 1: `CreatureBehavior` інтерфейс**

```java
package me.vangoo.infrastructure.creatures.behavior;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;

/** Per-entity бойова поведінка істоти. Тікається CreatureBehaviorManager раз на секунду,
 * лише коли поряд є потойбічні (передаються у nearbyBeyonders). */
public interface CreatureBehavior {
    void tick(LivingEntity self, List<Player> nearbyBeyonders);
}
```

- [ ] **Step 2: `CreatureBehaviorFactory` (заглушка)**

```java
package me.vangoo.infrastructure.creatures.behavior;

import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.domain.creatures.CreatureTier;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

/** Обирає поведінку за шляхом істоти. Поки повертає null для всіх — реальні архетипи додаються
 * наступними задачами. */
public final class CreatureBehaviorFactory {

    private final BeyonderService beyonderService;
    private final Plugin plugin;

    public CreatureBehaviorFactory(BeyonderService beyonderService, Plugin plugin) {
        this.beyonderService = beyonderService;
        this.plugin = plugin;
    }

    public CreatureBehavior create(CreatureDefinition def) {
        String pathway = def.pathway() == null ? "" : def.pathway().toLowerCase(Locale.ROOT);
        boolean apex = def.tier() == CreatureTier.APEX;
        return switch (pathway) {
            // архетипи додаються наступними задачами (Task 4-9)
            default -> null;
        };
    }
}
```

- [ ] **Step 3: `CreatureBehaviorManager`**

```java
package me.vangoo.infrastructure.creatures.behavior;

import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.creatures.CreatureDefinition;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Центральний тік-менеджер поведінок істот: один повторюваний таск ітерує живі тегувані істоти
 * і делегує до їхнього CreatureBehavior, коли поряд є потойбічні. Само-очищає мертвих/відсутніх. */
public final class CreatureBehaviorManager {

    private static final double R = 12.0;

    private final BeyonderService beyonderService;
    private final CreatureBehaviorFactory factory;
    private final Map<UUID, CreatureBehavior> behaviors = new HashMap<>();
    private final BukkitTask task;

    public CreatureBehaviorManager(Plugin plugin, BeyonderService beyonderService, CreatureBehaviorFactory factory) {
        this.beyonderService = beyonderService;
        this.factory = factory;
        this.task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickAll, 40L, 20L);
    }

    public void start(LivingEntity entity, CreatureDefinition def) {
        CreatureBehavior behavior = factory.create(def);
        if (behavior != null) {
            behaviors.put(entity.getUniqueId(), behavior);
        }
    }

    public void stopAll() {
        if (task != null) {
            task.cancel();
        }
        behaviors.clear();
    }

    private void tickAll() {
        if (behaviors.isEmpty()) return;
        for (Map.Entry<UUID, CreatureBehavior> e : new ArrayList<>(behaviors.entrySet())) {
            Entity ent = Bukkit.getEntity(e.getKey());
            if (!(ent instanceof LivingEntity self) || self.isDead()) {
                behaviors.remove(e.getKey());
                continue;
            }
            List<Player> nearby = new ArrayList<>();
            for (Entity n : self.getNearbyEntities(R, R, R)) {
                if (n instanceof Player p && beyonderService.getBeyonder(p.getUniqueId()) != null) {
                    nearby.add(p);
                }
            }
            if (nearby.isEmpty()) continue;
            e.getValue().tick(self, nearby);
        }
    }
}
```

- [ ] **Step 4: `CreatureSpawner` стартує поведінку**

Додати поле + параметр конструктора + виклик. Конкретно:
```java
    private final Map<String, CreatureAppearance> appearances;
    private final CreatureCodec codec;
    private final Plugin plugin;
    private final me.vangoo.infrastructure.creatures.behavior.CreatureBehaviorManager behaviorManager;

    public CreatureSpawner(Map<String, CreatureAppearance> appearances, CreatureCodec codec, Plugin plugin,
                           me.vangoo.infrastructure.creatures.behavior.CreatureBehaviorManager behaviorManager) {
        this.appearances = appearances;
        this.codec = codec;
        this.plugin = plugin;
        this.behaviorManager = behaviorManager;
    }
```
У кінці `spawn(...)`, перед `return Optional.of(living);`, додати:
```java
        codec.tag(living, def.id());
        behaviorManager.start(living, def);
        return Optional.of(living);
```
(рядок `codec.tag(...)` уже є — додати `behaviorManager.start(living, def);` одразу після нього).

- [ ] **Step 5: Wiring у `ServiceContainer`**

У creature-блоці `initializeApplicationServices` (рядки ~180-189) перед конструюванням `creatureSpawner` додати фабрику+менеджер, і передати менеджер у спавнер:
```java
        this.creatureBehaviorFactory = new me.vangoo.infrastructure.creatures.behavior.CreatureBehaviorFactory(
                beyonderService, plugin);
        this.creatureBehaviorManager = new me.vangoo.infrastructure.creatures.behavior.CreatureBehaviorManager(
                plugin, beyonderService, creatureBehaviorFactory);
        this.creatureSpawner = new me.vangoo.infrastructure.creatures.CreatureSpawner(
                java.util.Map.of("vanilla",
                        new me.vangoo.infrastructure.creatures.VanillaAppearance(customItemService)),
                creatureCodec, plugin, creatureBehaviorManager);
```
Додати поля поряд із рештою creature-полів:
```java
    private me.vangoo.infrastructure.creatures.behavior.CreatureBehaviorFactory creatureBehaviorFactory;
    private me.vangoo.infrastructure.creatures.behavior.CreatureBehaviorManager creatureBehaviorManager;
```
Додати геттер поряд з іншими creature-геттерами:
```java
    public me.vangoo.infrastructure.creatures.behavior.CreatureBehaviorManager getCreatureBehaviorManager() { return creatureBehaviorManager; }
```

- [ ] **Step 6: `stopAll()` в `onDisable`**

У `MysteriesAbovePlugin.onDisable()` додати рядок (перш ніж/після наявного збереження):
```java
        if (services != null && services.getCreatureBehaviorManager() != null) {
            services.getCreatureBehaviorManager().stopAll();
        }
```
(Якщо `onDisable` відсутній — додати метод `@Override public void onDisable() { ... }` з цим рядком.)

- [ ] **Step 7: Зібрати**

Run: `"/c/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd" clean package`
Expected: BUILD SUCCESS, усі юніт-тести зелені. (Поведінок ще нема — фабрика повертає null; каркас працює без ефектів.)

- [ ] **Step 8: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/creatures/behavior src/main/java/me/vangoo/infrastructure/creatures/CreatureSpawner.java src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java src/main/java/me/vangoo/MysteriesAbovePlugin.java
git commit -m "feat(behaviors): behavior framework (interface, manager, factory stub) wired into spawner"
```

---

### Task 4: `VerdictBehavior` (Justiciar — аура вироку)

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/creatures/behavior/VerdictBehavior.java`
- Modify: `src/main/java/me/vangoo/infrastructure/creatures/behavior/CreatureBehaviorFactory.java`

**Interfaces:**
- Consumes: `CreatureBehavior` (Task 3).
- Produces: `class VerdictBehavior(boolean apex)`.

- [ ] **Step 1: Створити `VerdictBehavior`**

```java
package me.vangoo.infrastructure.creatures.behavior;

import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/** Justiciar: постійна аура вироку — поряд із істотою потойбічні «скуті судом»
 * (Slowness + Mining Fatigue + Weakness). На apex Slowness II. */
public final class VerdictBehavior implements CreatureBehavior {

    private final boolean apex;

    public VerdictBehavior(boolean apex) {
        this.apex = apex;
    }

    @Override
    public void tick(LivingEntity self, List<Player> nearbyBeyonders) {
        int slowAmp = apex ? 1 : 0;
        for (Player p : nearbyBeyonders) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, slowAmp, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 40, 0, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, false, false));
        }
        self.getWorld().spawnParticle(Particle.WAX_OFF, self.getLocation().add(0, 1, 0), 3, 0.4, 0.4, 0.4, 0);
    }
}
```

- [ ] **Step 2: Додати кейс у фабрику**

У `CreatureBehaviorFactory.create` switch додати перед `default`:
```java
            case "justiciar" -> new VerdictBehavior(apex);
```

- [ ] **Step 3: Зібрати**

Run: `"/c/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd" clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/creatures/behavior/VerdictBehavior.java src/main/java/me/vangoo/infrastructure/creatures/behavior/CreatureBehaviorFactory.java
git commit -m "feat(behaviors): Justiciar VerdictBehavior (debuff aura)"
```

---

### Task 5: `RevealBehavior` (WhiteTower — викриття + харас)

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/creatures/behavior/RevealBehavior.java`
- Modify: `src/main/java/me/vangoo/infrastructure/creatures/behavior/CreatureBehaviorFactory.java`

**Interfaces:**
- Produces: `class RevealBehavior(boolean apex)`.

- [ ] **Step 1: Створити `RevealBehavior`**

```java
package me.vangoo.infrastructure.creatures.behavior;

import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** WhiteTower: аура викриття — Glowing (підсвічує крізь стіни) + зрідка спалах Blindness;
 * періодично кидає світловий снаряд (Snowball, для хараса/відкидання) у найближчого. */
public final class RevealBehavior implements CreatureBehavior {

    private final boolean apex;
    private long lastShot;

    public RevealBehavior(boolean apex) {
        this.apex = apex;
    }

    @Override
    public void tick(LivingEntity self, List<Player> nearbyBeyonders) {
        for (Player p : nearbyBeyonders) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false));
            if (ThreadLocalRandom.current().nextDouble() < 0.25) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 25, 0, false, false));
            }
        }
        long now = System.currentTimeMillis();
        long cd = apex ? 3500 : 5000;
        if (now - lastShot >= cd) {
            lastShot = now;
            Player target = nearbyBeyonders.get(0);
            Snowball ball = self.getWorld().spawn(self.getEyeLocation(), Snowball.class);
            ball.setShooter(self);
            Vector dir = target.getEyeLocation().toVector().subtract(self.getEyeLocation().toVector());
            if (dir.lengthSquared() > 0.001) {
                ball.setVelocity(dir.normalize().multiply(1.6));
            }
        }
        self.getWorld().spawnParticle(Particle.END_ROD, self.getLocation().add(0, 1, 0), 4, 0.3, 0.5, 0.3, 0.01);
    }
}
```

- [ ] **Step 2: Кейс у фабрику**

```java
            case "whitetower" -> new RevealBehavior(apex);
```

- [ ] **Step 3: Зібрати**

Run: `"/c/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd" clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/creatures/behavior/RevealBehavior.java src/main/java/me/vangoo/infrastructure/creatures/behavior/CreatureBehaviorFactory.java
git commit -m "feat(behaviors): WhiteTower RevealBehavior (glow + harass projectile)"
```

---

### Task 6: `AmbushBehavior` (Visionary — стелс-засідка + дрен розсудку)

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/creatures/behavior/AmbushBehavior.java`
- Modify: `src/main/java/me/vangoo/infrastructure/creatures/behavior/CreatureBehaviorFactory.java`

**Interfaces:**
- Consumes: `BeyonderService.getBeyonder(UUID) -> Beyonder`; `Beyonder.increaseSanityLoss(int)`.
- Produces: `class AmbushBehavior(boolean apex, BeyonderService beyonderService)`.

- [ ] **Step 1: Створити `AmbushBehavior`**

```java
package me.vangoo.infrastructure.creatures.behavior;

import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.entities.Beyonder;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Visionary: стелс-засідка — поки гравець далеко, істота невидима й підкрадається; при зближенні
 * розкривається й накладає ментальний сплеск (Nausea + Blindness + Darkness) + малий дрен розсудку. */
public final class AmbushBehavior implements CreatureBehavior {

    private final boolean apex;
    private final BeyonderService beyonderService;

    public AmbushBehavior(boolean apex, BeyonderService beyonderService) {
        this.apex = apex;
        this.beyonderService = beyonderService;
    }

    @Override
    public void tick(LivingEntity self, List<Player> nearbyBeyonders) {
        Player target = nearest(self, nearbyBeyonders);
        double dist = target.getLocation().distance(self.getLocation());

        if (dist > 4.0) {
            self.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 40, 0, false, false));
            self.setCustomNameVisible(false);
            return;
        }

        self.removePotionEffect(PotionEffectType.INVISIBILITY);
        self.setCustomNameVisible(true);

        int nauseaDur = apex ? 100 : 70;
        target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, nauseaDur, 0, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false));

        if (ThreadLocalRandom.current().nextDouble() < 0.2) {
            Beyonder b = beyonderService.getBeyonder(target.getUniqueId());
            if (b != null) {
                b.increaseSanityLoss(1);
            }
        }
        self.getWorld().spawnParticle(Particle.SQUID_INK, target.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.01);
    }

    private Player nearest(LivingEntity self, List<Player> players) {
        Player best = players.get(0);
        double bestSq = best.getLocation().distanceSquared(self.getLocation());
        for (Player p : players) {
            double d = p.getLocation().distanceSquared(self.getLocation());
            if (d < bestSq) {
                bestSq = d;
                best = p;
            }
        }
        return best;
    }
}
```

- [ ] **Step 2: Кейс у фабрику**

```java
            case "visionary" -> new AmbushBehavior(apex, beyonderService);
```

- [ ] **Step 3: Зібрати**

Run: `"/c/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd" clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/creatures/behavior/AmbushBehavior.java src/main/java/me/vangoo/infrastructure/creatures/behavior/CreatureBehaviorFactory.java
git commit -m "feat(behaviors): Visionary AmbushBehavior (stealth + mental burst + tiny sanity drain)"
```

---

### Task 7: `ControlBehavior` (Fool — притягування + ляльки + дрен розсудку)

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/creatures/behavior/ControlBehavior.java`
- Modify: `src/main/java/me/vangoo/infrastructure/creatures/behavior/CreatureBehaviorFactory.java`

**Interfaces:**
- Consumes: `BeyonderService`; `Attribute.MAX_HEALTH`.
- Produces: `class ControlBehavior(boolean apex, BeyonderService beyonderService)`.

- [ ] **Step 1: Створити `ControlBehavior`**

```java
package me.vangoo.infrastructure.creatures.behavior;

import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.entities.Beyonder;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Fool: контроль — періодично притягує найближчого потойбічного «нитками» + Slowness; на низькому
 * HP разово прикликає слабких «ляльок» (зомбі); зрідка малий дрен розсудку. */
public final class ControlBehavior implements CreatureBehavior {

    private final boolean apex;
    private final BeyonderService beyonderService;
    private long lastPull;
    private boolean summoned;

    public ControlBehavior(boolean apex, BeyonderService beyonderService) {
        this.apex = apex;
        this.beyonderService = beyonderService;
    }

    @Override
    public void tick(LivingEntity self, List<Player> nearbyBeyonders) {
        Player target = nearest(self, nearbyBeyonders);
        long now = System.currentTimeMillis();
        if (now - lastPull >= 3000) {
            lastPull = now;
            Vector dir = self.getLocation().toVector().subtract(target.getLocation().toVector());
            if (dir.lengthSquared() > 0.01) {
                dir.normalize().multiply(apex ? 1.2 : 0.9).setY(0.2);
                target.setVelocity(target.getVelocity().add(dir));
            }
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, false));
            if (ThreadLocalRandom.current().nextDouble() < 0.2) {
                Beyonder b = beyonderService.getBeyonder(target.getUniqueId());
                if (b != null) {
                    b.increaseSanityLoss(1);
                }
            }
        }

        if (!summoned) {
            AttributeInstance maxHp = self.getAttribute(Attribute.MAX_HEALTH);
            double max = maxHp != null ? maxHp.getValue() : self.getHealth();
            if (self.getHealth() <= max * 0.35) {
                summoned = true;
                int n = apex ? 2 : 1;
                for (int i = 0; i < n; i++) {
                    Entity z = self.getWorld().spawnEntity(self.getLocation(), EntityType.ZOMBIE);
                    if (z instanceof LivingEntity puppet) {
                        puppet.setCustomName("§8Лялька");
                        puppet.setCustomNameVisible(false);
                    }
                }
            }
        }
        self.getWorld().spawnParticle(Particle.WITCH, self.getLocation().add(0, 1, 0), 4, 0.3, 0.5, 0.3, 0);
    }

    private Player nearest(LivingEntity self, List<Player> players) {
        Player best = players.get(0);
        double bestSq = best.getLocation().distanceSquared(self.getLocation());
        for (Player p : players) {
            double d = p.getLocation().distanceSquared(self.getLocation());
            if (d < bestSq) {
                bestSq = d;
                best = p;
            }
        }
        return best;
    }
}
```

- [ ] **Step 2: Кейс у фабрику**

```java
            case "fool" -> new ControlBehavior(apex, beyonderService);
```

- [ ] **Step 3: Зібрати**

Run: `"/c/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd" clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/creatures/behavior/ControlBehavior.java src/main/java/me/vangoo/infrastructure/creatures/behavior/CreatureBehaviorFactory.java
git commit -m "feat(behaviors): Fool ControlBehavior (pull + low-HP puppets + tiny sanity drain)"
```

---

### Task 8: `BlinkBehavior` (Door — блінк за спину + Levitation)

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/creatures/behavior/BlinkBehavior.java`
- Modify: `src/main/java/me/vangoo/infrastructure/creatures/behavior/CreatureBehaviorFactory.java`

**Interfaces:**
- Consumes: `SafeLocations.passableNear(Location)` (Task 2).
- Produces: `class BlinkBehavior(boolean apex)`.

- [ ] **Step 1: Створити `BlinkBehavior`**

```java
package me.vangoo.infrastructure.creatures.behavior;

import me.vangoo.infrastructure.creatures.SafeLocations;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/** Door: блінк-скірмішер — періодично телепортується за спину найближчого потойбічного (через
 * безпечну локацію) і накладає Levitation. */
public final class BlinkBehavior implements CreatureBehavior {

    private final boolean apex;
    private long lastBlink;

    public BlinkBehavior(boolean apex) {
        this.apex = apex;
    }

    @Override
    public void tick(LivingEntity self, List<Player> nearbyBeyonders) {
        Player target = nearest(self, nearbyBeyonders);
        long now = System.currentTimeMillis();
        long cd = apex ? 3000 : 4500;
        if (now - lastBlink < cd) {
            return;
        }
        lastBlink = now;

        Location behind = target.getLocation().clone().subtract(target.getLocation().getDirection().multiply(2));
        Location safe = SafeLocations.passableNear(behind);

        self.getWorld().spawnParticle(Particle.PORTAL, self.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);
        self.teleport(safe);
        self.getWorld().spawnParticle(Particle.PORTAL, safe.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);

        target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 30, 0, false, false));
    }

    private Player nearest(LivingEntity self, List<Player> players) {
        Player best = players.get(0);
        double bestSq = best.getLocation().distanceSquared(self.getLocation());
        for (Player p : players) {
            double d = p.getLocation().distanceSquared(self.getLocation());
            if (d < bestSq) {
                bestSq = d;
                best = p;
            }
        }
        return best;
    }
}
```

- [ ] **Step 2: Кейс у фабрику**

```java
            case "door" -> new BlinkBehavior(apex);
```

- [ ] **Step 3: Зібрати**

Run: `"/c/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd" clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/creatures/behavior/BlinkBehavior.java src/main/java/me/vangoo/infrastructure/creatures/behavior/CreatureBehaviorFactory.java
git commit -m "feat(behaviors): Door BlinkBehavior (teleport behind + levitation)"
```

---

### Task 9: `ChaosBehavior` (Error — хаос/невдача + дуже малий дрен розсудку)

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/creatures/behavior/ChaosBehavior.java`
- Modify: `src/main/java/me/vangoo/infrastructure/creatures/behavior/CreatureBehaviorFactory.java`

**Interfaces:**
- Consumes: `BeyonderService`; `SafeLocations.passableNear(Location)`.
- Produces: `class ChaosBehavior(boolean apex, BeyonderService beyonderService)`.

- [ ] **Step 1: Створити `ChaosBehavior`**

```java
package me.vangoo.infrastructure.creatures.behavior;

import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.infrastructure.creatures.SafeLocations;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Error: трикстер невдачі — періодично робить одне випадкове: короткий телепорт гравця, Weakness,
 * Mining Fatigue або Bad Omen; дуже зрідка крихітний дрен розсудку. */
public final class ChaosBehavior implements CreatureBehavior {

    private final boolean apex;
    private final BeyonderService beyonderService;
    private long lastAct;

    public ChaosBehavior(boolean apex, BeyonderService beyonderService) {
        this.apex = apex;
        this.beyonderService = beyonderService;
    }

    @Override
    public void tick(LivingEntity self, List<Player> nearbyBeyonders) {
        long now = System.currentTimeMillis();
        long cd = apex ? 2500 : 3500;
        if (now - lastAct < cd) {
            return;
        }
        lastAct = now;

        Player target = nearbyBeyonders.get(ThreadLocalRandom.current().nextInt(nearbyBeyonders.size()));
        switch (ThreadLocalRandom.current().nextInt(4)) {
            case 0 -> {
                Location l = target.getLocation().clone().add(rand(), 0, rand());
                target.teleport(SafeLocations.passableNear(l));
            }
            case 1 -> target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0, false, false));
            case 2 -> target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 80, 0, false, false));
            default -> target.addPotionEffect(new PotionEffect(PotionEffectType.BAD_OMEN, 100, 0, false, false));
        }

        if (ThreadLocalRandom.current().nextDouble() < 0.1) {
            Beyonder b = beyonderService.getBeyonder(target.getUniqueId());
            if (b != null) {
                b.increaseSanityLoss(1);
            }
        }
        self.getWorld().spawnParticle(Particle.SMOKE, target.getLocation().add(0, 1, 0), 6, 0.3, 0.5, 0.3, 0.01);
    }

    private double rand() {
        return ThreadLocalRandom.current().nextDouble(-5.0, 5.0);
    }
}
```

- [ ] **Step 2: Кейс у фабрику**

```java
            case "error" -> new ChaosBehavior(apex, beyonderService);
```

- [ ] **Step 3: Зібрати**

Run: `"/c/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/maven/lib/maven3/bin/mvn.cmd" clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 4: In-server (ручне) — повна перевірка всіх архетипів**

Розгорнути JAR на 1.21.1 і `/creature spawn`:
1. `justiciar_judge_9` — поряд: Slowness/Mining Fatigue/Weakness (аура).
2. `whitetower_manticore_9` — Glowing + спалахи Blindness + кидає снаряд.
3. `visionary_manhal_9` — зникає здалеку, при зближенні Nausea/Blindness/Darkness; розсудок трохи падає.
4. `fool_nighthawk_9` — притягує + Slowness; на низькому HP прикликає «ляльку».
5. `door_wanderer_9` — телепортується за спину + Levitation.
6. `error_sphinx_9` — хаотично телепортує/дебафить; дуже зрідка трохи розсудку.
7. Перф: натовп істот без гравців поряд — без лагу (тік пропускається).
8. `/reload` чи зупинка — таск скасовано, ефекти не «висять».

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/creatures/behavior/ChaosBehavior.java src/main/java/me/vangoo/infrastructure/creatures/behavior/CreatureBehaviorFactory.java
git commit -m "feat(behaviors): Error ChaosBehavior (random misfortune + tiny sanity drain)"
```

---

## Self-Review

**Spec coverage:**
- Поле `pathway` (деривація з id) → Task 1 ✔
- Спільний `SafeLocations` util + рефактор структурного лістенера → Task 2 ✔
- Каркас (interface + manager центральний тік + self-clean + фабрика) + інтеграція спавну + onDisable → Task 3 ✔
- Visionary засідка-стелс + дрен → Task 6 ✔
- Fool контроль/ляльки + дрен → Task 7 ✔
- Door блінк → Task 8 ✔
- Justiciar аура-вирок (дебафи, без реального lock) → Task 4 ✔
- WhiteTower викриття + снаряд → Task 5 ✔
- Error хаос + дуже малий дрен → Task 9 ✔
- Ефекти лише на потойбічних (фільтр у менеджері) → Task 3 ✔
- Перф (пропуск тіку без гравців) → Task 3 ✔
- ArchitectureTest зелений (усе в infrastructure) → Task 1 build ✔

**Placeholder scan:** немає TBD/TODO; код повний у кожному кроці.

**Type consistency:** `CreatureBehavior.tick(LivingEntity, List<Player>)` однаковий у Task 3-9; `CreatureBehaviorFactory.create(CreatureDefinition)` (Task 3) розширюється кейсами в Task 4-9 з конструкторами рівно як у «Produces»; `CreatureBehaviorManager(Plugin, BeyonderService, CreatureBehaviorFactory)` + `start`/`stopAll` (Task 3) використані у ServiceContainer/onDisable; `CreatureSpawner` 4-арг конструктор (Task 3) узгоджений із wiring; `SafeLocations.passableNear(Location)` (Task 2) — у Task 8/9; `CreatureDefinition.pathway()` (Task 1) — у фабриці (Task 3). `Beyonder.increaseSanityLoss(int)` та `BeyonderService.getBeyonder(UUID)` — наявні сигнатури.
