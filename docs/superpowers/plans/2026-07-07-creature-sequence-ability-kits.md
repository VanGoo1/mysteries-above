# Creature Sequence Ability Kits Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Міфічні істоти отримують кіти мобових версій бойових здібностей свого шляху, накопичені з послідовностей 9..N (модель прогресії гравців), із ГКД+пріоритетом кастів і стійками MELEE/RANGED.

**Architecture:** Чистий `AbilityCastPlanner` у `domain.creatures` вирішує «що кастувати зараз» (ГКД, пріоритет, кулдауни); кастомна Mythic-механіка `kitcast` диспетчить обраний метаскіл; самі здібності — YAML-метаскіли в `mythic-pack` (баланс істот незалежний від Java-коду гравців). Шаблони `MA_<Pathway>_Common/Apex` замінюються на `MA_<Pathway>_S<seq>`.

**Tech Stack:** Java 21, Spigot API 1.21, MythicMobs 5.12.1 (`io.lumine:Mythic-Dist`, provided), JUnit 5, ArchUnit.

**Spec:** `docs/superpowers/specs/2026-07-07-creature-sequence-ability-kits-design.md`

## Global Constraints

- Коміти: Conventional Commits, тільки англійською, `type(scope): subject` ≤ 72 симв. (`.claude/rules/commit-messages.md`); кожен коміт закінчується `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.
- `io.lumine..` — ТІЛЬКИ в `me.vangoo.infrastructure.mythic..` (ArchUnit `mythicMobsApiIsConfinedToBridgePackage`).
- Кожна Mythic-механіка: публічний конструктор `(MythicMechanicLoadEvent)` + власний `getThreadSafetyLevel()` → `SYNC_ONLY` (пінить `MythicComponentContractTest`, який автоматично сканує пакет `components` — нові класи покриваються без змін тесту).
- `mythic-pack/**` — нефільтрований ресурс: жодних Maven-плейсхолдерів `${}` у YAML.
- `MythicPackInstaller.PACK_FILES` НЕ змінюється — нові скіли/шаблони живуть у наявних файлах пака.
- Maven не в PATH. Запуск тестів через IntelliJ-бандл:
  ```powershell
  $mvn = (Get-ChildItem "C:\Program Files\JetBrains" -Recurse -Filter mvn.cmd | Select-Object -First 1).FullName
  & $mvn -o test
  ```
- Мова: код/ключі/ідентифікатори — англійською; коментарі у файлах пака та Java — українською (як у сусідніх файлах). Нових user-facing рядків цей план не додає.

---

### Task 1: `AbilityCastPlanner` (чистий домен, TDD)

**Files:**
- Create: `src/main/java/me/vangoo/domain/creatures/AbilityCastPlanner.java`
- Test: `src/test/java/me/vangoo/domain/creatures/AbilityCastPlannerTest.java`

**Interfaces:**
- Consumes: нічого (чистий Java).
- Produces: `AbilityCastPlanner(List<KitEntry> kit, long gcd)`; `record KitEntry(String skillName, long cooldown)` (вкладений публічний рекорд); `Optional<String> pickNext(long now)`. Одиниці часу довільні — Task 2 передає мілісекунди.

- [ ] **Step 1: Написати тест, що падає**

```java
package me.vangoo.domain.creatures;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbilityCastPlannerTest {

    // kit: signature (пріоритет 1, кулдаун 300), filler (пріоритет 2, кулдаун 200); ГКД 120
    private static AbilityCastPlanner planner() {
        return new AbilityCastPlanner(List.of(
                new AbilityCastPlanner.KitEntry("signature", 300),
                new AbilityCastPlanner.KitEntry("filler", 200)
        ), 120);
    }

    @Test
    void picksHighestPriorityWhenAllReady() {
        assertEquals(Optional.of("signature"), planner().pickNext(0));
    }

    @Test
    void gcdBlocksAnyCastUntilElapsed() {
        var p = planner();
        p.pickNext(0);
        assertEquals(Optional.empty(), p.pickNext(119));
    }

    @Test
    void fallsBackToLowerPriorityWhileSignatureOnItsOwnCooldown() {
        var p = planner();
        p.pickNext(0); // signature: готова знову з t=300
        assertEquals(Optional.of("filler"), p.pickNext(120));
    }

    @Test
    void returnsEmptyWhenEverythingOnCooldown() {
        var p = planner();
        p.pickNext(0);   // signature до 300
        p.pickNext(120); // filler до 320; ГКД до 240
        assertEquals(Optional.empty(), p.pickNext(240)); // ГКД минув, але обидві на кулдауні
    }

    @Test
    void signatureRegainsPriorityAfterItsCooldown() {
        var p = planner();
        p.pickNext(0);
        p.pickNext(120);
        assertEquals(Optional.of("signature"), p.pickNext(300));
    }

    @Test
    void skillCooldownsAreIndependentPerPlannerInstance() {
        var first = planner();
        first.pickNext(0);
        assertEquals(Optional.of("signature"), planner().pickNext(0));
    }
}
```

- [ ] **Step 2: Запустити тест — має впасти компіляцією**

```powershell
$mvn = (Get-ChildItem "C:\Program Files\JetBrains" -Recurse -Filter mvn.cmd | Select-Object -First 1).FullName
& $mvn -o test -Dtest=AbilityCastPlannerTest
```
Expected: BUILD FAILURE — `cannot find symbol: class AbilityCastPlanner`.

- [ ] **Step 3: Мінімальна реалізація**

```java
package me.vangoo.domain.creatures;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Правило темпу кастів істоти: глобальний кулдаун (ГКД) між будь-якими двома кастами
 * + власний кулдаун кожної здібності. Коли готові кілька — перемагає перша у списку
 * (порядок списку = пріоритет; найнижча послідовність ставиться першою).
 * Одиниці часу довільні (тіки/мс) — now і кулдауни мають бути в одних одиницях.
 */
public class AbilityCastPlanner {

    public record KitEntry(String skillName, long cooldown) {}

    private final List<KitEntry> kit;
    private final long gcd;
    private long gcdReadyAt;
    private final Map<String, Long> skillReadyAt = new HashMap<>();

    public AbilityCastPlanner(List<KitEntry> kit, long gcd) {
        this.kit = List.copyOf(kit);
        this.gcd = gcd;
    }

    public Optional<String> pickNext(long now) {
        if (now < gcdReadyAt) return Optional.empty();
        for (KitEntry entry : kit) {
            if (now >= skillReadyAt.getOrDefault(entry.skillName(), Long.MIN_VALUE)) {
                gcdReadyAt = now + gcd;
                skillReadyAt.put(entry.skillName(), now + entry.cooldown());
                return Optional.of(entry.skillName());
            }
        }
        return Optional.empty();
    }
}
```

- [ ] **Step 4: Запустити тест — має пройти**

```powershell
& $mvn -o test -Dtest=AbilityCastPlannerTest
```
Expected: `Tests run: 6, Failures: 0, Errors: 0` — BUILD SUCCESS.

- [ ] **Step 5: Перевірити чистоту домену (ArchUnit)**

```powershell
& $mvn -o test -Dtest=ArchitectureTest
```
Expected: PASS (`domain.creatures` у чистому скоупі — жодного Bukkit/Mythic у новому класі).

- [ ] **Step 6: Коміт**

```powershell
git add src/main/java/me/vangoo/domain/creatures/AbilityCastPlanner.java src/test/java/me/vangoo/domain/creatures/AbilityCastPlannerTest.java
git commit -m @'
feat(creatures): add AbilityCastPlanner for kit cast pacing

Pure-domain rule for creature ability kits: global cooldown between any
two casts plus per-ability cooldowns, priority to the lowest-sequence
ability. Consumed by the kitcast mythic mechanic in the next commit.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 2: `KitCastMechanic` — диспетчер кітів

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/mythic/components/KitCastMechanic.java`

**Interfaces:**
- Consumes: `AbilityCastPlanner` / `KitEntry` з Task 1; `MythicBukkit.inst().getAPIHelper().castSkill(Entity, String, Entity, Location, Collection<Entity>, Collection<Location>, float)` (сигнатура перевірена в Mythic-Dist 5.12.1).
- Produces: механіка `kitcast{gcd=<тіки>;skills=<Name:кулдаунТіки>,<Name:кулдаунТіки>,...}` для рядків шаблонів у Task 6. Порядок у `skills` = пріоритет (першим — найнижча послідовність).

Юніт-тест неможливий (Bukkit/Mythic у рантаймі); контракт пінить наявний `MythicComponentContractTest` (авто-скан пакета), поведінка — in-server у Task 8.

- [ ] **Step 1: Написати механіку**

```java
package me.vangoo.infrastructure.mythic.components;

