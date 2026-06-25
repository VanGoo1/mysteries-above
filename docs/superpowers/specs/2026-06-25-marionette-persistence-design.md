# Персистентність маріонеток (Marionettist) — дизайн

**Дата:** 2026-06-25
**Здібність:** `me.vangoo.pathways.fool.abilities.MarionettistControl` (Sequence 5, Fool/Дурень)
**Трейт:** `me.vangoo.infrastructure.citizens.MarionetteMinionTrait`

## Проблема

Маріонетки, створені через `MarionettistControl`, **не переживають перезапуск сервера**. Три причини:

1. **`cleanUp()` знищує всіх NPC при вимкненні сервера.**
   `MysteriesAbovePlugin.onDisable()` → `ServiceContainer.cleanup()` → `beyonder.cleanUpAbilities()` →
   `MarionettistControl.cleanUp()` робить `npc.destroy()` для кожної маріонетки. Тож навіть вбудована
   персистентність Citizens не допомагає — NPC явно видаляються до того, як Citizens їх збереже.
2. **`MarionetteMinionTrait` не зберігає свої поля.** Немає `@Persist` чи `load/save`, тож Citizens
   відновлює NPC-оболонку, але `ownerCasterId`, інвентар, шлях/послідовність, скін, HP губляться.
3. **Рантайм-реєстри в `MarionettistControl` не відбудовуються при старті.** Мапи `marionetteNpcs`,
   `marionetteOwner`, `strandedNpcs`, а також death-listener існують лише в пам'яті. Навіть із живим
   NPC після рестарту здібність про нього «не знає»: немає світіння, меню порожнє, ліміт не рахується,
   смерть не обробляється.

## Підхід

Використовуємо **вбудовану персистентність Citizens** (версія 2.0.43-SNAPSHOT, hard `depend`). Citizens
автоматично серіалізує всіх NPC та їхні трейти у `saves.yml` при вимкненні й відновлює при старті.
Власного JSON-репозиторію **не** додаємо — це дублювало б роботу Citizens і розсинхронізувалося б із
життєвим циклом NPC.

Потрібні чотири зміни + обробка граничних випадків.

## 1. Персистентний трейт (`MarionetteMinionTrait`)

Додаємо `@Persist`/`load(DataKey)`/`save(DataKey)`, щоб поля зберігались разом з NPC:

- **Прості поля** через `@Persist`:
  - `ownerCasterId` — як `String` (UUID.toString); при load — `UUID.fromString`.
  - `originalPlayerName`, `skinTextureValue`, `skinTextureSignature`.
  - `capturedSpirituality`, `capturedMaxSpirituality`, `capturedHealth`, `capturedMaxHealth`.
- **Шлях/послідовність** — серіалізуємо як:
  - `pathwayName` (`String`) = `capturedPathway.getName()`,
  - `sequenceLevel` (`int`) = `capturedSequence.level()`.
  Регідрація при load через `PathwayManager` (див. розділ «Залежності»).
- **`capturedInventory`** (List<ItemStack>, **зі збереженням слотів**, `null` = порожній слот) —
  серіалізація у `load/save`. Зберігаємо за індексом слота, щоб null-слоти не злипалися.
  Використовуємо механізм серіалізації ItemStack, доступний у Citizens (`ItemStorage`/DataKey), або
  Bukkit-серіалізацію в Base64 — обрати під час реалізації за тим, що round-trip-стабільне для
  кастомних NBT-предметів плагіна.
- **`capturedAbilities`** — **НЕ** серіалізуємо. Відбудовуємо детерміновано зі шляху+послідовності
  (як у живого Beyonder). Якщо шлях невідомий — порожній список.

### Регідрація домену
- `pathwayName` → `Pathway` через `PathwayManager.getPathway(name)`.
- `sequenceLevel` → `Sequence` (конструктор/VO).
- Якщо `pathwayName` порожній або шлях не знайдено → трейт лишається валідним як **не-Beyonder**
  маріонетка (`wasBeyonder() == false`), без винятків.

## 2. Не знищувати NPC при вимкненні сервера

`MarionettistControl.cleanUp()` розділяємо за наміром:

- **При вимкненні сервера** (поточний шлях виклику через `cleanUpAbilities()`):
  - скасувати всі фонові таски (`possessionMonitors`, `masterTask`, `activeSessions`);
  - зробити **авто-swapOut** для кожного гравця, що зараз керує маріонеткою — відновити його тіло,
    інвентар, особистість (`restoreIdentity`) і скін, щоб коректно зберегтись у `beyonders.json`
    (підтверджено користувачем: «Авто-вихід при вимкненні»);
  - **NPC лишити живими** — Citizens їх збереже. Очистити лише in-memory мапи.
- **`npc.destroy()`** викликається **лише** при явному звільненні (`releaseMarionette`) або смерті
  маріонетки — як і зараз.

> Примітка: чинний `cleanUp()` уже undisguise-ить і повертає нік, але **не** робить повний swapOut
> (не відновлює інвентар/особистість). Авто-swapOut виправляє цей латентний баг заразом.

## 3. Відбудова реєстрів при старті (`MarionetteRestorer`)

