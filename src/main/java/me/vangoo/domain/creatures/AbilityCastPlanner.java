package me.vangoo.domain.creatures;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Правило темпу кастів істоти: глобальний кулдаун (ГКД) між будь-якими двома кастами
 * + власний кулдаун кожної здібності. Коли готові кілька — вибір ЗВАЖЕНО-ВИПАДКОВИЙ
 * серед готових: вага = позиція у списку (перша — найважча; фірмову здібність
 * найнижчої послідовності ставлять першою). Випадковість дає різноманітну ротацію
 * замість детермінованого циклу, де топ-здібність монополізує кожне вікно ГКД.
 * Одиниці часу довільні (тіки/мс) — now і кулдауни мають бути в одних одиницях.
 */
public class AbilityCastPlanner {

    public record KitEntry(String skillName, long cooldown) {}

    private final List<KitEntry> kit;
    private final long gcd;
    private final Random random;
    private long gcdReadyAt;
    private final Map<String, Long> skillReadyAt = new HashMap<>();

    public AbilityCastPlanner(List<KitEntry> kit, long gcd) {
        this(kit, gcd, new Random());
    }

    // для тестів: детермінований random
    AbilityCastPlanner(List<KitEntry> kit, long gcd, Random random) {
        this.kit = List.copyOf(kit);
        this.gcd = gcd;
        this.random = random;
    }

    public Optional<String> pickNext(long now) {
        if (now < gcdReadyAt) return Optional.empty();
        List<Integer> readyIndexes = new ArrayList<>();
        int totalWeight = 0;
        for (int i = 0; i < kit.size(); i++) {
            if (now >= skillReadyAt.getOrDefault(kit.get(i).skillName(), Long.MIN_VALUE)) {
                readyIndexes.add(i);
                totalWeight += weightOf(i);
            }
        }
        if (readyIndexes.isEmpty()) return Optional.empty();

        int roll = random.nextInt(totalWeight);
        int chosenIndex = readyIndexes.get(readyIndexes.size() - 1);
        for (int i : readyIndexes) {
            roll -= weightOf(i);
            if (roll < 0) {
                chosenIndex = i;
                break;
            }
        }
        KitEntry chosen = kit.get(chosenIndex);
        gcdReadyAt = now + gcd;
        skillReadyAt.put(chosen.skillName(), now + chosen.cooldown());
        return Optional.of(chosen.skillName());
    }

    // вага за позицією: перша у списку (фірмова) — найважча, остання — 1
    private int weightOf(int index) {
        return kit.size() - index;
    }
}
