# Церкви (Економіка 6b) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Реєстр усіх канонічних інституцій + церкви: автогенеровані храми біля сіл, членство з вкладом/рангами, служба (полювання/доставка/рампейджери/пожертви), замовлення готового зілля зі сховища церкви, ініціація гравця без шляху.

**Architecture:** Чисте ядро `domain.organizations` (реєстр, членство, ранги, генератор завдань, сховище, замовлення — юніт-тести) + оркестратор `ChurchService` в `application` + адаптери в `infrastructure` (конфіг, 3 JSON-репозиторії, Citizens-священик, розміщення структур, triumph-gui меню) + лістенери/команда в `presentation`. Спек: `docs/superpowers/specs/2026-07-12-ingredient-economy-churches-design.md`.

**Tech Stack:** Java 21, Paper API 1.21 (provided), Citizens (depend), MythicMobs (depend), triumph-gui (shaded), Gson, JUnit 5 + ArchUnit.

## Global Constraints

- Увесь user-facing текст — українською; ідентифікатори/логи — англійською (`.claude/rules/localization.md`).
- Коміти — Conventional Commits англійською (`.claude/rules/commit-messages.md`); коміт-повідомлення передавати через `git commit -F <msgfile>` (here-strings ламаються).
- `domain.organizations` — нуль `org.bukkit..`/`dev.triumphteam..`/`net.kyori..`; додається в `PURE_DOMAIN` `ArchitectureTest` у Task 1.
- `mvn` НЕ в PATH. Запуск тестів у PowerShell:
  ```powershell
  $mvn = (Get-ChildItem "C:\Program Files\JetBrains" -Recurse -Filter mvn.cmd | Select-Object -First 1).FullName
  & $mvn -o test "-Dtest=InstitutionRegistryTest"
  ```
- Назви шляхів у реєстрі — ключі `PathwayManager` БЕЗ пробілів (`"WhiteTower"`, не `"White Tower"`); для нереалізованих шляхів — той самий CamelCase-конвеншн (`"TwilightGiant"`, `"HangedMan"`, `"RedPriest"`, `"WheelOfFortune"`, `"BlackEmperor"`).
- Послідовності: 9 = найслабша … 0 = найсильніша. «Доступ до N-ї послідовності» = `minSequence = N`; повний доступ = `minSequence = 0`.
- itemKey-формати як на ринку: `custom:<id>`, `recipe:<pathway>:<seq>`, `characteristic:<pathway>:<seq>`.
- Кожна нова команда — оголошення в `plugin.yml` + `setExecutor` у `MysteriesAbovePlugin.registerCommands()`; кожен listener — реєстрація в `registerEvents()`; scheduler — `start()`/`stop()` у `ServiceContainer`.
- Персистентність: запис після кожної мутації (патерн `GatheringSnapshotRepository`); сирі Характеристики зі сховища гравцям НЕ видаються (лише списання у варіння).
- Робота — на гілці `feat/church-organizations` (створити з `main` перед Task 1: `git checkout -b feat/church-organizations`).

---

### Task 1: Домен — `InstitutionType`, `PathwayAccess`, `Institution` + ArchUnit

**Files:**
- Create: `src/main/java/me/vangoo/domain/organizations/InstitutionType.java`
- Create: `src/main/java/me/vangoo/domain/organizations/PathwayAccess.java`
- Create: `src/main/java/me/vangoo/domain/organizations/Institution.java`
- Modify: `src/test/java/me/vangoo/architecture/ArchitectureTest.java:22-30` (додати `"me.vangoo.domain.organizations"` у `PURE_DOMAIN`)
- Test: `src/test/java/me/vangoo/domain/organizations/InstitutionTest.java`

**Interfaces:**
- Produces: `enum InstitutionType { CHURCH, SECRET_ORDER }`
- Produces: `PathwayAccess(String pathwayName, int minSequence, String branch)` record — `full(name)`, `full(name, branch)`, `partial(name, minSeq)`, `partial(name, minSeq, branch)`, `isFull()`, `supportsSequence(int seq)`.
- Produces: `Institution(String id, InstitutionType type, String displayName, String lore, List<PathwayAccess> accesses)` record — `accessFor(String pathwayName)`, `acceptsAnyPathway()`, `acceptsPathway(String pathwayNameOrNull)`.

- [ ] **Step 1: Написати падаючий тест**

```java
package me.vangoo.domain.organizations;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class InstitutionTest {

    @Test
    void fullAccessSupportsEverySequence() {
        PathwayAccess a = PathwayAccess.full("Door");
        assertTrue(a.isFull());
        assertEquals(0, a.minSequence());
        assertTrue(a.supportsSequence(0));
        assertTrue(a.supportsSequence(9));
    }

    @Test
    void partialAccessCapsAtMinSequence() {
        PathwayAccess a = PathwayAccess.partial("Fool", 3);
        assertFalse(a.isFull());
        assertTrue(a.supportsSequence(3));
        assertTrue(a.supportsSequence(9));
        assertFalse(a.supportsSequence(2));
    }

    @Test
    void accessValidatesArguments() {
        assertThrows(IllegalArgumentException.class, () -> PathwayAccess.partial("Fool", -1));
        assertThrows(IllegalArgumentException.class, () -> PathwayAccess.partial("Fool", 10));
        assertThrows(IllegalArgumentException.class, () -> PathwayAccess.full(" "));
    }

    @Test
    void churchRequiresAccessesAndAcceptsPathlessPlayers() {
        assertThrows(IllegalArgumentException.class, () -> new Institution(
                "church-x", InstitutionType.CHURCH, "X", "лор", List.of()));

        Institution church = new Institution("church-evernight", InstitutionType.CHURCH,
                "Церква Богині Вічної Ночі", "лор",
                List.of(PathwayAccess.full("Darkness"), PathwayAccess.partial("Fool", 3)));
        assertTrue(church.acceptsPathway(null));            // без шляху — можна
        assertTrue(church.acceptsPathway("Fool"));          // PARTIAL не блокує вступ
        assertFalse(church.acceptsPathway("Error"));        // чужий шлях
        assertFalse(church.acceptsAnyPathway());
        assertEquals(Optional.of(3),
                church.accessFor("fool").map(PathwayAccess::minSequence)); // case-insensitive
    }

    @Test
    void secretOrderWithNoAccessesAcceptsAnyone() {
        Institution order = new Institution("order-mirror-people", InstitutionType.SECRET_ORDER,
                "Дзеркальні Люди", "лор", List.of());
        assertTrue(order.acceptsAnyPathway());
        assertTrue(order.acceptsPathway("Error"));
        assertTrue(order.acceptsPathway(null));
    }
}
```

- [ ] **Step 2: Запустити тест — має впасти (класів не існує)**

```powershell
$mvn = (Get-ChildItem "C:\Program Files\JetBrains" -Recurse -Filter mvn.cmd | Select-Object -First 1).FullName
& $mvn -o test "-Dtest=InstitutionTest"
```
Очікування: COMPILATION ERROR (cannot find symbol `PathwayAccess`).

- [ ] **Step 3: Реалізувати класи**

`InstitutionType.java`:
```java
package me.vangoo.domain.organizations;

/** Тип інституції: церква (домен ортодоксального бога) чи таємна організація. */
public enum InstitutionType { CHURCH, SECRET_ORDER }
```

`PathwayAccess.java`:
```java
package me.vangoo.domain.organizations;

/**
 * Доступ інституції до шляху. {@code minSequence} — найнижча (найсильніша) послідовність,
 * яку інституція підтримує: 0 = повний доступ, N = «неповний до N-ї послідовності».
 * {@code branch} — опційна помітка підвладної групи (для Церкви Блазня), інакше "".
 */
public record PathwayAccess(String pathwayName, int minSequence, String branch) {

    public PathwayAccess {
        if (pathwayName == null || pathwayName.isBlank()) {
            throw new IllegalArgumentException("pathwayName must not be blank");
        }
        if (minSequence < 0 || minSequence > 9) {
            throw new IllegalArgumentException("minSequence must be in [0..9]: " + minSequence);
        }
        branch = branch == null ? "" : branch;
    }

    public static PathwayAccess full(String pathwayName) {
        return new PathwayAccess(pathwayName, 0, "");
    }

    public static PathwayAccess full(String pathwayName, String branch) {
        return new PathwayAccess(pathwayName, 0, branch);
    }

    public static PathwayAccess partial(String pathwayName, int minSequence) {
        return new PathwayAccess(pathwayName, minSequence, "");
    }

    public static PathwayAccess partial(String pathwayName, int minSequence, String branch) {
        return new PathwayAccess(pathwayName, minSequence, branch);
    }

    public boolean isFull() {
        return minSequence == 0;
    }

    /** Чи підтримує інституція цю послідовність шляху (9 = найслабша … 0 = найсильніша). */
    public boolean supportsSequence(int sequence) {
        return sequence >= minSequence;
    }
}
```

`Institution.java`:
```java
package me.vangoo.domain.organizations;

import java.util.List;
import java.util.Optional;

/** Канонічна інституція: церква або таємна організація. Незмінний VO. */
public record Institution(
        String id,
        InstitutionType type,
        String displayName,
        String lore,
        List<PathwayAccess> accesses) {

    public Institution {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (type == InstitutionType.CHURCH && accesses.isEmpty()) {
            throw new IllegalArgumentException("church must declare pathway accesses: " + id);
        }
        accesses = List.copyOf(accesses);
    }

    public Optional<PathwayAccess> accessFor(String pathwayName) {
        return accesses.stream()
                .filter(a -> a.pathwayName().equalsIgnoreCase(pathwayName))
                .findFirst();
    }

    /** Порожні доступи в SECRET_ORDER = «шляхи залежать від учасників» (приймає будь-кого). */
    public boolean acceptsAnyPathway() {
        return type == InstitutionType.SECRET_ORDER && accesses.isEmpty();
    }

    /** Правило вступу: церква приймає гравця без шляху або зі шляхом зі своїх доступів. */
    public boolean acceptsPathway(String pathwayNameOrNull) {
        if (acceptsAnyPathway()) {
            return true;
        }
        if (pathwayNameOrNull == null) {
            return type == InstitutionType.CHURCH;
        }
        return accessFor(pathwayNameOrNull).isPresent();
    }
}
```

- [ ] **Step 4: Додати пакет у `PURE_DOMAIN`**

У `src/test/java/me/vangoo/architecture/ArchitectureTest.java` до масиву `PURE_DOMAIN` (після `"me.vangoo.domain.market"`) додати:
```java
            "me.vangoo.domain.market",
            "me.vangoo.domain.organizations"
```

- [ ] **Step 5: Запустити тести — мають пройти**

```powershell
& $mvn -o test "-Dtest=InstitutionTest,ArchitectureTest"
```
Очікування: PASS (обидва).

- [ ] **Step 6: Коміт**

```bash
git add src/main/java/me/vangoo/domain/organizations src/test/java/me/vangoo/domain/organizations src/test/java/me/vangoo/architecture/ArchitectureTest.java
git commit -F <(printf 'feat(organizations): add institution and pathway access domain model\n')
```
(або через msgfile: `printf '...' > /tmp/msg.txt && git commit -F /tmp/msg.txt`)

---

### Task 2: Домен — `InstitutionRegistry` (повний ростер)

**Files:**
- Create: `src/main/java/me/vangoo/domain/organizations/InstitutionRegistry.java`
- Test: `src/test/java/me/vangoo/domain/organizations/InstitutionRegistryTest.java`

**Interfaces:**
- Consumes: `Institution`, `InstitutionType`, `PathwayAccess` (Task 1).
- Produces: `InstitutionRegistry` клас — `all()`, `churches()`, `byId(String id)`, `churchesAccepting(String pathwayNameOrNull)`. Id-конвеншн: `church-*` / `order-*` (kebab).

- [ ] **Step 1: Написати падаючий тест**

```java
package me.vangoo.domain.organizations;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class InstitutionRegistryTest {

    private final InstitutionRegistry registry = new InstitutionRegistry();

    @Test
    void hasTenChurchesAndAtLeastTwentyFiveOrders() {
        assertEquals(10, registry.churches().size());
        assertTrue(registry.all().size() - registry.churches().size() >= 25);
    }

    @Test
    void idsAreUnique() {
        Set<String> ids = registry.all().stream()
                .map(Institution::id).collect(Collectors.toSet());
        assertEquals(registry.all().size(), ids.size());
    }

    @Test
    void everyImplementedPathwayIsCoveredByAChurchOrOrder() {
        // Наявні шляхи плагіна (ключі PathwayManager). Error — свідомо лише таємна організація.
        for (String pathway : List.of("Fool", "Door", "Justiciar", "Visionary", "WhiteTower")) {
            assertFalse(registry.churchesAccepting(pathway).isEmpty(),
                    "no church accepts " + pathway);
        }
        assertTrue(registry.all().stream()
                .anyMatch(i -> i.accessFor("Error").isPresent()), "Error not covered at all");
    }

    @Test
    void pinnedCanonAccessesAreCorrect() {
        Institution evernight = registry.byId("church-evernight").orElseThrow();
        assertEquals(3, evernight.accessFor("Fool").orElseThrow().minSequence());

        Institution storms = registry.byId("church-lord-of-storms").orElseThrow();
        assertEquals(7, storms.accessFor("Visionary").orElseThrow().minSequence());

        Institution fool = registry.byId("church-fool").orElseThrow();
        assertTrue(fool.accessFor("Door").orElseThrow().isFull());
        assertEquals("Сім'я Авраам", fool.accessFor("Door").orElseThrow().branch());
        assertEquals(2, fool.accessFor("Justiciar").orElseThrow().minSequence());

        Institution wisdom = registry.byId("church-knowledge-wisdom").orElseThrow();
        assertTrue(wisdom.accessFor("WhiteTower").orElseThrow().isFull());
    }

    @Test
    void pathlessPlayerIsAcceptedByEveryChurch() {
        assertEquals(10, registry.churchesAccepting(null).size());
    }
}
```

