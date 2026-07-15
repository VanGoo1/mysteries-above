# Pathway Scaffolds + Tinting Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the shared `StubPathway`/`StubPotions` with 16 real per-pathway scaffold packages, and move potion name-color tinting into `PathwayPotions` via `PathwayBranding`.

**Architecture:** Each of the 16 unimplemented pathways gets its own package (identical in shape to real ones: `<Name>` Pathway subclass + `<Name>Potions` + empty `abilities/` package). "Not implemented" is detected by `Pathway.hasAnyAbility()` instead of `instanceof StubPathway`. `PathwayPotions` derives both liquid and name colors from `PathwayBranding`, so no `*Potions` class passes colors anymore.

**Tech Stack:** Java 21, Spigot/Bukkit (Paper) API 1.21, JUnit 5, ArchUnit. Build via IntelliJ-bundled Maven.

## Global Constraints

- **Maven command (mvn not on PATH):** `& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd"` — run from repo root in PowerShell.
- **Domain purity:** `domain` must not import `me.vangoo.pathways` (ArchitectureTest). Scaffolds live in `me.vangoo.pathways`; `hasAnyAbility()` lives in `domain.entities.Pathway`; colors go through `me.vangoo.domain.PathwayBranding` (domain root, where `org.bukkit.Color`/`ChatColor` are allowed).
- **Localization:** all user-facing strings Ukrainian; identifiers, class names, Sequence names (in `PathwayManager`) stay English.
- **Commit messages:** Conventional Commits, English only, imperative, no trailing period, no em-dash separators. End every commit body with `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`. Commit via `-F <msgfile>` (here-strings leave literal `@` in the message).
- **Class name == PathwayManager key:** e.g. class `HangedMan` (package `hangedman`) → key `"HangedMan"`. Name is derived from `getClass().getSimpleName()` via the 2-arg `Pathway(group, sequenceNames)` constructor.

**The 16 scaffold pathways** (class, package, PathwayGroup — group is passed by `PathwayManager`, not hardcoded in the class):

| Class | Package | PathwayGroup |
|-------|---------|--------------|
| `Sun` | `sun` | `GodAlmighty` |
| `Tyrant` | `tyrant` | `GodAlmighty` |
| `HangedMan` | `hangedman` | `GodAlmighty` |
| `Hermit` | `hermit` | `DemonOfKnowledge` |
| `Paragon` | `paragon` | `DemonOfKnowledge` |
| `BlackEmperor` | `blackemperor` | `TheAnarchy` |
| `Darkness` | `darkness` | `EternalDarkness` |
| `Death` | `death` | `EternalDarkness` |
| `TwilightGiant` | `twilightgiant` | `EternalDarkness` |
| `Mother` | `mother` | `GoddessOfOrigin` |
| `Moon` | `moon` | `GoddessOfOrigin` |
| `RedPriest` | `redpriest` | `CalamityOfDestruction` |
| `Demoness` | `demoness` | `CalamityOfDestruction` |
| `Abyss` | `abyss` | `FatherOfDevils` |
| `Chained` | `chained` | `FatherOfDevils` |
| `WheelOfFortune` | `wheeloffortune` | `KeyOfLight` |

---

### Task 1: `Pathway.hasAnyAbility()` domain method

**Files:**
- Modify: `src/main/java/me/vangoo/domain/entities/Pathway.java`
- Test: `src/test/java/me/vangoo/domain/entities/PathwayTest.java`

**Interfaces:**
- Produces: `public boolean Pathway.hasAnyAbility()` — `true` iff at least one sequence has a registered ability. Used by `ChurchService` (Task 2).

- [ ] **Step 1: Write the failing test**

`PathwayTest.java` already has a private test-local `StubPathway extends Pathway` helper (empty `initializeAbilities`, no Bukkit) and a `TEN` sequence-names constant — reuse them. Add these imports at the top:

```java
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
```

Add two test-local helpers (a minimal concrete `ActiveAbility` — `ActiveAbility` supplies `getType()`, so only these five methods remain — and a pathway that registers one):

