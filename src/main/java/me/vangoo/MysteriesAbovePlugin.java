package me.vangoo;

import de.slikey.effectlib.EffectManager;
import fr.skytasul.glowingentities.GlowingEntities;
import me.vangoo.application.services.*;
import me.vangoo.presentation.commands.RampagerCommand;
import me.vangoo.presentation.commands.SequencePotionCommand;
import me.vangoo.infrastructure.IBeyonderRepository;
import me.vangoo.infrastructure.JSONBeyonderRepository;
import me.vangoo.infrastructure.abilities.AbilityItemFactory;
import me.vangoo.presentation.listeners.BeyonderPlayerListener;
import me.vangoo.presentation.listeners.PathwayPotionListener;
import me.vangoo.presentation.commands.PathwayCommand;
import me.vangoo.presentation.commands.MasteryCommand;
import me.vangoo.presentation.listeners.AbilityMenuListener;
import me.vangoo.infrastructure.ui.AbilityMenu;
import me.vangoo.infrastructure.ui.BossBarUtil;
import me.vangoo.infrastructure.ui.NBTBuilder;
import org.bukkit.plugin.java.JavaPlugin;

public class MysteriesAbovePlugin extends JavaPlugin {

    private AbilityMenu abilityMenu;
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

    @Override
    public void onEnable() {
        glowingEntities = new GlowingEntities(this);
        saveDefaultConfig();
        initializeManagers();
        registerEvents();
        registerCommands();

        getServer().getScheduler().runTaskTimer(
                this,
                () -> beyonderService.regenerateAll(),
                20L,
                20L
        );
    }

    @Override
    public void onDisable() {
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
                        this.getDataFolder() + "beyonders.json",
                        pathwayManager
                );

        this.beyonderService = new BeyonderService(beyonderStorage, new BossBarUtil());

        this.lockManager = new AbilityLockManager();

        this.abilityExecutor = new AbilityExecutor(
                this,
                cooldownManager,
                beyonderService,
                lockManager,
                rampageEffectsHandler,
                glowingEntities
        );

        this.potionManager = new PotionManager(pathwayManager, this);
        this.effectManager = new EffectManager(this);
    }

    private void registerEvents() {
        AbilityMenuListener abilityMenuListener = new AbilityMenuListener(abilityMenu, beyonderService, abilityExecutor, abilityItemFactory, rampageEffectsHandler);

        BeyonderPlayerListener beyonderPlayerListener = new BeyonderPlayerListener(beyonderService, new BossBarUtil(), abilityExecutor, abilityItemFactory);
        PathwayPotionListener pathwayPotionListener = new PathwayPotionListener(potionManager, beyonderService);

        getServer().getPluginManager().registerEvents(abilityMenuListener, this);
        getServer().getPluginManager().registerEvents(beyonderPlayerListener, this);
        getServer().getPluginManager().registerEvents(pathwayPotionListener, this);
    }

    private void registerCommands() {
        PathwayCommand pathwayCommand = new PathwayCommand(beyonderService, pathwayManager, abilityMenu, abilityItemFactory);
        SequencePotionCommand sequencePotionCommand = new SequencePotionCommand(potionManager);
        getCommand("pathway").setExecutor(pathwayCommand);
        getCommand("pathway").setTabCompleter(pathwayCommand);
        getCommand("potion").setExecutor(sequencePotionCommand);
        getCommand("potion").setTabCompleter(sequencePotionCommand);
        getCommand("mastery").setExecutor(new MasteryCommand(beyonderService));
        getCommand("rampager").setExecutor(new RampagerCommand(beyonderService));
    }
}