- [ ] **Step 2: Запустити — впаде (класу нема)**

```powershell
& $mvn -o test "-Dtest=InstitutionRegistryTest"
```
Очікування: COMPILATION ERROR.

- [ ] **Step 3: Реалізувати реєстр**

`InstitutionRegistry.java` (повний ростер зі спеку; правило пінінгу: «повний або неповний» → FULL, «щонайменше до N» → PARTIAL(N), PARTIAL без N у каноні → PARTIAL(4)):

```java
package me.vangoo.domain.organizations;

import java.util.List;
import java.util.Optional;

import static me.vangoo.domain.organizations.InstitutionType.CHURCH;
import static me.vangoo.domain.organizations.InstitutionType.SECRET_ORDER;
import static me.vangoo.domain.organizations.PathwayAccess.full;
import static me.vangoo.domain.organizations.PathwayAccess.partial;

/** Кодовий реєстр усіх канонічних інституцій (10 церков + 25 таємних організацій). */
public final class InstitutionRegistry {

    private final List<Institution> all = buildAll();

    public List<Institution> all() {
        return all;
    }

    public List<Institution> churches() {
        return all.stream().filter(i -> i.type() == CHURCH).toList();
    }

    public Optional<Institution> byId(String id) {
        return all.stream().filter(i -> i.id().equalsIgnoreCase(id)).findFirst();
    }

    public List<Institution> churchesAccepting(String pathwayNameOrNull) {
        return churches().stream().filter(c -> c.acceptsPathway(pathwayNameOrNull)).toList();
    }

    private static List<Institution> buildAll() {
        return List.of(
                // ── Церкви (10) ──────────────────────────────────────────────
                new Institution("church-evernight", CHURCH, "Церква Богині Вічної Ночі",
                        "Богиня Вічної Ночі береже сон світу й таємниці пітьми.",
                        List.of(full("Darkness"), full("Death"), full("TwilightGiant"),
                                partial("Fool", 3), partial("Hermit", 9))),
                new Institution("church-god-of-combat", CHURCH, "Церква Бога Битви",
                        "Бог Битви шанує силу, звитягу і чесний двобій.",
                        List.of(full("TwilightGiant"), partial("Darkness", 4), partial("Death", 4))),
                new Institution("church-earth-mother", CHURCH, "Церква Матері-Землі",
                        "Мати-Земля дарує врожай, родючість і тихе зростання.",
                        List.of(full("Mother"), full("Moon"))),
                new Institution("church-lord-of-storms", CHURCH, "Церква Володаря Штормів",
                        "Володар Штормів панує над морем, громом і гнівом небес.",
                        List.of(full("Tyrant"), partial("Visionary", 7))),
                new Institution("church-knowledge-wisdom", CHURCH, "Церква Бога Знань та Мудрості",
                        "Бог Знань та Мудрості освячує книги, школи й розум.",
                        List.of(full("WhiteTower"))),
                new Institution("church-eternal-sun", CHURCH, "Церква Вічного Палаючого Сонця",
                        "Вічне Палаюче Сонце спалює нечисть і дарує світло.",
                        List.of(full("Sun"))),
                new Institution("church-steam-machinery", CHURCH, "Церква Бога Пари та Машин",
                        "Бог Пари та Машин благословляє фабрики, винаходи й прогрес.",
                        List.of(full("Paragon"), partial("Hermit", 4))),
                new Institution("church-fool", CHURCH, "Церква Блазня",
                        "Той, хто сидить над сірим туманом; править через підвладні групи.",
                        List.of(full("Door", "Сім'я Авраам"),
                                partial("Chained", 1, "Фракція Стриманості"),
                                partial("TwilightGiant", 2, "Срібне Місто"),
                                partial("Death", 2, "Срібне Місто"),
                                partial("HangedMan", 2, "Срібне Місто"),
                                partial("Sun", 2, "Срібне Місто"),
                                partial("Darkness", 2, "Місячне Місто"),
                                partial("Justiciar", 2, "Місячне Місто"),
                                full("RedPriest", "Місячне Місто"))),
                new Institution("church-eternal-darkness", CHURCH, "Церква Вічної Темряви",
                        "Вічна Темрява — давній лик ночі, що не знає світанку.",
                        List.of(full("Darkness"), full("TwilightGiant"), full("Death"))),
                new Institution("church-ruler-of-calamity", CHURCH, "Церква Володаря Катаклізмів",
                        "Володар Катаклізмів несе бурю, пошесть і кару.",
                        List.of(full("RedPriest"), full("Demoness"))),

                // ── Таємні організації (25) — у 6b лише дані ────────────────
                new Institution("order-great-white-brotherhood", SECRET_ORDER,
                        "Велике Біле Братство", "Братство обраних, що шукає вознесіння.", List.of()),
                new Institution("order-rose-redemption", SECRET_ORDER,
                        "Спокута Рози", "Уламок Школи Думки Рози, що шукає прощення.", List.of()),
                new Institution("order-twilight-hermit", SECRET_ORDER,
                        "Орден Сутінкового Відшельника", "Служить Відшельникові Сутінків.", List.of()),
                new Institution("order-moses-ascetic", SECRET_ORDER,
                        "Аскетичний Орден Мойсея", "Аскети таємниць і самозречення.",
                        List.of(full("Hermit"))),
                new Institution("order-demoness-sect", SECRET_ORDER,
                        "Секта Відьом", "Служниці Відьми у вічній змові.",
                        List.of(full("Demoness"))),
                new Institution("order-mirror-people", SECRET_ORDER,
                        "Дзеркальні Люди", "Живуть у відображеннях і крадуть лики.", List.of()),
                new Institution("order-secret-order", SECRET_ORDER,
                        "Таємний Орден", "Стережуть спадок Блазня.",
                        List.of(full("Fool"))),
                new Institution("order-shadow-of-order", SECRET_ORDER,
                        "Тінь Порядку", "Вірять у залізний лад Чорного Імператора.",
                        List.of(full("BlackEmperor"))),
                new Institution("order-blood-sanctify", SECRET_ORDER,
                        "Секта Освячення Крові", "Кров — двері в Безодню.",
                        List.of(full("Abyss"))),
                new Institution("order-theosophy", SECRET_ORDER,
                        "Теософський Орден", "Шукачі прихованих імен богів.",
                        List.of(partial("Demoness", 8), partial("Door", 8))),
                new Institution("order-numinous-episcopate", SECRET_ORDER,
                        "Нумінозний Епіскопат", "Єпископат мертвих і тиші.",
                        List.of(full("Death"))),
                new Institution("order-life-school", SECRET_ORDER,
                        "Школа Думки Життя", "Вчення про колесо життя і місячні припливи.",
                        List.of(full("WheelOfFortune"), full("Moon"))),
                new Institution("order-rose-school", SECRET_ORDER,
                        "Школа Думки Рози", "Давнє вчення кайданів і місяця.",
                        List.of(full("Chained"), full("Moon"))),
                new Institution("order-aurora", SECRET_ORDER,
                        "Орден Аврори", "Чекають світанку Повішеного.",
                        List.of(full("HangedMan"), partial("Door", 4), full("WheelOfFortune"))),
                new Institution("order-iron-blood-cross", SECRET_ORDER,
                        "Орден Залізного та Кривавого Хреста", "Хрест із заліза, віра з крові.",
                        List.of(full("RedPriest"))),
                new Institution("order-psychology-alchemists", SECRET_ORDER,
                        "Психологічні Алхіміки", "Алхімія розуму й сновидінь.",
                        List.of(full("Visionary"))),
                new Institution("order-element-dawn", SECRET_ORDER,
                        "Елемент Світанку", "Первісні стихії проти нового світу.",
                        List.of(full("Hermit"))),
                new Institution("order-hermits-of-fate", SECRET_ORDER,
                        "Відшельники Долі", "Злодії доль і читачі помилок світу.",
                        List.of(full("Error"))),
                new Institution("order-naturism-sect", SECRET_ORDER,
                        "Секта Натуризму", "Повернення до дикої природи кайданів.",
                        List.of(full("Chained"))),
                new Institution("order-baboons-society", SECRET_ORDER,
                        "Дослідницьке Товариство Кучерявих Бабуїнів",
                        "Ексцентричні дослідники потойбічного.", List.of()),
                new Institution("order-april-fools", SECRET_ORDER,
                        "Першоквітневі Дурні", "Жарт, що став вірою.", List.of()),
                new Institution("order-nightstalkers", SECRET_ORDER,
                        "Нічні Сталкери", "Полюють у місячному сяйві.",
                        List.of(full("Mother"), full("Moon"))),
                new Institution("order-bliss-society", SECRET_ORDER,
                        "Товариство Блаженства", "Насолода як шлях у Безодню.",
                        List.of(full("Abyss"), full("Chained"))),
                new Institution("order-god-descent-school", SECRET_ORDER,
                        "Школа Сходження Бога", "Закликають бога зійти в тіло.",
                        List.of(full("Mother"), full("Moon"))),
                new Institution("order-truth-school", SECRET_ORDER,
                        "Школа Істини", "Істина понад закон і хаос.",
                        List.of(full("BlackEmperor"), full("Justiciar"))));
    }
}
```

- [ ] **Step 4: Запустити тести — мають пройти**

```powershell
& $mvn -o test "-Dtest=InstitutionRegistryTest"
```
Очікування: PASS.

- [ ] **Step 5: Коміт**

```bash
printf 'feat(organizations): add canonical institution registry\n\nTen churches and twenty five secret orders with pinned pathway\naccesses; ambiguous canon entries pinned to PARTIAL(4).\n' > /tmp/msg.txt
git add src/main/java/me/vangoo/domain/organizations/InstitutionRegistry.java src/test/java/me/vangoo/domain/organizations/InstitutionRegistryTest.java
git commit -F /tmp/msg.txt
```

---

### Task 3: Домен — `ChurchRank` і `Membership`

**Files:**
- Create: `src/main/java/me/vangoo/domain/organizations/ChurchRank.java`
- Create: `src/main/java/me/vangoo/domain/organizations/Membership.java`
- Test: `src/test/java/me/vangoo/domain/organizations/ChurchRankTest.java`
- Test: `src/test/java/me/vangoo/domain/organizations/MembershipTest.java`

**Interfaces:**
- Consumes: `PathwayAccess` (Task 1); `ChurchTask`, `PotionOrder` (Task 4/5 — `Membership` тримає їх полями, на цьому таску поля ще відсутні й додаються в Task 4/5; тут — лише вклад/баланс).
- Produces: `enum ChurchRank { VIRIANYN, SLUZHKA, DYAKON, YEPYSKOP, KARDYNAL }` — `displayName()`, `minOrderSequence()` (8/6/4/2/0), `static ChurchRank of(int lifetimeContribution, int[] thresholds)` (thresholds — 5 значень, мінімальний вклад кожного рангу, `thresholds[0]==0`), `int lowestOrderableSequence(PathwayAccess access)` = `max(minOrderSequence, access.minSequence())`.
- Produces: `Membership` (мутабельний доменний клас) — `Membership(UUID playerId, String institutionId)`, `playerId()`, `institutionId()`, `lifetimeContribution()`, `balance()`, `addContribution(int points)`, `boolean spend(int points)`, `rank(int[] thresholds)`.

- [ ] **Step 1: Написати падаючі тести**

```java
package me.vangoo.domain.organizations;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChurchRankTest {

    private static final int[] THRESHOLDS = {0, 200, 600, 1500, 3500};

    @Test
    void ranksResolveByLifetimeContribution() {
        assertEquals(ChurchRank.VIRIANYN, ChurchRank.of(0, THRESHOLDS));
        assertEquals(ChurchRank.VIRIANYN, ChurchRank.of(199, THRESHOLDS));
        assertEquals(ChurchRank.SLUZHKA, ChurchRank.of(200, THRESHOLDS));
        assertEquals(ChurchRank.DYAKON, ChurchRank.of(600, THRESHOLDS));
        assertEquals(ChurchRank.YEPYSKOP, ChurchRank.of(1500, THRESHOLDS));
        assertEquals(ChurchRank.KARDYNAL, ChurchRank.of(9999, THRESHOLDS));
    }

    @Test
    void rankGatesOrderableSequence() {
        assertEquals(8, ChurchRank.VIRIANYN.minOrderSequence());
        assertEquals(0, ChurchRank.KARDYNAL.minOrderSequence());
        // Стеля = max(ранг, PARTIAL-ліміт): Кардинал у церкві з доступом «до 3» — лише до 3.
        assertEquals(3, ChurchRank.KARDYNAL.lowestOrderableSequence(
                PathwayAccess.partial("Fool", 3)));
        // Вірянин із повним доступом — усе одно лише до 8.
        assertEquals(8, ChurchRank.VIRIANYN.lowestOrderableSequence(
                PathwayAccess.full("Door")));
    }
}
```

