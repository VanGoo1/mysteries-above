package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.ToggleablePassiveAbility;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sequence 9 Arbiter: Danger Intuition
 */
public class Intuition extends ToggleablePassiveAbility {

    private static final double DETECTION_RADIUS = 20.0;
    private static final long WARNING_COOLDOWN_MS = 5000;

    private final Map<UUID, Long> warnedEntities = new HashMap<>();
    private int tickCounter = 0;

    @Override
    public String getName() {
        return "[Пасивна] Інтуїція Небезпеки";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Дозволяє передчувати небезпеку від озброєних гравців та агресивних мобів.";
    }

    @Override
    public void onEnable(IAbilityContext context) {
        warnedEntities.clear();

        context.effects().playSoundForPlayer(
                context.getCasterId(),
                Sound.BLOCK_BEACON_ACTIVATE,
                0.5f,
                2.0f
        );

        context.messaging().sendMessage(
                context.getCasterId(),
                "§aІнтуїцію загострено."
        );
    }

    @Override
    public void onDisable(IAbilityContext context) {
        warnedEntities.clear();

        context.effects().playSoundForPlayer(
                context.getCasterId(),
                Sound.BLOCK_BEACON_DEACTIVATE,
                0.5f,
                2.0f
        );

        context.messaging().sendMessage(
                context.getCasterId(),
                "§cІнтуїцію послаблено."
        );
    }

    @Override
    public void tick(IAbilityContext context) {
        // Оптимізація: перевірка раз на 10 тіків (0.5 сек)
        if (tickCounter++ % 10 != 0) {
            return;
        }

        UUID casterId = context.getCasterId();
        List<LivingEntity> nearbyEntities = context.targeting().getNearbyEntities(DETECTION_RADIUS);

        long currentTime = System.currentTimeMillis();

        for (LivingEntity entity : nearbyEntities) {
            // Фільтрація: пропускаємо самого себе
            if (entity.getUniqueId().equals(casterId)) {
                continue;
            }

            boolean isThreat = false;

            // 1. Перевірка Мобів
            if (entity instanceof Mob mob) {
                // Перевіряємо, чи моб націлений на кастера
                if (mob.getTarget() != null && mob.getTarget().getUniqueId().equals(casterId)) {
                    isThreat = true;
                }
            }
            // 2. Перевірка Гравців
            else if (entity instanceof Player potentialAttacker) {
                // Гравець загроза, якщо тримає зброю І дивиться на кастера
                if (isHoldingWeapon(context, potentialAttacker.getUniqueId()) &&
                        isLookingAt(context, potentialAttacker.getUniqueId(), casterId)) {
                    isThreat = true;
                }
            }

            // Якщо загрозу виявлено
            if (isThreat) {
                // Перевіряємо кулдаун сповіщення для цієї конкретної сутності
                if (!warnedEntities.containsKey(entity.getUniqueId()) ||
                        (currentTime - warnedEntities.get(entity.getUniqueId()) > WARNING_COOLDOWN_MS)) {

                    sendWarning(context, casterId, entity);
                    warnedEntities.put(entity.getUniqueId(), currentTime);
                }
            }
        }

        // Очищення старих записів з пам'яті
        if (warnedEntities.size() > 50) {
            warnedEntities.entrySet().removeIf(entry -> currentTime - entry.getValue() > WARNING_COOLDOWN_MS);
        }
    }

    private void sendWarning(IAbilityContext context, UUID casterId, LivingEntity threat) {
        UUID threatId = threat.getUniqueId();

        // Отримуємо локації через context
        Location casterLoc = context.playerData().getCurrentLocation(casterId);
        Location threatLoc = context.playerData().getCurrentLocation(threatId);

        if (casterLoc == null || threatLoc == null) {
            return;
        }

        double distance = casterLoc.distance(threatLoc);
        String name = context.playerData().getName(threatId);

        // Визначаємо колір залежно від типу загрози
        TextColor color = (threat instanceof Player) ? NamedTextColor.RED : NamedTextColor.YELLOW;

        // Звук залишаємо, щоб привернути увагу
        context.effects().playSoundForPlayer(
                casterId,
                Sound.BLOCK_NOTE_BLOCK_BASS,
                1.0f,
                0.5f
        );

        // Формуємо повідомлення через Adventure Component
        Component message = Component.text()
                .append(Component.text("⚠ ЗАГРОЗА: ", NamedTextColor.DARK_RED))
                .append(Component.text(name, color))
                .append(Component.text(" [", NamedTextColor.GRAY))
                .append(Component.text(String.format("%.1f", distance) + "м", NamedTextColor.GOLD))
                .append(Component.text("]", NamedTextColor.GRAY))
                .build();

        // Відправляємо в ActionBar через context
        context.messaging().sendMessageToActionBar(casterId, message);
    }

    /**
     * Перевіряє, чи тримає гравець зброю
     */
    private boolean isHoldingWeapon(IAbilityContext context, UUID playerId) {
        ItemStack item = context.playerData().getMainHandItem(playerId);

        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        String typeName = item.getType().name();
        return typeName.endsWith("_SWORD") ||
                typeName.endsWith("_AXE") ||
                typeName.equals("TRIDENT") ||
                typeName.equals("BOW") ||
                typeName.equals("CROSSBOW");
    }

    /**
     * Перевіряє, чи дивиться атакуючий на жертву
     */
    private boolean isLookingAt(IAbilityContext context, UUID attackerId, UUID victimId) {
        Location attackerEyeLoc = context.playerData().getEyeLocation(attackerId);
        Location victimEyeLoc = context.playerData().getEyeLocation(victimId);

        if (attackerEyeLoc == null || victimEyeLoc == null) {
            return false;
        }

        Vector toVictim = victimEyeLoc.toVector()
                .subtract(attackerEyeLoc.toVector())
                .normalize();

        Vector direction = attackerEyeLoc.getDirection();

        // Dot product > 0.5 означає кут менше ~60 градусів
        return direction.dot(toVictim) > 0.5;
    }
}