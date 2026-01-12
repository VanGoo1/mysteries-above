package me.vangoo.application.services;

import me.vangoo.domain.valueobjects.RecordedEvent;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CoreProtectHandler {

    private static CoreProtectAPI api;
    private static boolean initialized = false;

    private static void init() {
        if (initialized) return;
        Plugin plugin = Bukkit.getPluginManager().getPlugin("CoreProtect");
        if (plugin instanceof CoreProtect) {
            api = ((CoreProtect) plugin).getAPI();
        }
        initialized = true;
    }

    public static List<RecordedEvent> lookupEvents(Location center, int radius, int timeSeconds) {
        if (!initialized) init();
        if (api == null || !api.isEnabled()) return Collections.emptyList();

        List<RecordedEvent> events = new ArrayList<>();

        // 1. ТРАНЗАКЦІЇ (Речі)
        try {
            List<Integer> containerActions = new ArrayList<>();
            containerActions.add(4); // CONTAINER_TRANSACTION

            List<String[]> containerLookup = api.performLookup(
                    timeSeconds, null, null, null, null, containerActions, radius, center
            );

            if (containerLookup != null) {
                for (String[] data : containerLookup) {
                    CoreProtectAPI.ParseResult result = api.parseResult(data);
                    Location loc = new Location(center.getWorld(), result.getX(), result.getY(), result.getZ());

                    String description = formatContainerTransaction(result.getPlayer(), result, data);

                    events.add(new RecordedEvent(
                            loc,
                            description,
                            RecordedEvent.EventType.CONTAINER_TRANSACTION,
                            result.getTime() * 1000L
                    ));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 2. БЛОКИ (Ламання/Ставлення)
        try {
            List<Integer> blockActions = new ArrayList<>(Arrays.asList(0, 1));

            List<String[]> blockLookup = api.performLookup(
                    timeSeconds, null, null, null, null, blockActions, radius, center
            );

            if (blockLookup != null) {
                for (String[] data : blockLookup) {
                    CoreProtectAPI.ParseResult result = api.parseResult(data);
                    if (isIgnored(result.getType())) continue;

                    RecordedEvent.EventType type = mapType(result.getActionId());
                    Location loc = new Location(center.getWorld(), result.getX(), result.getY(), result.getZ());
                    String description = formatDescription(result, type);

                    events.add(new RecordedEvent(
                            loc,
                            description,
                            type,
                            result.getTime() * 1000L
                    ));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Сортуємо: нові зверху
        events.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

        return events;
    }

    private static String formatContainerTransaction(String user, CoreProtectAPI.ParseResult result, String[] data) {
        try {
            Material itemType = result.getType();
            String itemName = (itemType != null) ? cleanMaterialName(itemType.name()) : "предмет";

            // === ВИЗНАЧЕННЯ ДІЇ (+/-) ===
            // Індекс 7 = Action Data (0=Remove, 1=Add)
            String sign = "§f±";
            if (data.length > 7) {
                try {
                    int actionData = Integer.parseInt(data[7]);
                    if (actionData == 1) sign = "§a+";      // Зелений плюс (поклав)
                    else if (actionData == 0) sign = "§c-"; // Червоний мінус (взяв)
                } catch (NumberFormatException ignored) {}
            }

            // === ВИПРАВЛЕНИЙ ПОШУК КІЛЬКОСТІ ===
            int amount = 1;

            // Варіант А: Якщо API повертає довгий масив, кількість зазвичай на 10-му індексі
            if (data.length > 10 && data[10].matches("\\d+")) {
                amount = Integer.parseInt(data[10]);
            }
            // Варіант Б (Backup): Якщо Варіант А не спрацював або дав 1 (а це може бути помилка WorldID)
            // Ми скануємо кінець масиву. Якщо знаходимо число > 1, то це точно кількість.
            else {
                for (int i = data.length - 1; i > 7; i--) {
                    if (data[i].matches("\\d+")) {
                        int val = Integer.parseInt(data[i]);
                        // Фільтр:
                        // Якщо це ID світу (зазвичай 1) або Action (0/1), ми їх поки пропускаємо,
                        // АЛЕ якщо ми знаходимо число 64, 16, 5 і т.д. -> це точно кількість.
                        if (val > 1 && val <= 6400) {
                            amount = val;
                            break;
                        }
                    }
                }
            }

            // ДЕБАГ: Якщо бачиш в грі неправильні числа, подивись в консоль на цей рядок!
            // Bukkit.getLogger().info("[DEBUG DATA] " + Arrays.toString(data) + " -> Detected Amount: " + amount);

            return String.format("%s: %s%s x%d", user, sign, itemName, amount);

        } catch (Exception e) {
            return user + ": змінив вміст";
        }
    }

    private static String formatDescription(CoreProtectAPI.ParseResult result, RecordedEvent.EventType type) {
        String user = cleanName(result.getPlayer());
        String targetName = (result.getType() != null) ? cleanMaterialName(result.getType().name()) : "блок";

        return switch (type) {
            case BLOCK_BREAK -> "§c" + user + " зламав " + targetName;
            case BLOCK_PLACE -> "§a" + user + " поставив " + targetName;
            default -> user + " " + targetName;
        };
    }

    private static boolean isIgnored(Material mat) {
        if (mat == null) return true;
        return mat == Material.GRASS_BLOCK || mat == Material.TALL_GRASS || mat == Material.AIR ||
                mat == Material.CAVE_AIR || mat == Material.FIRE || mat == Material.SHORT_GRASS;
    }

    private static RecordedEvent.EventType mapType(int actionId) {
        return switch (actionId) {
            case 0 -> RecordedEvent.EventType.BLOCK_BREAK;
            case 1 -> RecordedEvent.EventType.BLOCK_PLACE;
            case 4 -> RecordedEvent.EventType.CONTAINER_TRANSACTION;
            default -> null;
        };
    }

    private static String cleanName(String name) {
        if (name == null) return "Хтось";
        if (name.startsWith("#")) name = name.substring(1);
        return name;
    }

    private static String cleanMaterialName(String name) {
        if (name == null) return "";
        return name.toLowerCase().replace("_", " ");
    }
}