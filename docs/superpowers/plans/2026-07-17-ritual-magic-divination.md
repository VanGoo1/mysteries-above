# Ritual Magic & Divination Redistribution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Додати Ритуальну Магію (Fool/Door/WhiteTower, Посл. 9→7), перерозподілити ворожіння (Fool ← Мистецтво Ворожіння без кулі; Door ← Кришталева Куля; WhiteTower втрачає ворожіння), реворкнути Перевтілення (справжнє маскування, 20 дух./с).

**Architecture:** Чистий доменний каталог ритуалів (`domain.rituals`, ArchUnit PURE_DOMAIN) + самотікова `RitualSession` + тонка спільна `RitualMagic` у новому пакеті `me.vangoo.pathways.common.abilities`. `DivinationArts` переїжджає в `fool.abilities` і втрачає Кришталеву кулю; нова `CrystalBall` у `door.abilities`; `Shapeshifting` переписується на профіль-маскування за зразком `GatheringAnonymizer` + toggle-драйв за зразком `PsychologicalInvisibility`.

**Tech Stack:** Java 21, Paper API 1.21, JUnit 5, ArchUnit, triumph-gui (через `context.ui()`).

**Spec:** `docs/superpowers/specs/2026-07-17-ritual-magic-divination-design.md`

## Global Constraints

- Увесь user-facing текст — українською (`.claude/rules/localization.md`).
- Коміти — англійською, Conventional Commits (`.claude/rules/commit-messages.md`).
- `domain.rituals` — НУЛЬ `org.bukkit..`/`net.kyori..` імпортів (додається в `PURE_DOMAIN` ArchUnit).
- Сесії: instance-реєстри `Map<UUID, …>` (ніколи не `static`), повторний каст замінює сесію, `cleanUp()` скасовує всі, `tick()` перечитує гравця через `Bukkit.getPlayer` (`.claude/rules/pathway-abilities.md`).
- Deferred-здібності: ресурси списує `AbilityResourceConsumer.consumeResources(...)` у колбеку меню; НІКОЛИ не став кулдаун вручну на deferred-результат.
- Maven НЕ на PATH. Запуск:
  ```powershell
  $mvn = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd"
  & $mvn -o -q test            # усі тести
  & $mvn -o -q test "-Dtest=RitualCatalogTest"   # один клас
  ```
- Ефекти (вівтар, сесія, куля, маскування) верифікуються in-server, не моками. Юніт-тести — лише чистий домен.

---

### Task 1: Domain — RitualType / RitualRecipe / RitualCatalog

**Files:**
- Create: `src/main/java/me/vangoo/domain/rituals/RitualType.java`
- Create: `src/main/java/me/vangoo/domain/rituals/RitualRecipe.java`
- Create: `src/main/java/me/vangoo/domain/rituals/RitualCatalog.java`
- Modify: `src/test/java/me/vangoo/architecture/ArchitectureTest.java:22-31` (масив `PURE_DOMAIN`)
- Test: `src/test/java/me/vangoo/domain/rituals/RitualCatalogTest.java`

**Interfaces:**
- Consumes: нічого (чистий домен).
- Produces: `RitualType` (7 значень), `RitualRecipe(RitualType type, String displayName, String description, int minSequence, int candlesRequired, Map<String,Integer> ingredients, boolean requiresHandSacrifice)` з методом `boolean availableAt(int sequenceLevel)`; `RitualCatalog.ALL : List<RitualRecipe>`, `RitualCatalog.availableFor(int sequenceLevel) : List<RitualRecipe>`, `RitualCatalog.of(RitualType) : RitualRecipe`.

- [ ] **Step 1: Write the failing test**

`src/test/java/me/vangoo/domain/rituals/RitualCatalogTest.java`:

```java
package me.vangoo.domain.rituals;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RitualCatalogTest {

    @Test
    void catalogHasSevenRituals() {
        assertEquals(7, RitualCatalog.ALL.size());
    }

    @Test
    void sequenceGatingIsThreeFiveSeven() {
        assertEquals(3, RitualCatalog.availableFor(9).size());
        assertEquals(5, RitualCatalog.availableFor(8).size());
        assertEquals(7, RitualCatalog.availableFor(7).size());
        assertEquals(7, RitualCatalog.availableFor(0).size()); // нижче — все лишається
    }

    @Test
    void seqSevenRitualsNeedFiveCandlesOthersThree() {
        for (RitualRecipe r : RitualCatalog.ALL) {
            if (r.minSequence() == 7) {
                assertEquals(5, r.candlesRequired(), r.displayName());
            } else {
                assertEquals(3, r.candlesRequired(), r.displayName());
            }
        }
    }

    @Test
    void onlySacrificeUsesHandItemAndHasNoIngredientList() {
        for (RitualRecipe r : RitualCatalog.ALL) {
            if (r.type() == RitualType.SACRIFICE) {
                assertTrue(r.requiresHandSacrifice());
                assertTrue(r.ingredients().isEmpty());
            } else {
                assertFalse(r.requiresHandSacrifice(), r.displayName());
                assertFalse(r.ingredients().isEmpty(), r.displayName());
            }
        }
    }

    @Test
    void everyRitualResolvableByType() {
        for (RitualType type : RitualType.values()) {
            assertEquals(type, RitualCatalog.of(type).type());
        }
    }

    @Test
    void namesAndDescriptionsAreUkrainianNonEmpty() {
        for (RitualRecipe r : RitualCatalog.ALL) {
            assertFalse(r.displayName().isBlank());
            assertFalse(r.description().isBlank());
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `& $mvn -o -q test "-Dtest=RitualCatalogTest"`
Expected: COMPILATION ERROR — `package me.vangoo.domain.rituals does not exist`.

- [ ] **Step 3: Write the implementation**

`src/main/java/me/vangoo/domain/rituals/RitualType.java`:

```java
package me.vangoo.domain.rituals;

/**
 * Типи ритуалів ритуальної магії (за вікі LOTM: молитви, жертви, дарування,
 * спіритизм, дзеркальне ворожіння, стіна духовності).
 */
public enum RitualType {
    LUCK_PRAYER,
    SANCTIFICATION,
    SACRIFICE,
    BESTOWMENT,
    MEDIUMSHIP,
    MIRROR_DIVINATION,
    SPIRIT_WALL
}
```

`src/main/java/me/vangoo/domain/rituals/RitualRecipe.java`:

```java
package me.vangoo.domain.rituals;

import java.util.Map;

/**
 * Чистий рецепт ритуалу: вимоги до вівтаря й інгредієнтів + гейт послідовності.
 * Інгредієнти — імена Bukkit Material як String (домен не імпортує Bukkit);
 * резолвить їх шар поведінки.
 */
public record RitualRecipe(
        RitualType type,
        String displayName,
        String description,
        int minSequence,
        int candlesRequired,
        Map<String, Integer> ingredients,
        boolean requiresHandSacrifice
) {
    public boolean availableAt(int sequenceLevel) {
        return sequenceLevel <= minSequence;
    }
}
```

`src/main/java/me/vangoo/domain/rituals/RitualCatalog.java`:

```java
package me.vangoo.domain.rituals;

import java.util.List;
import java.util.Map;

/** Каталог семи ритуалів. Гейт: Посл. 9 — 3 ритуали, 8 — 5, 7 — усі 7. */
public final class RitualCatalog {

    public static final List<RitualRecipe> ALL = List.of(
            new RitualRecipe(RitualType.LUCK_PRAYER, "Молитва удачі",
                    "Прихильність долі на кілька хвилин; зірваний ритуал накличе невдачу",
                    9, 3, Map.of("GOLD_NUGGET", 1), false),
            new RitualRecipe(RitualType.SANCTIFICATION, "Освячення предмета",
                    "Відновлює міцність предмета в головній руці",
                    9, 3, Map.of("IRON_INGOT", 1), false),
            new RitualRecipe(RitualType.SACRIFICE, "Жертвопринесення",
                    "Спаліть предмет із головної руки — відновіть духовність",
                    9, 3, Map.of(), true),
            new RitualRecipe(RitualType.BESTOWMENT, "Ритуал дарування",
                    "Шанс отримати інгредієнт зілля наступної послідовності",
                    8, 3, Map.of("DIAMOND", 2), false),
            new RitualRecipe(RitualType.MEDIUMSHIP, "Спіритизм",
                    "Духи розкажуть про минулі події поблизу",
                    8, 3, Map.of("BONE", 1), false),
            new RitualRecipe(RitualType.MIRROR_DIVINATION, "Дзеркальне ворожіння",
                    "Покаже, хто діяв у цьому місці останнім часом",
                    7, 5, Map.of("AMETHYST_SHARD", 1), false),
            new RitualRecipe(RitualType.SPIRIT_WALL, "Стіна духовності",
                    "Захисна зона навколо вівтаря на пів хвилини",
                    7, 5, Map.of("LAPIS_LAZULI", 1), false)
    );

