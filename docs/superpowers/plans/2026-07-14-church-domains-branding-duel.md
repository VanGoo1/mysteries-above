# Church Domains, Pathway Branding & Initiation Duel — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add all 22 pathways as stubs (16 new, no abilities/recipes), a universal per-pathway branding color applied to potions and Characteristics, replace vault-based church initiation with a Sequence-9 duel that lets a pathless member choose a domain pathway, and fix two ChurchMenu issues.

**Architecture:** One `StubPathway`/`StubPotions` pair + a data table register the 16 stubs. A pure `PathwayBranding` registry becomes the single source of truth for potion liquid color and Characteristic name color. A new `ChurchDuelService` + `DuelSession` + `DuelArenaProvider` + `DuelBriefing` + `DuelListener` implement the duel in a dedicated void world, decoupled from `ChurchMenu` via setter-injected callbacks. `ChurchService` gains a `trialPassed` flag replacing the old initiation-task machinery.

**Tech Stack:** Java 21, Spigot/Paper API 1.21, Maven (shaded plugin JAR), triumph-gui, MythicMobs bridge, Citizens, JUnit 5.

## Global Constraints

- **Build/test with the IntelliJ-bundled Maven** (`mvn` is NOT on PATH). Use exactly:
  `& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" <args>` from PowerShell.
- **Commit message format:** Conventional Commits, English only, imperative subject ≤72 chars, no trailing period, no dashes as separators. End every commit body with `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`. Commit via `-F <file>` (a here-string leaves literal `@` in the message on this shell).
- **All user-facing text in Ukrainian** (names, descriptions, GUI, messages). Identifiers, class names, config keys, logs, and `PathwayManager` Sequence names stay English (novel canon).
- **`domain` core purity:** no `org.bukkit..` in `entities`/`services`/`spells`/`brewing`/`creatures`. `domain` root (`PathwayPotions`, new `PathwayBranding`) MAY use `org.bukkit.Color`/`ChatColor`. `domain` must never import `me.vangoo.pathways`.
- **MythicMobs `io.lumine..` only inside `me.vangoo.infrastructure.mythic..`** — spawn via `MythicCreatureGateway`.
- **Persist church state after every mutation** (`persist()`), never `static` mutable state on services/sessions.
- Pathway class/stub name MUST equal its `PathwayManager` key AND its `InstitutionRegistry` id (`Darkness`, `TwilightGiant`, …).

---

### Task 1: PathwayGroup expansion + explicit-name Pathway constructor

**Files:**
- Modify: `src/main/java/me/vangoo/domain/entities/PathwayGroup.java`
- Modify: `src/main/java/me/vangoo/domain/entities/Pathway.java`

**Interfaces:**
- Produces: `PathwayGroup.{EternalDarkness,GoddessOfOrigin,CalamityOfDestruction,FatherOfDevils,KeyOfLight}`; `new Pathway(PathwayGroup group, String name, List<String> sequenceNames)` (name may be null → uses `getClass().getSimpleName()`).

- [ ] **Step 1: Add the 5 groups**

In `PathwayGroup.java` replace the enum constants block with:

```java
public enum PathwayGroup {
    LordOfMysteries("Володар Таємниць"),
    DemonOfKnowledge("Демон Знання"),
    GodAlmighty("Бог Всемогутній"),
    TheAnarchy("Анархія"),
    EternalDarkness("Вічна Темрява"),
    GoddessOfOrigin("Богиня Витоків"),
    CalamityOfDestruction("Лихо Руйнування"),
    FatherOfDevils("Батько Дияволів"),
    KeyOfLight("Ключ Світла");
```

(Leave the rest of the file unchanged.)

- [ ] **Step 2: Add the explicit-name constructor to Pathway**

In `Pathway.java` replace the single constructor with two:

```java
    public Pathway(PathwayGroup group, List<String> sequenceNames) {
        this(group, null, sequenceNames);
    }

    public Pathway(PathwayGroup group, String explicitName, List<String> sequenceNames) {
        String resolved = explicitName != null ? explicitName : this.getClass().getSimpleName();
        if (sequenceNames.size() != SEQUENCE_COUNT) {
            throw new IllegalArgumentException("Pathway " + resolved + " must declare " + SEQUENCE_COUNT
                    + " sequence names (0-9), got " + sequenceNames.size());
        }
        this.name = resolved;
        this.group = group;
        this.sequenceNames = sequenceNames;
        sequenceAbilities = new HashMap<>();
        initializeAbilities();
    }
```

- [ ] **Step 3: Build to verify it compiles**

Run: `& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q -o compile`
Expected: BUILD SUCCESS (existing pathways still use the 2-arg constructor unchanged).

- [ ] **Step 4: Commit**

```
feat(pathways): add canonical pathway groups and explicit-name constructor
```

---

### Task 2: PathwayBranding registry (TDD)

**Files:**
- Create: `src/main/java/me/vangoo/domain/PathwayBranding.java`
- Create: `src/test/java/me/vangoo/domain/PathwayBrandingTest.java`

**Interfaces:**
- Produces: `PathwayBranding.Branding(org.bukkit.Color liquid, org.bukkit.ChatColor text)`; `PathwayBranding.of(String name) → Branding`; `PathwayBranding.liquidOf(String) → Color`; `PathwayBranding.textOf(String) → ChatColor`; `PathwayBranding.NAMES → Set<String>` (all 22). Unknown/null name → neutral gray fallback.

- [ ] **Step 1: Write the failing test**

```java
package me.vangoo.domain;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PathwayBrandingTest {

    private static final String[] ALL = {
            "Error", "Visionary", "Door", "Justiciar", "WhiteTower", "Fool",
            "Sun", "Tyrant", "HangedMan", "Darkness", "Death", "TwilightGiant",
            "RedPriest", "Demoness", "Hermit", "Paragon", "Mother", "Moon",
            "Abyss", "Chained", "BlackEmperor", "WheelOfFortune"
    };

    @Test
    void everyPathwayHasBranding() {
        assertEquals(22, PathwayBranding.NAMES.size());
        for (String name : ALL) {
            assertTrue(PathwayBranding.NAMES.contains(name), "missing branding for " + name);
            assertNotNull(PathwayBranding.of(name).liquid());
            assertNotNull(PathwayBranding.of(name).text());
        }
    }

    @Test
    void unknownOrNullFallsBackToGray() {
        assertEquals(ChatColor.GRAY, PathwayBranding.of("Nonexistent").text());
        assertEquals(ChatColor.GRAY, PathwayBranding.of(null).text());
        assertEquals(Color.fromRGB(128, 128, 128), PathwayBranding.of(null).liquid());
    }

    @Test
    void preservesExistingErrorColor() {
        assertEquals(Color.fromRGB(26, 0, 181), PathwayBranding.liquidOf("Error"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q -o test -Dtest=PathwayBrandingTest`
Expected: FAIL / compile error — `PathwayBranding` does not exist.

- [ ] **Step 3: Implement PathwayBranding**

