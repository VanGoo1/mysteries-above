package me.vangoo.presentation.commands;

import me.vangoo.infrastructure.structures.LootGenerationService;
import me.vangoo.infrastructure.structures.StructurePopulator;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.List;

public class StructureCommand implements CommandExecutor, TabCompleter {

    private final StructurePopulator structurePopulator;
    private final LootGenerationService lootService;

    public StructureCommand(StructurePopulator structurePopulator, LootGenerationService lootService) {
        this.structurePopulator = structurePopulator;
        this.lootService = lootService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§5[Structure] §f/structure <place|list|tag|checktag>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "place" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§cТільки для гравців");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§c/structure place <id>");
                    return true;
                }

                // Використовуємо Populator для розміщення
                if (structurePopulator.placeStructureManually(args[1], p.getLocation())) {
                    sender.sendMessage("§aСтруктуру розміщено!");
                } else {
                    sender.sendMessage("§cПомилка: Структуру не знайдено або виник збій.");
                }
            }
            case "list" -> {
                sender.sendMessage("§6=== Структури ===");
                // Беремо список ID з Populator
                structurePopulator.getStructureIds().forEach(id ->
                        sender.sendMessage("§e• " + id));
            }
            case "tag" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§cТільки для гравців");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§c/structure tag <назва>");
                    return true;
                }
                Block block = p.getTargetBlockExact(6);
                if (block != null && block.getState() instanceof Chest chest) {
                    // Використовуємо LootService для встановлення тегу
                    lootService.setChestTag(chest, args[1]);
                    sender.sendMessage("§aТег встановлено: §e" + args[1]);
                } else {
                    sender.sendMessage("§cДивіться на скриню!");
                }
            }
            case "checktag" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("§cТільки для гравців");
                    return true;
                }
                Block block = p.getTargetBlockExact(6);
                if (block != null && block.getState() instanceof Chest chest) {
                    // Використовуємо LootService для читання тегу
                    String tag = lootService.getChestTag(chest);
                    if (tag != null) {
                        sender.sendMessage("§aТег: §e" + tag);
                    } else {
                        sender.sendMessage("§eНемає тегу");
                    }
                } else {
                    sender.sendMessage("§cДивіться на скриню!");
                }
            }
            default -> sender.sendMessage("§cНевідома підкоманда.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("place", "list", "tag", "checktag");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("place")) {
            // Динамічний список структур
            return structurePopulator.getStructureIds().stream().toList();
        }
        return List.of();
    }
}