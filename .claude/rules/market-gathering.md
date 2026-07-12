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
- Предмети монет (`CurrencyCodec`): «Золотий фунт» = `MUSIC_DISC_MELLOHI`, «Коппет» =
  `MUSIC_DISC_STAL` — самостійні пластинки, підготовані під заміну текстури РЕСУРС-ПАКОМ
  так само, як Характеристики (рядковий custom-model-data `gold_pound` / `coppet`,
  `setMaxStackSize(64)`, `unbreakable`, item-flags). `jukebox_playable` знято рефлексією
  (`tryStripJukeboxPlayable`, як у `CharacteristicCodec`) — у програвальник не вставляються.
- Ручний розмін у руці — `CurrencyExchangeListener` (presentation), з підтвердженням через
  `ConfirmationMenu`: звичайний ПКМ по фунту → 1 фунт у 20 коппетів (лише в повітря / по
  неінтерактивному блоку, щоб не красти інтеракції); Shift+ПКМ по коппетах → усі повні
  двадцятки стаку в руці у фунти. Це НЕ емісія (сума збережена, net-zero) — предмети
  ростуть лише через `CurrencyCodec`, стак у руці зменшується рівно на витрачене, а
  конвертація на підтвердженні перечитує руку (клік — лише прев'ю).
- **Ціни скупки скейляться від послідовності для ВСІХ категорій** (9 = найслабша …
  0 = найсильніша): `ingredient-coppets-by-seq`, `recipe-book-coppets-by-seq`,
  `characteristic-coppets-by-seq` (мапи `seq→коппети`) + `overrides` (точкова ціна на
  `custom:<id>`, перекриває скейл) + `ingredient-coppets` (плоский фолбек для інгредієнта
  БЕЗ послідовності). Кожна мапа має ФОЛБЕК у коді
  (`MarketConfig.DEFAULT_*_COPPETS`, вантажить `loadSeqMap`): якщо серверний config.yml без
  секції (Bukkit не перезаписує наявний config при оновленні плагіна) — беруться дефолти,
  інакше скуповувалося б за нуль.
- **Послідовність інгредієнта виводиться з рецептів**, а не з предмета (у `CustomItem`
  її нема): `IngredientSequenceIndex.build` (application) сканує потони всіх шляхів і мапить
  `custom:<id> → найнижча (найсильніша) послідовність рецепта, де інгредієнт ужитий`;
  індекс інжектиться сеттером у `MarketItemClassifier` у `ServiceContainer` ПІСЛЯ побудови
  `PotionManager` (потони створюються після класифікатора). Інгредієнт поза рецептами →
  `sequence = -1` → плоский фолбек `ingredient-coppets`.
- Краш-безпека: `gathering-state.json` (`GatheringSnapshotRepository`) пишеться після
  кожної мутації; відновлення НЕ продовжує сесію — усе в чергу повернень
  (видається лістенером при вході гравця).
- Анонімність (на час OPEN): Paper `setPlayerProfile` без `textures` (дефолтний скін),
  scoreboard-team `ma_gathering` ховає ніки, чат зали перехоплює `GatheringListener`
  і подає від «Незнайомець №N» (`MarketSession.aliasOf`).
- Виставлення лота / оферта / скупка — предметом у ГОЛОВНІЙ РУЦІ (не GUI-слоти):
  менша поверхня дюпів. Ціни/кількість — через `ChatPromptService` (одноразовий чат-промпт).

## Бартер: ціна як `Consideration`

- Ціна лота / оферти / зустрічної — `domain.market.Consideration` (чистий VO):
  опційна `ItemDemand` (`itemKey` + `amount`) та/або грошовий буст `PoundMoney`
  (мінімум одне присутнє; юніт-тест `ConsiderationTest`). `Lot`, `Negotiation`,
  `Settlement` квотуються `Consideration`, не `PoundMoney`.
- **Комісія лише з ЧИСТО грошових угод.** `Consideration.commission(rate)` = нуль,
  якщо є предметна складова (бартер, навіть із бустом), інакше `ceil(гроші×rate)`.
  Це свідомо лишає бартер поза грошовим стоком.
- **Ескроу однобічний.** Ескроюється лише товар продавця (як раніше). Предмет
  покупця НЕ ескроюється — його знімають атомарно на сеттлменті
  (`GatheringService.removeMatching` → `deliverItem` продавцю). Тому схема
  снепшота й інваріант збереження ескроу не змінюються.
- **Введення ціни — через GUI, не off-hand.** Головна рука = товар, який
  віддаєш (лот/оферта). Тип ціни обирається в `MarketMenu.chooseConsideration`
  (міні-меню «Монетами / Предметом»): «Монетами» → чат-промпт суми
  (`Consideration.money`); «Предметом (бартер)» → `openBarterPicker` (пікер зі
  знайомих гравцю інгредієнтів `knownIngredientStacks`) → чат-промпт кількості →
  чат-промпт грошового бусту (`0` дозволено) → `Consideration.of(demand, boot)`.
  Предметну вимогу можна попросити ЛИШЕ зі знайомих інгредієнтів (обмеження
  пікера). Колишній off-hand-механізм прибрано як зовсім непомітний для гравця.
- **Перед-перевірки:** `buyLot`/`accept` звіряють, що покупець має предметну
  вимогу (`countMatching`) ДО мутації домену (симетрично до перевірки монет);
  брак предмета лишає торг відкритим. Утиліта `barterShortfall` дає текст
  причини для UI перед підтвердженням.

## Підтвердження необоротних дій

- `infrastructure.ui.ConfirmationMenu.open(player, give, get, title, onConfirm)` —
  спільне «Ви віддаєте ↔ Ви отримаєте» з кнопками Підтвердити/Скасувати.
- Скупка організатором (`OrganizerClickListener`) більше НЕ продає миттєво:
  рахує ціну через `GatheringService.buybackPayout` і відкриває підтвердження;
  сам продаж (`buybackFromHand`, емісія) — на кнопці Підтвердити. Нульова ціна
  відхиляється одразу.
- Прийняття/купівля БАРТЕРНОЇ угоди (де віддаєш предмет) теж проходить
  `ConfirmationMenu`; чисто грошові купівлі/акцепти лишаються в один клік.

## Розсадка учасників

- `infrastructure.market.VenueLayout` — чиста геометрія (юніт-тест
  `VenueLayoutTest`): `organizer()` (чоло зали, обличчям до залу) і
  `attendee(index, total)` (рознесена сітка перед Посередником, обличчям до
  нього, в межах підлоги). `GatheringVenueProvider.organizerSpawn()` /
  `attendeeSpawn(i, n)` загортають їх у `Location`; `GatheringService.open()`
  телепортує кожного на власний слот, а NPC — на подіум. Раніше всі падали в
  одну точку всередині Посередника.

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
  по літері в action bar АУДИТОРІЇ (звук `BLOCK_WOODEN_BUTTON_CLICK_ON`
  на кожній видимій літері), тримає повний рядок `LINE_HOLD_TICKS`, кличе
  `onComplete` по завершенні. Створюється й стартує в `GatheringService.open()`
  одразу після телепорту й анонімізації учасників. Аудиторія — `frozenAudience()`
  = лише ще заморожені гравці (той, хто пропустив, доповіді більше не бачить).
- На час доповіді гравці — у множині `frozen` (`GatheringService`);
  `GatheringListener.onFrozenMove` скасовує `PlayerMoveEvent`, поки
  `gatheringService.isFrozen(playerId)`. `onBriefingComplete()` (природний кінець
  скрипту) звільняє всіх, хто ще заморожений (`freeFrozen` для кожного). `frozen`
  і `briefed` — instance-`Set<UUID>`, чистяться також при `expel`/`close`
  (`frozen.remove` / `briefed.remove`; `handleQuit()` — НЕ чистить).
- **Пропуск доповіді — ПЕР-ГРАВЦЕВИЙ** (не на всю залу): `PlayerToggleSneakEvent`
  (початок присідання) на замороженому гравці кличе
  `GatheringService.skipBriefing(player)`, що звільняє САМЕ його
  (`freeFrozen`: `frozen.remove` + `briefed.add` + сигнал «Тепер ви вільні» в
  action bar) — доповідь для решти триває. Коли заморожених більше не лишилось,
  `skipBriefing` глушить спільний тік (`briefing.cancel()`). (Раніше пропуск
  одним завершував доповідь для всіх — це був баг.)
- Меню ринку (`/gathering menu`, кафедра) гейтоване не лише на
  `isOpenParticipant`, а й на `hasBeenBriefed` — інакше заморожений гравець
  міг відкрити торги до завершення доповіді.
- **Усі слова Посередника — в action bar**, не в чат (як і сама доповідь):
  репліки при скупці (`OrganizerClickListener.organizerSay` — «Покажи, що
  приніс», «За таке не дам і коппета»), результат скупки та сигнал звільнення
  (`GatheringService.organizerSay`). Наратив-запрошення `announce()` («Шепіт у
  пітьмі…» з клікабельним `[Прийти]`) лишається в чаті — це не мова Посередника.

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
2. Ціна скупки: `BuybackPriceTable.unitPriceFor` (мапа `seq→коппети` для категорії) +
   секція `market.buyback.<категорія>-coppets-by-seq` у config.yml + дефолт-мапа в
   `MarketConfig.DEFAULT_*_COPPETS` (фолбек для старого config) + кейс у
   `BuybackPriceTableTest`. Якщо в категорії немає власної послідовності — треба джерело
   послідовності (як `IngredientSequenceIndex` для інгредієнтів) + плоский фолбек.

## Заборони

- ❌ Створювати монети повз `CurrencyCodec`/`WalletService` — канали емісії лише:
  скупка організатором, лут, `/coins give`. (Розмін фунт↔коппети в руці — net-zero,
  не емісія: `CurrencyExchangeListener` теж робить монети через `CurrencyCodec`.)
- ❌ Мутувати `MarketSession` повз `GatheringService` — обхід ескроу і снепшота.
- ❌ Готові зілля на ринку — свідомо заборонені (пряма купівля просування).
- ❌ Товар у лут-таблицях із префіксом `characteristic:` — інваріант Спеку 1 лишається.
