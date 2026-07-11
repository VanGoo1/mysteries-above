# Підпільний ринок (Збори Потойбічних)

## Механізм

- Фази `IDLE → ANNOUNCED → OPEN → CLOSING → IDLE` (`domain.market.GatheringPhase`);
  веде `GatheringService` (application); автозапуск — `GatheringScheduler`
  (щохвилини звіряє персистований час наступного збору).
- Правила грошей/лотів/торгу — ЧИСТИЙ `domain.market` (`PoundMoney`, `CoinChange`,
  `MarketSession`, `BuybackPriceTable`) з юніт-тестами; пакет у `PURE_DOMAIN`
  `ArchitectureTest`. Ефекти (телепорт, профілі, NPC, GUI) — application/infrastructure.
- Домен ВІДДАЄ команди (`Settlement`, `Refund`) — виконує їх `GatheringService`.
  ІНВАРІАНТ: кожен ескроу-предмет має рівно один вихід (угода або повернення);
  захищено `MarketNegotiationTest.conservationInvariantHolds`.
- Гроші: вся математика в коппетах (1 фунт = 20 коппетів). Емісія: скупка NPC
  (`BuybackPriceTable`, конфіг `market.buyback.*`) + лут (`currency:pound` /
  `currency:coppet` у `global_loot.yml`). Сток: комісія `market.commission-rate` (згорає).
- Краш-безпека: `gathering-state.json` (`GatheringSnapshotRepository`) пишеться після
  кожної мутації; відновлення НЕ продовжує сесію — усе в чергу повернень
  (видається лістенером при вході гравця).
- Анонімність (на час OPEN): Paper `setPlayerProfile` без `textures` (дефолтний скін),
  scoreboard-team `ma_gathering` ховає ніки, чат зали перехоплює `GatheringListener`
  і подає від «Незнайомець №N» (`MarketSession.aliasOf`).
- Виставлення лота / оферта / скупка — предметом у ГОЛОВНІЙ РУЦІ (не GUI-слоти):
  менша поверхня дюпів. Ціни/кількість — через `ChatPromptService` (одноразовий чат-промпт).

## Спокій зборів: попередження → кік → бан

- Лічильник порушень — чистий `domain.market.GatheringConduct` (WARN на 1-ше
  порушення учасника, KICK на кожне наступне; `reset()` скидає всіх). Санкцію
  виконує `GatheringService.recordViolation(Player)`: дебаунс
  `VIOLATION_DEBOUNCE_MILLIS` (2с) від спаму подій, при KICK — `bannedFromNext.add(id)`
  + `expel(player)` (звільняє ескроу гравця, знімає анонімність, телепортує додому) + `persist()`.
- Каст здібності на зборах блокує `AbilityExecutor` через сеттер-інжектований
  `GatheringAbilityGuard` (`application.services`, інтерфейс
  `interceptAbility(UUID) → boolean`) — `GatheringService` реалізує його й
  фіксує порушення в `interceptAbility`. Провід —
  `ServiceContainer`: `abilityExecutor.setGatheringAbilityGuard(gatheringService)`
  (не конструкторна залежність — уникає циклу `AbilityExecutor` ↔ market-сервіси).
  Удар/PvP на зборах — окрема точка входу в `GatheringListener`, що так само
  кличе `recordViolation`.
- Бан діє РІВНО на наступний збір: `bannedFromNext` (накопичується під час
  поточного OPEN) переноситься в `skipThisRound` на початку `announce()`
  (`skipThisRound.clear(); skipThisRound.addAll(bannedFromNext); bannedFromNext.clear()`)
  — `join()` відхиляє гравців зі `skipThisRound`, а через один цикл множина
  знову спорожняється. Обидві множини — `Set<UUID>` поля `GatheringService`,
  НЕ статичні.
- `announce()` також планує похвилинний `announceCountdown()`
  (`runTaskTimer` кожні 20*60 тіків), який шле приєднаним «Збори розпочнуться
  за N хв» до відкриття (`open()`), поки фаза лишається `ANNOUNCED`.

## Відкриття збору: доповідь Посередника

- `infrastructure.market.OrganizerBriefing` — самотіковий об'єкт (власний
  `BukkitTask`, `start()`/`cancel()`), друкує захардкожений `SCRIPT`
  по літері в action bar усіх учасників (звук `BLOCK_WOODEN_BUTTON_CLICK_ON`
  на кожній видимій літері), тримає повний рядок `LINE_HOLD_TICKS`, кличе
  `onComplete` по завершенні. Створюється й стартує в `GatheringService.open()`
  одразу після телепорту й анонімізації учасників.
