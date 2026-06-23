package me.vangoo.domain.pathways.fool.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence 6: Faceless — Graceful Descent (Витончений спуск)
 *
 * Evolution of ClownAgility. Complete fall damage immunity,
 * enhanced speed/jump, and perfect body control.
 * Replaces ClownAgility via shared AbilityIdentity.
 */
public class GracefulDescent extends PermanentPassiveAbility {

    private final Map<UUID, Integer> tickCounters = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Витончений спуск";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Повний контроль над тілом. Імунітет до шкоди від падіння. " +
                "Швидкість +2, Стрибок +2. Еволюція Спритності Клоуна.";
    }

    @Override
    public AbilityIdentity getIdentity() {
        return AbilityIdentity.of("fool_agility"); // Same identity as ClownAgility → replaces it
    }

    @Override
    public void onActivate(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        tickCounters.put(casterId, 0);

        // Subscribe to complete fall damage immunity
        context.events().subscribeToTemporaryEvent(casterId,
                EntityDamageEvent.class,
                e -> e.getEntity().getUniqueId().equals(casterId)
                        && e.getCause() == EntityDamageEvent.DamageCause.FALL,
                e -> {
                    e.setCancelled(true);

                    // Visual landing effect
                    Location loc = e.getEntity().getLocation();
                    double fallDist = ((org.bukkit.entity.Player) e.getEntity()).getFallDistance();

                    if (fallDist > 5) {
                        // Impressive landing from great height
                        context.effects().spawnParticle(Particle.CLOUD, loc, 15, 0.5, 0.1, 0.5);
                        context.effects().spawnParticle(Particle.SWEEP_ATTACK, loc.clone().add(0, 0.3, 0), 3, 0.5, 0, 0.5);
                        context.effects().playSound(loc, Sound.ENTITY_HORSE_LAND, 1.0f, 0.8f);
                    } else {
                        context.effects().spawnParticle(Particle.CLOUD, loc, 5, 0.3, 0.1, 0.3);
                    }
                },
                Integer.MAX_VALUE
        );
    }

    @Override
    public void onDeactivate(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        tickCounters.remove(casterId);
        context.events().unsubscribeAll(casterId);

        context.entity().removePotionEffect(casterId, PotionEffectType.SPEED);
        context.entity().removePotionEffect(casterId, PotionEffectType.JUMP_BOOST);
    }

    @Override
    public void tick(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        int counter = tickCounters.getOrDefault(casterId, 0) + 1;
        tickCounters.put(casterId, counter);

        // Refresh enhanced buffs every 2 seconds
        if (counter % 40 == 0) {
            context.entity().applyPotionEffect(casterId, PotionEffectType.SPEED, 60, 1);       // Speed II
            context.entity().applyPotionEffect(casterId, PotionEffectType.JUMP_BOOST, 60, 1);  // Jump II
        }
    }

    @Override
    public void cleanUp() {
        tickCounters.clear();
    }
}
