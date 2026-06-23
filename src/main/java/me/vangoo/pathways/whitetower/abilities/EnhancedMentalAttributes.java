package me.vangoo.pathways.whitetower.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.text.DecimalFormat;
import java.util.*;

public class EnhancedMentalAttributes extends PermanentPassiveAbility {

    private static final DecimalFormat DF = new DecimalFormat("#.#");
    private static final int XP_INTERVAL_TICKS = 600;
    private static final int ANALYSIS_INTERVAL_TICKS = 5;
    private static final int TRACE_INTERVAL_TICKS = 10;
    private static final int TREASURE_INTERVAL_TICKS = 40;

    // Polymath константи
    private static final double POLYMATH_XP_MULTIPLIER = 1.5; // +50% досвіду
    private static final double POLYMATH_ENCHANT_LUCK = 0.25; // 25% шанс покращити зачарування
    private static final int POLYMATH_BREWING_BONUS = 1; // +1 пляшка при варінні

    private final Random random = new Random();
    private int tickCounter = 0;

    @Override
    public String getName() {
        return "Покращені Ментальні Якості";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        StringBuilder sb = new StringBuilder("Пасивно усуває дезорієнтацію, дає пасивне накопичення досвіду і показує ХП цілі.\n");

        if (userSequence.level() <= 8) {
            sb.append("Розкриває слабкості ворогів, ефекти зілля, підказує розташування скарбів\n");
        }
        if (userSequence.level() <= 7) {
            sb.append("Відчуття приховавих сутностей, аналіз спорядження, передчуття нападів.\n");
        }
        if (userSequence.level() <= 6) {
            sb.append("Збільшена кількість отримання досвіду, шанс покращення атрибуту зачарування, шанс отримати додаткове зілля при звичайому зіллєварінні.");
        }
        return sb.toString();
    }

    @Override
    public void onActivate(IAbilityContext context) {
        super.onActivate(context);
        int seq = context.beyonder().getBeyonder(context.getCasterId()).getSequenceLevel();
        if (seq <= 6) {
            registerPolymathEvents(context);
        }
    }

    @Override
    public void tick(IAbilityContext context) {
        tickCounter++;
        UUID casterId = context.getCasterId();
        if (!context.playerData().isOnline(casterId)) return;

        int currentSeq = context.beyonder().getBeyonder(casterId).getSequenceLevel();
        boolean isSeq8 = currentSeq <= 8;
        boolean isSeq7 = currentSeq <= 7;
        boolean isSeq6 = currentSeq <= 6;

        // --- 1. Mental Clarity ---
        removeNegativeEffects(context, isSeq8, isSeq6);

        // --- 2. Passive Learning ---
        if (tickCounter % XP_INTERVAL_TICKS == 0) {
            givePassiveXP(context, isSeq7, isSeq8, isSeq6);
        }

        // --- 3. Analytical Sight & Danger Sense ---
        if (tickCounter % ANALYSIS_INTERVAL_TICKS == 0) {
            analyzeTarget(context, isSeq8, isSeq7, isSeq6);
            if (isSeq7) {
                checkDangerSense(context); // Тепер перевіряємо це частіше
            }
        }

        // --- 4. Reveal Invisible (ЗАМІНА: Сліди ворогів -> Бачення невидимого) ---
        if (isSeq7 && tickCounter % 10 == 0) {
            revealInvisibleTargets(context);
        }

        // --- 5. Treasure Sense ---
        if (isSeq8 && tickCounter % TREASURE_INTERVAL_TICKS == 0) {
            detectNearestTreasure(context, isSeq6);
        }
    }

    // --- POLYMATH MECHANICS (SEQ 6) ---

