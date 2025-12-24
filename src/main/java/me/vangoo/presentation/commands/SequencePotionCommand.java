package me.vangoo.presentation.commands;

import me.vangoo.domain.PathwayPotions;
import me.vangoo.application.services.PotionManager;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class SequencePotionCommand implements CommandExecutor, TabCompleter {
    private final PotionManager potionManager;
    private static final String PREFIX = ChatColor.DARK_PURPLE + "[Potion] " + ChatColor.RESET;

    public SequencePotionCommand(PotionManager potionManager) {
        this.potionManager = potionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Ця команда доступна тільки для гравців!");
            return true;
        }

        try {
            switch (args.length) {
                case 0 -> handleDefaultPotion(player);
                case 1 -> handlePathwayPotion(player, args[0], Sequence.starter());
                case 2 -> handlePathwayWithSequence(player, args[0], args[1]);
                default -> sendUsageMessage(player);
            }
        } catch (Exception e) {
            player.sendMessage(PREFIX + ChatColor.RED + "Сталася помилка: " + e.getMessage());
        }

        return true;
    }

    private void handleDefaultPotion(Player player) {
        List<PathwayPotions> potions = potionManager.getPotions();
        if (potions.isEmpty()) {
            player.sendMessage(PREFIX + ChatColor.RED + "Немає доступних pathway!");
            return;
        }

        String pathwayName = potions.get(0).getPathway().getName();
        givePotion(player, pathwayName, Sequence.starter());
    }

    private void handlePathwayPotion(Player player, String pathwayName, Sequence sequence) {
        try {
            givePotion(player, pathwayName, sequence);
            player.sendMessage(PREFIX + ChatColor.GREEN +
                    String.format("Видано зілля %s послідовності %d!", pathwayName, sequence.level()));
        } catch (IllegalArgumentException e) {
            player.sendMessage(PREFIX + ChatColor.RED + "Pathway не знайдено: " + pathwayName);
        }
    }

    private void handlePathwayWithSequence(Player player, String pathwayName, String sequenceStr) {
        Sequence sequence;
        try {
            sequence = Sequence.of(Integer.parseInt(sequenceStr));
        } catch (IllegalArgumentException e) {
            player.sendMessage(PREFIX + ChatColor.RED + "Sequence має бути від 0 до 9!");
            return;
        }
        handlePathwayPotion(player, pathwayName, sequence);
    }

    private void givePotion(Player player, String pathwayName, Sequence sequence) {
        ItemStack potion = potionManager.createPotionItem(pathwayName, sequence);

        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItem(player.getLocation(), potion);
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Інвентар повний! Зілля випало на землю.");
        } else {
            player.getInventory().addItem(potion);
        }
    }

    private void sendUsageMessage(Player player) {
        player.sendMessage(PREFIX + ChatColor.YELLOW + "Використання: /potion [pathway] [sequence]");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return potionManager.getPotions().stream()
                    .map(p -> p.getPathway().getName())
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            return IntStream.rangeClosed(0, 9)
                    .mapToObj(String::valueOf)
                    .filter(seq -> seq.startsWith(args[1]))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}