package me.vangoo.commands;

import me.vangoo.domain.Beyonder;
import me.vangoo.managers.BeyonderManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RampagerCommand implements CommandExecutor {


    private final BeyonderManager beyonderManager;

    public RampagerCommand(BeyonderManager beyonderManager) {
        this.beyonderManager = beyonderManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Ця команда доступна тільки для гравців!");
            return true;
        }

        Beyonder beyonder = beyonderManager.GetBeyonder(player.getUniqueId());

        if (beyonder == null) {
            player.sendMessage(ChatColor.RED + "Ви не є потойбічним");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("Sanity loss scale: " + beyonder.getSanityLossScale());
            return true;
        }

        if (args[0].equalsIgnoreCase("set")) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Використання: /rampager set <значення>");
                player.sendMessage(ChatColor.GRAY + "Значення має бути від 1 до 100");
                return true;
            }
            int value = Integer.parseInt(args[1]);
            beyonder.setSanityLossScale(value);
        }
        return true;
    }
}
