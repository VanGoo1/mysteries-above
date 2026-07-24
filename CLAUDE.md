# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

`mysteries-above` is a Spigot/Bukkit Minecraft plugin (Java 21, API 1.21) inspired by *Lord of the Mysteries*. Players become **Beyonders** who progress along a **Pathway** through **Sequences** (9 = weakest → 0 = strongest), unlocking **Abilities** at each sequence by drinking potions. Much of the in-code text, descriptions, and comments are in **Ukrainian** — keep new user-facing strings consistent with that.

## Commands

- **Build**: `mvn clean package` (default goal; produces a shaded plugin JAR via maven-shade-plugin). Shading bundles `glowingentities`, `EffectLib`, and `triumph-gui`; `paper-api` (the server runs Paper), `coreprotect`, and MythicMobs (`io.lumine:Mythic-Dist`) are `provided` — MythicMobs is a separate plugin dependency (`depend: [Citizens, MythicMobs]` in `plugin.yml`), not shaded into the JAR.
- **Run tests**: `mvn test` (JUnit 5; ArchUnit for architecture rules). Surefire + test deps live in `pom.xml`.
- **Single test class**: `mvn test -Dtest=SpellRecipeTest`
- **Single test method**: `mvn test -Dtest=SpellRecipeTest#aoeScalesWithPowerAndArea`
- Pure-domain logic (progression, balance) is unit-tested without Bukkit; ability **effects** are verified in-server, not mocked.

## Architecture

Layered / hexagonal design under `me.vangoo`. Dependencies point inward toward `domain`:

- **`domain`** — pure business logic, no Bukkit scheduling/DI. Key pieces:
  - `entities`: `Beyonder` (the player aggregate), `Pathway` (abstract — concrete pathways live in the behavior layer below; also has a 3-arg explicit-name constructor, currently unused, available for a caller that needs to override the derived name), `PathwayGroup` (9 constants: `LordOfMysteries`, `DemonOfKnowledge`, `GodAlmighty`, `TheAnarchy`, `EternalDarkness`, `GoddessOfOrigin`, `CalamityOfDestruction`, `FatherOfDevils`, `KeyOfLight`).
  - `PathwayBranding` (`me.vangoo.domain`): the color registry for all 22 pathways — `of`/`liquidOf`/`textOf`/`NAMES` map each pathway name to a `Color`+`ChatColor` pair (gray fallback for unknown/null). See `.claude/rules/pathway-branding.md`.
  - `abilities.core`: `Ability` base class + `ActiveAbility` / `PermanentPassiveAbility` / `ToggleablePassiveAbility`, `AbilityType`, `AbilityResult`, and the `IAbilityContext` interface.
  - `abilities.context`: fine-grained context interfaces (`ICooldownContext`, `IMessagingContext`, `ITargetContext`, `IGlowingContext`, `IRampageContext`, `ISchedulingContext`, `IUIContext`, etc.) — abilities depend on these, not on concrete Bukkit services.
  - `spells`: pure spell data/rules (`SpellRecipe`, `SpellBlueprint`, `SpellCodec`) — no Bukkit; the reference example of the rules-vs-effects seam.
  - `valueobjects`: immutables like `Sequence`, `Spirituality`, `Mastery`, `SanityLoss`, `AbilityResult`, `SequenceBasedSuccessChance`.
