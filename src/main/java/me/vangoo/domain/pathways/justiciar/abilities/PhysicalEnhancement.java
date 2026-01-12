package me.vangoo.domain.pathways.justiciar.abilities; // Зміни пакет, якщо клас буде спільним

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.valueobjects.Sequence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PhysicalEnhancement extends PermanentPassiveAbility {

    private final String abilityName;
    private final String descriptionTemplate;
    private final int hpCalculationBase; // Базове число для формули HP (наприклад, 4 або 6)
    private final List<PotionEffectType> effectTypes;

    private static final double DEFAULT_HEALTH = 20.0;
    private static final int REFRESH_PERIOD_TICKS = 5 * 20; // Оновлюємо кожні 5 сек
    private static final int EFFECT_DURATION_TICKS = 6 * 20; // Тривалість ефекту 6 сек (щоб не мигало)

    /**
     * Конструктор для створення універсального посилення.
     *
     * @param name Назва здібності (напр. "Тіло Воїна", "Фізичні посилення")
     * @param description Опис (текст). Статистика буде додана автоматично.
     * @param hpCalculationBase Базове значення для скалювання HP (зазвичай 4 або 6). 0 - якщо HP не додається.
     * @param effects Типи ефектів, які треба накладати (напр. PotionEffectType.SPEED).
     */
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

        // Формуємо красивий список ефектів для опису
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
                bonusHp / 2.0, // Конвертуємо в серця для відображення
                effectList
        );
    }

    @Override
    public void onActivate(IAbilityContext context) {
        applyEffects(context);
        updateHealth(context);
    }

    @Override
    public void tick(IAbilityContext context) {
        Player player = context.getCaster();
        if (player == null || !player.isValid() || player.isDead()) return;

        // 1. Оновлення HP (перевірка кожного тіка, але зміна тільки при необхідності)
        updateHealth(context);

        // 2. Оновлення Ефектів (періодично)
        if (player.getTicksLived() % REFRESH_PERIOD_TICKS == 0) {
            applyEffects(context);
        }
    }

    @Override
    public void onDeactivate(IAbilityContext context) {
        Player player = context.getCaster();
        if (player == null) return;

        // Скидання HP до стандарту (якщо це єдиний модифікатор)
        AttributeInstance healthAttr = player.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            // Тут обережно: ми скидаємо на дефолт.
            // Якщо у гравця є інші джерела HP, треба складнішу логіку (AttributeModifier),
            // але для простоти поки залишаємо так.
            healthAttr.setBaseValue(DEFAULT_HEALTH);
            if (player.getHealth() > DEFAULT_HEALTH) {
                player.setHealth(DEFAULT_HEALTH);
            }
        }

        // Прибираємо всі ефекти, які контролює ця здібність
        for (PotionEffectType type : effectTypes) {
            player.removePotionEffect(type);
        }
    }

    // --- Логіка ---

    private void updateHealth(IAbilityContext context) {
        if (hpCalculationBase <= 0) return; // Якщо HP не налаштовано

        Player player = context.getCaster();
        Sequence sequence = context.getCasterBeyonder().getSequence();
        AttributeInstance healthAttr = player.getAttribute(Attribute.MAX_HEALTH);

        if (healthAttr == null) return;

        int bonusHp = calculateBonusHP(sequence);
        double targetMaxHealth = DEFAULT_HEALTH + bonusHp;
        double currentMaxBase = healthAttr.getBaseValue();

        // Оновлюємо тільки якщо значення змінилося (оптимізація)
        if (Math.abs(currentMaxBase - targetMaxHealth) > 0.1) {
            healthAttr.setBaseValue(targetMaxHealth);
            if (player.getHealth() > targetMaxHealth) {
                player.setHealth(targetMaxHealth);
            }
        }
    }

    private void applyEffects(IAbilityContext context) {
        if (effectTypes.isEmpty()) return;

        Player player = context.getCaster();
        Sequence sequence = context.getCasterBeyonder().getSequence();
        int amplifier = calculateAmplifier(sequence);

        for (PotionEffectType type : effectTypes) {
            // force=true перезаписує старий ефект
            player.addPotionEffect(new PotionEffect(type, EFFECT_DURATION_TICKS, amplifier, false, false, true));
        }
    }

    // --- Розрахунки ---

    private int calculateBonusHP(Sequence sequence) {
        if (hpCalculationBase <= 0) return 0;
        // Використовуємо DIVINE стратегію як універсальну для фізичних бафів
        return scaleValue(hpCalculationBase, sequence, SequenceScaler.ScalingStrategy.DIVINE);
    }

    private int calculateAmplifier(Sequence sequence) {
        int power = SequenceScaler.getSequencePower(sequence.level());

        // Універсальна шкала сили ефектів:
        // High Sequence (2-0): Amplifier 2 (Рівень 3)
        // Mid Sequence (4-3): Amplifier 1 (Рівень 2)
        // Low Sequence (9-5): Amplifier 0 (Рівень 1)

        if (power >= 8) return 2;
        if (power >= 5) return 1;
        return 0;
    }

    // Утиліта для красивої назви в описі
    private String formatEffectName(PotionEffectType type) {
        String name = type.getName().toLowerCase().replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
}