```java
package me.vangoo.domain;

import org.bukkit.ChatColor;
import org.bukkit.Color;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Єдине джерело правди для брендинг-кольору кожного шляху: колір рідини зілля
 * та ChatColor назви (зілля/Характеристики). Лежить у корені domain, де
 * org.bukkit.Color уже дозволений (як у {@link PathwayPotions}).
 */
public final class PathwayBranding {

    public record Branding(Color liquid, ChatColor text) {}

    private static final Branding FALLBACK = new Branding(Color.fromRGB(128, 128, 128), ChatColor.GRAY);

    private static final Map<String, Branding> TABLE = new LinkedHashMap<>();

    private static void put(String name, int r, int g, int b, ChatColor text) {
        TABLE.put(name, new Branding(Color.fromRGB(r, g, b), text));
    }

    static {
        // Наявні 6 (кольори збережено без змін із PotionManager)
        put("Error", 26, 0, 181, ChatColor.DARK_BLUE);
        put("Visionary", 128, 128, 128, ChatColor.GRAY);
        put("Door", 0, 0, 115, ChatColor.BLUE);
        put("Justiciar", 255, 255, 0, ChatColor.YELLOW);
        put("WhiteTower", 255, 0, 50, ChatColor.RED);
        put("Fool", 128, 0, 128, ChatColor.LIGHT_PURPLE);
        // 16 стубів
        put("Sun", 255, 190, 0, ChatColor.GOLD);
        put("Tyrant", 0, 140, 170, ChatColor.DARK_AQUA);
        put("HangedMan", 205, 130, 160, ChatColor.LIGHT_PURPLE);
        put("Darkness", 35, 25, 70, ChatColor.DARK_GRAY);
        put("Death", 150, 165, 140, ChatColor.GRAY);
        put("TwilightGiant", 150, 110, 70, ChatColor.GOLD);
        put("RedPriest", 170, 0, 0, ChatColor.DARK_RED);
        put("Demoness", 200, 0, 120, ChatColor.LIGHT_PURPLE);
        put("Hermit", 0, 175, 200, ChatColor.AQUA);
        put("Paragon", 175, 150, 90, ChatColor.YELLOW);
        put("Mother", 60, 160, 60, ChatColor.GREEN);
        put("Moon", 170, 190, 225, ChatColor.AQUA);
        put("Abyss", 80, 0, 120, ChatColor.DARK_PURPLE);
        put("Chained", 95, 95, 105, ChatColor.GRAY);
        put("BlackEmperor", 45, 0, 55, ChatColor.DARK_PURPLE);
        put("WheelOfFortune", 120, 80, 165, ChatColor.LIGHT_PURPLE);
    }

    public static final Set<String> NAMES = Set.copyOf(TABLE.keySet());

    private PathwayBranding() {}

    public static Branding of(String pathwayName) {
        if (pathwayName == null) return FALLBACK;
        return TABLE.getOrDefault(pathwayName, FALLBACK);
    }

    public static Color liquidOf(String pathwayName) {
        return of(pathwayName).liquid();
    }

    public static ChatColor textOf(String pathwayName) {
        return of(pathwayName).text();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q -o test -Dtest=PathwayBrandingTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```
feat(pathways): add PathwayBranding color registry for all 22 pathways
```

---

### Task 3: StubPathway + StubPotions classes

**Files:**
- Create: `src/main/java/me/vangoo/pathways/stub/StubPathway.java`
- Create: `src/main/java/me/vangoo/pathways/stub/StubPotions.java`
- Create: `src/test/java/me/vangoo/pathways/stub/StubPathwayTest.java`

**Interfaces:**
- Consumes: `Pathway(group, name, sequenceNames)` (Task 1).
- Produces: `new StubPathway(PathwayGroup, String name, List<String> sequenceNames)`; `new StubPotions(Pathway, org.bukkit.Color liquid, org.bukkit.ChatColor nameColor, IItemResolver)`.

- [ ] **Step 1: Write the failing test**

```java
package me.vangoo.pathways.stub;

