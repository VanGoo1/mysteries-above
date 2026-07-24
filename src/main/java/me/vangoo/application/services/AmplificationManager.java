package me.vangoo.application.services;

import me.vangoo.domain.valueobjects.AmplificationBuff;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Тримає активний множник шкоди на кастера (Sun {@code Notarization}: Ампліфікація).
 * Стан — просто час спливання, без окремого таску: {@link #getDamageMultiplier} сам
 * перевіряє момент часу й прибирає прострочений запис (той самий підхід, що {@code CooldownManager}).
 */
public class AmplificationManager {
    private final Map<UUID, AmplificationBuff> buffs = new ConcurrentHashMap<>();

    public void amplifyDamage(UUID playerId, double multiplier, int durationSeconds) {
        buffs.put(playerId, new AmplificationBuff(multiplier, System.currentTimeMillis() + durationSeconds * 1000L));
    }

    public double getDamageMultiplier(UUID playerId) {
        AmplificationBuff buff = buffs.get(playerId);
        if (buff == null) return 1.0;
        if (!buff.isActive(System.currentTimeMillis())) {
            buffs.remove(playerId);
            return 1.0;
        }
        return buff.multiplier();
    }
}
