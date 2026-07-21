package me.vangoo.pathways.fool.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.ThreadVisionRange;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence 5: Marionettist — Thread Sight (Бачення ниток).
 *
 * <p>Пасивка: бачачи Нитки Духовного Тіла, потойбічний бачить над головами всіх
 * істот поблизу нитки, що тягнуться вгору, і <b>виявляє невидимих</b> (психо-
 * невидимість, зілля невидимості) — вони підсвічуються контуром лише для нього.
 * Радіус — {@link ThreadVisionRange}. Рендер — лише для власника (не глобально).
 */
public class ThreadSight extends PermanentPassiveAbility {

    private static final int GLOW_TICKS = 30;

    private final Map<UUID, Integer> tickCounters = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Бачення ниток";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Ви бачите Нитки Духовного Тіла над головами істот у радіусі " +
                ThreadVisionRange.rangeFor(userSequence) + " блоків і виявляєте невидимих " +
                "(психоневидимість, зілля невидимості) — вони підсвічуються лише для вас.";
    }

    @Override
    public void onActivate(IAbilityContext context) {
        tickCounters.put(context.getCasterId(), 0);
    }

    @Override
    public void onDeactivate(IAbilityContext context) {
        tickCounters.remove(context.getCasterId());
    }

    @Override
    public void tick(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        int counter = tickCounters.getOrDefault(casterId, 0) + 1;
        tickCounters.put(casterId, counter);
        if (counter % 10 != 0) return; // раз на 0.5с

        int range = ThreadVisionRange.rangeFor(context.getCasterBeyonder().getSequence());
        for (LivingEntity entity : context.targeting().getNearbyEntities(range)) {
            if (entity.getUniqueId().equals(casterId)) continue;

            // Нитки над головою — лише для власника здібності.
            Location head = entity.getLocation().add(0, entity.getHeight() + 0.4, 0);
            for (int i = 0; i < 3; i++) {
                context.effects().spawnParticleForPlayer(casterId, Particle.SOUL,
                        head.clone().add(0, 0.3 * i, 0), 1, 0.02, 0.05, 0.02);
            }

            // Виявлення невидимих: підсвічуємо контуром лише для власника.
            if (entity.hasPotionEffect(PotionEffectType.INVISIBILITY) || entity.isInvisible()) {
                context.glowing().setGlowing(entity.getUniqueId(), casterId, ChatColor.AQUA, GLOW_TICKS);
            }
        }
    }

    @Override
    public void cleanUp() {
        tickCounters.clear();
    }
}
