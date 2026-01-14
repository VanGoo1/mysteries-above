package me.vangoo.domain.pathways.whitetower.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class EnhancedMentalAttributes extends PermanentPassiveAbility {

    private static final DecimalFormat DF = new DecimalFormat("#");
    private static final int XP_INTERVAL_TICKS = 600;
    private static final int ANALYSIS_INTERVAL_TICKS = 5;
    private static final int TRACE_INTERVAL_TICKS = 10;
    private static final int TREASURE_INTERVAL_TICKS = 40;

    private int tickCounter = 0;

    @Override
    public String getName() {
        return "Покращені Ментальні Якості";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        if (userSequence.level() <= 7) {
            return "Детективне бачення: сліди ворогів, аналіз спорядження, передчуття засідок та пошук доказів (скарбів).";
        }
        if (userSequence.level() == 8) {
            return "Розкриває слабкості ворогів, ефекти зілля, підказує розташування скарбів та пасивно накопичує досвід.";
        }
        return "Пасивно усуває дезорієнтацію, дає досвід і показує ХП цілі.";
    }

    @Override
    public void tick(IAbilityContext context) {
        tickCounter++;
        Player player = context.getCaster();
        if (player == null || !player.isOnline()) return;

        int currentSeq = context.getEntitySequenceLevel(context.getCasterId()).orElse(9);
        boolean isSeq8 = currentSeq <= 8;
        boolean isSeq7 = currentSeq <= 7;

        // --- 1. Mental Clarity ---
        removeNegativeEffects(context, player, isSeq8);

        // --- 2. Passive Learning ---
        if (tickCounter % XP_INTERVAL_TICKS == 0) {
            givePassiveXP(context, player, isSeq7, isSeq8);
        }

        // --- 3. Analytical Sight & Danger Sense ---
        if (tickCounter % ANALYSIS_INTERVAL_TICKS == 0) {
            if (player.isOnGround()) {
                boolean infoDisplayed = analyzeTarget(context, player, isSeq8, isSeq7);
                if (!infoDisplayed && isSeq7) {
                    checkDangerSense(context, player);
                }
            }
        }

        // --- 4. Visual Reconstruction (Traces) ---
        if (isSeq7 && tickCounter % TRACE_INTERVAL_TICKS == 0) {
            visualizeTraces(context, player);
        }

        // --- 5. Treasure Sense ---
        if (isSeq8 && tickCounter % TREASURE_INTERVAL_TICKS == 0) {
            detectNearestTreasure(context, player);
        }
    }

    private void removeNegativeEffects(IAbilityContext context, Player player, boolean isSeq8) {
        if (player.hasPotionEffect(PotionEffectType.NAUSEA)) context.removeEffect(player.getUniqueId(), PotionEffectType.NAUSEA);
        if (player.hasPotionEffect(PotionEffectType.BLINDNESS)) context.removeEffect(player.getUniqueId(), PotionEffectType.BLINDNESS);
        if (player.hasPotionEffect(PotionEffectType.DARKNESS)) context.removeEffect(player.getUniqueId(), PotionEffectType.DARKNESS);
        if (isSeq8 && player.hasPotionEffect(PotionEffectType.HUNGER)) {
            context.removeEffect(player.getUniqueId(), PotionEffectType.HUNGER);
        }
    }

    private void givePassiveXP(IAbilityContext context, Player player, boolean isSeq7, boolean isSeq8) {
        int xpAmount = isSeq7 ? 4 : (isSeq8 ? 3 : 2);
        player.giveExp(xpAmount);
        float pitch = isSeq8 ? 1.8f : 1.5f;
        context.playSoundToCaster(Sound.ITEM_BOOK_PAGE_TURN, 0.5f, pitch);
    }

    private void visualizeTraces(IAbilityContext context, Player player) {
        double range = 15.0;
        for (LivingEntity entity : player.getWorld().getLivingEntities()) {
            if (entity.equals(player)) continue;
            if (entity.getLocation().distanceSquared(player.getLocation()) > range * range) continue;
            if (entity.getVelocity().length() > 0.08 || !entity.isOnGround()) {
                context.spawnParticle(Particle.END_ROD, entity.getLocation(), 0, 0,0,0);
            }
        }
    }

    private boolean checkDangerSense(IAbilityContext context, Player player) {
        double dangerRange = 10.0;
        boolean dangerDetected = false;

        for (LivingEntity entity : player.getWorld().getLivingEntities()) {
            if (entity.equals(player)) continue;
            if (entity.getLocation().distanceSquared(player.getLocation()) > dangerRange * dangerRange) continue;
            if (entity instanceof Mob mob && mob.getTarget() != null && mob.getTarget().equals(player)) {
                Vector toEntity = entity.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
                Vector playerDirection = player.getEyeLocation().getDirection();
                double angle = toEntity.dot(playerDirection);
                if (angle < 0.5) {
                    dangerDetected = true;
                    break;
                }
            }
        }

        if (dangerDetected) {
            Component warning = Component.text("⚠ УВАГА: ", NamedTextColor.RED)
                    .append(Component.text("Зафіксовано ворожий намір!", NamedTextColor.GOLD));
            context.sendMessageToActionBar(player, warning);
            context.playSoundToCaster(Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 2.0f);
            return true;
        }
        return false;
    }

    private boolean analyzeTarget(IAbilityContext context, Player player, boolean isDeepAnalysis, boolean isDetectiveAnalysis) {
        double range = isDeepAnalysis ? 25.0 : 15.0;
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                range,
                0.5,
                entity -> {
                    if (!(entity instanceof LivingEntity) || entity.getUniqueId().equals(player.getUniqueId())) {
                        return false;
                    }
                    if (entity instanceof ArmorStand) {
                        ArmorStand as = (ArmorStand) entity;
                        if (as.isMarker() || !as.isVisible()) {
                            return false;
                        }
                    }
                    return true;
                }
        );

        if (result != null && result.getHitEntity() instanceof LivingEntity target) {
            Component info = buildTargetInfo(context, target, isDeepAnalysis, isDetectiveAnalysis);
            context.sendMessageToActionBar(player, info);
            return true;
        }
        return false;
    }

    private Component buildTargetInfo(IAbilityContext context, LivingEntity target, boolean isDeepAnalysis, boolean isDetectiveAnalysis) {
        double health = target.getHealth();
        double maxHealth = target.getAttribute(Attribute.MAX_HEALTH) != null ? target.getAttribute(Attribute.MAX_HEALTH).getValue() : 0;
        double armor = target.getAttribute(Attribute.ARMOR) != null ? target.getAttribute(Attribute.ARMOR).getValue() : 0;

        Component info = Component.text()
                .append(Component.text(target.getName(), NamedTextColor.GOLD))
                .append(Component.text(" | ХП: ", NamedTextColor.GRAY))
                .append(Component.text(DF.format(health) + "/" + DF.format(maxHealth),
                        health < maxHealth / 3 ? NamedTextColor.RED : NamedTextColor.GREEN))
                .append(Component.text(" | Захист: ", NamedTextColor.GRAY))
                .append(Component.text(DF.format(armor), NamedTextColor.AQUA)).build();

        if (isDeepAnalysis && !target.getActivePotionEffects().isEmpty()) {
            List<Component> effectsList = new ArrayList<>();
            for (PotionEffect effect : target.getActivePotionEffects()) {
                String effectName = formatEffectName(effect.getType());
                NamedTextColor color = isPositiveEffect(effect.getType()) ? NamedTextColor.GREEN : NamedTextColor.RED;
                effectsList.add(Component.text(effectName, color));
            }
            if (effectsList.size() > 3) {
                effectsList = effectsList.subList(0, 3);
                effectsList.add(Component.text("...", NamedTextColor.GRAY));
            }
            info = info.append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                    .append(Component.join(JoinConfiguration.separator(Component.text(", ")), effectsList));
        }

        if (isDetectiveAnalysis) {
            EntityEquipment eq = target.getEquipment();
            if (eq != null) {
                ItemStack hand = eq.getItemInMainHand();
                if (hand.getType() != Material.AIR) {
                    String item = hand.getType().name().toLowerCase().replace("_", " ");
                    if (item.contains("diamond")) item = "d." + item.split(" ")[1];
                    else if (item.contains("netherite")) item = "n." + item.split(" ")[1];
                    else if (item.contains("iron")) item = "i." + item.split(" ")[1];

                    info = info.append(Component.text(" | Hand: ", NamedTextColor.YELLOW))
                            .append(Component.text(item, NamedTextColor.WHITE));

                    if (hand.getItemMeta() instanceof Damageable dmg) {
                        int percent = (int)((1 - (double)dmg.getDamage() / hand.getType().getMaxDurability()) * 100);
                        NamedTextColor durColor = percent < 30 ? NamedTextColor.RED : NamedTextColor.GREEN;
                        info = info.append(Component.text(" (" + percent + "%)", durColor));
                    }
                }
            }
        }

        return info;
    }

    private void detectNearestTreasure(IAbilityContext context, Player player) {
        int radius = 15;
        Block center = player.getLocation().getBlock();
        Block closestBlock = null;
        double closestDistSq = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = center.getRelative(x, y, z);
                    if (isValidContainerType(block.getType())) {
                        if (block.getState() instanceof Container container) {
                            if (container.getInventory().isEmpty()) continue;
                            double distSq = block.getLocation().distanceSquared(player.getLocation());
                            if (distSq < closestDistSq) {
                                closestDistSq = distSq;
                                closestBlock = block;
                            }
                        }
                    }
                }
            }
        }

        if (closestBlock != null) {
            double distance = Math.sqrt(closestDistSq);
            Component message = Component.text()
                    .append(Component.text("Ви відчуваєте скарби поруч: ", NamedTextColor.AQUA))
                    .append(Component.text(DF.format(distance) + "м", NamedTextColor.GOLD)).build();
            context.sendMessageToActionBar(player, message);
        }
    }

    private boolean isValidContainerType(Material type) {
        String name = type.name();
        return name.contains("CHEST") || name.contains("BARREL") || name.contains("SHULKER_BOX");
    }

    private String formatEffectName(PotionEffectType type) {
        String name = type.getKey().getKey();
        if (name.startsWith("minecraft:")) name = name.substring(10);
        return name.length() > 3 ? name.substring(0, 3).toUpperCase() : name.toUpperCase();
    }

    private boolean isPositiveEffect(PotionEffectType type) {
        return type.equals(PotionEffectType.REGENERATION) || type.equals(PotionEffectType.SPEED) ||
                type.equals(PotionEffectType.STRENGTH) || type.equals(PotionEffectType.RESISTANCE) ||
                type.equals(PotionEffectType.FIRE_RESISTANCE) || type.equals(PotionEffectType.ABSORPTION);
    }

    @Override
    public void cleanUp() {
        tickCounter = 0;
    }
}
