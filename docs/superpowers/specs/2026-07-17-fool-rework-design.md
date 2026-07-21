# Реворк шляху Блазня (Fool) — Seq 9→5

Дата: 2026-07-17
Гілка: `feat/fool-rework`

## Огляд

Комплексний реворк здібностей шляху Блазня (`me.vangoo.pathways.fool`), щоб
наблизити їх до канону *Lord of the Mysteries* і зробити механіки цікавішими.
14 змін по послідовностях 9→5 (Seq 6 не чіпаємо): реворки наявних здібностей,
2 нові пасивки, 1 нова активка, 1 багфікс.

**Принципи (з CLAUDE.md / `.claude/rules`):**
- Балансні числа/формули — у `domain` (VO або `domain.services`) з юніт-тестами.
  У здібностях — лише glue + Bukkit-ефекти.
- User-facing текст — українською (`getName`, `getDescription`, повідомлення).
- `getDescription(Sequence)` показує вже відскейлені числа (`scaleValue`).
- Стан сесій — інстанс-поля, ніколи `static`; `cleanUp()` скасовує все.
- Скейл сили від Sequence — через `scaleValue`/`SequenceScaler`, не власні множники.
- Реалізація перевіряється на сервері (мок-тести лише для чистого домену).

**Порядок реалізації (фази):** Seq 9 → Seq 8 → Seq 7 → Seq 5. Між фазами —
`mvn clean package` (через IntelliJ bundled mvn — `mvn` не в PATH) і, де можливо,
перевірка на сервері. Seq 5 (Marionette, ~1500 рядків) — остання й найризикованіша.

---

## Seq 9 — Провидець (Seer)

### 1. DangerIntuition — сильне покращення (пасивка)

Файл: `pathways/fool/abilities/DangerIntuition.java` (реворк наявного).
База: `PermanentPassiveAbility` (без змін).

Лишаємо наявні попередження (ворожі моби що таргетять кастера, озброєні гравці,
снаряди). Додаємо чотири нові прояви:

> Правка (2026-07-18): **усі попередження — в action bar**, не в чат
> (`sendMessageToActionBar`): небезпечні моби, озброєні гравці, вхідні снаряди.

1. **Передчуття удару.** За ~10–20 тіків до ворожого удару/пострілу по кастеру —
   сигнал в action bar (`⚠ Небезпека!`) + звук. Реалізація: підписка на
   `EntityDamageByEntityEvent`/`ProjectileLaunchEvent`, де ціль = кастер; коли
   ворог заносить удар (провокатор — наближення озброєного + погляд), даємо
   короткий warning tick перед приходом шкоди. Спрощення: при виявленні
   вхідного снаряда/замаху — попередження одразу (передчуття = раннє інфо-вікно).

2. **Бачення крізь двері.** Коли кастер дивиться зблизька (≤3 бл) на блок із
   набору дверей/люків/воріт (`DOOR`, `TRAPDOOR`, `FENCE_GATE`, будь-які
   `*_DOOR`), на кілька секунд підсвічуються сущності по той бік (у радіусі
   за напрямком погляду) через тимчасовий glow (`context.glowing()`) або
   партикли-контур. НЕ залежить від Spirit Vision.

3. **Передбачення дії.** Зрідка (низький шанс за тік) над ворогом, за яким
   дивиться кастер, короткий натяк в action bar («ціль зараз атакує» /
   «ціль тікає» — грубо з поточного стану моба: чи має target, чи біжить).

4. **Ухилення від летального.** Підписка на `EntityDamageEvent` кастера:
   якщо `finalDamage >= currentHealth` (удар був би смертельним) — з шансом
   (скейлиться з Seq) скасовуємо шкоду, даємо коротку невразливість
   (`INVULNERABILITY`/i-frames) + невеликий ривок убік + партикли/звук
   ухилення. Має власний внутрішній кулдаун (напр. 15с), щоб не був
   абсолютним щитом.

