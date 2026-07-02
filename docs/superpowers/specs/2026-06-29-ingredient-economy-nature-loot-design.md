# Економіка Характеристик — Спек 4: Природа (форедж) + тиризація луту (дизайн)

**Дата:** 2026-06-29
**Статус:** затверджено до реалізації
**Парасольковий роадмап:** [2026-06-26-ingredient-economy-roadmap.md](2026-06-26-ingredient-economy-roadmap.md)
**Передумова:** [Спек 1 — Фундамент](2026-06-26-ingredient-economy-foundation-design.md) (поділ main/auxiliary, інваріант луту), [Спек 3 — полювання](2026-06-27-ingredient-economy-hunting-design.md) (патерн `CreatureSelector` + ambient-спавнер).

## Огляд і межі

Спек 4 реалізує **четвертий канал постачання** роадмапу — **природний форедж допоміжних
інгредієнтів** — і **розділяє наявний лут-фон за рідкістю** (тиризація). Дві половини в одному
спеку, як у роадмапі.

**Канал форедж:** біля гравця на вегетації (трава, папороть, квіти, листя, ягідні кущі) періодично
з'являється **видима 3D-модель допоміжного інгредієнта**. Гравець б'є по ній **лівою кнопкою миші
(ЛКМ)** — інгредієнт **падає на землю** як звичайний предмет, нода зникає. Форедж дає **лише
допоміжні** інгредієнти, **біом-тематично**, і доступний **усім гравцям** (не лише Beyonder) — це
найдешевший спільний канал економіки.

**Тиризація луту:** наразі **одна** плоска `global_loot.yml` тягнеться **всіма** джерелами однаково
(і затонулий корабель, і стародавнє місто, і археологія). Це розриває зв'язок «складність контенту →
цінність нагороди». Додаємо поле **`tier`** кожному предмету (`base` / `rare`); звичайні ванільні
скрині тягнуть лише `base`, данжі/спец-структури — `base + rare`. Це **ребаланс наявного фону**, а не
нова механіка.

### Інваріанти (важливо)

- Форедж видає **тільки допоміжні** інгредієнти — id беруться з `forage.yml`, де перелічено лише
  наявні auxiliary з `potion-recipes.yml`. Жодних основних, рецептів, зілль чи Характеристик.
- **Характеристики недосяжні** ні форежем, ні тиризованим лутом: форедж не торкається лут-таблиць
  узагалі, а наявний `LootGenerationService.createItemFromId` уже відхиляє будь-який `characteristic:`
  id (Спек 1). Окремого захисту не додаємо.
- Канал форедж — **окремий найдешевший рівень** економіки; він **не** є тіром усередині
  `global_loot.yml`. У `global_loot.yml` лише два тіри: `base` (звичайні скрині) і `rare`
  (данжі/структури).

**Поза межами (наступні спеки):** компас/тяжіння до найближчого джерела Характеристики (Спек 5);
перерозподіл — ринок/церкви (Спек 6); Сефіроти (Спек 7). Жодних нових інгредієнтів чи Характеристик
у цьому спеку не вводимо — лише новий **канал** для наявних auxiliary та **ребаланс** наявного луту.

## Архітектура (rules vs effects)

Дзеркалить еталони `CreatureSelector` / `AmbientCreatureSpawner` зі Спеку 3 та `BrewMatcher` зі Спеку 1:
**правило вибору — чистий domain з юніт-тестами**; **ефекти (сутності, дроп, планувальник) —
infrastructure/presentation**, перевіряються in-server.

### Чисте ядро — `me.vangoo.domain.forage` (без Bukkit)

Тримаємо пакет Bukkit-вільним (тип вегетації/біом — рядки, резолвляться в інфрі). Якщо
`ArchitectureTest` має список «чистих» пакетів — додаємо туди `domain.forage` (як `domain.brewing`,
`domain.creatures`).

