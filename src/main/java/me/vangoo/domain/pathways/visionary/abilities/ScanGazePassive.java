package me.vangoo.domain.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.ToggleablePassiveAbility;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ScanGazePassive extends ToggleablePassiveAbility {
    private static final int RANGE = 10;
    private static final String IDENTITY = "scan_gaze";
    private static final int CHECK_INTERVAL = 20;

    // Перенесли стани на рівень кастера (UUID -> lastTargetUUID / tickCounter)
    private final ConcurrentMap<UUID, UUID> lastTargets = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Integer> tickCounters = new ConcurrentHashMap<>();

    @Override
    public AbilityIdentity getIdentity() {
        return AbilityIdentity.of(IDENTITY);
    }

    @Override
    public String getName() {
        return "[Пасивна] Сканування поглядом";
    }

    @Override
    public String getDescription(Sequence sequence) {
        return "Автоматично показує інформацію про гравців, на яких ви дивитесь. " +
                "Увімкніть/вимкніть через меню здібностей.";
    }

    @Override
    public void onEnable(IAbilityContext context) {
        context.effects().playSoundForPlayer(
                context.getCasterId(),
                org.bukkit.Sound.BLOCK_BEACON_ACTIVATE,
                0.5f,
                1.5f
        );
    }

    @Override
    public void onDisable(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        // очищуємо стан тільки для цього кастера
        lastTargets.remove(casterId);
        tickCounters.remove(casterId);

        context.effects().playSoundForPlayer(
                context.getCasterId(),
                org.bukkit.Sound.BLOCK_BEACON_DEACTIVATE,
                0.5f,
                1.0f
        );
    }

    @Override
    public void tick(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        if (casterId == null) return;

        int counter = tickCounters.getOrDefault(casterId, 0) + 1;
        // зберігаємо оновлений лічильник
        tickCounters.put(casterId, counter);

        // Only check periodically to reduce overhead
        if (counter % CHECK_INTERVAL != 0) {
            return;
        }

        // скидаємо лічильник після перевірки (щоб він не ріс безкінечно)
        tickCounters.put(casterId, 0);

        Optional<Player> targetOpt = context.targeting().getTargetedPlayer(RANGE);

        if (targetOpt.isEmpty()) {
            lastTargets.remove(casterId);
            return;
        }

        Player target = targetOpt.get();
        UUID last = lastTargets.get(casterId);

        // Only show info if target changed
        if (last != null && last.equals(target.getUniqueId())) {
            return;
        }

        lastTargets.put(casterId, target.getUniqueId());

        // Show scan information (now as hologram only for caster)
        showScanInfo(context, target);
        // Subtle sound effect
        context.effects().playSoundForPlayer(
                context.getCasterId(),
                org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME,
                0.3f,
                2.0f
        );
    }

    private void showScanInfo(IAbilityContext context, Player target) {
        Beyonder caster = context.getCasterBeyonder();
        if (caster == null) return;

        // отримати Player-об'єкт кастера з context
        Player casterPlayer = context.getCasterPlayer(); // або інший метод, який у вас є

        boolean showAdvanced = caster.getSequenceLevel() < 7;

        // === БАЗОВІ ДАНІ ===
        double hp = Math.round(target.getHealth() * 10.0) / 10.0;
        double maxHp = target.getAttribute(Attribute.MAX_HEALTH) != null
                ? Math.round(target.getAttribute(Attribute.MAX_HEALTH).getValue() * 10.0) / 10.0
                : 20.0;

        int hunger = target.getFoodLevel();

        StringBuilder msg = new StringBuilder();
        msg.append(ChatColor.RED).append("❤ ").append(hp).append("/").append(maxHp).append("  ")
                .append(ChatColor.GOLD).append("🍖 ").append(hunger).append("/20");

        // === РОЗШИРЕНА ІНФА (Seq < 7) ===
        if (showAdvanced) {
            float saturation = target.getSaturation();
            msg.append(ChatColor.YELLOW)
                    .append("  ✦ Sat: ")
                    .append(Math.round(saturation * 10.0) / 10.0);

            if (!target.getActivePotionEffects().isEmpty()) {
                msg.append(ChatColor.DARK_PURPLE).append("  ✦ ");

                int shown = 0;
                for (PotionEffect effect : target.getActivePotionEffects()) {
                    if (shown++ >= 2) break; // не перевантажуємо голограму

                    String name = effect.getType().getKey().getKey();
                    int amp = effect.getAmplifier() + 1;

                    msg.append(name)
                            .append(" ")
                            .append(amp)
                            .append(" ");
                }
            }
        }

        // === HOLOGRAM (лише для кастера) ===
        Component comp = LegacyComponentSerializer.legacySection().deserialize(msg.toString());

        long durationTicks = 200L; // 10 секунд
        long updateIntervalTicks = 1L; // оновлювати кожен тік

        // Передаємо casterPlayer як viewer — голограма буде видима лише йому
        context.messaging().spawnFollowingHologramForPlayer(casterPlayer, target, comp, durationTicks, updateIntervalTicks);
    }

    @Override
    public void cleanUp() {
        // повністю очистити всі стани — викликається при деініціалізації/перезавантаженні
        lastTargets.clear();
        tickCounters.clear();
    }
}
