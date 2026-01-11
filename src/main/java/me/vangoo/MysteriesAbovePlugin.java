package me.vangoo;

import de.slikey.effectlib.EffectManager;
import fr.skytasul.glowingentities.GlowingEntities;
import me.vangoo.application.services.*;
import me.vangoo.infrastructure.di.ServiceContainer;
import me.vangoo.presentation.commands.*;
import me.vangoo.presentation.listeners.*;
import me.vangoo.infrastructure.ui.NBTBuilder;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

public class MysteriesAbovePlugin extends JavaPlugin {
    private Logger pluginLogger;
    private ServiceContainer services;
    private GlowingEntities glowingEntities;
    private EffectManager effectManager;

    @Override
    public void onEnable() {
        this.pluginLogger = this.getLogger();

        // Initialize NBT builder FIRST (needed by CustomItemFactory)
        NBTBuilder.setPlugin(this);

        // Initialize external dependencies FIRST (needed by ServiceContainer)
        try {
            glowingEntities = new GlowingEntities(this);
        } catch (Exception e) {
            pluginLogger.severe("Failed to initialize GlowingEntities! Make sure the dependency is installed.");
            pluginLogger.severe("Error: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        try {
            effectManager = new EffectManager(this);
        } catch (Exception e) {
            pluginLogger.severe("Failed to initialize EffectLib! Make sure the dependency is installed.");
            pluginLogger.severe("Error: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (glowingEntities == null) {
            pluginLogger.severe("GlowingEntities is null! Make sure the GlowingEntities plugin is installed and enabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize service container
        services = new ServiceContainer(this, glowingEntities, effectManager);

        // Register events and commands
        registerEvents();
        registerCommands();

        // Start schedulers
        services.startSchedulers();

        // Setup event subscriptions
        services.getEventPublisher().subscribeToAbility(ev -> {
            getLogger().info("[GLOBAL-SUB] Got ability event: " + ev.abilityName() + " from " + ev.casterId());
        });

        // Setup rampage event listener
        services.getEventPublisher().subscribeToRampage(services.getRampageEventListener()::handle);

        // Start regeneration scheduler (every second)
        startRegenerationScheduler();

        // Register recipes
        services.getRecipeBookCraftingRecipe().registerRecipe();

        // Load loot tables
        services.getLootTableConfigLoader().loadLootTable();

        pluginLogger.info("Custom items system initialized:");
        pluginLogger.info("  Total items: " + services.getCustomItemService().getStatistics().get("totalItems"));
        pluginLogger.info("Recipe and crafting system initialized");
    }

    private void startRegenerationScheduler() {
        getServer().getScheduler().runTaskTimer(
                this,
                () -> {
                    try {
                        services.getBeyonderService().regenerateAll();
                    } catch (Exception e) {
                        getLogger().severe("Error in regeneration scheduler: " + e.getMessage());
                        e.printStackTrace();
                        // Continue running - don't let one error stop the scheduler
                    }
                },
                20L,
                20L
        );
    }

    private void initializeCustomItems() {
        // Custom items are already initialized in ServiceContainer
        // This method is kept for logging purposes
    }

    @Override
    public void onDisable() {
        // Stop schedulers
        if (services != null) {
            services.stopSchedulers();
        }

        // Dispose external dependencies
        if (effectManager != null) {
            effectManager.dispose();
        }
        if (glowingEntities != null) {
            glowingEntities.disable();
        }

        // Save all data and cleanup
        if (services != null) {
            services.saveAll();
            services.cleanup();
        }

        super.onDisable();
    }


    private void registerEvents() {
        AbilityMenuListener abilityMenuListener =
                new AbilityMenuListener(services.getAbilityMenu(), services.getBeyonderService(),
                        services.getAbilityItemFactory(), pluginLogger);

        BeyonderPlayerListener beyonderPlayerListener =
                new BeyonderPlayerListener(services.getBeyonderService(), services.getBossBarUtil(),
                        services.getAbilityExecutor(), services.getAbilityItemFactory(),
                        services.getRampageManager(), pluginLogger);

        PathwayPotionListener pathwayPotionListener =
                new PathwayPotionListener(services.getPotionManager(), services.getBeyonderService(),
                        services.getPassiveAbilityScheduler());

        PassiveAbilityLifecycleListener passiveAbilityLifecycleListener =
                new PassiveAbilityLifecycleListener(services.getPassiveAbilityScheduler());

        RecipeBookInteractionListener recipeBookListener =
                new RecipeBookInteractionListener(services.getRecipeBookFactory(), services.getRecipeUnlockService());

        PotionCraftingListener potionCraftingListener =
                new PotionCraftingListener(this, services.getPotionCraftingService());

        MasterRecipeBookListener masterRecipeBookListener = new MasterRecipeBookListener(
                this,
                services.getCustomItemService(),
                services.getRecipeUnlockService(),
                services.getPotionManager(),
                services.getAbilityMenu()
        );
        VanillaStructureLootListener vanillaStructureLootListener =
                new VanillaStructureLootListener(
                        this,
                        services.getLootGenerationService(),
                        services.getLootTableConfigLoader().getGlobalLootTable()
                );
        getServer().getPluginManager().registerEvents(abilityMenuListener, this);
        getServer().getPluginManager().registerEvents(beyonderPlayerListener, this);
        getServer().getPluginManager().registerEvents(pathwayPotionListener, this);
        getServer().getPluginManager().registerEvents(passiveAbilityLifecycleListener, this);
        getServer().getPluginManager().registerEvents(services.getBeyonderSleepListener(), this);
        getServer().getPluginManager().registerEvents(new PotionCauldronListener(this), this);
        getServer().getPluginManager().registerEvents(recipeBookListener, this);
        getServer().getPluginManager().registerEvents(potionCraftingListener, this);
        getServer().getPluginManager().registerEvents(masterRecipeBookListener, this);
        getServer().getPluginManager().registerEvents(vanillaStructureLootListener, this);
    }

    private void registerCommands() {
        PathwayCommand pathwayCommand = new PathwayCommand(services.getBeyonderService(),
                services.getPathwayManager(), services.getAbilityMenu(), services.getAbilityItemFactory());
        SequencePotionCommand sequencePotionCommand = new SequencePotionCommand(services.getPotionManager());
        RampagerCommand rampagerCommand = new RampagerCommand(services.getBeyonderService());
        CustomItemCommand customItemCommand = new CustomItemCommand(services.getCustomItemService());
        RecipeBookCommand recipeBookCommand = new RecipeBookCommand(
                services.getRecipeBookFactory(),
                services.getPotionManager(),
                services.getRecipeUnlockService()
        );
        getCommand("pathway").setExecutor(pathwayCommand);
        getCommand("pathway").setTabCompleter(pathwayCommand);
        getCommand("potion").setExecutor(sequencePotionCommand);
        getCommand("potion").setTabCompleter(sequencePotionCommand);
        getCommand("mastery").setExecutor(new MasteryCommand(services.getBeyonderService()));
        getCommand("rampager").setExecutor(rampagerCommand);
        getCommand("rampager").setTabCompleter(rampagerCommand);
        getCommand("custom-items").setExecutor(customItemCommand);
        getCommand("custom-items").setTabCompleter(customItemCommand);
        getCommand("recipe").setExecutor(recipeBookCommand);
        getCommand("recipe").setTabCompleter(recipeBookCommand);
    }

}