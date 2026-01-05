package me.vangoo;

import de.slikey.effectlib.EffectManager;
import fr.skytasul.glowingentities.GlowingEntities;
import me.vangoo.application.services.*;
import me.vangoo.application.services.RampageManager;
import me.vangoo.domain.valueobjects.CustomItem;
import me.vangoo.infrastructure.IRecipeUnlockRepository;
import me.vangoo.infrastructure.JSONRecipeUnlockRepository;
import me.vangoo.infrastructure.items.*;
import me.vangoo.infrastructure.listeners.RampageEventListener;
import me.vangoo.infrastructure.recipes.RecipeBookCraftingRecipe;
import me.vangoo.infrastructure.schedulers.AbilityMenuItemUpdater;
import me.vangoo.infrastructure.schedulers.MasteryRegenerationScheduler;
import me.vangoo.infrastructure.schedulers.PassiveAbilityScheduler;
import me.vangoo.infrastructure.schedulers.RampageScheduler;
import me.vangoo.infrastructure.structures.LootGenerationService;
import me.vangoo.infrastructure.structures.NBTStructureConfigLoader;
import me.vangoo.infrastructure.structures.StructurePopulator;
import me.vangoo.presentation.commands.*;
import me.vangoo.infrastructure.IBeyonderRepository;
import me.vangoo.infrastructure.JSONBeyonderRepository;
import me.vangoo.infrastructure.abilities.AbilityItemFactory;
import me.vangoo.presentation.listeners.*;
import me.vangoo.infrastructure.ui.AbilityMenu;
import me.vangoo.infrastructure.ui.BossBarUtil;
import me.vangoo.infrastructure.ui.NBTBuilder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;
import java.util.logging.Logger;

public class MysteriesAbovePlugin extends JavaPlugin {
    Logger pluginLogger;
    AbilityMenu abilityMenu;
    CooldownManager cooldownManager;
    AbilityLockManager lockManager;
    PathwayManager pathwayManager;
    PotionManager potionManager;
    BeyonderService beyonderService;
    GlowingEntities glowingEntities;
    AbilityExecutor abilityExecutor;
    EffectManager effectManager;
    IBeyonderRepository beyonderStorage;
    AbilityItemFactory abilityItemFactory;
    PassiveAbilityManager passiveAbilityManager;
    PassiveAbilityScheduler passiveAbilityScheduler;
    MasteryRegenerationScheduler masteryRegenerationScheduler;
    RampageScheduler rampageScheduler;
    BeyonderSleepListener beyonderSleepListener;
    RampageManager rampageManager;
    AbilityContextFactory abilityContextFactory;
    PotionItemFactory potionItemFactory;
    DomainEventPublisher eventPublisher;
    SanityPenaltyHandler sanityPenaltyHandler;
    RampageEventListener rampageEventListener;
    TemporaryEventManager temporaryEventManager;
    CustomItemFactory customItemFactory;
    CustomItemRegistry customItemRegistry;
    CustomItemService customItemService;
    NBTStructureConfigLoader configLoader;
    LootGenerationService lootService;
    StructurePopulator structurePopulator;
    RecipeBookFactory recipeBookFactory;
    IRecipeUnlockRepository recipeUnlockRepository;
    RecipeUnlockService recipeUnlockService;
    PotionCraftingService potionCraftingService;
    RecipeBookCraftingRecipe recipeBookCraftingRecipe;
    AbilityMenuItemUpdater abilityMenuItemUpdater;

    @Override
    public void onEnable() {
        this.pluginLogger = this.getLogger();
        glowingEntities = new GlowingEntities(this);
        initializeManagers();
        registerEvents();
        registerCommands();

        startSchedulers();
        eventPublisher.subscribeToAbility(ev -> {
            getLogger().info("[GLOBAL-SUB] Got ability event: " + ev.abilityName() + " from " + ev.casterId());
        });
    }

    @Override
    public void onDisable() {
        stopSchedulers();
        if (effectManager != null) {
            effectManager.dispose();
        }
        glowingEntities.disable();
        beyonderStorage.saveAll();
        beyonderStorage.getAll().forEach(((uuid, beyonder) -> beyonder.cleanUpAbilities()));
        recipeUnlockRepository.saveAll();

        super.onDisable();
    }

