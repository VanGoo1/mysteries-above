# Ranged Stance Behavior Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Замінити velocity-відскок RANGED-мобів на станову поведінку зі справжньою навігацією: стоїть і кастує в робочій смузі, відходить кроком при зближенні, контратакує в мілі, коли гравець б'є впритул.

**Architecture:** `pom.xml` переходить на paper-api (надмножина spigot-api), що відкриває `Mob#getPathfinder()` і `LivingEntity#attack()`. Дві нові Mythic-механіки: `provoke` пише дедлайн у PDC ентіті, `rangedstance` — автомат станів APPROACH/HOLD/BACKOFF/PROVOKED, що повністю веде рух моба (ванільні рухові AI-цілі в RANGED-шаблонах вимикаються). Механіка `retreat` лишається для ривків усередині метаскілів.

**Tech Stack:** Java 21, Paper API 1.21.11, MythicMobs 5.12.1, JUnit 5 + ArchUnit.

**Spec:** `docs/superpowers/specs/2026-07-07-ranged-stance-behavior-design.md`
**Гілка:** `feat/creature-sequence-kits` (продовжуємо на ній — фіча будується на секвенс-кітах).

## Global Constraints

- `io.lumine..` — ТІЛЬКИ в `me.vangoo.infrastructure.mythic..`; кожна механіка: публічний конструктор `(MythicMechanicLoadEvent)` + власний `getThreadSafetyLevel()` → `SYNC_ONLY` (авто-пінить `MythicComponentContractTest`).
- Жодних static-полів зі станом: спільний стан двох механік — тільки через PDC ентіті (ключ `mysteriesabove:provoked_until`, LONG).
- `mythic-pack/**` — нефільтрований ресурс: без `${}`. `MythicPackInstaller.PACK_FILES` не змінюється.
- Числа спеки (використовувати точно): смуга `min=5`/`max=11`, тік механіки `~onTimer:10`, вікно провокації `seconds=8`, гейт провокації `?playerwithin{d=3}`, швидкості moveTo: APPROACH `1.05`, PROVOKED/BACKOFF `1.15`, точка відходу `7.0` блоків, мілі-досяжність `2.2`, інтервал ударів `1000` мс.
- Коміти: Conventional Commits, англійською, ≤72 симв., трейлер `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`. **Метод коміту: `Set-Content` масив рядків у файл → `git commit -F` → перевірити `git log -1 --format=%B`** (here-strings ламали повідомлення).
- Maven не в PATH; **Task 1 виконується БЕЗ `-o`** (paper-api треба скачати), решта — можна `-o`:
  ```powershell
  $mvn = (Get-ChildItem "C:\Program Files\JetBrains" -Recurse -Filter mvn.cmd | Select-Object -First 1).FullName
  & $mvn clean test
  ```

---

### Task 1: pom.xml → paper-api

**Files:**
- Modify: `pom.xml` (репозиторії ~рядок 105-130; залежність spigot-api ~рядок 156-161)

**Interfaces:**
- Produces: компіляція проти Paper API — Tasks 2 використовують `org.bukkit.entity.Mob#getPathfinder()`, `Mob#lookAt(Entity)`, `LivingEntity#attack(Entity)`.

- [ ] **Step 1: Додати репозиторій PaperMC**

У блок `<repositories>` (після `spigot-repo`) додати:

```xml
        <repository>
            <id>papermc</id>
            <url>https://repo.papermc.io/repository/maven-public/</url>
        </repository>
```

- [ ] **Step 2: Замінити залежність**

```xml
        <dependency>
            <groupId>org.spigotmc</groupId>
            <artifactId>spigot-api</artifactId>
            <version>1.21.11-R0.1-SNAPSHOT</version>
            <scope>provided</scope>
        </dependency>
```
→
```xml
        <dependency>
            <groupId>io.papermc.paper</groupId>
            <artifactId>paper-api</artifactId>
            <version>1.21.11-R0.1-SNAPSHOT</version>
            <scope>provided</scope>
        </dependency>
```
Репозиторій `spigot-repo` лишити (нешкідливий).

- [ ] **Step 3: Повний онлайн-прогін**

