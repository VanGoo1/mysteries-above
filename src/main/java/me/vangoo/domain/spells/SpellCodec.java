package me.vangoo.domain.spells;

import java.util.Locale;

/**
 * Єдине місце формату згенерованого заклинання: {@link SpellRecipe} ↔ рядок-ID + людський опис.
 * <p>
 * Замінює колишній {@code GeneratedSpellSerializer}, де {@code serialize()} та {@code create()}
 * дублювали той самий {@code String.format} на 11 полів. Кодек чистий (0 Bukkit), бо рецепт
 * оперує лише примітивами та доменними enum-ами. Формат зворотно-сумісний зі старими збереженнями:
 * слот частинки (поз. 1) пишеться як похідна назва й ігнорується при читанні.
 */
public final class SpellCodec {

    private static final String PREFIX = "generated:";
    private static final String DELIMITER = "|";
    private static final int FIELD_COUNT = 11;

    private SpellCodec() {
    }

    public static boolean isSerializedSpell(String abilityId) {
        return abilityId != null && abilityId.toLowerCase(Locale.US).startsWith(PREFIX);
    }

    public static String encode(SpellRecipe r) {
        return String.format(Locale.US, "%s%s%s%s%s%.1f%s%.1f%s%.1f%s%d%s%s%s%d%s%d%s%d%s%s",
                PREFIX,
                r.shape().name(), DELIMITER,
                particleNameFor(r.shape()), DELIMITER,
                r.damage(), DELIMITER,
                r.radius(), DELIMITER,
                r.heal(), DELIMITER,
                r.durationTicks(), DELIMITER,
                r.buff() != null ? r.buff().name() : "NONE", DELIMITER,
                r.buffAmplifier(), DELIMITER,
                r.spiritualityCost(), DELIMITER,
                r.cooldownSeconds(), DELIMITER,
                sanitizeName(r.name()));
    }

    public static SpellRecipe decode(String serializedId) {
        if (!isSerializedSpell(serializedId)) {
            throw new IllegalArgumentException("Not a serialized spell: " + serializedId);
        }
        String data = serializedId.substring(serializedId.toLowerCase(Locale.US).indexOf(PREFIX) + PREFIX.length());
        String[] parts = data.split("\\|", -1);
        if (parts.length != FIELD_COUNT) {
            throw new IllegalArgumentException("Invalid spell data format: " + serializedId);
        }

        SpellRecipe.Shape shape = SpellRecipe.Shape.valueOf(parts[0].toUpperCase(Locale.US));
        // parts[1] — частинка, навмисно ігнорується (похідна від типу).
        double damage = Double.parseDouble(parts[2].replace(",", "."));
        double radius = Double.parseDouble(parts[3].replace(",", "."));
        double heal = Double.parseDouble(parts[4].replace(",", "."));
        int duration = Integer.parseInt(parts[5]);
        SpellRecipe.Buff buff = parseBuff(parts[6]);
        int amplifier = Integer.parseInt(parts[7]);
        int cost = Integer.parseInt(parts[8]);
        int cooldown = Integer.parseInt(parts[9]);
        String name = parts[10].replace("_", " ");

        return new SpellRecipe(name, shape, damage, radius, heal, duration, buff, amplifier, cost, cooldown);
    }

    /** Людський опис рецепта (колишній {@code generateDescription}). Кулдаун/вартість додає система. */
    public static String describe(SpellRecipe r) {
        StringBuilder desc = new StringBuilder();
        desc.append("§7Тип: §b").append(typeNameUkrainian(r.shape()));
        if (r.dealsDamage()) {
            desc.append("\n§7Шкода: §c").append(String.format(Locale.US, "%.1f", r.damage()));
        }
        if (r.radius() > 0) {
            desc.append("\n§7Радіус: §9").append(String.format(Locale.US, "%.1f", r.radius())).append("м");
        }
        if (r.heals()) {
            desc.append("\n§7Зцілення: §a").append(String.format(Locale.US, "%.1f", r.heal()));
        }
        return desc.toString();
    }

    private static SpellRecipe.Buff parseBuff(String raw) {
        String upper = raw.toUpperCase(Locale.US);
        if (upper.equals("NONE")) {
            return null;
        }
        try {
            return SpellRecipe.Buff.valueOf(upper);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String particleNameFor(SpellRecipe.Shape shape) {
        return switch (shape) {
            case PROJECTILE -> "FIREWORK";
            case AOE -> "EXPLOSION";
            case TELEPORT -> "PORTAL";
            case SELF -> "HEART";
            case BUFF -> "SOUL";
        };
    }

    private static String typeNameUkrainian(SpellRecipe.Shape shape) {
        return switch (shape) {
            case PROJECTILE -> "Промінь";
            case AOE -> "Вибух";
            case TELEPORT -> "Телепорт";
            case SELF -> "Зцілення";
            case BUFF -> "Посилення";
        };
    }

    private static String sanitizeName(String name) {
        return name.replace(DELIMITER, "").replace("\n", " ");
    }
}