**Domain:**
- `domain/valueobjects/DangerPremonition.java` (або `domain/services`):
  - `lethalDodgeChance(Sequence)` — шанс ухилення від летального (напр.
    Seq9 ~15% … росте до Seq зі шляху; але Fool доступний лише 9..5, тож
    діапазон у межах цих Seq).
  - `actionPredictionChance(Sequence)` — шанс натяку на дію.
  - `premonitionLeadTicks(Sequence)` — вікно передчуття.
  - Юніт-тест `DangerPremonitionTest` (монотонність за Sequence, межі 0..1).

**Тексти:** оновити `getDescription` — перелічити нові прояви українською.

---

## Seq 8 — Клоун (Clown)

### 2. ExpressionControl → тогл-дрейн

Файл: `pathways/fool/abilities/ExpressionControl.java` (реворк).
База: `ActiveAbility` (лишається), але поведінка — тогл із periodic cost
за зразком `visionary/abilities/PsychologicalInvisibility`.

- Прибираємо фіксовану `DURATION_SECONDS`. Тепер маска активна **доки не
  скасуєш**.
- `getSpiritualityCost()` = `COST_PER_SECOND` (10); додаємо
  `getPeriodicCost()` = 10.
- Тік раз на секунду списує 10 духовності, нараховує mastery
  (`MasteryProgressionCalculator.calculateMasteryGain`), авто-вимикає при
  виснаженні (як `PsychologicalInvisibility.enable/disable`).
- Повторний каст — свідоме зняття. Вихід гравця — зняття.
- `isMasked(UUID)` лишається (використовується іншими pathway-здібностями
  читання). **Увага:** зараз `isMasked` — `static`; лишаємо сумісним
  (інші класи звертаються статично), але внутрішній реєстр тасків робимо
  консистентним із наявним патерном (уже `static` — не погіршуємо; в межах
  цього реворку не переписуємо на інстанс, щоб не чіпати зовнішні виклики).

**Domain:** тривіально (константа 10/с) — окремий VO не потрібен.

**Тексти:** `getDescription` — «Маска активна, доки не скасуєте. 10 духовності/с.»

### 3. PaperCutter → пасивний кидок паперу

Файл: `pathways/fool/abilities/PaperCutter.java` (реворк).
База: змінюється з `ActiveAbility` на **пасивну** (`PermanentPassiveAbility`)
— вона просто «вмикає» здатність кидати папір; фактичний кидок ловить листенер.

Механіка:
- Тримаєш **звичайний** папір у руці й **ПКМ** → кидається паперовий снаряд
  (`Snowball` з трейл-партиклами, як зараз), урон скейлиться з Seq
  (`SequenceScaler`), **безкоштовно** (0 духовності), кулдаун **0.5с** (10 тіків,
  per-player, у листенері/здібності).
- **Guard «чистий папір»:** кидок дозволено лише якщо предмет у руці —
  `Material.PAPER` **і** НЕ:
  - ability-item: `AbilityItemFactory.isAbilityItem(item)`;
  - кастом-айтем/інгредієнт: перевірка через `CustomItemService`
    (`getFromItemStack`/NBT-маркер кастом-айтема) — інгредієнти шляхів це
    кастом-предмети на базі PAPER, їх кидати НЕ можна;
  - перейменований/із NBT службовий папір (маріонетковий swap-back тощо —
    вони не PAPER, але про всяк перевіряємо ability/menu NBT).
- Кидок списує 1 папір зі стаку.

**Реалізація:**
- `presentation/listeners/PaperThrowListener.java` — `PlayerInteractEvent`
  (RIGHT_CLICK_AIR/RIGHT_CLICK_BLOCK), фільтр: гравець — Fool-Beyonder з
  розблокованою здібністю PaperCutter (перевірка через `BeyonderService`/
  ability lookup), чистий папір у руці, не на кулдауні. Викликає логіку
  кидка (спільну зі здібністю/руннером).
- Балансна логіка (урон, швидкість) — у здібності + domain-VO.
- **Wiring:** зареєструвати листенер у `ServiceContainer.registerEvents`
  (потрібні `BeyonderService`, `CustomItemService`, `AbilityItemFactory`).

