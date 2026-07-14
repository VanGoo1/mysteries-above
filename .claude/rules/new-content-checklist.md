---
paths:
  - "src/main/java/me/vangoo/pathways/**"
  - "src/main/java/me/vangoo/application/services/PathwayManager.java"
  - "src/main/java/me/vangoo/application/services/PotionManager.java"
  - "src/main/resources/*.yml"
---

# Чеклісти нового контенту

Пропущений крок = контент, якого «не існує» в грі. Проходь список до кінця.

## Нова здібність

1. Клас у `me.vangoo.pathways.<pathway>.abilities`, база — `ActiveAbility` / `PermanentPassiveAbility` / `ToggleablePassiveAbility` / `OneTimeUseAbility`.
2. Балансні формули — у `domain` (VO або `domain.services`) з unit-тестом; у здібності лише glue + ефекти (див. правило pathway-abilities).
3. Додати в `initializeAbilities()` конкретного pathway: `sequenceAbilities.put(seq, List.of(...))`. Здібності нижчих Sequence успадковуються при advance через `AbilityTransformer`; заміна версії — через спільний `AbilityIdentity`.
4. Опис/назва/повідомлення — українською; опис через `getDescription(Sequence)` показує вже відскейлені числа (`scaleValue`).
5. Якщо здібність тримає стан — правила сесій + `cleanUp()` обов'язково.
6. Перевірка — на сервері (`/pathway` — видати шлях; `mvn clean package` → JAR у plugins).

## Новий pathway

1. Пакет `me.vangoo.pathways.<name>`: клас `<Name> extends Pathway` з `initializeAbilities()`, пакет `abilities`, клас `<Name>Potions extends PathwayPotions` (з `me.vangoo.domain`).
2. Зареєструвати в `PathwayManager.initializePathways()`: ключ-ім'я, `PathwayGroup`, список із **10 назв Sequence** (індекс = рівень: 0 = найсильніша … 9 = найслабша).
3. Зареєструвати зілля в `PotionManager.initializePotions()` (колір, `IItemResolver` → `customItemService`, рецепти з `potion-recipes.yml`).
4. Рецепти інгредієнтів — секція у `potion-recipes.yml` (читає `PotionRecipeConfigLoader`, матчить `domain.brewing.BrewMatcher`).

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

## Нова істота (полювання)

Повний механізм — `.claude/rules/mythic-creatures.md`. Коротко:

1. Моб (стати, вигляд, скіли) — у MythicMobs-паку `mythic-pack/Mobs/<pathway>.yml` (+ метаскіли в `Skills/<pathway>.yml` за потреби); новий файл — дописати в `MythicPackInstaller.PACK_FILES`.
2. Дефініція у `creatures.yml` → `CreatureConfigLoader` → реєстр у `ServiceContainer` (`creatureRegistry`).
3. Правила відбору/спавну — `domain.creatures` (`CreatureSelector`, `SpawnRule`, `SpawnDistanceGate`) — чистий домен, тестується юнітами.
4. Спавн-канали вже підключені: `NaturalCreatureSpawnListener`, `StructureCreatureSpawnListener`, `AmbientCreatureSpawner` (конфіг `creatures.*` у `config.yml`), дроп — `CreatureDeathListener` + `LootGenerationService`. Тест у грі: `/mm mobs spawn <id>`.

## Новий кастомний предмет / лут

1. Предмет — у `custom-items.yml` (`CustomItemConfigLoader` → `CustomItemRegistry`); доступ у коді тільки через `CustomItemService`. Видача: `/custom-items give`.
2. Лут — `global_loot.yml` (`LootTableConfigLoader`), генерація — `LootGenerationService` (структури, археологія, ванільні скрині).
3. Нова команда — див. чеклист у правилі wiring (plugin.yml + registerCommands).
