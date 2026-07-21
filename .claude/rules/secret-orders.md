# Таємні організації (Економіка 6c)

## Механізм

- **Реєстр** — той самий чистий `domain.organizations.InstitutionRegistry`, що й для церков
  (`.claude/rules/church-organizations.md`): 25 `order-*` інституцій, `InstitutionType.SECRET_ORDER`.
  Орден із **порожніми** `accesses` — `Institution.acceptsAnyPathway()` — приймає БУДЬ-ЯКИЙ
  шлях («шляхи залежать від учасників»); орден із доступами — лише свої `PathwayAccess`.
- **Оркестратор** — `application.services.SecretOrderService` (написаний за зразком
  `ChurchService`): увесь стан — instance-поля, гідрується з двох репозиторіїв у конструкторі,
  `persist()` (членства/запрошення/кулдауни/фальшиві документи) і `persistState()`
  (схованки/розвіддані/кулдауни храмів і священиків) — після КОЖНОЇ мутації відповідного стану.
- **Ранг = послідовність, БЕЗ очок вкладу.** `domain.organizations.OrderRank.of(sequenceLevel)`
  (Пішак 8 / Вістря 6 / Довірений 4 / Магістр 2 / Прихований Владика 0 — `minSequence`, нижня
  межа щабля). На відміну від церков (`ChurchRank` — вклад), орден не накопичує жодного
  персистентного числа для рангу: він завжди виводиться з поточної послідовності Beyonder'а.
- **Вступ — двома шляхами**, обидва йдуть через `SecretOrderService.join`
  (`JoinResult`: `OK`/`ALREADY_MEMBER`/`NO_PATHWAY`/`WRONG_PATHWAY`/`COOLDOWN`/`ABANDONED`/
  `UNKNOWN_ORDER`):
  1. **Шифроване послання** (`custom-items.yml` `order_cipher_message`, ПКМ) — гравцю БЕЗ
     членства відкриває `OrderMenu.openJoinPicker` (лише ордени, що приймають його шлях;
     гравець без шляху отримує відмову «орденам потрібна сила», а не пікер).
  2. **Запрошення за вчинок** (`domain.organizations.InvitationRules` + `Invitation`,
     персистентне — не згорає): `onApexKilled` (домен вбитого апекса → орден того ж
     `PathwayGroup`, фолбек — будь-який орден, що приймає шлях гравця), `onBeyonderKilled`
     (лічильник `config.invitesBeyonderKills`, дефолт 3 → жорстко зашитий список
     `BLOOD_ORDERS`), `onRampagerStopped` (жорстко зашитий `ORDER_ORDERS`). Гравцю БЕЗ шляху
     запрошення не приходять узагалі (`InvitationRules.pickOrder` повертає порожньо).
     `/order accept <id>` вимагає реального запрошення в списку.
  Вступ сідить схованку ордену (`seedStashIfAbsent`, якщо ще не сіджена) і шле повідомлення
  Куратора «шукайте нас у меню Містичних Здібностей» — предмета-підтвердження нема, тож момент
  вступу мусить сам вказати на нову вкладку.
- **Завдання** — `OrderTaskGenerator` (чистий, дзеркалить `ChurchTaskGenerator`): на набір
  **щонайбільше одна храмова операція** (RAID або ASSASSINATE — ASSASSINATE лише від
  `rank ≥ TRUSTED`, інакше RAID) і **щонайбільше одна шпигунська** (RECON або SABOTAGE —
  лише якщо гравець сам член ЯКОЇСЬ церкви (`doubleAgentChurch`), ціль ЗАВЖДИ його ВЛАСНА
  церква; SABOTAGE лише від `rank ≥ BLADE`, інакше RECON); обидва слоти — монетка
  (`random.nextBoolean()`), можуть не випасти взагалі. Решта — чергування HUNT/DELIVER:
  HUNT — істота чужого `PathwayGroup` відносно доменів ордену (фолбек — будь-яка, як у
  церков); DELIVER — інгредієнти рецептів ШЛЯХУ ГРАВЦЯ (не ордену — схованка живить саме
  його прогрес). Вікно/квота — той самий `domain.organizations.TaskQuota` з 6b, конфіг
  `orders.tasks.*`, рішення в `SecretOrderService.ensureFreshTasks` (кличеться при вступі й
  на відкритті меню завдань).
