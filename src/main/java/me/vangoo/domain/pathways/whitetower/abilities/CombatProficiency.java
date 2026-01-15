package me.vangoo.domain.pathways.whitetower.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

public class CombatProficiency extends PermanentPassiveAbility {

    // Константи балансу (шкода тепер динамічна)
    private static final double PARRY_REDUCTION_PERCENT = 0.30; // -30% вхідної шкоди при блокуванні
    private static final double CRIT_CHANCE = 0.25; // 25% шанс на крит

    @Override
    public String getName() {
        return "Бойова Майстерність";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        // Динамічний опис, що показує поточний бонус
        double currentBonus = calculateWeaponDamageBonus(userSequence);
        return "Надає майстерність над зброєю.\n" +
                "Поточний бонус до шкоди: §c+" + currentBonus + "\n" +
                "Дозволяє ефективно парирувати удари (-30% шкоди).";
    }

    @Override
    public void tick(IAbilityContext context) {
        Player player = context.getCasterPlayer();
        if (player == null || !player.isOnline()) return;

        // Оновлюємо підписку на події раз на секунду
        if (player.getTicksLived() % 20 == 0) {
            registerCombatEvents(context, player);
        }
    }

    private void registerCombatEvents(IAbilityContext context, Player player) {
        // 1. АТАКА
        context.subscribeToEvent(
                EntityDamageByEntityEvent.class,
                event -> {
                    if (event.getDamager().equals(player)) return true;
                    if (event.getDamager() instanceof Arrow arrow && arrow.getShooter().equals(player)) return true;
                    return false;
                },
                event -> handleAttack(context, player, event),
                25
        );

        // 2. ЗАХИСТ
        context.subscribeToEvent(
                EntityDamageEvent.class,
                event -> event.getEntity().equals(player),
                event -> handleDefense(context, player, event),
                25
        );
    }

    private void handleAttack(IAbilityContext context, Player player, EntityDamageByEntityEvent event) {
        ItemStack item = player.getInventory().getItemInMainHand();
        boolean isMelee = isMeleeWeapon(item.getType());
        boolean isRanged = event.getDamager() instanceof Arrow;

        if (!isMelee && !isRanged) return;

        double originalDamage = event.getDamage();

        // --- ЗМІНИ ТУТ ---
        // Отримуємо Sequence з контексту
        // (Якщо context.getSequence() немає, використай свій сервіс: SequenceService.get(player))
        Sequence sequence = context.getCasterBeyonder().getSequence();

        // Розраховуємо бонус на основі рівня
        double bonus = calculateWeaponDamageBonus(sequence);

        // Розрахунок Крита
        if (ThreadLocalRandom.current().nextDouble() < CRIT_CHANCE) {
            // Крит додає 30% від базової шкоди + фіксований бонус майстерності
            double critBonus = originalDamage * 0.3;
            bonus += critBonus;

            context.spawnParticle(Particle.CRIT, event.getEntity().getLocation().add(0, 1, 0), 10, 0.2, 0.2, 0.2);
            context.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
        }

        // Застосовуємо нову шкоду
        event.setDamage(originalDamage + bonus);
    }

    private void handleDefense(IAbilityContext context, Player player, EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK &&
                event.getCause() != EntityDamageEvent.DamageCause.PROJECTILE) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();

        if (isMeleeWeapon(item.getType())) {
            double originalDamage = event.getDamage();
            double reducedDamage = originalDamage * (1.0 - PARRY_REDUCTION_PERCENT);

            event.setDamage(reducedDamage);

            if (originalDamage > 2.0) {
                context.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.5f, 1.5f);
                context.spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().add(0, 1, 0), 1);
            }
        }
    }

    // --- Новий метод розрахунку шкоди ---
    private double calculateWeaponDamageBonus(Sequence sequence) {
        if (sequence == null) return 0.0;

        int level = sequence.level(); // Припускаємо, що метод повертає int (9, 8, 7...)

        if (level > 8) return 0.0;  // Seq 9: 0
        if (level == 8) return 0.5; // Seq 8: +0.5
        if (level == 7) return 1.0;
        if (level == 6) return 1.5;
        if (level == 5) return 2.0;
        return 3.0;                 // Seq 4+: +3.0
    }

    private boolean isMeleeWeapon(Material type) {
        String name = type.name();
        return name.endsWith("_SWORD") ||
                name.endsWith("_AXE") ||
                name.equals("TRIDENT") ||
                name.equals("MACE") ||
                name.equals("BOW") ||
                name.equals("CROSSBOW");
    }
}