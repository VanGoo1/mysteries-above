# Ритуальна магія (Fool / Door / WhiteTower)

## Механізм

- **Спільна здібність** — `me.vangoo.pathways.common.abilities.RitualMagic` (Посл. 9 у Fool,
  Door, WhiteTower; пакет `pathways.common` — для здібностей, що належать кільком шляхам
  одразу). Прогрес БЕЗ заміни класу: доступність ритуалів гейтить `RitualCatalog.availableFor`
  (Посл. 9 — 3, 8 — 5, 7 — усі 7), сила ефектів — `SequenceScaler` (MODERATE).
- **Домен** — `domain.rituals` (у `PURE_DOMAIN` ArchUnit, нуль Bukkit): `RitualType`,
  `RitualRecipe` (гейт послідовності, свічки, інгредієнти як імена `Material` у String),
  `RitualCatalog` (юніт-тест `RitualCatalogTest`), `SacrificeKind`/`SacrificeAppraiser`
  (юніт-тест `SacrificeAppraiserTest`), `RitualEffectMath` (базові числа тривалостей/ремонту
  до Sequence-скейлу — `LUCK_BASE_TICKS`, `SANCTIFY_BASE_DURABILITY`,
  `EVENTS_BASE_WINDOW_SECONDS`, `WALL_BASE_TICKS` — і формула шансу Дарування
  `bestowmentChance(sequenceLevel)`, юніт-тест `RitualEffectMathTest`). Винесено з ефект-шару
  так само, як `SacrificeAppraiser`: балансна математика ритуалів не належить runner-у.
- **Вівтар фізичний**: запалені `CANDLE`-блоки в радіусі 3 бл від кастера; базові ритуали —
  3 свічки, ритуали Посл. 7 — 5. Меню — `context.ui().openChoiceMenu`, deferred; ресурси
  списує `AbilityResourceConsumer` у колбеку, потім інгредієнти/жертва з інвентаря.
- **Сесія** — `RitualSession` (самотікова, патерн DuelBriefing): заклинання по літерах
  в action bar (4-частинна структура з вікі — `RitualIncantations`), зрив = вихід за 4 бл /
  урон / смерть / офлайн → `onAbort` (sanity +2, для Молитви удачі — ще Unluck).
  Реєстр сесій — instance-поле `RitualMagic`, `cleanUp()` скасовує всі.
- **Ефекти** — `RitualEffectRunner` (stateless, живе в `pathways.common.abilities`): удача
  (Luck), освячення (ремонт міцності), жертвопринесення (духовність за `SacrificeAppraiser`),
  дарування (шанс інгредієнта наступної посл. за `RitualEffectMath.bestowmentChance`),
  спіритизм/дзеркало (минулі події через `IEventContext.getPastEvents` — CoreProtect), стіна
  духовності (відштовхує монстрів + Resistance, самозгасний таск). Runner лише читає базові
  числа з `RitualEffectMath`, скейлить їх через `SequenceScaler` і виконує Bukkit-ефекти —
  сам нових балансних чисел не тримає.

## Як додати ритуал

1. Значення в `RitualType` + запис у `RitualCatalog.ALL` (посл./свічки/інгредієнти/опис укр).
2. 4 рядки заклинання в `RitualIncantations.LINES`.
3. Базове число (тривалість/поріг) — константа в `RitualEffectMath`, якщо ритуал її потребує.
4. Гілка в `RitualEffectRunner.run` + іконка в `RitualMagic.iconFor`.
5. Онови пінінг у `RitualCatalogTest` (кількість, гейти) і, якщо додав формулу, —
   `RitualEffectMathTest`.

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
- ❌ Видавати інгредієнти/Характеристики повз `RitualEffectRunner.runBestowment` —
  єдиний канал видачі предметів ритуалами.
