package me.vangoo.application.services;

import de.slikey.effectlib.EffectManager;
import fr.skytasul.glowingentities.GlowingEntities;
import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.domain.abilities.core.IAbilityContext;
import org.bukkit.entity.Player;

import java.util.Objects;

public class AbilityContextFactory {

    private final MysteriesAbovePlugin plugin;
    private final CooldownManager cooldownManager;
    private final BeyonderService beyonderService;
    private final AbilityLockManager lockManager;
    private final GlowingEntities glowingEntities;
    private final EffectManager effectManager;
    private final RampageManager rampageManager;
    private final TemporaryEventManager temporaryEventManager;
    private final PassiveAbilityManager passiveAbilityManager;
    private final DomainEventPublisher eventPublisher;
    private final RecipeUnlockService recipeUnlockService;
    private final PotionManager potionManager;
    private final ContractService contractService;
    private final AmplificationManager amplificationManager;
    private final me.vangoo.infrastructure.waypoints.WaypointStore waypointStore;
    private final me.vangoo.infrastructure.theft.TheftLedger theftLedger;
    private final PathwayManager pathwayManager;
    private final me.vangoo.infrastructure.mythic.MythicCreatureGateway mythicCreatureGateway;
    private final java.util.Map<String, me.vangoo.domain.creatures.CreatureDefinition> creatureRegistry;
    private final me.vangoo.domain.rituals.IngredientSourceIndex ingredientSources;
    /**
     * Церкви приходять сеттером, а не конструктором: ChurchService будується пізніше за цю
     * фабрику (той самий прийом, що й ChurchService.setFalsePapersCheck). Контексти
     * створюються вже в рантаймі, тож на момент першого касту поле проставлене.
     */
    private ChurchService churchService;


    public AbilityContextFactory(
            MysteriesAbovePlugin plugin,
            CooldownManager cooldownManager,
            BeyonderService beyonderService,
            AbilityLockManager lockManager,
            GlowingEntities glowingEntities,
            EffectManager effectManager,
            RampageManager rampageManager,
            TemporaryEventManager temporaryEventManager,
            PassiveAbilityManager passiveAbilityManager,
            DomainEventPublisher eventPublisher,
            RecipeUnlockService recipeUnlockService,
            PotionManager potionManager,
            ContractService contractService,
            AmplificationManager amplificationManager,
            me.vangoo.infrastructure.waypoints.WaypointStore waypointStore,
            me.vangoo.infrastructure.theft.TheftLedger theftLedger,
            PathwayManager pathwayManager,
            me.vangoo.infrastructure.mythic.MythicCreatureGateway mythicCreatureGateway,
            java.util.Map<String, me.vangoo.domain.creatures.CreatureDefinition> creatureRegistry,
            me.vangoo.domain.rituals.IngredientSourceIndex ingredientSources
    ) {
        this.plugin = Objects.requireNonNull(plugin, "Plugin cannot be null");
        this.cooldownManager = Objects.requireNonNull(cooldownManager, "CooldownManager cannot be null");
        this.beyonderService = Objects.requireNonNull(beyonderService, "BeyonderService cannot be null");
        this.lockManager = Objects.requireNonNull(lockManager, "AbilityLockManager cannot be null");
        this.glowingEntities = Objects.requireNonNull(glowingEntities, "GlowingEntities cannot be null");
        this.effectManager = Objects.requireNonNull(effectManager, "EffectManager cannot be null");
        this.rampageManager = rampageManager;
        this.temporaryEventManager = temporaryEventManager;
        this.passiveAbilityManager = passiveAbilityManager;
        this.eventPublisher = eventPublisher;
        this.recipeUnlockService = recipeUnlockService;
        this.potionManager = potionManager;
        this.contractService = Objects.requireNonNull(contractService, "ContractService cannot be null");
        this.amplificationManager = Objects.requireNonNull(amplificationManager, "AmplificationManager cannot be null");
        this.waypointStore = Objects.requireNonNull(waypointStore, "WaypointStore cannot be null");
        this.theftLedger = Objects.requireNonNull(theftLedger, "TheftLedger cannot be null");
        this.pathwayManager = Objects.requireNonNull(pathwayManager, "PathwayManager cannot be null");
        this.mythicCreatureGateway = Objects.requireNonNull(mythicCreatureGateway, "MythicCreatureGateway cannot be null");
        this.creatureRegistry = Objects.requireNonNull(creatureRegistry, "Creature registry cannot be null");
        this.ingredientSources = Objects.requireNonNull(ingredientSources, "Ingredient source index cannot be null");
    }


    public void setChurchService(ChurchService churchService) {
        this.churchService = churchService;
    }

    public IAbilityContext createContext(Player caster) {
        return new BukkitAbilityContext(
                caster,
                plugin,
                cooldownManager,
                beyonderService,
                lockManager,
                glowingEntities,
                effectManager,
                rampageManager,
                temporaryEventManager,
                passiveAbilityManager,
                eventPublisher,
                recipeUnlockService,
                potionManager,
                contractService,
                churchService,
                amplificationManager,
                waypointStore,
                theftLedger,
                pathwayManager,
                mythicCreatureGateway,
                creatureRegistry,
                ingredientSources
        );
    }
}