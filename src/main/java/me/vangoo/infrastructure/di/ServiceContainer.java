package me.vangoo.infrastructure.di;

import me.vangoo.application.services.*;
import me.vangoo.domain.valueobjects.CustomItem;
import me.vangoo.infrastructure.*;
import me.vangoo.infrastructure.abilities.AbilityItemFactory;
import me.vangoo.infrastructure.market.GatheringSnapshotRepository;
import me.vangoo.infrastructure.items.*;
import me.vangoo.infrastructure.listeners.RampageEventListener;
import me.vangoo.infrastructure.listeners.RampageRemnantDeathListener;
import me.vangoo.infrastructure.recipes.RecipeBookCraftingRecipe;
import me.vangoo.infrastructure.schedulers.*;
import me.vangoo.infrastructure.structures.LootGenerationService;
import me.vangoo.infrastructure.structures.LootTableConfigLoader;
import me.vangoo.infrastructure.ui.AbilityMenu;
import me.vangoo.infrastructure.ui.BossBarUtil;
import me.vangoo.presentation.listeners.BeyonderSleepListener;
import me.vangoo.presentation.listeners.MarionetteExitListener;
import me.vangoo.presentation.listeners.PaperThrowListener;
import me.vangoo.presentation.listeners.MainBodyAbilityListener;
import me.vangoo.presentation.listeners.MarionetteLifecycleListener;
import me.vangoo.presentation.listeners.MarionetteRestorer;
import me.vangoo.infrastructure.citizens.OrganizerNpcService;
import me.vangoo.MysteriesAbovePlugin;
import org.bukkit.plugin.java.JavaPlugin;

import me.vangoo.domain.brewing.RecipeDefinition;
import java.io.File;
import java.util.Map;

/**
 * Dependency Injection Container
 * Manages all service dependencies to avoid God Object pattern
 */
public class ServiceContainer {

    // Core services
    private final JavaPlugin plugin;
    private PathwayManager pathwayManager;
    private CooldownManager cooldownManager;
    private AbilityLockManager abilityLockManager;
    private RampageManager rampageManager;
    private AmplificationManager amplificationManager;
    private SanityPenaltyHandler sanityPenaltyHandler;
    private PassiveAbilityManager passiveAbilityManager;
    private DomainEventPublisher eventPublisher;
    private TemporaryEventManager temporaryEventManager;
    private me.vangoo.domain.organizations.InstitutionRegistry institutionRegistry;

    // Infrastructure services
    private JSONBeyonderRepository jsonBeyonderRepository;
    private BatchedBeyonderRepository beyonderRepository;
    private AbilityItemFactory abilityItemFactory;
    private PotionItemFactory potionItemFactory;
    private RecipeBookFactory recipeBookFactory;
    private IRecipeUnlockRepository recipeUnlockRepository;
    private RecipeUnlockService recipeUnlockService;
    private PotionCraftingService potionCraftingService;
    private CustomItemFactory customItemFactory;
    private CustomItemRegistry customItemRegistry;
    private CustomItemService customItemService;
    private LootTableConfigLoader lootTableConfigLoader;
    private LootGenerationService lootGenerationService;
    private CharacteristicCodec characteristicCodec;
    private CurrencyCodec currencyCodec;
    private WalletService walletService;
    private me.vangoo.infrastructure.market.MarketConfig marketConfig;
    private MarketItemClassifier marketItemClassifier;
    private Map<String, Map<Integer, RecipeDefinition>> potionRecipeConfig;
    private GatheringSnapshotRepository gatheringSnapshotRepository;
    private me.vangoo.infrastructure.market.GatheringVenueProvider gatheringVenueProvider;
    private me.vangoo.infrastructure.market.GatheringAnonymizer gatheringAnonymizer;
    private OrganizerNpcService organizerNpcService;
    private me.vangoo.infrastructure.organizations.ChurchConfig churchConfig;
    private me.vangoo.infrastructure.organizations.JSONMembershipRepository membershipRepository;
    private me.vangoo.infrastructure.organizations.ChurchSiteRepository churchSiteRepository;
    private me.vangoo.infrastructure.organizations.ChurchStateRepository churchStateRepository;
    private me.vangoo.infrastructure.organizations.OrderConfig orderConfig;
    private me.vangoo.infrastructure.organizations.JSONOrderMembershipRepository orderMembershipRepository;
    private me.vangoo.infrastructure.organizations.OrderStateRepository orderStateRepository;
    private me.vangoo.infrastructure.items.OrderItems orderItems;

