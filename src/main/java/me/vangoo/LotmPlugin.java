package me.vangoo;

import me.vangoo.abilities.AbilityManager;
import me.vangoo.beyonders.BeyonderManager;
import me.vangoo.commands.BeyonderCommand;
import me.vangoo.listeners.AbilityMenuListener;
import me.vangoo.pathways.PathwayManager;
import me.vangoo.potions.PotionManager;
import me.vangoo.utils.AbilityMenu;
import me.vangoo.utils.BossBarUtil;
import me.vangoo.utils.NBTBuilder;
import org.bukkit.plugin.java.JavaPlugin;

public class LotmPlugin extends JavaPlugin {
    private BeyonderManager beyonderManager;
    private PathwayManager pathwayManager;
    private PotionManager potionManager;
    private AbilityManager abilityManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        initializeManagers();
        registerCommands();
        registerEvents();
    }

    private void initializeManagers() {
        NBTBuilder.setPlugin(this);
        this.pathwayManager = new PathwayManager();
        this.potionManager = new PotionManager(pathwayManager, this);
        this.abilityManager = new AbilityManager();
        this.beyonderManager = new BeyonderManager(this, pathwayManager, abilityManager, new BossBarUtil());
    }

    private void registerEvents() {
        AbilityMenuListener abilityMenuListener = new AbilityMenuListener(new AbilityMenu(), getBeyonderManager(), getAbilityManager());
        getServer().getPluginManager().registerEvents(abilityMenuListener, this);
        getServer().getPluginManager().registerEvents(beyonderManager, this);
        getServer().getPluginManager().registerEvents(potionManager, this);
    }

    private void registerCommands() {
        getCommand("beyonder").setExecutor(new BeyonderCommand(potionManager));
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
}
