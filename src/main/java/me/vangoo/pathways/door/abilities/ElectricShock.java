package me.vangoo.pathways.door.abilities;

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
        return "Випускає електричний розряд уперед (" + (int) RANGE + " блоків). " +
                "Жива істота на шляху дістає миттєву шкоду; без цілі розряд просто б'є в порожнечу.";
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

        // Розряд б'є завжди — навіть у порожнечу. Ціль лише вирішує, чи буде кому боляче:
        // без неї дуга догорає в повітрі (або об стіну), коштуючи ті самі ресурси.
        if (result != null && result.getHitEntity() instanceof LivingEntity target) {
            Location end = target.getLocation().add(0, target.getHeight() * 0.5, 0);
            Vector toTarget = end.toVector().subtract(eyeLocation.toVector());
            double distance = toTarget.length();
            toTarget.normalize();

            drawLightningEffect(world, eyeLocation, toTarget, distance);

            context.entity().damage(target.getUniqueId(), DAMAGE);
            context.effects().playSound(end, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.6f, 2.0f);

            return AbilityResult.success();
        }

        // Порожній постріл: дуга йде на всю дальність, але гасне об перешкоду.
        double distance = RANGE;
        RayTraceResult blockHit = world.rayTraceBlocks(eyeLocation, direction, RANGE, FluidCollisionMode.NEVER, true);
        if (blockHit != null) {
            distance = eyeLocation.toVector().distance(blockHit.getHitPosition());
        }

        Location end = eyeLocation.clone().add(direction.clone().normalize().multiply(distance));
        drawLightningEffect(world, eyeLocation, direction.clone().normalize(), distance);
        context.effects().playSound(end, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.4f, 2.2f);
        world.spawnParticle(Particle.ELECTRIC_SPARK, end, 12, 0.2, 0.2, 0.2, 0.05);

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