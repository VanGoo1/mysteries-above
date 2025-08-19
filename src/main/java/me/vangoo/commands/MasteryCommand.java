package me.vangoo.commands;

import me.vangoo.beyonders.Beyonder;
import me.vangoo.beyonders.BeyonderManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MasteryCommand implements CommandExecutor, TabCompleter {

    private final BeyonderManager beyonderManager;

    public MasteryCommand(BeyonderManager beyonderManager) {
        this.beyonderManager = beyonderManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Ця команда доступна тільки для гравців!");
            return true;
        }

        Player player = (Player) sender;
        Beyonder beyonder = beyonderManager.GetBeyonder(player.getUniqueId());

        if (beyonder == null) {
            player.sendMessage(ChatColor.RED + "Ви не є потойбічним");
            return true;
        }

        if (args.length == 0) {
            // Показати поточне засвоєння
            showMasteryInfo(player, beyonder);
            return true;
        }

        if (args[0].equalsIgnoreCase("info")) {
            showMasteryInfo(player, beyonder);
            return true;
        }

        if (args[0].equalsIgnoreCase("set")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Використання: /mastery set <значення>");
                player.sendMessage(ChatColor.GRAY + "Значення має бути від 1 до 100");
                return true;
            }

            try {
                int value = Integer.parseInt(args[1]);

                if (value < 1 || value > 100) {
                    player.sendMessage(ChatColor.RED + "Значення має бути від 1 до 100!");
                    return true;
                }

                setMastery(player, beyonder, value);
                return true;

            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "'" + args[1] + "' не є числом!");
                return true;
            }
        }

        // Невідома підкоманда
        showUsage(player);
        return true;
    }

    private void showMasteryInfo(Player player, Beyonder beyonder) {
        int mastery = beyonder.getMastery();
        int sequence = beyonder.getSequence();
        String pathwayName = beyonder.getPathway() != null ? beyonder.getPathway().getName() : "Невідомо";

        player.sendMessage(ChatColor.GOLD + "=== Інформація про засвоєння ===");
        player.sendMessage(ChatColor.YELLOW + "Шлях: " + ChatColor.WHITE + pathwayName);
        player.sendMessage(ChatColor.YELLOW + "Послідовність: " + ChatColor.WHITE + sequence);
        player.sendMessage(ChatColor.YELLOW + "Засвоєння: " + ChatColor.GREEN + mastery + ChatColor.WHITE + "/100 (" + ChatColor.GREEN + mastery + "%" + ChatColor.WHITE + ")");

        if (beyonder.canAdvance()) {
            player.sendMessage(ChatColor.GREEN + "✓ Ви можете перейти до наступної послідовності!");
        } else {
            int needed = 100 - mastery;
            player.sendMessage(ChatColor.YELLOW + "До просування потрібно ще " + ChatColor.GREEN + needed + ChatColor.YELLOW + " засвоєння");
        }
    }

    private void setMastery(Player player, Beyonder beyonder, int value) {
        int oldMastery = beyonder.getMastery();
        beyonder.setMastery(value);

        player.sendMessage(ChatColor.GREEN + "Засвоєння змінено з " + ChatColor.YELLOW + oldMastery +
                ChatColor.GREEN + " на " + ChatColor.YELLOW + value + ChatColor.GREEN + " (" + value + "%)");

        if (beyonder.canAdvance()) {
            player.sendMessage(ChatColor.GOLD + "★ Ви досягли максимального засвоєння і можете просунутися!");
        }
    }

    private void showUsage(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Команди засвоєння ===");
        player.sendMessage(ChatColor.YELLOW + "/mastery" + ChatColor.GRAY + " - Показати поточне засвоєння");
        player.sendMessage(ChatColor.YELLOW + "/mastery info" + ChatColor.GRAY + " - Показати поточне засвоєння");
        player.sendMessage(ChatColor.YELLOW + "/mastery set <1-100>" + ChatColor.GRAY + " - Встановити засвоєння");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Перший аргумент - підкоманди
            List<String> subcommands = Arrays.asList("set", "info");
            String input = args[0].toLowerCase();

            for (String subcommand : subcommands) {
                if (subcommand.startsWith(input)) {
                    completions.add(subcommand);
                }
            }
        } else if (args.length == 2) {
            String subcommand = args[0].toLowerCase();

            if (subcommand.equals("set")) {
                // Пропозиції значень для set
                List<String> values = Arrays.asList("1", "10", "25", "50", "75", "100");
                String input = args[1];

                for (String value : values) {
                    if (value.startsWith(input)) {
                        completions.add(value);
                    }
                }

                // Також додати поточне значення + 10, 25, 50
                if (sender instanceof Player) {
                    Player player = (Player) sender;
                    Beyonder beyonder = beyonderManager.GetBeyonder(player.getUniqueId());

                    if (beyonder != null) {
                        int current = beyonder.getMastery();
                        if (current < 100) {
                            String[] suggestions = {
                                    String.valueOf(Math.min(100, current + 10)),
                                    String.valueOf(Math.min(100, current + 25)),
                                    String.valueOf(Math.min(100, current + 50))
                            };

                            for (String suggestion : suggestions) {
                                if (suggestion.startsWith(input) && !completions.contains(suggestion)) {
                                    completions.add(suggestion);
                                }
                            }
                        }
                    }
                }
            }
        }
        return completions;
    }
}
