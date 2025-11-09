package me.vangoo;

import de.slikey.effectlib.EffectManager;
import fr.skytasul.glowingentities.GlowingEntities;
import me.vangoo.commands.RampagerCommand;
import me.vangoo.commands.SequencePotionCommand;
import me.vangoo.domain.Ability;
import me.vangoo.infrastructure.IBeyonderStorage;
import me.vangoo.infrastructure.JSONBeyonderStorage;
import me.vangoo.infrastructure.PathwayAdapter;
import me.vangoo.listeners.BeyonderPlayerListener;
import me.vangoo.listeners.PathwayPotionListener;
import me.vangoo.managers.*;
import me.vangoo.commands.PathwayCommand;
import me.vangoo.commands.MasteryCommand;
import me.vangoo.listeners.AbilityMenuListener;
import me.vangoo.domain.AbilityMenu;
import me.vangoo.utils.BossBarUtil;
import me.vangoo.utils.NBTBuilder;
import org.bukkit.plugin.java.JavaPlugin;

public class MysteriesAbovePlugin extends JavaPlugin {

    private AbilityMenu abilityMenu;
    private BeyonderManager beyonderManager;
    private PathwayManager pathwayManager;
    private PotionManager potionManager;
    private AbilityManager abilityManager;
    private GlowingEntities glowingEntities;
    private RampagerManager rampagerManager;
    private EffectManager effectManager;
    private IBeyonderStorage beyonderStorage;

    @Override
    public void onEnable() {
        glowingEntities = new GlowingEntities(this);
        saveDefaultConfig();
        initializeManagers();
        registerEvents();
        registerCommands();
    }

    @Override
    public void onDisable() {
        if (effectManager != null) {
            effectManager.dispose();
        }
        glowingEntities.disable();
        beyonderStorage.saveAll();
        super.onDisable();
    }

    private void initializeManagers() {
        Ability.setPlugin(this);
        NBTBuilder.setPlugin(this);

        this.abilityMenu = new AbilityMenu(this);
        this.pathwayManager = new PathwayManager();
        this.rampagerManager = new RampagerManager();
        this.potionManager = new PotionManager(pathwayManager, this);
        this.abilityManager = new AbilityManager(new CooldownManager(), rampagerManager);
        this.beyonderStorage = new JSONBeyonderStorage(this.getDataFolder() + "beyonders.json",
                new PathwayAdapter(pathwayManager));
        this.beyonderManager = new BeyonderManager(this, new BossBarUtil(), beyonderStorage);
        this.effectManager = new EffectManager(this);
    }

    private void registerEvents() {
        AbilityMenuListener abilityMenuListener = new AbilityMenuListener(abilityMenu, beyonderManager, abilityManager, rampagerManager);

        BeyonderPlayerListener beyonderPlayerListener = new BeyonderPlayerListener(beyonderManager, new BossBarUtil(), abilityManager);
        PathwayPotionListener pathwayPotionListener = new PathwayPotionListener(potionManager, beyonderManager);

        getServer().getPluginManager().registerEvents(abilityMenuListener, this);
        getServer().getPluginManager().registerEvents(beyonderPlayerListener, this);
        getServer().getPluginManager().registerEvents(pathwayPotionListener, this);
    }

    private void registerCommands() {
        PathwayCommand pathwayCommand = new PathwayCommand(beyonderManager, pathwayManager, abilityMenu, abilityManager);
        SequencePotionCommand sequencePotionCommand = new SequencePotionCommand(potionManager);
        getCommand("pathway").setExecutor(pathwayCommand);
        getCommand("pathway").setTabCompleter(pathwayCommand);
        getCommand("potion").setExecutor(sequencePotionCommand);
        getCommand("potion").setTabCompleter(sequencePotionCommand);
        getCommand("mastery").setExecutor(new MasteryCommand(beyonderManager));
        getCommand("rampager").setExecutor(new RampagerCommand(beyonderManager));
    }

    public BeyonderManager getBeyonderManager() {
        return beyonderManager;
    }

    public PathwayManager getPathwayManager() {
        return pathwayManager;
    }

    public PotionManager getPotionManager() {
        return potionManager;
    }

    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

    public GlowingEntities getGlowingEntities() {
        return glowingEntities;
    }

    public EffectManager getEffectManager() {
        return effectManager;
    }
}