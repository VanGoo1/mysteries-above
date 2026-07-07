# Секвенс-кіти здібностей міфічних істот (дизайн)

**Дата:** 2026-07-07
**Статус:** затверджено до реалізації
**Передумови:** міграція істот у MythicMobs-пак (`.claude/rules/mythic-creatures.md`);
попередні спеки [2026-06-27-creature-pathway-behaviors-design.md](2026-06-27-creature-pathway-behaviors-design.md)
та [2026-06-28-creature-ecology-combat-scaling-design.md](2026-06-28-creature-ecology-combat-scaling-design.md)
(архетипний шар, який цей дизайн замінює).

## Мета

Істота шляху на послідовності N використовує **впізнавані бойові здібності свого шляху,
накопичені з послідовностей 9..N** — та сама модель накопичення, що в гравців. Істота Fool 9
б'ється здібностями Seq 9; істота Fool 8 — здібностями Seq 9 і 8, і так далі. Поведінка
логічна: мілі-кіти йдуть у контакт, дальні кіти тримають дистанцію (кайтять); касти —
«в міру», без спаму.

## Рішення (зафіксовано з замовником)

1. **Бойовий піднабір**: у кіт входять лише бойові здібності, адаптовані під моба.
   Утилітарні/GUI (Divination, GoodMemory, Record, меню) пропускаються або стають пасивним
   еквівалентом (DangerIntuition → ухилення).
2. **Метаскіли в паку**: мобова версія здібності — окремий YAML-метаскіл, названий за
   здібністю. Баланс істот повністю в YAML — незалежний від Java-коду гравцевих здібностей
   (можна різати числа істот, не чіпаючи гравців). Складні ефекти — кастомні механіки в
   `infrastructure.mythic.components`.
3. **Стійка пер-моб**: MELEE або RANGED за домінантою кіта; RANGED кайтить (відскакує, коли
   гравець ближче порога).
4. **ГКД + пріоритет**: глобальний кулдаун між будь-якими двома кастами (~6 с звичайні,
   ~4 с апекс) + власний кулдаун кожної здібності; коли готові кілька — перевага здібності
   **нижчої** послідовності (фірмова сильна — рідше, але помітно). Між кастами — звичайна
   атака/рух.
5. **Архетипні скіли замінюються кітами**: найкращі ефекти `MA_Fool_Pull`,
   `MA_Visionary_Burst`, `MA_Door_Seal`, `MA_Justiciar_Aura`, `MA_Whitetower_Reveal`,
   `MA_Error_Chaos`, `MA_Fool_Summon` поглинаються конкретними здібностями кітів. Діри в
   гравцевому ростері (Error 7–5, WhiteTower 8) — кіт не росте, але стає сильнішим
   (коротші кулдауни / більші числа у нижчих шаблонах).

**Поза межами:** нові істоти; зміни луту/спавну/конвергенції; 3D-моделі (сумісність
закладено: вся поведінка — метаскіли, `Type` косметичний і в майбутньому замінюється на
ModelEngine/BetterModel без зміни коду); реальне блокування гравцевих здібностей
(Justiciar лишається сильними дебафами).

## Архітектура (rules vs effects)

«Що кастувати зараз» (ГКД, пріоритет, кулдауни) — **правило** → чистий домен.
Самі здібності (частинки, шкода, потіони, телепорти) — **ефекти** → YAML пака + кастомні
механіки.

### `domain.creatures.AbilityCastPlanner` (новий, чистий)

- Вхід при створенні: упорядкований список записів кіта `(skillName, cooldownTicks)` —
  порядок списку = пріоритет (перший = найвищий, тобто найнижча послідовність) — та
  `gcdTicks`.
- `Optional<String> pickNext(long nowTicks)`: повертає ім'я першого скіла, що не на власному
  кулдауні, якщо ГКД минув; фіксує час касту (ГКД + кулдаун обраного). Інакше —
  `Optional.empty()`.
- Жодного Bukkit/Mythic. Юніт-тести: ГКД блокує другий каст, пріоритет першого готового,
  власні кулдауни незалежні, «нічого не готово».