import me.vangoo.domain.entities.PathwayGroup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StubPathwayTest {

    private static final List<String> TEN = List.of(
            "S0", "S1", "S2", "S3", "S4", "S5", "S6", "S7", "S8", "S9");

    @Test
    void nameComesFromConstructorNotClassName() {
        StubPathway p = new StubPathway(PathwayGroup.EternalDarkness, "Darkness", TEN);
        assertEquals("Darkness", p.getName());
        assertEquals(PathwayGroup.EternalDarkness, p.getGroup());
        assertEquals("S9", p.getSequenceName(9));
    }

    @Test
    void hasNoAbilities() {
        StubPathway p = new StubPathway(PathwayGroup.EternalDarkness, "Death", TEN);
        assertTrue(p.GetAbilitiesForSequence(0).isEmpty());
        assertTrue(p.GetAbilitiesForSequence(9).isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q -o test -Dtest=StubPathwayTest`
Expected: FAIL / compile error — `StubPathway` does not exist.

- [ ] **Step 3: Implement StubPathway**

```java
package me.vangoo.pathways.stub;

import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;

import java.util.List;

/**
 * Шлях-заглушка: канонічні назви послідовностей і група, але БЕЗ здібностей.
 * Дає зілля/Характеристики (через {@link StubPotions}) і робить церкви цього
 * домену функціональними до повної реалізації шляху.
 */
public class StubPathway extends Pathway {

    public StubPathway(PathwayGroup group, String name, List<String> sequenceNames) {
        super(group, name, sequenceNames);
    }

    @Override
    protected void initializeAbilities() {
        // Заглушка — здібностей ще немає.
    }
}
```

- [ ] **Step 4: Implement StubPotions**

```java
package me.vangoo.pathways.stub;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.entities.Pathway;
import org.bukkit.ChatColor;
import org.bukkit.Color;

import java.util.List;

/**
 * Зілля шляху-заглушки: колір рідини й колір назви беруться з брендингу,
 * рецептів варіння немає (зілля лише створюються, не варяться).
 */
public class StubPotions extends PathwayPotions {

    public StubPotions(Pathway pathway, Color potionColor, ChatColor nameColor, IItemResolver itemResolver) {
        super(pathway, potionColor, nameColor, List.of(), itemResolver);
        // рецепти не вантажаться — loadRecipes не викликається
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q -o test -Dtest=StubPathwayTest`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```
feat(pathways): add reusable StubPathway and StubPotions
```

---

### Task 4: Register 16 stubs + route potion colors through branding

**Files:**
- Modify: `src/main/java/me/vangoo/application/services/PathwayManager.java`
- Modify: `src/main/java/me/vangoo/application/services/PotionManager.java`
- Modify: `src/test/java/me/vangoo/domain/organizations/InstitutionRegistryTest.java`

**Interfaces:**
- Consumes: `StubPathway`, `StubPotions` (Task 3), `PathwayBranding` (Task 2), `PathwayGroup` groups (Task 1).
- Produces: `PathwayManager.getPathway(name)` returns a non-null `StubPathway` for all 16 stub names; `PotionManager.createPotionItem(stubName, seq)` works for all 22.

- [ ] **Step 1: Register the 16 stubs in PathwayManager**

In `PathwayManager.java` add imports:

```java
import me.vangoo.pathways.stub.StubPathway;
```

At the end of `initializePathways()` (after the `Fool` line) add:

```java
        registerStub("Sun", PathwayGroup.GodAlmighty, List.of(
                "Sun", "White Angel", "Light Seeker", "Justice Mentor", "Shadowless",
                "Priest of Light", "Notary", "Solar High Priest", "Light Petitioner", "Bard"));
        registerStub("Tyrant", PathwayGroup.GodAlmighty, List.of(
                "Tyrant", "God of Thunder", "Calamity", "Sea King", "Calamity Patron",
                "Ocean Songster", "Wind-Blessed", "Seafarer", "Furious One", "Sailor"));
        registerStub("HangedMan", PathwayGroup.GodAlmighty, List.of(
                "Hanged Man", "Dark Angel", "Profaner Presbyter", "Trinity Templar", "Black Knight",
                "Shepherd", "Rose Bishop", "Shadow Ascetic", "Listener", "Secrets Supplicant"));
        registerStub("Hermit", PathwayGroup.DemonOfKnowledge, List.of(
                "Hermit", "Sage", "Emperor of Knowledge", "Clairvoyant", "Mysticologist",
                "Constellation Magister", "Scroll Professor", "Warlock", "Adjacent Scholar", "Seeker of Mysteries"));
        registerStub("Paragon", PathwayGroup.DemonOfKnowledge, List.of(
                "Paragon", "Illuminator", "Knowledge Mentor", "Arcane Scholar", "Alchemist",
                "Astronomer", "Craftsman", "Appraiser", "Archaeologist", "Scholar"));
        registerStub("BlackEmperor", PathwayGroup.TheAnarchy, List.of(
                "Black Emperor", "Fallen Angel", "Duke of Entropy", "Prince of Abrogation", "Count of the Fallen",
                "Mentor of Disorder", "Baron of Corruption", "Briber", "Barbarian", "Advocate"));
        registerStub("Darkness", PathwayGroup.EternalDarkness, List.of(
                "Darkness", "Knight of Misfortune", "Concealed Servitor", "Bishop of Horror", "Nightwatcher",
                "Spirit Sorcerer", "Soul Assurer", "Nightmare", "Midnight Poet", "Sleepless"));
        registerStub("Death", PathwayGroup.EternalDarkness, List.of(
                "Death", "Pale Emperor", "Consul of Death", "Ferryman", "Undying",
                "Gatekeeper", "Spirit Usher", "Spiritualist", "Gravedigger", "Corpse Collector"));
        registerStub("TwilightGiant", PathwayGroup.EternalDarkness, List.of(
                "Twilight Giant", "Hand of God", "Glory", "Silver Knight", "Demon Hunter",
                "Guardian", "Dawn Paladin", "Weapon Master", "Pugilist", "Warrior"));
        registerStub("Mother", PathwayGroup.GoddessOfOrigin, List.of(
                "Mother", "Nature Walker", "Matriarch of Desolation", "Life Giver", "Ancient Alchemist",
                "Druid", "Biologist", "Harvest Priest", "Physician", "Gardener"));
        registerStub("Moon", PathwayGroup.GoddessOfOrigin, List.of(
                "Moon", "Goddess of Beauty", "Moon Duke", "High Sorcerer", "Shaman King",
                "Crimson Scholar", "Potions Professor", "Vampire", "Beast Tamer", "Apothecary"));
        registerStub("RedPriest", PathwayGroup.CalamityOfDestruction, List.of(
                "Red Priest", "Conqueror", "Weather Warlock", "War Bishop", "Iron-Blooded Knight",
                "Reaper", "Conspirator", "Pyromaniac", "Provoker", "Hunter"));
        registerStub("Demoness", PathwayGroup.CalamityOfDestruction, List.of(
                "Demoness", "Apocalypse", "Catastrophe", "Everlasting", "Despair",
                "Suffering", "Pleasure", "Witch", "Instigator", "Assassin"));
        registerStub("Abyss", PathwayGroup.FatherOfDevils, List.of(
                "Abyss", "Filthy Monarch", "Bloody Archduke", "Prattler", "Demon",
                "Apostle of Desire", "Devil", "Serial Killer", "Wingless Angel", "Criminal"));
        registerStub("Chained", PathwayGroup.FatherOfDevils, List.of(
                "Chained", "Abomination", "Ancient Curse", "Disciple of Silence", "Doll",
                "Ghost", "Zombie", "Werewolf", "Sleepwalker", "Prisoner"));
        registerStub("WheelOfFortune", PathwayGroup.KeyOfLight, List.of(
                "Wheel of Fortune", "Mercury Serpent", "Diviner", "Mage of Misfortune", "Chaos Walker",
                "Victor", "Priest of Misfortune", "Lucky One", "Robot", "Monster"));
```

Add the helper method (below `initializePathways()`):

```java
    private void registerStub(String name, PathwayGroup group, List<String> sequenceNames) {
        pathways.put(name, new StubPathway(group, name, sequenceNames));
    }
```

- [ ] **Step 2: Route all potion colors through branding + register 16 stub potions in PotionManager**

In `PotionManager.java` add imports:

```java
import me.vangoo.domain.PathwayBranding;
import me.vangoo.pathways.stub.StubPotions;
import java.util.Set;
```

Replace each existing hardcoded `Color.fromRGB(...)` argument with `PathwayBranding.liquidOf("<Name>")`. For example the Error block becomes:

```java
        potions.add(new ErrorPotions(
                pathwayManager.getPathway("Error"),
                PathwayBranding.liquidOf("Error"),
                customItemService,
                recipeConfig.getOrDefault("Error", Map.of())
        ));
```

Do the same for `Visionary`, `Door`, `Justiciar`, `WhiteTower`, `Fool` (use their own names). Then at the end of `initializePotions(...)` add:

```java
        Set<String> stubs = Set.of(
                "Sun", "Tyrant", "HangedMan", "Hermit", "Paragon", "BlackEmperor",
                "Darkness", "Death", "TwilightGiant", "Mother", "Moon",
                "RedPriest", "Demoness", "Abyss", "Chained", "WheelOfFortune");
        for (String name : stubs) {
            potions.add(new StubPotions(
                    pathwayManager.getPathway(name),
                    PathwayBranding.liquidOf(name),
                    PathwayBranding.textOf(name),
                    customItemService));
        }
```

- [ ] **Step 3: Update InstitutionRegistryTest pinning**

Open `InstitutionRegistryTest.java` and read its current assertions. Any test that asserts certain pathway names are "unimplemented"/absent from `PathwayManager` is now stale (all 22 resolve). Update those assertions so the canonical pathway names referenced by churches (`Darkness`, `Death`, `TwilightGiant`, `Hermit`, `Sun`, `Paragon`, `Mother`, `Moon`, `Tyrant`, `HangedMan`, `RedPriest`, `Demoness`, `BlackEmperor`, `WheelOfFortune`, `Abyss`, `Chained`) are treated as valid. Keep the church-count (10) and order-count (25) and unique-id assertions unchanged. If the test does not reference PathwayManager at all, leave it unchanged.

- [ ] **Step 4: Build + run the full test suite**

Run: `& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q -o test`
Expected: BUILD SUCCESS, all tests green (including `ArchitectureTest`, `InstitutionRegistryTest`).

- [ ] **Step 5: Commit**

```
feat(pathways): register 16 stub pathways and source potion colors from branding
```

---

### Task 5: Characteristic branding (name color + per-pathway model key)

**Files:**
- Modify: `src/main/java/me/vangoo/infrastructure/items/CharacteristicCodec.java`

**Interfaces:**
- Consumes: `PathwayBranding.textOf(name)` (Task 2).
- Produces: `CharacteristicCodec.modelKeyFor(String pathwayName)` (`"characteristic_" + lowercase name`).

- [ ] **Step 1: Add the model-key prefix + helper, replace the single MODEL_KEY**

In `CharacteristicCodec.java` add import:

```java
import me.vangoo.domain.PathwayBranding;
```

Replace the `MODEL_KEY` constant declaration with:

```java
    /**
     * Префікс ключа custom-model-data для ресурс-паку. Тепер ПЕР-ШЛЯХОВИЙ
     * ({@code characteristic_<pathway>}) — кожен шлях можна тонувати окремо.
     */
    public static final String MODEL_KEY_PREFIX = "characteristic_";

    public static String modelKeyFor(String pathwayName) {
        return (MODEL_KEY_PREFIX + pathwayName).toLowerCase(java.util.Locale.ROOT);
    }
```

- [ ] **Step 2: Use branding color for the name and the per-pathway model key**

In `create(...)`, replace the `meta.setDisplayName(...)` call with:

```java
        meta.setDisplayName(PathwayBranding.textOf(pathwayName) + "Характеристика: " + pathwayName
                + ChatColor.GRAY + " [Seq " + sequence + "]");
```

And replace the `cmd.setStrings(List.of(MODEL_KEY));` line with:

```java
            cmd.setStrings(List.of(modelKeyFor(pathwayName)));
```

- [ ] **Step 3: Build to verify it compiles**

Run: `& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q -o compile`
Expected: BUILD SUCCESS. (No unit test — Characteristic visuals are verified in-server.)

- [ ] **Step 4: Commit**

```
feat(items): tint Characteristics by pathway branding color and per-pathway model key
```

---

### Task 6: ChurchMenu — non-clickable rank + shifted donation slot

**Files:**
- Modify: `src/main/java/me/vangoo/infrastructure/ui/ChurchMenu.java`

**Interfaces:**
- Consumes: `churchService.rankThresholds()`, `churchService.membershipOf(uuid)`, `Membership.rank(int[])`, `ChurchRank.values()/displayName()` (existing).

- [ ] **Step 1: Make "Мій ранг" a non-clickable hover tile in openMain**

In `openMain(...)`, replace the `gui.setItem(2, 8, ...)` block (the `NAME_TAG` "Мій ранг" button that calls `openRank`) with:

```java
        gui.setItem(2, 8, new GuiItem(rankTile(player), e -> e.setCancelled(true)));
```

Add a private helper (place it near `openMain`):

```java
    /** Плитка рангу з усією інформацією в lore (некликабельна, все видно на наведенні). */
    private ItemStack rankTile(Player player) {
        Membership membership = churchService.membershipOf(player.getUniqueId()).orElse(null);
        if (membership == null) {
            return button(Material.NAME_TAG, ChatColor.YELLOW + "Мій ранг", "Ви не член церкви");
        }
        int[] thresholds = churchService.rankThresholds();
        ChurchRank rank = membership.rank(thresholds);
        ChurchRank[] ranks = ChurchRank.values();
        int nextIndex = rank.ordinal() + 1;
        String nextLine;
        if (nextIndex < ranks.length) {
            int needed = Math.max(0, thresholds[nextIndex] - membership.lifetimeContribution());
            nextLine = "До " + ranks[nextIndex].displayName() + ": ще " + needed + " очок вкладу";
        } else {
            nextLine = "Це найвищий ранг.";
        }
        return button(Material.NAME_TAG, ChatColor.YELLOW + "Ранг: " + rank.displayName(),
                "Вклад за весь час: " + membership.lifetimeContribution() + " очок",
                "Баланс: " + membership.balance() + " очок",
                nextLine);
    }
```

- [ ] **Step 2: Remove the now-unused openRank sub-menu**

Delete the entire `openRank(Player player, String institutionId)` method (the `// ── 6. Мій ранг` section). Confirm no other caller references it (only `openMain` did).

- [ ] **Step 3: Shift the donation "item in hand" tile one slot right**

In `openDonations(...)`, change the chest tile position from `gui.setItem(2, 3, ...)` to `gui.setItem(2, 4, ...)` (leave the coins tile at `(2, 6)` and Back at `(3, 5)`).

- [ ] **Step 4: Build to verify it compiles**

Run: `& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q -o compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```
fix(church): show rank on hover and realign donation item tile
```

---

### Task 7: DuelArenaProvider (arena world)

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/organizations/DuelArenaProvider.java`

**Interfaces:**
- Produces: `DuelArenaProvider.arenaSpawn() → Location` (player), `opponentSpawn() → Location`, `isDuelWorld(World) → boolean`, `WORLD_NAME` constant.

- [ ] **Step 1: Implement the provider (mirrors GatheringVenueProvider)**

```java
package me.vangoo.infrastructure.organizations;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;

/**
 * Void-світ для дуелі ініціації: кам'яна арена 21×21 на y=64. Ідемпотентний,
 * за зразком {@link me.vangoo.infrastructure.market.GatheringVenueProvider}.
 */
public class DuelArenaProvider {

    public static final String WORLD_NAME = "mysteries_duel";
    private static final int PLATFORM_Y = 64;
    private static final int HALF = 10;

    public Location arenaSpawn() {
        World world = getOrCreateWorld();
        return new Location(world, 0.5, PLATFORM_Y + 1, 8.5, 180f, 0f); // гравець дивиться на північ (до опонента)
    }

    public Location opponentSpawn() {
        World world = getOrCreateWorld();
        return new Location(world, 0.5, PLATFORM_Y + 1, -6.5, 0f, 0f);
    }

    public boolean isDuelWorld(World world) {
        return world != null && WORLD_NAME.equals(world.getName());
    }

    private World getOrCreateWorld() {
        World existing = Bukkit.getWorld(WORLD_NAME);
        if (existing != null) {
            return existing;
        }
        World world = new WorldCreator(WORLD_NAME)
                .environment(World.Environment.NORMAL)
                .generator(new EmptyChunkGenerator())
                .createWorld();
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setTime(15000L);
        buildArenaIfMissing(world);
        world.setSpawnLocation(0, PLATFORM_Y + 1, 0);
        return world;
    }

    private void buildArenaIfMissing(World world) {
        if (world.getBlockAt(0, PLATFORM_Y, 0).getType() == Material.POLISHED_ANDESITE) {
            return;
        }
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                world.getBlockAt(x, PLATFORM_Y, z).setType(Material.POLISHED_ANDESITE);
            }
        }
        for (int[] corner : new int[][]{{-HALF, -HALF}, {-HALF, HALF}, {HALF, -HALF}, {HALF, HALF}}) {
            world.getBlockAt(corner[0], PLATFORM_Y + 1, corner[1]).setType(Material.SOUL_LANTERN);
        }
    }

    private static final class EmptyChunkGenerator extends ChunkGenerator {
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q -o compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```
feat(church): add DuelArenaProvider void arena world
```

---

### Task 8: DuelBriefing (priest action-bar dialogue)

**Files:**
- Create: `src/main/java/me/vangoo/infrastructure/organizations/DuelBriefing.java`

**Interfaces:**
- Produces: `new DuelBriefing(Plugin, Supplier<List<Player>> audience, Runnable onComplete)`; `start()`, `cancel()`.

- [ ] **Step 1: Implement DuelBriefing (mirrors OrganizerBriefing, priest script)**

```java
package me.vangoo.infrastructure.organizations;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.function.Supplier;

/**
 * Діалог священика перед дуеллю ініціації: репліки друкуються по літері в
 * action bar. Самотіковий об'єкт (власний BukkitTask); onComplete кличеться в
 * головному потоці по завершенні. Патерн {@code OrganizerBriefing}.
 */
public class DuelBriefing {

    private static final int CHAR_TICKS = 2;
    private static final int LINE_HOLD_TICKS = 35;

    private static final List<String> SCRIPT = List.of(
            "Отже, ти прагнеш ступити на шлях нашого бога.",
            "Віра доводиться не словом, а ділом.",
            "Перед тобою постане потойбічне створіння дев'ятої послідовності.",
            "Здолай його — і шлях відкриється тобі.",
            "Впадеш — повернешся живим, та з порожніми руками.",
            "Готуйся. Випробування починається."
    );

    private final Plugin plugin;
    private final Supplier<List<Player>> audience;
    private final Runnable onComplete;

    private BukkitTask task;
    private int lineIndex;
    private int charIndex;
    private int holdTicks;

    public DuelBriefing(Plugin plugin, Supplier<List<Player>> audience, Runnable onComplete) {
        this.plugin = plugin;
        this.audience = audience;
        this.onComplete = onComplete;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, CHAR_TICKS);
    }

    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        if (lineIndex >= SCRIPT.size()) {
            finish();
            return;
        }
        String line = SCRIPT.get(lineIndex);
        String prefix = ChatColor.GOLD + "" + ChatColor.ITALIC + "Священник: " + ChatColor.WHITE;
        if (charIndex < line.length()) {
            charIndex++;
            char revealed = line.charAt(charIndex - 1);
            String shown = prefix + line.substring(0, charIndex);
            for (Player p : audience.get()) {
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(shown));
                if (revealed != ' ') {
                    p.playSound(p.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 0.3f,
                            1.2f + (float) (Math.random() * 0.3));
                }
            }
        } else if (holdTicks < LINE_HOLD_TICKS) {
            holdTicks++;
            String shown = prefix + line;
            for (Player p : audience.get()) {
                p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(shown));
            }
        } else {
            lineIndex++;
            charIndex = 0;
            holdTicks = 0;
        }
    }

    private void finish() {
        cancel();
        onComplete.run();
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q -o compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```
feat(church): add DuelBriefing priest dialogue
```

---

### Task 9: ChurchService — trial state replaces vault initiation

**Files:**
- Modify: `src/main/java/me/vangoo/application/services/ChurchService.java`
- Modify: `src/main/java/me/vangoo/infrastructure/organizations/JSONMembershipRepository.java`

**Interfaces:**
- Produces: `churchService.canStartTrial(Player) → boolean`, `hasPassedTrial(UUID) → boolean`, `markTrialPassed(UUID)`, `completeTrialInitiation(Player, String pathwayName) → boolean`, existing `initiationPathwayChoices(Player) → List<String>` (kept).
- Consumes: `potionManager.createPotionItem`, `recipeUnlockService.unlockRecipe` (existing).

- [ ] **Step 1: Add `trialPassed` to the persistence record**

In `JSONMembershipRepository.java` change `PlayerChurchData` to add the flag (Gson gives `false` for missing → backward compatible):

```java
    public record PlayerChurchData(MembershipRecord membership,
                                   long rejoinCooldownUntilEpochMillis,
                                   boolean initiationUsed,
                                   boolean trialPassed) {}
```

- [ ] **Step 2: Add the trialPassed field + hydrate/persist in ChurchService**

In `ChurchService.java` add the field next to `initiationUsed`:

```java
    private final Set<UUID> trialPassed = new HashSet<>();
```

In `hydrate()`, inside the `model.players().forEach(...)` body, after the `if (data.initiationUsed()) { initiationUsed.add(playerId); }` line add:

```java
                if (data.trialPassed()) {
                    trialPassed.add(playerId);
                }
```

In `persist()`, update the `allIds` union and the `PlayerChurchData` construction:

```java
        allIds.addAll(trialPassed);
```
```java
            players.put(id.toString(), new PlayerChurchData(mr,
                    rejoinCooldownUntil.getOrDefault(id, 0L), initiationUsed.contains(id),
                    trialPassed.contains(id)));
```

- [ ] **Step 3: Replace initiation gate + remove vault-initiation methods**

Rename/replace `canStartInitiation` with `canStartTrial` and add the trial methods. Replace the whole `// ── Ініціація без шляху (правило 9)` section (`canStartInitiation`, `startInitiation`, `claimInitiation`) with:

```java
    // ── Випробування шляху (дуель) ───────────────────────────────────────────

    public boolean canStartTrial(Player player) {
        UUID id = player.getUniqueId();
        Membership membership = memberships.get(id);
        if (membership == null) {
            return false;
        }
        return pathwayNameOf(player) == null
                && !initiationUsed.contains(id)
                && !trialPassed.contains(id);
    }

    public boolean hasPassedTrial(UUID playerId) {
        return trialPassed.contains(playerId);
    }

    /** Позначає, що гравець здолав дуель і може обрати шлях домену. */
    public void markTrialPassed(UUID playerId) {
        if (memberships.containsKey(playerId)) {
            trialPassed.add(playerId);
            persist();
        }
    }

    public List<String> initiationPathwayChoices(Player player) {
        Membership membership = memberships.get(player.getUniqueId());
        if (membership == null) {
            return List.of();
        }
        Institution church = registry.byId(membership.institutionId()).orElse(null);
        if (church == null) {
            return List.of();
        }
        return church.accesses().stream()
                .map(PathwayAccess::pathwayName)
                .filter(name -> pathwayManager.getPathway(name) != null)
                .toList();
    }

    /** Після перемоги в дуелі: видає Seq-9 зілля обраного шляху + знання рецепту. */
    public boolean completeTrialInitiation(Player player, String pathwayName) {
        UUID id = player.getUniqueId();
        Membership membership = memberships.get(id);
        if (membership == null || !trialPassed.contains(id)) {
            return false;
        }
        if (pathwayNameOf(player) != null || initiationUsed.contains(id)) {
            return false;
        }
        if (!initiationPathwayChoices(player).contains(pathwayName)) {
            return false;
        }
        giveItem(player, potionManager.createPotionItem(pathwayName, Sequence.of(9)));
        recipeUnlockService.unlockRecipe(id, pathwayName, 9);
        initiationUsed.add(id);
        trialPassed.remove(id);
        persist();
        player.sendMessage(PREFIX + ChatColor.GREEN + "Вітаємо! Ви ступили на шлях " + pathwayName + ".");
        return true;
    }
```

- [ ] **Step 4: Remove the old hunt-initiation progress block from onCreatureKilled**

In `onCreatureKilled(...)`, delete the block that reads `membership.initiationTask()` and advances it (the `ChurchTask initTask = membership.initiationTask(); ...` section). Keep the regular task loop and its `if (changed) persist();`.

- [ ] **Step 5: Require a pathway to order potions (remove the pathless-order bypass)**

In `quoteOrder(...)`, in the branch where `pathwayName == null`, replace the pathless logic with an early `return Optional.empty();` so only players who already have a pathway can order. Concretely, the `else { ... targetSeq = 9; ... }` block becomes:

```java
        } else {
            return Optional.empty();
        }
```

- [ ] **Step 6: Fix hydrate/persist references to removed initiation-task setters (if any won't compile)**

`Membership.initiationTask()/setInitiation/clearInitiation` may still be referenced in `hydrate()`/`persist()` and `toRecord`. Leave the `MembershipRecord.initiationTask/initiationPathway` fields and the `hydrate()` line `if (mr.initiationTask() != null) { membership.setInitiation(...); }` intact (they now simply stay null in practice) so the schema and `Membership` API are untouched. Only the *duel* code stops populating them. Verify compilation in the next step.

- [ ] **Step 7: Build + full tests**

Run: `& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q -o test`
Expected: BUILD SUCCESS. If a church repositories test asserts the old 3-arg `PlayerChurchData`, update it to the 4-arg form (add `false`).

- [ ] **Step 8: Commit**

```
feat(church): replace vault initiation with duel trial state
```

---

### Task 10: DuelSession + ChurchDuelService

**Files:**
- Create: `src/main/java/me/vangoo/application/services/DuelSession.java`
- Create: `src/main/java/me/vangoo/application/services/ChurchDuelService.java`

**Interfaces:**
- Consumes: `DuelArenaProvider` (Task 7), `DuelBriefing` (Task 8), `MythicCreatureGateway.spawn`, `ChurchService.{canStartTrial,markTrialPassed,registry}`, `PathwayManager.getPathway`, `Map<String,CreatureDefinition> creatureRegistry` (`.id()/.pathway()/.sequence()`), `InstitutionRegistry.byId`, `Institution.accesses()`, `PathwayAccess.pathwayName()`, `Pathway.getGroup()`.
- Produces: `ChurchDuelService.startTrial(Player, String institutionId)`, `hasActiveDuel(UUID)`, `isFrozen(UUID)`, `onPlayerLost(Player)`, `opponentDied(UUID entityUuid)`, `abandon(UUID)`, `handleStrandedOnJoin(Player)`, `endAll()`, `setTrialChoiceOpener(BiConsumer<Player,String>)`.

- [ ] **Step 1: Implement DuelSession**

```java
package me.vangoo.application.services;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * Живий стан однієї дуелі: опонент, куди повернути гравця, його попередній режим.
 * Володіє власним BukkitTask (таймаут). Ніколи не static — реєстр у ChurchDuelService.
 */
public class DuelSession {

    private static final long TIMEOUT_TICKS = 20L * 60 * 5; // 5 хв

    private final UUID playerId;
    private final String institutionId;
    private final UUID opponentUuid;
    private final Location returnLocation;
    private final GameMode previousGameMode;
    private final Runnable onTimeout;

    private BukkitTask task;

    public DuelSession(UUID playerId, String institutionId, UUID opponentUuid,
                       Location returnLocation, GameMode previousGameMode, Runnable onTimeout) {
        this.playerId = playerId;
        this.institutionId = institutionId;
        this.opponentUuid = opponentUuid;
        this.returnLocation = returnLocation;
        this.previousGameMode = previousGameMode;
        this.onTimeout = onTimeout;
    }

    public void start(org.bukkit.plugin.Plugin plugin) {
        task = Bukkit.getScheduler().runTaskLater(plugin, onTimeout, TIMEOUT_TICKS);
    }

    /** Скасовує таймаут і прибирає опонента зі світу. */
    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        Entity opponent = Bukkit.getEntity(opponentUuid);
        if (opponent != null && !opponent.isDead()) {
            opponent.remove();
        }
    }

    public UUID playerId() { return playerId; }
    public String institutionId() { return institutionId; }
    public UUID opponentUuid() { return opponentUuid; }
    public Location returnLocation() { return returnLocation; }
    public GameMode previousGameMode() { return previousGameMode; }
}
```

- [ ] **Step 2: Implement ChurchDuelService**

```java
package me.vangoo.application.services;

import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;
import me.vangoo.domain.organizations.Institution;
import me.vangoo.domain.organizations.PathwayAccess;
import me.vangoo.infrastructure.mythic.MythicCreatureGateway;
import me.vangoo.infrastructure.organizations.DuelArenaProvider;
import me.vangoo.infrastructure.organizations.DuelBriefing;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Оркестратор дуелі ініціації: телепорт в арену, діалог священика, спавн істоти
 * Seq-9 чужої групи, наслідок (перемога → вибір шляху / поразка → повернення живим).
 * Стан — лише instance-поля. Провід — ServiceContainer.
 */
public class ChurchDuelService {

    private static final String PREFIX = ChatColor.GOLD + "[Церква] " + ChatColor.RESET;

    private final Plugin plugin;
    private final ChurchService churchService;
    private final PathwayManager pathwayManager;
    private final MythicCreatureGateway gateway;
    private final DuelArenaProvider arena;
    private final Map<String, CreatureDefinition> creatureRegistry;
    private final Random random = new Random();

    private final Map<UUID, DuelSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, DuelBriefing> briefings = new ConcurrentHashMap<>();
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();

    private BiConsumer<Player, String> trialChoiceOpener;

    public ChurchDuelService(Plugin plugin, ChurchService churchService, PathwayManager pathwayManager,
                             MythicCreatureGateway gateway, DuelArenaProvider arena,
                             Map<String, CreatureDefinition> creatureRegistry) {
        this.plugin = plugin;
        this.churchService = churchService;
        this.pathwayManager = pathwayManager;
        this.gateway = gateway;
        this.arena = arena;
        this.creatureRegistry = creatureRegistry;
    }

    public void setTrialChoiceOpener(BiConsumer<Player, String> opener) {
        this.trialChoiceOpener = opener;
    }

    public boolean hasActiveDuel(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public boolean isFrozen(UUID playerId) {
        return frozen.contains(playerId);
    }

    // ── Старт ────────────────────────────────────────────────────────────────

    public void startTrial(Player player, String institutionId) {
        UUID id = player.getUniqueId();
        if (sessions.containsKey(id) || frozen.contains(id)) {
            return;
        }
        if (!churchService.canStartTrial(player)) {
            player.sendMessage(PREFIX + ChatColor.RED + "Випробування зараз недоступне.");
            return;
        }
        Institution church = churchService.registry().byId(institutionId).orElse(null);
        if (church == null) {
            return;
        }
        Optional<String> opponentId = pickOpponent(church);
        if (opponentId.isEmpty()) {
            player.sendMessage(PREFIX + ChatColor.RED + "Гідного суперника не знайдено.");
            return;
        }
        Location back = player.getLocation().clone();
        GameMode prevMode = player.getGameMode();
        player.teleport(arena.arenaSpawn());
        player.setGameMode(GameMode.SURVIVAL);
        frozen.add(id);
        DuelBriefing briefing = new DuelBriefing(plugin, () -> audienceOf(id),
                () -> onBriefingDone(id, institutionId, opponentId.get(), back, prevMode));
        briefings.put(id, briefing);
        briefing.start();
    }

    private List<Player> audienceOf(UUID id) {
        Player p = Bukkit.getPlayer(id);
        return (p != null && p.isOnline()) ? List.of(p) : List.of();
    }

    private void onBriefingDone(UUID id, String institutionId, String opponentId,
                                Location back, GameMode prevMode) {
        briefings.remove(id);
        frozen.remove(id);
        Player player = Bukkit.getPlayer(id);
        if (player == null || !player.isOnline()) {
            return; // гравець вийшов під час доповіді — нічого не спавнимо
        }
        Optional<LivingEntity> opponent = gateway.spawn(opponentId, arena.opponentSpawn());
        if (opponent.isEmpty()) {
            player.sendMessage(PREFIX + ChatColor.RED + "Суперник не з'явився. Спробуйте пізніше.");
            player.teleport(back);
            player.setGameMode(prevMode);
            return;
        }
        DuelSession session = new DuelSession(id, institutionId, opponent.get().getUniqueId(),
                back, prevMode, () -> onTimeout(id));
        sessions.put(id, session);
        session.start(plugin);
        player.sendMessage(PREFIX + ChatColor.YELLOW + "Дуель почалась. Здолай створіння!");
    }

    // ── Наслідки ───────────────────────────────────────────────────────────────

    /** Викликає DuelListener на EntityDeathEvent опонента. */
    public void opponentDied(UUID entityUuid) {
        for (DuelSession s : sessions.values()) {
            if (entityUuid.equals(s.opponentUuid())) {
                win(s);
                return;
            }
        }
    }

    private void win(DuelSession s) {
        UUID id = s.playerId();
        sessions.remove(id);
        s.cancel();
        churchService.markTrialPassed(id);
        Player p = Bukkit.getPlayer(id);
        if (p == null || !p.isOnline()) {
            return;
        }
        p.teleport(s.returnLocation());
        p.setGameMode(s.previousGameMode());
        p.sendMessage(PREFIX + ChatColor.GREEN + "Ви здолали створіння! Оберіть свій шлях у священика.");
        if (trialChoiceOpener != null) {
            String inst = s.institutionId();
            Bukkit.getScheduler().runTask(plugin, () -> trialChoiceOpener.accept(p, inst));
        }
    }

    /** Викликає DuelListener, коли смертельний удар по гравцю у дуель-світі скасовано. */
    public void onPlayerLost(Player player) {
        DuelSession s = sessions.remove(player.getUniqueId());
        if (s == null) {
            return;
        }
        frozen.remove(player.getUniqueId());
        s.cancel();
        healToFull(player);
        player.teleport(s.returnLocation());
        player.setGameMode(s.previousGameMode());
        player.sendMessage(PREFIX + ChatColor.RED + "Ви програли дуель. Поверніться, коли будете готові.");
    }

    private void onTimeout(UUID id) {
        DuelSession s = sessions.remove(id);
        if (s == null) {
            return;
        }
        frozen.remove(id);
        s.cancel();
        Player p = Bukkit.getPlayer(id);
        if (p != null && p.isOnline()) {
            healToFull(p);
            p.teleport(s.returnLocation());
            p.setGameMode(s.previousGameMode());
            p.sendMessage(PREFIX + ChatColor.RED + "Час вичерпано. Дуель завершено.");
        }
    }

    // ── Життєвий цикл (quit / crash / disable) ────────────────────────────────

    public void abandon(UUID id) {
        frozen.remove(id);
        DuelBriefing b = briefings.remove(id);
        if (b != null) {
            b.cancel();
        }
        DuelSession s = sessions.remove(id);
        if (s != null) {
            s.cancel();
        }
    }

    public void handleStrandedOnJoin(Player player) {
        if (arena.isDuelWorld(player.getWorld()) && !sessions.containsKey(player.getUniqueId())) {
            Location fallback = Bukkit.getWorlds().get(0).getSpawnLocation();
            player.teleport(fallback);
            player.setGameMode(GameMode.SURVIVAL);
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Дуель перервалась. Вас повернуто у світ.");
        }
    }

    public void endAll() {
        for (DuelSession s : new ArrayList<>(sessions.values())) {
            Player p = Bukkit.getPlayer(s.playerId());
            if (p != null && p.isOnline()) {
                p.teleport(s.returnLocation());
                p.setGameMode(s.previousGameMode());
            }
            s.cancel();
        }
        sessions.clear();
        for (DuelBriefing b : briefings.values()) {
            b.cancel();
        }
        briefings.clear();
        frozen.clear();
    }

    // ── Хелпери ────────────────────────────────────────────────────────────────

    private void healToFull(Player player) {
        double max = player.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null
                ? player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() : 20.0;
        player.setHealth(max);
        player.setFireTicks(0);
    }

    /** Seq-9 істота з групи, ЧУЖОЇ домену церкви; фолбек — будь-яка Seq-9. */
    private Optional<String> pickOpponent(Institution church) {
        Set<PathwayGroup> domainGroups = church.accesses().stream()
                .map(PathwayAccess::pathwayName)
                .map(pathwayManager::getPathway)
                .filter(Objects::nonNull)
                .map(Pathway::getGroup)
                .collect(Collectors.toCollection(HashSet::new));
        List<String> foreign = new ArrayList<>();
        List<String> anySeq9 = new ArrayList<>();
        for (CreatureDefinition c : creatureRegistry.values()) {
            if (c.sequence() != 9) {
                continue;
            }
            anySeq9.add(c.id());
            Pathway p = pathwayManager.getPathway(c.pathway());
            if (p != null && !domainGroups.contains(p.getGroup())) {
                foreign.add(c.id());
            }
        }
        List<String> pool = !foreign.isEmpty() ? foreign : anySeq9;
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(pool.get(random.nextInt(pool.size())));
    }
}
```

- [ ] **Step 3: Build to verify it compiles**

Run: `& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q -o compile`
Expected: BUILD SUCCESS. If `Attribute.GENERIC_MAX_HEALTH` is deprecated/renamed on this API, use the enum constant that exists (`GENERIC_MAX_HEALTH`); grep an existing usage in the codebase with `Grep "GENERIC_MAX_HEALTH"` and match it.

- [ ] **Step 4: Commit**

```
feat(church): add ChurchDuelService and DuelSession
```

---

### Task 11: Wire the duel — listener, ServiceContainer, menu integration, lifecycle

**Files:**
- Create: `src/main/java/me/vangoo/presentation/listeners/DuelListener.java`
- Modify: `src/main/java/me/vangoo/infrastructure/di/ServiceContainer.java`
- Modify: `src/main/java/me/vangoo/MysteriesAbovePlugin.java`
- Modify: `src/main/java/me/vangoo/infrastructure/ui/ChurchMenu.java`

**Interfaces:**
- Consumes: `ChurchDuelService` (Task 10), `MythicCreatureGateway`, `DuelArenaProvider`, `ChurchService.{canStartTrial,hasPassedTrial,completeTrialInitiation,initiationPathwayChoices}`.
- Produces: `ServiceContainer.getChurchDuelService()`, `getDuelListener()`; `ChurchMenu.setDuelService(ChurchDuelService)`, `ChurchMenu.openTrialPathwayChoice(Player, String)`.

- [ ] **Step 1: Implement DuelListener**

```java
package me.vangoo.presentation.listeners;

import me.vangoo.application.services.ChurchDuelService;
import me.vangoo.infrastructure.mythic.MythicCreatureGateway;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Події дуелі: заморозка під час доповіді, скасування смертельного удару (поразка),
 * смерть опонента (перемога), безпечне завершення при виході/вході.
 */
public class DuelListener implements Listener {

    private final ChurchDuelService duelService;
    private final MythicCreatureGateway creatures;

    public DuelListener(ChurchDuelService duelService, MythicCreatureGateway creatures) {
        this.duelService = duelService;
        this.creatures = creatures;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFrozenMove(PlayerMoveEvent event) {
        if (!duelService.isFrozen(event.getPlayer().getUniqueId())) {
            return;
        }
        if (event.getTo() != null && event.getFrom().distanceSquared(event.getTo()) > 0) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLethalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!duelService.hasActiveDuel(player.getUniqueId())) {
            return;
        }
        if (player.getHealth() - event.getFinalDamage() <= 0) {
            event.setCancelled(true);
            duelService.onPlayerLost(player);
        }
    }

    @EventHandler
    public void onOpponentDeath(EntityDeathEvent event) {
        duelService.opponentDied(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        duelService.abandon(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        duelService.handleStrandedOnJoin(event.getPlayer());
    }
}
```

- [ ] **Step 2: Wire in ServiceContainer**

In `ServiceContainer.java` add fields (near the other church fields ~line 82-106):

```java
    private me.vangoo.infrastructure.organizations.DuelArenaProvider duelArenaProvider;
    private ChurchDuelService churchDuelService;
    private me.vangoo.presentation.listeners.DuelListener duelListener;
```

In the application/UI init phase, AFTER `churchMenu` is constructed (~line 321), add:

```java
        this.duelArenaProvider = new me.vangoo.infrastructure.organizations.DuelArenaProvider();
        this.churchDuelService = new ChurchDuelService(plugin, churchService, pathwayManager,
                mythicCreatureGateway, duelArenaProvider, creatureRegistry);
        this.churchMenu.setDuelService(churchDuelService);
        this.churchDuelService.setTrialChoiceOpener(churchMenu::openTrialPathwayChoice);
        this.duelListener = new me.vangoo.presentation.listeners.DuelListener(
                churchDuelService, mythicCreatureGateway);
```

Confirm the exact field names for the gateway and creature registry by grepping in `ServiceContainer.java` (`Grep "mythicCreatureGateway\|creatureRegistry"`); use whatever those fields are actually named. Add getters near the other church getters:

```java
    public ChurchDuelService getChurchDuelService() { return churchDuelService; }
    public me.vangoo.presentation.listeners.DuelListener getDuelListener() { return duelListener; }
```

- [ ] **Step 3: Register listener + end duels on disable**

In `MysteriesAbovePlugin.java` `registerEvents()`, after the church listener registration (~line 280-281) add:

```java
        getServer().getPluginManager().registerEvents(services.getDuelListener(), this);
```

In `onDisable()`, before `services.getChurchPriestService().despawnAll();` add:

```java
        services.getChurchDuelService().endAll();
```

- [ ] **Step 4: ChurchMenu — duel start button + pathway choice after win**

In `ChurchMenu.java` add a field + setter:

```java
    private ChurchDuelService duelService;

    public void setDuelService(ChurchDuelService duelService) {
        this.duelService = duelService;
    }
```

(import `me.vangoo.application.services.ChurchDuelService`.)

In `openTasks(...)`, replace the initiation block (the `if (churchService.canStartInitiation(player)) { ... [Ініціація] ... } else { ... [Отримати зілля] ... }` section) with:

```java
        if (churchService.canStartTrial(player)) {
            gui.setItem(6, 9, new GuiItem(button(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "[Випробування шляху]",
                            "Дуель зі створінням 9 послідовності.",
                            ChatColor.RED + "Смертельно небезпечно — добре підготуйтесь!",
                            "Перемога відкриє вибір шляху домену."),
                    e -> runSynced(player, () -> confirmTrial(player, institutionId))));
        } else if (churchService.hasPassedTrial(player.getUniqueId())) {
            gui.setItem(6, 9, new GuiItem(button(Material.NETHER_STAR, ChatColor.GREEN + "[Обрати шлях]",
                            "Ви здолали випробування — оберіть свій шлях"),
                    e -> runSynced(player, () -> openTrialPathwayChoice(player, institutionId))));
        }
```

Delete the now-unused `openInitiationPathwayChoice(...)` method. Add the confirm + choice methods (near `openTasks`):

```java
    private void confirmTrial(Player player, String institutionId) {
        if (duelService == null) {
            return;
        }
        ItemStack give = button(Material.IRON_SWORD, ChatColor.RED + "Ви ризикуєте життям");
        ItemStack get = button(Material.NETHER_STAR, ChatColor.GREEN + "Право обрати шлях домену");
        confirm.open(player, give, get, "⛪ Випробування шляху",
                () -> duelService.startTrial(player, institutionId));
    }

    /** Пікер шляху домену після перемоги в дуелі. */
    public void openTrialPathwayChoice(Player player, String institutionId) {
        List<String> choices = churchService.initiationPathwayChoices(player);
        if (choices.isEmpty()) {
            player.sendMessage(PREFIX + ChatColor.RED + "Церква не пропонує шляхів для вибору.");
            return;
        }
        openPathwayPicker(player, "⛪ Оберіть свій шлях", choices,
                () -> openMain(player, institutionId),
                pathwayName -> {
                    if (!churchService.completeTrialInitiation(player, pathwayName)) {
                        player.sendMessage(PREFIX + ChatColor.RED + "Не вдалося завершити ініціацію.");
                    }
                    openMain(player, institutionId);
                });
    }
```

In `openOrder(...)`, replace the pathless branch (which built a pathway picker for ordering) so pathless players are told to take the trial first:

```java
        if (churchService.pathwayNameOf(player) != null) {
            showOrderQuote(player, institutionId, null);
        } else {
            player.sendMessage(PREFIX + ChatColor.RED
                    + "Спершу оберіть шлях через Випробування шляху.");
            runSynced(player, () -> openMain(player, institutionId));
        }
```

Remove the now-unused `showOrderQuote(..., String pathwayNameForPathless)` extra parameter handling ONLY if it becomes dead; simplest is to keep passing `null` as before. Do not change `showOrderQuote`/`placeOrder` signatures.

- [ ] **Step 5: Build + full tests**

Run: `& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q -o test`
Expected: BUILD SUCCESS, all tests green.

- [ ] **Step 6: Commit**

```
feat(church): wire initiation duel listener, menu and lifecycle
```

---

### Task 12: Documentation

**Files:**
- Modify: `.claude/rules/church-organizations.md`
- Modify: `.claude/rules/new-content-checklist.md`
- Modify: `CLAUDE.md`
- Create: `.claude/rules/pathway-branding.md`

- [ ] **Step 1: Update church-organizations.md**

Under «Ініціація», replace the vault-based initiation description with the duel:
initiation is now a **duel** — a pathless member takes «Випробування шляху», is teleported to
`mysteries_duel`, hears the priest's action-bar dialogue (`DuelBriefing`), fights a Seq-9
creature of a foreign group (`ChurchDuelService`/`DuelSession`/`DuelArenaProvider`), and on
victory chooses a domain pathway (`completeTrialInitiation` grants the Seq-9 potion + recipe
knowledge directly, no vault). Death cancels the lethal hit and returns the player with all
items. Note the `trialPassed` flag in `memberships.json`. Note that stub-domain churches no
longer brew/order (no recipes) but can still initiate via the duel.

- [ ] **Step 2: Update new-content-checklist.md**

Change «Наразі зареєстровано 6 pathways: Error, Visionary, Door, Justiciar, WhiteTower, Fool.»
to note **22 pathways registered — 6 full + 16 stubs** via one `StubPathway`/`StubPotions`
pair; stub potions/characteristics exist and are colored via `PathwayBranding` but have no brew
recipes and no abilities.

- [ ] **Step 3: Update CLAUDE.md**

In the pathways/architecture section, update the pathway count and mention `StubPathway`,
`PathwayBranding`, and the 9 `PathwayGroup`s. In the `church.*` notes, mention the duel
initiation and the `mysteries_duel` world.

- [ ] **Step 4: Create pathway-branding.md**

```markdown
# Брендинг шляхів (колір зілль і Характеристик)

Єдине джерело правди — `me.vangoo.domain.PathwayBranding` (корінь domain, де
дозволений `org.bukkit.Color`): `pathwayName → Branding(Color liquid, ChatColor text)`.

- **Зілля**: `PotionManager` бере колір рідини з `PathwayBranding.liquidOf(name)` для всіх 22.
- **Характеристики**: `CharacteristicCodec` фарбує назву `PathwayBranding.textOf(name)` і
  ставить пер-шляховий ключ моделі `characteristic_<pathway>` (lowercase) під ресурс-пак.
- Невідомий/`null` шлях → нейтральний сірий фолбек.

## Як додати/змінити колір

1. Додай/зміни рядок `put("<Name>", r, g, b, ChatColor.X)` у статичному блоці
   `PathwayBranding`. Ім'я = ключ `PathwayManager` (без пробілів).
2. Онови `PathwayBrandingTest` (кількість 22, наявність нового імені).
3. Ресурс-пак: текстура під ключ `characteristic_<name>` (lowercase) — опційно.

## Заборони

- ❌ Хардкодити `Color.fromRGB(...)` у `PotionManager`/`CharacteristicCodec` — лише через
  `PathwayBranding`.
```

- [ ] **Step 5: Commit**

```
docs(church): document duel initiation, stub pathways and pathway branding
```

---

## Self-Review Notes

- **Spec coverage:** Component 1 → Tasks 1,3,4; Component 2 → Tasks 2,4,5; Component 3 → Tasks 7,8,9,10,11; Component 4 → Task 6; docs → Task 12. All spec sections mapped.
- **Type consistency:** `PathwayBranding.{of,liquidOf,textOf,NAMES}`, `StubPathway(group,name,names)`, `StubPotions(pathway,Color,ChatColor,resolver)`, `ChurchDuelService.{startTrial,onPlayerLost,opponentDied,abandon,handleStrandedOnJoin,endAll,setTrialChoiceOpener,hasActiveDuel,isFrozen}`, `ChurchService.{canStartTrial,hasPassedTrial,markTrialPassed,completeTrialInitiation}`, `ChurchMenu.{setDuelService,openTrialPathwayChoice}` — used consistently across tasks.
- **In-server verification (post-Task 11):** on a Paper+MythicMobs+Citizens server, `/pathway` grant a stub (e.g. Darkness) → check potion/Characteristic color; join a stub-domain church while pathless → take «Випробування шляху» → confirm → arena + priest dialogue → kill creature → choose pathway → receive Seq-9 potion; die in arena → returned with items; check «Мій ранг» hover and donation tile alignment.
- **Assumptions to watch:** `Attribute.GENERIC_MAX_HEALTH` constant name (verify against codebase); exact `ServiceContainer` field names for the Mythic gateway and creature registry (grep before editing); `InstitutionRegistryTest`/church-repository test expectations after the pathway-resolution and `PlayerChurchData` schema changes.
