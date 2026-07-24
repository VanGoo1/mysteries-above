package me.vangoo.pathways.sun.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.HolyAffinity;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

/**
 * Sequence 5: новий одноразовий вибух — миттєво лікує й очищує союзників поруч,
 * б'є темних/нежить поруч святим уроном. На відміну від {@link SunHalo}
 * (пасивна аура), це разовий сплеск без стану.
 */
public class HolyWave extends ActiveAbility {

    private static final double RADIUS = 10.0;
    private static final int BASE_HEAL = 6;
    private static final int BASE_DAMAGE = 6;
    private static final int WEAKNESS_TICKS = 60;
    private static final int COOLDOWN = 25;

    private static final PotionEffectType[] NEGATIVE_EFFECTS = {
            PotionEffectType.POISON,
            PotionEffectType.WITHER,
            PotionEffectType.SLOWNESS,
            PotionEffectType.WEAKNESS,
            PotionEffectType.BLINDNESS,
            PotionEffectType.NAUSEA,
            PotionEffectType.HUNGER,
            PotionEffectType.DARKNESS
    };

    @Override
    public String getName() {
        return "Хвиля святості";
    }

    @Override
    public String getDescription(Sequence sequence) {
        int heal = scaleValue(BASE_HEAL, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        int damage = scaleValue(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.STRONG);
        return String.format(
                "§fВибух святого світла поруч (радіус §e%.0f §fбл): союзники миттєво лікуються на §a%d " +
                        "§fі позбавляються негативних ефектів, темні й нежить отримують §c%d §fшкоди та Слабкість.",
                RADIUS, heal, damage
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
        Location center = context.getCasterLocation();

        int heal = scaleValue(BASE_HEAL, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        int baseDamage = scaleValue(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.STRONG);

        cleanseAndHeal(context, casterId, heal);
        for (Player ally : context.targeting().getNearbyPlayers(RADIUS)) {
            cleanseAndHeal(context, ally.getUniqueId(), heal);
        }

        int hit = 0;
        for (LivingEntity entity : context.targeting().getNearbyEntities(RADIUS)) {
            if (entity.getUniqueId().equals(casterId)) continue;
            if (!HolyTargetClassifier.isDarkOrUndead(entity, context)) continue;

            double multiplier = HolyAffinity.damageMultiplier(true) * context.amplification().getDamageMultiplier(casterId);
            int damage = (int) Math.ceil(baseDamage * multiplier);
            context.entity().damage(entity.getUniqueId(), damage);
            context.entity().applyPotionEffect(entity.getUniqueId(), PotionEffectType.WEAKNESS, WEAKNESS_TICKS, 0);
            hit++;
        }

        Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.5f);
        context.effects().playWaveEffect(center, RADIUS, Particle.END_ROD, 25);
        context.effects().playCircleEffect(center, RADIUS, Particle.FLAME, 20);
        context.effects().playExplosionRingEffect(center.clone().add(0, 0.5, 0), RADIUS * 0.5, Particle.DUST, gold);
        context.effects().spawnParticle(Particle.EXPLOSION, center, 1); // НЕ FLASH: на 1.21.11 вимагає data org.bukkit.Color
        context.effects().playSound(center, Sound.ENTITY_EVOKER_CAST_SPELL, 1.2f, 1.4f);
        context.effects().playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.5f);

        context.messaging().sendMessage(casterId,
                ChatColor.GOLD + "☀ Хвиля святості вразила темних цілей: " + hit);

        return AbilityResult.success();
    }

    private void cleanseAndHeal(IAbilityContext context, UUID targetId, int heal) {
        context.entity().heal(targetId, heal);
        for (PotionEffectType negative : NEGATIVE_EFFECTS) {
            context.entity().removePotionEffect(targetId, negative);
        }
    }
}
