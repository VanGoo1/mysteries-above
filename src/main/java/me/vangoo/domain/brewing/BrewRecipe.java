package me.vangoo.domain.brewing;

import java.util.HashMap;
import java.util.Map;

/**
 * Правило зіставлення рецепта (чисте, без Bukkit). Інгредієнти — рядкові ключі у форматі
 * {@code custom:<id>} / {@code vanilla:<MATERIAL>}; Характеристика — {@code characteristic:<шлях>:<seq>}.
 */
public record BrewRecipe(
        String pathwayName,
        int sequence,
        Map<String, Integer> mainCounts,
        Map<String, Integer> auxCounts) {

    public BrewRecipe {
        // Незмінний VO: захищаємось від мутації переданих мап ззовні.
        mainCounts = Map.copyOf(mainCounts);
        auxCounts = Map.copyOf(auxCounts);
    }

    /** Ключ Характеристики цього (шлях, seq). */
    public String characteristicKey() {
        return "characteristic:" + pathwayName + ":" + sequence;
    }

    /** Чи відповідає наданий набір ключів цьому рецепту (класично АБО через Характеристику). */
    public boolean matches(Map<String, Integer> provided) {
        return matchesClassic(provided) || matchesViaCharacteristic(provided);
    }

    private boolean matchesClassic(Map<String, Integer> provided) {
        Map<String, Integer> required = new HashMap<>(mainCounts);
        auxCounts.forEach((k, v) -> required.merge(k, v, Integer::sum));
        return provided.equals(required);
    }

    private boolean matchesViaCharacteristic(Map<String, Integer> provided) {
        // 1× Характеристика замість УСІХ основних + ті самі допоміжні, нічого зайвого.
        Map<String, Integer> required = new HashMap<>(auxCounts);
        required.put(characteristicKey(), 1); // ключ Характеристики не може збігтися з aux-ключем
        return provided.equals(required);
    }
}