```java
package me.vangoo.domain.organizations;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MembershipTest {

    private static final int[] THRESHOLDS = {0, 200, 600, 1500, 3500};

    @Test
    void contributionGrowsBothCounters() {
        Membership m = new Membership(UUID.randomUUID(), "church-evernight");
        m.addContribution(250);
        assertEquals(250, m.lifetimeContribution());
        assertEquals(250, m.balance());
        assertEquals(ChurchRank.SLUZHKA, m.rank(THRESHOLDS));
    }

    @Test
    void spendingLowersBalanceButNotRank() {
        Membership m = new Membership(UUID.randomUUID(), "church-evernight");
        m.addContribution(700);
        assertTrue(m.spend(650));
        assertEquals(50, m.balance());
        assertEquals(700, m.lifetimeContribution());
        assertEquals(ChurchRank.DYAKON, m.rank(THRESHOLDS)); // ранг за lifetime, не за балансом
        assertFalse(m.spend(51)); // бракує — відмова, баланс не змінюється
        assertEquals(50, m.balance());
    }

    @Test
    void rejectsNonPositiveAmounts() {
        Membership m = new Membership(UUID.randomUUID(), "church-evernight");
        assertThrows(IllegalArgumentException.class, () -> m.addContribution(0));
        assertThrows(IllegalArgumentException.class, () -> m.spend(-1));
    }
}
```

- [ ] **Step 2: Запустити — впадуть (COMPILATION ERROR)**

```powershell
& $mvn -o test "-Dtest=ChurchRankTest,MembershipTest"
```

- [ ] **Step 3: Реалізувати**

`ChurchRank.java`:
```java
package me.vangoo.domain.organizations;

/** Ранги церкви за сумарним вкладом. Стеля замовлень: чим вищий ранг — тим сильніші зілля. */
public enum ChurchRank {
    VIRIANYN("Вірянин", 8),
    SLUZHKA("Служка", 6),
    DYAKON("Диякон", 4),
    YEPYSKOP("Єпископ", 2),
    KARDYNAL("Кардинал", 0);

    private final String displayName;
    private final int minOrderSequence;

    ChurchRank(String displayName, int minOrderSequence) {
        this.displayName = displayName;
        this.minOrderSequence = minOrderSequence;
    }

    public String displayName() {
        return displayName;
    }

    /** Найнижча (найсильніша) послідовність зілля, доступна рангу. */
    public int minOrderSequence() {
        return minOrderSequence;
    }

    /** @param thresholds мінімальний lifetime-вклад кожного рангу (5 значень, [0] = 0). */
    public static ChurchRank of(int lifetimeContribution, int[] thresholds) {
        if (thresholds.length != values().length) {
            throw new IllegalArgumentException("expected " + values().length + " thresholds");
        }
        ChurchRank result = VIRIANYN;
        for (ChurchRank rank : values()) {
            if (lifetimeContribution >= thresholds[rank.ordinal()]) {
                result = rank;
            }
        }
        return result;
    }

    /** Фактична стеля замовлення: обмежують і ранг, і PARTIAL-ліміт доступу шляху. */
    public int lowestOrderableSequence(PathwayAccess access) {
        return Math.max(minOrderSequence, access.minSequence());
    }
}
```

`Membership.java`:
```java
package me.vangoo.domain.organizations;

import java.util.UUID;

/**
 * Членство гравця в церкві: lifetime-вклад (визначає ранг, не зменшується)
 * і баланс очок (валюта замовлень зілля).
 */
public class Membership {

    private final UUID playerId;
    private final String institutionId;
    private int lifetimeContribution;
    private int balance;

    public Membership(UUID playerId, String institutionId) {
        this.playerId = playerId;
        this.institutionId = institutionId;
    }

    public UUID playerId() {
        return playerId;
    }

    public String institutionId() {
        return institutionId;
    }

    public int lifetimeContribution() {
        return lifetimeContribution;
    }

    public int balance() {
        return balance;
    }

    public void addContribution(int points) {
        requirePositive(points);
        lifetimeContribution += points;
        balance += points;
    }

    /** @return false, якщо балансу бракує (нічого не змінюється). */
    public boolean spend(int points) {
        requirePositive(points);
        if (balance < points) {
            return false;
        }
        balance -= points;
        return true;
    }

    public ChurchRank rank(int[] thresholds) {
        return ChurchRank.of(lifetimeContribution, thresholds);
    }

    private static void requirePositive(int points) {
        if (points <= 0) {
            throw new IllegalArgumentException("points must be positive: " + points);
        }
    }
}
```

- [ ] **Step 4: Запустити тести — PASS**

```powershell
& $mvn -o test "-Dtest=ChurchRankTest,MembershipTest"
```

- [ ] **Step 5: Коміт**

```bash
printf 'feat(organizations): add church ranks and membership contribution\n' > /tmp/msg.txt
git add src/main/java/me/vangoo/domain/organizations/ChurchRank.java src/main/java/me/vangoo/domain/organizations/Membership.java src/test/java/me/vangoo/domain/organizations/ChurchRankTest.java src/test/java/me/vangoo/domain/organizations/MembershipTest.java
git commit -F /tmp/msg.txt
```

---

### Task 4: Домен — `ChurchTask` і `ChurchTaskGenerator`

**Files:**
- Create: `src/main/java/me/vangoo/domain/organizations/ChurchTask.java`
- Create: `src/main/java/me/vangoo/domain/organizations/ChurchTaskGenerator.java`
- Modify: `src/main/java/me/vangoo/domain/organizations/Membership.java` (поля завдань)
- Test: `src/test/java/me/vangoo/domain/organizations/ChurchTaskGeneratorTest.java`

**Interfaces:**
- Consumes: `Institution`, `PathwayAccess`, `Membership`.
- Produces: `ChurchTask(ChurchTask.Type type, String targetKey, String targetName, int required, int progress, int rewardPoints)` record — `enum Type { HUNT, DELIVER }`, `withProgress(int)`, `isComplete()`.
- Produces: `ChurchTaskGenerator` — records `CreatureCandidate(String creatureId, String pathway, int sequence)`, `IngredientCandidate(String itemKey, String displayName, int sequence)`; методи `List<ChurchTask> generate(int count, Institution church, Map<String,String> pathwayToGroup, List<CreatureCandidate> creatures, List<IngredientCandidate> ingredients, Random random)` і `Optional<ChurchTask> generateInitiation(List<CreatureCandidate> creatures, Random random)`.
- Produces (на `Membership`): `List<ChurchTask> tasks()`, `setTasks(List<ChurchTask>)`, `long lastTaskRefreshEpochMillis()`, `setLastTaskRefreshEpochMillis(long)`, `ChurchTask initiationTask()` (nullable), `String initiationPathway()` (nullable), `setInitiation(ChurchTask, String)`, `clearInitiation()`.

**Формули (фіксовані в коді генератора):**
- HUNT: ціль — істота, чия група (`pathwayToGroup`) НЕ збігається з жодною групою шляхів церкви (шляхи без групи в мапі — не кандидати); `required = 1 + sequence / 3` (seq 9 → 4, seq 0 → 1); `rewardPoints = (10 − sequence) × 12 × required`.
- DELIVER: `required = 2 + sequence / 2` (seq 9 → 6, seq 0 → 2); `rewardPoints = (10 − sequence) × 4 × required`.
- Ініціація: найслабша істота з кандидатів (максимальний `sequence`), `required = 2`, `rewardPoints = 5`.
- Без дублів `targetKey` в одному пулі; мікс типів: генератор чергує HUNT/DELIVER, поки є кандидати.

- [ ] **Step 1: Написати падаючий тест**

```java
package me.vangoo.domain.organizations;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ChurchTaskGeneratorTest {

    private final ChurchTaskGenerator generator = new ChurchTaskGenerator();

    private final Institution church = new Institution("church-knowledge-wisdom",
            InstitutionType.CHURCH, "Церква Бога Знань та Мудрості", "лор",
            List.of(PathwayAccess.full("WhiteTower")));

    // WhiteTower → GodAlmighty; Error → LordOfMysteries (ворожа група)
    private final Map<String, String> groups = Map.of(
            "WhiteTower", "GodAlmighty", "Visionary", "GodAlmighty",
            "Error", "LordOfMysteries", "Door", "LordOfMysteries");

    private final List<ChurchTaskGenerator.CreatureCandidate> creatures = List.of(
            new ChurchTaskGenerator.CreatureCandidate("error_sphinx_9", "Error", 9),
            new ChurchTaskGenerator.CreatureCandidate("error_wyrm_6", "Error", 6),
            new ChurchTaskGenerator.CreatureCandidate("visionary_owl_9", "Visionary", 9));

    private final List<ChurchTaskGenerator.IngredientCandidate> ingredients = List.of(
            new ChurchTaskGenerator.IngredientCandidate("custom:night_vanilla", "Нічна ваніль", 9),
            new ChurchTaskGenerator.IngredientCandidate("custom:lava_lily", "Лавова лілія", 6));

    @Test
    void huntsOnlyHostileGroups() {
        List<ChurchTask> tasks = generator.generate(4, church, groups, creatures,
                List.of(), new Random(42));
        assertFalse(tasks.isEmpty());
        for (ChurchTask t : tasks) {
            assertEquals(ChurchTask.Type.HUNT, t.type());
            assertNotEquals("visionary_owl_9", t.targetKey()); // своя група — не ціль
        }
    }

    @Test
    void noDuplicateTargetsAndCountRespected() {
        List<ChurchTask> tasks = generator.generate(2, church, groups, creatures,
                ingredients, new Random(1));
        assertEquals(2, tasks.size());
        Set<String> keys = tasks.stream().map(ChurchTask::targetKey).collect(Collectors.toSet());
        assertEquals(2, keys.size());
    }

    @Test
    void strongerTargetPaysMore() {
        // seq 6 (сильніша) проти seq 9: нагорода за одиницю більша
        ChurchTask strong = ChurchTask.hunt("error_wyrm_6", "?", 6);
        ChurchTask weak = ChurchTask.hunt("error_sphinx_9", "?", 9);
        assertTrue(strong.rewardPoints() / strong.required()
                > weak.rewardPoints() / weak.required());
    }

    @Test
    void progressAndCompletion() {
        ChurchTask t = ChurchTask.deliver("custom:night_vanilla", "Нічна ваніль", 9);
        assertFalse(t.isComplete());
        ChurchTask done = t.withProgress(t.required());
        assertTrue(done.isComplete());
    }

    @Test
    void initiationPicksWeakestCreatureWithSmallDemand() {
        ChurchTask init = generator.generateInitiation(creatures, new Random(7)).orElseThrow();
        assertEquals(ChurchTask.Type.HUNT, init.type());
        assertEquals(2, init.required());
        assertTrue(init.targetKey().endsWith("_9")); // найслабша (max sequence)
        assertTrue(generator.generateInitiation(List.of(), new Random(7)).isEmpty());
    }
}
```

- [ ] **Step 2: Запустити — COMPILATION ERROR**

```powershell
& $mvn -o test "-Dtest=ChurchTaskGeneratorTest"
```

- [ ] **Step 3: Реалізувати**

`ChurchTask.java`:
```java
package me.vangoo.domain.organizations;

/** Завдання церкви: полювання (targetKey = id істоти) або доставка (targetKey = itemKey). */
public record ChurchTask(Type type, String targetKey, String targetName,
                         int required, int progress, int rewardPoints) {

    public enum Type { HUNT, DELIVER }

    public static ChurchTask hunt(String creatureId, String displayName, int sequence) {
        int required = 1 + sequence / 3;
        return new ChurchTask(Type.HUNT, creatureId, displayName, required, 0,
                (10 - sequence) * 12 * required);
    }

    public static ChurchTask deliver(String itemKey, String displayName, int sequence) {
        int required = 2 + sequence / 2;
        return new ChurchTask(Type.DELIVER, itemKey, displayName, required, 0,
                (10 - sequence) * 4 * required);
    }

    /** Полегшене завдання ініціації: слабка ціль, символічна нагорода. */
    public static ChurchTask initiationHunt(String creatureId, String displayName) {
        return new ChurchTask(Type.HUNT, creatureId, displayName, 2, 0, 5);
    }

    public ChurchTask withProgress(int newProgress) {
        return new ChurchTask(type, targetKey, targetName, required,
                Math.min(newProgress, required), rewardPoints);
    }

    public boolean isComplete() {
        return progress >= required;
    }
}
```

