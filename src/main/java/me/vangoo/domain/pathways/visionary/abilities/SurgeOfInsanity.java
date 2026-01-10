package me.vangoo.domain.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class SurgeOfInsanity extends ActiveAbility {

    private static final int BASE_RANGE = 5;
    private static final int COOLDOWN = 30; // Тепер просто COOLDOWN
    private static final int BASE_DURATION_SECONDS = 10;
    private static final int BASE_SANITY_INCREASE = 5;

    private static final double MOB_DAMAGE = 8.0; // 4 серця

    @Override
    public String getName() {
        return "Всплеск божевілля";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        int range = scaleValue(BASE_RANGE, userSequence, SequenceScaler.ScalingStrategy.MODERATE);
        int duration = scaleValue(BASE_DURATION_SECONDS, userSequence, SequenceScaler.ScalingStrategy.MODERATE);
        int sanityIncrease = scaleValue(BASE_SANITY_INCREASE, userSequence, SequenceScaler.ScalingStrategy.WEAK);

        return String.format(
                "В радіусі %d блоків накладає Слабкість і Сліпоту на %d с. " +
                        "Потойбічні втрачають %d глузду (Sanity). " +
                        "Звичайні істоти отримують 4 серця шкоди.",
                range, duration, sanityIncrease
        );
    }

    @Override
    public int getSpiritualityCost() {
        return 80;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Sequence userSequence = context.getCasterBeyonder().getSequence();

        // Скейлимо параметри ефекту, але НЕ кулдаун
        int range = scaleValue(BASE_RANGE, userSequence, SequenceScaler.ScalingStrategy.MODERATE);
        int durationSeconds = scaleValue(BASE_DURATION_SECONDS, userSequence, SequenceScaler.ScalingStrategy.MODERATE);
        int sanityIncrease = scaleValue(BASE_SANITY_INCREASE, userSequence, SequenceScaler.ScalingStrategy.WEAK);

        int durationTicks = durationSeconds * 20;

        // Візуалізація
        context.spawnParticle(Particle.DRAGON_BREATH, context.getCasterLocation(), 100, range, 1, range);
        context.playSound(context.getCasterLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, 1.0f, 0.5f);
        context.playSound(context.getCasterLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 0.5f);

        List<LivingEntity> nearbyEntities = context.getNearbyEntities(range);

        if (nearbyEntities.isEmpty()) {
            return AbilityResult.failure("Немає цілей поблизу.");
        }

        int affectedPlayers = 0;
        int affectedMobs = 0;

        for (LivingEntity target : nearbyEntities) {
            if (target.getUniqueId().equals(context.getCasterId())) continue;

            if (target instanceof Player) {
                // Гравець: Дебафи + Sanity
                context.applyEffect(target.getUniqueId(), PotionEffectType.WEAKNESS, durationTicks, 0);
                context.applyEffect(target.getUniqueId(), PotionEffectType.BLINDNESS, durationTicks, 0);
                context.updateSanityLoss(target.getUniqueId(), sanityIncrease);

                context.sendMessage(target.getUniqueId(),
                        ChatColor.DARK_PURPLE + "Ваш розум затьмарюється! (+" + sanityIncrease + " втрати контролю)");
                context.spawnParticle(Particle.SMOKE, target.getLocation().add(0, 1.8, 0), 10, 0.2, 0.2, 0.2);
                affectedPlayers++;
            } else {
                // Моб: Шкода
                context.damage(target.getUniqueId(), MOB_DAMAGE);
                context.spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 10, 0.2, 0.2, 0.2);
                affectedMobs++;
            }
        }

        context.sendMessageToCaster(
                ChatColor.DARK_PURPLE + "Вивільнено божевілля! Гравців: " + affectedPlayers + ", Істот: " + affectedMobs
        );

        return AbilityResult.success();
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return COOLDOWN;
    }
}