### `KitCastMechanic` (новий, `infrastructure.mythic.components`)

- Кастомна механіка-диспетчер `kitcast{gcd=<тіки>;skills=<Name:cdТіки>,<Name:cdТіки>,...}`.
  Викликається з шаблону моба на таймері (~20 тіків) з таргетером цілі
  (`@Target`/`@NearestPlayer`).
- Тримає `Map<UUID, AbilityCastPlanner>` per-entity (інстанс-поле; ліниве створення;
  чистка запису, коли ентіті мертве/відсутнє). Питає планувальник; якщо той повернув скіл —
  виконує однойменний метаскіл через Mythic API на поточну ціль.
- Контракти як у всіх компонентів: публічний конструктор із `MythicMechanicLoadEvent`,
  `getThreadSafetyLevel() → SYNC_ONLY` (виконує скіли, що чіпають Bukkit) — пінить
  `MythicComponentContractTest`. Реєстрація — у `MythicBridge.registerComponents`.

### Кастомні механіки для окремих ефектів

Додаються за потреби (кожна — той самий контракт компонентів):

- `blinkbehind` — телепорт моба за спину цілі з перевіркою прохідності (Door S5 Blink).
- `retreat` — відскік від цілі (кайт RANGED-стійки), якщо вбудованої `velocity`-хореографії
  забракне.
- `displace` — мікро-телепорт гравця на 3–5 блоків із перевіркою прохідності
  (Error ShadowTheft; переїжджає логіка нинішнього `MA_Error_Chaos`).

Наявні `drainsanity`, `scatter`, `isbeyonder` перевикористовуються як є.

## Пак: шаблони та кіти

### `Mobs/templates.yml` — шаблон на (шлях × послідовність)

Замість `MA_<Pathway>_Common/Apex` — `MA_<Pathway>_S9` … `MA_<Pathway>_S5`
(усі `Template: MA_Base`; `MA_Base` без змін — placeholder Type, агро, Despawn).
Кожен шаблон містить:

- **один** рядок `kitcast` із **повним кітом 9..N явно** (без успадкування Skills через
  Template-ланцюжок: повний кіт видно й балансується в одному рядку);
- пасивні рядки (стелс-невидимість Visionary S6+, частинки шляху);
- для RANGED-стійки — ретріт-рядок (див. Стійки).

ГКД: S9–S6 → `gcd=120` (6 с), S5 → `gcd=80` (4 с). Апекс-відмінність розчиняється в
S5-шаблоні; `tier` у `creatures.yml` лишається як є (лут/спавн — поза межами).

Моби в `Mobs/<pathway>.yml` міняють лише `Template:` (напр., `fool_joker_8` →
`MA_Fool_S8`). Join-ключ `creatures.yml` ↔ пак не змінюється.

### `Skills/<pathway>.yml` — метаскіли здібностей

Іменування: `MA_<Pathway>_S<seq>_<AbilityName>` (напр., `MA_Fool_S8_PaperCutter`).
Усередині — тільки хореографія: `projectile`/`shoot`, `potion`, `pull`, `ignite`,
`teleport`-механіки, частинки, звуки, `drainsanity`. Ментальні/контрольні ефекти —
`TargetConditions: isbeyonder true`; чиста фізична шкода може бити будь-кого (як у
нинішній конвенції). Власні кулдауни здібностей задаються в `kitcast`-рядку (не через
`Cooldown:` метаскіла — одне джерело правди).

### Два типи здібностей у кіті

- **Наступальні (через kitcast, під ГКД):** усе, що істота кастує з власної ініціативи
  на таймері — снаряди, вибухи, аури-імпульси, контроль. Саме їх стримує ГКД+пріоритет.
- **Реактивні/пасивні (поза kitcast, поза ГКД):** тригерні відповіді — EscapeTrick
  (`~onDamaged` при сильному уроні), ухилення DangerIntuition/DangerSense (`~onDamaged`),
  FlameJump (умова «ціль впритул»), саммон ляльок Fool (`~onDamaged
  ?healthpercent{h=<35}`), пасиви-аури (стелс-невидимість). Вони лишаються окремими
  рядками шаблона з власним `Cooldown:` у YAML — реакція не мусить чекати ГКД, і ГКД
  вона не з'їдає.

