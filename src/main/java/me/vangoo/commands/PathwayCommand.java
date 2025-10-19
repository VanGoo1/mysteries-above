package me.vangoo.commands;

import me.vangoo.domain.*;
import me.vangoo.managers.*;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.stream.IntStream;

public class PathwayCommand implements CommandExecutor, TabCompleter {

    private final PotionManager potionManager;
    private final BeyonderManager beyonderManager;
    private final PathwayManager pathwayManager;
    private final AbilityMenu abilityMenu;
    private final AbilityManager abilityManager;

    private static final String PREFIX = ChatColor.DARK_PURPLE + "[Pathway] " + ChatColor.RESET;

    public PathwayCommand(PotionManager potionManager, BeyonderManager beyonderManager, PathwayManager pathwayManager, AbilityMenu abilityMenu, AbilityManager abilityManager) {
        this.potionManager = potionManager;
        this.beyonderManager = beyonderManager;
        this.pathwayManager = pathwayManager;
        this.abilityMenu = abilityMenu;
        this.abilityManager = abilityManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Використання: /pw <get|set|remove> ...");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "get" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Тільки гравець може отримати зілля.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(PREFIX + ChatColor.RED + "Вкажіть назву шляху.");
                    return true;
                }
                int sequence = (args.length >= 3) ? Integer.parseInt(args[2]) : 9;
                potionManager.getPotionsPathway(args[1]).ifPresent(potions -> givePotion(player, potions, sequence));
                player.sendMessage(PREFIX + ChatColor.GREEN + "Зілля видано.");
            }
            case "set" -> {
                if (args.length != 4) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Використання: /pw set <гравець> <шлях> <послідовність>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Гравець не онлайн.");
                    return true;
                }
                Pathway path = pathwayManager.getPathway(args[2]);
                if (path == null) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Шлях не знайдено.");
                    return true;
                }

                // Якщо гравець вже Потойбічний, просто очищуємо його дані перед встановленням нових
                if (beyonderManager.GetBeyonder(target.getUniqueId()) != null) {
                    clearBeyonderData(target);
                }

                int sequence = Integer.parseInt(args[3]);
                List<Ability> abilities = new ArrayList<>();
                for (int i = 9; i >= sequence; i--) {
                    abilities.addAll(path.GetAbilitiesForSequence(i));
                }

                Beyonder beyonder = new Beyonder(target.getUniqueId(), abilities);
                beyonder.setPathway(path);
                beyonder.setSequence(sequence);
                beyonder.updateMaxSpirituality();
                beyonder.setSpirituality(beyonder.getMaxSpirituality());

                beyonderManager.AddBeyonder(beyonder);
                beyonderManager.createSpiritualityBar(target, beyonder);
                abilityMenu.giveAbilityMenuItemToPlayer(target);
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Шлях для " + target.getName() + " встановлено.");
            }
            case "remove" -> {
                if (args.length != 2) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Використання: /pw remove <гравець>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Гравець не онлайн.");
                    return true;
                }
                clearBeyonderData(target);
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Шлях для " + target.getName() + " видалено.");
            }
            default -> sender.sendMessage(PREFIX + ChatColor.GRAY + "Невідома підкоманда. Доступно: get, set, remove.");
        }
        return true;
    }

    private void clearBeyonderData(Player target) {
        Beyonder beyonder = beyonderManager.GetBeyonder(target.getUniqueId());
        if (beyonder == null) return;

        PlayerInventory inv = target.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && (abilityMenu.isAbilityMenu(item) || abilityManager.GetAbilityFromItem(item, beyonder) != null)) {
                inv.setItem(i, null);
            }
        }
        beyonderManager.RemoveBeyonder(target.getUniqueId());
    }

    private void givePotion(Player player, PathwayPotions pathwayPotions, int sequence) {
        ItemStack potion = pathwayPotions.returnPotionForSequence(sequence);
        if (player.getInventory().firstEmpty() == -1) {
            player.getWorld().dropItem(player.getLocation(), potion);
        } else {
            player.getInventory().addItem(potion);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // Логіка автодоповнення залишена, оскільки вона дуже корисна
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], Arrays.asList("get", "set", "remove"), new ArrayList<>());
        }
        String sub = args[0].toLowerCase();
        if (args.length == 2) {
            if (sub.equals("get")) return StringUtil.copyPartialMatches(args[1], potionManager.getPotions().stream().map(p -> p.getPathway().getName()).toList(), new ArrayList<>());
            if (sub.equals("set") || sub.equals("remove")) return StringUtil.copyPartialMatches(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), new ArrayList<>());
        }
        if (args.length == 3) {
            if (sub.equals("get") || sub.equals("set")) {
                List<String> options = sub.equals("get")
                        ? IntStream.rangeClosed(0, 9).mapToObj(String::valueOf).toList()
                        : pathwayManager.getAllPathwayNames().stream().toList();
                return StringUtil.copyPartialMatches(args[2], options, new ArrayList<>());
            }
        }
        if (args.length == 4 && sub.equals("set")) {
            return StringUtil.copyPartialMatches(args[3], IntStream.rangeClosed(0, 9).mapToObj(String::valueOf).toList(), new ArrayList<>());
        }
        return Collections.emptyList();
    }
}