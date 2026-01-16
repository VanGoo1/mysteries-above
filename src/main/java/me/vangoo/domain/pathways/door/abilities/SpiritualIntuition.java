package me.vangoo.domain.pathways.door.abilities;

import me.vangoo.domain.abilities.core.AbilityType;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.ToggleablePassiveAbility;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
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

        context.sendMessageToCaster(ChatColor.DARK_PURPLE + "✦ Духовна інтуїція активована");
        context.playSoundToCaster(Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.8f);

        // Підписуємось на зміну предмета в руці
        subscribeToItemHeldChanges(context);
    }

    @Override
    public void onDisable(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        recentDetections.remove(casterId);

        context.sendMessageToCaster(ChatColor.YELLOW + "✦ Духовна інтуїція вимкнена");
        context.playSoundToCaster(Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.0f);
    }

    @Override
    public void tick(IAbilityContext context) {
        // Очищуємо старі записи раз на секунду (кожні 20 тіків)
        Player caster = context.getCasterPlayer();
        if (caster == null || caster.getTicksLived() % 20 != 0) {
            return;
        }

        cleanupOldDetections(context.getCasterId());
    }

    /**
     * Підписуємось на події зміни предмета в руці
     */
    private void subscribeToItemHeldChanges(IAbilityContext context) {
        UUID casterId = context.getCasterId();

        // Підписуємось на PlayerItemHeldEvent - коли гравець перемикає слоти хотбару
        context.subscribeToEvent(
                PlayerItemHeldEvent.class,
                event -> {
                    Player eventPlayer = event.getPlayer();

                    // Ігноруємо власні дії
                    if (eventPlayer.getUniqueId().equals(casterId)) {
                        return false;
                    }

                    // Перевіряємо, чи є eventPlayer beyonder'ом
                    if (!context.isBeyonder(eventPlayer.getUniqueId())) {
                        return false;
                    }

                    // Перевіряємо відстань
                    Player caster = context.getCasterPlayer();
                    if (caster == null || !caster.isOnline()) {
                        return false;
                    }

                    Location casterLoc = caster.getLocation();
                    Location eventLoc = eventPlayer.getLocation();

                    if (!casterLoc.getWorld().equals(eventLoc.getWorld())) {
                        return false;
                    }

                    double distance = casterLoc.distance(eventLoc);
                    return distance <= DETECTION_RANGE;
                },
                event -> handleItemHeldChange(context, event),
                Integer.MAX_VALUE // Працює постійно, поки здібність увімкнена
        );
    }

    /**
     * Обробка зміни предмета в руці
     */
    private void handleItemHeldChange(IAbilityContext context, PlayerItemHeldEvent event) {
        Player attacker = event.getPlayer();
        UUID attackerId = attacker.getUniqueId();
        UUID casterId = context.getCasterId();

        // Отримуємо beyonder атакуючого
        Beyonder attackerBeyonder = context.getBeyonderFromEntity(attackerId);
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
        Player caster = context.getCasterPlayer();
        if (caster == null || !caster.isOnline()) {
            return;
        }

        // Розраховуємо відстань для інтенсивності попередження
        double distance = caster.getLocation().distance(attacker.getLocation());
        ChatColor color = getWarningColor(distance);

        // Формуємо повідомлення
        String message = color + "⚠ " + ChatColor.WHITE + attacker.getName() +
                color + " готує " + ChatColor.YELLOW + abilityName;

        // Показуємо в actionbar
        caster.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                new TextComponent(message)
        );

        // Звуковий ефект (тихіший для далеких)
        float volume = (float) (1.0 - (distance / DETECTION_RANGE)) * 0.5f;
        context.playSoundToCaster(Sound.BLOCK_NOTE_BLOCK_BELL, volume, 1.8f);

        // Партикли навколо кастера (тільки якщо близько)
        if (distance <= 10) {
            context.spawnParticle(
                    Particle.SOUL_FIRE_FLAME,
                    caster.getLocation().add(0, 2, 0),
                    3,
                    0.3, 0.1, 0.3
            );
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