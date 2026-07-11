package me.vangoo.domain.market;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Лічильник порушень спокою на зборах. Чистий домен: перше порушення —
 * попередження, наступні — вигнання. Без Bukkit; санкцію виконує GatheringService.
 */
public class GatheringConduct {

    public enum Sanction { WARN, KICK }

    private final Map<UUID, Integer> strikes = new HashMap<>();

    public Sanction recordViolation(UUID playerId) {
        int count = strikes.merge(playerId, 1, Integer::sum);
        return count <= 1 ? Sanction.WARN : Sanction.KICK;
    }

    public void reset() {
        strikes.clear();
    }
}