    // UI and menus
    private AbilityMenu abilityMenu;
    private BossBarUtil bossBarUtil;
    private me.vangoo.infrastructure.ui.MarketMenu marketMenu;
    private me.vangoo.infrastructure.ui.ConfirmationMenu confirmationMenu;
    private me.vangoo.infrastructure.ui.ChurchMenu churchMenu;
    private me.vangoo.infrastructure.ui.OrderMenu orderMenu;

    // Application services
    private BeyonderService beyonderService;
    private AbilityExecutor abilityExecutor;
    private AbilityContextFactory abilityContextFactory;
    private PotionManager potionManager;
    private GatheringService gatheringService;
    private MarketItemNamer marketItemNamer;
    private me.vangoo.application.services.CreatureNamer creatureNamer;
    private ChurchService churchService;
    private me.vangoo.infrastructure.citizens.ChurchPriestService churchPriestService;
    private me.vangoo.infrastructure.organizations.ChurchStructurePlacer churchStructurePlacer;
    private me.vangoo.infrastructure.organizations.ChurchSiteService churchSiteService;
    private me.vangoo.infrastructure.organizations.DuelArenaProvider duelArenaProvider;
    private ChurchDuelService churchDuelService;
    private me.vangoo.presentation.listeners.DuelListener duelListener;
    private SecretOrderService secretOrderService;
    private me.vangoo.infrastructure.contracts.JSONContractRepository contractRepository;
    private me.vangoo.application.services.DivinePunishment divinePunishment;
    private me.vangoo.application.services.ContractService contractService;

    // Schedulers
    private PassiveAbilityScheduler passiveAbilityScheduler;
    private MasteryRegenerationScheduler masteryRegenerationScheduler;
    private RampageScheduler rampageScheduler;
    private AbilityMenuItemUpdater abilityMenuItemUpdater;
    private me.vangoo.infrastructure.schedulers.AmbientCreatureSpawner ambientCreatureSpawner;
    private me.vangoo.infrastructure.schedulers.ConvergenceDriftScheduler convergenceDriftScheduler;
    private me.vangoo.infrastructure.forage.ForageNodeCodec forageNodeCodec;
    private me.vangoo.infrastructure.schedulers.ForageNodeSpawner forageNodeSpawner;
    private GatheringScheduler gatheringScheduler;
    private me.vangoo.infrastructure.schedulers.ChurchOrderScheduler churchOrderScheduler;
    private me.vangoo.infrastructure.schedulers.OrderScheduler orderScheduler;

    // Event listeners
    private RampageEventListener rampageEventListener;
    private CharacteristicExtractor characteristicExtractor;
    private WardenRemnantCodec wardenRemnantCodec;
    private RampageRemnantDeathListener rampageRemnantDeathListener;
    private java.util.Map<String, me.vangoo.domain.creatures.CreatureDefinition> creatureRegistry;
    private me.vangoo.domain.creatures.CreatureSelector creatureSelector;
    private me.vangoo.infrastructure.mythic.MythicCreatureGateway mythicCreatureGateway;
    private me.vangoo.presentation.listeners.CreatureDeathListener creatureDeathListener;
    private me.vangoo.presentation.listeners.NaturalCreatureSpawnListener naturalCreatureSpawnListener;
    private me.vangoo.presentation.listeners.StructureCreatureSpawnListener structureCreatureSpawnListener;
    private me.vangoo.presentation.listeners.CreatureDamageListener creatureDamageListener;
    private BeyonderSleepListener beyonderSleepListener;
    private MarionetteExitListener marionetteExitListener;
    private PaperThrowListener paperThrowListener;
    private MainBodyAbilityListener mainBodyAbilityListener;
    private MarionetteLifecycleListener marionetteLifecycleListener;
    private MarionetteRestorer marionetteRestorer;
    private me.vangoo.presentation.listeners.GatheringListener gatheringListener;
    private me.vangoo.presentation.listeners.OrganizerClickListener organizerClickListener;
    private me.vangoo.presentation.listeners.CurrencyExchangeListener currencyExchangeListener;

    // Recipes
    private RecipeBookCraftingRecipe recipeBookCraftingRecipe;

    public ServiceContainer(JavaPlugin plugin, fr.skytasul.glowingentities.GlowingEntities glowingEntities,
                            de.slikey.effectlib.EffectManager effectManager) {
        this.plugin = plugin;

        // Initialize core services
        initializeCoreServices();

        // Initialize infrastructure
        initializeInfrastructure();

        // Initialize application services with external dependencies
        initializeApplicationServices(glowingEntities, effectManager);

        // Initialize UI
        initializeUI();

        // Initialize schedulers
        initializeSchedulers();

        // Initialize event listeners
        initializeEventListeners();

        // Initialize recipes
        initializeRecipes();
    }

