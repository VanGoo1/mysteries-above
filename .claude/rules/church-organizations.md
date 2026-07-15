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
  генераторі (юніт-тест `ChurchTaskGeneratorTest`). Розмір набору — `church.tasks.max-active`.
  **HUNT має фолбек**: якщо чужих груп не лишилось, полюємо на БУДЬ-ЯКИХ істот. Без нього
  Церква Блазня (її підгрупи покривають LordOfMysteries + GodAlmighty + TheAnarchy, тобто всі
  групи, що мають істот у `creatures.yml`) не давала HUNT ніколи — самі доставки. Дзеркалить
  `ChurchDuelService.pickOpponent`; пінить
  `ChurchTaskGeneratorTest.fallsBackToAnyCreatureWhenChurchDomainCoversEveryCreatureGroup`.
- **Вікно і квота наборів** — чистий `domain.organizations.TaskQuota` (юніт-тест `TaskQuotaTest`);
  стан на `Membership` (`lastTaskRefreshEpochMillis` = початок вікна, `taskSetsUsed`), рішення
  в `ChurchService.ensureFreshTasks` (кличеться при вступі й на кожному відкритті меню завдань;
  фонового планувальника НЕМА). Три правила:
  1. вікно (`church.tasks.refresh-hours`) вичерпалось → старт нового, `setsUsed = 0`, і
     **незрушені** (`progress == 0`) завдання відкидаються, а завдання З ПРОГРЕСОМ доживають
     (раніше 24-та година стирала half-виконане полювання без попередження);
  2. пул порожній + квота є → генерується ЦІЛИЙ набір, `setsUsed++` («закрий набір —
     отримаєш новий», без добового простою);
  3. квоти нема → чекати скидання.
  `church.tasks.sets-per-day` (дефолт 5) — це стеля фарму очок вкладу, а вклад визначає ранг
  і оплачує замовлення зілль. Міняєш її — міняєш швидкість просування по рангах.
  Набір, що не згенерувався (`generate` віддав порожньо), квоту НЕ витрачає.
  UI-стан — `ChurchService.taskPoolStatus` (`TaskPoolStatus`), годинник — `ChurchMenu.quotaTile`
  (слот 6,5 у меню завдань; формат відліку — спільний `ChurchMenu.formatDuration`).
- **Назви цілей у меню резолвляться з `targetKey`, а не з persisted `targetName`** (HUNT —
  `CreatureNamer.displayName`, DELIVER — `MarketItemNamer.displayName`): у завданнях, записаних
  до появи укр-назв, у `targetName` лежить англійський id, і резолв із ключа лікує їх без
  міграції `memberships.json`. Те саме в чат-повідомленні про виконане полювання
  (`ChurchService.onCreatureKilled`). HUNT-плитка додає підказку де шукати —
  `ChurchMenu.huntSpawnLore` (біоми + «біля структур» для apex).
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
  `hasOrdered`/`markOrdered`). Пише її САМ `setActiveOrder` — інваріант «активне замовлення
  завжди означає, що зілля вже замовляли» структурний, а не обов'язок викликача, тож
  `placeOrder` не кличе `markOrdered` окремо (і новий викликач не зможе про це забути).
  Запис на момент замовлення, а не видачі: скасувати замовлення не можна, тож ранній запис
  не лишає дірки. Живе на `Membership`, тому `leave()` стирає її разом із членством —
  пам'ять свідомо В МЕЖАХ ЧЛЕНСТВА, а не постійна per-гравець, як `initiationUsed`.
  Це НЕ лазівка «вийшов-повернувся»: вихід закриває цю церкву назавжди (див. нижче).
  Пінить `MembershipTest.activeOrderAlwaysLandsInOrderHistory`.
- **Вихід із церкви — необоротний.** `leave()` дописує id церкви в
  `ChurchService.abandonedChurches` (`Map<UUID, Set<String>>`, персиститься), а `join()`
  віддає `JoinResult.ABANDONED` для будь-якої зреченої церкви. Кулдаун
  (`rejoinCooldownUntil`) лишається й далі, але стосується вже ЛИШЕ інших церков.
  Гравець мусить розуміти, на що йде, тож обидва входи попереджають ДО дії: `ChurchMenu`
  показує в `ConfirmationMenu` явне «НАЗАВЖДИ: назад вас не приймуть», а `/church leave`
  двокрокова — перший виклик лише попереджає, діє тільки `/church leave confirm`.
  Зречена церква не показує кнопку «[Вступити]» взагалі — замість неї бар'єр «Двері
  зачинено» (`ChurchMenu.openGreeting`).
