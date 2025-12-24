package me.vangoo.presentation.commands;

import me.vangoo.domain.*;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.entities.Pathway;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.infrastructure.ui.AbilityMenu;
import me.vangoo.infrastructure.abilities.AbilityItemFactory;
import me.vangoo.application.services.BeyonderService;
import me.vangoo.application.services.PathwayManager;
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

    private final BeyonderService beyonderService;
    private final PathwayManager pathwayManager;
    private final AbilityMenu abilityMenu;
    private final AbilityItemFactory abilityItemFactory;

    private static final String PREFIX = ChatColor.DARK_PURPLE + "[Pathway] " + ChatColor.RESET;

    public PathwayCommand(BeyonderService beyonderService, PathwayManager pathwayManager, AbilityMenu abilityMenu,
                          AbilityItemFactory abilityItemFactory) {
        this.beyonderService = beyonderService;
        this.pathwayManager = pathwayManager;
        this.abilityMenu = abilityMenu;
        this.abilityItemFactory = abilityItemFactory;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Використання: /pw <set|remove> ...");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "set" -> {
                if (args.length != 4) {
                    sender.sendMessage(
                            PREFIX + ChatColor.RED + "Використання: /pw set <гравець> <шлях> <послідовність>");
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

                if (beyonderService.getBeyonder(target.getUniqueId()) != null) {
                    clearBeyonderData(target);
                }

                int sequence = Integer.parseInt(args[3]);
                Beyonder beyonder = new Beyonder(target.getUniqueId(), Sequence.of(sequence), path);
                beyonderService.createBeyonder(beyonder);
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
        Beyonder beyonder = beyonderService.getBeyonder(target.getUniqueId());
        if (beyonder == null)
            return;

        PlayerInventory inv = target.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && (abilityMenu.isAbilityMenu(item)
                    || abilityItemFactory.getAbilityFromItem(item, beyonder) != null)) {
                inv.setItem(i, null);
            }
        }
        beyonderService.removeBeyonder(target.getUniqueId());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], Arrays.asList("set", "remove"), new ArrayList<>());
        }
        String sub = args[0].toLowerCase();
        if (args.length == 2) {
            if (sub.equals("set") || sub.equals("remove"))
                return StringUtil.copyPartialMatches(args[1],
                        Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), new ArrayList<>());
        }
        if (args.length == 3) {
            if (sub.equals("set")) {
                List<String> options = pathwayManager.getAllPathwayNames().stream().toList();
                return StringUtil.copyPartialMatches(args[2], options, new ArrayList<>());
            }
        }
        if (args.length == 4 && sub.equals("set")) {
            return StringUtil.copyPartialMatches(args[3],
                    IntStream.rangeClosed(0, 9).mapToObj(String::valueOf).toList(), new ArrayList<>());
        }
        return Collections.emptyList();
    }
}