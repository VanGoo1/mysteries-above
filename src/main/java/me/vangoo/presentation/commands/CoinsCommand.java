package me.vangoo.presentation.commands;

import me.vangoo.application.services.WalletService;
import me.vangoo.domain.market.PoundMoney;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

/**
 * /coins give <player> <pounds> [coppets]
 * Адмін-видача валюти. Свідомо НЕ під /gathering: монети — загальна валюта,
 * інші механіки можуть використовувати її поза зборами.
 */
public class CoinsCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = ChatColor.GOLD + "[Coins] " + ChatColor.RESET;
    private static final String USAGE = "Використання: /coins give <гравець> <фунти> [коппети]";

    private final WalletService walletService;

    public CoinsCommand(WalletService walletService) {
        this.walletService = walletService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 3 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(PREFIX + ChatColor.RED + USAGE);
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Гравець не онлайн: " + args[1]);
            return true;
        }
        int pounds;
        int coppets = 0;
        try {
            pounds = Integer.parseInt(args[2]);
            if (args.length >= 4) {
                coppets = Integer.parseInt(args[3]);
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Невірне число. " + USAGE);
            return true;
        }
        if (pounds < 0 || coppets < 0 || (pounds == 0 && coppets == 0)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Сума має бути додатною");
            return true;
        }
        PoundMoney money = PoundMoney.of(pounds, coppets);
        walletService.give(target, money);
        sender.sendMessage(PREFIX + ChatColor.GREEN
                + String.format("Видано %s гравцю %s", money.format(), target.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("give");
        }
        if (args.length == 2) {
            String lower = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(lower))
                    .collect(Collectors.toList());
        }
        if (args.length == 3) {
            return List.of("1", "5", "10");
        }
        if (args.length == 4) {
            return List.of("0", "10", "19");
        }
        return List.of();
    }
}
