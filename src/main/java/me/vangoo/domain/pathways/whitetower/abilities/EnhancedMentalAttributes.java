package me.vangoo.domain.pathways.whitetower.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
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
import org.bukkit.event.player.PlayerItemDamageEvent;
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
import java.util.Random;

public class EnhancedMentalAttributes extends PermanentPassiveAbility {

    private static final DecimalFormat DF = new DecimalFormat("#.#"); // Більш точний формат для Seq 6
    private static final int XP_INTERVAL_TICKS = 600;
    private static final int ANALYSIS_INTERVAL_TICKS = 5;
    private static final int TRACE_INTERVAL_TICKS = 10;
    private static final int TREASURE_INTERVAL_TICKS = 40;

    // Константи для Ерудита
    private static final double POLYMATH_DURABILITY_SAVE_CHANCE = 0.35; // 35% шанс зберегти міцність
    private final Random random = new Random();

    private int tickCounter = 0;

    @Override
    public String getName() {
        return "Покращені Ментальні Якості";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        StringBuilder sb = new StringBuilder("Пасивно усуває дезорієнтацію, дає досвід і показує ХП цілі.\n");

        if (userSequence.level() <= 8) {
            sb.append("Розкриває слабкості ворогів, ефекти зілля, підказує розташування скарбів та пасивно накопичує досвід.\n");
        }
        if (userSequence.level() <= 7) {
            sb.append("Сліди ворогів, аналіз спорядження, передчуття засідок.\n");
        }
        if (userSequence.level() <= 6) {
            sb.append("Майстерне володіння інструментами (Квапливість), збереження міцності предметів, глибокий аналіз та прискорене навчання.");
        }
        return sb.toString();
    }

    @Override
    public void onActivate(IAbilityContext context) {
        super.onActivate(context);
        registerPolymathEvents(context);
    }