    private void initializeCoreServices() {
        this.pathwayManager = new PathwayManager();
        this.cooldownManager = new CooldownManager();
        this.abilityLockManager = new AbilityLockManager();
        this.eventPublisher = new DomainEventPublisher();
        this.rampageManager = new RampageManager(eventPublisher);
        this.amplificationManager = new AmplificationManager();
        this.sanityPenaltyHandler = new SanityPenaltyHandler();
        this.passiveAbilityManager = new PassiveAbilityManager();
        this.temporaryEventManager = new TemporaryEventManager(plugin);
        this.institutionRegistry = new me.vangoo.domain.organizations.InstitutionRegistry();
    }

    private void initializeInfrastructure() {
        // Repository with batching (save every 5 minutes = 6000 ticks)
        String beyonderPath = plugin.getDataFolder() + File.separator + "beyonders.json";
        this.jsonBeyonderRepository = new JSONBeyonderRepository(beyonderPath, pathwayManager);
        this.beyonderRepository = new BatchedBeyonderRepository(jsonBeyonderRepository, 6000);

        // Factories
        this.abilityItemFactory = new AbilityItemFactory();
        this.potionItemFactory = new PotionItemFactory();
        this.recipeBookFactory = new RecipeBookFactory();

        // Recipe system
        String recipeUnlocksPath = plugin.getDataFolder() + File.separator + "recipe_unlocks.json";
        this.recipeUnlockRepository = new JSONRecipeUnlockRepository(recipeUnlocksPath);
        this.recipeUnlockService = new RecipeUnlockService(recipeUnlockRepository);

        // Custom items
        this.customItemFactory = new CustomItemFactory();
        this.customItemRegistry = new CustomItemRegistry(customItemFactory);
        CustomItemConfigLoader configLoader = new CustomItemConfigLoader(plugin);
        Map<String, CustomItem> items = configLoader.loadItems();
        customItemRegistry.registerAll(items);
        this.customItemService = new CustomItemService(customItemRegistry);

        // Initialize potion manager first (needed for loot service)
        PotionRecipeConfigLoader recipeConfigLoader = new PotionRecipeConfigLoader(plugin);
        Map<String, Map<Integer, RecipeDefinition>> recipeConfig = recipeConfigLoader.load();
        this.potionRecipeConfig = recipeConfig;
        this.characteristicCodec = new CharacteristicCodec();
        this.currencyCodec = new CurrencyCodec();
        this.walletService = new WalletService(currencyCodec);
        this.marketConfig = me.vangoo.infrastructure.market.MarketConfig.load(plugin);
        this.marketItemClassifier = new MarketItemClassifier(
                characteristicCodec, recipeBookFactory, customItemService, currencyCodec);
        this.gatheringSnapshotRepository = new GatheringSnapshotRepository(
                plugin.getDataFolder() + File.separator + "gathering-state.json");
        this.gatheringVenueProvider = new me.vangoo.infrastructure.market.GatheringVenueProvider();
        this.gatheringAnonymizer = new me.vangoo.infrastructure.market.GatheringAnonymizer();
        this.organizerNpcService = new OrganizerNpcService();
        this.characteristicExtractor = new CharacteristicExtractor(characteristicCodec);
        this.wardenRemnantCodec = new WardenRemnantCodec(plugin);
        this.potionManager = new PotionManager(pathwayManager, potionItemFactory, customItemService, recipeConfig);
        // Ціна скупки інгредієнтів скейлиться від послідовності рецепта, де вони вжиті:
        // будуємо індекс itemKey→послідовність із потонів (створених щойно вище) й інжектимо
        // в класифікатор (він створений раніше, тож лише сеттером).
        this.marketItemClassifier.setIngredientSequenceIndex(
                IngredientSequenceIndex.build(potionManager.getPotions(), customItemService));

        // Loot system
        this.lootTableConfigLoader = new LootTableConfigLoader(plugin);
        lootTableConfigLoader.loadLootTable();
        this.lootGenerationService = new LootGenerationService(
                plugin, customItemService, potionManager, recipeBookFactory, currencyCodec);

        // --- Спек 6b: церкви ---
        this.churchConfig = me.vangoo.infrastructure.organizations.ChurchConfig.load(plugin);
        this.membershipRepository = new me.vangoo.infrastructure.organizations.JSONMembershipRepository(
                plugin.getDataFolder() + File.separator + "memberships.json");
        this.churchSiteRepository = new me.vangoo.infrastructure.organizations.ChurchSiteRepository(
                plugin.getDataFolder() + File.separator + "church-sites.json");
        this.churchStateRepository = new me.vangoo.infrastructure.organizations.ChurchStateRepository(
                plugin.getDataFolder() + File.separator + "churches-state.json");

        // --- Спек 6c: таємні організації ---
        this.orderConfig = me.vangoo.infrastructure.organizations.OrderConfig.load(plugin);
        this.orderMembershipRepository = new me.vangoo.infrastructure.organizations.JSONOrderMembershipRepository(
                plugin.getDataFolder() + File.separator + "order-memberships.json");
        this.orderStateRepository = new me.vangoo.infrastructure.organizations.OrderStateRepository(
                plugin.getDataFolder() + File.separator + "orders-state.json");
        this.orderItems = new me.vangoo.infrastructure.items.OrderItems(customItemService);

        // --- Sun: контракти ---
        this.contractRepository = new me.vangoo.infrastructure.contracts.JSONContractRepository(
                plugin.getDataFolder() + File.separator + "contracts.json");
    }