    private void initializeManagers() {
        NBTBuilder.setPlugin(this);
        initializeCustomItems();
        this.temporaryEventManager = new TemporaryEventManager(this);
        this.eventPublisher = new DomainEventPublisher();
        this.rampageManager = new RampageManager(eventPublisher);
        this.sanityPenaltyHandler = new SanityPenaltyHandler();
        this.cooldownManager = new CooldownManager();
        this.rampageScheduler = new RampageScheduler(this, rampageManager);
        this.abilityItemFactory = new AbilityItemFactory();

        this.pathwayManager = new PathwayManager();

        this.beyonderStorage =
                new JSONBeyonderRepository(
                        this.getDataFolder() + File.separator + "beyonders.json",
                        pathwayManager
                );

        this.beyonderService = new BeyonderService(beyonderStorage, new BossBarUtil());
        this.lockManager = new AbilityLockManager();
        this.effectManager = new EffectManager(this);
        this.passiveAbilityManager = new PassiveAbilityManager();

        this.abilityContextFactory = new AbilityContextFactory(this, cooldownManager, beyonderService, lockManager,
                glowingEntities, effectManager, rampageManager, temporaryEventManager, passiveAbilityManager, eventPublisher);

        this.passiveAbilityScheduler = new PassiveAbilityScheduler(
                this,
                passiveAbilityManager,
                beyonderService,
                abilityContextFactory
        );
        this.rampageEventListener = new RampageEventListener(passiveAbilityScheduler, beyonderService);
        eventPublisher.subscribeToRampage(rampageEventListener::handle);

        this.abilityExecutor = new AbilityExecutor(
                beyonderService,
                lockManager,
                rampageManager,
                passiveAbilityManager,
                abilityContextFactory,
                sanityPenaltyHandler,
                eventPublisher
        );

        this.potionItemFactory = new PotionItemFactory();
        this.potionManager = new PotionManager(pathwayManager, potionItemFactory, customItemService);
        initializeRecipeSystem();
        this.abilityMenu = new AbilityMenu(this, abilityItemFactory, recipeUnlockService, potionManager, abilityExecutor);
        this.abilityMenuItemUpdater = new AbilityMenuItemUpdater(this, beyonderService, abilityMenu);
        this.recipeBookCraftingRecipe = new RecipeBookCraftingRecipe(this, customItemService);
        this.recipeBookCraftingRecipe.registerRecipe();
        configLoader = new NBTStructureConfigLoader(this);
        lootService = new LootGenerationService(this, customItemService, potionManager, recipeBookFactory);
        structurePopulator = new StructurePopulator(this, configLoader, lootService);
        for (World world : Bukkit.getWorlds()) {
            world.getPopulators().add(structurePopulator);
        }
        getLogger().info("Structure system initialized: " +
                structurePopulator.getStructureIds().size() + " structures");
        this.masteryRegenerationScheduler = new MasteryRegenerationScheduler(this, beyonderService);
        this.beyonderSleepListener = new BeyonderSleepListener(beyonderService);
    }

    private void registerEvents() {
        AbilityMenuListener abilityMenuListener =
                new AbilityMenuListener(abilityMenu, beyonderService, abilityItemFactory, pluginLogger);

        BeyonderPlayerListener beyonderPlayerListener =
                new BeyonderPlayerListener(beyonderService, new BossBarUtil(), abilityExecutor, abilityItemFactory, pluginLogger);

        PathwayPotionListener pathwayPotionListener =
                new PathwayPotionListener(potionManager, beyonderService, passiveAbilityScheduler);

        PassiveAbilityLifecycleListener passiveAbilityLifecycleListener =
                new PassiveAbilityLifecycleListener(this.passiveAbilityScheduler);

        RecipeBookInteractionListener recipeBookListener =
                new RecipeBookInteractionListener(recipeBookFactory, recipeUnlockService);

        PotionCraftingListener potionCraftingListener =
                new PotionCraftingListener(this, potionCraftingService);

        MasterRecipeBookListener masterRecipeBookListener = new MasterRecipeBookListener(
                this,
                customItemService,
                recipeUnlockService,
                potionManager,
                abilityMenu
        );
        getServer().getPluginManager().registerEvents(abilityMenuListener, this);
        getServer().getPluginManager().registerEvents(beyonderPlayerListener, this);
        getServer().getPluginManager().registerEvents(pathwayPotionListener, this);
        getServer().getPluginManager().registerEvents(passiveAbilityLifecycleListener, this);
        getServer().getPluginManager().registerEvents(beyonderSleepListener, this);
        getServer().getPluginManager().registerEvents(new PotionCauldronListener(this), this);
        getServer().getPluginManager().registerEvents(recipeBookListener, this);
        getServer().getPluginManager().registerEvents(potionCraftingListener, this);
        getServer().getPluginManager().registerEvents(masterRecipeBookListener, this);
    }

