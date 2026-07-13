# Церкви та інституції (Економіка 6b)

## Механізм

- **Реєстр** — чистий `domain.organizations.InstitutionRegistry` (код, не конфіг):
  10 церков (`church-*`) + 25 таємних організацій (`order-*`). `Institution` (VO) —
  `id`, `InstitutionType` (`CHURCH`/`SECRET_ORDER`), назва, лор, список `PathwayAccess`
  (`full(pathway)` / `partial(pathway, minSeq)`, опційна `branch` для підгруп Церкви Блазня).
  Назви шляхів — ключі `PathwayManager` БЕЗ пробілів (`WhiteTower`); нереалізовані шляхи —
  той самий CamelCase (`TwilightGiant`, `RedPriest`…). Юніт-тести: `InstitutionTest`,
  `InstitutionRegistryTest` (пінять кількість, унікальність id, канонічні доступи).
- **Членство** — `domain.organizations.Membership` (мутабельний): `lifetimeContribution`
  (визначає ранг, НЕ зменшується) + `balance` (валюта замовлень). Ранги — `ChurchRank`
  (Вірянин/Служка/Диякон/Єпископ/Кардинал), пороги вкладу — `config.yml church.rank-thresholds`
  (5 значень, [0]=0). Стеля замовлення = `max(rank.minOrderSequence, access.minSequence())`.
- **Завдання** — `ChurchTask` (HUNT/DELIVER) + чистий `ChurchTaskGenerator`. HUNT — істота
  чужої `PathwayGroup`; DELIVER — інгредієнт зі шляхів церкви. Формули нагород — фіксовані в
  генераторі (юніт-тест `ChurchTaskGeneratorTest`). Пул оновлюється раз на
  `church.tasks.refresh-hours`, `church.tasks.max-active` активних.
- **Замовлення зілля** — `PotionOrder` (VO). Гравець платить очками вкладу; зілля вариться зі
  сховища церкви `church.order.brew-hours` годин, потім `[Забрати]` у священика.
- **Сховище** — `domain.organizations.ChurchVault` (itemKey → кількість). Книги рецептів
  (`recipe:<p>:<seq>`) — знання-ГЕЙТ, НЕ споживаються. Варіння списує класичні інгредієнти,
  інакше Характеристику-заміну (`consumeFor` атомарний; юніт-тест `ChurchVaultTest`).
- **Оркестратор** — `application.services.ChurchService`: увесь стан у instance-полях,
  гідрується з репозиторіїв у конструкторі, `persist()`/`persistState()` після КОЖНОЇ мутації.
  Провід — `ServiceContainer` (реєстр у core, конфіг+репо в infrastructure, сервіс+сайти в
  application, меню в UI, планувальник у schedulers).
- **Сайти** — `infrastructure.organizations.ChurchSiteService` (persist після мутації) +
  `ChurchStructurePlacer` (датапак `mysteries:church_<shortId>`; нема NBT → warn+фолбек без
  будівлі) + `infrastructure.citizens.ChurchPriestService` (NPC, SHOULD_SAVE=false — респавн
  зі `church-sites.json` в `onEnable().spawnAllNpcs()`, despawn в `onDisable`).
- **Автоспавн** — `presentation.listeners.ChurchSpawnListener` (`ChunkLoadEvent`): біля кожного
  нового села — випадкова ще не розміщена церква (кожна — щонайбільше раз на світ; оброблені
  села персистяться). Ключ села — min-кут bbox структури.
- **UI/вхід** — `infrastructure.ui.ChurchMenu` (клік по священику через `ChurchListener`),
  команда `/church bind|unbind|leave|info`. Kill-прогрес завдань і «зупинити рампейджера» —
  `ChurchListener` (`EntityDeathEvent`/`PlayerDeathEvent`).
- **Ініціація** — гравець БЕЗ шляху вступає в церкву, бере полегшене завдання, отримує зілля
  Seq 9 обраного шляху + рецепт у знання (`claimInitiation`). Одноразово (`initiationUsed`,
  переживає leave/join).

## Персистентність

- `memberships.json` (`JSONMembershipRepository`) — членства, кулдаун повторного вступу,
  флаг ініціації. `church-sites.json` (`ChurchSiteRepository`) — сайти + оброблені села.
  `churches-state.json` (`ChurchStateRepository`) — сховища (institutionId → itemKey → кількість).
  Усі — Gson-каркас `GatheringSnapshotRepository` (corrupt/missing → `Optional.empty()`).
  Схема record-ів = JSON-схема: змінюєш поле — думай про зворотну сумісність живих даних.

## Як додати інституцію в реєстр

1. Додай `new Institution(...)` у `InstitutionRegistry.buildAll()` (id `church-*`/`order-*`,
   kebab; CHURCH мусить мати ≥1 `PathwayAccess`, інакше конструктор кине).
2. Онови лічильники/пінінг у `InstitutionRegistryTest` (кількість церков/орденів, покриття шляхів).
3. Церква → додай `.nbt` ключ у `mysteries-datapack/README-churches.md` (фолбек працює без нього).

## Як додати ранг / змінити пороги

- Пороги вкладу — `config.yml church.rank-thresholds` (5 значень, [0]=0) + дефолт у
  `ChurchConfig.DEFAULT_RANK_THRESHOLDS` (Bukkit не перезаписує наявний config). Новий ранг —
  константа в `ChurchRank` (displayName укр + `minOrderSequence`) + оновити довжину масиву порогів.

## Інваріанти

- Сирі Характеристики зі сховища гравцям НЕ видаються (лише списання у варіння замовлень/ініціації).
- Книга рецепта у сховищі — знання-гейт, `consumeFor` її не споживає.
- Кожна церква має щонайбільше один сайт на світ (`unplacedChurchIds` мінус розміщені).
- Стан пишеться після кожної мутації (краш між батчами не лишає сховище/членство неузгодженим).
- `ChurchService` стан — лише instance-поля, ніколи `static`.

## Заборони

- ❌ Мутувати `ChurchVault` повз `ChurchService` — обхід персисту й інваріанта незлічуваності.
- ❌ Видавати зілля/рецепти повз замовлення (`placeOrder`/`claimOrder`) чи ініціацію
  (`claimInitiation`) — це єдині канали видачі.
- ❌ Додавати `io.lumine..` чи Bukkit у `domain.organizations` (ArchUnit `PURE_DOMAIN`).
- ❌ Кликати `spawnAllNpcs()` двічі — дублікати NPC-священиків (єдиний виклик в `onEnable`).
