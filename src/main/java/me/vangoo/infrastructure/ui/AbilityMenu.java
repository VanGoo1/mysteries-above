package me.vangoo.infrastructure.ui;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.application.services.AbilityExecutor;
import me.vangoo.application.services.PotionManager;
import me.vangoo.application.services.RecipeUnlockService;
import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.AbilityType;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.infrastructure.abilities.AbilityItemFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.stream.Collectors;

public class AbilityMenu {
    private final MysteriesAbovePlugin plugin;
    private final AbilityItemFactory abilityItemFactory;
    private final RecipeUnlockService recipeUnlockService;
    private final PotionManager potionManager;
    private final AbilityExecutor abilityExecutor;

    private static final Material BORDER_MATERIAL = Material.GRAY_STAINED_GLASS_PANE;
    private static final Material RECIPE_BUTTON_MATERIAL = Material.ENCHANTED_BOOK;
    private static final Material FILTER_BUTTON_MATERIAL = Material.HOPPER;

    // Зберігаємо поточний фільтр для кожного гравця
    private final Map<UUID, AbilityFilter> playerFilters = new HashMap<>();

    public enum AbilityFilter {
        ALL("Всі здібності", Material.NETHER_STAR),
        ACTIVE("Активні", Material.DIAMOND_SWORD),
        TOGGLEABLE_PASSIVE("Перемикаються", Material.LEVER),
        PERMANENT_PASSIVE("Постійні", Material.BEACON);

        private final String displayName;
        private final Material icon;

        AbilityFilter(String displayName, Material icon) {
            this.displayName = displayName;
            this.icon = icon;
        }

        public String getDisplayName() {
            return displayName;
        }

        public Material getIcon() {
            return icon;
        }

        public AbilityFilter next() {
            AbilityFilter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public AbilityMenu(MysteriesAbovePlugin plugin,
                       AbilityItemFactory itemFactory,
                       RecipeUnlockService recipeUnlockService,
                       PotionManager potionManager,
                       AbilityExecutor abilityExecutor) {
        this.plugin = plugin;
        this.abilityItemFactory = itemFactory;
        this.recipeUnlockService = recipeUnlockService;
        this.potionManager = potionManager;
        this.abilityExecutor = abilityExecutor;
    }

    /**
     * Створює предмет меню з інформацією про гравця
     */
    public ItemStack getMenuItem(Beyonder beyonder) {
        ItemStack item = new ItemStack(Material.ECHO_SHARD);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "✦ Містичні Здібності ✦");
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "╔══════════════════════════╗");
        lore.add(ChatColor.GOLD + " ⚡ " + ChatColor.YELLOW + "Шлях: " +
                ChatColor.WHITE + beyonder.getPathway().getName());
        lore.add(ChatColor.GOLD + " ◈ " + ChatColor.YELLOW + "Послідовність: " +
                ChatColor.WHITE + beyonder.getSequenceLevel() +
                ChatColor.GRAY + " (" + beyonder.getPathway().getSequenceName(beyonder.getSequenceLevel()) + ")");
        lore.add(ChatColor.GOLD + " ✦ " + ChatColor.YELLOW + "Засвоєння: " +
                ChatColor.GREEN + beyonder.getMasteryValue() + "%" +
                (beyonder.canAdvance() ? ChatColor.GREEN + " ✓" : ""));
        lore.add("");
        lore.add(ChatColor.GOLD + " ☠ " + ChatColor.YELLOW + "Втрата контролю: " +
                getSanityColor(beyonder.getSanityLossScale()) + beyonder.getSanityLossScale() +
                ChatColor.GRAY + "/100");
        lore.add(ChatColor.GRAY + "╚══════════════════════════╝");
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "▸ " + ChatColor.GRAY + "Клікніть для відкриття меню");

        meta.setLore(lore);
        item.setItemMeta(meta);