`ChurchTaskGenerator.java`:
```java
package me.vangoo.domain.organizations;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/** Чистий генератор пулу завдань церкви: ротація HUNT/DELIVER без дублів цілей. */
public final class ChurchTaskGenerator {

    public record CreatureCandidate(String creatureId, String pathway, int sequence) {}

    public record IngredientCandidate(String itemKey, String displayName, int sequence) {}

    public List<ChurchTask> generate(int count, Institution church,
                                     Map<String, String> pathwayToGroup,
                                     List<CreatureCandidate> creatures,
                                     List<IngredientCandidate> ingredients,
                                     Random random) {
        Set<String> churchGroups = new HashSet<>();
        for (PathwayAccess access : church.accesses()) {
            String group = pathwayToGroup.get(access.pathwayName());
            if (group != null) {
                churchGroups.add(group);
            }
        }
        List<CreatureCandidate> hostile = new ArrayList<>(creatures.stream()
                .filter(c -> {
                    String group = pathwayToGroup.get(c.pathway());
                    return group != null && !churchGroups.contains(group);
                }).toList());
        List<IngredientCandidate> pool = new ArrayList<>(ingredients);

        List<ChurchTask> tasks = new ArrayList<>();
        Set<String> usedKeys = new HashSet<>();
        boolean huntTurn = true;
        while (tasks.size() < count && (!hostile.isEmpty() || !pool.isEmpty())) {
            if ((huntTurn && !hostile.isEmpty()) || pool.isEmpty()) {
                CreatureCandidate c = hostile.remove(random.nextInt(hostile.size()));
                if (usedKeys.add(c.creatureId())) {
                    tasks.add(ChurchTask.hunt(c.creatureId(), c.creatureId(), c.sequence()));
                }
            } else {
                IngredientCandidate i = pool.remove(random.nextInt(pool.size()));
                if (usedKeys.add(i.itemKey())) {
                    tasks.add(ChurchTask.deliver(i.itemKey(), i.displayName(), i.sequence()));
                }
            }
            huntTurn = !huntTurn;
        }
        return tasks;
    }

    /** Ініціація: найслабша (max sequence) істота з кандидатів; порожньо, якщо кандидатів нема. */
    public Optional<ChurchTask> generateInitiation(List<CreatureCandidate> creatures,
                                                   Random random) {
        return creatures.stream()
                .max(Comparator.comparingInt(CreatureCandidate::sequence))
                .map(c -> ChurchTask.initiationHunt(c.creatureId(), c.creatureId()));
    }
}
```

У `Membership.java` додати поля й методи (після `balance`):
```java
    private java.util.List<ChurchTask> tasks = new java.util.ArrayList<>();
    private long lastTaskRefreshEpochMillis;
    private ChurchTask initiationTask;      // null = не активна
    private String initiationPathway;       // шлях зілля, обраний при ініціації

    public java.util.List<ChurchTask> tasks() { return tasks; }

    public void setTasks(java.util.List<ChurchTask> tasks) {
        this.tasks = new java.util.ArrayList<>(tasks);
    }

    public long lastTaskRefreshEpochMillis() { return lastTaskRefreshEpochMillis; }

    public void setLastTaskRefreshEpochMillis(long millis) {
        this.lastTaskRefreshEpochMillis = millis;
    }

    public ChurchTask initiationTask() { return initiationTask; }

    public String initiationPathway() { return initiationPathway; }

    public void setInitiation(ChurchTask task, String pathwayName) {
        this.initiationTask = task;
        this.initiationPathway = pathwayName;
    }

    public void clearInitiation() {
        this.initiationTask = null;
        this.initiationPathway = null;
    }
```

- [ ] **Step 4: Запустити тести — PASS (плюс регресія Task 3)**

```powershell
& $mvn -o test "-Dtest=ChurchTaskGeneratorTest,MembershipTest"
```

- [ ] **Step 5: Коміт**

```bash
printf 'feat(organizations): add church task model and pure task generator\n' > /tmp/msg.txt
git add src/main/java/me/vangoo/domain/organizations src/test/java/me/vangoo/domain/organizations
git commit -F /tmp/msg.txt
```

---

### Task 5: Домен — `PotionOrder` і `ChurchVault`

**Files:**
- Create: `src/main/java/me/vangoo/domain/organizations/PotionOrder.java`
- Create: `src/main/java/me/vangoo/domain/organizations/ChurchVault.java`
- Modify: `src/main/java/me/vangoo/domain/organizations/Membership.java` (поле замовлення)
- Test: `src/test/java/me/vangoo/domain/organizations/ChurchVaultTest.java`

**Interfaces:**
- Consumes: `BrewRecipe` (наявний `me.vangoo.domain.brewing`: `mainCounts()`, `auxCounts()`, `characteristicKey()`).
- Produces: `PotionOrder(String pathwayName, int sequence, long readyAtEpochMillis, int pointsPaid)` record — `isReady(long nowMillis)`.
- Produces: `ChurchVault` (мутабельний) — `add(String itemKey, int amount)`, `amountOf(String itemKey)`, `snapshot()` → `Map<String,Integer>` (копія), `restore(Map<String,Integer>)`, `hasRecipeKnowledge(String pathwayName, int sequence)` (наявність `recipe:<p>:<seq>`, НЕ споживається), `Map<String,Integer> missingFor(BrewRecipe recipe)` (порожня мапа = можна варити; повертає брак «ближчого» варіанта), `boolean consumeFor(BrewRecipe recipe)` (класика пріоритетна, інакше Характеристика-заміна; false = бракує, нічого не списано).
- Produces (на `Membership`): `PotionOrder activeOrder()` (nullable), `setActiveOrder(PotionOrder)`, `clearActiveOrder()`.

- [ ] **Step 1: Написати падаючий тест**

```java
package me.vangoo.domain.organizations;

import me.vangoo.domain.brewing.BrewRecipe;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChurchVaultTest {

    private final BrewRecipe recipe = new BrewRecipe("Door", 9,
            Map.of("custom:night_vanilla", 2, "custom:lava_lily", 1),   // основні
            Map.of("custom:pure_water", 1));                             // допоміжні

    @Test
    void recipeKnowledgeIsGateNotFuel() {
        ChurchVault vault = new ChurchVault();
        assertFalse(vault.hasRecipeKnowledge("Door", 9));
        vault.add("recipe:Door:9", 1);
        assertTrue(vault.hasRecipeKnowledge("Door", 9));
        vault.add("custom:night_vanilla", 2);
        vault.add("custom:lava_lily", 1);
        vault.add("custom:pure_water", 1);
        assertTrue(vault.consumeFor(recipe));
        assertTrue(vault.hasRecipeKnowledge("Door", 9)); // книга лишається
    }

    @Test
    void consumesClassicVariantFirst() {
        ChurchVault vault = new ChurchVault();
        vault.add("custom:night_vanilla", 2);
        vault.add("custom:lava_lily", 1);
        vault.add("custom:pure_water", 1);
        vault.add("characteristic:Door:9", 1);
        assertTrue(vault.consumeFor(recipe));
        // класика списана, Характеристика вціліла
        assertEquals(0, vault.amountOf("custom:night_vanilla"));
        assertEquals(1, vault.amountOf("characteristic:Door:9"));
    }

    @Test
    void fallsBackToCharacteristicSubstitution() {
        ChurchVault vault = new ChurchVault();
        vault.add("custom:pure_water", 1);            // лише допоміжні
        vault.add("characteristic:Door:9", 1);        // + Характеристика-заміна
        assertTrue(vault.missingFor(recipe).isEmpty());
        assertTrue(vault.consumeFor(recipe));
        assertEquals(0, vault.amountOf("characteristic:Door:9"));
        assertEquals(0, vault.amountOf("custom:pure_water"));
    }

    @Test
    void missingForReportsShortfallAtomically() {
        ChurchVault vault = new ChurchVault();
        vault.add("custom:night_vanilla", 1); // треба 2
        Map<String, Integer> missing = vault.missingFor(recipe);
        assertEquals(1, missing.get("custom:night_vanilla"));
        assertEquals(1, missing.get("custom:lava_lily"));
        assertEquals(1, missing.get("custom:pure_water"));
        assertFalse(vault.consumeFor(recipe));
        assertEquals(1, vault.amountOf("custom:night_vanilla")); // нічого не списано
    }

    @Test
    void conservationInvariantHolds() {
        // «зайшло» = «лежить» + «списано у варіння»
        ChurchVault vault = new ChurchVault();
        vault.add("custom:pure_water", 5);
        vault.add("characteristic:Door:9", 2);
        int inflow = 5 + 2;
        assertTrue(vault.consumeFor(recipe)); // списує 1 воду + 1 Характеристику
        int stored = vault.snapshot().values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(inflow - 2, stored);
    }

    @Test
    void orderReadiness() {
        PotionOrder order = new PotionOrder("Door", 9, 1_000L, 60);
        assertFalse(order.isReady(999L));
        assertTrue(order.isReady(1_000L));
    }
}
```

- [ ] **Step 2: Запустити — COMPILATION ERROR**

```powershell
& $mvn -o test "-Dtest=ChurchVaultTest"
```

- [ ] **Step 3: Реалізувати**

`PotionOrder.java`:
```java
package me.vangoo.domain.organizations;

/** Активне замовлення зілля: що вариться, коли буде готове, скільки очок сплачено. */
public record PotionOrder(String pathwayName, int sequence,
                          long readyAtEpochMillis, int pointsPaid) {

    public boolean isReady(long nowMillis) {
        return nowMillis >= readyAtEpochMillis;
    }
}
```

`ChurchVault.java`:
```java
package me.vangoo.domain.organizations;

import me.vangoo.domain.brewing.BrewRecipe;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Сховище церкви: itemKey → кількість. Книги рецептів — знання-гейт (не списуються);
 * інгредієнти й Характеристики списуються у варіння замовлень.
 */
public class ChurchVault {

    private final Map<String, Integer> items = new HashMap<>();

    public void add(String itemKey, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
        items.merge(itemKey, amount, Integer::sum);
    }

    public int amountOf(String itemKey) {
        return items.getOrDefault(itemKey, 0);
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

    public boolean hasRecipeKnowledge(String pathwayName, int sequence) {
        return amountOf("recipe:" + pathwayName + ":" + sequence) > 0;
    }

    /** Порожня мапа = можна варити (класикою або Характеристикою). Інакше — брак класики. */
    public Map<String, Integer> missingFor(BrewRecipe recipe) {
        Map<String, Integer> classicMissing = shortfall(classicRequirement(recipe));
        if (classicMissing.isEmpty()) {
            return classicMissing;
        }
        if (shortfall(characteristicRequirement(recipe)).isEmpty()) {
            return Map.of();
        }
        return classicMissing;
    }

    /** Атомарне списання: класика пріоритетна; false — бракує обох варіантів. */
    public boolean consumeFor(BrewRecipe recipe) {
        Map<String, Integer> requirement = classicRequirement(recipe);
        if (!shortfall(requirement).isEmpty()) {
            requirement = characteristicRequirement(recipe);
            if (!shortfall(requirement).isEmpty()) {
                return false;
            }
        }
        requirement.forEach((key, amount) -> {
            int rest = items.get(key) - amount;
            if (rest == 0) {
                items.remove(key);
            } else {
                items.put(key, rest);
            }
        });
        return true;
    }

    private static Map<String, Integer> classicRequirement(BrewRecipe recipe) {
        Map<String, Integer> required = new HashMap<>(recipe.mainCounts());
        recipe.auxCounts().forEach((k, v) -> required.merge(k, v, Integer::sum));
        return required;
    }

    private static Map<String, Integer> characteristicRequirement(BrewRecipe recipe) {
        Map<String, Integer> required = new HashMap<>(recipe.auxCounts());
        required.merge(recipe.characteristicKey(), 1, Integer::sum);
        return required;
    }

    private Map<String, Integer> shortfall(Map<String, Integer> requirement) {
        Map<String, Integer> missing = new LinkedHashMap<>();
        requirement.forEach((key, amount) -> {
            int lack = amount - amountOf(key);
            if (lack > 0) {
                missing.put(key, lack);
            }
        });
        return missing;
    }
}
```

У `Membership.java` додати (поряд з ініціацією):
```java
    private PotionOrder activeOrder; // null = нема замовлення

    public PotionOrder activeOrder() { return activeOrder; }

    public void setActiveOrder(PotionOrder order) { this.activeOrder = order; }

    public void clearActiveOrder() { this.activeOrder = null; }
```

- [ ] **Step 4: Запустити тести — PASS (весь пакет)**

```powershell
& $mvn -o test "-Dtest=me.vangoo.domain.organizations.*"
```

- [ ] **Step 5: Коміт**

```bash
printf 'feat(organizations): add church vault and potion order domain\n\nVault consumes classic ingredients first, falls back to the\ncharacteristic substitution; recipe books gate knowledge unconsumed.\n' > /tmp/msg.txt
git add src/main/java/me/vangoo/domain/organizations src/test/java/me/vangoo/domain/organizations
git commit -F /tmp/msg.txt
```

---

### Task 6: Інфраструктура — `ChurchConfig` + секція `church.*` у config.yml

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/organizations/ChurchConfig.java`
- Modify: `src/main/resources/config.yml` (нова секція `church.*` в кінці файлу)

**Interfaces:**
- Consumes: патерн `MarketConfig.loadSeqMap` (`infrastructure.market.MarketConfig:60-78`) — скопіювати приватним методом (не робити публічним у чужому класі).
- Produces: record `ChurchConfig(int[] rankThresholds, int rejoinCooldownDays, int tasksRefreshHours, int tasksMaxActive, int orderBrewHours, Map<Integer,Integer> orderPointsBySeq, Map<Integer,Integer> donationIngredientPointsBySeq, Map<Integer,Integer> donationRecipePointsBySeq, Map<Integer,Integer> donationCharacteristicPointsBySeq, int pointsPerCoppet, int vaultSeedBrewsPerRecipe, int vaultSeedCharacteristicsPerSeq, int spawnVillageOffset)` — `static ChurchConfig load(Plugin plugin)`.

- [ ] **Step 1: Реалізувати `ChurchConfig`** (конфіг-адаптер без юніт-тесту — Bukkit `Plugin`; перевіряється компіляцією і in-server)

```java
package me.vangoo.infrastructure.organizations;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

/** Читає секцію church.* із config.yml; усі мапи мають фолбек-дефолти в коді
 * (Bukkit не перезаписує наявний config.yml при оновленні плагіна). */
