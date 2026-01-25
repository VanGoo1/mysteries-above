package me.vangoo.domain.pathways.error.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class SuperiorObservation extends PermanentPassiveAbility {
    private static final double DETECTION_RADIUS = 10.0;
    private static final int CHECK_INTERVAL = 40; // Перевірка кожні 2 секунди

    // Цінні руди
    private static final Set<Material> VALUABLE_ORES = Set.of(
            Material.DIAMOND_ORE,
            Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE,
            Material.DEEPSLATE_EMERALD_ORE,
            Material.ANCIENT_DEBRIS,
            Material.GOLD_ORE,
            Material.DEEPSLATE_GOLD_ORE
    );

    // Цінні предмети в інвентарі
    private static final Set<Material> VALUABLE_ITEMS = Set.of(
            Material.DIAMOND,
            Material.EMERALD,
            Material.NETHERITE_INGOT,
            Material.NETHERITE_SCRAP,
            Material.ECHO_SHARD,
            Material.ELYTRA,
            Material.TOTEM_OF_UNDYING,
            Material.ENCHANTED_GOLDEN_APPLE,
            Material.NETHER_STAR
    );

    private int tickCounter = 0;
    private final Map<UUID, Long> lastAlertTime = new HashMap<>();
    private static final long ALERT_COOLDOWN_MS = 5000; // 5 секунд між повідомленнями

    @Override
    public String getName() {
        return "Покращена Спостережливість";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Ви інстинктивно відчуваєте цінні матеріали та предмети в радіусі 10 блоків.";
    }

    @Override
    public void tick(IAbilityContext context) {
        tickCounter++;

        if (tickCounter < CHECK_INTERVAL) {
            return;
        }

        tickCounter = 0;

        UUID casterId = context.getCasterId();
        Location casterLoc = context.getCasterLocation();

        // Перевірка кулдауну для повідомлень
        long currentTime = System.currentTimeMillis();
        Long lastAlert = lastAlertTime.get(casterId);
        if (lastAlert != null && currentTime - lastAlert < ALERT_COOLDOWN_MS) {
            return;
        }

        // Збір інформації про цінності
        List<String> detectedItems = new ArrayList<>();

        // 1. Перевірка цінних руд навколо
        int oreCount = scanForValuableOres(casterLoc);
        if (oreCount > 0) {
            detectedItems.add(ChatColor.GOLD + "Цінні руди поблизу (" + oreCount + ")");
        }

        // 2. Перевірка цінних предметів у гравців
        Map<String, Integer> playerTreasures = scanNearbyPlayers(context);
        for (Map.Entry<String, Integer> entry : playerTreasures.entrySet()) {
            detectedItems.add(ChatColor.AQUA + entry.getKey() + ": " + entry.getValue() + " цінностей");
        }

        // Відправка повідомлення
        if (!detectedItems.isEmpty()) {
            String message = ChatColor.YELLOW + "⚠ Виявлено: " + ChatColor.GRAY + String.join(", ", detectedItems);
            context.messaging().sendMessageToActionBar(casterId, Component.text(message));
            lastAlertTime.put(casterId, currentTime);
        }
    }

    /**
     * Сканує блоки навколо на наявність цінних руд
     */
    private int scanForValuableOres(Location center) {
        int count = 0;
        int radius = (int) DETECTION_RADIUS;

        World world = center.getWorld();
        if (world == null) return 0;

        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        // Оптимізований скан (не перевіряємо кожен блок, лише в сфері)
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    // Перевірка відстані (сфера замість куба)
                    if (x*x + y*y + z*z > radius*radius) continue;

                    Block block = world.getBlockAt(centerX + x, centerY + y, centerZ + z);

                    if (VALUABLE_ORES.contains(block.getType())) {
                        count++;
                    }
                }
            }
        }

        return count;
    }

    /**
     * Сканує інвентарі гравців поблизу
     * @return Map<Ім'я гравця, Кількість цінностей>
     */
    private Map<String, Integer> scanNearbyPlayers(IAbilityContext context) {
        Map<String, Integer> result = new HashMap<>();

        List<Player> nearbyPlayers = context.targeting().getNearbyPlayers(DETECTION_RADIUS);

        for (Player player : nearbyPlayers) {
            int treasureCount = countValuableItems(player);

            if (treasureCount > 0) {
                result.put(player.getName(), treasureCount);
            }
        }

        return result;
    }

    /**
     * Підраховує цінні предмети в інвентарі гравця
     */
    private int countValuableItems(Player player) {
        int count = 0;

        ItemStack[] contents = player.getInventory().getContents();

        for (ItemStack item : contents) {
            if (item != null && VALUABLE_ITEMS.contains(item.getType())) {
                count += item.getAmount();
            }
        }

        return count;
    }

    @Override
    public void cleanUp() {
        lastAlertTime.clear();
    }
}