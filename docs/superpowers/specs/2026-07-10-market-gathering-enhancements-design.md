# Доопрацювання підпільного ринку (Збори Потойбічних)

Дата: 2026-07-10
Гілка: `feat/market-gathering`

## Мета

Сім пов'язаних покращень фази зборів: людські назви товарів, відлік до
початку, покарання за насильство/спроби здібностей, вхід у меню через
об'єкт замість команди, кнопка «Назад», піднята скупка рідкісних речей і
кінематографічна доповідь Посередника на старті збору.

---

## 1. Укр-назви товарів у меню

**Проблема:** меню показує сирий `itemKey` (`custom:dimensional_wanderer_eye`).

**Рішення:** новий `MarketItemNamer` (application) — резолвер `itemKey → укр-назва`:

- `custom:<id>` → `CustomItemService.createItemStack(id)` → display name предмета.
  (Головний шлях: замовлення/торги завжди на інгредієнти.)
- `recipe:<pathway>:<seq>` → назва книги рецептів через `RecipeBookFactory`.
- Характеристика (`characteristic:…`) → назва з наявного механізму; фолбек — олюднений id.
- Невідомий ключ → олюднений хвіст ключа (заміна `_`/`:` на пробіли, без сирого префікса).

**Точки підстановки в `MarketMenu`:** `openOrders` («Шукають:»),
`openNegotiations` («Ви продаєте/купуєте:»). Лоти вже показують реальний
ескроу-стак — не чіпаємо.

**Wiring:** `MarketItemNamer` створюється в `ServiceContainer`, передається в `MarketMenu`.

---

## 2. Щохвилинний відлік до зборів

У фазі `ANNOUNCED` — повторюваний таск раз на 60 с, шле гравцям зі списку
`joined` (лише онлайн): `[Збори] До зборів N хв.` На останній хвилині —
формулювання «менше хвилини». Таск живе в `phaseTasks` (скасовується на
`open()`/`forceCloseIfActive`). `GatheringService` зберігає `openAtMillis`
(момент планового `open`) для обчислення залишку.

---

## 3. Покарання за удар / спробу здібності в залі

### Чиста модель — `GatheringConduct` (domain.market, unit-тест)

Лічильник порушень на гравця → рішення. Без Bukkit; пакет уже в `PURE_DOMAIN`.

```
enum Sanction { NONE, WARN, KICK }
GatheringConduct:
  Sanction recordViolation(UUID)  // 1-ше → WARN, 2-ге і далі → KICK
  (внутрішній Map<UUID,Integer>)
```

Юніт-тест: перше порушення = WARN, друге = KICK, лічильник на різних гравців незалежний.

### Debounce (в `GatheringService`)

Не більше одного зарахованого порушення на гравця / 2 с (щоб випадковий
подвійний клік не викидав миттєво). `Map<UUID,Long> lastViolationAt`.

### Удар (урон проходить — вибір користувача)

Листенер `EntityDamageByEntityEvent` (в `GatheringListener`): якщо світ —
venue, кривдник — open-учасник, жертва — гравець (не NPC-Посередник) →
`gatheringService.recordViolation(attacker)`. Урон **не** скасовуємо.

### Спроба здібності (не кастається + порушення)

Вузький інтерфейс у application:

```java
public interface GatheringAbilityGuard {
    /** true → здібність заблокувати (гравець на зборах); фіксує порушення як side effect. */
    boolean interceptAbility(UUID playerId);
}
```

`GatheringService implements GatheringAbilityGuard`: якщо гравець —
open-учасник → `recordViolation` + повертає `true`. `AbilityExecutor`
дістає guard **сеттером** (`setGatheringAbilityGuard`), бо будується раніше
за `GatheringService` (ServiceContainer: 240 vs 257). У `execute()`, після
перевірки `player != null`, до перевірки локів:
`if (guard != null && guard.interceptAbility(id)) return AbilityResult.failure("Тут ваші сили мовчать.")`.
Єдина точка перехоплення ловить усі шляхи касту (меню, хотбар, основне тіло).

### Обробка санкції (в `GatheringService.recordViolation`)

- `WARN` → `⚠ [Збори] Насильство тут не терплять. Наступного разу — виганяю.`
- `KICK` → повідомлення гравцю + кік як `handleQuit` (unmask, скасувати
  торги/ескроу, unfreeze, телепорт додому, вивести зі `session`/
  `openParticipantIds`/`frozen`/`briefed`) + додати в `bannedFromNext`.

### Бан (персист, знімається після пропуску 1 збору)

- `bannedFromNext: Set<UUID>` — нове поле `Snapshot` (зворотно сумісне: при
  читанні старого файлу gson дасть `null` → трактуємо як порожній).
- `announce()`: копіює `bannedFromNext` у транзієнтний `skipThisRound`,
  потім **очищає** `bannedFromNext` (нові бани цього раунду йдуть на
  наступний). `join()` відхиляє гравців зі `skipThisRound`
  (`[Збори] Вас не пустять на цей збір — минулого разу ви порушили спокій.`).
- Друге порушення під час збору → `bannedFromNext.add(id)` + `persist()`.

---

## 4. Меню через кафедру (lectern), не команду

- `GatheringVenueProvider.buildPlatformIfMissing` ставить **постійну кафедру**
  (`Material.LECTERN`) біля спавну, напр. блок `(1, 65, 0)` (Посередник —
  на `(0.5, 65, 0.5)`).
- Листенер `PlayerInteractEvent` (в `GatheringListener`): правий клік по
  `LECTERN` у venue-світі open-учасником → `event.setCancelled(true)`
  (глушить дефолтне GUI кафедри) + `marketMenu.openMain(player)`.
  Потрібно інжектнути `MarketMenu` у `GatheringListener`.
