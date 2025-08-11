package me.vangoo.implementation.VisionaryPathway.abilities;

import me.vangoo.abilities.Ability;
import me.vangoo.beyonders.Beyonder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

public class SharpVision extends Ability {
    private static final int RANGE = 100;

    @Override
    public String getName() {
        return "Гострий зір";
    }

    @Override
    public String getDescription() {
        return "Дає нічне бачення та підсвічування на 30 секунд.";
    }

    @Override
    public int getSpiritualityCost() {
        return 50;
    }


    @Override
    public boolean execute(Player caster, Beyonder beyonder) {
        caster.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 30 * 20, 0, true, false));

        // Отримуємо всіх живих істот в радіусі 100 блоків
        Collection<LivingEntity> nearbyEntities = caster.getWorld().getNearbyEntities(
                        caster.getLocation(), 100, 100, 100
                ).stream()
                .filter(entity -> entity instanceof LivingEntity)
                .filter(entity -> !entity.equals(caster))
                .map(entity -> (LivingEntity) entity)
                .toList();

        // Створюємо персональний scoreboard для гравця
        Scoreboard personalBoard = Bukkit.getScoreboardManager().getNewScoreboard();
        Team glowTeam = personalBoard.registerNewTeam("glowing");
        glowTeam.setColor(ChatColor.YELLOW);

        // Зберігаємо оригінальні стани підсвічування
        Map<UUID, Boolean> originalGlowStates = new HashMap<>();

        for (LivingEntity entity : nearbyEntities) {
            // Зберігаємо оригінальний стан
            originalGlowStates.put(entity.getUniqueId(), entity.isGlowing());

            // Включаємо підсвічування
            entity.setGlowing(true);

            // Додаємо до команди для кольору
            if (entity instanceof Player) {
                glowTeam.addEntry(entity.getName());
            } else {
                // Для мобів створюємо унікальне ім'я
                String mobIdentifier = entity.getType().name() + "_" + entity.getEntityId();
                if (mobIdentifier.length() > 16) {
                    mobIdentifier = mobIdentifier.substring(0, 16);
                }
                glowTeam.addEntry(mobIdentifier);
            }
        }

        caster.setScoreboard(personalBoard);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                for (Map.Entry<UUID, Boolean> entry : originalGlowStates.entrySet()) {
                    UUID entityUUID = entry.getKey();
                    Boolean originalState = entry.getValue();

                    // Знаходимо істоту за UUID
                    for (LivingEntity entity : nearbyEntities) {
                        if (entity.getUniqueId().equals(entityUUID) &&
                                entity.isValid() && !entity.isDead()) {
                            entity.setGlowing(originalState);
                            break;
                        }
                    }
                }

                // Повертаємо основний scoreboard
                caster.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());

                // Очищуємо команду
                if (personalBoard.getTeam("glowing") != null) {
                    personalBoard.getTeam("glowing").unregister();
                }

            } catch (Exception e) {
                // Логуємо помилку
                plugin.getLogger().warning("Error while removing glow effect: " + e.getMessage());
            }

        }, 30 * 20L);

        return true;
    }

    @Override
    public ItemStack getItem() {
        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + getName());
            meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Активна: 30с нічне бачення + підсвічування істот навколо",
                    ChatColor.GRAY + "-----------------------------",
                    ChatColor.GRAY + "Вартість: " + ChatColor.BLUE + getCooldown() / 20 + "c",
                    ChatColor.GRAY + "Діапазон: " + ChatColor.BLUE + RANGE + " блоків",
                    ChatColor.GRAY + "Кулдаун: " + ChatColor.BLUE +"10с"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public int getCooldown() {
        return 600;
    }
}