public record ChurchConfig(int[] rankThresholds,
                           int rejoinCooldownDays,
                           int tasksRefreshHours,
                           int tasksMaxActive,
                           int orderBrewHours,
                           Map<Integer, Integer> orderPointsBySeq,
                           Map<Integer, Integer> donationIngredientPointsBySeq,
                           Map<Integer, Integer> donationRecipePointsBySeq,
                           Map<Integer, Integer> donationCharacteristicPointsBySeq,
                           int pointsPerCoppet,
                           int vaultSeedBrewsPerRecipe,
                           int vaultSeedCharacteristicsPerSeq,
                           int spawnVillageOffset) {

    private static final int[] DEFAULT_RANK_THRESHOLDS = {0, 200, 600, 1500, 3500};
    private static final Map<Integer, Integer> DEFAULT_ORDER_POINTS = Map.of(
            9, 60, 8, 90, 7, 140, 6, 200, 5, 280, 4, 380, 3, 500, 2, 650, 1, 850, 0, 1100);
    private static final Map<Integer, Integer> DEFAULT_DONATION_INGREDIENT = Map.of(
            9, 2, 8, 3, 7, 5, 6, 8, 5, 12, 4, 18, 3, 26, 2, 36, 1, 48, 0, 62);
    private static final Map<Integer, Integer> DEFAULT_DONATION_RECIPE = Map.of(
            9, 40, 8, 55, 7, 75, 6, 100, 5, 135, 4, 180, 3, 240, 2, 310, 1, 390, 0, 480);
    private static final Map<Integer, Integer> DEFAULT_DONATION_CHARACTERISTIC = Map.of(
            9, 100, 8, 160, 7, 260, 6, 400, 5, 600, 4, 850, 3, 1150, 2, 1550, 1, 2000, 0, 2600);

    public static ChurchConfig load(Plugin plugin) {
        var cfg = plugin.getConfig();
        int[] thresholds = DEFAULT_RANK_THRESHOLDS.clone();
        var list = cfg.getIntegerList("church.rank-thresholds");
        if (list.size() == thresholds.length && list.get(0) == 0) {
            for (int i = 0; i < thresholds.length; i++) {
                thresholds[i] = list.get(i);
            }
        } else if (!list.isEmpty()) {
            plugin.getLogger().warning("church.rank-thresholds must be 5 values starting with 0; using defaults");
        }
        return new ChurchConfig(
                thresholds,
                cfg.getInt("church.rejoin-cooldown-days", 3),
                cfg.getInt("church.tasks.refresh-hours", 24),
                cfg.getInt("church.tasks.max-active", 2),
                cfg.getInt("church.order.brew-hours", 12),
                loadSeqMap(plugin, "church.order.points-by-seq", DEFAULT_ORDER_POINTS),
                loadSeqMap(plugin, "church.donation.ingredient-points-by-seq", DEFAULT_DONATION_INGREDIENT),
                loadSeqMap(plugin, "church.donation.recipe-points-by-seq", DEFAULT_DONATION_RECIPE),
                loadSeqMap(plugin, "church.donation.characteristic-points-by-seq", DEFAULT_DONATION_CHARACTERISTIC),
                cfg.getInt("church.donation.points-per-coppet", 1),
                cfg.getInt("church.vault.seed.brews-per-recipe", 3),
                cfg.getInt("church.vault.seed.characteristics-per-seq", 1),
                cfg.getInt("church.spawn.village-offset", 24));
    }

    private static Map<Integer, Integer> loadSeqMap(Plugin plugin, String path,
                                                    Map<Integer, Integer> defaults) {
        Map<Integer, Integer> bySeq = new HashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    bySeq.put(Integer.parseInt(key), section.getInt(key));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning(path + ": bad sequence key '" + key + "', skipped");
                }
            }
        }
        if (bySeq.isEmpty()) {
            bySeq.putAll(defaults);
            plugin.getLogger().info(path + " missing; using built-in defaults");
        }
        return bySeq;
    }
}
```

- [ ] **Step 2: Додати секцію в `src/main/resources/config.yml`** (у кінець файлу; значення дублюють дефолти)

```yaml
# ── Церкви (Економіка 6b) ────────────────────────────────────────────
church:
  # Мінімальний сумарний вклад рангів: Вірянин, Служка, Диякон, Єпископ, Кардинал
  rank-thresholds: [0, 200, 600, 1500, 3500]
  rejoin-cooldown-days: 3
  tasks:
    refresh-hours: 24
    max-active: 2
  order:
    brew-hours: 12
    points-by-seq: {9: 60, 8: 90, 7: 140, 6: 200, 5: 280, 4: 380, 3: 500, 2: 650, 1: 850, 0: 1100}
  donation:
    ingredient-points-by-seq: {9: 2, 8: 3, 7: 5, 6: 8, 5: 12, 4: 18, 3: 26, 2: 36, 1: 48, 0: 62}
    recipe-points-by-seq: {9: 40, 8: 55, 7: 75, 6: 100, 5: 135, 4: 180, 3: 240, 2: 310, 1: 390, 0: 480}
    characteristic-points-by-seq: {9: 100, 8: 160, 7: 260, 6: 400, 5: 600, 4: 850, 3: 1150, 2: 1550, 1: 2000, 0: 2600}
    points-per-coppet: 1
  vault:
    seed:
      brews-per-recipe: 3
      characteristics-per-seq: 1
  spawn:
    village-offset: 24
```

- [ ] **Step 3: Перевірити компіляцію**

```powershell
& $mvn -o compile
```
Очікування: BUILD SUCCESS.

- [ ] **Step 4: Коміт**

```bash
printf 'feat(organizations): add church config section with code defaults\n' > /tmp/msg.txt
git add src/main/java/me/vangoo/infrastructure/organizations/ChurchConfig.java src/main/resources/config.yml
git commit -F /tmp/msg.txt
```

---

### Task 7: Інфраструктура — три JSON-репозиторії

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/organizations/JSONMembershipRepository.java`
- Create: `src/main/java/me/vangoo/infrastructure/organizations/ChurchSiteRepository.java`
- Create: `src/main/java/me/vangoo/infrastructure/organizations/ChurchStateRepository.java`
- Test: `src/test/java/me/vangoo/infrastructure/organizations/ChurchRepositoriesTest.java`

**Interfaces:**
- Consumes: патерн `GatheringSnapshotRepository` (Gson + records; corrupt file → `Optional.empty()`); конструктор приймає `String filePath`.
- Produces (`JSONMembershipRepository`, файл `memberships.json`):
  - records: `TaskRecord(String type, String targetKey, String targetName, int required, int progress, int rewardPoints)`, `OrderRecord(String pathwayName, int sequence, long readyAtEpochMillis, int pointsPaid)`, `MembershipRecord(String institutionId, int lifetimeContribution, int balance, long lastTaskRefreshEpochMillis, List<TaskRecord> tasks, TaskRecord initiationTask, String initiationPathway, OrderRecord activeOrder)`, `PlayerChurchData(MembershipRecord membership, long rejoinCooldownUntilEpochMillis, boolean initiationUsed)` (усі — вкладені public records), `Model(Map<String, PlayerChurchData> players)` (ключ — UUID-рядок);
  - методи: `Optional<Model> load()`, `save(Model model)`.
- Produces (`ChurchSiteRepository`, файл `church-sites.json`): records `Site(String institutionId, String world, double x, double y, double z, float yaw, float pitch)`, `Model(List<Site> sites, List<String> processedVillageKeys)`; методи `load()`/`save(Model)`.
- Produces (`ChurchStateRepository`, файл `churches-state.json`): record `Model(Map<String, Map<String, Integer>> vaults)` (institutionId → itemKey → amount); методи `load()`/`save(Model)`.

- [ ] **Step 1: Написати падаючий round-trip тест** (Gson без Bukkit — звичайний юніт)

```java
package me.vangoo.infrastructure.organizations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChurchRepositoriesTest {

    @TempDir
    Path dir;

    @Test
    void membershipRoundTripsAndKeepsNullables() {
        var repo = new JSONMembershipRepository(dir.resolve("memberships.json").toString());
        assertTrue(repo.load().isEmpty()); // нема файлу — порожньо

        var task = new JSONMembershipRepository.TaskRecord(
                "HUNT", "error_sphinx_9", "error_sphinx_9", 4, 1, 48);
        var member = new JSONMembershipRepository.MembershipRecord(
                "church-evernight", 250, 100, 123L, List.of(task), null, null,
                new JSONMembershipRepository.OrderRecord("Door", 9, 999L, 60));
        var model = new JSONMembershipRepository.Model(Map.of(
                "11111111-1111-1111-1111-111111111111",
                new JSONMembershipRepository.PlayerChurchData(member, 0L, true),
                "22222222-2222-2222-2222-222222222222",
                new JSONMembershipRepository.PlayerChurchData(null, 777L, false)));
        repo.save(model);

        var loaded = repo.load().orElseThrow();
        var p1 = loaded.players().get("11111111-1111-1111-1111-111111111111");
        assertEquals("church-evernight", p1.membership().institutionId());
        assertEquals(1, p1.membership().tasks().size());
        assertNull(p1.membership().initiationTask());
        assertEquals(9, p1.membership().activeOrder().sequence());
        assertTrue(p1.initiationUsed());
        assertNull(loaded.players().get("22222222-2222-2222-2222-222222222222").membership());
    }

    @Test
    void sitesAndVaultsRoundTrip() {
        var sites = new ChurchSiteRepository(dir.resolve("church-sites.json").toString());
        sites.save(new ChurchSiteRepository.Model(
                List.of(new ChurchSiteRepository.Site("church-fool", "world", 1, 65, 2, 0f, 0f)),
                List.of("world:100:200")));
        var loadedSites = sites.load().orElseThrow();
        assertEquals("church-fool", loadedSites.sites().get(0).institutionId());
        assertEquals(List.of("world:100:200"), loadedSites.processedVillageKeys());

        var state = new ChurchStateRepository(dir.resolve("churches-state.json").toString());
        state.save(new ChurchStateRepository.Model(
                Map.of("church-fool", Map.of("custom:night_vanilla", 6, "recipe:Door:9", 1))));
        assertEquals(6, state.load().orElseThrow()
                .vaults().get("church-fool").get("custom:night_vanilla"));
    }

    @Test
    void corruptFileIsIgnored() throws IOException {
        Path file = dir.resolve("memberships.json");
        Files.writeString(file, "{broken json");
        assertTrue(new JSONMembershipRepository(file.toString()).load().isEmpty());
    }
}
```

- [ ] **Step 2: Запустити — COMPILATION ERROR**

```powershell
& $mvn -o test "-Dtest=ChurchRepositoriesTest"
```

- [ ] **Step 3: Реалізувати три репозиторії** (однаковий каркас; показано membership — інші два аналогічно зі своїми records)

```java
package me.vangoo.infrastructure.organizations;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/** memberships.json: членства, кулдаун повторного вступу, флаг ініціації. Пишеться після кожної мутації. */
public class JSONMembershipRepository {

    private static final Logger LOGGER = Logger.getLogger(JSONMembershipRepository.class.getName());

    public record TaskRecord(String type, String targetKey, String targetName,
                             int required, int progress, int rewardPoints) {}

    public record OrderRecord(String pathwayName, int sequence,
                              long readyAtEpochMillis, int pointsPaid) {}

    public record MembershipRecord(String institutionId, int lifetimeContribution, int balance,
                                   long lastTaskRefreshEpochMillis, List<TaskRecord> tasks,
                                   TaskRecord initiationTask, String initiationPathway,
                                   OrderRecord activeOrder) {}

    public record PlayerChurchData(MembershipRecord membership,
                                   long rejoinCooldownUntilEpochMillis,
                                   boolean initiationUsed) {}

    public record Model(Map<String, PlayerChurchData> players) {}

    private final File file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public JSONMembershipRepository(String filePath) {
        this.file = new File(filePath);
    }

    public Optional<Model> load() {
        if (!file.exists() || file.length() == 0) {
            return Optional.empty();
        }
        try (FileReader reader = new FileReader(file)) {
            return Optional.ofNullable(gson.fromJson(reader, Model.class));
        } catch (IOException | JsonSyntaxException e) {
            LOGGER.warning("Failed to load memberships: " + e.getMessage());
            return Optional.empty();
        }
    }

    public void save(Model model) {
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(model, writer);
        } catch (IOException e) {
            LOGGER.warning("Failed to save memberships: " + e.getMessage());
        }
    }
}
```

`ChurchSiteRepository.java` — той самий каркас із `record Site(String institutionId, String world, double x, double y, double z, float yaw, float pitch) {}` та `record Model(List<Site> sites, List<String> processedVillageKeys) {}` (лог-повідомлення: `"Failed to load church sites"` / `"Failed to save church sites"`).

`ChurchStateRepository.java` — той самий каркас із `record Model(Map<String, Map<String, Integer>> vaults) {}` (лог: `"...church state..."`).

- [ ] **Step 4: Запустити тести — PASS**

```powershell
& $mvn -o test "-Dtest=ChurchRepositoriesTest"
```

- [ ] **Step 5: Коміт**

```bash
printf 'feat(organizations): add membership, site and vault json repositories\n' > /tmp/msg.txt
git add src/main/java/me/vangoo/infrastructure/organizations src/test/java/me/vangoo/infrastructure/organizations
git commit -F /tmp/msg.txt
```

---

### Task 8: Application — `ChurchService` (оркестратор)