    private void initializeApplicationServices(fr.skytasul.glowingentities.GlowingEntities glowingEntities,
                                               de.slikey.effectlib.EffectManager effectManager) {
        this.bossBarUtil = new BossBarUtil();
        this.beyonderService = new BeyonderService(beyonderRepository, bossBarUtil);

        // --- Спек 3: полювання (істоти) ---
        me.vangoo.infrastructure.creatures.CreatureConfigLoader creatureConfigLoader =
                new me.vangoo.infrastructure.creatures.CreatureConfigLoader(plugin);
        this.creatureRegistry = java.util.Collections.unmodifiableMap(creatureConfigLoader.load());
        this.creatureSelector = new me.vangoo.domain.creatures.CreatureSelector(creatureRegistry.values());
        this.forageNodeCodec = new me.vangoo.infrastructure.forage.ForageNodeCodec(plugin);
        this.mythicCreatureGateway = new me.vangoo.infrastructure.mythic.MythicCreatureGateway(plugin);
        this.creatureDeathListener = new me.vangoo.presentation.listeners.CreatureDeathListener(
                mythicCreatureGateway, creatureRegistry, lootGenerationService, beyonderService);
        double minSpawnDistance = plugin.getConfig().getDouble("creatures.min-spawn-distance", 2000.0);
        this.naturalCreatureSpawnListener = new me.vangoo.presentation.listeners.NaturalCreatureSpawnListener(
                creatureSelector, mythicCreatureGateway, minSpawnDistance, beyonderService);
        this.structureCreatureSpawnListener = new me.vangoo.presentation.listeners.StructureCreatureSpawnListener(
                creatureSelector, mythicCreatureGateway, minSpawnDistance);
        this.creatureDamageListener = new me.vangoo.presentation.listeners.CreatureDamageListener(
                mythicCreatureGateway, creatureRegistry, beyonderService);

        me.vangoo.domain.abilities.context.IVisualEffectsContext punishmentEffects =
                new me.vangoo.application.services.context.VisualEffectsContext(
                        effectManager, (MysteriesAbovePlugin) plugin);
        this.divinePunishment = new me.vangoo.application.services.DivinePunishment(
                abilityLockManager, punishmentEffects);
        this.contractService = new me.vangoo.application.services.ContractService(
                contractRepository, divinePunishment);

        this.abilityContextFactory = new AbilityContextFactory(
                (MysteriesAbovePlugin) plugin,
                cooldownManager,
                beyonderService,
                abilityLockManager,
                glowingEntities,
                effectManager,
                rampageManager,
                temporaryEventManager,
                passiveAbilityManager,
                eventPublisher,
                recipeUnlockService,
                potionManager,
                contractService,
                amplificationManager
        );

        this.abilityExecutor = new AbilityExecutor(
                beyonderService,
                abilityLockManager,
                rampageManager,
                passiveAbilityManager,
                abilityContextFactory,
                sanityPenaltyHandler,
                eventPublisher
        );

        this.potionCraftingService = new PotionCraftingService(
                potionManager,
                recipeUnlockService,
                customItemService,
                characteristicCodec
        );

        this.marketItemNamer = new MarketItemNamer(customItemService);
        // Назви істот резолвить пак MythicMobs, біоми — creatureRegistry (обидва вже готові).
        this.creatureNamer = new me.vangoo.application.services.CreatureNamer(
                mythicCreatureGateway, creatureRegistry);
        this.gatheringService = new GatheringService(plugin, marketConfig, walletService,
                marketItemClassifier, gatheringVenueProvider, gatheringAnonymizer,
                gatheringSnapshotRepository, organizerNpcService, beyonderService,
                recipeUnlockService, potionManager, marketItemNamer);
        this.abilityExecutor.setGatheringAbilityGuard(gatheringService);

        // --- Спек 6b: церкви ---
        this.churchService = new ChurchService(plugin, churchConfig, institutionRegistry,
                beyonderService, pathwayManager, potionManager, potionRecipeConfig,
                recipeUnlockService, marketItemClassifier, marketItemNamer, creatureNamer, walletService,
                creatureRegistry, membershipRepository, churchStateRepository);
        this.churchPriestService = new me.vangoo.infrastructure.citizens.ChurchPriestService(institutionRegistry);
        this.churchStructurePlacer = new me.vangoo.infrastructure.organizations.ChurchStructurePlacer(plugin);
        this.churchSiteService = new me.vangoo.infrastructure.organizations.ChurchSiteService(
                churchSiteRepository, churchPriestService, churchStructurePlacer, churchService);

        // --- Спек 6c: таємні організації ---
        this.secretOrderService = new SecretOrderService(plugin, orderConfig, institutionRegistry,
                beyonderService, pathwayManager, recipeUnlockService, customItemService,
                characteristicCodec, marketItemClassifier, marketItemNamer, creatureNamer,
                creatureRegistry, potionRecipeConfig, churchService, churchSiteService,
                mythicCreatureGateway, potionManager, recipeBookFactory,
                orderItems, orderMembershipRepository, orderStateRepository, churchPriestService);
        churchSiteService.setPriestClosurePredicate(secretOrderService::isTempleClosed);
        churchService.setFalsePapersCheck(secretOrderService::consumeFalsePapersIfActive);
    }

