package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.SequenceBasedSuccessChance;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class Authority extends ActiveAbility {

    private static final double RADIUS = 15.0;
    private static final int BUILDUP_TIME_TICKS = 120;
    private static final int SLOWNESS_DURATION_TICKS = 200;
    private static final int SLOWNESS_AMPLIFIER = 0;
    private static final double MOB_DAMAGE = 8.0; // 4 hearts

    @Override
    public String getName() {
        return "Authority";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Випромінюєш ауру неоспорюваного авторитету.\n" +
                ChatColor.GRAY + "▪ Радіус: " + ChatColor.WHITE + (int)RADIUS + " блоків\n" +
                ChatColor.GRAY + "▪ Затримка: " + ChatColor.WHITE + "6 секунд\n" +
                ChatColor.GRAY + "▪ Ефект: Викидання предметів + броні\n" +
                ChatColor.GRAY + "▪ Дебаф: Повільність I на 10с\n" +
                ChatColor.GRAY + "▪ Моби: " + ChatColor.WHITE + "8 урону (4 серця)\n" +
                ChatColor.DARK_GRAY + "▸ Висока послідовність = більший шанс";
    }

    @Override
    public int getSpiritualityCost() {
        return 40;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return 30;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        List<Player> nearbyPlayers = context.getNearbyPlayers(RADIUS);
        List<LivingEntity> nearbyEntities = context.getNearbyEntities(RADIUS);

        // Видаляємо гравців зі списку ентіті
        nearbyEntities.removeIf(entity -> entity instanceof Player);

        Beyonder caster = context.getCasterBeyonder();
        int casterSequence = caster.getSequenceLevel();

        showAuraActivation(context);

        if (nearbyPlayers.isEmpty() && nearbyEntities.isEmpty()) {
            context.sendMessageToCaster(
                    ChatColor.GOLD + "Аура Авторитету активована! " +
                            ChatColor.GRAY + "Поруч немає живих істот..."
            );
            return AbilityResult.success();
        }

        context.sendMessageToCaster(
                ChatColor.GOLD + "Аура Авторитету активована! " +
                        ChatColor.GRAY + "Ефект через 6 секунд..."
        );

        Set<UUID> affectedPlayers = new HashSet<>();
        Set<UUID> resistedPlayers = new HashSet<>();

        scheduleBuildupEffects(context, nearbyPlayers);

        context.scheduleDelayed(() -> {
            executeAuthorityEffect(
                    context,
                    nearbyPlayers,
                    casterSequence,
                    affectedPlayers,
                    resistedPlayers
            );

            // Атакуємо мобів
            for (LivingEntity entity : nearbyEntities) {
                if (entity.isValid() && !entity.isDead()) {
                    context.damage(entity.getUniqueId(), MOB_DAMAGE);
                    showMobDamageEffect(context, entity);
                }
            }

            showResultStatistics(context, affectedPlayers, resistedPlayers, nearbyEntities.size());

        }, BUILDUP_TIME_TICKS);

        return AbilityResult.success();
    }

    private void scheduleBuildupEffects(IAbilityContext context, List<Player> targets) {
        context.scheduleDelayed(() -> {
            showBuildupWarning(context, targets, 1);
        }, 40L);

        context.scheduleDelayed(() -> {
            showBuildupWarning(context, targets, 2);
        }, 80L);

        context.scheduleDelayed(() -> {
            showBuildupWarning(context, targets, 3);
        }, 110L);
    }

    private void showBuildupWarning(IAbilityContext context, List<Player> targets, int stage) {
        for (Player target : targets) {
            if (!target.isOnline()) continue;

            Location loc = target.getLocation().add(0, 1, 0);

            int particleCount = stage * 10;
            float soundPitch = 0.5f + (stage * 0.3f);

            context.spawnParticle(
                    Particle.ENCHANT,
                    loc,
                    particleCount,
                    0.5, 0.5, 0.5
            );

            context.spawnParticle(
                    Particle.END_ROD,
                    loc,
                    stage * 3,
                    0.3, 0.3, 0.3
            );

            context.playSound(
                    loc,
                    Sound.BLOCK_BELL_USE,
                    0.5f,
                    soundPitch
            );

            String warning = switch (stage) {
                case 1 -> ChatColor.YELLOW + "⚠ Відчуваєш тиск авторитету...";
                case 2 -> ChatColor.GOLD + "⚠⚠ Важко опиратися...";
                case 3 -> ChatColor.RED + "⚠⚠⚠ НЕМОЖЛИВО ОПИРАТИСЯ!";
                default -> "";
            };

            context.sendMessage(target.getUniqueId(), warning);
        }
    }

    private void executeAuthorityEffect(
            IAbilityContext context,
            List<Player> targets,
            int casterSequence,
            Set<UUID> affectedPlayers,
            Set<UUID> resistedPlayers
    ) {
        for (Player target : targets) {
            if (!target.isOnline()) continue;

            UUID targetId = target.getUniqueId();

            Beyonder targetBeyonder = context.getBeyonderFromEntity(targetId);

            boolean resisted = false;

            if (targetBeyonder != null) {
                int targetSequence = targetBeyonder.getSequenceLevel();

                SequenceBasedSuccessChance successChance =
                        new SequenceBasedSuccessChance(casterSequence, targetSequence);

                if (!successChance.rollSuccess()) {
                    resisted = true;
                    resistedPlayers.add(targetId);

                    showResistanceEffect(context, target, successChance);
                    continue;
                }
            }

            applyAuthorityEffect(context, target);
            affectedPlayers.add(targetId);
        }
    }

    private void applyAuthorityEffect(IAbilityContext context, Player target) {
        UUID targetId = target.getUniqueId();
        PlayerInventory inv = target.getInventory();
        Location dropLocation = target.getLocation().add(0, 1.5, 0);

        ItemStack mainHand = inv.getItemInMainHand();
        if (mainHand.getType() != Material.AIR) {
            target.getWorld().dropItem(dropLocation, mainHand.clone());
            inv.setItemInMainHand(new ItemStack(Material.AIR));
        }

        ItemStack offHand = inv.getItemInOffHand();
        if (offHand.getType() != Material.AIR) {
            target.getWorld().dropItem(dropLocation, offHand.clone());
            inv.setItemInOffHand(new ItemStack(Material.AIR));
        }

        removeAndDropArmor(context, target, inv, dropLocation);

        context.applyEffect(
                targetId,
                PotionEffectType.SLOWNESS,
                SLOWNESS_DURATION_TICKS,
                SLOWNESS_AMPLIFIER
        );

        showAuthorityEffect(context, target);

        context.sendMessage(
                targetId,
                ChatColor.RED + "Ти не можеш опиратися цьому авторитету!"
        );
    }

    private void removeAndDropArmor(
            IAbilityContext context,
            Player target,
            PlayerInventory inv,
            Location dropLocation
    ) {
        ItemStack helmet = inv.getHelmet();
        ItemStack chestplate = inv.getChestplate();
        ItemStack leggings = inv.getLeggings();
        ItemStack boots = inv.getBoots();

        inv.setHelmet(null);
        inv.setChestplate(null);
        inv.setLeggings(null);
        inv.setBoots(null);

        List<ItemStack> armorPieces = Arrays.asList(helmet, chestplate, leggings, boots);

        for (ItemStack armor : armorPieces) {
            if (armor == null || armor.getType() == Material.AIR) {
                continue;
            }

            HashMap<Integer, ItemStack> leftover = inv.addItem(armor);

            if (!leftover.isEmpty()) {
                for (ItemStack drop : leftover.values()) {
                    target.getWorld().dropItem(dropLocation, drop);
                }
            }
        }
    }

    private void showAuthorityEffect(IAbilityContext context, Player target) {
        Location loc = target.getLocation().add(0, 1, 0);

        context.spawnParticle(
                Particle.EXPLOSION,
                loc,
                1,
                0, 0, 0
        );

        // ВИПРАВЛЕННЯ:
        // FALLING_DUST викликав помилку, бо вимагав BlockData.
        // Замінено на CRIT (золоті частинки), що пасує темі і не крашить сервер.
        context.spawnParticle(
                Particle.CRIT,
                loc,
                30,
                0.5, 1.0, 0.5
        );

        context.spawnParticle(
                Particle.END_ROD,
                loc,
                20,
                0.3, 0.5, 0.3
        );

        context.playSound(
                loc,
                Sound.ENTITY_LIGHTNING_BOLT_THUNDER,
                0.5f,
                1.2f
        );

        context.playSound(
                loc,
                Sound.ENTITY_WITHER_BREAK_BLOCK,
                0.7f,
                0.8f
        );
    }

    private void showMobDamageEffect(IAbilityContext context, LivingEntity entity) {
        Location loc = entity.getLocation().add(0, 1, 0);

        context.spawnParticle(
                Particle.DAMAGE_INDICATOR,
                loc,
                15,
                0.3, 0.5, 0.3
        );

        context.spawnParticle(
                Particle.CRIT,
                loc,
                10,
                0.2, 0.3, 0.2
        );

        context.playSound(
                loc,
                Sound.ENTITY_PLAYER_ATTACK_STRONG,
                0.8f,
                0.9f
        );
    }

    private void showResistanceEffect(
            IAbilityContext context,
            Player target,
            SequenceBasedSuccessChance successChance
    ) {
        Location loc = target.getLocation().add(0, 1, 0);

        context.spawnParticle(
                Particle.FIREWORK,
                loc,
                20,
                0.5, 0.5, 0.5
        );

        context.playSound(
                loc,
                Sound.ITEM_SHIELD_BLOCK,
                1.0f,
                1.2f
        );

        context.sendMessage(
                target.getUniqueId(),
                ChatColor.GREEN + "Ти зміг опиратися! " +
                        ChatColor.GRAY + "(Шанс: " + successChance.getFormattedChance() + ")"
        );
    }

    private void showAuraActivation(IAbilityContext context) {
        Location centerLoc = context.getCasterLocation();

        context.playSphereEffect(
                centerLoc.add(0, 1, 0),
                RADIUS,
                Particle.ENCHANT,
                60
        );

        context.playWaveEffect(
                centerLoc,
                RADIUS,
                Particle.GLOW,
                40
        );

        context.playLineEffect(
                centerLoc,
                centerLoc.clone().add(0, 5, 0),
                Particle.END_ROD
        );

        context.playSoundToCaster(Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.0f, 0.8f);
        context.playSoundToCaster(Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);

        context.playCircleEffect(
                centerLoc,
                RADIUS,
                Particle.SOUL_FIRE_FLAME,
                80
        );
    }

    private void showResultStatistics(
            IAbilityContext context,
            Set<UUID> affectedPlayers,
            Set<UUID> resistedPlayers,
            int affectedMobs
    ) {
        int total = affectedPlayers.size() + resistedPlayers.size() + affectedMobs;

        if (total == 0) {
            context.sendMessageToCaster(
                    ChatColor.YELLOW + "Всі цілі покинули радіус дії"
            );
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append(ChatColor.GOLD).append("═══ Результат Авторитету ═══\n");

        if (!affectedPlayers.isEmpty()) {
            message.append(ChatColor.GREEN)
                    .append("✓ Гравці підкорено: ")
                    .append(ChatColor.WHITE)
                    .append(affectedPlayers.size())
                    .append("\n");
        }

        if (!resistedPlayers.isEmpty()) {
            message.append(ChatColor.RED)
                    .append("✗ Гравці опирались: ")
                    .append(ChatColor.WHITE)
                    .append(resistedPlayers.size())
                    .append("\n");
        }

        if (affectedMobs > 0) {
            message.append(ChatColor.YELLOW)
                    .append("⚔ Моби атаковано: ")
                    .append(ChatColor.WHITE)
                    .append(affectedMobs)
                    .append("\n");
        }

        int successRate = (int)((affectedPlayers.size() * 100.0) / Math.max(1, affectedPlayers.size() + resistedPlayers.size()));
        message.append(ChatColor.GRAY)
                .append("Успішність: ")
                .append(ChatColor.YELLOW)
                .append(successRate)
                .append("%");

        context.sendMessageToCaster(message.toString());

        if (successRate >= 70 || affectedMobs >= 3) {
            context.spawnParticle(
                    Particle.TOTEM_OF_UNDYING,
                    context.getCasterLocation().add(0, 2, 0),
                    30,
                    0.5, 0.5, 0.5
            );
            context.playSoundToCaster(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        } else if (successRate >= 40) {
            context.spawnParticle(
                    Particle.HAPPY_VILLAGER,
                    context.getCasterLocation().add(0, 2, 0),
                    20,
                    0.5, 0.5, 0.5
            );
        } else {
            context.spawnParticle(
                    Particle.SMOKE,
                    context.getCasterLocation().add(0, 2, 0),
                    15,
                    0.5, 0.5, 0.5
            );
        }
    }
}