- **`me.vangoo.pathways`** — the **ability behavior / effect layer** (its own layer, **not** part of `domain`). Bukkit is allowed here; it depends inward on `domain` and is orchestrated by `application` (`PathwayManager` wires it). This is the home of the **effect** half of the rules-vs-effects seam. Contents:
  - one package per pathway (`error`, `door`, `justiciar`, `visionary`, `whitetower`, `fool`) + a shared `common` package (abilities that belong to several pathways at once: `RitualMagic` + `RitualSession`/`RitualEffectRunner` — see `.claude/rules/ritual-magic.md`), each pathway package with an `abilities` subpackage, a concrete `Pathway` subclass that builds its per-sequence ability lists in `initializeAbilities()`, and a `*Potions` class. These 6 are the only pathways with real abilities and brew recipes. The other 16 registered pathways are scaffolds: each has its own package `me.vangoo.pathways.<name>` (a `<Name> extends Pathway` with an empty `initializeAbilities()`, a `<Name>Potions`, and an empty `abilities` package via `package-info.java`) — identical in shape to the 6 real pathways, ready to be filled in. Scaffolds have potions/characteristics (colored via `PathwayBranding`) but no abilities and no brew recipes; "not yet implemented" is detected by `Pathway.hasAnyAbility()`. `PathwayManager.initializePathways()` wires all 22.
  - **thin abilities** — `Ability` subclasses holding only glue (name/cost/cooldown + delegation); balance math is pulled out to `domain` VOs.
  - **runners** — stateless Bukkit choreography for one-shot effects (e.g. `SpellEffectRunner`).
  - **sessions** — live, self-ticking objects for stateful effects (e.g. `JurisdictionSession`, `DiviningRodSession`, `DreamVisionSession`).
  - **Boundary:** `domain` must never depend on this layer — `ArchitectureTest.domainDoesNotDependOnBehaviorLayer` fails the build if it does. Note: **sessions** and **recipe-VOs are patterns _within_ layers, not new layers** — sessions live here, recipe-VOs live in `domain`; the rules-vs-effects seam cuts _across_ the `domain`↔`pathways` boundary rather than adding a level.
- **`application.services`** — orchestration: `BeyonderService`, `AbilityExecutor`, `PathwayManager`, `CooldownManager`, `RampageManager`, `PassiveAbilityManager`, `PotionManager`, etc. The concrete context implementations live in `application.services.context`.
- **`infrastructure`** — Bukkit/external integrations: JSON repositories (`JSONBeyonderRepository` wrapped by `BatchedBeyonderRepository`), schedulers, item/recipe/loot factories, UI helpers, `mythic` (MythicMobs bridge: `MythicCreatureGateway`, `MythicBridge`, custom components, `MythicPackInstaller`), and `di.ServiceContainer`.
- **`presentation`** — `commands`, `listeners` (Bukkit event handlers), and GUI/menu glue.

## Change discipline
Before changing code:
- Read target files first.
- Search usages/callers before changing APIs.
- Prefer minimal patches.
- Prefer extending existing patterns over introducing new ones.
- Avoid broad refactors during feature work.
- Keep changes limited to the requested feature.
- Explain architectural changes before implementing them.
- After two failed approaches, stop and reassess.

### Wiring (`ServiceContainer`)
There is **no DI framework**. `MysteriesAbovePlugin.onEnable()` constructs a single `ServiceContainer` (`infrastructure.di`) that manually instantiates every service in dependency order and exposes getters. Listeners, commands, and schedulers are wired from these getters in the plugin's `registerEvents()` / `registerCommands()` / `startSchedulers()`. When you add a service, register it in `ServiceContainer` and thread it through there.

### ServiceContainer maintenance rules
Avoid unnecessary changes to `ServiceContainer`.

When adding a service:
- check if an existing service can be extended first
- add only the required wiring
- do not reorganize unrelated dependencies
- do not reorder existing initialization unless required
- avoid touching unrelated getters or services