    private void initializeUI() {
        this.confirmationMenu = new me.vangoo.infrastructure.ui.ConfirmationMenu(plugin);

        // OrderMenu будується ПЕРЕД AbilityMenu: вкладка ордену в меню здібностей відкриває
        // його напряму, тож він мусить існувати на момент конструювання AbilityMenu.
        this.orderMenu = new me.vangoo.infrastructure.ui.OrderMenu(
                plugin, secretOrderService, marketItemNamer, creatureNamer, confirmationMenu, orderItems);

        this.abilityMenu = new AbilityMenu(
                (MysteriesAbovePlugin) plugin,
                abilityItemFactory,
                recipeUnlockService,
                potionManager,
                abilityExecutor,
                pathwayManager,
                abilityContextFactory,
                secretOrderService,
                orderMenu
        );

        this.marketMenu = new me.vangoo.infrastructure.ui.MarketMenu(
                plugin, gatheringService, walletService, marketItemNamer, confirmationMenu);
        this.churchMenu = new me.vangoo.infrastructure.ui.ChurchMenu(
                plugin, churchService, marketItemNamer, creatureNamer, confirmationMenu);

        this.duelArenaProvider = new me.vangoo.infrastructure.organizations.DuelArenaProvider();
        this.churchDuelService = new ChurchDuelService(plugin, churchService, pathwayManager,
                mythicCreatureGateway, duelArenaProvider, creatureRegistry);
        this.churchMenu.setDuelService(churchDuelService);
        this.churchDuelService.setTrialChoiceOpener(churchMenu::openTrialPathwayChoice);
        this.duelListener = new me.vangoo.presentation.listeners.DuelListener(
                churchDuelService);
    }

