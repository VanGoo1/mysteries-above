# Істоти: MythicMobs

Контент мобів (стати, вигляд, скіли, шаблони) живе у **MythicMobs-паку** `src/main/resources/mythic-pack/`
(розгортається у `plugins/MythicMobs/Packs/MysteriesAbove` через `MythicPackInstaller`, який **перезаписує**
серверні файли — джерело правди тільки репозиторій, не редагуй пак на сервері).
Правила спавну/луту лишаються в коді: `domain.creatures` + `creatures.yml`.

## Join-ключ

`creatures.yml` id == internal name моба в паку (напр. `error_sphinx_9`). Перейменування — завжди в обох місцях.
`base_entity` у `creatures.yml` дублює `Type` пака свідомо (потрібен чистому домену для aquatic ambient-спавну).

## Додати істоту

1. Моб у `mythic-pack/Mobs/<pathway>.yml`: `Template: MA_<Pathway>_Common|Apex`, Type/Display/Health/Damage/Options.
   Кожен запис Mobs/*.yml (шаблони теж) мусить резолвити `Type` напряму або через ланцюжок `Template:`,
   інакше "No Type specified" на старті (гард — `MythicPackMobTypeTest`).
2. Запис у `creatures.yml`: `base_entity`, `tier`, `loot` (тільки інгредієнти), `spawn` (natural/structure).
3. Нова поведінка → метаскіл у `mythic-pack/Skills/<pathway>.yml`; таргет-гейт — умова `isbeyonder`.
4. Файл додано? Впиши його в `MythicPackInstaller.PACK_FILES`.
5. Перевірка in-server: `/mm mobs spawn <id>`, скіли/лут/агро.

## Межі (ArchitectureTest)

- `io.lumine..` — ТІЛЬКИ в `me.vangoo.infrastructure.mythic..` (`mythicMobsApiIsConfinedToBridgePackage`).
  Спавн/ідентифікація для решти коду — через `MythicCreatureGateway` (`spawn(id, loc)`, `isCreature`, `creatureId`).
- Кастомні механіки/умови (`drainsanity`, `scatter`, `isbeyonder`) — в `infrastructure.mythic.components`;
  сервіси беруть зі статичного `MythicBridge` (єдиний дозволений static-виняток: конструктор диктує MythicMobs).
  Реєстрація компонентів — `MythicBridge.registerComponents(plugin)`, викликати в `onEnable` ПІСЛЯ `MythicBridge.init(beyonderService)`.
  Конструктор компонента — ОБОВʼЯЗКОВО публічний із параметром load-event
  (`MythicMechanicLoadEvent` / `MythicConditionLoadEvent`) — реєстр шукає саме його рефлексією
  (гард — `MythicComponentContractTest`).
  Після мутації Beyonder у механіці — обовʼязково `beyonderService.updateBeyonder(...)`.
- Балансові числа спавну/луту (шанси, конвергенція, дистанції) — у `domain.creatures` з юніт-тестами,
  НЕ в YAML пака. У паку — тільки хореографія ефектів (аркадні прості атаки, ванільна стилістика).

## Відомі спрощення

- Таргет «найближчий потойбічний» апроксимовано `@NearestPlayer + isbeyonder` (не-потойбічний ближче — тік холостий).
- Саммон ляльок Fool — скіл-кулдаун 30с при HP<35% (замість one-shot у старому коді).
- Снаряд Whitetower (`shoot` на `@NearestPlayer`) не має `isbeyonder`-гейта і може летіти в не-потойбічного (шкода незначна).

## 3D-моделі (майбутнє)

Обирається per-mob у паку: ванільний `Type` (за замовчуванням) або ModelEngine/BetterModel-модель
через їхню інтеграцію з MythicMobs — код плагіна не змінюється.
