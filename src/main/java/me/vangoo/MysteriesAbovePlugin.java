package me.vangoo;

import de.slikey.effectlib.EffectManager;
import fr.skytasul.glowingentities.GlowingEntities;
import me.vangoo.application.services.*;
import me.vangoo.application.services.RampageManager;
import me.vangoo.domain.valueobjects.CustomItem;
import me.vangoo.domain.valueobjects.Structure;
import me.vangoo.infrastructure.items.CustomItemConfigLoader;
import me.vangoo.infrastructure.items.CustomItemFactory;
import me.vangoo.infrastructure.items.CustomItemRegistry;
import me.vangoo.infrastructure.items.PotionItemFactory;
import me.vangoo.infrastructure.listeners.RampageEventListener;
import me.vangoo.infrastructure.schedulers.MasteryRegenerationScheduler;
import me.vangoo.infrastructure.schedulers.PassiveAbilityScheduler;
import me.vangoo.infrastructure.schedulers.RampageScheduler;
import me.vangoo.infrastructure.structures.NBTStructureLoader;
import me.vangoo.infrastructure.structures.StructureConfigLoader;
import me.vangoo.infrastructure.structures.StructureGenerator;
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
    StructureService structureService;
    NBTStructureLoader nbtStructureLoader;
    StructureGenerator structureGenerator;
    LootGenerationService lootGenerationService;

    @Override
    public void onEnable() {
        this.pluginLogger = this.getLogger();
        glowingEntities = new GlowingEntities(this);
        initializeManagers();
        initializeStructures();
        registerEvents();
        registerCommands();

        startSchedulers();
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
        this.abilityMenu = new AbilityMenu(this, abilityItemFactory);

        this.pathwayManager = new PathwayManager();

        this.beyonderStorage =
                new JSONBeyonderRepository(
                        this.getDataFolder() + File.separator + "beyonders.json",
                        pathwayManager
                );

        this.beyonderService = new BeyonderService(beyonderStorage, new BossBarUtil());
        this.lockManager = new AbilityLockManager();
        this.effectManager = new EffectManager(this);
        this.abilityContextFactory = new AbilityContextFactory(this, cooldownManager, beyonderService, lockManager, glowingEntities, effectManager, rampageManager, temporaryEventManager);

        this.passiveAbilityManager = new PassiveAbilityManager();
        this.passiveAbilityScheduler = new PassiveAbilityScheduler(
                this,
                passiveAbilityManager,
                beyonderService,
                abilityContextFactory
        );
        this.rampageEventListener = new RampageEventListener(passiveAbilityScheduler, beyonderService);
        eventPublisher.subscribe(rampageEventListener::handle);

        this.abilityExecutor = new AbilityExecutor(
                beyonderService,
                lockManager,
                rampageManager,
                passiveAbilityManager,
                abilityContextFactory,
                sanityPenaltyHandler
        );

        this.potionItemFactory = new PotionItemFactory();
        this.potionManager = new PotionManager(pathwayManager, potionItemFactory);

        this.masteryRegenerationScheduler = new MasteryRegenerationScheduler(this, beyonderService);
        this.beyonderSleepListener = new BeyonderSleepListener(beyonderService);
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

    private void registerEvents() {
        AbilityMenuListener abilityMenuListener =
                new AbilityMenuListener(abilityMenu, beyonderService, abilityExecutor, abilityItemFactory, pluginLogger);

        BeyonderPlayerListener beyonderPlayerListener =
                new BeyonderPlayerListener(beyonderService, new BossBarUtil(), abilityExecutor, abilityItemFactory, pluginLogger);

        PathwayPotionListener pathwayPotionListener =
                new PathwayPotionListener(potionManager, beyonderService, passiveAbilityScheduler);

        PassiveAbilityLifecycleListener passiveAbilityLifecycleListener =
                new PassiveAbilityLifecycleListener(this.passiveAbilityScheduler);

        getServer().getPluginManager().registerEvents(abilityMenuListener, this);
        getServer().getPluginManager().registerEvents(beyonderPlayerListener, this);
        getServer().getPluginManager().registerEvents(pathwayPotionListener, this);
        getServer().getPluginManager().registerEvents(passiveAbilityLifecycleListener, this);
        getServer().getPluginManager().registerEvents(beyonderSleepListener, this);
        getServer().getPluginManager().registerEvents(new PotionCauldronListener(this), this);
    }


    private void registerCommands() {
        PathwayCommand pathwayCommand = new PathwayCommand(beyonderService, pathwayManager, abilityMenu, abilityItemFactory);
        SequencePotionCommand sequencePotionCommand = new SequencePotionCommand(potionManager);
        RampagerCommand rampagerCommand = new RampagerCommand(beyonderService);
        CustomItemCommand customItemCommand = new CustomItemCommand(customItemService);
        getCommand("pathway").setExecutor(pathwayCommand);
        getCommand("pathway").setTabCompleter(pathwayCommand);
        getCommand("potion").setExecutor(sequencePotionCommand);
        getCommand("potion").setTabCompleter(sequencePotionCommand);
        getCommand("mastery").setExecutor(new MasteryCommand(beyonderService));
        getCommand("rampager").setExecutor(rampagerCommand);
        getCommand("rampager").setTabCompleter(rampagerCommand);
        getCommand("custom-items").setExecutor(customItemCommand);
        getCommand("custom-items").setTabCompleter(customItemCommand);
        StructureCommand structureCommand = new StructureCommand(this, structureService, lootGenerationService, structureGenerator.getLootTagKey());
        getCommand("structure").setExecutor(structureCommand);
        getCommand("structure").setTabCompleter(structureCommand);
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
    }

    private void initializeStructures() {
        // NBT loader (using Bukkit native API - no WorldEdit needed!)
        File structuresDir = new File(getDataFolder(), "structures");
        this.nbtStructureLoader = new NBTStructureLoader(this, structuresDir);

        // Loot generation
        lootGenerationService = new LootGenerationService(customItemService);

        // Structure service
        this.structureService = new StructureService(nbtStructureLoader);

        // Load structures from config
        StructureConfigLoader configLoader = new StructureConfigLoader(this);
        Map<String, Structure> structures = configLoader.loadStructures();
        structureService.registerAll(structures);

        // Register world populator
        this.structureGenerator = new StructureGenerator(
                this,
                structureService,
                lootGenerationService
        );

        for (World world : Bukkit.getWorlds()) {
            world.getPopulators().add(structureGenerator);
        }

        pluginLogger.info("Structure system initialized:");
        pluginLogger.info("  Total structures: " + structures.size());
    }
}