    private void initializeSchedulers() {
        this.passiveAbilityScheduler = new PassiveAbilityScheduler(
                (MysteriesAbovePlugin) plugin,
                passiveAbilityManager,
                beyonderService,
                abilityContextFactory
        );

        this.masteryRegenerationScheduler = new MasteryRegenerationScheduler(
                plugin,
                beyonderService
        );

        this.rampageScheduler = new RampageScheduler(plugin, rampageManager);

        double ambientMinDistance = plugin.getConfig().getDouble("creatures.min-spawn-distance", 2000.0);
        long ambientInterval = plugin.getConfig().getLong("creatures.ambient.interval-seconds", 60L);
        double ambientChance = plugin.getConfig().getDouble("creatures.ambient.chance", 0.022);
        int ambientMaxNearby = plugin.getConfig().getInt("creatures.ambient.max-nearby", 3);
        this.ambientCreatureSpawner = new me.vangoo.infrastructure.schedulers.AmbientCreatureSpawner(
                (MysteriesAbovePlugin) plugin, beyonderService, creatureSelector, mythicCreatureGateway,
                creatureRegistry, ambientMinDistance, ambientInterval, ambientChance, ambientMaxNearby);

        long convInterval = plugin.getConfig().getLong("convergence.interval-ticks", 200L);
        double convRadius = plugin.getConfig().getDouble("convergence.radius", 128.0);
        double convDrift = plugin.getConfig().getDouble("convergence.item-drift-speed", 0.08);
        double convMobNudge = plugin.getConfig().getDouble("convergence.mob-nudge-chance", 0.5);
        double convWhisper = plugin.getConfig().getDouble("convergence.whisper-chance", 0.03);
        this.convergenceDriftScheduler = new me.vangoo.infrastructure.schedulers.ConvergenceDriftScheduler(
                (MysteriesAbovePlugin) plugin, beyonderService, pathwayManager, mythicCreatureGateway,
                creatureRegistry, characteristicCodec, wardenRemnantCodec,
                convInterval, convRadius, convDrift, convMobNudge, convWhisper);

        me.vangoo.infrastructure.forage.ForageConfigLoader forageConfigLoader =
                new me.vangoo.infrastructure.forage.ForageConfigLoader(plugin);
        me.vangoo.infrastructure.forage.ForageConfig forageConfig = forageConfigLoader.load();
        me.vangoo.domain.forage.ForageSelector forageSelector =
                new me.vangoo.domain.forage.ForageSelector(forageConfig.biomes());
        this.forageNodeSpawner = new me.vangoo.infrastructure.schedulers.ForageNodeSpawner(
                (MysteriesAbovePlugin) plugin, forageSelector, forageNodeCodec, forageConfig);

        this.abilityMenuItemUpdater = new AbilityMenuItemUpdater(
                plugin,
                beyonderService,
                abilityMenu
        );

        this.gatheringScheduler = new GatheringScheduler(plugin, gatheringService);
        this.churchOrderScheduler = new me.vangoo.infrastructure.schedulers.ChurchOrderScheduler(plugin, churchService);
        this.orderScheduler = new me.vangoo.infrastructure.schedulers.OrderScheduler(plugin, secretOrderService);
    }

    private void initializeEventListeners() {
        this.rampageEventListener = new RampageEventListener(passiveAbilityScheduler, beyonderService, wardenRemnantCodec);
        this.rampageRemnantDeathListener = new RampageRemnantDeathListener(wardenRemnantCodec, characteristicExtractor);
        this.beyonderSleepListener = new BeyonderSleepListener(beyonderService);
        this.marionetteExitListener = new MarionetteExitListener(abilityContextFactory, pathwayManager);
        this.paperThrowListener = new PaperThrowListener(abilityContextFactory, beyonderService, customItemService);
        this.mainBodyAbilityListener = new MainBodyAbilityListener(beyonderService, abilityExecutor, pathwayManager);
        this.marionetteLifecycleListener = new MarionetteLifecycleListener(abilityContextFactory, pathwayManager, characteristicExtractor);
        this.marionetteRestorer = new MarionetteRestorer(pathwayManager);
        this.gatheringListener = new me.vangoo.presentation.listeners.GatheringListener(
                plugin, gatheringService, gatheringVenueProvider, marketMenu);
        this.organizerClickListener = new me.vangoo.presentation.listeners.OrganizerClickListener(
                organizerNpcService, gatheringService, confirmationMenu);
        this.currencyExchangeListener = new me.vangoo.presentation.listeners.CurrencyExchangeListener(
                currencyCodec, confirmationMenu);
    }

    private void initializeRecipes() {
        this.recipeBookCraftingRecipe = new RecipeBookCraftingRecipe(plugin, customItemService);
    }

    // Getters for all services
    public PathwayManager getPathwayManager() { return pathwayManager; }
    public CooldownManager getCooldownManager() { return cooldownManager; }
    public AbilityLockManager getAbilityLockManager() { return abilityLockManager; }
    public RampageManager getRampageManager() { return rampageManager; }
    public SanityPenaltyHandler getSanityPenaltyHandler() { return sanityPenaltyHandler; }
    public PassiveAbilityManager getPassiveAbilityManager() { return passiveAbilityManager; }
    public DomainEventPublisher getEventPublisher() { return eventPublisher; }
    public TemporaryEventManager getTemporaryEventManager() { return temporaryEventManager; }

