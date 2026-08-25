package me.vangoo.pathways.tyrant.abilities;

import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.abilities.core.PermanentPassiveAbility;
import me.vangoo.domain.valueobjects.AbilityIdentity;
import me.vangoo.domain.valueobjects.Sequence;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

/**
 * Sequence 9: Рівновага. Досконале чуття рівноваги морського вовка — Матроса важче
 * збити з ніг. Механічно керує атрибутом KNOCKBACK_RESISTANCE (як PhysicalEnhancement
 * керує MAX_HEALTH): виставляє на активацію й скидає в 0 на деактивації, щоб бонус
 * не записався у файл гравця.
 */
public class Balance extends PermanentPassiveAbility {

    /** Спільний identity стійкості Тирана: сильніша версія (AirCushion) замінює цю. */
    static final String STANCE_IDENTITY = "tyrant_stance";

    private static final int TICK_PERIOD = 20;     // раз на секунду перепідтвердити

    private int tickCounter = 0;

    /** 0 = нема, 1.0 = повний імунітет. Перекривається прогресуючою версією. */
    protected double resistance() {
        return 0.25;
    }

    @Override
    public AbilityIdentity getIdentity() {
        return AbilityIdentity.of(STANCE_IDENTITY);
    }

    @Override
    public String getName() {
        return "Рівновага";
    }

    @Override
    public String getDescription(Sequence sequence) {
        return "§fДосконале чуття рівноваги морської сутності: вас важче збити з ніг " +
                "§b(опір відкиданню)§f.";
    }

    @Override
    public void onActivate(IAbilityContext context) {
        tickCounter = 0;
        applyResistance(context, resistance());
    }

    @Override
    public void tick(IAbilityContext context) {
        if (!context.playerData().isOnline(context.getCasterId())) return;
        tickCounter++;
        if (tickCounter % TICK_PERIOD == 0) {
            applyResistance(context, resistance());
        }
    }

    @Override
    public void onDeactivate(IAbilityContext context) {
        if (context.playerData().isOnline(context.getCasterId())) {
            applyResistance(context, 0.0);
        }
    }

    protected void applyResistance(IAbilityContext context, double value) {
        Player player = context.getCasterPlayer();
        if (player == null || !player.isValid()) return;

        AttributeInstance attr = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (attr != null && Math.abs(attr.getBaseValue() - value) > 1e-4) {
            attr.setBaseValue(value);
        }
    }
}
