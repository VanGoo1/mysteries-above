# Станова RANGED-поведінка істот (дизайн)

**Дата:** 2026-07-07
**Статус:** затверджено до реалізації
**Передумова:** секвенс-кіти здібностей
([2026-07-07-creature-sequence-ability-kits-design.md](2026-07-07-creature-sequence-ability-kits-design.md)),
гілка `feat/creature-sequence-kits`.

## Проблема

Поточний кайт RANGED-мобів — velocity-відскок (`retreat`) поверх ванільного melee-AI:
пасфайндер жене моба в контакт, retreat його відкидає — виходить сіпання «біжить →
стрибає назад → біжить». Замовник хоче станову поведінку: моб **кастує з дистанції
стоячи**, при зближенні гравця **відходить кроком**, а коли гравець **б'є його впритул —
контратакує в мілі**.

## Рішення (підхід A, затверджено)

Одна Java-механіка-автомат зі справжньою навігацією Paper API. Сервер — Paper
(підтверджено замовником), тому `pom.xml` переводиться зі `spigot-api` на `paper-api`
(надмножина: наявний код компілюється без змін), що відкриває `Mob#getPathfinder()`
(ходьба, не поштовхи) та `LivingEntity#attack(Entity)` (ванільний мілі-удар з
атрибутом Damage моба).

## Механіка `rangedstance` (автомат станів)

`rangedstance{min=5;max=11}` — `ITargetedEntitySkill`, `SYNC_ONLY`,
рядок шаблону: `@NearestPlayer{r=16} ~onTimer:10` (тік 0.5 с). Per-entity стан —
інстанс-мапа в механіці (як у `KitCastMechanic`, з лінивою чисткою мертвих).
Тривалість провокації механіка НЕ конфігурує — вона лише читає дедлайн
`provokedUntil` із PDC (єдине джерело правди — параметр `provoke{seconds=...}`).

| Стан | Умова | Дія (щотіка механіки) |
|---|---|---|
| APPROACH | ціль далі `max` | `pathfinder.moveTo(ціль, ~1.05)` — йде в робочу смугу |
| HOLD | дистанція у смузі `min..max` | `pathfinder.stopPathfinding()` + дивитись на ціль — стоїть і кастує (kitcast працює незалежно) |
| BACKOFF | ціль ближче `min` І не спровокований | навігація до точки ~7 блоків у протилежний від цілі бік, валідованої `SafeLocations.passableNear` + перевірка опори/лави (як у `blinkbehind`); точка не знайдена → лишається HOLD |
| PROVOKED | `now < provokedUntil` (PDC) | `moveTo(ціль, ~1.15)`; коли ціль у ≤2.2 блоках і минув ~1 с від попереднього удару — `caster.attack(ціль)` |

Вихід із PROVOKED — вікно спливло без нових ударів → назад до дистанційних станів.
Без цілі таргетер нікого не дає, механіка не викликається: останній moveTo сам
завершується, і моб стоїть (нерухові AI-цілі lookatplayers/randomlookaround дають ідл).

## Механіка `provoke` (тригер провокації)

`provoke{seconds=8}` — пише `provokedUntil = now + seconds` у **PersistentDataContainer**
ентіті (спільний ключ `mysteriesabove:provoked_until`, тип LONG). PDC обрано свідомо:
дві різні механіки (інстанси різних класів) ділять стан без static-полів.
Рядок шаблону: `@self ~onDamaged ?playerwithin{d=3}` — «б'є» = урон, коли гравець
у ≤3 блоках; постріли з лука здалеку провокацію НЕ вмикають (моб відповідає кастами).
Повторні удари поновлюють вікно.

## Зміни шаблонів (`Mobs/templates.yml`, лише RANGED)

RANGED-шаблони (Fool S8–S5, Door S9–S5, WhiteTower S5):