    public static List<RitualRecipe> availableFor(int sequenceLevel) {
        return ALL.stream().filter(r -> r.availableAt(sequenceLevel)).toList();
    }

    public static RitualRecipe of(RitualType type) {
        return ALL.stream().filter(r -> r.type() == type).findFirst().orElseThrow();
    }

    private RitualCatalog() {
    }
}
```

- [ ] **Step 4: Widen ArchUnit pure-domain scope**

У `src/test/java/me/vangoo/architecture/ArchitectureTest.java` додай рядок у масив `PURE_DOMAIN` (після `"me.vangoo.domain.organizations"`):

```java
            "me.vangoo.domain.organizations",
            "me.vangoo.domain.rituals"
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `& $mvn -o -q test "-Dtest=RitualCatalogTest"` → Expected: PASS (6 tests).
Run: `& $mvn -o -q test "-Dtest=ArchitectureTest"` → Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add src/main/java/me/vangoo/domain/rituals src/test/java/me/vangoo/domain/rituals src/test/java/me/vangoo/architecture/ArchitectureTest.java
git commit -m "feat(rituals): pure ritual catalog with sequence gating"
```

---

### Task 2: Domain — SacrificeAppraiser

**Files:**
- Create: `src/main/java/me/vangoo/domain/rituals/SacrificeKind.java`
- Create: `src/main/java/me/vangoo/domain/rituals/SacrificeAppraiser.java`
- Test: `src/test/java/me/vangoo/domain/rituals/SacrificeAppraiserTest.java`

**Interfaces:**
- Produces: `SacrificeKind` enum (`PATHWAY_INGREDIENT`, `PRECIOUS`, `VALUABLE`, `TRIFLE`); `SacrificeAppraiser.spiritualityFor(SacrificeKind) : int` (базове відновлення духовності — скейлить викликач).

- [ ] **Step 1: Write the failing test**

`src/test/java/me/vangoo/domain/rituals/SacrificeAppraiserTest.java`:

```java
package me.vangoo.domain.rituals;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SacrificeAppraiserTest {

    @Test
    void pathwayIngredientIsTheBestSacrifice() {
        assertEquals(300, SacrificeAppraiser.spiritualityFor(SacrificeKind.PATHWAY_INGREDIENT));
    }

    @Test
    void valueStrictlyDecreasesByKind() {
        int ingredient = SacrificeAppraiser.spiritualityFor(SacrificeKind.PATHWAY_INGREDIENT);
        int precious = SacrificeAppraiser.spiritualityFor(SacrificeKind.PRECIOUS);
        int valuable = SacrificeAppraiser.spiritualityFor(SacrificeKind.VALUABLE);
        int trifle = SacrificeAppraiser.spiritualityFor(SacrificeKind.TRIFLE);
        assertTrue(ingredient > precious);
        assertTrue(precious > valuable);
        assertTrue(valuable > trifle);
        assertTrue(trifle > 0);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `& $mvn -o -q test "-Dtest=SacrificeAppraiserTest"`
Expected: COMPILATION ERROR — `cannot find symbol: class SacrificeAppraiser`.

- [ ] **Step 3: Write the implementation**

`src/main/java/me/vangoo/domain/rituals/SacrificeKind.java`:

```java
package me.vangoo.domain.rituals;

/** Категорія жертви для Жертвопринесення (класифікує шар поведінки). */
public enum SacrificeKind {
    PATHWAY_INGREDIENT,
    PRECIOUS,
    VALUABLE,
    TRIFLE
}
```

`src/main/java/me/vangoo/domain/rituals/SacrificeAppraiser.java`:

```java
package me.vangoo.domain.rituals;

/** Оцінка жертви: скільки духовності повертає спалення предмета на вівтарі. */
public final class SacrificeAppraiser {

    public static int spiritualityFor(SacrificeKind kind) {
        return switch (kind) {
            case PATHWAY_INGREDIENT -> 300;
            case PRECIOUS -> 150;
            case VALUABLE -> 60;
            case TRIFLE -> 10;
        };
    }

    private SacrificeAppraiser() {
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `& $mvn -o -q test "-Dtest=SacrificeAppraiserTest"` → Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```powershell
git add src/main/java/me/vangoo/domain/rituals src/test/java/me/vangoo/domain/rituals
git commit -m "feat(rituals): sacrifice appraiser for spirituality restoration"
```

---

### Task 3: RitualSession + RitualIncantations (шар поведінки)

**Files:**
- Create: `src/main/java/me/vangoo/pathways/common/abilities/RitualIncantations.java`
- Create: `src/main/java/me/vangoo/pathways/common/abilities/RitualSession.java`

**Interfaces:**
- Consumes: `RitualType` (Task 1).
- Produces: `RitualIncantations.linesFor(RitualType) : List<String>` (4 рядки укр);
  `RitualSession(UUID casterId, Location altarCenter, List<String> lines, Runnable onComplete, Consumer<String> onAbort)` з методами `tick()`, `bindTask(BukkitTask)`, `cancel()`. Викликач планує: `scheduleRepeating(session::tick, 0L, 1L)`.

- [ ] **Step 1: Write RitualIncantations**

`src/main/java/me/vangoo/pathways/common/abilities/RitualIncantations.java`:

```java
package me.vangoo.pathways.common.abilities;

import me.vangoo.domain.rituals.RitualType;

import java.util.List;
import java.util.Map;

/**
 * Канонічна 4-частинна структура заклинання (вікі LOTM): звертання до сутності →
 * молитва про ласку → прохання одним реченням → підсилення інгредієнтом.
 */
final class RitualIncantations {

    private static final Map<RitualType, List<String>> LINES = Map.of(
            RitualType.LUCK_PRAYER, List.of(
                    "Я молю силу прихованих сутностей удачі,",
                    "я молю про вашу ласку,",
                    "даруйте мені прихильність випадку,",
                    "золото, що сяє в пітьмі, підсили моє слово!"),
            RitualType.SANCTIFICATION, List.of(
                    "Я освячую тебе, знаряддя моє,",
                    "я очищаю тебе від скверни,",
                    "служи мені в цьому ритуалі,",
                    "залізо, вірне й холодне, підсили моє слово!"),
            RitualType.SACRIFICE, List.of(
                    "Я молю увагу прихованого сущого,",
                    "я молю прийняти мій дар,",
                    "прийміть цю жертву на вівтарі,",
                    "полум'я свічок, донеси мою офіру!"),
            RitualType.BESTOWMENT, List.of(
                    "Я молю силу за брамою царства,",
                    "я молю про вашу щедрість,",
                    "даруйте мені частку ваших володінь,",
                    "діаманте, чистий як зоря, підсили моє слово!"),
            RitualType.MEDIUMSHIP, List.of(
                    "Я кличу духів цього місця,",
                    "я молю про вашу відповідь,",
                    "повідайте, що тут відбулося,",
                    "кістко предків, підсили моє слово!"),
            RitualType.MIRROR_DIVINATION, List.of(
                    "Я молю силу таємниць,",
                    "я молю про одкровення,",
                    "покажіть мені всіх, хто ступав тут,",
                    "кристале, що бачив усе, підсили моє слово!"),
            RitualType.SPIRIT_WALL, List.of(
                    "Я звертаюсь до власної духовності,",
                    "я не кличу нікого, крім себе,",
                    "постань стіною навколо цього вівтаря,",
                    "лазурите, камене неба, підсили моє слово!")
    );

    static List<String> linesFor(RitualType type) {
        return LINES.get(type);
    }

    private RitualIncantations() {
    }
}
```

- [ ] **Step 2: Write RitualSession**

`src/main/java/me/vangoo/pathways/common/abilities/RitualSession.java`:

```java
package me.vangoo.pathways.common.abilities;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Живий хід ритуалу: заклинання по літерах в action bar над вівтарем.
 * Самотіковий об'єкт (патерн DuelBriefing/сесій): володіє своїм BukkitTask,
 * tick() перечитує гравця через Bukkit — жодного захопленого контексту.
 * Зрив: вихід за 4 бл від вівтаря, отриманий урон, смерть, офлайн.
 */
public class RitualSession {

    private static final int TICKS_PER_CHAR = 2;
    private static final int LINE_HOLD_TICKS = 20;
    private static final double MAX_DISTANCE = 4.0;

    private final UUID casterId;
    private final Location altarCenter;
    private final List<String> lines;
    private final Runnable onComplete;
    private final Consumer<String> onAbort;

    private BukkitTask task;
    private int tickCounter;
    private int lineIndex;
    private int charIndex;
    private int holdRemaining;
    private double lastHealth = -1;
    private boolean done;

    public RitualSession(UUID casterId, Location altarCenter, List<String> lines,
                         Runnable onComplete, Consumer<String> onAbort) {
        this.casterId = casterId;
        this.altarCenter = altarCenter.clone();
        this.lines = List.copyOf(lines);
        this.onComplete = onComplete;
        this.onAbort = onAbort;
    }

    public void bindTask(BukkitTask task) {
        this.task = task;
    }

    public void tick() {
        if (done) return;

        Player player = Bukkit.getPlayer(casterId);
        if (player == null || !player.isOnline() || player.isDead()) {
            abort("ритуал обірвано");
            return;
        }
        if (!player.getWorld().equals(altarCenter.getWorld())
                || player.getLocation().distance(altarCenter) > MAX_DISTANCE) {
            abort("ви полишили вівтар");
            return;
        }
        double health = player.getHealth();
        if (lastHealth >= 0 && health < lastHealth - 0.01) {
            abort("вас поранено під час заклинання");
            return;
        }
        lastHealth = health;

        tickCounter++;

        if (holdRemaining > 0) {
            holdRemaining--;
            if (holdRemaining == 0) {
                lineIndex++;
                charIndex = 0;
                if (lineIndex >= lines.size()) {
                    finishSuccess(player);
                }
            }
            return;
        }

        if (tickCounter % TICKS_PER_CHAR != 0) return;

        String line = lines.get(lineIndex);
        charIndex = Math.min(charIndex + 1, line.length());
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(ChatColor.LIGHT_PURPLE + line.substring(0, charIndex)));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f, 1.6f);
        player.getWorld().spawnParticle(Particle.ENCHANT,
                altarCenter.clone().add(0, 1.2, 0), 3, 0.6, 0.4, 0.6);

        if (charIndex >= line.length()) {
            holdRemaining = LINE_HOLD_TICKS;
        }
    }

    private void finishSuccess(Player player) {
        done = true;
        cancel();
        player.getWorld().spawnParticle(Particle.END_ROD,
                altarCenter.clone().add(0, 1.5, 0), 40, 0.8, 0.8, 0.8);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.4f);
        onComplete.run();
    }

    private void abort(String reason) {
        done = true;
        cancel();
        onAbort.accept(reason);
    }

    public void cancel() {
        if (task != null) task.cancel();
    }
}
```

- [ ] **Step 3: Compile**

Run: `& $mvn -o -q compile` → Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/me/vangoo/pathways/common
git commit -m "feat(rituals): self-ticking ritual session with incantation delivery"
```

