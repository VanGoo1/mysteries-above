package me.vangoo.infrastructure.schedulers;

import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.infrastructure.ui.AbilityMenu;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Infrastructure: Scheduler для автоматичного оновлення ability menu item в інвентарі
 * <p>
 * Відповідальності:
 * - Перевіряти кожні 20 тіків (1 секунда) наявність ability menu item у гравців
 * - Оновлювати інформацію в item при змінах данних beyonder'а
 * - Зберігати snapshot данних для порівняння та уникнення зайвих оновлень
 */
public class AbilityMenuItemUpdater {
    private final Logger LOGGER;

    private final Plugin plugin;
    private final BeyonderService beyonderService;
    private final AbilityMenu abilityMenu;

    private final Map<UUID, BeyonderSnapshot> lastKnownData;

    private BukkitTask updateTask;

    private static final long UPDATE_INTERVAL_TICKS = 20L; // 1 секунда

    public AbilityMenuItemUpdater(
            Plugin plugin,
            BeyonderService beyonderService,
            AbilityMenu abilityMenu) {
        this.plugin = plugin;
        this.beyonderService = beyonderService;
        this.abilityMenu = abilityMenu;
        this.lastKnownData = new HashMap<>();
        this.LOGGER = plugin.getLogger();
    }

    /**
     * Запустити scheduler
     */
    public void start() {
        if (updateTask != null && !updateTask.isCancelled()) {
            return; // Вже працює
        }

        updateTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::updateAllMenuItems,
                UPDATE_INTERVAL_TICKS, // Початкова затримка
                UPDATE_INTERVAL_TICKS  // Період
        );

        LOGGER.info("AbilityMenuItemUpdater started (1 second interval)");
    }

    /**
     * Зупинити scheduler
     */
    public void stop() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
            updateTask = null;
        }

        // Очистити збережені дані
        lastKnownData.clear();

        LOGGER.info("AbilityMenuItemUpdater stopped");
    }

    /**
     * Оновити menu items для всіх онлайн гравців
     */
    private void updateAllMenuItems() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerMenuItem(player);
        }
    }

    /**
     * Оновити menu item для конкретного гравця
     */
    private void updatePlayerMenuItem(Player player) {
        Beyonder beyonder = beyonderService.getBeyonder(player.getUniqueId());

        if (beyonder == null) {
            // Гравець не є beyonder'ом - видаляємо snapshot якщо був
            lastKnownData.remove(player.getUniqueId());
            return;
        }

        // Створюємо snapshot поточних данних
        BeyonderSnapshot currentSnapshot = BeyonderSnapshot.from(beyonder);
        BeyonderSnapshot lastSnapshot = lastKnownData.get(player.getUniqueId());

        // Перевіряємо чи змінились дані
        if (lastSnapshot != null && lastSnapshot.equals(currentSnapshot)) {
            return; // Дані не змінились, оновлення не потрібне
        }

        // Шукаємо ability menu item в інвентарі
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);

            if (item != null && abilityMenu.isAbilityMenu(item)) {
                // Знайшли menu item - оновлюємо його
                ItemStack updatedItem = abilityMenu.getMenuItem(beyonder);
                player.getInventory().setItem(i, updatedItem);

                // Зберігаємо snapshot
                lastKnownData.put(player.getUniqueId(), currentSnapshot);
                return; // Оновили, можна виходити
            }
        }
    }

    /**
     * Примусове оновлення menu item для гравця (викликається при важливих змінах)
     */
    public void forceUpdate(Player player) {
        updatePlayerMenuItem(player);
    }

    /**
     * Перевірити чи scheduler працює
     */
    public boolean isRunning() {
        return updateTask != null && !updateTask.isCancelled();
    }

    /**
     * Snapshot данних beyonder'а для порівняння
     */
    private static class BeyonderSnapshot {
        private final String pathwayName;
        private final int sequenceLevel;
        private final double masteryValue;
        private final int sanityLoss;

        private BeyonderSnapshot(String pathwayName, int sequenceLevel, double masteryValue, int sanityLoss) {
            this.pathwayName = pathwayName;
            this.sequenceLevel = sequenceLevel;
            this.masteryValue = masteryValue;
            this.sanityLoss = sanityLoss;
        }

        public static BeyonderSnapshot from(Beyonder beyonder) {
            return new BeyonderSnapshot(
                    beyonder.getPathway().getName(),
                    beyonder.getSequenceLevel(),
                    beyonder.getMasteryValue(),
                    beyonder.getSanityLossScale()
            );
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BeyonderSnapshot that)) return false;

            // Використовуємо Double.compare для коректного порівняння дробових чисел
            return sequenceLevel == that.sequenceLevel &&
                    Double.compare(that.masteryValue, masteryValue) == 0 &&
                    sanityLoss == that.sanityLoss &&
                    Objects.equals(pathwayName, that.pathwayName);
        }

        @Override
        public int hashCode() {
            // Оновлено логіку хешування для double
            return Objects.hash(pathwayName, sequenceLevel, masteryValue, sanityLoss);
        }
    }
}