**Domain:**
- `domain/valueobjects/PaperThrowDamage.java` — `damageFor(Sequence)` (скейл),
  `THROW_COOLDOWN_TICKS = 10`. Юніт-тест `PaperThrowDamageTest`.

**Еволюція на Seq 7** — див. п.8 нижче (спільний `AbilityIdentity`).

### 4. ClownAgility — прибрати Стрибок +1, додати акробатику

Файл: `pathways/fool/abilities/ClownAgility.java` (реворк).
База: `PermanentPassiveAbility` (без змін), identity `fool_agility` лишається
(GracefulDescent на Seq6 і далі заміняє її).

- **Прибрати** `JUMP_BOOST` (більше не застосовуємо).
- Лишити **Speed** (тік-рефреш) — без змін.
- Замінити 70% зменшення падіння на **повний імунітет до шкоди від падіння**
  (`EntityDamageEvent`, `FALL` → `e.setCancelled(true)` або damage=0 + landing FX).
- **Лазіння/чіпляння за стіни:** новий тік-механізм — коли гравець притиснутий
  до вертикальної стіни (блок перед ним на рівні очей/ніг твердий), дивиться в
  неї й затиснув пробіл (наближення до стіни + рух угору), даємо повільний підйом
  угору (керований `setVelocity` вгору при контакті зі стіною, поки є стіна й
  напрям погляду в неї). Обмеження: не спрацьовує над лавою/у воді; швидкість
  лазіння скейлиться з Seq.

**Domain:**
- `domain/valueobjects/WallClimbRules.java` — `climbSpeed(Sequence)`, гейти
  (мін. твердість блоку тощо як параметри). Юніт-тест `WallClimbRulesTest`.

**Тексти:** «Швидкість +1. Імунітет до шкоди від падіння. Лазіння по стінах.»

---

## Seq 7 — Фокусник (Magician)

### 5. FlameJump — покращення

Файл: `pathways/fool/abilities/FlameJump.java` (реворк).

- `SCAN_RADIUS` тепер базовий **30**, **скейлиться з Seq** (`scaleValue`/
  `SequenceScaler` — сильніша Seq → дальший стрибок).
- Після телепорту в/до полум'я — **тимчасова fire-immunity**: гравець НЕ
  отримує вогняної шкоди (`FIRE`, `FIRE_TICK`, `LAVA`? — лише вогонь, не лава),
  **поки не вийде** з вогняного блоку. Реалізація: підписка на
  `EntityDamageEvent` кастера з причиною FIRE/FIRE_TICK → cancel, поки
  тік-перевірка бачить, що гравець стоїть у вогні; коли вийшов із вогню —
  знімаємо immunity (unsubscribe + скидання fireTicks).

**Domain:**
- `domain/valueobjects/FlameJumpRange.java` — `rangeFor(Sequence)`. Юніт-тест.

### 6. PaperSubstitution → крафт-партія ляльок + тогл захисту

Файл: `pathways/fool/abilities/PaperSubstitution.java` (повний реворк).
База: `ActiveAbility`.

Дві дії:
- **Звичайний каст** — «створити партію ляльок». Потрібно ≥ вартості паперу
  (напр. 10). Створює `count` ляльок (базово **3**, скейлиться з Seq до
  ~6, є стеля), списує папір. Ляльки **накопичуються** в інстанс-реєстрі
  `Map<UUID, Integer> dollCount` (кількість «зарядів»), зберігаються між
  кастами (до стелі накопичення).
- **Shift+ПКМ** — тогл «захист» (`Set<UUID> protectionActive`): вмикає/вимикає
  режим поглинання.

Поки захист **увімкнено** і є ляльки:
- Підписка на `EntityDamageEvent` кастера з важким ударом
  (`e.getFinalDamage() >= DAMAGE_THRESHOLD`) → cancel шкоди, **-1 лялька**,
  **телепорт навмання поблизу** (радіус ~5–8, безпечна локація), звук/партикли.
  На місці, звідки гравця «висмикнуло», лишаються **лише білі партикли, що
  розсипаються донизу** (`spawnScatter`) — БЕЗ armor-stand-стойки й предмета
  паперу (правка 2026-07-18). Коли ляльки скінчились —
  захист авто-вимикається з повідомленням.
