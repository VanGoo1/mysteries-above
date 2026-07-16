# Таємні організації (Спек 6c) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Дати 25 орденам з `InstitutionRegistry` повну механіку: вступ (шифроване послання + запрошення за вчинки), членство з рангом-від-послідовності, завдання з need-based фаворами куратора, рейди на сховища церков, замахи на священиків, шпигунство подвійних агентів і контррозвідку церков.

**Architecture:** Дзеркало 6b: чистий `domain.organizations` (юніт-тести) → Bukkit-оркестратор `SecretOrderService` (application, instance-стан, persist після кожної мутації) → Gson-репозиторії `order-memberships.json` / `orders-state.json` → triumph-gui `OrderMenu` + `OrderListener`/`OrderCommand`. Точки дотику з церквами — нові публічні методи `ChurchService`/`ChurchSiteService`.

**Tech Stack:** Java 21, Spigot/Paper 1.21, triumph-gui, Gson, Citizens, MythicMobs (через `MythicCreatureGateway`), JUnit 5.

**Спек:** `docs/superpowers/specs/2026-07-16-secret-orders-design.md` — читати ПЕРЕД початком.

## Global Constraints

- Увесь user-facing текст — українською; ідентифікатори/логи — англійською (`.claude/rules/localization.md`).
- Коміти — Conventional Commits англійською (`.claude/rules/commit-messages.md`); повідомлення через `-F msgfile` (here-strings лишають літеральні `@`).
- `domain.organizations` — БЕЗ Bukkit/`io.lumine..` (ArchUnit `PURE_DOMAIN` вже пінить пакет).
- Стан сервісів — лише instance-поля; persist після КОЖНОЇ мутації.
- Схема JSON-record-ів = схема файлу: нові поля мають переживати `null` зі старих файлів.
- Maven НЕ на PATH. У кожній сесії PowerShell спершу:
  `$mvn = (Get-ChildItem "C:\Program Files\JetBrains" -Recurse -Filter mvn.cmd | Select-Object -First 1).FullName`
  Запуск: `& $mvn -o test -Dtest=<Class>`; повний білд: `& $mvn -o clean package`.
- Конфіг: всі ключі `orders.*` мають дефолт у коді (`OrderConfig`), Bukkit не перезаписує наявний config.yml.
- Гроші/зілля/Характеристики: єдині канали видачі з цього спеку — фавор куратора і здобич рейду; готових зілль орден НЕ видає.

---

### Task 1: TaskWeight + OrderRank (чистий домен)

**Files:**
- Create: `src/main/java/me/vangoo/domain/organizations/TaskWeight.java`
- Create: `src/main/java/me/vangoo/domain/organizations/OrderRank.java`
- Test: `src/test/java/me/vangoo/domain/organizations/OrderRankTest.java`

**Interfaces:**
- Produces: `enum TaskWeight { LIGHT, STANDARD, MAJOR }` з `boolean atLeast(TaskWeight)`.
- Produces: `enum OrderRank { PAWN, BLADE, TRUSTED, MAGISTER, HIDDEN_LORD }` з `static OrderRank of(int sequenceLevel)`, `boolean atLeast(OrderRank)`, `String displayName()`.

- [ ] **Step 1: Write the failing test**

```java
package me.vangoo.domain.organizations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderRankTest {

    @Test
    void rankDerivesFromSequenceBands() {
        assertEquals(OrderRank.PAWN, OrderRank.of(9));
        assertEquals(OrderRank.PAWN, OrderRank.of(8));
        assertEquals(OrderRank.BLADE, OrderRank.of(7));
        assertEquals(OrderRank.BLADE, OrderRank.of(6));
        assertEquals(OrderRank.TRUSTED, OrderRank.of(5));
        assertEquals(OrderRank.TRUSTED, OrderRank.of(4));
        assertEquals(OrderRank.MAGISTER, OrderRank.of(3));
        assertEquals(OrderRank.MAGISTER, OrderRank.of(2));
        assertEquals(OrderRank.HIDDEN_LORD, OrderRank.of(1));
        assertEquals(OrderRank.HIDDEN_LORD, OrderRank.of(0));
    }

    @Test
    void atLeastComparesLadderPosition() {
        assertTrue(OrderRank.TRUSTED.atLeast(OrderRank.PAWN));
        assertTrue(OrderRank.TRUSTED.atLeast(OrderRank.TRUSTED));
        assertFalse(OrderRank.PAWN.atLeast(OrderRank.TRUSTED));
    }

    @Test
    void weightAtLeastComparesSeverity() {
        assertTrue(TaskWeight.MAJOR.atLeast(TaskWeight.STANDARD));
        assertFalse(TaskWeight.LIGHT.atLeast(TaskWeight.STANDARD));
    }

    @Test
    void outOfRangeSequenceClamps() {
        assertEquals(OrderRank.PAWN, OrderRank.of(12));
        assertEquals(OrderRank.HIDDEN_LORD, OrderRank.of(-1));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `& $mvn -o test -Dtest=OrderRankTest`
Expected: COMPILATION ERROR (`OrderRank` does not exist).

- [ ] **Step 3: Write minimal implementation**

`TaskWeight.java`:

```java
package me.vangoo.domain.organizations;

/** Вага завдання ордену — визначає цінність фавора, який ним заробляється. */
public enum TaskWeight {
    LIGHT, STANDARD, MAJOR;

    public boolean atLeast(TaskWeight other) {
        return ordinal() >= other.ordinal();
    }
}
```

`OrderRank.java`:

```java
package me.vangoo.domain.organizations;

/**
 * Ранг у таємній організації виводиться з ПОСЛІДОВНОСТІ гравця (сила = вага),
 * а не з вкладу — очок у орденів немає взагалі. Нуль власного стану.
 */
public enum OrderRank {
    PAWN("Пішак", 8),
    BLADE("Вістря", 6),
    TRUSTED("Довірений", 4),
    MAGISTER("Магістр", 2),
    HIDDEN_LORD("Прихований Владика", 0);

    private final String displayName;
    private final int minSequence; // найсильніша послідовність щабля (нижня межа діапазону)

    OrderRank(String displayName, int minSequence) {
        this.displayName = displayName;
        this.minSequence = minSequence;
    }

    public String displayName() {
        return displayName;
    }

    public static OrderRank of(int sequenceLevel) {
        int seq = Math.max(0, Math.min(9, sequenceLevel));
        for (OrderRank rank : values()) {
            if (seq >= rank.minSequence) {
                return rank;
            }
        }
        return HIDDEN_LORD;
    }

