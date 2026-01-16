package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * Sequence 8 Interrogator: Psychic Lashing
 *
 * Атака ілюзорною блискавкою, що відскакує від цілі до цілі.
 */
public class PsychicLashing extends ActiveAbility {

    private static final double CAST_RANGE = 12.0;       // Дальність першого удару
    private static final double BOUNCE_RANGE = 6.0;      // Дальність стрибка
    private static final int MAX_BOUNCES = 4;            // Максимум цілей
    private static final double DAMAGE_PER_HIT = 5.0;    // 2.5 серця

    @Override
    public String getName() {
        return "Психічне Шмагання";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Випускає розряд ілюзорної блискавки, що шмагає розум ворога.\n" +
                "Розряд " + ChatColor.AQUA + "перескакує" + ChatColor.RESET + " на найближчих ворогів (до " + MAX_BOUNCES + " цілей), " +
                "завдаючи ментального урону.";
    }

    @Override
    public int getSpiritualityCost() {
        return 75;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return 15;
    }

    /**
     * Вмикає перевірку Sequence Suppression.
     * Перевірка здійснюється на ПЕРШІЙ цілі, в яку ви цілитесь.
     * Якщо вона резистить атаку — ланцюг навіть не почнеться.
     */
    @Override
    protected Optional<LivingEntity> getSequenceCheckTarget(IAbilityContext context) {
        return context.getTargetedEntity(CAST_RANGE);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player caster = context.getCasterPlayer();

        // 1. Знаходимо початкову ціль (тут вона вже гарантовано пройшла перевірку на Seq Resistance, якщо це Beyonder)
        Optional<LivingEntity> primaryTargetOpt = context.getTargetedEntity(CAST_RANGE);
        if (primaryTargetOpt.isEmpty()) {
            return AbilityResult.failure("Немає цілі для атаки.");
        }

        LivingEntity currentTarget = primaryTargetOpt.get();
        Set<UUID> hitEntities = new HashSet<>();
        hitEntities.add(caster.getUniqueId());

        Location previousLoc = caster.getEyeLocation().add(0, -0.3, 0);
        int bounces = 0;

        // 2. Цикл ланцюгової блискавки
        while (currentTarget != null && bounces < MAX_BOUNCES) {
            // -- Логіка удару --
            applyLashingEffect(context, currentTarget, previousLoc);
            hitEntities.add(currentTarget.getUniqueId());

            // Оновлюємо точку для наступного променя
            previousLoc = currentTarget.getEyeLocation();
            bounces++;

            // -- Пошук наступної цілі --
            currentTarget = findNextTarget(context, currentTarget.getLocation(), hitEntities);
        }

        return AbilityResult.success();
    }

    private void applyLashingEffect(IAbilityContext context, LivingEntity target, Location origin) {
        context.playBeamEffect(origin, target.getEyeLocation(), Particle.FIREWORK, 0.1, 10);
        context.spawnParticle(Particle.SOUL_FIRE_FLAME, target.getEyeLocation(), 5, 0.2, 0.2, 0.2);

        context.playSound(target.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 1.8f);
        context.playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.3f, 2.0f);

        context.damage(target.getUniqueId(), DAMAGE_PER_HIT);
        context.applyEffect(target.getUniqueId(), PotionEffectType.SLOWNESS, 10, 2);
    }

    private LivingEntity findNextTarget(IAbilityContext context, Location center, Set<UUID> excludeIds) {
        // Оптимізований пошук (як обговорювали раніше)
        List<LivingEntity> allCandidates = context.getNearbyEntities(40.0);

        LivingEntity bestTarget = null;
        double closestDistSq = Double.MAX_VALUE;
        double bounceRangeSq = BOUNCE_RANGE * BOUNCE_RANGE;

        for (LivingEntity entity : allCandidates) {
            if (excludeIds.contains(entity.getUniqueId())) continue;
            if (entity instanceof org.bukkit.entity.ArmorStand) continue;

            double distSq = entity.getLocation().distanceSquared(center);

            if (distSq <= bounceRangeSq) {
                if (distSq < closestDistSq) {
                    closestDistSq = distSq;
                    bestTarget = entity;
                }
            }
        }
        return bestTarget;
    }
}