---

### Task 4: RitualEffectRunner (ефекти семи ритуалів)

**Files:**
- Create: `src/main/java/me/vangoo/pathways/common/abilities/RitualEffectRunner.java`

**Interfaces:**
- Consumes: `RitualRecipe`/`RitualType`/`SacrificeKind`/`SacrificeAppraiser` (Tasks 1–2); `IAbilityContext`; `SequenceScaler.ScalingStrategy`.
- Produces: `RitualEffectRunner.run(RitualRecipe recipe, IAbilityContext context, Location altarCenter, ItemStack sacrificedItem)` — виконує ефект після успішного заклинання (`sacrificedItem` — знімок жертви, `null` для всіх, крім SACRIFICE); `RitualEffectRunner.classifySacrifice(IAbilityContext, ItemStack) : SacrificeKind`.

- [ ] **Step 1: Write the runner**

`src/main/java/me/vangoo/pathways/common/abilities/RitualEffectRunner.java`:

```java
package me.vangoo.pathways.common.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.rituals.RitualRecipe;
import me.vangoo.domain.rituals.SacrificeAppraiser;
import me.vangoo.domain.rituals.SacrificeKind;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.RecordedEvent;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.Spirituality;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Stateless-хореографія ефектів ритуалів (запускається після успішного заклинання).
 * Балансові базові числа тут — константи ефектів; скейл — SequenceScaler.
 */
public class RitualEffectRunner {

    private static final int LUCK_BASE_TICKS = 6000;        // 5 хв
    private static final int SANCTIFY_BASE_DURABILITY = 250;
    private static final int EVENTS_BASE_WINDOW_SECONDS = 1800; // 30 хв
    private static final int WALL_BASE_TICKS = 600;         // 30 с
    private static final double WALL_RADIUS = 6.0;

    private final Random rng = new Random();

    public void run(RitualRecipe recipe, IAbilityContext context, Location altarCenter, ItemStack sacrificedItem) {
        switch (recipe.type()) {
            case LUCK_PRAYER -> runLuck(context);
            case SANCTIFICATION -> runSanctify(context);
            case SACRIFICE -> runSacrifice(context, sacrificedItem);
            case BESTOWMENT -> runBestowment(context);
            case MEDIUMSHIP -> runMediumship(context, altarCenter);
            case MIRROR_DIVINATION -> runMirror(context, altarCenter);
            case SPIRIT_WALL -> runSpiritWall(context, altarCenter);
        }
    }

    private void runLuck(IAbilityContext context) {
        Beyonder b = context.getCasterBeyonder();
        int duration = scale(LUCK_BASE_TICKS, b.getSequence());
        context.entity().applyPotionEffect(context.getCasterId(), PotionEffectType.LUCK, duration, 0);
        context.messaging().sendMessage(context.getCasterId(),
                ChatColor.GREEN + "✦ Сутності почули вас: удача на " + (duration / 1200) + " хв.");
    }

    private void runSanctify(IAbilityContext context) {
        Player player = context.getCasterPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || !(hand.getItemMeta() instanceof Damageable meta) || meta.getDamage() == 0) {
            context.messaging().sendMessage(context.getCasterId(),
                    ChatColor.YELLOW + "✦ Предмет у руці не потребує освячення.");
            return;
        }
        int repair = scale(SANCTIFY_BASE_DURABILITY, context.getCasterBeyonder().getSequence());
        meta.setDamage(Math.max(0, meta.getDamage() - repair));
        hand.setItemMeta(meta);
        context.messaging().sendMessage(context.getCasterId(),
                ChatColor.GREEN + "✦ Предмет освячено: відновлено до " + repair + " міцності.");
    }

    private void runSacrifice(IAbilityContext context, ItemStack sacrificed) {
        if (sacrificed == null || sacrificed.getType() == Material.AIR) return;
        Beyonder b = context.getCasterBeyonder();
        SacrificeKind kind = classifySacrifice(context, sacrificed);
        int restored = scale(SacrificeAppraiser.spiritualityFor(kind), b.getSequence());
        Spirituality sp = b.getSpirituality();
        b.setSpirituality(sp.increment(Math.min(restored, sp.maximum() - sp.current())));
        context.messaging().sendMessage(context.getCasterId(),
                ChatColor.GREEN + "✦ Жертву прийнято: +" + restored + " духовності.");
    }

    public SacrificeKind classifySacrifice(IAbilityContext context, ItemStack item) {
        Beyonder b = context.getCasterBeyonder();
        for (int seq = 9; seq >= 0; seq--) {
            List<ItemStack> ingredients =
                    context.beyonder().getIngredientsForPotion(b.getPathway(), Sequence.of(seq));
            if (ingredients == null) continue;
            for (ItemStack ingredient : ingredients) {
                if (ingredient != null && ingredient.isSimilar(item)) {
                    return SacrificeKind.PATHWAY_INGREDIENT;
                }
            }
        }
        Material m = item.getType();
        if (m == Material.DIAMOND || m == Material.EMERALD
                || m == Material.NETHERITE_INGOT || m == Material.NETHERITE_SCRAP) {
            return SacrificeKind.PRECIOUS;
        }
        if (m == Material.GOLD_INGOT || m == Material.IRON_INGOT
                || m == Material.GOLD_BLOCK || m == Material.IRON_BLOCK
                || m == Material.AMETHYST_SHARD) {
            return SacrificeKind.VALUABLE;
        }
        return SacrificeKind.TRIFLE;
    }

    private void runBestowment(IAbilityContext context) {
        Beyonder b = context.getCasterBeyonder();
        int level = b.getSequenceLevel();
        UUID casterId = context.getCasterId();
        if (level == 0) {
            context.messaging().sendMessage(casterId, ChatColor.YELLOW + "✦ Ви вже на вершині шляху.");
            return;
        }
        List<ItemStack> ingredients =
                context.beyonder().getIngredientsForPotion(b.getPathway(), Sequence.of(level - 1));
        if (ingredients == null || ingredients.isEmpty()) {
            context.messaging().sendMessage(casterId, ChatColor.YELLOW + "✦ Сутності мовчать — дарунку немає.");
            return;
        }
        double chance = Math.min(0.9, 0.5 + 0.06 * (9 - level));
        if (rng.nextDouble() >= chance) {
            context.messaging().sendMessage(casterId, ChatColor.YELLOW + "✦ Сутності прийняли дар, але не відповіли.");
            return;
        }
        ItemStack gift = ingredients.get(rng.nextInt(ingredients.size())).clone();
        gift.setAmount(1);
        Player player = context.getCasterPlayer();
        player.getInventory().addItem(gift)
                .values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        context.messaging().sendMessage(casterId, ChatColor.GREEN + "✦ Брама відчинилась — ви отримали дарунок!");
    }

    private void runMediumship(IAbilityContext context, Location altar) {
        printEvents(context, altar, 15, 8, "🕯 ГОЛОСИ ДУХІВ");
    }

    private void runMirror(IAbilityContext context, Location altar) {
        printEvents(context, altar, 10, 10, "🪞 ДЗЕРКАЛО МИНУЛОГО");
    }

    private void printEvents(IAbilityContext context, Location altar, int radius, int limit, String header) {
        UUID casterId = context.getCasterId();
        int window = scale(EVENTS_BASE_WINDOW_SECONDS, context.getCasterBeyonder().getSequence());
        List<RecordedEvent> events = new ArrayList<>(context.events().getPastEvents(altar, radius, window));
        events.sort(Comparator.comparingLong(RecordedEvent::getTimestamp).reversed());

        context.messaging().sendMessage(casterId, ChatColor.DARK_PURPLE + "═══════════════════════════════");
        context.messaging().sendMessage(casterId, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + header);
        if (events.isEmpty()) {
            context.messaging().sendMessage(casterId, ChatColor.GRAY + "Це місце мовчить — слідів не лишилось.");
        }
        long now = System.currentTimeMillis();
        for (RecordedEvent e : events.subList(0, Math.min(limit, events.size()))) {
            long minutesAgo = Math.max(0, (now - e.getTimestamp()) / 60000);
            context.messaging().sendMessage(casterId, ChatColor.GRAY + "• " + minutesAgo + " хв тому — "
                    + ChatColor.AQUA + e.getDescription());
        }
        context.messaging().sendMessage(casterId, ChatColor.DARK_PURPLE + "═══════════════════════════════");
    }

    private void runSpiritWall(IAbilityContext context, Location altar) {
        UUID casterId = context.getCasterId();
        int duration = scale(WALL_BASE_TICKS, context.getCasterBeyonder().getSequence());
        context.messaging().sendMessage(casterId,
                ChatColor.AQUA + "✦ Стіна духовності постала на " + (duration / 20) + " с.");

        final int[] elapsed = {0};
        final BukkitTask[] holder = new BukkitTask[1];
        holder[0] = context.scheduling().scheduleRepeating(() -> {
            World world = altar.getWorld();
            elapsed[0] += 10;
            if (world == null || elapsed[0] >= duration) {
                holder[0].cancel();
                return;
            }
            for (int i = 0; i < 24; i++) {
                double angle = Math.PI * 2 * i / 24;
                world.spawnParticle(Particle.ENCHANT,
                        altar.clone().add(Math.cos(angle) * WALL_RADIUS, 1.0, Math.sin(angle) * WALL_RADIUS),
                        1, 0, 0.4, 0);
            }
            for (Entity e : world.getNearbyEntities(altar, WALL_RADIUS, 4, WALL_RADIUS)) {
                if (e instanceof Monster monster) {
                    Vector away = monster.getLocation().toVector().subtract(altar.toVector()).setY(0);
                    if (away.lengthSquared() < 0.01) away = new Vector(1, 0, 0);
                    monster.setVelocity(away.normalize().multiply(0.6).setY(0.2));
                }
            }
            Player caster = Bukkit.getPlayer(casterId);
            if (caster != null && caster.getWorld().equals(world)
                    && caster.getLocation().distance(altar) <= WALL_RADIUS) {
                caster.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30, 0, true, false));
            }
        }, 0L, 10L);
    }

    private int scale(int base, Sequence sequence) {
        double multiplier = SequenceScaler.calculateMultiplier(
                sequence.level(), SequenceScaler.ScalingStrategy.MODERATE);
        return (int) Math.ceil(base * multiplier);
    }
}
```