**Files:**
- Create: `src/main/java/me/vangoo/application/services/ChurchService.java`
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java` (поле `potionRecipeConfig`: зберегти результат `PotionRecipeConfigLoader.load()`, який зараз передається в `PotionManager` локальною змінною, у поле контейнера — знадобиться `ChurchService`)

**Interfaces:**
- Consumes: усе з Task 1–7; наявні `BeyonderService.getBeyonder(UUID)` (може повернути `null` — гравець без шляху), `Beyonder.getPathway().getName()`, `Beyonder.getSequenceLevel()`, `PathwayManager.getPathway(String)` (null = шлях не реалізований) і `PathwayManager.getPathways()`/еквівалентний доступ до всіх шляхів (використати наявний API; групу шляху дає `Pathway.getPathwayGroup().name()` — звірити точний геттер у `Pathway`), `PotionManager.createPotionItem(String, Sequence)`, `RecipeUnlockService.unlockRecipe(UUID, String, int)`, `MarketItemClassifier.classify(ItemStack)` → `ClassifiedItem(category, itemKey, sequence)`, `MarketItemNamer.displayName(String itemKey)`, `WalletService.charge(Player, PoundMoney)`, `Map<String, CreatureDefinition> creatureRegistry` (`CreatureDefinition.pathway()`, `.sequence()`), `Map<String, Map<Integer, RecipeDefinition>> potionRecipeConfig` (`RecipeDefinition.mainIds()/auxIds()`).
- Produces (використовують Task 9–12):
  - `enum JoinResult { OK, ALREADY_MEMBER, WRONG_PATHWAY, COOLDOWN, UNKNOWN_CHURCH }`
  - `JoinResult join(Player player, String institutionId)`; `boolean leave(Player player)`
  - `Optional<Membership> membershipOf(UUID playerId)`; `Optional<Institution> churchOf(UUID playerId)`; `String pathwayNameOf(Player)` (nullable)
  - `void ensureFreshTasks(Player player)`; `List<ChurchTask> tasksOf(Player)`
  - `int deliverTask(Player player, int taskIndex)` (повертає кількість зданих одиниць; 0 = нічого)
  - `void onCreatureKilled(Player killer, String creatureId)`; `void onRampagerKilled(Player killer)`
  - `int donateFromHand(Player player)` (нараховані очки; 0 = відмова); `int donateCoins(Player player, PoundMoney money)`
  - `boolean canStartInitiation(Player)`; `List<String> initiationPathwayChoices(Player)`; `boolean startInitiation(Player, String pathwayName)`; `boolean claimInitiation(Player)` (видача зілля + рецепт у знання)
  - `record OrderQuote(String pathwayName, int sequence, int price, Map<String,Integer> missing)`; `Optional<OrderQuote> quoteOrder(Player, String pathwayNameForPathless)`; `boolean placeOrder(Player, String pathwayNameForPathless)`; `boolean claimOrder(Player)`; `Optional<PotionOrder> orderOf(UUID)`
  - `void seedVaultIfAbsent(String institutionId)`; `ChurchVault vaultOf(String institutionId)`
  - `void tickOrders()` (сповіщення про готові замовлення)
  - `int[] rankThresholds()`; `InstitutionRegistry registry()`
- Константа: `RAMPAGER_REWARD_POINTS = 150`.

**Ключові правила реалізації (без відхилень):**
1. Стан у пам'яті: `Map<UUID, Membership> memberships`, `Map<UUID, Long> rejoinCooldownUntil`, `Set<UUID> initiationUsed`, `Map<String, ChurchVault> vaults`, `Set<UUID> notifiedReadyOrders` — усі instance-поля, гідруються з репозиторіїв у конструкторі, `persist()`/`persistState()` після КОЖНОЇ мутації.
2. `join`: перевірки в порядку ALREADY_MEMBER → COOLDOWN (`System.currentTimeMillis() < rejoinCooldownUntil`) → UNKNOWN_CHURCH (`registry.byId` пусто або `type != CHURCH`) → WRONG_PATHWAY (`!institution.acceptsPathway(pathwayNameOf(player))`) → OK: `new Membership`, `ensureFreshTasks`, persist.
3. `leave`: видалити членство (разом із завданнями/замовленням — згорають), `rejoinCooldownUntil = now + days*86_400_000L`, persist. Ініціаційний флаг НЕ чіпати.
4. `ensureFreshTasks`: якщо `now − lastTaskRefreshEpochMillis ≥ refreshHours*3_600_000L` → згенерувати `config.tasksMaxActive()` нових завдань (старі невиконані замінюються), оновити мітку, persist. Кандидати-істоти: `creatureRegistry.values()` → `CreatureCandidate(id, pathway, sequence)`. Мапа груп: для кожного шляху з `PathwayManager` — `name → group.name()`. Кандидати-інгредієнти: для кожного доступу церкви з реалізованим шляхом, для кожного `seq` з `potionRecipeConfig.get(pathway)`, де `access.supportsSequence(seq)` — усі `mainIds()+auxIds()` БЕЗ префікса `vanilla:` → `IngredientCandidate(ingredientKey(id), namer.displayName(ingredientKey(id)), seq)`; `ingredientKey(id)` = `id`, якщо вже має префікс `custom:`, інакше `"custom:" + id`.
5. `deliverTask`: завдання типу DELIVER; порахувати в інвентарі стаки з `classifier.classify(stack).itemKey().equals(targetKey)` (патерн `GatheringService.countMatching:880-891`); зняти `min(наявне, required − progress)` (патерн `removeMatching:894+`); зняте → `vaultOf(church).add(targetKey, n)` + **пожертвенні** очки одразу (`donationIngredientPointsBySeq` за seq завдання × n); якщо завдання виконане — прибрати з пулу і додати `rewardPoints` (подвійний вклад зі спеку); persist обох сховищ.
6. `onCreatureKilled`: HUNT-завдання з `targetKey == creatureId` → `withProgress(+1)`; на завершенні — нагорода + повідомлення гравцю (`ChatColor.GREEN`, українською) + прибрати; те саме для `initiationTask` (без очок — завершення відкриває `claimInitiation`); persist.
7. `donateFromHand`: `classify` руки; `INGREDIENT` → приймається завжди; `RECIPE_BOOK`/`CHARACTERISTIC` — лише якщо `institution.accessFor(pathway з itemKey)` присутній (чужі — відмова 0); очки = таблиця за `sequence` × amount; стак повністю зникає з руки → `vault.add(itemKey, amount)`; persist. Монети в руці (`classify` пусто) → 0.
8. `donateCoins`: `walletService.charge(player, money)` пусто → 0; інакше очки = `money.coppets() × pointsPerCoppet`; монети згорають (у сховище НЕ кладуться); persist.
9. Ініціація: `canStartInitiation` = член церкви ∧ `pathwayNameOf == null` ∧ `!initiationUsed` ∧ `initiationTask == null`; `initiationPathwayChoices` = доступи церкви з реалізованим шляхом (`pathwayManager.getPathway(name) != null`); `startInitiation` — `generateInitiation` з УСІХ істот реєстру; `claimInitiation`: завдання виконане ∧ `vault.hasRecipeKnowledge(pathway, 9)` ∧ `vault.consumeFor(brewRecipeFor(pathway, 9))` → видати `potionManager.createPotionItem(pathway, Sequence.of(9))` (надлишок — дроп під ноги), `recipeUnlockService.unlockRecipe(uuid, pathway, 9)`, `initiationUsed.add`, `clearInitiation`, persist. Брак у сховищі → повідомлення з переліком через `namer.displayName` і `false`.
10. Замовлення: `quoteOrder` — для гравця зі шляхом ціль = `getSequenceLevel() − 1` (`<0` → пусто), доступ = `accessFor(шлях)` (нема → пусто), гейт `targetSeq ≥ rank.lowestOrderableSequence(access)` (інакше пусто); для гравця без шляху — `pathwayNameForPathless` із `initiationPathwayChoices`-подібного списку, ціль seq 9. `price = orderPointsBySeq.get(seq)`. `missing = vault.missingFor(recipe)` (плюс відсутність книги → до missing додати `recipe:<p>:<seq> → 1`). `placeOrder`: `quote.missing` порожнє ∧ `activeOrder == null` ∧ `membership.spend(price)` → `vault.consumeFor` → `setActiveOrder(new PotionOrder(pathway, seq, now + brewHours*3_600_000L, price))`, persist. `claimOrder`: `isReady` → видати зілля, `clearActiveOrder`, persist.
11. `seedVaultIfAbsent`: для кожного доступу з реалізованим шляхом, для `seq` від 9 до `access.minSequence()`: `def = potionRecipeConfig.get(pathway).get(seq)`; якщо є — `add("recipe:<p>:<seq>", 1)`, кожен id з `mainIds()+auxIds()` (без `vanilla:`) × `vaultSeedBrewsPerRecipe`, `add("characteristic:<p>:<seq>", vaultSeedCharacteristicsPerSeq)`; persistState.
12. `tickOrders`: для кожного membership із готовим замовленням і гравцем онлайн, якого ще нема в `notifiedReadyOrders` → повідомлення «Ваше зілля готове — заберіть у священика» + додати в множину; `claimOrder`/`leave` прибирають із множини.

- [ ] **Step 1: Реалізувати `ChurchService`** за правилами вище (без юніт-тесту — клас Bukkit-залежний; доменна логіка вже покрита Task 1–5; перевірка — компіляція + in-server у Task 13/14). Конструктор:

```java
public ChurchService(Plugin plugin, ChurchConfig config, InstitutionRegistry registry,
                     BeyonderService beyonderService, PathwayManager pathwayManager,
                     PotionManager potionManager,
                     Map<String, Map<Integer, RecipeDefinition>> potionRecipeConfig,
                     RecipeUnlockService recipeUnlockService,
                     MarketItemClassifier classifier, MarketItemNamer namer,
                     WalletService walletService,
                     Map<String, CreatureDefinition> creatureRegistry,
                     JSONMembershipRepository membershipRepository,
                     ChurchStateRepository stateRepository) { ... }
```

Гідрація в конструкторі: `membershipRepository.load()` → відновити `Membership` (мапінг records ↔ домен: `TaskRecord` ↔ `ChurchTask` через `ChurchTask.Type.valueOf(type)`, `OrderRecord` ↔ `PotionOrder`), `rejoinCooldownUntil`, `initiationUsed`; `stateRepository.load()` → `vaults` через `ChurchVault.restore`.

- [ ] **Step 2: Зберегти `potionRecipeConfig` полем `ServiceContainer`**

У `initializeInfrastructure` знайти місце, де результат `PotionRecipeConfigLoader` передається в `new PotionManager(...)`, і зберегти його в нове поле `private Map<String, Map<Integer, RecipeDefinition>> potionRecipeConfig;` + геттер `getPotionRecipeConfig()`.

- [ ] **Step 3: Компіляція**

```powershell
& $mvn -o compile
```
Очікування: BUILD SUCCESS.

- [ ] **Step 4: Коміт**

```bash
printf 'feat(organizations): add church service orchestrator\n\nMembership lifecycle, task progress, donations, vault seeding and\npotion orders brewed from vault resources.\n' > /tmp/msg.txt
git add src/main/java/me/vangoo/application/services/ChurchService.java src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java
git commit -F /tmp/msg.txt
```

---

### Task 9: Інфраструктура — священик (Citizens), розміщення структур, сайти

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/citizens/ChurchPriestService.java`
- Create: `src/main/java/me/vangoo/infrastructure/organizations/ChurchStructurePlacer.java`
- Create: `src/main/java/me/vangoo/infrastructure/organizations/ChurchSiteService.java`

**Interfaces:**
- Consumes: `InstitutionRegistry`, `ChurchSiteRepository` (Task 7), `ChurchService.seedVaultIfAbsent` (Task 8); патерн `OrganizerNpcService` (SHOULD_SAVE=false, `setProtected(true)`).
- Produces (`ChurchPriestService`): `spawn(String institutionId, Location loc)`, `despawnAll()`, `Optional<String> institutionOf(NPC npc)`, `despawnAt(String institutionId, Location near)`.
- Produces (`ChurchStructurePlacer`): `boolean place(String institutionId, Location loc)` — вантажить `mysteries:church_<shortId>` (де `shortId` — id без префікса `church-`, дефіси → підкреслення, напр. `church-evernight` → `mysteries:church_evernight`); нема NBT → warn-лог і `false` (сайт усе одно створюється — фолбек без будівлі).
- Produces (`ChurchSiteService`): `boolean bind(String institutionId, Location loc)` (ручний, без структури), `boolean autoPlace(String villageKey, String institutionId, Location loc)` (структура + сайт + NPC + `seedVaultIfAbsent`), `boolean unbindNearest(Location loc)` (радіус 16 бл), `boolean isVillageProcessed(String key)`, `markVillageProcessed(String key)`, `List<String> unplacedChurchIds()` (церкви реєстру мінус ті, що вже мають сайт), `spawnAllNpcs()` (виклик з `onEnable`).

- [ ] **Step 1: Реалізувати `ChurchPriestService`**