    /**
     * Реєструє івенти для Полімата
     */
    private void registerPolymathEvents(IAbilityContext context) {
        UUID casterId = context.getCasterId();

        // 1. Бонус досвіду від всіх джерел
        context.events().subscribeToTemporaryEvent(casterId,
                PlayerExpChangeEvent.class,
                e -> e.getPlayer().getUniqueId().equals(casterId),
                e -> {
                    int originalXP = e.getAmount();
                    int bonusXP = (int) (originalXP * (POLYMATH_XP_MULTIPLIER - 1.0));

                    if (bonusXP > 0) {
                        e.setAmount(originalXP + bonusXP);

                        // Візуальний ефект при великому бонусі
                        if (bonusXP >= 5 && random.nextDouble() < 0.3) {
                            Location loc = context.playerData().getCurrentLocation(casterId);
                            if (loc != null) {
                                context.effects().spawnParticle(
                                        Particle.ENCHANT,
                                        loc.add(0, 1.5, 0),
                                        15,
                                        0.3, 0.3, 0.3
                                );
                                context.effects().playSoundForPlayer(
                                        casterId,
                                        Sound.ENTITY_PLAYER_LEVELUP,
                                        0.3f,
                                        1.8f
                                );
                            }
                        }
                    }
                },
                Integer.MAX_VALUE
        );

        // 2. Покращення зачарувань
        context.events().subscribeToTemporaryEvent(casterId,
                EnchantItemEvent.class,
                e -> e.getEnchanter().getUniqueId().equals(casterId),
                e -> {
                    if (random.nextDouble() < POLYMATH_ENCHANT_LUCK) {
                        // Збільшуємо рівень одного випадкового зачарування
                        Map<org.bukkit.enchantments.Enchantment, Integer> enchants = e.getEnchantsToAdd();
                        if (!enchants.isEmpty()) {
                            List<org.bukkit.enchantments.Enchantment> enchantList = new ArrayList<>(enchants.keySet());
                            org.bukkit.enchantments.Enchantment toBoost = enchantList.get(random.nextInt(enchantList.size()));

                            int currentLevel = enchants.get(toBoost);
                            int maxLevel = toBoost.getMaxLevel();

                            if (currentLevel < maxLevel) {
                                enchants.put(toBoost, currentLevel + 1);

                                // Ефекти
                                context.effects().playSound(
                                        e.getEnchantBlock().getLocation(),
                                        Sound.BLOCK_ENCHANTMENT_TABLE_USE,
                                        1.0f,
                                        1.5f
                                );
                                context.effects().spawnParticle(
                                        Particle.ENCHANT,
                                        e.getEnchantBlock().getLocation().add(0.5, 1.5, 0.5),
                                        30,
                                        0.5, 0.5, 0.5
                                );

                                context.messaging().sendMessage(
                                        casterId,
                                        ChatColor.LIGHT_PURPLE + "✨ Ваші глибокі знання покращили зачарування!"
                                );
                            }
                        }
                    }
                },
                Integer.MAX_VALUE
        );

        // 3. Бонус при варінні зілля
        context.events().subscribeToTemporaryEvent(casterId,
                BrewEvent.class,
                e -> {
                    Location brewLoc = e.getBlock().getLocation();
                    Location playerLoc = context.playerData().getCurrentLocation(casterId);
                    // Перевірка дистанції (гравець має бути поруч)
                    return playerLoc != null && brewLoc.distance(playerLoc) < 5.0;
                },
                e -> {
                    // Шанс 35%
                    if (random.nextDouble() > 0.35) return;

                    context.scheduling().scheduleDelayed(() -> {
                        var contents = e.getContents();
                        ItemStack sourcePotion = null;
                        int emptyStandSlot = -1;

                        // 1. Шукаємо зразок зілля та вільне місце у стійці (тільки нижні слоти 0-2)
                        for (int i = 0; i < 3; i++) {
                            ItemStack item = contents.getItem(i);
                            if (item != null && item.getType().name().contains("POTION")) {
                                if (sourcePotion == null) sourcePotion = item;
                            } else if (item == null || item.getType() == Material.AIR) {
                                if (emptyStandSlot == -1) emptyStandSlot = i;
                            }
                        }

                        // Якщо варити було нічого (дивна помилка), виходимо
                        if (sourcePotion == null) return;

                        ItemStack bonusPotion = sourcePotion.clone();
                        // СЦЕНАРІЙ А: Є місце у варильній стійці
                        if (emptyStandSlot != -1) {
                            contents.setItem(emptyStandSlot, bonusPotion);

                            context.messaging().sendMessage(casterId, ChatColor.AQUA + "⚗ Ваша майстерність створила дублікат у стійці!");
                        }
                        // СЦЕНАРІЙ Б: Стійка повна, даємо в інвентар гравця
                        else if (context.playerData().isOnline(casterId)) {
                            context.entity().giveItem(casterId, bonusPotion);
                        }

                        // Візуальні ефекти (спрацьовують у будь-якому випадку)
                        context.effects().playSound(
                                e.getBlock().getLocation(),
                                Sound.BLOCK_BREWING_STAND_BREW,
                                1.0f,
                                1.3f
                        );
                        context.effects().spawnParticle(
                                Particle.INSTANT_EFFECT,
                                e.getBlock().getLocation().add(0.5, 1.0, 0.5),
                                20,
                                0.3, 0.3, 0.3
                        );

                    }, 2L);
                },
                Integer.MAX_VALUE
        );
    }