```java
    /** Мінімальна конкретна здібність — лише для наповнення послідовності в тесті. */
    private static final class TestAbility extends ActiveAbility {
        @Override public String getName() { return "t"; }
        @Override public String getDescription(Sequence userSequence) { return "t"; }
        @Override public int getSpiritualityCost() { return 0; }
        @Override public int getCooldown(Sequence userSequence) { return 0; }
        @Override protected AbilityResult performExecution(IAbilityContext context) { return null; }
    }

    /** Шлях з однією здібністю на Seq 9 — для перевірки hasAnyAbility() == true. */
    private static final class OneAbilityPathway extends Pathway {
        OneAbilityPathway(List<String> sequenceNames) {
            super(PathwayGroup.LordOfMysteries, sequenceNames);
        }
        @Override
        protected void initializeAbilities() {
            sequenceAbilities.put(9, List.of(new TestAbility()));
        }
    }
```

And the two tests:

```java
    @Test
    void hasAnyAbilityIsFalseWhenNoAbilitiesRegistered() {
        assertFalse(new StubPathway(TEN).hasAnyAbility());
    }

    @Test
    void hasAnyAbilityIsTrueWhenAnAbilityIsRegistered() {
        assertTrue(new OneAbilityPathway(TEN).hasAnyAbility());
    }
```

(`performExecution` returning `null` is safe: the test only constructs the ability, never executes it. Confirm the abstract signatures against `src/main/java/me/vangoo/domain/abilities/core/Ability.java` — currently `getName()`, `getDescription(Sequence)`, `getSpiritualityCost()`, `getCooldown(Sequence)`, `getType()` (final in `ActiveAbility`), `performExecution(IAbilityContext)`.)

- [ ] **Step 2: Run test to verify it fails**

Run: `& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q -o test -Dtest=PathwayTest`
Expected: FAIL / compile error — `hasAnyAbility()` does not exist.

- [ ] **Step 3: Add the method**

In `Pathway.java`, after `GetAbilitiesForSequence(...)`:

```java
/** true, якщо бодай одна послідовність має зареєстровану здібність. */
public boolean hasAnyAbility() {
    return sequenceAbilities.values().stream().anyMatch(list -> !list.isEmpty());
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q -o test -Dtest=PathwayTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```
git add src/main/java/me/vangoo/domain/entities/Pathway.java src/test/java/me/vangoo/domain/entities/PathwayTest.java
git commit -F <msgfile>
```
Message:
```
feat(pathways): add Pathway.hasAnyAbility to detect implemented pathways

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```

---

### Task 2: Switch ChurchService gate to `hasAnyAbility()`

Decouples the church initiation-choice gate from the concrete `StubPathway` class before that class is deleted. Behavior is identical: a stub has no abilities, so `hasAnyAbility()` returns `false` exactly where `instanceof StubPathway` was `true`.

**Files:**
- Modify: `src/main/java/me/vangoo/application/services/ChurchService.java` (line ~563 and the `StubPathway` import ~line 29)

**Interfaces:**
- Consumes: `Pathway.hasAnyAbility()` (Task 1).

- [ ] **Step 1: Replace the gate**

In `ChurchService.initiationPathwayChoices(...)`, change:

```java
                .filter(name -> {
                    Pathway pathway = pathwayManager.getPathway(name);
                    return pathway != null && !(pathway instanceof StubPathway);
                })
```
to:
```java
                .filter(name -> {
                    Pathway pathway = pathwayManager.getPathway(name);
                    return pathway != null && pathway.hasAnyAbility();
                })
