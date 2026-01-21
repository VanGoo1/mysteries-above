package me.vangoo.domain.pathways.whitetower.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.entities.Beyonder;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class CombatProficiency extends PermanentPassiveAbility {

    private static final double PARRY_REDUCTION_PERCENT = 0.20;
    private static final double CRIT_CHANCE = 0.25;
    // Використовуємо Integer.MAX_VALUE, але контролюємо виконання всередині події
    private static final int PERMANENT_DURATION = Integer.MAX_VALUE;

    private final Set<UUID> registeredPlayers = ConcurrentHashMap.newKeySet();

    @Override
    public String getName() {
        return "Бойова Майстерність";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        double bonus = calculateWeaponDamageBonus(userSequence);
        return "Надає майстерність над зброєю.\n" +
                "§7▪ Бонус шкоди: §c+" + bonus + "\n" +
                "§7▪ Парирування: §eЗброя у руках §7(-20% вх. шкоди)";
    }

    @Override
    public void tick(IAbilityContext context) {
        UUID casterId = context.getCasterId();
        if (casterId == null) return;

        // Реєструємо слухачі тільки один раз для сесії гравця
        if (registeredPlayers.add(casterId)) {
            registerCombatEvents(context, casterId);
        }
    }

    /* ===================== EVENTS REGISTRATION ===================== */

    private void registerCombatEvents(IAbilityContext context, UUID playerId) {
        // АТАКА
        context.events().subscribeToTemporaryEvent(
                playerId,
                EntityDamageByEntityEvent.class,
                event -> isAttackByPlayer(event, playerId),
                event -> handleAttack(context, playerId, event),
                PERMANENT_DURATION
        );

        // ЗАХИСТ (ПАРИРУВАННЯ)
        context.events().subscribeToTemporaryEvent(
                playerId,
                EntityDamageEvent.class,
                event -> event.getEntity() instanceof Player p && p.getUniqueId().equals(playerId),
                event -> handleDefense(context, playerId, event),
                PERMANENT_DURATION
        );
    }

    private boolean isAttackByPlayer(EntityDamageByEntityEvent event, UUID playerId) {
        if (event.getDamager() instanceof Player p) {
            return p.getUniqueId().equals(playerId);
        }
        if (event.getDamager() instanceof Arrow arrow) {
            return arrow.getShooter() instanceof Player p && p.getUniqueId().equals(playerId);
        }
        return false;
    }

    /* ===================== VALIDATION GATEKEEPER ===================== */

    /**
     * Перевіряє, чи має гравець все ще право використовувати цю здібність.
     * Це вирішує проблему, коли ефекти продовжуються після втрати контролю/смерті.
     */
    private boolean isValidUser(IAbilityContext context, UUID playerId) {
        // Перевірка онлайн
        if (!context.playerData().isOnline(playerId)) return false;

        Beyonder beyonder = context.beyonder().getBeyonder(playerId);
        if (beyonder == null) return false;

        // Перевірка: чи прямою посиланням ця інстанція є в списку способностей beyonder
        boolean hasThisAbilityByReference = beyonder.getAbilities().stream()
                .anyMatch(ability -> ability == this);

        // Якщо нема прямої інстанції, відхиляємо — це точна перевірка, щоб уникнути дублювання
        if (!hasThisAbilityByReference) return false;

        // І остання: перевірка, чи дає послідовність ненульовий бонус
        return calculateWeaponDamageBonus(beyonder.getSequence()) > 0;
    }
    /* ===================== ATTACK LOGIC ===================== */

    private void handleAttack(IAbilityContext context, UUID playerId, EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;

        // ВАЖЛИВО: Перевірка валідності перед виконанням
        if (!isValidUser(context, playerId)) return;

        ItemStack item = context.playerData().getMainHandItem(playerId);
        Material type = (item != null) ? item.getType() : Material.AIR;

        boolean melee = isMeleeWeapon(type);
        boolean ranged = event.getDamager() instanceof Arrow;

        if (!melee && !ranged) return;

        // Отримуємо актуальні дані про Beyonder знову
        Beyonder caster = context.beyonder().getBeyonder(playerId);
        double bonus = calculateWeaponDamageBonus(caster.getSequence());

        // Логіка КРИТИЧНОГО УДАРУ
        boolean isCrit = ThreadLocalRandom.current().nextDouble() < CRIT_CHANCE;
        if (isCrit) {
            // Крит додає 30% до поточної шкоди події (включаючи чари і т.д.)
            double critBonus = event.getDamage() * 0.3;
            bonus += critBonus;

            context.effects().spawnParticle(
                    Particle.CRIT,
                    event.getEntity().getLocation().add(0, 1.5, 0),
                    15, 0.3, 0.3, 0.3
            );
            context.effects().playSoundForPlayer(
                    playerId,
                    Sound.ENTITY_PLAYER_ATTACK_CRIT,
                    1.0f, 1.2f
            );
        }

        // ВАЖЛИВО: Додаємо до існуючої шкоди, а не замінюємо її
        event.setDamage(event.getDamage() + bonus);
    }

    /* ===================== DEFENSE LOGIC ===================== */

    private void handleDefense(IAbilityContext context, UUID playerId, EntityDamageEvent event) {
        if (event.isCancelled()) return;

        // ВАЖЛИВО: Перевірка валідності
        if (!isValidUser(context, playerId)) return;

        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK &&
                event.getCause() != EntityDamageEvent.DamageCause.PROJECTILE) {
            return;
        }

        ItemStack item = context.playerData().getMainHandItem(playerId);
        Material type = (item != null) ? item.getType() : Material.AIR;

        // Перевірка: Тільки меч у руках (Shift прибрано)
        if (!isMeleeWeapon(type)) return;

        // Перевірка: Гравець повинен дивитися в бік атаки (не можна блокувати спиною)
        if (event instanceof EntityDamageByEntityEvent subEvent) {
            // Вектор від гравця до атакуючого
            Vector directionToAttacker = subEvent.getDamager().getLocation().toVector()
                    .subtract(context.playerData().getCurrentLocation(playerId).toVector()).normalize();
            // Вектор погляду гравця
            Vector playerDirection = context.playerData().getEyeLocation(playerId).getDirection();

            // Скалярний добуток: якщо > 0, кут < 90 градусів (дивляться один на одного приблизно)
            // Використовуємо 0.0 (90 градусів) або 0.5 (60 градусів) для точності.
            // 0.0 достатньо комфортно для геймплею.
            if (playerDirection.dot(directionToAttacker) < 0) return;
        }

        double reduced = event.getDamage() * (1.0 - PARRY_REDUCTION_PERCENT);
        event.setDamage(reduced);

        // Ефекти успішного парирування
        context.effects().playSound(
                context.playerData().getCurrentLocation(playerId),
                Sound.ITEM_SHIELD_BLOCK,
                0.8f, 1.4f
        );
        context.effects().spawnParticle(
                Particle.SWEEP_ATTACK,
                context.playerData().getCurrentLocation(playerId).add(0, 1.0, 0),
                1
        );
    }

    /* ===================== UTILS ===================== */

    private double calculateWeaponDamageBonus(Sequence sequence) {
        if (sequence == null) return 0.0;
        int level = sequence.level();

        // Рівні згідно опису (Sequence 9-5)
        if (level >= 9) return 0.0; // Reader (без бонусів)
        if (level == 8) return 0.5; // Student (невеликий бонус)
        if (level == 7) return 1.0; // Detective (+1 серце)
        if (level == 6) return 1.5; // Polymath
        if (level == 5) return 2.0; // Mysticism Magister
        return 3.0;                 // Higher levels
    }

    private boolean isMeleeWeapon(Material type) {
        if (type == null) return false;
        String name = type.name();
        return name.endsWith("_SWORD")
                || name.endsWith("_AXE")
                || type == Material.TRIDENT
                || type == Material.MACE;
    }

    @Override
    public void cleanUp() {
        // Очищаємо список зареєстрованих.
        // Самі івенти "відімруть" завдяки перевірці isValidUser, коли Beyonder об'єкт зникне або зміниться.
        registeredPlayers.clear();
    }
}