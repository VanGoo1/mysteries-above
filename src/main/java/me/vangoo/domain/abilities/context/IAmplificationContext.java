package me.vangoo.domain.abilities.context;

import java.util.UUID;

/**
 * Тимчасовий множник шкоди на кастера (напр. Sun {@code Notarization}: Ампліфікація).
 * Глобальний lookup-сервіс без ідентичності кастера — сесії можуть тримати його напряму
 * (як {@code IBeyonderContext}/{@code IEventContext}), без захоплення чийогось {@code IAbilityContext}.
 */
public interface IAmplificationContext {

    void amplifyDamage(UUID playerId, double multiplier, int durationSeconds);

    /** Активний множник кастера, або {@code 1.0}, якщо ампліфікація не діє чи вже минула. */
    double getDamageMultiplier(UUID playerId);
}
