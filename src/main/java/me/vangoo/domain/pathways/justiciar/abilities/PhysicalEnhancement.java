package me.vangoo.domain.pathways.justiciar.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class PhysicalEnhancement extends PermanentPassiveAbility {

    private final String abilityName;
    private final String descriptionTemplate;
    private final int hpCalculationBase;
    private final List<PotionEffectType> effectTypes;

    private static final double DEFAULT_HEALTH = 20.0;
    private static final int REFRESH_PERIOD_TICKS = 100; // 5 секунд
    private static final int EFFECT_DURATION_TICKS = 120; // 6 секунд

    private int tickCounter = 0;

    public PhysicalEnhancement(String name, String description, int hpCalculationBase, PotionEffectType... effects) {
        this.abilityName = name;
        this.descriptionTemplate = description;
        this.hpCalculationBase = hpCalculationBase;
        this.effectTypes = Arrays.asList(effects);
    }

    @Override
    public String getName() {
        return abilityName;
    }

    @Override
    public String getDescription(Sequence userSequence) {
        int bonusHp = calculateBonusHP(userSequence);
        int amplifier = calculateAmplifier(userSequence);

        String effectList = effectTypes.isEmpty()
                ? "немає"
                : effectTypes.stream()
                .map(type -> formatEffectName(type) + " " + (amplifier + 1))
                .collect(Collectors.joining(", "));

        return String.format(
                "%s\n\n" +
                        "§7Поточні бонуси:\n" +
                        "§a+%.1f ❤ Здоров'я\n" +
                        "§bЕфекти: §f%s",
                descriptionTemplate,
                bonusHp / 2.0,
                effectList
        );
    }

    @Override
    public void onActivate(IAbilityContext context) {
        tickCounter = 0;
        // Спочатку оновлюємо HP, щоб при вході одразу виставити правильне значення
        updateHealth(context);
        applyEffects(context);
    }

    @Override
    public void tick(IAbilityContext context) {
        UUID playerId = context.getCasterId();

        // Перевіряємо чи гравець онлайн
        if (!context.playerData().isOnline(playerId)) {
            return;
        }

        tickCounter++;

        // Перевіряємо HP рідше (раз на секунду), щоб не навантажувати сервер
        if (tickCounter % 20 == 0) {
            updateHealth(context);
        }

        // Оновлюємо ефекти кожні 5 секунд
        if (tickCounter % REFRESH_PERIOD_TICKS == 0) {
            applyEffects(context);
        }
    }

    @Override
    public void onDeactivate(IAbilityContext context) {
        UUID playerId = context.getCasterId();

        // Перевіряємо чи гравець онлайн
        if (!context.playerData().isOnline(playerId)) {
            return;
        }

        // ВАЖЛИВО: Ми прибираємо ефекти, але з HP треба бути обережним.
        // Якщо гравець просто виходить з гри, нам НЕ треба скидати HP до 20,
        // інакше це запишеться в файл гравця.
        // Але оскільки ми не знаємо причину деактивації (вихід чи зміна класу),
        // ми скидаємо HP, але пропорційно зменшуємо поточне, щоб не було глітчів.

        resetHealth(context, playerId);
        removeEffects(context, playerId);
    }

    // --- Виправлена логіка HP ---

    private void updateHealth(IAbilityContext context) {
        if (hpCalculationBase <= 0) return;

        Player player = context.getCasterPlayer();
        if (player == null || !player.isValid() || player.isDead()) {
            return;
        }

        Sequence sequence = context.getCasterBeyonder().getSequence();
        AttributeInstance healthAttr = player.getAttribute(Attribute.MAX_HEALTH);

        if (healthAttr == null) return;

        int bonusHp = calculateBonusHP(sequence);
        double targetMaxHealth = DEFAULT_HEALTH + bonusHp;
        double currentMaxBase = healthAttr.getBaseValue();

        // Оновлюємо тільки якщо значення змінилося
        if (Math.abs(currentMaxBase - targetMaxHealth) > 0.1) {

            // 1. Запам'ятовуємо поточний відсоток здоров'я
            double currentHealth = player.getHealth();
            double healthPercentage = currentHealth / currentMaxBase;

            // 2. Встановлюємо новий максимум
            healthAttr.setBaseValue(targetMaxHealth);

            // 3. Масштабуємо поточне здоров'я відповідно до нового максимуму
            // Це запобігає необхідності регенерувати "порожні" серця
            double newHealth = targetMaxHealth * healthPercentage;

            // Безпечна перевірка, щоб не вбити гравця і не перевищити ліміт
            if (newHealth > targetMaxHealth) newHealth = targetMaxHealth;
            if (newHealth < 1.0 && currentHealth >= 1.0) newHealth = 1.0; // Не вбивати при зміні статів

            player.setHealth(newHealth);
        }
    }

    /**
     * Скидає здоров'я до стандартного значення при деактивації
     */
    private void resetHealth(IAbilityContext context, UUID playerId) {
        if (hpCalculationBase <= 0) return;

        Player player = context.getCasterPlayer();
        if (player == null || !player.isValid()) {
            return;
        }

        AttributeInstance healthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null && healthAttr.getBaseValue() > DEFAULT_HEALTH) {
            double oldMax = healthAttr.getBaseValue();
            double currentHp = player.getHealth();
            double percent = currentHp / oldMax;

            healthAttr.setBaseValue(DEFAULT_HEALTH);

            // Масштабуємо здоров'я вниз, щоб зберегти відсоток
            player.setHealth(Math.max(1, DEFAULT_HEALTH * percent));
        }
    }

    /**
     * Накладає або оновлює поточні ефекти
     */
    private void applyEffects(IAbilityContext context) {
        if (effectTypes.isEmpty()) return;

        UUID playerId = context.getCasterId();
        Sequence sequence = context.getCasterBeyonder().getSequence();
        int amplifier = calculateAmplifier(sequence);

        for (PotionEffectType type : effectTypes) {
            context.entity().applyPotionEffect(
                    playerId,
                    type,
                    EFFECT_DURATION_TICKS,
                    amplifier
            );
        }
    }

    /**
     * Видаляє всі ефекти при деактивації
     */
    private void removeEffects(IAbilityContext context, UUID playerId) {
        for (PotionEffectType type : effectTypes) {
            context.entity().removePotionEffect(playerId, type);
        }
    }

    // --- Розрахунки ---

    private int calculateBonusHP(Sequence sequence) {
        if (hpCalculationBase <= 0) return 0;
        return scaleValue(hpCalculationBase, sequence, SequenceScaler.ScalingStrategy.DIVINE);
    }

    private int calculateAmplifier(Sequence sequence) {
        int power = SequenceScaler.getSequencePower(sequence.level());
        if (power >= 8) return 2;
        if (power >= 5) return 1;
        return 0;
    }

    private String formatEffectName(PotionEffectType type) {
        String name = type.getName().toLowerCase().replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
}