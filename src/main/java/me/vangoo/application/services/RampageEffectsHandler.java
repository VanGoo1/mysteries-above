package me.vangoo.application.services;

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


import static org.bukkit.Bukkit.getLogger;

public class RampageEffectsHandler {

    public void applySanityPenalty(Player player, Beyonder beyonder, SanityPenalty penalty) {
        if (!penalty.hasEffect()) {
            return;
        }

        // Show message and visuals based on penalty type
        String message = getSanityPenaltyMessage(penalty);
        showActionBarMessage(player, message);

        // Apply physical effects based on penalty type
        switch (penalty.type()) {
            case DAMAGE -> applyDamage(player, penalty.amount());
            case SPIRITUALITY_LOSS -> applySpiritualityLoss(player, beyonder, penalty.amount());
            case EXTREME -> applyExtremeRampage(player, beyonder);
        }
    }

    private String getSanityPenaltyMessage(SanityPenalty penalty) {
        return switch (penalty.type()) {
            case DAMAGE -> {
                if (penalty.amount() <= 2) {
                    yield "Ваші руки злегка тремтять...";
                } else if (penalty.amount() <= 5) {
                    yield "Ваші сили відмовляються слухатися!";
                } else if (penalty.amount() <= 10) {
                    yield "Хаос у вашій свідомості завдає шкоди!";
                } else {
                    yield "Втрата контролю посилюється!";
                }
            }
            case SPIRITUALITY_LOSS -> {
                if (penalty.amount() <= 10) {
                    yield "Хаос викрадає вашу духовність...";
                } else if (penalty.amount() <= 50) {
                    yield "Божевільний шепіт виснажує вас!";
                } else {
                    yield "Втрата контролю вкрала велику частину духовності!";
                }
            }
            case EXTREME -> "ХАОС ПОГЛИНАЄ ВАШУ СВІДОМІСТЬ!";
            default -> "Щось не так...";
        };
    }

    private void showActionBarMessage(Player player, String message) {
        ChatColor color = ChatColor.YELLOW;

        if (message.contains("ХАОС ПОГЛИНАЄ")) {
            color = ChatColor.DARK_PURPLE;
        } else if (message.contains("Божевільний") || message.contains("посилюється")) {
            color = ChatColor.DARK_RED;
        } else if (message.contains("Хаос") || message.contains("шкоди")) {
            color = ChatColor.RED;
        } else if (message.contains("відмовляються")) {
            color = ChatColor.GOLD;
        }

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(color + message));
    }

    private void applyDamage(Player player, int amount) {
        player.damage(amount);
        player.sendMessage(ChatColor.RED + "Хаос завдає вам " + amount + " шкоди!");
    }

    private void applySpiritualityLoss(Player player, Beyonder beyonder, int amount) {
        Spirituality current = beyonder.getSpirituality();
        Spirituality reduced = current.decrement(amount);
        beyonder.setSpirituality(reduced);

        player.sendMessage(ChatColor.DARK_RED +
                "Втрата контролю вкрала " + amount + " духовності!");
    }

    private void applyExtremeRampage(Player player, Beyonder beyonder) {
        showExtremeRampageMessages(player);
        notifyNearbyPlayers(player);
        spawnWardenTransformation(player);
        player.setHealth(0.0);
    }

    private void showExtremeRampageMessages(Player player) {
        player.sendMessage(ChatColor.DARK_PURPLE + "=".repeat(50));
        player.sendMessage(ChatColor.GRAY + "" + ChatColor.BOLD + "ВТРАТА КОНТРОЛЮ");
        player.sendMessage(ChatColor.WHITE + "Хаос поглинає вашу свідомість...");
        player.sendMessage(ChatColor.WHITE + "Ваше тіло більше не належить вам!");
        player.sendMessage(ChatColor.DARK_PURPLE + "=".repeat(50));
    }

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

    private void spawnWardenTransformation(Player player) {
        Location location = player.getLocation();
        if (location.getWorld() == null) {
            getLogger().warning("Cannot spawn warden: world is null");
            return;
        }

        Warden warden = (Warden) location.getWorld()
                .spawnEntity(location, EntityType.WARDEN);

        warden.setCustomName(ChatColor.DARK_RED + "" + ChatColor.BOLD +
                "Бешаний " + player.getName());
        warden.setCustomNameVisible(true);

        Player nearestPlayer = findNearestPlayer(location, player);
        if (nearestPlayer != null) {
            warden.setTarget(nearestPlayer);
        }

        location.getWorld().strikeLightningEffect(location);
        location.getWorld().playSound(location, Sound.ENTITY_WARDEN_EMERGE, 2.0f, 0.5f);
        location.getWorld().playSound(location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);

        location.getWorld().spawnParticle(
                Particle.SOUL_FIRE_FLAME,
                location.add(0, 1, 0),
                50, 2.0, 2.0, 2.0, 0.1
        );
    }

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