- **`ForageEntry`** (VO/record) — `String ingredientId`, `int weight`.
- **`ForageSelector`** — **правило вибору** (аналог `CreatureSelector.pickForAmbient`), чисте й
  тестоване:
  - конструюється з `Map<String biome, List<ForageEntry>>`;
  - `Optional<String> pickForBiome(String biome, double roll)` — зважений детермінований вибір id
    серед кандидатів біому за `roll ∈ [0,1)` (нормалізація за сумою ваг); `empty`, якщо для біому
    немає кандидатів;
  - без Bukkit, без стану.

### Тиризація — розширення наявного `domain.valueobjects`

- **`enum LootTier { BASE, RARE }`** (`domain.valueobjects`).
- **`LootItem`** отримує поле **`LootTier tier`** (дефолт `BASE`, якщо у YAML не вказано). Наявні
  конструктори/виклики оновлюються; відсутність `tier` у конфігу = `BASE` (зворотна сумісність даних).
- **`LootTableData.filterByTier(Set<LootTier> allowed)`** → нова `LootTableData` лише з предметами,
  чий `tier ∈ allowed`. Чисто, без Bukkit, тестовано. Наявна логіка ролу
  (`LootGenerationService.rollItem`/`generateLoot`) **не змінюється** — просто отримує відфільтровану
  таблицю.

### Завантаження конфіга — `ForageConfigLoader` (`infrastructure.forage`)

Дзеркалить `CreatureConfigLoader` / `CustomItemConfigLoader`:
- `saveDefaultResource`-патерн для `forage.yml` із теки плагіна;
- парсить секцію `spawn` (параметри спавну + список матеріалів вегетації) і секцію `biomes`
  (`Map<String, List<ForageEntry>>`);
- невідомі/невалідні id інгредієнтів логуються warning і пропускаються (як у наявних завантажувачах);
  валідність `Material` для вегетації перевіряється тут (warning + пропуск невалідних).
- повертає невеликий `ForageConfig` VO: селектор-мапу + параметри спавну + множину `Material`
  вегетації.

### Сутність-нода — `ForageNode` (`infrastructure.forage`)

Видима 3D-модель + клікабельний хітбокс. **Дві Bukkit-сутності в одній логічній ноді:**
- **`ItemDisplay`** — показує модель інгредієнта (через `customItemService.createItemStack(id)` →
  custom-model-data; за бажання — підсвітка через наявний `glowingentities`, щоб ноду було видно);
- **невидимий `ArmorStand`** на тій самій позиції — **хітбокс для ЛКМ**. На Spigot саме armor stand
  надійно ловить лівий клік через `EntityDamageByEntityEvent` (display-сутності хітбоксу не мають).
  Armor stand: `setVisible(false)`, `setGravity(false)`, `setMarker(false)` (потрібен хітбокс),
  `setBasePlate(false)`, `setArms(false)`, можна `setSmall(true)`; маніпуляцію вимикаємо (див.
  лістенер).
- обидві сутності тегуються PDC (`forage_node` = true; `forage_ingredient` = ingredientId) ключем,
  namespaced плагіном — щоб лістенер упізнав ноду й прибрав **обидві** сутності парою.
- `ForageNode` інкапсулює створення (`spawn(Location, String ingredientId, ...)`), `remove()`
  (видаляє обидві сутності) і доступ до позиції/id.

> Вибір механізму кліку (`ArmorStand`-хітбокс + `ItemDisplay`-візуал) — **ефект-шар**, перевіряється
> in-server. Якщо in-server виявиться, що одного armor stand із предметом на голові достатньо за
> візуалом — спрощуємо до однієї сутності без зміни решти коду (нода лишається абстракцією).

### Планувальник — `ForageNodeSpawner` (`infrastructure.schedulers`)

Дзеркалить `AmbientCreatureSpawner` (`start()` / `stop()`, один `BukkitTask` із `runTaskTimer`):

