package me.vangoo.presentation.commands;

import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.infrastructure.creatures.CreatureSpawner;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** /creature spawn &lt;id&gt; [amount] — адмін-спавн кастомної істоти на місці гравця. */
public class CreatureCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = ChatColor.DARK_AQUA + "[Creature] " + ChatColor.RESET;

    private final Map<String, CreatureDefinition> registry;
    private final CreatureSpawner spawner;

    public CreatureCommand(Map<String, CreatureDefinition> registry, CreatureSpawner spawner) {
        this.registry = registry;
        this.spawner = spawner;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Лише для гравця.");
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("spawn")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Використання: /creature spawn <id> [кількість]");
            return true;
        }
        CreatureDefinition def = registry.get(args[1]);
        if (def == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Невідома істота: " + args[1]);
            return true;
        }
        int amount = 1;
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Невірна кількість: " + args[2]);
                return true;
            }
            if (amount < 1 || amount > 20) {
                sender.sendMessage(PREFIX + ChatColor.RED + "Кількість має бути від 1 до 20");
                return true;
            }
        }
        int spawned = 0;
        for (int i = 0; i < amount; i++) {
            if (spawner.spawn(def, player.getLocation()).isPresent()) spawned++;
        }
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Заспавнено " + spawned + "x " + args[1]);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(args[0], List.of("spawn"));
        }
        if (args.length == 2) {
            return filter(args[1], new ArrayList<>(registry.keySet()));
        }
        if (args.length == 3) {
            return filter(args[2], List.of("1", "3", "5", "10"));
        }
        return List.of();
    }

    private List<String> filter(String input, List<String> options) {
        String lower = input.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase().startsWith(lower)) out.add(o);
        }
        return out;
    }
}
