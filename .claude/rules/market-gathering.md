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
