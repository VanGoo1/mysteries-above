package me.vangoo.domain.pathways.error.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ShadowTheft extends ActiveAbility {
    private static final double RANGE = 8.0;
    private static final int SPIRITUALITY_COST = 25;
    private static final int COOLDOWN_SECONDS = 120;
    private static final int INVISIBILITY_TICKS = 60; // 3 секунди

    @Override
    public String getName() {
        return "Крадіжка тіні";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Телепортує за спину ворога, викрадає випадковий предмет та дарує невидимість на 3с.";
    }

    @Override
    public int getSpiritualityCost() {
        return SPIRITUALITY_COST;
    }

    @Override
    public int getCooldown(Sequence sequence) {
        return COOLDOWN_SECONDS;
    }

    @Override
    protected void preExecution(IAbilityContext context) {
        Location startLoc = context.getCasterLocation();
        context.effects().spawnParticle(Particle.PORTAL, startLoc.add(0, 1, 0), 50, 0.5, 0.5, 0.5);
        context.effects().playSoundForPlayer(context.getCasterId(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        // 1. Пошук цілі через контекст
        LivingEntity target = context.targeting().getNearbyEntities(RANGE).stream()
                .min(Comparator.comparingDouble(e -> e.getLocation().distance(context.getCasterLocation())))
                .orElse(null);

        if (target == null) {
            return AbilityResult.failure("Ціль не знайдена!");
        }

        // 2. Розрахунок позиції (бізнес-логіка)
        Location behindTarget = calculateSafeBehindLocation(target);

        // 3. Переміщення через контекст
        context.entity().teleport(context.getCasterId(), behindTarget);

        // Візуальні ефекти на місці прибуття
        context.effects().spawnParticle(Particle.SMOKE, behindTarget, 30, 0.3, 0.5, 0.3);
        context.effects().playSound(behindTarget, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);

        // 4. Спроба крадіжки
        boolean success = attemptTheft(context, target);

        if (success) {
            applyShadowStealth(context);
            return AbilityResult.success();
        }

        context.messaging().sendMessageToActionBar(context.getCasterId(), Component.text("кишені цілі порожні.").color(NamedTextColor.YELLOW));
        return AbilityResult.success();
    }

    @Override
    protected void postExecution(IAbilityContext context) {
        context.effects().playSoundForPlayer(context.getCasterId(), Sound.ENTITY_BAT_TAKEOFF, 0.5f, 1.5f);
    }

    // ==========================================
    // ЛОГІКА РОЗРАХУНКУ (Pure Logic)
    // ==========================================

    private Location calculateSafeBehindLocation(LivingEntity target) {
        Location loc = target.getLocation();
        Vector direction = loc.getDirection().normalize();
        Location behind = loc.clone().subtract(direction.multiply(1.2)); // 1.2 блоки назад

        // Перевірка безпеки: якщо блок за спиною твердий, повертаємо локацію цілі (наступимо на неї)
        if (behind.getBlock().getType().isSolid()) {
            return loc;
        }
        return behind;
    }

    private boolean attemptTheft(IAbilityContext context, LivingEntity target) {
        if (!(target instanceof HumanEntity human)) return false;

        // Отримуємо предмети через стандартний Bukkit API (context дає доступ до Player/HumanEntity)
        ItemStack[] contents = human.getInventory().getContents();
        List<ItemStack> lootPool = new ArrayList<>();

        for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR && !isProtected(item)) {
                lootPool.add(item);
            }
        }

        if (lootPool.isEmpty()) return false;

        // Вибір випадкового предмета
        ItemStack targetItem = lootPool.get(ThreadLocalRandom.current().nextInt(lootPool.size()));
        ItemStack toSteal = targetItem.clone();
        toSteal.setAmount(1);

        // Використання методів контексту для маніпуляції інвентарем
        context.entity().consumeItem(human.getUniqueId(), toSteal);
        context.entity().giveItem(context.getCasterId(), toSteal);

        if (human instanceof Player victim) {
            context.messaging().sendMessageToActionBar(victim.getUniqueId(), Component.text("Ви відчули, як хтось порпався у ваших речах...").color(NamedTextColor.RED));
            context.effects().playSound(victim.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 0.5f);
        }
        return true;
    }

    private boolean isProtected(ItemStack item) {
        Material type = item.getType();
        return type.name().contains("NETHERITE") || type == Material.ELYTRA || type == Material.TOTEM_OF_UNDYING;
    }

    private void applyShadowStealth(IAbilityContext context) {
        // Ефекти через контекст
        context.entity().applyPotionEffect(context.getCasterId(), PotionEffectType.INVISIBILITY, INVISIBILITY_TICKS, 0);

        // Повторюваний візуальний ефект (Shadow Trail)
        final int[] elapsed = {0};
        context.scheduling().scheduleRepeating(() -> {
            if (elapsed[0] >= INVISIBILITY_TICKS) return;

            context.effects().spawnParticle(Particle.SMOKE, context.getCasterLocation().add(0, 0.5, 0), 3, 0.2, 0.2, 0.2);
            elapsed[0] += 2;
        }, 0, 2);
    }
}