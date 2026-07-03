---
paths:
  - "src/main/java/me/vangoo/application/**"
  - "src/main/java/me/vangoo/infrastructure/**"
  - "src/main/java/me/vangoo/presentation/**"
  - "src/main/java/me/vangoo/MysteriesAbovePlugin.java"
---

# Wiring: ServiceContainer і точки входу

DI-фреймворку немає. Увесь граф збирає вручну `infrastructure.di.ServiceContainer`; споживає його `MysteriesAbovePlugin.onEnable()`.

## Порядок ініціалізації (конструктор ServiceContainer)

`initializeCoreServices` (PathwayManager, CooldownManager, AbilityLockManager, DomainEventPublisher, RampageManager, SanityPenaltyHandler, PassiveAbilityManager, TemporaryEventManager) → `initializeInfrastructure` (репозиторії, фабрики айтемів, конфіг-лоадери, PotionManager, лут) → `initializeApplicationServices` (BeyonderService, вся підсистема істот, AbilityContextFactory, AbilityExecutor, PotionCraftingService) → `initializeUI` (AbilityMenu) → `initializeSchedulers` → `initializeEventListeners` → `initializeRecipes`.

**Новий сервіс**: поле + створення у правильній фазі (залежності мусять бути вже створені) + геттер. Далі підключити у плагіні.

## Чеклісти підключення

- **Listener**: клас у `presentation.listeners` (ігрові Bukkit-події) або `infrastructure.listeners` (реакції на доменні події, як `RampageEventListener`). Створити в `ServiceContainer` (або локально в `registerEvents()`), зареєструвати в `MysteriesAbovePlugin.registerEvents()` через `getServer().getPluginManager().registerEvents(...)`. Незареєстрований listener мовчки не працює.
- **Command**: клас у `presentation.commands`; у `MysteriesAbovePlugin.registerCommands()` — `setExecutor` + `setTabCompleter`; **обов'язково** оголосити команду в `plugin.yml` (permission `mysteriesabove.admin`), інакше `getCommand()` поверне null.
- **Scheduler**: клас у `infrastructure.schedulers` зі `start()`/`stop()`; додати в `ServiceContainer.startSchedulers()` **і** `stopSchedulers()`.
- **Підписка на доменні події**: `services.getEventPublisher().subscribeToAbility(...)` / `subscribeToRampage(...)` в `onEnable()`.

## Виконання здібностей (єдиний вхід)

`AbilityExecutor.execute(beyonder, ability)`: перевірка `AbilityLockManager` → створення контексту через `AbilityContextFactory.createContext(player)` → pre-check кулдауна → `beyonder.useAbility(ability, context)` (уся доменна логіка там) → публікація `AbilityDomainEvent.AbilityUsed` → toggle-пасивки через `PassiveAbilityManager.toggleAbility` → sanity-штрафи (`SanityPenaltyHandler`, EXTREME → `RampageManager.startRampage`) → `beyonderService.updateBeyonder(beyonder)`.

Не викликай `ability.execute(...)` в обхід `AbilityExecutor` з presentation-коду — загубиш локи, події, штрафи й збереження.

## Доступ до гравців-Beyonder'ів

Тільки через `BeyonderService` (`getBeyonder`, `createBeyonder`, `updateBeyonder`, `setOverride`/`removeOverride` для маріонеток). Після мутації `Beyonder` поза `AbilityExecutor` — виклич `beyonderService.updateBeyonder(...)`, інакше зміни не потраплять у батч-збереження.

## onDisable — порядок важливий

`stopSchedulers()` (в т.ч. `ambientCreatureSpawner.stop()`) → повернення гравців із маріонеток у власне тіло (див. блок `MarionettistControl` в `onDisable`) → `saveAll()` → `cleanup()` (форс-сейв + `beyonder.cleanUpAbilities()` для кожного — саме тут скасовуються сесії здібностей). Нове, що тримає стан/таски, має влитись у цей ланцюжок.