- Вимкнув тогл — ляльки НЕ витрачаються (лежать про запас).

Персистентність накопичених ляльок між рестартами — **поза скоупом** цього
реворку (лишаються in-memory, як інший стан здібностей Fool); за потреби —
окремий тікет. (Явно зафіксовано, щоб не було неоднозначності.)

**Крафт-процес (цікаво):** каст програє коротку анімацію «складання» —
партикли паперу кружляють навколо гравця, звук перегортання сторінок, за
~1с з'являється лічильник ляльок в action bar.

**Domain:**
- `domain/valueobjects/DollBatch.java` — `dollsPerCast(Sequence)`,
  `paperCost(Sequence)`, `maxStored(Sequence)`, `DAMAGE_THRESHOLD`,
  `teleportRadius`. Юніт-тест `DollBatchTest` (скейл, стелі).

**Тексти:** опис пояснює обидві дії й накопичення.

### 7. DamageTransfer → зцілення на основі недавньої шкоди

Файл: `pathways/fool/abilities/DamageTransfer.java` (реворк).
База: `ActiveAbility`.

- **Звичайний каст (на собі):** лікує кастера на **~50% шкоди**, отриманої ним
  за останні 10с (не до фулл; стеля скейлиться з Seq). Пул недавньої шкоди
  кастера вже трекається (`recentDamage`, `recordDamage`).
- **Shift+ПКМ на цілі:** лікує ЦІЛЬ на основі шкоди, яку **сама ціль** недавно
  отримала (не пул кастера). Потрібен легкий трекер отриманої шкоди для інших
  істот/гравців.

**Реалізація трекера:** розширити наявний `recordDamage(UUID, amount)` так, щоб
він фіксував недавню шкоду **будь-якої** істоти (не лише кастера-Fool). Джерело
викликів — глобальний listener шкоди (перевірити, звідки зараз кличеться
`DamageTransfer.recordDamage`; якщо лише для Fool — розширити на всіх, кого
можна зцілити). Зберігаємо `Map<UUID, DamageRecord>` (як зараз), додаємо запис
для цілей.

**Domain:**
- `domain/valueobjects/RecentDamageHeal.java` — `healAmount(double recentDamage,
  Sequence)` (частка + стеля за Seq), `DAMAGE_WINDOW_MS = 10_000`. Юніт-тест
  `RecentDamageHealTest`.

**Тексти:** опис — само-зцілення + Shift+ПКМ зцілення союзника.

### 8. Papercutter-еволюція «Папір як зброя» (Seq 7)

`AbilityIdentity` (як `ScanGaze` → `ScanGazePassive` у visionary):
- **Seq 8:** `PaperCutter` — пасивний кидок (п.3), завжди активний.
- **Seq 7:** нова здібність `PaperWeaponry` з тим самим `AbilityIdentity`
  (`fool_paper_cutter` / спільний ключ) — заміняє Seq8-версію при advance.
  Тепер це **активна** здібність:
  - Звичайний кидок паперу (ПКМ, через листенер) — **лишається** (успадкована
    поведінка).
  - **Shift+ПКМ відкриває меню** «Створити з паперу» (triumph-gui через
    `context.ui()`): вибір типу зброї — **бита** (нокбек), **цегла** (важкий
    урон по дузі/при ударі), **тростина** (сповільнення при ударі) тощо.
    Вартість **32–64 паперу** залежно від сили предмета. Створює реальний
    предмет-зброю в інвентарі (кастом ItemStack з NBT-маркером + ефектом при
    ударі, який ловить окремий листенер) або одноразовий кидок — **default:
    створює предмет-зброю в руках**, що діє N ударів/секунд.

**Файли:**
- `pathways/fool/abilities/PaperWeaponry.java` — Seq7-версія (active,
  spawns menu), спільний identity з `PaperCutter`.