- **Вступ теж через `ConfirmationMenu`** (`ChurchMenu.confirmJoin`): обітниця однобічна,
  тож ціну гравець бачить ДО кліку, а не дізнається на виході. «Віддаєте» перелічує
  однобічність (двом церквам не служать, вихід лише назавжди, вклад і ранг згорять,
  кулдаун `rejoinCooldownDays()` на вступ в іншу), «Отримаєте» — завдання, ранги,
  замовлення зілля з його гейтами; рядок про Випробування шляху додається ЛИШЕ гравцю
  без шляху. Тексти мусять називати реальні механіки — якщо міняєш гейт замовлення чи
  кулдаун, онови й це попередження, інакше воно почне брехати.
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
  прапор пройденої дуелі-без-обраного-шляху (`trialPassed`, тимчасовий), прапор
  «вже колись ініційований» (`initiationUsed`, постійний) та `PlayerChurchData.abandonedChurches`
  — назавжди зречені церкви (null у старих файлах = нічого не зрікався; персиститься навіть
  для гравця БЕЗ `Membership`, тож `persist()` збирає id і з `abandonedChurches.keySet()`).
  `MembershipRecord.taskSetsUsed` — витрачені набори у вікні; поля нема в старих файлах →
  Gson дає `0`, тобто гравець заходить із ЦІЛОЮ квотою (пінить
  `ChurchRepositoriesTest.membershipFromBeforeNewFieldsLoadsWithThemAbsent`).
  `MembershipRecord.lastTaskRefreshEpochMillis` тепер означає початок вікна квоти — ключ НЕ
  перейменований свідомо: для старих файлів останнє оновлення і є стартом вікна, тож міграція
  живих даних не потрібна.
  `MembershipRecord.orderedPotions`
  — історія замовлень; у файлах, записаних до появи поля, це `null`, і
  `Membership.restoreOrderedPotionKeys(null)` читає його як порожню історію (пінить
  `ChurchRepositoriesTest.membershipWithoutOrderedPotionsFieldLoadsAsEmptyHistory`).
  Порожня історія + активне замовлення в такому файлі НЕ дають фори: інваріант
  `setActiveOrder` досіває ключ, а `restoreOrderedPotionKeys` перезатверджує його після
  свого `clear()`, тож гідрація не залежить від порядку двох викликів (пінить
  `MembershipTest.legacyHydrationSeedsHistoryFromActiveOrderInEitherOrder`).
  `church-sites.json` (`ChurchSiteRepository`) — сайти + оброблені села.
  `churches-state.json` (`ChurchStateRepository`) — сховища (institutionId → itemKey → кількість).
  Усі — Gson-каркас `GatheringSnapshotRepository` (corrupt/missing → `Optional.empty()`).
  Схема record-ів = JSON-схема: змінюєш поле — думай про зворотну сумісність живих даних.

## Як додати інституцію в реєстр

1. Додай `new Institution(...)` у `InstitutionRegistry.buildAll()` (id `church-*`/`order-*`,
   kebab; CHURCH мусить мати ≥1 `PathwayAccess`, інакше конструктор кине).
2. Онови лічильники/пінінг у `InstitutionRegistryTest` (кількість церков/орденів, покриття шляхів).
3. Церква → додай `.nbt` ключ у `mysteries-datapack/README-churches.md` (фолбек працює без нього).

## Як міняти темп завдань

- Довжина вікна — `church.tasks.refresh-hours`; розмір набору — `church.tasks.max-active`;
  скільки наборів на вікно — `church.tasks.sets-per-day`. Усі три мають дефолт у `ChurchConfig`
  (Bukkit не перезаписує наявний config.yml, без фолбеку сервер поїде на нулях).
- Стеля завдань на вікно = `sets-per-day` × `max-active`. Це і є ліміт фарму очок вкладу.

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
- Покинута церква закрита для гравця назавжди — `abandonedChurches` НЕ чиститься ніколи
  (як `initiationUsed`). Саме це тримає інваріант вище: історію замовлень можна стерти
  виходом, але повернутись у ту саму церкву вже не можна.
- Набір завдань видається лише ЦІЛИМ і лише в порожній пул; квота витрачається виключно за
  реально згенерований набір (порожній `generate` її не палить).
- Завдання з прогресом (`progress > 0`) не стирається скиданням вікна — згорає тільки незрушене.
- Кожна церква має щонайбільше один сайт на світ (`unplacedChurchIds` мінус розміщені).
- Стан пишеться після кожної мутації (краш між батчами не лишає сховище/членство неузгодженим).
- `ChurchService` стан — лише instance-поля, ніколи `static`.

## Заборони

- ❌ Мутувати `ChurchVault` повз `ChurchService` — обхід персисту й інваріанта незлічуваності.
- ❌ Видавати зілля/рецепти повз замовлення (`placeOrder`/`claimOrder`) чи ініціацію-дуель
  (`completeTrialInitiation`) — це єдині канали видачі.
- ❌ Додавати `io.lumine..` чи Bukkit у `domain.organizations` (ArchUnit `PURE_DOMAIN`).
- ❌ Кликати `spawnAllNpcs()` двічі — дублікати NPC-священиків (єдиний виклик в `onEnable`).
