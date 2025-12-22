package me.vangoo.domain.pathways.visionary.abilities;

import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class Appeasement extends ActiveAbility {
    private static final int RANGE = 4;
    private static final int COOLDOWN = 40;
    private static final int SANITY_REDUCTION = 25;
    private static final int REGENERATION_SECONDS = 7;

    @Override
    public String getName() {
        return "Умиротворення";
    }

    @Override
    public String getDescription() {
        return "В радіусі " + RANGE + " блоків прибирає всі небажані ефекти, надає Регенерацію I на " +
                REGENERATION_SECONDS + " секунд та зменшує втрату контролю на " + SANITY_REDUCTION + ".";
    }

    @Override
    public int getSpiritualityCost() {
        return 100;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Location casterLoc = context.getCasterLocation();

        // Візуальні ефекти зони спокою
        context.spawnParticle(Particle.END_ROD, casterLoc, 100, RANGE, 1, RANGE);
        context.spawnParticle(Particle.ENCHANT, casterLoc, 50, RANGE * 0.7, 1, RANGE * 0.7);
        context.spawnParticle(Particle.FIREWORK, casterLoc, 50, RANGE * 0.8, 1.5, RANGE * 0.8);
        context.spawnParticle(Particle.SOUL, casterLoc, 30, RANGE, 0.5, RANGE);

        // Звукові ефекти
        context.playSound(casterLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.2f);
        context.playSound(casterLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);
        context.playSound(casterLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.3f);

        // Знаходимо всі живі сутності в радіусі
        List<LivingEntity> nearbyEntities = context.getNearbyEntities(RANGE);
        int affectedCount = 0;

        for (LivingEntity livingEntity : nearbyEntities) {
            // Прибрати всі ефекти
            context.removeAllEffects(livingEntity.getUniqueId());

            // Накласти Регенерацію I на 7 секунд
            int regenerationDurationTicks = REGENERATION_SECONDS * 20;
            context.applyEffect(
                    livingEntity.getUniqueId(),
                    PotionEffectType.REGENERATION,
                    regenerationDurationTicks,
                    0
            );

            // Для гравців: зменшити втрату контролю
            if (livingEntity instanceof Player) {
                context.updateSanityLoss(livingEntity.getUniqueId(), -SANITY_REDUCTION);

                // Візуальні ефекти лікування для гравця
                Location entityLoc = livingEntity.getLocation();
                context.spawnParticle(Particle.HEART, entityLoc, 5, 0.5, 0.5, 0.5);
                context.spawnParticle(
                        Particle.HAPPY_VILLAGER,
                        entityLoc.clone().add(0, 1, 0),
                        15, 0.5, 0.5, 0.5
                );
                context.playSound(entityLoc, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 2.0f);

                context.sendMessage(
                        livingEntity.getUniqueId(),
                        ChatColor.GREEN + "✦ Ви відчуваєте спокій і ясність розуму"
                );
            } else {
                // Для мобів
                Location entityLoc = livingEntity.getLocation();
                context.spawnParticle(
                        Particle.HAPPY_VILLAGER,
                        entityLoc.clone().add(0, 1, 0),
                        10, 0.5, 0.5, 0.5
                );
                context.spawnParticle(
                        Particle.END_ROD,
                        entityLoc.clone().add(0, 0.5, 0),
                        5, 0.3, 0.3, 0.3
                );
            }

            affectedCount++;
        }

        // Повідомлення кастеру
        context.sendMessageToCaster(
                ChatColor.GREEN + "✦ Умиротворення вплинуло на " + affectedCount + " істот"
        );

        return AbilityResult.success();
    }

    @Override
    public int getCooldown() {
        return COOLDOWN;
    }
}