        NBTBuilder nbtBuilder = new NBTBuilder(item);
        return nbtBuilder.setBoolean("ability_menu_item", true).build();
    }

    /**
     * Відкриває головне меню здібностей
     */
    public void openMenu(Player player, Beyonder beyonder) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Gui gui = createMainGui(player, beyonder);
            gui.open(player);
        });
    }

    /**
     * Створює головне GUI меню
     */
    private Gui createMainGui(Player player, Beyonder beyonder) {
        Gui gui = Gui.gui()
                .title(Component.text("Здібності Потойбічного")
                        .color(NamedTextColor.DARK_GRAY))
                .rows(6)
                .disableAllInteractions()
                .create();

        // Отримуємо поточний фільтр
        AbilityFilter currentFilter = playerFilters.getOrDefault(player.getUniqueId(), AbilityFilter.ALL);

        // Заповнюємо GUI
        populateGui(gui, player, beyonder, currentFilter);

        return gui;
    }

    /**
     * Заповнює GUI контентом (використовується для створення та оновлення)
     */
    private void populateGui(Gui gui, Player player, Beyonder beyonder, AbilityFilter currentFilter) {
        // Очищуємо всі слоти перед оновленням
        for (int row = 1; row <= 6; row++) {
            for (int col = 1; col <= 9; col++) {
                gui.updateItem(row, col, new ItemStack(Material.AIR));
            }
        }
        // 1. Get all abilities (getAbilities() already merges pathway + off-pathway)
        List<Ability> allAbilities = new ArrayList<>(beyonder.getAbilities());
        // Фільтруємо здібності
        List<Ability> filteredAbilities = filterAbilities(allAbilities, currentFilter);

        // Додаємо здібності до GUI (слоти 2-5 rows, 2-8 cols)
        int slot = 0;
        for (int row = 2; row <= 5 && slot < filteredAbilities.size(); row++) {
            for (int col = 2; col <= 8 && slot < filteredAbilities.size(); col++) {
                Ability ability = filteredAbilities.get(slot);
                gui.setItem(row, col, createAbilityGuiItem(ability, beyonder, player));
                slot++;
            }
        }

        // Декоративні рамки
        addBorders(gui);

        // Інформаційна панель (центр верху)
        addInfoPanel(gui, beyonder);

        // Кнопка фільтру
        addFilterButton(gui, player, beyonder, currentFilter);

        // Кнопка рецептів
        addRecipeButton(gui, player);
    }

    /**
     * Оновлює існуюче GUI (плавне оновлення без закриття)
     */
    private void refreshGui(Gui gui, Player player, Beyonder beyonder, AbilityFilter newFilter) {
        // Оновлюємо контент
        populateGui(gui, player, beyonder, newFilter);

        // Викликаємо update для застосування змін
        gui.update();
    }

    /**
     * Фільтрує здібності за типом
     */
    private List<Ability> filterAbilities(List<Ability> abilities, AbilityFilter filter) {
        if (filter == AbilityFilter.ALL) {
            return new ArrayList<>(abilities);
        }

        AbilityType targetType = switch (filter) {
            case ACTIVE -> AbilityType.ACTIVE;
            case TOGGLEABLE_PASSIVE -> AbilityType.TOGGLEABLE_PASSIVE;
            case PERMANENT_PASSIVE -> AbilityType.PERMANENT_PASSIVE;
            default -> null;
        };

        if (targetType == null) {
            return new ArrayList<>(abilities);
        }

        return abilities.stream()
                .filter(ability -> ability.getType() == targetType)
                .collect(Collectors.toList());
    }

    /**
     * Створює GuiItem для здібності
     */
    private GuiItem createAbilityGuiItem(Ability ability, Beyonder beyonder, Player player) {
        ItemStack abilityItem = createAbilityItem(ability, beyonder, player);

        return new GuiItem(abilityItem, event -> {
            event.setCancelled(true);
            handleAbilityClick(player, beyonder, ability);
        });
    }

    /**
     * Додає декоративні рамки (1-indexed!)
     */
    private void addBorders(Gui gui) {
        ItemStack border = new ItemStack(BORDER_MATERIAL);
        ItemMeta meta = border.getItemMeta();
        meta.setDisplayName(" ");
        border.setItemMeta(meta);
        GuiItem borderItem = new GuiItem(border);

        // Верхня рамка (row 1, cols 1-9)
        for (int col = 1; col <= 9; col++) {
            gui.setItem(1, col, borderItem);
        }

        // Нижня рамка (row 6, cols 1-9)
        for (int col = 1; col <= 9; col++) {
            gui.setItem(6, col, borderItem);
        }

        // Бокові рамки (rows 2-5)
        for (int row = 2; row <= 5; row++) {
            gui.setItem(row, 1, borderItem);
            gui.setItem(row, 9, borderItem);
        }
    }

    /**
     * Додає інформаційну панель (центр верху - row 1, col 5)
     */
    private void addInfoPanel(Gui gui, Beyonder beyonder) {
        ItemStack info = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) info.getItemMeta();
        assert meta != null;
        meta.setOwningPlayer(Bukkit.getPlayer(beyonder.getPlayerId()));

        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + beyonder.getPathway().getName());
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Великий Древній: " + ChatColor.YELLOW +
                beyonder.getPathway().getGroup().getDisplayName());
        lore.add(ChatColor.GRAY + "Поточна послідовність: " + ChatColor.WHITE +
                beyonder.getSequenceLevel() + " - " +
                beyonder.getPathway().getSequenceName(beyonder.getSequenceLevel()));
        lore.add("");

        // Засвоєння
        int masteryValue = beyonder.getMasteryValue();
        int masteryBars = masteryValue / 5;
        String masteryBar = ChatColor.GOLD + "█".repeat(masteryBars) +
                ChatColor.GRAY + "█".repeat(20 - masteryBars);

        lore.add(ChatColor.YELLOW + "✦ Засвоєння: " + ChatColor.GREEN + masteryValue + "%");
        lore.add(masteryBar);
        lore.add("");

        if (beyonder.canAdvance()) {
            lore.add(ChatColor.GREEN + "✓ Готовий до просування!");
        }

        meta.setLore(lore);
        info.setItemMeta(meta);

        gui.setItem(1, 5, new GuiItem(info));
    }

    /**
     * Створює предмет здібності для GUI
     */
    private ItemStack createAbilityItem(Ability ability, Beyonder beyonder, Player player) {
        ItemStack item = abilityItemFactory.getItemFromAbility(ability, beyonder.getSequence());
        ItemMeta meta = item.getItemMeta();

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");

        switch (ability.getType()) {
            case ACTIVE -> {
                lore.add(ChatColor.GREEN + "▸ " + ChatColor.GRAY + "Клацніть щоб отримати предмет");

                if (hasAbilityItem(player, ability, beyonder)) {
                    lore.add(ChatColor.YELLOW + "✓ Вже в інвентарі");
                }
            }
            case TOGGLEABLE_PASSIVE -> {
                lore.add(ChatColor.AQUA + "▸ " + ChatColor.GRAY + "Клацніть щоб перемкнути");
            }
            case PERMANENT_PASSIVE -> {
                lore.add(ChatColor.GOLD + "✦ " + ChatColor.GRAY + "Завжди активна");
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        }

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Перевіряє чи є предмет здібності в інвентарі
     */
    private boolean hasAbilityItem(Player player, Ability ability, Beyonder beyonder) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && abilityItemFactory.getAbilityFromItem(item, beyonder) != null) {
                if (abilityItemFactory.getAbilityFromItem(item, beyonder).getIdentity().equals(ability.getIdentity())) {
                    return true;
                }
            }
        }
        return false;
    }
    /**
     * Обробляє клік по здібності
     */
    private void handleAbilityClick(Player player, Beyonder beyonder, Ability ability) {
        switch (ability.getType()) {
            case ACTIVE -> {
                // Check if player already has this ability item
                if (hasAbilityItem(player, ability, beyonder)) {
                    player.sendMessage(ChatColor.RED + "Ця здібність вже є у вашому інвентарі!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }

                // Check if inventory is full
                if (player.getInventory().firstEmpty() == -1) {
                    player.sendMessage(ChatColor.RED + "У вашому інвентарі немає вільного місця!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }

                // Give the ability item
                ItemStack abilityItem = abilityItemFactory.getItemFromAbility(
                        ability, beyonder.getSequence());
                player.getInventory().addItem(abilityItem);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
                player.sendMessage(ChatColor.GREEN + "Ви отримали предмет здібності: " + ability.getName());
            }
            case TOGGLEABLE_PASSIVE -> {
                AbilityResult result = abilityExecutor.execute(beyonder, ability);

                if (result.hasMessage()) {
                    player.sendMessage(result.getMessage());
                }
            }
            case PERMANENT_PASSIVE -> player.sendMessage(ChatColor.YELLOW + "Ця здібність завжди активна!");
        }
    }

    /**
     * Додає кнопку фільтру (row 6, col 3)
     */
    private void addFilterButton(Gui gui, Player player, Beyonder beyonder, AbilityFilter currentFilter) {
        ItemStack filterButton = new ItemStack(currentFilter.getIcon());
        ItemMeta meta = filterButton.getItemMeta();
        assert meta != null;
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "⚙ Фільтр: " +
                ChatColor.WHITE + currentFilter.getDisplayName());
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Наступний фільтр: " + ChatColor.RESET + ChatColor.GREEN + currentFilter.next().getDisplayName());
        lore.add("");
        lore.add(ChatColor.GREEN + "▸ Клацніть щоб перемкнути");

        meta.setLore(lore);
        filterButton.setItemMeta(meta);

        GuiItem guiItem = new GuiItem(filterButton, event -> {
            event.setCancelled(true);

            // Перемикаємо фільтр
            AbilityFilter newFilter = currentFilter.next();
            playerFilters.put(player.getUniqueId(), newFilter);

            // Звук
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);

            // Плавне оновлення без закриття меню
            refreshGui(gui, player, beyonder, newFilter);
        });

        gui.setItem(6, 3, guiItem);
    }

    /**
     * Додає кнопку рецептів (row 6, col 7)
     */
    private void addRecipeButton(Gui gui, Player player) {
        ItemStack recipeButton = new ItemStack(RECIPE_BUTTON_MATERIAL);
        ItemMeta meta = recipeButton.getItemMeta();

        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "📖 Книга Рецептів");
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Переглянути всі відкриті");
        lore.add(ChatColor.GRAY + "рецепти зілля");
        lore.add("");

        int unlockedCount = recipeUnlockService.getUnlockedRecipes(player.getUniqueId()).size();
        lore.add(ChatColor.GOLD + "✦ " + ChatColor.YELLOW + "Відкрито: " +
                ChatColor.WHITE + unlockedCount);
        lore.add("");
        lore.add(ChatColor.GREEN + "▸ Клацніть щоб відкрити");

        meta.setLore(lore);
        recipeButton.setItemMeta(meta);

        GuiItem guiItem = new GuiItem(recipeButton, event -> {
            event.setCancelled(true);
            player.closeInventory();

            RecipeBookMenu recipeMenu = new RecipeBookMenu(
                    plugin, recipeUnlockService, potionManager, this);
            recipeMenu.openMainMenu(player);
        });

        gui.setItem(6, 7, guiItem);
    }

    /**
     * Перевіряє чи є предмет меню здібностей
     */
    public boolean isAbilityMenu(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        NBTBuilder nbtBuilder = new NBTBuilder(item);
        return nbtBuilder.getBoolean(item, "ability_menu_item").orElse(false);
    }

    public void giveAbilityMenuItemToPlayer(Player player, Beyonder beyonder) {
        // Перевіряємо чи вже є предмет меню
        for (ItemStack item : player.getInventory().getContents()) {
            if (isAbilityMenu(item)) {
                return;
            }
        }

        ItemStack menuItem = getMenuItem(beyonder);
        player.getInventory().setItem(9, menuItem);
    }

    /**
     * Отримує колір для відображення санітності
     */
    private ChatColor getSanityColor(int scale) {
        if (scale >= 96) return ChatColor.DARK_RED;
        if (scale >= 81) return ChatColor.RED;
        if (scale >= 61) return ChatColor.GOLD;
        if (scale >= 41) return ChatColor.YELLOW;
        if (scale >= 21) return ChatColor.GREEN;
        return ChatColor.DARK_GREEN;
    }
}