```

- [ ] **Step 2: Remove the now-unused import**

Delete the line `import me.vangoo.pathways.stub.StubPathway;` near the top of `ChurchService.java`.

- [ ] **Step 3: Verify compile + church tests pass**

Run: `& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q -o test -Dtest=InstitutionRegistryTest,ChurchVaultTest,ChurchTaskGeneratorTest`
Expected: PASS (compile succeeds; no behavior change).

- [ ] **Step 4: Commit**

```
git add src/main/java/me/vangoo/application/services/ChurchService.java
git commit -F <msgfile>
```
Message:
```
refactor(church): gate initiation choices on hasAnyAbility not StubPathway

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```

---

### Task 3: 16 scaffold Pathway classes, wire PathwayManager, delete StubPathway

Creates the 16 `<Name>.java` scaffolds + their empty `abilities/` packages, rewires `PathwayManager` to instantiate them via 2-arg constructors, and deletes `StubPathway` + its test. `PotionManager` still wraps these pathways in `StubPotions` (unchanged until Task 5) — that keeps the build green.

**Files:**
- Create (×16): `src/main/java/me/vangoo/pathways/<pkg>/<Name>.java`
- Create (×16): `src/main/java/me/vangoo/pathways/<pkg>/abilities/package-info.java`
- Modify: `src/main/java/me/vangoo/application/services/PathwayManager.java`
- Delete: `src/main/java/me/vangoo/pathways/stub/StubPathway.java`
- Delete: `src/test/java/me/vangoo/pathways/stub/StubPathwayTest.java`
- Modify: `src/test/java/me/vangoo/domain/organizations/InstitutionRegistryTest.java` (comment only)

**Interfaces:**
- Produces (×16): `public class <Name> extends Pathway` with `public <Name>(PathwayGroup group, List<String> sequenceNames)`. Consumed by `PathwayManager` (this task) and by the matching `<Name>Potions` (Task 5).

- [ ] **Step 1: Create the 16 Pathway scaffold classes**

For each row in the Global-Constraints table, create `src/main/java/me/vangoo/pathways/<pkg>/<Name>.java` using this exact template (substitute `<pkg>`, `<Name>`, and the Ukrainian pathway label in the javadoc):

```java
package me.vangoo.pathways.<pkg>;

import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.entities.PathwayGroup;

import java.util.List;

/**
 * <Name> Pathway (<укр. назва>) — заготовка під майбутню реалізацію.
 * Здібностей ще немає: наповнюється в {@link #initializeAbilities()}.
 * Група передається з PathwayManager.
 */
public class <Name> extends Pathway {

    public <Name>(PathwayGroup group, List<String> sequenceNames) {
        super(group, sequenceNames);
    }

    @Override
    protected void initializeAbilities() {
        // Заготовка — здібностей ще немає.
    }
}
```

Ukrainian labels to use in the javadoc: Sun=Сонце, Tyrant=Тиран, HangedMan=Повішений, Hermit=Відлюдник, Paragon=Взірець, BlackEmperor=Чорний Імператор, Darkness=Пітьма, Death=Смерть, TwilightGiant=Сутінковий Велетень, Mother=Матір, Moon=Місяць, RedPriest=Червоний Священник, Demoness=Демониця, Abyss=Безодня, Chained=Закутий, WheelOfFortune=Колесо Фортуни.

- [ ] **Step 2: Create the 16 empty `abilities/` packages**

For each `<pkg>`, create `src/main/java/me/vangoo/pathways/<pkg>/abilities/package-info.java`:

```java
/**
 * Здібності шляху <Name> (<укр. назва>). Поки що порожньо — заготовка.
 * Додавайте сюди класи, що успадковують ActiveAbility / PermanentPassiveAbility /
 * ToggleablePassiveAbility, і реєструйте їх у
 * {@link me.vangoo.pathways.<pkg>.<Name>#initializeAbilities()}.
 */
package me.vangoo.pathways.<pkg>.abilities;
```

- [ ] **Step 3: Rewire PathwayManager**

In `PathwayManager.java`:
1. Remove `import me.vangoo.pathways.stub.StubPathway;`.
2. Add 16 imports: `import me.vangoo.pathways.sun.Sun;` … `import me.vangoo.pathways.wheeloffortune.WheelOfFortune;` (one per class).
3. Replace each `registerStub("<Name>", PathwayGroup.X, List.of(...))` call with `pathways.put("<Name>", new <Name>(PathwayGroup.X, List.of(...)))`, keeping the exact same 10 sequence-name lists already present. Example for the first:

```java
        pathways.put("Sun", new Sun(PathwayGroup.GodAlmighty, List.of(
                "Sun", "White Angel", "Light Seeker", "Justice Mentor", "Shadowless",
                "Priest of Light", "Notary", "Solar High Priest", "Light Petitioner", "Bard")));