```java
package me.vangoo.infrastructure.citizens;

import me.vangoo.domain.organizations.Institution;
import me.vangoo.domain.organizations.InstitutionRegistry;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** NPC-священики церков: по одному на сайт; SHOULD_SAVE=false — респавняться на старті
 * з church-sites.json (як Посередник ринку, не персистяться Citizens'ом). */
public class ChurchPriestService {

    private final InstitutionRegistry registry;
    private final Map<Integer, String> npcToInstitution = new HashMap<>();

    public ChurchPriestService(InstitutionRegistry registry) {
        this.registry = registry;
    }

    public void spawn(String institutionId, Location location) {
        String name = registry.byId(institutionId)
                .map(Institution::displayName).orElse(institutionId);
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER,
                ChatColor.GOLD + "Жрець — " + name);
        npc.data().set(NPC.Metadata.SHOULD_SAVE, false);
        npc.setProtected(true);
        npc.spawn(location);
        npcToInstitution.put(npc.getId(), institutionId);
    }

    public Optional<String> institutionOf(NPC npc) {
        return npc == null ? Optional.empty()
                : Optional.ofNullable(npcToInstitution.get(npc.getId()));
    }

    public void despawnAt(String institutionId, Location near) {
        npcToInstitution.entrySet().removeIf(e -> {
            if (!e.getValue().equals(institutionId)) return false;
            NPC npc = CitizensAPI.getNPCRegistry().getById(e.getKey());
            if (npc == null) return true;
            boolean close = npc.isSpawned() && npc.getEntity().getWorld() == near.getWorld()
                    && npc.getEntity().getLocation().distance(near) <= 32;
            if (close) npc.destroy();
            return close;
        });
    }

    public void despawnAll() {
        npcToInstitution.keySet().forEach(id -> {
            NPC npc = CitizensAPI.getNPCRegistry().getById(id);
            if (npc != null) npc.destroy();
        });
        npcToInstitution.clear();
    }
}
```

- [ ] **Step 2: Реалізувати `ChurchStructurePlacer`**

```java
package me.vangoo.infrastructure.organizations;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.plugin.Plugin;
import org.bukkit.structure.Structure;

import java.util.Random;

/** Ставить храмову будівлю з mysteries-datapack (mysteries:church_<id>). Нема NBT —
 * warn і false: сайт/NPC працюють і без будівлі (фолбек до появи контенту). */
public class ChurchStructurePlacer {

    private final Plugin plugin;
    private final Random random = new Random();

    public ChurchStructurePlacer(Plugin plugin) {
        this.plugin = plugin;
    }

    public boolean place(String institutionId, Location loc) {
        String shortId = institutionId.replaceFirst("^church-", "").replace('-', '_');
        NamespacedKey key = new NamespacedKey("mysteries", "church_" + shortId);
        Structure structure = Bukkit.getStructureManager().loadStructure(key);
        if (structure == null) {
            plugin.getLogger().warning("Church structure " + key + " not found in datapack; "
                    + "placing site without a building");
            return false;
        }
        structure.place(loc, true, StructureRotation.NONE, Mirror.NONE, 0, 1.0f, random);
        return true;
    }
}
```

- [ ] **Step 3: Реалізувати `ChurchSiteService`** — тримає `ChurchSiteRepository.Model` у пам'яті, persist після кожної мутації:

```java
package me.vangoo.infrastructure.organizations;

import me.vangoo.application.services.ChurchService;
import me.vangoo.domain.organizations.Institution;
import me.vangoo.infrastructure.citizens.ChurchPriestService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/** Сайти храмів: ручний bind, автоспавн біля сіл (кожна церква — раз на світ), NPC. */
public class ChurchSiteService {

    private final ChurchSiteRepository repository;
    private final ChurchPriestService priests;
    private final ChurchStructurePlacer placer;
    private final ChurchService churchService;
    private List<ChurchSiteRepository.Site> sites = new ArrayList<>();
    private List<String> processedVillages = new ArrayList<>();

    public ChurchSiteService(ChurchSiteRepository repository, ChurchPriestService priests,
                             ChurchStructurePlacer placer, ChurchService churchService) {
        this.repository = repository;
        this.priests = priests;
        this.placer = placer;
        this.churchService = churchService;
        repository.load().ifPresent(m -> {
            sites = new ArrayList<>(m.sites());
            processedVillages = new ArrayList<>(m.processedVillageKeys());
        });
    }

    public boolean bind(String institutionId, Location loc) {
        sites.add(toSite(institutionId, loc));
        churchService.seedVaultIfAbsent(institutionId);
        priests.spawn(institutionId, loc);
        persist();
        return true;
    }

    public boolean autoPlace(String villageKey, String institutionId, Location loc) {
        placer.place(institutionId, loc); // false = без будівлі, сайт усе одно живе
        markVillageProcessed(villageKey);
        return bind(institutionId, loc);
    }

    public boolean unbindNearest(Location loc) {
        for (int i = 0; i < sites.size(); i++) {
            ChurchSiteRepository.Site s = sites.get(i);
            World w = Bukkit.getWorld(s.world());
            if (w != null && w.equals(loc.getWorld())
                    && loc.distance(new Location(w, s.x(), s.y(), s.z())) <= 16) {
                priests.despawnAt(s.institutionId(), loc);
                sites.remove(i);
                persist();
                return true;
            }
        }
        return false;
    }

    public boolean isVillageProcessed(String key) {
        return processedVillages.contains(key);
    }

    public void markVillageProcessed(String key) {
        if (!processedVillages.contains(key)) {
            processedVillages.add(key);
            persist();
        }
    }

    /** Церкви без жодного сайту — кандидати автоспавну (кожна — щонайбільше раз на світ). */
    public List<String> unplacedChurchIds() {
        List<String> placed = sites.stream()
                .map(ChurchSiteRepository.Site::institutionId).toList();
        return churchService.registry().churches().stream()
                .map(Institution::id)
                .filter(id -> !placed.contains(id))
                .toList();
    }

    public void spawnAllNpcs() {
        for (ChurchSiteRepository.Site s : sites) {
            World w = Bukkit.getWorld(s.world());
            if (w != null) {
                priests.spawn(s.institutionId(),
                        new Location(w, s.x(), s.y(), s.z(), s.yaw(), s.pitch()));
            }
        }
    }

    private static ChurchSiteRepository.Site toSite(String institutionId, Location l) {
        return new ChurchSiteRepository.Site(institutionId, l.getWorld().getName(),
                l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    private void persist() {
        repository.save(new ChurchSiteRepository.Model(sites, processedVillages));
    }
}
```

- [ ] **Step 4: Компіляція + коміт**

```powershell
& $mvn -o compile
```

```bash
printf 'feat(organizations): add priest npc, structure placer and site service\n' > /tmp/msg.txt
git add src/main/java/me/vangoo/infrastructure/citizens/ChurchPriestService.java src/main/java/me/vangoo/infrastructure/organizations
git commit -F /tmp/msg.txt
```

---

### Task 10: Presentation — `ChurchSpawnListener` (автоспавн біля сіл)

**Files:**
- Create: `src/main/java/me/vangoo/presentation/listeners/ChurchSpawnListener.java`

**Interfaces:**
- Consumes: `ChurchSiteService` (Task 9), `ChurchConfig.spawnVillageOffset()` (Task 6).
- Produces: Bukkit `Listener` на `ChunkLoadEvent`.

- [ ] **Step 1: Реалізувати лістенер**

```java
package me.vangoo.presentation.listeners;

import me.vangoo.infrastructure.organizations.ChurchSiteService;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.util.BoundingBox;

import java.util.List;
import java.util.Random;

/**
 * Знайдене село → поруч спавниться випадкова ще не розміщена церква (кожна — раз на світ).
 * Ключ села — min-кут bbox структурного старту; оброблені села персистяться.
 */
public class ChurchSpawnListener implements Listener {

    private final ChurchSiteService sites;
    private final int villageOffset;
    private final Random random = new Random();

    public ChurchSpawnListener(ChurchSiteService sites, int villageOffset) {
        this.sites = sites;
        this.villageOffset = villageOffset;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (GeneratedStructure structure : event.getChunk().getStructures()) {
            NamespacedKey key = Registry.STRUCTURE.getKey(structure.getStructure());
            if (key == null || !key.getKey().startsWith("village")) {
                continue;
            }
            BoundingBox box = structure.getBoundingBox();
            String villageKey = event.getWorld().getName()
                    + ":" + (int) box.getMinX() + ":" + (int) box.getMinZ();
            if (sites.isVillageProcessed(villageKey)) {
                continue;
            }
            List<String> unplaced = sites.unplacedChurchIds();
            if (unplaced.isEmpty()) {
                sites.markVillageProcessed(villageKey); // усі 10 розміщені — більше не перевіряти
                continue;
            }
            String churchId = unplaced.get(random.nextInt(unplaced.size()));
            Location spot = pickSpot(event.getWorld(), box);
            sites.autoPlace(villageKey, churchId, spot);
        }
    }

    /** Точка за межею села: випадкова сторона світу + офсет, y — поверхня. */
    private Location pickSpot(World world, BoundingBox box) {
        int side = random.nextInt(4);
        double x = switch (side) {
            case 0 -> box.getMaxX() + villageOffset;
            case 1 -> box.getMinX() - villageOffset;
            default -> box.getCenterX();
        };
        double z = switch (side) {
            case 2 -> box.getMaxZ() + villageOffset;
            case 3 -> box.getMinZ() - villageOffset;
            default -> box.getCenterZ();
        };
        int y = world.getHighestBlockYAt((int) x, (int) z) + 1;
        return new Location(world, x, y, z);
    }
}
```

Примітка виконавцю: якщо на цільовій версії Paper `Registry.STRUCTURE` має інше ім'я константи — знайти актуальне через `org.bukkit.Registry` (мета — `NamespacedKey` структури; village-структури мають ключі `village_plains`, `village_desert` тощо).

- [ ] **Step 2: Компіляція + коміт**

```powershell
& $mvn -o compile
```

```bash
printf 'feat(organizations): spawn a unique church near each new village\n' > /tmp/msg.txt
git add src/main/java/me/vangoo/presentation/listeners/ChurchSpawnListener.java
git commit -F /tmp/msg.txt
```

---

### Task 11: UI + лістенери — `ChurchMenu` і `ChurchListener`

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/ui/ChurchMenu.java`
- Create: `src/main/java/me/vangoo/presentation/listeners/ChurchListener.java`

**Interfaces:**
- Consumes: `ChurchService` (Task 8: `join/leave/tasksOf/deliverTask/donateFromHand/donateCoins/canStartInitiation/initiationPathwayChoices/startInitiation/claimInitiation/quoteOrder/placeOrder/claimOrder/orderOf/membershipOf/churchOf/rankThresholds/ensureFreshTasks`), `ChurchPriestService.institutionOf(NPC)` (Task 9), наявні `MoneyPicker.open(player, title, allowZero, onConfirm, onCancel)`, `ConfirmationMenu.open(player, give, get, title, onConfirm)`, `MarketItemNamer`, `RampageManager.isInRampage(UUID)`, `MythicCreatureGateway.creatureId(Entity)`; GUI-ідіома — `MarketMenu` (`Gui.gui().title(...).rows(n).disableAllInteractions().create()`, `runSynced`).
- Produces (`ChurchMenu`): `openFor(Player player, String institutionId)` — роутер: не член → привітання+[Вступити]; член цієї церкви → головне меню (Завдання / Замовлення зілля / Пожертви / Мій ранг); член іншої → лор-відмова у чат.
- Produces (`ChurchListener`): обробники `NPCRightClickEvent` (роутинг у меню), `EntityDeathEvent` (kill-прогрес), `PlayerDeathEvent` (рампейджер).

- [ ] **Step 1: Реалізувати `ChurchMenu`** (структура за зразком `MarketMenu`; усі тексти українською):

Обов'язкові екрани й поведінка (кожен пункт — конкретна кнопка/дія):
1. **Привітання не-члена** (3 ряди): паперовий item із `lore` церкви (з реєстру) + кнопка `[Вступити]` → `churchService.join`; результат мапиться на повідомлення: `WRONG_PATHWAY` → «Ваш шлях чужий цій церкві.», `COOLDOWN` → «Ви нещодавно покинули інституцію — поверніться пізніше.», `ALREADY_MEMBER` (в іншій церкві) → «Ви вже служите іншій церкві.», `OK` → «Вас прийнято до лав.» і відкрити головне меню.
2. **Головне меню** (3 ряди): Завдання (`BOOK`), Замовлення зілля (`BREWING_STAND`), Пожертви (`GOLD_INGOT`), Мій ранг (`NAME_TAG`), `[Покинути церкву]` (`BARRIER`, з `ConfirmationMenu`: give = табличка «Ваш вклад згорить», get = табличка «Свобода»).
3. **Завдання** (перед побудовою — `churchService.ensureFreshTasks(player)`): по item на завдання (HUNT → `IRON_SWORD`, DELIVER → `CHEST`; lore: назва цілі через `MarketItemNamer`/creatureId, `progress/required`, нагорода); клік по DELIVER → `deliverTask` і повідомлення «Здано N од.»; якщо `canStartInitiation` → окрема кнопка `[Ініціація]` (`NETHER_STAR`): вибір шляху з `initiationPathwayChoices` (по item на шлях) → `startInitiation`; якщо `initiationTask` виконано → кнопка `[Отримати зілля]` → `claimInitiation`.
4. **Замовлення зілля**: якщо `orderOf` присутнє — item статусу (готове → `[Забрати]` → `claimOrder`; ні — «Вариться, лишилось X год Y хв»); інакше `quoteOrder` (для гравця без шляху — спершу вибір шляху, як в ініціації): `missing` порожнє → `ConfirmationMenu` (give = табличка «N очок вкладу», get = пляшечка «Зілля <шлях> Посл. <seq>») → `placeOrder`; `missing` непорожнє → повідомлення «Сховищу церкви бракує: …» (кожен ключ через `namer.displayName`).
5. **Пожертви** (3 ряди): `[Пожертвувати предмет у руці]` → `donateFromHand` («+N очок вкладу» або «Церква не прийме це.»); `[Пожертвувати монети]` → `MoneyPicker.open(player, "Пожертва монет", false, money -> donateCoins, назад)`.
6. **Мій ранг**: item із рангом (`rank(thresholds).displayName()`), lifetime-вкладом, балансом і порогом наступного рангу.

- [ ] **Step 2: Реалізувати `ChurchListener`**

```java
package me.vangoo.presentation.listeners;

