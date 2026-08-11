package me.vangoo.pathways.tyrant.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Optional;

/**
 * Sequence 7: Плівка Задухи. Мореплавець огортає голову цілі водяною плівкою — та задихається
 * на суходолі, сліпне й слабшає. Плівку важко зняти: вона тримається фіксований час, а вода
 * не рятує, бо сама є її джерелом.
 */
public class WaterFilm extends ActiveAbility {

    private static final int BASE_DOT = 1;          // шкода за тік задухи
    private static final double MAX_RANGE = 25.0;
    private static final int DURATION_TICKS = 120;  // ~6 с, "важко зняти"
    private static final int TICK_PERIOD = 20;      // тік задухи щосекунди
    private static final int VISUAL_PERIOD = 5;     // оновлення плівки, щоб іти за ціллю
    private static final int COOLDOWN = 30;
    private static final int SPIRITUALITY_COST = 80;

    @Override
    public String getName() {
        return "Плівка Задухи";
    }

    @Override
    public String getDescription(Sequence sequence) {
        int dot = scaleValue(BASE_DOT, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        return String.format(
                "§fВодяна плівка огортає голову цілі: осліплення, слабкість і задуха на суходолі — " +
                        "§c%d §fшкоди щосекунди протягом 6 с.", dot);
    }

    @Override
    public int getSpiritualityCost() {
        return SPIRITUALITY_COST;
    }

    @Override
    public int getCooldown(Sequence sequence) {
        return COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player player = context.getCasterPlayer();
        if (player == null || !player.isValid()) {
            return AbilityResult.failure("Гравець недоступний");
        }

        Optional<LivingEntity> targeted = context.targeting().getTargetedEntity(MAX_RANGE);
        if (targeted.isEmpty()) {
            return AbilityResult.failure("Немає цілі на лінії зору");
        }
        LivingEntity target = targeted.get();

        Sequence sequence = context.getCasterBeyonder().getSequence();
        int dot = scaleValue(BASE_DOT, sequence, SequenceScaler.ScalingStrategy.MODERATE);

        // Контроль на весь час дії — накидаємо одразу, "важко зняти".
        context.entity().applyPotionEffect(target.getUniqueId(), PotionEffectType.DARKNESS, DURATION_TICKS, 0);
        context.entity().applyPotionEffect(target.getUniqueId(), PotionEffectType.SLOWNESS, DURATION_TICKS, 1);
        context.entity().applyPotionEffect(target.getUniqueId(), PotionEffectType.WEAKNESS, DURATION_TICKS, 0);

        context.effects().playSound(target.getEyeLocation(), Sound.AMBIENT_UNDERWATER_ENTER, 1.0f, 0.8f);

        // Плівка стежить за головою цілі + задуха на суходолі, поки тримається. У воді
        // плівка зливається з довкіллям і не душить — тік шкоди пропускається, контроль лишається.
        // Візуал оновлюється частіше за DoT, щоб плавно йти за ціллю.
        int[] elapsed = {0};
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = context.scheduling().scheduleRepeating(() -> {
            elapsed[0] += VISUAL_PERIOD;
            if (!target.isValid() || elapsed[0] > DURATION_TICKS) {
                if (holder[0] != null) holder[0].cancel();
                return;
            }
            // плівка йде за головою цілі
            context.effects().playCircleEffect(target.getEyeLocation(), 0.6, Particle.DRIPPING_WATER, VISUAL_PERIOD);
            // задуха тікає раз на секунду
            if (elapsed[0] % TICK_PERIOD == 0 && !target.isInWater()) {
                target.damage(dot, player);
                context.effects().playSound(target.getEyeLocation(),
                        Sound.ENTITY_GENERIC_DRINK, 1.0f, 0.6f);
            }
        }, VISUAL_PERIOD, VISUAL_PERIOD);

        return AbilityResult.success();
    }
}
