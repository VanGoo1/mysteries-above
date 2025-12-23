package me.vangoo.presentation.commands;

import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.entities.Beyonder;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class RampagerCommand implements CommandExecutor, TabCompleter {

    private final BeyonderService beyonderService;

    public RampagerCommand(BeyonderService beyonderService) {
        this.beyonderService = beyonderService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Ця команда доступна тільки для гравців!");
            return true;
        }

        Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());
        if (beyonder == null) {
            player.sendMessage(ChatColor.RED + "Ви не є потойбічним");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatColor.GRAY + "Sanity loss scale: " + ChatColor.GOLD + beyonder.getSanityLossScale());
            player.sendMessage(ChatColor.GRAY + "Використання: /rampager set <0-100>");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("set")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Використання: /rampager set <значення>");
                player.sendMessage(ChatColor.GRAY + "Значення має бути від 0 до 100");
                return true;
            }

            int value;
            try {
                value = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Невірне число: " + args[1]);
                return true;
            }

            if (value < 0 || value > 100) {
                player.sendMessage(ChatColor.RED + "Значення має бути від 0 до 100");
                return true;
            }

            int current = beyonder.getSanityLossScale();
            int delta = value - current;

            try {
                if (delta > 0) {
                    beyonder.increaseSanityLoss(delta);
                } else if (delta < 0) {
                    beyonder.decreaseSanityLoss(-delta);
                } else {
                    player.sendMessage(ChatColor.YELLOW + "Значення вже встановлено: " + value);
                    return true;
                }

                beyonderService.updateBeyonder(beyonder);
                player.sendMessage(ChatColor.GREEN + "Sanity loss scale встановлено: " + value);
            } catch (IllegalArgumentException ex) {
                player.sendMessage(ChatColor.RED + "Не вдалося змінити значення: " + ex.getMessage());
            }
            return true;
        }

        // Невідома підкоманда
        player.sendMessage(ChatColor.RED + "Невідома підкоманда: " + args[0]);
        player.sendMessage(ChatColor.GRAY + "Доступні: set");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> opts = Arrays.asList("set");
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return opts.stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            List<String> suggestions = new ArrayList<>(Arrays.asList("0", "10", "25", "50", "75", "100"));

            if (sender instanceof Player player) {
                try {
                    var beyonder = beyonderService.getBeyonder(player.getUniqueId());
                    if (beyonder != null) {
                        String current = String.valueOf(beyonder.getSanityLossScale());
                        if (!suggestions.contains(current)) {
                            suggestions.add(0, current);
                        }
                    }
                } catch (Exception ignored) {  }
            }

            String prefix = args[1].toLowerCase(Locale.ROOT);
            return suggestions.stream()
                    .filter(s -> s.startsWith(prefix))
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}
