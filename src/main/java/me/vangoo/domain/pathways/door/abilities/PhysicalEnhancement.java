package me.vangoo.domain.pathways.door.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class PhysicalEnhancement extends PermanentPassiveAbility {

    private static final int BASE_HP_CALC_VALUE = 4;
    private static final double DEFAULT_HEALTH = 20.0;

    // Таймінги для ефектів
    private static final int REFRESH_PERIOD_TICKS = 7 * 20; // 7 сек
    private static final int EFFECT_DURATION_TICKS = 8 * 20; // 8 сек

    @Override
    public String getName() {
        return "Фізичні посилення";
    }

    @Override
    public String getDescription(Sequence userSequence) {
        int bonusHp = calculateBonusHP(userSequence);
        int amplifier = calculateAmplifier(userSequence);
        int effectLevel = amplifier + 1;

        return String.format(
                "Ви отримує сильне тіло. " +
                        "Пасивно надає: Сила %d, Швидкість %d та +%.1f сердець до здоров'я.",
                effectLevel, effectLevel, bonusHp / 2.0
        );
    }

    @Override
    public void onActivate(IAbilityContext context) {
        // Миттєво застосовуємо все при старті
        applyPotionEffects(context);
        updateHealth(context);
    }

    @Override
    public void tick(IAbilityContext context) {
        Player player = context.getCaster();
        if (player == null || !player.isValid() || player.isDead()) return;

        // 1. ХП перевіряємо КОЖЕН тік.
        // Це гарантує, що при зміні послідовності (0 -> 8) ХП впаде миттєво.
        // Операція getAttribute дуже швидка, це не навантажить сервер.
        updateHealth(context);

        // 2. Ефекти оновлюємо раз на 7 секунд (циклічно)
        if (player.getTicksLived() % REFRESH_PERIOD_TICKS == 0) {
            applyPotionEffects(context);
        }
    }

    @Override
    public void onDeactivate(IAbilityContext context) {
        Player player = context.getCaster();
        if (player == null) return;

        // ЖОРСТКЕ СКИДАННЯ ПРИ ВИДАЛЕННІ ЗДІБНОСТІ
        AttributeInstance healthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            // Завжди повертаємо до 20.0
            healthAttr.setBaseValue(DEFAULT_HEALTH);

            // Якщо у гравця зараз більше 20 хп, зрізаємо його, щоб не висіли зайві серця
            if (player.getHealth() > DEFAULT_HEALTH) {
                player.setHealth(DEFAULT_HEALTH);
            }
        }

        player.removePotionEffect(PotionEffectType.STRENGTH);
        player.removePotionEffect(PotionEffectType.SPEED);
    }

    /**
     * Логіка оновлення здоров'я.
     * Перевіряє поточний sequence гравця і порівнює з реальним атрибутом.
     */
    private void updateHealth(IAbilityContext context) {
        Player player = context.getCaster();
        Sequence sequence = context.getCasterBeyonder().getSequence();
        AttributeInstance healthAttr = player.getAttribute(Attribute.MAX_HEALTH);

        if (healthAttr == null) return;

        // Розраховуємо, скільки має бути ХП на поточному рівні
        int bonusHp = calculateBonusHP(sequence);
        double targetMaxHealth = DEFAULT_HEALTH + bonusHp;
        double currentMaxBase = healthAttr.getBaseValue();

        // Порівнюємо. Якщо значення відрізняється (наприклад, змінили послідовність командою),
        // ми примусово встановлюємо правильне значення.
        if (Math.abs(currentMaxBase - targetMaxHealth) > 0.1) {
            healthAttr.setBaseValue(targetMaxHealth);

            // Якщо ми зменшили макс хп (наприклад seq 0 -> 8), треба перевірити поточне хп
            if (player.getHealth() > targetMaxHealth) {
                player.setHealth(targetMaxHealth);
            }
        }
    }

    private void applyPotionEffects(IAbilityContext context) {
        Player player = context.getCaster();
        Sequence sequence = context.getCasterBeyonder().getSequence();
        int amplifier = calculateAmplifier(sequence);

        // Накладаємо на 8 секунд (перекриває цикл у 7 секунд)
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, EFFECT_DURATION_TICKS, amplifier, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, EFFECT_DURATION_TICKS, amplifier, false, false));
    }

    // --- Розрахунки ---

    private int calculateBonusHP(Sequence sequence) {
        // DIVINE (+60% за рівень)
        int calculated = scaleValue(BASE_HP_CALC_VALUE, sequence, SequenceScaler.ScalingStrategy.DIVINE);
        return Math.max(6, calculated);
    }

    private int calculateAmplifier(Sequence sequence) {
        int power = SequenceScaler.getSequencePower(sequence.level());

        if (power >= 8) return 2; // Seq 1-0: Level 3
        if (power >= 5) return 1; // Seq 4-2: Level 2
        return 0;                 // Seq 9-5: Level 1
    }
}