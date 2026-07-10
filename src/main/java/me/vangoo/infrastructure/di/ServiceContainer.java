package me.vangoo.infrastructure.di;

import me.vangoo.application.services.*;
import me.vangoo.domain.valueobjects.CustomItem;
import me.vangoo.infrastructure.*;
import me.vangoo.infrastructure.abilities.AbilityItemFactory;
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
import me.vangoo.presentation.listeners.MainBodyAbilityListener;
import me.vangoo.presentation.listeners.MarionetteLifecycleListener;
import me.vangoo.presentation.listeners.MarionetteRestorer;
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
    private SanityPenaltyHandler sanityPenaltyHandler;
    private PassiveAbilityManager passiveAbilityManager;
    private DomainEventPublisher eventPublisher;
    private TemporaryEventManager temporaryEventManager;

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

    // UI and menus
    private AbilityMenu abilityMenu;
    private BossBarUtil bossBarUtil;

    // Application services
    private BeyonderService beyonderService;
    private AbilityExecutor abilityExecutor;
    private AbilityContextFactory abilityContextFactory;
    private PotionManager potionManager;

    // Schedulers
    private PassiveAbilityScheduler passiveAbilityScheduler;
    private MasteryRegenerationScheduler masteryRegenerationScheduler;
    private RampageScheduler rampageScheduler;
    private AbilityMenuItemUpdater abilityMenuItemUpdater;
    private me.vangoo.infrastructure.schedulers.AmbientCreatureSpawner ambientCreatureSpawner;
    private me.vangoo.infrastructure.forage.ForageNodeCodec forageNodeCodec;
    private me.vangoo.infrastructure.schedulers.ForageNodeSpawner forageNodeSpawner;

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
    private MainBodyAbilityListener mainBodyAbilityListener;
    private MarionetteLifecycleListener marionetteLifecycleListener;
    private MarionetteRestorer marionetteRestorer;

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
        this.sanityPenaltyHandler = new SanityPenaltyHandler();
        this.passiveAbilityManager = new PassiveAbilityManager();
        this.temporaryEventManager = new TemporaryEventManager(plugin);
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
        this.characteristicCodec = new CharacteristicCodec();
        this.currencyCodec = new CurrencyCodec();
        this.walletService = new WalletService(currencyCodec);
        this.characteristicExtractor = new CharacteristicExtractor(characteristicCodec);
        this.wardenRemnantCodec = new WardenRemnantCodec(plugin);
        this.potionManager = new PotionManager(pathwayManager, potionItemFactory, customItemService, recipeConfig);

        // Loot system
        this.lootTableConfigLoader = new LootTableConfigLoader(plugin);
        lootTableConfigLoader.loadLootTable();
        this.lootGenerationService = new LootGenerationService(
                plugin, customItemService, potionManager, recipeBookFactory);
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
                potionManager
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
    }

    private void initializeUI() {
        this.abilityMenu = new AbilityMenu(
                (MysteriesAbovePlugin) plugin,
                abilityItemFactory,
                recipeUnlockService,
                potionManager,
                abilityExecutor,
                pathwayManager,
                abilityContextFactory
        );
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
    }

    private void initializeEventListeners() {
        this.rampageEventListener = new RampageEventListener(passiveAbilityScheduler, beyonderService, wardenRemnantCodec);
        this.rampageRemnantDeathListener = new RampageRemnantDeathListener(wardenRemnantCodec, characteristicExtractor);
        this.beyonderSleepListener = new BeyonderSleepListener(beyonderService);
        this.marionetteExitListener = new MarionetteExitListener(abilityContextFactory, pathwayManager);
        this.mainBodyAbilityListener = new MainBodyAbilityListener(beyonderService, abilityExecutor, pathwayManager);
        this.marionetteLifecycleListener = new MarionetteLifecycleListener(abilityContextFactory, pathwayManager, characteristicExtractor);
        this.marionetteRestorer = new MarionetteRestorer(pathwayManager);
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

    public AbilityMenu getAbilityMenu() { return abilityMenu; }
    public BossBarUtil getBossBarUtil() { return bossBarUtil; }

    public BeyonderService getBeyonderService() { return beyonderService; }
    public AbilityExecutor getAbilityExecutor() { return abilityExecutor; }
    public AbilityContextFactory getAbilityContextFactory() { return abilityContextFactory; }
    public PotionManager getPotionManager() { return potionManager; }

    public PassiveAbilityScheduler getPassiveAbilityScheduler() { return passiveAbilityScheduler; }
    public MasteryRegenerationScheduler getMasteryRegenerationScheduler() { return masteryRegenerationScheduler; }
    public RampageScheduler getRampageScheduler() { return rampageScheduler; }
    public AbilityMenuItemUpdater getAbilityMenuItemUpdater() { return abilityMenuItemUpdater; }
    public me.vangoo.infrastructure.schedulers.AmbientCreatureSpawner getAmbientCreatureSpawner() { return ambientCreatureSpawner; }
    public me.vangoo.infrastructure.forage.ForageNodeCodec getForageNodeCodec() { return forageNodeCodec; }
    public me.vangoo.infrastructure.schedulers.ForageNodeSpawner getForageNodeSpawner() { return forageNodeSpawner; }

    public RampageEventListener getRampageEventListener() { return rampageEventListener; }
    public CharacteristicExtractor getCharacteristicExtractor() { return characteristicExtractor; }
    public WardenRemnantCodec getWardenRemnantCodec() { return wardenRemnantCodec; }
    public RampageRemnantDeathListener getRampageRemnantDeathListener() { return rampageRemnantDeathListener; }
    public me.vangoo.infrastructure.mythic.MythicCreatureGateway getMythicCreatureGateway() { return mythicCreatureGateway; }
    public me.vangoo.presentation.listeners.CreatureDeathListener getCreatureDeathListener() { return creatureDeathListener; }
    public me.vangoo.presentation.listeners.NaturalCreatureSpawnListener getNaturalCreatureSpawnListener() { return naturalCreatureSpawnListener; }
    public me.vangoo.presentation.listeners.StructureCreatureSpawnListener getStructureCreatureSpawnListener() { return structureCreatureSpawnListener; }
    public me.vangoo.presentation.listeners.CreatureDamageListener getCreatureDamageListener() { return creatureDamageListener; }
    public BeyonderSleepListener getBeyonderSleepListener() { return beyonderSleepListener; }
    public MarionetteExitListener getMarionetteExitListener() { return marionetteExitListener; }
    public MainBodyAbilityListener getMainBodyAbilityListener() { return mainBodyAbilityListener; }
    public MarionetteLifecycleListener getMarionetteLifecycleListener() { return marionetteLifecycleListener; }
    public MarionetteRestorer getMarionetteRestorer() { return marionetteRestorer; }

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
        forageNodeSpawner.start();

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
        if (forageNodeSpawner != null) {
            forageNodeSpawner.stop();
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