    // --- BASE LOGIC ---

    private void removeNegativeEffects(IAbilityContext context, boolean isSeq8, boolean isSeq6) {
        UUID casterId = context.getCasterId();
        context.entity().removePotionEffect(casterId, PotionEffectType.NAUSEA);
        context.entity().removePotionEffect(casterId, PotionEffectType.BLINDNESS);
        context.entity().removePotionEffect(casterId, PotionEffectType.DARKNESS);

        if (isSeq6) {
            context.entity().removePotionEffect(casterId, PotionEffectType.MINING_FATIGUE);
        }

        if (isSeq8) {
            context.entity().removePotionEffect(casterId, PotionEffectType.HUNGER);
        }
    }

    private void givePassiveXP(IAbilityContext context, boolean isSeq7, boolean isSeq8, boolean isSeq6) {
        // Базовий XP (без Polymath множника, бо він додається через івент)
        int xpAmount = isSeq6 ? 4 : (isSeq7 ? 3 : (isSeq8 ? 2 : 1));

        context.entity().giveExperience(context.getCasterId(), xpAmount);

        float pitch = isSeq6 ? 2.0f : (isSeq8 ? 1.8f : 1.5f);
        if (!isSeq6 || tickCounter % (XP_INTERVAL_TICKS * 2) == 0) {
            context.effects().playSoundForPlayer(context.getCasterId(), Sound.ITEM_BOOK_PAGE_TURN, 0.5f, pitch);
        }
    }

