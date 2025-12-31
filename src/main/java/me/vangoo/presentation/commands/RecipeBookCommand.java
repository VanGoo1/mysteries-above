package me.vangoo.presentation.commands;

import me.vangoo.application.services.PotionManager;
import me.vangoo.application.services.RecipeUnlockService;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.domain.valueobjects.UnlockedRecipe;
import me.vangoo.infrastructure.items.RecipeBookFactory;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Presentation Command: Manage recipe books
 * Usage: /recipe <give|list> ...
 */
public class RecipeBookCommand implements CommandExecutor, TabCompleter {
    private final RecipeBookFactory recipeBookFactory;
    private final PotionManager potionManager;
    private final RecipeUnlockService recipeUnlockService;

    private static final String PREFIX = ChatColor.DARK_PURPLE + "[Recipe] " + ChatColor.RESET;

    public RecipeBookCommand(
            RecipeBookFactory recipeBookFactory,
            PotionManager potionManager,
            RecipeUnlockService recipeUnlockService) {
        this.recipeBookFactory = recipeBookFactory;
        this.potionManager = potionManager;
        this.recipeUnlockService = recipeUnlockService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "give" -> handleGive(sender, args);
            case "list" -> handleList(sender, args);
            default -> sendUsage(sender);
        }

        return true;
    }

    /**
     * Handle /recipe give <player> <pathway> <sequence>
     */
    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(PREFIX + ChatColor.RED +
                    "Використання: /recipe give <гравець> <шлях> <послідовність>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Гравець не онлайн: " + args[1]);
            return;
        }

        String pathwayName = args[2];
        int sequence;

        try {
            sequence = Integer.parseInt(args[3]);
            if (sequence < 0 || sequence > 9) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Послідовність має бути від 0 до 9");
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Невірна послідовність: " + args[3]);
            return;
        }

        // Find pathway potions
        PathwayPotions pathwayPotions = potionManager.getPotionsPathway(pathwayName).orElse(null);

        if (pathwayPotions == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Шлях не знайдено: " + pathwayName);
            return;
        }

        // Check if recipe exists
        if (pathwayPotions.getIngredients(sequence) == null) {
            sender.sendMessage(PREFIX + ChatColor.RED +
                    "Рецепт для цієї послідовності не знайдено");
            return;
        }

        // Create recipe book
        ItemStack recipeBook = recipeBookFactory.createRecipeBook(pathwayPotions, sequence);

        // Give to player
        if (target.getInventory().firstEmpty() == -1) {
            target.getWorld().dropItem(target.getLocation(), recipeBook);
            sender.sendMessage(PREFIX + ChatColor.YELLOW +
                    "Інвентар повний! Книжку випущено на землю.");
        } else {
            target.getInventory().addItem(recipeBook);
        }

        sender.sendMessage(PREFIX + ChatColor.GREEN +
                String.format("Видано книжку рецептів гравцю %s: %s (Послідовність %d)",
                        target.getName(), pathwayName, sequence));

        target.sendMessage(PREFIX + ChatColor.GREEN + "Ви отримали книжку рецептів!");
    }

    /**
     * Handle /recipe list [player]
     */
    private void handleList(CommandSender sender, String[] args) {
        Player target;

        if (args.length < 2) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(PREFIX + ChatColor.RED +
                        "Використання: /recipe list <гравець>");
                return;
            }
            target = (Player) sender;
        } else {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Гравець не онлайн: " + args[1]);
                return;
            }
        }

        // Get unlocked recipes
        Set<UnlockedRecipe> unlockedRecipes = recipeUnlockService.getUnlockedRecipes(
                target.getUniqueId()
        );

        sender.sendMessage(PREFIX + ChatColor.GOLD + "=== Розблоковані рецепти: " +
                target.getName() + " ===");

        if (unlockedRecipes.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Немає розблокованих рецептів");
            return;
        }

        // Group by pathway
        Map<String, List<UnlockedRecipe>> byPathway = unlockedRecipes.stream()
                .collect(Collectors.groupingBy(UnlockedRecipe::pathwayName));

        for (Map.Entry<String, List<UnlockedRecipe>> entry : byPathway.entrySet()) {
            sender.sendMessage(ChatColor.YELLOW + "▪ " + entry.getKey() + ":");

            List<UnlockedRecipe> recipes = entry.getValue();
            recipes.sort(Comparator.comparingInt(UnlockedRecipe::sequence).reversed());

            for (UnlockedRecipe recipe : recipes) {
                sender.sendMessage(ChatColor.GRAY + "  - Послідовність " +
                        ChatColor.WHITE + recipe.sequence());
            }
        }

        sender.sendMessage(ChatColor.GRAY + "Всього: " + ChatColor.YELLOW +
                unlockedRecipes.size());
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(PREFIX + ChatColor.GOLD + "=== Команди книжок рецептів ===");
        sender.sendMessage(ChatColor.YELLOW + "/recipe give <гравець> <шлях> <послідовність>" +
                ChatColor.GRAY + " - Видати книжку");
        sender.sendMessage(ChatColor.YELLOW + "/recipe list [гравець]" +
                ChatColor.GRAY + " - Список розблокованих рецептів");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterMatches(args[0], Arrays.asList("give", "list"));
        }

        String subCommand = args[0].toLowerCase();

        if (args.length == 2) {
            // Player names for both subcommands
            return filterMatches(args[1],
                    Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .collect(Collectors.toList())
            );
        }

        if (args.length == 3 && subCommand.equals("give")) {
            // Pathway names
            return filterMatches(args[2],
                    potionManager.getPotions().stream()
                            .map(p -> p.getPathway().getName())
                            .collect(Collectors.toList())
            );
        }

        if (args.length == 4 && subCommand.equals("give")) {
            // Sequence numbers
            return filterMatches(args[3],
                    Arrays.asList("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")
            );
        }

        return List.of();
    }

    private List<String> filterMatches(String input, List<String> options) {
        String lower = input.toLowerCase();
        return options.stream()
                .filter(option -> option.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}