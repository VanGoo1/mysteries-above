# Економіка Характеристик — Спек 2: B (вилучення) (дизайн)

**Дата:** 2026-06-27
**Статус:** затверджено до реалізації
**Парасольковий роадмап:** [2026-06-26-ingredient-economy-roadmap.md](2026-06-26-ingredient-economy-roadmap.md)
**Передумова:** [Спек 1 — Фундамент](2026-06-26-ingredient-economy-foundation-design.md) (тип Характеристика + `CharacteristicCodec`).

## Огляд і межі

Спек 2 реалізує **канал B** роадмапу — вилучення Характеристики при **повній втраті
контролю** носія. Сила скінченна: вона не дублюється й не з'являється з нічого — лише
**вилучається** з носія і потрапляє в обіг. Два джерела:

1. **Гравець-Beyonder втрачає контроль (рампейдж).** При трансформації у Warden гравець
   **повністю втрачає свій шлях** (уже відбувається через `removeBeyonder`). Сама Характеристика
   **не** конденсується миттєво — вона «всередині» Бешаного Warden і випадає **лише коли цього
   Warden убито**. Есенцію здобуває той, хто здолає монстра.
2. **Маріонетку вбито.** Маріонетки не рампейджать, але якщо вбити маріонетку, що несе **захоплену
   особистість Beyonder** (`MarionetteMinionTrait` з ненульовим `capturedPathway`), на місці смерті
   випадає `Характеристика[захоплений шлях, захоплений Seq]`.

**Ключове уточнення (важливо):** тригер — **втрата контролю**, а не звичайна смерть. Гравець, що
просто помер у бою, **нічого не вилучає** і зберігає свій шлях. Дроп Характеристики від рампейджу
прив'язаний до **смерті Warden**, а не до моменту трансформації.

**Поза межами (наступні спеки):** апекс-істоти як джерело Характеристик (Спек 3); природний форедж
і тиризація луту (Спек 4); компас/тяжіння (Спек 5); перерозподіл і Сефіроти (Спеки 6–7). Жодних
змін у самій rampage-механіці (тривалість, фази, порятунок) — лише додаємо вилучення есенції.

## Архітектура (rules vs effects)

«Правило» вилучення тривіальне: носій `(шлях, Seq)` конденсується рівно в **1×**
`Характеристика[шлях, Seq]`. Це вже описує VO `me.vangoo.domain.brewing.Characteristic` зі Спека 1
(`new Characteristic(pathwayName, sequence)`). **Нової domain-логіки немає** — отже, і нового
юніт-тесту немає (дзеркалить рішення Спека 1, де `CharacteristicCodec` не має headless-тесту й
перевіряється in-server). Усе нове — це **ефекти** (NBT/PDC на сутності, дроп предмета у світ), що
живуть у `infrastructure`/`presentation` і перевіряються in-server.

### Новий ефект-сервіс — `CharacteristicExtractor` (`infrastructure.items`)

Спільний для обох call-site, щоб не дублювати мінт+дроп+захист:

- `org.bukkit.entity.Item extractTo(Location loc, String pathwayName, int sequence)`:
  - мінтить предмет через наявний `CharacteristicCodec.create(pathwayName, sequence, 1)`;
  - кидає у світ: `loc.getWorld().dropItem(loc, item)`;
  - **захищає краплю** (дефіцитне ядро не має тривіально зникати): `item.setInvulnerable(true)`
    (переживає вогонь/лаву/вибух). Despawn-логіку не чіпаємо (без Paper-only API; не смітимо
    «вічними» предметами);
  - **флейвор:** короткий звук + частинка на місці (напр. `Sound.BLOCK_AMETHYST_BLOCK_CHIME` +
    `Particle.WITCH`/`SOUL`), щоб конденсація була помітною;
  - повертає створену `Item`-сутність (для можливих майбутніх потреб; виклики можуть ігнорувати).
- Конструюється з наявного `CharacteristicCodec`.

### Новий кодек тега — `WardenRemnantCodec` (`infrastructure.items`)

Записує/читає «есенцію всередині Warden» у `PersistentDataContainer` сутності (а не предмета —
тому `NBTBuilder`, що працює з `ItemStack`, тут не підходить; використовуємо PDC напряму через
`NamespacedKey`):

- конструктор приймає `Plugin` (для `NamespacedKey`);
- `void tag(Entity warden, String pathwayName, int sequence)` — пише `PersistentDataType.STRING`
  (шлях) + `PersistentDataType.INTEGER` (seq);
- `boolean isRemnant(Entity entity)` — чи має тег;
- `Optional<Characteristic> read(Entity entity)` → `(шлях, Seq)`, якщо тег присутній.

Ключі: `characteristic_remnant_pathway`, `characteristic_remnant_sequence` (namespaced плагіном).

### Хук 1 — рампейдж гравця (`RampageEventListener`)

`RampageEventListener.executeTransformation(Player)` — це ЄДИНЕ місце трансформації (за коментарем
у класі). Зміни:

- **до** `removeBeyonder` захопити носія: `Beyonder b = beyonderService.getBeyonder(id)`; якщо
  `b == null` — пропустити тегування (вже знятий);
- після спавну Warden (`warden`) **протегувати** його:
  `wardenRemnantCodec.tag(warden, b.getPathway().getName(), b.getSequenceLevel())`;
- наявні `passiveAbilityScheduler.unregisterPlayer`, `removeBeyonder`, `setHealth(0)` **лишаються
  без змін** (повна втрата шляху вже забезпечена `removeBeyonder`);
- **прибрати будь-який миттєвий дроп** Характеристики (його тут немає й не додаємо — дроп
  відкладено до смерті Warden).