- `/gathering menu` **лишається** як запасний спосіб (вибір користувача).

---

## 5. Кнопка «Назад» у кожній вкладці

`MarketMenu.paginated(...)` отримує параметр `Runnable back`; кнопка
`◄ Назад` у слоті `(6,1)`. Цілі: Лоти/Замовлення/Мої угоди → `openMain`;
Інгредієнти (`openKnownIngredients`) → `openOrders` (батьківська вкладка).

---

## 6. Піднята скупка рідкісних речей (balance)

Тільки config.yml (`market.buyback.*`) — числа тюнінгові, тести
`BuybackPriceTableTest` конфіг не читають (хардкод у конструкторі), тож не ламаються.

- `recipe-book-coppets`: 10 → **200** (10 фунтів; книга розблоковує просування).
- `characteristic-coppets-by-seq` (рідкісні — ~×2):
  - `"9"`: 40 → 80, `"8"`: 60 → 130, `"7"`: 90 → 220, `"6"`: 130 → 360, `"5"`: 180 → 540.

Значення орієнтовні, легко коригуються далі.

---

## 7. Кінематографічна доповідь Посередника на старті збору

**Сценарій відкриття:** гравці спавняться в залі й **стоять нерухомо**,
слухаючи Посередника. Він розповідає, куди вони потрапили, правила місця
(що можна / чого не можна) і принципи торгівлі. Доповідь — в **action bar**,
літери з'являються **поступово** (друкарський ефект), кожна літера — з
тихим звуком (друкарська машинка). Після доповіді всі можуть рухатись.

### Заморозка руху

- `GatheringService`: `Set<UUID> frozen`. Проставляється на всіх учасників
  у `open()`; знімається по завершенні доповіді і в `close()`/кіку.
- `GatheringListener` `PlayerMoveEvent`: якщо гравець `frozen` і **блок-позиція**
  змінилася (from→to по x/y/z) → `event.setCancelled(true)`. Поворот голови
  (yaw/pitch) дозволений, щоб роздивитись залу.

### Доповідь — `OrganizerBriefing` (infrastructure.market)

Самотіковий об'єкт (як «сесія»): володіє власним `BukkitTask`,
`start(Plugin, List<Player>, Runnable onComplete)` / `cancel()`.

- **Сценарій** — константний `List<String>` укр-реплік: привітання/де ви →
  правила (насильство карається, здібності тут мовчать) → принципи торгівлі
  (лот предметом із руки, замовлення, торг, скупка в Посередника, комісія,
  анонімність).
- **Друкарський ефект:** для кожної репліки — відкриваємо по 1 символу раз на
  `char-ticks` (≈2 тіки/символ) у action bar усім учасникам; на кожен
  непробільний символ — звук `Sound.BLOCK_WOODEN_BUTTON_CLICK_ON`, гучність
  ~0.3, злегка варійований pitch («друкарська машинка», не голосно).
- **Утримання рядка:** повний рядок висить `line-hold-ticks` (≈40 тіків),
  action bar перепосилається щотіку, щоб не згас.
- **Учасник вийшов** посеред доповіді → просто пропускаємо (беремо живих онлайн).
- **Завершення:** `onComplete` → `GatheringService` знімає `frozen` з усіх,
  додає всіх у `briefed`, шле `[Збори] Тепер ви вільні. Торгуйте.`
- `cancel()` на `close()`/`forceCloseIfActive` (страховка; unfreeze робить close).

Реплики-хінти «Торгуйте — кафедра / скупка» з поточного `open()` переносяться
у сценарій доповіді (щоб не дублювати чат поверх action-bar-доповіді).

### Вкладка «Принципи торгівлі» в меню

- `openMain`: кнопка (`Material.KNOWLEDGE_BOOK`) «Принципи торгівлі».
- Клік: якщо гравець **не** в `briefed` → `[Збори] Спершу вислухайте
  Посередника.`; інакше `openPrinciples(player)` — інфо-GUI (rows 6,
  вимкнені взаємодії) з переліком принципів у lore + кнопка «◄ Назад» → `openMain`.
- `briefed: Set<UUID>` у `GatheringService` (per-session; чиститься в `close()`).
  Гейт для async не потрібен — меню відкривається лише в головному потоці.

---

## Wiring (ServiceContainer) — підсумок

- `MarketItemNamer` → в `MarketMenu`.
- `abilityExecutor.setGatheringAbilityGuard(gatheringService)` після побудови обох.
- `MarketMenu` → в `GatheringListener` (для кліку по кафедрі).
- `OrganizerBriefing` — створюється всередині `GatheringService.open()`
  (тримається полем, cancel у close), або інжектиться як фабрика; деталь реалізації.

## Тести

- `GatheringConductTest` (JUnit): WARN→KICK, незалежність гравців.
- `BuybackPriceTableTest` — без змін (конфіг не читає).
- Ефекти (доповідь, заморозка, кафедра, action bar) — перевірка in-server
  (`mvn clean package` → JAR → сервер), не мокаються.

## Оновлення документації (той самий комміт)

`.claude/rules/market-gathering.md`: додати механіку порушень/бану
(`GatheringConduct`, `GatheringAbilityGuard`, `bannedFromNext` у snapshot),
кафедру-вхід у меню, доповідь-заморозку, `MarketItemNamer`. Схема snapshot —
нове поле `bannedFromNext` (зворотна сумісність через null-default).

## Поза обсягом (YAGNI)

- Пізній вхід у вже відкритий збір (гравець, що вийшов під час доповіді, не
  повертається — телепортується додому, як і зараз).
- Гейт торгових операцій під час доповіді (гравці заморожені; ризик низький).
