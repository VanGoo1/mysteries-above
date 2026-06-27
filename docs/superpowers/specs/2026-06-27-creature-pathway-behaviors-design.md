# Поведінка міфічних істот за шляхом (дизайн)

**Дата:** 2026-06-27
**Статус:** затверджено до реалізації
**Передумова:** Спек 3 — полювання ([2026-06-27-ingredient-economy-hunting-design.md](2026-06-27-ingredient-economy-hunting-design.md)) + повний ростер `creatures.yml` (28 істот, 6 шляхів).

## Огляд і межі

Додаємо міфічним істотам **бойову поведінку/стратегію, що залежить від шляху** істоти, аби
полювання стало небезпечним і характерним. Не всі істоти однакові: одні випромінюють **ауру**,
інші **ховаються й нападають із засідки**, треті **телепортуються** чи **прикликають слуг**. Кожен
із 6 шляхів плагіна (Error, Visionary, Door, Justiciar, WhiteTower, Fool) має свій **архетип
поведінки**, натхненний лором (`docs/seq.txt`, `docs/pathways.txt`).

**У межах:** само-тікаюча поведінка на кожну живу істоту; ефекти на **потойбічних** у радіусі;
масштаб за тіром (apex сильніше/частіше); дуже малий вплив на розсудок у ментальних шляхів.

**Поза межами:** зміна ванільного пасфайндингу/таргетингу (рух/агро лишається ванільним —
поведінка *накладається* зверху); кастомні 3D-моделі (окремий шов `CreatureAppearance`); реальне
блокування API здібностей (Justiciar зводимо до сильних дебафів — див. нижче); нові інгредієнти.

## Архітектура (rules vs effects)

Майже все тут — **ефекти** (потіон-ефекти, телепорти, невидимість, частинки), тож живе в
`infrastructure.creatures.behavior` (Bukkit дозволено), перевіряється in-server. Єдина
«правилоподібна» дрібниця — **вибір архетипу за шляхом** (чистий маппінг) — інкапсульована у
факторі.

Замість **сесії-з-власним-таском на кожну істоту** (патерн `JurisdictionSession`) використовуємо
**один центральний тік-менеджер** + **per-entity об'єкт поведінки зі станом**. Причина: істот може
бути багато одночасно — один повторюваний таск, що ітерує живі істоти, дешевший за N окремих тасків.
Per-entity стан (кулдаун невидимості, «прикликав слуг разово») тримається в самому об'єкті поведінки.

### `CreatureBehavior` (інтерфейс, `infrastructure.creatures.behavior`)

- `void tick(LivingEntity self, java.util.List<org.bukkit.entity.Player> nearbyBeyonders)`;
- реалізації **per-entity, зі станом** (створюються на спавн істоти); тікаються центральним
  менеджером ~раз на секунду;
- параметри істоти (tier тощо) приходять у конструктор реалізації через фабрику.

Реалізації (одна на архетип):
- `AmbushBehavior` (Visionary), `ControlBehavior` (Fool), `BlinkBehavior` (Door),
  `VerdictBehavior` (Justiciar), `RevealBehavior` (WhiteTower), `ChaosBehavior` (Error).

### `CreatureBehaviorFactory` (`infrastructure.creatures.behavior`)

- `CreatureBehavior create(CreatureDefinition def)` — обирає архетип за `def.pathway()` (нормалізовано
  у нижній регістр); невідомий шлях → `null` (істота просто без спец-поведінки, нешкідливо);
- тримає посилання на `BeyonderService` (для дрену розсудку) і `Plugin` (логер/частинки), щоб
  передавати в ментальні поведінки.

### `CreatureBehaviorManager` (`infrastructure.creatures.behavior`)

- реєстр `Map<UUID, CreatureBehavior>` (інстанс-поле, не static);
- `void start(LivingEntity entity, CreatureDefinition def)` — створює поведінку через фабрику
  (якщо не `null`) і реєструє за `entity.getUniqueId()`;
- один **центральний** повторюваний `BukkitTask` (період 20 тіків), запускається в конструкторі з
  `Plugin`; кожен тік:
  - ітерує копію entrySet; для кожного `(uuid, behavior)`:
    - `Entity e = Bukkit.getEntity(uuid)`; якщо `e == null` або `!(e instanceof LivingEntity)` або
      `e.isDead()` → видалити з реєстру (само-очищення; не треба чіпати death-лістенер);
    - інакше зібрати **поряд потойбічних**: `e.getNearbyEntities(R,R,R)` → лише `Player`, для яких
      `beyonderService.getBeyonder(p.getUniqueId()) != null`; якщо список порожній — **пропустити
      тік цієї істоти** (перф: жодних ефектів, коли нема кого зачіпати);
    - інакше `behavior.tick((LivingEntity) e, nearby)`;
