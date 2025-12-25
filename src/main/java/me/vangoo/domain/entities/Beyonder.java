package me.vangoo.domain.entities;

import me.vangoo.domain.valueobjects.SanityPenalty;
import me.vangoo.domain.abilities.core.Ability;
import me.vangoo.domain.abilities.core.AbilityResult;
import me.vangoo.domain.abilities.core.AbilityType;
import me.vangoo.domain.abilities.core.IAbilityContext;
import me.vangoo.domain.services.SequenceScaler;
import me.vangoo.domain.services.SpiritualityCalculator;
import me.vangoo.domain.valueobjects.Mastery;
import me.vangoo.domain.valueobjects.SanityLoss;
import me.vangoo.domain.valueobjects.Sequence;
import me.vangoo.domain.valueobjects.Spirituality;

import java.util.*;

public class Beyonder {
    private final UUID playerId;
    private Pathway pathway;
    private Sequence sequence;
    private Mastery mastery;
    private Spirituality spirituality;
    private SanityLoss sanityLoss;
    private transient Random random;
    private transient List<Ability> abilities;
    private transient SpiritualityCalculator spiritualityCalculator;

    public Beyonder(UUID playerId, Sequence sequence, Pathway pathway) {
        if (playerId == null) {
            throw new IllegalArgumentException("Player ID cannot be null");
        }
        if (pathway == null) {
            throw new IllegalArgumentException("Pathway cannot be null");
        }
        if (sequence == null) {
            throw new IllegalArgumentException("Sequence cannot be null");
        }

        this.playerId = playerId;
        this.sequence = sequence;
        this.pathway = pathway;
        this.mastery = Mastery.zero();
        this.sanityLoss = SanityLoss.none();
        this.abilities = new ArrayList<>();
        this.spiritualityCalculator = new SpiritualityCalculator();
        this.random = new Random();
        // Calculate initial spirituality
        updateMaximumSpirituality();
        this.spirituality = Spirituality.empty(spirituality.maximum());

        // Load abilities
        loadAbilities();
    }

    private void updateMaximumSpirituality() {
        int newMax = spiritualityCalculator.calculateMaximumSpirituality(sequence, mastery);

        if (spirituality == null) {
            spirituality = Spirituality.empty(newMax);
        } else {
            spirituality = spirituality.withNewMaximum(newMax);
        }
    }

    private void loadAbilities() {
        abilities = new ArrayList<>();
        for (int seq : sequence.getAllSequencesUpToCurrent()) {
            abilities.addAll(pathway.GetAbilitiesForSequence(seq));
        }
    }

    /**
     * Initialize transient fields after deserialization
     */
    public void initializeTransientFields() {
        if (spiritualityCalculator == null) {
            spiritualityCalculator = new SpiritualityCalculator();
        }
        if (random == null) {
            random = new Random();
        }
        if (abilities == null || abilities.isEmpty()) {
            loadAbilities();
        }
    }

    /**
     * Restore beyonder from persistence (for deserialization)
     */
    public static Beyonder restore(
            UUID playerId,
            Pathway pathway,
            Sequence sequence,
            Mastery mastery,
            int currentSpirituality,
            SanityLoss sanityLoss) {
        Beyonder beyonder = new Beyonder(playerId, sequence, pathway);
        beyonder.mastery = mastery;
        beyonder.sanityLoss = sanityLoss;
        beyonder.updateMaximumSpirituality();
        beyonder.spirituality = Spirituality.of(
                Math.min(currentSpirituality, beyonder.spirituality.maximum()),
                beyonder.spirituality.maximum());
        return beyonder;
    }

    public boolean canAdvance() {
        return mastery.canAdvance() && sequence.canAdvance();
    }

    public boolean canConsumePotion(Pathway potionPathway, Sequence potionSequence) {
        // Must be same pathway group
        if (potionPathway.getGroup() != pathway.getGroup()) {
            return false;
        }

        // Must be next sequence (potion sequence + 1 = current sequence)
        if (sequence.level() != potionSequence.level() + 1) {
            return false;
        }

        return mastery.canAdvance();
    }

    public void cleanUpAbilities() {
        if (abilities != null) {
            for (Ability ability : abilities) {
                ability.cleanUp();
            }
        }
    }

    public void advance() {
        if (!canAdvance()) {
            throw new IllegalStateException(
                    "Cannot advance: sequence=" + sequence + ", mastery=" + mastery);
        }

        sequence = sequence.advance();
        mastery = mastery.reset();
        updateMaximumSpirituality();
        loadAbilities();
    }

    public AbilityResult useAbility(Ability ability, IAbilityContext context) {
        if (!hasAbility(ability)) {
            return AbilityResult.failure("У вас немає цієї здібності");
        }

        if (ability.getType() == AbilityType.ACTIVE) {
            int cost = ability.getSpiritualityCost();
            if (!spirituality.hasSufficient(cost)) {
                return AbilityResult.insufficientResources(
                        String.format("Недостатньо духовності: потрібно %d, є %d",
                                cost, spirituality.current())
                );
            }
        }

        AbilityResult executionResult = ability.execute(context);

        if (!executionResult.isSuccess()) {
            return executionResult;
        }

        if (ability.getType() == AbilityType.ACTIVE) {
            int cost = ability.getSpiritualityCost();
            spirituality = spirituality.decrement(cost);

            double masteryGainRatio = (double) cost / getMaxSpirituality();
            int masteryGain = (int) Math.ceil(masteryGainRatio * 100);
            mastery = mastery.increment(masteryGain);

            if (spirituality.isCritical()) {
                increaseSanityLoss(2);
            }
        }else {
            return AbilityResult.success();
        }

        SanityPenalty penalty = checkAndCalculateSanityPenalty();

        if (penalty.hasEffect()) {
            String message = getSanityLossMessage();
            return AbilityResult.successWithPenalty(penalty, message);
        }

        return AbilityResult.success();
    }

