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
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Sequence 7: Лазурна Хвиля. Мореплавець спалахує лазурним світлом і жбурляє вперед
 * водяний вал, що змітає все на своєму шляху — завдає шкоди й відкидає ворогів, поки
 * котиться від кастера.
 */
public class AzureWave extends ActiveAbility {

    private static final int BASE_DAMAGE = 8;
    private static final double LENGTH = 12.0;      // як далеко котиться вал
    private static final double WIDTH = 5.0;        // ширина фронту
    private static final double KNOCKBACK = 1.3;
    private static final int DURATION_TICKS = 20;   // ~1 с проходу
    private static final int COOLDOWN = 15;
    private static final int SPIRITUALITY_COST = 70;

    @Override
    public String getName() {
        return "Лазурна Хвиля";
    }

    @Override
    public String getDescription(Sequence sequence) {
        int damage = scaleValue(BASE_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        return String.format(
                "§fЛазурний спалах жбурляє вперед водяний вал: усе на шляху §cвідкидає §fі " +
                        "завдає §c%d §fшкоди, поки хвиля котиться вперед.", damage);
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

        Location origin = player.getLocation();
        Vector forward = origin.getDirection().setY(0).normalize();
        var color = PathwayBranding.liquidOf("Tyrant");

        // Лазурний спалах при кастуванні + сам вал.
        context.effects().playWaveEffect(origin, 2.0, Particle.SPLASH, 8);
        context.effects().playSurgingWave(origin, forward, LENGTH, WIDTH, color, DURATION_TICKS);
        context.effects().playSound(origin, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.0f, 0.8f);

        // Фронт валу рухається щотіка; хто потрапив під його поточну позицію — дістає шкоду
        // й відкид уперед (кожен ворог б'ється лише раз).
        double step = LENGTH / DURATION_TICKS;
        double half = WIDTH / 2.0;
        Set<UUID> hit = new HashSet<>();
        int[] tick = {0};
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = context.scheduling().scheduleRepeating(() -> {
            tick[0]++;
            if (tick[0] > DURATION_TICKS) {
                if (holder[0] != null) holder[0].cancel();
                return;
            }
            Location front = origin.clone().add(forward.clone().multiply(step * tick[0]));
            for (Entity entity : player.getWorld().getNearbyEntities(front, half, 2.0, half)) {
                if (!(entity instanceof LivingEntity target) || target.equals(player)) continue;
                if (!hit.add(target.getUniqueId())) continue;
                target.damage(damage, player);
                Vector push = forward.clone().multiply(KNOCKBACK);
                push.setY(0.4);
                target.setVelocity(push);
            }
        }, 1L, 1L);

        return AbilityResult.success();
    }
}
