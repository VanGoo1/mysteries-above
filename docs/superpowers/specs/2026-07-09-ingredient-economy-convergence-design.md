# Економіка Характеристик — Спек 5: C (Закон Конвергенції) (дизайн)

**Дата:** 2026-07-09
**Статус:** затверджено до реалізації
**Парасольковий роадмап:** [2026-06-26-ingredient-economy-roadmap.md](2026-06-26-ingredient-economy-roadmap.md)
**Передумови:** [Спек 1 — Фундамент](2026-06-26-ingredient-economy-foundation-design.md) (тип Характеристика, `CharacteristicCodec`), [Спек 2 — вилучення](2026-06-27-ingredient-economy-extraction-design.md) (`WardenRemnantCodec`, впала есенція), [Спек 3 — полювання](2026-06-27-ingredient-economy-hunting-design.md) (міфічні істоти, `MythicCreatureGateway`, реєстр істот).

## Огляд і межі

Спек 5 реалізує **канал C** роадмапу — Закон Конвергенції. **Свідоме переосмислення проти роадмапу
(рішення користувача):** це **не компас** і **не інструмент у руках гравця**. Закон діє **приховано,
через долю** — невидиме тяжіння, що стягує докупи все резонансне (той самий шлях або сусідній
`PathwayGroup`). Гравець **не керує цим і не усвідомлює**; він лише *натрапляє* на есенцію й зустрічі,
і зрідка чує тихий незрозумілий відголос.

Це прямо реалізує головний принцип роадмапу — **сила циркулює, а не зникає**: впала Характеристика
не гниє в глушині, доля несе її до кревного носія; істоти твого шляху забредають до тебе.

**Ядро — «тяжіння резонансних об'єктів до резонансного Beyonder'а»** (обране з-поміж альтернатив
A/B/C на етапі брейнстормінгу; ядро A).

### Що рухається (і що ні)

- **Рухаються ЛИШЕ не-гравцеві резонансні об'єкти:** впалі Характеристики (Item із
  `CharacteristicCodec`), рештки (`WardenRemnantCodec`), міфічні істоти *твого/сусіднього шляху*.
- **Живих Beyonder'ів (інших гравців) НІКОЛИ не рухаємо** — насильне зміщення гравця було б грубим
  захопленням керування, помітним одразу. Зустрічі між гравцями стаються **опосередковано**: їхні
  притягнуті істоти/есенції накладаються.

### Помітність і радіус

- **Майже повністю невидимо.** Жодних частинок, жодного HUD/тексту, жодної навігаційної інформації.
  Єдиний дозволений прояв — **вкрай рідкісний, тихий, ненапрямний звук** гравцю-магніту (містична
  атмосфера, 0 напряму).
- **Радіус тяжіння — 128 блоків** (практично завантажена зона навколо гравця). Крос-світова доставка
  есенції — **поза межами** цього спека.

**Поза межами (наступні спеки):** перерозподіл — підпільний ринок і церкви (Спек 6); Сефіроти (Спек 7).
Жодних змін у самих джерелах Характеристик (Спеки 1–2) чи спавні істот (Спеки 3–4) — лише додаємо
поверх них приховане тяжіння.

## Резонанс (визначення)

Групування шляхів (із `PathwayManager.initializePathways`):

| PathwayGroup | Шляхи |
|---|---|
| LordOfMysteries | Error, Door, Fool |
| GodAlmighty | Visionary, WhiteTower |
| TheAnarchy | Justiciar (наразі без сусідів) |
| DemonOfKnowledge | (поки порожня) |

