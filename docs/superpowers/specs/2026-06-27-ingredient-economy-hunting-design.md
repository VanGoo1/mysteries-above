# Економіка Характеристик — Спек 3: A (полювання) (дизайн)

**Дата:** 2026-06-27
**Статус:** затверджено до реалізації
**Парасольковий роадмап:** [2026-06-26-ingredient-economy-roadmap.md](2026-06-26-ingredient-economy-roadmap.md)
**Передумова:** [Спек 1 — Фундамент](2026-06-26-ingredient-economy-foundation-design.md) (тип Характеристика, інваріант луту), [Спек 2 — вилучення](2026-06-27-ingredient-economy-extraction-design.md).

## Огляд і межі

Спек 3 реалізує **канал A** роадмапу — полювання на кастомних міфічних істот як джерело
**інгредієнтів**. Істота = тегований ванільний базовий ентіті з кастомним ім'ям, статами й
лут-таблицею; її смерть дропає інгредієнти вбивці. Три канали потрапляння істот у світ:
**адмін-команда**, **природний спавн** (дика природа), **спавн біля кастомних структур**.

### Свідоме відхилення від роадмапу (важливо)

Роадмап (таблиця каналів, рядки 43–44; граф каналів) робив **апекс-істот джерелом Характеристик**.
**Це відхилено за рішенням користувача:** міфічні істоти дають **ЛИШЕ інгредієнти, ніколи
Характеристики**. Єдиним джерелом Характеристик лишається **смерть/втрата контролю Beyonder
(Спек 2)** — інваріант «сила лише вилучається з носія, не народжується з істот» стає строгим.

Поділ **common / apex** тому **не про тип дропу**, а про:
- **складність** (хп/урон/швидкість/розмір);
- **рідкість** появи;
- **Seq-рівень інгредієнтів**: common → переважно Seq 9–6; apex → Seq 5–0;
- **канал спавну**: apex тяжіє до кастомних структур, common — до дикої природи.

**Інваріант (захист у коді):** дроп істоти проходить **тільки** через наявний
`LootGenerationService.createItemFromId`, який уже **відхиляє** будь-який `characteristic:` id
(Спек 1). Тож навіть помилковий запис `characteristic:*` у `creatures.yml` фізично не видасть
Характеристику. Окремого захисту не додаємо — реюзаємо наявний.

**Поза межами (наступні спеки):** природний форедж допоміжних із трави/листя й тиризація луту
понад звичайні скрині (Спек 4); компас/тяжіння (Спек 5); перерозподіл і Сефіроти (Спеки 6–7).
Справжнє воргодж-розміщення власних структур/спавнерів **не** входить — спавн біля структур
реалізуємо піггібеком на наявне розпізнавання структур (нижче). **BossBar для апекс-істот у цьому
спеку не робимо** (можна додати пізніше через наявний `BossBarUtil`).

## Архітектура (rules vs effects)

Дзеркалить еталон `SpellRecipe`/`BrewMatcher`: **правило вибору — чистий domain з юніт-тестами**;
**ефекти (спавн ентіті, стати, вигляд, дроп) — infrastructure/presentation**, перевіряються
in-server.

### Чисте ядро — `me.vangoo.domain.creatures` (без Bukkit)

Тримаємо пакет Bukkit-вільним (тип ентіті як **рядок**, резолвиться в інфрі). Якщо
`ArchitectureTest` має список «чистих» пакетів — додаємо туди `domain.creatures`; інакше інваріант
тримається рев'ю + відсутністю імпортів `org.bukkit.*` (як `domain.brewing` у Спеку 1).

- **`CreatureTier`** — enum `COMMON`, `APEX`.
- **`CreatureStats`** (VO/record) — `double health, double damage, double speed, double scale`.
- **`SpawnRule`** (VO) — дані спавну для одного каналу:
  - `naturalBiomes: List<String>`, `naturalReplaces: List<String>` (типи ванільних мобів, які
    природний спавн може «підвищити»), `naturalChance: double`;
  - `structureKeys: List<String>` (підрядки ключів лут-таблиць структур, як у
    `VanillaStructureLootListener`), `structureChance: double`.
