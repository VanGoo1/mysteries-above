package me.vangoo.pathways.tyrant.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Sequence 9: Морський ривок. Активний вияв трейту Physical Enhancement (сила Матроса) —
 * гравець потужно кидає тіло вперед, а всіх на шляху відкидає й б'є. Скромна за силою,
 * як і належить найслабшій Sequence, але це активний вихід для накопичення засвоєння.
 */
public class SeaLunge extends ActiveAbility {

    private static final double BASE_POWER = 1.4;   // сила ривка вперед
    private static final int BASE_DAMAGE = 3;       // шкода тим, у кого врізаєшся
    private static final double KNOCKBACK = 1.2;
    private static final double HIT_RADIUS = 3.0;
    private static final int COOLDOWN = 6;
    private static final int SPIRITUALITY_COST = 35;

    @Override
    public String getName() {
        return "Морський ривок";
    }

    @Override
    public String getDescription(Sequence sequence) {
        int damage = scaleValue(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        return String.format(
                "§fСила моря кидає вас уперед стрімким ривком. Усіх на шляху §cвідкидає " +
                        "§fі завдає §c%d §fшкоди.", damage);
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

        Sequence sequence = context.getCasterBeyonder().getSequence();
        int damage = scaleValue(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);

        Vector direction = player.getLocation().getDirection().normalize();

        // Кидок тіла вперед із невеликим підйомом.
        Vector velocity = direction.clone().multiply(BASE_POWER);
        if (velocity.getY() < 0.4) velocity.setY(0.4);
        player.setVelocity(velocity);

        // Відкинути й ударити всіх перед собою.
        for (Entity entity : player.getNearbyEntities(HIT_RADIUS, 2.0, HIT_RADIUS)) {
            if (!(entity instanceof LivingEntity target) || target.equals(player)) continue;
            Vector toTarget = target.getLocation().toVector().subtract(player.getLocation().toVector());
            if (toTarget.lengthSquared() < 1e-4 || direction.dot(toTarget.normalize()) < 0.3) continue;

            target.damage(damage, player);
            Vector push = direction.clone().multiply(KNOCKBACK);
            push.setY(0.35);
            target.setVelocity(push);
        }

        // Ефекти: сплеск-хвиля біля ніг + водяний шлейф за гравцем + звук тризуба-ривка.
        Location feet = player.getLocation();
        context.effects().playWaveEffect(feet, 2.0, Particle.SPLASH, 12);
        context.effects().playTrailEffect(player.getUniqueId(), Particle.BUBBLE, 20);
        context.effects().playSound(feet, Sound.ITEM_TRIDENT_RIPTIDE_1, 1.0f, 1.1f);

        return AbilityResult.success();
    }
}