- Меню — `context.ui()` / окремий GUI-клас, або inline `Gui.gui()`.
- Ефект зброї при ударі — listener (розширення `PaperThrowListener` або новий
  `PaperWeaponListener`), читає NBT-тип зброї.

**Domain:**
- `domain/valueobjects/PaperWeaponType.java` — enum (BAT/BRICK/CANE…),
  кожен: `paperCost` (32..64), `effect params` (нокбек/бонус-урон/slow-тики).
  Юніт-тест `PaperWeaponTypeTest` (вартості в діапазоні, унікальність).

### 9. Підводне дихання (нова пасивка)

Файл: `pathways/fool/abilities/AquaticBreath.java` (нова).
База: `PermanentPassiveAbility`.

- Поки гравець під водою й **не глибше 5 блоків** від поверхні води — не
  втрачає кисень (тік-рефреш `air`/`WATER_BREATHING`, або скидання `remainingAir`).
- Глибше порогу — дихання не діє (кисень витрачається нормально).
- Поріг глибини **скейлиться з Seq** (сильніша Seq → глибше можна).

**Реалізація глибини:** від локації гравця вгору рахуємо кількість блоків води
до першого не-водяного блоку (поверхні). Якщо ≤ поріг — застосовуємо дихання.

**Domain:**
- `domain/valueobjects/BreathDepthLimit.java` — `maxDepth(Sequence)` (база 5).
  Юніт-тест.

Реєстрація в `Fool.initializeAbilities()` — додати до Seq 7 списку.

### 10. Illusion Creation (нова активна)

Файл: `pathways/fool/abilities/IllusionCreation.java` (нова).
База: `ActiveAbility`.

> Правка (2026-07-18): режим **обирається Shift+ПКМ** (перемикання, deferred —
> без витрат), а НЕ чергується автоматично на кожному касті. Звичайний ПКМ
> виконує поточний обраний режим. Проєкція фальшивого звуку — **окремий режим у
> тому ж обороті** Shift+ПКМ (`SOUND_MODE`), а не завжди-Shift-дія.

Режими (перемикаються Shift+ПКМ, виконуються звичайним ПКМ):
- **Локальна ілюзія** у точці погляду: фальшивий звук + кольорові партикли + дим,
  які інші сприймають як реальні (вибух / кроки / пожежа — `IllusionKind`).
- **Проєкція звуку на ціль:** відтворити звук «нізвідки» гравцю-цілі, щоб
  дезорієнтувати (лише для неї).

**Domain:**
- `domain/valueobjects/IllusionKind.java` — enum типів (EXPLOSION/FOOTSTEPS/
  FIRE/…), вартість духовності кожного. Юніт-тест (опційно, якщо є числа).

Реєстрація — Seq 7 список у `Fool.initializeAbilities()`.

---

## Seq 6 — Безликий (Faceless)

Без змін. `Shapeshifting` та `GracefulDescent` лишаються; `GracefulDescent`
і далі заміняє `ClownAgility` через спільний `AbilityIdentity` `fool_agility`.

---

## Seq 5 — Маріонетник (Marionettist)

### 11. MarionettistControl — реворк фаз

Файл: `pathways/fool/abilities/MarionettistControl.java` (реворк наявного,
~1500 рядків; чіпаємо фазову логіку в `beginThreading`/`onPhase2Begin`/
`convertToMarionette`).

- **Фаза 1 (фіксація ниток):** тривалість **5–20с залежно від засвоєння**
  кастера (не фіксовані 20с `LOCK_TICKS`). Формула: чим вище mastery —
  тим коротша фіксація (швидше). Нитки-партикли (`drawThreadParticles`)
  показуємо **лише** якщо жертва — потойбічна з Seq **> кастера** (слабша),
  або самому кастеру; для жертв Seq ≤ кастера нитки **невидимі** (жертва
  такого рівня не бачить ниток).
- **Збій фіксації великим уроном:** поки фаза триває, якщо жертва отримує
  АБО завдає **великий разовий урон** (≥ поріг) — фіксація збивається
  (`cancelSession`). Реалізація: тимчасова підписка на `EntityDamageEvent`/
  `EntityDamageByEntityEvent` для `targetId` під час сесії.
