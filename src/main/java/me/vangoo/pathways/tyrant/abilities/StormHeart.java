package me.vangoo.pathways.tyrant.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence 5: Штормове Серце. Відчувши смертельну загрозу, серце Океанського Співця
 * стискається й розширюється, мов джерело шторму, а кров б'є по жилах припливною хвилею.
 * <p>
 * Реактивний пасив: сам підписується на {@link EntityDamageEvent} (патерн {@link AirCushion})
 * — окремий слухач і вузол у ServiceContainer не потрібні.
 */
public class StormHeart extends PermanentPassiveAbility {

    private static final double HP_THRESHOLD = 0.4;
    private static final int BURST_TICKS = 120;   // 6 секунд
    private static final long REARM_MILLIS = 60_000L;

    // Момент останнього спрацювання на гравця. Інстанс-поле: здібність спільна для шляху.
    private final Map<UUID, Long> lastTrigger = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Штормове Серце";
    }

    @Override
    public String getDescription(Sequence sequence) {
        return "§fКоли удар лишає вам менше §b40% здоров'я§f, серце б'є, мов джерело шторму: " +
                "§bСила II§f, §bШвидкість II§f і §bРегенерація§f на 6 с. Перезарядка — 60 с.";
    }

    @Override
    public void tick(IAbilityContext context) {
        // Пасив реактивний — уся логіка в підписці на шкоду.
    }

    @Override
    public void onActivate(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        context.events().subscribeToTemporaryEvent(
                casterId,
                EntityDamageEvent.class,
                event -> event.getEntity().getUniqueId().equals(casterId)
                        && !event.isCancelled()
                        && event.getEntity() instanceof Player player
                        && dropsBelowThreshold(player, event.getFinalDamage())
                        && isReady(casterId),
                event -> surge(context, (Player) event.getEntity()),
                Integer.MAX_VALUE
        );
    }

    @Override
    public void onDeactivate(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        context.events().unsubscribeAll(casterId);
        lastTrigger.remove(casterId);
    }

    private boolean dropsBelowThreshold(Player player, double finalDamage) {
        double max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        double after = player.getHealth() - finalDamage;
        return after > 0 && after < max * HP_THRESHOLD;
    }

    private boolean isReady(UUID casterId) {
        long now = System.currentTimeMillis();
        Long last = lastTrigger.get(casterId);
        if (last != null && now - last < REARM_MILLIS) return false;
        lastTrigger.put(casterId, now);
        return true;
    }

    private void surge(IAbilityContext context, Player player) {
        UUID casterId = player.getUniqueId();
        context.entity().applyPotionEffect(casterId, PotionEffectType.STRENGTH, BURST_TICKS, 1);
        context.entity().applyPotionEffect(casterId, PotionEffectType.SPEED, BURST_TICKS, 1);
        context.entity().applyPotionEffect(casterId, PotionEffectType.REGENERATION, BURST_TICKS, 0);

        Location feet = player.getLocation();
        Color color = PathwayBranding.liquidOf(context.getCasterBeyonder().getPathway().getName());
        context.effects().playRisingSpiral(feet, 2.4, 0.8, color, 30);
        context.effects().playExplosionRingEffect(feet, 1.2, Particle.DUST,
                new Particle.DustOptions(color, 1.5f));
        context.effects().playSound(feet, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.6f);
        context.effects().playSound(feet, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 0.6f);
        context.messaging().sendMessage(casterId, "§bСерце б'є штормом — кров рине припливом!");
    }
}
