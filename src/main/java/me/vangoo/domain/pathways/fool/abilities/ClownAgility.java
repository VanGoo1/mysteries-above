package me.vangoo.domain.pathways.fool.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence 8: Clown — Clown Agility (Спритність клоуна)
 *
 * Enhanced physicality — speed, jump boost, and reduced fall damage (70%).
 * The Clown's body is refined for acrobatic performance.
 */
public class ClownAgility extends PermanentPassiveAbility {

    private static final double FALL_DAMAGE_REDUCTION = 0.7; // 70% reduction

    private final Map<UUID, Integer> tickCounters = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Спритність клоуна";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Надзвичайна спритність та контроль тіла. " +
                "Швидкість +1, Стрибок +1. Шкода від падіння зменшена на 70%.";
    }

    @Override
    public AbilityIdentity getIdentity() {
        return AbilityIdentity.of("fool_agility");
    }

    @Override
    public void onActivate(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        tickCounters.put(casterId, 0);

        // Subscribe to fall damage reduction
        context.events().subscribeToTemporaryEvent(casterId,
                EntityDamageEvent.class,
                e -> e.getEntity().getUniqueId().equals(casterId)
                        && e.getCause() == EntityDamageEvent.DamageCause.FALL,
                e -> {
                    double original = e.getDamage();
                    double reduced = original * (1.0 - FALL_DAMAGE_REDUCTION);
                    e.setDamage(reduced);

                    // Landing effect
                    Location loc = e.getEntity().getLocation();
                    context.effects().spawnParticle(Particle.CLOUD, loc, 8, 0.4, 0.1, 0.4);
                    context.effects().playSound(loc, Sound.ENTITY_HORSE_LAND, 0.8f, 1.2f);
                },
                Integer.MAX_VALUE
        );
    }

    @Override
    public void onDeactivate(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        tickCounters.remove(casterId);
        context.events().unsubscribeAll(casterId);

        // Remove buffs
        context.entity().removePotionEffect(casterId, PotionEffectType.SPEED);
        context.entity().removePotionEffect(casterId, PotionEffectType.JUMP_BOOST);
    }

    @Override
    public void tick(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        int counter = tickCounters.getOrDefault(casterId, 0) + 1;
        tickCounters.put(casterId, counter);

        // Refresh buffs every 2 seconds (40 ticks)
        if (counter % 40 == 0) {
            context.entity().applyPotionEffect(casterId, PotionEffectType.SPEED, 60, 0);
            context.entity().applyPotionEffect(casterId, PotionEffectType.JUMP_BOOST, 60, 0);
        }
    }

    @Override
    public void cleanUp() {
        tickCounters.clear();
    }
}