```
   (Do this for all 16, preserving every existing sequence-name list verbatim.)
4. Delete the `registerStub(...)` helper method:
```java
    private void registerStub(String name, PathwayGroup group, List<String> sequenceNames) {
        pathways.put(name, new StubPathway(group, name, sequenceNames));
    }
```

- [ ] **Step 4: Delete StubPathway and its test**

```
git rm src/main/java/me/vangoo/pathways/stub/StubPathway.java
git rm src/test/java/me/vangoo/pathways/stub/StubPathwayTest.java
```
(`StubPotions.java` stays for now — deleted in Task 5. Its javadoc does not `{@link StubPathway}`, so no broken reference.)

- [ ] **Step 5: Update the InstitutionRegistryTest comment**

In `InstitutionRegistryTest.stubOnlyChurchesAreTheKnownSet`, change the comment phrase `(не me.vangoo.pathways.stub.StubPathway)` to `(шлях без зареєстрованих здібностей — ChurchService.hasAnyAbility)`. Do not change the test logic or the `realPathways` set.

- [ ] **Step 6: Build and run the full test suite**

Run: `& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q -o test`
Expected: PASS. In particular `ArchitectureTest` (no domain→pathways leak), `InstitutionRegistryTest`, `PathwayBrandingTest`. Compilation must succeed with StubPathway gone.

- [ ] **Step 7: Commit**

```
git add -A
git commit -F <msgfile>
```
Message:
```
feat(pathways): replace shared StubPathway with 16 scaffold packages

Each unimplemented pathway now has its own package and Pathway subclass,
identical in shape to real pathways, ready to be filled in later. The
church initiation gate already keys on hasAnyAbility, so behavior is
unchanged.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```

---

### Task 4: Move tinting into PathwayPotions (branding-driven colors)

`PathwayPotions` derives both colors from `PathwayBranding`; the constructor loses its `Color` and `ChatColor` parameters. All existing `*Potions` (6 real + `StubPotions`) and `PotionManager` are updated in this one atomic compile unit. Name colors of Error/Door/Justiciar/WhiteTower/Fool change to match branding (intended).

**Files:**
- Modify: `src/main/java/me/vangoo/domain/PathwayPotions.java`
- Modify: `src/main/java/me/vangoo/pathways/error/ErrorPotions.java`
- Modify: `src/main/java/me/vangoo/pathways/visionary/VisionaryPotions.java`
- Modify: `src/main/java/me/vangoo/pathways/door/DoorPotions.java`
- Modify: `src/main/java/me/vangoo/pathways/justiciar/JusticiarPotions.java`
- Modify: `src/main/java/me/vangoo/pathways/whitetower/WhiteTowerPotions.java`
- Modify: `src/main/java/me/vangoo/pathways/fool/FoolPotions.java`
- Modify: `src/main/java/me/vangoo/pathways/stub/StubPotions.java`
- Modify: `src/main/java/me/vangoo/application/services/PotionManager.java`

**Interfaces:**
- Produces: `PathwayPotions(Pathway pathway, List<String> description, IItemResolver itemResolver)` — new 3-arg constructor; colors read internally from `PathwayBranding.of(pathway.getName())`.
- Produces (real ×6): `<Name>Potions(Pathway pathway, IItemResolver itemResolver, Map<Integer, RecipeDefinition> recipes)`.

- [ ] **Step 1: Change the PathwayPotions constructor**

In `PathwayPotions.java`, add `import me.vangoo.domain.PathwayBranding;` is unnecessary (same package). Replace the constructor:

```java
public PathwayPotions(Pathway pathway, List<String> description, IItemResolver itemResolver) {
    PathwayBranding.Branding branding = PathwayBranding.of(pathway.getName());
    this.pathway = pathway;
    this.potionColor = branding.liquid();
    this.nameColor = branding.text();
    this.description = description;
    this.itemResolver = itemResolver;
    this.ingredientsPerSequence = new HashMap<>();
    this.auxIngredientsPerSequence = new HashMap<>();
}
```
Remove the now-unused `import org.bukkit.Color;` and `import org.bukkit.ChatColor;` only if no other code in the file uses them (the getters `getPotionColor()`/`getNameColor()` return the stored fields — keep the field types `Color`/`ChatColor`, so those imports stay). Verify: `Color` and `ChatColor` are still referenced by fields and getters → keep both imports.

- [ ] **Step 2: Update the 6 real `*Potions` classes**

For each, drop the `Color potionColor` and `ChatColor nameColor` from the constructor and the `super(...)` call, and remove now-unused `org.bukkit.Color` / `org.bukkit.ChatColor` imports. Result for `ErrorPotions` (apply the same shape to all 6, each keeping only its own recipes call):

```java
package me.vangoo.pathways.error;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.brewing.RecipeDefinition;
import me.vangoo.domain.entities.Pathway;