- `AIGoalSelectors: - 0 clear - 1 float - 2 lookatplayers - 3 randomlookaround` —
  ванільні рухові цілі вимкнено (рух повністю веде механіка), нерухові лишаються для
  живості. `AITargetSelectors` з `MA_Base` НЕ чіпаються (таргет потрібен і механіці,
  і PROVOKED-мілі).
- Рядок `retreat{...} @NearestPlayer{r=4} ~onTimer:30 ?playerwithin{d=4}` замінюється на:
  ```yaml
  - rangedstance{min=5;max=11} @NearestPlayer{r=16} ~onTimer:10
  - provoke{seconds=8} @self ~onDamaged ?playerwithin{d=3}
  ```
- MELEE-шаблони — без змін. Механіка `retreat` НЕ видаляється: вона лишається як
  ривок-ухилення всередині метаскілів (`MA_Fool_S7_FlameJump`,
  `MA_Fool_S9_DangerIntuition`) — там стрибок доречний.

## Зміна збірки

`pom.xml`: залежність `org.spigotmc:spigot-api` → `io.papermc.paper:paper-api`
(та сама лінія 1.21, `scope=provided`) + репозиторій `https://repo.papermc.io/repository/maven-public/`.
Ризик регресії: нульовий за API (надмножина); верифікація — повний `mvn clean test`
(усі 76 тестів) + `clean package`. Оновити згадки збірки: CLAUDE.md (розділ Commands),
`.claude/rules/persistence-and-resources.md` (розділ «Залежності збірки»).

## Крайові випадки

- **Плавці** (Guardian, Drowned): Paper-навігація використовує рідний рух моба у воді;
  на суші деградує до ванільної незграбності — як і до цієї зміни.
- **Shulker** (WhiteTower S5): нерухомий — moveTo/stop no-op, він і так «стоїть і
  кастує». Прийнятно.
- **Летуни** (Phantom): у RANGED-ростері немає (Fool S9 — MELEE); якщо з'явиться —
  тюнити окремо.
- Точка відходу впирається в стіну/обрив/лаву → моб тримає позицію (HOLD), не стрибає.
- `/mm reload` перевантажує механіки — per-entity стан скидається; PDC-мітка
  провокації переживає (нешкідливо: максимум 8 с «залишкової» люті).

## Тестування

- Юніт-тести неможливі (навігація/PDC = живий сервер). Контракти нових механік
  (публічний load-event конструктор + `SYNC_ONLY`) автоматично пінить
  `MythicComponentContractTest`; ArchUnit-межі (`io.lumine` тільки в
  `infrastructure.mythic`) — без змін.
- Після pom-свопа: повний `mvn -o clean test` (76 тестів зелені) + `clean package`.
- **In-server чеклист:** Door S8 (Drowned) — стоїть і кастує у смузі 5–11; відходить
  кроком при зближенні; після 1–2 ударів впритул переходить у мілі на ~8 с і
  повертається до стійки; Fool S8 (Vindicator) — те саме; WhiteTower S5 (Shulker) —
  кастує стоячи; лук здалеку НЕ провокує мілі; MELEE-моби (Justiciar) поводяться як
  раніше; жодних AsyncCatcher у консолі.

## Документація (той самий коміт)

`.claude/rules/mythic-creatures.md`, секція «Кіти здібностей»: пункт «Стійка»
переписати — RANGED = `rangedstance`+`provoke` рядки + очищені рухові AI-цілі;
`retreat` згадати як ривок усередині метаскілів. Прибрати згадку retreat-кайту.

## Файли (орієнтовно)

**Нові:**
- `infrastructure/mythic/components/RangedStanceMechanic.java`
- `infrastructure/mythic/components/ProvokeMechanic.java`

**Змінені:**
- `pom.xml` (paper-api + репозиторій)
- `mythic-pack/Mobs/templates.yml` (RANGED-шаблони: AIGoalSelectors + заміна retreat-рядка)
- `.claude/rules/mythic-creatures.md`, `CLAUDE.md`, `.claude/rules/persistence-and-resources.md`
- (не змінюється: `RetreatMechanic` — лишається для метаскілів-ривків)