- **Вага завдання → вага фавора**: `TaskWeight` (LIGHT/STANDARD/MAJOR). HUNT/DELIVER —
  LIGHT при Seq 9..6, STANDARD глибше; RECON — STANDARD; RAID/ASSASSINATE/SABOTAGE — MAJOR
  завжди. Завершення нараховує `Favor(weight, now)` — **окрім LIGHT, що має 0.35 шанс піти
  без фавора** («Куратор лише кивнув») — розминкові завдання не гарантовано конвертуються
  у валюту прохань (рандом сервіса, юніт-тестом не пінитьcя, задокументовано в коді).
- **Фавори — need-based прохання** (`domain.organizations.FavorOptions`, чистий, головне
  правило спеку): що гравець МОЖЕ попросити залежить від ситуації — `HUNT_INFO` завжди
  (LIGHT); `VAULT_INTEL` лише якщо орден має свіжі розвіддані бодай однієї церкви (LIGHT);
  від STANDARD — знає рецепт наступної посл. → `INGREDIENTS`, інакше `RECIPE_KNOWLEDGE`;
  від MAJOR — `CLEAR_COOLDOWN` і `FALSE_PAPERS` завжди, а `CHARACTERISTIC` лише якщо ЗНАЄ
  рецепт **і** `rank ≥ TRUSTED`. Кожен claim-метод (`claimHuntInfo`, `claimVaultIntel`,
  `claimRecipe`, `claimIngredients`, `claimCharacteristic`, `claimClearCooldown`,
  `claimFalsePapers`) списує НАЙДЕШЕВШИЙ фавор, що покриває потрібну вагу
  (`OrderMembership.consumeFavor` — MAJOR не палиться на дрібне прохання). **Матеріальний
  claim, що фактично нічого не видав** (нема апексів домену, нема свіжих розвіданих, рецепт
  уже відомий, у схованці бракує), **повертає списаний фавор гравцю** (`membership.addFavor`)
  — прохання не з'їдає валюту, якщо куратор реально нічим не допоміг.
- **Схованка ордену** (`domain.organizations.OrderStash`) — невидима членам, `itemKey → n`.
  **Входи**: одноразовий сід при вступі (`seedStashIfAbsent`, restart-safe — гейт на
  наявність ключа ордену), здача DELIVER-завдань, здача рейдової здобичі
  (`depositRaidLoot`). **Виходи — ЛИШЕ фавори** (`claimIngredients` — до
  `orders.favor.ingredients-per-claim` (дефолт 2) різних ключів наступної посл.;
  `claimCharacteristic` — 1x `characteristic:<шлях>:<наступна посл.>`). Сід: ордени з
  конкретними доступами — по `orders.stash.seed-ingredients-per-recipe` (дефолт 2) кожного
  інгредієнта Seq-9 рецепта КОЖНОГО реалізованого (`Pathway.hasAnyAbility()`) доступного
  шляху; ордени «будь-хто» (порожні доступи) — по 1x Seq-9 інгредієнти КОЖНОГО реалізованого
  шляху в грі (не лише вступника).
