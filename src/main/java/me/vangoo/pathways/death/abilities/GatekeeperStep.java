package me.vangoo.pathways.death.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.GatekeeperLore;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * Sequence 5: Крок Воротаря. Один крок кидає воротаря вперед крізь вітрові потоки,
 * що ламають усе на шляху, і б'є тих, хто опиниться поруч у місці приземлення.
 */
public class GatekeeperStep extends ActiveAbility {

    // ponytail: емпірична калібровка, не фізична модель — Minecraft-тертя з'їдає імпульс
    // нерівномірно залежно від поверхні. Підкрути, якщо дистанція в грі відчувається не так.
    private static final double VELOCITY_PER_BLOCK = 0.32;
    private static final double VERTICAL_LIFT = 0.2;
    private static final double WALL_WIDTH = 2.0;
    private static final int WALL_DURATION_TICKS = 10;
    private static final int IMPACT_DELAY_TICKS = 6;
    private static final double IMPACT_RADIUS = 2.5;
    private static final double IMPACT_KNOCKBACK = 0.8;

    @Override
    public String getName() {
        return "Крок Вартового";
    }

    @Override
    public String getDescription(Sequence sequence) {
        double distance = GatekeeperLore.stepDistance(sequence);
        int damage = GatekeeperLore.stepImpactDamage(sequence);
        return String.format(
                "§fВи кидаєтесь уперед на " + "§b%.0f §fблоків, а хто опиниться поруч у місці приземлення отримує §c%d §fшкоди.",
                distance, damage);
    }

    @Override
    public int getSpiritualityCost() {
        return GatekeeperLore.STEP_COST;
    }

    @Override
    public int getCooldown(Sequence sequence) {
        return GatekeeperLore.stepCooldownSeconds(sequence);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Player player = context.getCasterPlayer();
        if (player == null || !player.isValid()) {
            return AbilityResult.failure("Гравець недоступний");
        }

        Sequence sequence = context.getCasterBeyonder().getSequence();
        double distance = GatekeeperLore.stepDistance(sequence);
        int damage = GatekeeperLore.stepImpactDamage(sequence);

        Location origin = player.getLocation();
        Vector direction = origin.getDirection().setY(0).normalize();
        Color color = PathwayBranding.liquidOf("Death");

        context.effects().playSurgingWave(origin, direction, distance, WALL_WIDTH, color, WALL_DURATION_TICKS);
        context.effects().playSound(origin, Sound.ENTITY_BREEZE_SHOOT, 1.0f, 0.9f);

        Vector velocity = direction.clone().multiply(distance * VELOCITY_PER_BLOCK);
        velocity.setY(VERTICAL_LIFT);
        player.setVelocity(velocity);

        context.scheduling().scheduleDelayed(() -> landingImpact(player, damage), IMPACT_DELAY_TICKS);

        return AbilityResult.success();
    }

    private void landingImpact(Player player, int damage) {
        if (!player.isValid()) return;
        Location landing = player.getLocation();
        if (landing.getWorld() == null) return;

        for (Entity entity : landing.getWorld().getNearbyEntities(landing, IMPACT_RADIUS, 2.0, IMPACT_RADIUS)) {
            if (!(entity instanceof LivingEntity target) || target.equals(player)) continue;

            target.damage(damage, player);

            Vector push = target.getLocation().toVector().subtract(landing.toVector());
            if (push.lengthSquared() < 1e-4) push = new Vector(0, 0, 0.1);
            push.normalize().multiply(IMPACT_KNOCKBACK);
            push.setY(0.3);
            target.setVelocity(push);
        }
    }
}
