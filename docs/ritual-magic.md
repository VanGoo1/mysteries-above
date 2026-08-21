# Ритуальна магія (Fool / Door / WhiteTower)

## Механізм

- **Спільна здібність** — `me.vangoo.pathways.common.abilities.RitualMagic` (Посл. 9 у Fool,
  Door, WhiteTower; пакет `pathways.common` — для здібностей, що належать кільком шляхам
  одразу). Прогрес БЕЗ заміни класу: доступність ритуалів гейтить `RitualCatalog.availableFor`
  (Посл. 9 — 3, 8 — 5, 7 — усі 7), сила ефектів — `SequenceScaler` (MODERATE).
- **Домен** — `domain.rituals` (у `PURE_DOMAIN` ArchUnit, нуль Bukkit): `RitualType`,
  `RitualRecipe` (гейт послідовності, свічки, інгредієнти як імена `Material` у String),
  `RitualCatalog` (юніт-тест `RitualCatalogTest`), `IngredientSourceIndex` (звідки береться
  інгредієнт: істота з `creatures.yml` чи форедж із `forage.yml`; юніт-тест
  `IngredientSourceIndexTest`) + `IngredientHint`, `SacrificeKind`/`SacrificeAppraiser`
  (юніт-тест `SacrificeAppraiserTest`), `RitualEffectMath` (базові числа тривалостей/ремонту
  до Sequence-скейлу — `LUCK_BASE_TICKS`, `SANCTIFY_BASE_DURABILITY`,
  `EVENTS_BASE_WINDOW_SECONDS`, `WALL_BASE_TICKS` — і формула шансу Дарування
  `bestowmentChance(sequenceLevel)`, юніт-тест `RitualEffectMathTest`). Винесено з ефект-шару
  так само, як `SacrificeAppraiser`: балансна математика ритуалів не належить runner-у.
- **Вівтар фізичний**: запалені `CANDLE`-блоки в радіусі 3 бл від кастера; базові ритуали —
  3 свічки, ритуали Посл. 7 — 5. Меню — `context.ui().openChoiceMenu`, deferred; духовність
  (і кулдаун) списує `AbilityResourceConsumer` у колбеку меню на СТАРТІ обряду.
- **Інгредієнти й жертву списуємо ЛИШЕ по завершенні заклинання** (`RitualMagic.completeRitual`,
  колбек `onComplete` сесії), а не в мить кліку по меню — інакше предмет із головної руки
  «згорав» до того, як гравець устигав узяти потрібне. На кліку — тільки м'яка перевірка
  наявності інгредієнтів; жертву в руці взагалі не читаємо (гравець бере її під час читання,
  його про це попереджають). На завершенні перечитуємо інвентар/руку: пусто → «Обряд згас»
  без ефекту (духовність уже сплачено — це і є ціна невдалої підготовки).
- **Сесія** — `RitualSession` (самотікова, патерн DuelBriefing): заклинання по літерах
  в action bar (4-частинна структура з вікі — `RitualIncantations`), зрив = вихід за 4 бл /
  урон / смерть / офлайн → `onAbort` (sanity +2, для Молитви удачі — ще Unluck).
  Реєстр сесій — instance-поле `RitualMagic`, `cleanUp()` скасовує всі.
- **Ефекти** — `RitualEffectRunner` (stateless, живе в `pathways.common.abilities`): удача
  (Luck), освячення (ремонт міцності), жертвопринесення (духовність за `SacrificeAppraiser`),
  одкровення (книга-натяк за `RitualEffectMath.bestowmentChance`), спіритизм/дзеркало (минулі події через `IEventContext.getPastEvents` — CoreProtect), стіна
  духовності (відштовхує монстрів + Resistance, самозгасний таск). Одкровення предметів НЕ
  створює — див. окремий розділ нижче. Runner лише читає базові
  числа з `RitualEffectMath`, скейлить їх через `SequenceScaler` і виконує Bukkit-ефекти —
  сам нових балансних чисел не тримає.

## Як додати ритуал

1. Значення в `RitualType` + запис у `RitualCatalog.ALL` (посл./свічки/інгредієнти/опис укр).
2. 4 рядки заклинання в `RitualIncantations.LINES`.
3. Базове число (тривалість/поріг) — константа в `RitualEffectMath`, якщо ритуал її потребує.
4. Гілка в `RitualEffectRunner.run` + іконка в `RitualMagic.iconFor`.
5. Онови пінінг у `RitualCatalogTest` (кількість, гейти) і, якщо додав формулу, —
   `RitualEffectMathTest`.

## Ритуал одкровення (`BESTOWMENT`, Посл. 8) — нагорода є ЗНАННЯМ, не матерією

