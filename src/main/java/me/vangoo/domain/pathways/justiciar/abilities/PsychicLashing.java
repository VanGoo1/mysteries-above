package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

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
                "Розряд §bперескакує§r на найближчих ворогів (до " + MAX_BOUNCES + " цілей), " +
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
        return context.targeting().getTargetedEntity(CAST_RANGE);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();

        // 1. Знаходимо початкову ціль (вона вже пройшла перевірку Sequence Resistance)
        Optional<LivingEntity> primaryTargetOpt = context.targeting().getTargetedEntity(CAST_RANGE);
        if (primaryTargetOpt.isEmpty()) {
            return AbilityResult.failure("Немає цілі для атаки.");
        }

        LivingEntity currentTarget = primaryTargetOpt.get();
        Set<UUID> hitEntities = new HashSet<>();
        hitEntities.add(casterId);

        // Початкова точка для променя (очі кастера)
        Location casterEyeLoc = context.getCasterEyeLocation();
        Location previousLoc = casterEyeLoc.clone().add(0, -0.3, 0);
        int bounces = 0;

        // 2. Цикл ланцюгової блискавки
        while (currentTarget != null && bounces < MAX_BOUNCES) {
            // Логіка удару
            applyLashingEffect(context, currentTarget, previousLoc);
            hitEntities.add(currentTarget.getUniqueId());

            // Оновлюємо точку для наступного променя
            previousLoc = currentTarget.getEyeLocation();
            bounces++;

            // Пошук наступної цілі
            currentTarget = findNextTarget(context, currentTarget.getLocation(), hitEntities);
        }

        return AbilityResult.success();
    }

    /**
     * Застосовує ефект психічного шмагання до цілі
     */
    private void applyLashingEffect(IAbilityContext context, LivingEntity target, Location origin) {
        UUID targetId = target.getUniqueId();
        Location targetEyeLoc = target.getEyeLocation();

        // Візуальний промінь від попередньої точки до очей цілі
        context.effects().playBeamEffect(origin, targetEyeLoc, Particle.FIREWORK, 0.1, 10);

        // Частинки навколо голови цілі
        context.effects().spawnParticle(Particle.SOUL_FIRE_FLAME, targetEyeLoc, 5, 0.2, 0.2, 0.2);

        // Звуки
        Location targetLoc = target.getLocation();
        context.effects().playSound(targetLoc, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 1.8f);
        context.effects().playSound(targetLoc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.3f, 2.0f);

        // Урон
        context.entity().damage(targetId, DAMAGE_PER_HIT);

        // Ефект уповільнення (10 тіків = 0.5 сек, рівень 2)
        context.entity().applyPotionEffect(targetId, PotionEffectType.SLOWNESS, 10, 2);
    }

    /**
     * Знаходить найближчу наступну ціль для стрибка блискавки
     */
    private LivingEntity findNextTarget(IAbilityContext context, Location center, Set<UUID> excludeIds) {
        // Оптимізований пошук у великому радіусі
        List<LivingEntity> allCandidates = context.targeting().getNearbyEntities(40.0);

        LivingEntity bestTarget = null;
        double closestDistSq = Double.MAX_VALUE;
        double bounceRangeSq = BOUNCE_RANGE * BOUNCE_RANGE;

        for (LivingEntity entity : allCandidates) {
            UUID entityId = entity.getUniqueId();

            // Фільтрація
            if (excludeIds.contains(entityId)) continue;
            if (entity instanceof ArmorStand) continue;

            double distSq = entity.getLocation().distanceSquared(center);

            // Перевірка дистанції та пошук найближчого
            if (distSq <= bounceRangeSq && distSq < closestDistSq) {
                closestDistSq = distSq;
                bestTarget = entity;
            }
        }

        return bestTarget;
    }
}