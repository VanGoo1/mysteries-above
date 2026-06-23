package me.vangoo.pathways.fool.abilities;

import me.vangoo.domain.abilities.core.AbilityResourceConsumer;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Sequence 9: Seer — Divination (Ворожіння)
 *
 * Core ability of the Seer. Uses a "spiritual pendulum" to reveal hidden
 * information — locate players, scan beyonders, or reveal invisible entities.
 */
public class Divination extends ActiveAbility {

    private static final int BASE_COST = 80;
    private static final int BASE_COOLDOWN = 30;
    private static final double LOCATE_RADIUS = 100.0;
    private static final double REVEAL_RADIUS = 20.0;
    private static final int GLOW_DURATION_TICKS = 200; // 10 seconds

    @Override
    public String getName() {
        return "Ворожіння";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Духовний маятник розкриває приховане. Оберіть режим: " +
                "знайти гравця, прочитати долю цілі, або виявити невидимих.";
    }

    @Override
    public int getSpiritualityCost() {
        return BASE_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return BASE_COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();

        List<DivinationMode> modes = List.of(
                DivinationMode.LOCATE_PLAYER,
                DivinationMode.READ_FATE,
                DivinationMode.REVEAL_HIDDEN
        );

        context.ui().openChoiceMenu("Ворожіння", modes, this::createMenuItem,
                mode -> handleChoice(context, casterId, mode));

        return AbilityResult.deferred();
    }