    public boolean atLeast(OrderRank other) {
        return ordinal() >= other.ordinal();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `& $mvn -o test -Dtest=OrderRankTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/vangoo/domain/organizations/TaskWeight.java src/main/java/me/vangoo/domain/organizations/OrderRank.java src/test/java/me/vangoo/domain/organizations/OrderRankTest.java
printf 'feat(orders): add task weight and sequence-derived order rank\n' > "$TEMP/msg.txt"
git commit -F "$TEMP/msg.txt"
```

---

### Task 2: OrderTask (типи, ваги, фабрики)

**Files:**
- Create: `src/main/java/me/vangoo/domain/organizations/OrderTask.java`
- Test: `src/test/java/me/vangoo/domain/organizations/OrderTaskTest.java`

**Interfaces:**
- Consumes: `TaskWeight` (Task 1).
- Produces: `record OrderTask(Type type, TaskWeight weight, String targetKey, String targetName, int required, int progress)` з `enum Type { DELIVER, HUNT, RAID, ASSASSINATE, RECON, SABOTAGE }`, фабриками `deliver(itemKey, name, seq)`, `hunt(creatureId, name, seq)`, `raid(churchId, churchName)`, `assassinate(churchId, churchName)`, `recon(churchId, churchName)`, `sabotage(churchId, churchName)`, методами `withProgress(int)`, `isComplete()`, `isTempleOp()` (RAID/ASSASSINATE), `isSpyOp()` (RECON/SABOTAGE).

- [ ] **Step 1: Write the failing test**

```java
package me.vangoo.domain.organizations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderTaskTest {

    @Test
    void deliverAndHuntWeightDropsWithStrongerSequence() {
        assertEquals(TaskWeight.LIGHT, OrderTask.deliver("custom:x", "X", 9).weight());
        assertEquals(TaskWeight.LIGHT, OrderTask.hunt("mob", "Моб", 6).weight());
        assertEquals(TaskWeight.STANDARD, OrderTask.deliver("custom:x", "X", 5).weight());
        assertEquals(TaskWeight.STANDARD, OrderTask.hunt("mob", "Моб", 0).weight());
    }

    @Test
    void templeAndSpyOpsCarryFixedWeightAndSingleRequirement() {
        OrderTask raid = OrderTask.raid("church-evernight", "Богині Вічної Ночі");
        assertEquals(TaskWeight.MAJOR, raid.weight());
        assertEquals(1, raid.required());
        assertTrue(raid.isTempleOp());
        assertFalse(raid.isSpyOp());

        OrderTask recon = OrderTask.recon("church-evernight", "Богині Вічної Ночі");
        assertEquals(TaskWeight.STANDARD, recon.weight());
        assertTrue(recon.isSpyOp());

        assertEquals(TaskWeight.MAJOR, OrderTask.sabotage("c", "C").weight());
        assertEquals(TaskWeight.MAJOR, OrderTask.assassinate("c", "C").weight());
    }

    @Test
    void progressCapsAtRequired() {
        OrderTask hunt = OrderTask.hunt("mob", "Моб", 9); // required = 1 + 9/3 = 4
        assertEquals(4, hunt.required());
        OrderTask done = hunt.withProgress(99);
        assertEquals(4, done.progress());
        assertTrue(done.isComplete());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `& $mvn -o test -Dtest=OrderTaskTest`
Expected: COMPILATION ERROR (`OrderTask` does not exist).

- [ ] **Step 3: Write minimal implementation**

```java
package me.vangoo.domain.organizations;

/**
 * Завдання ордену. targetKey: DELIVER → itemKey, HUNT → creatureId,
 * RAID/ASSASSINATE/RECON/SABOTAGE → institutionId церкви-цілі.
 */
public record OrderTask(Type type, TaskWeight weight, String targetKey, String targetName,
                        int required, int progress) {

    public enum Type { DELIVER, HUNT, RAID, ASSASSINATE, RECON, SABOTAGE }

    public static OrderTask deliver(String itemKey, String displayName, int sequence) {
        return new OrderTask(Type.DELIVER, weightForSequence(sequence), itemKey, displayName,
                2 + sequence / 2, 0);
    }

    public static OrderTask hunt(String creatureId, String displayName, int sequence) {
        return new OrderTask(Type.HUNT, weightForSequence(sequence), creatureId, displayName,
                1 + sequence / 3, 0);
    }

    public static OrderTask raid(String churchId, String churchName) {
        return new OrderTask(Type.RAID, TaskWeight.MAJOR, churchId, churchName, 1, 0);
    }

    public static OrderTask assassinate(String churchId, String churchName) {
        return new OrderTask(Type.ASSASSINATE, TaskWeight.MAJOR, churchId, churchName, 1, 0);
    }

    public static OrderTask recon(String churchId, String churchName) {
        return new OrderTask(Type.RECON, TaskWeight.STANDARD, churchId, churchName, 1, 0);
    }

    public static OrderTask sabotage(String churchId, String churchName) {
        return new OrderTask(Type.SABOTAGE, TaskWeight.MAJOR, churchId, churchName, 1, 0);
    }

    /** Слабкі послідовності (9..6) — легка розминка; сильніші цілі — STANDARD. */
    private static TaskWeight weightForSequence(int sequence) {
        return sequence >= 6 ? TaskWeight.LIGHT : TaskWeight.STANDARD;
    }

    public OrderTask withProgress(int newProgress) {
        return new OrderTask(type, weight, targetKey, targetName, required,
                Math.min(newProgress, required));
    }

    public boolean isComplete() {
        return progress >= required;
    }

    public boolean isTempleOp() {
        return type == Type.RAID || type == Type.ASSASSINATE;
    }

    public boolean isSpyOp() {
        return type == Type.RECON || type == Type.SABOTAGE;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `& $mvn -o test -Dtest=OrderTaskTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/vangoo/domain/organizations/OrderTask.java src/test/java/me/vangoo/domain/organizations/OrderTaskTest.java
printf 'feat(orders): add order task types with weights and factories\n' > "$TEMP/msg.txt"
git commit -F "$TEMP/msg.txt"
```

---

### Task 3: Favor + FavorOptions (головне чисте правило спеку)

**Files:**
- Create: `src/main/java/me/vangoo/domain/organizations/Favor.java`
- Create: `src/main/java/me/vangoo/domain/organizations/FavorOptions.java`
- Test: `src/test/java/me/vangoo/domain/organizations/FavorOptionsTest.java`

**Interfaces:**
- Consumes: `TaskWeight`, `OrderRank` (Tasks 1).
- Produces: `record Favor(TaskWeight weight, long earnedAtEpochMillis)`.
- Produces: `FavorOptions.Option` enum `{ HUNT_INFO, VAULT_INTEL, RECIPE_KNOWLEDGE, INGREDIENTS, CHARACTERISTIC, CLEAR_COOLDOWN, FALSE_PAPERS }`; `record Context(boolean knowsNextRecipe, TaskWeight weight, OrderRank rank, boolean orderHasIntel)`; `static List<Option> available(Context ctx)`; `static TaskWeight requiredWeight(Option option)`.

- [ ] **Step 1: Write the failing test**

```java
package me.vangoo.domain.organizations;

import org.junit.jupiter.api.Test;

import java.util.List;

import static me.vangoo.domain.organizations.FavorOptions.Option.*;
import static org.junit.jupiter.api.Assertions.*;

class FavorOptionsTest {

    private static List<FavorOptions.Option> opts(boolean knowsRecipe, TaskWeight w,
                                                  OrderRank rank, boolean intel) {
        return FavorOptions.available(new FavorOptions.Context(knowsRecipe, w, rank, intel));
    }

    @Test
    void withoutNextRecipeTheOnlyMaterialAskIsTheRecipe() {
        List<FavorOptions.Option> options = opts(false, TaskWeight.STANDARD, OrderRank.PAWN, false);
        assertTrue(options.contains(RECIPE_KNOWLEDGE));
        assertFalse(options.contains(INGREDIENTS));
        assertFalse(options.contains(CHARACTERISTIC));
    }

    @Test
    void knowingRecipeUnlocksIngredientsInsteadOfRecipe() {
        List<FavorOptions.Option> options = opts(true, TaskWeight.STANDARD, OrderRank.PAWN, false);
        assertFalse(options.contains(RECIPE_KNOWLEDGE));
        assertTrue(options.contains(INGREDIENTS));
    }

    @Test
    void characteristicNeedsMajorFavorAndTrustedRank() {
        assertFalse(opts(true, TaskWeight.STANDARD, OrderRank.TRUSTED, false).contains(CHARACTERISTIC));
        assertFalse(opts(true, TaskWeight.MAJOR, OrderRank.BLADE, false).contains(CHARACTERISTIC));
        assertTrue(opts(true, TaskWeight.MAJOR, OrderRank.TRUSTED, false).contains(CHARACTERISTIC));
    }

    @Test
    void lightFavorBuysOnlyInformation() {
        List<FavorOptions.Option> options = opts(false, TaskWeight.LIGHT, OrderRank.MAGISTER, true);
        assertEquals(List.of(HUNT_INFO, VAULT_INTEL), options);
    }

    @Test
    void vaultIntelRequiresFreshIntel() {
        assertFalse(opts(true, TaskWeight.MAJOR, OrderRank.TRUSTED, false).contains(VAULT_INTEL));
        assertTrue(opts(true, TaskWeight.LIGHT, OrderRank.PAWN, true).contains(VAULT_INTEL));
    }

    @Test
    void servicesNeedMajorFavor() {
        List<FavorOptions.Option> major = opts(true, TaskWeight.MAJOR, OrderRank.PAWN, false);
        assertTrue(major.contains(CLEAR_COOLDOWN));
        assertTrue(major.contains(FALSE_PAPERS));
        List<FavorOptions.Option> standard = opts(true, TaskWeight.STANDARD, OrderRank.PAWN, false);
        assertFalse(standard.contains(CLEAR_COOLDOWN));
        assertFalse(standard.contains(FALSE_PAPERS));
    }

    @Test
    void requiredWeightMatchesGates() {
        assertEquals(TaskWeight.LIGHT, FavorOptions.requiredWeight(HUNT_INFO));
        assertEquals(TaskWeight.STANDARD, FavorOptions.requiredWeight(RECIPE_KNOWLEDGE));
        assertEquals(TaskWeight.STANDARD, FavorOptions.requiredWeight(INGREDIENTS));
        assertEquals(TaskWeight.MAJOR, FavorOptions.requiredWeight(CHARACTERISTIC));
        assertEquals(TaskWeight.MAJOR, FavorOptions.requiredWeight(CLEAR_COOLDOWN));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `& $mvn -o test -Dtest=FavorOptionsTest`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Write minimal implementation**

`Favor.java`:

```java
package me.vangoo.domain.organizations;

/** Виконане завдання = борг вдячності куратора. Персистується до витрати. */
public record Favor(TaskWeight weight, long earnedAtEpochMillis) {}
```

`FavorOptions.java`:

```java
package me.vangoo.domain.organizations;

import java.util.ArrayList;
import java.util.List;

/**
 * Need-based прохання до куратора: що гравець МОЖЕ попросити за фавор,
 * залежить від його ситуації, ваги фавора й рангу. Головне правило спеку 6c.
 */
public final class FavorOptions {

    public enum Option { HUNT_INFO, VAULT_INTEL, RECIPE_KNOWLEDGE, INGREDIENTS, CHARACTERISTIC, CLEAR_COOLDOWN, FALSE_PAPERS }

    /**
     * @param knowsNextRecipe гравець уже знає рецепт своєї наступної послідовності
     * @param weight          вага фавора, який він готовий витратити
     * @param rank            ранг (= послідовність) гравця в ордені
     * @param orderHasIntel   орден має свіжі розвіддані бодай однієї церкви
     */
    public record Context(boolean knowsNextRecipe, TaskWeight weight, OrderRank rank,
                          boolean orderHasIntel) {}

    private FavorOptions() {}

    public static List<Option> available(Context ctx) {
        List<Option> options = new ArrayList<>();
        options.add(Option.HUNT_INFO);
        if (ctx.orderHasIntel()) {
            options.add(Option.VAULT_INTEL);
        }
        if (ctx.weight().atLeast(TaskWeight.STANDARD)) {
            if (ctx.knowsNextRecipe()) {
                options.add(Option.INGREDIENTS);
            } else {
                options.add(Option.RECIPE_KNOWLEDGE);
            }
        }
        if (ctx.weight().atLeast(TaskWeight.MAJOR)) {
            if (ctx.knowsNextRecipe() && ctx.rank().atLeast(OrderRank.TRUSTED)) {
                options.add(Option.CHARACTERISTIC);
            }
            options.add(Option.CLEAR_COOLDOWN);
            options.add(Option.FALSE_PAPERS);
        }
        return options;
    }

    /** Мінімальна вага фавора, яку списує кожне прохання. */
    public static TaskWeight requiredWeight(Option option) {
        return switch (option) {
            case HUNT_INFO, VAULT_INTEL -> TaskWeight.LIGHT;
            case RECIPE_KNOWLEDGE, INGREDIENTS -> TaskWeight.STANDARD;
            case CHARACTERISTIC, CLEAR_COOLDOWN, FALSE_PAPERS -> TaskWeight.MAJOR;
        };
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `& $mvn -o test -Dtest=FavorOptionsTest`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/vangoo/domain/organizations/Favor.java src/main/java/me/vangoo/domain/organizations/FavorOptions.java src/test/java/me/vangoo/domain/organizations/FavorOptionsTest.java
printf 'feat(orders): add favor and need-based curator options rule\n' > "$TEMP/msg.txt"
git commit -F "$TEMP/msg.txt"
```

---

### Task 4: OrderStash (схованка ордену)

**Files:**
- Create: `src/main/java/me/vangoo/domain/organizations/OrderStash.java`
- Test: `src/test/java/me/vangoo/domain/organizations/OrderStashTest.java`

**Interfaces:**
- Produces: `class OrderStash` з `add(String itemKey, int amount)`, `boolean take(String itemKey, int amount)` (атомарно, false якщо бракує), `int amountOf(String)`, `Map<String,Integer> snapshot()`, `restore(Map<String,Integer>)`, `boolean isEmpty()`.

- [ ] **Step 1: Write the failing test**

```java
package me.vangoo.domain.organizations;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OrderStashTest {

    @Test
    void takeIsAtomicAndConserving() {
        OrderStash stash = new OrderStash();
        stash.add("custom:herb", 3);
        assertFalse(stash.take("custom:herb", 5)); // бракує → нічого не змінилось
        assertEquals(3, stash.amountOf("custom:herb"));
        assertTrue(stash.take("custom:herb", 3));
        assertEquals(0, stash.amountOf("custom:herb"));
        assertTrue(stash.isEmpty());
    }

    @Test
    void snapshotRestoreRoundTrips() {
        OrderStash stash = new OrderStash();
        stash.add("characteristic:Door:9", 1);
        OrderStash copy = new OrderStash();
        copy.restore(stash.snapshot());
        assertEquals(1, copy.amountOf("characteristic:Door:9"));
    }

    @Test
    void restoreDropsNonPositiveEntries() {
        OrderStash stash = new OrderStash();
        stash.restore(Map.of("custom:a", 0, "custom:b", 2));
        assertEquals(0, stash.amountOf("custom:a"));
        assertEquals(2, stash.amountOf("custom:b"));
    }

    @Test
    void addRejectsNonPositive() {
        assertThrows(IllegalArgumentException.class, () -> new OrderStash().add("x", 0));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `& $mvn -o test -Dtest=OrderStashTest`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Write minimal implementation**

```java
package me.vangoo.domain.organizations;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Схованка ордену: itemKey → кількість. Невидима членам (вміст знає лише куратор).
 * Входи: здобич рейдів, DELIVER-завдання, одноразовий сід. Виходи: ЛИШЕ фавори.
 */
public class OrderStash {

    private final Map<String, Integer> items = new HashMap<>();

    public void add(String itemKey, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
        items.merge(itemKey, amount, Integer::sum);
    }

    /** Атомарне списання; false — бракує (нічого не змінюється). */
    public boolean take(String itemKey, int amount) {
        int have = amountOf(itemKey);
        if (have < amount) {
            return false;
        }
        if (have == amount) {
            items.remove(itemKey);
        } else {
            items.put(itemKey, have - amount);
        }
        return true;
    }

    public int amountOf(String itemKey) {
        return items.getOrDefault(itemKey, 0);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public Map<String, Integer> snapshot() {
        return new LinkedHashMap<>(items);
    }

    public void restore(Map<String, Integer> saved) {
        items.clear();
        saved.forEach((k, v) -> {
            if (v != null && v > 0) {
                items.put(k, v);
            }
        });
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `& $mvn -o test -Dtest=OrderStashTest`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/vangoo/domain/organizations/OrderStash.java src/test/java/me/vangoo/domain/organizations/OrderStashTest.java
printf 'feat(orders): add order stash with atomic take\n' > "$TEMP/msg.txt"
git commit -F "$TEMP/msg.txt"
```

---

### Task 5: RaidPlanner (математика здобичі й тривоги)

**Files:**
- Create: `src/main/java/me/vangoo/domain/organizations/RaidPlanner.java`
- Test: `src/test/java/me/vangoo/domain/organizations/RaidPlannerTest.java`

**Interfaces:**
- Produces: `static double alarmChancePerSecond(double base, boolean hasIntel, double intelFactor)`; `static Map<String,Integer> rollLoot(Map<String,Integer> vaultSnapshot, int picks, boolean includeCharacteristics, Random random)`.

- [ ] **Step 1: Write the failing test**

```java
package me.vangoo.domain.organizations;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class RaidPlannerTest {

    private static final Map<String, Integer> VAULT = Map.of(
            "custom:herb", 10,
            "custom:blood", 4,
            "recipe:Door:9", 1,
            "characteristic:Door:9", 2);

    @Test
    void intelLowersAlarmChance() {
        assertEquals(0.04, RaidPlanner.alarmChancePerSecond(0.04, false, 0.5), 1e-9);
        assertEquals(0.02, RaidPlanner.alarmChancePerSecond(0.04, true, 0.5), 1e-9);
    }

    @Test
    void lootNeverExceedsVaultAmounts() {
        for (int seed = 0; seed < 50; seed++) {
            Map<String, Integer> loot = RaidPlanner.rollLoot(VAULT, 5, true, new Random(seed));
            loot.forEach((key, amount) -> {
                assertTrue(amount > 0);
                assertTrue(amount <= VAULT.get(key), key + " over-looted");
            });
        }
    }

    @Test
    void characteristicsAreJackpotOnlyWithIntel() {
        for (int seed = 0; seed < 200; seed++) {
            Map<String, Integer> loot = RaidPlanner.rollLoot(VAULT, 5, false, new Random(seed));
            assertTrue(loot.keySet().stream().noneMatch(k -> k.startsWith("characteristic:")),
                    "characteristic looted without intel, seed=" + seed);
        }
    }

    @Test
    void emptyVaultYieldsEmptyLoot() {
        assertTrue(RaidPlanner.rollLoot(Map.of(), 3, true, new Random(1)).isEmpty());
    }

    @Test
    void picksBoundTotalDraws() {
        for (int seed = 0; seed < 50; seed++) {
            Map<String, Integer> loot = RaidPlanner.rollLoot(VAULT, 2, true, new Random(seed));
            int totalDrawn = loot.values().stream().mapToInt(Integer::intValue).sum();
            assertTrue(totalDrawn <= 2 * 2, "each pick draws at most 2 units");
            assertTrue(loot.size() <= 2);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `& $mvn -o test -Dtest=RaidPlannerTest`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Write minimal implementation**

```java
package me.vangoo.domain.organizations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Чиста математика рейду: шанс тривоги й вибірка здобичі зі знімка сховища церкви.
 * Категорії за вагою: інгредієнти 70, книги рецептів 20, Характеристики 10 —
 * Характеристики в пулі ЛИШЕ зі свіжими розвідданими (джекпот).
 */
public final class RaidPlanner {

    private static final int INGREDIENT_WEIGHT = 70;
    private static final int RECIPE_WEIGHT = 20;
    private static final int CHARACTERISTIC_WEIGHT = 10;

    private RaidPlanner() {}

    public static double alarmChancePerSecond(double base, boolean hasIntel, double intelFactor) {
        return hasIntel ? base * intelFactor : base;
    }

    /** До {@code picks} витягів; кожен витяг — 1..2 одиниці одного ключа, не більше наявного. */
    public static Map<String, Integer> rollLoot(Map<String, Integer> vaultSnapshot, int picks,
                                                boolean includeCharacteristics, Random random) {
        Map<String, Integer> remaining = new HashMap<>(vaultSnapshot);
        if (!includeCharacteristics) {
            remaining.keySet().removeIf(k -> k.startsWith("characteristic:"));
        }
        Map<String, Integer> loot = new LinkedHashMap<>();
        for (int i = 0; i < picks; i++) {
            List<String> ingredients = keysWithPrefix(remaining, "custom:");
            List<String> recipes = keysWithPrefix(remaining, "recipe:");
            List<String> characteristics = keysWithPrefix(remaining, "characteristic:");
            int totalWeight = (ingredients.isEmpty() ? 0 : INGREDIENT_WEIGHT)
                    + (recipes.isEmpty() ? 0 : RECIPE_WEIGHT)
                    + (characteristics.isEmpty() ? 0 : CHARACTERISTIC_WEIGHT);
            if (totalWeight == 0) {
                break;
            }
            int roll = random.nextInt(totalWeight);
            List<String> pool;
            if (!ingredients.isEmpty() && roll < INGREDIENT_WEIGHT) {
                pool = ingredients;
            } else if (!recipes.isEmpty()
                    && roll < (ingredients.isEmpty() ? 0 : INGREDIENT_WEIGHT) + RECIPE_WEIGHT) {
                pool = recipes;
            } else if (!characteristics.isEmpty()) {
                pool = characteristics;
            } else if (!recipes.isEmpty()) {
                pool = recipes;
            } else {
                pool = ingredients;
            }
            String key = pool.get(random.nextInt(pool.size()));
            int available = remaining.get(key);
            int amount = Math.min(available, 1 + random.nextInt(2));
            loot.merge(key, amount, Integer::sum);
            if (available - amount <= 0) {
                remaining.remove(key);
            } else {
                remaining.put(key, available - amount);
            }
        }
        return loot;
    }

    private static List<String> keysWithPrefix(Map<String, Integer> map, String prefix) {
        List<String> keys = new ArrayList<>();
        for (String key : map.keySet()) {
            if (key.startsWith(prefix)) {
                keys.add(key);
            }
        }
        keys.sort(String::compareTo); // детермінізм для тестів
        return keys;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `& $mvn -o test -Dtest=RaidPlannerTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/vangoo/domain/organizations/RaidPlanner.java src/test/java/me/vangoo/domain/organizations/RaidPlannerTest.java
printf 'feat(orders): add raid loot and alarm math\n' > "$TEMP/msg.txt"
git commit -F "$TEMP/msg.txt"
```

---

### Task 6: OrderMembership (агрегат членства)

**Files:**
- Create: `src/main/java/me/vangoo/domain/organizations/OrderMembership.java`
- Test: `src/test/java/me/vangoo/domain/organizations/OrderMembershipTest.java`

**Interfaces:**
- Consumes: `OrderTask`, `Favor`, `TaskWeight`.
- Produces: `class OrderMembership(UUID playerId, String institutionId, String curatorName)` з `tasks()/setTasks(List)`, `lastTaskRefreshEpochMillis()/setLastTaskRefreshEpochMillis(long)`, `taskSetsUsed()/consumeTaskSet()/startTaskWindow(long)/restoreTaskSetsUsed(int)` (дзеркало `Membership`), `favors()` (unmodifiable), `addFavor(Favor)`, `Optional<Favor> consumeFavor(TaskWeight required)` — списує НАЙДЕШЕВШИЙ фавор, що покриває вагу.

- [ ] **Step 1: Write the failing test**

```java
package me.vangoo.domain.organizations;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderMembershipTest {

    private OrderMembership membership() {
        return new OrderMembership(UUID.randomUUID(), "order-aurora", "Пан у сірому");
    }

    @Test
    void consumeFavorSpendsCheapestSufficientOne() {
        OrderMembership m = membership();
        m.addFavor(new Favor(TaskWeight.MAJOR, 1L));
        m.addFavor(new Favor(TaskWeight.STANDARD, 2L));
        m.addFavor(new Favor(TaskWeight.LIGHT, 3L));

        Optional<Favor> spent = m.consumeFavor(TaskWeight.STANDARD);
        assertTrue(spent.isPresent());
        assertEquals(TaskWeight.STANDARD, spent.get().weight()); // не MAJOR
        assertEquals(2, m.favors().size());
    }

    @Test
    void consumeFavorFailsWhenNothingCoversWeight() {
        OrderMembership m = membership();
        m.addFavor(new Favor(TaskWeight.LIGHT, 1L));
        assertTrue(m.consumeFavor(TaskWeight.MAJOR).isEmpty());
        assertEquals(1, m.favors().size()); // нічого не списано
    }

    @Test
    void taskWindowResetMirrorsChurchQuota() {
        OrderMembership m = membership();
        m.consumeTaskSet();
        m.consumeTaskSet();
        assertEquals(2, m.taskSetsUsed());
        m.startTaskWindow(1000L);
        assertEquals(0, m.taskSetsUsed());
        assertEquals(1000L, m.lastTaskRefreshEpochMillis());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `& $mvn -o test -Dtest=OrderMembershipTest`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Write minimal implementation**

```java
package me.vangoo.domain.organizations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Членство в таємній організації. Очок НЕМАЄ: ранг виводиться з послідовності
 * ({@link OrderRank}), нагороди — персистентні фавори. Куратор — наративна персона,
 * генерується при вступі й живе з членством.
 */
public class OrderMembership {

    private final UUID playerId;
    private final String institutionId;
    private final String curatorName;
    private List<OrderTask> tasks = new ArrayList<>();
    private long lastTaskRefreshEpochMillis;
    private int taskSetsUsed;
    private final List<Favor> favors = new ArrayList<>();

    public OrderMembership(UUID playerId, String institutionId, String curatorName) {
        this.playerId = playerId;
        this.institutionId = institutionId;
        this.curatorName = curatorName;
    }

    public UUID playerId() { return playerId; }

    public String institutionId() { return institutionId; }

    public String curatorName() { return curatorName; }

    public List<OrderTask> tasks() { return tasks; }

    public void setTasks(List<OrderTask> tasks) {
        this.tasks = new ArrayList<>(tasks);
    }

    public long lastTaskRefreshEpochMillis() { return lastTaskRefreshEpochMillis; }

    public void setLastTaskRefreshEpochMillis(long millis) {
        this.lastTaskRefreshEpochMillis = millis;
    }

    public int taskSetsUsed() { return taskSetsUsed; }

    public void consumeTaskSet() { taskSetsUsed++; }

    public void startTaskWindow(long now) {
        this.lastTaskRefreshEpochMillis = now;
        this.taskSetsUsed = 0;
    }

    public void restoreTaskSetsUsed(int used) {
        this.taskSetsUsed = Math.max(0, used);
    }

    public List<Favor> favors() {
        return Collections.unmodifiableList(favors);
    }

    public void addFavor(Favor favor) {
        favors.add(favor);
    }

    /** Списує НАЙДЕШЕВШИЙ фавор, що покриває вагу — MAJOR не палиться на дрібне прохання. */
    public Optional<Favor> consumeFavor(TaskWeight required) {
        Optional<Favor> cheapest = favors.stream()
                .filter(f -> f.weight().atLeast(required))
                .min(Comparator.comparing(f -> f.weight().ordinal()));
        cheapest.ifPresent(favors::remove);
        return cheapest;
    }

    /** Гідрація з persisted-стану. */
    public void restoreFavors(List<Favor> saved) {
        favors.clear();
        if (saved != null) {
            favors.addAll(saved);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `& $mvn -o test -Dtest=OrderMembershipTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/vangoo/domain/organizations/OrderMembership.java src/test/java/me/vangoo/domain/organizations/OrderMembershipTest.java
printf 'feat(orders): add order membership aggregate with favor spending\n' > "$TEMP/msg.txt"
git commit -F "$TEMP/msg.txt"
```

---

### Task 7: OrderTaskGenerator

**Files:**
- Create: `src/main/java/me/vangoo/domain/organizations/OrderTaskGenerator.java`
- Test: `src/test/java/me/vangoo/domain/organizations/OrderTaskGeneratorTest.java`

**Interfaces:**
- Consumes: `OrderTask`, `OrderRank`, `Institution`, `PathwayAccess` (наявні).
- Produces:

```java
public record CreatureCandidate(String creatureId, String pathway, int sequence) {}
public record IngredientCandidate(String itemKey, String displayName, int sequence) {}
public record ChurchTarget(String churchId, String churchName) {}
public List<OrderTask> generate(int count, Institution order,
        Map<String,String> pathwayToGroup,
        List<CreatureCandidate> creatures,
        List<IngredientCandidate> ingredients,
        List<ChurchTarget> raidableChurches,   // сайти є, кулдаун минув
        ChurchTarget doubleAgentChurch,        // null = не подвійний агент
        OrderRank rank, Random random)
```

Правила: щонайбільше ОДНА храмова операція (RAID; ASSASSINATE лише за rank ≥ TRUSTED, 50/50 з RAID) і щонайбільше ОДНА шпигунська (RECON/SABOTAGE 50/50; SABOTAGE лише rank ≥ BLADE) на набір; решта — чергування HUNT/DELIVER (ворожі групи + фолбек «будь-які», дзеркало `ChurchTaskGenerator`); без дублів цілей.

- [ ] **Step 1: Write the failing test**

```java
package me.vangoo.domain.organizations;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class OrderTaskGeneratorTest {

    private final OrderTaskGenerator generator = new OrderTaskGenerator();
    private final Institution order = new Institution("order-aurora",
            InstitutionType.SECRET_ORDER, "Орден Аврори", "лор",
            List.of(PathwayAccess.full("Door")));
    private final Map<String, String> groups = Map.of(
            "Door", "LordOfMysteries", "door", "LordOfMysteries",
            "WhiteTower", "DemonOfKnowledge", "whitetower", "DemonOfKnowledge");
    private final List<OrderTaskGenerator.CreatureCandidate> creatures = List.of(
            new OrderTaskGenerator.CreatureCandidate("wt_mob", "whitetower", 9),
            new OrderTaskGenerator.CreatureCandidate("door_mob", "door", 9));
    private final List<OrderTaskGenerator.IngredientCandidate> ingredients = List.of(
            new OrderTaskGenerator.IngredientCandidate("custom:herb", "Трава", 9));
    private final OrderTaskGenerator.ChurchTarget church =
            new OrderTaskGenerator.ChurchTarget("church-evernight", "Богині Вічної Ночі");

    @Test
    void spyTasksOnlyForDoubleAgents() {
        for (int seed = 0; seed < 30; seed++) {
            List<OrderTask> tasks = generator.generate(4, order, groups, creatures, ingredients,
                    List.of(church), null, OrderRank.MAGISTER, new Random(seed));
            assertTrue(tasks.stream().noneMatch(OrderTask::isSpyOp), "seed=" + seed);
        }
    }

    @Test
    void doubleAgentGetsAtMostOneSpyTaskTargetingOwnChurch() {
        boolean seenSpy = false;
        for (int seed = 0; seed < 30; seed++) {
            List<OrderTask> tasks = generator.generate(4, order, groups, creatures, ingredients,
                    List.of(church), church, OrderRank.MAGISTER, new Random(seed));
            long spies = tasks.stream().filter(OrderTask::isSpyOp).count();
            assertTrue(spies <= 1);
            seenSpy |= spies == 1;
            tasks.stream().filter(OrderTask::isSpyOp)
                    .forEach(t -> assertEquals("church-evernight", t.targetKey()));
        }
        assertTrue(seenSpy, "spy task never generated across seeds");
    }

    @Test
    void noTempleOpsWithoutRaidableChurches() {
        for (int seed = 0; seed < 30; seed++) {
            List<OrderTask> tasks = generator.generate(4, order, groups, creatures, ingredients,
                    List.of(), null, OrderRank.HIDDEN_LORD, new Random(seed));
            assertTrue(tasks.stream().noneMatch(OrderTask::isTempleOp), "seed=" + seed);
        }
    }

    @Test
    void assassinationRequiresTrustedRank() {
        for (int seed = 0; seed < 60; seed++) {
            List<OrderTask> tasks = generator.generate(4, order, groups, creatures, ingredients,
                    List.of(church), null, OrderRank.PAWN, new Random(seed));
            assertTrue(tasks.stream().noneMatch(t -> t.type() == OrderTask.Type.ASSASSINATE),
                    "seed=" + seed);
        }
    }

    @Test
    void huntPrefersForeignGroupsWithFallback() {
        for (int seed = 0; seed < 30; seed++) {
            List<OrderTask> tasks = generator.generate(4, order, groups, creatures, ingredients,
                    List.of(), null, OrderRank.PAWN, new Random(seed));
            tasks.stream().filter(t -> t.type() == OrderTask.Type.HUNT)
                    .forEach(t -> assertEquals("wt_mob", t.targetKey())); // чужа група
        }
    }

    @Test
    void noDuplicateTargets() {
        for (int seed = 0; seed < 30; seed++) {
            List<OrderTask> tasks = generator.generate(6, order, groups, creatures, ingredients,
                    List.of(church), church, OrderRank.MAGISTER, new Random(seed));
            long distinct = tasks.stream().map(t -> t.type() + "|" + t.targetKey()).distinct().count();
            assertEquals(tasks.size(), distinct, "seed=" + seed);
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `& $mvn -o test -Dtest=OrderTaskGeneratorTest`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Write minimal implementation**

```java
package me.vangoo.domain.organizations;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Чистий генератор набору завдань ордену. На набір: ≤1 храмова операція
 * (RAID / ASSASSINATE за rank≥TRUSTED), ≤1 шпигунська (RECON / SABOTAGE за
 * rank≥BLADE, лише подвійним агентам, ціль — ВЛАСНА церква агента), решта —
 * чергування HUNT/DELIVER (ворожі групи, фолбек «будь-які» — дзеркало
 * ChurchTaskGenerator).
 */
public final class OrderTaskGenerator {

    public record CreatureCandidate(String creatureId, String pathway, int sequence) {}

    public record IngredientCandidate(String itemKey, String displayName, int sequence) {}

    public record ChurchTarget(String churchId, String churchName) {}

    public List<OrderTask> generate(int count, Institution order,
                                    Map<String, String> pathwayToGroup,
                                    List<CreatureCandidate> creatures,
                                    List<IngredientCandidate> ingredients,
                                    List<ChurchTarget> raidableChurches,
                                    ChurchTarget doubleAgentChurch,
                                    OrderRank rank, Random random) {
        List<OrderTask> tasks = new ArrayList<>();
        Set<String> usedKeys = new HashSet<>();

        // Шпигунська операція — перша претензія на слот (найцінніший контент агента).
        if (doubleAgentChurch != null && tasks.size() < count && random.nextBoolean()) {
            boolean sabotage = rank.atLeast(OrderRank.BLADE) && random.nextBoolean();
            OrderTask spy = sabotage
                    ? OrderTask.sabotage(doubleAgentChurch.churchId(), doubleAgentChurch.churchName())
                    : OrderTask.recon(doubleAgentChurch.churchId(), doubleAgentChurch.churchName());
            if (usedKeys.add(spy.type() + "|" + spy.targetKey())) {
                tasks.add(spy);
            }
        }

        // Храмова операція — одна на набір.
        if (!raidableChurches.isEmpty() && tasks.size() < count && random.nextBoolean()) {
            ChurchTarget target = raidableChurches.get(random.nextInt(raidableChurches.size()));
            boolean assassinate = rank.atLeast(OrderRank.TRUSTED) && random.nextBoolean();
            OrderTask op = assassinate
                    ? OrderTask.assassinate(target.churchId(), target.churchName())
                    : OrderTask.raid(target.churchId(), target.churchName());
            if (usedKeys.add(op.type() + "|" + op.targetKey())) {
                tasks.add(op);
            }
        }

        // Ворожість: групи, покриті доступами ордену, — свої; решта — цілі HUNT.
        Set<String> orderGroups = new HashSet<>();
        for (PathwayAccess access : order.accesses()) {
            String group = pathwayToGroup.get(access.pathwayName());
            if (group != null) {
                orderGroups.add(group);
            }
        }
        List<CreatureCandidate> known = creatures.stream()
                .filter(c -> pathwayToGroup.get(c.pathway()) != null)
                .toList();
        List<CreatureCandidate> foreign = known.stream()
                .filter(c -> !orderGroups.contains(pathwayToGroup.get(c.pathway())))
                .toList();
        List<CreatureCandidate> hostile = new ArrayList<>(foreign.isEmpty() ? known : foreign);
        List<IngredientCandidate> pool = new ArrayList<>(ingredients);

        boolean huntTurn = true;
        while (tasks.size() < count && (!hostile.isEmpty() || !pool.isEmpty())) {
            if ((huntTurn && !hostile.isEmpty()) || pool.isEmpty()) {
                CreatureCandidate c = hostile.remove(random.nextInt(hostile.size()));
                OrderTask t = OrderTask.hunt(c.creatureId(), c.creatureId(), c.sequence());
                if (usedKeys.add(t.type() + "|" + t.targetKey())) {
                    tasks.add(t);
                }
            } else {
                IngredientCandidate i = pool.remove(random.nextInt(pool.size()));
                OrderTask t = OrderTask.deliver(i.itemKey(), i.displayName(), i.sequence());
                if (usedKeys.add(t.type() + "|" + t.targetKey())) {
                    tasks.add(t);
                }
            }
            huntTurn = !huntTurn;
        }
        return tasks;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `& $mvn -o test -Dtest=OrderTaskGeneratorTest`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/vangoo/domain/organizations/OrderTaskGenerator.java src/test/java/me/vangoo/domain/organizations/OrderTaskGeneratorTest.java
printf 'feat(orders): add order task set generator with temple and spy ops\n' > "$TEMP/msg.txt"
git commit -F "$TEMP/msg.txt"
```

---

### Task 8: Invitation + InvitationRules

**Files:**
- Create: `src/main/java/me/vangoo/domain/organizations/Invitation.java`
- Create: `src/main/java/me/vangoo/domain/organizations/InvitationRules.java`
- Test: `src/test/java/me/vangoo/domain/organizations/InvitationRulesTest.java`

**Interfaces:**
- Produces: `record Invitation(String institutionId, String reason, long createdAtEpochMillis)`.
- Produces: `enum InvitationRules.DeedType { APEX_KILL, BEYONDER_KILLS, RAMPAGER_STOPPED }`; `static Optional<Institution> pickOrder(DeedType deed, String deedPathwayOrNull, String playerPathwayOrNull, List<Institution> secretOrders, Map<String,String> pathwayToGroup, Random random)`.

Правила: гравець без шляху запрошення не отримує (ордени приймають лише Потойбічних); кандидат мусить `acceptsPathway(playerPathway)`. Тематика: APEX_KILL → ордени з доступом до шляху ГРУПИ вбитої істоти; BEYONDER_KILLS → пін `order-blood-sanctify`, `order-demoness-sect`, `order-bliss-society`; RAMPAGER_STOPPED → пін `order-shadow-of-order`, `order-truth-school`; фолбек — будь-який приймаючий.

- [ ] **Step 1: Write the failing test**

```java
package me.vangoo.domain.organizations;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class InvitationRulesTest {

    private final Institution bloodSect = new Institution("order-blood-sanctify",
            InstitutionType.SECRET_ORDER, "Секта Освячення Крові", "лор", List.of());
    private final Institution doorOrder = new Institution("order-aurora",
            InstitutionType.SECRET_ORDER, "Орден Аврори", "лор",
            List.of(PathwayAccess.full("Door")));
    private final Institution wtOrder = new Institution("order-psychology-alchemists",
            InstitutionType.SECRET_ORDER, "Психологічні Алхіміки", "лор",
            List.of(PathwayAccess.full("Visionary")));
    private final Map<String, String> groups = Map.of(
            "Door", "LordOfMysteries", "Visionary", "GoddessOfOrigin");

    @Test
    void pathwaylessPlayerNeverInvited() {
        Optional<Institution> pick = InvitationRules.pickOrder(
                InvitationRules.DeedType.APEX_KILL, "Door", null,
                List.of(bloodSect, doorOrder), groups, new Random(1));
        assertTrue(pick.isEmpty());
    }

    @Test
    void apexKillPrefersOrderOfCreatureGroup() {
        for (int seed = 0; seed < 20; seed++) {
            Optional<Institution> pick = InvitationRules.pickOrder(
                    InvitationRules.DeedType.APEX_KILL, "Door", "Door",
                    List.of(bloodSect, doorOrder, wtOrder), groups, new Random(seed));
            assertEquals("order-aurora", pick.orElseThrow().id());
        }
    }

    @Test
    void beyonderKillsPreferPinnedBloodOrders() {
        for (int seed = 0; seed < 20; seed++) {
            Optional<Institution> pick = InvitationRules.pickOrder(
                    InvitationRules.DeedType.BEYONDER_KILLS, null, "Door",
                    List.of(doorOrder, bloodSect), groups, new Random(seed));
            assertEquals("order-blood-sanctify", pick.orElseThrow().id());
        }
    }

    @Test
    void candidatesMustAcceptPlayerPathway() {
        Optional<Institution> pick = InvitationRules.pickOrder(
                InvitationRules.DeedType.RAMPAGER_STOPPED, null, "Door",
                List.of(wtOrder), groups, new Random(1)); // wtOrder не приймає Door
        assertTrue(pick.isEmpty());
    }

    @Test
    void fallsBackToAnyAcceptingOrder() {
        Optional<Institution> pick = InvitationRules.pickOrder(
                InvitationRules.DeedType.RAMPAGER_STOPPED, null, "Door",
                List.of(doorOrder), groups, new Random(1));
        assertEquals("order-aurora", pick.orElseThrow().id());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `& $mvn -o test -Dtest=InvitationRulesTest`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Write minimal implementation**

`Invitation.java`:

```java
package me.vangoo.domain.organizations;

/** Персистентне запрошення ордену за вчинок: чекає, поки гравець прийме (не згорає). */
public record Invitation(String institutionId, String reason, long createdAtEpochMillis) {}
```

`InvitationRules.java`:

```java
package me.vangoo.domain.organizations;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;

/**
 * За який вчинок який орден виходить на гравця. Гравцю без шляху запрошення
 * не надходять — орденам потрібна сила (ранг = послідовність).
 */
public final class InvitationRules {

    public enum DeedType { APEX_KILL, BEYONDER_KILLS, RAMPAGER_STOPPED }

    private static final List<String> BLOOD_ORDERS =
            List.of("order-blood-sanctify", "order-demoness-sect", "order-bliss-society");
    private static final List<String> ORDER_ORDERS =
            List.of("order-shadow-of-order", "order-truth-school");

    private InvitationRules() {}

    public static Optional<Institution> pickOrder(DeedType deed, String deedPathwayOrNull,
                                                  String playerPathwayOrNull,
                                                  List<Institution> secretOrders,
                                                  Map<String, String> pathwayToGroup,
                                                  Random random) {
        if (playerPathwayOrNull == null) {
            return Optional.empty();
        }
        List<Institution> accepting = secretOrders.stream()
                .filter(o -> o.type() == InstitutionType.SECRET_ORDER)
                .filter(o -> o.acceptsPathway(playerPathwayOrNull))
                .toList();
        if (accepting.isEmpty()) {
            return Optional.empty();
        }
        List<Institution> preferred = switch (deed) {
            case APEX_KILL -> {
                String group = deedPathwayOrNull == null ? null : pathwayToGroup.get(deedPathwayOrNull);
                yield group == null ? List.of() : accepting.stream()
                        .filter(o -> o.accesses().stream()
                                .map(a -> pathwayToGroup.get(a.pathwayName()))
                                .filter(Objects::nonNull)
                                .anyMatch(group::equals))
                        .toList();
            }
            case BEYONDER_KILLS -> accepting.stream()
                    .filter(o -> BLOOD_ORDERS.contains(o.id())).toList();
            case RAMPAGER_STOPPED -> accepting.stream()
                    .filter(o -> ORDER_ORDERS.contains(o.id())).toList();
        };
        List<Institution> pool = preferred.isEmpty() ? accepting : preferred;
        return Optional.of(pool.get(random.nextInt(pool.size())));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `& $mvn -o test -Dtest=InvitationRulesTest`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/vangoo/domain/organizations/Invitation.java src/main/java/me/vangoo/domain/organizations/InvitationRules.java src/test/java/me/vangoo/domain/organizations/InvitationRulesTest.java
printf 'feat(orders): add deed-driven invitation rules\n' > "$TEMP/msg.txt"
git commit -F "$TEMP/msg.txt"
```

---

### Task 9: OrderConfig + репозиторії (orders-state / order-memberships)

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/organizations/OrderConfig.java`
- Create: `src/main/java/me/vangoo/infrastructure/organizations/JSONOrderMembershipRepository.java`
- Create: `src/main/java/me/vangoo/infrastructure/organizations/OrderStateRepository.java`
- Test: `src/test/java/me/vangoo/infrastructure/organizations/OrderRepositoriesTest.java`

**Interfaces:**
- Produces: `record OrderConfig(...)` зі `static OrderConfig load(Plugin plugin)` — усі дефолти в коді (патерн `ChurchConfig`). Поля (config-ключі під `orders.*`):
  - `int invitesBeyonderKills` (`invites.beyonder-kills`, 3)
  - `int tasksRefreshHours` (24), `int tasksMaxActive` (2), `int tasksSetsPerWindow` (5)
  - `int raidChannelSeconds` (45), `double raidAlarmChance` (0.04), `double raidAlarmIntelFactor` (0.5), `int raidLootPicks` (3), `int raidLootIntelPicks` (4), `int raidTempleCooldownHours` (24), `int raidZoneRadius` (24), `int raidGuards` (2)
  - `int assassinationPriestRespawnHours` (12)
  - `int sabotageDelayHours` (6), `int reconTtlHours` (48)
  - `double exposureReconChance` (0.15), `double exposureSabotageChance` (0.35), `double exposureFailedRaidChance` (0.5)
  - `int stashSeedIngredientsPerRecipe` (2), `int favorIngredientsPerClaim` (2)
  - `int rejoinCooldownDays` (3), `int talismanReissueCooldownMinutes` (30)
- Produces: `JSONOrderMembershipRepository` (файл `order-memberships.json`; той самий Gson-каркас, що `JSONMembershipRepository`) з record-ами:

```java
public record TaskRecord(String type, String weight, String targetKey, String targetName,
                         int required, int progress) {}
public record FavorRecord(String weight, long earnedAtEpochMillis) {}
public record MembershipRecord(String institutionId, String curatorName,
                               long lastTaskRefreshEpochMillis, int taskSetsUsed,
                               List<TaskRecord> tasks, List<FavorRecord> favors) {}
public record InvitationRecord(String institutionId, String reason, long createdAtEpochMillis) {}
public record PlayerOrderData(MembershipRecord membership,
                              long rejoinCooldownUntilEpochMillis,
                              List<String> abandonedOrders,
                              List<InvitationRecord> invitations,
                              int beyonderKills,
                              long talismanReissueAfterEpochMillis,
                              Map<String, Integer> pendingRaidLoot,
                              boolean falsePapers) {}
public record Model(Map<String, PlayerOrderData> players) {}
```

- Produces: `OrderStateRepository` (файл `orders-state.json`):

```java
public record IntelRecord(Map<String, Integer> manifest, long expiresAtEpochMillis) {}
public record Model(Map<String, Map<String, Integer>> stashes,          // orderId → itemKey → n
                    Map<String, IntelRecord> intel,                      // "orderId|churchId" → знімок
                    Map<String, Long> templeCooldownUntil,               // churchId → epoch millis
                    Map<String, Long> priestClosedUntil) {}              // churchId → epoch millis
```

Обидва репозиторії: `Optional<Model> load()` (corrupt/missing → empty), `void save(Model)` — скопіювати тіло `JSONMembershipRepository` з новими типами.

- [ ] **Step 1: Write the failing test**

```java
package me.vangoo.infrastructure.organizations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class OrderRepositoriesTest {

    @TempDir
    Path dir;

    @Test
    void membershipFileWithoutNewFieldsLoadsWithThemAbsent() throws IOException {
        Path file = dir.resolve("order-memberships.json");
        // Файл «з майбутнього минулого»: без invitations/beyonderKills/talisman-поля.
        Files.writeString(file, """
                {"players":{"11111111-1111-1111-1111-111111111111":
                  {"membership":{"institutionId":"order-aurora","curatorName":"Тінь",
                   "lastTaskRefreshEpochMillis":5,"taskSetsUsed":1,"tasks":[],"favors":null},
                   "rejoinCooldownUntilEpochMillis":0,"abandonedOrders":null}}}
                """);
        JSONOrderMembershipRepository repo = new JSONOrderMembershipRepository(file.toString());
        Optional<JSONOrderMembershipRepository.Model> model = repo.load();
        assertTrue(model.isPresent());
        JSONOrderMembershipRepository.PlayerOrderData data =
                model.get().players().get("11111111-1111-1111-1111-111111111111");
        assertNull(data.invitations());
        assertNull(data.membership().favors());
        assertNull(data.pendingRaidLoot());
        assertFalse(data.falsePapers());
        assertEquals(0, data.beyonderKills());
        assertEquals(0L, data.talismanReissueAfterEpochMillis());
    }

    @Test
    void stateRoundTripsAllFourSections() {
        OrderStateRepository repo = new OrderStateRepository(
                dir.resolve("orders-state.json").toString());
        repo.save(new OrderStateRepository.Model(
                Map.of("order-aurora", Map.of("custom:herb", 2)),
                Map.of("order-aurora|church-evernight",
                        new OrderStateRepository.IntelRecord(Map.of("custom:herb", 5), 123L)),
                Map.of("church-evernight", 55L),
                Map.of("church-evernight", 77L)));
        OrderStateRepository.Model loaded = repo.load().orElseThrow();
        assertEquals(2, loaded.stashes().get("order-aurora").get("custom:herb"));
        assertEquals(123L, loaded.intel().get("order-aurora|church-evernight").expiresAtEpochMillis());
        assertEquals(55L, loaded.templeCooldownUntil().get("church-evernight"));
        assertEquals(77L, loaded.priestClosedUntil().get("church-evernight"));
    }

    @Test
    void corruptFilesLoadAsEmpty() throws IOException {
        Path bad = dir.resolve("orders-state.json");
        Files.writeString(bad, "{not json");
        assertTrue(new OrderStateRepository(bad.toString()).load().isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `& $mvn -o test -Dtest=OrderRepositoriesTest`
Expected: COMPILATION ERROR.

- [ ] **Step 3: Write implementation**

`JSONOrderMembershipRepository.java` — точна копія каркаса `JSONMembershipRepository` (Gson pretty, UTF-8, `Optional.empty()` на IOException/JsonSyntaxException, warning у Logger) з record-ами з блоку Interfaces і конструктором `JSONOrderMembershipRepository(String filePath)`.

`OrderStateRepository.java` — той самий каркас із `Model`/`IntelRecord` з блоку Interfaces.

`OrderConfig.java`:

```java
package me.vangoo.infrastructure.organizations;

import org.bukkit.plugin.Plugin;

/** Читає секцію orders.* із config.yml; усі значення мають дефолт у коді. */
public record OrderConfig(int invitesBeyonderKills,
                          int tasksRefreshHours, int tasksMaxActive, int tasksSetsPerWindow,
                          int raidChannelSeconds, double raidAlarmChance, double raidAlarmIntelFactor,
                          int raidLootPicks, int raidLootIntelPicks,
                          int raidTempleCooldownHours, int raidZoneRadius, int raidGuards,
                          int assassinationPriestRespawnHours,
                          int sabotageDelayHours, int reconTtlHours,
                          double exposureReconChance, double exposureSabotageChance,
                          double exposureFailedRaidChance,
                          int stashSeedIngredientsPerRecipe, int favorIngredientsPerClaim,
                          int rejoinCooldownDays, int talismanReissueCooldownMinutes) {

    public static OrderConfig load(Plugin plugin) {
        var cfg = plugin.getConfig();
        return new OrderConfig(
                cfg.getInt("orders.invites.beyonder-kills", 3),
                cfg.getInt("orders.tasks.refresh-hours", 24),
                cfg.getInt("orders.tasks.max-active", 2),
                cfg.getInt("orders.tasks.sets-per-window", 5),
                cfg.getInt("orders.raid.channel-seconds", 45),
                cfg.getDouble("orders.raid.alarm-chance", 0.04),
                cfg.getDouble("orders.raid.alarm-intel-factor", 0.5),
                cfg.getInt("orders.raid.loot-picks", 3),
                cfg.getInt("orders.raid.loot-intel-picks", 4),
                cfg.getInt("orders.raid.temple-cooldown-hours", 24),
                cfg.getInt("orders.raid.zone-radius", 24),
                cfg.getInt("orders.raid.guards", 2),
                cfg.getInt("orders.assassination.priest-respawn-hours", 12),
                cfg.getInt("orders.sabotage.delay-hours", 6),
                cfg.getInt("orders.recon.ttl-hours", 48),
                cfg.getDouble("orders.exposure.recon-chance", 0.15),
                cfg.getDouble("orders.exposure.sabotage-chance", 0.35),
                cfg.getDouble("orders.exposure.failed-raid-chance", 0.5),
                cfg.getInt("orders.stash.seed-ingredients-per-recipe", 2),
                cfg.getInt("orders.favor.ingredients-per-claim", 2),
                cfg.getInt("orders.rejoin-cooldown-days", 3),
                cfg.getInt("orders.talisman.reissue-cooldown-minutes", 30));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `& $mvn -o test -Dtest=OrderRepositoriesTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/organizations/OrderConfig.java src/main/java/me/vangoo/infrastructure/organizations/JSONOrderMembershipRepository.java src/main/java/me/vangoo/infrastructure/organizations/OrderStateRepository.java src/test/java/me/vangoo/infrastructure/organizations/OrderRepositoriesTest.java
printf 'feat(orders): add order config and gson repositories\n' > "$TEMP/msg.txt"
git commit -F "$TEMP/msg.txt"
```

---

### Task 10: Предмети (послання, талісман) + лут

**Files:**
- Modify: `src/main/resources/custom-items.yml` (в кінець списку `custom-items:`)
- Modify: `src/main/resources/global_loot.yml` (в кінець `items:`)
- Create: `src/main/java/me/vangoo/infrastructure/items/OrderItems.java`

**Interfaces:**
- Produces: `class OrderItems` з `public static final String CIPHER_ID = "order_cipher_message"`, `TALISMAN_ID = "order_talisman"`; конструктор `OrderItems(CustomItemService customItemService)`; `boolean isCipherMessage(ItemStack)`, `boolean isTalisman(ItemStack)`, `Optional<ItemStack> createTalisman()`, `Optional<ItemStack> createCipherMessage()`.
- Предмети генеричні: талісман НЕ кодує орден (членство сервіс знає за UUID гравця).

- [ ] **Step 1: Add items to custom-items.yml**

```yaml
  # ============================================
  # SECRET ORDERS (6c)
  # ============================================
  order_cipher_message:
    display-name: "§8Шифроване послання"
    material: PAPER
    lore:
      - ""
      - "§7Рядки, що складаються в сенс лише"
      - "§7для того, хто готовий їх прочитати."
      - ""
      - "§eПКМ — розшифрувати"
      - ""
    glow: true
    custom-model-data: "order_cipher_message"

  order_talisman:
    display-name: "§8Талісман зв'язку"
    material: PAPER
    lore:
      - ""
      - "§7Холодний на дотик. Хтось завжди"
      - "§7слухає з того боку."
      - ""
      - "§eПКМ — вийти на зв'язок"
      - ""
    glow: true
    custom-model-data: "order_talisman"
```

- [ ] **Step 2: Add cipher message to global_loot.yml** (в кінець `items:`; талісман у лут НЕ кладемо — він видається при вступі)

```yaml
    # --- Secret orders (6c) ---
    order_cipher_message:
      item_id: "order_cipher_message"
      weight: 8
      amount_min: 1
      amount_max: 1
```

- [ ] **Step 3: Write OrderItems helper**

```java
package me.vangoo.infrastructure.items;

import me.vangoo.application.services.CustomItemService;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/** Ідентифікація та створення предметів орденів. Обидва — генеричні custom items:
 * талісман не кодує орден (членство сервіс знає за UUID власника). */
public class OrderItems {

    public static final String CIPHER_ID = "order_cipher_message";
    public static final String TALISMAN_ID = "order_talisman";

    private final CustomItemService customItemService;

    public OrderItems(CustomItemService customItemService) {
        this.customItemService = customItemService;
    }

    public boolean isCipherMessage(ItemStack stack) {
        return hasId(stack, CIPHER_ID);
    }

    public boolean isTalisman(ItemStack stack) {
        return hasId(stack, TALISMAN_ID);
    }

    public Optional<ItemStack> createCipherMessage() {
        return customItemService.createItemStack(CIPHER_ID);
    }

    public Optional<ItemStack> createTalisman() {
        return customItemService.createItemStack(TALISMAN_ID);
    }

    private boolean hasId(ItemStack stack, String id) {
        return customItemService.getCustomItem(stack)
                .map(item -> id.equals(item.id()))
                .orElse(false);
    }
}
```

Примітка виконавцю: перевір реальний геттер id у `domain.valueobjects.CustomItem` (`id()` чи `getId()`) — відкрий файл і використай наявний.

- [ ] **Step 4: Build**

Run: `& $mvn -o clean package -DskipTests`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/custom-items.yml src/main/resources/global_loot.yml src/main/java/me/vangoo/infrastructure/items/OrderItems.java
printf 'feat(orders): add cipher message and talisman items with dungeon loot\n' > "$TEMP/msg.txt"
git commit -F "$TEMP/msg.txt"
```

---

### Task 11: Точки дотику в ChurchService / ChurchSiteService / ChurchListener

**Files:**
- Modify: `src/main/java/me/vangoo/application/services/ChurchService.java`
- Modify: `src/main/java/me/vangoo/infrastructure/organizations/ChurchSiteService.java`
- Modify: `src/main/java/me/vangoo/presentation/listeners/ChurchListener.java`

**Interfaces (Produces — на них спирається SecretOrderService):**
- `ChurchService.stealFromVault(String institutionId, Map<String,Integer> requested)` → `Map<String,Integer>` реально списаного (не більше наявного; persistState()).
- `ChurchService.vaultSnapshot(String institutionId)` → `Map<String,Integer>` (для RECON/маніфеста).
- `ChurchService.delayRandomBrewingOrder(String churchId, long delayMillis, UUID exceptPlayer)` → `Optional<UUID>` жертви (замовлення пересувається, сповіщення жертві якщо онлайн).
- `ChurchService.expelExposedSpy(Player player)` → boolean — той самий `leave()` + broadcast членам церкви «Викрито зрадника».
- `ChurchService.broadcastToChurch(String churchId, String message)` — усім онлайн-членам.
- `ChurchService.onTempleDefended(Player killer, String churchId)` — якщо killer член цієї церкви: +`DEFEND_REWARD_POINTS` (константа 250) вкладу.
- `ChurchSiteService.sites()` → `List<ChurchSiteRepository.Site>`; `ChurchSiteService.siteOf(String institutionId)` → `Optional<Site>` (перший сайт).
- `ChurchSiteService.setPriestClosurePredicate(java.util.function.Predicate<String>)` — `spawnAllNpcs()` пропускає закриті храми (респавн після замаху робить SecretOrderService).
- `ChurchListener.onPriestClick` — НЕ відкриває меню, якщо клікер присідає (шпигунські дії заберуть sneak-клік у Task 13) АБО якщо injected `Predicate<String> templeClosed` каже true (страховка, якщо NPC ще стоїть).

- [ ] **Step 1: Add methods to ChurchService** (після блоку «Сховище церкви»)

```java
    // ── Точки дотику з таємними організаціями (Спек 6c) ─────────────────────

    public static final int DEFEND_REWARD_POINTS = 250;

    /** Знімок сховища для розвідданих RECON. */
    public Map<String, Integer> vaultSnapshot(String institutionId) {
        return vaultOf(institutionId).snapshot();
    }

    /**
     * Крадіжка рейду: списує зі сховища не більше наявного, повертає реально взяте.
     * Другий легальний вихід одиниці сховища (поруч зі списанням у варіння).
     */
    public Map<String, Integer> stealFromVault(String institutionId, Map<String, Integer> requested) {
        ChurchVault vault = vaultOf(institutionId);
        Map<String, Integer> taken = new LinkedHashMap<>();
        requested.forEach((key, amount) -> {
            int available = vault.amountOf(key);
            int take = Math.min(available, amount);
            if (take > 0) {
                // ChurchVault не має take(); знімаємо через consume-подібний прямий шлях:
                // add() з від'ємним не існує — розширюємо ChurchVault методом take() нижче.
                if (vault.take(key, take)) {
                    taken.put(key, take);
                }
            }
        });
        if (!taken.isEmpty()) {
            persistState();
        }
        return taken;
    }

    /** Саботаж: пересуває замовлення випадкового члена церкви (не самого агента). */
    public Optional<UUID> delayRandomBrewingOrder(String churchId, long delayMillis, UUID exceptPlayer) {
        List<Membership> victims = memberships.values().stream()
                .filter(m -> m.institutionId().equals(churchId))
                .filter(m -> !m.playerId().equals(exceptPlayer))
                .filter(m -> m.activeOrder() != null
                        && !m.activeOrder().isReady(System.currentTimeMillis()))
                .toList();
        if (victims.isEmpty()) {
            return Optional.empty();
        }
        Membership victim = victims.get(random.nextInt(victims.size()));
        PotionOrder old = victim.activeOrder();
        victim.setActiveOrder(new PotionOrder(old.pathwayName(), old.sequence(),
                old.readyAtEpochMillis() + delayMillis, old.pointsPaid()));
        persist();
        Player online = Bukkit.getPlayer(victim.playerId());
        if (online != null && online.isOnline()) {
            online.sendMessage(PREFIX + ChatColor.RED + "У варильні щось пішло не так — "
                    + "ваше зілля затримається.");
        }
        return Optional.of(victim.playerId());
    }

    /** Викриття подвійного агента: вигнання = той самий необоротний leave + розголос. */
    public boolean expelExposedSpy(Player player) {
        Optional<Institution> church = churchOf(player.getUniqueId());
        if (church.isEmpty()) {
            return false;
        }
        String churchId = church.get().id();
        boolean left = leave(player);
        if (left) {
            player.sendMessage(PREFIX + ChatColor.DARK_RED + "" + ChatColor.BOLD
                    + "Вас викрито! Церква вигнала вас назавжди.");
            broadcastToChurch(churchId, ChatColor.DARK_RED + "Викрито зрадника: "
                    + ChatColor.RED + player.getName() + ChatColor.DARK_RED + "!");
        }
        return left;
    }

    public void broadcastToChurch(String churchId, String message) {
        for (Membership m : memberships.values()) {
            if (!m.institutionId().equals(churchId)) {
                continue;
            }
            Player online = Bukkit.getPlayer(m.playerId());
            if (online != null && online.isOnline()) {
                online.sendMessage(PREFIX + message);
            }
        }
    }

    /** Вбивство рейдера під тривогою членом церкви-цілі. */
    public void onTempleDefended(Player killer, String churchId) {
        Membership membership = memberships.get(killer.getUniqueId());
        if (membership == null || !membership.institutionId().equals(churchId)) {
            return;
        }
        membership.addContribution(DEFEND_REWARD_POINTS);
        killer.sendMessage(PREFIX + ChatColor.GREEN + "Ви захистили храм: +"
                + DEFEND_REWARD_POINTS + " очок.");
        persist();
    }
```

І додай у `ChurchVault` атомарний `take` (дзеркало `OrderStash.take`):

```java
    /** Атомарне зняття (крадіжка рейду 6c); false — бракує, нічого не змінюється. */
    public boolean take(String itemKey, int amount) {
        int have = amountOf(itemKey);
        if (have < amount) {
            return false;
        }
        if (have == amount) {
            items.remove(itemKey);
        } else {
            items.put(itemKey, have - amount);
        }
        return true;
    }
```

- [ ] **Step 2: Add unit test for ChurchVault.take** — допиши в наявний `src/test/java/me/vangoo/domain/organizations/ChurchVaultTest.java`:

```java
    @Test
    void takeIsAtomicSecondVaultExit() {
        ChurchVault vault = new ChurchVault();
        vault.add("custom:herb", 3);
        assertFalse(vault.take("custom:herb", 4));
        assertEquals(3, vault.amountOf("custom:herb"));
        assertTrue(vault.take("custom:herb", 3));
        assertEquals(0, vault.amountOf("custom:herb"));
    }
```

Run: `& $mvn -o test -Dtest=ChurchVaultTest` — Expected: PASS.

- [ ] **Step 3: Extend ChurchSiteService**

```java
    private java.util.function.Predicate<String> priestClosurePredicate = id -> false;

    public void setPriestClosurePredicate(java.util.function.Predicate<String> predicate) {
        this.priestClosurePredicate = predicate;
    }

    public List<ChurchSiteRepository.Site> sites() {
        return List.copyOf(sites);
    }

    public java.util.Optional<ChurchSiteRepository.Site> siteOf(String institutionId) {
        return sites.stream()
                .filter(s -> s.institutionId().equals(institutionId))
                .findFirst();
    }
```

і в `spawnAllNpcs()` перед `priests.spawn(...)` додай гейт:

```java
            if (priestClosurePredicate.test(s.institutionId())) {
                continue; // храм закритий після замаху — священика відродить SecretOrderService
            }
```

- [ ] **Step 4: Gate ChurchListener.onPriestClick**

Заміни тіло `onPriestClick`:

```java
    @EventHandler
    public void onPriestClick(NPCRightClickEvent event) {
        if (event.getClicker().isSneaking()) {
            return; // sneak-клік зарезервовано під шпигунські дії ордену (OrderListener)
        }
        priests.institutionOf(event.getNPC())
                .ifPresent(id -> menu.openFor(event.getClicker(), id));
    }
```

- [ ] **Step 5: Build + full test run**

Run: `& $mvn -o clean package`
Expected: BUILD SUCCESS, усі тести зелені.

- [ ] **Step 6: Commit**

```bash
git add -A src/main/java src/test/java
printf 'feat(church): expose vault theft, sabotage and defense hooks for orders\n' > "$TEMP/msg.txt"
git commit -F "$TEMP/msg.txt"
```

---

### Task 12: SecretOrderService — ядро (членство, запрошення, завдання, фавори)

**Files:**
- Create: `src/main/java/me/vangoo/application/services/SecretOrderService.java`

**Interfaces:**
- Consumes: усе з Tasks 1–11.
- Produces (використовує UI/лістенери/команда):
  - `enum JoinResult { OK, ALREADY_MEMBER, NO_PATHWAY, WRONG_PATHWAY, COOLDOWN, ABANDONED, UNKNOWN_ORDER }`
  - `JoinResult join(Player, String institutionId)` — видає талісман при OK (через `OrderItems`), генерує куратора, стартує завдання.
  - `boolean leave(Player)` — необоротно (`abandonedOrders`), кулдаун.
  - `Optional<OrderMembership> membershipOf(UUID)`, `Optional<Institution> orderOf(UUID)`, `boolean hasAbandoned(UUID, String)`.
  - `List<Institution> joinableOrders(Player)` — SECRET_ORDER + acceptsPathway + не зречений.
  - `List<Invitation> invitationsOf(UUID)`, `void onApexKilled(Player, String creaturePathway)`, `void onBeyonderKilled(Player killer)`, `void onRampagerStopped(Player)` — тригери запрошень (persist; повідомлення в чат якщо онлайн).
  - `boolean reissueTalisman(Player)` — з кулдауном.
  - `void ensureFreshTasks(Player)`, `List<OrderTask> tasksOf(Player)`, `TaskQuota taskQuota()`, `Optional<ChurchService.TaskPoolStatus> taskPoolStatus(Player)` (перевикористовує той самий record).
  - `int deliverTask(Player, int taskIndex)` — предмети з інвентаря → схованка; завершення → фавор.
  - `void onCreatureKilled(Player, String creatureId)` — HUNT-прогрес; завершення → фавор.
  - `List<Favor> favorsOf(UUID)`; `List<FavorOptions.Option> favorOptionsFor(Player, TaskWeight)`;
  - claim-методи: `boolean claimHuntInfo(Player)`, `claimVaultIntel(Player)`, `claimRecipe(Player)`, `claimIngredients(Player)`, `claimCharacteristic(Player)`, `claimClearCooldown(Player)` (знімає `rejoinCooldownUntil` і в церквах — потрібен ще один дотик: `ChurchService.clearRejoinCooldown(UUID)` — додай тривіальний метод), `claimFalsePapers(Player)` (ставить прапор `falsePapers` — Set<UUID>, персистується у `PlayerOrderData`? НІ — YAGNI: разова легенда, зберігаємо у `order-memberships.json` НЕ треба; простіше: `claimFalsePapers` НЕ реалізовуємо окремим станом, а МИТТЄВО знімає гейт шляху при наступному вступі — прапор `Set<UUID> falsePapersActive` персистується додатковим полем `boolean falsePapers` у `PlayerOrderData`; `ChurchService.join` це не знає — тому дотик: `ChurchMenu`/вступ у церкву питає `secretOrderService.consumeFalsePapersIfActive(uuid)`… ЦЕ РОЗДУВАЄ СКОУП. РІШЕННЯ: FALSE_PAPERS у цьому плані видає гравцю ЛИШЕ повідомлення-купон і НЕ інтегрується у вступ церкви — Deviation: реалізуй прапор (`boolean falsePapers` у `PlayerOrderData`) + єдиний метод `boolean consumeFalsePapersIfActive(UUID)` (true = був активний, спалено), а гейт у `ChurchService.join` заміни: `WRONG_PATHWAY` → перед поверненням спитай injected `FalsePapersCheck` (functional interface, сеттер `setFalsePapersCheck`), і якщо true — прийми. Це 5 рядків у ChurchService.)
  - `void seedStashIfAbsent(String orderId)` — для кожного доступу ордену (реалізовані шляхи) 2× інгредієнти Seq-9 рецепта; ордени «будь-хто» (порожні доступи) — по 1× інгредієнти Seq-9 КОЖНОГО реалізованого шляху.
  - `OrderStash stashOf(String orderId)`; `boolean orderHasIntel(String orderId)`; `Optional<Map<String,Integer>> intelManifest(String orderId, String churchId)`.
  - `void tick()` — хвилинний: респавн священиків із минулим `priestClosedUntil` (через `ChurchSiteService.siteOf` + `ChurchPriestService.spawn`), чистка простроченого intel.
  - `boolean isTempleClosed(String churchId)`, `boolean isTempleOnCooldown(String churchId)`.
- Стан (instance): `Map<UUID,OrderMembership> memberships`, `Map<UUID,Long> rejoinCooldownUntil`, `Map<UUID,Set<String>> abandonedOrders`, `Map<UUID,List<Invitation>> invitations`, `Map<UUID,Integer> beyonderKills`, `Map<UUID,Long> talismanReissueAfter`, `Set<UUID> falsePapers`, `Map<String,OrderStash> stashes`, `Map<String,IntelState> intel` (`record IntelState(Map<String,Integer> manifest, long expiresAt)`), `Map<String,Long> templeCooldownUntil`, `Map<String,Long> priestClosedUntil`. Гідрація в конструкторі; `persist()`/`persistState()` після кожної мутації (патерн `ChurchService` 1:1, включно з mapper-методами record↔domain).
- Конструктор: `(Plugin, OrderConfig, InstitutionRegistry, BeyonderService, PathwayManager, RecipeUnlockService, CustomItemService, CharacteristicCodec, MarketItemClassifier, MarketItemNamer, CreatureNamer, Map<String,CreatureDefinition> creatureRegistry, Map<String,Map<Integer,RecipeDefinition>> potionRecipeConfig, ChurchService, ChurchSiteService, MythicCreatureGateway, OrderItems, JSONOrderMembershipRepository, OrderStateRepository)`.

Ключова логіка (пиши за зразком `ChurchService`, ті самі приватні хелпери `countMatching`/`removeMatching`/`giveItem` — скопіюй їх, вони приватні):

- `join`: гейти (ALREADY_MEMBER → ABANDONED → COOLDOWN → UNKNOWN_ORDER/type → NO_PATHWAY якщо `pathwayNameOf(player)==null` → WRONG_PATHWAY через `acceptsPathway`) → `new OrderMembership(id, orderId, randomCurator())` → `seedStashIfAbsent(orderId)` → видай талісман (`OrderItems.createTalisman` + `giveItem`) → зніми запрошення цього ордену → `ensureFreshTasks` → persist. Пул кураторів: `List.of("Пан у сірому","Тінь за свічкою","Безіменний брат","Сестра Шепіт","Речник Мовчання")`.
- `ensureFreshTasks`: копія логіки `ChurchService.ensureFreshTasks` над `OrderMembership` + `OrderTaskGenerator.generate(config.tasksMaxActive(), order, pathwayToGroup(), creatureCandidates(), ingredientCandidatesFor(player), raidableChurches(), doubleAgentChurchOf(player), rankOf(player), random)`. `raidableChurches()` = церкви з `churchSiteService.sites()`, чий `templeCooldownUntil`/`priestClosedUntil` минув. `ingredientCandidatesFor` — інгредієнти рецептів ШЛЯХУ ГРАВЦЯ (усі seq його шляху з `potionRecipeConfig`), бо схованка живить фавори саме його прогресу. `doubleAgentChurchOf` = `churchService.churchOf(uuid)` → `ChurchTarget`.
- Завершення будь-якого завдання (deliver повний, hunt повний, а темпл/шпигунські — з Task 13) → `membership.addFavor(new Favor(task.weight(), now))` + повідомлення «Куратор запам'ятав це.» (+ LIGHT з шансом 0.35 (`random`) — БЕЗ фавора, повідомлення «Куратор лише кивнув.»; пін цього правила юніт-тестом НЕ треба — рандом сервіса).
- `claimIngredients`: рецепт наступної послідовності гравця (`beyonder.getSequenceLevel()-1`) з `potionRecipeConfig`; збери список ключів (`ingredientKey`, без `vanilla:`); зі схованки візьми до `config.favorIngredientsPerClaim()` різних ключів по 1 шт (`stash.take(key,1)`); якщо взято 0 — поверни фавор не списуючи і скажи чого бракує (`MarketItemNamer.displayName`); інакше — `giveItem` кожен через `customItemService.createItemStack(idWithoutPrefix)`.
- `claimCharacteristic`: ключ `characteristic:<шлях>:<nextSeq>`; `stash.take(key,1)` → `characteristicCodec.create(pathway, nextSeq, 1)` → giveItem; нема — фавор лишається, повідом брак.
- `claimRecipe`: `recipeUnlockService.unlockRecipe(uuid, pathway, nextSeq)` — знання напряму, повз схованку.
- `claimHuntInfo`: апекс-істоти групи шляху гравця з `creatureRegistry` (tier == `CreatureTier.APEX`) → повідомлення з `creatureNamer.displayName` + `biomeNames` (+ «біля структур»).
- `claimVaultIntel`: покажи маніфест першого свіжого intel ордену (`MarketItemNamer.displayName` кожного ключа) в чат.
- Усі claim: спершу `favorOptionsFor` мусить містити опцію (інакше false), потім `membership.consumeFavor(FavorOptions.requiredWeight(option))`; якщо матеріальний claim не видав НІЧОГО — фавор ПОВЕРНИ (`membership.addFavor(spent)`).

- [ ] **Step 1: Implement the service** (за блоком Interfaces; ~600 рядків; персист/гідрація — калька `ChurchService.hydrate/persist/persistState` з record-мапперами `toTask/toRecord/toFavor/...`).

- [ ] **Step 2: Build**

Run: `& $mvn -o clean package`
Expected: BUILD SUCCESS (юніти доменних Task 1–8 лишаються зелені).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/me/vangoo/application/services/SecretOrderService.java src/main/java/me/vangoo/application/services/ChurchService.java
printf 'feat(orders): add secret order service core with favors and invites\n' > "$TEMP/msg.txt"
git commit -F "$TEMP/msg.txt"
```

(`ChurchService` у цьому коміті — лише якщо додавав `clearRejoinCooldown`/`FalsePapersCheck`.)

---

### Task 13: RaidSession + операції (рейд, замах, шпигунство, викриття)

**Files:**
- Create: `src/main/java/me/vangoo/application/services/RaidSession.java`
- Modify: `src/main/java/me/vangoo/application/services/SecretOrderService.java` (методи операцій)

**Interfaces:**
- Produces (`RaidSession`, патерн `DuelSession` + самотік `OrganizerBriefing`):

```java
public class RaidSession {
    public RaidSession(UUID playerId, String churchId, org.bukkit.Location siteCenter,
                       int channelSeconds, double alarmChancePerSecond, int zoneRadius,
                       Runnable onAlarm, Runnable onSuccess, Runnable onFail) {...}
    public void start(org.bukkit.plugin.Plugin plugin, java.util.Random random) // runTaskTimer 20 тіків
    public void cancel()
    public boolean alarmed()
    public UUID playerId()
    public String churchId()
}
```

Тік: гравець офлайн/мертвий/поза `zoneRadius` від центру → `onFail`+cancel; інакше progress++ (action bar `⚒ Злом... N/Т` через `player.sendActionBar` або `spigot().sendMessage(ChatMessageType.ACTION_BAR, ...)` — глянь, як шле `DuelBriefing`, і зроби так само); одна перевірка `random.nextDouble() < alarmChancePerSecond` поки `!alarmed` → `alarmed=true; onAlarm.run()`; progress == channelSeconds → `onSuccess` + cancel.

- Produces (нові методи `SecretOrderService`):
  - `boolean startRaid(Player player)` — гейти: має RAID-задачу; ніч (`world.getTime()` в [13000,23000]); у радіусі сайту цілі; храм не на кулдауні/не закритий; нема активної сесії. Створює RaidSession (intel → `RaidPlanner.alarmChancePerSecond`), реєстр `Map<UUID,RaidSession> raids` (instance!).
  - `onAlarm`: спавни `config.raidGuards()` істот домену церкви (пул: `creatureRegistry` де група шляху істоти ∈ групи доменів церкви, seq ≤ 7; фолбек — будь-які) через `gateway.spawn` біля гравця; `churchService.broadcastToChurch(churchId, "На храм ... напад! (x, z)")`.
  - `onSuccess`: `RaidPlanner.rollLoot(churchService.vaultSnapshot(churchId), picks, hasIntel, random)` → `churchService.stealFromVault` → видай предмети гравцю: `custom:` → `customItemService.createItemStack`; `recipe:<p>:<s>` → `recipeBookFactory.createRecipeBook(potionManager.getPotionsPathway(p).orElseThrow(), s)` (додай `PotionManager potionManager` і `RecipeBookFactory recipeBookFactory` у конструктор сервісу); `characteristic:<p>:<s>` → `characteristicCodec.create(p, s, n)` → запам'ятай ключі здобичі в RAID-задачі... НІ — задача НЕ зараховується тут: фавор за RAID видається лише після ЗДАЧІ здобичі в схованку (кнопка RAID-плитки в меню, Task 14, метод `int depositRaidLoot(Player, int taskIndex)` — знімає через `removeMatching` предмети, чиї itemKey є в записаному луті задачі, кладе в схованку, і по здачі всього — прогрес `withProgress(1)` → фавор). Для цього додай на `OrderTask` персистентне поле нема — НЕ треба: зберігай мапу «playerId → останній лут рейду» (`Map<UUID, Map<String,Integer>> pendingRaidLoot`, персистується ДОДАТКОВИМ полем `Map<String,Integer> pendingRaidLoot` у `PlayerOrderData` — Task 9 виконавцю: додай це поле одразу; старі файли → null → порожньо) → кулдаун храму `templeCooldownUntil.put(churchId, now + hours)` → persistState.
  - `onFail`: кулдаун храму; якщо це церква самого гравця (подвійний агент) — ролл `exposureFailedRaidChance` → `churchService.expelExposedSpy(player)`; RAID-задача згорає (прибери з пулу).
  - `void onRaiderDied(Player raider, Player killerOrNull)` — виклик з лістенера: активна сесія → onFail; якщо killer — член церкви-цілі → `churchService.onTempleDefended(killer, churchId)`.
  - `boolean startAssassination(Player player, String priestInstitutionId)` — виклик з лістенера при ударі по NPC: має ASSASSINATE-задачу на цю церкву → despawn священика (`ChurchPriestService.despawnAt(institutionId, npcLocation)` — передай `ChurchPriestService` у конструктор), спавн охоронця (істота домену церкви, найвища доступна seq... обери з `creatureRegistry` мінімальну `sequence()` [найсильнішу] серед домену; фолбек будь-яка) через gateway, реєстр `Map<UUID,UUID> assassinationGuards` (guardUuid → playerId); повідомлення.
  - `void onGuardKilled(UUID guardUuid, Player killer)` — замах зараховано: прогрес задачі → фавор; `priestClosedUntil.put(churchId, now + respawnHours)`; broadcast церкві «Священика вбито…»; persistState.
  - `boolean performSpyAction(Player player, String priestInstitutionId)` — sneak-клік по священику СВОЄЇ церкви: активна RECON → intel: `intel.put(orderId+"|"+churchId, new IntelState(churchService.vaultSnapshot(churchId), now+ttl))`, прогрес→фавор, ролл викриття recon; активна SABOTAGE → `churchService.delayRandomBrewingOrder(churchId, hours→millis, playerId)`, прогрес→фавор, ролл викриття sabotage. Викриття: `churchService.expelExposedSpy(player)`. true якщо була шпигунська дія (лістенер тоді не відкриває меню).
  - `void endAllRaids()` — для onDisable.

- [ ] **Step 1: Implement RaidSession** (повний клас за Interfaces).

- [ ] **Step 2: Implement operation methods у SecretOrderService** (за Interfaces).

- [ ] **Step 3: Build**

Run: `& $mvn -o clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/me/vangoo/application/services/RaidSession.java src/main/java/me/vangoo/application/services/SecretOrderService.java
printf 'feat(orders): add raid session, assassination and espionage operations\n' > "$TEMP/msg.txt"
git commit -F "$TEMP/msg.txt"
```

---

### Task 14: OrderMenu (талісман-меню, вступ, куратор)

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/ui/OrderMenu.java`

**Interfaces:**
- Consumes: `SecretOrderService`, `MarketItemNamer`, `CreatureNamer`, `ConfirmationMenu`, `OrderItems`.
- Produces: `void openJoinPicker(Player)` (з шифрованого послання: пагінований список `joinableOrders`; клік → `confirmJoin`), `void confirmJoin(Player, Institution)` (ConfirmationMenu: «Віддаєте» — послання, вірність, необоротний вихід, кулдаун; «Отримаєте» — талісман, завдання, фавори куратора; onConfirm → зніми 1 послання з руки + `secretOrderService.join`), `void openMain(Player)` (Завдання / Куратор / Мій орден), `void openTasks(Player)` (плитки завдань: DELIVER із кнопкою здачі → `deliverTask`; RAID/ASSASSINATE/RECON/SABOTAGE — плитка-пояснення ЯК виконувати; квота-плитка як `ChurchMenu.quotaTile`), `void openCurator(Player)` (список фаворів + кнопки прохань за `favorOptionsFor`; кожен claim через `ConfirmationMenu` якщо витрачає MAJOR).

Патерни бери з `ChurchMenu` (той самий triumph-gui: `Gui.gui().rows(...).disableAllInteractions()`, `GuiItem`, `formatDuration` — скопіюй приватний хелпер). Усі тексти українською, стиль реплік куратора — таємничий, коротко.

- [ ] **Step 1: Implement OrderMenu** (за Interfaces; ~350 рядків).

- [ ] **Step 2: Build**

Run: `& $mvn -o clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/me/vangoo/infrastructure/ui/OrderMenu.java
printf 'feat(orders): add talisman-driven order menu with curator favors\n' > "$TEMP/msg.txt"
git commit -F "$TEMP/msg.txt"
```

---

### Task 15: OrderListener + OrderCommand

**Files:**
- Create: `src/main/java/me/vangoo/presentation/listeners/OrderListener.java`
- Create: `src/main/java/me/vangoo/presentation/commands/OrderCommand.java`

**Interfaces:**
- `OrderListener(SecretOrderService, OrderMenu, OrderItems, MythicCreatureGateway, ChurchPriestService, BeyonderService, RampageManager, Map<String,CreatureDefinition> creatureRegistry)`:
  - `PlayerInteractEvent` (RIGHT_CLICK_AIR/BLOCK, main hand): талісман → `orderMenu.openMain`; послання → член? підказка : `orderMenu.openJoinPicker`; event.setCancelled(true) для обох. Талісман + `player.isSneaking()` → `secretOrderService.startRaid(player)` (старт злому біля храму).
  - `NPCRightClickEvent`: якщо клікер присідає → `priests.institutionOf(npc)` → `secretOrderService.performSpyAction(player, id)`.
  - `net.citizensnpcs.api.event.NPCDamageByEntityEvent` (удар по NPC): нападник Player з ASSASSINATE → `secretOrderService.startAssassination(player, institutionId)`.
  - `EntityDeathEvent`: (а) `creatures.creatureId` → `secretOrderService.onCreatureKilled(killer, id)` (HUNT) + якщо `creatureRegistry.get(id).tier() == CreatureTier.APEX` → `onApexKilled(killer, def.pathway())`; (б) `secretOrderService.onGuardKilled(entity.getUniqueId(), killer)` (охоронець замаху).
  - `PlayerDeathEvent`: (а) жертва мала активний рейд → `onRaiderDied(victim, killer)`; (б) killer існує і жертва — Beyonder зі шляхом (`beyonderService.getBeyonder(...)`) → `onBeyonderKilled(killer)`; (в) жертва в рампейджі → `onRampagerStopped(killer)`.
  - `PlayerJoinEvent`: нагадування про запрошення (`invitationsOf`) з клікабельним текстом «/order invites».
- `OrderCommand(SecretOrderService)` — патерн `ChurchCommand`:
  - `/order invites` — список + `/order accept <id>`;
  - `/order accept <institutionId>` — `acceptInvitation`;
  - `/order talisman` — `reissueTalisman`;
  - `/order leave` / `/order leave confirm` — двокрокова, текст «двері зачиняться назавжди»;
  - `/order info` — орден, куратор, ранг (`OrderRank.of(seq).displayName()`), фавори, завдання.
  - TabCompleter: `invites`, `accept`, `talisman`, `leave`, `info`.

- [ ] **Step 1: Implement OrderListener** (за Interfaces).
- [ ] **Step 2: Implement OrderCommand** (за Interfaces).
- [ ] **Step 3: Build**

Run: `& $mvn -o clean package`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/me/vangoo/presentation/listeners/OrderListener.java src/main/java/me/vangoo/presentation/commands/OrderCommand.java
printf 'feat(orders): add order listener and player command\n' > "$TEMP/msg.txt"
git commit -F "$TEMP/msg.txt"
```

---

### Task 16: Wiring (ServiceContainer, плагін, plugin.yml, config.yml, scheduler)

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/schedulers/OrderScheduler.java`
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java`
- Modify: `src/main/java/me/vangoo/MysteriesAbovePlugin.java`
- Modify: `src/main/resources/plugin.yml`
- Modify: `src/main/resources/config.yml`

**Interfaces:**
- `OrderScheduler(Plugin, SecretOrderService)` — `start()`: `runTaskTimer` кожні 1200 тіків (хвилина) → `secretOrderService.tick()`; `stop()`. Патерн `ChurchOrderScheduler`.

- [ ] **Step 1: Створи OrderScheduler** (за патерном `ChurchOrderScheduler` — відкрий і скопіюй структуру).

- [ ] **Step 2: ServiceContainer** — у фазах (дивись існуючий блок церков, рядки ~229–390):
  - `initializeInfrastructure`: `orderConfig = OrderConfig.load(plugin)`; `orderMembershipRepository = new JSONOrderMembershipRepository(plugin.getDataFolder() + File.separator + "order-memberships.json")`; `orderStateRepository = new OrderStateRepository(... + "orders-state.json")`; `orderItems = new OrderItems(customItemService)` (ПІСЛЯ customItemService).
  - `initializeApplicationServices` (ПІСЛЯ churchService + churchSiteService + churchPriestService):
    ```java
    this.secretOrderService = new SecretOrderService(plugin, orderConfig, institutionRegistry,
            beyonderService, pathwayManager, recipeUnlockService, customItemService,
            characteristicCodec, marketItemClassifier, marketItemNamer, creatureNamer,
            creatureRegistry, potionRecipeConfig, churchService, churchSiteService,
            churchPriestService, mythicCreatureGateway, potionManager, recipeBookFactory,
            orderItems, orderMembershipRepository, orderStateRepository);
    churchSiteService.setPriestClosurePredicate(secretOrderService::isTempleClosed);
    churchService.setFalsePapersCheck(secretOrderService::consumeFalsePapersIfActive);
    ```
    (точний список параметрів звір із фінальним конструктором Task 12/13).
  - `initializeUI`: `orderMenu = new OrderMenu(plugin, secretOrderService, marketItemNamer, creatureNamer, confirmationMenu, orderItems)`.
  - `initializeSchedulers`: `orderScheduler = new OrderScheduler(plugin, secretOrderService)` + `start()`/`stop()` у відповідних методах.
  - Геттери: `getSecretOrderService()`, `getOrderMenu()`, `getOrderItems()`.
- [ ] **Step 3: MysteriesAbovePlugin**:
  - `registerEvents()`: `new OrderListener(...)` через getters.
  - `registerCommands()`: `getCommand("order").setExecutor(new OrderCommand(services.getSecretOrderService()))` + TabCompleter.
  - `onDisable()`: перед `saveAll()` — `services.getSecretOrderService().endAllRaids()`.
- [ ] **Step 4: plugin.yml** — додай:

```yaml
  order:
    description: Таємні організації — членство, завдання, фавори
    usage: /order <invites|accept|talisman|leave|info>
```

- [ ] **Step 5: config.yml** — додай секцію-документацію (значення = дефолти з коду):

```yaml
# Таємні організації (Спек 6c)
orders:
  invites:
    beyonder-kills: 3
  tasks:
    refresh-hours: 24
    max-active: 2
    sets-per-window: 5
  raid:
    channel-seconds: 45
    alarm-chance: 0.04
    alarm-intel-factor: 0.5
    loot-picks: 3
    loot-intel-picks: 4
    temple-cooldown-hours: 24
    zone-radius: 24
    guards: 2
  assassination:
    priest-respawn-hours: 12
  sabotage:
    delay-hours: 6
  recon:
    ttl-hours: 48
  exposure:
    recon-chance: 0.15
    sabotage-chance: 0.35
    failed-raid-chance: 0.5
  stash:
    seed-ingredients-per-recipe: 2
  favor:
    ingredients-per-claim: 2
  rejoin-cooldown-days: 3
  talisman:
    reissue-cooldown-minutes: 30
```

- [ ] **Step 6: Full build + all tests**

Run: `& $mvn -o clean package`
Expected: BUILD SUCCESS, всі тести (включно з `ArchitectureTest` — `domain.organizations` лишився чистим).

- [ ] **Step 7: Commit**

```bash
git add -A
printf 'feat(orders): wire secret orders through container, plugin and config\n' > "$TEMP/msg.txt"
git commit -F "$TEMP/msg.txt"
```

---

### Task 17: Документація + правило + роадмап + in-server чекліст

**Files:**
- Create: `.claude/rules/secret-orders.md`
- Modify: `.claude/rules/church-organizations.md`
- Modify: `CLAUDE.md`
- Modify: `docs/superpowers/specs/2026-06-26-ingredient-economy-roadmap.md`

- [ ] **Step 1: `.claude/rules/secret-orders.md`** — за структурою `church-organizations.md`: механізм (вступ/членство/ранг=Seq/завдання/фавори/рейд/замах/шпигунство/викриття), персистентність (схеми обох файлів + зворотна сумісність null-полів), «як міняти темп», інваріанти (схованка невидима; виходи схованки — лише фавори; сира Характеристика гравцю — лише здобич рейду або MAJOR-фавор; вихід із ордену необоротний; ≤1 храмова і ≤1 шпигунська операція на набір; стан — instance-поля), заборони (❌ мутувати OrderStash повз SecretOrderService; ❌ видавати нагороди повз claim-методи; ❌ Bukkit у domain.organizations).
- [ ] **Step 2: Онови `church-organizations.md`**: сховище тепер має ДВА виходи (варіння + крадіжка рейдом, `ChurchVault.take`/`stealFromVault`); викриття шпигуна → `expelExposedSpy` (leave + broadcast); замах → despawn/respawn священика (`priestClosedUntil`, гейт у `spawnAllNpcs`); доручення «Захисти храм» (`onTempleDefended`, `DEFEND_REWARD_POINTS`); sneak-клік по священику зарезервовано під шпигунські дії; `FalsePapersCheck` у `join`.
- [ ] **Step 3: Онови `CLAUDE.md`**: секція config (`orders.*`), персистентність (`order-memberships.json`, `orders-state.json`), команда `/order` (гравецька), посилання на нове правило.
- [ ] **Step 4: Онови роадмап**: 6c — **реалізовано** (короткий підсумок + примітка: сід схованок орденів — друге розширення винятку емісії; правило `.claude/rules/secret-orders.md`).
- [ ] **Step 5: Commit**

```bash
git add .claude/rules/secret-orders.md .claude/rules/church-organizations.md CLAUDE.md docs/superpowers/specs/2026-06-26-ingredient-economy-roadmap.md
printf 'docs(orders): add secret orders rule and update church docs and roadmap\n' > "$TEMP/msg.txt"
git commit -F "$TEMP/msg.txt"
```

- [ ] **Step 6: In-server чекліст** (ручна верифікація на тест-сервері; `/custom-items give <ти> order_cipher_message`):
  1. Послання → ПКМ → пікер орденів (лише ті, що приймають твій шлях) → підтвердження → талісман у руках, послання зникло.
  2. Гравець без шляху: ПКМ посланням → відмова «орденам потрібна сила».
  3. Талісман → меню: завдання згенеровані; квота-плитка тікає.
  4. DELIVER: принеси інгредієнти → здача → фавор у куратора.
  5. Куратор: без рецепта наступної посл. → «попросити рецепт» (STANDARD-фавор) → рецепт розблоковано; з рецептом → «інгредієнти» (≤2 зі схованки) / «Характеристика» (MAJOR + Довірений).
  6. RAID: вночі біля храму, sneak+ПКМ талісманом → канал у action bar; вихід із зони → провал; тривога → варта спавниться, члени церкви онлайн бачать сповіщення; смерть рейдера від захисника → захисник +250 очок церкви; успіх → предмети в інвентарі, сховище церкви схудло, кулдаун храму.
  7. Подвійний агент: sneak-клік по СВОЄМУ священику з RECON → розвіддані (наступний рейд цієї церкви бачить маніфест і рідше тривожиться); із SABOTAGE → замовлення іншого члена затрималось; ролл викриття → вигнання з церкви + broadcast «Викрито зрадника».
  8. ASSASSINATE: удар по священику → охоронець; вбий → священик зник, меню церкви недоступне; через `priest-respawn-hours` (постав 0.01 для тесту) — повернувся.
  9. Запрошення: вбий апекса → шепіт-запрошення; вийди з серверу/зайди → нагадування; `/order accept <id>` працює.
  10. `/order leave` → попередження; `/order leave confirm` → вихід; повторний вступ у ТОЙ САМИЙ орден — «двері зачинено», в інший — кулдаун.
  11. Рестарт сервера: членства, фавори, запрошення, схованки, розвіддані, кулдаун храму, закритий храм — усе відновлено.

---

## Self-Review (виконано автором плану)

1. **Spec coverage:** вступ (ключ+запрошення) — Tasks 8, 10, 12, 15; ранг=Seq — Task 1; завдання+ваги — Tasks 2, 7; фавори need-based — Tasks 3, 6, 12; схованка+сід+інваріант — Tasks 4, 12; рейд (ніч/канал/тривога/варта/здобич/кулдаун) — Tasks 5, 13; замах+закриття храму — Tasks 11, 13; RECON/SABOTAGE/викриття — Tasks 11, 13; контррозвідка «Захисти храм» — Tasks 11, 13, 15; послуги (CLEAR_COOLDOWN, FALSE_PAPERS) — Tasks 3, 12; персистентність — Task 9; wiring/конфіг/команда — Task 16; докси — Task 17. Гепів не знайдено.
2. **Відхилення від спеку, зафіксовані свідомо:** (а) FALSE_PAPERS інтегровано мінімально через injected `FalsePapersCheck` у `ChurchService.join`; (б) «легкі завдання інколи без нагороди» — шанс 0.35 у сервісі, без юніт-піна; (в) здобич RAID зараховує задачу одразу при успіху злому (здача в схованку — окремий крок DELIVER-подібної кнопки в меню НЕ вимагається: фавор дається после здачі лута кнопкою «Здати здобич» у меню завдань — реалізуй у Task 14 як кнопку RAID-плитки, що знімає предмети через `removeMatching` по ключах лута і кладе в схованку). Виконавцю Task 13/14: тримай інваріант «фавор за RAID — лише після здачі предметів у схованку».
3. **Type consistency:** `TaskQuota` перевикористано з 6b без змін; `ChurchService.TaskPoolStatus` перевикористано; `OrderTaskGenerator.ChurchTarget` — єдиний тип цілі церкви; `stealFromVault` повертає фактичну мапу; `OrderItems` id-константи спільні для yml і коду.