    private SanityPenalty checkAndCalculateSanityPenalty() {
        // Negligible sanity loss - no check needed
        if (sanityLoss.isNegligible()) {
            return SanityPenalty.none();
        }

        double failureChance = sanityLoss.calculateFailureChance();

        // Roll for penalty
        if (random.nextDouble() >= failureChance) {
            return SanityPenalty.none();
        }

        // Check failed - calculate penalty
        return calculatePenalty();
    }

    /**
     * Calculate penalty based on sanity loss severity and sequence level
     */
    private SanityPenalty calculatePenalty() {
        // EXTREME: 96-100 → Death
        if (sanityLoss.isExtreme()) {
            return SanityPenalty.extreme();
        }

        // CRITICAL: 81-95 → Large penalties
        if (sanityLoss.isCritical()) {
            if (random.nextBoolean()) {
                int spiritualityLoss = SequenceScaler.scaleSpiritualityLoss(
                        getMaxSpirituality(),
                        sequence.level()
                );
                return SanityPenalty.spiritualityLoss(spiritualityLoss);
            } else {
                int damage = SequenceScaler.scaleDamagePenalty(sequence.level());
                return SanityPenalty.damage(damage);
            }
        }

        // SEVERE: 61-80 → Medium penalties
        if (sanityLoss.isSevere()) {
            if (random.nextBoolean()) {
                int spiritualityLoss = SequenceScaler.scaleSpiritualityLoss(
                        getMaxSpirituality(),
                        sequence.level()
                ) * 2 / 3;
                return SanityPenalty.spiritualityLoss(Math.max(5, spiritualityLoss));
            } else {
                int damage = SequenceScaler.scaleDamagePenalty(sequence.level());
                return SanityPenalty.damage(damage);
            }
        }

        // SERIOUS: 41-60 → Small penalties
        if (sanityLoss.isSerious()) {
            if (random.nextDouble() < 0.7) {
                int damage = SequenceScaler.scaleDamagePenalty(sequence.level());
                return SanityPenalty.damage(Math.max(1, damage / 2));
            } else {
                int spiritualityLoss = SequenceScaler.scaleSpiritualityLoss(
                        getMaxSpirituality(),
                        sequence.level()
                ) / 3;
                return SanityPenalty.spiritualityLoss(Math.max(3, spiritualityLoss));
            }
        }

        // MODERATE: 21-40 → Rare minor damage
        if (sanityLoss.isModerate()) {
            if (random.nextDouble() < 0.3) {
                return SanityPenalty.damage(1);
            }
        }

        // MINOR: 11-20 → No penalty
        return SanityPenalty.none();
    }

    /**
     * Generate message based on current sanity loss
     */
    private String getSanityLossMessage() {
        if (sanityLoss.isMinor()) {
            return "Ваші руки злегка тремтять...";
        } else if (sanityLoss.isModerate()) {
            return "Ваші сили відмовляються слухатися!";
        } else if (sanityLoss.isSerious()) {
            return "Хаос у вашій свідомості блокує здібності!";
        } else if (sanityLoss.isSevere()) {
            return "Втрата контролю посилюється!";
        } else if (sanityLoss.isCritical()) {
            return "Божевільний шепіт заважає зосередитися!";
        } else if (sanityLoss.isExtreme()) {
            return "ХАОС ПОГЛИНАЄ ВАШУ СВІДОМІСТЬ!";
        }
        return "Щось не так...";
    }

    public void restoreAfterSleep() {
        decreaseSanityLoss(5);
        if (spirituality.current() < getMaxSpirituality())
            spirituality = Spirituality.of(getMaxSpirituality() / 2, getMaxSpirituality());
    }

    public void regenerateSpirituality() {
        if (!spirituality.isFull()) {
            int regenRate = spiritualityCalculator.calculateRegenerationRate(sequence, mastery);
            spirituality = spirituality.regenerate(regenRate);
        }
    }

    public void setSpirituality(Spirituality spirituality) {
        this.spirituality = spirituality;
    }

    public void increaseSanityLoss(int amount) {
        sanityLoss = sanityLoss.increase(amount);
    }

    public void decreaseSanityLoss(int amount) {
        sanityLoss = sanityLoss.decrease(amount);
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Pathway getPathway() {
        return pathway;
    }

    public Sequence getSequence() {
        return sequence;
    }

    public int getSequenceLevel() {
        return sequence.level();
    }

    public Mastery getMastery() {
        return mastery;
    }

    public int getMasteryValue() {
        return mastery.value();
    }

    public void setMastery(Mastery mastery) {
        this.mastery = mastery;
        updateMaximumSpirituality();
    }

    public Spirituality getSpirituality() {
        return spirituality;
    }

    public int getSpiritualityValue() {
        return spirituality.current();
    }

    public int getMaxSpirituality() {
        return spirituality.maximum();
    }

    public SanityLoss getSanityLoss() {
        return sanityLoss;
    }

    public int getSanityLossScale() {
        return sanityLoss.scale();
    }

    public List<Ability> getAbilities() {
        return abilities != null ? Collections.unmodifiableList(abilities) : Collections.emptyList();
    }

    private boolean hasAbility(Ability ability) {
        return abilities != null && abilities.contains(ability);
    }

    public Optional<Ability> getAbilityByName(String name) {
        if (abilities == null) return Optional.empty();

        return abilities.stream()
                .filter(a -> a.getName().equals(name))
                .findFirst();
    }
}