- **Конверсія:** якщо фазу не збито протягом **5 хв** (не поточні
  `CONVERT_TICKS=100` = 5с!) — створюється маріонетка. 5 хв **сильно
  скейлиться з Seq** (сильніша Seq → швидша конверсія). Тобто фаза 2
  (заціпеніння) триває довго, і весь цей час її можна зірвати уроном.

  > Примітка: поточні `LOCK_TICKS`/`CONVERT_TICKS`/`STRAND_DEATH_MS` — числа
  > в класі; виносимо у domain-VO `MarionetteTiming`.

**Domain:**
- `domain/valueobjects/MarionetteTiming.java`:
  - `phase1LockTicks(double mastery)` — 5..20с за засвоєнням.
  - `phase2ConvertTicks(Sequence)` — базово 5 хв, скейл за Seq.
  - `mobSwapPhaseTicks(Sequence)` — ~15с, скейл (для п.12).
  - `breakDamageThreshold()` — поріг великого урону для збою.
  - Юніт-тест `MarionetteTimingTest` (монотонність, межі).

### 12. Свап із мобами (не потойбічними)

У тому ж `MarionettistControl`:
- Дозволити ціль-**моба** (не-Beyonder LivingEntity), не лише гравця. Фаза 2
  для мобів ~**15с** (`mobSwapPhaseTicks`, скейл).
- Конверсія моба → маріонетка (Citizens NPC із виглядом/HP моба). Свап-вселення
  **як у гравців**: береш тіло/HP/розмір моба, твоє тіло стоїть NPC-заглушкою.
  Моб без шляху → `trait.wasBeyonder()==false` → здібностей не отримуєш (уже є
  ця гілка в `swapIn`).