    public IBeyonderRepository getBeyonderRepository() { return beyonderRepository; }
    public BatchedBeyonderRepository getBatchedBeyonderRepository() { return beyonderRepository; }
    public AbilityItemFactory getAbilityItemFactory() { return abilityItemFactory; }
    public PotionItemFactory getPotionItemFactory() { return potionItemFactory; }
    public RecipeBookFactory getRecipeBookFactory() { return recipeBookFactory; }
    public IRecipeUnlockRepository getRecipeUnlockRepository() { return recipeUnlockRepository; }
    public RecipeUnlockService getRecipeUnlockService() { return recipeUnlockService; }
    public PotionCraftingService getPotionCraftingService() { return potionCraftingService; }
    public CustomItemFactory getCustomItemFactory() { return customItemFactory; }
    public CustomItemRegistry getCustomItemRegistry() { return customItemRegistry; }
    public CustomItemService getCustomItemService() { return customItemService; }
    public LootTableConfigLoader getLootTableConfigLoader() { return lootTableConfigLoader; }
    public LootGenerationService getLootGenerationService() { return lootGenerationService; }
    public CharacteristicCodec getCharacteristicCodec() { return characteristicCodec; }
    public CurrencyCodec getCurrencyCodec() { return currencyCodec; }
    public WalletService getWalletService() { return walletService; }
    public me.vangoo.infrastructure.market.MarketConfig getMarketConfig() { return marketConfig; }
    public MarketItemClassifier getMarketItemClassifier() { return marketItemClassifier; }
    public Map<String, Map<Integer, RecipeDefinition>> getPotionRecipeConfig() { return potionRecipeConfig; }
    public GatheringSnapshotRepository getGatheringSnapshotRepository() { return gatheringSnapshotRepository; }
    public me.vangoo.infrastructure.market.GatheringVenueProvider getGatheringVenueProvider() { return gatheringVenueProvider; }
    public me.vangoo.infrastructure.market.GatheringAnonymizer getGatheringAnonymizer() { return gatheringAnonymizer; }
    public OrganizerNpcService getOrganizerNpcService() { return organizerNpcService; }
    public me.vangoo.infrastructure.organizations.ChurchConfig getChurchConfig() { return churchConfig; }

    public AbilityMenu getAbilityMenu() { return abilityMenu; }
    public BossBarUtil getBossBarUtil() { return bossBarUtil; }
    public me.vangoo.infrastructure.ui.MarketMenu getMarketMenu() { return marketMenu; }
    public me.vangoo.infrastructure.ui.ConfirmationMenu getConfirmationMenu() { return confirmationMenu; }
    public me.vangoo.infrastructure.ui.ChurchMenu getChurchMenu() { return churchMenu; }

    public BeyonderService getBeyonderService() { return beyonderService; }
    public AbilityExecutor getAbilityExecutor() { return abilityExecutor; }
    public AbilityContextFactory getAbilityContextFactory() { return abilityContextFactory; }
    public PotionManager getPotionManager() { return potionManager; }
    public GatheringService getGatheringService() { return gatheringService; }
    public ChurchService getChurchService() { return churchService; }
    public me.vangoo.infrastructure.citizens.ChurchPriestService getChurchPriestService() { return churchPriestService; }
    public me.vangoo.infrastructure.organizations.ChurchSiteService getChurchSiteService() { return churchSiteService; }
    public ChurchDuelService getChurchDuelService() { return churchDuelService; }
    public me.vangoo.presentation.listeners.DuelListener getDuelListener() { return duelListener; }
    public SecretOrderService getSecretOrderService() { return secretOrderService; }
    public me.vangoo.infrastructure.ui.OrderMenu getOrderMenu() { return orderMenu; }
    public me.vangoo.infrastructure.items.OrderItems getOrderItems() { return orderItems; }
    public me.vangoo.application.services.ContractService getContractService() { return contractService; }

    public PassiveAbilityScheduler getPassiveAbilityScheduler() { return passiveAbilityScheduler; }
    public MasteryRegenerationScheduler getMasteryRegenerationScheduler() { return masteryRegenerationScheduler; }
    public RampageScheduler getRampageScheduler() { return rampageScheduler; }
    public AbilityMenuItemUpdater getAbilityMenuItemUpdater() { return abilityMenuItemUpdater; }
    public me.vangoo.infrastructure.schedulers.AmbientCreatureSpawner getAmbientCreatureSpawner() { return ambientCreatureSpawner; }
    public me.vangoo.infrastructure.schedulers.ConvergenceDriftScheduler getConvergenceDriftScheduler() { return convergenceDriftScheduler; }
    public me.vangoo.infrastructure.forage.ForageNodeCodec getForageNodeCodec() { return forageNodeCodec; }
    public me.vangoo.infrastructure.schedulers.ForageNodeSpawner getForageNodeSpawner() { return forageNodeSpawner; }
    public GatheringScheduler getGatheringScheduler() { return gatheringScheduler; }
    public me.vangoo.infrastructure.schedulers.ChurchOrderScheduler getChurchOrderScheduler() { return churchOrderScheduler; }
    public me.vangoo.infrastructure.schedulers.OrderScheduler getOrderScheduler() { return orderScheduler; }

