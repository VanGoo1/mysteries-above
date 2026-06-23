package me.vangoo.domain.spells;

/**
 * Рецепт згенерованого заклинання — ЛИШЕ дані + правила балансу.
 * <p>
 * Жодного Bukkit: ні {@code Particle}, ні {@code PotionEffectType}, ні {@code Location}.
 * Саме це серіалізується ({@link SpellCodec}), валідовується і тестується без сервера.
 * Хореографія ефектів (партикли, урон, телепорт) живе окремо, у шарі поведінки.
 */
public record SpellRecipe(
        String name,
        Shape shape,
        double damage,
        double radius,
        double heal,
        int durationTicks,
        Buff buff,            // null, якщо shape != BUFF
        int buffAmplifier,
        int spiritualityCost,
        int cooldownSeconds
) {
    public enum Shape {PROJECTILE, AOE, SELF, BUFF, TELEPORT}

    /** Доменне представлення баффів — НЕ Bukkit PotionEffectType. Маппінг живе в раннері. */
    public enum Buff {SPEED, STRENGTH, RESISTANCE, REGENERATION, FIRE_RESISTANCE}

    public SpellRecipe {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Spell name cannot be blank");
        }
        if (shape == null) {
            throw new IllegalArgumentException("Spell shape cannot be null");
        }
        if (spiritualityCost < 0 || cooldownSeconds < 0) {
            throw new IllegalArgumentException("Cost/cooldown cannot be negative");
        }
        if (shape == Shape.BUFF && buff == null) {
            throw new IllegalArgumentException("BUFF spell requires a buff");
        }
    }

    public boolean dealsDamage() {
        return damage > 0;
    }

    public boolean heals() {
        return heal > 0;
    }

    /**
     * Будує рецепт із вибору гравця в конструкторі заклинань.
     * Це колишній {@code Spellcasting.calculateStats} — тепер чиста доменна функція,
     * яку можна покрити юніт-тестами без Bukkit/Mockito.
     */
    public static SpellRecipe fromBlueprint(SpellBlueprint b) {
        int cooldown = Math.max(1, 10 - b.cooldownLvl());
        double damage = 0, radius = 0, heal = 0;
        int durationTicks = 0, amplifier = 0, cost;
        Buff buff = null;

        switch (b.shape()) {
            case PROJECTILE -> {
                damage = 5 + b.powerLvl() * 2.5;
                cost = 25 + b.powerLvl() * 5;
            }
            case AOE -> {
                damage = 4 + b.powerLvl() * 2.0;
                radius = 3 + b.areaLvl() * 1.5;
                cost = 40 + b.powerLvl() * 5 + b.areaLvl() * 5;
                cooldown += 5;
            }
            case TELEPORT -> {
                radius = 10 + b.areaLvl() * 5;
                cost = 30 + b.areaLvl() * 3;
            }
            case SELF -> {
                heal = 4 + b.healLvl() * 2.0;
                cost = 40 + b.healLvl() * 8;
                cooldown += 10;
            }
            case BUFF -> {
                durationTicks = 100 + b.areaLvl() * 40;
                amplifier = b.healLvl();
                buff = b.buff();
                cost = 50 + b.areaLvl() * 5 + b.healLvl() * 20;
                cooldown += 30;
            }
            default -> throw new IllegalStateException("Unknown shape: " + b.shape());
        }

        cost = Math.max(5, cost - b.costReductionLvl() * 5);
        return new SpellRecipe(b.name(), b.shape(), damage, radius, heal,
                durationTicks, buff, amplifier, cost, cooldown);
    }
}