    private void handleChoice(IAbilityContext context, UUID casterId, DivinationMode mode) {
        Beyonder casterBeyonder = context.getCasterBeyonder();
        Player caster = context.getCasterPlayer();
        if (caster != null) caster.closeInventory();

        if (!AbilityResourceConsumer.consumeResources(this, casterBeyonder, context)) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "Недостатньо духовності!");
            return;
        }
        context.events().publishAbilityUsedEvent(this, casterBeyonder);

        // Pendulum animation (2 seconds)
        playPendulumAnimation(context, casterId);

        context.scheduling().scheduleDelayed(() -> {
            switch (mode) {
                case LOCATE_PLAYER -> executeLocatePlayer(context, casterId);
                case READ_FATE -> executeReadFate(context, casterId);
                case REVEAL_HIDDEN -> executeRevealHidden(context, casterId);
            }
        }, 40L); // 2 seconds after animation
    }

    // ==========================================
    // MODE 1: Locate Nearest Player
    // ==========================================
    private void executeLocatePlayer(IAbilityContext context, UUID casterId) {
        Location casterLoc = context.playerData().getCurrentLocation(casterId);
        if (casterLoc == null) return;

        Player nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (Player p : context.targeting().getNearbyPlayers(LOCATE_RADIUS)) {
            if (p.getUniqueId().equals(casterId)) continue;
            double dist = p.getLocation().distance(casterLoc);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = p;
            }
        }

        if (nearest == null) {
            context.messaging().sendMessage(casterId,
                    ChatColor.GRAY + "🔮 Маятник не відчуває нікого поблизу...");
            return;
        }

        // Direction calculation
        Location targetLoc = nearest.getLocation();
        String direction = getCardinalDirection(casterLoc, targetLoc);
        int distance = (int) Math.round(nearestDist);

        // Glow the target for caster
        context.glowing().setGlowing(nearest.getUniqueId(), casterId, ChatColor.GOLD, GLOW_DURATION_TICKS);

        context.messaging().sendMessage(casterId,
                ChatColor.GOLD + "🔮 Маятник вказує: " + ChatColor.WHITE + nearest.getName() +
                        ChatColor.GRAY + " знаходиться на " + ChatColor.YELLOW + direction +
                        ChatColor.GRAY + " (~" + distance + " блоків)");

        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.5f);
    }

    // ==========================================
    // MODE 2: Read Fate (Scan Beyonder)
    // ==========================================
    private void executeReadFate(IAbilityContext context, UUID casterId) {
        Optional<Player> targetOpt = context.targeting().getTargetedPlayer(8);

        if (targetOpt.isEmpty()) {
            context.messaging().sendMessage(casterId,
                    ChatColor.RED + "🔮 Потрібно дивитися на гравця!");
            return;
        }

        Player target = targetOpt.get();
        UUID targetId = target.getUniqueId();

        double hp = Math.round(context.playerData().getHealth(targetId) * 10.0) / 10.0;
        double maxHp = Math.round(context.playerData().getMaxHealth(targetId) * 10.0) / 10.0;

        StringBuilder msg = new StringBuilder();
        msg.append(ChatColor.GOLD).append("🔮 Доля ").append(ChatColor.WHITE).append(target.getName()).append("\n");
        msg.append(ChatColor.RED).append("  ❤ ").append(hp).append("/").append(maxHp).append("\n");

        // Check if beyonder
        Beyonder targetBeyonder = context.beyonder().getBeyonder(targetId);
        if (targetBeyonder != null) {
            msg.append(ChatColor.DARK_PURPLE).append("  ✦ Потойбічний: ")
                    .append(ChatColor.LIGHT_PURPLE)
                    .append(targetBeyonder.getPathway().getName())
                    .append(" (Seq ").append(targetBeyonder.getSequenceLevel()).append(")\n");

            int spPercent = (int) ((targetBeyonder.getSpirituality().current() * 100.0) /
                    targetBeyonder.getSpirituality().maximum());
            msg.append(ChatColor.AQUA).append("  ✦ Духовність: ")
                    .append(ChatColor.WHITE).append(spPercent).append("%");
        } else {
            msg.append(ChatColor.GRAY).append("  ✦ Звичайна людина");
        }

        context.messaging().sendMessage(casterId, msg.toString());
        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
        context.glowing().setGlowing(targetId, casterId, ChatColor.DARK_PURPLE, GLOW_DURATION_TICKS);
    }

    // ==========================================
    // MODE 3: Reveal Hidden
    // ==========================================
    private void executeRevealHidden(IAbilityContext context, UUID casterId) {
        Location casterLoc = context.playerData().getCurrentLocation(casterId);
        if (casterLoc == null) return;

        int revealed = 0;
        for (Entity entity : context.targeting().getNearbyEntities(REVEAL_RADIUS)) {
            if (entity instanceof LivingEntity living) {
                if (entity instanceof Player player) {
                    if (player.getUniqueId().equals(casterId)) continue;
                    // Show invisible players
                    context.entity().showPlayerToTarget(casterId, player.getUniqueId());
                    context.glowing().setGlowing(player.getUniqueId(), casterId, ChatColor.WHITE, 100);
                    revealed++;
                }
            }
        }

        if (revealed > 0) {
            context.messaging().sendMessage(casterId,
                    ChatColor.GOLD + "🔮 Виявлено " + ChatColor.WHITE + revealed +
                            ChatColor.GOLD + " прихованих сутностей!");
            context.effects().playSoundForPlayer(casterId, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 2.0f);
        } else {
            context.messaging().sendMessage(casterId,
                    ChatColor.GRAY + "🔮 Нічого прихованого не знайдено поблизу.");
        }

        // Visual burst
        if (casterLoc.getWorld() != null) {
            context.effects().playSphereEffect(casterLoc.clone().add(0, 1, 0), REVEAL_RADIUS, Particle.ENCHANT, 3);
        }
    }

    // ==========================================
    // PENDULUM ANIMATION
    // ==========================================
    private void playPendulumAnimation(IAbilityContext context, UUID casterId) {
        Location baseLoc = context.playerData().getCurrentLocation(casterId);
        if (baseLoc == null) return;

        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);

        for (int i = 0; i < 40; i++) {
            int tick = i;
            context.scheduling().scheduleDelayed(() -> {
                Location loc = context.playerData().getCurrentLocation(casterId);
                if (loc == null) return;

                double angle = tick * 0.3;
                double radius = 0.8;
                double x = Math.cos(angle) * radius;
                double z = Math.sin(angle) * radius;
                double y = 2.2 + Math.sin(tick * 0.15) * 0.3;

                Location particleLoc = loc.clone().add(x, y, z);
                context.effects().spawnParticle(Particle.ENCHANT, particleLoc, 2, 0.02, 0.02, 0.02);
                context.effects().spawnParticle(Particle.END_ROD, particleLoc, 1, 0, 0, 0);
            }, tick);
        }
    }

    // ==========================================
    // HELPERS
    // ==========================================
    private String getCardinalDirection(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        if (angle < 0) angle += 360;

        if (angle >= 337.5 || angle < 22.5) return "Південь";
        if (angle >= 22.5 && angle < 67.5) return "Південний Захід";
        if (angle >= 67.5 && angle < 112.5) return "Захід";
        if (angle >= 112.5 && angle < 157.5) return "Північний Захід";
        if (angle >= 157.5 && angle < 202.5) return "Північ";
        if (angle >= 202.5 && angle < 247.5) return "Північний Схід";
        if (angle >= 247.5 && angle < 292.5) return "Схід";
        return "Південний Схід";
    }

    private ItemStack createMenuItem(DivinationMode mode) {
        ItemStack item = new ItemStack(mode.getIcon());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(mode.getColor() + mode.getDisplayName());
            meta.setLore(List.of(ChatColor.GRAY + mode.getDescription()));
            item.setItemMeta(meta);
        }
        return item;
    }

    enum DivinationMode {
        LOCATE_PLAYER("Знайти гравця", "Маятник вкаже напрямок до найближчого гравця",
                Material.COMPASS, ChatColor.GOLD),
        READ_FATE("Прочитати долю", "Дізнатися шлях, послідовність та стан цілі",
                Material.BOOK, ChatColor.DARK_PURPLE),
        REVEAL_HIDDEN("Розкрити сховане", "Виявити невидимих у радіусі " + (int) REVEAL_RADIUS + " блоків",
                Material.ENDER_EYE, ChatColor.AQUA);

        private final String displayName;
        private final String description;
        private final Material icon;
        private final ChatColor color;

        DivinationMode(String displayName, String description, Material icon, ChatColor color) {
            this.displayName = displayName;
            this.description = description;
            this.icon = icon;
            this.color = color;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public Material getIcon() { return icon; }
        public ChatColor getColor() { return color; }
    }
}
