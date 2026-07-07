package me.vangoo.domain.creatures;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Правило темпу кастів істоти: глобальний кулдаун (ГКД) між будь-якими двома кастами
 * + власний кулдаун кожної здібності. Коли готові кілька — перемагає перша у списку
 * (порядок списку = пріоритет; найнижча послідовність ставиться першою).
 * Одиниці часу довільні (тіки/мс) — now і кулдауни мають бути в одних одиницях.
 */
public class AbilityCastPlanner {

    public record KitEntry(String skillName, long cooldown) {}

    private final List<KitEntry> kit;
    private final long gcd;
    private long gcdReadyAt;
    private final Map<String, Long> skillReadyAt = new HashMap<>();

    public AbilityCastPlanner(List<KitEntry> kit, long gcd) {
        this.kit = List.copyOf(kit);
        this.gcd = gcd;
    }

    public Optional<String> pickNext(long now) {
        if (now < gcdReadyAt) return Optional.empty();
        for (KitEntry entry : kit) {
            if (now >= skillReadyAt.getOrDefault(entry.skillName(), Long.MIN_VALUE)) {
                gcdReadyAt = now + gcd;
                skillReadyAt.put(entry.skillName(), now + entry.cooldown());
                return Optional.of(entry.skillName());
            }
        }
        return Optional.empty();
    }
}