- [ ] **Step 2: Compile**

Run: `& $mvn -o -q compile` → Expected: BUILD SUCCESS.
Якщо `Sequence.level()` не існує — перевір точний геттер у `me.vangoo.domain.valueobjects.Sequence` (`level()` для record / `getLevel()`) і використай його.

- [ ] **Step 3: Commit**

```powershell
git add src/main/java/me/vangoo/pathways/common
git commit -m "feat(rituals): effect runner for the seven rituals"
```

---

### Task 5: RitualMagic ability + реєстрація в трьох шляхах

**Files:**
- Create: `src/main/java/me/vangoo/pathways/common/abilities/RitualMagic.java`
- Modify: `src/main/java/me/vangoo/pathways/fool/Fool.java` (Посл. 9)
- Modify: `src/main/java/me/vangoo/pathways/door/Door.java` (Посл. 9)
- Modify: `src/main/java/me/vangoo/pathways/whitetower/WhiteTower.java` (Посл. 9)

**Interfaces:**
- Consumes: усе з Tasks 1–4.
- Produces: `RitualMagic extends ActiveAbility` з `getName() == "Ритуальна магія"`; instance-реєстр `Map<UUID, RitualSession>`; `cleanUp()`.

- [ ] **Step 1: Write RitualMagic**

`src/main/java/me/vangoo/pathways/common/abilities/RitualMagic.java`:

```java
package me.vangoo.pathways.common.abilities;

import me.vangoo.domain.abilities.core.AbilityResourceConsumer;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.rituals.RitualCatalog;
import me.vangoo.domain.rituals.RitualRecipe;
import me.vangoo.domain.rituals.RitualType;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.type.Candle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Посл. 9 (Fool / Door / WhiteTower): Ритуальна магія.
 * Фізичний вівтар: запалені свічки в радіусі 3 бл. Вибір ритуалу — меню;
 * хід — RitualSession (заклинання по літерах); ефект — RitualEffectRunner.
 * Прогрес: Посл. 9 — 3 ритуали, 8 — 5, 7 — усі 7 (RitualCatalog).
 */
public class RitualMagic extends ActiveAbility {

    private static final int BASE_COST = 100;
    private static final int BASE_COOLDOWN = 60;
    private static final int ALTAR_RADIUS = 3;
    private static final int ABORT_SANITY_LOSS = 2;

    private final RitualEffectRunner runner = new RitualEffectRunner();
    private final Map<UUID, RitualSession> activeSessions = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Ритуальна магія";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        int available = RitualCatalog.availableFor(userSequence.level()).size();
        return "Знання ритуальної магії: вівтар зі свічок, заклинання й прохання до " +
                "прихованих сутностей. Доступно ритуалів: " + available + " із 7. " +
                "\n§7§oПоставте й запаліть свічки поруч, тоді кастуйте.";
    }

    @Override
    public int getSpiritualityCost() {
        return BASE_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return BASE_COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        Location altar = context.getCasterLocation();
        int litCandles = countLitCandles(altar);

        if (litCandles < 3) {
            return AbilityResult.failure("Вівтар не готовий: потрібно щонайменше 3 запалені свічки в радіусі "
                    + ALTAR_RADIUS + " бл (зараз: " + litCandles + ")");
        }

        int level = context.getCasterBeyonder().getSequenceLevel();
        List<RitualRecipe> available = RitualCatalog.availableFor(level).stream()
                .filter(r -> litCandles >= r.candlesRequired())
                .toList();

        if (available.isEmpty()) {
            return AbilityResult.failure("Замало свічок для доступних ритуалів");
        }

        context.ui().openChoiceMenu("Ритуальна магія", available,
                this::createRitualItem,
                recipe -> startRitual(context, altar, recipe));
        return AbilityResult.deferred();
    }

    private ItemStack createRitualItem(RitualRecipe recipe) {
        ItemStack item = new ItemStack(iconFor(recipe.type()));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + recipe.displayName());
            meta.setLore(List.of(
                    ChatColor.GRAY + recipe.description(),
                    ChatColor.DARK_GRAY + "Свічок: " + recipe.candlesRequired()
                            + (recipe.ingredients().isEmpty() ? "" : " • Інгредієнти: " + ingredientsLine(recipe))
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String ingredientsLine(RitualRecipe recipe) {
        StringBuilder sb = new StringBuilder();
        recipe.ingredients().forEach((mat, count) -> {
            if (sb.length() > 0) sb.append(", ");
            sb.append(count).append("x ").append(mat.toLowerCase().replace('_', ' '));
        });
        return sb.toString();
    }

    private Material iconFor(RitualType type) {
        return switch (type) {
            case LUCK_PRAYER -> Material.RABBIT_FOOT;
            case SANCTIFICATION -> Material.ANVIL;
            case SACRIFICE -> Material.FLINT_AND_STEEL;
            case BESTOWMENT -> Material.DIAMOND;
            case MEDIUMSHIP -> Material.BONE;
            case MIRROR_DIVINATION -> Material.AMETHYST_SHARD;
            case SPIRIT_WALL -> Material.LAPIS_LAZULI;
        };
    }

    private void startRitual(IAbilityContext context, Location altar, RitualRecipe recipe) {
        UUID casterId = context.getCasterId();
        Beyonder beyonder = context.getCasterBeyonder();
        Player player = context.getCasterPlayer();
        if (player == null) return;
        player.closeInventory();

        // Перевірка інгредієнтів ДО списання духовності.
        for (Map.Entry<String, Integer> entry : recipe.ingredients().entrySet()) {
            Material mat = Material.valueOf(entry.getKey());
            if (!player.getInventory().containsAtLeast(new ItemStack(mat), entry.getValue())) {
                context.messaging().sendMessage(casterId, ChatColor.RED + "Бракує інгредієнтів: "
                        + entry.getValue() + "x " + mat.name().toLowerCase().replace('_', ' '));
                return;
            }
        }
        ItemStack sacrificed = null;
        if (recipe.requiresHandSacrifice()) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType() == Material.AIR) {
                context.messaging().sendMessage(casterId, ChatColor.RED + "Візьміть жертву в головну руку.");
                return;
            }
            sacrificed = hand.clone();
            sacrificed.setAmount(1);
        }

        if (!AbilityResourceConsumer.consumeResources(this, beyonder, context)) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "Недостатньо духовності!");
            return;
        }
        context.events().publishAbilityUsedEvent(this, beyonder);

        // Списуємо інгредієнти / жертву.
        for (Map.Entry<String, Integer> entry : recipe.ingredients().entrySet()) {
            player.getInventory().removeItem(new ItemStack(Material.valueOf(entry.getKey()), entry.getValue()));
        }
        if (recipe.requiresHandSacrifice()) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            hand.setAmount(hand.getAmount() - 1);
        }

        // Повторний каст замінює сесію власника.
        RitualSession previous = activeSessions.remove(casterId);
        if (previous != null) previous.cancel();

        ItemStack sacrificedFinal = sacrificed;
        RitualSession session = new RitualSession(casterId, altar,
                RitualIncantations.linesFor(recipe.type()),
                () -> {
                    activeSessions.remove(casterId);
                    runner.run(recipe, context, altar, sacrificedFinal);
                },
                reason -> {
                    activeSessions.remove(casterId);
                    applyBacklash(context, recipe, reason);
                });
        BukkitTask task = context.scheduling().scheduleRepeating(session::tick, 0L, 1L);
        session.bindTask(task);
        activeSessions.put(casterId, session);
    }

    private void applyBacklash(IAbilityContext context, RitualRecipe recipe, String reason) {
        UUID casterId = context.getCasterId();
        context.getCasterBeyonder().increaseSanityLoss(ABORT_SANITY_LOSS);
        if (recipe.type() == RitualType.LUCK_PRAYER) {
            context.entity().applyPotionEffect(casterId,
                    org.bukkit.potion.PotionEffectType.UNLUCK, 2400, 0);
        }
        context.messaging().sendMessage(casterId, ChatColor.RED + "✗ Ритуал зірвано (" + reason
                + ") — відкат вдарив по розуму.");
    }

    private int countLitCandles(Location center) {
        World world = center.getWorld();
        if (world == null) return 0;
        int count = 0;
        for (int x = -ALTAR_RADIUS; x <= ALTAR_RADIUS; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -ALTAR_RADIUS; z <= ALTAR_RADIUS; z++) {
                    var data = world.getBlockAt(center.getBlockX() + x,
                            center.getBlockY() + y, center.getBlockZ() + z).getBlockData();
                    if (data instanceof Candle candle && candle.isLit()) count++;
                }
            }
        }
        return count;
    }

    @Override
    public void cleanUp() {
        activeSessions.values().forEach(RitualSession::cancel);
        activeSessions.clear();
    }
}
```

- [ ] **Step 2: Register in three pathways (Посл. 9)**

`Fool.java` — імпорт + список Посл. 9:

```java
import me.vangoo.pathways.common.abilities.RitualMagic;
```

```java
        // Sequence 9: Seer (Провидець)
        sequenceAbilities.put(9, List.of(
                new Divination(),
                new SeerSpiritVision(),
                new DangerIntuition(),
                new RitualMagic()
        ));
```

`Door.java` — імпорт + Посл. 9:

```java
import me.vangoo.pathways.common.abilities.RitualMagic;
```

```java
        sequenceAbilities.put(9, List.of(new DoorOpening(), new RitualMagic()));
```

`WhiteTower.java` — імпорт + Посл. 9 (DivinationArts(80) поки ЛИШАЄТЬСЯ — приберемо в Task 7):

```java
import me.vangoo.pathways.common.abilities.RitualMagic;
```

```java
        sequenceAbilities.put(9, List.of(new DivinationArts(80), new EnhancedMentalAttributes(), new RitualMagic()));
```

- [ ] **Step 3: Compile + full tests**

Run: `& $mvn -o -q test` → Expected: BUILD SUCCESS, усі тести зелені.

- [ ] **Step 4: Commit**

```powershell
git add src/main/java/me/vangoo/pathways
git commit -m "feat(rituals): ritual magic ability wired into Fool, Door and WhiteTower"
```

---

### Task 6: CrystalBall (Door, клас без реєстрації)

**Files:**
- Create: `src/main/java/me/vangoo/pathways/door/abilities/CrystalBall.java`

**Interfaces:**
- Consumes: `IAbilityContext`, `DivinationOdds` (наявний `me.vangoo.domain.valueobjects.DivinationOdds` — конструктор `new DivinationOdds(casterSeq, targetSeq)`, метод `successProbability()`), `AbilityDomainEvent` (`abilityName()`, `occurredAt()`), `RecordedEvent`.
- Produces: `CrystalBall extends ActiveAbility`, `getName() == "Кришталева куля"`, два режими меню.

- [ ] **Step 1: Write CrystalBall**

`src/main/java/me/vangoo/pathways/door/abilities/CrystalBall.java`:

```java
package me.vangoo.pathways.door.abilities;

import me.vangoo.domain.abilities.core.AbilityResourceConsumer;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.events.AbilityDomainEvent;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.DivinationOdds;
import me.vangoo.domain.valueobjects.RecordedEvent;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * Посл. 7 (Door): Кришталева куля — астрологічні повноваження (вікі LOTM):
 * 1) власні спогади (журнал недавніх подій за участі кастера, як сонне провидіння про себе);
 * 2) накладання силуетів — викриття маскування гравця в прицілі.
 */
public class CrystalBall extends ActiveAbility {

    private static final int BASE_COST = 120;
    private static final int BASE_COOLDOWN = 60;
    private static final int MEMORY_BASE_WINDOW_SECONDS = 900; // 15 хв, скейлиться
    private static final double SILHOUETTE_RANGE = 20.0;
    private static final int ANTI_DIVINATION_UNLOCK_SEQUENCE = 7;

    private final Random rng = new Random();

    @Override
    public String getName() {
        return "Кришталева куля";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Куля астролога: пригадайте власне минуле або накладіть силуети, " +
                "щоб викрити чуже маскування.";
    }

    @Override
    public int getSpiritualityCost() {
        return BASE_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return BASE_COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        List<Mode> modes = List.of(Mode.MEMORY_JOURNAL, Mode.SILHOUETTE);
        context.ui().openChoiceMenu("Кришталева куля", modes, this::createMenuItem,
                mode -> handleChoice(context, mode));
        return AbilityResult.deferred();
    }

    private void handleChoice(IAbilityContext context, Mode mode) {
        Player caster = context.getCasterPlayer();
        if (caster != null) caster.closeInventory();
        switch (mode) {
            case MEMORY_JOURNAL -> runMemoryJournal(context);
            case SILHOUETTE -> runSilhouette(context);
        }
    }

    // ========== 1. ВЛАСНІ СПОГАДИ ==========

    private void runMemoryJournal(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        Beyonder beyonder = context.getCasterBeyonder();
        if (!AbilityResourceConsumer.consumeResources(this, beyonder, context)) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "Недостатньо духовності!");
            return;
        }
        context.events().publishAbilityUsedEvent(this, beyonder);
        playGazeEffect(context);

        int window = (int) Math.ceil(MEMORY_BASE_WINDOW_SECONDS * SequenceScaler.calculateMultiplier(
                beyonder.getSequenceLevel(), SequenceScaler.ScalingStrategy.MODERATE));

        Player caster = context.getCasterPlayer();
        String casterName = caster.getName();
        Location loc = context.getCasterLocation();

        List<AbilityDomainEvent> ownCasts = context.events().getAbilityEventHistory(casterId, window);
        List<RecordedEvent> worldEvents = new ArrayList<>(context.events().getPastEvents(loc, 30, window));
        worldEvents.removeIf(e -> !e.getDescription().contains(casterName));
        worldEvents.sort(Comparator.comparingLong(RecordedEvent::getTimestamp).reversed());

        context.messaging().sendMessage(casterId, ChatColor.DARK_PURPLE + "═══════════════════════════════");
        context.messaging().sendMessage(casterId,
                ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "🔮 СПОГАДИ КРИШТАЛЕВОЇ КУЛІ");
        long now = System.currentTimeMillis();

        if (ownCasts.isEmpty() && worldEvents.isEmpty()) {
            context.messaging().sendMessage(casterId, ChatColor.GRAY + "Туман минулого порожній.");
        }
        for (AbilityDomainEvent e : ownCasts.subList(0, Math.min(8, ownCasts.size()))) {
            long minutesAgo = Math.max(0, (now - e.occurredAt()) / 60000);
            context.messaging().sendMessage(casterId, ChatColor.GRAY + "• " + minutesAgo
                    + " хв тому — ви використали " + ChatColor.AQUA + e.abilityName());
        }
        for (RecordedEvent e : worldEvents.subList(0, Math.min(8, worldEvents.size()))) {
            long minutesAgo = Math.max(0, (now - e.getTimestamp()) / 60000);
            context.messaging().sendMessage(casterId, ChatColor.GRAY + "• " + minutesAgo
                    + " хв тому — " + ChatColor.AQUA + e.getDescription());
        }
        context.messaging().sendMessage(casterId, ChatColor.DARK_PURPLE + "═══════════════════════════════");
    }

    // ========== 2. НАКЛАДАННЯ СИЛУЕТІВ ==========

    private void runSilhouette(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        Optional<Player> targetOpt = context.targeting().getTargetedPlayer(SILHOUETTE_RANGE);
        if (targetOpt.isEmpty()) {
            context.messaging().sendMessage(casterId,
                    ChatColor.RED + "🔮 Дивіться на гравця (до " + (int) SILHOUETTE_RANGE + " бл).");
            return;
        }
        Player target = targetOpt.get();

        if (resistsDivination(context, target)) {
            // Опір пройдено ціллю — духовність все одно витрачається (спроба була).
            if (!AbilityResourceConsumer.consumeResources(this, context.getCasterBeyonder(), context)) {
                context.messaging().sendMessage(casterId, ChatColor.RED + "Недостатньо духовності!");
                return;
            }
            context.messaging().sendMessage(casterId,
                    ChatColor.RED + "🔮 Силуети розпливаються — щось заважає ворожінню.");
            return;
        }

        if (!AbilityResourceConsumer.consumeResources(this, context.getCasterBeyonder(), context)) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "Недостатньо духовності!");
            return;
        }
        context.events().publishAbilityUsedEvent(this, context.getCasterBeyonder());
        playGazeEffect(context);

        String realName = target.getName();
        String profileName = target.getPlayerProfile().getName();
        String displayName = ChatColor.stripColor(target.getDisplayName());
        boolean disguised = (profileName != null && !realName.equals(profileName))
                || (displayName != null && !realName.equals(displayName));

        if (disguised) {
            context.messaging().sendMessage(casterId, ChatColor.GOLD + "🔮 Силуети НЕ збігаються! Перед вами "
                    + ChatColor.WHITE + (profileName != null ? profileName : displayName)
                    + ChatColor.GOLD + ", а насправді це " + ChatColor.RED + realName + ChatColor.GOLD + ".");
        } else {
            context.messaging().sendMessage(casterId, ChatColor.GREEN + "🔮 Силуети збігаються — "
                    + ChatColor.WHITE + realName + ChatColor.GREEN + " є тим, ким виглядає.");
        }
    }

    private boolean resistsDivination(IAbilityContext context, Player target) {
        UUID targetId = target.getUniqueId();
        boolean anti = context.beyonder().isAbilityActivated(targetId, AbilityIdentity.of("Anti Divination"))
                || context.beyonder().isAbilityActivated(targetId, AbilityIdentity.of("Anti-Divination"))
                || context.beyonder().isAbilityActivated(targetId, AbilityIdentity.of("anti_divination"))
                || context.beyonder().isAbilityActivated(targetId, AbilityIdentity.of("AntiDivination"))
                || context.beyonder().isAbilityActivated(targetId, new AntiDivination().getIdentity());
        if (!anti) return false;
        Beyonder targetBeyonder = context.beyonder().getBeyonder(targetId);
        if (targetBeyonder == null || targetBeyonder.getSequenceLevel() > ANTI_DIVINATION_UNLOCK_SEQUENCE) {
            return false;
        }
        double chance = new DivinationOdds(context.getCasterBeyonder().getSequenceLevel(),
                targetBeyonder.getSequenceLevel()).successProbability();
        return rng.nextDouble() >= chance;
    }

    private void playGazeEffect(IAbilityContext context) {
        context.effects().playSoundForPlayer(context.getCasterId(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
        context.effects().spawnParticle(Particle.END_ROD,
                context.getCasterLocation().add(0, 1.5, 0), 25, 0.4, 0.4, 0.4);
    }

    private ItemStack createMenuItem(Mode mode) {
        ItemStack item = new ItemStack(mode.icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(mode.color + mode.displayName);
            meta.setLore(List.of(ChatColor.GRAY + mode.description));
            item.setItemMeta(meta);
        }
        return item;
    }

    private enum Mode {
        MEMORY_JOURNAL("Власні спогади", "Хронологія ваших недавніх дій і подій довкола вас",
                Material.WRITABLE_BOOK, ChatColor.AQUA),
        SILHOUETTE("Накладання силуетів", "Викрити, чи справжній вигляд гравця в прицілі",
                Material.AMETHYST_CLUSTER, ChatColor.LIGHT_PURPLE);

        final String displayName;
        final String description;
        final Material icon;
        final ChatColor color;

        Mode(String displayName, String description, Material icon, ChatColor color) {
            this.displayName = displayName;
            this.description = description;
            this.icon = icon;
            this.color = color;
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `& $mvn -o -q compile` → Expected: BUILD SUCCESS.
Якщо `AntiDivination` не має публічного безаргументного конструктора — прибери рядок `|| context.beyonder().isAbilityActivated(targetId, new AntiDivination().getIdentity())` і додай замість нього `|| context.beyonder().isAbilityActivated(targetId, AbilityIdentity.of("Анти-ворожіння"))` з фактичним `getName()` класу `AntiDivination` (подивись у файлі).

- [ ] **Step 3: Commit**

```powershell
git add src/main/java/me/vangoo/pathways/door/abilities/CrystalBall.java
git commit -m "feat(door): crystal ball ability with memory journal and silhouette overlay"
```

---

### Task 7: Перерозподіл ворожіння (переїзд DivinationArts, видалення Divination, реєстрації)

**Files:**
- Move: `src/main/java/me/vangoo/pathways/door/abilities/DivinationArts.java` → `src/main/java/me/vangoo/pathways/fool/abilities/DivinationArts.java`
- Move: `src/main/java/me/vangoo/pathways/door/abilities/DiviningRodSession.java` → `src/main/java/me/vangoo/pathways/fool/abilities/DiviningRodSession.java`
- Move: `src/main/java/me/vangoo/pathways/door/abilities/DreamVisionSession.java` → `src/main/java/me/vangoo/pathways/fool/abilities/DreamVisionSession.java`
- Delete: `src/main/java/me/vangoo/pathways/fool/abilities/Divination.java`
- Modify: `src/main/java/me/vangoo/pathways/fool/Fool.java`
- Modify: `src/main/java/me/vangoo/pathways/door/Door.java`
- Modify: `src/main/java/me/vangoo/pathways/whitetower/WhiteTower.java`

**Interfaces:**
- Consumes: `CrystalBall` (Task 6), `RitualMagic` (Task 5).
- Produces: фінальні реєстрації трьох шляхів (див. кроки).

- [ ] **Step 1: git mv трьох класів**

```powershell
git mv src/main/java/me/vangoo/pathways/door/abilities/DivinationArts.java src/main/java/me/vangoo/pathways/fool/abilities/DivinationArts.java
git mv src/main/java/me/vangoo/pathways/door/abilities/DiviningRodSession.java src/main/java/me/vangoo/pathways/fool/abilities/DiviningRodSession.java
git mv src/main/java/me/vangoo/pathways/door/abilities/DreamVisionSession.java src/main/java/me/vangoo/pathways/fool/abilities/DreamVisionSession.java
```

У кожному з трьох файлів заміни перший рядок
`package me.vangoo.pathways.door.abilities;` → `package me.vangoo.pathways.fool.abilities;`.

- [ ] **Step 2: Strip Кришталевої кулі з DivinationArts**

У `fool/abilities/DivinationArts.java`:

1. В енумі `DivinationType` видали константу `CRYSTAL_BALL("Кришталева куля", ...)` (перший запис; енум починається з `ASTROLOGY`).
2. У `handleDivinationChoice` видали гілку `case CRYSTAL_BALL -> performCrystalBallDivination(ctx);`.
3. Видали методи повністю: `performCrystalBallDivination`, `revealPlayerInfo`, `revealWeatherPrediction`.
4. Видали конструктор `public DivinationArts(int spiritualityCost) {...}` (лишається тільки безаргументний).
5. Онови `getDescription(...)`: заміни текст на
   `"Володіння мистецтвом гадання. Відкриває доступ до різних методів передбачення: астрологія, маятник, лозошукання та сонне провидіння." + "\n§7§oЛозошукання покращується з просуванням послідовності."`

- [ ] **Step 3: Fool.java — фінальний вигляд Посл. 9**

Видали імпорт/використання `Divination` (клас видаляємо у Step 6). Список Посл. 9:

```java
        // Sequence 9: Seer (Провидець)
        sequenceAbilities.put(9, List.of(
                new DivinationArts(),
                new SeerSpiritVision(),
                new DangerIntuition(),
                new RitualMagic()
        ));