### Ability execution flow
`Ability.execute(IAbilityContext)` is `final` and runs the pipeline: cooldown check → `canExecute` → `preExecution` → optional sequence-based success roll (`getSequenceCheckTarget`) → `performExecution` (the method subclasses implement) → `postExecution`. Note: for `ACTIVE` abilities the cooldown is **not** set inside `execute()` — it is applied later by `AbilityResourceConsumer` after resource consumption, and `AbilityResult` may be **deferred** (don't set cooldown on deferred results). Use `scaleValue(base, sequence, strategy)` (via `SequenceScaler`) to make effects stronger as the sequence level drops.

## Architecture seam: rules vs effects

When adding or changing ability code, classify it with one question:
**Is this a rule that must stay correct (progression, costs, cooldown-as-a-number, sanity, state invariants) — or an effect in the world (particles, damage, teleport, GUI, inventory)?**

- **Rule** → `domain`, plain Java, unit-tested, **zero Bukkit**.
- **Effect** → the behavior layer `me.vangoo.pathways` (thin ability + runner/session); cross-cutting Bukkit services (scheduling, cooldown, persistence) live in `application` / `infrastructure`. Bukkit allowed, verified in-server.

The pure core of `domain` (`entities`, `services`, `spells`, `brewing`, `creatures`) must not import `org.bukkit.*`, `dev.triumphteam.*`, or `net.kyori.*`, and `domain` must not depend on the behavior layer `me.vangoo.pathways`. Ability **behavior** lives in `me.vangoo.pathways.*` (Bukkit allowed). `ArchitectureTest` pins both invariants — widen the pure-domain scope as more code is cleaned. (Still not clean: `valueobjects` `CustomItem` / `RecordedEvent`, and `domain.abilities.core/context` which still expose Bukkit types like `Player` / `Location`.)

**Two patterns for a complex ability — pick by lifecycle:**

- **One-shot (stateless)** — fires and forgets (damage, teleport, buff). Shape: data-recipe (pure VO) → runner (effect layer) → thin `Ability`. Reference: `SpellRecipe` + `SpellCodec` (pure domain) / `SpellEffectRunner` (Bukkit choreography) / `GeneratedSpell` (thin adapter). The GUI (`Spellcasting`) only collects a `SpellBlueprint`; balance math lives in `SpellRecipe.fromBlueprint`.
- **Stateful (long-lived)** — establishes something that lives across time with a `start → tick → cancel` lifecycle (zones, clones, body-swaps). Shape: pure params → **session** object (owns its own `BukkitTask` + `tick()` + `cancel()`) → thin `Ability` holding an **instance** `Map<UUID, Session>` registry. Reference: `AreaOfJurisdiction` / `JurisdictionSession`; also `DiviningRodSession` and `DreamVisionSession` (the `door` pathway). Rules: (1) the registry is an **instance** field, never `static` — an ability instance is already shared per-pathway, so that is the correct scope; (2) the session ticks via **Bukkit directly**, never a captured `IAbilityContext` — capturing one caster's context into shared state is a bug. (Exception: a session may hold a **global, non-caster-bound service** like `IEventContext` when it genuinely needs it — see `DreamVisionSession`'s event subscriptions; the test is "does this reference carry one caster's identity?", not "is it from the context?"); (3) the ability overrides `cleanUp()` to `cancel()` every session (wired through `Beyonder.cleanUpAbilities()` on disable), and a re-cast replaces+cancels the owner's previous session.

**Anti-patterns (these produced the current debt):**
- ❌ Adding a context method just to wrap one Bukkit call you could make directly in the effect layer.
- ❌ Passing Bukkit types (`Location`, `Player`, `ItemStack`) through a "port" and calling it an abstraction.
- ❌ Putting balance / stat math in a GUI or behavior class.
- ❌ `static` mutable state on an ability (registries, tasks, a captured `IAbilityContext`) → use an instance field + a session that owns its own task.
- ❌ An invariant defended by a `КРИТИЧНО` comment → it should be a type or a test.

## Adding a Pathway / Ability

1. **Ability**: create a class in `me.vangoo.pathways.<pathway>.abilities` extending `ActiveAbility`, `PermanentPassiveAbility`, or `ToggleablePassiveAbility`. Implement `getName`, `getDescription(Sequence)`, `getSpiritualityCost`, `getCooldown(Sequence)`, and `performExecution(IAbilityContext)`. Access services via the typed context sub-interfaces (e.g. `context.cooldown()`, `context.messaging()`, `context.targeting()`). Keep balance/stat math in `domain` (a pure VO like `SpellRecipe`); keep only Bukkit effects here.
2. **Pathway**: register abilities per sequence in the pathway's `initializeAbilities()` (in `me.vangoo.pathways.<pathway>`) via `sequenceAbilities.put(sequence, List.of(...))`.
3. **Register the pathway**: add it to `PathwayManager.initializePathways()` with its `PathwayGroup` and the list of 10 sequence names.