import java.util.List;
import java.util.Map;

public class ErrorPotions extends PathwayPotions {
    public ErrorPotions(Pathway pathway, IItemResolver itemResolver,
                        Map<Integer, RecipeDefinition> recipes) {
        super(pathway, List.of(), itemResolver);
        loadRecipes(recipes);
    }
}
```
The other five are identical except package + class name: `VisionaryPotions`, `DoorPotions`, `JusticiarPotions`, `WhiteTowerPotions`, `FoolPotions`. None passes a color anymore.

- [ ] **Step 3: Update StubPotions to the new signature**

`StubPotions.java` — drop the color params (it will be deleted in Task 5, but must compile now):

```java
package me.vangoo.pathways.stub;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.entities.Pathway;

/** Зілля шляху-заглушки: кольори з брендингу, рецептів варіння немає. */
public class StubPotions extends PathwayPotions {
    public StubPotions(Pathway pathway, IItemResolver itemResolver) {
        super(pathway, List.of(), itemResolver);
    }
}
```
(Add `import java.util.List;`.)

- [ ] **Step 4: Update PotionManager wiring**

In `PotionManager.initializePotions(...)`, remove `import me.vangoo.domain.PathwayBranding;` only if unused afterward (it becomes unused — the 6 real calls and the stub loop no longer pass `PathwayBranding.liquidOf`/`textOf`). Update the 6 real calls to drop the color argument, e.g.:

```java
        potions.add(new ErrorPotions(
                pathwayManager.getPathway("Error"),
                customItemService,
                recipeConfig.getOrDefault("Error", Map.of())
        ));
```
(same for Visionary, Door, Justiciar, WhiteTower, Fool). Update the stub loop:

```java
        for (String name : stubs) {
            potions.add(new StubPotions(
                    pathwayManager.getPathway(name),
                    customItemService));
        }
```
Remove the now-unused `PathwayBranding` import if IntelliJ/compiler flags it.

- [ ] **Step 5: Build and test**

Run: `& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q -o test`
Expected: PASS. `PathwayBrandingTest` unchanged and green.

- [ ] **Step 6: Commit**

```
git add -A
git commit -F <msgfile>
```
Message:
```
refactor(pathways): derive potion name color from PathwayBranding

PathwayPotions now reads both liquid and name colors from PathwayBranding,
so no potion class hardcodes a color. Aligns Error/Door/Justiciar/
WhiteTower/Fool potion name colors with their branding (single source of
truth), matching how characteristics are already colored.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```

---

### Task 5: 16 scaffold Potions classes, rewire PotionManager, delete StubPotions

Replaces the shared `StubPotions` loop with 16 `<Name>Potions` classes wired uniformly alongside the 6 real ones.

**Files:**
- Create (×16): `src/main/java/me/vangoo/pathways/<pkg>/<Name>Potions.java`
- Modify: `src/main/java/me/vangoo/application/services/PotionManager.java`
- Delete: `src/main/java/me/vangoo/pathways/stub/StubPotions.java`

**Interfaces:**
- Consumes: `PathwayPotions(Pathway, List, IItemResolver)` (Task 4); the 16 `<Name>` classes (Task 3).
- Produces (×16): `<Name>Potions(Pathway pathway, IItemResolver itemResolver, Map<Integer, RecipeDefinition> recipes)`.

- [ ] **Step 1: Create the 16 `<Name>Potions` scaffold classes**

For each row in the Global-Constraints table, create `src/main/java/me/vangoo/pathways/<pkg>/<Name>Potions.java` using this template (identical in shape to `ErrorPotions` after Task 4):

```java
package me.vangoo.pathways.<pkg>;

