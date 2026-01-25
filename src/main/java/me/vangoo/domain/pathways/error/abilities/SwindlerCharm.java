package me.vangoo.domain.pathways.error.abilities;

import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class SwindlerCharm extends ActiveAbility {

    private static final double RADIUS = 10.0;
    private static final int SPIRITUALITY_COST = 40;
    private static final int COOLDOWN_SECONDS = 20;

    @Override
    public String getName() {
        return "Шарм Афериста";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Використовує шарм та красномовство, щоб заплутати ворогів. Моби втрачають інтерес, а гравці губляться у своїх діях (плутають предмети в руках).";
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
        // Візуальний ефект "Шарму" (сердечка)
        context.effects().spawnParticle(Particle.HEART, context.getCasterLocation().add(0, 2, 0), 10, 0.5, 0.5, 0.5);
        // Звук "Бурмотіння" (імітація красномовства)
        context.effects().playSoundForPlayer(context.getCasterId(), Sound.ENTITY_VILLAGER_TRADE, 1.0f, 1.0f);
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        UUID casterId = context.getCasterId();

        // Отримуємо всіх живих істот в радіусі
        List<LivingEntity> targets = context.targeting().getNearbyEntities(RADIUS).stream()
                .filter(e -> e != null && !e.getUniqueId().equals(casterId))
                .toList();

        if (targets.isEmpty()) {
            return AbilityResult.failure("Нікого немає поруч, щоб зачарувати.");
        }

        // Список мобів, яких ми будемо контролювати 3 секунди
        List<Mob> charmedMobs = new ArrayList<>();

        for (LivingEntity target : targets) {
            // Ефект 1: Charm & Eloquence (Загальний дебаф)
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0)); // 5 секунд
            target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0)); // 7 секунд

            // Ефект 2: Збираємо мобів для тривалого контролю
            if (target instanceof Mob mob) {
                charmedMobs.add(mob);
                mob.setTarget(null); // Скидаємо ціль миттєво
            }

            // Ефект 3: Thought Misdirection (Для гравців - плутанина в руках)
            if (target instanceof Player enemyPlayer) {
                performItemMisdirection(context, enemyPlayer);
            }

            // Візуалізація
            context.effects().spawnParticle(Particle.HEART, target.getEyeLocation().add(0, 0.5, 0), 3, 0.3, 0.3, 0.3);
        }

        // --- НОВА ЛОГІКА: Утримання контролю над мобами (3 секунди) ---
        if (!charmedMobs.isEmpty()) {
            final int[] ticks = {0};
            // Запускаємо завдання, що повторюється кожні 5 тіків (0.25 с)
            context.scheduling().scheduleRepeating(() -> {
                // Якщо пройшло більше 60 тіків (3 секунди) - зупиняємо
                if (ticks[0] >= 60) return;

                for (Mob mob : charmedMobs) {
                    if (mob.isValid()) {
                        // Якщо моб знову націлився на Афериста - скидаємо це
                        if (mob.getTarget() != null && mob.getTarget().getUniqueId().equals(casterId)) {
                            mob.setTarget(null);
                        }
                    }
                }
                ticks[0] += 5;
            }, 0, 5);
        }

        // Баф для самого Афериста
        context.entity().applyPotionEffect(casterId,PotionEffectType.SPEED, 60, 1);

        return AbilityResult.success();
    }

    @Override
    protected void postExecution(IAbilityContext context) {
        // Звук успішного обману
        context.effects().playSound(context.getCasterLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1.0f, 1.5f);
    }

    /**
     * Реалізація "Thought Misdirection" (Перенаправлення думок).
     * Ми "перенаправляємо" намір гравця використати зброю, підміняючи її на інший предмет.
     */
    private void performItemMisdirection(IAbilityContext context, Player enemy) {
        // Шанс спрацювання (щоб не було занадто імбово, наприклад 80%)
        if (ThreadLocalRandom.current().nextDouble() > 0.8) return;

        int heldSlot = enemy.getInventory().getHeldItemSlot();
        int randomSlot = ThreadLocalRandom.current().nextInt(0, 9); // Слот хотбару 0-8

        if (heldSlot == randomSlot) {
            randomSlot = (randomSlot + 1) % 9; // Гарантуємо, що слот інший
        }

        // Зміна активного слота (Гравець різко бере в руки інший предмет)
        enemy.getInventory().setHeldItemSlot(randomSlot);

        // Повідомлення жертві
        context.messaging().sendMessageToActionBar(enemy.getUniqueId(),
                Component.text("Ваші думки сплутались...", NamedTextColor.DARK_PURPLE));

        // Звук для жертви
        context.effects().playSoundForPlayer(enemy.getUniqueId(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 2.0f);
    }
}