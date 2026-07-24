package me.vangoo.pathways.sun.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.HolyAffinity;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

/**
 * Sequence 7: Свята клятва. Тимчасово підсилює фізичну силу/спритність (Сила+Швидкість)
 * і "вогняну/святу шкоду" — на час дії удари кастера підпалюють ціль і завдають бонусної
 * святої шкоди по темних/нежиті. На відміну від {@link CleaveOfPurification} (лічильник
 * ударів, спільний хук на весь інстанс здібності), тут — часове вікно на одного кастера
 * через {@code subscribeToTemporaryEvent} (автоматично відписується через durationTicks,
 * без потреби у спільному реєстрі зарядів).
 */
public class HolyOath extends ActiveAbility {

    private static final int BASE_DURATION_SECONDS = 15;
    private static final int BASE_BONUS_DAMAGE = 3;
    private static final int FIRE_TICKS = 40;
    private static final int COOLDOWN = 35;

    @Override
    public String getName() {
        return "Свята клятва";
    }

    @Override
    public String getDescription(Sequence sequence) {
        int duration = scaleValue(BASE_DURATION_SECONDS, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        int bonus = scaleValue(BASE_BONUS_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        int amplifier = calculateAmplifier(sequence);
        return String.format(
                "§fТимчасово підсилює вас: Сила %d, Швидкість %d на %d с. Удари в ближньому бою " +
                        "підпалюють ціль і завдають §c+%d §fсвятої шкоди по темних/нежиті.",
                amplifier + 1, amplifier + 1, duration, bonus
        );
    }

    @Override
    public int getSpiritualityCost() {
        return 45;
    }

    @Override
    public int getCooldown(Sequence sequence) {
        return COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        Sequence sequence = context.getCasterBeyonder().getSequence();

        int durationSeconds = scaleValue(BASE_DURATION_SECONDS, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        int durationTicks = durationSeconds * 20;
        int amplifier = calculateAmplifier(sequence);
        int bonus = scaleValue(BASE_BONUS_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);

        context.entity().applyPotionEffect(casterId, PotionEffectType.STRENGTH, durationTicks, amplifier);
        context.entity().applyPotionEffect(casterId, PotionEffectType.SPEED, durationTicks, amplifier);

        context.events().subscribeToTemporaryEvent(casterId,
                EntityDamageByEntityEvent.class,
                event -> isOathMeleeHit(event, casterId),
                event -> applyOathHit(context, event, bonus),
                durationTicks
        );

        Location loc = context.getCasterLocation();
        context.effects().playSphereEffect(loc.clone().add(0, 1, 0), 1.2, Particle.END_ROD, 20);
        context.effects().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.3f);
        context.effects().playSound(loc, Sound.ITEM_TOTEM_USE, 0.8f, 1.4f);

        context.messaging().sendMessage(casterId,
                ChatColor.GOLD + "☀ Свята клятва промовлена на " + durationSeconds + " с");

        return AbilityResult.success();
    }

    private boolean isOathMeleeHit(EntityDamageByEntityEvent event, UUID casterId) {
        if (!(event.getDamager() instanceof Player attacker)) return false;
        if (!attacker.getUniqueId().equals(casterId)) return false;
        return event.getEntity() instanceof LivingEntity;
    }

    private void applyOathHit(IAbilityContext context, EntityDamageByEntityEvent event, int bonus) {
        LivingEntity target = (LivingEntity) event.getEntity();
        target.setFireTicks(FIRE_TICKS);

        boolean darkTarget = HolyTargetClassifier.isDarkOrUndead(target, context);
        double multiplier = HolyAffinity.damageMultiplier(darkTarget);
        int damage = (int) Math.ceil(bonus * multiplier);
        event.setDamage(event.getDamage() + damage);

        target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0, 1, 0), 8, 0.2, 0.3, 0.2);
    }

    private int calculateAmplifier(Sequence sequence) {
        int power = SequenceScaler.getSequencePower(sequence.level());
        if (power >= 8) return 2;
        if (power >= 5) return 1;
        return 0;
    }
}
