package me.vangoo.pathways.tyrant.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.UUID;

/**
 * Sequence 6: Руки Вітру. Благословенний Вітром огортає долоні стисненим повітрям —
 * на кілька секунд його удари в ближньому бою пробивають будь-який захист: додаткова
 * шкода йде повз броню, а з кулака вихоплюється порив вітру.
 * <p>
 * Вікно бафа — це тривалість самої підписки на {@link EntityDamageByEntityEvent}:
 * слухач відмирає сам, окремої сесії чи реєстру не треба (кулдаун довший за вікно,
 * тож повторний каст під час дії неможливий).
 */
public class WindImbuedHands extends ActiveAbility {

    private static final int BASE_BONUS_DAMAGE = 4;   // «наскрізна» шкода понад звичайний удар
    private static final int DURATION_TICKS = 120;    // 6 секунд
    private static final int COOLDOWN = 25;
    private static final int SPIRITUALITY_COST = 70;

    @Override
    public String getName() {
        return "Руки Вітру";
    }

    @Override
    public String getDescription(Sequence sequence) {
        int bonus = scaleValue(BASE_BONUS_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        return String.format(
                "§fДолоні огортає стиснене повітря: протягом §b%d с §fкожен ваш удар у ближньому " +
                        "бою додає §c%d §fшкоди §7повз броню§f, вивільняючи порив вітру.",
                DURATION_TICKS / 20, bonus);
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

        UUID casterId = context.getCasterId();
        int bonus = scaleValue(BASE_BONUS_DAMAGE, context.getCasterBeyonder().getSequence(),
                SequenceScaler.ScalingStrategy.MODERATE);

        context.events().subscribeToTemporaryEvent(
                casterId,
                EntityDamageByEntityEvent.class,
                event -> isMeleeHitBy(event, casterId),
                event -> pierce(context, (LivingEntity) event.getEntity(), bonus),
                DURATION_TICKS
        );

        // Візуал активації: вітрова спіраль уздовж тіла + аура кольору шляху + порив вітру.
        Color color = PathwayBranding.liquidOf(context.getCasterBeyonder().getPathway().getName());
        Location feet = player.getLocation();
        context.effects().playHelixEffect(feet, feet.clone().add(0, 2.2, 0), Particle.SMALL_GUST, 30);
        context.effects().playFadingAura(feet, color, 30);
        context.effects().playSound(feet, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1.0f, 1.3f);

        return AbilityResult.success();
    }

    private static boolean isMeleeHitBy(EntityDamageByEntityEvent event, UUID casterId) {
        return event.getEntity() instanceof LivingEntity
                && event.getDamager() instanceof Player damager
                && damager.getUniqueId().equals(casterId)
                && (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK);
    }

    /**
     * «Наскрізна» шкода: MC не вміє ігнорувати броню в межах події, тому знімаємо здоров'я
     * напряму — броня, опір і чари на неї не впливають. Звичайний удар довершиться зверху.
     */
    private void pierce(IAbilityContext context, LivingEntity victim, int bonus) {
        victim.setHealth(Math.max(0.0, victim.getHealth() - bonus));

        Location hit = victim.getLocation().add(0, victim.getHeight() * 0.5, 0);
        context.effects().spawnParticle(Particle.GUST, hit, 1);
        context.effects().playSound(hit, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 0.8f, 1.6f);
    }
}
