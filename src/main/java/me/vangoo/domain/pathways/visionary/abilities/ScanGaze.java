package me.vangoo.domain.pathways.visionary.abilities;


import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
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
    public String getDescription() {
        return "При натисканні на гравця показує його HP, голод та броню. " +
                "Для послідовностей вище за 9: показує додаткову інформацію (насичення, активні ефекти).";
    }

    @Override
    public int getSpiritualityCost() {
        return 5;
    }

    @Override
    protected Optional<LivingEntity> getSequenceCheckTarget(IAbilityContext context) {
        return context.getTargetedPlayer(RANGE)
                .filter(p -> context.isBeyonder(p.getUniqueId()))
                .map(p -> p);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Optional<Player> targetedPlayer = context.getTargetedPlayer(RANGE);

        if (!targetedPlayer.isPresent()) {
            context.sendMessageToCaster(
                    ChatColor.RED + "Немає цілі: наведіться на гравця в радіусі " + RANGE + " блоків."
            );
            return AbilityResult.failure("No valid target found");
        }

        Player target = targetedPlayer.get();

        // Базова інформація (завжди)
        double hp = Math.round(target.getHealth() * 10.0) / 10.0;
        double maxHp = 20.0;
        if (target.getAttribute(Attribute.MAX_HEALTH) != null) {
            maxHp = Math.round(target.getAttribute(Attribute.MAX_HEALTH).getValue() * 10.0) / 10.0;
        }

        int hunger = target.getFoodLevel();

        double armor = 0.0;
        if (target.getAttribute(Attribute.ARMOR) != null) {
            armor = Math.round(target.getAttribute(Attribute.ARMOR).getValue());
        }

        // Показати базову інформацію
        context.sendMessage(context.getCasterId(), ChatColor.AQUA + "Сканування " + ChatColor.WHITE + target.getName());
        context.sendMessage(context.getCasterId(),
                ChatColor.GRAY + "Здоров'я: " + ChatColor.YELLOW + hp +
                        ChatColor.GRAY + " / " + ChatColor.YELLOW + maxHp
        );
        context.sendMessage(context.getCasterId(),
                ChatColor.GRAY + "Голод: " + ChatColor.YELLOW + hunger +
                        ChatColor.GRAY + " / " + ChatColor.YELLOW + "20"
        );
        context.sendMessage(context.getCasterId(),
                ChatColor.GRAY + "Броня: " + ChatColor.YELLOW + (int) armor +
                        ChatColor.GRAY + " / " + ChatColor.YELLOW + "20"
        );

        // Додаткова інформація для менших послідовностей
        Beyonder casterBeyonder = context.getCasterBeyonder();
        if (casterBeyonder != null && casterBeyonder.getSequenceLevel() < 9) {
            // Показати насичення
            float saturation = target.getSaturation();
            context.sendMessage(context.getCasterId(),
                    ChatColor.GRAY + "Насичення: " + ChatColor.YELLOW +
                            Math.round(saturation * 10.0) / 10.0
            );

            // Показати активні ефекти
            var activeEffects = target.getActivePotionEffects();
            if (!activeEffects.isEmpty()) {
                context.sendMessage(context.getCasterId(), ChatColor.GRAY + "Активні ефекти:");
                for (PotionEffect effect : activeEffects) {
                    String effectName = effect.getType().getTranslationKey();
                    int amplifier = effect.getAmplifier() + 1;
                    int duration = effect.getDuration() / 20; // ticks to seconds

                    context.sendMessage(context.getCasterId(),
                            ChatColor.GRAY + "  • " + ChatColor.YELLOW + effectName + " " + amplifier +
                                    ChatColor.GRAY + " (" + duration + "с)"
                    );
                }
            } else {
                context.sendMessage(context.getCasterId(),
                        ChatColor.GRAY + "Активні ефекти: " + ChatColor.YELLOW + "немає"
                );
            }
        }

        return AbilityResult.success();
    }

    @Override
    public int getCooldown() {
        return 10;
    }
}
