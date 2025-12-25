package me.vangoo.infrastructure.listeners;

import me.vangoo.domain.events.RampageDomainEvent;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;

import java.util.UUID;

/**
 * Infrastructure: Слухає rampage події та обробляє їх
 *
 * Відповідальності:
 * - Слухати доменні події rampage
 * - Показувати візуальні ефекти (частинки, звуки, повідомлення)
 * - Виконувати трансформацію (спавн Warden, вбивство гравця)
 *
 * Це ЄДИНЕ місце для всіх rampage візуалів та трансформації.
 */
public class RampageEventListener {

    /**
     * Обробити доменну подію
     */
    public void handle(RampageDomainEvent event) {
        switch (event) {
            case RampageDomainEvent.RampageStarted e -> onRampageStarted(e);
            case RampageDomainEvent.PhaseChanged e -> onPhaseChanged(e);
            case RampageDomainEvent.TransformationCompleted e -> onTransformationCompleted(e);
            case RampageDomainEvent.RampageRescued e -> onRampageRescued(e);
            case RampageDomainEvent.RampageCancelled e -> onRampageCancelled(e);
        }
    }

    // ==========================================
    // ОБРОБНИКИ ПОДІЙ
    // ==========================================

    private void onRampageStarted(RampageDomainEvent.RampageStarted event) {
        Player player = getPlayer(event.playerId());
        if (player == null) return;

        // Показати попередження
        player.sendMessage(ChatColor.DARK_RED + "═".repeat(50));
        player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "⚠ ПОПЕРЕДЖЕННЯ ⚠");
        player.sendMessage(ChatColor.YELLOW + "Хаос поглинає вашу свідомість!");
        player.sendMessage(ChatColor.GRAY + "У вас є " + ChatColor.RED +
                event.durationSeconds() + " секунд" + ChatColor.GRAY + " до трансформації");
        player.sendMessage(ChatColor.GRAY + "Використайте " + ChatColor.AQUA +
                "Умиротворення" + ChatColor.GRAY + " для порятунку");
        player.sendMessage(ChatColor.DARK_RED + "═".repeat(50));

