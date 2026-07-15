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
  сховища церкви `church.order.brew-hours` годин, потім `[Забрати]` у священика. Замовлення
  доступне лише гравцю З ВЖЕ ОБРАНИМ шляхом — гравець без шляху, що тицяє замовити, тепер
  перенаправляється на «спершу пройдіть Випробування шляху» замість прямого пікера шляхів
  (цей обхід ініціації закрито).
- **Гейти замовлення** — усі в `ChurchService.quoteOrder`, яка повертає `OrderOffer`
  (`quote` + `OrderDenial`), а НЕ `Optional.empty()`: кожна відмова має власну причину й
  укр-текст у `ChurchMenu.denialText`. Порядок: технічні випадки (не член / без шляху /
  вже Посл. 0 / шлях поза доменом церкви / нема ціни чи рецепта) → `UNAVAILABLE`;
  стеля рангу → `RANK_TOO_LOW`; **засвоєння < 100%** → `MASTERY_INCOMPLETE`;
  **ключ уже в історії** → `ALREADY_ORDERED`; **баланс < ціни** → `NOT_ENOUGH_POINTS`
  (єдина причина, що несе `quote` — щоб UI показав ціну поруч із балансом);
  брак у сховищі лишається успішним `quote` з непорожнім `missing()`.
  Замовити можна ЛИШЕ наступну послідовність (`targetSeq = getSequenceLevel() - 1`).
  Гейт 100% — це `beyonder.getMastery().canAdvance()`, той самий предикат, що гейтить
  `canConsumePotion`/`advance()`: НЕ заводь окрему константу чи ключ конфігу для «100%».
  `placeOrder` ходить через `quoteOrder`, тож гейти захищають і його.
- **Історія замовлень** — `Membership.orderedPotionKeys` (ключ `"<шлях>:<посл.>"`,
  `hasOrdered`/`markOrdered`). Пишеться в `placeOrder` (не в `claimOrder`: скасувати
  замовлення не можна, тож ранній запис не лишає дірки). Живе на `Membership`, тому
  `leave()` стирає її разом із членством — пам'ять свідомо В МЕЖАХ ЧЛЕНСТВА, а не
  постійна per-гравець, як `initiationUsed`.
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
- **Ініціація — дуель** (замінила стару сховищну ініціацію повністю, не депрекейт:
  `canStartInitiation`/`startInitiation`/`claimInitiation` ВИДАЛЕНІ з `ChurchService`).
  Член церкви БЕЗ шляху, для якого `canStartTrial(player)` — true, бачить кнопку
  «[Випробування шляху]» → підтверджує через `ConfirmationMenu` →
  `application.services.ChurchDuelService.startTrial` телепортує гравця в арену
  `mysteries_duel` (`infrastructure.organizations.DuelArenaProvider` — лінива генерація
  void-світу з плоскою кам'яною платформою й фіксованими точками спавну гравця/опонента),
  програє репліку священика по літері в action bar
  (`infrastructure.organizations.DuelBriefing`, той самий патерн, що `OrganizerBriefing` на
  ринку), потім спавнить Seq-9 істоту з `PathwayGroup`, ЧУЖОЇ для доменів церкви (фолбек —
  будь-яка Seq-9 істота, якщо чужих нема). Живий стан бою — `application.services.DuelSession`
  (опонент, точка повернення, попередній game mode, власний timeout-таск); Bukkit-глюй —
  `presentation.listeners.DuelListener`: заморожує рух гравця на час доповіді, СКАСОВУЄ
  летальний удар по дуелянту й кличе `onPlayerLost` замість смерті (гравця лікує на повне
  здоров'я й повертає з усіма речами — на програші НІЧОГО не втрачається), реагує на смерть
  опонента як на перемогу, страхує quit/join.
  Перемога → `markTrialPassed`/`hasPassedTrial`; гравець бачить «[Обрати шлях]» → обирає з
  доступних церкві шляхів (`initiationPathwayChoices`) → `completeTrialInitiation` видає
  Seq-9 зілля обраного шляху ПЛЮС рецепт-знання напряму, без жодної залежності від сховища
  церкви (стара схема вимагала HUNT-завдання + книгу рецепта + інгредієнти вже в сховищі —
  це реальна зміна поведінки). Два прапори в `ChurchService` (обидва `Set<UUID>`,
  персистуються в `memberships.json` через `PlayerChurchData`): `trialPassed` — тимчасовий,
  «дуель здолана, шлях ще не обрано», знімається (`remove`) в кінці успішного
  `completeTrialInitiation`; `initiationUsed` — постійний «вже ініційований колись» гейт
  (не видаляється ніколи, як і раніше), перевіряється в `canStartTrial`/`completeTrialInitiation`.
  Обидва переживають leave/join.
  Церкви, чий шлях ще не реалізований (`Pathway.hasAnyAbility()` == false — заготовка
  без здібностей і рецептів варіння), більше НЕ варять і не приймають замовлення зілля
  (нема рецептів), і не пропонуються у виборі шляху після дуелі
  (`initiationPathwayChoices`), але сама дуель-ініціація лишається можливою — вона
  незалежна від сховища й рецептів церкви.

## Персистентність

- `memberships.json` (`JSONMembershipRepository`) — членства, кулдаун повторного вступу,
  прапор пройденої дуелі-без-обраного-шляху (`trialPassed`, тимчасовий) та прапор
  «вже колись ініційований» (`initiationUsed`, постійний). `MembershipRecord.orderedPotions`
  — історія замовлень; у файлах, записаних до появи поля, це `null`, і
  `Membership.restoreOrderedPotionKeys(null)` читає його як порожню історію (пінить
  `ChurchRepositoriesTest.membershipWithoutOrderedPotionsFieldLoadsAsEmptyHistory`).
  `church-sites.json` (`ChurchSiteRepository`) — сайти + оброблені села.
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

- Сирі Характеристики зі сховища гравцям НЕ видаються (лише списання у варіння замовлень).
- Ініціація (дуель) НЕ торкається `ChurchVault` — видача Seq-9 зілля й рецепта в
  `completeTrialInitiation` йде повз сховище церкви.
- Книга рецепта у сховищі — знання-гейт, `consumeFor` її не споживає.
- Одне зілля (`шлях:посл.`) — щонайбільше одне замовлення на членство; зілля з церкви не
  накопичити «про запас»: гейт 100% засвоєння пускає замовлення лише тоді, коли гравець
  уже готовий його випити.
- Кожна церква має щонайбільше один сайт на світ (`unplacedChurchIds` мінус розміщені).
- Стан пишеться після кожної мутації (краш між батчами не лишає сховище/членство неузгодженим).
- `ChurchService` стан — лише instance-поля, ніколи `static`.

## Заборони

- ❌ Мутувати `ChurchVault` повз `ChurchService` — обхід персисту й інваріанта незлічуваності.
- ❌ Видавати зілля/рецепти повз замовлення (`placeOrder`/`claimOrder`) чи ініціацію-дуель
  (`completeTrialInitiation`) — це єдині канали видачі.
- ❌ Додавати `io.lumine..` чи Bukkit у `domain.organizations` (ArchUnit `PURE_DOMAIN`).
- ❌ Кликати `spawnAllNpcs()` двічі — дублікати NPC-священиків (єдиний виклик в `onEnable`).
