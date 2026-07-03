package me.vangoo.presentation.listeners;

import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.creatures.CreatureDefinition;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.infrastructure.mythic.MythicCreatureGateway;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Map;

/**
 * Скейлінг ЗАХИСТУ: коли наша істота (за реєстром creatures.yml) б'є потойбічного-гравця, урон множиться на
 * (1 - creatureDamageReduction(sequence)). Чим сильніший гравець (нижчий sequence), тим менше урону.
 * Істота визначається через MythicMobs API (внутрішнє ім'я) + реєстр CreatureDefinition — не через PDC-теги.
 */
public class CreatureDamageListener implements Listener {

    private final MythicCreatureGateway gateway;
    private final Map<String, CreatureDefinition> registry;
    private final BeyonderService beyonderService;

    public CreatureDamageListener(MythicCreatureGateway gateway, Map<String, CreatureDefinition> registry,
                                   BeyonderService beyonderService) {
        this.gateway = gateway;
        this.registry = registry;
        this.beyonderService = beyonderService;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Beyonder beyonder = beyonderService.getBeyonder(victim.getUniqueId());
        if (beyonder == null) return;

        if (!isCreatureSource(event.getDamager())) return;

        double reduction = SequenceScaler.creatureDamageReduction(beyonder.getSequenceLevel());
        if (reduction <= 0.0) return;
        event.setDamage(event.getDamage() * (1.0 - reduction));
    }

    /** Прямий удар нашою істотою або снаряд, випущений нею (не будь-яким Mythic-мобом). */
    private boolean isCreatureSource(Entity damager) {
        if (isOurCreature(damager)) return true;
        if (damager instanceof Projectile proj) {
            ProjectileSource shooter = proj.getShooter();
            return shooter instanceof Entity e && isOurCreature(e);
        }
        return false;
    }

    private boolean isOurCreature(Entity e) {
        return gateway.creatureId(e).map(registry::containsKey).orElse(false);
    }
}