- **Рейд на сховище храму** (`startRaid` → `RaidSession` → `raidAlarm`/`raidSucceeded`/
  `raidFailed`/`onRaiderDied`, `depositRaidLoot`): гейти старту — активна RAID-задача на
  конкретну церкву, глупа ніч (`world.getTime()` у `[13000,23000]`), гравець у радіусі
  `orders.raid.zone-radius` від сайту храму, храм не закритий/не на кулдауні, нема вже
  активної сесії.
  **Вхід — `/order raid`** (`OrderCommand.handleRaid`), набраний вручну або натиснутий у
  клікабельній автопропозиції. Автопропозицію шле `offerRaids()` із хвилинного `tick()`
  (НЕ `PlayerMoveEvent` — той коштував би обходу сайтів на кожен крок кожного гравця;
  ціна рішення — до хвилини очікування під храмом, і про неї попереджає лор RAID-плитки).
  Гейти перевіряє мовчазний `raidConditionsMet` (без повідомлень, на відміну від
  `startRaid`), центр зони обидва беруть зі спільного `raidZoneCenter`.
  **Антиспам:** `raidOffered` (гравець → churchId, транзієнтний) тримає одну пропозицію на
  один захід; мітка знімається, щойно умови перестали виконуватись (вийшов із зони, настав
  день, вийшов із гри), тож повернення дає нову пропозицію, а прохід повз храм — ні.
  Текст пропозиції ОБОВ'ЯЗКОВО називає ціну («провал зачинить храм на N год так само, як
  успіх») — це замінює `ConfirmationMenu`, який тут не годиться: GUI засліпив би того, хто
  підкрадається до храму.
  `RaidSession` — самотіковий об'єкт (власний `BukkitTask`, тік щосекунди),
  за зразком `DuelSession`/`OrganizerBriefing`: **НІКОЛИ не static**, реєстр `Map<UUID,
  RaidSession>` живе в `SecretOrderService`, тік перечитує гравця через `Bukkit.getPlayer`
  щоразу — жодного захопленого `IAbilityContext`. Кожну секунду: гравець офлайн/мертвий/поза
  зоною → провал; поки не сполохано — ролл тривоги
  (`RaidPlanner.alarmChancePerSecond`, розвіддані знижують шанс через
  `raid-alarm-intel-factor`); дійшли до `raid-channel-seconds` → успіх. **Тривога** —
  спавн `orders.raid.guards` охоронців домену церкви (Seq ≤ 7, фолбек — будь-хто) +
  `broadcastToChurch` онлайн-членам. **Успіх** — `RaidPlanner.rollLoot` тягне зі знімка
  сховища церкви (`churchService.vaultSnapshot`), фактичне списання —
  `churchService.stealFromVault` (другий легальний вихід `ChurchVault`, поруч зі списанням
  у варіння), здобич одразу в руки гравцю + записується в `pendingRaidLoot` +
  `pendingRaidChurch` (**прив'язка до конкретної церкви** — щоб фавор не приписався іншому
  храму). Храм іде на `raid-temple-cooldown-hours` кулдаун і в успіху, і в провалі.
  **Фавор за RAID НЕ дається на успіху злому** — лише після
  `depositRaidLoot` (кнопка «Здати здобич» на RAID-плитці меню завдань): здача звіряє, що
  здобич саме з церкви цього завдання (`task.targetKey().equals(lootChurch)`), інакше
  відмова; повна здача завершує RAID-задачу й нараховує фавор. Провал (вихід із зони /
  таймаут) прибирає RAID-задачу зі списку БЕЗ фавора й, якщо гравець — член саме тієї
  церкви, що рейдив, ролить викриття (`exposureFailedRaidChance` → `expelExposedSpy`).
  Смерть рейдера (`onRaiderDied`) = провал сесії; якщо вбивця — член церкви-цілі,
  зараховується захист храму (`churchService.onTempleDefended`, +250 очок вкладу церкви).
- **Замах на священика** (`startAssassination`/`onGuardKilled`): вимагає активної
  ASSASSINATE-задачі на цю церкву. Удар по священику деспавнить його негайно
  (`priestService.despawnAt`) і зачиняє храм на `orders.assassination.priest-respawn-hours`
  (`priestClosedUntil`) — **храм чесно закритий із моменту удару**, незалежно від того,
  чи здолано охоронця; спавниться найсильніша (мін. `sequence`) істота домену церкви
  (фолбек — будь-яка). `SecretOrderService.tick()` (щохвилинний `OrderScheduler`) респавнить
  священика, коли `priestClosedUntil` минув, ЗАВЖДИ — навіть якщо охоронця так і не вбили.
  `assassinationGuards` (guardUuid → замовник+церква) — транзієнтний реєстр, не персистується
  (гине з мобом на рестарті, як рейдові сесії). **Фавор за замах іде ОРИГІНАЛЬНОМУ
  замовнику** (`GuardMark.assassin()`), а не тому, хто фактично добив охоронця — і
  зараховується, навіть якщо задачу вже стерло вікно-скидання під час бою (фолбек на
  `creditCompletion` напряму, коли `indexOfTask` не знаходить задачу).
