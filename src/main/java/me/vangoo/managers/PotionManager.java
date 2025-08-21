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

    public boolean canConsumePotion(Beyonder beyonder, Pathway pathway, int sequence) {
        // Якщо гравець не потусторонній
        if (beyonder.getSequence() == -1) {
            // Може стати потустороннім тільки з зілля послідовності 9
            return sequence == 9;
        }
        // Якщо гравець вже потусторонній
        // Перевіряємо чи той самий шлях та чи тієї ж групи
        if (beyonder.getPathway().getGroup() != pathway.getGroup()) {
            return false;
        }

        // Перевіряємо чи наступна послідовність (у зворотному порядку)
        if (beyonder.getSequence() != sequence + 1) {
            return false; // Не та послідовність
        }

        // Перевіряємо чи засвоєння 100%
        return beyonder.getMastery() >= 100;
    }


}