## Config & persistence

- `src/main/resources/`: `plugin.yml` (commands + `mysteriesabove.admin` permission), `config.yml` (секції `creatures.*`, `market.*`, `convergence.*`, `church.*`), `custom-items.yml`, `global_loot.yml`, `creatures.yml`, `potion-recipes.yml`, `forage.yml` (форедж: цілі/донори/біомні таблиці — див. `.claude/rules/forage.md`). `plugin.yml` and `*.yml` are Maven-filtered resources; `mythic-pack/**` (see below) is not.
  `config.yml` містить секції `creatures.*` (спавн істот), `market.*` (підпільний ринок), `convergence.*` (приховане тяжіння Закону Конвергенції), `church.*` (церкви: ранги/завдання/замовлення/пожертви/сід сховищ — див. `.claude/rules/church-organizations.md`) і `orders.*` (таємні організації: запрошення/завдання/рейд/замах/шпигунство/схованка/фавори — див. `.claude/rules/secret-orders.md`); усі читає `ServiceContainer` через `plugin.getConfig()`.
- Mob content (stats, appearance, skills, templates) lives in the MythicMobs pack `src/main/resources/mythic-pack/`, installed to the server by `MythicPackInstaller`; spawn/loot rules stay in code (`domain.creatures` + `creatures.yml`). See `.claude/rules/mythic-creatures.md` for the full mechanism.
- Клієнтські ассети (текстури інгредієнтів, «зачаровані» блоки фореджу) — серверний ресурспак `mysteries-resourcepack/`; датапак структур — `mysteries-datapack/`. Обидва роздаються поза Maven-збіркою (див. README ресурспаку).
- **Матеріали предметів**: кожен предмет плагіну стоїть на музичній пластинці (`MUSIC_DISC_*`), одна на категорію, а НЕ на `PAPER` — ванільний папір споживає шлях Блазня як ресурс. Обробка пластинки (ліміт стака, зняття `jukebox_playable`) — тільки через `infrastructure.items.DiscItems`; розпізнавання предметів — по NBT, ніколи по матеріалу чи lore. Див. `.claude/rules/item-materials.md`.
- Player state persists to `beyonders.json` in the plugin data folder, written by `BatchedBeyonderRepository` (batched save every 5 minutes + save on disable). Recipe unlocks persist to `recipe_unlocks.json`.
- Підпільний ринок: стан зборів персиститься в `gathering-state.json`
  (`GatheringSnapshotRepository`; час наступного збору + ескроу + черга повернень);
  світ-заглушка `mysteries_gathering` створюється ідемпотентно. Див. `.claude/rules/market-gathering.md`.
- Церкви (Економіка 6b): `memberships.json` (членства/кулдаун/флаг пройденої дуелі-без-шляху
  `trialPassed` [тимчасовий] + флаг «вже колись ініційований» `initiationUsed` [постійний]
  + історія замовлених зілль `orderedPotions` [в межах членства] + назавжди зречені церкви
  `abandonedChurches` [вихід із церкви необоротний]),
  `church-sites.json` (сайти храмів + оброблені села), `churches-state.json`
  (сховища церков) — усі пишуться після кожної мутації (`ChurchService`, каркас
  `GatheringSnapshotRepository`). Секція `church.*` у `config.yml` (ранги/завдання/замовлення/
  пожертви/сід сховищ) читає `ChurchConfig` з дефолтами в коді. Реєстр інституцій — код
  (`InstitutionRegistry`), не конфіг. Ініціація — дуель проти Seq-9 істоти чужого домену
  в окремому void-світі `mysteries_duel` (`ChurchDuelService`/`DuelSession`/
  `DuelArenaProvider`/`DuelBriefing`/`DuelListener`), а не сховищний обряд. Див.
  `.claude/rules/church-organizations.md`.