- **Інтервал:** `forage.spawn.interval-seconds` (дефолт 40) → тік кожні N с.
- Кожен тік, для кожного **онлайн-гравця** (Beyonder чи ні; пропускаємо spectator/creative за
  потреби — як у наявних спавнерах):
  1. **Шанс:** `random.nextDouble() < forage.spawn.chance` (дефолт 0.5); інакше пропуск. **Без
     дистанційного гейту** — форедж усюди, навіть біля бази (на відміну від ambient-істот).
  2. **Анти-накопичення:** порахувати живі ноди цього спавнера в радіусі 32 блоки від гравця;
     пропустити, якщо ≥ `forage.spawn.max-nearby` (дефолт 5).
  3. **Пошук вегетації** (§ `ForageNodeLocation`) у радіусі біля гравця; якщо не знайдено — пропуск
     тіку.
  4. Прочитати біом у знайденій точці, викликати `selector.pickForBiome(biome, random.nextDouble())`;
     якщо `empty` — пропуск.
  5. Створити `ForageNode` у знайденій позиції; зареєструвати в **інстанс-реєстрі** з міткою часу.
- **Реєстр і TTL:** інстанс-поле `Map`/список живих нод. Кожен тік прибирає ноди, старші за
  `forage.spawn.ttl-seconds` (дефолт 120). **`stop()` прибирає всі ноди** (дисципліна сесій — чистка
  на disable, як `cleanUp()` у abilities/сесій).
- try/catch на кожного гравця, щоб помилка одного не зупиняла цикл (патерн
  `AmbientCreatureSpawner` / `PassiveAbilityScheduler`).

### Пошук вегетації — `ForageNodeLocation` (`infrastructure.forage`)

Невеликий хелпер `Optional<Location> findVegetationNear(Player player, Set<Material> vegetation,
int radius)`:
- кілька випадкових спроб: випадкові зміщення `dx,dz ∈ [-radius, radius]` (дефолт radius 8–16) біля
  гравця, по вертикалі — від поверхні (`getHighestBlockAt`) донизу на кілька блоків;
- кандидат валідний, якщо тип блоку ∈ `vegetation` **і** позиція ноди (трохи над/поряд блоком) не
  всередині твердого блоку;
- повернути першу придатну точку (позицію для ноди), інакше `empty` (тік пропускається).

### Збір — `ForageHarvestListener` (`presentation.listeners`)

- `@EventHandler onForageHit(EntityDamageByEntityEvent e)`:
  - якщо нападник не `Player` → вихід;
  - якщо ціль не має PDC `forage_node` → вихід;
  - `e.setCancelled(true)`;
  - прочитати `forage_ingredient` id, зібрати `ItemStack` через `customItemService`;
  - **прибрати ноду** (обидві сутності) і **зняти її з реєстру спавнера** (нода надає метод/колбек,
    або спавнер слухає видалення — реалізаційна деталь; реєстр лишається консистентним);
  - `world.dropItem(loc, stack)` — інгредієнт **падає на землю** (не одразу в інвентар);
  - дрібний звук/партикл збору (як інші ефекти плагіна).
- Додатково `@EventHandler` на `PlayerArmorStandManipulateEvent` для PDC-нод → `cancel()` (щоб ПКМ не
  «забирав» предмет armor stand і не ламав ноду нештатно).

## Дані

### `forage.yml`

```yaml
forage:
  spawn:
    interval-seconds: 40      # як часто планувальник перевіряє гравців
    chance: 0.5               # шанс спробувати спавн ноди за тік на гравця
    max-nearby: 5             # не спавнити, якщо стільки нод цього спавнера вже в радіусі 32
    ttl-seconds: 120          # нода зникає, якщо її не зібрали
    search-radius: 12         # радіус пошуку вегетації біля гравця
    vegetation:               # на чому з'являються ноди
      - SHORT_GRASS
      - TALL_GRASS
      - FERN
      - LARGE_FERN
      - OAK_LEAVES
      - BIRCH_LEAVES
      - SPRUCE_LEAVES
      - SWEET_BERRY_BUSH
      - DANDELION
      - POPPY
      - CORNFLOWER
  biomes:                     # біом → зважена таблиця допоміжних id (усі — наявні auxiliary)
    PLAINS:
      - { id: elf_flower_petals, weight: 50 }
      - { id: autumn_crocus_essence, weight: 30 }
    SUNFLOWER_PLAINS:
      - { id: elf_flower_petals, weight: 50 }
    FOREST:
      - { id: red_chestnut_flower, weight: 40 }
      - { id: memory_flower_petals, weight: 40 }
    FLOWER_FOREST:
      - { id: memory_flower_petals, weight: 50 }
      - { id: peppermint_extract, weight: 30 }
    DESERT:
      - { id: dragon_savageland_pollen, weight: 60 }
    # ... решта біомів; чернеткова розмітка, користувач вільно править
```

