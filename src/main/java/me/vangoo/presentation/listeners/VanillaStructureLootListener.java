package me.vangoo.presentation.listeners;

import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.LootTier;
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
 * Listener для додавання кастомного лута до ванільних структур Minecraft.
 * Тиризація: звичайні скрині тягнуть лише BASE; данжі/спец-структури — BASE + RARE.
 */
public class VanillaStructureLootListener implements Listener {

    private final Logger logger;
    private final LootGenerationService lootService;
    private final LootTableData globalLootTable;
    private final BeyonderService beyonderService;
    private final Random random = new Random();

    private final Map<String, StructureLootRule> enabledStructures;

    /** Правило луту структури: шанс додати кастомний лут + дозволені тіри. */
    private record StructureLootRule(double chance, Set<LootTier> tiers) {}

    public VanillaStructureLootListener(
            Plugin plugin,
            LootGenerationService lootService,
            LootTableData globalLootTable,
            BeyonderService beyonderService) {
        this.logger = plugin.getLogger();
        this.lootService = lootService;
        this.globalLootTable = globalLootTable;
        this.beyonderService = beyonderService;
        this.enabledStructures = loadEnabledVanillaStructures();

        logger.info("VanillaStructureLootListener initialized for " + enabledStructures.size() + " structure types");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLootGenerate(LootGenerateEvent event) {
        String lootTableKey = event.getLootTable().getKey().toString();

        StructureLootRule rule = getRule(lootTableKey);
        if (rule == null || rule.chance() <= 0.0) {
            return;
        }
        if (random.nextDouble() > rule.chance()) {
            return;
        }

        addCustomLoot(event, lootTableKey, rule.tiers());
    }

    private StructureLootRule getRule(String lootTableKey) {
        for (Map.Entry<String, StructureLootRule> entry : enabledStructures.entrySet()) {
            if (lootTableKey.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Map<String, StructureLootRule> loadEnabledVanillaStructures() {
        Map<String, StructureLootRule> structures = new HashMap<>();

        Set<LootTier> base = EnumSet.of(LootTier.BASE);
        Set<LootTier> baseRare = EnumSet.of(LootTier.BASE, LootTier.RARE);

        // Звичайні скрині -> лише BASE
        structures.put("shipwreck", new StructureLootRule(0.10, base));
        structures.put("mineshaft", new StructureLootRule(0.10, base));
        structures.put("desert_pyramid", new StructureLootRule(0.10, base));
        structures.put("jungle_temple", new StructureLootRule(0.10, base));
        structures.put("buried_treasure", new StructureLootRule(0.15, base));
        structures.put("ocean_ruin_warm", new StructureLootRule(0.10, base));
        structures.put("ocean_ruin_cold", new StructureLootRule(0.10, base));
        structures.put("ruined_portal", new StructureLootRule(0.20, base));
        structures.put("simple_dungeon", new StructureLootRule(0.10, base));
        structures.put("trial_chambers/supply", new StructureLootRule(0.10, base));

        // Данжі / спец-структури -> BASE + RARE
        structures.put("mansion", new StructureLootRule(0.20, baseRare));
        structures.put("ancient_city", new StructureLootRule(0.25, baseRare));
        structures.put("bastion", new StructureLootRule(0.20, baseRare));
        structures.put("nether_bridge", new StructureLootRule(0.05, baseRare));
        structures.put("end_city", new StructureLootRule(0.10, baseRare));
        structures.put("stronghold", new StructureLootRule(0.10, baseRare));
        structures.put("pillager_outpost", new StructureLootRule(0.20, baseRare));
        structures.put("trial_chambers/corridor", new StructureLootRule(0.10, baseRare));
        structures.put("mysteries", new StructureLootRule(0.15, baseRare));
        structures.put("nova_structures", new StructureLootRule(0.15, baseRare));

        logger.info("Enabled vanilla structures for custom loot: " + structures.size());
        return structures;
    }

    private void addCustomLoot(LootGenerateEvent event, String lootTableKey, Set<LootTier> tiers) {
        if (globalLootTable == null || globalLootTable.items().isEmpty()) {
            return;
        }

        LootTableData tiered = globalLootTable.filterByTier(tiers);
        if (tiered.items().isEmpty()) {
            return;
        }

        Beyonder beyonder = null;
        if (event.getEntity() != null) {
            beyonder = beyonderService.getBeyonder(event.getEntity().getUniqueId());
        }

        List<ItemStack> currentLoot = event.getLoot();
        int itemsToAdd = (random.nextDouble() <= 0.20) ? 2 : 1;

        logger.fine("Adding " + itemsToAdd + " custom items (tiers " + tiers + ") to " + lootTableKey);

        List<ItemStack> generatedLoot = lootService.generateLoot(tiered, itemsToAdd, false, beyonder);
        currentLoot.addAll(generatedLoot);
    }
}