    public RampageEventListener getRampageEventListener() { return rampageEventListener; }
    public CharacteristicExtractor getCharacteristicExtractor() { return characteristicExtractor; }
    public WardenRemnantCodec getWardenRemnantCodec() { return wardenRemnantCodec; }
    public RampageRemnantDeathListener getRampageRemnantDeathListener() { return rampageRemnantDeathListener; }
    public me.vangoo.infrastructure.mythic.MythicCreatureGateway getMythicCreatureGateway() { return mythicCreatureGateway; }
    public java.util.Map<String, me.vangoo.domain.creatures.CreatureDefinition> getCreatureRegistry() { return creatureRegistry; }
    public me.vangoo.presentation.listeners.CreatureDeathListener getCreatureDeathListener() { return creatureDeathListener; }
    public me.vangoo.presentation.listeners.NaturalCreatureSpawnListener getNaturalCreatureSpawnListener() { return naturalCreatureSpawnListener; }
    public me.vangoo.presentation.listeners.StructureCreatureSpawnListener getStructureCreatureSpawnListener() { return structureCreatureSpawnListener; }
    public me.vangoo.presentation.listeners.CreatureDamageListener getCreatureDamageListener() { return creatureDamageListener; }
    public BeyonderSleepListener getBeyonderSleepListener() { return beyonderSleepListener; }
    public MarionetteExitListener getMarionetteExitListener() { return marionetteExitListener; }
    public PaperThrowListener getPaperThrowListener() { return paperThrowListener; }
    public MainBodyAbilityListener getMainBodyAbilityListener() { return mainBodyAbilityListener; }
    public MarionetteLifecycleListener getMarionetteLifecycleListener() { return marionetteLifecycleListener; }
    public MarionetteRestorer getMarionetteRestorer() { return marionetteRestorer; }
    public me.vangoo.presentation.listeners.GatheringListener getGatheringListener() { return gatheringListener; }
    public me.vangoo.presentation.listeners.OrganizerClickListener getOrganizerClickListener() { return organizerClickListener; }
    public me.vangoo.presentation.listeners.CurrencyExchangeListener getCurrencyExchangeListener() { return currencyExchangeListener; }

    public RecipeBookCraftingRecipe getRecipeBookCraftingRecipe() { return recipeBookCraftingRecipe; }


    /**
     * Start all schedulers
     */
    public void startSchedulers() {
        passiveAbilityScheduler.start();
        masteryRegenerationScheduler.start();
        rampageScheduler.start();
        abilityMenuItemUpdater.start();
        ambientCreatureSpawner.start();
        convergenceDriftScheduler.start();
        forageNodeSpawner.start();
        gatheringScheduler.start();
        churchOrderScheduler.start();
        orderScheduler.start();

        // Start batched save scheduler (every 5 minutes)
        startBatchedSaveScheduler();
    }

    private void startBatchedSaveScheduler() {
        plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                () -> beyonderRepository.saveDirtyPlayers(),
                6000L, // Initial delay: 5 minutes
                6000L  // Repeat every 5 minutes
        );
    }

    /**
     * Stop all schedulers
     */
    public void stopSchedulers() {
        if (passiveAbilityScheduler != null) {
            passiveAbilityScheduler.stop();
        }
        if (masteryRegenerationScheduler != null) {
            masteryRegenerationScheduler.stop();
        }
        if (rampageScheduler != null) {
            rampageScheduler.stop();
        }
        if (abilityMenuItemUpdater != null) {
            abilityMenuItemUpdater.stop();
        }
        if (ambientCreatureSpawner != null) {
            ambientCreatureSpawner.stop();
        }
        if (convergenceDriftScheduler != null) {
            convergenceDriftScheduler.stop();
        }
        if (forageNodeSpawner != null) {
            forageNodeSpawner.stop();
        }
        if (gatheringScheduler != null) {
            gatheringScheduler.stop();
        }
        if (churchOrderScheduler != null) {
            churchOrderScheduler.stop();
        }
        if (orderScheduler != null) {
            orderScheduler.stop();
        }
    }

    /**
     * Save all persistent data
     */
    public void saveAll() {
        beyonderRepository.saveAll();
        recipeUnlockRepository.saveAll();
    }

    /**
     * Clean up all resources
     */
    public void cleanup() {
        // Force save all pending changes before shutdown
        beyonderRepository.saveAll();
        beyonderRepository.getAll().forEach(((uuid, beyonder) -> beyonder.cleanUpAbilities()));
    }
}