**Резонанс** між джерелом і Beyonder'ом:
- **той самий шлях** (порівняння назв без регістру) → сильний резонанс;
- **той самий `PathwayGroup`** (сусід) → слабший резонанс;
- інакше — **немає резонансу** (об'єкт не тяжіє до цього гравця).

## Архітектура (rules vs effects)

Дзеркалить еталон `CreatureSelector`/`ConvergenceBias` (правило) + `AmbientCreatureSpawner` (ефект):
**правило вибору магніта й сили — чистий domain з юніт-тестами**; **ефекти (нудж velocity/pathfinder,
звук) — infrastructure**, перевіряються in-server.

### Чисте ядро — `ConvergencePull` (`me.vangoo.domain.creatures`, без Bukkit)

Тримаємо пакет Bukkit-вільним (координати як `double x/z`, шлях і група як **рядки** — резолвляться в
інфрі). `domain.creatures` уже в списку «чистих» пакетів `ArchitectureTest` — інваріант тримається
автоматично.

- **`ResonantBeyonder`** (record) — кандидат-магніт:
  `(UUID id, String pathway, String group, int sequenceLevel, double x, double z)`.
- **`ConvergenceSource`** (record) — джерело, що тяжіє:
  `(String pathway, String group, int sequence, double x, double z)`.
- **`PullResult`** (record) — `(UUID targetId, double strength)`; `strength` ∈ (0,1].
- **`ConvergencePull`** — правило (аналог `CreatureSelector`), чисте й тестоване:
  - `Optional<PullResult> computePull(ConvergenceSource source, Collection<ResonantBeyonder> beyonders, double radius)`.
  - Крок 1 — **фільтр**: лишити кандидатів, що (а) в межах радіуса
    (`dx*dx + dz*dz <= radius*radius`) і (б) **резонують** (той самий шлях **або** та сама група).
  - Крок 2 — **вага вибору** для кожного кандидата:
    `weight = resonanceWeight × seqMultiplier / (1 + distance)`, де
    - `resonanceWeight`: свій шлях = `2.0` (`SAME_PATHWAY_WEIGHT`), сусід = `1.0` (`NEIGHBOR_WEIGHT`);
    - `seqMultiplier` (дзеркалить `CreatureSelector.multiplier`): `source.sequence == b.sequenceLevel - 1`
      («наступна потрібна») → `4.0`; `== b.sequenceLevel` («поточна») → `2.0`; інакше `1.0`.
  - Крок 3 — **вибір магніта**: кандидат із **найбільшою вагою**; тайбрейк — **найближчий**
    (менша distance); подальший тайбрейк — детермінований (менший `id`), щоб правило було чистим.
  - Крок 4 — **сила нуджа**: `strength = resonanceWeight × seqMultiplier / MAX_SCORE`, де
    `MAX_SCORE = SAME_PATHWAY_WEIGHT × 4.0 = 8.0` → strength ∈ (0,1].
    **Важливо:** відстань впливає лише на *вибір* магніта (крок 2), **не** на силу — так есенція, яка
    тобі *потрібна* (свій шлях + наступна seq), тяжіє однаково відчутно на всьому радіусі.
  - Порожня колекція кандидатів / нуль резонансу / усі поза радіусом → `Optional.empty()`.
  - Без Bukkit, без стану, детермінований.

> Правило повертає лише **кого** (targetId) і **наскільки сильно** (strength). **Напрям** обчислює
> ефект-шар із реальних локацій (`target − source`), бо це вже Bukkit-ефект.

### Ефект-шар — `ConvergenceDriftScheduler` (`infrastructure.schedulers`)

Дзеркалить `AmbientCreatureSpawner`: клас із `start()` / `stop()`, що володіє одним `BukkitTask` від
`runTaskTimer`.

- **Інтервал:** `convergence.interval-ticks` (дефолт 200 → щотіку кожні 10 с).
- **Кожен тік:**
  1. **Побудувати кандидатів-магнітів:** для КОЖНОГО онлайн-гравця, що є Beyonder'ом, зібрати
     `ResonantBeyonder(id, pathway, group, sequenceLevel, x, z)`. Група —
     `pathwayManager.getPathway(pathwayName).getGroup().name()`. Якщо жодного Beyonder'а — пропустити тік.
  2. **Зібрати джерела:** для кожного онлайн-Beyonder'а (скан-центр) взяти `getNearbyEntities(radius,
     radius, radius)` і **дедуплікувати** по `Entity` через `Set<UUID>` на весь тік (об'єкт у радіусі
     двох гравців обробляється **один раз**):
     - `Item` → `characteristicCodec.read(item.getItemStack())` → `Characteristic(pathway, seq)`;
     - `wardenRemnantCodec.isRemnant(e)` → `read(e)` → `Characteristic(pathway, seq)`;
     - `gateway.creatureId(e)` → `creatureRegistry.get(id)` → `(def.pathway(), def.sequence())`.
     - Джерело без розпізнаного шляху/seq — пропустити.
  3. Для кожного джерела зібрати `ConvergenceSource(pathway, group, seq, x, z)` (група джерела —
     той самий резолв через `PathwayManager`; невідомий шлях → пропуск) і викликати
     `convergencePull.computePull(source, allMagnets, radius)`.
  4. Якщо є `PullResult`:
     - знайти локацію цільового Beyonder'а (`Bukkit.getPlayer(targetId)`; офлайн/зник → пропуск);
     - горизонтальний одиничний вектор `dir = (targetLoc − sourceLoc)` з `y = 0`, `normalize()`;
     - **предмет (`Item`):** `item.setVelocity(item.getVelocity().add(dir × item-drift-speed × strength))`
       — дуже м'який дрейф (дефолт `item-drift-speed = 0.08`); не чіпаємо `y` (гравітація предмета);
     - **моб (`Mob` — рештка/істота):** з імовірністю `mob-nudge-chance × strength` —
       `mob.getPathfinder().moveTo(targetLoc)` (природне забрідання; MythicMobs-AI не ламаємо —
       поодинокий `moveTo` лише зміщує ціль блукання);
     - **рідкісний відголос:** з імовірністю `whisper-chance` (незалежний ролл) — гравцю-магніту
       `player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.AMBIENT,
       0.15f, ~1.4f)`. **Без напряму, без тексту, без частинок.**
  5. Обробку кожного скан-центру загорнути в try/catch (патерн `AmbientCreatureSpawner` /
     `PassiveAbilityScheduler.tickAllPlayers`) — помилка на одному гравці не спиняє цикл.
- Живих Beyonder'ів (гравців) як *джерела* **не** обробляємо — вони лише магніти.

### Конфіг (`config.yml`)

```yaml
convergence:
  interval-ticks: 200      # як часто тікає доля-поле (10 с)
  radius: 128              # радіус виявлення (блоки)
  item-drift-speed: 0.08   # базовий горизонтальний velocity-нудж есенції-предмета
  mob-nudge-chance: 0.5    # базова P(pathfind до магніта) при strength = 1
  whisper-chance: 0.03     # рідкісний тихий ненапрямний звук притягнутому Beyonder'у