import io.lumine.mythic.api.adapters.AbstractEntity;
import io.lumine.mythic.api.skills.ITargetedEntitySkill;
import io.lumine.mythic.api.skills.SkillMetadata;
import io.lumine.mythic.api.skills.SkillResult;
import io.lumine.mythic.api.skills.ThreadSafetyLevel;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicMechanicLoadEvent;
import io.lumine.mythic.core.skills.SkillMechanic;
import io.lumine.mythic.core.utils.annotations.MythicMechanic;
import me.vangoo.domain.creatures.AbilityCastPlanner;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@MythicMechanic(author = "mysteries-above", name = "kitcast",
        description = "Casts the next ready ability of the creature's sequence kit (GCD + priority)")
public class KitCastMechanic extends SkillMechanic implements ITargetedEntitySkill {

    private static final long MILLIS_PER_TICK = 50L;

    private final List<AbilityCastPlanner.KitEntry> kit;
    private final long gcdMillis;
    // per-entity стан темпу кастів; мертві записи вичищаються ліниво в purgeDeadEntries
    private final Map<UUID, AbilityCastPlanner> planners = new ConcurrentHashMap<>();

    // CustomComponentRegistry інстанціює компонент рефлексією саме через конструктор (load event)
    public KitCastMechanic(MythicMechanicLoadEvent event) {
        super(event.getContainer().getManager(), event.getConfig().getLine(), event.getConfig());
        this.gcdMillis = event.getConfig().getInteger(new String[]{"gcd", "g"}, 120) * MILLIS_PER_TICK;
        this.kit = parseKit(event.getConfig().getString(new String[]{"skills", "s"}, ""));
    }

    // Формат: skills=Name:кулдаунТіки,Name:кулдаунТіки — порядок = пріоритет
    private static List<AbilityCastPlanner.KitEntry> parseKit(String raw) {
        List<AbilityCastPlanner.KitEntry> entries = new ArrayList<>();
        for (String part : raw.split(",")) {
            String[] pair = part.trim().split(":");
            if (pair.length != 2) continue;
            entries.add(new AbilityCastPlanner.KitEntry(pair[0], Long.parseLong(pair[1]) * MILLIS_PER_TICK));
        }
        return List.copyOf(entries);
    }

    // Виконує метаскіли, що чіпають Bukkit — тільки main thread
    @Override
    public ThreadSafetyLevel getThreadSafetyLevel() {
        return ThreadSafetyLevel.SYNC_ONLY;
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        if (kit.isEmpty()) return SkillResult.INVALID_CONFIG;
        Entity caster = data.getCaster().getEntity().getBukkitEntity();
        AbilityCastPlanner planner = planners.computeIfAbsent(caster.getUniqueId(),
                id -> new AbilityCastPlanner(kit, gcdMillis));
        Optional<String> next = planner.pickNext(System.currentTimeMillis());
        if (next.isEmpty()) return SkillResult.CONDITION_FAILED;
        MythicBukkit.inst().getAPIHelper().castSkill(caster, next.get(), target.getBukkitEntity(),
                caster.getLocation(), List.of(target.getBukkitEntity()), List.of(), 1.0f);
        purgeDeadEntries();
        return SkillResult.SUCCESS;
    }

    // Зрідка вичищаємо записи істот, яких уже немає (SYNC_ONLY → Bukkit.getEntity безпечний)
    private void purgeDeadEntries() {
        if (ThreadLocalRandom.current().nextInt(50) != 0) return;
        planners.keySet().removeIf(id -> Bukkit.getEntity(id) == null);
    }
}
```

- [ ] **Step 2: Запустити контракт- і арх-тести**

```powershell
& $mvn -o test -Dtest="MythicComponentContractTest,ArchitectureTest"
```
Expected: PASS — конструктор із load-event знайдено, `getThreadSafetyLevel` оголошено, `io.lumine` лишився в `infrastructure.mythic`, залежність `infrastructure.mythic → domain.creatures` дозволена (dependencies point inward).

- [ ] **Step 3: Коміт**

```powershell
git add src/main/java/me/vangoo/infrastructure/mythic/components/KitCastMechanic.java
git commit -m @'
feat(creatures): add kitcast mythic mechanic dispatching sequence kits

One timer line per mob template: parses the kit (skill:cooldown pairs in
priority order), asks the pure AbilityCastPlanner what is ready, and
casts that metaskill via the Mythic API at the current target.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 3: `RetreatMechanic` + `BlinkBehindMechanic`

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/mythic/components/RetreatMechanic.java`
- Create: `src/main/java/me/vangoo/infrastructure/mythic/components/BlinkBehindMechanic.java`

**Interfaces:**
- Consumes: `me.vangoo.infrastructure.creatures.SafeLocations.passableNear(Location)` (наявний util).
- Produces: механіки `retreat{strength=<double>;vertical=<double>}` (кайт-відскік кастера ВІД цілі) та `blinkbehind{distance=<double>}` (телепорт кастера за спину цілі) для YAML у Tasks 5–6.

- [ ] **Step 1: Написати `RetreatMechanic`**

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
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

@MythicMechanic(author = "mysteries-above", name = "retreat",
        description = "Pushes the caster away from the target (kite hop for ranged stances)")
public class RetreatMechanic extends SkillMechanic implements ITargetedEntitySkill {

    private final double strength;
    private final double vertical;

    // CustomComponentRegistry інстанціює компонент рефлексією саме через конструктор (load event)
    public RetreatMechanic(MythicMechanicLoadEvent event) {
        super(event.getContainer().getManager(), event.getConfig().getLine(), event.getConfig());
        this.strength = event.getConfig().getDouble(new String[]{"strength", "str"}, 1.0);
        this.vertical = event.getConfig().getDouble(new String[]{"vertical", "vy"}, 0.35);
    }

    // setVelocity — Bukkit, тільки main thread
    @Override
    public ThreadSafetyLevel getThreadSafetyLevel() {
        return ThreadSafetyLevel.SYNC_ONLY;
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        Entity caster = data.getCaster().getEntity().getBukkitEntity();
        Vector away = caster.getLocation().toVector()
                .subtract(target.getBukkitEntity().getLocation().toVector());
        away.setY(0);
        if (away.lengthSquared() < 1.0E-4) {
            away = new Vector(1, 0, 0); // ціль у тій самій точці — довільний горизонтальний напрямок
        }
        caster.setVelocity(away.normalize().multiply(strength).setY(vertical));
        return SkillResult.SUCCESS;
    }
}
```

- [ ] **Step 2: Написати `BlinkBehindMechanic`**

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
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

@MythicMechanic(author = "mysteries-above", name = "blinkbehind",
        description = "Teleports the caster behind the target when the spot is passable")
public class BlinkBehindMechanic extends SkillMechanic implements ITargetedEntitySkill {

    private final double distance;

    // CustomComponentRegistry інстанціює компонент рефлексією саме через конструктор (load event)
    public BlinkBehindMechanic(MythicMechanicLoadEvent event) {
        super(event.getContainer().getManager(), event.getConfig().getLine(), event.getConfig());
        this.distance = event.getConfig().getDouble(new String[]{"distance", "d"}, 2.0);
    }

    // teleport — Bukkit, тільки main thread
    @Override
    public ThreadSafetyLevel getThreadSafetyLevel() {
        return ThreadSafetyLevel.SYNC_ONLY;
    }

    @Override
    public SkillResult castAtEntity(SkillMetadata data, AbstractEntity target) {
        Entity caster = data.getCaster().getEntity().getBukkitEntity();
        Entity victim = target.getBukkitEntity();
        Vector facing = victim.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() < 1.0E-4) {
            facing = new Vector(1, 0, 0); // ціль дивиться вертикально — довільний напрямок
        }
        Location behind = victim.getLocation().clone()
                .subtract(facing.normalize().multiply(distance));
        Location dest = SafeLocations.passableNear(behind);
        if (!dest.clone().subtract(0, 1, 0).getBlock().getType().isSolid()) {
            return SkillResult.CONDITION_FAILED; // немає опори — блінк скасовано, ГКД уже витрачено
        }
        dest.setDirection(victim.getLocation().toVector().subtract(dest.toVector())); // обличчям до цілі
        caster.teleport(dest);
        return SkillResult.SUCCESS;
    }
}
```

- [ ] **Step 3: Контракт- і арх-тести**

```powershell
& $mvn -o test -Dtest="MythicComponentContractTest,ArchitectureTest"
```
Expected: PASS.

- [ ] **Step 4: Коміт**

```powershell
git add src/main/java/me/vangoo/infrastructure/mythic/components/RetreatMechanic.java src/main/java/me/vangoo/infrastructure/mythic/components/BlinkBehindMechanic.java
git commit -m @'
feat(creatures): add retreat and blinkbehind mythic mechanics