- `void stopAll()` — скасувати таск, очистити реєстр (виклик у `onDisable`);
- `R` (радіус активації/дії) — константа (напр. 12 блоків).

### Інтеграція спавну

`CreatureSpawner.spawn(def, loc)` після тегування викликає `behaviorManager.start(entity, def)`,
тож **усі** шляхи спавну (команда, природний, структурний) автоматично отримують поведінку. Смерть
не потребує окремого хука — менеджер само-очищає мертвих/відсутніх на тіку.

### Поле `pathway` у `CreatureDefinition`

Поведінка обирається за шляхом істоти. Додаємо `String pathway` у `CreatureDefinition`. У
`CreatureConfigLoader`:
- читати опційне поле `pathway:` з `creatures.yml`;
- якщо відсутнє — **вивести зі стандартного префікса id** (підрядок до першого `_`:
  `visionary_manhal_9` → `visionary`). Наявні 28 id уже префіксовані шляхом, тож **редагувати YAML
  не обов'язково** (працює з коробки); поле лишається для гнучкості.

## Поведінки за шляхом (тактика + ефект)

Усі ефекти — на **потойбічних** у радіусі `R`; тривалість/амплітуда × тіром (`apex` → довше/+1 рівень,
коротші кулдауни). Нижче — базові (common) значення; apex масштабує.

- **Visionary — `AmbushBehavior` (засідка-стелс):** коли істоту давно (≥ ~4 c) не били й гравець
  далі ~4 блоків — вмикає `INVISIBILITY` (підкрадання). Коли гравець близько (≤ ~3 блоки) АБО
  істоту щойно вдарили — «розкривається» (знімає невидимість) і накладає сплеск **NAUSEA(~4 c) +
  BLINDNESS(~1.5 c) + DARKNESS(~3 c)** на найближчого потойбічного; зрідка (20% такого тіку) —
  `increaseSanityLoss(1)`. Частинки `Particle.SQUID_INK`/`WITCH`.
- **Fool — `ControlBehavior` (контроль/ляльки):** періодично (кулдаун ~3 c) **притягує** найближчого
  потойбічного до себе (вектор до істоти × сила) + `SLOWNESS(~2 c)` («нитки»); коли HP істоти
  падає нижче 35% — **разово** прикликає 1 (apex: 2) слабких `ZOMBIE`, тегованих службовим маркером,
  без власної поведінки; зрідка `increaseSanityLoss(1)`.
- **Door — `BlinkBehavior` (блінк-скірмішер):** кулдаун ~4 c — **телепортується за спину**
  найближчого потойбічного (позиція = гравець − напрямок погляду × 2, із перевіркою прохідності
  через наявний `safeSpawnLocation`-підхід) і накладає `LEVITATION(~1.5 c)` АБО зміщує гравця на
  2–3 блоки. Частинки `Particle.PORTAL`.
- **Justiciar — `VerdictBehavior` (зона-вирок / аура):** **постійна аура** поряд: усім потойбічним у
  `R` → `SLOWNESS(II на apex) + MINING_FATIGUE + WEAKNESS` (~1.5 c, поновлюється щотіку) — «скутий
  судом». (Реального блокування здібностей не робимо — зводимо до сильних дебафів.) Частинки
  `Particle.WAX_OFF`.
- **WhiteTower — `RevealBehavior` (викриття + харас):** **аура**: усім потойбічним у `R` →
  `GLOWING(~2 c)` (підсвічує крізь стіни) + зрідка спалах `BLINDNESS(~1 c)`; кулдаун ~5 c — кидає
  слабкий «світловий» снаряд (`SmallFireball`/`Snowball` із невеликою шкодою) у найближчого. Частинки
  `Particle.END_ROD`.
- **Error — `ChaosBehavior` (трикстер/невдача):** кулдаун ~3 c — випадково обирає одне: короткий
  **телепорт гравця** на 3–5 блоків у випадковий бік (із перевіркою прохідності), `WEAKNESS`,
  `MINING_FATIGUE`, або `BAD_OMEN`; зрідка (10%) `increaseSanityLoss(1)` («дуже малий», як просив
  замовник). Частинки `Particle.SMOKE`.

> Перевикористання `safeSpawnLocation`-логіки (із `StructureCreatureSpawnListener`) для безпечних
> телепортів: винести у малий статичний util `SafeLocations.passableNear(Location)` і викликати з
> Blink/Chaos та зі структурного лістенера (DRY).

## Дрен розсудку

