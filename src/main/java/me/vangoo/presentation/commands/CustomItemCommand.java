package me.vangoo.presentation.commands;

import me.vangoo.application.services.CustomItemService;
import me.vangoo.domain.valueobjects.CustomItem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command for managing custom items
 * Usage: /customitem <give|list|info> ...
 */
public class CustomItemCommand implements CommandExecutor, TabCompleter {
    private final CustomItemService customItemService;
    private static final String PREFIX = ChatColor.DARK_PURPLE + "[CustomItem] " + ChatColor.RESET;

    public CustomItemCommand(CustomItemService customItemService) {
        this.customItemService = customItemService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "give" -> handleGive(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            default -> sendUsage(sender);
        }

        return true;
    }

    /**
     * Handle /customitem give <player> <item_id> [amount]
     */
    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Використання: /customitem give <гравець> <item_id> [кількість]");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Гравець не онлайн: " + args[1]);
            return;
        }

        String itemId = args[2];
        int amount = 1;

        if (args.length >= 4) {
            try {
                amount = Integer.parseInt(args[3]);
                if (amount < 1 || amount > 64) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Кількість має бути від 1 до 64");
                    return;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Невірна кількість: " + args[3]);
                return;
            }
        }

        // Create and give item
        ItemStack itemStack = customItemService.createItemStack(itemId, amount).orElse(null);

        if (itemStack == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Предмет не знайдено: " + itemId);
            return;
        }

        // Give to player
        if (target.getInventory().firstEmpty() == -1) {
            target.getWorld().dropItem(target.getLocation(), itemStack);
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Інвентар повний! Предмет випав на землю.");
        } else {
            target.getInventory().addItem(itemStack);
        }

        sender.sendMessage(PREFIX + ChatColor.GREEN +
                String.format("Видано %dx %s гравцю %s", amount, itemId, target.getName()));

        target.sendMessage(PREFIX + ChatColor.GREEN + "Ви отримали кастомний предмет!");
    }

    /**
     * Handle /customitem list
     */
    private void handleList(CommandSender sender) {
        sender.sendMessage(PREFIX + ChatColor.GOLD + "=== Кастомні предмети ===");

        List<CustomItem> items = new ArrayList<>(customItemService.getAllItems());

        if (items.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Немає предметів");
            return;
        }

        items.stream()
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .forEach(item -> {
                    sender.sendMessage(
                            ChatColor.YELLOW + "• " + item.id() +
                                    ChatColor.GRAY + " - " +
                                    ChatColor.WHITE + item.displayName()
                    );
                });

        sender.sendMessage(ChatColor.GRAY + "Всього: " + ChatColor.YELLOW + items.size());
    }

    /**
     * Handle /customitem info <item_id>
     */
    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Використання: /customitem info <item_id>");
            return;
        }

        String itemId = args[1];
        CustomItem item = customItemService.getItem(itemId).orElse(null);

        if (item == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Предмет не знайдено: " + itemId);
            return;
        }

        sender.sendMessage(PREFIX + ChatColor.GOLD + "=== Інформація про предмет ===");
        sender.sendMessage(ChatColor.YELLOW + "ID: " + ChatColor.WHITE + item.id());
        sender.sendMessage(ChatColor.YELLOW + "Назва: " + item.displayName());
        sender.sendMessage(ChatColor.YELLOW + "Матеріал: " + ChatColor.WHITE + item.material());
        sender.sendMessage(ChatColor.YELLOW + "Світіння: " + ChatColor.WHITE + (item.glow() ? "Так" : "Ні"));

        if (item.hasCustomModelData()) {
            sender.sendMessage(ChatColor.YELLOW + "Custom Model Data: " + ChatColor.WHITE + item.customModelData());
        }

        if (!item.lore().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "Опис:");
            item.lore().forEach(line -> sender.sendMessage(ChatColor.GRAY + "  " + line));
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(PREFIX + ChatColor.GOLD + "=== Команди кастомних предметів ===");
        sender.sendMessage(ChatColor.YELLOW + "/customitem give <гравець> <item_id> [кількість]" +
                ChatColor.GRAY + " - Видати предмет");
        sender.sendMessage(ChatColor.YELLOW + "/customitem list" +
                ChatColor.GRAY + " - Список предметів");
        sender.sendMessage(ChatColor.YELLOW + "/customitem info <item_id>" +
                ChatColor.GRAY + " - Інформація про предмет");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterMatches(args[0], Arrays.asList("give", "list", "info"));
        }

        String subCommand = args[0].toLowerCase();

        if (args.length == 2) {
            if (subCommand.equals("give")) {
                // Player names
                return filterMatches(args[1],
                        Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .collect(Collectors.toList())
                );
            } else if (subCommand.equals("info")) {
                // Item IDs
                return filterMatches(args[1],
                        new ArrayList<>(customItemService.getAllItems().stream()
                                .map(CustomItem::id)
                                .collect(Collectors.toList()))
                );
            }
        }

        if (args.length == 3 && subCommand.equals("give")) {
            // Item IDs for give command
            return filterMatches(args[2],
                    new ArrayList<>(customItemService.getAllItems().stream()
                            .map(CustomItem::id)
                            .collect(Collectors.toList()))
            );
        }

        if (args.length == 4 && subCommand.equals("give")) {
            // Amount suggestions
            return filterMatches(args[3], Arrays.asList("1", "8", "16", "32", "64"));
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