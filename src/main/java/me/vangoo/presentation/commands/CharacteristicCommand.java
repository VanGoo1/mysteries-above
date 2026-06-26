package me.vangoo.presentation.commands;

import me.vangoo.application.services.PotionManager;
import me.vangoo.domain.PathwayPotions;
import me.vangoo.infrastructure.items.CharacteristicCodec;
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
 * /characteristic give <player> <pathway> <seq> [amount]
 * Тимчасовий адмін-канал видачі Характеристик (до появи джерел: апекс-істоти / смерть Beyonder).
 */
public class CharacteristicCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = ChatColor.LIGHT_PURPLE + "[Characteristic] " + ChatColor.RESET;

    private final CharacteristicCodec characteristicCodec;
    private final PotionManager potionManager;

    public CharacteristicCommand(CharacteristicCodec characteristicCodec, PotionManager potionManager) {
        this.characteristicCodec = characteristicCodec;
        this.potionManager = potionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(PREFIX + ChatColor.RED
                    + "Використання: /characteristic give <гравець> <шлях> <seq> [кількість]");
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(PREFIX + ChatColor.RED
                    + "Використання: /characteristic give <гравець> <шлях> <seq> [кількість]");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Гравець не онлайн: " + args[1]);
            return true;
        }

        PathwayPotions pathway = potionManager.getPotionsPathway(args[2]).orElse(null);
        if (pathway == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Невідомий шлях: " + args[2]);
            return true;
        }
        String pathwayName = pathway.getPathway().getName();

        int sequence;
        try {
            sequence = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Невірна послідовність: " + args[3]);
            return true;
        }
        if (sequence < 0 || sequence > 9) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Послідовність має бути від 0 до 9");
            return true;
        }

        int amount = 1;
        if (args.length >= 5) {
            try {
                amount = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Невірна кількість: " + args[4]);
                return true;
            }
            if (amount < 1 || amount > 64) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Кількість має бути від 1 до 64");
                return true;
            }
        }

        ItemStack item = characteristicCodec.create(pathwayName, sequence, amount);
        if (target.getInventory().firstEmpty() == -1) {
            target.getWorld().dropItem(target.getLocation(), item);
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Інвентар повний! Предмет випав на землю.");
        } else {
            target.getInventory().addItem(item);
        }

        sender.sendMessage(PREFIX + ChatColor.GREEN + String.format(
                "Видано %dx Характеристика[%s, Seq %d] гравцю %s", amount, pathwayName, sequence, target.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("give"));
        }
        if (args.length == 2) {
            return filter(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
        }
        if (args.length == 3) {
            return filter(args[2], potionManager.getPotions().stream()
                    .map(p -> p.getPathway().getName()).collect(Collectors.toList()));
        }
        if (args.length == 4) {
            return filter(args[3], Arrays.asList("9", "8", "7", "6", "5", "4", "3", "2", "1", "0"));
        }
        if (args.length == 5) {
            return filter(args[4], Arrays.asList("1", "8", "16", "32", "64"));
        }
        return List.of();
    }

    private List<String> filter(String input, List<String> options) {
        String lower = input.toLowerCase();
        return new ArrayList<>(options.stream()
                .filter(o -> o.toLowerCase().startsWith(lower))
                .collect(Collectors.toList()));
    }
}
