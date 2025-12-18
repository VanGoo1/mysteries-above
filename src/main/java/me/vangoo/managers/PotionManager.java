package me.vangoo.managers;

import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.domain.Beyonder;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.implementation.ErrorPathway.ErrorPotions;
import me.vangoo.implementation.VisionaryPathway.VisionaryPotions;
import me.vangoo.domain.Pathway;
import org.bukkit.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PotionManager {
    private final List<PathwayPotions> potions;
    private final PathwayManager pathwayManager;
    MysteriesAbovePlugin plugin;

    public PotionManager(PathwayManager pathwayManager, MysteriesAbovePlugin plugin) {
        this.pathwayManager = pathwayManager;
        this.potions = new ArrayList<>();
        this.plugin = plugin;
        initializePotions();
    }

    private void initializePotions() {
        potions.add(new ErrorPotions(pathwayManager.getPathway("Error"), Color.fromRGB(26, 0, 181)));
        potions.add(new VisionaryPotions(pathwayManager.getPathway("Visionary"), Color.fromRGB(128, 128, 128)));
    }

    public Optional<PathwayPotions> getPotionsPathway(String pathwayName) {
        return potions.stream()
                .filter(potion -> potion.getPathway().getName().equalsIgnoreCase(pathwayName))
                .findFirst();
    }

    public List<PathwayPotions> getPotions() {
        return potions;
    }
}
