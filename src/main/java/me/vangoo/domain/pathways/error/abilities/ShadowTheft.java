package me.vangoo.domain.pathways.error.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ShadowTheft extends ActiveAbility {
    private static final double RANGE = 8.0;
    private static final int SPIRITUALITY_COST = 55;
    private static final int COOLDOWN_SECONDS = 30;
    private static final int INVISIBILITY_TICKS = 60; // 3 секунди

    @Override
    public String getName() {
        return "Крадіжка тіні";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Телепортує за спину ворога (на якого ви дивитесь), викрадає випадковий предмет та дарує невидимість на 3с.";
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
    protected Optional<LivingEntity> getSequenceCheckTarget(IAbilityContext context) {
        return context.targeting().getTargetedEntity(RANGE);
    }
    @Override
    protected void preExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        Location startLoc = context.getCasterLocation();
        context.effects().spawnParticle(Particle.PORTAL, startLoc.add(0, 1, 0), 50, 0.5, 0.5, 0.5);
        context.effects().playSoundForPlayer(casterId, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();

        // Використовуємо getTargetedEntity для отримання цілі, на яку дивиться гравець
        Optional<LivingEntity> targetOpt = context.targeting().getTargetedEntity(RANGE);

        if (targetOpt.isEmpty()) {
            return AbilityResult.failure("Ви повинні дивитися на ціль!");
        }

        LivingEntity target = targetOpt.get();

        // Розрахунок позиції
        Location behindTarget = calculateSafeBehindLocation(target);

        // Телепортація
        context.entity().teleport(context.getCasterId(), behindTarget);

        // Ефекти появи
        context.effects().spawnParticle(Particle.SMOKE, behindTarget, 30, 0.3, 0.5, 0.3);
        context.effects().playSound(behindTarget, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);

        // Спроба крадіжки
        boolean success = attemptTheft(context, target);

        if (success) {
            applyShadowStealth(context);
            context.messaging().sendMessageToActionBar(casterId, Component.text(ChatColor.GREEN + "Ви успішно викрали предмет та зникли в тінях!"));
            return AbilityResult.success();
        }

        context.messaging().sendMessageToActionBar(casterId, Component.text(ChatColor.YELLOW + "Телепортація вдалася, але кишені цілі порожні."));
        return AbilityResult.success();
    }

    @Override
    protected void postExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        context.effects().playSoundForPlayer(casterId, Sound.ENTITY_BAT_TAKEOFF, 0.5f, 1.5f);
    }

    // ==========================================
    // ЛОГІКА
    // ==========================================

    private Location calculateSafeBehindLocation(LivingEntity target) {
        Location loc = target.getLocation();
        Vector direction = loc.getDirection().normalize();
        Location behind = loc.clone().subtract(direction.multiply(1.2));

        if (behind.getBlock().getType().isSolid()) {
            return loc;
        }
        return behind;
    }

    private boolean attemptTheft(IAbilityContext context, LivingEntity target) {
        UUID casterId = context.getCasterId();

        if (!(target instanceof HumanEntity human)) return false;

        ItemStack[] contents = human.getInventory().getContents();
        List<ItemStack> lootPool = new ArrayList<>();

        for (ItemStack item : contents) {
            if (item != null && item.getType() != Material.AIR && !isProtected(item)) {
                lootPool.add(item);
            }
        }

        if (lootPool.isEmpty()) return false;

        // Вибір предмета
        ItemStack targetItem = lootPool.get(ThreadLocalRandom.current().nextInt(lootPool.size()));
        ItemStack toSteal = targetItem.clone();
        toSteal.setAmount(1);

        // Видаляємо у жертви
        context.entity().removeItem(human.getUniqueId(), toSteal);

        // Даємо злодію
        context.entity().giveItem(casterId, toSteal);

        // Ефекти для жертви
        if (human instanceof Player victim) {
            context.effects().playSound(victim.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 0.5f);
            context.messaging().sendMessageToActionBar(victim.getUniqueId(), Component.text(ChatColor.RED + "Ви відчули, як хтось порпався у ваших речах..."));
        }

        return true;
    }

    private boolean isProtected(ItemStack item) {
        Material type = item.getType();

        // Базовий захист цінних предметів
        if (type.name().contains("NETHERITE") ||
                type == Material.ELYTRA ||
                type == Material.TOTEM_OF_UNDYING) {
            return true;
        }

        // Захист містичних здібностей (Echo Shard)
        if (type == Material.ECHO_SHARD) {
            return true;
        }

        // Захист здібностей на папері
        if (type == Material.PAPER) {
            // Перевіряємо lore предмета
            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta.hasLore()) {
                    List<String> lore = meta.getLore();
                    // Якщо в lore є підказка про здібність - захищаємо
                    for (String line : lore) {
                        String stripped = ChatColor.stripColor(line).toLowerCase();
                        if (stripped.contains("кулдаун") ||
                                stripped.contains("cooldown") ||
                                stripped.contains("вартість") ||
                                stripped.contains("cost")) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private void applyShadowStealth(IAbilityContext context) {
        UUID casterId = context.getCasterId();

        // 1. Вмикаємо невидимість
        context.entity().setHidden(casterId, true);

        // 2. Ефект диму під час дії
        final int[] elapsed = {0};
        context.scheduling().scheduleRepeating(() -> {
            if (elapsed[0] >= INVISIBILITY_TICKS) return;
            context.effects().spawnParticle(Particle.SMOKE, context.getCasterLocation().add(0, 0.5, 0), 3, 0.2, 0.2, 0.2);
            elapsed[0] += 2;
        }, 0, 2);

        // 3. Вимикаємо через 3 секунди
        context.scheduling().scheduleDelayed(() -> {
            if (context.playerData().isOnline(casterId)) {
                context.entity().setHidden(casterId, false);
                context.messaging().sendMessageToActionBar(casterId, Component.text(ChatColor.GRAY + "Дія тіні завершилася."));
                context.effects().playSoundForPlayer(casterId, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 0.5f);
            }
        }, INVISIBILITY_TICKS);
    }
}