import me.vangoo.domain.IItemResolver;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.brewing.RecipeDefinition;
import me.vangoo.domain.entities.Pathway;

import java.util.List;
import java.util.Map;

/** Зілля шляху <Name> — заготовка: кольори з брендингу, рецептів варіння ще немає. */
public class <Name>Potions extends PathwayPotions {
    public <Name>Potions(Pathway pathway, IItemResolver itemResolver,
                         Map<Integer, RecipeDefinition> recipes) {
        super(pathway, List.of(), itemResolver);
        loadRecipes(recipes);
    }
}
```

- [ ] **Step 2: Rewire PotionManager to use the 16 classes**

In `PotionManager.java`:
1. Remove `import me.vangoo.pathways.stub.StubPotions;`.
2. Add 16 imports: `import me.vangoo.pathways.sun.SunPotions;` … `import me.vangoo.pathways.wheeloffortune.WheelOfFortunePotions;`.
3. Delete the `Set<String> stubs = Set.of(...)` block and its `for` loop.
4. After the Fool `potions.add(...)`, add 16 explicit registrations, each following the real pattern (empty recipe map falls back via `getOrDefault`):

```java
        potions.add(new SunPotions(
                pathwayManager.getPathway("Sun"),
                customItemService,
                recipeConfig.getOrDefault("Sun", Map.of())));
```
   (Repeat for all 16 classes/keys: Sun, Tyrant, HangedMan, Hermit, Paragon, BlackEmperor, Darkness, Death, TwilightGiant, Mother, Moon, RedPriest, Demoness, Abyss, Chained, WheelOfFortune. `import java.util.Set;` becomes unused — remove it.)

- [ ] **Step 3: Delete StubPotions and the now-empty stub package**

```
git rm src/main/java/me/vangoo/pathways/stub/StubPotions.java
```
(After this the `me/vangoo/pathways/stub/` directory is empty; `git rm` leaves no tracked files there.)

- [ ] **Step 4: Build and run the full suite**

Run: `& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q -o test`
Expected: PASS. Confirm no reference to `me.vangoo.pathways.stub` remains: `git grep -n "pathways.stub"` returns nothing under `src/`.

- [ ] **Step 5: Commit**

```
git add -A
git commit -F <msgfile>
```
Message:
```
feat(pathways): give each scaffold pathway its own Potions class

