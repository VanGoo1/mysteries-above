---
paths:
  - "src/main/resources/**"
  - "src/main/java/me/vangoo/infrastructure/**"
---

# Персистентність і ресурси

## Дані гравців — `beyonders.json`

- Пише `JSONBeyonderRepository`, обгорнутий у `BatchedBeyonderRepository` (батч кожні 6000 тіків ≈ 5 хв + `saveAll()` в `onDisable`). DTO/мапери — `infrastructure.dto` / `infrastructure.mappers`.
- Зміни, зроблені напряму в `Beyonder`, потрапляють на диск лише після `BeyonderService.updateBeyonder(...)` — не забувай його після мутацій.
- **Схема JSON має лишатися зворотно сумісною**: на сервері живі дані. Нове поле — з дефолтом при читанні відсутнього значення; перейменування/видалення — тільки з міграцією при завантаженні.
- Сервер може впасти між батчами — не тримай у пам'яті критичний стан, що не переживе відкат на 5 хв.

## Інші сховища

- `recipe_unlocks.json` — `JSONRecipeUnlockRepository` + `RecipeUnlockService` (`unlockRecipe` **і** `revokeRecipe`: рецепт уміє не лише з'являтись, а й переїжджати від жертви до медіума — `.claude/rules/lingering-souls.md`).
- **Свідомо БЕЗ файлу**: реєстр душ `LingeringSouls` (Death, Посл. 7) живе лише в пам'яті — рестарт стирає душі за задумом, як і личину `TheftOfHeavenlySecrets`. Не заводь під нього сховище.
- `waypoints.json` — `WaypointStore` (морські мітки Морської Пам'яті, Tyrant; до 10 на гравця, пише після кожної мутації). Здібності дістаються сховища через `context.waypoints()` (`IWaypointContext`).
- `retinue.json` — `RetinueStore` (почет нежиті Death, Посл. 6; переживає relog і рестарт, на відміну від `LingeringSouls` вище — пише лише при виході з серверу). `UndeadRetinue` лишається простим об'єктом шляху, сховище дістається йому сеттером із `ServiceContainer`. Див. `.claude/rules/lingering-souls.md`.
- Маріонетки (Fool) — NPC зберігає **Citizens** у своєму `saves.yml` через `MarionetteMinionTrait`; регідрація на старті — `MarionetteRestorer` (+ фолбек-скан через 40 тіків в `onEnable`). Не знищуй NPC в `onDisable`.

## Ресурси (`src/main/resources`)

- `plugin.yml` і всі `*.yml` — **Maven-filtered**: у target потрапляє оброблена копія, тож тестуй через `mvn clean package`, а не сирі файли.
- Файли конфігів і хто їх читає: `config.yml` (в т.ч. ключі `creatures.*`) → `plugin.getConfig()` у `ServiceContainer`; `custom-items.yml` → `CustomItemConfigLoader`; `potion-recipes.yml` → `PotionRecipeConfigLoader`; `creatures.yml` → `CreatureConfigLoader` (правила спавну/луту істот — контент мобів живе в mythic-pack, див. `mythic-creatures.md`); `global_loot.yml` → `LootTableConfigLoader`.
- `mythic-pack/**` — **нефільтрований** ресурс (копіюється як є через `MythicPackInstaller`; не додавай туди Maven-плейсхолдери `${}`).
- `bettermodel/models/*.bbmodel` — нефільтрований ресурс (потрапляє під блок `filtering=false`, бо не `*.yml`); ставить `BetterModelInstaller` у `plugins/BetterModel/models`. Текстура вшита в `.bbmodel`, окремий `.png` не потрібен. Див. `bettermodel-models.md`.
- Кожна команда мусить бути оголошена в `plugin.yml` (permission `mysteriesabove.admin`, default: op); `depend: [Citizens, MythicMobs]`, `softdepend: [CoreProtect]`.

## Залежності збірки

Shade-йдуть `glowingentities`, `EffectLib`, `triumph-gui`; `paper-api`, `coreprotect`, MythicMobs (`io.lumine:Mythic-Dist`) та BetterModel (`io.github.toxicity188:bettermodel-bukkit-api`) — `provided` (MythicMobs — plugin-залежність через `depend`, BetterModel — через `softdepend`, обидва не shade). Нову бібліотеку додавай свідомо: або в shade (росте JAR), або як plugin-залежність у `plugin.yml`.

Увага: `bettermodel-bukkit-api` (3.x) — НЕ той самий артефакт, що легасі `io.github.toxicity188:bettermodel` (1.x, застиг на 1.15.2). Версія артефакту збігається з версією самого плагіна BetterModel.
