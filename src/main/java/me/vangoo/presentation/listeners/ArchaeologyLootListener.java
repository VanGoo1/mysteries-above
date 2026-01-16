package me.vangoo.presentation.listeners;

import me.vangoo.infrastructure.structures.LootGenerationService;
import me.vangoo.domain.valueobjects.LootTableData;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BrushableBlock;
import org.bukkit.block.data.Brushable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.logging.Logger;

/**
 * Listener для заміни ванільного археологічного лута на кастомний
 * ЗМІНЮЄ предмет ДО початку brushing - гравець бачить правильний айтем одразу!
 */
public class ArchaeologyLootListener implements Listener {

    private final Logger logger;
    private final LootGenerationService lootService;
    private final LootTableData globalLootTable;
    private final Random random = new Random();

    // Шанси заміни для різних археологічних структур
    private final Map<String, Double> structureReplaceChances;

    public ArchaeologyLootListener(
            Plugin plugin,
            LootGenerationService lootService,
            LootTableData globalLootTable) {
        this.logger = plugin.getLogger();
        this.lootService = lootService;
        this.globalLootTable = globalLootTable;
        this.structureReplaceChances = loadStructureReplaceChances();

        logger.info("ArchaeologyLootListener initialized - modifies item BEFORE brushing starts");
    }

    /**
     * Заміняємо предмет у BrushableBlock ДО того, як гравець почне brushing
     * Так гравець побачить правильний предмет під час анімації!
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        // Перевіряємо чи це підозрілий блок
        if (block.getType() != Material.SUSPICIOUS_SAND &&
                block.getType() != Material.SUSPICIOUS_GRAVEL) {
            return;
        }

        // Перевіряємо чи гравець використовує пензлик
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.BRUSH) {
            return;
        }

        // Отримуємо BrushableBlock
        if (!(block.getState() instanceof BrushableBlock brushableBlock)) {
            return;
        }

        // Перевіряємо чи brushing вже почався
        if (block.getBlockData() instanceof Brushable brushable) {
            int dusted = brushable.getDusted();
            if (dusted > 0) {
                return; // Вже почали чистити, пропускаємо
            }
        } else {
            return;
        }

        // Отримуємо лут-таблицю блоку (визначає структуру!)
        if (brushableBlock.getLootTable() == null) {
            return;
        }

        String lootTableKey = brushableBlock.getLootTable().getKey().toString();

        // Визначаємо шанс заміни на основі структури
        double replaceChance = getReplaceChanceForStructure(lootTableKey);

        if (replaceChance <= 0.0) {
            return;
        }

        // Перевіряємо шанс заміни
        double roll = random.nextDouble();

        if (roll > replaceChance) {
            return; // Залишаємо ванільний лут
        }

        // ЗАМІНЮЄМО предмет у блоці ДО початку brushing
        replaceBlockItem(brushableBlock, lootTableKey);
    }

    /**
     * Визначає шанс заміни на основі конкретної археологічної структури
     */
    private double getReplaceChanceForStructure(String lootTableKey) {
        for (Map.Entry<String, Double> entry : structureReplaceChances.entrySet()) {
            if (lootTableKey.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return 0.0;
    }

    /**
     * Завантажує шанси заміни для кожної археологічної структури
     */
    private Map<String, Double> loadStructureReplaceChances() {
        Map<String, Double> chances = new HashMap<>();

        chances.put("archaeology/desert_pyramid", 0.15);
        chances.put("archaeology/desert_well", 0.20);
        chances.put("archaeology/ocean_ruin_cold", 0.15);
        chances.put("archaeology/ocean_ruin_warm", 0.15);
        chances.put("archaeology/trail_ruins_common", 0.20);
        chances.put("archaeology/trail_ruins_rare", 0.25);

        chances.put("archaeology/ruins", 0.15);
        chances.put("nova_structures:archaeology", 0.20);

        return chances;
    }

    /**
     * ЗМІНЮЄ предмет всередині BrushableBlock на кастомний
     * Це робиться ДО brushing, тому гравець побачить правильний предмет!
     */
    private void replaceBlockItem(BrushableBlock brushableBlock, String lootTableKey) {
        if (globalLootTable == null || globalLootTable.items().isEmpty()) {
            logger.warning("ArchaeologyLootListener: globalLootTable is null or empty!");
            return;
        }

        // Генеруємо 1 кастомний предмет
        List<ItemStack> generatedLoot = lootService.generateLoot(
                globalLootTable,
                1,
                false
        );

        if (generatedLoot.isEmpty()) {
            logger.warning("ArchaeologyLootListener: Failed to generate custom archaeology loot - list is empty");
            return;
        }

        ItemStack customItem = generatedLoot.getFirst();

        try {
            brushableBlock.setLootTable(null);
        } catch (Exception e) {
            logger.warning("Failed to setLootTable(null): " + e);
        }

        // ВСТАНОВЛЮЄМО новий предмет у блок
        brushableBlock.setItem(customItem);

        // ВАЖЛИВО: оновлюємо стан блоку, щоб зміни застосувалися
        brushableBlock.update();

        logger.info("Replaced archaeology item in " + lootTableKey + " with " + customItem.getType());
    }
}