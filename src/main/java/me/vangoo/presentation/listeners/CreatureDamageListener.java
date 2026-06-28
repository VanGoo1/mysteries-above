package me.vangoo.presentation.listeners;

import me.vangoo.application.services.BeyonderService;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.infrastructure.creatures.CreatureCodec;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Скейлінг ЗАХИСТУ: коли тегована потойбічна істота б'є потойбічного-гравця, урон множиться на
 * (1 - creatureDamageReduction(sequence)). Чим сильніший гравець (нижчий sequence), тим менше урону.
 */
public class CreatureDamageListener implements Listener {

    private final CreatureCodec codec;
    private final BeyonderService beyonderService;

    public CreatureDamageListener(CreatureCodec codec, BeyonderService beyonderService) {
        this.codec = codec;
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

    /** Прямий удар істоти або снаряд, випущений істотою. */
    private boolean isCreatureSource(Entity damager) {
        if (codec.isCreature(damager)) return true;
        if (damager instanceof Projectile proj) {
            ProjectileSource shooter = proj.getShooter();
            return shooter instanceof Entity e && codec.isCreature(e);
        }
        return false;
    }
}
