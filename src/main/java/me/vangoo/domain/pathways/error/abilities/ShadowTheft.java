package me.vangoo.domain.pathways.error.abilities;

import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ShadowTheft extends ActiveAbility {
    private static final int RANGE = 8;
    private static final int SPIRITUALITY_COST = 25;
    private static final int COOLDOWN_SECONDS = 120;
    private static final int INVISIBILITY_DURATION = 60; // 3 seconds in ticks

    @Override
    public String getName() {
        return "Shadow Theft";
    }

    @Override
    public String getDescription() {
        return "Телепортуйтеся за спину ворога в радіусі " + RANGE + " блоків та вкрадіть випадковий предмет. Стаєте невидимими на 3 секунди.";
    }

    @Override
    public int getSpiritualityCost() {
        return SPIRITUALITY_COST;
    }

    @Override
    public int getCooldown() {
        return COOLDOWN_SECONDS;
    }

    @Override
    protected void preExecution(IAbilityContext context) {
        Location startLoc = context.getCasterLocation();

        // Ефект телепортації (початок)
        context.spawnParticle(Particle.PORTAL, startLoc.clone().add(0, 1, 0), 50, 0.5, 0.5, 0.5);
        context.spawnParticle(Particle.SMOKE, startLoc.clone().add(0, 1, 0), 20, 0.3, 0.3, 0.3);

        // Звук зникнення
        context.playSoundToCaster(Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        // 1. Знайти найближчу ціль
        List<LivingEntity> nearbyEntities = context.getNearbyEntities(RANGE);

        if (nearbyEntities.isEmpty()) {
            return AbilityResult.failure("Немає цілей в радіусі " + RANGE + " блоків!");
        }

        LivingEntity target = findNearestTarget(context.getCasterLocation(), nearbyEntities);

        if (target == null) {
            return AbilityResult.failure("Не вдалося знайти підходящу ціль!");
        }

        // 2. Розрахувати позицію за спиною
        Location behindTarget = calculateBehindLocation(target);

        // 3. Телепортуватись
        context.teleport(context.getCasterId(), behindTarget);

        // 4. Ефект появи
        context.spawnParticle(Particle.PORTAL, behindTarget, 50, 0.5, 0.5, 0.5);
        context.spawnParticle(Particle.SMOKE, behindTarget, 20, 0.3, 0.3, 0.3);
        context.playSound(behindTarget, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);

        // 5. Спробувати вкрасти предмет (тільки у HumanEntity)
        boolean stolen = attemptTheft(context, target);

        // 6. Дати невидимість якщо вкрали
        if (stolen) {
            context.applyEffect(
                    context.getCasterId(),
                    PotionEffectType.INVISIBILITY,
                    INVISIBILITY_DURATION,
                    0
            );

            // Ефект тіней навколо caster'а
            createShadowEffect(context);

            context.sendMessageToCaster("§aУспішно вкрали предмет!");
            return AbilityResult.success();
        }

        // Крадіжка не вдалась, але телепортація була
        context.sendMessageToCaster("§eНе вдалося нічого вкрасти");
        return AbilityResult.success(); // Здібність все одно спрацювала
    }

    @Override
    protected void postExecution(IAbilityContext context) {
        // Фінальний звук
        context.playSoundToCaster(Sound.ENTITY_BAT_TAKEOFF, 0.5f, 1.5f);
    }

    // ==========================================
    // PRIVATE METHODS - BUSINESS LOGIC
    // ==========================================

    /**
     * Знайти найближчу валідну ціль
     */
    private LivingEntity findNearestTarget(Location from, List<LivingEntity> entities) {
        LivingEntity nearest = null;
        double minDistance = RANGE;

        for (LivingEntity entity : entities) {
            // Пропускаємо неживих
            if (entity.isDead()) continue;

            double distance = entity.getLocation().distance(from);
            if (distance < minDistance) {
                nearest = entity;
                minDistance = distance;
            }
        }

        return nearest;
    }

    /**
     * Розрахувати позицію за спиною цілі
     */
    private Location calculateBehindLocation(LivingEntity target) {
        Location targetLoc = target.getLocation();
        Vector direction = targetLoc.getDirection().normalize();

        // Позиція за спиною (протилежно до напрямку погляду)
        Vector behind = direction.multiply(-1.5); // 1.5 блоки назад
        Location behindLoc = targetLoc.clone().add(behind);

        // Перевірити чи безпечна позиція
        if (isSafeLocation(behindLoc)) {
            return behindLoc;
        }

        // Якщо небезпечно - просто поруч з ціллю
        return targetLoc.clone().add(1, 0, 0);
    }

    /**
     * Перевірити чи безпечна локація для телепортації
     */
    private boolean isSafeLocation(Location loc) {
        // Перевірити чи не solid блок
        if (loc.getBlock().getType().isSolid()) {
            return false;
        }

        // Перевірити блок над головою
        Location above = loc.clone().add(0, 1, 0);
        if (above.getBlock().getType().isSolid()) {
            return false;
        }

        // Перевірити чи не падає у void
        return !(loc.getY() < 0);
    }

    /**
     * Спробувати вкрасти предмет
     * Працює тільки з HumanEntity (Player, Villager з інвентарем)
     */
    private boolean attemptTheft(IAbilityContext context, LivingEntity target) {
        // Тільки HumanEntity мають інвентар для крадіжки
        if (!(target instanceof HumanEntity human)) {
            return false;
        }

        Inventory inventory = human.getInventory();
        List<ItemStack> validItems = new ArrayList<>();

        // Знайти всі предмети які можна вкрасти
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                // Не красти важливі предмети (опціонально)
                if (!isProtectedItem(item)) {
                    validItems.add(item);
                }
            }
        }

        if (validItems.isEmpty()) {
            return false;
        }

        // Вибрати випадковий предмет
        ItemStack targetItem = validItems.get(
                ThreadLocalRandom.current().nextInt(validItems.size())
        );

        // Створити копію для крадіжки (1 штука)
        ItemStack stolenItem = targetItem.clone();
        stolenItem.setAmount(1);

        // Забрати у цілі
        boolean removed = context.removeItem(human, stolenItem);

        if (removed) {
            // Дати caster'у
            context.giveItem(context.getCaster(), stolenItem);

            // Повідомити ціль (якщо це гравець)
            if (target instanceof org.bukkit.entity.Player player) {
                context.sendMessage(
                        player.getUniqueId(),
                        "§cХтось вкрав у вас " + getItemName(stolenItem) + "!"
                );
                context.playSound(
                        player.getLocation(),
                        Sound.ENTITY_ITEM_PICKUP,
                        1.0f,
                        0.8f
                );
            }

            return true;
        }

        return false;
    }

    /**
     * Перевірити чи предмет захищений від крадіжки
     */
    private boolean isProtectedItem(ItemStack item) {
        // Приклад: не красти зачаровані предмети високого рівня
        if (item.getEnchantments().size() > 3) {
            return true;
        }

        // Не красти рідкісні предмети
        Material type = item.getType();
        if (type == Material.NETHERITE_SWORD ||
                type == Material.NETHERITE_AXE ||
                type == Material.ELYTRA ||
                type == Material.TOTEM_OF_UNDYING) {
            return true;
        }

        return false;
    }

    /**
     * Отримати відображувану назву предмета
     */
    private String getItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }

        // Fallback до типу матеріалу
        return item.getType().name().toLowerCase().replace('_', ' ');
    }

    /**
     * Створити ефект тіней навколо caster'а (під час невидимості)
     */
    private void createShadowEffect(IAbilityContext context) {
        // Повторюючий ефект протягом невидимості
        final int[] ticks = {0};

        context.scheduleRepeating(() -> {
            if (ticks[0] >= INVISIBILITY_DURATION) {
                return; // Зупиниться автоматично
            }

            Location loc = context.getCasterLocation();

            // Темні частинки навколо
            context.spawnParticle(
                    Particle.SMOKE,
                    loc,
                    5,
                    0.5, 1.0, 0.5
            );

            // Іноді додати angry villager (для dramatic effect)
            if (ticks[0] % 10 == 0) {
                context.spawnParticle(
                        Particle.ANGRY_VILLAGER,
                        loc,
                        3,
                        0.3, 0.5, 0.3
                );
            }

            ticks[0]++;
        }, 0, 1); // Кожен тік
    }
}