Лише **ментальні** шляхи: Visionary, Fool (малий), Error (дуже малий). Через
`BeyonderService.getBeyonder(uuid)` → `increaseSanityLoss(1)` із низькою ймовірністю за тік, тільки
коли ефект реально застосовано. Жодних інших змін у системі розсудку/рампейджу.

## Wiring (`ServiceContainer` + `MysteriesAbovePlugin`)

- У creature-блоці `ServiceContainer` (після `beyonderService`, перед/разом зі спавнером):
  ```java
  this.creatureBehaviorFactory = new CreatureBehaviorFactory(beyonderService, plugin);
  this.creatureBehaviorManager = new CreatureBehaviorManager(plugin, beyonderService, creatureBehaviorFactory);
  this.creatureSpawner = new CreatureSpawner(/* ...наявне... */, creatureBehaviorManager);
  ```
- `CreatureSpawner` отримує `CreatureBehaviorManager` і викликає `start(...)` наприкінці `spawn`.
- Геттер `getCreatureBehaviorManager()`; у `MysteriesAbovePlugin.onDisable()` →
  `services.getCreatureBehaviorManager().stopAll()`.

## Тестування

- **Юніт:** немає headless-тестів — фабрика й поведінки конструюють Bukkit-об'єкти й залежать від
  сервера (як решта ефект-класів creatures). `ArchitectureTest` лишається зеленим (усе в
  `infrastructure`, `domain` не залежить).
- **In-server (ручне):** `/creature spawn` кожного архетипу й перевірка:
  1. **Visionary** зникає, підкрадається, при контакті — Nausea/Blindness/Darkness; розсудок трохи
     падає.
  2. **Fool** притягує гравця + Slowness; на низькому HP прикликає ляльку(и).
  3. **Door** телепортується за спину + Levitation/зміщення.
  4. **Justiciar** поряд тримає Slowness/Mining Fatigue/Weakness (аура).
  5. **WhiteTower** дає Glowing + спалахи Blindness + кидає снаряд.
  6. **Error** хаотично телепортує/дебафить; дуже зрідка трохи розсудку.
  7. Перф: натовп істот без гравців поряд — тік пропускається (нема ефектів/лагу).
  8. `onDisable`/reload — таск скасовано, ефекти не «висять».

## Перевикористання (не переписуємо)

- Патерн **сесії** (`JurisdictionSession`) — взірець `tick()`/`cancel()`/Bukkit-напряму (адаптовано
  до центрального менеджера).
- `BeyonderService.getBeyonder` + `Beyonder.increaseSanityLoss` — дрен розсудку.
- `CreatureCodec` — впізнавання істот (службовий маркер ляльок Fool — окремий простий PDC-прапор).
- `CreatureSpawner` — єдина точка інтеграції старту поведінки.
- `safeSpawnLocation`-логіка зі `StructureCreatureSpawnListener` — спільний `SafeLocations` util.

## Файли (орієнтовно)

**Нові:**
- `infrastructure/creatures/behavior/CreatureBehavior.java` (інтерфейс)
- `infrastructure/creatures/behavior/CreatureBehaviorFactory.java`
- `infrastructure/creatures/behavior/CreatureBehaviorManager.java`
- `infrastructure/creatures/behavior/AmbushBehavior.java` (Visionary)
- `infrastructure/creatures/behavior/ControlBehavior.java` (Fool)
- `infrastructure/creatures/behavior/BlinkBehavior.java` (Door)
- `infrastructure/creatures/behavior/VerdictBehavior.java` (Justiciar)
- `infrastructure/creatures/behavior/RevealBehavior.java` (WhiteTower)
- `infrastructure/creatures/behavior/ChaosBehavior.java` (Error)
- `infrastructure/creatures/SafeLocations.java` (спільний util безпечної локації)

**Змінені:**
- `domain/creatures/CreatureDefinition.java` (+ `String pathway`)
- `infrastructure/creatures/CreatureConfigLoader.java` (читати/виводити `pathway`)
- `infrastructure/creatures/CreatureSpawner.java` (+ `CreatureBehaviorManager`, виклик `start`)
- `presentation/listeners/StructureCreatureSpawnListener.java` (перевести `safeSpawnLocation` на `SafeLocations`)
- `infrastructure/di/ServiceContainer.java` (wiring фабрики/менеджера + геттер)
- `MysteriesAbovePlugin.java` (`stopAll()` в `onDisable`)
- `src/test/java/.../creatures/CreatureSelectorTest.java` (хелпер `def(...)` конструює
  `CreatureDefinition` — оновити під новий параметр `pathway`)
- (опц.) `src/main/resources/creatures.yml` (явні `pathway:` — не обов'язково, є деривація)