```

### Wiring (`ServiceContainer` + `MysteriesAbovePlugin`)

- У `ServiceContainer` сконструювати після наявних `characteristicCodec` / `wardenRemnantCodec` /
  `mythicCreatureGateway` / `creatureRegistry` / `pathwayManager` / `beyonderService`:
  ```java
  this.convergencePull = new ConvergencePull(); // або статичні методи — за смаком реалізації
  this.convergenceDriftScheduler = new ConvergenceDriftScheduler(
          plugin, beyonderService, pathwayManager, mythicCreatureGateway,
          creatureRegistry, characteristicCodec, wardenRemnantCodec,
          /* config: interval, radius, itemDriftSpeed, mobNudgeChance, whisperChance */);
  ```
- Геттер `getConvergenceDriftScheduler()`.
- У `MysteriesAbovePlugin.startSchedulers()` — `services.getConvergenceDriftScheduler().start()`;
  у `stopSchedulers()` — `...stop()` (як інші планувальники).
- **Жодного нового** `domain` → Bukkit чи `domain` → `pathways` зв'язку. ArchUnit-інваріанти
  збережено (`ConvergencePull` — чистий `domain.creatures`).

## Тестування

- **Юніт `ConvergencePullTest`** (чистий domain, без Bukkit):
  - свій шлях перемагає сусіда при рівній відстані;
  - «наступна потрібна» seq (`b.seq − 1`) підсилює `strength` проти «поточної» й «іншої»;
  - кандидат поза радіусом — виключений;
  - нуль резонансу (чужий шлях і чужа група) → `Optional.empty()`;
  - сусідній шлях (та сама група) резонує; чужа група — ні;
  - тайбрейк по найближчому при рівній вазі; детермінований добір при повній рівності;
  - `strength` у межах (0,1]; максимум (свій шлях + наступна seq) = 1.0.
- **In-server (ручне):** `mvn clean package`; на сервері:
  1. Дропнути `Характеристику[твій шлях, seq]` за ~30 блоків від Beyonder'а того ж шляху → есенція
     повільно, непомітно сунеться до нього; підійти — зустріч «сама сталася».
  2. Заспавнити істоту твого шляху поряд → зрідка забрідає до тебе (не агресивно-телепортно).
  3. Дропнути Характеристику **чужого шляху й чужої групи** → лежить нерухомо.
  4. Есенція сусіднього шляху (та сама група) → тяжіє слабше, ніж свого.
  5. Зрідка — тихий відголос без напряму; жодного HUD/частинок/тексту.
  6. `ArchitectureTest` зелений: `ConvergencePull` — Bukkit-вільний `domain.creatures`.

## Перевикористання (не переписуємо)

- `ConvergenceBias` / `CreatureSelector.multiplier` — **взірець** seq-множників (×4/×2/×1); повторюємо ту
  саму семантику «наступна потрібна / поточна / інша» у `ConvergencePull`.
- `AmbientCreatureSpawner` — патерн планувальника (`start`/`stop`, один `BukkitTask`, try/catch на гравця,
  читання конфіга `creatures.*`) для `ConvergenceDriftScheduler`.
- `CharacteristicCodec.read(ItemStack)` (Спек 1) — розпізнавання впалої Характеристики.
- `WardenRemnantCodec` (Спек 2) — розпізнавання решток; `Characteristic` VO — `(шлях, seq)`.
- `MythicCreatureGateway.creatureId` (Спек 3) + реєстр істот — шлях/seq міфічної істоти.
- `PathwayManager.getPathway(name).getGroup()` — резолв групи шляху для резонансу.
- `BeyonderService` — перелік онлайн-Beyonder'ів (магніти) з їхнім шляхом/seq.

## Файли (орієнтовно)

**Нові:**
- `domain/creatures/ConvergencePull.java`
- `domain/creatures/ConvergenceSource.java` (record)
- `domain/creatures/ResonantBeyonder.java` (record)
- `domain/creatures/PullResult.java` (record)
- `infrastructure/schedulers/ConvergenceDriftScheduler.java`
- `src/test/java/.../creatures/ConvergencePullTest.java`

**Змінені:**
- `infrastructure/di/ServiceContainer.java` (wiring правила + планувальника + геттер)
- `MysteriesAbovePlugin.java` (`start()`/`stop()` планувальника)
- `src/main/resources/config.yml` (секція `convergence`)

## Оновлення правил/доків (у тому ж комітному потоці)

- `.claude/rules/mythic-creatures.md` — секція «Відомі спрощення»: додати, що Закон Конвергенції тепер
  діє як **приховане тяжіння резонансних об'єктів до Beyonder'а** (не компас), радіус 128, рухаються
  лише не-гравцеві об'єкти.
- Розглянути короткий рядок у CLAUDE.md (розділ config/persistence) про секцію `convergence` у
  `config.yml`, якщо інші планувальники там згадані.
</content>
</invoke>
