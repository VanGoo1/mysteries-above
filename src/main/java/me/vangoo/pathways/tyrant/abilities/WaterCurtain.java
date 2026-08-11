package me.vangoo.pathways.tyrant.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Sequence 5: Водяна Завіса. Океанський Співець піднімає перед собою стіну води —
 * вона гасить полум'я, ковтає чужі стріли й снаряди, а той, хто стоїть за нею,
 * дістає опір.
 */
public class WaterCurtain extends ActiveAbility {

    private static final double WIDTH = 5.0;
    private static final double HEIGHT = 3.0;
    private static final double DISTANCE = 2.5;      // за скільки блоків попереду стоїть завіса
    private static final double SHELTER_RANGE = 5.0; // доки завіса ще прикриває кастера
    private static final int DURATION_TICKS = 160;   // 8 с
    private static final int COOLDOWN = 20;
    private static final int SPIRITUALITY_COST = 100;

    @Override
    public String getName() {
        return "Водяна Завіса";
    }

    @Override
    public String getDescription(Sequence sequence) {
        return "§fПіднімає перед вами стіну води на §b8 с§f: вона §bпоглинає чужі снаряди§f, " +
                "гасить полум'я й дає §bОпір II§f усім, хто стоїть за нею.";
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

        Vector forward = player.getLocation().getDirection().setY(0).normalize();
        Location wall = player.getLocation().add(forward.clone().multiply(DISTANCE));
        var color = PathwayBranding.liquidOf("Tyrant");

        context.effects().playStandingCurtain(wall, forward, WIDTH, HEIGHT, color, DURATION_TICKS);
        context.effects().playWaveEffect(wall, 2.0, Particle.SPLASH, 10);
        context.effects().playSound(wall, Sound.ITEM_BUCKET_EMPTY, 1.0f, 0.7f);
        context.effects().playSound(wall, Sound.AMBIENT_UNDERWATER_ENTER, 1.0f, 1.1f);

        double half = WIDTH / 2.0;
        int[] tick = {0};
        BukkitTask[] holder = new BukkitTask[1];
        holder[0] = context.scheduling().scheduleRepeating(() -> {
            tick[0]++;
            if (tick[0] > DURATION_TICKS || !player.isValid()) {
                if (holder[0] != null) holder[0].cancel();
                return;
            }

            // Завіса ковтає чужі снаряди й гасить вогонь на всьому, що її перетинає.
            for (Entity entity : wall.getWorld().getNearbyEntities(wall, half, HEIGHT, half)) {
                if (entity instanceof Projectile projectile) {
                    if (projectile.getShooter() == player) continue;
                    projectile.remove();
                    wall.getWorld().spawnParticle(Particle.SPLASH, projectile.getLocation(), 12,
                            0.2, 0.2, 0.2);
                    context.effects().playSound(projectile.getLocation(),
                            Sound.ENTITY_GENERIC_SPLASH, 0.7f, 1.3f);
                } else if (entity instanceof LivingEntity living) {
                    living.setFireTicks(0);
                }
            }

            // Опір тому, хто ховається за завісою (оновлюємо, поки він там).
            if (tick[0] % 20 == 0 && player.getWorld().equals(wall.getWorld())
                    && player.getLocation().distanceSquared(wall) <= SHELTER_RANGE * SHELTER_RANGE) {
                context.entity().applyPotionEffect(player.getUniqueId(),
                        PotionEffectType.RESISTANCE, 40, 1);
            }
        }, 1L, 1L);

        return AbilityResult.success();
    }
}
