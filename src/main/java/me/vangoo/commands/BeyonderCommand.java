package me.vangoo.commands;

import me.vangoo.managers.PotionManager;
import me.vangoo.domain.PathwayPotions;
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

public class BeyonderCommand implements CommandExecutor, TabCompleter {
    private final PotionManager potionManager;

    // Константи для магічних чисел
    private static final int DEFAULT_SEQUENCE = 9;
    private static final int MIN_SEQUENCE = 0;
    private static final int MAX_SEQUENCE = 9;

    // Повідомлення
    private static final String PREFIX = ChatColor.DARK_PURPLE + "[Beyonder] " + ChatColor.RESET;
    private static final String USAGE_MESSAGE = PREFIX + ChatColor.YELLOW +
            "Використання: /beyonder [pathway] [sequence]\n" +
            ChatColor.GRAY + "Приклади:\n" +
            ChatColor.WHITE + "  /beyonder " + ChatColor.GRAY + "- отримати зілля першого pathway sequence 9\n" +
            ChatColor.WHITE + "  /beyonder Error " + ChatColor.GRAY + "- отримати Error pathway sequence 9\n" +
            ChatColor.WHITE + "  /beyonder Visionary 5 " + ChatColor.GRAY + "- отримати Visionary pathway sequence 5";

    public BeyonderCommand(PotionManager potionManager) {
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
                case 1 -> handlePathwayPotion(player, args[0], DEFAULT_SEQUENCE);
                case 2 -> handlePathwayWithSequence(player, args[0], args[1]);
                default -> sendUsageMessage(player);
            }
        } catch (Exception e) {
            player.sendMessage(PREFIX + ChatColor.RED + "Сталася помилка при виконанні команди!");
        }

        return true;
    }

    private void handleDefaultPotion(Player player) {
        List<PathwayPotions> potions = potionManager.getPotions();
        if (potions.isEmpty()) {
            player.sendMessage(PREFIX + ChatColor.RED + "Немає доступних pathway!");
            return;
        }

        PathwayPotions firstPathway = potions.get(0);
        givePotion(player, firstPathway, DEFAULT_SEQUENCE);

        player.sendMessage(PREFIX + ChatColor.GREEN +
                String.format("Видано зілля %s послідовності %d!",
                        firstPathway.getPathway().getName(), DEFAULT_SEQUENCE));
    }

    private void handlePathwayPotion(Player player, String pathwayName, int sequence) {
        Optional<PathwayPotions> pathwayOpt = potionManager.getPotionsPathway(pathwayName);

        if (pathwayOpt.isEmpty()) {
            sendPathwayNotFoundMessage(player, pathwayName);
            return;
        }

        PathwayPotions pathway = pathwayOpt.get();
        givePotion(player, pathway, sequence);

        player.sendMessage(PREFIX + ChatColor.GREEN +
                String.format("Видано зілля %s sequence %d!",
                        pathway.getPathway().getName(), sequence));
    }

    private void handlePathwayWithSequence(Player player, String pathwayName, String sequenceStr) {
        // Валідація sequence
        if (!isValidSequence(sequenceStr)) {
            player.sendMessage(PREFIX + ChatColor.RED +
                    String.format("Некоректний sequence! Має бути число від %d до %d",
                            MIN_SEQUENCE, MAX_SEQUENCE));
            return;
        }

        int sequence = Integer.parseInt(sequenceStr);
        handlePathwayPotion(player, pathwayName, sequence);
    }

    private void givePotion(Player player, PathwayPotions pathwayPotions, int sequence) {
        ItemStack potion = pathwayPotions.returnPotionForSequence(sequence);

        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItem(player.getLocation(), potion);
            player.sendMessage(PREFIX + ChatColor.YELLOW +
                    "Інвентар повний! Зілля випало на землю.");
        } else {
            player.getInventory().addItem(potion);
        }
    }

    private boolean isValidSequence(String sequenceStr) {
        try {
            int sequence = Integer.parseInt(sequenceStr);
            return sequence >= MIN_SEQUENCE && sequence <= MAX_SEQUENCE;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void sendPathwayNotFoundMessage(Player player, String pathwayName) {
        List<String> availablePathways = potionManager.getPotions().stream()
                .map(p -> p.getPathway().getName())
                .collect(Collectors.toList());

        player.sendMessage(PREFIX + ChatColor.RED +
                String.format("Pathway '%s' не знайдено!", pathwayName));

        if (!availablePathways.isEmpty()) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Доступні pathway: " +
                    ChatColor.WHITE + String.join(", ", availablePathways));
        }
    }

    private void sendUsageMessage(Player player) {
        player.sendMessage(USAGE_MESSAGE);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }

        switch (args.length) {
            case 1:
                // Автодоповнення для pathway names
                return potionManager.getPotions().stream()
                        .map(p -> p.getPathway().getName())
                        .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());

            case 2:
                // Автодоповнення для sequence numbers
                return IntStream.rangeClosed(MIN_SEQUENCE, MAX_SEQUENCE)
                        .mapToObj(String::valueOf)
                        .filter(seq -> seq.startsWith(args[1]))
                        .collect(Collectors.toList());

            default:
                return new ArrayList<>();
        }
    }

}