**Інваріант: ритуал ніколи не створює інгредієнт.** Раніше він видавав випадковий інгредієнт
наступної Послідовності за `3× NETHERITE_SCRAP` — і на сервері з Create (де незерит
фармиться конвеєром) це був прямий конвеєр «руда → прогрес по шляху». Виправлення не в
піднятті ціни: будь-яку фармабельну валюту мод рано чи пізно здешевить. Нагорода змінена на
таку, що **не стакається** — інформацію. Другий каст на ту саму Послідовність дає ту саму
книгу, тож фарм жертви не конвертується ні в що.

- **Плата** — не `Material`, а один інгредієнт **чужого шляху** й саме Посл. N−1, із головної
  руки (`requiresHandSacrifice = true`, `ingredients = Map.of()`). Виражати таку жертву через
  `RitualRecipe.ingredients` не можна: там імена `Material`, а інгредієнти — кастомні
  предмети з NBT. Валідатор — `RitualMagic.bestowmentRejection` через наявний
  `context.beyonder().findRecipesUsing(hand)`.
- **Перевірка стоїть ДВІЧІ** — у `startRitual` (до списання духовності) і в `completeRitual`
  (до знищення предмета). Це не перестраховка: `completeRitual` нищить жертву ще до
  `runner.run`, тож одна перевірка з'їдала б рідкісний предмет за неправильний клік.
- **Нагорода** — `RevelationBook`: `WRITTEN_BOOK` **без NBT**, тож
  `RecipeBookFactory.isRecipeBook` її не впізнає й прочитання нічого не розблоковує. Це
  свідомо: рецепти дають лут, церкви й ордени — ритуал дає лише сліди.
- **Текст — натяк, не карта**: імені моба й точного біома книга не називає, лише сімейство
  місцевості (`RevelationBook.family`: `*OCEAN` → «у морях», `*CAVES`/`DEEP_DARK` → «у
  глибоких печерах», нетерські → «у Незері» тощо). Шукати все одно доводиться.
- **Дані** — `IngredientSourceIndex`, який `ServiceContainer` будує раз на старті з
  `creatureRegistry` + `forageConfig.biomes()` і віддає в `AbilityContextFactory` поруч із
  `creatureRegistry`. Саме тому `forage.yml` тепер читається в `initializeApplicationServices`,
  а не в `initializeSchedulers`. `global_loot.yml` для цього марний: лут глобальний, місця в
  ньому немає — інгредієнт без джерела дає «сліди губляться».
- Джойн «інгредієнт → джерело» робить `BeyonderContext.ingredientHints`, бо id дістається з
  NBT (інфраструктура), а шар здібностей має бачити вже готові підказки.

❌ Не повертати видачу предметів у `runBestowment` і не платити за нього ванільним
матеріалом — обидва рухи відкривають ту саму дірку заново.

## Кришталева куля (Door, Посл. 7)

`door.abilities.CrystalBall` — one-shot, два режими: журнал власних спогадів
(`getAbilityEventHistory` + `getPastEvents`, фільтр за ім'ям кастера) і накладання силуетів
(викриття маскування: профіль/display name ≠ справжнє ім'я; опір — `new
AntiDivination().getIdentity()` через `context.beyonder().isAbilityActivated` +
`DivinationOdds`, як в інших ворожіннях).

## Перевтілення (Fool, Посл. 6) — маскування

`fool.abilities.Shapeshifting`: копіює скін (Paper `setPlayerProfile`, патерн
`GatheringAnonymizer` — профіль НЕ персистентний, релогін повертає вигляд), нік у табі
(`setPlayerListName`) і чаті (`setDisplayName`) будь-якого гравця з `getOfflinePlayers()`
(меню з голів, сортування за останнім входом, ліміт 53). Без ліміту часу; шкода НЕ знімає;
`getSpiritualityCost()` повертає `0` — увесь кошт періодичний, `getPeriodicCost()` = 20
духовності/с, знятий у власному таску сесії через нециклів-захоплений
`context.beyonder()` (не `IAbilityContext` кастера); вичерпання → авто-зняття. Реєстр масок —
instance-поле, `cleanUp()` знімає всі. Викриває маскування — Кришталева куля Door.

## Заборони

- ❌ Bukkit/`net.kyori` у `domain.rituals` (ArchUnit `PURE_DOMAIN`).
- ❌ Балансові числа ритуалів поза `domain.rituals` (`RitualEffectMath` для
  тривалостей/шансів, `SacrificeAppraiser` для оцінки жертви) — нові формули тільки чистими
  VO/константами з тестами, не локальними числами в `RitualEffectRunner`.
- ❌ `static` реєстри сесій/масок (правило сесій `pathway-abilities.md`).
- ❌ Видавати інгредієнти чи Характеристики БУДЬ-ЯКИМ ритуалом: єдиний предмет, який
  ритуали створюють, — книга-натяк Одкровення (див. розділ вище).