```powershell
$mvn = (Get-ChildItem "C:\Program Files\JetBrains" -Recurse -Filter mvn.cmd | Select-Object -First 1).FullName
& $mvn clean test
```
Expected: BUILD SUCCESS, `Tests run: 76, Failures: 0` — весь наявний код компілюється проти paper-api без змін.
Якщо paper-api `1.21.11-R0.1-SNAPSHOT` не резолвиться — перевірити доступні версії
(`https://repo.papermc.io/repository/maven-public/io/papermc/paper/paper-api/maven-metadata.xml`)
і взяти точну 1.21.11-лінію; якщо її немає взагалі — СТОП, ескалювати (не даунгрейдити мінор без згоди).

- [ ] **Step 4: Коміт**

Повідомлення (через `Set-Content` + `git commit -F`, як у Global Constraints):
```
chore(build): switch spigot-api to paper-api for pathfinder access

paper-api is a superset of spigot-api (runtime is Paper already); this
unlocks Mob#getPathfinder and LivingEntity#attack for the ranged stance
state machine.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
```
`git add pom.xml`, коміт, перевірити `git log -1 --format=%B`.

---

### Task 2: Механіки `provoke` + `rangedstance` (+ спільний PDC-ключ)

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/mythic/components/StanceKeys.java`
- Create: `src/main/java/me/vangoo/infrastructure/mythic/components/ProvokeMechanic.java`
- Create: `src/main/java/me/vangoo/infrastructure/mythic/components/RangedStanceMechanic.java`

**Interfaces:**
- Consumes: Paper API з Task 1; наявний `me.vangoo.infrastructure.creatures.SafeLocations.passableNear(Location)`.
- Produces для Task 3 (YAML): механіки `provoke{seconds=8}` (targeted, пише PDC на ціль) та `rangedstance{min=5;max=11}` (targeted, веде рух кастера відносно цілі).

Юніт-тести неможливі (навігація/PDC = живий сервер); контракти пінить наявний `MythicComponentContractTest` (авто-скан пакета), поведінка — in-server (Task 5).

- [ ] **Step 1: `StanceKeys.java`**

```java
package me.vangoo.infrastructure.mythic.components;

import org.bukkit.NamespacedKey;

/** Спільні PDC-ключі станової поведінки: provoke пише, rangedstance читає. */
final class StanceKeys {

    static final NamespacedKey PROVOKED_UNTIL = new NamespacedKey("mysteriesabove", "provoked_until");

    private StanceKeys() {}
}
```

- [ ] **Step 2: `ProvokeMechanic.java`**

```java
package me.vangoo.infrastructure.mythic.components;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.api.skills.ThreadSafetyLevel;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.core.skills.SkillMechanic;
import io.lumine.mythic.core.utils.annotations.MythicMechanic;
import org.bukkit.persistence.PersistentDataType;

@MythicMechanic(author = "mysteries-above", name = "provoke",
        description = "Marks the target mob as provoked (melee retaliation window) via a PDC timestamp")
public class ProvokeMechanic extends SkillMechanic implements ITargetedEntitySkill {

    private final long windowMillis;

    // CustomComponentRegistry інстанціює компонент рефлексією саме через конструктор (load event)
    public ProvokeMechanic(MythicMechanicLoadEvent event) {
        super(event.getContainer().getManager(), event.getConfig().getLine(), event.getConfig());
        this.windowMillis = event.getConfig().getInteger(new String[]{"seconds", "s"}, 8) * 1000L;
    }