```

(`DivinationArts` тепер у `me.vangoo.pathways.fool.abilities` — вже покривається `import me.vangoo.pathways.fool.abilities.*;`.)

- [ ] **Step 4: Door.java — Посл. 7 із CrystalBall**

```java
        sequenceAbilities.put(7, List.of(new CrystalBall(), new SpiritualVision(), new SpiritualIntuition(), new AntiDivination()));
```

`import me.vangoo.pathways.door.abilities.*;` вже покриває `CrystalBall`.

- [ ] **Step 5: WhiteTower.java — прибрати DivinationArts(80)**

```java
        sequenceAbilities.put(9, List.of(new RitualMagic(), new EnhancedMentalAttributes()));
```

Прибери зайві імпорти `me.vangoo.pathways.door.abilities.*` і `me.vangoo.pathways.door.abilities.Record`, якщо після правки вони не використовуються (перевір, що `WhiteTower` більше нічого з door не вживає — Record/інші там були зайві).

- [ ] **Step 6: Delete Divination.java**

```powershell
git rm src/main/java/me/vangoo/pathways/fool/abilities/Divination.java
```

- [ ] **Step 7: Full tests**

Run: `& $mvn -o -q test` → Expected: BUILD SUCCESS (компіляція підтвердить, що на `Divination`/старі пакети ніхто не посилається; `Justiciar` імпортує `door.abilities.AntiDivination` — він НЕ переїжджав, має лишитись зеленим).

- [ ] **Step 8: Commit**

```powershell
git add -A src/main/java/me/vangoo/pathways
git commit -m "feat(pathways): redistribute divination between Fool, Door and WhiteTower"
```

---

### Task 8: Реворк Shapeshifting (Перевтілення)

**Files:**
- Rewrite: `src/main/java/me/vangoo/pathways/fool/abilities/Shapeshifting.java` (повна заміна вмісту)

**Interfaces:**
- Consumes: Paper `PlayerProfile`/`ProfileProperty` (`com.destroystokyo.paper.profile`), патерн `GatheringAnonymizer` (профіль не персистентний — релогін сам повертає вигляд), toggle-драйв за зразком `PsychologicalInvisibility`.
- Produces: `Shapeshifting extends ActiveAbility`, `getName() == "Перевтілення"` (ідентичність збережена — реєстрація у Fool Посл. 6 не змінюється).

- [ ] **Step 1: Rewrite Shapeshifting.java**

Повний новий вміст файлу:

```java
package me.vangoo.pathways.fool.abilities;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import me.vangoo.domain.abilities.core.AbilityResourceConsumer;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.MasteryProgressionCalculator;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.Spirituality;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Посл. 6: Безликий — Перевтілення (реворк).
 * Справжнє маскування: скін (Paper profile), нік у табі й чаті БУДЬ-ЯКОГО гравця,
 * що колись був на сервері. Без ліміту часу; шкода НЕ знімає маскування.
 * Підтримка коштує 20 духовності/с — вичерпалась → маскування спадає.
 * Профіль не персистентний (як у GatheringAnonymizer): релогін повертає справжній вигляд.
 */
public class Shapeshifting extends ActiveAbility {

    private static final int COST_PER_SECOND = 20;
    private static final int BASE_COOLDOWN = 10;
    private static final int MENU_LIMIT = 53;

    // Інстанс-реєстр живих маскувань (НЕ static — правило сесій).
    private final Map<UUID, MaskSession> activeMasks = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Перевтілення";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Скопіюйте скін та ім'я будь-якого гравця, що колись був на сервері. " +
                "Без ліміту часу; шкода не знімає маскування. Підтримка: " +
                COST_PER_SECOND + " духовності/с. Повторний каст знімає личину.";
    }

    @Override
    public int getSpiritualityCost() {
        return COST_PER_SECOND;
    }

    @Override
    public int getPeriodicCost() {
        return COST_PER_SECOND;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return BASE_COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();

        if (activeMasks.containsKey(casterId)) {
            Player player = context.getCasterPlayer();
            unmask(player, casterId, "свідоме зняття");
            context.cooldown().setCooldown(this, casterId);
            return AbilityResult.success();
        }

        List<OfflinePlayer> candidates = Arrays.stream(Bukkit.getOfflinePlayers())
                .filter(p -> p.getName() != null && !p.getUniqueId().equals(casterId))
                .sorted(Comparator.comparingLong(OfflinePlayer::getLastPlayed).reversed())
                .limit(MENU_LIMIT)
                .toList();

        if (candidates.isEmpty()) {
            return AbilityResult.failure("Немає личин: на сервері ще ніхто не бував");
        }

        context.ui().openChoiceMenu("Перевтілення: оберіть личину", candidates,
                this::createHeadItem,
                target -> activateMask(context, target));
        return AbilityResult.deferred();
    }

    private ItemStack createHeadItem(OfflinePlayer target) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(target);
            meta.setDisplayName(ChatColor.GOLD + target.getName());
            meta.setLore(List.of(ChatColor.GRAY + (target.isOnline() ? "Зараз онлайн" : "Був на сервері")));
            head.setItemMeta(meta);
        }
        return head;
    }

    private void activateMask(IAbilityContext context, OfflinePlayer target) {
        UUID casterId = context.getCasterId();
        Player caster = context.getCasterPlayer();
        if (caster == null) return;
        caster.closeInventory();

        if (!AbilityResourceConsumer.consumeResources(this, context.getCasterBeyonder(), context)) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "Недостатньо духовності!");
            return;
        }
        context.events().publishAbilityUsedEvent(this, context.getCasterBeyonder());

        String disguiseName = target.getName();
        PlayerProfile originalProfile = caster.getPlayerProfile();

        // Скін: копіюємо textures цілі, якщо кешовані; інакше — лише ім'я (фолбек зі спеки).
        PlayerProfile masked = caster.getPlayerProfile();
        masked.setName(disguiseName);
        Optional<ProfileProperty> textures = target.getPlayerProfile().getProperties().stream()
                .filter(p -> p.getName().equals("textures"))
                .findFirst();
        if (textures.isPresent()) {
            masked.setProperty(textures.get());
        } else {
            masked.removeProperty("textures");
            context.messaging().sendMessage(casterId, ChatColor.YELLOW
                    + "⚠ Скін цієї личини не збережено на сервері — скопійовано лише ім'я.");
        }
        caster.setPlayerProfile(masked);
        caster.setDisplayName(disguiseName);
        caster.setPlayerListName(disguiseName);

        Location loc = caster.getLocation();
        caster.getWorld().spawnParticle(Particle.SMOKE, loc.clone().add(0, 1, 0), 30, 0.4, 0.8, 0.4);
        caster.playSound(loc, Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1f, 1.0f);
        caster.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(ChatColor.GOLD + "🎭 Ви — " + disguiseName));

        // Дрейн 20/с. Захоплений context несе ідентичність ЦЬОГО кастера — допустимо
        // (той самий патерн, що PsychologicalInvisibility).
        BukkitTask task = context.scheduling().scheduleRepeating(() -> {
            Player p = Bukkit.getPlayer(casterId);
            if (p == null || !p.isOnline()) {
                // Профіль скинеться сам при релогіні — просто чистимо сесію.
                MaskSession s = activeMasks.remove(casterId);
                if (s != null) s.task().cancel();
                return;
            }
            var beyonder = context.getCasterBeyonder();
            Spirituality sp = beyonder.getSpirituality();
            if (sp.current() < COST_PER_SECOND) {
                unmask(p, casterId, "виснаження духовності");
                return;
            }
            beyonder.setSpirituality(sp.decrement(COST_PER_SECOND));
            double masteryGain = MasteryProgressionCalculator.calculateMasteryGain(
                    COST_PER_SECOND, beyonder.getSequence());
            if (masteryGain > 0) {
                beyonder.setMastery(beyonder.getMastery().add(masteryGain));
            }
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.GOLD + "🎭 " + disguiseName));
        }, 20L, 20L);

        activeMasks.put(casterId, new MaskSession(task, originalProfile, disguiseName));
    }

    private void unmask(Player player, UUID casterId, String reason) {
        MaskSession session = activeMasks.remove(casterId);
        if (session == null) return;
        session.task().cancel();

        if (player != null && player.isOnline()) {
            player.setPlayerProfile(session.originalProfile());
            player.setDisplayName(null);
            player.setPlayerListName(null);
            Location loc = player.getLocation();
            player.getWorld().spawnParticle(Particle.SMOKE, loc.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3);
            player.playSound(loc, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 0.8f);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(ChatColor.GRAY + "🎭 Личину знято • " + reason));
        }
    }

    @Override
    public void cleanUp() {
        for (UUID casterId : activeMasks.keySet()) {
            unmask(Bukkit.getPlayer(casterId), casterId, "вимкнення");
        }
        activeMasks.clear();
    }

    private record MaskSession(BukkitTask task, PlayerProfile originalProfile, String disguiseName) {
    }
}
```

- [ ] **Step 2: Full tests**

Run: `& $mvn -o -q test` → Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```powershell
git add src/main/java/me/vangoo/pathways/fool/abilities/Shapeshifting.java
git commit -m "feat(fool): rework shapeshifting into real profile disguise with upkeep drain"
```

---

### Task 9: Документація + фінальна збірка

**Files:**
- Create: `.claude/rules/ritual-magic.md`
- Modify: `CLAUDE.md` (секція `me.vangoo.pathways`)
- Modify: `.claude/rules/pathway-abilities.md` (еталони сесій)

**Interfaces:** нічого нового в коді.

- [ ] **Step 1: Create `.claude/rules/ritual-magic.md`**

```markdown
# Ритуальна магія (Fool / Door / WhiteTower)