Новий компонент `me.vangoo.infrastructure.citizens.MarionetteRestorer`, що сканує
`CitizensAPI.getNPCRegistry()`: для кожного NPC із `MarionetteMinionTrait` повертає у
`MarionettistControl` його `npcId` + `ownerCasterId` через **нову публічну метод**
`registerLoadedMarionette(int npcId, UUID ownerId)`, який:
- наповнює `marionetteNpcs` та `marionetteOwner`;
- запускає `masterTask` (через наявний `ensureMasterTask()`);
- НЕ ставить `stranded` (див. граничні випадки).

Скан **ідемпотентний** (повторний виклик не дублює записи — використовуємо Set/`putIfAbsent`).
Glow застосовується ліниво в `masterTick()`, коли власник онлайн (логіка вже є).

### Доступ до інстансу здібності
`MarionettistControl` — це інстанс у списку здібностей Sequence 5 шляху Fool. Restorer має отримати
саме той інстанс. Дістаємо його через `PathwayManager`/Fool-pathway (знайти здібність за
`MarionettistControl.IDENTITY`) і викликати `registerLoadedMarionette`. Точку доступу узгодити з
наявним патерном `ServiceContainer` під час реалізації.

## 4. Єдиний постійний death-listener

Зараз обробка смерті — *тимчасова* підписка на `NPCDeathEvent` через
`ctx.events().subscribeToTemporaryEvent(...)`, прив'язана до конкретної посесії. Після рестарту вона
зникає → завантажені маріонетки не обробляють смерть.

Рішення: винести в **один постійний listener** (`MarionetteDeathListener`, presentation/listeners),
зареєстрований у `MysteriesAbovePlugin.registerEvents()`. На `NPCDeathEvent`:
- перевіряє `e.getNPC().hasTrait(MarionetteMinionTrait.class)`;
- делегує в `MarionettistControl.onMarionetteDeath(int npcId)` — нову публічну метод, що містить
  чинну логіку: очистити дропи NPC, дропнути речі маріонетки на місці смерті (з пропуском службових
  предметів), зробити swapOut власника якщо він керує, прибрати glow і записи реєстрів, `npc.destroy()`,
  повідомити власника.

Стара логіка з `registerNpcDeathListener` видаляється; `convertToMarionette` більше не підписується на
подію (єдиний listener покриває і свіжі, і завантажені маріонетки).

## 5. Граничні випадки

- **Посесія при рестарті** — авто-swapOut при вимкненні (розділ 2). Маріонетка лишається вільною й
  переживає рестарт; гравець повертається у власне тіло. (Підтверджено користувачем.)
- **Stranded при рестарті** — скидаємо. Завантажена маріонетка стає звичайною/доступною. 10-хв дедлайн
  мав сенс лише поки власник «відійшов» наживо; після рестарту власник офлайн, тож відлік не
  починаємо (`strandedNpcs` не наповнюється на старті).
- **Порядок ініціалізації** — Citizens (`depend`) вмикається раніше, але NPC можуть ще не бути в
  реєстрі на момент нашого `onEnable`. Скан робимо **відкладено**: підпискою на
  `CitizensReloadEvent`/`CitizensEnableEvent` або `scheduler.runTask` (наступний тік) у `onEnable`.
  Обрати під час реалізації; скан ідемпотентний, тож подвійний виклик безпечний.
- **Невідомий шлях при регідрації** — маріонетка лишається як не-Beyonder, без падіння (розділ 1).
- **Ліміт маріонеток** — рахується коректно автоматично після відбудови `marionetteNpcs`.

## 6. Тестування

**Юніт (без Bukkit):**
- Регідрація домену: `pathwayName + level → Pathway/Sequence` через `PathwayManager` (round-trip);
  fallback на не-Beyonder при невідомому/порожньому шляху.
- Серіалізація знімка інвентаря: round-trip списку зі збереженням слотів та `null`-порожніх слотів.

**In-server (ручна перевірка — прийнятий у проєкті спосіб для ефектів):**
- Створити маріонетку → перезапустити сервер → NPC на місці; світіння, меню, ліміт, HP, скін, інвентар
  збереглись; вхід/вихід працює.
- Смерть маріонетки після рестарту дропає речі й чистить записи.
- Гравець керує маріонеткою → рестарт → авто-swapOut: гравець у власному тілі з власним інвентарем;
  маріонетка вільна й на місці.

## Залежності / точки інтеграції

- `MarionetteMinionTrait` → потребує `PathwayManager` для регідрації шляху. Citizens створює трейти
  рефлексією (конструктор без аргументів), тож `PathwayManager` подаємо через статичний bootstrap-хук
  (напр. `MarionetteMinionTrait.bind(pathwayManager)` з `ServiceContainer`/`onEnable`) — узгодити з
  наявним патерном проєкту під час реалізації.
- `MysteriesAbovePlugin.onEnable()` → реєстрація `MarionetteDeathListener` + запуск `MarionetteRestorer`.
- `ServiceContainer` → конструювання `MarionetteRestorer`/listener у порядку залежностей.
- `MarionettistControl` → нові публічні методи `registerLoadedMarionette(int, UUID)` та
  `onMarionetteDeath(int)`; розділення `cleanUp()`.

## Поза межами (YAGNI)

- Відновлення активної посесії після логіну гравця (обрано простіший авто-вихід).
- Збереження 10-хв strand-дедлайну через рестарт.
- Окремий JSON-репозиторій маріонеток.
