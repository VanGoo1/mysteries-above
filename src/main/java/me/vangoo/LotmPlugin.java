package me.vangoo;

import fr.skytasul.glowingentities.GlowingEntities;
import me.vangoo.domain.Ability;
import me.vangoo.managers.AbilityManager;
import me.vangoo.managers.CooldownManager;
import me.vangoo.managers.BeyonderManager;
import me.vangoo.commands.BeyonderCommand;
import me.vangoo.commands.MasteryCommand;
import me.vangoo.listeners.AbilityMenuListener;
import me.vangoo.managers.PathwayManager;
import me.vangoo.managers.PotionManager;
import me.vangoo.domain.AbilityMenu;
import me.vangoo.utils.BossBarUtil;
import me.vangoo.utils.NBTBuilder;
import org.bukkit.plugin.java.JavaPlugin;

public class LotmPlugin extends JavaPlugin {
    private BeyonderManager beyonderManager;
    private PathwayManager pathwayManager;
    private PotionManager potionManager;
    private AbilityManager abilityManager;
    private GlowingEntities glowingEntities;

    @Override
    public void onEnable() {
        glowingEntities = new GlowingEntities(this);
        saveDefaultConfig();
        initializeManagers();
        registerCommands();
        registerEvents();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        glowingEntities.disable();
    }

    private void initializeManagers() {
        Ability.setPlugin(this);
        NBTBuilder.setPlugin(this);
        this.pathwayManager = new PathwayManager();
        this.potionManager = new PotionManager(pathwayManager, this);
        this.abilityManager = new AbilityManager(new CooldownManager());
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
        getCommand("mastery").setExecutor(new MasteryCommand(beyonderManager));
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
}
