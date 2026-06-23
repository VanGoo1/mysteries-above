package me.vangoo.domain.pathways.fool.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence 7: Magician — Paper Figurine Substitution (Заміна паперовою лялькою)
 *
 * Iconic Magician survival trick. Activates a shield; when hit with heavy damage,
 * the caster swaps with a paper figurine and teleports to safety.
 */
public class PaperSubstitution extends ActiveAbility {

    private static final int BASE_COST = 120;
    private static final int BASE_COOLDOWN = 60;
    private static final int SHIELD_DURATION_TICKS = 160; // 8 seconds
    private static final double DAMAGE_THRESHOLD = 4.0;
    private static final double ESCAPE_DISTANCE = 5.0;

    private static final Map<UUID, BukkitTask> activeShields = new ConcurrentHashMap<>();

    @Override
    public String getName() {
        return "Заміна паперовою лялькою";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Підготовляє паперову ляльку на " + (SHIELD_DURATION_TICKS / 20) + "с. " +
                "При отриманні " + (int) DAMAGE_THRESHOLD + "+ шкоди — лялька приймає удар замість вас. " +
                "Потребує 1 папір.";
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
    protected boolean canExecute(IAbilityContext context) {
        UUID casterId = context.getCasterId();

        // Check if already has shield
        if (activeShields.containsKey(casterId)) {
            context.messaging().sendMessage(casterId, ChatColor.YELLOW + "Паперовий щит вже активний!");
            return false;
        }

        // Check for paper in inventory
        var player = context.getCasterPlayer();
        if (player == null) return false;
        if (!player.getInventory().contains(Material.PAPER)) {
            context.messaging().sendMessage(casterId, ChatColor.RED + "✗ Потрібен папір в інвентарі!");
            return false;
        }

        return true;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();

        // Consume one paper
        context.entity().consumeItem(casterId, new ItemStack(Material.PAPER, 1));

        // Activate shield
        context.messaging().sendMessageToActionBar(casterId,
                Component.text("📜 Паперовий щит готовий!", NamedTextColor.GOLD));
        context.effects().playSoundForPlayer(casterId, Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.5f);

        final int[] ticks = {0};
        final boolean[] triggered = {false};

        // Subscribe to damage events
        context.events().subscribeToTemporaryEvent(casterId,
                EntityDamageEvent.class,
                e -> e.getEntity().getUniqueId().equals(casterId) && e.getDamage() >= DAMAGE_THRESHOLD,
                e -> {
                    if (triggered[0]) return;
                    triggered[0] = true;

                    // Cancel the damage
                    e.setCancelled(true);

                    // Execute substitution
                    executeSubstitution(context, casterId, e);
                },
                SHIELD_DURATION_TICKS
        );

        // Duration timer
        BukkitTask task = context.scheduling().scheduleRepeating(() -> {
            ticks[0]++;

            if (!context.playerData().isOnline(casterId) || triggered[0]) {
                cancelShield(context, casterId);
                return;
            }

            if (ticks[0] >= SHIELD_DURATION_TICKS) {
                cancelShield(context, casterId);
                context.messaging().sendMessageToActionBar(casterId,
                        Component.text("📜 Паперовий щит розсипався", NamedTextColor.GRAY));
                return;
            }

            // Update action bar
            if (ticks[0] % 20 == 0) {
                int remaining = (SHIELD_DURATION_TICKS - ticks[0]) / 20;
                context.messaging().sendMessageToActionBar(casterId,
                        Component.text("📜 Паперовий щит (" + remaining + "с)", NamedTextColor.GOLD));
            }
        }, 0L, 1L);

        activeShields.put(casterId, task);
        return AbilityResult.success();
    }

    private void executeSubstitution(IAbilityContext context, UUID casterId, EntityDamageEvent damageEvent) {
        Location originalLoc = context.playerData().getCurrentLocation(casterId);
        if (originalLoc == null || originalLoc.getWorld() == null) return;

        // 1. Spawn paper figurine (armor stand) at caster's position
        World world = originalLoc.getWorld();
        ArmorStand figurine = world.spawn(originalLoc, ArmorStand.class, stand -> {
            stand.setVisible(true);
            stand.setSmall(false);
            stand.setBasePlate(false);
            stand.setArms(true);
            stand.setGravity(true);
            stand.setCustomName(ChatColor.WHITE + "📜 Паперова лялька");
            stand.setCustomNameVisible(true);
            stand.getEquipment().setHelmet(new ItemStack(Material.PAPER));
        });

        // 2. Calculate escape location (behind the caster, away from damage source)
        Vector escapeDir;
        if (damageEvent.getEntity().getLocation().getDirection() != null) {
            escapeDir = damageEvent.getEntity().getLocation().getDirection().multiply(-1).setY(0).normalize();
        } else {
            escapeDir = originalLoc.getDirection().multiply(-1).setY(0).normalize();
        }

        Location escapeLoc = findEscapeLocation(originalLoc, escapeDir);
        if (escapeLoc == null) {
            escapeLoc = originalLoc.clone().add(0, 2, 0); // Fallback: go up
        }
        escapeLoc.setYaw(originalLoc.getYaw());
        escapeLoc.setPitch(originalLoc.getPitch());

        // 3. Effects at original position
        context.effects().spawnParticle(Particle.FIREWORK, originalLoc.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3);
        context.effects().spawnParticle(Particle.SMOKE, originalLoc.clone().add(0, 1, 0), 15, 0.4, 0.6, 0.4);
        context.effects().playSound(originalLoc, Sound.ENTITY_ITEM_BREAK, 1.5f, 1.0f);
        context.effects().playSound(originalLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.5f);

        // 4. Teleport caster
        context.entity().teleport(casterId, escapeLoc);

        // 5. Arrival effects
        context.effects().spawnParticle(Particle.END_ROD, escapeLoc.clone().add(0, 1, 0), 10, 0.2, 0.3, 0.2);
        context.messaging().sendMessageToActionBar(casterId,
                Component.text("📜 Паперова лялька прийняла удар!", NamedTextColor.GREEN));

        // 6. Remove figurine after 3 seconds with dissolution effect
        context.scheduling().scheduleDelayed(() -> {
            if (figurine.isValid()) {
                Location figLoc = figurine.getLocation();
                context.effects().spawnParticle(Particle.SMOKE, figLoc.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3);

                Particle.DustOptions whiteDust = new Particle.DustOptions(Color.WHITE, 1.0f);
                world.spawnParticle(Particle.DUST, figLoc.clone().add(0, 1, 0), 15, 0.3, 0.4, 0.3, 0, whiteDust);

                context.effects().playSound(figLoc, Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.5f);
                figurine.remove();
            }
        }, 60L); // 3 seconds

        // Cancel the shield
        cancelShield(context, casterId);
    }

    private Location findEscapeLocation(Location origin, Vector direction) {
        for (int dist = (int) ESCAPE_DISTANCE; dist >= 2; dist--) {
            Location check = origin.clone().add(direction.clone().multiply(dist));
            if (isSafe(check)) return check;
        }
        return null;
    }

    private boolean isSafe(Location loc) {
        if (loc.getWorld() == null) return false;
        Block feet = loc.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);

        return feet.isPassable() && head.isPassable() && ground.getType().isSolid();
    }

    private void cancelShield(IAbilityContext context, UUID casterId) {
        BukkitTask task = activeShields.remove(casterId);
        if (task != null) task.cancel();
        context.events().unsubscribeAll(casterId);
    }

    @Override
    public void cleanUp() {
        for (BukkitTask task : activeShields.values()) {
            task.cancel();
        }
        activeShields.clear();
    }
}
