package me.vangoo.commands;

import me.vangoo.beyonders.BeyonderManager;
import me.vangoo.potions.PotionManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class BeyonderCommand implements CommandExecutor {
    private final PotionManager potionManager;

    public BeyonderCommand(PotionManager potionManager) {
        this.potionManager = potionManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return false;

        if (args.length == 0) {
            ItemStack item = potionManager.getPotions().getFirst().returnPotionForSequence(9);
            player.getInventory().addItem(item);
        }
        return true;
    }
}