retreat gives ranged-stance mobs a kite hop away from the target;
blinkbehind teleports the Door apex behind its victim with the same
SafeLocations passability check used by scatter.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 4: Гард `MythicPackKitReferenceTest`

**Files:**
- Test: `src/test/java/me/vangoo/infrastructure/mythic/MythicPackKitReferenceTest.java`

**Interfaces:**
- Consumes: сирі YAML пака (`src/main/resources/mythic-pack/Mobs/*.yml`, `Skills/*.yml`).
- Produces: гард для Tasks 5–6 — кожне ім'я скіла у `kitcast{skills=...}`, `randomskill{skills=...}`, `skill{s=...}`, `onHitSkill=...` мусить існувати як топ-рівневий метаскіл у `Skills/*.yml`.

- [ ] **Step 1: Написати тест**

```java
package me.vangoo.infrastructure.mythic;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * kitcast{skills=...}, randomskill{skills=...}, skill{s=...} та onHitSkill=... посилаються
 * на метаскіли за іменем-рядком; одрук виявляється лише в рантаймі ("Skill not found" у
 * консолі при касті). Тест пінить: кожне посилання з пака існує у Skills/*.yml.
 */
class MythicPackKitReferenceTest {

    private static final File PACK_DIR = new File("src/main/resources/mythic-pack");
    private static final List<Pattern> LIST_REFERENCES = List.of(
            Pattern.compile("kitcast\\{[^}]*?skills=([^;}]+)"),
            Pattern.compile("randomskill\\{[^}]*?skills=([^;}]+)"));
    private static final List<Pattern> SINGLE_REFERENCES = List.of(
            Pattern.compile("skill\\{s=([A-Za-z0-9_]+)"),
            Pattern.compile("onHitSkill=([A-Za-z0-9_]+)"));

    @Test
    void everyReferencedMetaskillExists() throws IOException {
        Set<String> defined = definedMetaskills();
        assertFalse(defined.isEmpty(), "No metaskills found in Skills/*.yml — path is broken");

        List<String> missing = new ArrayList<>();
        for (File yml : packYmlFiles()) {
            String content = Files.readString(yml.toPath());
            for (String ref : referencedSkills(content)) {
                if (!defined.contains(ref)) {
                    missing.add(yml.getName() + " -> " + ref);
                }
            }
        }
        assertTrue(missing.isEmpty(), "References to missing metaskills: " + missing);
    }

    private Set<String> referencedSkills(String content) {
        Set<String> refs = new HashSet<>();
        for (Pattern pattern : SINGLE_REFERENCES) {
            Matcher m = pattern.matcher(content);
            while (m.find()) refs.add(m.group(1));
        }
        for (Pattern pattern : LIST_REFERENCES) {
            Matcher m = pattern.matcher(content);
            while (m.find()) {
                for (String entry : m.group(1).split(",")) {
                    refs.add(entry.trim().split(":")[0]);
                }
            }
        }
        return refs;
    }

    private Set<String> definedMetaskills() {
        Set<String> defined = new HashSet<>();
        File[] files = new File(PACK_DIR, "Skills").listFiles((dir, name) -> name.endsWith(".yml"));
        assertNotNull(files, "Skills dir missing");
        for (File file : files) {
            defined.addAll(YamlConfiguration.loadConfiguration(file).getKeys(false));
        }
        return defined;
    }

    private List<File> packYmlFiles() {
        List<File> all = new ArrayList<>();
        for (String sub : List.of("Mobs", "Skills")) {
            File[] files = new File(PACK_DIR, sub).listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null) all.addAll(Arrays.asList(files));
        }
        return all;
    }
}
```

- [ ] **Step 2: Запустити — має пройти вже на поточному паку**

```powershell
& $mvn -o test -Dtest=MythicPackKitReferenceTest
```
Expected: PASS (усі нинішні `skill{s=...}` і `randomskill` посилання резолвляться). Якщо FAIL — у поточному паку реальний битий референс: полагодити пак, не тест.

- [ ] **Step 3: Коміт**

