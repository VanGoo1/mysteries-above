package me.vangoo.domain.pathways.fool.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequence 8: Clown — Expression Control (Контроль міміки)
 *
 * Perfect control over facial expressions and body language. Creates a "mask"
 * that blocks all information-gathering abilities from reading the caster.
 */
public class ExpressionControl extends ActiveAbility {

    private static final int BASE_COST = 60;
    private static final int BASE_COOLDOWN = 45;
    private static final int DURATION_SECONDS = 30;
    private static final int DURATION_TICKS = DURATION_SECONDS * 20;

    // Active masks: casterUUID -> task
    private static final Map<UUID, BukkitTask> activeMasks = new ConcurrentHashMap<>();
    private static final Set<UUID> maskedPlayers = ConcurrentHashMap.newKeySet();

    @Override
    public String getName() {
        return "Контроль міміки";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Надягає ідеальну «маску» на " + DURATION_SECONDS + " секунд. " +
                "Здібності читання (Сканування, Телепатія, Духовне бачення) " +
                "не можуть прочитати ваші наміри та стан.";
    }

    @Override
    public int getSpiritualityCost() {
        return BASE_COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return BASE_COOLDOWN;
    }

    /**
     * Check if a player currently has expression control active.
     * Used by other pathway abilities to determine if their reading attempt should fail.
     */
    public static boolean isMasked(UUID playerId) {
        return maskedPlayers.contains(playerId);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();

        // If already masked, cancel early
        if (maskedPlayers.contains(casterId)) {
            disableMask(context, casterId, "свідоме зняття");
            return AbilityResult.success();
        }

        enableMask(context, casterId);
        return AbilityResult.success();
    }

    private void enableMask(IAbilityContext context, UUID casterId) {
        maskedPlayers.add(casterId);

        context.messaging().sendMessageToActionBar(casterId,
                Component.text("🎭 Маска активна", NamedTextColor.GOLD));
        context.effects().playSoundForPlayer(casterId, Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 1.5f);

        final int[] ticks = {0};

        BukkitTask task = context.scheduling().scheduleRepeating(() -> {
            ticks[0]++;

            if (!context.playerData().isOnline(casterId)) {
                disableMask(context, casterId, "вихід");
                return;
            }

            // Duration check
            if (ticks[0] >= DURATION_TICKS) {
                disableMask(context, casterId, "час вийшов");
                return;
            }

            // Action bar update every 2 seconds
            if (ticks[0] % 40 == 0) {
                int remaining = (DURATION_TICKS - ticks[0]) / 20;
                context.messaging().sendMessageToActionBar(casterId,
                        Component.text("🎭 Маска активна (" + remaining + "с)", NamedTextColor.GOLD));
            }

            // Subtle particles every second
            if (ticks[0] % 20 == 0) {
                var loc = context.playerData().getCurrentLocation(casterId);
                if (loc != null) {
                    context.effects().spawnParticle(Particle.SOUL,
                            loc.clone().add(0, 2.0, 0), 3, 0.15, 0.1, 0.15);
                }
            }
        }, 0L, 1L);

        activeMasks.put(casterId, task);
    }

    private void disableMask(IAbilityContext context, UUID casterId, String reason) {
        maskedPlayers.remove(casterId);

        BukkitTask task = activeMasks.remove(casterId);
        if (task != null) {
            task.cancel();
        }

        context.messaging().sendMessageToActionBar(casterId,
                Component.text("🎭 Маска знята • " + reason, NamedTextColor.GRAY));
        context.effects().playSoundForPlayer(casterId, Sound.BLOCK_IRON_DOOR_CLOSE, 1f, 1.2f);
    }

    @Override
    public void cleanUp() {
        for (BukkitTask task : activeMasks.values()) {
            task.cancel();
        }
        activeMasks.clear();
        maskedPlayers.clear();
    }
}