- **`CreatureDefinition`** (VO) — `String id`, `String baseEntityType` (ім'я ванільного
  `EntityType`), `String displayName`, `CreatureTier tier`, `CreatureStats stats`,
  `Map<String,String> equipment` (слот→item id, опц.), `String appearance` (ключ вигляду, дефолт
  `"vanilla"`), `LootTableData loot` (реюз наявного VO), `SpawnRule spawn`,
  `boolean clearVanillaDrops` (дефолт `true`).
- **`CreatureSelector`** — **правило вибору** (аналог `BrewMatcher`), чисте й тестоване:
  - `Optional<CreatureDefinition> pickForBiome(String biome, String baseEntityType, double roll)` —
    серед істот, чий `SpawnRule.naturalBiomes` містить біом і `naturalReplaces` містить базовий тип,
    зважений вибір за `naturalChance` (детермінований при поданому `roll` ∈ [0,1));
  - `Optional<CreatureDefinition> pickForStructure(String structureKey, double roll)` — серед істот,
    чий `structureKeys` збігається з ключем (через `contains`, як наявний матчинг структур),
    зважений вибір за `structureChance`.
  - Конструюється з `Collection<CreatureDefinition>`. Без Bukkit, без стану, детермінований.

### Завантаження конфіга — `CreatureConfigLoader` (`infrastructure.creatures`)

Дзеркалить `CustomItemConfigLoader` / `PotionRecipeConfigLoader`:
- `saveDefaultResource`-патерн для `creatures.yml` із теки плагіна;
- парсить у `Map<String, CreatureDefinition>`; лут-секцію — у `LootTableData`
  (реюз `LootItem`), як наявні завантажувачі;
- невідомі/невалідні id, типи ентіті чи матеріали → warning + пропуск (як у наявних
  завантажувачах). **Жодних Bukkit-резолвів типів тут не обов'язково** — зберігаємо рядки;
  валідність `EntityType`/`Material` перевіряється на спавні (warning при провалі).

### Тег істоти — `CreatureCodec` (`infrastructure.creatures`)

Патерн `WardenRemnantCodec` (PDC на сутності, не на предметі):
- конструктор приймає `Plugin` (для `NamespacedKey`);
- `void tag(Entity entity, String creatureId)` — `PersistentDataType.STRING`;
- `boolean isCreature(Entity)`;
- `Optional<String> readId(Entity)`.
Ключ: `creature_id` (namespaced плагіном).

### Спавн і вигляд — `CreatureSpawner` + `CreatureAppearance` (`infrastructure.creatures`)

- **`CreatureAppearance`** — крихітна абстракція-**шов** для майбутніх 3D-моделей:
  - `void apply(LivingEntity entity, CreatureDefinition def)`;
  - **`VanillaAppearance`** (зараз): `setCustomName(def.displayName)` + `setCustomNameVisible(true)`;
    застосувати `equipment` через `EntityEquipment`; задати `Attribute.GENERIC_SCALE` з
    `stats.scale` (1.21 API);
  - майбутнє: `ModelEngineAppearance` (ModelEngine/MEG) — підміна без зміни решти коду.
- **`CreatureSpawner`** — реюз `CreatureAppearance` + `CreatureCodec`:
  - `Optional<LivingEntity> spawn(CreatureDefinition def, Location loc)`:
    - резолвить `EntityType` з `def.baseEntityType` (warning+`empty` при провалі);
    - `world.spawnEntity(loc, type)`; якщо не `LivingEntity` — warning+видалити+`empty`;
    - застосувати стати: `GENERIC_MAX_HEALTH`+`setHealth`, `GENERIC_ATTACK_DAMAGE`,
      `GENERIC_MOVEMENT_SPEED` (через `getAttribute(...).setBaseValue`, з null-гардами);
    - `appearance.apply(...)`; `codec.tag(entity, def.id)`;
    - повернути сутність.
  - Конструюється з `Map<String,CreatureAppearance>` (за ключем `def.appearance`, дефолт vanilla),
    `CreatureCodec`, `CustomItemService` (резолв `equipment` id), `Plugin` (логер).

### Хук смерті — `CreatureDeathListener` (`presentation.listeners`)