```powershell
git add src/test/java/me/vangoo/infrastructure/mythic/MythicPackKitReferenceTest.java
git commit -m @'
test(creatures): guard mythic pack metaskill references

Typos in kitcast/skill/onHitSkill names only surface in-server at cast
time; fail the build instead.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 5: Метаскіли кітів у `Skills/*.yml` (старі поки лишаються)

**Files:**
- Modify: `src/main/resources/mythic-pack/Skills/fool.yml`
- Modify: `src/main/resources/mythic-pack/Skills/visionary.yml`
- Modify: `src/main/resources/mythic-pack/Skills/door.yml`
- Modify: `src/main/resources/mythic-pack/Skills/justiciar.yml`
- Modify: `src/main/resources/mythic-pack/Skills/whitetower.yml`
- Modify: `src/main/resources/mythic-pack/Skills/error.yml`

**Interfaces:**
- Consumes: механіки `drainsanity`, `scatter`, `isbeyonder` (наявні), `retreat`, `blinkbehind` (Task 3).
- Produces: метаскіли з точними іменами нижче — Task 6 посилається на них у `kitcast{...}`/`skill{s=...}` рядках шаблонів. Старі архетипні метаскіли НЕ видаляти в цьому таску (шаблони ще посилаються на них — гард Task 4 має лишатися зеленим у кожному коміті); видалення — у Task 6.

Конвенція: наступальні метаскіли (викликаються через kitcast) — БЕЗ `Cooldown:` (кулдаун задає kitcast-рядок, одне джерело правди); реактивні (тригеряться з шаблону) — З `Cooldown:` у секундах.

- [ ] **Step 1: Дописати в `Skills/fool.yml` (у кінець файлу, старі скіли не чіпати)**

```yaml
# ── Кіт S9..S5: мобові версії гравцевих здібностей Fool ──
# Реактивна (поза ГКД): ухилення при отриманні урону (DangerIntuition, S9)
MA_Fool_S9_DangerIntuition:
  Cooldown: 10
  Skills:
  - retreat{strength=0.8;vertical=0.3}
  - potion{type=SPEED;duration=40;level=0;particles=false} @self
  - effect:particles{p=cloud;amount=6;hS=0.3;vS=0.3;y=0.5} @self

# Наступальна: снаряд-лезо (PaperCutter, S8)
MA_Fool_S8_PaperCutter:
  Skills:
  - projectile{onHitSkill=MA_Fool_S8_PaperCutter_Hit;velocity=14;interval=1;hitRadius=1;verticalHitRadius=1;maxRange=16}
  - effect:sound{s=entity.snowball.throw;v=1;p=1.4} @self
MA_Fool_S8_PaperCutter_Hit:
  Skills:
  - damage{amount=3}
  - effect:particles{p=sweep_attack;amount=2;hS=0.2;vS=0.2;y=1}

# Наступальна: повітряний болт із відкиданням (AirBullet, S7)
MA_Fool_S7_AirBullet:
  Skills:
  - projectile{onHitSkill=MA_Fool_S7_AirBullet_Hit;velocity=18;interval=1;hitRadius=1.2;verticalHitRadius=1.2;maxRange=16}
  - effect:sound{s=entity.breeze.shoot;v=1;p=1} @self
MA_Fool_S7_AirBullet_Hit:
  Skills:
  - damage{amount=4}
  - throw{velocity=7;velocityY=3}
  - effect:particles{p=cloud;amount=10;hS=0.4;vS=0.4;y=1;speed=0.05}

# Реактивна (поза ГКД): вогняний відскік, коли ціль впритул (FlameJump, S7)
MA_Fool_S7_FlameJump:
  Cooldown: 8
  Skills:
  - effect:particles{p=flame;amount=20;hS=0.4;vS=0.6;y=0.5;speed=0.02} @self
  - retreat{strength=1.4;vertical=0.5}
  - effect:sound{s=entity.blaze.shoot;v=1;p=1.2} @self

# Наступальна: нитки маріонетки — pull+slowness (Marionette Control, S5; поглинає MA_Fool_Pull)
MA_Fool_S5_Marionette:
  TargetConditions:
  - isbeyonder true
  Skills:
  - pull{velocity=1.2}
  - potion{type=SLOWNESS;duration=40;level=1;particles=false}
  - drainsanity{amount=1} 0.2
  - effect:particles{p=witch;amount=10;hS=0.4;vS=0.6;y=1}

# Реактивна (поза ГКД): ляльки при HP<35% (поглинає MA_Fool_Summon; 2 шт. — рівень S5/апекс)
MA_Fool_S5_Puppets:
  Cooldown: 30
  Skills:
  - summon{mob=MA_FoolPuppet;amount=2;radius=1}
```

- [ ] **Step 2: Дописати в `Skills/visionary.yml`**

```yaml
# ── Кіт S9..S5: мобові версії гравцевих здібностей Visionary ──
# Наступальна: мітка-сканування — Glowing (ScanGaze, S9)
MA_Visionary_S9_ScanGaze:
  TargetConditions:
  - isbeyonder true
  Skills:
  - potion{type=GLOWING;duration=60;level=0;particles=false}
  - effect:particles{p=end_rod;amount=6;hS=0.2;vS=0.4;y=1;speed=0.01}

# Реактивна (поза ГКД): відчуття небезпеки — резист при уроні (DangerSense, S8)
MA_Visionary_S8_DangerSense:
  Cooldown: 10
  Skills:
  - potion{type=RESISTANCE;duration=40;level=0;particles=false} @self
  - effect:particles{p=squid_ink;amount=4;hS=0.3;vS=0.3;y=1} @self

# Реактивна на зближення (поза ГКД): ментальний вибух (SurgeOfInsanity, S7;
# поглинає MA_Visionary_Burst — тригериться з шаблону при d<4)
MA_Visionary_S7_Surge:
  Cooldown: 6
  TargetConditions:
  - isbeyonder true
  Skills:
  - potion{type=NAUSEA;duration=70;level=1;particles=false}
  - potion{type=BLINDNESS;duration=30;level=1;particles=false}
  - potion{type=DARKNESS;duration=60;level=1;particles=false}
  - drainsanity{amount=1} 0.2
  - effect:particles{p=squid_ink;amount=8;hS=0.3;vS=0.5;y=1;speed=0.01}

# Наступальна: бойовий гіпноз — «ступор» (BattleHypnotism, S6)
MA_Visionary_S6_Hypnosis:
  TargetConditions:
  - isbeyonder true
  Skills:
  - potion{type=SLOWNESS;duration=30;level=3;particles=false}
  - potion{type=WEAKNESS;duration=60;level=1;particles=false}
  - drainsanity{amount=1} 0.15
  - effect:particles{p=witch;amount=12;hS=0.3;vS=0.5;y=1.6}
  - effect:sound{s=block.amethyst_block.chime;v=1;p=0.6}
```

- [ ] **Step 3: Дописати в `Skills/door.yml`**

```yaml
# ── Кіт S9..S5: мобові версії гравцевих здібностей Door ──
# Наступальна: печать дверей (DoorOpening, S9; поглинає MA_Door_Seal)
MA_Door_S9_Seal:
  TargetConditions:
  - isbeyonder true
  Skills:
  - potion{type=SLOWNESS;duration=80;level=1;particles=false}
  - potion{type=DARKNESS;duration=60;level=1;particles=false}
  - effect:particles{p=reverse_portal;amount=40;hS=0.6;vS=1.0;y=1;speed=0.05}

# Наступальна: сліпучий спалах (Flash, S8)
MA_Door_S8_Flash:
  TargetConditions:
  - isbeyonder true
  Skills:
  - potion{type=BLINDNESS;duration=35;level=1;particles=false}
  - effect:particles{p=flash;amount=1;y=1}
  - effect:sound{s=entity.firework_rocket.blast;v=1;p=1.4}

# Наступальна: підпал (Burning, S8)
MA_Door_S8_Burning:
  Skills:
  - ignite{ticks=60}
  - effect:particles{p=flame;amount=12;hS=0.3;vS=0.5;y=1;speed=0.02}

# Наступальна: електричний розряд (ElectricShock, S8)
MA_Door_S8_Shock:
  Skills:
  - damage{amount=3}
  - potion{type=SLOWNESS;duration=20;level=2;particles=false}
  - effect:particles{p=electric_spark;amount=14;hS=0.3;vS=0.6;y=1;speed=0.1}
  - effect:sound{s=entity.lightning_bolt.impact;v=0.6;p=1.6}

# Реактивна (поза ГКД): трюк утечі — телепорт убік при уроні (EscapeTrick, S8; scatter на себе)
MA_Door_S8_EscapeTrick:
  Cooldown: 15
  Skills:
  - effect:particles{p=portal;amount=30;hS=0.4;vS=0.8;y=1;speed=0.1} @self
  - scatter{radius=7} @self
  - effect:sound{s=entity.enderman.teleport;v=1;p=1} @self

# Наступальна: блінк за спину + удар (Blink, S5)
MA_Door_S5_Blink:
  Skills:
  - effect:particles{p=portal;amount=20;hS=0.4;vS=0.8;y=1} @self
  - blinkbehind{distance=2}
  - damage{amount=5}
  - effect:sound{s=entity.enderman.teleport;v=1;p=0.8} @self
```

- [ ] **Step 4: Дописати в `Skills/justiciar.yml`**

```yaml
# ── Кіт S9..S5: мобові версії гравцевих здібностей Justiciar ──
# Наступальна: наказ влади (Authority, S9)
MA_Justiciar_S9_Authority:
  TargetConditions:
  - isbeyonder true
  Skills:
  - potion{type=SLOWNESS;duration=40;level=1;particles=false}
  - potion{type=WEAKNESS;duration=40;level=1;particles=false}
  - effect:sound{s=entity.wither.ambient;v=0.6;p=1.6}

# Наступальна: погляд арбітра — мітка (ArbitersGaze, S9)
MA_Justiciar_S9_ArbitersGaze:
  TargetConditions:
  - isbeyonder true
  Skills:
  - potion{type=GLOWING;duration=80;level=0;particles=false}
  - effect:particles{p=wax_off;amount=8;hS=0.3;vS=0.5;y=1.6}

# Пасивна аура-зона (поза ГКД): юрисдикція (AreaOfJurisdiction, S8; поглинає MA_Justiciar_Aura)
MA_Justiciar_S8_Jurisdiction:
  TargetConditions:
  - isbeyonder true
  Skills:
  - potion{type=SLOWNESS;duration=50;level=1;particles=false}
  - potion{type=MINING_FATIGUE;duration=50;level=1;particles=false}
  - potion{type=WEAKNESS;duration=50;level=1;particles=false}

# Наступальна: батіг болю + псі-пробій (WhipOfPain + PsychicPiercing, S7)
MA_Justiciar_S7_Whip:
  Skills:
  - damage{amount=5}
  - drainsanity{amount=1} 0.15
  - effect:particles{p=crit;amount=12;hS=0.3;vS=0.5;y=1;speed=0.2}
  - effect:sound{s=entity.ravager.attack;v=0.8;p=1.4}

# Наступальна: тавро стримування — корінь (BrandOfRestraint, S7)
MA_Justiciar_S7_Brand:
  TargetConditions:
  - isbeyonder true
  Skills:
  - potion{type=SLOWNESS;duration=30;level=4;particles=false}
  - effect:particles{p=soul_fire_flame;amount=16;hS=0.3;vS=0.2;y=0.2;speed=0.01}

# Наступальна: вирок — бурст (Verdict, S6)
MA_Justiciar_S6_Verdict:
  Skills:
  - damage{amount=7}
  - effect:particles{p=wax_off;amount=24;hS=0.5;vS=0.8;y=1;speed=0.05}
  - effect:sound{s=block.anvil.land;v=0.8;p=1.2}

# Наступальна: кара (Punishment, S5)
MA_Justiciar_S5_Punishment:
  Skills:
  - damage{amount=10}
  - potion{type=SLOWNESS;duration=30;level=1;particles=false}
  - effect:particles{p=explosion;amount=2;y=1}
  - effect:sound{s=entity.lightning_bolt.thunder;v=0.7;p=1.4}
```

- [ ] **Step 5: Дописати в `Skills/whitetower.yml`**

```yaml
# ── Кіт S9..S5: мобові версії гравцевих здібностей WhiteTower ──
# S9–S7 гравцеві здібності не бойові (пасиви виражені у статах моба) — кіт стартує з S6.
# Наступальна: аналіз — викриття (Analysis, S6; поглинає MA_Whitetower_Reveal)
MA_Whitetower_S6_Analysis:
  TargetConditions:
  - isbeyonder true
  Skills:
  - potion{type=GLOWING;duration=40;level=1;particles=false}
  - potion{type=BLINDNESS;duration=25;level=1;particles=false} 0.25
  - effect:particles{p=end_rod;amount=8;hS=0.3;vS=0.5;y=1;speed=0.01}

# Наступальна: світлові снаряди (Spellcasting, S5; замінює негейчений snowball —
# закриває gap із mythic-creatures.md)
MA_Whitetower_S5_Spellcast:
  TargetConditions:
  - isbeyonder true
  Skills:
  - projectile{onHitSkill=MA_Whitetower_S5_Spellcast_Hit;velocity=16;interval=1;hitRadius=1;verticalHitRadius=1;maxRange=18}
  - effect:sound{s=entity.evoker.cast_spell;v=1;p=1.2} @self
MA_Whitetower_S5_Spellcast_Hit:
  Skills:
  - damage{amount=5}
  - effect:particles{p=end_rod;amount=16;hS=0.3;vS=0.3;y=1;speed=0.05}

# Реактивна (поза ГКД): дзеркальне прокляття — відбиття частки урону (MirrorCurse, S5)
MA_Whitetower_S5_MirrorCurse:
  Cooldown: 3
  Skills:
  - damage{amount=2;ignoreArmor=true}
  - effect:particles{p=enchant;amount=10;hS=0.3;vS=0.5;y=1}
```

- [ ] **Step 6: Дописати в `Skills/error.yml`**

```yaml
# ── Кіт S9..S7: мобові версії гравцевих здібностей Error ──
# (гравцеві здібності S7–S5 ще не існують — нижчі шаблони підсилюють цей самий кіт)
# Наступальна: крадіжка тіні — зміщення жертви (ShadowTheft, S9; поглинає телепорт MA_Error_Chaos)
MA_Error_S9_ShadowTheft:
  TargetConditions:
  - isbeyonder true
  Skills:
  - scatter{radius=4}
  - drainsanity{amount=1} 0.1
  - effect:particles{p=smoke;amount=10;hS=0.3;vS=0.5;y=1;speed=0.02}
  - effect:sound{s=entity.enderman.teleport;v=0.8;p=0.7}

# Наступальна: шарм шахрая — обман (SwindlerCharm, S8; поглинає дебафи MA_Error_Chaos)
MA_Error_S8_SwindlerCharm:
  TargetConditions:
  - isbeyonder true
  Skills:
  - potion{type=NAUSEA;duration=80;level=0;particles=false}
  - potion{type=WEAKNESS;duration=80;level=1;particles=false}
  - drainsanity{amount=1} 0.1
  - effect:particles{p=smoke;amount=8;hS=0.4;vS=0.5;y=1.6}
```

- [ ] **Step 7: Запустити гарди пака**

```powershell
& $mvn -o test -Dtest="MythicPackKitReferenceTest,MythicPackMobTypeTest"
```
Expected: PASS (нові метаскіли додано, старі не чіпалися, шаблони поки старі).

- [ ] **Step 8: Коміт**

```powershell
git add src/main/resources/mythic-pack/Skills
git commit -m @'
feat(creatures): add sequence kit metaskills for all pathways

Mob-side versions of the combat subset of player abilities, named
MA_<Pathway>_S<seq>_<Ability>. Old archetype metaskills stay until the
templates switch over in the next commit.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 6: Шаблони `MA_<Pathway>_S<seq>`, перемикання мобів, видалення архетипів

**Files:**
- Modify: `src/main/resources/mythic-pack/Mobs/templates.yml` (повна заміна вмісту)
- Modify: `src/main/resources/mythic-pack/Mobs/fool.yml`, `door.yml`, `error.yml`, `justiciar.yml`, `visionary.yml`, `whitetower.yml` (тільки рядки `Template:`)
- Modify: `src/main/resources/mythic-pack/Skills/*.yml` × 6 (видалити старі архетипні метаскіли)

**Interfaces:**
- Consumes: механіку `kitcast` (Task 2), `retreat` (Task 3), метаскіли Task 5.
- Produces: шаблони `MA_Fool_S9..S5`, `MA_Visionary_S9..S5`, `MA_Door_S9..S5`, `MA_Justiciar_S9..S5`, `MA_Whitetower_S9..S5`, `MA_Error_S9..S7`. Join-ключ `creatures.yml` ↔ пак не змінюється.

- [ ] **Step 1: Переписати `Mobs/templates.yml` повністю**

```yaml
# Шаблони. MA_Base — спільні опції; MA_<Pathway>_S<seq> — кіт здібностей, накопичений
# з послідовностей 9..N (модель прогресії гравців). Наступальні касти йдуть через kitcast
# (ГКД ~6с, у S5 ~4с; пріоритет — здібності нижчої послідовності, перші у списку);
# реактивні/пасивні — окремі тригерні рядки поза ГКД. Стійка: RANGED-шаблони мають
# retreat-рядок (кайт при d<4), MELEE — ні. Числа тут — баланс істот, незалежний від
# гравцевих здібностей.
MA_Base:
  # Плейсхолдер: шаблон ніколи не спавниться, але MythicMobs вантажить кожен запис Mobs/*.yml
  # як моба і без Type кидає "No Type specified". Конкретні моби перекривають Type власним.
  Type: ZOMBIE
  AITargetSelectors:
  - 0 clear
  - 1 attacker
  - 2 players
  Options:
    PreventOtherDrops: true
    MaxCombatDistance: 32
    Despawn: true

# ── Fool (RANGED з S8) ──
MA_Fool_S9:
  Template: MA_Base
  Skills:
  - skill{s=MA_Fool_S9_DangerIntuition} @trigger ~onDamaged
  - effect:particles{p=witch;amount=4;hS=0.3;vS=0.5;y=1} @self ~onTimer:20

MA_Fool_S8:
  Template: MA_Base
  Skills:
  - kitcast{gcd=120;skills=MA_Fool_S8_PaperCutter:120} @NearestPlayer{r=12} ~onTimer:20
  - skill{s=MA_Fool_S9_DangerIntuition} @trigger ~onDamaged
  - potion{type=SPEED;duration=45;level=0;particles=false} @self ~onTimer:40
  - retreat{strength=1.0;vertical=0.35} @NearestPlayer{r=4} ~onTimer:30 ?playerwithin{d=4}
  - effect:particles{p=witch;amount=4;hS=0.3;vS=0.5;y=1} @self ~onTimer:20

MA_Fool_S7:
  Template: MA_Base
  Skills:
  - kitcast{gcd=120;skills=MA_Fool_S7_AirBullet:160,MA_Fool_S8_PaperCutter:100} @NearestPlayer{r=12} ~onTimer:20
  - skill{s=MA_Fool_S7_FlameJump} @NearestPlayer{r=3} ~onTimer:20 ?playerwithin{d=3}
  - skill{s=MA_Fool_S9_DangerIntuition} @trigger ~onDamaged
  - potion{type=SPEED;duration=45;level=0;particles=false} @self ~onTimer:40
  - retreat{strength=1.0;vertical=0.35} @NearestPlayer{r=4} ~onTimer:30 ?playerwithin{d=4}
  - effect:particles{p=witch;amount=4;hS=0.3;vS=0.5;y=1} @self ~onTimer:20

# S6: гравцеві здібності S6 не бойові для моба — той самий кіт, коротші кулдауни
MA_Fool_S6:
  Template: MA_Base
  Skills:
  - kitcast{gcd=120;skills=MA_Fool_S7_AirBullet:140,MA_Fool_S8_PaperCutter:90} @NearestPlayer{r=12} ~onTimer:20
  - skill{s=MA_Fool_S7_FlameJump} @NearestPlayer{r=3} ~onTimer:20 ?playerwithin{d=3}
  - skill{s=MA_Fool_S9_DangerIntuition} @trigger ~onDamaged
  - potion{type=SPEED;duration=45;level=0;particles=false} @self ~onTimer:40
  - retreat{strength=1.0;vertical=0.35} @NearestPlayer{r=4} ~onTimer:30 ?playerwithin{d=4}
  - effect:particles{p=witch;amount=4;hS=0.3;vS=0.5;y=1} @self ~onTimer:20

MA_Fool_S5:
  Template: MA_Base
  Skills:
  - kitcast{gcd=80;skills=MA_Fool_S5_Marionette:200,MA_Fool_S7_AirBullet:140,MA_Fool_S8_PaperCutter:80} @NearestPlayer{r=12} ~onTimer:20
  - skill{s=MA_Fool_S5_Puppets} @self ~onDamaged ?healthpercent{h=<35}
  - skill{s=MA_Fool_S7_FlameJump} @NearestPlayer{r=3} ~onTimer:20 ?playerwithin{d=3}
  - skill{s=MA_Fool_S9_DangerIntuition} @trigger ~onDamaged
  - potion{type=SPEED;duration=45;level=0;particles=false} @self ~onTimer:40
  - retreat{strength=1.0;vertical=0.35} @NearestPlayer{r=4} ~onTimer:30 ?playerwithin{d=4}
  - effect:particles{p=witch;amount=4;hS=0.3;vS=0.5;y=1} @self ~onTimer:20

# ── Visionary (MELEE, засідник) ──
MA_Visionary_S9:
  Template: MA_Base
  Skills:
  - kitcast{gcd=120;skills=MA_Visionary_S9_ScanGaze:200} @NearestPlayer{r=12} ~onTimer:20

MA_Visionary_S8:
  Template: MA_Base
  Skills:
  - kitcast{gcd=120;skills=MA_Visionary_S9_ScanGaze:200} @NearestPlayer{r=12} ~onTimer:20
  - skill{s=MA_Visionary_S8_DangerSense} @self ~onDamaged

MA_Visionary_S7:
  Template: MA_Base
  Skills:
  - kitcast{gcd=120;skills=MA_Visionary_S9_ScanGaze:200} @NearestPlayer{r=12} ~onTimer:20
  - skill{s=MA_Visionary_S7_Surge} @PlayersInRadius{r=4} ~onTimer:20 ?playerwithin{d=4}
  - skill{s=MA_Visionary_S8_DangerSense} @self ~onDamaged

MA_Visionary_S6:
  Template: MA_Base
  Skills:
  - kitcast{gcd=120;skills=MA_Visionary_S6_Hypnosis:180,MA_Visionary_S9_ScanGaze:200} @NearestPlayer{r=12} ~onTimer:20
  - skill{s=MA_Visionary_S7_Surge} @PlayersInRadius{r=4} ~onTimer:20 ?playerwithin{d=4}
  - potion{type=INVISIBILITY;duration=45;particles=false} @self ~onTimer:20 ?!playerwithin{d=4}
  - skill{s=MA_Visionary_S8_DangerSense} @self ~onDamaged

MA_Visionary_S5:
  Template: MA_Base
  Skills:
  - kitcast{gcd=80;skills=MA_Visionary_S6_Hypnosis:140,MA_Visionary_S9_ScanGaze:160} @NearestPlayer{r=12} ~onTimer:20
  - skill{s=MA_Visionary_S7_Surge} @PlayersInRadius{r=4} ~onTimer:20 ?playerwithin{d=4}
  - potion{type=INVISIBILITY;duration=45;particles=false} @self ~onTimer:20 ?!playerwithin{d=4}
  - skill{s=MA_Visionary_S8_DangerSense} @self ~onDamaged

# ── Door (RANGED, блінк-скірмішер) ──
MA_Door_S9:
  Template: MA_Base
  Skills:
  - kitcast{gcd=120;skills=MA_Door_S9_Seal:140} @NearestPlayer{r=12} ~onTimer:20
  - retreat{strength=1.0;vertical=0.35} @NearestPlayer{r=4} ~onTimer:30 ?playerwithin{d=4}

MA_Door_S8:
  Template: MA_Base
  Skills:
  - kitcast{gcd=120;skills=MA_Door_S8_Shock:120,MA_Door_S8_Flash:140,MA_Door_S8_Burning:160,MA_Door_S9_Seal:160} @NearestPlayer{r=12} ~onTimer:20
  - skill{s=MA_Door_S8_EscapeTrick} @self ~onDamaged
  - retreat{strength=1.0;vertical=0.35} @NearestPlayer{r=4} ~onTimer:30 ?playerwithin{d=4}

# S7/S6: гравцеві здібності цих рівнів не бойові (дивінації/Record) — кіт S8, коротші кулдауни
MA_Door_S7:
  Template: MA_Base
  Skills:
  - kitcast{gcd=120;skills=MA_Door_S8_Shock:110,MA_Door_S8_Flash:130,MA_Door_S8_Burning:150,MA_Door_S9_Seal:150} @NearestPlayer{r=12} ~onTimer:20
  - skill{s=MA_Door_S8_EscapeTrick} @self ~onDamaged
  - retreat{strength=1.0;vertical=0.35} @NearestPlayer{r=4} ~onTimer:30 ?playerwithin{d=4}

MA_Door_S6:
  Template: MA_Base
  Skills:
  - kitcast{gcd=120;skills=MA_Door_S8_Shock:100,MA_Door_S8_Flash:120,MA_Door_S8_Burning:140,MA_Door_S9_Seal:140} @NearestPlayer{r=12} ~onTimer:20
  - skill{s=MA_Door_S8_EscapeTrick} @self ~onDamaged
  - retreat{strength=1.0;vertical=0.35} @NearestPlayer{r=4} ~onTimer:30 ?playerwithin{d=4}

MA_Door_S5:
  Template: MA_Base
  Skills:
  - kitcast{gcd=80;skills=MA_Door_S5_Blink:140,MA_Door_S8_Shock:100,MA_Door_S8_Flash:120,MA_Door_S8_Burning:140,MA_Door_S9_Seal:140} @NearestPlayer{r=12} ~onTimer:20
  - skill{s=MA_Door_S8_EscapeTrick} @self ~onDamaged
  - retreat{strength=1.0;vertical=0.35} @NearestPlayer{r=4} ~onTimer:30 ?playerwithin{d=4}

# ── Justiciar (MELEE) ──
MA_Justiciar_S9:
  Template: MA_Base
  Skills:
  - kitcast{gcd=120;skills=MA_Justiciar_S9_Authority:160,MA_Justiciar_S9_ArbitersGaze:200} @NearestPlayer{r=12} ~onTimer:20
  - effect:particles{p=wax_off;amount=3;hS=0.4;vS=0.4;y=1} @self ~onTimer:20

MA_Justiciar_S8:
  Template: MA_Base
  Skills:
  - kitcast{gcd=120;skills=MA_Justiciar_S9_Authority:160,MA_Justiciar_S9_ArbitersGaze:200} @NearestPlayer{r=12} ~onTimer:20
  - skill{s=MA_Justiciar_S8_Jurisdiction} @PlayersInRadius{r=6} ~onTimer:40
  - effect:particles{p=wax_off;amount=3;hS=0.4;vS=0.4;y=1} @self ~onTimer:20

MA_Justiciar_S7:
  Template: MA_Base
  Skills:
  - kitcast{gcd=120;skills=MA_Justiciar_S7_Whip:120,MA_Justiciar_S7_Brand:180,MA_Justiciar_S9_Authority:160} @NearestPlayer{r=12} ~onTimer:20
  - skill{s=MA_Justiciar_S8_Jurisdiction} @PlayersInRadius{r=6} ~onTimer:40
  - effect:particles{p=wax_off;amount=3;hS=0.4;vS=0.4;y=1} @self ~onTimer:20

MA_Justiciar_S6:
  Template: MA_Base
  Skills:
  - kitcast{gcd=120;skills=MA_Justiciar_S6_Verdict:200,MA_Justiciar_S7_Whip:120,MA_Justiciar_S7_Brand:180,MA_Justiciar_S9_Authority:160} @NearestPlayer{r=12} ~onTimer:20
  - skill{s=MA_Justiciar_S8_Jurisdiction} @PlayersInRadius{r=6} ~onTimer:40
  - effect:particles{p=wax_off;amount=3;hS=0.4;vS=0.4;y=1} @self ~onTimer:20

MA_Justiciar_S5:
  Template: MA_Base
  Skills:
  - kitcast{gcd=80;skills=MA_Justiciar_S5_Punishment:240,MA_Justiciar_S6_Verdict:160,MA_Justiciar_S7_Brand:140,MA_Justiciar_S7_Whip:100,MA_Justiciar_S9_Authority:120} @NearestPlayer{r=12} ~onTimer:20
  - skill{s=MA_Justiciar_S8_Jurisdiction} @PlayersInRadius{r=6} ~onTimer:40
  - effect:particles{p=wax_off;amount=3;hS=0.4;vS=0.4;y=1} @self ~onTimer:20

# ── WhiteTower (S9–S7 без кіта: пасиви у статах; RANGED з S5) ──
MA_Whitetower_S9:
  Template: MA_Base
  Skills:
  - effect:particles{p=end_rod;amount=4;hS=0.3;vS=0.5;y=1;speed=0.01} @self ~onTimer:20

MA_Whitetower_S8:
  Template: MA_Base
  Skills:
  - effect:particles{p=end_rod;amount=4;hS=0.3;vS=0.5;y=1;speed=0.01} @self ~onTimer:20

MA_Whitetower_S7:
  Template: MA_Base
  Skills:
  - effect:particles{p=end_rod;amount=4;hS=0.3;vS=0.5;y=1;speed=0.01} @self ~onTimer:20

MA_Whitetower_S6:
  Template: MA_Base
  Skills:
  - kitcast{gcd=120;skills=MA_Whitetower_S6_Analysis:140} @NearestPlayer{r=12} ~onTimer:20
  - effect:particles{p=end_rod;amount=4;hS=0.3;vS=0.5;y=1;speed=0.01} @self ~onTimer:20

MA_Whitetower_S5:
  Template: MA_Base
  Skills:
  - kitcast{gcd=80;skills=MA_Whitetower_S5_Spellcast:120,MA_Whitetower_S6_Analysis:120} @NearestPlayer{r=14} ~onTimer:20
  - skill{s=MA_Whitetower_S5_MirrorCurse} @trigger ~onDamaged
  - retreat{strength=1.0;vertical=0.35} @NearestPlayer{r=4} ~onTimer:30 ?playerwithin{d=4}
  - effect:particles{p=end_rod;amount=4;hS=0.3;vS=0.5;y=1;speed=0.01} @self ~onTimer:20

# ── Error (MELEE, трикстер; гравцеві здібності S7–S5 ще не існують — кіт S8, сильніший) ──
MA_Error_S9:
  Template: MA_Base
  Skills:
  - kitcast{gcd=120;skills=MA_Error_S9_ShadowTheft:160} @NearestPlayer{r=12} ~onTimer:20
  - effect:particles{p=smoke;amount=6;hS=0.3;vS=0.5;y=1;speed=0.01} @self ~onTimer:20

MA_Error_S8:
  Template: MA_Base
  Skills:
  - kitcast{gcd=120;skills=MA_Error_S8_SwindlerCharm:140,MA_Error_S9_ShadowTheft:140} @NearestPlayer{r=12} ~onTimer:20
  - potion{type=SPEED;duration=45;level=0;particles=false} @self ~onTimer:40
  - effect:particles{p=smoke;amount=6;hS=0.3;vS=0.5;y=1;speed=0.01} @self ~onTimer:20

MA_Error_S7:
  Template: MA_Base
  Skills:
  - kitcast{gcd=100;skills=MA_Error_S8_SwindlerCharm:100,MA_Error_S9_ShadowTheft:100} @NearestPlayer{r=12} ~onTimer:20
  - potion{type=SPEED;duration=45;level=0;particles=false} @self ~onTimer:40
  - effect:particles{p=smoke;amount=6;hS=0.3;vS=0.5;y=1;speed=0.01} @self ~onTimer:20
```

- [ ] **Step 2: Перемкнути `Template:` у 6 файлах мобів**

Тільки рядок `Template:` кожного запису (Type/Display/Health/Damage/Options не чіпати; `MA_FoolPuppet` без шаблону — не чіпати):

| Файл | Моб | Новий Template |
|---|---|---|
| fool.yml | fool_nighthawk_9 | MA_Fool_S9 |
| fool.yml | fool_joker_8 | MA_Fool_S8 |
| fool.yml | fool_phantom_ink_7 | MA_Fool_S7 |
| fool.yml | fool_shapeshifter_6 | MA_Fool_S6 |
| fool.yml | fool_puppeteer_5 | MA_Fool_S5 |
| visionary.yml | visionary_manhal_9 | MA_Visionary_S9 |
| visionary.yml | visionary_thoughtform_8 | MA_Visionary_S8 |
| visionary.yml | visionary_mirror_dragon_7 | MA_Visionary_S7 |
| visionary.yml | visionary_hunting_lizard_6 | MA_Visionary_S6 |
| visionary.yml | visionary_mind_dragon_5 | MA_Visionary_S5 |
| door.yml | door_wanderer_9 | MA_Door_S9 |
| door.yml | door_spirit_eater_8 | MA_Door_S8 |
| door.yml | door_lavos_squid_7 | MA_Door_S7 |
| door.yml | door_asmann_6 | MA_Door_S6 |
| door.yml | door_void_drifter_5 | MA_Door_S5 |
| justiciar.yml | justiciar_judge_9 | MA_Justiciar_S9 |
| justiciar.yml | justiciar_terror_worm_8 | MA_Justiciar_S8 |
| justiciar.yml | justiciar_flash_snake_7 | MA_Justiciar_S7 |
| justiciar.yml | justiciar_silent_verdict_6 | MA_Justiciar_S6 |
| justiciar.yml | justiciar_retribution_5 | MA_Justiciar_S5 |
| whitetower.yml | whitetower_manticore_9 | MA_Whitetower_S9 |
| whitetower.yml | whitetower_cave_monkey_8 | MA_Whitetower_S8 |
| whitetower.yml | whitetower_phantom_python_7 | MA_Whitetower_S7 |
| whitetower.yml | whitetower_chameleon_6 | MA_Whitetower_S6 |
| whitetower.yml | whitetower_void_beholder_5 | MA_Whitetower_S5 |
| error.yml | error_sphinx_9 | MA_Error_S9 |
| error.yml | error_sphinx_8 | MA_Error_S8 |
| error.yml | error_plague_serpent_7 | MA_Error_S7 |

- [ ] **Step 3: Видалити старі архетипні метаскіли зі `Skills/*.yml`**

Видалити цілком (їх більше ніхто не референсить):
- `fool.yml`: `MA_Fool_Pull`, `MA_Fool_PullApex`, `MA_Fool_Summon`, `MA_Fool_SummonApex`
- `visionary.yml`: `MA_Visionary_Burst`, `MA_Visionary_BurstApex`
- `door.yml`: `MA_Door_Seal`, `MA_Door_SealApex`
- `justiciar.yml`: `MA_Justiciar_Aura`, `MA_Justiciar_AuraApex`
- `whitetower.yml`: `MA_Whitetower_Reveal`
- `error.yml`: `MA_Error_Chaos`, `MA_Error_ChaosApex`, `MA_Error_Scatter`, `MA_Error_Weak`, `MA_Error_Fatigue`, `MA_Error_Omen`

- [ ] **Step 4: Запустити гарди пака**

```powershell
& $mvn -o test -Dtest="MythicPackKitReferenceTest,MythicPackMobTypeTest"
```
Expected: PASS — усі референси нових шаблонів резолвляться; кожен моб/шаблон резолвить Type через `MA_Base`. Якщо `MythicPackKitReferenceTest` FAIL зі списком `missing` — одрук у назві скіла в templates.yml проти Task 5; виправити назву, не тест.

- [ ] **Step 5: Коміт**

```powershell
git add src/main/resources/mythic-pack
git commit -m @'
feat(creatures): switch mobs to per-sequence kit templates

MA_<Pathway>_Common/Apex archetype templates become MA_<Pathway>_S<seq>
with a kitcast dispatcher line (full 9..N kit, priority order) plus
reactive trigger lines outside the GCD; ranged stances gain a retreat
kite hop. Absorbed archetype metaskills are removed.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 7: Оновити `.claude/rules/mythic-creatures.md` і скіл `designing-pathway-abilities` + повний прогін

**Files:**
- Modify: `.claude/rules/mythic-creatures.md`
- Modify: `.claude/skills/designing-pathway-abilities/SKILL.md`

**Interfaces:**
- Consumes: терміни/імена з Tasks 1–6 (`kitcast`, `AbilityCastPlanner`, `MA_<Pathway>_S<seq>`, `retreat`, `blinkbehind`).
- Produces: актуальне правило для майбутніх сесій + нагадування синхронізувати кіти істот при додаванні гравцевих здібностей (синхронізація ручна — кіти НЕ успадковують зміни гравцевих здібностей автоматично).

- [ ] **Step 1: Внести зміни в правило**

У секції **«Додати істоту»** замінити крок 1 на:

```markdown
1. Моб у `mythic-pack/Mobs/<pathway>.yml`: `Template: MA_<Pathway>_S<seq>` (шаблон = кіт
   здібностей послідовностей 9..seq), Type/Display/Health/Damage/Options.
   Кожен запис Mobs/*.yml (шаблони теж) мусить резолвити `Type` напряму або через ланцюжок
   `Template:`, інакше "No Type specified" на старті (гард — `MythicPackMobTypeTest`).
```

Після секції «Додати істоту» додати нову секцію:

```markdown
## Кіти здібностей (kitcast)

Кожен шаблон `MA_<Pathway>_S<seq>` має ОДИН рядок-диспетчер:
`kitcast{gcd=<тіки>;skills=<Скіл:кулдаунТіки>,...} @NearestPlayer{r=12} ~onTimer:20` —
повний кіт 9..seq явно, порядок = пріоритет (найнижча послідовність першою). ГКД+пріоритет
рахує чистий `domain.creatures.AbilityCastPlanner` (юніт-тести), диспетчить
`KitCastMechanic`. Метаскіли кітів іменуються `MA_<Pathway>_S<seq>_<Ability>` і мобово
віддзеркалюють БОЙОВІ гравцеві здібності шляху; баланс істот — тільки в YAML пака.

- **Наступальна здібність** → без `Cooldown:` у метаскілі (кулдаун у kitcast-рядку —
  одне джерело правди); додається в `skills=` кожного шаблону, де вона доступна.
- **Реактивна/пасивна** (відповідь на урон/зближення, аури, саммон) → окремий тригерний
  рядок шаблону (`~onDamaged`, `?playerwithin`) З власним `Cooldown:` (секунди) — поза ГКД.
- **Стійка:** RANGED-шаблон додає `retreat{...} @NearestPlayer{r=4} ~onTimer:30
  ?playerwithin{d=4}` (кайт); MELEE — нічого. Кастомні механіки руху: `retreat`,
  `blinkbehind` (в `infrastructure.mythic.components`).
- Посилання на метаскіли пінить `MythicPackKitReferenceTest` (kitcast/skill{s=}/onHitSkill/
  randomskill → мусить існувати в Skills/*.yml).
- Діра в гравцевому ростері (напр. Error 7–5) — кіт не росте: той самий kitcast із коротшими
  кулдаунами/меншим gcd у нижчому шаблоні.
```

У секції **«Відомі спрощення»** видалити пункт про снаряд Whitetower (закрито:
`MA_Whitetower_S5_Spellcast` має `isbeyonder`-гейт) і пункт про Fool-саммон переформулювати:

```markdown
- Саммон ляльок Fool — реактивний скіл `MA_Fool_S5_Puppets` (кулдаун 30с при HP<35%),
  тільки в S5-шаблоні.
```

- [ ] **Step 2: Оновити скіл `designing-pathway-abilities`**

У `.claude/skills/designing-pathway-abilities/SKILL.md`, секція **Process**, після кроку 6
(«Register») вставити новий крок 7 (нинішній крок 7 «Verify» стає кроком 8):

```markdown
7. **Mirror into creature kits (manual sync).** Creature ability kits in
   `src/main/resources/mythic-pack/` do NOT inherit player abilities — they are separate
   YAML metaskills balanced independently. If the new ability is COMBAT-relevant, add its
   mob version: a `MA_<Pathway>_S<seq>_<Ability>` metaskill in
   `mythic-pack/Skills/<pathway>.yml` and a kit entry in every template
   `MA_<Pathway>_S<N≤seq>` in `Mobs/templates.yml` (offensive → the `kitcast{skills=...}`
   line; reactive/passive → its own trigger line with `Cooldown:`). See
   `.claude/rules/mythic-creatures.md` § «Кіти здібностей (kitcast)». Utility/GUI
   abilities are NOT mirrored.
```

І в секції **Red flags — STOP** додати рядок:

```markdown
- "Creature kits will pick this up automatically" → they won't; mirror combat abilities
  into the mythic-pack kit or consciously skip (utility/GUI).
```

- [ ] **Step 3: Повний прогін тестів**

```powershell
& $mvn -o clean test
```
Expected: BUILD SUCCESS, усі тести зелені (в т.ч. ArchUnit, обидва пак-гарди, контракт-тест).

- [ ] **Step 4: Коміт**

```powershell
git add .claude/rules/mythic-creatures.md .claude/skills/designing-pathway-abilities/SKILL.md
git commit -m @'
docs(rules): document sequence ability kit mechanism for creatures

Also reminds the pathway-ability skill to mirror new combat abilities
into creature kits — the pack does not inherit player ability changes.

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>
'@
```

---

### Task 8: In-server верифікація (ручна, без коміту)

Зібрати jar (`& $mvn -o clean package`), задеплоїти на тест-сервер (MythicPackInstaller
перезапише пак), перевірити:

- [ ] `/mm mobs spawn fool_nighthawk_9` → тільки ухилення при ударі, мілі-поведінка, без кастів.
- [ ] `/mm mobs spawn fool_joker_8` → кидає леза (~раз на 6с), кайтить при зближенні, Speed-пасив.
- [ ] `/mm mobs spawn fool_puppeteer_5` → нитки (притягання) в пріоритеті, болти/леза між ними, ГКД ~4с; при HP<35% — 2 ляльки; FlameJump при контакті.
- [ ] `/mm mobs spawn visionary_hunting_lizard_6` → невидимість здалеку, вибух впритул, гіпноз-касти, резист при уроні.
- [ ] `/mm mobs spawn door_void_drifter_5` → блінк за спину + удар, спалахи/розряди/печаті, втеча-телепорт при уроні, кайт.
- [ ] `/mm mobs spawn justiciar_retribution_5` → іде в контакт (без кайту), аура-зона дебафів, кара/вирок за пріоритетом.
- [ ] `/mm mobs spawn whitetower_void_beholder_5` → світлові снаряди ТІЛЬКИ по потойбічних, віддзеркалення урону, кайт.
- [ ] `/mm mobs spawn error_plague_serpent_7` → зміщення гравця + обман частіше, ніж у sphinx_9 (коротший ГКД).
- [ ] Не-потойбічний гравець поруч: ментальні/контрольні ефекти не застосовуються (isbeyonder), фізична шкода (леза/болти/удари) працює.
- [ ] Розсудок: дрен зрідка в ментальних кастах, видно в стані Beyonder.
- [ ] Темп: між кастами моби б'ються звичайною атакою; жодного «кулеметного» спаму.
- [ ] `stop` сервера / `reload` — без помилок AsyncCatcher і без «завислих» ефектів.

Якщо якась вбудована механіка пака не існує в MythicMobs 5.12.1 (лог `Invalid mechanic`
на старті) — замінити рядок на еквівалент і повторити: кандидати на ризик —
`projectile` (є в 5.x), `throw` (є), `ignite` (є), `pull` (уже використовується),
`randomskill` (уже використовується). Помилки видно в консолі при завантаженні пака.

---

## Self-Review (виконано при написанні плану)

- **Spec coverage:** планувальник+ГКД (Task 1–2), стійки/кайт (Task 3, 6), кіти всіх 6 шляхів із поглинанням архетипів (Task 5–6), гард референсів (Task 4), правило-документація (Task 7), in-server чеклист зі спеки (Task 8). Числа YAML — стартові, тюнінг in-server.
- **Placeholder scan:** чисто — кожен крок містить повний код/YAML/команду.
- **Type consistency:** `AbilityCastPlanner.KitEntry(String, long)` і `pickNext(long)` однакові в Task 1 (визначення) і Task 2 (використання); імена метаскілів Task 5 = імена в kitcast/skill{s=} рядках Task 6 (перевіряється й механічно — `MythicPackKitReferenceTest`).
- **API verified:** `castSkill(Entity, String, Entity, Location, Collection, Collection, float)`, `SkillResult.INVALID_CONFIG/CONDITION_FAILED`, `MythicLineConfig.getString(String[], String)` — підтверджені javap по Mythic-Dist-5.12.1.jar.
