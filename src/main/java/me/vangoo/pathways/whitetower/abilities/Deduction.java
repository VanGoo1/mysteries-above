package me.vangoo.pathways.whitetower.abilities;

import me.vangoo.domain.PathwayBranding;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Optional;
import java.util.UUID;

/**
 * Посл. 8 (Student of Ratiocination): Дедукція.
 * Спостереження й логіка замість сили — кастер не б'є сильніше, він бачить, де ціль
 * тримається погано. Мітка живе {@link #MARK_SECONDS} с і множить шкоду САМЕ від кастера.
 * <p>
 * Стану не тримає навмисно: мітка = тимчасова підписка з TTL, тож гасне сама й переживати
 * рестарт їй нічим (пор. модифікатор атрибута, який на 1.21.1 запікся б у файл гравця).
 */
public class Deduction extends ActiveAbility {

    private static final int COST = 100;
    private static final int COOLDOWN = 30;
    private static final double RANGE = 20.0;
    private static final int MARK_SECONDS = 8;
    /** База для Посл. 9; MODERATE дає +15% за рівень (Посл. 8 → +33%). */
    private static final int BASE_BONUS_PERCENT = 28;

    @Override
    public String getName() {
        return "Дедукція";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        return "Прораховуєте рухи цілі й знаходите слабке місце: " + MARK_SECONDS
                + " с ваші удари по ній завдають на " + bonusPercent(userSequence) + "% більше шкоди."
                + "\n§7§oПотрібна жива ціль у межах " + (int) RANGE + " бл.";
    }

    @Override
    public int getSpiritualityCost() {
        return COST;
    }

    @Override
    public int getCooldown(Sequence userSequence) {
        return COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        Optional<LivingEntity> targeted = context.targeting().getTargetedEntity(RANGE);
        if (targeted.isEmpty()) {
            return AbilityResult.failure("Немає кого прораховувати: наведіться на ціль");
        }

        LivingEntity target = targeted.get();
        UUID targetId = target.getUniqueId();
        UUID casterId = context.getCasterId();
        Sequence sequence = context.getCasterBeyonder().getSequence();

        int bonus = bonusPercent(sequence);
        double multiplier = 1.0 + bonus / 100.0;
        int durationTicks = MARK_SECONDS * 20;
        Color towerColor = PathwayBranding.liquidOf("WhiteTower");

        // Ключ власний, не casterId: чужий unsubscribeAll(casterId) мовчки зняв би мітку.
        context.events().subscribeToTemporaryEvent(
                UUID.randomUUID(),
                EntityDamageByEntityEvent.class,
                event -> targetId.equals(event.getEntity().getUniqueId()) && isDealtBy(event, casterId),
                event -> {
                    if (event.isCancelled() || !(event.getEntity() instanceof LivingEntity victim)) return;
                    // Бонус знімаємо зі здоров'я напряму (як у WindImbuedHands.pierce): правка
                    // шкоди в самій події перераховує модифікатори броні/опору від нової бази,
                    // і приріст губиться замість того, щоб дійти до цілі.
                    victim.setHealth(Math.max(0.0, victim.getHealth() - event.getFinalDamage() * (multiplier - 1.0)));
                    context.effects().playExplosionRingEffect(
                            event.getEntity().getLocation().add(0, 1, 0), 0.8,
                            Particle.DUST, new Particle.DustOptions(towerColor, 1.0f));
                    context.effects().playSound(event.getEntity().getLocation(),
                            Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.6f);
                },
                durationTicks
        );

        context.glowing().setGlowing(targetId, casterId,
                PathwayBranding.textOf("WhiteTower"), durationTicks);
        playDeduction(context, target, towerColor);

        context.messaging().sendMessage(casterId, ChatColor.AQUA + "✎ Слабке місце знайдено: "
                + ChatColor.WHITE + "+" + bonus + "% шкоди " + ChatColor.GRAY + "(" + MARK_SECONDS + " с)");
        return AbilityResult.success();
    }

    /** Промінь від очей до цілі, кільце під нею й німб — «висновок» лягає на ціль. */
    private void playDeduction(IAbilityContext context, LivingEntity target, Color towerColor) {
        context.effects().playTravelingBeam(
                context.getCasterEyeLocation(), target.getEyeLocation(), towerColor,
                () -> {
                    context.effects().playExplosionRingEffect(target.getLocation(), 1.2,
                            Particle.DUST, new Particle.DustOptions(towerColor, 1.2f));
                    context.effects().playAlertHalo(target.getEyeLocation().clone().add(0, 0.8, 0), towerColor);
                    context.effects().playSound(target.getLocation(),
                            Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.4f);
                });
    }

    /** Удар кастера — рукою або його ж снарядом. */
    private static boolean isDealtBy(EntityDamageByEntityEvent event, UUID casterId) {
        if (event.getDamager() instanceof Player player) {
            return player.getUniqueId().equals(casterId);
        }
        return event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter
                && shooter.getUniqueId().equals(casterId);
    }

    private int bonusPercent(Sequence userSequence) {
        return scaleValue(BASE_BONUS_PERCENT, userSequence, SequenceScaler.ScalingStrategy.MODERATE);
    }
}
