package me.vangoo.implementation.VisionaryPathway.abilities;

import de.slikey.effectlib.effect.SphereEffect;
import me.vangoo.domain.Ability;
import me.vangoo.domain.Beyonder;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Appeasement extends Ability {
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
        return "В радіусі " + RANGE + " блоків прибирає всі небажані ефекти, надає Регенерацію I на " + REGENERATION_SECONDS + " секунд та зменшує втрату контролю.";
    }

    @Override
    public int getSpiritualityCost() {
        return 100;
    }

    @Override
    public boolean execute(Player caster, Beyonder beyonder) {
        Location casterLoc = caster.getLocation();

        // Створення візуального ефекту calm zone
        createCalmZoneEffect(casterLoc);

        // Звукові ефекти
        casterLoc.getWorld().playSound(casterLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.2f);
        casterLoc.getWorld().playSound(casterLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.5f);
        casterLoc.getWorld().playSound(casterLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.3f);

        // Знаходимо всі живі сутності в радіусі
        Collection<Entity> nearbyEntities = casterLoc.getWorld().getNearbyEntities(
                casterLoc, RANGE, RANGE, RANGE
        );
        int affectedCount = 0;

        for (Entity entity : nearbyEntities) {
            if (entity instanceof LivingEntity) {
                LivingEntity livingEntity = (LivingEntity) entity;

                // Прибираємо всі ефекти зілля (негативні і позитивні)
                for (PotionEffect effect : livingEntity.getActivePotionEffects()) {
                    livingEntity.removePotionEffect(effect.getType());
                }

                // Накладаємо ефект Регенерації I на 7 секунд
                int regenerationDurationTicks = REGENERATION_SECONDS * 20;
                livingEntity.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, regenerationDurationTicks, 0));

                // Для гравців: скидаємо втрату контролю
                if (livingEntity instanceof Player) {
                    Player player = (Player) livingEntity;

                    Beyonder targetBeyonder = getBeyonderForPlayer(player);

                    if (targetBeyonder != null && targetBeyonder.getSanityLossScale() > 0) {
                        int currentLoss = targetBeyonder.getSanityLossScale();
                        int newLoss = Math.max(0, currentLoss - SANITY_REDUCTION);
                        targetBeyonder.setSanityLossScale(newLoss);

                        player.sendMessage(ChatColor.AQUA + "✦ Умиротворення заспокоює хаос у вашому розумі...");
                        player.sendMessage(ChatColor.GREEN + "Втрата контролю: " + currentLoss + " → " + newLoss);
                    }

                    // Візуальний ефект лікування для гравця
                    createHealingEffectForPlayer(player);
                } else {
                    // Для мобів
                    createHealingEffectForEntity(livingEntity);
                }

                affectedCount++;
            }
        }

        // Повідомлення кастеру
        caster.sendMessage(ChatColor.GREEN + "✦ Умиротворення вплинуло на " + affectedCount + " істот");

        return true;
    }

    private void createCalmZoneEffect(Location location) {
        // Основна сфера умиротворення (біла/блакитна)
        SphereEffect mainSphere = new SphereEffect(plugin.getEffectManager());
        mainSphere.setLocation(location.clone().add(0, 1, 0));
        mainSphere.particle = Particle.END_ROD;
        mainSphere.radius = RANGE;
        mainSphere.particles = 60;
        mainSphere.iterations = 25;
        mainSphere.period = 1;
        mainSphere.start();

        // Внутрішня сфера (спокійні частинки)
        SphereEffect innerSphere = new SphereEffect(plugin.getEffectManager());
        innerSphere.setLocation(location.clone().add(0, 1, 0));
        innerSphere.particle = Particle.ENCHANT;
        innerSphere.radius = RANGE * 0.7;
        innerSphere.particles = 40;
        innerSphere.iterations = 20;
        innerSphere.period = 2;
        innerSphere.start();

        // Випромінювання від центру (спіраль)
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 20) {
                    cancel();
                    return;
                }

                double angle = ticks * 0.5;
                for (int i = 0; i < 8; i++) {
                    double currentAngle = angle + (Math.PI * 2 * i) / 8;
                    double radius = RANGE * (1 - ticks / 20.0);

                    double x = Math.cos(currentAngle) * radius;
                    double z = Math.sin(currentAngle) * radius;
                    double y = ticks * 0.1;

                    Location particleLoc = location.clone().add(x, y, z);
                    location.getWorld().spawnParticle(
                            Particle.DOLPHIN,
                            particleLoc,
                            1, 0, 0, 0, 0
                    );
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Зоряні партикли навколо
        location.getWorld().spawnParticle(
                Particle.FIREWORK,
                location.clone().add(0, 1, 0),
                50, RANGE * 0.8, 1.5, RANGE * 0.8, 0.05
        );

        // Заспокійливі частинки (блакитні)
        location.getWorld().spawnParticle(
                Particle.SOUL,
                location.clone().add(0, 0.5, 0),
                30, RANGE, 0.5, RANGE, 0.02
        );
    }

    private void createHealingEffectForPlayer(Player player) {
        Location playerLoc = player.getLocation();

        // Серця
        player.getWorld().spawnParticle(
                Particle.HEART,
                playerLoc.clone().add(0, 2, 0),
                5, 0.5, 0.5, 0.5, 0
        );

        // Щасливі сільські жителі
        player.getWorld().spawnParticle(
                Particle.HAPPY_VILLAGER,
                playerLoc.clone().add(0, 1, 0),
                15, 0.5, 0.5, 0.5, 0
        );

        // Спіраль навколо гравця
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 15) {
                    cancel();
                    return;
                }

                double angle = ticks * 0.4;
                for (int i = 0; i < 3; i++) {
                    double currentAngle = angle + (Math.PI * 2 * i) / 3;
                    double x = Math.cos(currentAngle) * 0.8;
                    double z = Math.sin(currentAngle) * 0.8;
                    double y = 0.5 + (ticks * 0.1);

                    Location particleLoc = playerLoc.clone().add(x, y, z);
                    player.getWorld().spawnParticle(
                            Particle.END_ROD,
                            particleLoc,
                            1, 0, 0, 0, 0
                    );
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        // Звук лікування
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 2.0f);
    }

    private void createHealingEffectForEntity(LivingEntity entity) {
        Location entityLoc = entity.getLocation();

        // Зелені партикли лікування
        entity.getWorld().spawnParticle(
                Particle.HAPPY_VILLAGER,
                entityLoc.clone().add(0, 1, 0),
                10, 0.5, 0.5, 0.5, 0
        );

        // Блискучі частинки
        entity.getWorld().spawnParticle(
                Particle.END_ROD,
                entityLoc.clone().add(0, 0.5, 0),
                5, 0.3, 0.3, 0.3, 0.02
        );
    }

    private Beyonder getBeyonderForPlayer(Player player) {
        // Використовуємо статичне поле 'plugin' з базового класу Ability для доступу до менеджера
        return plugin.getBeyonderManager().GetBeyonder(player.getUniqueId());
    }

    @Override
    public ItemStack getItem() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("Дальність", RANGE + " блоків");
        attributes.put("Ефект", "Очищення + Регенерація I (" + REGENERATION_SECONDS + "с)");
        attributes.put("Спеціальне", "Зменшує втрату контролю");
        return abilityItemFactory.createItem(this, attributes);
    }

    @Override
    public int getCooldown() {
        return COOLDOWN;
    }
}