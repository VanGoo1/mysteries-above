package me.vangoo.pathways.tyrant.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Sequence 5: Їдка Злива. Океанський Співець збирає над ціллю величезну водяну сферу,
 * і та лускає їдким дощем — вода роз'їдає все живе в зоні, доки не вичерпається,
 * заразом гасячи будь-яке полум'я.
 */
public class CorrosiveDeluge extends ActiveAbility {

    private static final int BASE_TICK_DAMAGE = 3;
    private static final double RADIUS = 6.0;
    private static final double MAX_RANGE = 25.0;
    private static final int DURATION_TICKS = 160;  // 8 с
    private static final int PULSE_TICKS = 20;      // шкода раз на секунду
    private static final int COOLDOWN = 25;
    private static final int SPIRITUALITY_COST = 150;

    @Override
    public String getName() {
        return "Їдка Злива";
    }

    @Override
    public String getDescription(Sequence sequence) {
        int damage = scaleValue(BASE_TICK_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        return String.format(
                "§fВодяна сфера лускає над обраним місцем їдкою зливою: усе живе в радіусі " +
                        "§b%.0f §fблоків дістає §c%d §fшкоди щосекунди протягом §b8 с§f, " +
                        "а полум'я гасне.", RADIUS, damage);
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

        Block aimed = player.getTargetBlockExact((int) MAX_RANGE);
        if (aimed == null) {
            return AbilityResult.failure("Немає точки для зливи — цільтесь у землю чи блок");
        }
        Location center = aimed.getLocation().add(0.5, 1.0, 0.5);

        Sequence sequence = context.getCasterBeyonder().getSequence();
        int damage = scaleValue(BASE_TICK_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        var color = PathwayBranding.liquidOf("Tyrant");

        // Сфера вгорі лускає, кільце позначає межу зони на весь час зливи.
        context.effects().playSphereEffect(center.clone().add(0, 5.0, 0), 2.5,
                Particle.DRIPPING_WATER, 20);
        context.effects().playWaveEffect(center, RADIUS, Particle.SPLASH, 12);
        context.effects().playCircleEffect(center, RADIUS, Particle.SPLASH, DURATION_TICKS);
        context.effects().playGlowingDust(center.clone().add(0, 4.0, 0), color);
        context.effects().playSound(center, Sound.ENTITY_GENERIC_SPLASH, 1.4f, 0.6f);
        context.effects().playSound(center, Sound.WEATHER_RAIN_ABOVE, 1.4f, 0.8f);

        int[] tick = {0};
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = context.scheduling().scheduleRepeating(() -> {
            tick[0]++;
            if (tick[0] > DURATION_TICKS || center.getWorld() == null) {
                if (holder[0] != null) holder[0].cancel();
                return;
            }

            // Сам дощ: краплі падають згори по всій площі зони.
            center.getWorld().spawnParticle(Particle.FALLING_WATER,
                    center.clone().add(0, 4.0, 0), 25, RADIUS / 2, 1.0, RADIUS / 2);

            if (tick[0] % PULSE_TICKS != 0) return;

            for (Entity entity : center.getWorld().getNearbyEntities(center, RADIUS, RADIUS, RADIUS)) {
                if (!(entity instanceof LivingEntity target) || target.equals(player)) continue;
                target.setFireTicks(0);
                target.damage(damage, player);
                context.effects().spawnParticle(Particle.SPLASH,
                        target.getLocation().add(0, 1.0, 0), 8, 0.3, 0.4, 0.3);
            }
            context.effects().playSound(center, Sound.WEATHER_RAIN, 0.9f, 0.7f);
        }, 1L, 1L);

        return AbilityResult.success();
    }
}
