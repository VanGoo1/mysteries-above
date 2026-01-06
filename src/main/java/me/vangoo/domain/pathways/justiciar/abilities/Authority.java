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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * Domain Ability: Authority (Авторитет)
 *
 * Активна здібність що:
 * - Випромінює ауру влади
 * - Через 6 секунд примушує гравців викинути предмет з рук
 * - Знімає всю броню (якщо інвентар повний - викидає на землю)
 * - Накладає повільність 1 на 10 секунд
 * - Використовує sequence-based шанси опору
 */
public class Authority extends ActiveAbility {

    private static final double RADIUS = 15.0;
    private static final int BUILDUP_TIME_TICKS = 120; // 6 seconds
    private static final int SLOWNESS_DURATION_TICKS = 200; // 10 seconds
    private static final int SLOWNESS_AMPLIFIER = 0; // Slowness 1

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
                ChatColor.DARK_GRAY + "▸ Висока послідовність = більший шанс";
    }

    @Override
    public int getSpiritualityCost() {
        return 40;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return 30; // 30 seconds
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        List<Player> nearbyPlayers = context.getNearbyPlayers(RADIUS);

        if (nearbyPlayers.isEmpty()) {
            return AbilityResult.failure("В радіусі немає інших гравців!");
        }

        Beyonder caster = context.getCasterBeyonder();
        int casterSequence = caster.getSequenceLevel();

        // Show initial aura activation
        showAuraActivation(context);

        // Notify caster
        context.sendMessageToCaster(
                ChatColor.GOLD + "Аура Авторитету активована! " +
                        ChatColor.GRAY + "Ефект через 6 секунд..."
        );

        // Track affected players for statistics
        Set<UUID> affectedPlayers = new HashSet<>();
        Set<UUID> resistedPlayers = new HashSet<>();

        // Schedule buildup effects (visual warnings)
        scheduleBuildupEffects(context, nearbyPlayers);

        // Schedule main effect after 6 seconds
        context.scheduleDelayed(() -> {
            executeAuthorityEffect(
                    context,
                    nearbyPlayers,
                    casterSequence,
                    affectedPlayers,
                    resistedPlayers
            );

            // Show final statistics to caster
            showResultStatistics(context, affectedPlayers, resistedPlayers);

        }, BUILDUP_TIME_TICKS);

        return AbilityResult.success();
    }

    /**
     * Schedule visual buildup effects during 6 second delay
     */
    private void scheduleBuildupEffects(IAbilityContext context, List<Player> targets) {
        // Warning at 2 seconds (4 seconds remaining)
        context.scheduleDelayed(() -> {
            showBuildupWarning(context, targets, 1);
        }, 40L);

        // Warning at 4 seconds (2 seconds remaining)
        context.scheduleDelayed(() -> {
            showBuildupWarning(context, targets, 2);
        }, 80L);

        // Final warning at 5.5 seconds (0.5 seconds remaining)
        context.scheduleDelayed(() -> {
            showBuildupWarning(context, targets, 3);
        }, 110L);
    }

    /**
     * Show progressive warning effects
     */
    private void showBuildupWarning(IAbilityContext context, List<Player> targets, int stage) {
        for (Player target : targets) {
            if (!target.isOnline()) continue;

            Location loc = target.getLocation().add(0, 1, 0);

            // Progressively more intense effects
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

            // Warning messages
            String warning = switch (stage) {
                case 1 -> ChatColor.YELLOW + "⚠ Відчуваєш тиск авторитету...";
                case 2 -> ChatColor.GOLD + "⚠⚠ Важко опиратися...";
                case 3 -> ChatColor.RED + "⚠⚠⚠ НЕМОЖЛИВО ОПИРАТИСЯ!";
                default -> "";
            };

            context.sendMessage(target.getUniqueId(), warning);
        }
    }

    /**
     * Execute the main authority effect on all targets
     */
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

            // Get target's sequence if they are a beyonder
            Beyonder targetBeyonder = context.getBeyonderFromEntity(targetId);

            boolean resisted = false;

            // Check sequence-based resistance if target is a beyonder
            if (targetBeyonder != null) {
                int targetSequence = targetBeyonder.getSequenceLevel();

                SequenceBasedSuccessChance successChance =
                        new SequenceBasedSuccessChance(casterSequence, targetSequence);

                // Roll for success
                if (!successChance.rollSuccess()) {
                    resisted = true;
                    resistedPlayers.add(targetId);

                    // Show resistance feedback
                    showResistanceEffect(context, target, successChance);
                    continue;
                }
            }

            // Apply authority effect
            applyAuthorityEffect(context, target);
            affectedPlayers.add(targetId);
        }
    }

    /**
     * Apply the full authority effect to a target
     */
    private void applyAuthorityEffect(IAbilityContext context, Player target) {
        UUID targetId = target.getUniqueId();
        PlayerInventory inv = target.getInventory();
        Location dropLocation = target.getLocation().add(0, 1.5, 0);

        // 1. Drop item from main hand
        ItemStack mainHand = inv.getItemInMainHand();
        if (mainHand.getType() != Material.AIR) {
            target.getWorld().dropItem(dropLocation, mainHand.clone());
            inv.setItemInMainHand(new ItemStack(Material.AIR));
        }

        // 2. Drop item from off hand
        ItemStack offHand = inv.getItemInOffHand();
        if (offHand.getType() != Material.AIR) {
            target.getWorld().dropItem(dropLocation, offHand.clone());
            inv.setItemInOffHand(new ItemStack(Material.AIR));
        }

        // 3. Remove and drop armor
        removeAndDropArmor(context, target, inv, dropLocation);

        // 4. Apply slowness effect
        context.applyEffect(
                targetId,
                PotionEffectType.SLOWNESS,
                SLOWNESS_DURATION_TICKS,
                SLOWNESS_AMPLIFIER
        );

        // 5. Show effect on target
        showAuthorityEffect(context, target);

        // 6. Notify target
        context.sendMessage(
                targetId,
                ChatColor.RED + "Ти не можеш опиратися цьому авторитету!"
        );
    }

    /**
     * Remove armor and try to place in inventory, drop if full
     */
    private void removeAndDropArmor(
            IAbilityContext context,
            Player target,
            PlayerInventory inv,
            Location dropLocation
    ) {
        // Get all armor pieces
        ItemStack helmet = inv.getHelmet();
        ItemStack chestplate = inv.getChestplate();
        ItemStack leggings = inv.getLeggings();
        ItemStack boots = inv.getBoots();

        // Clear armor slots
        inv.setHelmet(null);
        inv.setChestplate(null);
        inv.setLeggings(null);
        inv.setBoots(null);

        // Try to add to inventory, drop if can't
        List<ItemStack> armorPieces = Arrays.asList(helmet, chestplate, leggings, boots);

        for (ItemStack armor : armorPieces) {
            if (armor == null || armor.getType() == Material.AIR) {
                continue;
            }

            // Try to add to inventory
            HashMap<Integer, ItemStack> leftover = inv.addItem(armor);

            // If couldn't add (inventory full), drop on ground
            if (!leftover.isEmpty()) {
                for (ItemStack drop : leftover.values()) {
                    target.getWorld().dropItem(dropLocation, drop);
                }
            }
        }
    }

    /**
     * Show visual effect when authority is applied
     */
    private void showAuthorityEffect(IAbilityContext context, Player target) {
        Location loc = target.getLocation().add(0, 1, 0);

        // Explosion-like particle effect
        context.spawnParticle(
                Particle.EXPLOSION,
                loc,
                1,
                0, 0, 0
        );

        // Gold particles falling down (symbolizing authority)
        context.spawnParticle(
                Particle.FALLING_DUST,
                loc,
                30,
                0.5, 1.0, 0.5
        );

        // Flash effect
        context.spawnParticle(
                Particle.END_ROD,
                loc,
                20,
                0.3, 0.5, 0.3
        );

        // Sound effects
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

    /**
     * Show effect when target resists
     */
    private void showResistanceEffect(
            IAbilityContext context,
            Player target,
            SequenceBasedSuccessChance successChance
    ) {
        Location loc = target.getLocation().add(0, 1, 0);

        // Shield-like particles
        context.spawnParticle(
                Particle.FIREWORK,
                loc,
                20,
                0.5, 0.5, 0.5
        );

        // Resistance sound
        context.playSound(
                loc,
                Sound.ITEM_SHIELD_BLOCK,
                1.0f,
                1.2f
        );

        // Notify target of successful resistance
        context.sendMessage(
                target.getUniqueId(),
                ChatColor.GREEN + "Ти зміг опиратися! " +
                        ChatColor.GRAY + "(Шанс: " + successChance.getFormattedChance() + ")"
        );
    }

    /**
     * Show initial aura activation effect
     */
    private void showAuraActivation(IAbilityContext context) {
        Location centerLoc = context.getCasterLocation();

        // Large expanding sphere
        context.playSphereEffect(
                centerLoc.add(0, 1, 0),
                RADIUS,
                Particle.ENCHANT,
                60
        );

        // Wave at ground level
        context.playWaveEffect(
                centerLoc,
                RADIUS,
                Particle.GLOW,
                40
        );

        // Pillar of light at caster
        context.playLineEffect(
                centerLoc,
                centerLoc.clone().add(0, 5, 0),
                Particle.END_ROD
        );

        // Sound effects
        context.playSoundToCaster(Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.0f, 0.8f);
        context.playSoundToCaster(Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);

        // Circle effect
        context.playCircleEffect(
                centerLoc,
                RADIUS,
                Particle.SOUL_FIRE_FLAME,
                80
        );
    }

    /**
     * Show result statistics to caster
     */
    private void showResultStatistics(
            IAbilityContext context,
            Set<UUID> affectedPlayers,
            Set<UUID> resistedPlayers
    ) {
        int total = affectedPlayers.size() + resistedPlayers.size();

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
                    .append("✓ Підкорено: ")
                    .append(ChatColor.WHITE)
                    .append(affectedPlayers.size())
                    .append("\n");
        }

        if (!resistedPlayers.isEmpty()) {
            message.append(ChatColor.RED)
                    .append("✗ Опирались: ")
                    .append(ChatColor.WHITE)
                    .append(resistedPlayers.size())
                    .append("\n");
        }

        int successRate = (int)((affectedPlayers.size() * 100.0) / total);
        message.append(ChatColor.GRAY)
                .append("Успішність: ")
                .append(ChatColor.YELLOW)
                .append(successRate)
                .append("%");

        context.sendMessageToCaster(message.toString());

        // Visual feedback at caster location
        if (successRate >= 70) {
            // High success - golden effect
            context.spawnParticle(
                    Particle.TOTEM_OF_UNDYING,
                    context.getCasterLocation().add(0, 2, 0),
                    30,
                    0.5, 0.5, 0.5
            );
            context.playSoundToCaster(Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        } else if (successRate >= 40) {
            // Medium success
            context.spawnParticle(
                    Particle.HAPPY_VILLAGER,
                    context.getCasterLocation().add(0, 2, 0),
                    20,
                    0.5, 0.5, 0.5
            );
        } else {
            // Low success
            context.spawnParticle(
                    Particle.SMOKE,
                    context.getCasterLocation().add(0, 2, 0),
                    15,
                    0.5, 0.5, 0.5
            );
        }
    }
}