- **Шпигунство подвійного агента** (`performSpyAction`, sneak-клік по священику ВЛАСНОЇ
  церкви гравця — `ChurchListener` явно ігнорує sneak-кліки, слот зарезервовано під
  `OrderListener`): активна RECON → знімок сховища церкви (`churchService.vaultSnapshot`) у
  `intel` (ключ `orderId|churchId`, TTL `orders.recon.ttl-hours`) — інші члени ордену бачать
  його через `claimVaultIntel`, і `orderHasIntel`/цей знімок знижує шанс тривоги наступного
  рейду на цю церкву; активна SABOTAGE → `churchService.delayRandomBrewingOrder` відкладає
  активне НЕготове замовлення випадкового ІНШОГО члена церкви на
  `orders.sabotage.delay-hours`. Обидві дії: завершення задачі → фавор → окремий ролл
  викриття (`exposureReconChance` / `exposureSabotageChance` → `expelExposedSpy`).
  Без активної RECON/SABOTAGE-задачі на цю церкву sneak-клік по власному священику — no-op.
- **Вкладка ордену замість предмета-талісмана.** Головне меню відкриває кнопка в меню
  Містичних Здібностей (`AbilityMenu.addOrderButton`, слот `6,5`), а не предмет у руці.
  Показується ЛИШЕ члену (`membershipOf().isPresent()`) і ЛИШЕ поза контролем маріонетки —
  слот `6,5` займає перемикач маріонетки, і два стани взаємовиключні, тож колізії нема.
  Не-члену кнопки нема взагалі: таємні ордени себе не рекламують, вхід у систему дає
  шифроване послання з лута або вчинкове запрошення.
  **Чому не предмет:** членство не мусить бути фізичним об'єктом, який можна загубити на
  смерті, вкрасти `ShadowTheft`-ом чи пред'явити як доказ приналежності. Орден пам'ятає
  своїх — доводити нічого не треба. Разом із талісманом пішли `OrderItems.createTalisman`/
  `isTalisman`, `reissueTalisman`, `/order talisman`, `orders.talisman.*` і запис
  `order_talisman` у `custom-items.yml`; `MUSIC_DISC_11` НЕ звільнилась — на ній лишається
  шифроване послання.
  **Провід:** `OrderMenu` і `ConfirmationMenu` будуються в `ServiceContainer.initializeUI`
  ПЕРЕД `AbilityMenu` — інакше конструкторна ін'єкція меню ордену неможлива. Сеттера тут
  свідомо нема: циклу між ними не існує, вистачає порядку.

## Персистентність

- **`order-memberships.json`** (`JSONOrderMembershipRepository`): на гравця —
  `MembershipRecord` (institutionId, curatorName, вікно/квота завдань, задачі, фавори) +
  кулдаун повторного вступу + `abandonedOrders` (назавжди зречені ордени) + запрошення +
  `beyonderKills` (лічильник до наступного запрошення) +
  **`pendingRaidLoot`** (незданий рейд-лут гравця) + **`pendingRaidChurch`** (з якої церкви
  цей лут — зв'язує здачу з правильною RAID-задачею; `null` у старих файлах = нема прив'язки,
  а порожній/`null` `pendingRaidLoot` = нема лута) + `falsePapers` (флаг фальшивих
  документів, дефолт `false` у старих файлах).
- **`orders-state.json`** (`OrderStateRepository`): `stashes` (orderId → itemKey → n),
  `intel` (`"orderId|churchId"` → знімок + TTL), `templeCooldownUntil` і `priestClosedUntil`
  (обидва churchId → epoch millis).
- Обидва — Gson-каркас (`corrupt`/`missing` → `Optional.empty()`), пишуться після КОЖНОЇ
  мутації відповідного стану (`persist()` / `persistState()` — не одна спільна функція,
  бо членства й схованки/розвіддані мутуються різними операціями). Нові поля в обох
  файлах — з `null`-фолбеком при читанні старих даних; жодної міграції наживо не потрібно.

## Як міняти темп

- Вікно/розмір/квота завдань — `orders.tasks.refresh-hours` / `max-active` / `sets-per-window`
  (той самий механізм `TaskQuota`, що в церков — стеля фарму фаворів = `sets-per-window` ×
  `max-active`).