## Стійки (мілі vs кайт)

Стійка призначається пер-моб (фактично — пер-шаблон) за домінантою кіта:

- **MELEE** — нічого не додається: `MA_Base` вже агресивний, моб іде в контакт
  (здібності вимагають зближення — Visionary-вибух, Justiciar-удари).
- **RANGED** — шаблон додає ретріт-скіл на таймері: якщо ціль ближче ~5 блоків —
  відскік від неї (`velocity` від цілі + частинки; фолбек — кастомна `retreat`).
  Ванільний пасфайндинг лишається — моб «відклеюється» і тримає середню дистанцію,
  з якої кастує.

Розподіл: **RANGED** — Fool (леза/болти/нитки), WhiteTower (світлові снаряди), Door
(блінк-скірмішер); **MELEE** — Justiciar (аура/удари), Visionary (засідник), Error
(трикстер). Уточнюється при in-server тюнінгу.

## Кіти по шляхах (мапінг гравцевих здібностей)

Нижче — бойовий піднабір; конкретні числа (шкода/тривалості/кулдауни) — на етапі
реалізації в YAML, стартуючи від сили нинішніх архетипних скілів.

### Fool (RANGED)
- **S9** DangerIntuition → пасив: короткий ривок-ухилення/Speed при отриманні урону.
- **S8** PaperCutter → снаряд-лезо (шкода); ClownAgility → пасив швидкості.
- **S7** AirBullet → повітряний болт із відкиданням; FlameJump → вогняний відскік, коли
  ціль впритул (синергія з кайтом).
- **S6** — кіт не росте (Shapeshifting/GracefulDescent не бойові для моба).
- **S5** Маріонетковий контроль → «нитки»: pull + slowness через kitcast (поглинає
  `MA_Fool_Pull`) + реактивне прикликання ляльок `MA_FoolPuppet` при HP<35%
  (`~onDamaged`, поза ГКД; поглинає `MA_Fool_Summon`, кулдаун 30 с зберігається).

### Visionary (MELEE, засідник)
- **S9** ScanGaze → мітка: Glowing на ціль.
- **S8** DangerSense → пасив-ухилення (короткий Speed/Resistance при уроні).
- **S7** SurgeOfInsanity → ментальний вибух впритул: Nausea/Blindness/Darkness +
  `drainsanity` (поглинає `MA_Visionary_Burst`).
- **S6** PsychologicalInvisibility → стелс-невидимість, коли ціль далеко (поглинає
  нинішній invisibility-рядок); BattleHypnotism → короткий «ступор» (сильний Slowness +
  Weakness).
- **S5** — сильніші версії (Alteration/Guidance/DreamTraversal не бойові для моба).

### Door (RANGED, блінк-скірмішер)
- **S9** DoorOpening → «печать дверей» (поглинає `MA_Door_Seal`).
- **S8** Flash → сліпучий спалах (Blindness); Burning → підпал; ElectricShock → розряд
  (шкода + короткий Slowness); EscapeTrick → втеча-телепорт при сильному уроні.
- **S7/S6** — кіт не росте (дивінації/Record не бойові).
- **S5** Blink → телепорт за спину цілі (`blinkbehind`) + удар.

### Justiciar (MELEE)
- **S9** Authority → наказ: Slowness/Weakness на ціль; ArbitersGaze → мітка (Glowing).
- **S8** AreaOfJurisdiction → аура-зона дебафів довкола моба (поглинає
  `MA_Justiciar_Aura`).
- **S7** WhipOfPain → посилений удар болю; BrandOfRestraint → корінь (сильний Slowness на
  місці); (PsychicPiercing/PsychicLashing — у S7-версію дрену розсудку).
- **S6** Verdict → «вирок»: бурст-шкода з частинками.
- **S5** Punishment → кара: важкий смайт-удар.