    private boolean analyzeTarget(IAbilityContext context, boolean isDeepAnalysis, boolean isDetectiveAnalysis, boolean isPolymathAnalysis) {
        double range = isPolymathAnalysis ? 35.0 : (isDeepAnalysis ? 25.0 : 15.0);
        Optional<LivingEntity> targetOpt = context.targeting().getTargetedEntity(range);

        if (targetOpt.isEmpty()) return false;

        LivingEntity target = targetOpt.get();
        if (target instanceof ArmorStand stand && stand.isMarker()) return false;

        Component info = buildTargetInfo(context, target, isDeepAnalysis, isDetectiveAnalysis, isPolymathAnalysis);
        context.messaging().sendMessageToActionBar(context.getCasterId(), info);

        if (isPolymathAnalysis && tickCounter % 40 == 0) {
            context.effects().spawnParticle(Particle.ENCHANT, target.getEyeLocation().add(0, 0.5, 0), 5, 0.3, 0.3, 0.3);
        }
        return true;
    }
    private void revealInvisibleTargets(IAbilityContext context) {
        double range = 20.0;
        UUID casterId = context.getCasterId();

        context.targeting().getNearbyEntities(range).forEach(entity -> {
            // Не показувати себе
            if (entity.getUniqueId().equals(casterId)) return;

            if (entity instanceof LivingEntity living) {
                // Якщо є ефект невидимості
                if (living.hasPotionEffect(PotionEffectType.INVISIBILITY)) {

                    // Викликаємо наш НОВИЙ метод
                    context.effects().spawnParticleForPlayer(
                            casterId,                      // Хто бачить (тільки ви)
                            Particle.WHITE_ASH,    // Тип (напівпрозорий дим)
                            living.getLocation().add(0, 1.0, 0), // Центр (груди/голова)
                            5,    // Кількість
                            0.3,  // offsetX (ширина)
                            0.5,  // offsetY (висота)
                            0.3   // offsetZ (глибина) - ви забули його минулого разу
                    );
                }
            }
        });
    }
    private Component buildTargetInfo(IAbilityContext context, LivingEntity target, boolean isDeepAnalysis, boolean isDetectiveAnalysis, boolean isPolymathAnalysis) {
        // ВИПРАВЛЕНО: Беремо HP прямо з моба/гравця
        double health = target.getHealth();
        // Отримуємо макс. HP безпечно (деякі моби можуть не мати атрибуту, тому дефолт 20)
        double maxHealth = target.getAttribute(Attribute.MAX_HEALTH) != null
                ? target.getAttribute(Attribute.MAX_HEALTH).getValue()
                : 20.0;

        String hpStr = isPolymathAnalysis ? String.format("%.1f", health) : DF.format(health);
        String maxHpStr = isPolymathAnalysis ? String.format("%.1f", maxHealth) : DF.format(maxHealth);

        // Визначаємо ім'я (Нік гравця або Назва моба)
        Component nameComp = target instanceof Player ? Component.text(target.getName()) : Component.text(target.getType().name());

        Component info = Component.text()
                .append(nameComp.color(NamedTextColor.GOLD))
                .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                .append(Component.text("❤ " + hpStr + "/" + maxHpStr,
                        health < maxHealth / 3 ? NamedTextColor.RED : NamedTextColor.GREEN)).build();

        // Показ ефектів (Sequence 8+)
        if (isDeepAnalysis && !target.getActivePotionEffects().isEmpty()) {
            List<Component> effectsList = new ArrayList<>();
            for (PotionEffect effect : target.getActivePotionEffects()) {
                String effectName = formatEffectName(effect.getType());
                if (isPolymathAnalysis && effect.getAmplifier() > 0) effectName += " " + (effect.getAmplifier() + 1);
                NamedTextColor color = isPositiveEffect(effect.getType()) ? NamedTextColor.GREEN : NamedTextColor.RED;
                effectsList.add(Component.text(effectName, color));
            }
            if (effectsList.size() > (isPolymathAnalysis ? 5 : 3)) {
                effectsList = effectsList.subList(0, isPolymathAnalysis ? 5 : 3);
                effectsList.add(Component.text("...", NamedTextColor.GRAY));
            }
            info = info.append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                    .append(Component.join(JoinConfiguration.separator(Component.text(", ")), effectsList));
        }

        // Аналіз спорядження (Sequence 7+) - показує зброю в руках
        if (isDetectiveAnalysis) {
            ItemStack hand = target.getEquipment() != null ? target.getEquipment().getItemInMainHand() : null;
            if (hand != null && hand.getType() != Material.AIR) {
                String item = hand.getType().name().toLowerCase().replace("_", " ");
                info = info.append(Component.text(" | 🗡 ", NamedTextColor.YELLOW)).append(Component.text(item, NamedTextColor.WHITE));

                if (hand.getItemMeta() instanceof Damageable dmg && hand.getType().getMaxDurability() > 0) {
                    int percent = (int)((1 - (double)dmg.getDamage() / hand.getType().getMaxDurability()) * 100);
                    info = info.append(Component.text("(" + percent + "%)", percent < 30 ? NamedTextColor.RED : NamedTextColor.GREEN));
                }
            }
        }
        return info;
    }