- Таємні організації (Економіка 6c): `order-memberships.json` (`JSONOrderMembershipRepository`
  — членства/ранг=послідовність/фавори/задачі/запрошення/кулдаун вступу/
  `abandonedOrders` [вихід із ордену необоротний]/`pendingRaidLoot`+
  `pendingRaidChurch` [незданий рейд-лут і церква, з якої він]/`falsePapers`) і
  `orders-state.json` (`OrderStateRepository` — схованки орденів, розвіддані з TTL,
  кулдауни храмів і закриті-замахом священики) — обидва пишуться після кожної мутації,
  Gson-каркас `GatheringSnapshotRepository` (corrupt/missing → порожньо). Секція `orders.*`
  у `config.yml` читає `OrderConfig` з дефолтами в коді; реєстр 25 орденів — той самий код
  `InstitutionRegistry`, що й церков. Рейд на сховище храму й замах на священика — прямі
  точки дотику з `ChurchVault`/`ChurchService` (другий вихід сховища, sneak-клік по
  священику зарезервовано під шпигунство). Див. `.claude/rules/secret-orders.md`.
- Магічне засвідчення Sun (Посл. 6): здібність `Contract` відкриває Золотий Сувій
  (`ContractMenu`) — Мир (`PEACE`)/Клятва (`DEBT`)/Обмін/Засвідчення вміння. `contracts.json`
  (`JSONContractRepository`) персиститься лише для підписаних `PEACE`/`DEBT`; обмін
  (validate-at-commit, без застави) і засвідчення (баф союзника через `context.amplification()`)
  стану не мають. Здібності дістаються сервісу через `IContractContext`/`context.contracts()`.
  Порушення виявляє `ContractListener` (удар між сторонами `PEACE`, дедлайн `DEBT`) і кличе
  `ContractService.breach()` → `DivinePunishment.punish()` (holy lightning + true damage +
  печатка здібностей 10-15 хв + Зламаний Сонячний Диск). `DivinePunishment` — перевикористовуваний
  ранер покарання для майбутніх механік. Прогрес `Beyonder`'а кара не чіпає. Див. `docs/contracts.md`.
- Admin commands (all require `mysteriesabove.admin`): `/pathway`, `/mastery`, `/rampager`, `/potion`, `/custom-items`, `/recipe`, `/structure`, `/characteristic`, `/coins`. Creature testing goes through MythicMobs' own command: `/mm mobs spawn <id>`. `/gathering` — гравецька команда (join/menu), її start/stop — адмінські (перевірка права в коді). `/church` — гравецька (leave/info; `leave` двокрокова — діє лише `/church leave confirm`, бо
вихід із церкви необоротний), bind/unbind — адмінські (перевірка права в коді). `/order` — гравецька команда (invites/accept/raid/leave/info; `leave` теж двокрокова —
`/order leave confirm`, вихід із ордену необоротний); сам вступ за шифрованим посланням
йде через предмет-меню (`OrderMenu`), не команду, а головне меню ордену — вкладка в меню
Містичних Здібностей (`AbilityMenu`, слот 6,5, лише члену), не предмет.

## Maintaining docs & rules

Scoped working rules live in `.claude/rules/*.md` (loaded conditionally via `paths:` frontmatter; rules without frontmatter are always on). Treat them as part of the codebase:

- **When you add a new mechanic or cross-cutting pattern that affects — or will plausibly grow to affect — the wider architecture** (a new layer or seam, a lifecycle pattern like sessions, a persistence store, a registration/wiring flow, a config subsystem), add a dedicated rule file for it in `.claude/rules/` describing which classes/services/abstractions to use, where, and how — and update everything it touches: the relevant CLAUDE.md sections, existing rules that now overlap or contradict, and `ArchitectureTest` if the invariant is machine-enforceable.
- Do this **in the same commit/branch** as the code change. A mechanic that exists only in code and git history is undocumented; a rule that contradicts the code is worse than no rule.
- Keep rules non-duplicative: architecture overview belongs here in CLAUDE.md; rules hold the mechanism-specific "how to use it correctly" detail.