import me.vangoo.application.services.ChurchService;
import me.vangoo.application.services.RampageManager;
import me.vangoo.infrastructure.citizens.ChurchPriestService;
import me.vangoo.infrastructure.mythic.MythicCreatureGateway;
import me.vangoo.infrastructure.ui.ChurchMenu;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

/** Кліки по священику, kill-прогрес завдань і доручення «Зупинити бушуючого». */
public class ChurchListener implements Listener {

    private final ChurchPriestService priests;
    private final ChurchMenu menu;
    private final ChurchService churchService;
    private final MythicCreatureGateway creatures;
    private final RampageManager rampageManager;

    public ChurchListener(ChurchPriestService priests, ChurchMenu menu,
                          ChurchService churchService, MythicCreatureGateway creatures,
                          RampageManager rampageManager) {
        this.priests = priests;
        this.menu = menu;
        this.churchService = churchService;
        this.creatures = creatures;
        this.rampageManager = rampageManager;
    }

    @EventHandler
    public void onPriestClick(NPCRightClickEvent event) {
        priests.institutionOf(event.getNPC())
                .ifPresent(id -> menu.openFor(event.getClicker(), id));
    }

    @EventHandler
    public void onCreatureDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        creatures.creatureId(event.getEntity())
                .ifPresent(id -> churchService.onCreatureKilled(killer, id));
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || killer.equals(event.getEntity())) {
            return;
        }
        if (rampageManager.isInRampage(event.getEntity().getUniqueId())) {
            churchService.onRampagerKilled(killer);
        }
    }
}
```

- [ ] **Step 3: Компіляція + коміт**

```powershell
& $mvn -o compile
```

```bash
printf 'feat(organizations): add church menu and priest interaction listener\n' > /tmp/msg.txt
git add src/main/java/me/vangoo/infrastructure/ui/ChurchMenu.java src/main/java/me/vangoo/presentation/listeners/ChurchListener.java
git commit -F /tmp/msg.txt
```

---

### Task 12: Команда `/church`, scheduler і повний wiring

**Files:**
- Create: `src/main/java/me/vangoo/presentation/commands/ChurchCommand.java`
- Create: `src/main/java/me/vangoo/infrastructure/schedulers/ChurchOrderScheduler.java`
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java`
- Modify: `src/main/java/me/vangoo/MysteriesAbovePlugin.java`
- Modify: `src/main/resources/plugin.yml`

**Interfaces:**
- Consumes: усе з Task 6–11.
- Produces: команда `/church bind <id> | unbind | leave | info`; scheduler зі `start()`/`stop()`; геттери контейнера `getChurchService()`, `getChurchSiteService()`, `getChurchMenu()`, `getChurchPriestService()`, `getChurchOrderScheduler()`, `getChurchConfig()`.

- [ ] **Step 1: Реалізувати `ChurchCommand`** (`CommandExecutor` + `TabCompleter`)

Поведінка (перевірка `mysteriesabove.admin` У КОДІ лише для bind/unbind, як у `GatheringCommand`):
- `/church bind <institutionId>` (admin, лише гравець): `siteService.bind(id, player.getLocation())`; невідомий/не-церковний id → «Невідома церква: <id>».
- `/church unbind` (admin): `siteService.unbindNearest(player.getLocation())` → «Храм відв'язано» / «Поруч немає храму (радіус 16)».
- `/church leave`: `churchService.leave(player)` → «Ви покинули церкву. Вклад згорів.» / «Ви не член церкви.»
- `/church info`: назва церкви, ранг, вклад/баланс, активні завдання (рядками).
- TabCompleter: перший аргумент `bind|unbind|leave|info`; для `bind` — id церков із `registry.churches()`.

- [ ] **Step 2: Реалізувати `ChurchOrderScheduler`**

```java
package me.vangoo.infrastructure.schedulers;

import me.vangoo.application.services.ChurchService;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/** Щохвилини будить ChurchService.tickOrders() — сповіщення про готові зілля. */
public class ChurchOrderScheduler {

    private final Plugin plugin;
    private final ChurchService churchService;
    private BukkitTask task;

    public ChurchOrderScheduler(Plugin plugin, ChurchService churchService) {
        this.plugin = plugin;
        this.churchService = churchService;
    }

    public void start() {
        task = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, churchService::tickOrders, 20L * 60, 20L * 60);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
```

- [ ] **Step 3: Wiring у `ServiceContainer`** (фази — за наявним порядком):
  - `initializeCoreServices`: поле `institutionRegistry = new InstitutionRegistry()`.
  - `initializeInfrastructure`: `churchConfig = ChurchConfig.load(plugin)`; репозиторії:
    ```java
    this.membershipRepository = new JSONMembershipRepository(
            plugin.getDataFolder() + File.separator + "memberships.json");
    this.churchSiteRepository = new ChurchSiteRepository(
            plugin.getDataFolder() + File.separator + "church-sites.json");
    this.churchStateRepository = new ChurchStateRepository(
            plugin.getDataFolder() + File.separator + "churches-state.json");
    ```
  - `initializeApplicationServices` (ПІСЛЯ `potionManager`, `marketItemNamer`, `creatureRegistry`): `churchService = new ChurchService(plugin, churchConfig, institutionRegistry, beyonderService, pathwayManager, potionManager, potionRecipeConfig, recipeUnlockService, marketItemClassifier, marketItemNamer, walletService, creatureRegistry, membershipRepository, churchStateRepository)`; далі `churchPriestService = new ChurchPriestService(institutionRegistry)`, `churchStructurePlacer = new ChurchStructurePlacer(plugin)`, `churchSiteService = new ChurchSiteService(churchSiteRepository, churchPriestService, churchStructurePlacer, churchService)`.
  - `initializeUI`: `churchMenu = new ChurchMenu(plugin, churchService, marketItemNamer, confirmationMenu /* + moneyPicker створюється всередині, як у MarketMenu */)`.
  - `initializeSchedulers`: `churchOrderScheduler = new ChurchOrderScheduler(plugin, churchService)`; додати в `startSchedulers()` і `stopSchedulers()`.
  - Геттери на всі нові сервіси.
- [ ] **Step 4: `MysteriesAbovePlugin`**:
  - `registerEvents()`: `new ChurchListener(...)` і `new ChurchSpawnListener(services.getChurchSiteService(), services.getChurchConfig().spawnVillageOffset())` через `getServer().getPluginManager().registerEvents(...)`.
  - `registerCommands()`: `ChurchCommand churchCommand = new ChurchCommand(services.getChurchService(), services.getChurchSiteService()); getCommand("church").setExecutor(churchCommand); getCommand("church").setTabCompleter(churchCommand);`
  - `onEnable()` ПІСЛЯ реєстрації подій: `services.getChurchSiteService().spawnAllNpcs();` (респавн священиків із сайтів; Citizens уже доступний — плагін у `depend`).
  - `onDisable()`: перед `saveAll()` — `services.getChurchPriestService().despawnAll();`
- [ ] **Step 5: `plugin.yml`** — додати команду (БЕЗ `permission:` — leave/info відкриті всім; bind/unbind перевіряються в коді):

```yaml
  church:
    description: Церкви — членство, служба, замовлення зілля
    usage: /church <bind|unbind|leave|info>
```

- [ ] **Step 6: Повна збірка + повний прогін тестів**

```powershell
& $mvn -o clean test
```
Очікування: BUILD SUCCESS, усі тести зелені (включно з ArchUnit).

- [ ] **Step 7: Коміт**

```bash
printf 'feat(organizations): wire church command, scheduler and services\n' > /tmp/msg.txt
git add src/main/java/me/vangoo/presentation/commands/ChurchCommand.java src/main/java/me/vangoo/infrastructure/schedulers/ChurchOrderScheduler.java src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java src/main/java/me/vangoo/MysteriesAbovePlugin.java src/main/resources/plugin.yml
git commit -F /tmp/msg.txt
```

---

### Task 13: Датапак — храмові будівлі (контент, частково ручний)

**Files:**
- Create: `mysteries-datapack/data/mysteries/structure/church_<shortId>.nbt` × 10 (ручний контент)

**Interfaces:**
- Consumes: `ChurchStructurePlacer` шукає `mysteries:church_<shortId>` (Task 9): `church_evernight`, `church_god_of_combat`, `church_earth_mother`, `church_lord_of_storms`, `church_knowledge_wisdom`, `church_eternal_sun`, `church_steam_machinery`, `church_fool`, `church_eternal_darkness`, `church_ruler_of_calamity`.

- [ ] **Step 1: Зафіксувати очікувані ключі** — додати README-нотатку в `mysteries-datapack` (або оновити наявний опис датапаку): список 10 ключів вище + як зберегти будівлю (у грі: побудувати → structure block SAVE із назвою `mysteries:church_<shortId>` → забрати `.nbt` із `world/generated/mysteries/structures/` у датапак).
- [ ] **Step 2 (ручний, поза кодом):** побудувати/зберегти 10 будівель. До появи NBT механіка працює через фолбек (warn-лог, сайт+NPC без будівлі) — реліз коду НЕ блокується.
- [ ] **Step 3: Коміт** (README-нотатка; NBT — окремими комітами в міру готовності)

```bash
printf 'docs(organizations): document expected church structure keys\n' > /tmp/msg.txt
git add mysteries-datapack
git commit -F /tmp/msg.txt
```

---

### Task 14: Документація + фінальна верифікація

**Files:**
- Create: `.claude/rules/church-organizations.md`
- Modify: `CLAUDE.md` (Config & persistence: `memberships.json`, `church-sites.json`, `churches-state.json`, секція `church.*`; Admin commands: `/church`)
- Modify: `docs/superpowers/specs/2026-06-26-ingredient-economy-roadmap.md` (позначка «6b реалізовано» + примітка про свідомий виняток емісії: сід сховищ церков)

**Interfaces:**
- Consumes: усе реалізоване (Task 1–13).

- [ ] **Step 1: Написати `.claude/rules/church-organizations.md`** — за зразком `market-gathering.md`, розділи: механізм (реєстр/членство/ранги/завдання/замовлення/сховище/сайти), «Як додати інституцію в реєстр» (Institution у `InstitutionRegistry.buildAll` + тест покриття), «Як додати ранг/поріг» (config + `ChurchRank`), інваріанти (сирі Характеристики не видаються; книга-знання не споживається; кожна церква — раз на світ; персист після кожної мутації), заборони (мутувати `ChurchVault` повз `ChurchService`; видавати зілля повз замовлення/ініціацію).
- [ ] **Step 2: Оновити `CLAUDE.md` і роадмап** (стисло, без дублювання правила).
- [ ] **Step 3: Повний прогін**

```powershell
& $mvn -o clean package
```
Очікування: BUILD SUCCESS (shade JAR зібрано).

- [ ] **Step 4: In-server чекліст** (ручний, на тестовому сервері з Citizens+MythicMobs):
  1. Новий світ → знайти село → храм заспавнився (або warn-фолбек без NBT), NPC на місці; друге село → ІНША церква.
  2. `/church bind church-eternal-sun` → NPC; `/church unbind` → зник.
  3. Клік не-членом → привітання; вступ гравцем відповідного шляху / без шляху; відмова чужому шляху.
  4. Ініціація без шляху: вибір шляху → легке завдання → вбивства зараховуються → `[Отримати зілля]` → зілля Seq 9 у руках + рецепт у `/recipe`-меню гравця; повторна ініціація неможлива (і після leave/join).
  5. Завдання: kill-прогрес; доставка знімає предмети і кладе у сховище (перевірити подвійний вклад: `/church info`).
  6. Пожертви: інгредієнт/книга/Характеристика свого шляху (+очки), чужа Характеристика — відмова; монети через пікер (списуються).
  7. Замовлення: без ресурсів у сховищі → перелік браку; після пожертв → підтвердження → очки списані → через `brew-hours` (для тесту зменшити в config.yml) сповіщення → `[Забрати]` → зілля.
  8. Ранги: накрутити вклад → стеля замовлень зростає; `/church leave` → вклад згорів, кулдаун вступу діє.
  9. Рестарт сервера: сайти/NPC, членства, завдання, замовлення, сховища, оброблені села — відновлені.
  10. Зупинка рампейджера: `/rampager` на тестовому гравці → вбити → вклад нараховано.
- [ ] **Step 5: Коміт документації**

```bash
printf 'docs(rules): add church organizations rule and update overview\n' > /tmp/msg.txt
git add .claude/rules/church-organizations.md CLAUDE.md docs/superpowers/specs/2026-06-26-ingredient-economy-roadmap.md
git commit -F /tmp/msg.txt
```

- [ ] **Step 6: Фініш гілки** — invoke superpowers:finishing-a-development-branch (merge/PR за вибором користувача).

