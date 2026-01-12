package me.vangoo.domain.pathways.door.abilities;
import me.vangoo.domain.abilities.core.*;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

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
        Player caster = context.getCaster();
        World world = caster.getWorld();

        RayTraceResult result = world.rayTraceEntities(
                caster.getEyeLocation(),
                caster.getEyeLocation().getDirection(),
                RANGE,
                entity -> entity instanceof LivingEntity && entity != caster
        );

        if (result == null || !(result.getHitEntity() instanceof LivingEntity target)) {
            return AbilityResult.failure("Немає цілі");
        }

        Location start = caster.getEyeLocation();
        Location end = target.getLocation().add(0, target.getHeight() * 0.5, 0);
        Vector direction = end.toVector().subtract(start.toVector());
        double distance = direction.length();
        direction.normalize();

        double step = 0.4;
        Vector current = start.toVector();

        for (double d = 0; d < distance; d += step) {

            // невелике випадкове відхилення — "зигзаг"
            Vector offset = new Vector(
                    (Math.random() - 0.5) * 0.3,
                    (Math.random() - 0.5) * 0.3,
                    (Math.random() - 0.5) * 0.3
            );

            current.add(direction.clone().multiply(step));

            Location point = current.clone().add(offset).toLocation(world);

            world.spawnParticle(
                    Particle.ELECTRIC_SPARK,
                    point,
                    2,
                    0, 0, 0,
                    0
            );

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

        target.damage(DAMAGE, caster);

        world.playSound(
                end,
                Sound.ENTITY_LIGHTNING_BOLT_IMPACT,
                0.6f,
                2.0f
        );

        return AbilityResult.success();
    }
}
