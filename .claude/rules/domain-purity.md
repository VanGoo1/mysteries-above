---
paths:
  - "src/main/java/me/vangoo/domain/**"
  - "src/test/java/me/vangoo/**"
---

# Domain: чисте ядро правил

`me.vangoo.domain` — правила гри (прогрес, баланс, інваріанти стану). Сюди кладемо **числа і рішення**, не ефекти.

## Що де лежить

- `domain.entities` — `Beyonder` (агрегат гравця: spirituality/mastery/sanity, `useAbility`, `advance`, `cleanUpAbilities`), абстрактний `Pathway` (конкретні — у шарі `me.vangoo.pathways`), `PathwayGroup`.
- `domain.abilities.core` — `Ability` (final-пайплайн `execute`), базові класи `ActiveAbility` / `PermanentPassiveAbility` / `ToggleablePassiveAbility` / `OneTimeUseAbility`, `AbilityResult` (success / failure / deferred / sequenceResistance), `AbilityResourceConsumer` (ресурси для deferred-здібностей), `IAbilityContext`.
- `domain.abilities.context` — 12 вузьких інтерфейсів контексту (`ICooldownContext`, `ISchedulingContext`, `IMessagingContext`, `ITargetContext`, `IEventContext`, `IBeyonderContext`, `IRampageContext`, `IGlowingContext`, `IUIContext`, `IDataContext`, `IEntityContext`, `IVisualEffectsContext`). Реалізації — в `application.services.context`.
- `domain.services` — чиста математика: `SequenceScaler` (скейлінг від Sequence, стратегії), `MasteryProgressionCalculator`, `SpiritualityCalculator`, `AbilityTransformer` (заміна здібностей при advance через `AbilityIdentity.canReplace`).
- `domain.spells` — еталон «правила окремо від ефектів»: `SpellBlueprint` (сирий вибір із GUI) → `SpellRecipe.fromBlueprint` (ВСЯ балансна математика) → `SpellCodec` (серіалізація). Ефекти — у `pathways.whitetower.abilities.custom.SpellEffectRunner`.
- `domain.brewing` — `BrewMatcher`, `BrewRecipe`, `RecipeDefinition`, `Characteristic` (правила варіння зілль; конфіг вантажить `PotionRecipeConfigLoader` з `potion-recipes.yml`).
- `domain.creatures` — `CreatureDefinition`, `CreatureSelector`, `SpawnDistanceGate`, `SpawnRule`, `ConvergenceBias` (правила спавну істот; Bukkit-частина — в `infrastructure.creatures`).
- `domain.valueobjects` — імутабельні VO: `Sequence`, `Spirituality`, `Mastery`, `SanityLoss`, `SanityPenalty`, `SequenceBasedSuccessChance`, `DivinationOdds`, `AbilityIdentity` тощо.
- `domain.events` — `AbilityDomainEvent`, `RampageDomainEvent` (публікує `DomainEventPublisher` з application).
- Корінь `domain` — `PathwayPotions` (база для `*Potions` кожного pathway), `IItemResolver`.

## Правила

1. **Жодного нового Bukkit у domain.** `ArchitectureTest.pureDomainCoreHasNoBukkitOrGuiDependencies` пінить чистий скоуп: `entities`, `services`, `spells`, `brewing`, `creatures` — там заборонені `org.bukkit..`, `dev.triumphteam..`, `net.kyori..`. Решта пакетів (`valueobjects.CustomItem`/`RecordedEvent`, `abilities.core/context` з `Player`/`Location`) — відомий борг: не додавай туди нових Bukkit-типів, а коли чистиш — розширюй масив `PURE_DOMAIN` у тесті.
2. **`domain` ніколи не імпортує `me.vangoo.pathways`** — `ArchitectureTest.domainDoesNotDependOnBehaviorLayer` завалить білд.
3. **Нова балансна формула = чистий VO або domain.services + unit-тест.** Зразки: `SpellRecipeTest`, `SequenceScalerTest`, `DivinationOddsTest`, `BrewMatcherTest`. Запуск: `mvn test -Dtest=SpellRecipeTest` (метод: `-Dtest=SpellRecipeTest#aoeScalesWithPowerAndArea`).
4. Ефекти (партикли, телепорт, GUI, шкода) домену не стосуються — їм місце в `me.vangoo.pathways` (див. правило pathway-abilities).
