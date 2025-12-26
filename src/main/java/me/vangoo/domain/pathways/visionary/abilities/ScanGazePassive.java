package me.vangoo.domain.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.ToggleablePassiveAbility;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.Optional;


public class ScanGazePassive extends ToggleablePassiveAbility {
    private static final int RANGE = 10;
    private static final String IDENTITY = "scan_gaze";
    private static final int CHECK_INTERVAL = 20;

    // Track current target to avoid spam
    private Player lastTarget = null;
    private int tickCounter = 0;

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
        context.playSoundToCaster(
                org.bukkit.Sound.BLOCK_BEACON_ACTIVATE,
                0.5f,
                1.5f
        );
    }

    @Override
    public void onDisable(IAbilityContext context) {
        lastTarget = null;
        context.sendMessageToCaster(
                ChatColor.YELLOW + "✗ Пасивне сканування вимкнено"
        );
        context.playSoundToCaster(
                org.bukkit.Sound.BLOCK_BEACON_DEACTIVATE,
                0.5f,
                1.0f
        );
    }

    @Override
    public void tick(IAbilityContext context) {
        tickCounter++;

        // Only check periodically to reduce overhead
        if (tickCounter % CHECK_INTERVAL != 0) {
            return;
        }

        Optional<Player> targetOpt = context.getTargetedPlayer(RANGE);

        if (targetOpt.isEmpty()) {
            lastTarget = null;
            return;
        }

        Player target = targetOpt.get();

        // Only show info if target changed
        if (target.equals(lastTarget)) {
            return;
        }

        lastTarget = target;

        // Show scan information
        showScanInfo(context, target);

        // Subtle sound effect
        context.playSoundToCaster(
                org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME,
                0.3f,
                2.0f
        );
    }

    private void showScanInfo(IAbilityContext context, Player target) {
        // Enhanced info at higher sequences
        Beyonder caster = context.getCasterBeyonder();
        boolean showAdvanced = caster.getSequenceLevel() < 7;

        // Basic info
        double hp = Math.round(target.getHealth() * 10.0) / 10.0;
        double maxHp = target.getAttribute(Attribute.MAX_HEALTH) != null
                ? Math.round(target.getAttribute(Attribute.MAX_HEALTH).getValue() * 10.0) / 10.0
                : 20.0;

        int hunger = target.getFoodLevel();

        context.sendMessageToCaster(
                ChatColor.DARK_AQUA + "▶ " + ChatColor.WHITE + target.getName()
        );
        context.sendMessageToCaster(
                ChatColor.GRAY + "HP: " + ChatColor.YELLOW + hp + "/" + maxHp +
                        ChatColor.GRAY + " | Голод: " + ChatColor.YELLOW + hunger + "/20"
        );

        // Advanced info at sequence 6+
        if (showAdvanced) {
            float saturation = target.getSaturation();
            context.sendMessageToCaster(
                    ChatColor.GRAY + "Насичення: " + ChatColor.YELLOW +
                            Math.round(saturation * 10.0) / 10.0
            );

            // Show active effects
            var effects = target.getActivePotionEffects();
            if (!effects.isEmpty()) {
                StringBuilder effectsStr = new StringBuilder(ChatColor.GRAY + "Ефекти: ");
                int count = 0;
                for (PotionEffect effect : effects) {
                    if (count > 0) effectsStr.append(ChatColor.GRAY).append(", ");
                    effectsStr.append(ChatColor.YELLOW)
                            .append(effect.getType().getTranslationKey())
                            .append(" ")
                            .append(effect.getAmplifier() + 1);
                    count++;
                    if (count >= 3) break; // Show max 3 effects
                }
                context.sendMessageToCaster(effectsStr.toString());
            }
        }
    }

    @Override
    public void cleanUp() {
        lastTarget = null;
        tickCounter = 0;
    }
}