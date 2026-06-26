# Економіка Характеристик — Спек 1: Фундамент (дизайн)

**Дата:** 2026-06-26
**Статус:** затверджено до реалізації
**Парасольковий роадмап:** [2026-06-26-ingredient-economy-roadmap.md](2026-06-26-ingredient-economy-roadmap.md)

## Огляд і межі

Спек 1 — це **фундамент** Економіки Характеристик. Він додає:

- тип-предмет **«Характеристика[шлях, Seq]»** (кристалічна есенція, ключована шляхом+послідовністю);
- теги **основний / допоміжний** на інгредієнтах рецепта;
- логіку **опціональної заміни** при зварюванні (1× Характеристика замість *усіх* основних);
- **інваріант луту:** Характеристики ніколи не з'являються у звичайних лут-таблицях;
- **адмін-команду** для видачі Характеристики (тимчасовий тестовий канал, доки немає джерел).

**Поза межами (наступні спеки):** канали отримання Характеристик — апекс-істоти (Спек 3) та
смерть Beyonder (Спек 2); природний форедж допоміжних і тиризація луту (Спек 4); компас/тяжіння
(Спек 5); перерозподіл і Сефіроти (Спеки 6–7).

### Інваріант зворотної сумісності

Усі наявні рецепти (`FoolPotions`, `DoorPotions`, `VisionaryPotions`, `JusticiarPotions`,
`WhiteTowerPotions`, `ErrorPotions`) працюють **без змін поведінки**: усі їхні поточні інгредієнти
стають **основними**, допоміжних поки **0**. Отже шлях «через Характеристику» для цих рецептів =
рівно `1× Характеристика` (бо допоміжних немає). Контентний ребаланс (що зробити допоміжним) —
це Спек 4 (Природа), не цей спек.

## Модель рецепта

```
РЕЦЕПТ(шлях, Seq) = [основні інгредієнти] + [допоміжні інгредієнти]
```

Зварити можна **двома шляхами** (обидва вимагають **знання рецепта** — гейт через
`RecipeUnlockService` зберігається; «без знання зварити не можна»):

1. **Класичний:** усі основні + усі допоміжні (точна мультимножина, без зайвого).
2. **Через Характеристику:** рівно `1× Характеристика[шлях, Seq]` замість *усіх* основних + ті
   самі допоміжні (і нічого зайвого).

## Архітектура (rules vs effects)

Дзеркалить еталон `SpellRecipe`/`SpellCodec`: **правило зіставлення — чистий domain з юніт-тестами**,
**ефекти (предмет, NBT, котел) — infrastructure/presentation**.

### Чистий domain — `me.vangoo.domain.brewing` (без Bukkit)

- **`BrewRecipe`** (VO):
  - поля: `String pathwayName`, `int sequence`, `Map<String,Integer> mainCounts`,
    `Map<String,Integer> auxCounts`;
  - оперує **рядковими ключами** інгредієнтів у тому ж форматі, що вже існує:
    `custom:<id>` / `vanilla:<MATERIAL>`;
  - `String characteristicKey()` → `"characteristic:" + pathwayName + ":" + sequence`;
  - `boolean matches(Map<String,Integer> provided)` → `true`, якщо виконується **рівно один** зі шляхів:
    1. **Класичний:** `provided` дорівнює об'єднанню `mainCounts` ∪ `auxCounts` як **мультимножина**
       (однакові ключі й кількості; нічого зайвого, нічого бракує);
    2. **Через Характеристику:** `provided` складається рівно з `auxCounts` **плюс** рівно
       `1× characteristicKey()` — і **нічого зайвого** (жодного основного інгредієнта, жодного
       зайвого предмета).
- **`BrewMatcher`**:
  - вхід: колекція `BrewRecipe` + `Map<String,Integer> provided`;
  - вихід: `Optional<BrewRecipe>` — перший рецепт, для якого `matches(provided)` істинний.
  - Чисто, без Bukkit, без сортувань зі станом — детермінований перебір.

> Примітка щодо `domain.brewing` й ArchUnit: пакет тримаємо Bukkit-вільним. Якщо
> `ArchitectureTest` має список «чистих» пакетів — додаємо туди `domain.brewing`. Якщо це
> ускладнює крок — лишаємо інваріант на рівні рев'ю + відсутності імпортів `org.bukkit.*`,
> як зазначено в CLAUDE.md.

### Модель рецепта в поведінковому шарі — `PathwayPotions`

`PathwayPotions` (наразі в `me.vangoo.domain`, тримає `HashMap<Integer, ItemStack[]>` основних)
розширюється:

- додати другу мапу `HashMap<Integer, ItemStack[]> auxIngredientsPerSequence` (наявна мапа =
  основні);
- новий метод `addIngredientsRecipe(int seq, List<ItemStack> main, List<ItemStack> aux)`;
- **старий** `addIngredientsRecipe(int seq, ItemStack... ingredients)` **зберігається** і означає
  «усе основне, 0 допоміжних» — тому наявні 5–6 класів *Potions не чіпаємо;
- геттери: `ItemStack[] getMainIngredients(int seq)`, `ItemStack[] getAuxiliaryIngredients(int seq)`;
  наявний `getIngredients(int seq)` повертає **об'єднання** основних+допоміжних (використовується
  книгою рецептів для відображення).

> `PathwayPotions` уже імпортує `org.bukkit.inventory.ItemStack` — це поведінковий клас, а не
> чисте ядро; додавання другої мапи не змінює його статус.

### Предмет «Характеристика» — infrastructure