    private void detectNearestTreasure(IAbilityContext context, boolean isPolymath) {
        int radius = isPolymath ? 10 : 7;
        Location casterLoc = context.playerData().getCurrentLocation(context.getCasterId());
        if (casterLoc == null) return;
        World world = casterLoc.getWorld();
        if (world == null) return;

        Location closestContainerLoc = null;
        double minDistanceSq = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = world.getBlockAt(casterLoc.getBlockX() + x, casterLoc.getBlockY() + y, casterLoc.getBlockZ() + z);

                    // Швидка перевірка матеріалу перед зверненням до стейту
                    if (isValidContainerType(block.getType())) {

                        // Перевірка стейту - найважча частина. Робимо її тільки якщо блок підходить.
                        if (isPolymath) {
                            // Обережно з getState() - це навантажує сервер
                            if (block.getState() instanceof Container container) {
                                if (container.getInventory().isEmpty()) {
                                    continue;
                                }
                            }
                        }

                        double distSq = casterLoc.distanceSquared(block.getLocation());
                        if (distSq < minDistanceSq) {
                            minDistanceSq = distSq;
                            closestContainerLoc = block.getLocation();
                        }
                    }
                }
            }
        }

        if (closestContainerLoc != null) {
            double distance = Math.sqrt(minDistanceSq);
            NamedTextColor distColor = distance < 5 ? NamedTextColor.RED : NamedTextColor.GOLD;
            Component message = Component.text()
                    .append(Component.text(isPolymath ? "Аналіз місцевості виявив цінності: " : "Ви відчуваєте скарби поруч: ", NamedTextColor.AQUA))
                    .append(Component.text(DF.format(distance) + "м", distColor)).build();
            context.messaging().sendMessageToActionBar(context.getCasterId(), message);
        }
    }

    private void visualizeTraces(IAbilityContext context) {
        double range = 15.0;
        UUID casterId = context.getCasterId();
        context.targeting().getNearbyEntities(range).forEach(entity -> {
            if (entity.getUniqueId().equals(casterId)) return;

            Vector velocity = entity.getVelocity();
            boolean isOnGround = entity.isOnGround();

            if (velocity != null && (velocity.length() > 0.08 || !isOnGround)) {
                Location entityLoc = entity.getLocation();
                if (entityLoc != null) {
                    context.effects().spawnParticle(Particle.END_ROD, entityLoc, 0, 0, 0, 0);
                }
            }
        });
    }

    private boolean checkDangerSense(IAbilityContext context) {
        double dangerRange = 25.0;
        UUID casterId = context.getCasterId();
        Location casterLoc = context.playerData().getCurrentLocation(casterId);
        if (casterLoc == null) return false;

        List<String> threatNames = new ArrayList<>();

        context.targeting().getNearbyEntities(dangerRange).forEach(entity -> {
            if (entity.getUniqueId().equals(casterId)) return;
            if (!(entity instanceof LivingEntity)) return;

            // 1. ЛОГІКА ДЛЯ МОБІВ (Якщо заагрений на вас)
            if (entity instanceof Mob mob) {
                if (mob.getTarget() != null && mob.getTarget().getUniqueId().equals(casterId)) {
                    // Використовуємо кастомне ім'я, якщо є, або стандартне (Zombie, Skeleton)
                    threatNames.add(mob.getName());
                }
            }
            // 2. ЛОГІКА ДЛЯ ГРАВЦІВ (Дивиться на вас + Тримає зброю)
            else if (entity instanceof Player enemyPlayer) {
                if (isHoldingWeapon(enemyPlayer)) {
                    Vector toMe = casterLoc.toVector().subtract(enemyPlayer.getEyeLocation().toVector()).normalize();
                    Vector enemyLook = enemyPlayer.getEyeLocation().getDirection().normalize();

                    // Кут огляду ~15 градусів (0.96)
                    if (toMe.dot(enemyLook) > 0.96) {
                        threatNames.add(enemyPlayer.getName());
                    }
                }
            }
        });

        if (!threatNames.isEmpty()) {
            // Об'єднуємо імена через кому
            String names = String.join(", ", threatNames);

            Component warning = Component.text("⚠ ЗАГРОЗА ВІД: ", NamedTextColor.RED)
                    .append(Component.text(names, NamedTextColor.YELLOW));

            context.messaging().sendMessageToActionBar(casterId, warning);

            // Тихий звук "клац" (рідко, щоб не спамило)
            if (tickCounter % 20 == 0) {
                context.effects().playSoundForPlayer(casterId, Sound.UI_BUTTON_CLICK, 0.5f, 2.0f);
            }
            return true;
        }
        return false;
    }

    // Допоміжний метод для перевірки зброї
    private boolean isHoldingWeapon(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) return false;

        String name = item.getType().name();
        return name.contains("_SWORD") ||
                name.contains("_AXE") ||
                name.equals("BOW") ||
                name.equals("CROSSBOW") ||
                name.equals("TRIDENT");
    }

    private boolean isValidContainerType(Material type) {
        String name = type.name();
        return name.contains("CHEST") || name.contains("BARREL") || name.contains("SHULKER_BOX");
    }

    private String formatEffectName(PotionEffectType type) {
        String name = type.getKey().getKey();
        return name.replace('_', ' ').toUpperCase();
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