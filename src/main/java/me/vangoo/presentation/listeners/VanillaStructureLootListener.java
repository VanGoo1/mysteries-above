package me.vangoo.presentation.listeners;

import me.vangoo.infrastructure.structures.LootGenerationService;
import me.vangoo.domain.valueobjects.LootTableData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.logging.Logger;

/**
 * Listener для додавання кастомного лута до ванільних структур Minecraft
 */
public class VanillaStructureLootListener implements Listener {

    private final Logger logger;
    private final LootGenerationService lootService;
    private final LootTableData globalLootTable;
    private final Random random = new Random();

    // Ванільні структури до яких додається лут
    private final Map<String, Double> enabledStructures;

    public VanillaStructureLootListener(
            Plugin plugin,
            LootGenerationService lootService,
            LootTableData globalLootTable) {
        this.logger = plugin.getLogger();
        this.lootService = lootService;
        this.globalLootTable = globalLootTable;
        this.enabledStructures = loadEnabledVanillaStructures();

        logger.info("VanillaStructureLootListener initialized for " + enabledStructures.size() + " structure types");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLootGenerate(LootGenerateEvent event) {
        String lootTableKey = event.getLootTable().getKey().toString();

        double chance = getStructureChance(lootTableKey);
        if (chance <= 0.0) {
            return;
        }

        if (random.nextDouble() > chance) {
            return;
        }

        addCustomLoot(event, lootTableKey);
    }

    private double getStructureChance(String lootTableKey) {
        for (Map.Entry<String, Double> entry : enabledStructures.entrySet()) {
            if (lootTableKey.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return 0.0;
    }

    private Map<String, Double> loadEnabledVanillaStructures() {
        Map<String, Double> structures = new HashMap<>();

        structures.put("mansion", 0.20);
        structures.put("ancient_city", 0.25);
        structures.put("bastion", 0.20);
        structures.put("nether_bridge", 0.05);
        structures.put("end_city", 0.10);
        structures.put("stronghold", 0.10);
        structures.put("jungle_temple", 0.10);
        structures.put("desert_pyramid", 0.10);
        structures.put("pillager_outpost", 0.20);
        structures.put("ocean_ruin_warm", 0.10);
        structures.put("ocean_ruin_cold", 0.10);
        structures.put("buried_treasure", 0.15);
        structures.put("shipwreck", 0.10);
        structures.put("mineshaft", 0.10);
        structures.put("simple_dungeon", 0.10);
        structures.put("ruined_portal", 0.20);
        structures.put("mysteries", 0.15);
        structures.put("nova_structures", 0.15);
        structures.put("trial_chambers/supply", 0.10);
        structures.put("trial_chambers/corridor", 0.10);

        logger.info("Enabled vanilla structures for custom loot: " + structures.size());
        return structures;
    }

    /**
     * Додає кастомні предмети до лута
     */
    private void addCustomLoot(LootGenerateEvent event, String lootTableKey) {
        if (globalLootTable == null || globalLootTable.items().isEmpty()) {
            return;
        }

        List<ItemStack> currentLoot = event.getLoot();

        // Визначаємо кількість предметів для додавання (20% шанс на 2 предмети)
        int itemsToAdd = (Math.random() <= 0.20) ? 2 : 1;

        logger.fine("Adding " + itemsToAdd + " custom items to " + lootTableKey);

        // Генеруємо лут через LootGenerationService
        List<ItemStack> generatedLoot = lootService.generateLoot(
                globalLootTable,
                itemsToAdd,
                false // не дозволяємо дублікати
        );

        // Додаємо згенеровані предмети до луту події
        currentLoot.addAll(generatedLoot);

        logger.fine("Successfully added " + generatedLoot.size() + " items to vanilla structure");
    }
}