`RampageEventListener` отримує `WardenRemnantCodec` через конструктор (wiring у `ServiceContainer`).

### Хук 2 — смерть Бешаного Warden (новий `RampageRemnantDeathListener`)

`RampageEventListener` **не** є Bukkit-`Listener` (його викликає `DomainEventPublisher`), тож для
`EntityDeathEvent` додаємо **окремий** Bukkit-лістенер:

- `RampageRemnantDeathListener implements Listener` (`infrastructure.listeners`), залежить від
  `WardenRemnantCodec` + `CharacteristicExtractor`;
- `@EventHandler onEntityDeath(EntityDeathEvent e)`: якщо `wardenRemnantCodec.isRemnant(e.getEntity())`
  → прочитати `Characteristic` і `extractor.extractTo(e.getEntity().getLocation(), шлях, Seq)`;
- дефолтні дропи Warden не чіпаємо (їх практично нема); екстракція — додатковий дроп.

Реєстрація: `getServer().getPluginManager().registerEvents(...)` у `MysteriesAbovePlugin.registerEvents()`.

### Хук 3 — смерть маріонетки (`MarionetteLifecycleListener`)

`MarionetteLifecycleListener.onNpcDeath(NPCDeathEvent)` уже точка входу смерті маріонетки. Оскільки
`mc.onMarionetteDeath(...)` знищує NPC, читаємо потрібне **до** виклику:

- `MarionetteMinionTrait trait = npc.getTraitNullable(MarionetteMinionTrait.class)`;
- `Location loc = npc.getStoredLocation()`;
- якщо `trait != null && trait.getCapturedPathway() != null`:
  `extractor.extractTo(loc, trait.getCapturedPathway().getName(), trait.getCapturedSequence().level())`;
- далі — наявний виклик `mc.onMarionetteDeath(event, ownerCtx)` без змін (дроп інвентарю
  лишається у `MarionettistControl`, його не чіпаємо).

`MarionetteLifecycleListener` отримує `CharacteristicExtractor` через конструктор.

### Wiring (`ServiceContainer` + `MysteriesAbovePlugin`)

- У `ServiceContainer.initializeInfrastructure()` (поряд із `characteristicCodec`, рядок ~151):
  ```java
  this.characteristicExtractor = new CharacteristicExtractor(characteristicCodec);
  this.wardenRemnantCodec      = new WardenRemnantCodec(plugin);
  ```
- Оновити конструювання лістенерів:
  - `new RampageEventListener(passiveAbilityScheduler, beyonderService, wardenRemnantCodec)`;
  - `new MarionetteLifecycleListener(abilityContextFactory, pathwayManager, characteristicExtractor)`;
  - `this.rampageRemnantDeathListener = new RampageRemnantDeathListener(wardenRemnantCodec, characteristicExtractor)`.
- Геттери: `getCharacteristicExtractor()`, `getWardenRemnantCodec()`, `getRampageRemnantDeathListener()`.
- У `MysteriesAbovePlugin.registerEvents()` додати
  `registerEvents(services.getRampageRemnantDeathListener(), this)`.

## Тестування

- **Юніт:** немає (правило тривіальне, повторно використовує `Characteristic` зі Спека 1; жодної
  чистої domain-логіки не додано). `ArchitectureTest` лишається зеленим — нові класи в
  `infrastructure`/`presentation`, domain не залежить від них.
- **In-server (ручне):**
  1. Довести гравця-Beyonder до рампейджу (sanity → extreme) → трансформація у «Бешаний <нік>»
     Warden; гравець **втратив шлях** (немає духовності/здібностей); на місці трансформації
     Характеристики **ще немає**.
  2. Вбити цього Warden → на місці смерті випадає `Характеристика[шлях, Seq]` загиблого; предмет
     **незнищенний** (кинути в лаву — не згорає).
  3. Створити маріонетку із захопленої особистості Beyonder, потім вбити її → випадає
     `Характеристика[захоплений шлях, захоплений Seq]` на місці смерті.
  4. Вбити маріонетку **без** Beyonder-особистості (ціль не була потойбічним) → Характеристики
     немає (інші дропи інвентарю — як раніше).
  5. Звичайна смерть гравця-Beyonder у бою (без рампейджу) → Характеристики немає, шлях
     збережено.

## Перевикористання (не переписуємо)

- `CharacteristicCodec` (Спек 1) — мінт предмета Характеристики.
- `Characteristic` VO (Спек 1) — представлення `(шлях, Seq)`.
- `RampageEventListener.executeTransformation` — наявна точка трансформації; розширюємо тегуванням.
- `MarionetteLifecycleListener.onNpcDeath` + `MarionetteMinionTrait` getters — наявні дані
  захопленої особистості.
- `ServiceContainer` / `MysteriesAbovePlugin.registerEvents` — наявний патерн wiring/реєстрації.

## Файли (орієнтовно)

**Нові:**
- `infrastructure/items/CharacteristicExtractor.java`
- `infrastructure/items/WardenRemnantCodec.java`
- `infrastructure/listeners/RampageRemnantDeathListener.java`

**Змінені:**
- `infrastructure/listeners/RampageEventListener.java` (тегування Warden; захоплення носія до `removeBeyonder`)
- `presentation/listeners/MarionetteLifecycleListener.java` (дроп при смерті маріонетки з Beyonder-особистістю)
- `infrastructure/di/ServiceContainer.java` (wiring двох кодеків/екстрактора + новий лістенер + геттери)
- `MysteriesAbovePlugin.java` (реєстрація `RampageRemnantDeathListener`)
