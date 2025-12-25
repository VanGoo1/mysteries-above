package me.vangoo.application.services.rampager;

import me.vangoo.domain.valueobjects.SanityPenalty;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.RampageState;
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

import java.util.UUID;

import static org.bukkit.Bukkit.getLogger;

public class RampageEffectsHandler implements RampageEventListener {

    private final RampageManager rampageManager;

    public RampageEffectsHandler(RampageManager rampageManager) {
        this.rampageManager = rampageManager;
    }

    /**
     * Apply sanity penalty and potentially start rampage
     */
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
            case EXTREME -> startExtremeRampage(player, beyonder);
        }
    }

    /**
     * Start extreme rampage with 20 second delay
     */
    private void startExtremeRampage(Player player, Beyonder beyonder) {
        UUID playerId = player.getUniqueId();

        // Start rampage transformation
        boolean started = rampageManager.startRampage(playerId, beyonder, 20);

        if (!started) {
            getLogger().warning("Failed to start rampage for " + player.getName() + " - already in rampage");
        }
    }

    // ==========================================
    // RAMPAGE EVENT HANDLERS
    // ==========================================

    @Override
    public void onRampageStarted(UUID playerId, Beyonder beyonder, RampageState state) {
        Player player = org.bukkit.Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return;

        // Initial warning
        showRampageStartWarning(player);
        playRampageStartSound(player);
        spawnRampageStartParticles(player);
    }

    @Override
    public void onPhaseChanged(UUID playerId, RampageState oldState, RampageState newState) {
        Player player = org.bukkit.Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return;

        // Visual and audio feedback based on phase
        switch (newState.phase()) {
            case CRITICAL -> {
                showCriticalPhaseWarning(player);
                playCriticalPhaseSound(player);
            }
            case TRANSFORMING -> {
                // Final warning before transformation
                showTransformationWarning(player);
            }
        }
    }

    @Override
    public void onTransformationComplete(UUID playerId, RampageState state) {
        Player player = org.bukkit.Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return;

        // Execute transformation
        showExtremeRampageMessages(player);
        notifyNearbyPlayers(player);
        spawnWardenTransformation(player);
        player.setHealth(0.0);
    }

    @Override
    public void onRampageRescued(UUID playerId, UUID rescuerId, RampageState state) {
        Player player = org.bukkit.Bukkit.getPlayer(playerId);
        Player rescuer = org.bukkit.Bukkit.getPlayer(rescuerId);

        if (player != null && player.isOnline()) {
            showRescueSuccess(player, rescuer);
            playRescueSound(player);
            spawnRescueParticles(player);
        }
    }

    @Override
    public void onRampageCancelled(UUID playerId, RampageState state) {
        Player player = org.bukkit.Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage(ChatColor.GREEN + "✓ Втрата контролю скасована");
        }
    }

    // ==========================================
    // VISUAL EFFECTS - RAMPAGE START
    // ==========================================

    private void showRampageStartWarning(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Хаос поглинає вашу свідомість!");
    }

    private void playRampageStartSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 2.0f, 0.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.8f);
    }

    private void spawnRampageStartParticles(Player player) {
        Location loc = player.getLocation();
        player.getWorld().spawnParticle(
                Particle.SOUL_FIRE_FLAME,
                loc.clone().add(0, 1, 0),
                50, 1.0, 1.0, 1.0, 0.1
        );
    }

    // ==========================================
    // VISUAL EFFECTS - CRITICAL PHASE
    // ==========================================

    private void showCriticalPhaseWarning(Player player) {
        player.sendMessage("");
        player.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "⚠ КРИТИЧНИЙ СТАН ⚠");
        player.sendMessage("");
    }

    private void playCriticalPhaseSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_ANGRY, 2.0f, 0.7f);
        player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, 1.5f, 0.5f);
    }

    // ==========================================
    // VISUAL EFFECTS - TRANSFORMATION
    // ==========================================

    private void showTransformationWarning(Player player) {
        player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                new TextComponent(ChatColor.DARK_RED + "" + ChatColor.BOLD + "ТРАНСФОРМАЦІЯ НЕМИНУЧА!")
        );
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

    // ==========================================
    // VISUAL EFFECTS - RESCUE
    // ==========================================

    private void showRescueSuccess(Player player, Player rescuer) {
        String rescuerName = rescuer != null ? rescuer.getName() : "хтось";

        player.sendMessage("");
        player.sendMessage(ChatColor.GREEN + "✦ " + ChatColor.BOLD + "ВИ ВРЯТОВАНІ!" + ChatColor.RESET);
        player.sendMessage(ChatColor.GRAY + "Хаос відступає...");
        player.sendMessage("");

        if (rescuer != null && !rescuer.equals(player)) {
            rescuer.sendMessage(ChatColor.GREEN + "✓ Ви врятували " +
                    ChatColor.YELLOW + player.getName() +
                    ChatColor.GREEN + " від втрати контролю!");
        }
    }

    private void playRescueSound(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.5f);
    }

    private void spawnRescueParticles(Player player) {
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

    // ==========================================
    // HELPERS
    // ==========================================

    private String getSanityPenaltyMessage(SanityPenalty penalty) {
        return switch (penalty.type()) {
            case DAMAGE -> {
                if (penalty.amount() <= 2) yield "Ваші руки злегка тремтять...";
                else if (penalty.amount() <= 5) yield "Ваші сили відмовляються слухатися!";
                else if (penalty.amount() <= 10) yield "Хаос у вашій свідомості завдає шкоди!";
                else yield "Втрата контролю посилюється!";
            }
            case SPIRITUALITY_LOSS -> {
                if (penalty.amount() <= 10) yield "Хаос викрадає вашу духовність...";
                else if (penalty.amount() <= 50) yield "Божевільний шепіт виснажує вас!";
                else yield "Втрата контролю вкрала велику частину духовності!";
            }
            case EXTREME -> "ПОЧИНАЄТЬСЯ ТРАНСФОРМАЦІЯ!";
            default -> "Щось не так...";
        };
    }

    private void showActionBarMessage(Player player, String message) {
        ChatColor color = ChatColor.YELLOW;

        if (message.contains("ТРАНСФОРМАЦІЯ")) {
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