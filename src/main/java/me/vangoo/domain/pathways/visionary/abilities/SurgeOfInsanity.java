package me.vangoo.domain.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

public class SurgeOfInsanity extends ActiveAbility {
    private static final int RANGE = 5;
    private static final int COOLDOWN = 30;
    private static final int EFFECT_DURATION = 10 * 20; // 10 секунд
    private static final int SANITY_INCREASE = 15;
    private static final int ABILITY_LOCK_SECONDS = 10;

    @Override
    public String getName() {
        return "Всплеск божевілля";
    }

    @Override
    public String getDescription() {
        return "В радіусі " + RANGE + " блоків на інших гравців накладається слабкість 1 і сліпота 1. " +
                "Потойбічні в цьому радіусі також проявляють ознаки втрати контролю (+" + SANITY_INCREASE +
                " втрати контролю, здібності блокуються на " + ABILITY_LOCK_SECONDS + "с).";
    }

    @Override
    public int getSpiritualityCost() {
        return 90;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        // Візуальний ефект
        context.spawnParticle(
                Particle.DRAGON_BREATH,
                context.getCasterLocation(),
                100, RANGE, 1, RANGE
        );

        // Отримати гравців поблизу
        java.util.List<Player> nearbyPlayers = context.getNearbyPlayers(RANGE);

        if (nearbyPlayers.isEmpty()) {
            context.sendMessageToCaster(
                    ChatColor.YELLOW + "Немає гравців поблизу для впливу"
            );
            return AbilityResult.success();
        }

        int affectedCount = 0;

        for (Player target : nearbyPlayers) {
            // Накласти негативні ефекти
            context.applyEffect(
                    target.getUniqueId(),
                    PotionEffectType.WEAKNESS,
                    EFFECT_DURATION,
                    0
            );
            context.applyEffect(
                    target.getUniqueId(),
                    PotionEffectType.BLINDNESS,
                    EFFECT_DURATION,
                    0
            );

            // Заблокувати здібності
            context.lockAbilities(target.getUniqueId(), ABILITY_LOCK_SECONDS);

            // Збільшити втрату контролю
            context.updateSanityLoss(target.getUniqueId(), SANITY_INCREASE);

            // Повідомлення цілі
            context.sendMessage(
                    target.getUniqueId(),
                    ChatColor.DARK_PURPLE + "Ваш розум затьмарюється! Вам важко контролювати свої сили."
            );
            context.sendMessage(
                    target.getUniqueId(),
                    ChatColor.RED + "Ваша втрата контролю зросла на " + SANITY_INCREASE + "!"
            );
            context.sendMessage(
                    target.getUniqueId(),
                    ChatColor.RED + "Ваші здібності заблоковані на " + ABILITY_LOCK_SECONDS + " секунд!"
            );

            // Візуальні ефекти на цілі
            context.spawnParticle(
                    Particle.SMOKE,
                    target.getLocation().clone().add(0, 1, 0),
                    20, 0.5, 0.5, 0.5
            );

            affectedCount++;
        }

        context.sendMessageToCaster(
                ChatColor.DARK_PURPLE + "Ви вивільнили хвилю божевілля! Вражено гравців: " + affectedCount
        );

        return AbilityResult.success();
    }

    @Override
    public int getCooldown() {
        return COOLDOWN;
    }
}