    @Override
    public void tick(IAbilityContext context) {
        tickCounter++;
        Player player = context.getCasterPlayer();
        if (player == null || !player.isOnline()) return;

        int currentSeq = context.getEntitySequenceLevel(context.getCasterId()).orElse(9);
        boolean isSeq8 = currentSeq <= 8;
        boolean isSeq7 = currentSeq <= 7;
        boolean isSeq6 = currentSeq <= 6; // Ерудит / Полімат

        // --- 1. Mental Clarity & Polymath Efficiency ---
        removeNegativeEffects(context, player, isSeq8);
        if (isSeq6) {
            applyPolymathEfficiency(player);
        }

        // --- 2. Passive Learning ---
        if (tickCounter % XP_INTERVAL_TICKS == 0) {
            givePassiveXP(context, player, isSeq7, isSeq8, isSeq6);
        }

        // --- 3. Analytical Sight & Danger Sense ---
        if (tickCounter % ANALYSIS_INTERVAL_TICKS == 0) {
            if (player.isOnGround() || player.isFlying() || player.isGliding()) { // Ерудит аналізує навіть у польоті
                boolean infoDisplayed = analyzeTarget(context, player, isSeq8, isSeq7, isSeq6);
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
            detectNearestTreasure(context, player, isSeq6);
        }
    }

    // --- ЛОГІКА ПОЛІМАТА (SEQ 6) ---

    private void registerPolymathEvents(IAbilityContext context) {
        // "Ерудит знає, як використовувати речі ефективно" -> Збереження міцності
        context.events().subscribeToTemporaryEvent(context.getCasterId(),
                PlayerItemDamageEvent.class,
                event -> {
                    int seq = context.beyonder().getBeyonder(event.getPlayer().getUniqueId()).getSequenceLevel();
                    return seq <= 6;
                },
                event -> {
                    if (random.nextDouble() < POLYMATH_DURABILITY_SAVE_CHANCE) {
                        event.setCancelled(true);
                        // Візуальний ефект "розумного використання" (іскра)
                        if (random.nextDouble() < 0.1) {
                            // Використовуємо event.getPlayer().getLocation(), бо це надійніше всередині події
                            context.effects().spawnParticle(
                                    Particle.WAX_OFF,
                                    event.getPlayer().getLocation().add(0, 1, 0),
                                    20,
                                    0.3, 0.5, 0.3
                            );
                        }
                    }
                },
                Integer.MAX_VALUE // <--- ДОДАНО 4-й АРГУМЕНТ (Тривалість: назавжди)
        );
    }

    private void applyPolymathEfficiency(Player player) {
        // Якщо гравець тримає інструмент -> даємо Haste (ефективність роботи)
        ItemStack hand = player.getInventory().getItemInMainHand();
        String type = hand.getType().name();

        if (type.contains("PICKAXE") || type.contains("AXE") || type.contains("SHOVEL") || type.contains("HOE")) {
            // Лише якщо немає сильнішого ефекту
            if (!player.hasPotionEffect(PotionEffectType.HASTE)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 10, 0, false, false, true));
            }
        }
    }

    // --- БАЗОВА ЛОГІКА З ПОКРАЩЕННЯМИ ---

    private void removeNegativeEffects(IAbilityContext context, Player player, boolean isSeq8) {
        if (player.hasPotionEffect(PotionEffectType.NAUSEA)) context.removeEffect(player.getUniqueId(), PotionEffectType.NAUSEA);
        if (player.hasPotionEffect(PotionEffectType.BLINDNESS)) context.removeEffect(player.getUniqueId(), PotionEffectType.BLINDNESS);
        if (player.hasPotionEffect(PotionEffectType.DARKNESS)) context.removeEffect(player.getUniqueId(), PotionEffectType.DARKNESS);

        // Полімат також ігнорує сповільнення копання (втому) через ментальну стійкість
        int seq = context.getEntitySequenceLevel(player.getUniqueId()).orElse(9);
        if (seq <= 6 && player.hasPotionEffect(PotionEffectType.MINING_FATIGUE)) {
            context.removeEffect(player.getUniqueId(), PotionEffectType.MINING_FATIGUE);
        }

        if (isSeq8 && player.hasPotionEffect(PotionEffectType.HUNGER)) {
            context.removeEffect(player.getUniqueId(), PotionEffectType.HUNGER);
        }
    }

    private void givePassiveXP(IAbilityContext context, Player player, boolean isSeq7, boolean isSeq8, boolean isSeq6) {
        // Ерудит вчиться набагато швидше
        int xpAmount = isSeq6 ? 8 : (isSeq7 ? 4 : (isSeq8 ? 3 : 2));
        player.giveExp(xpAmount);

        float pitch = isSeq6 ? 2.0f : (isSeq8 ? 1.8f : 1.5f);
        // Менш нав'язливий звук для Ерудита
        if (!isSeq6 || tickCounter % (XP_INTERVAL_TICKS * 2) == 0) {
            context.playSoundToCaster(Sound.ITEM_BOOK_PAGE_TURN, 0.5f, pitch);
        }
    }

    private boolean analyzeTarget(IAbilityContext context, Player player, boolean isDeepAnalysis, boolean isDetectiveAnalysis, boolean isPolymathAnalysis) {
        double range = isPolymathAnalysis ? 35.0 : (isDeepAnalysis ? 25.0 : 15.0);
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                range,
                isPolymathAnalysis ? 1.0 : 0.5, // Ерудит має ширший фокус
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
            Component info = buildTargetInfo(context, target, isDeepAnalysis, isDetectiveAnalysis, isPolymathAnalysis);
            context.sendMessageToActionBar(player, info);

            // Ерудит підсвічує ціль для себе
            if (isPolymathAnalysis) {
                context.spawnParticle(
                        Particle.ENCHANT,
                        context.getCasterLocation().add(0, 1, 0),
                        20,
                        0.3, 0.5, 0.3
                );
            }
            return true;
        }
        return false;
    }

    private Component buildTargetInfo(IAbilityContext context, LivingEntity target, boolean isDeepAnalysis, boolean isDetectiveAnalysis, boolean isPolymathAnalysis) {
        double health = target.getHealth();
        double maxHealth = target.getAttribute(Attribute.MAX_HEALTH) != null ? target.getAttribute(Attribute.MAX_HEALTH).getValue() : 0;
        double armor = target.getAttribute(Attribute.ARMOR) != null ? target.getAttribute(Attribute.ARMOR).getValue() : 0;

        // Полімат бачить точні цифри, решта - округлені
        String hpStr = isPolymathAnalysis ? String.format("%.1f", health) : DF.format(health);
        String maxHpStr = isPolymathAnalysis ? String.format("%.1f", maxHealth) : DF.format(maxHealth);

        Component info = Component.text()
                .append(Component.text(target.getName(), NamedTextColor.GOLD))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("❤ " + hpStr + "/" + maxHpStr,
                        health < maxHealth / 3 ? NamedTextColor.RED : NamedTextColor.GREEN)).build();

        if (armor > 0) {
            info = info.append(Component.text(" | 🛡 " + DF.format(armor), NamedTextColor.AQUA));
        }

        // Ерудит бачить резисти (захист від магії/вогню/снарядів - імітація через аналіз атрибутів або ефектів)
        if (isPolymathAnalysis) {
            double knockbackRes = target.getAttribute(Attribute.KNOCKBACK_RESISTANCE) != null ? target.getAttribute(Attribute.KNOCKBACK_RESISTANCE).getValue() : 0;
            if (knockbackRes > 0) {
                info = info.append(Component.text(" | ⚓", NamedTextColor.GRAY));
            }
        }

        if (isDeepAnalysis && !target.getActivePotionEffects().isEmpty()) {
            List<Component> effectsList = new ArrayList<>();
            for (PotionEffect effect : target.getActivePotionEffects()) {
                String effectName = formatEffectName(effect.getType());
                // Ерудит бачить рівень ефекту (II, III)
                if (isPolymathAnalysis && effect.getAmplifier() > 0) {
                    effectName += " " + (effect.getAmplifier() + 1);
                }

                NamedTextColor color = isPositiveEffect(effect.getType()) ? NamedTextColor.GREEN : NamedTextColor.RED;
                effectsList.add(Component.text(effectName, color));
            }
            // Ерудит бачить більше ефектів
            int limit = isPolymathAnalysis ? 5 : 3;
            if (effectsList.size() > limit) {
                effectsList = effectsList.subList(0, limit);
                effectsList.add(Component.text("...", NamedTextColor.GRAY));
            }
            info = info.append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                    .append(Component.join(JoinConfiguration.separator(Component.text(", ")), effectsList));
        }

        // Логіка спорядження (Детектив+)
        if (isDetectiveAnalysis) {
            EntityEquipment eq = target.getEquipment();
            if (eq != null) {
                ItemStack hand = eq.getItemInMainHand();
                if (hand.getType() != Material.AIR) {
                    String item = hand.getType().name().toLowerCase().replace("_", " ");
                    // Скорочення назв
                    if (item.contains("diamond")) item = "dia." + item.split(" ")[1];
                    else if (item.contains("netherite")) item = "neth." + item.split(" ")[1];
                    else if (item.contains("iron")) item = "iron." + item.split(" ")[1];
                    else if (item.contains("golden")) item = "gold." + item.split(" ")[1];

                    info = info.append(Component.text(" | 🗡 ", NamedTextColor.YELLOW))
                            .append(Component.text(item, NamedTextColor.WHITE));

                    if (hand.getItemMeta() instanceof Damageable dmg) {
                        int percent = (int)((1 - (double)dmg.getDamage() / hand.getType().getMaxDurability()) * 100);
                        NamedTextColor durColor = percent < 30 ? NamedTextColor.RED : NamedTextColor.GREEN;
                        info = info.append(Component.text("(" + percent + "%)", durColor));
                    }

                    // Ерудит бачить зачарування на зброї
                    if (isPolymathAnalysis && hand.hasItemMeta() && hand.getItemMeta().hasEnchants()) {
                        info = info.append(Component.text(" ✨", NamedTextColor.LIGHT_PURPLE));
                    }
                }
            }
        }

        return info;
    }

    private void detectNearestTreasure(IAbilityContext context, Player player, boolean isPolymath) {
        // Ерудит відчуває скарби далі
        int radius = isPolymath ? 25 : 15;
        Block center = player.getLocation().getBlock();
        Block closestBlock = null;
        double closestDistSq = Double.MAX_VALUE;

        // Оптимізація: перевіряємо не кожен блок, а з кроком, або рідше для далеких дистанцій
        // Але для простоти залишимо повний перебір у меншому радіусі, якщо це не викликає лагів
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = center.getRelative(x, y, z);
                    if (isValidContainerType(block.getType())) {
                        // Ерудит ігнорує порожні скрині ще на етапі "чуття"
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
            NamedTextColor distColor = distance < 5 ? NamedTextColor.RED : NamedTextColor.GOLD;
            Component message = Component.text()
                    .append(Component.text(isPolymath ? "Аналіз місцевості виявив цінності: " : "Ви відчуваєте скарби поруч: ", NamedTextColor.AQUA))
                    .append(Component.text(DF.format(distance) + "м", distColor)).build();
            context.sendMessageToActionBar(player, message);
        }
    }

    // --- Допоміжні методи без змін або з мінімальними правками ---

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