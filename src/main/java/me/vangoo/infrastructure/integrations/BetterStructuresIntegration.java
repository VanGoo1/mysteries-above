package me.vangoo.infrastructure.integrations;

import me.vangoo.application.services.CustomItemService;
import me.vangoo.application.services.PotionManager;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.infrastructure.items.RecipeBookFactory;
import org.bukkit.Material;
import org.bukkit.block.Container;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import java.util.*;
import java.util.logging.Logger;

public class BetterStructuresIntegration implements Listener {
    private final Plugin plugin;
    private final CustomItemService customItemService;
    private final PotionManager potionManager;
    private final RecipeBookFactory recipeBookFactory;
    private final Random random = new Random();
    private final Logger logger;

    // Конфігурація лута для різних типів структур
    private final Map<String, StructureLootConfig> lootConfigs = new HashMap<>();

    public BetterStructuresIntegration(
            Plugin plugin,
            CustomItemService customItemService,
            PotionManager potionManager,
            RecipeBookFactory recipeBookFactory) {
        this.plugin = plugin;
        this.customItemService = customItemService;
        this.potionManager = potionManager;
        this.recipeBookFactory = recipeBookFactory;
        this.logger = plugin.getLogger();

        initializeLootConfigs();
    }

    /**
     * Налаштування лута для різних структур BetterStructures
     */
    private void initializeLootConfigs() {
        // Приклад: Desert Temple
        lootConfigs.put("desert_temple", new StructureLootConfig()
                .addItem("sphinx_brain", 0.25, 1, 3)
                .addItem("potion:Error:9", 0.15, 1, 1)
                .addItem("gold_mint_leaves", 0.30, 2, 5)
        );

        // Приклад: Jungle Temple
        lootConfigs.put("jungle_temple", new StructureLootConfig()
                .addItem("serpent_bird_feather", 0.30, 2, 5)
                .addItem("potion:Door:9", 0.20, 1, 1)
                .addItem("shadow_poison_flower", 0.15, 1, 2)
        );

        // Приклад: Ancient Library (кастомна структура)
        lootConfigs.put("ancient_library", new StructureLootConfig()
                .addItem("recipe:Visionary:8", 0.20, 1, 1)
                .addItem("recipe:Error:9", 0.15, 1, 1)
                .addItem("meteor_crystal", 0.10, 1, 2)
                .addItem("potion:Visionary:7", 0.08, 1, 1)
        );

        // Приклад: Dark Fortress
        lootConfigs.put("dark_fortress", new StructureLootConfig()
                .addItem("potion:Error:5", 0.10, 1, 1)
                .addItem("recipe:Door:6", 0.12, 1, 1)
                .addItem("phantom_netherdrake_pituitary", 0.20, 1, 2)
                .addItem("immortal_flesh_fungus", 0.15, 1, 3)
        );

        // Приклад: End Ruins
        lootConfigs.put("end_ruins", new StructureLootConfig()
                .addItem("potion:Visionary:3", 0.05, 1, 1)
                .addItem("recipe:Error:4", 0.08, 1, 1)
                .addItem("mercury_phoenix_core", 0.12, 1, 2)
                .addItem("crystal_threadworm", 0.18, 1, 4)
        );
    }

    /**
     * Головний event handler для ChestFillEvent
     *
     * Priority.HIGH щоб спрацювати після базового заповнення BetterStructures
     */
//    @EventHandler(priority = EventPriority.HIGH)
//    public void onChestFill(ChestFillEvent event) {
//        Container container = event.getContainer();
//
//        // Отримуємо snapshot inventory для безпечної модифікації
//        Inventory inventory = container.getSnapshotInventory();
//
//        // Визначаємо тип структури (потрібно отримати з event або NBT)
//        String structureType = getStructureType(container);
//
//        if (structureType == null) {
//            // Якщо не визначили тип - додаємо рандомний базовий лут
//            addRandomBasicLoot(inventory);
//            return;
//        }
//
//        // Отримуємо конфіг для цього типу структури
//        StructureLootConfig config = lootConfigs.get(structureType);
//
//        if (config == null) {
//            logger.fine("No custom loot config for structure: " + structureType);
//            return;
//        }
//
//        // Генеруємо та додаємо custom items
//        List<ItemStack> customLoot = config.generateLoot();
//
//        for (ItemStack item : customLoot) {
//            // Додаємо items у випадкові слоти
//            addItemToRandomSlot(inventory, item);
//        }
//
//        logger.info("Added " + customLoot.size() + " custom items to " + structureType);
//    }