    // PDC — Bukkit, тільки main thread
    @Override
    public ThreadSafetyLevel getThreadSafetyLevel() {
        return ThreadSafetyLevel.SYNC_ONLY;
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        target.getBukkitEntity().getPersistentDataContainer().set(StanceKeys.PROVOKED_UNTIL,
                PersistentDataType.LONG, System.currentTimeMillis() + windowMillis);
        return SkillResult.SUCCESS;
    }
}
```

- [ ] **Step 3: `RangedStanceMechanic.java`**

```java
package me.vangoo.infrastructure.mythic.components;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.api.skills.ThreadSafetyLevel;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.core.skills.SkillMechanic;
import io.lumine.mythic.core.utils.annotations.MythicMechanic;
import me.vangoo.infrastructure.creatures.SafeLocations;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Автомат станів RANGED-стійки (рухові AI-цілі шаблону вимкнені — рух веде механіка):
 * ціль далі max → APPROACH (іде в смугу); у смузі min..max → HOLD (стоїть, дивиться, кастує);
 * ближче min → BACKOFF (відходить кроком до валідної точки); спровокований (PDC від provoke)
 * → PROVOKED (іде на ціль і б'є ванільним мілі з атрибута Damage).
 */
@MythicMechanic(author = "mysteries-above", name = "rangedstance",
        description = "Ranged stance state machine: approach, hold and cast, walk away, melee when provoked")
public class RangedStanceMechanic extends SkillMechanic implements ITargetedEntitySkill {

    private static final long ATTACK_INTERVAL_MILLIS = 1000L;
    private static final double MELEE_REACH = 2.2;
    private static final double BACKOFF_DISTANCE = 7.0;

    private final double min;
    private final double max;
    // per-entity час останнього мілі-удару в PROVOKED; мертві записи чистяться в purgeDeadEntries
    private final Map<UUID, Long> lastAttackAt = new ConcurrentHashMap<>();

    // CustomComponentRegistry інстанціює компонент рефлексією саме через конструктор (load event)
    public RangedStanceMechanic(MythicMechanicLoadEvent event) {
        super(event.getContainer().getManager(), event.getConfig().getLine(), event.getConfig());
        this.min = event.getConfig().getDouble(new String[]{"min"}, 5.0);
        this.max = event.getConfig().getDouble(new String[]{"max"}, 11.0);
    }

    // Навігація/атака/PDC — Bukkit, тільки main thread
    @Override
    public ThreadSafetyLevel getThreadSafetyLevel() {
        return ThreadSafetyLevel.SYNC_ONLY;
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        if (!(data.getCaster().getEntity().getBukkitEntity() instanceof Mob mob)
                || !(target.getBukkitEntity() instanceof LivingEntity victim)) {
            return SkillResult.CONDITION_FAILED;
        }

        long now = System.currentTimeMillis();
        if (isProvoked(mob, now)) {
            meleeRetaliate(mob, victim, now);
            return SkillResult.SUCCESS;
        }

        double distance = mob.getLocation().distance(victim.getLocation());
        if (distance > max) {
            mob.getPathfinder().moveTo(victim, 1.05);
        } else if (distance < min) {
            backOff(mob, victim);
        } else {
            hold(mob, victim);
        }
        return SkillResult.SUCCESS;
    }

    private boolean isProvoked(Mob mob, long now) {
        Long until = mob.getPersistentDataContainer().get(StanceKeys.PROVOKED_UNTIL, PersistentDataType.LONG);
        return until != null && now < until;
    }

    // PROVOKED: іде на кривдника; впритул — ванільний удар (swing + knockback + атрибут Damage)
    private void meleeRetaliate(Mob mob, LivingEntity victim, long now) {
        mob.getPathfinder().moveTo(victim, 1.15);
        if (mob.getLocation().distance(victim.getLocation()) > MELEE_REACH) return;
        long last = lastAttackAt.getOrDefault(mob.getUniqueId(), 0L);
        if (now - last < ATTACK_INTERVAL_MILLIS) return;
        mob.attack(victim);
        lastAttackAt.put(mob.getUniqueId(), now);
        purgeDeadEntries();
    }

    private void hold(Mob mob, LivingEntity victim) {
        mob.getPathfinder().stopPathfinding();
        mob.lookAt(victim);
    }

    // BACKOFF: крок до точки ~7 блоків у протилежний бік; нема валідної точки — тримає позицію
    private void backOff(Mob mob, LivingEntity victim) {
        Vector away = mob.getLocation().toVector().subtract(victim.getLocation().toVector());
        away.setY(0);
        if (away.lengthSquared() < 1.0E-4) away = new Vector(1, 0, 0);
        Location dest = SafeLocations.passableNear(
                mob.getLocation().clone().add(away.normalize().multiply(BACKOFF_DISTANCE)));
        var below = dest.clone().subtract(0, 1, 0).getBlock();
        boolean lava = dest.getBlock().getType() == Material.LAVA || below.getType() == Material.LAVA;
        if (!below.getType().isSolid() || lava) {
            hold(mob, victim);
            return;
        }
        mob.getPathfinder().moveTo(dest, 1.15);
    }

    private void purgeDeadEntries() {
        if (ThreadLocalRandom.current().nextInt(50) != 0) return;
        lastAttackAt.keySet().removeIf(id -> Bukkit.getEntity(id) == null);
    }
}
```

- [ ] **Step 4: Контракт- і арх-тести**

```powershell
& $mvn -o test -Dtest="MythicComponentContractTest,ArchitectureTest"
```
Expected: PASS (нові механіки: load-event конструктор + SYNC_ONLY; `io.lumine` в межах).

- [ ] **Step 5: Коміт**

```
feat(creatures): add rangedstance and provoke mechanics

Navigation-driven state machine for ranged mobs: approach into the cast
band, hold and cast, walk away when crowded, and retaliate in melee for
a PDC-tracked window after being hit up close.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
```
`git add` трьох нових файлів, `git commit -F`, перевірити `%B`.

---

### Task 3: RANGED-шаблони пака на rangedstance

**Files:**
- Modify: `src/main/resources/mythic-pack/Mobs/templates.yml`

**Interfaces:**
- Consumes: механіки `rangedstance{min=5;max=11}` і `provoke{seconds=8}` з Task 2.

Рівно 10 RANGED-шаблонів: `MA_Fool_S8`, `MA_Fool_S7`, `MA_Fool_S6`, `MA_Fool_S5`,
`MA_Door_S9`, `MA_Door_S8`, `MA_Door_S7`, `MA_Door_S6`, `MA_Door_S5`, `MA_Whitetower_S5`.
MELEE-шаблони й `MA_Base` НЕ чіпати.

- [ ] **Step 1: У кожен із 10 шаблонів додати AIGoalSelectors**

Одразу після рядка `  Template: MA_Base` кожного з 10 шаблонів вставити:

```yaml
  AIGoalSelectors:
  - 0 clear
  - 1 float
  - 2 lookatplayers
  - 3 randomlookaround
```

(Рухові ванільні цілі вимкнено — рух повністю веде `rangedstance`; `float` — не тонути,
lookatplayers/randomlookaround — жива поведінка в ідлі. `AITargetSelectors` успадковуються
з `MA_Base` без змін.)

- [ ] **Step 2: У кожному з 10 шаблонів замінити retreat-рядок**

Рядок:
```yaml
  - retreat{strength=1.0;vertical=0.35} @NearestPlayer{r=4} ~onTimer:30 ?playerwithin{d=4}
```
замінити на два:
```yaml
  - rangedstance{min=5;max=11} @NearestPlayer{r=16} ~onTimer:10
  - provoke{seconds=8} @self ~onDamaged ?playerwithin{d=3}
```
(Всього в файлі має не лишитися жодного рядка `retreat{strength=1.0...}`; сама механіка
`retreat` й далі використовується метаскілами `MA_Fool_S7_FlameJump` і
`MA_Fool_S9_DangerIntuition` у Skills/fool.yml — їх не чіпати.)

- [ ] **Step 3: Оновити шапку-коментар файлу**

У головному коментарі `templates.yml` замінити речення про стійку
(«Стійка: RANGED-шаблони мають retreat-рядок (кайт при d<4), MELEE — ні.») на:

```yaml
# Стійка: RANGED-шаблони вимикають рухові AI-цілі і додають rangedstance (стоїть-кастує
# в смузі 5..11, відходить кроком, мілі-відповідь по provoke) + provoke ~onDamaged; MELEE — нічого.
```

- [ ] **Step 4: Гарди пака**

```powershell
& $mvn -o test -Dtest="MythicPackKitReferenceTest,MythicPackMobTypeTest"
```
Expected: PASS (kitcast-посилання не мінялись; Type резолвиться).

- [ ] **Step 5: Коміт**

```
feat(creatures): drive ranged stance via navigation state machine

Ranged templates clear vanilla movement goals and replace the velocity
kite hop with rangedstance (hold-and-cast band, walking backoff, melee
retaliation window via provoke on close-range hits).

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
```

---

### Task 4: Документація + повний прогін

**Files:**
- Modify: `.claude/rules/mythic-creatures.md` (пункт «Стійка» в секції «Кіти здібностей (kitcast)»)
- Modify: `CLAUDE.md` (розділ Commands, згадка spigot-api)
- Modify: `.claude/rules/persistence-and-resources.md` (розділ «Залежності збірки»)

- [ ] **Step 1: `.claude/rules/mythic-creatures.md`**

Пункт:
```markdown
- **Стійка:** RANGED-шаблон додає `retreat{...} @NearestPlayer{r=4} ~onTimer:30
  ?playerwithin{d=4}` (кайт); MELEE — нічого. Кастомні механіки руху: `retreat`,
  `blinkbehind` (в `infrastructure.mythic.components`).
```
замінити на:
```markdown
- **Стійка:** RANGED-шаблон вимикає рухові AI-цілі (`AIGoalSelectors: 0 clear` +
  float/lookatplayers/randomlookaround) і додає `rangedstance{min=5;max=11}
  @NearestPlayer{r=16} ~onTimer:10` (стоїть-кастує в смузі, відходить кроком,
  мілі-відповідь у вікні провокації) + `provoke{seconds=8} @self ~onDamaged
  ?playerwithin{d=3}` (удар впритул відкриває вікно; спільний стан — PDC
  `mysteriesabove:provoked_until`). MELEE — нічого. Кастомні механіки руху:
  `rangedstance`, `provoke`, `retreat` (ривок усередині метаскілів), `blinkbehind`
  (в `infrastructure.mythic.components`).
```

- [ ] **Step 2: `CLAUDE.md`**

У розділі Commands речення про залежності:
`` `spigot-api`, `coreprotect`, MythicMobs (`io.lumine:Mythic-Dist`) are `provided` ``
замінити `spigot-api` на `paper-api` (позначає й рантайм: сервер — Paper):
`` `paper-api` (сервер — Paper), `coreprotect`, MythicMobs (`io.lumine:Mythic-Dist`) are `provided` ``

- [ ] **Step 3: `.claude/rules/persistence-and-resources.md`**

У розділі «Залежності збірки» замінити `spigot-api` на `paper-api` у переліку provided-залежностей
(решту речення не чіпати).

- [ ] **Step 4: Повний прогін**

```powershell
& $mvn -o clean test
```
Expected: BUILD SUCCESS, 76/76.

- [ ] **Step 5: Коміт**

```
docs(rules): document navigation-based ranged stance

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
```
`git add` трьох docs-файлів.

---

### Task 5: In-server верифікація (ручна, без коміту)

`& $mvn -o clean package`, задеплоїти jar на Paper-сервер, перевірити:

- [ ] `/mm mobs spawn door_spirit_eater_8` (Drowned): тримає смугу 5–11 — стоїть і кастує (розряд/спалах/підпал), дивиться на гравця.
- [ ] Підійти впритул без ударів → відходить **кроком** (не стрибком), потім знову стоїть.
- [ ] Вдарити мечем 1–2 рази впритул → іде в мілі й б'є (~1 удар/с) ~8 с; перестати бити й відбігти → повертається до дистанційної стійки.
- [ ] Стріляти з лука з 15+ блоків → мілі НЕ вмикається, моб відповідає кастами/APPROACH.
- [ ] `/mm mobs spawn fool_joker_8` (Vindicator): те саме + леза летять із HOLD.
- [ ] `/mm mobs spawn whitetower_void_beholder_5` (Shulker): стоїть і кастує (no-op рух — ок).
- [ ] MELEE-моб (`/mm mobs spawn justiciar_judge_9`): поведінка без змін — іде в контакт.
- [ ] Загнати RANGED-моба в кут (стіна за спиною) → тримає позицію, не сіпається.
- [ ] Консоль: жодних AsyncCatcher/помилок механік при спавні й бою; `/mm reload` — чисто.
- [ ] Ривки метаскілів живі: Fool при контакті робить вогняний відскік (FlameJump) — це очікуваний «стрибок», не баг.

---

## Self-Review (виконано при написанні плану)

- **Spec coverage:** pom-своп (T1), обидві механіки + PDC (T2), 10 шаблонів + AIGoalSelectors + заміна retreat-рядка (T3), три docs-файли (T4), in-server чеклист зі спеки (T5). `retreat` збережено для метаскілів — Т3 явно застерігає.
- **Placeholders:** немає — всі кроки з повним кодом/YAML/командами.
- **Type consistency:** `StanceKeys.PROVOKED_UNTIL` (LONG) пишеться в `ProvokeMechanic` і читається в `RangedStanceMechanic`; параметри `min/max/seconds` у YAML Task 3 = дефолтам механік Task 2.
- **API:** `Mob#getPathfinder().moveTo(LivingEntity|Location, double)` / `stopPathfinding()`, `Mob#lookAt(Entity)`, `LivingEntity#attack(Entity)` — Paper API 1.17+; доступність підтверджує онлайн-збірка Task 1 (компіляція = перевірка).
