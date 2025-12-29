package me.vangoo.presentation.commands;

import me.vangoo.application.services.LootGenerationService;
import me.vangoo.application.services.StructureService;
import me.vangoo.domain.valueobjects.Structure;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class StructureCommand implements CommandExecutor, TabCompleter {
    private final Plugin plugin;
    private final StructureService structureService;
    private final LootGenerationService lootService;
    private final NamespacedKey lootTagKey;
    private static final String PREFIX = ChatColor.DARK_PURPLE + "[Structure] " + ChatColor.RESET;

    public StructureCommand(Plugin plugin, StructureService structureService,
                            LootGenerationService lootService, NamespacedKey lootTagKey) {
        this.plugin = plugin;
        this.structureService = structureService;
        this.lootService = lootService;
        this.lootTagKey = lootTagKey;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "place" -> handlePlace(sender, args);
            case "list" -> handleList(sender);
            case "tag" -> handleTag(sender, args);
            case "checktag" -> handleCheckTag(sender);
            default -> sendUsage(sender);
        }

        return true;
    }

    private void handlePlace(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Тільки для гравців");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Використання: /structure place <id>");
            return;
        }

        String structureId = args[1];
        Structure structure = structureService.getStructure(structureId).orElse(null);

        if (structure == null) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Структура не знайдена: " + structureId);
            return;
        }

        Location location = player.getLocation();
        boolean success = structureService.placeStructure(structureId, location);

        if (success) {
            sender.sendMessage(PREFIX + ChatColor.GREEN + "Структура розміщена: " + structureId);

            // ⚠️ FIX: Заповнюємо сундуки після розміщення
            if (structure.hasLootTables()) {
                plugin.getServer().getScheduler().runTaskLater(
                        plugin,
                        () -> fillStructureChests(structure, location),
                        40L // 2 секунди затримки
                );
            }
        } else {
            sender.sendMessage(PREFIX + ChatColor.RED + "Не вдалося розмістити структуру");
        }
    }

    /**
     * Заповнення сундуків в структурі (скопійовано з StructureGenerator)
     */
    private void fillStructureChests(Structure structure, Location baseLocation) {
        int searchRadius = 32;

        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int y = -searchRadius; y <= searchRadius; y++) {
                for (int z = -searchRadius; z <= searchRadius; z++) {
                    Block block = baseLocation.getWorld().getBlockAt(
                            baseLocation.getBlockX() + x,
                            baseLocation.getBlockY() + y,
                            baseLocation.getBlockZ() + z
                    );

                    if (block.getState() instanceof Chest chest) {
                        fillChest(chest, structure);
                    }
                }
            }
        }
    }

    /**
     * Заповнення одного сундука
     */
    private void fillChest(Chest chest, Structure structure) {
        var container = chest.getPersistentDataContainer();

        if (!container.has(lootTagKey, PersistentDataType.STRING)) {
            return;
        }

        String tag = container.get(lootTagKey, PersistentDataType.STRING);

        structure.lootTables().stream()
                .filter(table -> table.chestTag().equals(tag))
                .findFirst()
                .ifPresent(lootTable -> {
                    container.remove(lootTagKey);
                    chest.update();
                    lootService.fillChest(chest.getBlock(), lootTable);
                });
    }

    private void handleTag(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Тільки для гравців");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Використання: /structure tag <назва_тегу>");
            return;
        }

        Block block = player.getTargetBlockExact(6);
        if (block == null || !(block.getState() instanceof Chest chest)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Ви повинні дивитися на скриню!");
            return;
        }

        String tagName = args[1];
        chest.getPersistentDataContainer().set(lootTagKey, PersistentDataType.STRING, tagName);
        chest.update();

        sender.sendMessage(PREFIX + ChatColor.GREEN + "Тег скрині встановлено: " + ChatColor.YELLOW + tagName);
        sender.sendMessage(ChatColor.GRAY + "Тепер збережіть структуру через Structure Block.");
    }

    private void handleCheckTag(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Тільки для гравців");
            return;
        }

        Block block = player.getTargetBlockExact(6);
        if (block == null || !(block.getState() instanceof Chest chest)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Ви повинні дивитися на скриню!");
            return;
        }

        if (!chest.getPersistentDataContainer().has(lootTagKey, PersistentDataType.STRING)) {
            sender.sendMessage(PREFIX + ChatColor.YELLOW + "Ця скриня не має тегів.");
            return;
        }

        String currentTag = chest.getPersistentDataContainer().get(lootTagKey, PersistentDataType.STRING);
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Поточний тег: " + ChatColor.GOLD + currentTag);
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(PREFIX + ChatColor.GOLD + "=== Структури ===");

        List<Structure> structures = new ArrayList<>(structureService.getAllStructures());

        if (structures.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Немає структур");
            return;
        }

        structures.stream()
                .sorted((a, b) -> a.id().compareTo(b.id()))
                .forEach(structure -> {
                    sender.sendMessage(
                            ChatColor.YELLOW + "• " + structure.id() +
                                    ChatColor.GRAY + " - " +
                                    ChatColor.WHITE + structure.nbtFileName()
                    );
                });

        sender.sendMessage(ChatColor.GRAY + "Всього: " + ChatColor.YELLOW + structures.size());
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(PREFIX + ChatColor.GOLD + "=== Команди структур ===");
        sender.sendMessage(ChatColor.YELLOW + "/structure place <id>" +
                ChatColor.GRAY + " - Розмістити структуру");
        sender.sendMessage(ChatColor.YELLOW + "/structure list" +
                ChatColor.GRAY + " - Список структур");
        sender.sendMessage(ChatColor.YELLOW + "/structure tag <tag>" +
                ChatColor.GRAY + " - Встановити тег сундука");
        sender.sendMessage(ChatColor.YELLOW + "/structure checktag" +
                ChatColor.GRAY + " - Перевірити тег сундука");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filterMatches(args[0], Arrays.asList("place", "list", "tag", "checktag"));
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("place")) {
            return filterMatches(args[1],
                    structureService.getAllStructures().stream()
                            .map(Structure::id)
                            .collect(Collectors.toList())
            );
        }

        return List.of();
    }

    private List<String> filterMatches(String input, List<String> options) {
        String lower = input.toLowerCase();
        return options.stream()
                .filter(option -> option.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}