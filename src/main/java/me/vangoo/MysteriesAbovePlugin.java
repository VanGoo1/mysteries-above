package me.vangoo;

import de.slikey.effectlib.EffectManager;
import fr.skytasul.glowingentities.GlowingEntities;
import me.vangoo.application.services.*;
import me.vangoo.infrastructure.schedulers.MasteryRegenerationScheduler;
import me.vangoo.infrastructure.schedulers.PassiveAbilityScheduler;
import me.vangoo.presentation.commands.RampagerCommand;
import me.vangoo.presentation.commands.SequencePotionCommand;
import me.vangoo.infrastructure.IBeyonderRepository;
import me.vangoo.infrastructure.JSONBeyonderRepository;
import me.vangoo.infrastructure.abilities.AbilityItemFactory;
import me.vangoo.presentation.listeners.*;
import me.vangoo.presentation.commands.PathwayCommand;
import me.vangoo.presentation.commands.MasteryCommand;
import me.vangoo.infrastructure.ui.AbilityMenu;
import me.vangoo.infrastructure.ui.BossBarUtil;
import me.vangoo.infrastructure.ui.NBTBuilder;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
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
    RampageEffectsHandler rampageEffectsHandler;
    AbilityItemFactory abilityItemFactory;
    PassiveAbilityManager passiveAbilityManager;
    PassiveAbilityScheduler passiveAbilityScheduler;
    MasteryRegenerationScheduler masteryRegenerationScheduler;
    BeyonderSleepListener beyonderSleepListener;
    AbilityContextFactory abilityContextFactory;

    @Override
    public void onEnable() {
        this.pluginLogger = this.getLogger();
        glowingEntities = new GlowingEntities(this);
        saveDefaultConfig();
        initializeManagers();
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

        this.cooldownManager = new CooldownManager();

        this.abilityItemFactory = new AbilityItemFactory();
        this.abilityMenu = new AbilityMenu(this, abilityItemFactory);

        this.pathwayManager = new PathwayManager();
        this.rampageEffectsHandler = new RampageEffectsHandler();

        this.beyonderStorage =
                new JSONBeyonderRepository(
                        this.getDataFolder() + File.separator + "beyonders.json",
                        pathwayManager
                );

        this.beyonderService = new BeyonderService(beyonderStorage, new BossBarUtil());
        this.lockManager = new AbilityLockManager();
        this.passiveAbilityManager = new PassiveAbilityManager();
        this.effectManager = new EffectManager(this);
        this.abilityContextFactory = new AbilityContextFactory(this, cooldownManager, beyonderService, lockManager, glowingEntities, effectManager);
        this.abilityExecutor = new AbilityExecutor(
                beyonderService,
                lockManager,
                rampageEffectsHandler,
                passiveAbilityManager,
                abilityContextFactory
        );

        this.potionManager = new PotionManager(pathwayManager, this);

        this.passiveAbilityScheduler = new PassiveAbilityScheduler(
                this,
                passiveAbilityManager,
                beyonderService,
                abilityContextFactory
        );

        this.masteryRegenerationScheduler = new MasteryRegenerationScheduler(this, beyonderService);
        this.beyonderSleepListener = new BeyonderSleepListener(beyonderService);
    }

    private void registerEvents() {
        AbilityMenuListener abilityMenuListener =
                new AbilityMenuListener(abilityMenu, beyonderService, abilityExecutor, abilityItemFactory, pluginLogger);

        BeyonderPlayerListener beyonderPlayerListener =
                new BeyonderPlayerListener(beyonderService, new BossBarUtil(), abilityExecutor, abilityItemFactory, pluginLogger);

        PathwayPotionListener pathwayPotionListener =
                new PathwayPotionListener(potionManager, beyonderService);

        PassiveAbilityLifecycleListener passiveAbilityLifecycleListener =
                new PassiveAbilityLifecycleListener(this.passiveAbilityScheduler);

        getServer().getPluginManager().registerEvents(abilityMenuListener, this);
        getServer().getPluginManager().registerEvents(beyonderPlayerListener, this);
        getServer().getPluginManager().registerEvents(pathwayPotionListener, this);
        getServer().getPluginManager().registerEvents(passiveAbilityLifecycleListener, this);
        getServer().getPluginManager().registerEvents(beyonderSleepListener, this);
    }


    private void registerCommands() {
        PathwayCommand pathwayCommand = new PathwayCommand(beyonderService, pathwayManager, abilityMenu, abilityItemFactory);
        SequencePotionCommand sequencePotionCommand = new SequencePotionCommand(potionManager);
        RampagerCommand rampagerCommand = new RampagerCommand(beyonderService);
        getCommand("pathway").setExecutor(pathwayCommand);
        getCommand("pathway").setTabCompleter(pathwayCommand);
        getCommand("potion").setExecutor(sequencePotionCommand);
        getCommand("potion").setTabCompleter(sequencePotionCommand);
        getCommand("mastery").setExecutor(new MasteryCommand(beyonderService));
        getCommand("rampager").setExecutor(rampagerCommand);
        getCommand("rampager").setTabCompleter(rampagerCommand);
    }

    private void startSchedulers() {
        getServer().getScheduler().runTaskTimer(
                this,
                () -> beyonderService.regenerateAll(),
                20L,
                20L
        );

        // NEW: Start passive ability scheduler
        passiveAbilityScheduler.start();
        masteryRegenerationScheduler.start();
    }

    private void stopSchedulers() {
        if (passiveAbilityScheduler != null) {
            passiveAbilityScheduler.stop();
        }
        masteryRegenerationScheduler.stop();
    }
}