- **`CharacteristicCodec`** (`me.vangoo.infrastructure.items`):
  - `ItemStack create(String pathwayName, int sequence, int amount)` — будує предмет:
    - матеріал `AMETHYST_SHARD`, `glow = true` (через ту саму техніку, що `CustomItemFactory`:
      `UNBREAKING` + `HIDE_ENCHANTS`);
    - назва: `§dХарактеристика: <Шлях> §7[Seq <N>]`;
    - лор: короткий флейвор + підказка, що замінює всі основні інгредієнти рецепта;
    - NBT (через наявний `NBTBuilder`): `characteristic_pathway` = pathwayName,
      `characteristic_sequence` = seq;
  - `boolean isCharacteristic(ItemStack)` та
    `Optional<Characteristic> read(ItemStack)` → `(pathwayName, sequence)`.
  - `Characteristic` — маленький VO/record `(String pathwayName, int sequence)` з
    `itemKey()` = `"characteristic:" + pathwayName + ":" + sequence` (узгоджено з
    `BrewRecipe.characteristicKey()`).

> «Незнищенна, рідкісна» з роадмапу трактуємо як **флейвор**: рідкість забезпечать канали-джерела
> у наступних спеках; спец-механіки незнищенності не додаємо — предмет однаково споживається при
> зварюванні.

### Інтеграція зварювання — `PotionCraftingService`

- `getItemKey(ItemStack)`: **перед** перевіркою custom-item додати гілку — якщо
  `CharacteristicCodec.isCharacteristic(item)` істинний, повернути
  `"characteristic:<pathway>:<seq>"`.
- `trycraft(UUID, List<ItemStack>)`:
  - як і зараз, перебирати всі `PathwayPotions` × послідовності 9→0;
  - **гейт знання рецепта** (`recipeUnlockService.canCraftPotion`) лишається для **обох** шляхів;
  - для кожного (шлях, seq) збирати `BrewRecipe` з `getMainIngredients`/`getAuxiliaryIngredients`
    (ключі через `getItemKey`) і викликати `BrewMatcher`/`BrewRecipe.matches(providedCounts)`;
  - старий приватний `ingredientsMatch` прибрати (замінено матчером).
- `PotionCraftingService` отримує `CharacteristicCodec` через конструктор (wiring у
  `ServiceContainer`).

### Інваріант луту

- **Захист у коді:** `LootGenerationService.createItemFromId(String itemId)` для id, що
  починається з `"characteristic:"`, повертає `null` + лог-warning. Це гарантує, що навіть
  помилковий запис у `global_loot.yml`/лут-таблиці структур **не** видасть Характеристику.
- `global_loot.yml` Характеристик не містить (і не додаємо).

### Адмін-команда

- **`/characteristic give <player> <pathway> <seq> [amount]`**:
  - право `mysteriesabove.admin` (як інші адмін-команди);
  - валідація: гравець онлайн; шлях існує (через `PotionManager.getPotionsPathway`/`PathwayManager`);
    `seq` у діапазоні 0–9;
  - видає `CharacteristicCodec.create(...)` у інвентар (надлишок — дропом під ноги);
  - таб-компліт: підкоманда `give`, імена шляхів, рівні 0–9;
  - реєстрація: запис у `plugin.yml`, конструювання в `ServiceContainer`, прив'язка в
    `MysteriesAbovePlugin.registerCommands()`.

## Тестування

- **Юніт (`BrewMatcherTest`, чистий domain, без Bukkit):**
  - класичний точний збіг основних+допоміжних → успіх;
  - брак одного допоміжного → фейл;
  - зайвий (нерозпізнаний) предмет у наборі → фейл;
  - неправильні кількості → фейл;
  - заміна Характеристикою (Характеристика + усі допоміжні) → успіх;
  - Характеристика **іншого** шляху → фейл;
  - Характеристика **іншого** рівня → фейл;
  - Характеристика + залишок будь-якого основного інгредієнта → фейл;
  - Характеристика + брак допоміжного → фейл;
  - рецепт із 0 допоміжних: лише `1× Характеристика` → успіх; `2× Характеристика` → фейл.
- **In-server (ручне):** `mvn clean package`; на сервері — розблокувати рецепт, зварити
  класично (як раніше) → успіх; видати Характеристику командою, зварити нею → успіх; спроба
  зварити без знання рецепта → фейл; перевірити, що Характеристика не випадає з луту.

## Перевикористання (не переписуємо)

- `RecipeUnlockService` + книги рецептів — гейт знання для обох шляхів зварювання.
- `NBTBuilder` — запис/читання NBT Характеристики (та сама техніка, що для custom items).
- `CustomItemFactory` — приклад техніки glow/назви/моделі для предмета Характеристики.
- `PotionCraftingListener` (котел) — точка входу зварювання; **не змінюється** (вся логіка нижче в
  сервісі/матчері).
- `PotionManager` / `PathwayManager` — резолвінг шляху за назвою для команди й рецептів.

## Файли (орієнтовно)

**Нові:**
- `domain/brewing/BrewRecipe.java`
- `domain/brewing/BrewMatcher.java`
- `domain/brewing/Characteristic.java` (VO `(pathway, seq)` + `itemKey()`)
- `infrastructure/items/CharacteristicCodec.java`
- `presentation/commands/CharacteristicCommand.java`
- `src/test/java/.../brewing/BrewMatcherTest.java`

**Змінені:**
- `domain/PathwayPotions.java` (друга мапа + перевантаження + геттери)
- `application/services/PotionCraftingService.java` (матчер + ключ Характеристики)
- `infrastructure/structures/LootGenerationService.java` (відхилення `characteristic:` id)
- `infrastructure/di/ServiceContainer.java` (wiring кодека + команди)
- `MysteriesAbovePlugin.java` (реєстрація команди)
- `src/main/resources/plugin.yml` (команда `characteristic`)