- **id інгредієнтів** — лише наявні auxiliary з `potion-recipes.yml` (напр. `elf_flower_petals`,
  `autumn_crocus_essence`, `red_chestnut_flower`, `memory_flower_petals`, `peppermint_extract`,
  `dragon_savageland_pollen`, `string_grass_powder`, ...). Невідомий id → warning + пропуск.
- **Початкова розмітка біомів** — чернеткова (тематичні відповідності квітів/трав біомам); це
  **дані**, користувач вільно править файл.

### `global_loot.yml` (тиризація)

- Кожному предмету додаємо `tier: base` або `tier: rare` (відсутність = `base`).
- **Чернетка розкладу:**
  - `base` — інгредієнти Seq 9–7, рецепти 9–7, зілля 9–8;
  - `rare` — найрідкісніше: інгредієнти Seq 6–5, рецепти 6–5, зілля 7.
- Це **дані** — користувач вільно перетягує предмети між тірами.

### Джерела → тіри (у коді лістенерів)

- **`VanillaStructureLootListener`:** наявна мапа `enabledStructures` (ключ → шанс) розширюється до
  ключ → (шанс, `Set<LootTier>`):
  - звичайні скрині (`shipwreck`, `mineshaft`, `desert_pyramid`, `buried_treasure`, `ocean_ruin_*`,
    `ruined_portal`, `jungle_temple`, `simple_dungeon`, ...) → `{BASE}`;
  - данжі/спец-структури (`ancient_city`, `trial_chambers/*`, `bastion`, `end_city`, `stronghold`,
    `mansion`, `pillager_outpost`, `mysteries`, `nova_structures`) → `{BASE, RARE}`.
  - перед генерацією лістенер бере `globalLootTable.filterByTier(allowedTiers)` і передає у
    `lootService.generateLoot(...)`.
- **`ArchaeologyLootListener`:** археологія → `{BASE}` (фільтр перед генерацією). (Структури можна
  лишити дрібнішими, ніж скрині, або зберегти наявні шанси — баланс фіналізуємо в плані.)

## Wiring (`ServiceContainer` + `MysteriesAbovePlugin`)

- У `ServiceContainer.initializeInfrastructure()`:
  ```java
  this.forageConfig    = new ForageConfigLoader(plugin).load();        // ForageConfig
  this.forageSelector  = new ForageSelector(forageConfig.biomes());
  this.forageNodeSpawner = new ForageNodeSpawner(
                              plugin, forageSelector, customItemService, forageConfig);
  ```
- Лістенер: `new ForageHarvestListener(forageNodeSpawner, customItemService, plugin)` (через спавнер
  знімає ноду з реєстру).
- Геттери + реєстрація `registerEvents(...)`; виклик `forageNodeSpawner.start()` у
  `startSchedulers()` і `forageNodeSpawner.stop()` у `stopSchedulers()` (чистка нод на disable).
- `LootTableConfigLoader` оновлюється, щоб читати `tier` у `LootItem`; `VanillaStructureLootListener`
  отримує мапу тірів; `ArchaeologyLootListener` фільтрує по `{BASE}`.

## Тестування

- **Юніт (чистий domain, без Bukkit):**
  - **`ForageSelectorTest`:** вибір id за біомом; зважений детермінований вибір при сидованому `roll`;
    `empty` для невідомого/порожнього біому; одна-єдина опція завжди повертається для `roll ∈ [0,1)`.
  - **`LootTableDataTest`:** `filterByTier({BASE})` лишає лише base-предмети; `{BASE,RARE}` — усі;
    предмет без указаного `tier` трактується як `BASE`; порожній фільтр → порожня таблиця.