    /**
     * Визначити тип структури з NBT даних контейнера
     */
    private String getStructureType(Container container) {
        // Спроба 1: Через NBT tag (якщо BetterStructures зберігає)
        // Це залежить від версії BetterStructures

        // Спроба 2: Через location та найближчу структуру
        // (потрібен додатковий аналіз)

        // Спроба 3: Через custom persistentDataContainer
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "structure_type");
        var pdc = container.getPersistentDataContainer();

        if (pdc.has(key, org.bukkit.persistence.PersistentDataType.STRING)) {
            return pdc.get(key, org.bukkit.persistence.PersistentDataType.STRING);
        }

        // Fallback: повертаємо null, буде додано базовий лут
        return null;
    }

    /**
     * Додати базовий рандомний лут, якщо структура невідома
     */
    private void addRandomBasicLoot(Inventory inventory) {
        // 30% шанс на будь-який custom item
        if (random.nextDouble() < 0.30) {
            String[] basicItems = {
                    "gold_mint_leaves",
                    "night_vanilla_liquid",
                    "dragon_blood_grass",
                    "moonflower_essential_oil"
            };

            String itemId = basicItems[random.nextInt(basicItems.length)];
            ItemStack item = createItemFromId(itemId);

            if (item != null) {
                int amount = 1 + random.nextInt(3);
                item.setAmount(amount);
                addItemToRandomSlot(inventory, item);
            }
        }

        // 10% шанс на зілля послідовності 9
        if (random.nextDouble() < 0.10) {
            String[] pathways = {"Error", "Visionary", "Door"};
            String pathway = pathways[random.nextInt(pathways.length)];

            ItemStack potion = createItemFromId("potion:" + pathway + ":9");
            if (potion != null) {
                addItemToRandomSlot(inventory, potion);
            }
        }
    }

    /**
     * Додати item у випадковий порожній слот
     */
    private void addItemToRandomSlot(Inventory inventory, ItemStack item) {
        List<Integer> emptySlots = new ArrayList<>();

        // Знаходимо всі порожні слоти
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack slot = inventory.getItem(i);
            if (slot == null || slot.getType() == Material.AIR) {
                emptySlots.add(i);
            }
        }

        if (emptySlots.isEmpty()) {
            // Якщо немає порожніх слотів - замінюємо випадковий
            int slot = random.nextInt(inventory.getSize());
            inventory.setItem(slot, item);
        } else {
            // Вибираємо випадковий порожній слот
            int slot = emptySlots.get(random.nextInt(emptySlots.size()));
            inventory.setItem(slot, item);
        }
    }

    /**
     * Створити ItemStack з ID (підтримує custom items, potions, recipes)
     */
    private ItemStack createItemFromId(String itemId) {
        // Potion: "potion:pathway:sequence"
        if (itemId.startsWith("potion:")) {
            String[] parts = itemId.split(":");
            if (parts.length == 3) {
                try {
                    String pathway = parts[1];
                    int sequence = Integer.parseInt(parts[2]);
                    return potionManager.createPotionItem(pathway, Sequence.of(sequence));
                } catch (Exception e) {
                    logger.warning("Failed to create potion: " + itemId);
                }
            }
            return null;
        }

        // Recipe book: "recipe:pathway:sequence"
        if (itemId.startsWith("recipe:")) {
            String[] parts = itemId.split(":");
            if (parts.length == 3) {
                try {
                    String pathway = parts[1];
                    int sequence = Integer.parseInt(parts[2]);

                    var pathwayPotions = potionManager.getPotionsPathway(pathway).orElse(null);
                    if (pathwayPotions != null) {
                        return recipeBookFactory.createRecipeBook(pathwayPotions, sequence);
                    }
                } catch (Exception e) {
                    logger.warning("Failed to create recipe book: " + itemId);
                }
            }
            return null;
        }

        // Custom item
        return customItemService.createItemStack(itemId).orElse(null);
    }

    /**
     * Конфігурація лута для структури
     */
    private class StructureLootConfig {
        private final List<LootEntry> entries = new ArrayList<>();

        public StructureLootConfig addItem(String itemId, double chance, int minAmount, int maxAmount) {
            entries.add(new LootEntry(itemId, chance, minAmount, maxAmount));
            return this;
        }

        public List<ItemStack> generateLoot() {
            List<ItemStack> result = new ArrayList<>();

            for (LootEntry entry : entries) {
                if (random.nextDouble() < entry.chance) {
                    ItemStack item = createItemFromId(entry.itemId);
                    if (item != null) {
                        int amount = entry.minAmount + random.nextInt(entry.maxAmount - entry.minAmount + 1);
                        item.setAmount(amount);
                        result.add(item);
                    }
                }
            }

            return result;
        }
    }

    private record LootEntry(String itemId, double chance, int minAmount, int maxAmount) {}
}
