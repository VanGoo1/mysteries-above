package me.vangoo.infrastructure.ui;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import me.vangoo.MysteriesAbovePlugin;
import me.vangoo.application.services.AbilityContextFactory;
import me.vangoo.application.services.AbilityExecutor;
import me.vangoo.application.services.PathwayManager;
import me.vangoo.application.services.PotionManager;
import me.vangoo.application.services.RecipeUnlockService;
import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.AbilityType;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.entities.Beyonder.BeyonderSnapshot;
import me.vangoo.infrastructure.abilities.AbilityItemFactory;
import me.vangoo.pathways.fool.abilities.MarionettistControl;
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
    private final PathwayManager pathwayManager;
    private final AbilityContextFactory abilityContextFactory;

    // Чи відкрита вкладка маріонетки (тільки під час контролю маріонетки)
    private final Map<UUID, Boolean> marionetteTabOpen = new HashMap<>();

    private static final Material BORDER_MATERIAL = Material.GRAY_STAINED_GLASS_PANE;
    private static final Material RECIPE_BUTTON_MATERIAL = Material.ENCHANTED_BOOK;
    private static final Material FILTER_BUTTON_MATERIAL = Material.HOPPER;

    // Зберігаємо поточний фільтр для кожного гравця
    private final Map<UUID, AbilityFilter> playerFilters = new HashMap<>();

    public enum AbilityFilter {
        ALL(Material.NETHER_STAR),
        ACTIVE(Material.DIAMOND_SWORD),
        TOGGLEABLE_PASSIVE(Material.LEVER),
        PERMANENT_PASSIVE(Material.BEACON);

        private final Material icon;

        AbilityFilter(Material icon) {
            this.icon = icon;
        }

        public String getDisplayName() {
            return switch (this) {
                case ALL -> "Всі здібності";
                case ACTIVE -> "Активні";
                case TOGGLEABLE_PASSIVE -> "Перемикаються";
                case PERMANENT_PASSIVE -> "Постійні";
            };
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
                       AbilityExecutor abilityExecutor,
                       PathwayManager pathwayManager,
                       AbilityContextFactory abilityContextFactory) {
        this.plugin = plugin;
        this.abilityItemFactory = itemFactory;
        this.recipeUnlockService = recipeUnlockService;
        this.potionManager = potionManager;
        this.abilityExecutor = abilityExecutor;
        this.pathwayManager = pathwayManager;
        this.abilityContextFactory = abilityContextFactory;
    }

    /** Знаходить спільний інстанс MarionettistControl (стан володіння маріонетками). */
    private MarionettistControl findMarionettist() {
        Ability a = pathwayManager.findAbilityInAllPathways(MarionettistControl.IDENTITY);
        return (a instanceof MarionettistControl mc) ? mc : null;
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
        // Чи керує гравець маріонеткою + чи відкрита вкладка маріонетки
        MarionettistControl mc = findMarionettist();
        boolean possessing = mc != null && mc.isPossessing(player.getUniqueId());
        boolean tabOpen = possessing && marionetteTabOpen.getOrDefault(player.getUniqueId(), false);

        if (tabOpen) {
            // ВКЛАДКА МАРІОНЕТКИ: здібності ОСНОВНОГО ТІЛА (творця), що витрачають його духовність
            renderMarionetteTabAbilities(gui, player, beyonder, mc, currentFilter);
        } else {
            // Звичайний вигляд: здібності поточного тіла (getAbilities() мерджить pathway + off-pathway)
            List<Ability> allAbilities = new ArrayList<>(beyonder.getAbilities());
            List<Ability> filteredAbilities = filterAbilities(allAbilities, currentFilter);

            int slot = 0;
            for (int row = 2; row <= 5 && slot < filteredAbilities.size(); row++) {
                for (int col = 2; col <= 8 && slot < filteredAbilities.size(); col++) {
                    Ability ability = filteredAbilities.get(slot);
                    gui.setItem(row, col, createAbilityGuiItem(ability, beyonder, player));
                    slot++;
                }
            }
        }

        // Декоративні рамки
        addBorders(gui);

        // Інформаційна панель (центр верху)
        addInfoPanel(gui, beyonder);

        // Кнопка рецептів
        addRecipeButton(gui, player);

        // Кнопка фільтру — лише у звичайному вигляді
        if (!tabOpen) {
            addFilterButton(gui, player, beyonder, currentFilter);
        }

        // Перемикач вкладки маріонетки + кнопки керування (лише під час контролю)
        if (possessing) {
            addMarionetteTabButton(gui, player, beyonder, currentFilter, tabOpen);
        }
        if (tabOpen) {
            addMarionetteExitButton(gui, player, beyonder, currentFilter);
            addMarionetteSwapButton(gui, player, beyonder, currentFilter);
        }
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

        // For active abilities that are already in inventory, make them non-clickable
        if (ability.getType() == AbilityType.ACTIVE && hasAbilityItem(player, ability, beyonder)) {
            return new GuiItem(abilityItem); // No click handler - item is not clickable
        }

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
        double masteryValue = beyonder.getMasteryValue();

        int filledBars = (int) (masteryValue / 5.0);
        filledBars = Math.min(20, Math.max(0, filledBars));

        int emptyBars = 20 - filledBars;

        String masteryBar = ChatColor.GOLD + "█".repeat(filledBars) +
                ChatColor.GRAY + "█".repeat(emptyBars);

        lore.add(ChatColor.YELLOW + "✦ Засвоєння: " + ChatColor.GREEN + String.format("%.2f", masteryValue) + "%");
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
                // Double-check: Prevent duplicates by checking inventory again
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

    // ════════════════════════════════════════════════════════════════════════
    // Вкладка МАРІОНЕТКИ (тільки під час контролю): здібності основного тіла,
    // що витрачають духовність ОСНОВНОГО ТІЛА, + кнопки виходу/перемикання.
    // ════════════════════════════════════════════════════════════════════════

    private void renderMarionetteTabAbilities(Gui gui, Player player, Beyonder beyonder,
                                              MarionettistControl mc, AbilityFilter currentFilter) {
        BeyonderSnapshot mainBody = mc.getMainBodySnapshot(player.getUniqueId());
        List<Ability> creatorAbilities = mc.getMainBodyActiveAbilities(player.getUniqueId());

        int slot = 0;
        for (int row = 2; row <= 5 && slot < creatorAbilities.size(); row++) {
            for (int col = 2; col <= 8 && slot < creatorAbilities.size(); col++) {
                Ability ability = creatorAbilities.get(slot);
                gui.setItem(row, col, createCreatorAbilityGuiItem(gui, player, beyonder, ability, mainBody, currentFilter));
                slot++;
            }
        }
    }

    private GuiItem createCreatorAbilityGuiItem(Gui gui, Player player, Beyonder beyonder,
                                                Ability ability, BeyonderSnapshot mainBody,
                                                AbilityFilter currentFilter) {
        ItemStack item = abilityItemFactory.getItemFromAbility(
                ability, mainBody != null ? mainBody.sequence() : beyonder.getSequence());
        ItemMeta meta = item.getItemMeta();
        List<String> lore = (meta != null && meta.hasLore()) ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.add("");
        if (mainBody != null) {
            lore.add(ChatColor.GRAY + "Духовність тіла: " + ChatColor.AQUA +
                    mainBody.spirituality().current() + "/" + mainBody.spirituality().maximum());
        }
        lore.add(ChatColor.GREEN + "▸ " + ChatColor.GRAY + "Клацніть, щоб отримати предмет");
        lore.add(ChatColor.DARK_GRAY + "Витрачає духовність основного тіла");
        if (hasMainBodyAbilityItem(player, ability)) {
            lore.add(ChatColor.YELLOW + "✓ Вже в інвентарі");
        }
        if (meta != null) { meta.setLore(lore); item.setItemMeta(meta); }

        return new GuiItem(item, event -> {
            event.setCancelled(true);
            giveMainBodyAbilityItem(gui, player, beyonder, ability, mainBody, currentFilter);
        });
    }

    /**
     * Видає предмет здібності основного тіла (помічений NBT-міткою). Самe виконання —
     * через {@code MainBodyAbilityListener} при використанні предмета (списує духовність тіла).
     */
    private void giveMainBodyAbilityItem(Gui gui, Player player, Beyonder beyonder, Ability ability,
                                         BeyonderSnapshot mainBody, AbilityFilter currentFilter) {
        if (hasMainBodyAbilityItem(player, ability)) {
            player.sendMessage(ChatColor.RED + "Ця здібність вже є у вашому інвентарі!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(ChatColor.RED + "У вашому інвентарі немає вільного місця!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        ItemStack item = abilityItemFactory.getItemFromAbility(
                ability, mainBody != null ? mainBody.sequence() : beyonder.getSequence());
        item = new NBTBuilder(item)
                .setString(MarionettistControl.MAIN_BODY_ABILITY_NBT, ability.getIdentity().id())
                .build();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.DARK_PURPLE + "✦ Здібність основного тіла");
            lore.add(ChatColor.DARK_GRAY + "Витрачає духовність вашого тіла");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        player.getInventory().addItem(item);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
        player.sendMessage(ChatColor.GREEN + "Ви отримали предмет здібності: " + ability.getName());
        refreshGui(gui, player, beyonder, currentFilter);
    }

    private boolean hasMainBodyAbilityItem(Player player, Ability ability) {
        String id = ability.getIdentity().id();
        for (ItemStack it : player.getInventory().getContents()) {
            java.util.Optional<String> tag = MarionettistControl.mainBodyAbilityId(it);
            if (tag.isPresent() && tag.get().equals(id)) return true;
        }
        return false;
    }

    /** Перемикач вкладки маріонетки (row 6, col 5). */
    private void addMarionetteTabButton(Gui gui, Player player, Beyonder beyonder,
                                        AbilityFilter currentFilter, boolean tabOpen) {
        ItemStack btn = new ItemStack(tabOpen ? Material.NETHER_STAR : Material.STRING);
        ItemMeta meta = btn.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "✦ Вкладка Маріонетки");
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(tabOpen ? ChatColor.GRAY + "Зараз: здібності основного тіла"
                         : ChatColor.GRAY + "Зараз: здібності маріонетки");
        lore.add(ChatColor.GREEN + "▸ Клацніть, щоб перемкнути");
        meta.setLore(lore);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        btn.setItemMeta(meta);

        gui.setItem(6, 5, new GuiItem(btn, event -> {
            event.setCancelled(true);
            marionetteTabOpen.put(player.getUniqueId(), !tabOpen);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            refreshGui(gui, player, beyonder, currentFilter);
        }));
    }

    /** Кнопка виходу з маріонетки (row 6, col 4). */
    private void addMarionetteExitButton(Gui gui, Player player, Beyonder beyonder, AbilityFilter currentFilter) {
        ItemStack btn = new ItemStack(Material.ECHO_SHARD);
        ItemMeta meta = btn.getItemMeta();
        meta.setDisplayName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "⮌ Вийти з маріонетки");
        meta.setLore(List.of(ChatColor.GRAY + "Повернутись у своє основне тіло"));
        btn.setItemMeta(meta);

        gui.setItem(6, 4, new GuiItem(btn, event -> {
            event.setCancelled(true);
            player.closeInventory();
            marionetteTabOpen.remove(player.getUniqueId());
            MarionettistControl mc = findMarionettist();
            if (mc != null) {
                IAbilityContext ctx = abilityContextFactory.createContext(player);
                mc.exitIfPossessing(ctx);
            }
        }));
    }

    /** Кнопка швидкого перемикання між маріонетками (row 6, col 6). */
    private void addMarionetteSwapButton(Gui gui, Player player, Beyonder beyonder, AbilityFilter currentFilter) {
        ItemStack btn = new ItemStack(Material.LEAD);
        ItemMeta meta = btn.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "⇄ Перемкнути маріонетку");
        meta.setLore(List.of(ChatColor.GRAY + "Меню ваших маріонеток"));
        btn.setItemMeta(meta);

        gui.setItem(6, 6, new GuiItem(btn, event -> {
            event.setCancelled(true);
            player.closeInventory();
            MarionettistControl mc = findMarionettist();
            if (mc != null) {
                IAbilityContext ctx = abilityContextFactory.createContext(player);
                mc.openSwapMenu(ctx);
            }
        }));
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