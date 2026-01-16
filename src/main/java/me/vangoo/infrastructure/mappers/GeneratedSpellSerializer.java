package me.vangoo.infrastructure.mappers;

import me.vangoo.domain.pathways.whitetower.abilities.custom.GeneratedSpell;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import org.bukkit.Particle;
import org.bukkit.potion.PotionEffectType;
import java.util.Locale;

public class GeneratedSpellSerializer {

    private static final String SPELL_PREFIX = "generated:";
    private static final String DELIMITER = "|";

    public static boolean isSerializedSpell(String abilityId) {
        return abilityId != null && abilityId.startsWith(SPELL_PREFIX);
    }

    public static String serialize(GeneratedSpell spell) {
        return String.format(Locale.US, "%s%s%s%s%s%.1f%s%.1f%s%.1f%s%d%s%s%s%d%s%d%s%d%s%s",
                SPELL_PREFIX,
                spell.getEffectType().name().toUpperCase(), DELIMITER,
                spell.getParticle().name().toUpperCase(), DELIMITER,
                spell.getDamage(), DELIMITER,
                spell.getRadius(), DELIMITER,
                spell.getHeal(), DELIMITER,
                spell.getDuration(), DELIMITER,
                spell.getPotionEffect() != null ? spell.getPotionEffect().getName().toUpperCase() : "NONE", DELIMITER,
                spell.getPotionAmplifier(), DELIMITER,
                spell.getSpiritualityCost(), DELIMITER,
                spell.getCooldown(null), DELIMITER,
                sanitizeName(spell.getName())
        );
    }

    public static GeneratedSpell create(
            String name,
            String description,
            GeneratedSpell.EffectType type,
            Particle particle,
            double damage,
            double radius,
            double heal,
            int duration,
            PotionEffectType potionEffect,
            int potionAmplifier,
            int spiritualityCost,
            int cooldown) {

        String serializedId = String.format(Locale.US, "%s%s%s%s%s%.1f%s%.1f%s%.1f%s%d%s%s%s%d%s%d%s%d%s%s",
                SPELL_PREFIX,
                type.name().toUpperCase(), DELIMITER,
                particle.name().toUpperCase(), DELIMITER,
                damage, DELIMITER,
                radius, DELIMITER,
                heal, DELIMITER,
                duration, DELIMITER,
                potionEffect != null ? potionEffect.getName().toUpperCase() : "NONE", DELIMITER,
                potionAmplifier, DELIMITER,
                spiritualityCost, DELIMITER,
                cooldown, DELIMITER,
                sanitizeName(name)
        );

        return new GeneratedSpell(
                AbilityIdentity.of(serializedId),
                name,
                description,
                spiritualityCost,
                cooldown,
                type,
                particle,
                damage,
                radius,
                heal,
                duration,
                potionEffect,
                potionAmplifier
        );
    }

    public static GeneratedSpell deserialize(String serializedId) {
        if (!isSerializedSpell(serializedId)) {
            throw new IllegalArgumentException("Not a serialized spell: " + serializedId);
        }

        String data = serializedId.substring(SPELL_PREFIX.length());
        String[] parts = data.split("\\|", -1);

        if (parts.length != 11) {
            throw new IllegalArgumentException("Invalid spell data format");
        }

        try {
            GeneratedSpell.EffectType type = GeneratedSpell.EffectType.valueOf(parts[0].toUpperCase());
            Particle particle = Particle.valueOf(parts[1].toUpperCase());
            double damage = Double.parseDouble(parts[2].replace(",", "."));
            double radius = Double.parseDouble(parts[3].replace(",", "."));
            double heal = Double.parseDouble(parts[4].replace(",", "."));
            int duration = Integer.parseInt(parts[5]);
            String effectName = parts[6].toUpperCase();
            PotionEffectType effect = effectName.equals("NONE") ? null : PotionEffectType.getByName(effectName);
            int amplifier = Integer.parseInt(parts[7]);
            int cost = Integer.parseInt(parts[8]);
            int cooldown = Integer.parseInt(parts[9]);
            String name = parts[10].replace("_", " ");

            // Генеруємо опис БЕЗ кулдауну і вартості (система сама їх додасть)
            String description = generateDescription(type, damage, radius, heal);

            return new GeneratedSpell(
                    AbilityIdentity.of(serializedId),
                    name,
                    description,
                    cost,
                    cooldown,
                    type,
                    particle,
                    damage,
                    radius,
                    heal,
                    duration,
                    effect,
                    amplifier
            );
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Error parsing spell: " + serializedId);
        }
    }

    /**
     * Генерує опис ТІЛЬКИ з унікальних характеристик.
     * Кулдаун та Духовність видалені, щоб не дублювалися з системним описом.
     */
    private static String generateDescription(
            GeneratedSpell.EffectType type,
            double damage,
            double radius,
            double heal) {

        StringBuilder desc = new StringBuilder();
        desc.append("§7Тип: §b").append(getTypeNameUkrainian(type)).append("\n");

        if (damage > 0) {
            desc.append("§7Шкода: §c").append(String.format(Locale.US, "%.1f", damage)).append("\n");
        }
        if (radius > 0) {
            desc.append("§7Радіус: §9").append(String.format(Locale.US, "%.1f", radius)).append("м\n");
        }
        if (heal > 0) {
            desc.append("§7Зцілення: §a").append(String.format(Locale.US, "%.1f", heal));
        }

        // Видаляємо зайвий перенос рядка в кінці, якщо він є
        if (desc.length() > 0 && desc.charAt(desc.length() - 1) == '\n') {
            desc.setLength(desc.length() - 1);
        }

        return desc.toString();
    }

    private static String sanitizeName(String name) {
        return name.replace(DELIMITER, "").replace("\n", " ");
    }

    private static String getTypeNameUkrainian(GeneratedSpell.EffectType type) {
        return switch (type) {
            case PROJECTILE -> "Промінь";
            case AOE -> "Вибух";
            case TELEPORT -> "Телепорт";
            case SELF -> "Зцілення";
            case BUFF -> "Посилення";
        };
    }
}