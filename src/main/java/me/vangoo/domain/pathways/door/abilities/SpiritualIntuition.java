package me.vangoo.domain.pathways.door.abilities;

import me.vangoo.domain.abilities.core.AbilityType;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.ToggleablePassiveAbility;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SpiritualIntuition extends ToggleablePassiveAbility {

    private static final int DETECTION_RANGE = 30; // Радіус відчуття

    // Відслідковування останніх виявлень для уникнення спаму
    // casterId -> (attackerId -> AbilityDetectionData)
    private final Map<UUID, Map<UUID, AbilityDetectionData>> recentDetections = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> activeSubscriptions = new ConcurrentHashMap<>();
    private static final long DETECTION_COOLDOWN_MS = 5000; // 5 секунд між повідомленнями про ту саму здібність від того самого гравця

    @Override
    public String getName() {
        return "[Пасивна] Духовна інтуїція";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return String.format(
                "Відчуваєте, коли інші потойбічні беруть АКТИВНІ здібності в руки в радіусі %d блоків. ",
                DETECTION_RANGE
        );
    }

    @Override
    public void onEnable(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        recentDetections.put(casterId, new ConcurrentHashMap<>());

        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.8f);

        // 1. Генеруємо унікальний ID для цієї "сесії" підписки
        UUID subscriptionKey = UUID.randomUUID();
        activeSubscriptions.put(casterId, subscriptionKey);

        // 2. Підписуємось, передаючи subscriptionKey замість ID гравця
        // Це "обманює" менеджер, створюючи окрему групу подій саме для цієї активації
        context.events().subscribeToTemporaryEvent(
                subscriptionKey, // <-- ВАЖЛИВО: не casterId
                PlayerItemHeldEvent.class,
                event -> shouldProcessEvent(context, event),
                event -> handleItemHeldChange(context, event),
                Integer.MAX_VALUE // Працює "вічно", поки не скасуємо
        );
    }
    @Override
    public void onDisable(IAbilityContext context) {
        UUID casterId = context.getCasterId();

        // 1. Отримуємо ключ підписки
        UUID subscriptionKey = activeSubscriptions.remove(casterId);

        if (subscriptionKey != null) {

            context.events().unsubscribeAll(subscriptionKey);
        }

        recentDetections.remove(casterId);
        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.0f);
    }

    @Override
    public void tick(IAbilityContext context) {
        if (context.getCasterPlayer() != null && context.getCasterPlayer().getTicksLived() % 20 == 0) {
            cleanupOldDetections(context.getCasterId());
        }
    }
    private boolean shouldProcessEvent(IAbilityContext context, PlayerItemHeldEvent event) {
        Player eventPlayer = event.getPlayer();
        UUID casterId = context.getCasterId();

        if (eventPlayer.getUniqueId().equals(casterId)) return false;
        if (!context.beyonder().isBeyonder(eventPlayer.getUniqueId())) return false;

        Player caster = context.getCasterPlayer();
        if (caster == null || !caster.isOnline()) return false;

        if (caster.getLocation().getWorld() != eventPlayer.getLocation().getWorld()) return false;

        return caster.getLocation().distance(eventPlayer.getLocation()) <= DETECTION_RANGE;
    }

    private void handleItemHeldChange(IAbilityContext context, PlayerItemHeldEvent event) {
        Player attacker = event.getPlayer();
        UUID attackerId = attacker.getUniqueId();
        UUID casterId = context.getCasterId();

        // Отримуємо beyonder атакуючого
        Beyonder attackerBeyonder = context.beyonder().getBeyonder(attackerId);
        if (attackerBeyonder == null) {
            return;
        }

        // Отримуємо предмет в НОВОМУ слоті (куди перемикається гравець)
        int newSlot = event.getNewSlot();
        ItemStack newItem = attacker.getInventory().getItem(newSlot);

        if (newItem == null) {
            return;
        }

        // Перевіряємо, чи це ability item
        String abilityName = extractAbilityName(newItem, attackerBeyonder);

        if (abilityName == null) {
            return; // Не ability item
        }

        // КРИТИЧНО: Перевіряємо тип здібності - ігноруємо passive abilities
        var ability = attackerBeyonder.getAbilityByName(abilityName);
        if (ability.isEmpty()) {
            return;
        }

        AbilityType abilityType = ability.get().getType();

        // ФІЛЬТР: Показуємо ТІЛЬКИ активні здібності
        if (abilityType != AbilityType.ACTIVE) {
            return; // Ігноруємо toggleable та permanent passives
        }

        // Перевіряємо, чи не спамимо тим самим повідомленням
        if (isRecentlyDetected(casterId, attackerId, abilityName)) {
            return;
        }

        // Реєструємо виявлення
        recordDetection(casterId, attackerId, abilityName);

        // Показуємо попередження
        showIntuitionWarning(context, attacker, abilityName);
    }

    /**
     * Витягуємо назву здібності з ItemStack
     */
    private String extractAbilityName(ItemStack item, Beyonder beyonder) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }

        var meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return null;
        }

        String displayName = ChatColor.stripColor(meta.getDisplayName());

        // Перевіряємо, чи є така здібність у beyonder'а
        var abilityOpt = beyonder.getAbilityByName(displayName);
        return abilityOpt.map(ability -> ability.getName()).orElse(null);
    }

    /**
     * Перевіряємо, чи не було нещодавно виявлено цю саму здібність
     */
    private boolean isRecentlyDetected(UUID casterId, UUID attackerId, String abilityName) {
        Map<UUID, AbilityDetectionData> casterDetections = recentDetections.get(casterId);
        if (casterDetections == null) {
            return false;
        }

        AbilityDetectionData data = casterDetections.get(attackerId);
        if (data == null) {
            return false;
        }

        long timePassed = System.currentTimeMillis() - data.timestamp;
        boolean isSameAbility = data.abilityName.equals(abilityName);

        return isSameAbility && timePassed < DETECTION_COOLDOWN_MS;
    }

    /**
     * Записуємо виявлення
     */
    private void recordDetection(UUID casterId, UUID attackerId, String abilityName) {
        Map<UUID, AbilityDetectionData> casterDetections = recentDetections.computeIfAbsent(
                casterId,
                k -> new ConcurrentHashMap<>()
        );

        casterDetections.put(
                attackerId,
                new AbilityDetectionData(abilityName, System.currentTimeMillis())
        );
    }

    /**
     * Показуємо попередження в actionbar
     */
    private void showIntuitionWarning(IAbilityContext context, Player attacker, String abilityName) {
        UUID casterId = context.getCasterId();
        if (casterId == null || !context.playerData().isOnline(casterId)) {
            return;
        }

        UUID caster = context.getCasterId();
        if (caster == null) return;

        // Розраховуємо відстань для інтенсивності попередження
        double distance = context.playerData().getCurrentLocation(casterId).distance(attacker.getLocation());
        ChatColor chatColor = getWarningColor(distance);

        // Формуємо повідомлення
        String message = chatColor + "⚠ " + ChatColor.WHITE + attacker.getName() +
                chatColor + " готує " + ChatColor.YELLOW + abilityName;

        // Показуємо в actionbar
        context.messaging().sendMessageToActionBar(casterId, Component.text(message));

        // Звуковий ефект (тихіший для далеких)
        float volume = (float) (1.0 - (distance / DETECTION_RANGE));
        if (volume < 0.1f) volume = 0.1f;

        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_NOTE_BLOCK_BELL, volume, 1.8f);

        // Партикли навколо кастера (тільки якщо близько)
        if (distance <= 15) { // Збільшив трохи радіус візуалізації
            // Конвертуємо ChatColor в Bukkit Color для партиклів
            org.bukkit.Color particleColor;
            if (chatColor == ChatColor.RED) {
                particleColor = org.bukkit.Color.RED;
            } else if (chatColor == ChatColor.GOLD) {
                particleColor = org.bukkit.Color.ORANGE;
            } else {
                particleColor = org.bukkit.Color.YELLOW;
            }

            // Викликаємо новий метод
            context.effects().playAlertHalo(context.playerData().getCurrentLocation(casterId), particleColor);
        }
    }

    /**
     * Вибираємо колір попередження залежно від відстані
     */
    private ChatColor getWarningColor(double distance) {
        if (distance <= 10) {
            return ChatColor.RED; // Дуже близько - червоний
        } else if (distance <= 20) {
            return ChatColor.GOLD; // Середня відстань - помаранчевий
        } else {
            return ChatColor.YELLOW; // Далеко - жовтий
        }
    }

    /**
     * Очищуємо старі записи
     */
    private void cleanupOldDetections(UUID casterId) {
        Map<UUID, AbilityDetectionData> casterDetections = recentDetections.get(casterId);
        if (casterDetections == null) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        casterDetections.entrySet().removeIf(
                entry -> (currentTime - entry.getValue().timestamp) > DETECTION_COOLDOWN_MS * 2
        );
    }

    @Override
    public void cleanUp() {
        recentDetections.clear();
    }

    /**
     * Внутрішній клас для зберігання даних про виявлення
     */
    private static class AbilityDetectionData {
        final String abilityName;
        final long timestamp;

        AbilityDetectionData(String abilityName, long timestamp) {
            this.abilityName = abilityName;
            this.timestamp = timestamp;
        }
    }
}