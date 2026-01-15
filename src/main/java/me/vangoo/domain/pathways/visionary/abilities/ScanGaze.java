package me.vangoo.domain.pathways.visionary.abilities;


import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.Optional;


public class ScanGaze extends ActiveAbility {
    private static final int RANGE = 5;

    @Override
    public String getName() {
        return "Сканування поглядом";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "При натисканні на гравця показує його HP, голод та броню. " +
                "Для послідовностей вище за 9: показує додаткову інформацію (насичення, активні ефекти).";
    }

    @Override
    public AbilityIdentity getIdentity() {
        return AbilityIdentity.of("scan_gaze");
    }

    @Override
    public int getSpiritualityCost() {
        return 5;
    }

    @Override
    protected Optional<LivingEntity> getSequenceCheckTarget(IAbilityContext context) {
        return context.targeting().getTargetedPlayer(RANGE)
                .filter(p -> context.isBeyonder(p.getUniqueId()))
                .map(p -> p);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Optional<Player> targetedPlayer = context.targeting().getTargetedPlayer(RANGE);

        if (targetedPlayer.isEmpty()) {
            context.messaging().sendMessageToActionBar(context.getCasterId(),
                    net.kyori.adventure.text.Component.text(
                            ChatColor.RED + "Немає цілі в радіусі " + RANGE + " блоків"
                    )
            );
            return AbilityResult.failure("No valid target found");
        }

        Player target = targetedPlayer.get();

        // === БАЗОВІ ДАНІ ===
        double hp = Math.round(context.playerData().getHealth(target.getUniqueId()) * 10.0) / 10.0;
        double maxHp = 20.0;
        if (target.getAttribute(Attribute.MAX_HEALTH) != null) {
            maxHp = Math.round(context.playerData().getMaxHealth(target.getUniqueId()) * 10.0) / 10.0;
        }

        int hunger = context.playerData().getFoodLevel(target.getUniqueId());

        int armor = 0;
        if (target.getAttribute(Attribute.ARMOR) != null) {
            armor = (int) Math.round(target.getAttribute(Attribute.ARMOR).getValue());
        }

        StringBuilder message = new StringBuilder();
        message.append(ChatColor.AQUA).append("🔍 ").append(target.getName()).append("  ")
                .append(ChatColor.RED).append("❤ ").append(hp).append("/").append(maxHp).append("  ")
                .append(ChatColor.GOLD).append("🍖 ").append(hunger).append("/20  ")
                .append(ChatColor.GRAY).append("🛡 ").append(armor);

        // === ДОДАТКОВО ДЛЯ SEQ < 9 ===
        Beyonder caster = context.getCasterBeyonder();
        if (caster != null && caster.getSequenceLevel() < 9) {
            float saturation = context.playerData().getSaturation(target.getUniqueId());
            message.append(ChatColor.YELLOW)
                    .append("  ✦ Sat: ")
                    .append(Math.round(saturation * 10.0) / 10.0);

            if (!context.playerData().getActivePotionEffects(target.getUniqueId()).isEmpty()) {
                message.append(ChatColor.DARK_PURPLE).append("  ✦ ");
                int shown = 0;

                for (PotionEffect effect : context.playerData().getActivePotionEffects(target.getUniqueId())) {
                    if (shown++ >= 2) break; // не перевантажуємо action bar

                    String name = effect.getType().getKey().getKey();
                    int amp = effect.getAmplifier() + 1;
                    int duration = effect.getDuration() / 20;

                    message.append(name)
                            .append(" ")
                            .append(amp)
                            .append(" (")
                            .append(duration)
                            .append("с) ");
                }
            }
        }

        // === ACTION BAR ===
        context.sendMessageToActionBar(
                net.kyori.adventure.text.Component.text(message.toString())
        );

        return AbilityResult.success();
    }


    @Override
    public int getCooldown(Sequence userSequence) {
        return 10;
    }
}