### WhiteTower (RANGED)
- **S9** EnhancedMentalAttributes → пасив (виражено у статах).
- **S8/S7** — кіт не росте (стат-пасиви виражені в Health/Damage/Speed моба).
- **S6** Analysis → викриття: Glowing + спалах Blindness (поглинає
  `MA_Whitetower_Reveal`).
- **S5** Spellcasting → світлові снаряди з `isbeyonder`-гейтом і пристойною шкодою —
  замінює негейчений snowball і **закриває задокументований gap** у
  `mythic-creatures.md`; MirrorCurse → пасив: віддзеркалення частки отриманого урону.

### Error (MELEE, трикстер)
- **S9** ShadowTheft → «крадіжка позиції»: `displace` гравця на 3–5 блоків (поглинає
  телепорт-частину `MA_Error_Chaos`).
- **S8** SwindlerCharm → обман: Nausea + миттєвий скид агро (поглинає дебаф-частину
  `MA_Error_Chaos`); Agility → пасив швидкості.
- **S7–S5** — гравцевих здібностей ще немає: кіт той самий, але сильніший/частіший у
  нижчих шаблонах; коли шлях Error доросте у гравців — кіти доповнюються.

Пасиви-підсилення (PhysicalEnhancement, CombatProficiency, EnhancedMentalAttributes) не
кастуються — вони вже виражені у статах моба, що ростуть із послідовністю.

## Дрен розсудку

Без змін механізму: `drainsanity` усередині відповідних метаскілів ментальних шляхів
(Visionary, Fool — малий, Error — дуже малий), гейт `isbeyonder`.

## Тестування

- **Юніт:** `AbilityCastPlannerTest` — ГКД блокує повторний каст; пріоритет першого
  готового у порядку списку; незалежні власні кулдауни; «нічого не готово» → empty.
- **Наявні гарди:** `MythicPackMobTypeTest` (нові шаблони резолвлять Type через
  `MA_Base`); `MythicComponentContractTest` (нові механіки: конструктор із load-event +
  `SYNC_ONLY`).
- **Новий гард:** `MythicPackKitReferenceTest` — кожне ім'я в `kitcast{skills=...}` у
  Mobs/*.yml існує як метаскіл у Skills/*.yml (ловить одруки на збірці).
- **In-server (ручне):** `/mm mobs spawn` ланцюжок Fool 9→5 (кіт росте, S5 тягне нитками
  і кличе ляльок); кайт WhiteTower/Fool проти контакту Justiciar/Visionary; темп кастів
  (ГКД відчутний, між кастами звичайні атаки); `isbeyonder`-гейти; дрен розсудку;
  onDisable/reload без «завислих» ефектів.

## Документація (той самий коміт, що й реалізація)

- `.claude/rules/mythic-creatures.md`: описати kitcast/планувальник, іменування
  `MA_<Pathway>_S<seq>_<Ability>`, стійки, «як додати здібність у кіт»; прибрати
  Common/Apex і закриті «відомі спрощення» (snowball-гейт, Fool-саммон).
- CLAUDE.md змін не потребує (механізм лишається в межах mythic-скоупу правила).

## Файли (орієнтовно)

**Нові:**
- `domain/creatures/AbilityCastPlanner.java` (+ `AbilityCastPlannerTest`)
- `infrastructure/mythic/components/KitCastMechanic.java`
- `infrastructure/mythic/components/BlinkBehindMechanic.java`
- `infrastructure/mythic/components/DisplaceMechanic.java`
- (за потреби) `infrastructure/mythic/components/RetreatMechanic.java`
- `src/test/java/.../MythicPackKitReferenceTest.java`

**Змінені:**
- `mythic-pack/Mobs/templates.yml` (шаблони S9..S5 замість Common/Apex)
- `mythic-pack/Mobs/<pathway>.yml` × 6 (заміна `Template:`)
- `mythic-pack/Skills/<pathway>.yml` × 6 (метаскіли кітів; старі архетипні — видалити
  після поглинання)
- `MythicBridge.registerComponents` (нові механіки)
- `.claude/rules/mythic-creatures.md`

`MythicPackInstaller.PACK_FILES` — без змін, якщо не додаються нові файли пака (кіти
живуть у наявних Skills/Mobs файлах).
