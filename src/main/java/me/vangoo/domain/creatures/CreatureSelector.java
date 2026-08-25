package me.vangoo.domain.creatures;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Чисте правило вибору істоти для спавну (аналог BrewMatcher). Детерміноване при поданому roll.
 * Кожен кандидат займає сегмент [cumulative, cumulative+chance) на осі [0,1); roll за сумою
 * всіх шансів означає «спавну немає».
 */
public final class CreatureSelector {

    private final List<CreatureDefinition> creatures;

    public CreatureSelector(Collection<CreatureDefinition> creatures) {
        this.creatures = List.copyOf(creatures);
    }

    private static final double NEXT_NEEDED_WEIGHT = 4.0; // sequenceLevel - 1
    private static final double CURRENT_WEIGHT = 2.0;     // sequenceLevel

    public Optional<CreatureDefinition> pickForBiome(String biome, String baseEntityType, double roll) {
        return pickForBiome(biome, baseEntityType, roll, null);
    }

    public Optional<CreatureDefinition> pickForBiome(String biome, String baseEntityType, double roll,
                                                     ConvergenceBias bias) {
        List<CreatureDefinition> candidates = new ArrayList<>();
        double totalChance = 0.0;
        for (CreatureDefinition def : creatures) {
            SpawnRule s = def.spawn();
            if (s.naturalChance() <= 0.0) continue;
            if (!s.naturalBiomes().contains(biome)) continue;
            if (!s.naturalReplaces().contains(baseEntityType)) continue;
            candidates.add(def);
            totalChance += s.naturalChance();
        }
        if (candidates.isEmpty() || totalChance <= 0.0) return Optional.empty();

        double sumWeights = 0.0;
        double[] weights = new double[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            weights[i] = candidates.get(i).spawn().naturalChance() * multiplier(candidates.get(i), bias);
            sumWeights += weights[i];
        }
        // renormalize so the picked segments sum to the ORIGINAL totalChance (preserve P(spawn))
        double scale = sumWeights > 0.0 ? totalChance / sumWeights : 0.0;

        double cumulative = 0.0;
        for (int i = 0; i < candidates.size(); i++) {
            cumulative += weights[i] * scale;
            if (roll < cumulative) return Optional.of(candidates.get(i));
        }
        return Optional.empty();
    }

    private double multiplier(CreatureDefinition def, ConvergenceBias bias) {
        if (bias == null || bias.pathway() == null) return 1.0;
        if (def.pathway() == null || !def.pathway().equalsIgnoreCase(bias.pathway())) return 1.0;
        if (def.sequence() == bias.sequenceLevel() - 1) return NEXT_NEEDED_WEIGHT;
        if (def.sequence() == bias.sequenceLevel()) return CURRENT_WEIGHT;
        return 1.0;
    }

    /**
     * Вибір істоти для ambient-спавну: серед усіх істот, чий natural-біом збігається з поточним,
     * незалежно від шляху. Закон конвергенції лишається ВАГОВИМ ухилом (свій шлях і «наступна
     * потрібна» послідовність важать більше через {@link #multiplier}), а не жорстким фільтром.
     * Без «порогу неспавну» — рішення «спавнити» вже прийняв планувальник; цей метод лише обирає,
     * КОГО. {@code roll} у [0,1).
     */
    public Optional<CreatureDefinition> pickForAmbient(String biome, ConvergenceBias bias, double roll) {
        if (bias == null) return Optional.empty();
        List<CreatureDefinition> candidates = new ArrayList<>();
        for (CreatureDefinition def : creatures) {
            SpawnRule s = def.spawn();
            if (s.naturalChance() <= 0.0) continue;
            if (!s.naturalBiomes().contains(biome)) continue;
            candidates.add(def);
        }
        return weightedPick(candidates, def -> def.spawn().naturalChance(), bias, roll);
    }

    /**
     * Вибір істоти для структурного спавну — тобто «скриню відкрито». Ключі лут-таблиць у виборі
     * НЕ беруть участі: працює будь-яка структура (ванільна, датапакова, модова), бо інакше
     * ванільні скрині не давали нікого, крім блукаючого духа.
     *
     * <p>Пул — вікно з ДВОХ послідовностей навколо того, хто відкрив: його власна і наступна,
     * сильніша ({@code seq} і {@code seq - 1}). Посл. 9 бачить 9–8, Посл. 8 — 8–7, …, Посл. 6 —
     * 6–5. Це водночас замінює {@link ApexGate} на цьому шляху: Посл. 5 доступна лише гравцеві
     * Посл. 6 або 5, тобто рівно те саме правило, тільки виражене вікном.
     *
     * <p>Рішення «чи спавнити взагалі» приймає не цей метод, а лістенер за конфігом
     * ({@code creatures.structure.chance}) — тут лише КОГО, з ухилом Закону Конвергенції, як в
     * ambient. {@code roll} у [0,1).
     */
    public Optional<CreatureDefinition> pickForStructure(int playerSequence, ConvergenceBias bias, double roll) {
        List<CreatureDefinition> candidates = new ArrayList<>();
        for (CreatureDefinition def : creatures) {
            if (def.spawn().structureChance() <= 0.0) continue;
            if (def.sequence() != playerSequence && def.sequence() != playerSequence - 1) continue;
            candidates.add(def);
        }
        return weightedPick(candidates, def -> def.spawn().structureChance(), bias, roll);
    }

    /**
     * Ваговий вибір без «порогу неспавну»: рішення «спавнити» вже прийнято, лишилось обрати кого.
     * Спільний для ambient і структур — різняться вони лише пулом і полем-вагою.
     */
    private Optional<CreatureDefinition> weightedPick(List<CreatureDefinition> candidates,
                                                      java.util.function.ToDoubleFunction<CreatureDefinition> weightOf,
                                                      ConvergenceBias bias, double roll) {
        if (candidates.isEmpty()) return Optional.empty();

        double sumWeights = 0.0;
        double[] weights = new double[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            weights[i] = weightOf.applyAsDouble(candidates.get(i)) * multiplier(candidates.get(i), bias);
            sumWeights += weights[i];
        }
        if (sumWeights <= 0.0) return Optional.empty();

        double target = roll * sumWeights;
        double cumulative = 0.0;
        for (int i = 0; i < candidates.size(); i++) {
            cumulative += weights[i];
            if (target < cumulative) return Optional.of(candidates.get(i));
        }
        return Optional.of(candidates.get(candidates.size() - 1));
    }
}