    private void registerCommands() {
        PathwayCommand pathwayCommand = new PathwayCommand(beyonderService, pathwayManager, abilityMenu, abilityItemFactory);
        SequencePotionCommand sequencePotionCommand = new SequencePotionCommand(potionManager);
        RampagerCommand rampagerCommand = new RampagerCommand(beyonderService);
        CustomItemCommand customItemCommand = new CustomItemCommand(customItemService);
        RecipeBookCommand recipeBookCommand = new RecipeBookCommand(
                recipeBookFactory,
                potionManager,
                recipeUnlockService
        );
        StructureCommand structureCommand = new StructureCommand(structurePopulator, lootService);
        getCommand("pathway").setExecutor(pathwayCommand);
        getCommand("pathway").setTabCompleter(pathwayCommand);
        getCommand("potion").setExecutor(sequencePotionCommand);
        getCommand("potion").setTabCompleter(sequencePotionCommand);
        getCommand("mastery").setExecutor(new MasteryCommand(beyonderService));
        getCommand("rampager").setExecutor(rampagerCommand);
        getCommand("rampager").setTabCompleter(rampagerCommand);
        getCommand("custom-items").setExecutor(customItemCommand);
        getCommand("custom-items").setTabCompleter(customItemCommand);
        getCommand("structure").setExecutor(structureCommand);
        getCommand("structure").setTabCompleter(structureCommand);
        getCommand("recipe").setExecutor(recipeBookCommand);
        getCommand("recipe").setTabCompleter(recipeBookCommand);
    }

    private void startSchedulers() {
        getServer().getScheduler().runTaskTimer(
                this,
                () -> beyonderService.regenerateAll(),
                20L,
                20L
        );
        rampageScheduler.start();
        passiveAbilityScheduler.start();
        masteryRegenerationScheduler.start();
        abilityMenuItemUpdater.start();
    }

    private void stopSchedulers() {
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
    }

    private void initializeCustomItems() {
        this.customItemFactory = new CustomItemFactory();
        this.customItemRegistry = new CustomItemRegistry(customItemFactory);
        CustomItemConfigLoader configLoader = new CustomItemConfigLoader(this);
        Map<String, CustomItem> items = configLoader.loadItems();
        customItemRegistry.registerAll(items);
        this.customItemService = new CustomItemService(customItemRegistry);
        Map<String, Object> stats = customItemService.getStatistics();
        pluginLogger.info("Custom items system initialized:");
        pluginLogger.info("  Total items: " + stats.get("totalItems"));
    }

    private void initializeRecipeSystem() {
        // Recipe book factory
        this.recipeBookFactory = new RecipeBookFactory();

        // Recipe unlock repository
        String recipeUnlocksPath = this.getDataFolder() + File.separator + "recipe_unlocks.json";
        this.recipeUnlockRepository = new JSONRecipeUnlockRepository(recipeUnlocksPath);

        // Recipe unlock service
        this.recipeUnlockService = new RecipeUnlockService(recipeUnlockRepository);

        // Potion crafting service
        this.potionCraftingService = new PotionCraftingService(
                potionManager,
                recipeUnlockService,
                customItemService
        );

        pluginLogger.info("Recipe and crafting system initialized");
    }
}