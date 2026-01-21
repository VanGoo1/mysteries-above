package me.vangoo.domain.pathways.door.abilities;

import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.UUID;
public class ElectricShock extends ActiveAbility {

    private static final double RANGE = 6.0;
    private static final double DAMAGE = 4.0; // 2 серця
    private static final int COST = 25;
    private static final int COOLDOWN = 8;

    @Override
    public String getName() {
        return "Електричний розряд";
    }

    @Override
    public String getDescription(Sequence sequence) {
        return "Випускає електричний розряд у живу істоту, " +
                "завдаючи миттєвої шкоди.";
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

        if (eyeLocation == null || eyeLocation.getWorld() == null) {
            return AbilityResult.failure("Не вдалося визначити позицію");
        }

        World world = eyeLocation.getWorld();
        Vector direction = eyeLocation.getDirection();

        // RayTrace для пошуку цілі
        RayTraceResult result = world.rayTraceEntities(
                eyeLocation,
                direction,
                RANGE,
                entity -> entity instanceof LivingEntity && !entity.getUniqueId().equals(casterId)
        );

        if (result == null || !(result.getHitEntity() instanceof LivingEntity target)) {
            return AbilityResult.failure("Немає цілі");
        }

        UUID targetId = target.getUniqueId();
        Location start = eyeLocation;
        Location end = target.getLocation().add(0, target.getHeight() * 0.5, 0);
        Vector toTarget = end.toVector().subtract(start.toVector());
        double distance = toTarget.length();
        toTarget.normalize();

        // Малюємо електричний розряд
        drawLightningEffect(world, start, toTarget, distance);

        // Завдаємо шкоди
        context.entity().damage(targetId, DAMAGE);

        // Звук удару
        context.effects().playSound(end, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.6f, 2.0f);

        return AbilityResult.success();
    }

    /**
     * Малює ефект електричного розряду від start у напрямку direction
     */
    private void drawLightningEffect(World world, Location start, Vector direction, double distance) {
        double step = 0.4;
        Vector current = start.toVector();

        for (double d = 0; d < distance; d += step) {
            // Невелике випадкове відхилення — "зигзаг"
            Vector offset = new Vector(
                    (Math.random() - 0.5) * 0.3,
                    (Math.random() - 0.5) * 0.3,
                    (Math.random() - 0.5) * 0.3
            );

            current.add(direction.clone().multiply(step));
            Location point = current.clone().add(offset).toLocation(world);

            // Електричні іскри
            world.spawnParticle(
                    Particle.ELECTRIC_SPARK,
                    point,
                    2,
                    0, 0, 0,
                    0
            );

            // Колірний перехід (синій -> білий)
            world.spawnParticle(
                    Particle.DUST_COLOR_TRANSITION,
                    point,
                    1,
                    new Particle.DustTransition(
                            Color.fromRGB(80, 160, 255), // стартовий (синій)
                            Color.WHITE,                 // фінальний (білий)
                            1.1f                         // розмір
                    )
            );
        }
    }
}