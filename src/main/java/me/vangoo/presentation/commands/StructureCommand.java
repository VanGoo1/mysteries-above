package me.vangoo.presentation.commands;

import me.vangoo.infrastructure.structures.LootGenerationService;
import me.vangoo.infrastructure.structures.StructurePopulator;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

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
            sender.sendMessage("§5[Structure] §f/structure <place|list|stats|clear>");
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

                if (structurePopulator.placeStructureManually(args[1], p.getLocation())) {
                    sender.sendMessage("§aСтруктуру розміщено!");
                } else {
                    sender.sendMessage("§cПомилка: Структуру не знайдено або виник збій.");
                }
            }
            case "list" -> {
                sender.sendMessage("§6=== Структури ===");
                structurePopulator.getStructureIds().forEach(id ->
                        sender.sendMessage("§e• " + id));
            }
            // === НОВІ КОМАНДИ ===
            case "stats" -> {
                Map<String, Integer> spawnCounts = structurePopulator.getSpawnCounts();

                if (spawnCounts.isEmpty()) {
                    sender.sendMessage("§eЖодна структура ще не заспавнилась.");
                    return true;
                }

                sender.sendMessage("§6=== Статистика спавнів ===");
                spawnCounts.forEach((id, count) ->
                        sender.sendMessage("§f  " + id + ": §e" + count + " разів")
                );

                int total = spawnCounts.values().stream().mapToInt(Integer::intValue).sum();
                sender.sendMessage("§aВсього структур: §e" + total);
            }
            case "clear" -> {
                if (args.length < 2) {
                    sender.sendMessage("§c/structure clear <id|all>");
                    return true;
                }

                String target = args[1].toLowerCase();

                if (target.equals("all")) {
                    structurePopulator.getStructureIds().forEach(structurePopulator::clearSpawnHistory);
                    sender.sendMessage("§aІсторію спавнів очищено для всіх структур.");
                } else {
                    if (structurePopulator.getStructureIds().contains(target)) {
                        structurePopulator.clearSpawnHistory(target);
                        sender.sendMessage("§aІсторію спавнів очищено для '" + target + "'.");
                    } else {
                        sender.sendMessage("§cСтруктуру '" + target + "' не знайдено.");
                    }
                }
            }
            default -> sender.sendMessage("§cНевідома підкоманда.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("place", "list", "stats", "clear")
                    .stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("place")) {
                return structurePopulator.getStructureIds().stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }

            if (args[0].equalsIgnoreCase("clear")) {
                List<String> options = new ArrayList<>(structurePopulator.getStructureIds());
                options.add("all");
                return options.stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return List.of();
    }
}