        // Звуки та частинки
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 2.0f, 0.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);

        Location loc = player.getLocation();
        player.getWorld().spawnParticle(
                Particle.SOUL_FIRE_FLAME,
                loc.clone().add(0, 1, 0),
                50, 1.0, 1.0, 1.0, 0.1
        );
    }

    private void onPhaseChanged(RampageDomainEvent.PhaseChanged event) {
        Player player = getPlayer(event.playerId());
        if (player == null) return;

        switch (event.newPhase()) {
            case CRITICAL -> {
                // Критична фаза - менше 10 секунд
                player.sendMessage("");
                player.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD +
                        "⚠ КРИТИЧНИЙ СТАН ⚠");
                player.sendMessage(ChatColor.RED + "Залишилося менше 10 секунд!");
                player.sendMessage("");

                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_ANGRY, 2.0f, 0.7f);
                player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 1.5f, 0.5f);
            }
            case TRANSFORMING -> {
                // Остання секунда - неминуча трансформація
                player.spigot().sendMessage(
                        ChatMessageType.ACTION_BAR,
                        new TextComponent(ChatColor.DARK_RED + "" + ChatColor.BOLD +
                                "ТРАНСФОРМАЦІЯ НЕМИНУЧА!")
                );
            }
        }
    }

    private void onTransformationCompleted(RampageDomainEvent.TransformationCompleted event) {
        Player player = getPlayer(event.playerId());
        if (player == null) return;

        // Повідомлення гравцю
        player.sendMessage(ChatColor.DARK_PURPLE + "=".repeat(50));
        player.sendMessage(ChatColor.GRAY + "" + ChatColor.BOLD + "ВТРАТА КОНТРОЛЮ");
        player.sendMessage(ChatColor.WHITE + "Хаос поглинає вашу свідомість...");
        player.sendMessage(ChatColor.WHITE + "Ваше тіло більше не належить вам!");
        player.sendMessage(ChatColor.DARK_PURPLE + "=".repeat(50));

        // Повідомлення іншим гравцям
        Location loc = player.getLocation();
        String message = ChatColor.DARK_RED + "" + ChatColor.BOLD +
                player.getName() + " втратив контроль і трансформувався в жахливе створіння!";

        for (Player nearby : loc.getWorld().getPlayers()) {
            if (!nearby.equals(player) && nearby.getLocation().distance(loc) <= 150) {
                nearby.sendMessage(message);
            }
        }

        // ФАКТИЧНА ТРАНСФОРМАЦІЯ
        executeTransformation(player);
    }

    private void onRampageRescued(RampageDomainEvent.RampageRescued event) {
        Player player = getPlayer(event.playerId());
        Player rescuer = getPlayer(event.rescuerId());

        if (player != null) {
            player.sendMessage(ChatColor.GREEN + "✦ " + ChatColor.BOLD +
                    "ВИ ВРЯТОВАНІ!" + ChatColor.RESET);
            player.sendMessage(ChatColor.GRAY + "Хаос відступає...");
            player.sendMessage("");

            // Звуки та частинки
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.5f);

            Location loc = player.getLocation();
            player.getWorld().spawnParticle(
                    Particle.HAPPY_VILLAGER,
                    loc.clone().add(0, 1, 0),
                    50, 1.0, 1.0, 1.0, 0
            );
            player.getWorld().spawnParticle(
                    Particle.END_ROD,
                    loc.clone().add(0, 1, 0),
                    30, 0.5, 0.5, 0.5, 0.1
            );
        }

        if (rescuer != null && !rescuer.equals(player)) {
            rescuer.sendMessage(ChatColor.GREEN + "✓ Ви врятували " +
                    ChatColor.YELLOW + player.getName() +
                    ChatColor.GREEN + " від втрати контролю!");
        }
    }

    private void onRampageCancelled(RampageDomainEvent.RampageCancelled event) {
        Player player = getPlayer(event.playerId());
        if (player != null) {
            player.sendMessage(ChatColor.GREEN + "✓ Втрата контролю скасована (адмін)");
        }
    }

    // ==========================================
    // ТРАНСФОРМАЦІЯ У WARDEN
    // ==========================================

    /**
     * Виконати фактичну трансформацію гравця у Warden
     */
    private void executeTransformation(Player player) {
        Location location = player.getLocation();
        if (location.getWorld() == null) return;

        // Спавн Warden
        Warden warden = (Warden) location.getWorld()
                .spawnEntity(location, EntityType.WARDEN);

        warden.setCustomName(ChatColor.DARK_RED + "" + ChatColor.BOLD +
                "Бешаний " + player.getName());
        warden.setCustomNameVisible(true);

        // Націлити на найближчого гравця
        Player nearestPlayer = findNearestPlayer(location, player);
        if (nearestPlayer != null) {
            warden.setTarget(nearestPlayer);
        }

        // Ефекти
        location.getWorld().strikeLightningEffect(location);
        location.getWorld().playSound(location, Sound.ENTITY_WARDEN_EMERGE, 2.0f, 0.5f);
        location.getWorld().playSound(location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);

        location.getWorld().spawnParticle(
                Particle.SOUL_FIRE_FLAME,
                location.add(0, 1, 0),
                50, 2.0, 2.0, 2.0, 0.1
        );

        // Вбити гравця
        player.setHealth(0.0);
    }

    /**
     * Знайти найближчого гравця (окрім того, що трансформується)
     */
    private Player findNearestPlayer(Location location, Player exclude) {
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        final double MAX_RANGE = 100.0;

        for (Player player : location.getWorld().getPlayers()) {
            if (player.equals(exclude)) continue;

            double distance = player.getLocation().distance(location);
            if (distance < nearestDistance && distance <= MAX_RANGE) {
                nearest = player;
                nearestDistance = distance;
            }
        }

        return nearest;
    }

    // ==========================================
    // HELPER
    // ==========================================

    private Player getPlayer(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        return (player != null && player.isOnline()) ? player : null;
    }
}