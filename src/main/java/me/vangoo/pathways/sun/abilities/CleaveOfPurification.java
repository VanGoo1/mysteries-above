package me.vangoo.pathways.sun.abilities;

import me.vangoo.domain.abilities.context.IBeyonderContext;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.ActiveAbility;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.HolyAffinity;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sequence 7: наступні N ударів кастера у ближньому бою завдають бонусної святої шкоди.
 * <p>
 * Підписка на удари реєструється один раз, лениво (як {@code DamageTransfer#ensureTracking})
 * і живе для ВСІХ гравців з цією здібністю — інстанс healера спільний для pathway. Тому хендлер
 * НЕ захоплює {@link IAbilityContext} кастера (він належав би лише тому, хто востаннє кастнув):
 * єдине, що зберігається, — {@link IBeyonderContext}, глобальний lookup-сервіс без ідентичності
 * жодного кастера (той самий виняток, що описаний для {@code DreamVisionSession} у CLAUDE.md).
 */
public class CleaveOfPurification extends ActiveAbility {

    private static final int BASE_CHARGES = 3;
    private static final int BASE_BONUS_DAMAGE = 4;
    private static final int COOLDOWN = 25;

    private final Map<UUID, AtomicInteger> charges = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> bonusDamage = new ConcurrentHashMap<>();
    private volatile IBeyonderContext beyonderContext;
    private volatile UUID trackingKey;

    @Override
    public String getName() {
        return "Очищувальний удар";
    }

    @Override
    public String getDescription(Sequence sequence) {
        int hits = scaleValue(BASE_CHARGES, sequence, SequenceScaler.ScalingStrategy.WEAK);
        int bonus = scaleValue(BASE_BONUS_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        return String.format(
                "§fОсвячує зброю: наступні §e%d §fударів у ближньому бою завдають §c+%d §fсвятої шкоди " +
                        "§7(проти темних/нежиті — повна, проти інших — знижена).",
                hits, bonus
        );
    }

    @Override
    public int getSpiritualityCost() {
        return 30;
    }

    @Override
    public int getCooldown(Sequence sequence) {
        return COOLDOWN;
    }

    @Override
    protected AbilityResult performExecution(IAbilityContext context) {
        ensureTracking(context);
        Sequence sequence = context.getCasterBeyonder().getSequence();
        UUID casterId = context.getCasterId();

        int hits = scaleValue(BASE_CHARGES, sequence, SequenceScaler.ScalingStrategy.WEAK);
        int bonus = scaleValue(BASE_BONUS_DAMAGE, sequence, SequenceScaler.ScalingStrategy.MODERATE);
        charges.put(casterId, new AtomicInteger(hits));
        bonusDamage.put(casterId, bonus);

        context.effects().playSoundForPlayer(casterId, Sound.ITEM_TRIDENT_RETURN, 1.0f, 1.6f);
        context.messaging().sendMessage(casterId,
                ChatColor.GOLD + "✦ Ваша зброя освячена на " + hits + " ударів");

        return AbilityResult.success();
    }

    private void ensureTracking(IAbilityContext context) {
        if (trackingKey != null) return;
        synchronized (this) {
            if (trackingKey != null) return;
            beyonderContext = context.beyonder();
            UUID key = UUID.randomUUID();
            context.events().subscribeToTemporaryEvent(key,
                    EntityDamageByEntityEvent.class,
                    this::isChargedMeleeHit,
                    this::applyCharge,
                    Integer.MAX_VALUE
            );
            trackingKey = key;
        }
    }

    private boolean isChargedMeleeHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return false;
        if (!(event.getEntity() instanceof LivingEntity)) return false;
        AtomicInteger remaining = charges.get(attacker.getUniqueId());
        return remaining != null && remaining.get() > 0;
    }

    private void applyCharge(EntityDamageByEntityEvent event) {
        Player attacker = (Player) event.getDamager();
        AtomicInteger remaining = charges.get(attacker.getUniqueId());
        if (remaining == null || remaining.getAndDecrement() <= 0) return;

        LivingEntity target = (LivingEntity) event.getEntity();
        boolean darkTarget = HolyTargetClassifier.isDarkOrUndead(target, beyonderContext);
        double multiplier = HolyAffinity.damageMultiplier(darkTarget);
        int bonus = bonusDamage.getOrDefault(attacker.getUniqueId(), 0);
        int damage = (int) Math.ceil(bonus * multiplier);

        event.setDamage(event.getDamage() + damage);

        target.getWorld().spawnParticle(Particle.END_ROD, target.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3);
        target.getWorld().playSound(target.getLocation(), Sound.ITEM_TRIDENT_HIT, 0.8f, 1.4f);
        attacker.sendMessage(ChatColor.GOLD + "☀ Очищувальний удар: +" + damage +
                (remaining.get() > 0 ? "" : " (востаннє)"));
    }

    @Override
    public void cleanUp() {
        // Мапи per-caster, а інстанс спільний для pathway — НЕ чистимо тут (як DamageTransfer),
        // інакше вихід одного гравця стер би заряди всіх інших.
    }
}
