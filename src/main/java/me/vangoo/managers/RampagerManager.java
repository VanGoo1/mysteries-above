package me.vangoo.managers;

import me.vangoo.domain.Beyonder;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Random;

public class RampagerManager {
    private final Random random = new Random();


    public boolean executeLossOfControl(Player player, Beyonder beyonder){
        int lossOfControl = beyonder.getSanityLossScale();
        if (lossOfControl <= 0) return false;
        double failureChance = calculateFailureChance(lossOfControl);
        if (random.nextDouble() < failureChance) {
            sendLossOfControlMessage(player, lossOfControl);
            applyLossOfControlPenalty(player,beyonder);
            return true;
        }
        return false;
    }

    public double calculateFailureChance(int lossOfControl) {
        if (lossOfControl <= 10) return 0.05; // Мінімальний шанс 5%
        else if (lossOfControl <= 20) return 0.1 + (lossOfControl - 10) * 0.01; // 10-20%
        else if (lossOfControl <= 40) return 0.25 + (lossOfControl - 20) * 0.01; // 25-45%
        else if (lossOfControl <= 60) return 0.50 + (lossOfControl - 40) * 0.01; // 50-70%
        else if (lossOfControl <= 80) return 0.75 + (lossOfControl - 60) * 0.005; // 75-85%
        else return Math.min(0.95, 0.90 + (lossOfControl - 80) * 0.0025); // 90-95% макс
    }

    private void sendLossOfControlMessage(Player player, int lossOfControl) {
        String message;
        ChatColor color;

        if (lossOfControl <= 20) {
            message = "Ваші руки злегка тремтять...";
            color = ChatColor.YELLOW;
        } else if (lossOfControl <= 40) {
            message = "Ваші сили відмовляються слухатися!";
            color = ChatColor.GOLD;
        } else if (lossOfControl <= 60) {
            message = "Хаос у вашій свідомості блокує здібності!";
            color = ChatColor.RED;
        } else if (lossOfControl <= 90) {
            message = "Божевільний шепіт заважає зосередитися!";
            color = ChatColor.DARK_RED;
        } else {
            message = "ХАОС ПОГЛИНАЄ ВАШУ СВІДОМІСТЬ!";
            color = ChatColor.DARK_PURPLE;
        }

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(color + message));
    }

    private void applyLossOfControlPenalty(Player player, Beyonder beyonder) {
        int lossOfControl = beyonder.getSanityLossScale();
        if (lossOfControl > 95) {
            applyExtremeRampageEffects(player, beyonder);
            return;
        }
        if (lossOfControl > 80) {
            int spiritualityLoss = random.nextInt(10) + 5; // 5-14 духовності
            beyonder.setSpirituality(Math.max(0, beyonder.getSpirituality() - spiritualityLoss));
            player.sendMessage(ChatColor.DARK_RED + "Втрата контролю вкрала " + spiritualityLoss + " духовності!");
            return;
        }
        if (lossOfControl > 60) {
            double damage = 1.0 + (lossOfControl - 60) * 0.1; // 1-3 урону
            player.damage(damage);
            player.sendMessage(ChatColor.RED + "Хаос завдає вам шкоди!");
        }
    }

    private void applyExtremeRampageEffects(Player player, Beyonder beyonder) {
        player.sendMessage(ChatColor.DARK_PURPLE + "=".repeat(50));
        player.sendMessage(ChatColor.GRAY + "" + ChatColor.BOLD + "ВТРАТА КОНТРОЛЮ");
        player.sendMessage(ChatColor.WHITE + "Хаос поглинає вашу свідомість...");
        player.sendMessage(ChatColor.WHITE + "Ваше тіло більше не належить вам!");
        player.sendMessage(ChatColor.DARK_PURPLE + "=".repeat(50));

        Location loc = player.getLocation();
        for (Player nearbyPlayer : loc.getWorld().getPlayers()) {
            if (nearbyPlayer.getLocation().distance(loc) <= 150 && !nearbyPlayer.equals(player)) {
                nearbyPlayer.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD +
                        player.getName() + " втратив контроль і трансформувався в жахливе створіння!");
            }
        }
        spawnWardenAtPlayerLocation(player, beyonder);
        player.setHealth(0.0);
    }

    private void spawnWardenAtPlayerLocation(Player player, Beyonder beyonder) {
        Location spawnLocation = player.getLocation();

        // Спавнимо Вардена
        org.bukkit.entity.Warden warden = (org.bukkit.entity.Warden) spawnLocation.getWorld()
                .spawnEntity(spawnLocation, org.bukkit.entity.EntityType.WARDEN);

        // Налаштовуємо Вардена
        String playerName = player.getName();
        warden.setCustomName(ChatColor.DARK_RED + "" + ChatColor.BOLD +
                "Бешаний " + playerName);
        warden.setCustomNameVisible(true);

        // Робимо Вардена більш агресивним
        warden.setTarget(findNearestPlayer(spawnLocation, player));

        // Додаткові ефекти появи
        spawnLocation.getWorld().strikeLightningEffect(spawnLocation);

        // Звукові ефекти
        spawnLocation.getWorld().playSound(spawnLocation,
                org.bukkit.Sound.ENTITY_WARDEN_EMERGE, 2.0f, 0.5f);
        spawnLocation.getWorld().playSound(spawnLocation,
                org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 0.8f);

        // Партикли
        spawnLocation.getWorld().spawnParticle(
                org.bukkit.Particle.SOUL_FIRE_FLAME,
                spawnLocation.add(0, 1, 0),
                50, 2.0, 2.0, 2.0, 0.1
        );
    }

    private Player findNearestPlayer(Location location, Player excludePlayer) {
        Player nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Player player : location.getWorld().getPlayers()) {
            if (player.equals(excludePlayer)) continue;

            double distance = player.getLocation().distance(location);
            if (distance < nearestDistance && distance <= 100) { // Максимум 100 блоків
                nearest = player;
                nearestDistance = distance;
            }
        }
        return nearest;
    }
}
