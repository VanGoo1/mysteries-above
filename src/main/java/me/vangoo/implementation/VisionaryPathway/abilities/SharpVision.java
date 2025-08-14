package me.vangoo.implementation.VisionaryPathway.abilities;


import me.vangoo.abilities.Ability;
import me.vangoo.beyonders.Beyonder;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class SharpVision extends Ability {
    private static final int RANGE = 100;
    private static final int DURATION_SECONDS = 30;

    @Override
    public String getName() {
        return "Гострий зір";
    }

    @Override
    public String getDescription() {
        return "Дає нічне бачення та підсвічування на " + DURATION_SECONDS + " секунд.";
    }

    @Override
    public int getSpiritualityCost() {
        return 50;
    }


    @Override
    public boolean execute(Player caster, Beyonder beyonder) {
        caster.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, DURATION_SECONDS * 20, 0, true, false));

        // Отримуємо всіх живих істот в радіусі 100 блоків
        Collection<LivingEntity> nearbyEntities = caster.getWorld().getNearbyEntities(caster.getLocation(), 100, 100, 100).stream().filter(entity -> entity instanceof LivingEntity).filter(entity -> !entity.equals(caster)).map(entity -> (LivingEntity) entity).toList();

        try {
            for (Entity e : nearbyEntities) {
                ChatColor glowColor = ChatColor.WHITE;
                plugin.getGlowingEntities().setGlowing(e, caster, glowColor);
            }
        } catch (ReflectiveOperationException e) {
            caster.getServer().getLogger().warning(e.getMessage());
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                // Вимикаємо підсвічування для всіх істот
                try {
                    for (LivingEntity entity : nearbyEntities) {
                        if (entity.isValid()) { // Перевіряємо чи істота ще існує
                            plugin.getGlowingEntities().unsetGlowing(entity, caster);
                        }
                    }
                } catch (ReflectiveOperationException e) {
                    caster.getServer().getLogger().warning(e.getMessage());
                }

            }
        }.runTaskLater(plugin, DURATION_SECONDS * 20L);

        return true;
    }

    @Override
    public ItemStack getItem() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("Діапазон", RANGE + " блоків");

        return abilityItemFactory.createItem(this, attributes);
    }

    @Override
    public int getCooldown() {
        return 30;
    }
}