- На час доповіді гравці — у множині `frozen` (`GatheringService`);
  `GatheringListener.onFrozenMove` скасовує `PlayerMoveEvent`, поки
  `gatheringService.isFrozen(playerId)`. `onBriefingComplete()` очищає
  `frozen` і додає учасників у `briefed`. Обидва — instance-`Set<UUID>`,
  чистяться також при `expel`/`close` (`frozen.remove` / `briefed.remove`;
  `handleQuit()` — НЕ чистить, лише `expel`/`close`).
- Пропуск доповіді: `PlayerToggleSneakEvent` (початок присідання) на
  замороженому гравці кличе `GatheringService.skipBriefing(player)` →
  `OrganizerBriefing.skip()` (ідемпотентно — `no-op`, якщо `task == null`,
  тобто доповідь уже завершилась). Це той самий `finish()`, що й природне
  завершення скрипту — одне завершення на всю аудиторію, без окремого
  повідомлення.
- Меню ринку (`/gathering menu`, кафедра) гейтоване не лише на
  `isOpenParticipant`, а й на `hasBeenBriefed` — інакше заморожений гравець
  міг відкрити торги до завершення доповіді.

## Кафедра (lectern)

- Постійний `Material.LECTERN` ставиться разом із залою в
  `GatheringVenueProvider.buildPlatformIfMissing` на `(1, PLATFORM_Y + 1, 0)`
  (тобто `(1, 65, 0)` — на 1 блок вище кам'яної підлоги).
- `GatheringListener.onLecternClick` слухає `PlayerInteractEvent`
  (RIGHT_CLICK_BLOCK), фільтрує за `Material.LECTERN` +
  `venueProvider.isVenueWorld(...)`, скасовує подію (глушить вбудоване
  GUI кафедри-книги) і, якщо `gatheringService.isOpenParticipant(player)`,
  відкриває `marketMenu.openMain(player)`. Команда `/gathering menu`
  лишається як фолбек — той самий вхід у меню.

## Назви товарів у меню

- `application.services.MarketItemNamer.displayName(itemKey)` перетворює
  ринковий `itemKey` на укр-назву для лотів/оферт/скупки в `MarketMenu`:
  `custom:` → `CustomItemService.getItem(id).displayName()` (фолбек —
  `humanize`), `recipe:<path>:<seq>` → «Книга рецептів (path, Посл. seq)»,
  `characteristic:<path>:<seq>` → «Характеристика (path, Посл. seq)»,
  інше — `humanize` (хвіст ключа після `:`, підкреслення → пробіли,
  капіталізація першої літери). Провід — `ServiceContainer` створює один
  екземпляр (`new MarketItemNamer(customItemService)`) і передає в
  `MarketMenu`.

## Снепшот: схема з двома полями бану

- `GatheringSnapshotRepository.Snapshot` — 6 полів:
  `nextGatheringEpochMillis`, `participants`, `escrow`, `pendingReturns`,
  `bannedFromNext`, `skipThisRound` (обидва — `List<String>` UUID).
  Старі файли без цих полів десеріалізуються в `null` — `initializeFromSnapshot()`
  явно перевіряє `!= null` перед заповненням множин (зворотна сумісність).
  `persist()` серіалізує ОБИДВІ множини щоразу.

## Як додати товарну категорію на ринок

1. Значення в `MarketItemCategory` (domain) + гілка в `MarketItemClassifier.classify`
   (порядок перевірок: Характеристика → книга рецептів → custom item; зілля/ванільне/монети → empty).
2. Ціна скупки: `BuybackPriceTable.unitPriceFor` + секція `market.buyback.*` у config.yml
   (+ кейс у `BuybackPriceTableTest`).

## Заборони

- ❌ Створювати монети повз `CurrencyCodec`/`WalletService` — канали емісії лише:
  скупка організатором, лут, `/coins give`.
- ❌ Мутувати `MarketSession` повз `GatheringService` — обхід ескроу і снепшота.
- ❌ Готові зілля на ринку — свідомо заборонені (пряма купівля просування).
- ❌ Товар у лут-таблицях із префіксом `characteristic:` — інваріант Спеку 1 лишається.
