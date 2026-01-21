package me.vangoo.domain.pathways.door.abilities;

import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class Flash extends ActiveAbility {

    private static final int RADIUS = 10;
    private static final int DURATION = 60; // 3 секунди
    private static final int COST = 50;
    private static final int COOLDOWN = 15;

    @Override
    public String getName() {
        return "Флешка";
    }

    @Override
    public String getDescription(Sequence sequence) {
        return "Вивільняє сліпучий спалах, повністю дезорієнтуючи оточуючих " +
                "та дозволяючи кастеру зникнути з поля зору.";
    }

    @Override
    public int getSpiritualityCost() {
        return COST;
    }

    @Override
    public int getCooldown(Sequence sequence) {
        return COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();

        Location eyeLocation = context.playerData().getEyeLocation(casterId);
        Location casterLocation = context.playerData().getCurrentLocation(casterId);

        if (eyeLocation == null || casterLocation == null) {
            return AbilityResult.failure("Не вдалося визначити позицію");
        }

        // Створюємо сліпучий спалах
        createFlashEffect(context, eyeLocation, casterLocation);

        // Знаходимо гравців поблизу
        List<Player> nearbyPlayers = context.targeting().getNearbyPlayers(RADIUS);
        List<UUID> targetIds = nearbyPlayers.stream()
                .map(Player::getUniqueId)
                .collect(Collectors.toList());

        // Застосовуємо ефекти до кожного гравця
        for (UUID targetId : targetIds) {
            // Сліпота
            context.entity().applyPotionEffect(
                    targetId,
                    PotionEffectType.BLINDNESS,
                    DURATION,
                    1
            );

            // Ховаємо кастера від цілі
            context.entity().hidePlayerFromTarget(targetId, casterId);
        }

        // Показуємо кастера знову після закінчення ефекту
        context.scheduling().scheduleDelayed(() -> {
            for (UUID targetId : targetIds) {
                context.entity().showPlayerToTarget(targetId, casterId);
            }
        }, DURATION);

        return AbilityResult.success();
    }

    /**
     * Створює візуальні та звукові ефекти спалаху
     */
    private void createFlashEffect(IAbilityContext context, Location eyeLocation, Location casterLocation) {
        // Частинки на рівні очей
        context.effects().spawnParticle(Particle.END_ROD, eyeLocation, 5);

        // Вибух частинок навколо гравця
        context.effects().spawnParticle(Particle.END_ROD, casterLocation, 150, 0.0, 0.0, 0.0);

        // Звук вибуху
        context.effects().playSound(casterLocation, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 2.3f);

        // Звук активації маяка (додатковий ефект)
        context.effects().playSound(casterLocation, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 2.0f);
    }
}