- **In-server (ручне):** `mvn clean package`; на сервері:
  1. Ходити різними біомами → на вегетації з'являються 3D-ноди тематичних допоміжних; ЛКМ →
     інгредієнт **падає на землю**; нода зникає.
  2. Не чіпати ноду `ttl-seconds` → зникає сама; `max-nearby` обмежує щільність; `/reload`/disable —
     усі ноди прибрані (реєстр чистий).
  3. Форедж працює для **не-Beyonder** гравця теж; **без** дистанційного гейту (працює біля спавну).
  4. Відкрити звичайну скриню (`shipwreck`) → лише `base`-предмети; активувати данж (`ancient_city`/
     `trial_chambers`) → можливі `rare`. Характеристика не випадає ніде.
  5. `ArchitectureTest` зелений: `domain.forage` Bukkit-вільний; domain не залежить від
     behavior/infra.

## Перевикористання (не переписуємо)

- `AmbientCreatureSpawner` — **взірець** планувальника (`start`/`stop`, `BukkitTask`, per-player
  try/catch, інстанс-реєстр, чистка на `stop`) для `ForageNodeSpawner`.
- `AmbientSpawnLocation` — взірець пошуку точки спавну для `ForageNodeLocation` (інша умова: блок
  вегетації замість відкритої поверхні).
- `CreatureSelector.pickForAmbient` — взірець чистого зваженого селектора для `ForageSelector`.
- `CreatureConfigLoader` / `CustomItemConfigLoader` — патерн завантаження YAML для `ForageConfigLoader`.
- `CustomItemService` — резолв id інгредієнта у `ItemStack` (для ItemDisplay і для дропу).
- `LootGenerationService` / `LootTableData` / `LootItem` — генерація луту; додаємо лише `tier`-поле та
  чистий `filterByTier`, логіку ролу не чіпаємо. Інваріант відхилення `characteristic:` уже всередині.
- `VanillaStructureLootListener` / `ArchaeologyLootListener` — наявні точки входу луту; розширюємо
  фільтром тірів, структуру лістенерів не переписуємо.
- `NBTBuilder` / PDC-патерн (`CreatureCodec`, `WardenRemnantCodec`) — тег ноди на сутності.
- `glowingentities` (зашейджено) — опційна підсвітка ноди.

## Файли (орієнтовно)

**Нові:**
- `domain/forage/ForageEntry.java`
- `domain/forage/ForageSelector.java`
- `domain/valueobjects/LootTier.java`
- `infrastructure/forage/ForageConfig.java` (+ параметри спавну, множина вегетації, мапа біомів)
- `infrastructure/forage/ForageConfigLoader.java`
- `infrastructure/forage/ForageNode.java`
- `infrastructure/forage/ForageNodeLocation.java`
- `infrastructure/schedulers/ForageNodeSpawner.java`
- `presentation/listeners/ForageHarvestListener.java`
- `src/main/resources/forage.yml`
- `src/test/java/.../forage/ForageSelectorTest.java`
- `src/test/java/.../valueobjects/LootTableDataTest.java` (якщо немає)

**Змінені:**
- `domain/valueobjects/LootItem.java` (поле `tier`, дефолт `BASE`)
- `domain/valueobjects/LootTableData.java` (метод `filterByTier`)
- `infrastructure/structures/LootTableConfigLoader.java` (читання `tier`)
- `presentation/listeners/VanillaStructureLootListener.java` (мапа ключ→(шанс, тіри) + фільтр)
- `presentation/listeners/ArchaeologyLootListener.java` (фільтр `{BASE}`)
- `infrastructure/di/ServiceContainer.java` (wiring loader/selector/spawner/listener + геттери)
- `MysteriesAbovePlugin.java` (реєстрація лістенера; `start`/`stop` спавнера у schedulers)
- `src/main/resources/global_loot.yml` (поле `tier` кожному предмету)
- (за потреби) `ArchitectureTest` — додати `domain.forage` до «чистих» пакетів
