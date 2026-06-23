package me.vangoo.domain.pathways.fool.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.ToggleablePassiveAbility;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence 9: Seer — Spirit Vision (Духовне бачення)
 *
 * Toggleable passive that reveals auras of nearby entities. Health-based
 * coloring, beyonder detection, and invisible player visibility.
 */
public class SeerSpiritVision extends ToggleablePassiveAbility {

    private static final int PERIODIC_COST = 5;
    private static final double RANGE = 15.0;
    private static final int GLOW_DURATION_TICKS = 30; // Refreshed each tick cycle

    private final Map<UUID, Integer> tickCounters = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Духовне бачення";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Бачити аури сутностей: колір залежить від здоров'я. " +
                "Виявляє потойбічних та невидимих гравців у радіусі " + (int) RANGE + " блоків.";
    }

    @Override
    public int getPeriodicCost() {
        return PERIODIC_COST;
    }

    @Override
    public void onEnable(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        tickCounters.put(casterId, 0);
        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.8f);
        context.messaging().sendMessage(casterId, ChatColor.AQUA + "✦ Духовне бачення активоване");
    }

    @Override
    public void onDisable(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        tickCounters.remove(casterId);
        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.5f);
        context.messaging().sendMessage(casterId, ChatColor.GRAY + "✦ Духовне бачення деактивоване");
    }

    @Override
    public void tick(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        int counter = tickCounters.getOrDefault(casterId, 0) + 1;
        tickCounters.put(casterId, counter);

        // Process every 20 ticks (1 second)
        if (counter % 20 != 0) return;

        // Check spirituality
        Beyonder caster = context.getCasterBeyonder();
        if (caster == null) return;

        if (caster.getSpirituality().current() < PERIODIC_COST) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "✦ Духовність вичерпана — бачення згасає");
            return;
        }

        caster.setSpirituality(caster.getSpirituality().decrement(PERIODIC_COST));

        // Scan nearby players
        List<Player> nearbyPlayers = context.targeting().getNearbyPlayers(RANGE);

        for (Player target : nearbyPlayers) {
            if (target.getUniqueId().equals(casterId)) continue;

            UUID targetId = target.getUniqueId();
            double hp = context.playerData().getHealth(targetId);
            double maxHp = context.playerData().getMaxHealth(targetId);
            double hpPercent = (hp / maxHp) * 100.0;

            // Color based on health percentage
            ChatColor glowColor;
            if (hpPercent > 75) {
                glowColor = ChatColor.GREEN;
            } else if (hpPercent > 50) {
                glowColor = ChatColor.YELLOW;
            } else if (hpPercent > 25) {
                glowColor = ChatColor.RED;
            } else {
                glowColor = ChatColor.DARK_RED;
            }

            context.glowing().setGlowing(targetId, casterId, glowColor, GLOW_DURATION_TICKS);

            // Detect beyonders
            Beyonder targetBeyonder = context.beyonder().getBeyonder(targetId);
            if (targetBeyonder != null) {
                // Override glow for beyonders
                context.glowing().setGlowing(targetId, casterId, ChatColor.DARK_PURPLE, GLOW_DURATION_TICKS);

                // Show beyonder info every 5 seconds
                if (counter % 100 == 0) {
                    context.messaging().sendMessage(casterId,
                            ChatColor.DARK_PURPLE + "✦ " + target.getName() + " — потойбічний (" +
                                    targetBeyonder.getPathway().getName() + ")");
                }
            }

            // Reveal invisible players to caster
            context.entity().showPlayerToTarget(casterId, targetId);
        }

        // Subtle ambient particles for caster
        if (counter % 40 == 0) {
            var loc = context.playerData().getCurrentLocation(casterId);
            if (loc != null) {
                context.effects().spawnParticle(Particle.ENCHANT,
                        loc.clone().add(0, 2.2, 0), 5, 0.3, 0.1, 0.3);
            }
        }
    }

    @Override
    public void cleanUp() {
        tickCounters.clear();
    }
}
