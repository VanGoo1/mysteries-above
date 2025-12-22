package me.vangoo.application.services;

import me.vangoo.application.abilities.SanityLossCheckResult;
import me.vangoo.application.abilities.SanityPenalty;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Spirituality;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;

import java.util.Objects;

import static org.bukkit.Bukkit.getLogger;

public class RampageEffectsHandler {

    /**
     * Show effects based on sanity loss check result
     */
    public void showSanityLossEffects(Player player, Beyonder beyonder, SanityLossCheckResult result) {
        if (Objects.equals(result.penalty(), SanityPenalty.none())) {
            return;
        }

        // Show action bar message
        showActionBarMessage(player, result.message());

        // Apply penalty effects
        SanityPenalty penalty = result.penalty();
        switch (penalty.type()) {
            case DAMAGE -> applyDamage(player, penalty.amount());
            case SPIRITUALITY_LOSS -> applySpiritualityLoss(player, beyonder, penalty.amount());
            case EXTREME -> applyExtremeRampage(player, beyonder);
        }
    }

    /**
     * Show message in action bar
     */
    private void showActionBarMessage(Player player, String message) {
        ChatColor color = determineMessageColor(message);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(color + message));
    }

    /**
     * Determine color based on message severity
     */
    private ChatColor determineMessageColor(String message) {
        if (message.contains("ХАОС ПОГЛИНАЄ")) {
            return ChatColor.DARK_PURPLE;
        } else if (message.contains("Божевільний")) {
            return ChatColor.DARK_RED;
        } else if (message.contains("Хаос")) {
            return ChatColor.RED;
        } else if (message.contains("відмовляються")) {
            return ChatColor.GOLD;
        }
        return ChatColor.YELLOW;
    }

    /**
     * Apply physical damage penalty
     */
    private void applyDamage(Player player, int amount) {
        player.damage(amount);
        player.sendMessage(ChatColor.RED + "Хаос завдає вам шкоди!");
    }

    /**
     * Apply spirituality loss penalty
     */
    private void applySpiritualityLoss(Player player, Beyonder beyonder, int amount) {
        Spirituality current = beyonder.getSpirituality();
        Spirituality reduced = current.decrement(amount);
        beyonder.setSpirituality(reduced);

        player.sendMessage(ChatColor.DARK_RED +
                "Втрата контролю вкрала " + amount + " духовності!");
    }

    /**
     * Apply extreme rampage effects - death and warden transformation
     */
    private void applyExtremeRampage(Player player, Beyonder beyonder) {
        // Show dramatic message
        showExtremeRampageMessages(player);

        // Notify nearby players
        notifyNearbyPlayers(player);

        // Spawn warden
        spawnWardenTransformation(player);

        // Kill player
        player.setHealth(0.0);
    }

    /**
     * Show dramatic transformation messages
     */
    private void showExtremeRampageMessages(Player player) {
        player.sendMessage(ChatColor.DARK_PURPLE + "=".repeat(50));
        player.sendMessage(ChatColor.GRAY + "" + ChatColor.BOLD + "ВТРАТА КОНТРОЛЮ");
        player.sendMessage(ChatColor.WHITE + "Хаос поглинає вашу свідомість...");
        player.sendMessage(ChatColor.WHITE + "Ваше тіло більше не належить вам!");
        player.sendMessage(ChatColor.DARK_PURPLE + "=".repeat(50));
    }

    /**
     * Notify nearby players about transformation
     */
    private void notifyNearbyPlayers(Player player) {
        Location loc = player.getLocation();
        String message = ChatColor.DARK_RED + "" + ChatColor.BOLD +
                player.getName() + " втратив контроль і трансформувався в жахливе створіння!";

        for (Player nearby : loc.getWorld().getPlayers()) {
            if (!nearby.equals(player) && nearby.getLocation().distance(loc) <= 150) {
                nearby.sendMessage(message);
            }
        }
    }

    /**
     * Spawn warden at player location with effects
     */
    private void spawnWardenTransformation(Player player) {
        Location location = player.getLocation();
        if (location.getWorld() == null) {
            getLogger().warning("Cannot spawn warden: world is null");
            return;
        }

        // Spawn warden
        Warden warden = (Warden) location.getWorld()
                .spawnEntity(location, EntityType.WARDEN);

        // Configure warden
        warden.setCustomName(ChatColor.DARK_RED + "" + ChatColor.BOLD +
                "Бешаний " + player.getName());
        warden.setCustomNameVisible(true);

        // Target nearest player
        Player nearestPlayer = findNearestPlayer(location, player);
        if (nearestPlayer != null) {
            warden.setTarget(nearestPlayer);
        }

        // Visual effects
        location.getWorld().strikeLightningEffect(location);
        location.getWorld().playSound(location, Sound.ENTITY_WARDEN_EMERGE, 2.0f, 0.5f);
        location.getWorld().playSound(location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);

        // Particle effects
        location.getWorld().spawnParticle(
                Particle.SOUL_FIRE_FLAME,
                location.add(0, 1, 0),
                50, 2.0, 2.0, 2.0, 0.1
        );
    }

    /**
     * Find nearest player within range
     */
    private Player findNearestPlayer(Location location, Player exclude) {
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        final double MAX_RANGE = 100.0;

        for (Player player : location.getWorld().getPlayers()) {
            if (player.equals(exclude)) {
                continue;
            }

            double distance = player.getLocation().distance(location);
            if (distance < nearestDistance && distance <= MAX_RANGE) {
                nearest = player;
                nearestDistance = distance;
            }
        }

        return nearest;
    }
}