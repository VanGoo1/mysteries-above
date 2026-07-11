package me.vangoo.presentation.commands;

import me.vangoo.application.services.GatheringService;
import me.vangoo.domain.market.GatheringPhase;
import me.vangoo.infrastructure.ui.MarketMenu;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * /gathering join|menu — гравцям (join — ціль клікабельного [Прийти]);
 * /gathering start|stop — адмінам (перевірка права в коді: команда відкрита для всіх).
 */
public class GatheringCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = ChatColor.DARK_PURPLE + "[Збори] " + ChatColor.RESET;

    private final GatheringService gatheringService;
    private final MarketMenu marketMenu;

    public GatheringCommand(GatheringService gatheringService, MarketMenu marketMenu) {
        this.gatheringService = gatheringService;
        this.marketMenu = marketMenu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(PREFIX + ChatColor.GRAY + "Використання: /gathering <join|menu>");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "start" -> {
                if (!sender.hasPermission("mysteriesabove.admin")) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Недостатньо прав.");
                    return true;
                }
                if (gatheringService.phase() != GatheringPhase.IDLE) {
                    sender.sendMessage(PREFIX + ChatColor.RED
                            + "Збір уже йде (фаза: " + gatheringService.phase() + ").");
                    return true;
                }
                gatheringService.announce();
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Збір оголошено.");
            }
            case "stop" -> {
                if (!sender.hasPermission("mysteriesabove.admin")) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Недостатньо прав.");
                    return true;
                }
                gatheringService.forceCloseIfActive();
                sender.sendMessage(PREFIX + ChatColor.GREEN + "Збір закрито.");
            }
            case "join" -> {
                if (sender instanceof Player player) {
                    gatheringService.join(player);
                } else {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Ця команда доступна лише гравцям.");
                }
            }
            case "menu" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(PREFIX + ChatColor.RED + "Ця команда доступна лише гравцям.");
                    return true;
                }
                if (!gatheringService.isOpenParticipant(player)) {
                    player.sendMessage(PREFIX + ChatColor.RED + "Ринок зараз не відкритий для вас.");
                    return true;
                }
                if (!gatheringService.hasBeenBriefed(player.getUniqueId())) {
                    player.sendMessage(PREFIX + ChatColor.RED + "Спершу вислухайте Посередника.");
                    return true;
                }
                marketMenu.openMain(player);
            }
            default -> sender.sendMessage(PREFIX + ChatColor.GRAY
                    + "Використання: /gathering <join|menu>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        List<String> options = new ArrayList<>(List.of("join", "menu"));
        if (sender.hasPermission("mysteriesabove.admin")) {
            options.add("start");
            options.add("stop");
        }
        String lower = args[0].toLowerCase();
        return options.stream().filter(o -> o.startsWith(lower)).toList();
    }
}