- `@EventHandler onEntityDeath(EntityDeathEvent e)`:
  - якщо `!creatureCodec.isCreature(e.getEntity())` → вихід;
  - `CreatureDefinition def = registry.get(creatureCodec.readId(...))`; якщо нема → вихід;
  - **`if (def.clearVanillaDrops) e.getDrops().clear();`** (рішення: апекс/міфічні **замінюють**
    ванільні дропи повністю — без гнилої плоті/сідел);
  - згенерувати інгредієнти: `lootService.generateLoot(def.loot, count, false, killerBeyonder)`
    (де `count` = ролл у межах `loot.min_items/max_items`, як `VanillaStructureLootListener`);
    `killerBeyonder` = `beyonderService.getBeyonder(killer.getUniqueId())` для «розумного» реролу за
    рівнем (наявна логіка);
  - додати згенероване у `e.getDrops()` (падає на місці смерті природно).
  - **Характеристики неможливі за побудовою** (`createItemFromId` відхиляє `characteristic:`).

### Тригери спавну

1. **Команда — `CreatureCommand` (`presentation.commands`)** `/creature spawn <id> [amount]`:
   - право `mysteriesabove.admin`; ціль — локація гравця/куди дивиться;
   - таб-компліт: підкоманда `spawn` + id істот; реєстрація в `plugin.yml` +
     `MysteriesAbovePlugin.registerCommands()`.
2. **Природний — `NaturalCreatureSpawnListener` (`presentation.listeners`)**:
   - `@EventHandler onCreatureSpawn(CreatureSpawnEvent e)` з `SpawnReason.NATURAL`;
   - `CreatureSelector.pickForBiome(біом, тип, random)` → якщо є істота:
     `e.setCancelled(true)` + `spawner.spawn(def, e.getLocation())`.
3. **Структури — піггібек на наявний `LootGenerateEvent`**:
   - **розширюємо `VanillaStructureLootListener`** (там уже є мапа ключів структур і доступ до
     локації події) **або** додаємо вузький `StructureCreatureSpawnListener` поряд;
   - на `LootGenerateEvent` із ключем структури → `CreatureSelector.pickForStructure(ключ, random)` →
     `spawner.spawn(def, поряд із event.getLootContext().getLocation())`;
   - apex-істоти мають ненульовий `structureChance` для ключів `mysteries`/`nova_structures`/
     ванільних; common — здебільшого `0.0`. Дика природа лишається додатковим (рідкісним) каналом
     для тих самих істот через `naturalChance`.

### Wiring (`ServiceContainer` + `MysteriesAbovePlugin`)

- У `ServiceContainer.initializeInfrastructure()`:
  ```java
  this.creatureConfigLoader = new CreatureConfigLoader(plugin);
  this.creatureRegistry     = creatureConfigLoader.load();          // Map<String,CreatureDefinition>
  this.creatureSelector     = new CreatureSelector(creatureRegistry.values());
  this.creatureCodec        = new CreatureCodec(plugin);
  this.creatureSpawner      = new CreatureSpawner(
                                  Map.of("vanilla", new VanillaAppearance(customItemService)),
                                  creatureCodec, customItemService, plugin);
  ```
- Лістенери/команда:
  - `new CreatureDeathListener(creatureCodec, creatureRegistry, lootGenerationService, beyonderService)`;
  - `new NaturalCreatureSpawnListener(creatureSelector, creatureSpawner)`;
  - спавн біля структур — у `VanillaStructureLootListener` (інжект `creatureSelector`+`creatureSpawner`)
    або новий `StructureCreatureSpawnListener`;
  - `new CreatureCommand(creatureRegistry, creatureSpawner)`.
- Геттери + реєстрація `registerEvents(...)` / `registerCommands(...)` у `MysteriesAbovePlugin`,
  запис команди `creature` у `plugin.yml`.

## Дані: `creatures.yml`

```yaml
creatures:
  manhal_fish:
    base_entity: GUARDIAN
    display_name: "§3Манхал-риба"
    tier: common
    stats: { health: 30, damage: 6, speed: 0.25, scale: 1.2 }
    appearance: vanilla
    clear_vanilla_drops: true
    loot:
      min_items: 1
      max_items: 2
      items:
        - { id: manhal_fish_eyeball, weight: 70, min: 1, max: 2 }
        - { id: "recipe:Visionary:9", weight: 10, min: 1, max: 1 }
    spawn:
      natural: { biomes: [OCEAN, DEEP_OCEAN], replace: [GUARDIAN, DROWNED], chance: 0.03 }
      structure: { keys: [], chance: 0.0 }
  mind_dragon:
    base_entity: RAVAGER
    display_name: "§5Розумовий Дракон"
    tier: apex
    stats: { health: 200, damage: 18, speed: 0.30, scale: 1.6 }
    appearance: vanilla
    clear_vanilla_drops: true
    loot:
      min_items: 1
      max_items: 2
      items:
        - { id: adult_mind_dragon_blood, weight: 60, min: 1, max: 1 }   # Seq 5
    spawn:
      natural: { biomes: [], replace: [], chance: 0.0 }
      structure: { keys: [mysteries, nova_structures, ancient_city], chance: 0.35 }
```

