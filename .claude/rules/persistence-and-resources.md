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

- `recipe_unlocks.json` — `JSONRecipeUnlockRepository` + `RecipeUnlockService`.
- Маріонетки (Fool) — NPC зберігає **Citizens** у своєму `saves.yml` через `MarionetteMinionTrait`; регідрація на старті — `MarionetteRestorer` (+ фолбек-скан через 40 тіків в `onEnable`). Не знищуй NPC в `onDisable`.

## Ресурси (`src/main/resources`)

- `plugin.yml` і всі `*.yml` — **Maven-filtered**: у target потрапляє оброблена копія, тож тестуй через `mvn clean package`, а не сирі файли.
- Файли конфігів і хто їх читає: `config.yml` (в т.ч. ключі `creatures.*`) → `plugin.getConfig()` у `ServiceContainer`; `custom-items.yml` → `CustomItemConfigLoader`; `potion-recipes.yml` → `PotionRecipeConfigLoader`; `creatures.yml` → `CreatureConfigLoader` (правила спавну/луту істот — контент мобів живе в mythic-pack, див. `mythic-creatures.md`); `global_loot.yml` → `LootTableConfigLoader`.
- `mythic-pack/**` — **нефільтрований** ресурс (копіюється як є через `MythicPackInstaller`; не додавай туди Maven-плейсхолдери `${}`).
- Кожна команда мусить бути оголошена в `plugin.yml` (permission `mysteriesabove.admin`, default: op); `depend: [Citizens, MythicMobs]`, `softdepend: [CoreProtect]`.

## Залежності збірки

Shade-йдуть `glowingentities`, `EffectLib`, `triumph-gui`; `spigot-api`, `coreprotect` та MythicMobs (`io.lumine:Mythic-Dist`) — `provided` (MythicMobs — plugin-залежність через `depend`, не shade). Нову бібліотеку додавай свідомо: або в shade (росте JAR), або як plugin-залежність у `plugin.yml`.
