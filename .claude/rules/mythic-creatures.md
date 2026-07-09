# Істоти: MythicMobs

Контент мобів (стати, вигляд, скіли, шаблони) живе у **MythicMobs-паку** `src/main/resources/mythic-pack/`
(розгортається у `plugins/MythicMobs/Packs/MysteriesAbove` через `MythicPackInstaller`, який **перезаписує**
серверні файли — джерело правди тільки репозиторій, не редагуй пак на сервері).
Правила спавну/луту лишаються в коді: `domain.creatures` + `creatures.yml`.

## Join-ключ

`creatures.yml` id == internal name моба в паку (напр. `error_sphinx_9`). Перейменування — завжди в обох місцях.
`base_entity` у `creatures.yml` дублює `Type` пака свідомо (потрібен чистому домену для aquatic ambient-спавну).

## Додати істоту

1. Моб у `mythic-pack/Mobs/<pathway>.yml`: `Template: MA_<Pathway>_S<seq>` (шаблон = кіт
   здібностей послідовностей 9..seq), Type/Display/Health/Damage/Options.
   Кожен запис Mobs/*.yml (шаблони теж) мусить резолвити `Type` напряму або через ланцюжок
   `Template:`, інакше "No Type specified" на старті (гард — `MythicPackMobTypeTest`).
2. Запис у `creatures.yml`: `base_entity`, `tier`, `loot` (тільки інгредієнти), `spawn` (natural/structure).
3. Нова поведінка → метаскіл у `mythic-pack/Skills/<pathway>.yml`; таргет-гейт — умова `isbeyonder`.
4. Файл додано? Впиши його в `MythicPackInstaller.PACK_FILES`.
5. Перевірка in-server: `/mm mobs spawn <id>`, скіли/лут/агро.

## Кіти здібностей (kitcast)

Кожен шаблон `MA_<Pathway>_S<seq>` має ОДИН рядок-диспетчер:
`kitcast{gcd=<тіки>;skills=<Скіл:кулдаунТіки>,...} @NearestPlayer{r=12} ~onTimer:20` —
повний кіт 9..seq явно. Вибір серед готових — ЗВАЖЕНО-ВИПАДКОВИЙ: вага = позиція у списку
(перша — найважча; фірмову найнижчої послідовності став першою). Кулдаун топ-здібності
тримай ≥ 2×gcd, інакше вона домінуватиме в ротації. ГКД+ваги
рахує чистий `domain.creatures.AbilityCastPlanner` (юніт-тести), диспетчить
`KitCastMechanic`. Метаскіли кітів іменуються `MA_<Pathway>_S<seq>_<Ability>` і мобово
віддзеркалюють БОЙОВІ гравцеві здібності шляху; баланс істот — тільки в YAML пака.

- **Наступальна здібність** → без `Cooldown:` у метаскілі (кулдаун у kitcast-рядку —
  одне джерело правди); додається в `skills=` кожного шаблону, де вона доступна.
- **Реактивна/пасивна** (відповідь на урон/зближення, аури, саммон) → окремий тригерний
  рядок шаблону (`~onDamaged`, `?playerwithin`) З власним `Cooldown:` (секунди) — поза ГКД.
- **Стійка:** RANGED-шаблон вимикає рухові AI-цілі (`AIGoalSelectors: 0 clear` +
  float/lookatplayers/randomlookaround) і додає `rangedstance{min=5;max=11}
  @NearestPlayer{r=24} ~onTimer:10` (стоїть-кастує в смузі, відходить кроком,
  мілі-відповідь у вікні провокації) + `provoke{seconds=8} @self ~onDamaged
  ?playerwithin{d=3}` (удар впритул відкриває вікно; спільний стан — PDC
  `mysteriesabove:provoked_until`). MELEE — нічого. Кастомні механіки руху:
  `rangedstance`, `provoke`, `retreat` (ривок усередині метаскілів), `blinkbehind`
  (в `infrastructure.mythic.components`).
- Посилання на метаскіли пінить `MythicPackKitReferenceTest` (kitcast/skill{s=}/onHitSkill/
  randomskill → мусить існувати в Skills/*.yml).
- Діра в гравцевому ростері (напр. Error 7–5) — кіт не росте: той самий kitcast із коротшими
  кулдаунами/меншим gcd у нижчому шаблоні.

## Межі (ArchitectureTest)

- `io.lumine..` — ТІЛЬКИ в `me.vangoo.infrastructure.mythic..` (`mythicMobsApiIsConfinedToBridgePackage`).
  Спавн/ідентифікація для решти коду — через `MythicCreatureGateway` (`spawn(id, loc)`, `isCreature`, `creatureId`).
- Кастомні механіки/умови (`drainsanity`, `scatter`, `isbeyonder`) — в `infrastructure.mythic.components`;
  сервіси беруть зі статичного `MythicBridge` (єдиний дозволений static-виняток: конструктор диктує MythicMobs).
  Реєстрація компонентів — `MythicBridge.registerComponents(plugin)`, викликати в `onEnable` ПІСЛЯ `MythicBridge.init(beyonderService)`.
  Конструктор компонента — ОБОВʼЯЗКОВО публічний із параметром load-event
  (`MythicMechanicLoadEvent` / `MythicConditionLoadEvent`) — реєстр шукає саме його рефлексією.
  Скіл-клок MythicMobs АСИНХРОННИЙ (дефолт `ThreadSafetyLevel.EITHER`): механіка, що чіпає Bukkit
  або мутує домен, мусить перевизначити `getThreadSafetyLevel()` → `SYNC_ONLY`, інакше AsyncCatcher.
  Обидва контракти пінить `MythicComponentContractTest`.
  Після мутації Beyonder у механіці — обовʼязково `beyonderService.updateBeyonder(...)`.
- Балансові числа спавну/луту (шанси, конвергенція, дистанції) — у `domain.creatures` з юніт-тестами,
  НЕ в YAML пака. У паку — тільки хореографія ефектів (аркадні прості атаки, ванільна стилістика).

## Відомі спрощення

- Ambient-спавн більше НЕ прив'язаний жорстко до шляху гравця: обираються всі істоти по біому,
  свій шлях+«наступна потрібна» послідовність лише вагово пріоритетні (`CreatureSelector.pickForAmbient`).
- Таргет «найближчий потойбічний» апроксимовано `@NearestPlayer + isbeyonder` (не-потойбічний ближче — тік холостий).
- Саммон ляльок Fool — реактивний скіл `MA_Fool_S5_Puppets` (кулдаун 30с при HP<35%),
  тільки в S5-шаблоні.
- Гейтована кіт-здібність, кинута в не-потойбічного (@NearestPlayer), спалює свій кулдаун і ГКД холостим — моб виглядає пасивнішим проти не-потойбічних.
- Мілі-відповідь спровокованого RANGED-моба б'є НАЙБЛИЖЧОГО гравця (не кривдника), і гейт провокації — близькість будь-якого гравця (?playerwithin{d=3}), не авторство удару.

## 3D-моделі (майбутнє)

Обирається per-mob у паку: ванільний `Type` (за замовчуванням) або ModelEngine/BetterModel-модель
через їхню інтеграцію з MythicMobs — код плагіна не змінюється.