> Правка (2026-07-18): свап із маріонеткою-**мобом** тепер робить кастера
> **візуально цим мобом для всіх інших гравців, без ніка** — через packet-маску
> `infrastructure.disguise.EntityDisguiseService.disguiseAsMob` (спавнить моба під
> тим самим entity id, що й гравець; рухові пакети гравця рухають моба). Залишкове
> «основне тіло» на місці кастера — це NPC, тимчасово перетворений на **player-тип**
> (`npc.setBukkitEntityType(PLAYER)` + скін/нік кастера); на виході NPC повертається
> у вигляд моба (`setBukkitEntityType(mobType)`). Тип цілі зберігає
> `MarionetteMinionTrait.marionetteEntityType` (save/load; старі saves → PLAYER).
> `mobDisguised` (Set<UUID>, instance-поле) розрізняє, яку маску знімати на виході.
> Обмеження packet-маски (як у `SkinDisguiseService`): best-effort resend, себе
> кастер мобом не бачить від 1-ї особи (лише F5). Для моба інвентар гравця НЕ
> чіпається (у моба його нема).
- Перевірити `convertToMarionette` — зараз орієнтований на гравця
  (`SkinTrait`, ім'я гравця); для моба: NPC типу відповідного моба або
  player-NPC із дефолт-виглядом; ім'я = локалізована назва моба. Вигляд/HP
  моба переносяться (частково вже є: `fMaxHealth`/`fHealth`).

### 13. Багфікс — не можна робити маріонеткою Citizens-NPC

У `performExecution` (вибір цілі) додати guard **перед** `beginThreading`:
```
if (CitizensAPI.hasImplementation()
        && CitizensAPI.getNPCRegistry().isNPC(target)) {
    return AbilityResult.failure("§cЦе не жива ціль для Ниток.");
}
```
Це закриває баг, де жрець церкви (Citizens NPC) міг стати маріонеткою.
(`isMarionetteNpc` уже блокує ВЖЕ-маріонеток; цей guard ширший — будь-який NPC.)

### 14. Нова пасивка Seq 5 «Бачення ниток»

Файл: `pathways/fool/abilities/ThreadSight.java` (нова).
База: `PermanentPassiveAbility`.

- Тік (напр. раз на 10 тіків): для всіх гравців/істот **у радіусі** (обмежений
  заради продуктивності, напр. 24 бл) — партикли-нитки, що тягнуться вгору
  над головою (символ ниток духовного тіла).
- **Підсвітка невидимих:** сущності поблизу, що зараз невидимі (мають
  `INVISIBILITY`, або приховані психоневидимістю/psychological invisibility —
  перевіряти прапорці цих здібностей де можливо, інакше `PotionEffect
  INVISIBILITY`), — підсвічуються (glow лише для кастера через
  `context.glowing()`, або партикли-контур навколо їхньої позиції).
- Радіус скейлиться з Seq.

**Domain:**
- `domain/valueobjects/ThreadVisionRange.java` — `rangeFor(Sequence)`. Юніт-тест.

Реєстрація — Seq 5 список у `Fool.initializeAbilities()` (поряд із
`MarionettistControl`/`MarionetteSwapMenu`).

---

## Реєстрація здібностей (`Fool.initializeAbilities`)

- Seq 9: DivinationArts, SeerSpiritVision, **DangerIntuition (реворк)**, RitualMagic.
- Seq 8: **PaperCutter (реворк, passive)**, **ExpressionControl (реворк)**,
  **ClownAgility (реворк)**.
- Seq 7: **FlameJump (реворк)**, **PaperSubstitution (реворк)**, AirBullet,
  **DamageTransfer (реворк)**, **PaperWeaponry (нова, identity=PaperCutter)**,
  **AquaticBreath (нова)**, **IllusionCreation (нова)**.
- Seq 6: Shapeshifting, GracefulDescent — без змін.
- Seq 5: **MarionettistControl (реворк)**, MarionetteSwapMenu,
  **ThreadSight (нова)**.

> `AirBullet` — не в списку змін, лишається.
> `PaperWeaponry` через спільний identity заміняє `PaperCutter` при advance
> 8→7 (як `ScanGaze`→`ScanGazePassive`).

## Нові файли (підсумок)

**Domain (з юніт-тестами):**
- `DangerPremonition`, `PaperThrowDamage`, `WallClimbRules`, `FlameJumpRange`,
  `DollBatch`, `RecentDamageHeal`, `PaperWeaponType`, `BreathDepthLimit`,
  `IllusionKind`, `MarionetteTiming`, `ThreadVisionRange`.

**Pathways (ефекти):**
- `PaperWeaponry`, `AquaticBreath`, `IllusionCreation`, `ThreadSight` (нові
  здібності); реворки наявних.

**Presentation (listeners) + wiring:**
- `PaperThrowListener` (+ ефект паперової зброї) — реєстрація в `ServiceContainer`.

## Тести

- Юніт-тести всіх нових domain-VO (скейл монотонний за Sequence, межі 0..1
  для шансів, вартості в діапазонах).
- Ефекти (glow, телепорт, партикли, GUI, лазіння, свап) — на сервері
  (`/pathway` видати Fool, `mvn clean package` → JAR у plugins). Патерн
  `PathwayManager` не юніт-тестується headless.
- `ArchitectureTest` має лишатись зеленим (domain без Bukkit).

## Інваріанти / ризики

- Guard «чистий папір» мусить надійно виключати інгредієнти-кастом-айтеми на
  базі PAPER та ability-items — інакше гравець «викине» інгредієнт/предмет
  здібності. Це найважливіший коректнісний інваріант реворку PaperCutter.
- Marionette: guard Citizens-NPC (п.13) + невидимість ниток для рівних/сильніших
  жертв не має ламати наявну персистентність/свап-логіку.
- ExpressionControl лишає `isMasked` статичним контрактом для зовнішніх
  викликачів — не змінювати сигнатуру.
- `cleanUp()` кожної нової/зміненої здібності скасовує таски/сесії/підписки.

## Поза скоупом

- Персистентність накопичених паперових ляльок між рестартами.
- Зміни Seq 6.
- Нові істоти/зілля/рецепти (лише здібності).
- Оновлення MythicMobs-кітів під нові гравцеві здібності (окремий тікет).