## Механізм

- **Спільна здібність** — `me.vangoo.pathways.common.abilities.RitualMagic` (Посл. 9 у Fool,
  Door, WhiteTower; пакет `pathways.common` — для здібностей, що належать кільком шляхам
  одразу). Прогрес БЕЗ заміни класу: доступність ритуалів гейтить `RitualCatalog.availableFor`
  (Посл. 9 — 3, 8 — 5, 7 — усі 7), сила ефектів — `SequenceScaler` (MODERATE).
- **Домен** — `domain.rituals` (у `PURE_DOMAIN` ArchUnit, нуль Bukkit): `RitualType`,
  `RitualRecipe` (гейт послідовності, свічки, інгредієнти як імена `Material` у String),
  `RitualCatalog` (юніт-тест `RitualCatalogTest`), `SacrificeKind`/`SacrificeAppraiser`
  (юніт-тест `SacrificeAppraiserTest`).
- **Вівтар фізичний**: запалені `CANDLE`-блоки в радіусі 3 бл від кастера; базові ритуали —
  3 свічки, ритуали Посл. 7 — 5. Меню — `context.ui().openChoiceMenu`, deferred; ресурси
  списує `AbilityResourceConsumer` у колбеку, потім інгредієнти/жертва з інвентаря.
- **Сесія** — `RitualSession` (самотікова, патерн DuelBriefing): заклинання по літерах
  в action bar (4-частинна структура з вікі — `RitualIncantations`), зрив = вихід за 4 бл /
  урон / смерть / офлайн → `onAbort` (sanity +2, для Молитви удачі — ще Unluck).
  Реєстр сесій — instance-поле `RitualMagic`, `cleanUp()` скасовує всі.
- **Ефекти** — `RitualEffectRunner` (stateless): удача (Luck), освячення (ремонт міцності),
  жертвопринесення (духовність за `SacrificeAppraiser`), дарування (шанс інгредієнта
  наступної посл.), спіритизм/дзеркало (минулі події через `IEventContext.getPastEvents` —
  CoreProtect), стіна духовності (відштовхує монстрів + Resistance, самозгасний таск).

## Як додати ритуал

1. Значення в `RitualType` + запис у `RitualCatalog.ALL` (посл./свічки/інгредієнти/опис укр).
2. 4 рядки заклинання в `RitualIncantations.LINES`.
3. Гілка в `RitualEffectRunner.run` + іконка в `RitualMagic.iconFor`.
4. Онови пінінг у `RitualCatalogTest` (кількість, гейти).

## Кришталева куля (Door, Посл. 7)

`door.abilities.CrystalBall` — one-shot, два режими: журнал власних спогадів
(`getAbilityEventHistory` + `getPastEvents`, фільтр за ім'ям кастера) і накладання силуетів
(викриття маскування: профіль/display name ≠ справжнє ім'я; опір — `AntiDivination` +
`DivinationOdds`, як в інших ворожіннях).

## Перевтілення (Fool, Посл. 6) — маскування

`fool.abilities.Shapeshifting`: копіює скін (Paper `setPlayerProfile`, патерн
`GatheringAnonymizer` — профіль НЕ персистентний, релогін повертає вигляд), нік у табі
(`setPlayerListName`) і чаті (`setDisplayName`) будь-якого гравця з `getOfflinePlayers()`
(меню з голів, сортування за останнім входом, ліміт 53). Без ліміту часу; шкода НЕ знімає;
дрейн 20 духовності/с у власному таску сесії; вичерпання → авто-зняття. Реєстр масок —
instance-поле, `cleanUp()` знімає всі. Викриває маскування — Кришталева куля Door.

## Заборони

- ❌ Bukkit/`net.kyori` у `domain.rituals` (ArchUnit `PURE_DOMAIN`).
- ❌ Балансові числа ритуалів поза `domain.rituals`/константами runner-а — нові формули
  тільки чистими VO з тестами.
- ❌ `static` реєстри сесій/масок (правило сесій `pathway-abilities.md`).
- ❌ Видавати інгредієнти/Характеристики повз `RitualEffectRunner.runBestowment` —
  єдиний канал видачі предметів ритуалами.
```

- [ ] **Step 2: Update CLAUDE.md**

У секції `**me.vangoo.pathways**` (перший буліт про пакети) заміни фрагмент
`one package per pathway (`error`, `door`, `justiciar`, `visionary`, `whitetower`, `fool`)` на:

`one package per pathway (`error`, `door`, `justiciar`, `visionary`, `whitetower`, `fool`) + спільний пакет `common` (здібності кількох шляхів одразу: `RitualMagic` + `RitualSession`/`RitualEffectRunner` — див. `.claude/rules/ritual-magic.md`)`

- [ ] **Step 3: Update `.claude/rules/pathway-abilities.md`**

У рядку `Еталон: AreaOfJurisdiction + JurisdictionSession; також DiviningRodSession, DreamVisionSession (door).` заміни `(door)` на `(fool)` і додай `RitualSession (common)`:

`Еталон: AreaOfJurisdiction + JurisdictionSession; також DiviningRodSession, DreamVisionSession (fool) і RitualSession (common).`

- [ ] **Step 4: Final build + package**

Run: `& $mvn -o -q clean package` → Expected: BUILD SUCCESS, shaded JAR у `target/`.

- [ ] **Step 5: Commit**

```powershell
git add .claude/rules/ritual-magic.md .claude/rules/pathway-abilities.md CLAUDE.md
git commit -m "docs(rules): ritual magic mechanism and divination redistribution"
```

- [ ] **Step 6: In-server verification checklist (вручну, після деплою JAR)**

1. `/pathway` → видати Fool Посл. 9: «Ритуальна магія» без свічок → відмова з підказкою; 3 запалені свічки → меню з 3 ритуалів; зрив (відбігти) → повідомлення відкату.
2. Жертвопринесення: інгредієнт шляху в руці → значний приріст духовності; камінь → мізерний.
3. Door Посл. 7: «Кришталева куля» — журнал спогадів після пари кастів здібностей; силует по замаскованому Fool-гравцю викриває справжнє ім'я.
4. WhiteTower Посл. 9: «Мистецтво ворожіння» ВІДСУТНЄ, «Ритуальна магія» присутня.
5. Fool Посл. 9: «Мистецтво ворожіння» без пункту «Кришталева куля»; старого «Ворожіння» немає.
6. Перевтілення (Fool Посл. 6): меню голів; маскування міняє скін/таб/чат; удар НЕ знімає; духовність тане 20/с; повторний каст знімає.
```
