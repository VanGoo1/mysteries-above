package me.vangoo.infrastructure.ui;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import me.vangoo.application.services.PotionManager;
import me.vangoo.application.services.RecipeUnlockService;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.valueobjects.UnlockedRecipe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.stream.Collectors;

public class RecipeBookMenu {
    private final Plugin plugin;
    private final RecipeUnlockService recipeUnlockService;
    private final PotionManager potionManager;
    private final AbilityMenu abilityMenu;

    private static final Material BORDER_MATERIAL = Material.PURPLE_STAINED_GLASS_PANE;
    private static final Material BACK_BUTTON_MATERIAL = Material.BARRIER;

    public RecipeBookMenu(Plugin plugin,
                          RecipeUnlockService recipeUnlockService,
                          PotionManager potionManager,
                          AbilityMenu abilityMenu) {
        this.plugin = plugin;
        this.recipeUnlockService = recipeUnlockService;
        this.potionManager = potionManager;
        this.abilityMenu = abilityMenu;
    }

    /**
     * Відкриває головне меню рецептів (компаси шляхів)
     */
    public void openMainMenu(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Set<UnlockedRecipe> unlockedRecipes = recipeUnlockService.getUnlockedRecipes(
                    player.getUniqueId());

            Gui gui = createPathwaySelectionGui(player, unlockedRecipes);
            gui.open(player);
        });
    }

    /**
     * Створює GUI вибору шляху (1-indexed!)
     */
    private Gui createPathwaySelectionGui(Player player, Set<UnlockedRecipe> unlockedRecipes) {
        Gui gui = Gui.gui()
                .title(Component.text("📖 Книга Рецептів - Шляхи")
                        .color(NamedTextColor.DARK_GRAY)
                        .decorate(TextDecoration.BOLD))
                .rows(6)
                .disableAllInteractions()
                .create();

        // Заповнюємо GUI
        populatePathwaySelectionGui(gui, player, unlockedRecipes);

        return gui;
    }

    private void populatePathwaySelectionGui(Gui gui, Player player, Set<UnlockedRecipe> unlockedRecipes) {
        // Очищуємо всі слоти
        clearAllSlots(gui);

        // Декоративні рамки
        addBorders(gui);

        // Інформаційна панель
        addInfoPanel(gui, unlockedRecipes.size());

        // Групуємо рецепти за шляхами
        Map<String, List<UnlockedRecipe>> recipesByPathway = unlockedRecipes.stream()
                .collect(Collectors.groupingBy(
                        UnlockedRecipe::pathwayName,
                        TreeMap::new,
                        Collectors.toList()
                ));

        // Додаємо компаси шляхів
        addPathwayCompasses(gui, player, recipesByPathway);

        // Кнопка назад до меню здібностей
        addBackToAbilitiesButton(gui, player);
    }


    /**
     * Відкриває меню рецептів конкретного шляху
     */
    public void openPathwayRecipes(Player player, String pathwayName,
                                   List<UnlockedRecipe> recipes) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Gui gui = createPathwayRecipesGui(player, pathwayName, recipes);
            gui.open(player);
        });
    }

    /**
     * Створює GUI рецептів шляху
     */
    private Gui createPathwayRecipesGui(Player player, String pathwayName,
                                        List<UnlockedRecipe> recipes) {
        Gui gui = Gui.gui()
                .title(Component.text("📖 Рецепти: " + pathwayName)
                        .color(NamedTextColor.DARK_GRAY)
                        .decorate(TextDecoration.BOLD))
                .rows(6)
                .disableAllInteractions()
                .create();

        // Заповнюємо GUI
        populatePathwayRecipesGui(gui, player, pathwayName, recipes);

        return gui;
    }

    /**
     * Заповнює GUI рецептів шляху (для створення та оновлення)
     */
    private void populatePathwayRecipesGui(Gui gui, Player player, String pathwayName,
                                           List<UnlockedRecipe> recipes) {
        // Очищуємо всі слоти
        clearAllSlots(gui);

        // Декоративні рамки
        addBorders(gui);

        // Сортуємо рецепти від вищої до нижчої послідовності
        recipes.sort(Comparator.comparingInt(UnlockedRecipe::sequence).reversed());

        // Додаємо рецепти (починаючи з row 2, col 2)
        int[] contentSlots = {11, 12, 13, 14, 15, 16, 17,
                20, 21, 22, 23, 24, 25, 26,
                29, 30, 31, 32, 33, 34, 35,
                38, 39, 40, 41, 42, 43, 44};

        for (int i = 0; i < Math.min(recipes.size(), contentSlots.length); i++) {
            UnlockedRecipe recipe = recipes.get(i);
            int slot = contentSlots[i];
            int row = ((slot - 1) / 9) + 1;
            int col = ((slot - 1) % 9) + 1;

            ItemStack recipeItem = createRecipeItem(recipe, player);
            GuiItem guiItem = new GuiItem(recipeItem, event -> {
                event.setCancelled(true);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            });

            gui.setItem(row, col, guiItem);
        }

        // Кнопка назад до списку шляхів
        addBackToPathwaysButton(gui, player);
    }

    /**
     * Очищує всі слоти GUI
     */
    private void clearAllSlots(Gui gui) {
        for (int row = 1; row <= 6; row++) {
            for (int col = 1; col <= 9; col++) {
                gui.updateItem(row, col, new ItemStack(Material.AIR));
            }
        }
    }

    /**
     * Додає декоративні рамки
     */
    private void addBorders(Gui gui) {
        ItemStack border = new ItemStack(BORDER_MATERIAL);
        ItemMeta meta = border.getItemMeta();
        meta.setDisplayName(" ");
        border.setItemMeta(meta);
        GuiItem borderItem = new GuiItem(border);

        // Верхня і нижня рамки
        for (int col = 1; col <= 9; col++) {
            gui.setItem(1, col, borderItem);
            gui.setItem(6, col, borderItem);
        }

        // Бокові рамки
        for (int row = 2; row <= 5; row++) {
            gui.setItem(row, 1, borderItem);
            gui.setItem(row, 9, borderItem);
        }
    }

    /**
     * Додає інформаційну панель (row 1, col 5)
     */
    private void addInfoPanel(Gui gui, int totalRecipes) {
        ItemStack info = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = info.getItemMeta();

        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Відкриті Рецепти");
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Усього відкрито рецептів:");
        lore.add(ChatColor.YELLOW + "  ✦ " + ChatColor.WHITE + totalRecipes);
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "Оберіть шлях щоб");
        lore.add(ChatColor.DARK_GRAY + "побачити його рецепти");

        meta.setLore(lore);
        info.setItemMeta(meta);

        gui.setItem(1, 5, new GuiItem(info));
    }

    /**
     * Додає компаси шляхів
     */
    private void addPathwayCompasses(Gui gui, Player player,
                                     Map<String, List<UnlockedRecipe>> recipesByPathway) {
        int[] contentSlots = {11, 12, 13, 14, 15, 16, 17,
                20, 21, 22, 23, 24, 25, 26,
                29, 30, 31, 32, 33, 34, 35,
                38, 39, 40, 41, 42, 43, 44};

        int index = 0;
        for (Map.Entry<String, List<UnlockedRecipe>> entry : recipesByPathway.entrySet()) {
            if (index >= contentSlots.length) break;

            String pathwayName = entry.getKey();
            List<UnlockedRecipe> recipes = entry.getValue();

            int slot = contentSlots[index];
            int row = ((slot - 1) / 9) + 1;
            int col = ((slot - 1) % 9) + 1;

            ItemStack compass = createPathwayCompass(pathwayName, recipes.size());
            GuiItem guiItem = new GuiItem(compass, event -> {
                event.setCancelled(true);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);

                // Плавний перехід до рецептів шляху БЕЗ закриття
                populatePathwayRecipesGui(gui, player, pathwayName, recipes);


                Component titleComponent = Component.text("📖 Рецепти: " + pathwayName)
                        .color(NamedTextColor.DARK_GRAY)
                        .decorate(TextDecoration.BOLD);
                String legacyTitle = LegacyComponentSerializer.legacySection().serialize(titleComponent);
                // Оновлюємо заголовок
                gui.updateTitle(legacyTitle);

                gui.update();
            });

            gui.setItem(row, col, guiItem);
            index++;
        }
    }


    /**
     * Створює компас шляху
     */
    private ItemStack createPathwayCompass(String pathwayName, int recipeCount) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();

        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "◈ " + pathwayName);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Відкрито рецептів: " + ChatColor.YELLOW + recipeCount);
        lore.add("");
        lore.add(ChatColor.GREEN + "▸ Клацніть щоб переглянути");

        meta.setLore(lore);
        compass.setItemMeta(meta);

        return compass;
    }

    /**
     * Створює предмет рецепту з інформацією про інгредієнти
     */
    private ItemStack createRecipeItem(UnlockedRecipe recipe, Player player) {
        PathwayPotions pathwayPotions = potionManager.getPotionsPathway(recipe.pathwayName())
                .orElse(null);

        if (pathwayPotions == null) {
            return createErrorItem(recipe);
        }

        ItemStack item = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta meta = item.getItemMeta();

        ChatColor rankColor = getSequenceRankColor(recipe.sequence());
        String sequenceName = pathwayPotions.getPathway().getSequenceName(recipe.sequence());

        meta.setDisplayName(rankColor + "" + ChatColor.BOLD + sequenceName);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Шлях: " + ChatColor.YELLOW + recipe.pathwayName());
        lore.add(ChatColor.GRAY + "Послідовність: " + ChatColor.WHITE + recipe.sequence());
        lore.add("");
        lore.add(ChatColor.GOLD + "" + ChatColor.BOLD + "═══ Інгредієнти ═══");
        lore.add("");

        ItemStack[] ingredients = pathwayPotions.getIngredients(recipe.sequence());

        if (ingredients != null && ingredients.length > 0) {
            // Групуємо інгредієнти, щоб не дублювати рядки (наприклад, 2 рази по 1 діаманту)
            Map<String, Integer> ingredientCounts = new LinkedHashMap<>();
            Map<String, ItemStack> originalItems = new HashMap<>(); // Зберігаємо оригінал для перевірки isSimilar

            for (ItemStack ingredient : ingredients) {
                String name = getItemDisplayName(ingredient);
                ingredientCounts.merge(name, ingredient.getAmount(), Integer::sum);
                originalItems.putIfAbsent(name, ingredient);
            }

            for (Map.Entry<String, Integer> entry : ingredientCounts.entrySet()) {
                String name = entry.getKey();
                int requiredAmount = entry.getValue();
                ItemStack targetItem = originalItems.get(name);

                // 1. Рахуємо загальну кількість (Інвентар + Шалкери + Мішечки)
                int totalAmount = InventoryChecker.getTotalAmount(player, targetItem);

                // 2. Рахуємо кількість тільки в основному інвентарі (щоб знати, чи предмет схований)
                int mainInventoryAmount = 0;
                for (ItemStack invItem : player.getInventory().getContents()) {
                    if (invItem != null && invItem.isSimilar(targetItem)) {
                        mainInventoryAmount += invItem.getAmount();
                    }
                }

                StringBuilder line = new StringBuilder();
                line.append(ChatColor.YELLOW).append("  ▸ ").append(ChatColor.WHITE)
                        .append(requiredAmount).append("x ").append(ChatColor.GRAY).append(name);

                if (totalAmount >= requiredAmount) {
                    // Гравцю вистачає ресурсів
                    line.append(ChatColor.GREEN).append(" ✔");

                    // Якщо в основному інвентарі не вистачає, значить решта десь у контейнерах
                    if (mainInventoryAmount < requiredAmount) {
                        line.append(ChatColor.DARK_GRAY).append(" (у шалкері або у мішечку)");
                    }
                } else {
                    // Ресурсів не вистачає
                    line.append(ChatColor.RED).append(" ✘ ")
                            .append(ChatColor.DARK_GRAY).append("(").append(totalAmount).append("/").append(requiredAmount).append(")");
                }

                lore.add(line.toString());
            }
        } else {
            lore.add(ChatColor.DARK_GRAY + "  Інгредієнти невідомі");
        }

        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "━━━━━━━━━━━━━━━━━━━━");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Створює предмет помилки
     */
    private ItemStack createErrorItem(UnlockedRecipe recipe) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Помилка");

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Не вдалося завантажити");
        lore.add(ChatColor.GRAY + "інформацію про рецепт");
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + recipe.toString());

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Отримує відображувану назву предмета
     */
    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return ChatColor.stripColor(item.getItemMeta().getDisplayName());
        }

        String name = item.getType().name().toLowerCase().replace('_', ' ');
        String[] words = name.split(" ");
        StringBuilder formatted = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                formatted.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }

        return formatted.toString().trim();
    }

    /**
     * Отримує колір рангу послідовності
     */
    private ChatColor getSequenceRankColor(int sequence) {
        if (sequence == 0) return ChatColor.DARK_PURPLE;
        if (sequence >= 1 && sequence <= 3) return ChatColor.GOLD;
        if (sequence >= 4 && sequence <= 6) return ChatColor.AQUA;
        return ChatColor.GREEN;
    }

    /**
     * Додає кнопку назад до меню здібностей (row 6, col 5)
     */
    private void addBackToAbilitiesButton(Gui gui, Player player) {
        ItemStack backButton = new ItemStack(BACK_BUTTON_MATERIAL);
        ItemMeta meta = backButton.getItemMeta();

        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "◄ Назад");

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Повернутися до меню здібностей");

        meta.setLore(lore);
        backButton.setItemMeta(meta);

        GuiItem guiItem = new GuiItem(backButton, event -> {
            event.setCancelled(true);
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);

            // Повертаємося до меню здібностей
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
//                abilityMenu.openMenu(player, );
            }, 1L);
        });

        gui.setItem(6, 5, guiItem);
    }

    /**
     * Додає кнопку назад до списку шляхів (row 6, col 5)
     */
    private void addBackToPathwaysButton(Gui gui, Player player) {
        ItemStack backButton = new ItemStack(BACK_BUTTON_MATERIAL);
        ItemMeta meta = backButton.getItemMeta();

        meta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "◄ Назад");

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Повернутися до списку шляхів");

        meta.setLore(lore);
        backButton.setItemMeta(meta);

        GuiItem guiItem = new GuiItem(backButton, event -> {
            event.setCancelled(true);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.8f);

            // Плавне повернення до головного меню БЕЗ закриття
            Set<UnlockedRecipe> unlockedRecipes = recipeUnlockService.getUnlockedRecipes(
                    player.getUniqueId());

            if (!unlockedRecipes.isEmpty()) {
                populatePathwaySelectionGui(gui, player, unlockedRecipes);

                Component titleComponent = Component.text("📖 Книга Рецептів - Шляхи")
                        .color(NamedTextColor.DARK_GRAY)
                        .decorate(TextDecoration.BOLD);
                String legacyTitle = LegacyComponentSerializer.legacySection().serialize(titleComponent);

                gui.updateTitle(legacyTitle);

                gui.update();
            }
        });

        gui.setItem(6, 5, guiItem);
    }
}