- **id інгредієнтів** — ті самі custom-item id з `global_loot.yml`; `recipe:`/`potion:` дозволені,
  `characteristic:` буде відхилено наявним кодом.
- **Початковий вміст** генерую з наявних інгредієнтів `global_loot.yml`: мапінг Seq-тіра →
  `tier` (Seq 9–6 → common, Seq 5–0 → apex) із чернетковим розкладом істот; користувач вільно
  править файл.

## Тестування

- **Юніт `CreatureSelectorTest`** (чистий domain, без Bukkit):
  - `pickForBiome`: збіг біом+тип і `roll < chance` → істота; `roll >= chance` → empty;
  - біом без істот → empty; тип не в `replace` → empty;
  - `pickForStructure`: ключ збігається через `contains` → істота; інший ключ → empty;
  - зважений вибір при кількох кандидатах — детермінований при сидованому `roll`;
  - порожній реєстр → empty в обох методах.
- **In-server (ручне):** `mvn clean package`; на сервері:
  1. `/creature spawn mind_dragon` → з'являється кастомний моб (ім'я, розмір, хп); убити → падають
     **інгредієнти**, **жодної Характеристики**; ванільних дропів базового моба **немає**.
  2. Перебувати в заданому біомі → природний спавн common-істоти із заданим шансом.
  3. Активувати кастомну структуру (`mysteries`) → підвищений шанс спавну apex-істоти поряд; її
     лут — інгредієнти вищих Seq (5–0).
  4. `ArchitectureTest` зелений: нове ядро `domain.creatures` Bukkit-вільне, domain не залежить від
     behavior/infra.

## Перевикористання (не переписуємо)

- `LootGenerationService` / `LootTableData` / `LootItem` — генерація та «розумний» рерол дропу;
  інваріант відхилення `characteristic:` уже всередині.
- `VanillaStructureLootListener` — наявне розпізнавання структур і доступ до локації події (точка
  піггібеку структурного спавну).
- `WardenRemnantCodec` — патерн PDC-тега на сутності для `CreatureCodec`.
- `CustomItemConfigLoader` / `PotionRecipeConfigLoader` — патерн завантаження YAML для
  `CreatureConfigLoader`.
- `CustomItemService` — резолв id екіпа/інгредієнтів у `ItemStack`.
- `BeyonderService` — рівень убивці для «розумного» реролу луту.
- `BossBarUtil` — наявний (НЕ використовуємо в цьому спеку; зарезервовано на майбутнє для apex).

## Файли (орієнтовно)

**Нові:**
- `domain/creatures/CreatureTier.java`
- `domain/creatures/CreatureStats.java`
- `domain/creatures/SpawnRule.java`
- `domain/creatures/CreatureDefinition.java`
- `domain/creatures/CreatureSelector.java`
- `infrastructure/creatures/CreatureConfigLoader.java`
- `infrastructure/creatures/CreatureCodec.java`
- `infrastructure/creatures/CreatureSpawner.java`
- `infrastructure/creatures/CreatureAppearance.java` (+ `VanillaAppearance`)
- `presentation/listeners/CreatureDeathListener.java`
- `presentation/listeners/NaturalCreatureSpawnListener.java`
- `presentation/commands/CreatureCommand.java`
- `src/main/resources/creatures.yml`
- `src/test/java/.../creatures/CreatureSelectorTest.java`

**Змінені:**
- `presentation/listeners/VanillaStructureLootListener.java` (піггібек структурного спавну — або
  новий `StructureCreatureSpawnListener.java`)
- `infrastructure/di/ServiceContainer.java` (wiring loader/registry/selector/codec/spawner/listeners/команди + геттери)
- `MysteriesAbovePlugin.java` (реєстрація лістенерів і команди)
- `src/main/resources/plugin.yml` (команда `creature`)
- (за потреби) `ArchitectureTest` — додати `domain.creatures` до «чистих» пакетів