Replaces the shared StubPotions loop with 16 per-pathway *Potions classes,
so all 22 pathways are wired uniformly and each is ready to receive brew
recipes when implemented.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```

---

### Task 6: Update rules and CLAUDE.md

Docs must match the code in the same branch (project rule). Removes stub references, documents the scaffold pattern and branding-driven tinting.

**Files:**
- Modify: `.claude/rules/new-content-checklist.md`
- Modify: `.claude/rules/church-organizations.md`
- Modify: `.claude/rules/pathway-branding.md`
- Modify: `CLAUDE.md`

- [ ] **Step 1: `new-content-checklist.md`**

Replace the paragraph beginning "Наразі зареєстровано 22 pathways: ... через один спільний `me.vangoo.pathways.stub.StubPathway`/`StubPotions`. Stub-шляхи мають зілля й Характеристики..." with:

```
Наразі зареєстровано 22 pathways: 6 повних (Error, Visionary, Door, Justiciar,
WhiteTower, Fool — реалізовані здібності й рецепти варіння) + 16 заготовок (Sun,
Tyrant, HangedMan, Hermit, Paragon, BlackEmperor, Darkness, Death, TwilightGiant,
Mother, Moon, RedPriest, Demoness, Abyss, Chained, WheelOfFortune). Кожна заготовка
має ВЛАСНИЙ пакет `me.vangoo.pathways.<name>` (клас `<Name> extends Pathway` з
порожнім `initializeAbilities()`, клас `<Name>Potions extends PathwayPotions`,
порожній пакет `abilities` з `package-info.java`) — ідентично реальним шляхам, під
майбутню реалізацію. Заготовки дають зілля й Характеристики (колір — з
`me.vangoo.domain.PathwayBranding`), але БЕЗ рецептів варіння й БЕЗ здібностей.
«Не реалізований» визначається методом `Pathway.hasAnyAbility()` (false, поки немає
жодної здібності), а не окремим класом-стубом.
```

- [ ] **Step 2: `church-organizations.md`**

Find the sentence mentioning the stub domain gate (the paragraph ending "…сама ініціація незалежна від сховища й рецептів церкви." and any `StubPathway` mention in the initiation section). Replace the phrase describing the stub gate so it reads that `initiationPathwayChoices` offers only pathways where `pathway.hasAnyAbility()` is true (реалізовані шляхи), instead of `!(pathway instanceof StubPathway)`. Concretely, update the "Церкви зі stub-доменом шляху…" sentence to:

```
Церкви, чий шлях ще не реалізований (`Pathway.hasAnyAbility()` == false — заготовка
без здібностей і рецептів варіння), більше НЕ варять і не приймають замовлення зілля
(нема рецептів), і не пропонуються у виборі шляху після дуелі
(`initiationPathwayChoices`), але сама дуель-ініціація лишається можливою — вона
незалежна від сховища й рецептів церкви.
```

- [ ] **Step 3: `pathway-branding.md`**

Under "Зілля", change the bullet to state that BOTH the liquid color and the name `ChatColor` come from branding, resolved inside `PathwayPotions`:

```
- **Зілля**: `PathwayPotions` бере колір рідини (`PathwayBranding.liquidOf`) і колір
  назви (`PathwayBranding.textOf`) з брендингу за іменем шляху — конструктор
  `*Potions` кольорів НЕ приймає. Стосується всіх 22 шляхів.
```
Add to the "Заборони" section:
```
- ❌ Передавати `Color`/`ChatColor` у конструктор `*Potions` — кольори резолвить
  `PathwayPotions` з `PathwayBranding` за `pathway.getName()`.
```

- [ ] **Step 4: `CLAUDE.md`**

In the Overview/pathways architecture section, replace the sentence describing "the other 16 registered pathways are no-op stubs sharing one `me.vangoo.pathways.stub.StubPathway`/`StubPotions` pair..." with:

```
The other 16 registered pathways are scaffolds: each has its own package
`me.vangoo.pathways.<name>` (a `<Name> extends Pathway` with an empty
`initializeAbilities()`, a `<Name>Potions`, and an empty `abilities` package via
`package-info.java`) — identical in shape to the 6 real pathways, ready to be filled
in. Scaffolds have potions/characteristics (colored via `PathwayBranding`) but no
abilities and no brew recipes; "not yet implemented" is detected by
`Pathway.hasAnyAbility()`. `PathwayManager.initializePathways()` wires all 22.
```
Also, in the "Adding a Pathway / Ability" or config/persistence area, remove any lingering `StubPathway`/`StubPotions` mention if present.

- [ ] **Step 5: Verify nothing else references the old stub**

Run: `git grep -n "StubPathway\|StubPotions\|pathways.stub"`
Expected: only matches inside `docs/superpowers/` historical plans/specs (acceptable — those are dated records). No matches under `src/`, `.claude/rules/`, or `CLAUDE.md`.

- [ ] **Step 6: Commit**

```
git add -A
git commit -F <msgfile>
```
Message:
```
docs(pathways): document scaffold packages and branding-driven tinting

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>
```

---

## Final Verification

- [ ] Full build + tests: `& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.1.3\plugins\maven\lib\maven3\bin\mvn.cmd" -q -o clean package`
      Expected: BUILD SUCCESS, all tests pass, shaded JAR produced.
- [ ] `git grep -n "pathways.stub"` under `src/` → no results.
- [ ] In-server (manual): copy JAR to `plugins/`, `/pathway` grant a scaffold (e.g. Sun) to a player → potion is branding-colored, no abilities unlock; a church whose domain is that scaffold does not offer it in the duel pathway choice.
- [ ] In-server (manual): potion name colors of Error/Door/Justiciar/WhiteTower/Fool now match their liquid color.
```