- Рейд — `orders.raid.channel-seconds` (тривалість злому), `alarm-chance`/`alarm-intel-factor`
  (базовий шанс тривоги за секунду / множник зі свіжими розвідданими), `loot-picks`/
  `loot-intel-picks` (кількість витягів здобичі без/з розвідданими — Характеристики в пулі
  ЛИШЕ з розвідданими), `temple-cooldown-hours`, `zone-radius`, `guards`.
- Замах — `orders.assassination.priest-respawn-hours` (і закриття храму, і респавн
  священика — те саме число).
- Шпигунство — `orders.recon.ttl-hours` (свіжість розвіданих), `orders.sabotage.delay-hours`,
  `orders.exposure.recon-chance`/`sabotage-chance`/`failed-raid-chance` (шанси викриття).
- Схованка/фавори — `orders.stash.seed-ingredients-per-recipe`,
  `orders.favor.ingredients-per-claim`.
- Запрошення — `orders.invites.beyonder-kills` (поріг лічильника вбивств).
- Усі ключі мають дефолт у коді (`OrderConfig.load`) — Bukkit не перезаписує наявний
  `config.yml`, без дефолту сервер поїде на нулях/викривлених значеннях.

## Інваріанти

- Ранг ордену — ЗАВЖДИ похідна від поточної послідовності (`OrderRank.of`), жодного
  персистентного «очок вкладу» немає — на відміну від церков.
- Схованка ордену невидима членам; єдиний легальний вихід із неї — фавор-claim методи.
- Рейдова здобич — виняток пари кроків: гравець тримає її фізично між успіхом злому й
  здачею, але фавор за RAID-задачу нараховується ЛИШЕ після `depositRaidLoot`.
  Сира Характеристика гравцю в руки видається лише двома каналами: здобич рейду
  (`giveRaidLoot`) або `claimCharacteristic` (MAJOR-фавор, знає рецепт, `rank ≥ TRUSTED`) —
  жодного іншого шляху.
- Вихід із ордену необоротний — `abandonedOrders` не чиститься ніколи (дзеркалить
  `abandonedChurches` церков); `join()` на зречений орден завжди `ABANDONED`.
- ≤1 храмова операція (RAID/ASSASSINATE) і ≤1 шпигунська (RECON/SABOTAGE) на набір завдань —
  захищено генератором (`OrderTaskGenerator`), не UI.
- Замах закриває храм негайно з удару, незалежно від результату бою з охоронцем;
  `tick()` респавнить священика по таймеру завжди, а не лише по вбитому охоронцю.
- Фавор за замах іде тому, хто ЗАМОВИВ (вдарив першим), а не тому, хто фактично вбив
  охоронця — і переживає скидання вікна завдань під час бою.
- Увесь стан `SecretOrderService` — лише instance-поля, НІКОЛИ `static`; живі сесії (рейди,
  охоронці замаху) — окремий транзієнтний реєстр, що НЕ персистується (гине разом зі
  спавненими мобами на рестарті, як `DuelSession`).
- `RaidSession.tick()` перечитує гравця через `Bukkit.getPlayer(UUID)` щоразу — жодного
  захопленого `IAbilityContext`/`Player` в конструкторі.
- `domain.organizations` — жодного `org.bukkit..`/`io.lumine..` (ArchUnit `PURE_DOMAIN`,
  спільний з церквами тест).

## Заборони

- ❌ Мутувати `OrderStash` повз `SecretOrderService` — обхід персисту й інваріанта
  «виходи лише фавори».
- ❌ Видавати нагороди (рецепт/інгредієнти/Характеристику/фавор) повз claim-методи,
  `depositRaidLoot` чи `giveRaidLoot` — це єдині канали видачі.
- ❌ Додавати `org.bukkit..`/`io.lumine..` у `domain.organizations`.
- ❌ Кликати `endAllRaids()` не в `onDisable` ДО `saveAll()` — активні `BukkitTask` рейдів
  переживуть вимкнення плагіна й почнуть кидати помилки на наступному тіку.
- ❌ Робити реєстр рейдових сесій/охоронців замаху `static` — ці об'єкти прив'язані до
  конкретного інстансу `SecretOrderService`, як і в церков.
